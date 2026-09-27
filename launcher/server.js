const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const { execSync } = require('child_process');
const { Client, Authenticator } = require('minecraft-launcher-core');

const PORT = 38250;
const LAUNCHER_DIR = __dirname;
const PUBLIC_DIR = path.join(LAUNCHER_DIR, 'public');
const MODS_DIR = path.join(LAUNCHER_DIR, 'mods');
const CONFIG_FILE = path.join(LAUNCHER_DIR, 'launcher-config.json');

// Default configuration
let config = {
    username: 'KimikoPlayer',
    ram: 4096,
    jvmArgs: '-XX:+UseG1GC -XX:+ParallelRefProcEnabled',
    gameDir: path.join(process.env.APPDATA || os.homedir(), '.kimiko-client'),
    javaPath: ''
};

// Load saved config if exists
if (fs.existsSync(CONFIG_FILE)) {
    try {
        const saved = JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf8'));
        config = { ...config, ...saved };
    } catch (e) {}
}

function saveConfig() {
    try {
        fs.writeFileSync(CONFIG_FILE, JSON.stringify(config, null, 2), 'utf8');
    } catch (e) {}
}

// Find Java 21+ installations
function findJava() {
    const candidates = [
        config.javaPath,
        process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'javaw.exe') : '',
        process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java.exe') : '',
        'C:\\Program Files\\Java\\jdk-26.0.1\\bin\\javaw.exe',
        'C:\\Program Files\\Java\\jdk-26.0.1\\bin\\java.exe',
        'C:\\Program Files\\Java\\jdk-21\\bin\\javaw.exe',
        'C:\\Program Files\\Java\\jdk-21\\bin\\java.exe',
        'C:\\Program Files\\Eclipse Adoptium\\jdk-21\\bin\\javaw.exe',
        'C:\\Program Files\\Microsoft\\jdk-21\\bin\\javaw.exe',
        'C:\\Program Files\\BellSoft\\LibericaJDK-21\\bin\\javaw.exe',
        path.join(process.env.APPDATA || '', '.tlauncher', 'legacy', 'Minecraft', 'game', 'runtime', 'java-runtime-gamma', 'bin', 'javaw.exe')
    ];

    for (const cand of candidates) {
        if (cand && fs.existsSync(cand)) {
            return cand;
        }
    }

    // Try system PATH
    try {
        const res = execSync('where javaw.exe', { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
        const first = res.split(/\r?\n/)[0];
        if (first && fs.existsSync(first)) return first;
    } catch (e) {}

    try {
        const res = execSync('where java.exe', { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
        const first = res.split(/\r?\n/)[0];
        if (first && fs.existsSync(first)) return first;
    } catch (e) {}

    return 'javaw.exe';
}

// Global SSE clients
const sseClients = [];
function broadcast(event, data) {
    const msg = `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
    for (const res of sseClients) {
        try {
            res.write(msg);
        } catch (e) {}
    }
}

let launcherInstance = null;
let isLaunching = false;

// Ensure Fabric Profile JSON exists
async function ensureFabricProfile(gameDir) {
    const fabricVersionId = 'fabric-loader-0.19.5-1.21.11';
    const versionDir = path.join(gameDir, 'versions', fabricVersionId);
    const jsonPath = path.join(versionDir, `${fabricVersionId}.json`);

    if (!fs.existsSync(jsonPath)) {
        fs.mkdirSync(versionDir, { recursive: true });
        broadcast('log', { text: 'Загрузка манифеста Fabric Loader 0.19.5 (Minecraft 1.21.11)...' });
        const res = await fetch('https://meta.fabricmc.net/v2/versions/loader/1.21.11/0.19.5/profile/json');
        if (!res.ok) throw new Error(`Fabric Meta API returned ${res.status}`);
        const data = await res.json();
        fs.writeFileSync(jsonPath, JSON.stringify(data, null, 2), 'utf8');
        broadcast('log', { text: 'Манифест Fabric Loader успешно сохранен.' });
    }
}

// Copy bundled mods to game directory
function syncMods(gameDir) {
    const targetModsDir = path.join(gameDir, 'mods');
    fs.mkdirSync(targetModsDir, { recursive: true });

    if (fs.existsSync(MODS_DIR)) {
        const files = fs.readdirSync(MODS_DIR);
        for (const f of files) {
            if (f.endsWith('.jar')) {
                const src = path.join(MODS_DIR, f);
                const dest = path.join(targetModsDir, f);
                broadcast('log', { text: `Установка мода: ${f}...` });
                fs.copyFileSync(src, dest);
            }
        }
    }
}

// HTTP Server
const server = http.createServer(async (req, res) => {
    const parsedUrl = new URL(req.url, `http://${req.headers.host}`);
    const pathname = parsedUrl.pathname;

    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
    }

    // SSE Events
    if (pathname === '/api/events') {
        res.writeHead(200, {
            'Content-Type': 'text/event-stream',
            'Cache-Control': 'no-cache',
            'Connection': 'keep-alive'
        });
        res.write('event: connected\ndata: {}\n\n');
        sseClients.push(res);
        req.on('close', () => {
            const idx = sseClients.indexOf(res);
            if (idx !== -1) sseClients.splice(idx, 1);
        });
        return;
    }

    // API: System & Launcher info
    if (pathname === '/api/info' && req.method === 'GET') {
        const totalMemMb = Math.round(os.totalmem() / 1024 / 1024);
        const freeMemMb = Math.round(os.freemem() / 1024 / 1024);
        const detectedJava = findJava();

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            config,
            system: {
                totalMemMb,
                freeMemMb,
                detectedJava,
                cpu: os.cpus()[0] ? os.cpus()[0].model : 'CPU'
            }
        }));
        return;
    }

    // API: Ping FunTime
    if (pathname === '/api/ping' && req.method === 'GET') {
        const start = Date.now();
        const net = require('net');
        const socket = new net.Socket();
        socket.setTimeout(2500);

        socket.connect(25565, 'mc.funtime.su', () => {
            const latency = Date.now() - start;
            socket.destroy();
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ online: true, latency }));
        });

        socket.on('error', () => {
            socket.destroy();
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ online: false, latency: -1 }));
        });

        socket.on('timeout', () => {
            socket.destroy();
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ online: false, latency: -1 }));
        });
        return;
    }

    // API: Save settings
    if (pathname === '/api/settings' && req.method === 'POST') {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', () => {
            try {
                const data = JSON.parse(body);
                if (data.username) config.username = data.username.trim();
                if (data.ram) config.ram = parseInt(data.ram);
                if (data.jvmArgs !== undefined) config.jvmArgs = data.jvmArgs;
                if (data.gameDir) config.gameDir = data.gameDir;
                if (data.javaPath !== undefined) config.javaPath = data.javaPath;
                saveConfig();
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: true, config }));
            } catch (e) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: e.message }));
            }
        });
        return;
    }

    // API: Launch Game
    if (pathname === '/api/launch' && req.method === 'POST') {
        if (isLaunching) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Игра уже запускается или запущена!' }));
            return;
        }

        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', async () => {
            try {
                const data = JSON.parse(body || '{}');
                const username = (data.username || config.username || 'KimikoPlayer').trim();
                const ram = parseInt(data.ram) || config.ram || 4096;
                const gameDir = data.gameDir || config.gameDir;
                const javaPath = data.javaPath || findJava();

                config.username = username;
                config.ram = ram;
                config.gameDir = gameDir;
                config.javaPath = javaPath;
                saveConfig();

                isLaunching = true;
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ status: 'starting' }));

                broadcast('status', { state: 'downloading', text: 'Подготовка файлов игры...' });
                broadcast('log', { text: `[Kimiko Launcher] Запуск для пользователя: ${username}` });
                broadcast('log', { text: `[Kimiko Launcher] Папка игры: ${gameDir}` });
                broadcast('log', { text: `[Kimiko Launcher] Java: ${javaPath}` });
                broadcast('log', { text: `[Kimiko Launcher] Выделено памяти: ${ram} MB` });

                // 1. Ensure game root exists
                fs.mkdirSync(gameDir, { recursive: true });

                // 2. Download and set up Fabric Profile JSON
                await ensureFabricProfile(gameDir);

                // 3. Sync mods (Kimiko + Fabric API)
                syncMods(gameDir);

                // 4. Initialize MCLC
                launcherInstance = new Client();

                launcherInstance.on('debug', e => {
                    broadcast('log', { text: `[DEBUG] ${e}` });
                });

                launcherInstance.on('data', e => {
                    const text = e.toString().trim();
                    broadcast('log', { text: `[GAME] ${text}` });
                });

                launcherInstance.on('progress', e => {
                    const percent = e.total > 0 ? Math.round((e.current / e.total) * 100) : 0;
                    broadcast('progress', {
                        type: e.type,
                        task: e.task,
                        current: e.current,
                        total: e.total,
                        percent: percent,
                        text: `Загрузка: ${e.type} (${percent}%)`
                    });
                });

                launcherInstance.on('download-status', e => {
                    const percent = e.total > 0 ? Math.round((e.current / e.total) * 100) : 0;
                    broadcast('progress', {
                        type: e.type,
                        current: e.current,
                        total: e.total,
                        percent: percent,
                        text: `Загрузка библиотек: ${percent}%`
                    });
                });

                launcherInstance.on('close', code => {
                    isLaunching = false;
                    broadcast('status', { state: 'idle', text: `Игра завершена (код: ${code})` });
                    broadcast('log', { text: `[Kimiko Launcher] Игра завершена с кодом ${code}` });
                });

                broadcast('status', { state: 'downloading', text: 'Проверка ресурсов и библиотек Minecraft 1.21.11...' });

                const opts = {
                    clientPackage: null,
                    authorization: Authenticator.getAuth(username),
                    root: gameDir,
                    javaPath: javaPath,
                    version: {
                        number: '1.21.11',
                        type: 'release',
                        custom: 'fabric-loader-0.19.5-1.21.11'
                    },
                    memory: {
                        max: `${ram}M`,
                        min: '1024M'
                    },
                    customArgs: config.jvmArgs ? config.jvmArgs.split(' ').filter(Boolean) : ['-XX:+UseG1GC']
                };

                await launcherInstance.launch(opts);
                broadcast('status', { state: 'running', text: 'Kimiko Client успешно запущен!' });

            } catch (err) {
                isLaunching = false;
                console.error(err);
                broadcast('status', { state: 'error', text: `Ошибка: ${err.message}` });
                broadcast('log', { text: `[ERROR] ${err.stack || err.message}` });
            }
        });
        return;
    }

    // Static files serving
    let filePath = pathname === '/' ? path.join(PUBLIC_DIR, 'index.html') : path.join(PUBLIC_DIR, pathname);
    if (!filePath.startsWith(PUBLIC_DIR)) {
        res.writeHead(403);
        res.end('Forbidden');
        return;
    }

    if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
        const ext = path.extname(filePath).toLowerCase();
        const mimeTypes = {
            '.html': 'text/html; charset=utf-8',
            '.css': 'text/css; charset=utf-8',
            '.js': 'application/javascript; charset=utf-8',
            '.json': 'application/json',
            '.png': 'image/png',
            '.jpg': 'image/jpeg',
            '.svg': 'image/svg+xml',
            '.ico': 'image/x-icon',
            '.woff2': 'font/woff2'
        };
        const contentType = mimeTypes[ext] || 'application/octet-stream';
        res.writeHead(200, { 'Content-Type': contentType });
        fs.createReadStream(filePath).pipe(res);
    } else {
        res.writeHead(404);
        res.end('Not Found');
    }
});

server.listen(PORT, '127.0.0.1', () => {
    console.log(`[Kimiko Launcher] Server listening on http://127.0.0.1:${PORT}`);
});

// Audio Synthesizer (Web Audio API for rich UI feedback without external files)
const audioCtx = new (window.AudioContext || window.webkitAudioContext)();

function playSound(type) {
    if (audioCtx.state === 'suspended') {
        audioCtx.resume();
    }
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    osc.connect(gain);
    gain.connect(audioCtx.destination);

    const now = audioCtx.currentTime;

    if (type === 'click') {
        osc.type = 'sine';
        osc.frequency.setValueAtTime(650, now);
        osc.frequency.exponentialRampToValueAtTime(180, now + 0.08);
        gain.gain.setValueAtTime(0.2, now);
        gain.gain.linearRampToValueAtTime(0.01, now + 0.08);
        osc.start(now);
        osc.stop(now + 0.08);
    } else if (type === 'hover') {
        osc.type = 'triangle';
        osc.frequency.setValueAtTime(420, now);
        gain.gain.setValueAtTime(0.05, now);
        gain.gain.linearRampToValueAtTime(0.001, now + 0.05);
        osc.start(now);
        osc.stop(now + 0.05);
    } else if (type === 'launch') {
        osc.type = 'sawtooth';
        osc.frequency.setValueAtTime(220, now);
        osc.frequency.exponentialRampToValueAtTime(880, now + 0.4);
        gain.gain.setValueAtTime(0.25, now);
        gain.gain.linearRampToValueAtTime(0.01, now + 0.45);
        osc.start(now);
        osc.stop(now + 0.45);
    }
}

// Particle Canvas Background
const canvas = document.getElementById('bg-canvas');
const ctx = canvas.getContext('2d');
let particles = [];

function resizeCanvas() {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
}
window.addEventListener('resize', resizeCanvas);
resizeCanvas();

class Particle {
    constructor() {
        this.reset();
    }
    reset() {
        this.x = Math.random() * canvas.width;
        this.y = Math.random() * canvas.height;
        this.vx = (Math.random() - 0.5) * 0.4;
        this.vy = (Math.random() - 0.5) * 0.4;
        this.size = Math.random() * 2 + 1;
        this.color = Math.random() > 0.4 ? 'rgba(168, 85, 247, ' : 'rgba(236, 72, 153, ';
        this.alpha = Math.random() * 0.5 + 0.1;
    }
    update() {
        this.x += this.vx;
        this.y += this.vy;
        if (this.x < 0 || this.x > canvas.width || this.y < 0 || this.y > canvas.height) {
            this.reset();
        }
    }
    draw() {
        ctx.beginPath();
        ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2);
        ctx.fillStyle = this.color + this.alpha + ')';
        ctx.shadowBlur = 8;
        ctx.shadowColor = this.color + '0.8)';
        ctx.fill();
        ctx.shadowBlur = 0;
    }
}

for (let i = 0; i < 45; i++) {
    particles.push(new Particle());
}

function animateParticles() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    for (let i = 0; i < particles.length; i++) {
        particles[i].update();
        particles[i].draw();
        for (let j = i + 1; j < particles.length; j++) {
            const dx = particles[i].x - particles[j].x;
            const dy = particles[i].y - particles[j].y;
            const dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < 110) {
                ctx.beginPath();
                ctx.moveTo(particles[i].x, particles[i].y);
                ctx.lineTo(particles[j].x, particles[j].y);
                ctx.strokeStyle = `rgba(168, 85, 247, ${0.15 * (1 - dist / 110)})`;
                ctx.lineWidth = 0.6;
                ctx.stroke();
            }
        }
    }
    requestAnimationFrame(animateParticles);
}
animateParticles();

// Elements
const usernameInput = document.getElementById('username-input');
const avatarImg = document.getElementById('avatar-img');
const ramSlider = document.getElementById('ram-slider');
const ramValueBadge = document.getElementById('ram-value-badge');
const presetBtns = document.querySelectorAll('.preset-btn');
const btnLaunch = document.getElementById('btn-launch');
const launchBtnText = document.getElementById('launch-btn-text');
const progressBox = document.getElementById('progress-box');
const progressStatusText = document.getElementById('progress-status-text');
const progressPercent = document.getElementById('progress-percent');
const progressBarFill = document.getElementById('progress-bar-fill');
const progressSubText = document.getElementById('progress-sub-text');
const pingText = document.getElementById('ping-text');
const pingCircle = document.querySelector('.ping-circle');
const terminalBody = document.getElementById('terminal-body');

// Modals
const consoleModal = document.getElementById('console-modal');
const settingsModal = document.getElementById('settings-modal');
const btnConsole = document.getElementById('btn-console');
const btnSettings = document.getElementById('btn-settings');
const btnCloseConsole = document.getElementById('btn-close-console');
const btnCloseSettings = document.getElementById('btn-close-settings');
const btnSaveSettings = document.getElementById('btn-save-settings');
const btnCloseApp = document.getElementById('btn-close');
const btnClearLogs = document.getElementById('btn-clear-logs');
const btnCopyLogs = document.getElementById('btn-copy-logs');

const settingGameDir = document.getElementById('setting-game-dir');
const settingJavaPath = document.getElementById('setting-java-path');
const settingJvmArgs = document.getElementById('setting-jvm-args');
const displayGameDir = document.getElementById('display-game-dir');
const javaStatusText = document.getElementById('java-status-text');

let isLaunching = false;

// Update Avatar on typing
let avatarTimer = null;
usernameInput.addEventListener('input', () => {
    clearTimeout(avatarTimer);
    avatarTimer = setTimeout(() => {
        const name = usernameInput.value.trim() || 'KimikoPlayer';
        avatarImg.src = `https://mc-heads.net/avatar/${encodeURIComponent(name)}/64`;
    }, 450);
});

// RAM Slider & Presets
function setRam(mb) {
    ramSlider.value = mb;
    ramValueBadge.textContent = `${mb} MB`;
    presetBtns.forEach(btn => {
        if (parseInt(btn.getAttribute('data-ram')) === mb) {
            btn.classList.add('active');
        } else {
            btn.classList.remove('active');
        }
    });
}

ramSlider.addEventListener('input', (e) => {
    setRam(parseInt(e.target.value));
});

presetBtns.forEach(btn => {
    btn.addEventListener('click', () => {
        playSound('click');
        const ram = parseInt(btn.getAttribute('data-ram'));
        setRam(ram);
    });
});

// Logging
function addLog(text) {
    const line = document.createElement('div');
    line.className = 'log-line';

    if (text.includes('[ERROR]') || text.includes('Exception') || text.includes('Error')) {
        line.classList.add('error');
    } else if (text.includes('[WARN]') || text.includes('Warning')) {
        line.classList.add('warn');
    } else if (text.includes('[GAME]')) {
        line.classList.add('game');
    } else {
        line.classList.add('info');
    }

    line.textContent = text;
    terminalBody.appendChild(line);
    terminalBody.scrollTop = terminalBody.scrollHeight;
}

// Fetch Initial Info
async function loadInfo() {
    try {
        const res = await fetch('/api/info');
        const data = await res.json();
        
        if (data.config) {
            if (data.config.username) {
                usernameInput.value = data.config.username;
                avatarImg.src = `https://mc-heads.net/avatar/${encodeURIComponent(data.config.username)}/64`;
            }
            if (data.config.ram) setRam(data.config.ram);
            if (data.config.gameDir) {
                settingGameDir.value = data.config.gameDir;
                displayGameDir.textContent = data.config.gameDir.split(/[\\/]/).pop() || '.kimiko-client';
            }
            if (data.config.javaPath) settingJavaPath.value = data.config.javaPath;
            if (data.config.jvmArgs) settingJvmArgs.value = data.config.jvmArgs;
        }

        if (data.system) {
            if (data.system.detectedJava) {
                javaStatusText.textContent = `Java найдена`;
                javaStatusText.style.color = '#34d399';
            }
        }
    } catch (e) {
        console.error('Error fetching launcher info:', e);
    }
}
loadInfo();

// Ping Server
async function pingServer() {
    try {
        const res = await fetch('/api/ping');
        const data = await res.json();
        if (data.online) {
            pingText.textContent = `${data.latency} ms`;
            pingCircle.style.backgroundColor = '#10b981';
            pingCircle.style.boxShadow = '0 0 8px #10b981';
        } else {
            pingText.textContent = 'Офлайн';
            pingCircle.style.backgroundColor = '#f87171';
            pingCircle.style.boxShadow = '0 0 8px #f87171';
        }
    } catch (e) {
        pingText.textContent = 'Офлайн';
    }
}
pingServer();
setInterval(pingServer, 8000);

// Connect SSE Events
function setupEvents() {
    const evtSource = new EventSource('/api/events');

    evtSource.addEventListener('log', e => {
        const data = JSON.parse(e.data);
        if (data.text) addLog(data.text);
    });

    evtSource.addEventListener('progress', e => {
        const data = JSON.parse(e.data);
        progressBox.style.display = 'block';
        if (data.percent !== undefined) {
            progressPercent.textContent = `${data.percent}%`;
            progressBarFill.style.width = `${data.percent}%`;
        }
        if (data.text) progressStatusText.textContent = data.text;
        if (data.task) progressSubText.textContent = data.task;
    });

    evtSource.addEventListener('status', e => {
        const data = JSON.parse(e.data);
        if (data.text) {
            addLog(`[STATUS] ${data.text}`);
            progressStatusText.textContent = data.text;
        }

        if (data.state === 'running') {
            isLaunching = false;
            btnLaunch.classList.remove('disabled');
            launchBtnText.textContent = 'ИГРА ЗАПУЩЕНА';
            progressPercent.textContent = '100%';
            progressBarFill.style.width = '100%';
        } else if (data.state === 'idle') {
            isLaunching = false;
            btnLaunch.classList.remove('disabled');
            launchBtnText.textContent = 'ИГРАТЬ СЕЙЧАС';
        } else if (data.state === 'error') {
            isLaunching = false;
            btnLaunch.classList.remove('disabled');
            launchBtnText.textContent = 'ОШИБКА ЗАПУСКА';
        }
    });

    evtSource.onerror = () => {
        setTimeout(setupEvents, 3000);
    };
}
setupEvents();

// Launch Button
btnLaunch.addEventListener('click', async () => {
    if (isLaunching) return;

    playSound('launch');
    isLaunching = true;
    btnLaunch.classList.add('disabled');
    launchBtnText.textContent = 'ЗАПУСК...';
    progressBox.style.display = 'block';
    progressPercent.textContent = '0%';
    progressBarFill.style.width = '0%';
    progressStatusText.textContent = 'Связь с сервером...';

    const payload = {
        username: usernameInput.value.trim() || 'KimikoPlayer',
        ram: parseInt(ramSlider.value),
        gameDir: settingGameDir.value.trim(),
        javaPath: settingJavaPath.value.trim(),
        jvmArgs: settingJvmArgs.value.trim()
    };

    try {
        const res = await fetch('/api/launch', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (data.error) {
            alert(data.error);
            isLaunching = false;
            btnLaunch.classList.remove('disabled');
            launchBtnText.textContent = 'ИГРАТЬ СЕЙЧАС';
        }
    } catch (err) {
        alert('Ошибка связи с локальным сервером лаунчера: ' + err.message);
        isLaunching = false;
        btnLaunch.classList.remove('disabled');
        launchBtnText.textContent = 'ИГРАТЬ СЕЙЧАС';
    }
});

// Modals interaction
btnConsole.addEventListener('click', () => {
    playSound('click');
    consoleModal.classList.add('active');
});
btnCloseConsole.addEventListener('click', () => {
    playSound('click');
    consoleModal.classList.remove('active');
});

btnSettings.addEventListener('click', () => {
    playSound('click');
    settingsModal.classList.add('active');
});
btnCloseSettings.addEventListener('click', () => {
    playSound('click');
    settingsModal.classList.remove('active');
});

btnSaveSettings.addEventListener('click', async () => {
    playSound('click');
    const payload = {
        username: usernameInput.value.trim(),
        ram: parseInt(ramSlider.value),
        gameDir: settingGameDir.value.trim(),
        javaPath: settingJavaPath.value.trim(),
        jvmArgs: settingJvmArgs.value.trim()
    };

    try {
        await fetch('/api/settings', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        displayGameDir.textContent = settingGameDir.value.split(/[\\/]/).pop() || '.kimiko-client';
        settingsModal.classList.remove('active');
    } catch (e) {
        alert('Не удалось сохранить настройки: ' + e.message);
    }
});

btnClearLogs.addEventListener('click', () => {
    terminalBody.innerHTML = '';
});

btnCopyLogs.addEventListener('click', () => {
    const text = Array.from(terminalBody.querySelectorAll('.log-line'))
        .map(el => el.textContent)
        .join('\n');
    navigator.clipboard.writeText(text);
    alert('Логи скопированы в буфер обмена!');
});

btnCloseApp.addEventListener('click', () => {
    window.close();
});

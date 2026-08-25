const mineflayer = require('mineflayer');
const { WebSocketServer } = require('ws');

const PORT = 39871;
const wss = new WebSocketServer({ port: PORT, host: '127.0.0.1' });

console.log(`[Mineflayer Service] Running on ws://127.0.0.1:${PORT}`);

const bots = new Map(); // id -> { bot, config, lastState }

function broadcast(data) {
    const json = JSON.stringify(data);
    for (const client of wss.clients) {
        if (client.readyState === 1) {
            client.send(json);
        }
    }
}

function sendBotUpdate(id, overrides = {}) {
    const item = bots.get(id);
    if (!item) return;
    const { bot, config } = item;

    const health = bot ? bot.health || 20 : 20;
    const food = bot ? bot.food || 20 : 20;
    const pos = bot && bot.entity ? bot.entity.position : { x: 0, y: 0, z: 0 };
    const yaw = bot && bot.entity ? bot.entity.yaw : 0;
    const pitch = bot && bot.entity ? bot.entity.pitch : 0;

    const payload = {
        event: 'update',
        id: id,
        name: config.name,
        state: overrides.state || item.state || 'DISCONNECTED',
        statusMessage: overrides.statusMessage !== undefined ? overrides.statusMessage : item.statusMessage || '',
        health: health,
        food: food,
        x: pos.x || 0,
        y: pos.y || 0,
        z: pos.z || 0,
        yaw: yaw || 0,
        pitch: pitch || 0,
        ...overrides
    };

    item.state = payload.state;
    item.statusMessage = payload.statusMessage;
    broadcast(payload);
}

function createBot(config) {
    const { id, name, host, port, autoRegister, password } = config;

    if (bots.has(id)) {
        removeBot(id);
    }

    console.log(`[Mineflayer] Creating bot ${name} for ${host}:${port}`);

    const item = {
        bot: null,
        config: config,
        state: 'CONNECTING',
        statusMessage: `Подключение к ${host}:${port}...`
    };
    bots.set(id, item);
    sendBotUpdate(id);

    try {
        const bot = mineflayer.createBot({
            host: host || '127.0.0.1',
            port: port || 25565,
            username: name,
            auth: 'offline',
            checkTimeoutInterval: 60000,
            hideErrors: false
        });

        item.bot = bot;

        bot.once('login', () => {
            console.log(`[Mineflayer] Bot ${name} logged in`);
            sendBotUpdate(id, { state: 'LOGIN', statusMessage: 'Авторизован' });
        });

        bot.once('spawn', () => {
            console.log(`[Mineflayer] Bot ${name} spawned in world`);
            sendBotUpdate(id, { state: 'PLAYING', statusMessage: 'В игре!' });

            if (autoRegister) {
                setTimeout(() => {
                    if (bot && bot._client && bot._client.state === 'play') {
                        bot.chat(`/register ${password || 'Password123'} ${password || 'Password123'}`);
                        setTimeout(() => {
                            bot.chat(`/login ${password || 'Password123'}`);
                        }, 800);
                    }
                }, 1200);
            }
        });

        bot.on('health', () => {
            sendBotUpdate(id);
        });

        bot.on('move', () => {
            sendBotUpdate(id);
        });

        bot.on('kicked', (reason) => {
            let msg = 'Кикнут сервером';
            try {
                if (typeof reason === 'string') {
                    msg = reason;
                } else if (reason && reason.text) {
                    msg = reason.text;
                } else if (reason && reason.extra) {
                    msg = reason.extra.map(e => e.text || '').join('');
                } else if (reason && reason.value) {
                    msg = JSON.stringify(reason.value);
                } else {
                    msg = JSON.stringify(reason);
                }
            } catch (e) {
                msg = String(reason);
            }
            console.log(`[Mineflayer] Bot ${name} kicked: ${msg}`);
            sendBotUpdate(id, { state: 'ERROR', statusMessage: `Кик: ${msg}` });
        });

        bot.on('error', (err) => {
            const msg = err && err.message ? err.message : String(err);
            console.log(`[Mineflayer] Bot ${name} error: ${msg}`);
            sendBotUpdate(id, { state: 'ERROR', statusMessage: `Ошибка: ${msg}` });
        });

        bot.on('end', (reason) => {
            console.log(`[Mineflayer] Bot ${name} end: ${reason}`);
            sendBotUpdate(id, { state: 'DISCONNECTED', statusMessage: `Отключен: ${reason || 'соединение закрыто'}` });
        });

    } catch (err) {
        console.error(`[Mineflayer] Failed to spawn bot ${name}:`, err);
        sendBotUpdate(id, { state: 'ERROR', statusMessage: `Ошибка: ${err.message}` });
    }
}

function removeBot(id) {
    const item = bots.get(id);
    if (item) {
        if (item.bot) {
            try {
                item.bot.quit('Disconnected by client');
            } catch (e) {}
        }
        bots.delete(id);
        broadcast({ event: 'remove', id: id });
    }
}

wss.on('connection', (ws) => {
    console.log('[Mineflayer Service] Java client connected');

    // Send full current state
    for (const [id, item] of bots.entries()) {
        sendBotUpdate(id);
    }

    ws.on('message', (msgStr) => {
        try {
            const msg = JSON.parse(msgStr.toString());
            const action = msg.action;

            if (action === 'create') {
                createBot(msg);
            } else if (action === 'disconnect') {
                removeBot(msg.id);
            } else if (action === 'disconnectAll') {
                for (const id of Array.from(bots.keys())) {
                    removeBot(id);
                }
            } else if (action === 'walk') {
                const item = bots.get(msg.id);
                if (item && item.bot && item.bot.entity) {
                    const { forward, strafe, yaw, pitch, jump, sneak, sprint } = msg;
                    const bot = item.bot;

                    bot.look(yaw * (Math.PI / 180), pitch * (Math.PI / 180), true);

                    bot.setControlState('forward', forward > 0);
                    bot.setControlState('back', forward < 0);
                    bot.setControlState('left', strafe > 0);
                    bot.setControlState('right', strafe < 0);
                    bot.setControlState('jump', !!jump);
                    bot.setControlState('sneak', !!sneak);
                    bot.setControlState('sprint', !!sprint);
                }
            } else if (action === 'stopControl') {
                const item = bots.get(msg.id);
                if (item && item.bot) {
                    item.bot.clearControlStates();
                }
            } else if (action === 'moveTo') {
                const item = bots.get(msg.id);
                if (item && item.bot && item.bot.entity) {
                    const { x, y, z, yaw, pitch } = msg;
                    item.bot.look(yaw * (Math.PI / 180), pitch * (Math.PI / 180), true);
                    if (item.bot._client && item.bot._client.state === 'play') {
                        item.bot._client.write('position_look', {
                            x: x,
                            y: y,
                            z: z,
                            yaw: yaw,
                            pitch: pitch,
                            onGround: true
                        });
                    }
                }
            } else if (action === 'jump') {
                const item = bots.get(msg.id);
                if (item && item.bot) {
                    item.bot.setControlState('jump', true);
                    setTimeout(() => item.bot.setControlState('jump', false), 250);
                }
            } else if (action === 'jumpAll') {
                for (const item of bots.values()) {
                    if (item.bot) {
                        item.bot.setControlState('jump', true);
                        setTimeout(() => item.bot.setControlState('jump', false), 250);
                    }
                }
            } else if (action === 'swing') {
                const item = bots.get(msg.id);
                if (item && item.bot) {
                    item.bot.swingArm('right');
                }
            } else if (action === 'attack') {
                const item = bots.get(msg.id);
                if (item && item.bot && item.bot.entities) {
                    const target = item.bot.entities[msg.targetId];
                    if (target) {
                        item.bot.attack(target);
                    } else {
                        item.bot.swingArm('right');
                    }
                }
            } else if (action === 'chat') {
                if (msg.id) {
                    const item = bots.get(msg.id);
                    if (item && item.bot) item.bot.chat(msg.message);
                } else {
                    for (const item of bots.values()) {
                        if (item.bot) item.bot.chat(msg.message);
                    }
                }
            } else if (action === 'setSlot') {
                const item = bots.get(msg.id);
                if (item && item.bot) {
                    item.bot.setQuickBarSlot(msg.slot);
                }
            }
        } catch (e) {
            console.error('[Mineflayer Service] Error handling message:', e);
        }
    });

    ws.on('close', () => {
        console.log('[Mineflayer Service] Java client disconnected');
    });
});

process.on('SIGINT', () => {
    for (const item of bots.values()) {
        if (item.bot) item.bot.quit();
    }
    process.exit();
});

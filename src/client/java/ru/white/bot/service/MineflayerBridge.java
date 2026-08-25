package ru.white.bot.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import ru.white.bot.model.HeadlessBot;
import ru.white.module.impl.utils.BotManager;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

public class MineflayerBridge implements WebSocket.Listener {

    private static final int PORT = 39871;
    private static final String WS_URL = "ws://127.0.0.1:" + PORT;

    private static MineflayerBridge instance;

    public static synchronized MineflayerBridge get() {
        if (instance == null) {
            instance = new MineflayerBridge();
            instance.init();
        }
        return instance;
    }

    private Process nodeProcess;
    private WebSocket webSocket;
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final StringBuilder incomingMessage = new StringBuilder();

    public void init() {
        startNodeProcess();
        connectWebSocket();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopNodeProcess();
        }, "Mineflayer-Shutdown"));
    }

    public synchronized void startNodeProcess() {
        if (nodeProcess != null && nodeProcess.isAlive()) return;

        try {
            File botServiceDir = locateOrExtractBotService();

            File serverJs = new File(botServiceDir, "server.js");
            if (!serverJs.exists()) {
                System.err.println("[MineflayerBridge] server.js not found in " + botServiceDir.getAbsolutePath());
                return;
            }

            File nodeModules = new File(botServiceDir, "node_modules");
            if (!nodeModules.exists()) {
                System.out.println("[MineflayerBridge] node_modules missing, running npm install in " + botServiceDir.getAbsolutePath());
                try {
                    Process npmProc = new ProcessBuilder("npm", "install", "--no-audit", "--no-fund")
                            .directory(botServiceDir)
                            .start();
                    npmProc.waitFor();
                } catch (Exception e) {
                    System.err.println("[MineflayerBridge] npm install failed: " + e.getMessage());
                }
            }

            ProcessBuilder pb = new ProcessBuilder("node", "server.js");
            pb.directory(botServiceDir);
            pb.redirectErrorStream(true);
            nodeProcess = pb.start();

            System.out.println("[MineflayerBridge] Started Node.js Mineflayer service (PID: " + nodeProcess.pid() + ")");
        } catch (Exception e) {
            System.err.println("[MineflayerBridge] Failed to start node process: " + e.getMessage());
        }
    }

    private File locateOrExtractBotService() {
        File[] candidates = new File[] {
                new File("bot-service"),
                new File("d:/FrostixDLC/bot-service"),
                MinecraftClient.getInstance() != null && MinecraftClient.getInstance().runDirectory != null
                        ? new File(MinecraftClient.getInstance().runDirectory, "bot-service")
                        : new File("bot-service")
        };

        for (File candidate : candidates) {
            if (new File(candidate, "server.js").exists()) {
                return candidate;
            }
        }

        File targetDir = MinecraftClient.getInstance() != null && MinecraftClient.getInstance().runDirectory != null
                ? new File(MinecraftClient.getInstance().runDirectory, "bot-service")
                : new File("bot-service");

        targetDir.mkdirs();

        extractResource("/bot-service/server.js", new File(targetDir, "server.js"));
        extractResource("/bot-service/package.json", new File(targetDir, "package.json"));

        return targetDir;
    }

    private void extractResource(String resourcePath, File destination) {
        if (destination.exists()) return;
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in != null) {
                Files.copy(in, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {}
    }

    public synchronized void stopNodeProcess() {
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Stopping");
            } catch (Exception ignored) {}
        }
        if (nodeProcess != null && nodeProcess.isAlive()) {
            nodeProcess.destroyForcibly();
            nodeProcess = null;
        }
    }

    public void connectWebSocket() {
        if (webSocket != null && !webSocket.isInputClosed() && !webSocket.isOutputClosed()) {
            return;
        }
        if (!isConnecting.compareAndSet(false, true)) {
            return;
        }

        new Thread(() -> {
            try {
                Thread.sleep(1000);
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();

                CompletableFuture<WebSocket> wsFuture = client.newWebSocketBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .buildAsync(URI.create(WS_URL), this);

                this.webSocket = wsFuture.join();
                System.out.println("[MineflayerBridge] Connected to Mineflayer WebSocket bridge!");
            } catch (Exception e) {
                System.err.println("[MineflayerBridge] WebSocket connect failed, retrying in 3s: " + e.getMessage());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {}
                startNodeProcess();
            } finally {
                isConnecting.set(false);
            }
        }, "Mineflayer-WS-Connect").start();
    }

    public void sendJson(JsonObject obj) {
        if (webSocket == null || webSocket.isOutputClosed()) {
            connectWebSocket();
            return;
        }
        webSocket.sendText(obj.toString(), true);
    }

    public void createBot(String id, String name, String host, int port, boolean autoRegister, String password) {
        ensureConnected();
        JsonObject obj = new JsonObject();
        obj.addProperty("action", "create");
        obj.addProperty("id", id);
        obj.addProperty("name", name);
        obj.addProperty("host", host);
        obj.addProperty("port", port);
        obj.addProperty("autoRegister", autoRegister);
        obj.addProperty("password", password);
        sendJson(obj);
    }

    public void disconnectBot(String id) {
        ensureConnected();
        JsonObject obj = new JsonObject();
        obj.addProperty("action", "disconnect");
        obj.addProperty("id", id);
        sendJson(obj);
    }

    public void disconnectAll() {
        ensureConnected();
        JsonObject obj = new JsonObject();
        obj.addProperty("action", "disconnectAll");
        sendJson(obj);
    }

    public void walk(String id, double forward, double strafe, float yaw, float pitch, boolean jump, boolean sneak, boolean sprint) {
        JsonObject obj = new JsonObject();
        obj.addProperty("action", "walk");
        obj.addProperty("id", id);
        obj.addProperty("forward", forward);
        obj.addProperty("strafe", strafe);
        obj.addProperty("yaw", yaw);
        obj.addProperty("pitch", pitch);
        obj.addProperty("jump", jump);
        obj.addProperty("sneak", sneak);
        obj.addProperty("sprint", sprint);
        sendJson(obj);
    }

    public void stopControl(String id) {
        JsonObject obj = new JsonObject();
        obj.addProperty("action", "stopControl");
        obj.addProperty("id", id);
        sendJson(obj);
    }

    public void moveTo(String id, double x, double y, double z, float yaw, float pitch) {
        JsonObject obj = new JsonObject();
        obj.addProperty("action", "moveTo");
        obj.addProperty("id", id);
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("z", z);
        obj.addProperty("yaw", yaw);
        obj.addProperty("pitch", pitch);
        sendJson(obj);
    }

    public void jump(String id) {
        JsonObject obj = new JsonObject();
        obj.addProperty("action", "jump");
        obj.addProperty("id", id);
        sendJson(obj);
    }

    public void jumpAll() {
        JsonObject obj = new JsonObject();
        obj.addProperty("action", "jumpAll");
        sendJson(obj);
    }

    public void swing(String id) {
        JsonObject obj = new JsonObject();
        obj.addProperty("action", "swing");
        obj.addProperty("id", id);
        sendJson(obj);
    }

    public void attack(String id, int targetEntityId) {
        JsonObject obj = new JsonObject();
        obj.addProperty("action", "attack");
        obj.addProperty("id", id);
        obj.addProperty("targetId", targetEntityId);
        sendJson(obj);
    }

    public void sendChat(String id, String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("action", "chat");
        if (id != null) obj.addProperty("id", id);
        obj.addProperty("message", message);
        sendJson(obj);
    }

    public void setSlot(String id, int slot) {
        JsonObject obj = new JsonObject();
        obj.addProperty("action", "setSlot");
        obj.addProperty("id", id);
        obj.addProperty("slot", slot);
        sendJson(obj);
    }

    private void ensureConnected() {
        if (webSocket == null || webSocket.isOutputClosed()) {
            startNodeProcess();
            connectWebSocket();
        }
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        incomingMessage.append(data);
        if (last) {
            String fullMsg = incomingMessage.toString();
            incomingMessage.setLength(0);
            handleIncomingJson(fullMsg);
        }
        return WebSocket.Listener.super.onText(webSocket, data, last);
    }

    private void handleIncomingJson(String jsonStr) {
        try {
            JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
            String event = obj.has("event") ? obj.get("event").getAsString() : "";

            if ("update".equals(event)) {
                String id = obj.get("id").getAsString();
                BotManager manager = BotManager.get();
                if (manager != null) {
                    for (HeadlessBot bot : manager.getBots()) {
                        if (bot.getId().equals(id)) {
                            if (obj.has("state")) {
                                String s = obj.get("state").getAsString();
                                try {
                                    bot.setState(HeadlessBot.BotState.valueOf(s));
                                } catch (Exception ignored) {}
                            }
                            if (obj.has("statusMessage")) {
                                bot.setStatusMessage(obj.get("statusMessage").getAsString());
                            }
                            if (obj.has("health")) {
                                bot.setHealth(obj.get("health").getAsFloat());
                            }
                            if (obj.has("food")) {
                                bot.setFood(obj.get("food").getAsInt());
                            }
                            if (obj.has("x")) {
                                bot.setX(obj.get("x").getAsDouble());
                                bot.setY(obj.get("y").getAsDouble());
                                bot.setZ(obj.get("z").getAsDouble());
                                bot.setYaw(obj.get("yaw").getAsFloat());
                                bot.setPitch(obj.get("pitch").getAsFloat());
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[MineflayerBridge] Error parsing incoming json: " + e.getMessage());
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        WebSocket.Listener.super.onOpen(webSocket);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        System.out.println("[MineflayerBridge] WebSocket closed: " + reason);
        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        System.err.println("[MineflayerBridge] WebSocket error: " + error.getMessage());
    }
}

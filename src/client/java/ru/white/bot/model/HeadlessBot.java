package ru.white.bot.model;

import lombok.Getter;
import lombok.Setter;
import ru.white.bot.service.MineflayerBridge;

import java.util.UUID;

public class HeadlessBot {

    public enum BotState {
        DISCONNECTED("Отключен", 0xFFFF5555),
        CONNECTING("Подключение...", 0xFFFFAA00),
        LOGIN("Авторизация...", 0xFFFFFF55),
        CONFIGURATION("Конфигурация...", 0xFF55FFFF),
        PLAYING("В игре", 0xFF55FF55),
        ERROR("Ошибка", 0xFFFF3333);

        @Getter private final String display;
        @Getter private final int color;

        BotState(String display, int color) {
            this.display = display;
            this.color = color;
        }
    }

    @Getter private final String id;
    @Getter private final String name;
    @Getter private final UUID uuid;
    @Getter private final String host;
    @Getter private final int port;

    @Getter @Setter private volatile BotState state = BotState.DISCONNECTED;
    @Getter @Setter private volatile String statusMessage = "";

    @Getter @Setter private volatile float health = 20.0F;
    @Getter @Setter private volatile int food = 20;
    @Getter @Setter private volatile long ping = 0;
    @Getter @Setter private volatile double x = 0;
    @Getter @Setter private volatile double y = 0;
    @Getter @Setter private volatile double z = 0;
    @Getter @Setter private volatile float yaw = 0;
    @Getter @Setter private volatile float pitch = 0;
    @Getter @Setter private volatile boolean onGround = true;
    @Getter @Setter private volatile int selectedSlot = 0;

    @Getter @Setter private boolean following = false;
    @Getter @Setter private boolean attacking = false;
    @Getter @Setter private boolean autoRegister = true;
    @Getter @Setter private String password = "Password123";

    public HeadlessBot(String name, String host, int port) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.host = host;
        this.port = port;
        this.uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());
    }

    public void connect() {
        state = BotState.CONNECTING;
        statusMessage = "Запуск Mineflayer...";
        MineflayerBridge.get().createBot(id, name, host, port, autoRegister, password);
    }

    public void disconnect() {
        state = BotState.DISCONNECTED;
        statusMessage = "Отключен пользователем";
        MineflayerBridge.get().disconnectBot(id);
    }

    public boolean isConnected() {
        return state == BotState.PLAYING;
    }

    public void sendChat(String message) {
        MineflayerBridge.get().sendChat(id, message);
    }

    public void moveTo(double targetX, double targetY, double targetZ, float targetYaw, float targetPitch, boolean onGroundState) {
        this.x = targetX;
        this.y = targetY;
        this.z = targetZ;
        this.yaw = targetYaw;
        this.pitch = targetPitch;
        this.onGround = onGroundState;
        MineflayerBridge.get().moveTo(id, targetX, targetY, targetZ, targetYaw, targetPitch);
    }

    public void walk(double forward, double strafe, float cameraYaw, float cameraPitch, boolean jump, boolean sneak, boolean sprint) {
        MineflayerBridge.get().walk(id, forward, strafe, cameraYaw, cameraPitch, jump, sneak, sprint);
    }

    public void stopControl() {
        MineflayerBridge.get().stopControl(id);
    }

    public void jump() {
        MineflayerBridge.get().jump(id);
    }

    public void swing() {
        MineflayerBridge.get().swing(id);
    }

    public void attack(int targetEntityId) {
        MineflayerBridge.get().attack(id, targetEntityId);
    }

    public void setSlot(int slot) {
        this.selectedSlot = Math.max(0, Math.min(8, slot));
        MineflayerBridge.get().setSlot(id, this.selectedSlot);
    }
}

package ru.white.module.impl.utils;

import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import ru.white.bot.PotatoGraphics;
import ru.white.bot.model.HeadlessBot;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ButtonSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.module.impl.combat.AttackAura;
import ru.white.screen.BotManagerScreen;
import ru.white.utils.math.MathUtil;
import ru.white.utils.other.Instance;
import ru.white.utils.render.RenderUtil;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@ModuleInfo(
        name = "Bot Manager",
        desc = "Управление ботами прямо из одного окна",
        category = Category.OTHER,
        key = GLFW.GLFW_KEY_B
)
public class BotManager extends Module {

    public static BotManager get() {
        return Instance.get(BotManager.class);
    }

    public ButtonSetting openGui = new ButtonSetting(this, "Открыть меню ботов", () -> {
        mc.setScreen(new BotManagerScreen(mc.currentScreen));
    });

    public BooleanSetting potatoGraphics = new BooleanSetting(this, "Потато графика", false);
    public BooleanSetting globalFollow = new BooleanSetting(this, "Следовать за мной", true);
    public BooleanSetting globalAttack = new BooleanSetting(this, "Ассист в бою", true);
    public SliderSetting followDistance = new SliderSetting(this, "Дистанция следования", 2.5F, 1.0F, 8.0F, 0.5F);
    public BooleanSetting autoRegister = new BooleanSetting(this, "Авто-регистрация", true);

    private final List<HeadlessBot> bots = new CopyOnWriteArrayList<>();
    private int tickCounter = 0;

    public List<HeadlessBot> getBots() {
        return bots;
    }

    public HeadlessBot createBot(String name, String host, int port) {
        HeadlessBot bot = new HeadlessBot(name, host, port);
        bot.setFollowing(globalFollow.getValue());
        bot.setAttacking(globalAttack.getValue());
        bot.setAutoRegister(autoRegister.getValue());
        bots.add(bot);
        bot.connect();
        return bot;
    }

    public void createRandomBots(int count, String baseName, String host, int port) {
        for (int i = 0; i < count; i++) {
            String name = baseName + "_" + (int) MathUtil.random(100, 999);
            createBot(name, host, port);
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {}
        }
    }

    public void removeBot(HeadlessBot bot) {
        if (bot != null) {
            bot.disconnect();
            bots.remove(bot);
        }
    }

    public void disconnectAll() {
        for (HeadlessBot bot : bots) {
            bot.disconnect();
        }
        bots.clear();
    }

    public void followAll(boolean follow) {
        globalFollow.set(follow);
        for (HeadlessBot bot : bots) {
            bot.setFollowing(follow);
        }
    }

    public void attackAll(boolean attack) {
        globalAttack.set(attack);
        for (HeadlessBot bot : bots) {
            bot.setAttacking(attack);
        }
    }

    public void jumpAll() {
        for (HeadlessBot bot : bots) {
            if (bot.isConnected()) {
                bot.jump();
            }
        }
    }

    public void chatAll(String message) {
        for (HeadlessBot bot : bots) {
            if (bot.isConnected()) {
                bot.sendChat(message);
            }
        }
    }

    public String getCurrentServerHost() {
        if (mc.getCurrentServerEntry() != null) {
            String addr = mc.getCurrentServerEntry().address;
            if (addr.contains(":")) {
                return addr.split(":")[0];
            }
            return addr;
        }
        return "localhost";
    }

    public int getCurrentServerPort() {
        if (mc.getCurrentServerEntry() != null) {
            String addr = mc.getCurrentServerEntry().address;
            if (addr.contains(":")) {
                try {
                    return Integer.parseInt(addr.split(":")[1]);
                } catch (Exception ignored) {}
            }
        }
        return 25565;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (mc.currentScreen == null) {
            mc.setScreen(new BotManagerScreen(null));
        }
    }

    @EventHandler
    public void onUpdate(EventUpdate event) {
        PotatoGraphics.setEnabled(potatoGraphics.getValue());

        tickCounter++;
        if (mc.player == null || bots.isEmpty()) return;

        Vec3d playerPos = mc.player.getEntityPos();
        float playerYaw = mc.player.getYaw();
        float playerPitch = mc.player.getPitch();

        int botIndex = 0;
        int totalConnected = 0;
        for (HeadlessBot bot : bots) {
            if (bot.isConnected()) totalConnected++;
        }

        for (HeadlessBot bot : bots) {
            if (!bot.isConnected()) continue;

            // 1. Поведение следования
            if (bot.isFollowing() || globalFollow.getValue()) {
                // Распределяем ботов по дуге за спиной игрока
                double angleOffset = totalConnected > 1
                        ? (botIndex - (totalConnected - 1) / 2.0) * 0.7
                        : 0;

                double targetAngle = Math.toRadians(playerYaw + 180) + angleOffset;
                double dist = followDistance.getValue();
                double targetX = playerPos.x + Math.sin(-targetAngle) * dist;
                double targetZ = playerPos.z + Math.cos(-targetAngle) * dist;
                double targetY = playerPos.y;

                double dx = targetX - bot.getX();
                double dy = targetY - bot.getY();
                double dz = targetZ - bot.getZ();
                double distToTarget = Math.sqrt(dx * dx + dz * dz);

                if (distToTarget > 0.15) {
                    double speed = Math.min(0.28, distToTarget);
                    double moveX = bot.getX() + (dx / distToTarget) * speed;
                    double moveZ = bot.getZ() + (dz / distToTarget) * speed;

                    // Угол взгляда на игрока
                    float lookYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float lookPitch = (float) Math.toDegrees(-Math.atan2(dy, distToTarget));

                    boolean onGround = Math.abs(dy) < 0.2;
                    bot.moveTo(moveX, targetY, moveZ, lookYaw, lookPitch, onGround);
                } else {
                    bot.moveTo(bot.getX(), targetY, bot.getZ(), playerYaw, playerPitch, true);
                }
            }

            // 2. Поведение ассиста в атаке
            if ((bot.isAttacking() || globalAttack.getValue()) && tickCounter % 4 == 0) {
                if (AttackAura.target != null && AttackAura.target.isAlive()) {
                    double distToEnemy = Math.sqrt(
                            Math.pow(bot.getX() - AttackAura.target.getX(), 2) +
                            Math.pow(bot.getY() - AttackAura.target.getY(), 2) +
                            Math.pow(bot.getZ() - AttackAura.target.getZ(), 2)
                    );
                    if (distToEnemy <= 4.5) {
                        bot.attack(AttackAura.target.getId());
                    }
                }
            }

            botIndex++;
        }
    }
}

package ru.white.module.impl.utils;

import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import ru.white.bot.PotatoGraphics;
import ru.white.bot.model.HeadlessBot;
import ru.white.manager.event_impl.*;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ButtonSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.module.impl.combat.AttackAura;
import ru.white.screen.BotManagerScreen;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.MathUtil;
import ru.white.utils.other.Instance;
import ru.white.utils.render.Draw;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@ModuleInfo(
        name = "Bot Manager",
        desc = "Управление ботами и игра от их лица",
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

    public BooleanSetting mirrorControl = new BooleanSetting(this, "Зеркальное управление", false);
    public BooleanSetting potatoGraphics = new BooleanSetting(this, "Потато графика", false);
    public BooleanSetting globalFollow = new BooleanSetting(this, "Следовать за мной", true);
    public BooleanSetting globalAttack = new BooleanSetting(this, "Ассист в бою", true);
    public SliderSetting followDistance = new SliderSetting(this, "Дистанция следования", 2.5F, 1.0F, 8.0F, 0.5F);
    public BooleanSetting autoRegister = new BooleanSetting(this, "Авто-регистрация", true);

    private final List<HeadlessBot> bots = new CopyOnWriteArrayList<>();
    private HeadlessBot possessedBot = null;
    private int tickCounter = 0;

    public List<HeadlessBot> getBots() {
        return bots;
    }

    public HeadlessBot getPossessedBot() {
        return possessedBot;
    }

    public boolean isPossessing() {
        return possessedBot != null && possessedBot.isConnected();
    }

    public void possess(HeadlessBot bot) {
        if (bot != null && bot.isConnected()) {
            this.possessedBot = bot;
            if (mc.player != null) {
                bot.setYaw(mc.player.getYaw());
                bot.setPitch(mc.player.getPitch());
            }
        }
    }

    public void unpossess() {
        this.possessedBot = null;
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
            if (bot == possessedBot) unpossess();
            bot.disconnect();
            bots.remove(bot);
        }
    }

    public void disconnectAll() {
        unpossess();
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
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            java.net.SocketAddress socketAddress = mc.getNetworkHandler().getConnection().getAddress();
            if (socketAddress instanceof java.net.InetSocketAddress inet) {
                return inet.getHostString();
            }
        }
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
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            java.net.SocketAddress socketAddress = mc.getNetworkHandler().getConnection().getAddress();
            if (socketAddress instanceof java.net.InetSocketAddress inet) {
                return inet.getPort();
            }
        }
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

    @Override
    public void onDisable() {
        unpossess();
        super.onDisable();
    }

    @EventHandler
    public void onCameraPosition(CameraPositionEvent e) {
        if (isPossessing()) {
            e.setPos(new Vec3d(possessedBot.getX(), possessedBot.getY() + 1.62, possessedBot.getZ()));
            mc.options.setPerspective(Perspective.FIRST_PERSON);
        }
    }

    @EventHandler
    public void onMove(MoveEvent e) {
        if (isPossessing()) {
            e.setMovement(Vec3d.ZERO);
        }
    }

    @EventHandler
    public void onInput(InputEvent e) {
        if (!isPossessing()) return;

        int f = e.forward();
        float s = e.sideways();
        boolean jump = e.getInput().jump();
        boolean sneak = e.getInput().sneak();
        boolean sprint = e.getInput().sprint();

        if (mc.player != null) {
            float yaw = mc.player.getYaw();
            float pitch = mc.player.getPitch();

            if (f != 0 || s != 0 || jump) {
                possessedBot.walk(f, s, yaw, pitch, jump, sneak, sprint);
            } else {
                possessedBot.moveTo(possessedBot.getX(), possessedBot.getY(), possessedBot.getZ(), yaw, pitch, true);
            }
        }

        e.inputNone();
    }

    @EventHandler
    public void onKey(EventKey event) {
        int key = event.getKey();

        if (key == GLFW.GLFW_KEY_F8) {
            if (isPossessing()) {
                unpossess();
            } else if (!bots.isEmpty()) {
                for (HeadlessBot b : bots) {
                    if (b.isConnected()) {
                        possess(b);
                        break;
                    }
                }
            }
            return;
        }

        if (isPossessing()) {
            if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
                possessedBot.setSlot(key - GLFW.GLFW_KEY_1);
            }
        }
    }

    @EventHandler
    public void onDisplay(EventDisplay e) {
        if (!isPossessing() || mc.player == null) return;

        float targetScale = 2.0F;
        float currentScale = (float) mc.getWindow().getScaleFactor();
        float scaleFix = targetScale / currentScale;

        int screenWidth = (int) (mc.getWindow().getScaledWidth() / scaleFix);

        float bannerW = 280;
        float bannerH = 26;
        float bannerX = (screenWidth - bannerW) / 2.0F;
        float bannerY = 12;

        Draw.blur(bannerX, bannerY, bannerW, bannerH, 1.0F, 6, ColorUtil.getColor(15, 18, 26, 220));
        Draw.rect(bannerX, bannerY, bannerW, bannerH, ColorUtil.getColor(20, 24, 34, 200), 6);
        Draw.outline(bannerX, bannerY, bannerW, bannerH, 1.0F, ColorUtil.getColor(80, 180, 255, 160), 6);

        Font bold = Fonts.sf_bold;
        Font regular = Fonts.sf_regular;

        bold.draw("🎮 ВЫ УПРАВЛЯЕТЕ БОТОМ: " + possessedBot.getName(), bannerX + 10, bannerY + 6, 7.5F, ColorUtil.getColor(255, 255, 255, 255));
        regular.draw("[F8 / ESC] Вернуться к основе", bannerX + 10, bannerY + 16, 6.0F, ColorUtil.getColor(160, 180, 210, 220));
        bold.draw("HP: " + (int) possessedBot.getHealth(), bannerX + bannerW - 46, bannerY + 10, 7.0F, ColorUtil.getColor(255, 80, 90, 240));

        Draw.flush();
    }

    @EventHandler
    public void onUpdate(EventUpdate event) {
        PotatoGraphics.setEnabled(potatoGraphics.getValue());

        tickCounter++;
        if (mc.player == null || bots.isEmpty()) return;

        Vec3d playerPos = mc.player.getEntityPos();
        float playerYaw = mc.player.getYaw();
        float playerPitch = mc.player.getPitch();

        // Зеркальный режим
        if (mirrorControl.getValue() && !isPossessing()) {
            for (HeadlessBot bot : bots) {
                if (!bot.isConnected()) continue;
                bot.moveTo(playerPos.x, playerPos.y, playerPos.z, playerYaw, playerPitch, mc.player.isOnGround());
                if (mc.player.handSwinging) {
                    bot.swing();
                }
            }
            return;
        }

        int botIndex = 0;
        int totalConnected = 0;
        for (HeadlessBot bot : bots) {
            if (bot.isConnected()) totalConnected++;
        }

        for (HeadlessBot bot : bots) {
            if (!bot.isConnected()) continue;
            if (bot == possessedBot) continue; // Не трогаем бота, которым управляет игрок

            // 1. Поведение следования
            if (bot.isFollowing() || globalFollow.getValue()) {
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

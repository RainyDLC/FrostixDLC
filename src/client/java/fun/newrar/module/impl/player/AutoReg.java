package fun.newrar.module.impl.player;

import fun.newrar.manager.event_impl.EventPacket;
import fun.newrar.manager.event_impl.EventUpdate;
import fun.newrar.manager.event_impl.WorldLoadEvent;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BooleanSetting;
import fun.newrar.module.api.settings.impl.ModeSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.module.api.settings.impl.StringSetting;
import fun.newrar.utils.notification.NotificationManager;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.text.Text;

@ModuleInfo(
        name = "Auto Reg",
        category = Category.PLAYER,
        desc = "Автоматическая регистрация и авторизация на сервере с настраиваемым паролем"
)
public class AutoReg extends Module {
    public final StringSetting password = new StringSetting(this, "Пароль", "RainyDLC123");
    public final ModeSetting mode = new ModeSetting(this, "Команда рег", "/reg", "/register");
    public final BooleanSetting repeat = new BooleanSetting("Повторять пароль", true);
    public final BooleanSetting autoLogin = new BooleanSetting("Авто-логин (/login)", true);
    public final ModeSetting loginCmd = new ModeSetting(this, "Команда логин", "/login", "/l")
            .setVisible(autoLogin::getValue);
    public final SliderSetting delay = new SliderSetting(this, "Задержка (мс)", 500, 50, 2000, 50);

    private String pendingCommand = null;
    private long executeTime = 0L;
    private long cooldownUntil = 0L;

    public AutoReg() {
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        pendingCommand = null;
        executeTime = 0L;
        cooldownUntil = 0L;
    }

    @EventHandler
    public void onPacket(EventPacket packetEvent) {
        if (mc.player == null || mc.world == null) return;
        if (System.currentTimeMillis() < cooldownUntil) return;
        if (pendingCommand != null) return;

        var packet = packetEvent.getPacket();
        Text text = null;

        if (packet instanceof GameMessageS2CPacket m) {
            text = m.content();
        } else if (packet instanceof OverlayMessageS2CPacket m) {
            text = m.text();
        } else if (packet instanceof TitleS2CPacket m) {
            text = m.text();
        } else if (packet instanceof SubtitleS2CPacket m) {
            text = m.text();
        }

        if (text == null) return;

        String raw = text.getString();
        if (raw == null || raw.trim().isEmpty()) return;

        String clean = raw.replaceAll("§.", "").toLowerCase();

        if (clean.contains("успешно") || clean.contains("success") || clean.contains("вы вошли") || clean.contains("добро пожаловать")) {
            return;
        }

        if (isRegisterPrompt(clean)) {
            String pass = password.getValue();
            if (pass == null || pass.isEmpty()) pass = "RainyDLC123";

            String cmd = mode.getValue() + " " + pass + (repeat.getValue() ? " " + pass : "");
            queueCommand(cmd);
            return;
        }

        if (autoLogin.getValue() && isLoginPrompt(clean)) {
            String pass = password.getValue();
            if (pass == null || pass.isEmpty()) pass = "RainyDLC123";

            String cmd = loginCmd.getValue() + " " + pass;
            queueCommand(cmd);
        }
    }

    private void queueCommand(String cmd) {
        pendingCommand = cmd;
        long delayMs = delay.getValue() != null ? delay.getValue().longValue() : 500L;
        executeTime = System.currentTimeMillis() + delayMs;
        cooldownUntil = executeTime + 3500L;
    }

    @EventHandler
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        if (pendingCommand != null && System.currentTimeMillis() >= executeTime) {
            String cmd = pendingCommand;
            pendingCommand = null;

            if (mc.player.networkHandler != null) {
                if (cmd.startsWith("/")) {
                    mc.player.networkHandler.sendChatCommand(cmd.substring(1));
                } else {
                    mc.player.networkHandler.sendChatMessage(cmd);
                }
                String prefix = cmd.split(" ")[0];
                NotificationManager.send("AutoReg: отправлен " + prefix + " ***", NotificationManager.Type.INFO, "AutoReg");
            }
        }
    }

    private boolean isRegisterPrompt(String s) {
        return s.contains("/reg")
                || s.contains("/register")
                || s.contains("зарегистрируйтесь")
                || s.contains("зарегистрироваться")
                || s.contains("регистрация")
                || s.contains("придумайте пароль")
                || s.contains("введите пароль дважды")
                || s.contains("create a password")
                || s.contains("register your account")
                || s.contains("please register");
    }

    private boolean isLoginPrompt(String s) {
        return s.contains("/login")
                || s.contains("/l ")
                || s.contains("авторизуйтесь")
                || s.contains("войдите в аккаунт")
                || s.contains("введите /login")
                || s.contains("введите /l")
                || s.contains("введите свой пароль")
                || s.contains("введите пароль")
                || s.contains("please login");
    }
}

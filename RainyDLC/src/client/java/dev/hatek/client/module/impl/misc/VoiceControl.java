package dev.hatek.client.module.impl.misc;

import dev.hatek.client.feature.voice.VoiceCommands;
import dev.hatek.client.feature.voice.VoiceEngine;
import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.SliderSetting;
import dev.hatek.client.module.setting.TextSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class VoiceControl extends Module {
    private static final String DEFAULT_MODEL = "D:/whisper.cpp/models/ggml-base.bin";

    private static VoiceControl instance;

    private final TextSetting model = new TextSetting("Model", DEFAULT_MODEL, "path to ggml-*.bin");

    private final TextSetting device = new TextSetting("Microphone", "", "part of the name");

    private final SliderSetting sensitivity = new SliderSetting("Sensitivity", 2.2, 1.2, 6.0, 0.1);

    private final BoolSetting echo = new BoolSetting("Echo Heard", true);

    private final BoolSetting wake = new BoolSetting("Wake Word", true);

    private String lastStatus = "";
    private boolean announcedDevice;
    private boolean started;

    public VoiceControl() {
        super("VoiceControl", "Say \"Hatek\" and an order - offline speech, no limits",
                Category.MISC);
        instance = this;
        keybind(GLFW.GLFW_KEY_J);
        with(this.model, this.device, this.sensitivity, this.echo, this.wake);
    }

    public static VoiceControl instance() {
        return instance;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    @Override
    protected void onEnable() {
        Path model = VoiceEngine.resolveModel(
                this.model.value().isBlank() ? DEFAULT_MODEL : this.model.value());
        if (model == null) {
            say("Модель не найдена. Скачай ggml-base.bin: huggingface.co/ggerganov/whisper.cpp — "
                    + "и укажи путь в Model, или положи в папку hatek-voice в .minecraft");
            setEnabled(false);
            return;
        }
        VoiceEngine.sensitivity((float) this.sensitivity.value());
        VoiceCommands.wakeRequired(this.wake.value());
        VoiceEngine.start(model.toString(), this.device.value());
        this.lastStatus = "";
        this.announcedDevice = false;
        this.started = true;
        say("Загружаю модель: " + model.getFileName());
    }

    @Override
    protected void onDisable() {
        if (!Jarvis.active()) {
            VoiceEngine.stop();
        }
        if (this.started) {
            this.started = false;
            say("Голос выключен");
        }
    }

    public void onTick() {
        if (!isEnabled()) {
            return;
        }
        VoiceEngine.sensitivity((float) this.sensitivity.value());
        VoiceCommands.wakeRequired(this.wake.value());

        String status = VoiceEngine.status();
        if (!status.equals(this.lastStatus)) {
            this.lastStatus = status;
            if (!status.equals("listening") && !status.equals("recognising")) {
                say("Голос: " + status);
            }
            if (status.equals("listening") && !this.announcedDevice) {
                this.announcedDevice = true;
                say("Микрофон: " + VoiceEngine.device());
                if (this.echo.value()) {
                    listDevices();
                }
                say("Слушаю. Скажи: Хатек, включи ауру");
            }
            if (!VoiceEngine.running()) {
                setEnabled(false);
                return;
            }
        }

        String heard;
        for (String phrase : VoiceEngine.drain()) {
            if (Jarvis.active() && VoiceCommands.isJarvisPhrase(phrase)) {
                VoiceEngine.requeue(phrase);
                continue;
            }
            heard = phrase;
            String reply = VoiceCommands.handle(heard);
            if (this.echo.value()) {
                say("§8услышал: §7" + heard);
            }
            if (reply != null) {
                say(reply);
            } else if (!this.echo.value()) {
                say("§8не для меня: §7" + heard);
            }
        }
    }

    public void listDevices() {
        List<String> devices = VoiceEngine.devices();
        say("Микрофоны (" + devices.size() + "):");
        for (String name : devices) {
            say("§8- §7" + name);
        }
    }

    public static void say(String text) {
        Minecraft mc = mc();
        if (mc.level != null && mc.gui != null) {
            mc.gui.chatListener().handleSystemMessage(
                    Component.literal("§8[§fHatek§8] §7" + text), false);
            return;
        }
        if (mc.gui != null) {
            SystemToast.addOrUpdate(mc.gui.toastManager(),
                    SystemToast.SystemToastId.NARRATOR_TOGGLE,
                    Component.literal("Hatek"), Component.literal(text.replaceAll("§.", "")));
        }
    }
}

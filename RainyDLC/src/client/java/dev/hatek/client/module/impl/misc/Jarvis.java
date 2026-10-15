package dev.hatek.client.module.impl.misc;

import dev.hatek.client.feature.voice.JarvisSpeech;
import dev.hatek.client.feature.voice.VoiceCommands;
import dev.hatek.client.feature.voice.VoiceEngine;
import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ButtonSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import dev.hatek.client.module.setting.TextSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Jarvis — голосовой помощник. Скажи «Джарвис, открой гуи» — ответит
 * «Слушаюсь, сэр» живым голосом и откроет меню. «Джарвис, включи автомейс» —
 * включит AutoMace. Микрофон и динамик выбираются в настройках.
 */
public final class Jarvis extends Module {
    private static final String DEFAULT_MODEL = "D:/whisper.cpp/models/ggml-base.bin";
    private static final String DEFAULT_DEVICE = "По умолчанию";

    private static Jarvis instance;

    private final TextSetting model = new TextSetting("Model", DEFAULT_MODEL, "path to ggml-*.bin");
    private final ModeSetting microphone = new ModeSetting("Микрофон", 0, micOptions());
    private final ModeSetting speaker = new ModeSetting("Динамик", 0, speakerOptions());
    private final ButtonSetting refresh = new ButtonSetting("Устройства", "Обновить",
            this::refreshDevices);
    private final BoolSetting voice = new BoolSetting("Голос", true);
    private final BoolSetting echo = new BoolSetting("Echo Heard", true);
    private final SliderSetting sensitivity = new SliderSetting("Sensitivity", 2.2, 1.2, 6.0, 0.1);

    private String lastStatus = "";
    private boolean announced;

    public Jarvis() {
        super("Jarvis", "Голосовой помощник: «Джарвис, открой гуи»", Category.MISC);
        instance = this;
        with(this.model, this.microphone, this.speaker, this.refresh, this.voice,
                this.echo, this.sensitivity);
    }

    public static Jarvis instance() {
        return instance;
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static String[] micOptions() {
        List<String> out = new ArrayList<>();
        out.add(DEFAULT_DEVICE);
        out.addAll(VoiceEngine.devices());
        return out.toArray(new String[0]);
    }

    private static String[] speakerOptions() {
        List<String> out = new ArrayList<>();
        out.add(DEFAULT_DEVICE);
        out.addAll(JarvisSpeech.playbackDevices());
        return out.toArray(new String[0]);
    }

    private void refreshDevices() {
        this.microphone.setOptions(List.of(micOptions()));
        this.speaker.setOptions(List.of(speakerOptions()));
        say("Микрофоны: " + (this.microphone.options().size() - 1)
                + ", динамики: " + (this.speaker.options().size() - 1));
    }

    private String micChoice() {
        String value = this.microphone.value();
        return DEFAULT_DEVICE.equals(value) ? "" : value;
    }

    private String speakerChoice() {
        String value = this.speaker.value();
        return DEFAULT_DEVICE.equals(value) ? "" : value;
    }

    private void speak(String text) {
        if (this.voice.value() && text != null) {
            JarvisSpeech.speak(text, speakerChoice());
        }
    }

    @Override
    protected void onEnable() {
        String path = this.model.value().isBlank() ? DEFAULT_MODEL : this.model.value().trim();
        if (!Files.isRegularFile(Path.of(path))) {
            say("Модель не найдена: " + path);
            setEnabled(false);
            return;
        }
        VoiceEngine.sensitivity((float) this.sensitivity.value());
        VoiceEngine.start(path, micChoice());
        this.lastStatus = "";
        this.announced = false;
        say("Загружаю модель...");
    }

    @Override
    protected void onDisable() {
        if (!otherVoiceRunning()) {
            VoiceEngine.stop();
        }
        JarvisSpeech.stop();
        say("Джарвис отключён");
    }

    private static boolean otherVoiceRunning() {
        VoiceControl voice = VoiceControl.instance();
        return voice != null && voice.isEnabled();
    }

    @Override
    public void onClientTick() {
        if (!isEnabled()) {
            return;
        }
        VoiceEngine.sensitivity((float) this.sensitivity.value());

        String status = VoiceEngine.status();
        if (!status.equals(this.lastStatus)) {
            this.lastStatus = status;
            if (!status.equals("listening") && !status.equals("recognising")) {
                say("Джарвис: " + status);
            }
            if (status.equals("listening") && !this.announced) {
                this.announced = true;
                say("Микрофон: " + VoiceEngine.device());
                say("Слушаю. Скажи: Джарвис, открой гуи");
                speak("Джарвис на связи, сэр");
            }
            if (!VoiceEngine.running()) {
                setEnabled(false);
                return;
            }
        }

        for (String heard : VoiceEngine.drain()) {
            if (otherVoiceRunning() && VoiceCommands.isHatekPhrase(heard)
                    && !VoiceCommands.isJarvisPhrase(heard)) {
                VoiceEngine.requeue(heard);
                continue;
            }
            VoiceCommands.JarvisResult result = VoiceCommands.handleJarvis(heard);
            if (result == null) {
                if (this.echo.value()) {
                    say("§8не для меня: §7" + heard);
                }
                continue;
            }
            if (this.echo.value()) {
                say("§8услышал: §7" + heard);
            }
            say(result.chat());
            if (result.action() != null) {
                try {
                    result.action().run();
                } catch (Throwable ignored) {
                }
            }
            speak(result.say());
        }
    }

    public static void say(String text) {
        Minecraft mc = mc();
        if (mc.level != null && mc.gui != null) {
            mc.gui.chatListener().handleSystemMessage(
                    Component.literal("§8[§bДжарвис§8] §7" + text), false);
            return;
        }
        if (mc.gui != null) {
            SystemToast.addOrUpdate(mc.gui.toastManager(),
                    SystemToast.SystemToastId.NARRATOR_TOGGLE,
                    Component.literal("Jarvis"), Component.literal(text.replaceAll("§.", "")));
        }
    }
}

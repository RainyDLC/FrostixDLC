package mixin;

import net.minecraft.client.input.KeyInput;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rtx.kimiko.api.events.EventBus;
import rtx.kimiko.api.events.impl.input.KeyPressEvent;
import rtx.kimiko.api.modules.ModuleManager;
import rtx.kimiko.api.modules.impl.Interface.ClickGui;
import rtx.kimiko.api.ui.UI;
import rtx.kimiko.api.ui.horizon.HorizonGui;
import rtx.kimiko.api.voice.VoiceBindManager;
import rtx.kimiko.utils.sounds.Sounds;

@Mixin(value={Keyboard.class})
public abstract class KeyboardHandlerMixin {
    @Inject(method={"onKey"}, at={@At(value="HEAD")}, cancellable=true)
    private void kimiko$onKeyPress(long window, int action, KeyInput keyEvent, CallbackInfo ci) {
        if (action == 1) {
            try {
                VoiceBindManager.INSTANCE.noteInput();
            } catch (Throwable ignored) {}

            int bind = 344;
            ClickGui clickGui = null;
            try {
                clickGui = ClickGui.getInstance();
                if (clickGui == null && ModuleManager.get() != null) {
                    clickGui = ModuleManager.get().get(ClickGui.class);
                }
                if (clickGui != null && clickGui.getBind() != null && clickGui.getBind().getCode() > 0) {
                    bind = clickGui.getBind().getCode();
                }
            } catch (Throwable ignored) {}

            // пока меню ждёт клавишу для бинда или в поиске печатают, клавиша открытия уходит в меню, а не закрывает его
            boolean menuCapturing = false;
            try {
                menuCapturing = HorizonGui.isCapturingKeys();
            } catch (Throwable ignored) {}

            if (!menuCapturing && (keyEvent.key() == 344 || (bind > 0 && keyEvent.key() == bind))) {
                try {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc != null) {
                        if (mc.currentScreen == HorizonGui.INSTANCE) {
                            HorizonGui.INSTANCE.close();
                            ci.cancel();
                            return;
                        }
                        if (mc.currentScreen == UI.INSTANCE) {
                            UI.INSTANCE.close();
                            ci.cancel();
                            return;
                        }
                        boolean isBlocked = (mc.currentScreen instanceof ChatScreen
                                || mc.currentScreen instanceof HandledScreen);
                        if (!isBlocked) {
                            boolean horizon = true;
                            try {
                                horizon = clickGui == null || clickGui.useHorizon();
                            } catch (Throwable ignored) {}
                            mc.setScreen(horizon ? (Screen)HorizonGui.INSTANCE : (Screen)UI.INSTANCE);
                            try {
                                Sounds.play("gui_open");
                            } catch (Throwable ignored) {}
                            ci.cancel();
                            return;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        try {
            KeyPressEvent event = EventBus.get().post(new KeyPressEvent(keyEvent.key(), keyEvent.scancode(), keyEvent.modifiers(), KeyPressEvent.Action.of(action)));
            if (event != null && event.isCancelled()) {
                ci.cancel();
            }
        } catch (Throwable ignored) {}
    }
}

/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.input.KeyInput
 *  net.minecraft.client.Keyboard
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.screen.Screen
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mixin;

import net.minecraft.client.input.KeyInput;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rtx.kimiko.api.events.EventBus;
import rtx.kimiko.api.events.impl.input.KeyPressEvent;
import rtx.kimiko.api.modules.ModuleManager;
import rtx.kimiko.api.modules.impl.Interface.ClickGui;
import rtx.kimiko.api.ui.UI;
import rtx.kimiko.api.voice.VoiceBindManager;
import rtx.kimiko.utils.sounds.Sounds;

@Mixin(value={Keyboard.class})
public abstract class KeyboardHandlerMixin {
    @Inject(method={"onKey"}, at={@At(value="HEAD")}, cancellable=true)
    private void kimiko$onKeyPress(long window, int action, KeyInput keyEvent, CallbackInfo ci) {
        KeyPressEvent event;
        if (action == 1) {
            VoiceBindManager.INSTANCE.noteInput();
            ClickGui clickGui = ClickGui.getInstance();
            if (clickGui == null) {
                clickGui = ModuleManager.get().get(ClickGui.class);
            }
            int bind = (clickGui != null && clickGui.getBind() != null) ? clickGui.getBind().getCode() : 344;
            if (keyEvent.key() == 344 || (bind > 0 && keyEvent.key() == bind)) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.currentScreen == UI.INSTANCE) {
                    UI.INSTANCE.close();
                    ci.cancel();
                    return;
                }
                boolean isBlocked = (mc.currentScreen instanceof ChatScreen
                        || mc.currentScreen instanceof HandledScreen);
                if (!isBlocked) {
                    mc.setScreen((Screen)UI.INSTANCE);
                    Sounds.play("gui_open");
                    ci.cancel();
                    return;
                }
            }
        }
        if ((event = EventBus.get().post(new KeyPressEvent(keyEvent.key(), keyEvent.scancode(), keyEvent.modifiers(), KeyPressEvent.Action.of(action)))).isCancelled()) {
            ci.cancel();
        }
    }
}


/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.text.Text
 *  net.minecraft.util.Identifier
 *  net.minecraft.client.gui.widget.EntryListWidget$Entry
 *  net.minecraft.client.gui.Element
 *  net.minecraft.client.gui.widget.ButtonWidget
 *  net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget
 *  net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget$ServerEntry
 *  net.minecraft.client.gui.screen.Screen
 *  net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen
 *  net.minecraft.client.network.ServerInfo
 *  net.minecraft.client.network.ServerInfo$ServerType
 *  net.minecraft.client.gui.tooltip.Tooltip
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mixin;

import java.time.Duration;
import mods.acountswiher.ru.vidtu.ias.IAS;
import mods.acountswiher.ru.vidtu.ias.config.IASServerShortcutsConfig;
import mods.acountswiher.ru.vidtu.ias.screen.ServerShortcutButton;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.gui.tooltip.Tooltip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rtx.kimiko.utils.network.PinnedServers;

@Mixin(value={MultiplayerScreen.class})
public abstract class JoinMultiplayerScreenMixin
extends Screen {
    @Shadow
    protected MultiplayerServerListWidget serverListWidget;
    @Shadow
    private ButtonWidget buttonEdit;
    @Shadow
    private ButtonWidget buttonDelete;

    protected JoinMultiplayerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method={"init"}, at={@At(value="TAIL")})
    private void kimiko$addShortcutButtons(CallbackInfo ci) {
        MultiplayerScreen screen = (MultiplayerScreen)(Object)this;
        int index = 0;
        for (IASServerShortcutsConfig.ShortcutEntry shortcut : IASServerShortcutsConfig.load(IAS.configDirectory())) {
            Identifier texture = Identifier.of((String)"ias", (String)("textures/gui/server_shortcuts/" + shortcut.icon() + ".png"));
            ServerShortcutButton button = new ServerShortcutButton(8 + index++ * 20, 8, () -> texture, shortcut.name(), pressed -> screen.connect(new ServerInfo(shortcut.name(), shortcut.address(), ServerInfo.ServerType.OTHER)));
            button.setTooltip(Tooltip.of((Text)Text.literal((String)shortcut.name())));
            button.setTooltipDelay(Duration.ZERO);
            this.addDrawableChild(button);
        }
    }

    @Inject(method={"updateButtonActivationStates"}, at={@At(value="TAIL")})
    private void kimiko$protectPinnedServerControls(CallbackInfo ci) {
        MultiplayerServerListWidget.Entry entry2 = this.serverListWidget.getSelectedOrNull();
        if (entry2 instanceof MultiplayerServerListWidget.ServerEntry) {
            MultiplayerServerListWidget.ServerEntry entry = (MultiplayerServerListWidget.ServerEntry)entry2;
            if (PinnedServers.isPinned(entry.getServer().address)) {
                this.buttonEdit.active = false;
                this.buttonDelete.active = false;
            }
        }
    }
}


package rtx.kimiko.api.chat.commands.impl;

import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.NotNull;
import rtx.kimiko.api.chat.commands.Command;
import rtx.kimiko.api.ui.UI;
import rtx.kimiko.utils.sounds.Sounds;

public final class GuiCommand extends Command {
    public GuiCommand() {
        super("gui", "Opens ClickGUI", "clickgui");
    }

    @Override
    public void execute(@NotNull String label, @NotNull String[] args) {
        if (this.mc != null) {
            this.mc.send(() -> {
                this.mc.setScreen((Screen)UI.INSTANCE);
                try {
                    Sounds.play("gui_open");
                } catch (Throwable ignored) {}
            });
        }
    }
}

package fun.newrar.manager.event_impl;

import fun.newrar.manager.events.Event;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EventScreenRender extends Event {
    DrawContext drawContext;
    HandledScreen<?> screen;
    int mouseX;
    int mouseY;
}


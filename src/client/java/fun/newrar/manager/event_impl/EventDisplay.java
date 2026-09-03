package fun.newrar.manager.event_impl;

import fun.newrar.manager.events.Event;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gui.DrawContext;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EventDisplay extends Event {
    DrawContext drawContext;
    float partialTicks;
}


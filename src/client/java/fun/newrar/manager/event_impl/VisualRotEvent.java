package fun.newrar.manager.event_impl;

import fun.newrar.manager.events.CancellableEvent;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VisualRotEvent extends CancellableEvent {
    private float yaw;
    private float pitch;
    private float bodyaw;
}


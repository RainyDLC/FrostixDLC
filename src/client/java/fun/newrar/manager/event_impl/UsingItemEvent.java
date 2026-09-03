package fun.newrar.manager.event_impl;

import fun.newrar.manager.events.CancellableEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UsingItemEvent extends CancellableEvent {
    byte type;
}


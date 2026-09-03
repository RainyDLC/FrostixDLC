package fun.newrar.manager.event_impl;

import fun.newrar.manager.events.Event;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ActionEvent extends Event {
    private boolean sprintState;
}


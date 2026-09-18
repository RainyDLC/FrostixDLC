package fun.newrar.manager.event_impl;

import fun.newrar.manager.events.Event;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.math.Vec3d;

@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MoveEvent extends Event {
    Vec3d movement;
}


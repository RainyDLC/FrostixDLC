package fun.newrar.manager.event_impl;

import fun.newrar.manager.events.CancellableEvent;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import net.minecraft.network.packet.Packet;

@Getter
@Setter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EventPacket extends CancellableEvent {
    Packet<?> packet;
    Type type;

    public boolean isSend() {
        return type.equals(Type.SEND);
    }

    public enum Type {
        SEND, RECEIVE
    }
}


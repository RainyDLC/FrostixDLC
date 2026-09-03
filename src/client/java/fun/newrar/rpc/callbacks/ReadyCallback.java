package fun.newrar.rpc.callbacks;

import com.sun.jna.Callback;
import fun.newrar.rpc.DiscordUser;

public interface ReadyCallback extends Callback {
    void apply(final DiscordUser p0);
}


package fun.newrar.rpc.callbacks;

import com.sun.jna.Callback;
import fun.newrar.rpc.DiscordUser;

public interface JoinRequestCallback extends Callback {
    void apply(final DiscordUser p0);
}


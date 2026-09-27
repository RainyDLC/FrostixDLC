/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.sun.jna.Structure
 *  kotlin.Metadata
 *  kotlin.jvm.JvmField
 *  kotlin.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package rtx.kimiko.utils.discord.rpc.utils;

import com.sun.jna.Structure;
import java.util.Arrays;
import java.util.List;
import kotlin.Metadata;
import kotlin.jvm.JvmField;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rtx.kimiko.utils.discord.rpc.callbacks.DisconnectedCallback;
import rtx.kimiko.utils.discord.rpc.callbacks.ErroredCallback;
import rtx.kimiko.utils.discord.rpc.callbacks.JoinGameCallback;
import rtx.kimiko.utils.discord.rpc.callbacks.JoinRequestCallback;
import rtx.kimiko.utils.discord.rpc.callbacks.ReadyCallback;
import rtx.kimiko.utils.discord.rpc.callbacks.SpectateGameCallback;

@Metadata(mv={2, 4, 0}, k=1, xi=48, d1={"\u0000L\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u0016\u0018\u00002\u00020\u0001:\u0001\u001bB\u0007\u00a2\u0006\u0004\b\u0002\u0010\u0003J\u0015\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004H\u0014\u00a2\u0006\u0004\b\u0006\u0010\u0007R\u001d\u0010\n\u001a\u0004\u0018\u00010\b8\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\t\u00a2\u0006\u0006\n\u0004\b\n\u0010\u000bR\u001d\u0010\r\u001a\u0004\u0018\u00010\f8\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\t\u00a2\u0006\u0006\n\u0004\b\r\u0010\u000eR\u001d\u0010\u0010\u001a\u0004\u0018\u00010\u000f8\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\t\u00a2\u0006\u0006\n\u0004\b\u0010\u0010\u0011R\u001d\u0010\u0013\u001a\u0004\u0018\u00010\u00128\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\t\u00a2\u0006\u0006\n\u0004\b\u0013\u0010\u0014R\u001d\u0010\u0016\u001a\u0004\u0018\u00010\u00158\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\t\u00a2\u0006\u0006\n\u0004\b\u0016\u0010\u0017R\u001d\u0010\u0019\u001a\u0004\u0018\u00010\u00188\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\t\u00a2\u0006\u0006\n\u0004\b\u0019\u0010\u001a\u00a8\u0006\u001c"}, d2={"Lrtx/kimiko/utils/discord/rpc/utils/DiscordEventHandlers;", "Lcom/sun/jna/Structure;", "<init>", "()V", "", "", "getFieldOrder", "()Ljava/util/List;", "Lrtx/kimiko/utils/discord/rpc/callbacks/DisconnectedCallback;", "Lkotlin/jvm/JvmField;", "disconnected", "Lrtx/kimiko/utils/discord/rpc/callbacks/DisconnectedCallback;", "Lrtx/kimiko/utils/discord/rpc/callbacks/JoinRequestCallback;", "joinRequest", "Lrtx/kimiko/utils/discord/rpc/callbacks/JoinRequestCallback;", "Lrtx/kimiko/utils/discord/rpc/callbacks/SpectateGameCallback;", "spectateGame", "Lrtx/kimiko/utils/discord/rpc/callbacks/SpectateGameCallback;", "Lrtx/kimiko/utils/discord/rpc/callbacks/ReadyCallback;", "ready", "Lrtx/kimiko/utils/discord/rpc/callbacks/ReadyCallback;", "Lrtx/kimiko/utils/discord/rpc/callbacks/ErroredCallback;", "errored", "Lrtx/kimiko/utils/discord/rpc/callbacks/ErroredCallback;", "Lrtx/kimiko/utils/discord/rpc/callbacks/JoinGameCallback;", "joinGame", "Lrtx/kimiko/utils/discord/rpc/callbacks/JoinGameCallback;", "Builder", "rtx.kimiko:kimiko"})
public class DiscordEventHandlers
extends Structure {
    @JvmField
    @Nullable
    public DisconnectedCallback disconnected;
    @JvmField
    @Nullable
    public JoinRequestCallback joinRequest;
    @JvmField
    @Nullable
    public SpectateGameCallback spectateGame;
    @JvmField
    @Nullable
    public ReadyCallback ready;
    @JvmField
    @Nullable
    public ErroredCallback errored;
    @JvmField
    @Nullable
    public JoinGameCallback joinGame;

    @NotNull
    protected List<String> getFieldOrder() {
        String[] stringArray = new String[]{"ready", "disconnected", "errored", "joinGame", "spectateGame", "joinRequest"};
        List<String> list = Arrays.asList(stringArray);
        Intrinsics.checkNotNullExpressionValue(list, (String)"asList(...)");
        return list;
    }

    @Metadata(mv={2, 4, 0}, k=1, xi=48, d1={"\u0000D\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\u0018\u00002\u00020\u0001B\u0007\u00a2\u0006\u0004\b\u0002\u0010\u0003J\r\u0010\u0005\u001a\u00020\u0004\u00a2\u0006\u0004\b\u0005\u0010\u0006J\u0017\u0010\t\u001a\u00020\u00002\b\u0010\b\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\u0004\b\t\u0010\nJ\u0017\u0010\f\u001a\u00020\u00002\b\u0010\b\u001a\u0004\u0018\u00010\u000b\u00a2\u0006\u0004\b\f\u0010\rJ\u0017\u0010\u000f\u001a\u00020\u00002\b\u0010\b\u001a\u0004\u0018\u00010\u000e\u00a2\u0006\u0004\b\u000f\u0010\u0010J\u0017\u0010\u0012\u001a\u00020\u00002\b\u0010\b\u001a\u0004\u0018\u00010\u0011\u00a2\u0006\u0004\b\u0012\u0010\u0013J\u0017\u0010\u0015\u001a\u00020\u00002\b\u0010\b\u001a\u0004\u0018\u00010\u0014\u00a2\u0006\u0004\b\u0015\u0010\u0016J\u0017\u0010\u0018\u001a\u00020\u00002\b\u0010\b\u001a\u0004\u0018\u00010\u0017\u00a2\u0006\u0004\b\u0018\u0010\u0019R\u0014\u0010\u001a\u001a\u00020\u00048\u0002X\u0082\u0004\u00a2\u0006\u0006\n\u0004\b\u001a\u0010\u001b\u00a8\u0006\u001c"}, d2={"Lrtx/kimiko/utils/discord/rpc/utils/DiscordEventHandlers$Builder;", "", "<init>", "()V", "Lrtx/kimiko/utils/discord/rpc/utils/DiscordEventHandlers;", "build", "()Lrtx/kimiko/utils/discord/rpc/utils/DiscordEventHandlers;", "Lrtx/kimiko/utils/discord/rpc/callbacks/DisconnectedCallback;", "var1", "disconnected", "(Lrtx/kimiko/utils/discord/rpc/callbacks/DisconnectedCallback;)Lrtx/kimiko/utils/discord/rpc/utils/DiscordEventHandlers$Builder;", "Lrtx/kimiko/utils/discord/rpc/callbacks/ErroredCallback;", "errored", "(Lrtx/kimiko/utils/discord/rpc/callbacks/ErroredCallback;)Lrtx/kimiko/utils/discord/rpc/utils/DiscordEventHandlers$Builder;", "Lrtx/kimiko/utils/discord/rpc/callbacks/ReadyCallback;", "ready", "(Lrtx/kimiko/utils/discord/rpc/callbacks/ReadyCallback;)Lrtx/kimiko/utils/discord/rpc/utils/DiscordEventHandlers$Builder;", "Lrtx/kimiko/utils/discord/rpc/callbacks/JoinRequestCallback;", "joinRequest", "(Lrtx/kimiko/utils/discord/rpc/callbacks/JoinRequestCallback;)Lrtx/kimiko/utils/discord/rpc/utils/DiscordEventHandlers$Builder;", "Lrtx/kimiko/utils/discord/rpc/callbacks/JoinGameCallback;", "joinGame", "(Lrtx/kimiko/utils/discord/rpc/callbacks/JoinGameCallback;)Lrtx/kimiko/utils/discord/rpc/utils/DiscordEventHandlers$Builder;", "Lrtx/kimiko/utils/discord/rpc/callbacks/SpectateGameCallback;", "spectateGame", "(Lrtx/kimiko/utils/discord/rpc/callbacks/SpectateGameCallback;)Lrtx/kimiko/utils/discord/rpc/utils/DiscordEventHandlers$Builder;", "handlers", "Lrtx/kimiko/utils/discord/rpc/utils/DiscordEventHandlers;", "rtx.kimiko:kimiko"})
    public static final class Builder {
        @NotNull
        private final DiscordEventHandlers handlers = new DiscordEventHandlers();

        @NotNull
        public final DiscordEventHandlers build() {
            return this.handlers;
        }

        @NotNull
        public final Builder disconnected(@Nullable DisconnectedCallback var1) {
            this.handlers.disconnected = var1;
            return this;
        }

        @NotNull
        public final Builder errored(@Nullable ErroredCallback var1) {
            this.handlers.errored = var1;
            return this;
        }

        @NotNull
        public final Builder ready(@Nullable ReadyCallback var1) {
            this.handlers.ready = var1;
            return this;
        }

        @NotNull
        public final Builder joinRequest(@Nullable JoinRequestCallback var1) {
            this.handlers.joinRequest = var1;
            return this;
        }

        @NotNull
        public final Builder joinGame(@Nullable JoinGameCallback var1) {
            this.handlers.joinGame = var1;
            return this;
        }

        @NotNull
        public final Builder spectateGame(@Nullable SpectateGameCallback var1) {
            this.handlers.spectateGame = var1;
            return this;
        }
    }
}


/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.sun.jna.Library
 *  com.sun.jna.Native
 *  kotlin.Metadata
 *  kotlin.enums.EnumEntries
 *  kotlin.enums.EnumEntriesKt
 *  kotlin.jvm.JvmField
 *  kotlin.jvm.internal.DefaultConstructorMarker
 *  kotlin.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package rtx.kimiko.utils.discord.rpc.utils;

import com.sun.jna.Library;
import com.sun.jna.Native;
import kotlin.Metadata;
import kotlin.enums.EnumEntries;
import kotlin.enums.EnumEntriesKt;
import kotlin.jvm.JvmField;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rtx.kimiko.utils.discord.rpc.utils.DiscordEventHandlers;
import rtx.kimiko.utils.discord.rpc.utils.DiscordRichPresence;

@Metadata(mv={2, 4, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\u0010\b\n\u0002\b\n\n\u0002\u0010\u000b\n\u0002\b\b\bf\u0018\u0000 \u001c2\u00020\u0001:\u0002\u001d\u001cJ\u0019\u0010\u0005\u001a\u00020\u00042\b\u0010\u0003\u001a\u0004\u0018\u00010\u0002H&\u00a2\u0006\u0004\b\u0005\u0010\u0006J\u0019\u0010\b\u001a\u00020\u00042\b\u0010\u0003\u001a\u0004\u0018\u00010\u0007H&\u00a2\u0006\u0004\b\b\u0010\tJ!\u0010\r\u001a\u00020\u00042\b\u0010\u0003\u001a\u0004\u0018\u00010\n2\u0006\u0010\f\u001a\u00020\u000bH&\u00a2\u0006\u0004\b\r\u0010\u000eJ#\u0010\u000f\u001a\u00020\u00042\b\u0010\u0003\u001a\u0004\u0018\u00010\n2\b\u0010\f\u001a\u0004\u0018\u00010\nH&\u00a2\u0006\u0004\b\u000f\u0010\u0010J\u000f\u0010\u0011\u001a\u00020\u0004H&\u00a2\u0006\u0004\b\u0011\u0010\u0012J\u000f\u0010\u0013\u001a\u00020\u0004H&\u00a2\u0006\u0004\b\u0013\u0010\u0012J#\u0010\u0014\u001a\u00020\u00042\b\u0010\u0003\u001a\u0004\u0018\u00010\n2\b\u0010\f\u001a\u0004\u0018\u00010\nH&\u00a2\u0006\u0004\b\u0014\u0010\u0010J\u000f\u0010\u0015\u001a\u00020\u0004H&\u00a2\u0006\u0004\b\u0015\u0010\u0012J5\u0010\u0019\u001a\u00020\u00042\b\u0010\u0003\u001a\u0004\u0018\u00010\n2\b\u0010\f\u001a\u0004\u0018\u00010\u00022\u0006\u0010\u0017\u001a\u00020\u00162\b\u0010\u0018\u001a\u0004\u0018\u00010\nH&\u00a2\u0006\u0004\b\u0019\u0010\u001aJ\u000f\u0010\u001b\u001a\u00020\u0004H&\u00a2\u0006\u0004\b\u001b\u0010\u0012\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\u001e\u00c0\u0006\u0001"}, d2={"Lrtx/kimiko/utils/discord/rpc/utils/DiscordRPC;", "Lcom/sun/jna/Library;", "Lrtx/kimiko/utils/discord/rpc/utils/DiscordEventHandlers;", "var1", "", "Discord_UpdateHandlers", "(Lrtx/kimiko/utils/discord/rpc/utils/DiscordEventHandlers;)V", "Lrtx/kimiko/utils/discord/rpc/utils/DiscordRichPresence;", "Discord_UpdatePresence", "(Lrtx/kimiko/utils/discord/rpc/utils/DiscordRichPresence;)V", "", "", "var2", "Discord_Respond", "(Ljava/lang/String;I)V", "Discord_Register", "(Ljava/lang/String;Ljava/lang/String;)V", "Discord_Shutdown", "()V", "Discord_UpdateConnection", "Discord_RegisterSteamGame", "Discord_RunCallbacks", "", "var3", "var4", "Discord_Initialize", "(Ljava/lang/String;Lrtx/kimiko/utils/discord/rpc/utils/DiscordEventHandlers;ZLjava/lang/String;)V", "Discord_ClearPresence", "Companion", "DiscordReply", "rtx.kimiko:kimiko"})
public interface DiscordRPC
extends Library {
    @NotNull
    public static final Companion Companion = rtx.kimiko.utils.discord.rpc.utils.DiscordRPC.Companion.$$INSTANCE;
    @JvmField
    @NotNull
    public static final DiscordRPC INSTANCE = (DiscordRPC)Native.load("discord-rpc", DiscordRPC.class);

    public void Discord_UpdateHandlers(@Nullable DiscordEventHandlers var1);

    public void Discord_UpdatePresence(@Nullable DiscordRichPresence var1);

    public void Discord_Respond(@Nullable String var1, int var2);

    public void Discord_Register(@Nullable String var1, @Nullable String var2);

    public void Discord_Shutdown();

    public void Discord_UpdateConnection();

    public void Discord_RegisterSteamGame(@Nullable String var1, @Nullable String var2);

    public void Discord_RunCallbacks();

    public void Discord_Initialize(@Nullable String var1, @Nullable DiscordEventHandlers var2, boolean var3, @Nullable String var4);

    public void Discord_ClearPresence();

    @Metadata(mv={2, 4, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003R\u001c\u0010\u0006\u001a\u00020\u00048\u0006X\u0087\u0004\u0092\u0002\u0002\b\u0005\u00a2\u0006\u0006\n\u0004\b\u0006\u0010\u0007\u00a8\u0006\u0001\u00a8\u0006\b"}, d2={"Lrtx/kimiko/utils/discord/rpc/utils/DiscordRPC.Companion;", "", "<init>", "()V", "Lrtx/kimiko/utils/discord/rpc/utils/DiscordRPC;", "Lkotlin/jvm/JvmField;", "INSTANCE", "Lrtx/kimiko/utils/discord/rpc/utils/DiscordRPC;", "rtx.kimiko:kimiko"})
    public static final class Companion {
        static final /* synthetic */ Companion $$INSTANCE;

        private Companion() {
        }

        static {
            $$INSTANCE = new Companion();
        }
    }

    @Metadata(mv={2, 4, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0006\b\u0086\u0081\u0002\u0018\u0000 \b2\b\u0012\u0004\u0012\u00020\u00000\u0001:\u0001\bB\u0011\b\u0002\u0012\u0006\u0010\u0003\u001a\u00020\u0002\u00a2\u0006\u0004\b\u0004\u0010\u0005R\u0019\u0010\u0003\u001a\u00020\u00028\u0006X\u0087\u0004\u0092\u0002\u0002\b\u0006\u00a2\u0006\u0006\n\u0004\b\u0003\u0010\u0007j\u0002\b\tj\u0002\b\nj\u0002\b\u000b\u00a8\u0006\f"}, d2={"Lrtx/kimiko/utils/discord/rpc/utils/DiscordRPC$DiscordReply;", "", "", "reply", "<init>", "(Ljava/lang/String;II)V", "Lkotlin/jvm/JvmField;", "I", "Companion", "NO", "IGNORE", "YES", "rtx.kimiko:kimiko"})
    public static enum DiscordReply {
        NO(0),
        IGNORE(2),
        YES(1);
@NotNull
        public static final Companion Companion;
        @JvmField
        public final int reply;
        
        
        
        
        private DiscordReply(int reply) {
            this.reply = reply;
        }

        

        

        @NotNull
        public static EnumEntries<DiscordReply> getEntries() {
            return EnumEntriesKt.enumEntries(values());
        }

                static {
        Companion = new Companion(null);
    }

        @Metadata(mv={2, 4, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0011\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003J\u0015\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004H\u0002\u00a2\u0006\u0004\b\u0006\u0010\u0007\u00a8\u0006\b"}, d2={"Lrtx/kimiko/utils/discord/rpc/utils/DiscordRPC$DiscordReply.Companion;", "", "<init>", "()V", "", "Lrtx/kimiko/utils/discord/rpc/utils/DiscordRPC$DiscordReply;", "getReplies", "()[Lrtx/kimiko/utils/discord/rpc/utils/DiscordRPC$DiscordReply;", "rtx.kimiko:kimiko"})
        public static final class Companion {
            private Companion() {
            }

            private final DiscordReply[] getReplies() {
                DiscordReply[] discordReplyArray = new DiscordReply[]{NO, YES, IGNORE};
                return discordReplyArray;
            }

            public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
                this();
            }
        }
    }
}


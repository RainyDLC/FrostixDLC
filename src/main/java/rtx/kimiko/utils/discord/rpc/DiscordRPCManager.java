/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin.Unit
 *  kotlin.jvm.JvmStatic
 *  kotlin.jvm.internal.Intrinsics
 *  kotlin.text.StringsKt
 *  net.minecraft.client.MinecraftClient
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package rtx.kimiko.utils.discord.rpc;

import fun.shape.profile.Profile;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.jvm.JvmStatic;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rtx.kimiko.utils.discord.rpc.DiscordNativeLoader;
import rtx.kimiko.utils.discord.rpc.utils.DiscordEventHandlers;
import rtx.kimiko.utils.discord.rpc.utils.DiscordRPC;
import rtx.kimiko.utils.discord.rpc.utils.DiscordRichPresence;
import rtx.kimiko.utils.discord.rpc.utils.DiscordUser;
import rtx.kimiko.utils.discord.rpc.utils.RPCButton;
import rtx.kimiko.utils.profile.ProfileIdentity;

@Metadata(mv={2, 4, 0}, k=1, xi=48, d1={"\u0000h\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010\u0011\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003J\u0015\u0010\u0006\u001a\u0004\u0018\u00010\u0004H\u0007b\u0002\b\u0005\u00a2\u0006\u0004\b\u0006\u0010\u0007J\u0015\u0010\b\u001a\u0004\u0018\u00010\u0004H\u0007b\u0002\b\u0005\u00a2\u0006\u0004\b\b\u0010\u0007J\u0013\u0010\n\u001a\u00020\tH\u0007b\u0002\b\u0005\u00a2\u0006\u0004\b\n\u0010\u0003J\u0013\u0010\u000b\u001a\u00020\tH\u0007b\u0002\b\u0005\u00a2\u0006\u0004\b\u000b\u0010\u0003J\u000f\u0010\f\u001a\u00020\tH\u0002\u00a2\u0006\u0004\b\f\u0010\u0003J\u0019\u0010\u000f\u001a\u00020\t2\b\u0010\u000e\u001a\u0004\u0018\u00010\rH\u0002\u00a2\u0006\u0004\b\u000f\u0010\u0010J\u0017\u0010\u0013\u001a\u00020\u00122\u0006\u0010\u0011\u001a\u00020\u0004H\u0002\u00a2\u0006\u0004\b\u0013\u0010\u0014J\u000f\u0010\u0015\u001a\u00020\tH\u0002\u00a2\u0006\u0004\b\u0015\u0010\u0003J\u000f\u0010\u0016\u001a\u00020\tH\u0002\u00a2\u0006\u0004\b\u0016\u0010\u0003J\u0011\u0010\u0017\u001a\u0004\u0018\u00010\u0004H\u0002\u00a2\u0006\u0004\b\u0017\u0010\u0007J\u000f\u0010\u0018\u001a\u00020\u0004H\u0002\u00a2\u0006\u0004\b\u0018\u0010\u0007J\u000f\u0010\u0019\u001a\u00020\u0004H\u0002\u00a2\u0006\u0004\b\u0019\u0010\u0007J\u000f\u0010\u001b\u001a\u00020\u001aH\u0002\u00a2\u0006\u0004\b\u001b\u0010\u001cR\u0014\u0010\u001e\u001a\u00020\u001d8\u0002X\u0082\u0004\u00a2\u0006\u0006\n\u0004\b\u001e\u0010\u001fR\u0014\u0010 \u001a\u00020\u00048\u0002X\u0082T\u00a2\u0006\u0006\n\u0004\b \u0010!R\u0014\u0010#\u001a\u00020\"8\u0002X\u0082T\u00a2\u0006\u0006\n\u0004\b#\u0010$R\u001a\u0010&\u001a\b\u0012\u0004\u0012\u00020\u00040%8\u0002X\u0082\u0004\u00a2\u0006\u0006\n\u0004\b&\u0010'R\u0014\u0010(\u001a\u00020\"8\u0002X\u0082T\u00a2\u0006\u0006\n\u0004\b(\u0010$R\u0016\u0010)\u001a\u00020\u001a8\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\b)\u0010*R\u0016\u0010+\u001a\u00020\"8\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\b+\u0010$R\u0018\u0010-\u001a\u0004\u0018\u00010,8\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\b-\u0010.R\u0018\u0010/\u001a\u0004\u0018\u00010,8\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\b/\u0010.R\u0014\u00101\u001a\u0002008\u0002X\u0082\u0004\u00a2\u0006\u0006\n\u0004\b1\u00102R\u0018\u00104\u001a\u0004\u0018\u0001038\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\b4\u00105R\u0016\u0010\u0006\u001a\u00020\u00048\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\b\u0006\u0010!R\u0016\u0010\b\u001a\u00020\u00048\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\b\b\u0010!\u00a8\u00066"}, d2={"Lrtx/kimiko/utils/discord/rpc/DiscordRPCManager;", "", "<init>", "()V", "", "Lkotlin/jvm/JvmStatic;", "avatarUrl", "()Ljava/lang/String;", "username", "", "start", "stop", "runCallbacksLoop", "Lrtx/kimiko/utils/discord/rpc/utils/DiscordUser;", "user", "onReady", "(Lrtx/kimiko/utils/discord/rpc/utils/DiscordUser;)V", "details", "Lrtx/kimiko/utils/discord/rpc/utils/DiscordRichPresence;", "buildPresence", "(Ljava/lang/String;)Lrtx/kimiko/utils/discord/rpc/utils/DiscordRichPresence;", "startDetailsAnimation", "tickDetails", "smallImageUrl", "profileUsername", "currentUsername", "", "isLinux", "()Z", "Ljava/util/concurrent/atomic/AtomicBoolean;", "STARTED", "Ljava/util/concurrent/atomic/AtomicBoolean;", "CLIENT_ID", "Ljava/lang/String;", "", "CALLBACK_DELAY_MS", "J", "", "DETAILS_FRAMES", "[Ljava/lang/String;", "DETAILS_ANIM_INTERVAL_MS", "running", "Z", "startedAt", "Ljava/lang/Thread;", "callbackThread", "Ljava/lang/Thread;", "shutdownHook", "Ljava/util/concurrent/atomic/AtomicInteger;", "detailsFrame", "Ljava/util/concurrent/atomic/AtomicInteger;", "Ljava/util/concurrent/ScheduledExecutorService;", "detailsScheduler", "Ljava/util/concurrent/ScheduledExecutorService;", "rtx.kimiko:kimiko"})
public final class DiscordRPCManager {
    @NotNull
    public static final DiscordRPCManager INSTANCE = new DiscordRPCManager();
    @NotNull
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    @NotNull
    private static final String CLIENT_ID = "1519057999552057436";
    private static final long CALLBACK_DELAY_MS = 15000L;
    @NotNull
    private static final String[] DETAILS_FRAMES;
    private static final long DETAILS_ANIM_INTERVAL_MS = 3500L;
    private static volatile boolean running;
    private static volatile long startedAt;
    @Nullable
    private static volatile Thread callbackThread;
    @Nullable
    private static volatile Thread shutdownHook;
    @NotNull
    private static final AtomicInteger detailsFrame;
    @Nullable
    private static volatile ScheduledExecutorService detailsScheduler;
    @NotNull
    private static volatile String avatarUrl;
    @NotNull
    private static volatile String username;

    private DiscordRPCManager() {
    }

    @JvmStatic
    @Nullable
    public static final String avatarUrl() {
        String url = avatarUrl;
        return ((CharSequence)url).length() == 0 ? null : url;
    }

    @JvmStatic
    @Nullable
    public static final String username() {
        String u = username;
        return ((CharSequence)u).length() == 0 ? null : u;
    }

    @JvmStatic
    public static final void start() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        if (INSTANCE.isLinux()) {
            STARTED.set(false);
            return;
        }
        try {
            Thread hook;
            DiscordNativeLoader.prepare();
            startedAt = System.currentTimeMillis() / 1000L;
            DiscordEventHandlers handlers = new DiscordEventHandlers.Builder().ready(DiscordRPCManager::start$lambda$0).build();
            DiscordRPC.INSTANCE.Discord_Initialize(CLIENT_ID, handlers, true, "");
            running = true;
            Thread thread = new Thread(DiscordRPCManager::start$lambda$1, "Kimiko-Discord-RPC");
            thread.setDaemon(true);
            callbackThread = thread;
            thread.start();
            shutdownHook = hook = new Thread(DiscordRPCManager::start$lambda$2, "Kimiko-Discord-RPC-Shutdown");
            Runtime.getRuntime().addShutdownHook(hook);
        }
        catch (Throwable throwable) {
            running = false;
            STARTED.set(false);
        }
    }

    @JvmStatic
    public static final void stop() {
        if (!STARTED.compareAndSet(true, false)) {
            return;
        }
        running = false;
        ScheduledExecutorService localScheduler = detailsScheduler;
        detailsScheduler = null;
        ScheduledExecutorService scheduledExecutorService = localScheduler;
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdownNow();
        }
        Thread localThread = callbackThread;
        callbackThread = null;
        Thread thread = localThread;
        if (thread != null) {
            thread.interrupt();
        }
        Thread localHook = shutdownHook;
        shutdownHook = null;
        if (localHook != null) {
            try {
                boolean bl = Runtime.getRuntime().removeShutdownHook(localHook);
            }
            catch (IllegalStateException ignored) {
                Unit unit = Unit.INSTANCE;
            }
            catch (Exception ignored) {
                Unit unit = Unit.INSTANCE;
            }
        }
        try {
            DiscordRPC.INSTANCE.Discord_ClearPresence();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        try {
            DiscordRPC.INSTANCE.Discord_Shutdown();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    private final void runCallbacksLoop() {
        while (running) {
            try {
                DiscordRPC.INSTANCE.Discord_RunCallbacks();
                Thread.sleep(15000L);
            }
            catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            }
            catch (Throwable throwable) {
                break;
            }
        }
    }

    private final void onReady(DiscordUser user) {
        try {
            String string;
            Object object;
            avatarUrl = "";
            if (user != null && user.userId != null && user.avatar != null) {
                String string2 = user.userId;
                Intrinsics.checkNotNull((Object)string2);
                if (!StringsKt.isBlank((CharSequence)string2)) {
                    String string3 = user.avatar;
                    Intrinsics.checkNotNull((Object)string3);
                    if (!StringsKt.isBlank((CharSequence)string3)) {
                        avatarUrl = "https://cdn.discordapp.com/avatars/" + user.userId + "/" + user.avatar + ".png";
                    }
                }
            }
            username = (user != null && user.username != null) ? user.username : "";
            detailsFrame.set(0);
            DiscordRPC.INSTANCE.Discord_UpdatePresence(this.buildPresence(DETAILS_FRAMES[0]));
            this.startDetailsAnimation();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    private final DiscordRichPresence buildPresence(String details) {
        DiscordRichPresence.Builder presenceBuilder = new DiscordRichPresence.Builder().setStartTimestamp(startedAt).setDetails(details).setState("User: " + this.profileUsername() + " | Uid: " + Profile.getUid()).setButtons(RPCButton.Companion.create("Discord", "https://discord.gg/mCjMnPPYKA"), RPCButton.Companion.create("Telegram", "https://t.me/kimikodlc"));
        String smallImage = this.smallImageUrl();
        CharSequence charSequence = smallImage;
        if (!(charSequence == null || charSequence.length() == 0)) {
            presenceBuilder.setSmallImage(smallImage, this.profileUsername());
        }
        return presenceBuilder.build();
    }

    private final void startDetailsAnimation() {
        ScheduledExecutorService scheduler;
        if (detailsScheduler != null) {
            return;
        }
        detailsScheduler = scheduler = Executors.newSingleThreadScheduledExecutor(DiscordRPCManager::startDetailsAnimation$lambda$0);
        scheduler.scheduleAtFixedRate(DiscordRPCManager::startDetailsAnimation$lambda$1, 3500L, 3500L, TimeUnit.MILLISECONDS);
    }

    private final void tickDetails() {
        if (!running) {
            return;
        }
        try {
            int frame = detailsFrame.updateAndGet(DiscordRPCManager::tickDetails$lambda$0);
            DiscordRPC.INSTANCE.Discord_UpdatePresence(this.buildPresence(DETAILS_FRAMES[frame]));
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    private final String smallImageUrl() {
        String site = ProfileIdentity.avatarUrl();
        if (site != null) {
            return site;
        }
        return ((CharSequence)avatarUrl).length() > 0 ? avatarUrl : null;
    }

    private final String profileUsername() {
        String n = Profile.getUsername();
        CharSequence charSequence = n;
        if (!(charSequence == null || StringsKt.isBlank((CharSequence)charSequence))) {
            return n;
        }
        return this.currentUsername();
    }

    private final String currentUsername() {
        try {
            MinecraftClient minecraftClient2 = MinecraftClient.getInstance();
            Intrinsics.checkNotNullExpressionValue((Object)minecraftClient2, (String)"getInstance(...)");
            MinecraftClient mc = minecraftClient2;
            if (mc.getSession() != null) {
                String string = mc.getSession().getUsername();
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getName(...)");
                String name = string;
                if (!StringsKt.isBlank((CharSequence)name)) {
                    return name;
                }
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return "Player";
    }

    private final boolean isLinux() {
        String string = System.getProperty("os.name", "");
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getProperty(...)");
        String string2 = string;
        Locale locale = Locale.ROOT;
        Intrinsics.checkNotNullExpressionValue((Object)locale, (String)"ROOT");
        String string3 = string2.toLowerCase(locale);
        Intrinsics.checkNotNullExpressionValue((Object)string3, (String)"toLowerCase(...)");
        return String.valueOf(string3).contains("linux");
    }

    private static final void start$lambda$0(DiscordUser user) {
        INSTANCE.onReady(user);
    }

    private static final void start$lambda$1() {
        INSTANCE.runCallbacksLoop();
    }

    private static final void start$lambda$2() {
        DiscordRPCManager.stop();
    }

    private static final Thread startDetailsAnimation$lambda$0(Runnable runnable) {
        Thread thread = new Thread(runnable, "Kimiko-Discord-RPC-Anim");
        thread.setDaemon(true);
        return thread;
    }

    private static final void startDetailsAnimation$lambda$1() {
        INSTANCE.tickDetails();
    }

    private static final int tickDetails$lambda$0(int i) {
        return (i + 1) % DETAILS_FRAMES.length;
    }

    static {
        String[] stringArray = new String[]{"Build \u2726 v1.5 \u2726", "Build \u2726 v1.5 \u2727", "Build \u2727 v1.5 \u2726", "Build \u2727 v1.5 \u2727"};
        DETAILS_FRAMES = stringArray;
        detailsFrame = new AtomicInteger(0);
        avatarUrl = "";
        username = "";
    }
}


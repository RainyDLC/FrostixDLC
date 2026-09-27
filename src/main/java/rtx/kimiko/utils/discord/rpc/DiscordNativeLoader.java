/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.sun.jna.NativeLibrary
 *  kotlin.Metadata
 *  kotlin.io.CloseableKt
 *  kotlin.jvm.JvmStatic
 *  kotlin.jvm.internal.Intrinsics
 *  kotlin.text.StringsKt
 *  org.jetbrains.annotations.NotNull
 */
package rtx.kimiko.utils.discord.rpc;

import com.sun.jna.NativeLibrary;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Metadata;
import kotlin.io.CloseableKt;
import kotlin.jvm.JvmStatic;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={2, 4, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003J\u0013\u0010\u0006\u001a\u00020\u0004H\u0007b\u0002\b\u0005\u00a2\u0006\u0004\b\u0006\u0010\u0003R\u0014\u0010\b\u001a\u00020\u00078\u0002X\u0082\u0004\u00a2\u0006\u0006\n\u0004\b\b\u0010\t\u00a8\u0006\n"}, d2={"Lrtx/kimiko/utils/discord/rpc/DiscordNativeLoader;", "", "<init>", "()V", "", "Lkotlin/jvm/JvmStatic;", "prepare", "Ljava/util/concurrent/atomic/AtomicBoolean;", "PREPARED", "Ljava/util/concurrent/atomic/AtomicBoolean;", "rtx.kimiko:kimiko"})
public final class DiscordNativeLoader {
    @NotNull
    public static final DiscordNativeLoader INSTANCE = new DiscordNativeLoader();
    @NotNull
    private static final AtomicBoolean PREPARED = new AtomicBoolean(false);

    private DiscordNativeLoader() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @JvmStatic
    public static final void prepare() {
        if (!PREPARED.compareAndSet(false, true)) {
            return;
        }
        String string = System.getProperty("os.name", "");
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getProperty(...)");
        String string2 = string;
        Locale locale = Locale.ROOT;
        Intrinsics.checkNotNullExpressionValue((Object)locale, (String)"ROOT");
        String string3 = string2.toLowerCase(locale);
        Intrinsics.checkNotNullExpressionValue((Object)string3, (String)"toLowerCase(...)");
        String os = string3;
        if (!String.valueOf(os).contains("win")) {
            return;
        }
        String string4 = System.getProperty("os.arch", "");
        Intrinsics.checkNotNullExpressionValue((Object)string4, (String)"getProperty(...)");
        boolean x64 = String.valueOf(string4).contains("64");
        String resourcePath = x64 ? "/win32-x86-64/discord-rpc.dll" : "/win32-x86/discord-rpc.dll";
        try {
            String[] stringArray = new String[]{"kimiko-discord-rpc"};
            Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), stringArray);
            Files.createDirectories(tempDir, new FileAttribute[0]);
            Path targetDll = tempDir.resolve("discord-rpc.dll");
            Closeable closeable = DiscordNativeLoader.class.getResourceAsStream(resourcePath);
            Throwable throwable = null;
            try {
                InputStream inputStream = (InputStream)closeable;
                boolean bl = false;
                if (inputStream == null) {
                    return;
                }
                CopyOption[] copyOptionArray = new CopyOption[]{StandardCopyOption.REPLACE_EXISTING};
                long l = Files.copy(inputStream, targetDll, copyOptionArray);
            }
            catch (Throwable throwable2) {
                throwable = throwable2;
                throw throwable2;
            }
            finally {
                CloseableKt.closeFinally((Closeable)closeable, (Throwable)throwable);
            }
            NativeLibrary.addSearchPath((String)"discord-rpc", (String)((Object)tempDir.toAbsolutePath()).toString());
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }
}


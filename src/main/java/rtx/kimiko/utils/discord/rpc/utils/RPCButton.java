/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin.jvm.JvmName
 *  kotlin.jvm.JvmStatic
 *  kotlin.jvm.internal.DefaultConstructorMarker
 *  kotlin.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 */
package rtx.kimiko.utils.discord.rpc.utils;

import java.io.Serializable;
import kotlin.Metadata;
import kotlin.jvm.JvmName;
import kotlin.jvm.JvmStatic;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={2, 4, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0007\b\u0016\u0018\u0000 \r2\u00020\u0001:\u0001\rB\u0019\b\u0004\u0012\u0006\u0010\u0003\u001a\u00020\u0002\u0012\u0006\u0010\u0004\u001a\u00020\u0002\u00a2\u0006\u0004\b\u0005\u0010\u0006R%\u0010\u0003\u001a\u00020\u00028\u0007z\f\b\u0007\u0012\b\b\b\u0012\u0004\b\b(\t\u00a2\u0006\f\n\u0004\b\u0003\u0010\n\u001a\u0004\b\t\u0010\u000bR%\u0010\u0004\u001a\u00020\u00028\u0007z\f\b\u0007\u0012\b\b\b\u0012\u0004\b\b(\f\u00a2\u0006\f\n\u0004\b\u0004\u0010\n\u001a\u0004\b\f\u0010\u000b\u00a8\u0006\u000e"}, d2={"Lrtx/kimiko/utils/discord/rpc/utils/RPCButton;", "Ljava/io/Serializable;", "", "label", "url", "<init>", "(Ljava/lang/String;Ljava/lang/String;)V", "Lkotlin/jvm/JvmName;", "name", "getLabel", "Ljava/lang/String;", "()Ljava/lang/String;", "getUrl", "Companion", "rtx.kimiko:kimiko"})
public class RPCButton
implements Serializable {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private final String label;
    @NotNull
    private final String url;

    protected RPCButton(@NotNull String label, @NotNull String url) {
        Intrinsics.checkNotNullParameter((Object)label, (String)"label");
        Intrinsics.checkNotNullParameter((Object)url, (String)"url");
        this.label = label;
        this.url = url;
    }

    @JvmName(name="getLabel")
    @NotNull
    public final String getLabel() {
        return this.label;
    }

    @JvmName(name="getUrl")
    @NotNull
    public final String getUrl() {
        return this.url;
    }

    @JvmStatic
    @NotNull
    public static final RPCButton create(@NotNull String label, @NotNull String url) {
        return Companion.create(label, url);
    }

    @Metadata(mv={2, 4, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003J#\u0010\t\u001a\u00020\u00072\u0006\u0010\u0005\u001a\u00020\u00042\u0006\u0010\u0006\u001a\u00020\u0004H\u0007b\u0002\b\b\u00a2\u0006\u0004\b\t\u0010\n\u00a8\u0006\u000b"}, d2={"Lrtx/kimiko/utils/discord/rpc/utils/RPCButton.Companion;", "", "<init>", "()V", "", "label", "url", "Lrtx/kimiko/utils/discord/rpc/utils/RPCButton;", "Lkotlin/jvm/JvmStatic;", "create", "(Ljava/lang/String;Ljava/lang/String;)Lrtx/kimiko/utils/discord/rpc/utils/RPCButton;", "rtx.kimiko:kimiko"})
    public static final class Companion {
        private Companion() {
        }

        @JvmStatic
        @NotNull
        public final RPCButton create(@NotNull String label, @NotNull String url) {
            Intrinsics.checkNotNullParameter((Object)label, (String)"label");
            Intrinsics.checkNotNullParameter((Object)url, (String)"url");
            String string = label.substring(0, Math.min(label.length(), 31));
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"substring(...)");
            return new RPCButton(string, url);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}


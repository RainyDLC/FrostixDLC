/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.sun.jna.Callback
 *  kotlin.Metadata
 *  org.jetbrains.annotations.Nullable
 */
package rtx.kimiko.utils.discord.rpc.callbacks;

import com.sun.jna.Callback;
import kotlin.Metadata;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={2, 4, 0}, k=1, xi=48, d1={"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\b\u00e6\u0080\u0001\u0018\u00002\u00020\u0001J!\u0010\u0007\u001a\u00020\u00062\u0006\u0010\u0003\u001a\u00020\u00022\b\u0010\u0005\u001a\u0004\u0018\u00010\u0004H&\u00a2\u0006\u0004\b\u0007\u0010\b\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\t\u00c0\u0006\u0001"}, d2={"Lrtx/kimiko/utils/discord/rpc/callbacks/ErroredCallback;", "Lcom/sun/jna/Callback;", "", "var1", "", "var2", "", "apply", "(ILjava/lang/String;)V", "rtx.kimiko:kimiko"})
public interface ErroredCallback
extends Callback {
    public void apply(int var1, @Nullable String var2);
}


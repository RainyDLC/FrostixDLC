/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.sun.jna.Structure
 *  kotlin.Deprecated
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
import kotlin.Deprecated;
import kotlin.Metadata;
import kotlin.jvm.JvmField;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={2, 4, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0006\b\u0016\u0018\u00002\u00020\u0001B\u0007\u00a2\u0006\u0004\b\u0002\u0010\u0003J\u0015\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004H\u0014\u00a2\u0006\u0004\b\u0006\u0010\u0007R\u001d\u0010\t\u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\t\u0010\nR\u001d\u0010\u000b\u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\u000b\u0010\nR1\u0010\u000f\u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000er\f\b\f\u0012\b\b\r\u0012\u0004\b\b(\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\f\n\u0004\b\u000f\u0010\n\u0012\u0004\b\u0010\u0010\u0003R\u001d\u0010\u0011\u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\u0011\u0010\n\u00a8\u0006\u0012"}, d2={"Lrtx/kimiko/utils/discord/rpc/utils/DiscordUser;", "Lcom/sun/jna/Structure;", "<init>", "()V", "", "", "getFieldOrder", "()Ljava/util/List;", "Lkotlin/jvm/JvmField;", "userId", "Ljava/lang/String;", "username", "Lkotlin/Deprecated;", "message", "kept for JNA layout compatibility", "discriminator", "getDiscriminator$annotations", "avatar", "rtx.kimiko:kimiko"})
public class DiscordUser
extends Structure {
    @JvmField
    @Nullable
    public String userId;
    @JvmField
    @Nullable
    public String username;
    @JvmField
    @Nullable
    public String discriminator;
    @JvmField
    @Nullable
    public String avatar;

    @Deprecated(message="kept for JNA layout compatibility")
    public static /* synthetic */ void getDiscriminator$annotations() {
    }

    @NotNull
    protected List<String> getFieldOrder() {
        String[] stringArray = new String[]{"userId", "username", "discriminator", "avatar"};
        List<String> list = Arrays.asList(stringArray);
        Intrinsics.checkNotNullExpressionValue(list, (String)"asList(...)");
        return list;
    }
}


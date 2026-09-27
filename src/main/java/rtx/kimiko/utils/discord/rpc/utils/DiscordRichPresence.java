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
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import kotlin.Metadata;
import kotlin.jvm.JvmField;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rtx.kimiko.utils.discord.rpc.utils.RPCButton;

@Metadata(mv={2, 4, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0012\b\u0016\u0018\u00002\u00020\u0001:\u0001\"B\u0007\u00a2\u0006\u0004\b\u0002\u0010\u0003J\u0015\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004H\u0014\u00a2\u0006\u0004\b\u0006\u0010\u0007R\u001d\u0010\t\u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\t\u0010\nR\u001d\u0010\u000b\u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\u000b\u0010\nR\u001d\u0010\f\u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\f\u0010\nR\u001d\u0010\r\u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\r\u0010\nR\u001b\u0010\u000f\u001a\u00020\u000e8\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\u000f\u0010\u0010R\u001b\u0010\u0012\u001a\u00020\u00118\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\u0012\u0010\u0013R\u001d\u0010\u0014\u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\u0014\u0010\nR\u001b\u0010\u0015\u001a\u00020\u00118\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\u0015\u0010\u0013R\u001b\u0010\u0016\u001a\u00020\u000e8\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\u0016\u0010\u0010R\u001d\u0010\u0017\u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\u0017\u0010\nR\u001d\u0010\u0018\u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\u0018\u0010\nR\u001d\u0010\u0019\u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\u0019\u0010\nR\u001d\u0010\u001a\u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\u001a\u0010\nR\u001d\u0010\u001b\u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\u001b\u0010\nR\u001d\u0010\u001c\u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\u001c\u0010\nR\u001b\u0010\u001d\u001a\u00020\u00118\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\u001d\u0010\u0013R\u001d\u0010\u001e\u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\u001e\u0010\nR\u001d\u0010\u001f\u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b\u001f\u0010\nR\u001d\u0010 \u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b \u0010\nR\u001d\u0010!\u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000e\u0092\u0002\u0002\b\b\u00a2\u0006\u0006\n\u0004\b!\u0010\n\u00a8\u0006#"}, d2={"Lrtx/kimiko/utils/discord/rpc/utils/DiscordRichPresence;", "Lcom/sun/jna/Structure;", "<init>", "()V", "", "", "getFieldOrder", "()Ljava/util/List;", "Lkotlin/jvm/JvmField;", "largeImageKey", "Ljava/lang/String;", "largeImageText", "smallImageText", "partyPrivacy", "", "startTimestamp", "J", "", "instance", "I", "partyId", "partySize", "endTimestamp", "details", "joinSecret", "spectateSecret", "smallImageKey", "matchSecret", "state", "partyMax", "button_url_1", "button_label_1", "button_url_2", "button_label_2", "Builder", "rtx.kimiko:kimiko"})
public class DiscordRichPresence
extends Structure {
    @JvmField
    @Nullable
    public String largeImageKey;
    @JvmField
    @Nullable
    public String largeImageText;
    @JvmField
    @Nullable
    public String smallImageText;
    @JvmField
    @Nullable
    public String partyPrivacy;
    @JvmField
    public long startTimestamp;
    @JvmField
    public int instance;
    @JvmField
    @Nullable
    public String partyId;
    @JvmField
    public int partySize;
    @JvmField
    public long endTimestamp;
    @JvmField
    @Nullable
    public String details;
    @JvmField
    @Nullable
    public String joinSecret;
    @JvmField
    @Nullable
    public String spectateSecret;
    @JvmField
    @Nullable
    public String smallImageKey;
    @JvmField
    @Nullable
    public String matchSecret;
    @JvmField
    @Nullable
    public String state;
    @JvmField
    public int partyMax;
    @JvmField
    @Nullable
    public String button_url_1;
    @JvmField
    @Nullable
    public String button_label_1;
    @JvmField
    @Nullable
    public String button_url_2;
    @JvmField
    @Nullable
    public String button_label_2;

    public DiscordRichPresence() {
        this.setStringEncoding("UTF-8");
    }

    @NotNull
    protected List<String> getFieldOrder() {
        String[] stringArray = new String[]{"state", "details", "startTimestamp", "endTimestamp", "largeImageKey", "largeImageText", "smallImageKey", "smallImageText", "partyId", "partySize", "partyMax", "partyPrivacy", "matchSecret", "joinSecret", "spectateSecret", "button_label_1", "button_url_1", "button_label_2", "button_url_2", "instance"};
        List<String> list = Arrays.asList(stringArray);
        Intrinsics.checkNotNullExpressionValue(list, (String)"asList(...)");
        return list;
    }

    @Metadata(mv={2, 4, 0}, k=1, xi=48, d1={"\u0000D\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\b\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\u0018\u00002\u00020\u0001B\u0007\u00a2\u0006\u0004\b\u0002\u0010\u0003J\u0017\u0010\u0006\u001a\u00020\u00002\b\u0010\u0005\u001a\u0004\u0018\u00010\u0004\u00a2\u0006\u0004\b\u0006\u0010\u0007J\u0017\u0010\b\u001a\u00020\u00002\b\u0010\u0005\u001a\u0004\u0018\u00010\u0004\u00a2\u0006\u0004\b\b\u0010\u0007J!\u0010\n\u001a\u00020\u00002\b\u0010\u0005\u001a\u0004\u0018\u00010\u00042\b\u0010\t\u001a\u0004\u0018\u00010\u0004\u00a2\u0006\u0004\b\n\u0010\u000bJ\u0017\u0010\f\u001a\u00020\u00002\b\u0010\u0005\u001a\u0004\u0018\u00010\u0004\u00a2\u0006\u0004\b\f\u0010\u0007J\u0015\u0010\u000e\u001a\u00020\u00002\u0006\u0010\u0005\u001a\u00020\r\u00a2\u0006\u0004\b\u000e\u0010\u000fJ\u0015\u0010\u0011\u001a\u00020\u00002\u0006\u0010\u0005\u001a\u00020\u0010\u00a2\u0006\u0004\b\u0011\u0010\u0012J!\u0010\u0006\u001a\u00020\u00002\b\u0010\u0005\u001a\u0004\u0018\u00010\u00042\b\u0010\t\u001a\u0004\u0018\u00010\u0004\u00a2\u0006\u0004\b\u0006\u0010\u000bJ\u001d\u0010\u0011\u001a\u00020\u00002\u000e\u0010\u0014\u001a\n\u0012\u0004\u0012\u00020\u0010\u0018\u00010\u0013\u00a2\u0006\u0004\b\u0011\u0010\u0015J\u0015\u0010\u0017\u001a\u00020\u00002\u0006\u0010\u0005\u001a\u00020\u0016\u00a2\u0006\u0004\b\u0017\u0010\u0018J+\u0010\u001a\u001a\u00020\u00002\b\u0010\u0005\u001a\u0004\u0018\u00010\u00042\b\u0010\t\u001a\u0004\u0018\u00010\u00042\b\u0010\u0019\u001a\u0004\u0018\u00010\u0004\u00a2\u0006\u0004\b\u001a\u0010\u001bJ\u001d\u0010\u0011\u001a\u00020\u00002\u0006\u0010\u0005\u001a\u00020\u00102\u0006\u0010\t\u001a\u00020\u0010\u00a2\u0006\u0004\b\u0011\u0010\u001cJ\u0015\u0010\u0017\u001a\u00020\u00002\u0006\u0010\u0005\u001a\u00020\u001d\u00a2\u0006\u0004\b\u0017\u0010\u001eJ!\u0010\u001a\u001a\u00020\u00002\b\u0010\u0005\u001a\u0004\u0018\u00010\u00042\b\u0010\t\u001a\u0004\u0018\u00010\u0004\u00a2\u0006\u0004\b\u001a\u0010\u000bJ\u0015\u0010\u001f\u001a\u00020\u00002\u0006\u0010\u0005\u001a\u00020\u001d\u00a2\u0006\u0004\b\u001f\u0010\u001eJ\u0015\u0010\u001f\u001a\u00020\u00002\u0006\u0010\u0005\u001a\u00020\u0016\u00a2\u0006\u0004\b\u001f\u0010\u0018J\u0017\u0010\n\u001a\u00020\u00002\b\u0010\u0005\u001a\u0004\u0018\u00010\u0004\u00a2\u0006\u0004\b\n\u0010\u0007J\r\u0010!\u001a\u00020 \u00a2\u0006\u0004\b!\u0010\"R\u0014\u0010#\u001a\u00020 8\u0002X\u0082\u0004\u00a2\u0006\u0006\n\u0004\b#\u0010$\u00a8\u0006%"}, d2={"Lrtx/kimiko/utils/discord/rpc/utils/DiscordRichPresence$Builder;", "", "<init>", "()V", "", "var1", "setSmallImage", "(Ljava/lang/String;)Lrtx/kimiko/utils/discord/rpc/utils/DiscordRichPresence$Builder;", "setDetails", "var2", "setLargeImage", "(Ljava/lang/String;Ljava/lang/String;)Lrtx/kimiko/utils/discord/rpc/utils/DiscordRichPresence$Builder;", "setState", "", "setInstance", "(Z)Lrtx/kimiko/utils/discord/rpc/utils/DiscordRichPresence$Builder;", "Lrtx/kimiko/utils/discord/rpc/utils/RPCButton;", "setButtons", "(Lrtx/kimiko/utils/discord/rpc/utils/RPCButton;)Lrtx/kimiko/utils/discord/rpc/utils/DiscordRichPresence$Builder;", "", "buttons", "(Ljava/util/List;)Lrtx/kimiko/utils/discord/rpc/utils/DiscordRichPresence$Builder;", "Ljava/time/OffsetDateTime;", "setStartTimestamp", "(Ljava/time/OffsetDateTime;)Lrtx/kimiko/utils/discord/rpc/utils/DiscordRichPresence$Builder;", "var3", "setSecrets", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Lrtx/kimiko/utils/discord/rpc/utils/DiscordRichPresence$Builder;", "(Lrtx/kimiko/utils/discord/rpc/utils/RPCButton;Lrtx/kimiko/utils/discord/rpc/utils/RPCButton;)Lrtx/kimiko/utils/discord/rpc/utils/DiscordRichPresence$Builder;", "", "(J)Lrtx/kimiko/utils/discord/rpc/utils/DiscordRichPresence$Builder;", "setEndTimestamp", "Lrtx/kimiko/utils/discord/rpc/utils/DiscordRichPresence;", "build", "()Lrtx/kimiko/utils/discord/rpc/utils/DiscordRichPresence;", "richPresence", "Lrtx/kimiko/utils/discord/rpc/utils/DiscordRichPresence;", "rtx.kimiko:kimiko"})
    public static final class Builder {
        @NotNull
        private final DiscordRichPresence richPresence = new DiscordRichPresence();

        @NotNull
        public final Builder setSmallImage(@Nullable String var1) {
            return this.setSmallImage(var1, "");
        }

        @NotNull
        public final Builder setDetails(@Nullable String var1) {
            CharSequence charSequence = var1;
            if (!(charSequence == null || charSequence.length() == 0)) {
                String string = var1.substring(0, Math.min(var1.length(), 128));
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"substring(...)");
                this.richPresence.details = string;
            }
            return this;
        }

        @NotNull
        public final Builder setLargeImage(@Nullable String var1, @Nullable String var2) {
            this.richPresence.largeImageKey = var1;
            this.richPresence.largeImageText = var2;
            return this;
        }

        @NotNull
        public final Builder setState(@Nullable String var1) {
            CharSequence charSequence = var1;
            if (!(charSequence == null || charSequence.length() == 0)) {
                String string = var1.substring(0, Math.min(var1.length(), 128));
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"substring(...)");
                this.richPresence.state = string;
            }
            return this;
        }

        @NotNull
        public final Builder setInstance(boolean var1) {
            block3: {
                block4: {
                    block2: {
                        if (this.richPresence.button_label_1 == null) break block2;
                        String string = this.richPresence.button_label_1;
                        Intrinsics.checkNotNull((Object)string);
                        if (((CharSequence)string).length() == 0) break block3;
                    }
                    if (this.richPresence.button_label_2 == null) break block4;
                    String string = this.richPresence.button_label_2;
                    Intrinsics.checkNotNull((Object)string);
                    if (((CharSequence)string).length() == 0) break block3;
                }
                this.richPresence.instance = var1 ? 1 : 0;
            }
            return this;
        }

        @NotNull
        public final Builder setButtons(@NotNull RPCButton var1) {
            Intrinsics.checkNotNullParameter((Object)var1, (String)"var1");
            return this.setButtons(Collections.singletonList(var1));
        }

        @NotNull
        public final Builder setSmallImage(@Nullable String var1, @Nullable String var2) {
            this.richPresence.smallImageKey = var1;
            this.richPresence.smallImageText = var2;
            return this;
        }

        @NotNull
        public final Builder setButtons(@Nullable List<? extends RPCButton> buttons) {
            Collection collection = buttons;
            if (!(collection == null || collection.isEmpty())) {
                int var2 = Math.min(buttons.size(), 2);
                this.richPresence.button_label_1 = buttons.get(0).getLabel();
                this.richPresence.button_url_1 = buttons.get(0).getUrl();
                if (var2 == 2) {
                    this.richPresence.button_label_2 = buttons.get(1).getLabel();
                    this.richPresence.button_url_2 = buttons.get(1).getUrl();
                }
            }
            return this;
        }

        @NotNull
        public final Builder setStartTimestamp(@NotNull OffsetDateTime var1) {
            Intrinsics.checkNotNullParameter((Object)var1, (String)"var1");
            this.richPresence.startTimestamp = var1.toEpochSecond();
            return this;
        }

        @NotNull
        public final Builder setSecrets(@Nullable String var1, @Nullable String var2, @Nullable String var3) {
            block3: {
                block4: {
                    block2: {
                        if (this.richPresence.button_label_1 == null) break block2;
                        String string = this.richPresence.button_label_1;
                        Intrinsics.checkNotNull((Object)string);
                        if (((CharSequence)string).length() == 0) break block3;
                    }
                    if (this.richPresence.button_label_2 == null) break block4;
                    String string = this.richPresence.button_label_2;
                    Intrinsics.checkNotNull((Object)string);
                    if (((CharSequence)string).length() == 0) break block3;
                }
                this.richPresence.matchSecret = var1;
                this.richPresence.joinSecret = var2;
                this.richPresence.spectateSecret = var3;
            }
            return this;
        }

        @NotNull
        public final Builder setButtons(@NotNull RPCButton var1, @NotNull RPCButton var2) {
            Intrinsics.checkNotNullParameter((Object)var1, (String)"var1");
            Intrinsics.checkNotNullParameter((Object)var2, (String)"var2");
            RPCButton[] rPCButtonArray = new RPCButton[]{var1, var2};
            this.setButtons(Arrays.asList(rPCButtonArray));
            return this;
        }

        @NotNull
        public final Builder setStartTimestamp(long var1) {
            this.richPresence.startTimestamp = var1;
            return this;
        }

        @NotNull
        public final Builder setSecrets(@Nullable String var1, @Nullable String var2) {
            block3: {
                block4: {
                    block2: {
                        if (this.richPresence.button_label_1 == null) break block2;
                        String string = this.richPresence.button_label_1;
                        Intrinsics.checkNotNull((Object)string);
                        if (((CharSequence)string).length() == 0) break block3;
                    }
                    if (this.richPresence.button_label_2 == null) break block4;
                    String string = this.richPresence.button_label_2;
                    Intrinsics.checkNotNull((Object)string);
                    if (((CharSequence)string).length() == 0) break block3;
                }
                this.richPresence.joinSecret = var1;
                this.richPresence.spectateSecret = var2;
            }
            return this;
        }

        @NotNull
        public final Builder setEndTimestamp(long var1) {
            this.richPresence.endTimestamp = var1;
            return this;
        }

        @NotNull
        public final Builder setEndTimestamp(@NotNull OffsetDateTime var1) {
            Intrinsics.checkNotNullParameter((Object)var1, (String)"var1");
            this.richPresence.endTimestamp = var1.toEpochSecond();
            return this;
        }

        @NotNull
        public final Builder setLargeImage(@Nullable String var1) {
            return this.setLargeImage(var1, "");
        }

        @NotNull
        public final DiscordRichPresence build() {
            return this.richPresence;
        }
    }
}


package rtx.kimiko.api.modules.impl.Interface;

import java.util.Arrays;
import kotlin.jvm.JvmField;
import kotlin.jvm.JvmStatic;
import kotlin.jvm.internal.DefaultConstructorMarker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rtx.kimiko.api.liteapi.Feature;
import rtx.kimiko.api.modules.Category;
import rtx.kimiko.api.modules.Module;
import rtx.kimiko.api.modules.settings.Setting;
import rtx.kimiko.api.modules.settings.impl.ModeSetting;
import rtx.kimiko.api.ui.UiScale;
import rtx.kimiko.utils.key.KeyBind;

@Feature(value={"clickgui"})
public final class ClickGui
extends Module {
    @NotNull
    public static final Companion Companion = new Companion(null);
    public static final String STYLE_HORIZON = "Чёрная дыра";
    public static final String STYLE_CLASSIC = "Классика";
    @JvmField
    @NotNull
    public final ModeSetting style;
    @JvmField
    @NotNull
    public final ModeSetting scale;
    @Nullable
    private static ClickGui companionInstance;

    public ClickGui() {
        super("Click GUI", "Открывает клик-меню клиента.", Category.DISPLAY);
        this.style = (ModeSetting)this.register((Setting)new ModeSetting("Стиль меню", "Чёрная дыра - новое меню в стиле неба Ambience. Классика - прежнее меню.", STYLE_HORIZON, STYLE_HORIZON, STYLE_CLASSIC));
        String[] stringArray = UiScale.LABELS;
        this.scale = (ModeSetting)this.register((Setting)new ModeSetting("Масштаб меню", "Размер клик-меню. Внутри меню: Ctrl + колесо или Ctrl + \"-\" / \"=\".", UiScale.defaultLabel(), Arrays.copyOf(stringArray, stringArray.length)));
        this.setBind(KeyBind.Companion.keyboard(344));
        companionInstance = this;
        this.scale.setChangeListener(() -> ClickGui._init_$lambda$0(this));
    }

    /** true - открывать новое меню Horizon (чёрная дыра), false - классическое UI. */
    public boolean useHorizon() {
        return !this.style.is(STYLE_CLASSIC);
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    private static final void _init_$lambda$0(ClickGui this$0) {
        UiScale.select(this$0.scale.getValue());
    }

    @JvmStatic
    @Nullable
    public static final ClickGui getInstance() {
        return Companion.getInstance();
    }

    public static final class Companion {
        private Companion() {
        }

        @JvmStatic
        @Nullable
        public final ClickGui getInstance() {
            return companionInstance;
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

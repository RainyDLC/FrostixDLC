package rtx.kimiko.api.modules;

import org.jetbrains.annotations.NotNull;

public enum SubCategory {
    // Combat
    PVP("PVP"),
    UTILS("Utils"),

    // Visuals
    VISUALS("Visuals"),
    DISPLAY("Display");

    @NotNull
    private final String displayName;

    SubCategory(@NotNull String displayName) {
        this.displayName = displayName;
    }

    @NotNull
    public final String getDisplayName() {
        return this.displayName;
    }

    public final char getIconChar() {
        return switch (this) {
            case PVP -> 'a';
            case UTILS -> 'r';
            case VISUALS -> 'p';
            case DISPLAY -> 'j';
        };
    }

    @NotNull
    @Override
    public String toString() {
        return this.displayName;
    }

    @NotNull
    public static SubCategory[] getForCategory(@NotNull Category category) {
        if (category == Category.COMBAT) {
            return new SubCategory[]{PVP, UTILS};
        } else if (category == Category.VISUALS) {
            return new SubCategory[]{VISUALS, DISPLAY};
        }
        return new SubCategory[0];
    }
}

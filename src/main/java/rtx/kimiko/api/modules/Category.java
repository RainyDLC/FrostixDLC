package rtx.kimiko.api.modules;

import kotlin.enums.EnumEntries;
import kotlin.enums.EnumEntriesKt;
import org.jetbrains.annotations.NotNull;

public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    VISUALS("Visuals"),
    PLAYER("Player"),
    MISC("Misc"),
    DISPLAY("Display"),
    UTILS("Utils"),
    EVENTS("Events"),
    CONFIGS("Configs"),
    THEMES("Themes");

    @NotNull
    private final String displayName;

    Category(@NotNull String displayName) {
        this.displayName = displayName;
    }

    @NotNull
    public final String getDisplayName() {
        return this.displayName;
    }

    @NotNull
    @Override
    public String toString() {
        return this.displayName;
    }

    public final boolean hasSubCategories() {
        return this == COMBAT || this == VISUALS;
    }

    @NotNull
    public final SubCategory[] getSubCategories() {
        return SubCategory.getForCategory(this);
    }

    @NotNull
    public static EnumEntries<Category> getEntries() {
        return EnumEntriesKt.enumEntries(values());
    }
}

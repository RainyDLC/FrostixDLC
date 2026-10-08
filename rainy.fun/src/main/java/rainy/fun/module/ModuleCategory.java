package rainy.fun.module;

public enum ModuleCategory {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    RENDER("Render"),
    PLAYER("Player"),
    MISC("Miscellaneous"),
    CLIENT("Client");

    private final String title;

    ModuleCategory(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}

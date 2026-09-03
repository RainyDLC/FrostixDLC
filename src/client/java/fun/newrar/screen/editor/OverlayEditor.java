package fun.newrar.screen.editor;

public interface OverlayEditor {
    boolean isActive();

    void render(float width, float height, float mouseX, float mouseY, float parentAlpha);

    boolean mouseClicked(float mouseX, float mouseY, int button);

    boolean mouseReleased(int button);

    boolean mouseScrolled(float mouseX, float mouseY, double verticalAmount);

    void saveAndExit();

    void closeFromMenuRemoval();

    default void rawMouseButton(float mouseX, float mouseY, int button, boolean pressed) {
    }

    default void rawMouseMoved(float mouseX, float mouseY) {
    }

    default boolean mouseDragged(float deltaX, float deltaY) {
        return isActive();
    }
}


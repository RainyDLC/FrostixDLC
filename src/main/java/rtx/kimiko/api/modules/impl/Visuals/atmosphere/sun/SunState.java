package rtx.kimiko.api.modules.impl.Visuals.atmosphere.sun;

/**
 * Where the sun is on screen and how strongly it hits the lens.
 *
 * @param screenX    sun position in scaled GUI pixels
 * @param screenY    sun position in scaled GUI pixels
 * @param visibility 0..1, smoothed; includes occlusion, horizon, weather, screen-edge fade
 * @param alignment  0..1, 1 when the sun is dead center of the frame
 */
public record SunState(float screenX, float screenY, float visibility, float alignment) {

    public static final SunState HIDDEN = new SunState(0, 0, 0, 0);

    public boolean isVisible() {
        return visibility > 0.001f;
    }
}

package rtx.kimiko.api.ui.mainmenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import rtx.kimiko.utils.render.render2d.Render2D;
import rtx.kimiko.utils.render.render2d.gif.GifRenderer;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalTime;

public class MenuBackdrop {
    public static final String PRESET_VALLEY_MORNING = "mainmenu/morning_valley.png";
    public static final String PRESET_VALLEY_DAY = "mainmenu/day_valley.png";
    public static final String PRESET_VALLEY_SUNSET = "mainmenu/sunset_valley.png";
    public static final String PRESET_VALLEY_NIGHT = "mainmenu/night_valley.png";
    public static final String PRESET_FROSTIX = "mainmenu/background.png";

    public static final int PRESET_COUNT = 7;

    public static final String[] PRESET_NAMES = {
        "Авто (по реальному времени)",
        "Утро (Пшеничные поля)",
        "День (Пшеничные поля)",
        "Закат (Пшеничные поля)",
        "Ночь (Пшеничные поля)",
        "Frostix 4K Classic",
        "Свой фон (папка)"
    };

    private static Identifier customTextureId = null;
    private static String lastLoadedCustomPath = "";
    private static float currentParallaxX = 0.0f;
    private static float currentParallaxY = 0.0f;

    public static int getRealTimePhase() {
        int hour = LocalTime.now().getHour();
        if (hour >= 5 && hour < 11) {
            return 0; // Morning (Восход) 05:00 - 10:59
        } else if (hour >= 11 && hour < 18) {
            return 1; // Day (День) 11:00 - 17:59
        } else if (hour >= 18 && hour < 22) {
            return 2; // Sunset (Закат) 18:00 - 21:59
        } else {
            return 3; // Night (Ночь) 22:00 - 04:59
        }
    }

    public static String getRealTimePhaseName(int phase) {
        return switch (phase) {
            case 0 -> "Утро";
            case 1 -> "День";
            case 2 -> "Закат";
            default -> "Ночь";
        };
    }

    public static void render(DrawContext graphics, float width, float height, float mouseX, float mouseY, float dt) {
        MenuConfig cfg = MenuConfig.get();
        int activePhase = getRealTimePhase();

        // Parallax smooth interpolation
        float targetParallaxX = 0.0f;
        float targetParallaxY = 0.0f;
        if (cfg.mouseParallax) {
            targetParallaxX = ((mouseX / Math.max(1.0f, width)) - 0.5f) * 16.0f;
            targetParallaxY = ((mouseY / Math.max(1.0f, height)) - 0.5f) * 12.0f;
        }
        float lerpSpeed = Math.min(1.0f, dt * 4.5f);
        currentParallaxX += (targetParallaxX - currentParallaxX) * lerpSpeed;
        currentParallaxY += (targetParallaxY - currentParallaxY) * lerpSpeed;

        // Render base background image with slight overscan for parallax
        float overscanX = 24.0f;
        float overscanY = 18.0f;
        float bgX = -overscanX + currentParallaxX;
        float bgY = -overscanY + currentParallaxY;
        float bgW = width + overscanX * 2.0f;
        float bgH = height + overscanY * 2.0f;

        renderBackgroundImage(graphics, cfg, activePhase, bgX, bgY, bgW, bgH);

        // Left-side cinematic dark gradient (to give the text & frosted buttons incredible readability & pop)
        int darkAlpha = Math.max(0, Math.min(255, (int) (cfg.backgroundDarkness * 255.0f)));
        int darkLeft = (darkAlpha << 24);
        int darkTrans = 0x00000000;
        Render2D.rect(0, 0, width * 0.62f, height, 0.0f, darkLeft, darkTrans, darkTrans, darkLeft);

        // Top and bottom atmospheric vignettes
        int topVignette = ((int) (darkAlpha * 0.45f)) << 24;
        int botVignette = ((int) (darkAlpha * 0.65f)) << 24;
        Render2D.rect(0, 0, width, 55.0f, 0.0f, topVignette, topVignette, 0x00000000, 0x00000000);
        Render2D.rect(0, height - 60.0f, width, 60.0f, 0.0f, 0x00000000, 0x00000000, botVignette, botVignette);

        // Atmospheric floating fireflies/particles matching the current time of day
        if (cfg.particlesEnabled) {
            MenuParticles.updateAndRender(width, height, mouseX, mouseY, dt, cfg.particleCount, activePhase);
        }
    }

    private static void renderBackgroundImage(DrawContext graphics, MenuConfig cfg, int activePhase, float x, float y, float w, float h) {
        if (cfg.backgroundPreset == 6 && cfg.customBackgroundName != null && !cfg.customBackgroundName.isBlank()) {
            File customFile = new File(MenuConfig.getBackgroundsDir(), cfg.customBackgroundName);
            if (customFile.exists()) {
                String lower = cfg.customBackgroundName.toLowerCase();
                if (lower.endsWith(".gif")) {
                    GifRenderer.draw(graphics, x, y, w, h, 0.0f, customFile.getAbsolutePath(), 1.0f);
                    return;
                } else {
                    ensureCustomTextureLoaded(customFile);
                    if (customTextureId != null) {
                        Render2D.image(customTextureId.toString(), x, y, w, h, 0.0f, 0xFFFFFFFF);
                        return;
                    }
                }
            }
        }

        String texturePath;
        if (cfg.backgroundPreset == 0) {
            // Automatic real-life time of day
            texturePath = switch (activePhase) {
                case 0 -> PRESET_VALLEY_MORNING; // 05:00 - 10:59
                case 1 -> PRESET_VALLEY_DAY;     // 11:00 - 17:59
                case 2 -> PRESET_VALLEY_SUNSET;  // 18:00 - 21:59
                default -> PRESET_VALLEY_NIGHT;  // 22:00 - 04:59
            };
        } else {
            texturePath = switch (cfg.backgroundPreset) {
                case 1 -> PRESET_VALLEY_MORNING;
                case 2 -> PRESET_VALLEY_DAY;
                case 3 -> PRESET_VALLEY_SUNSET;
                case 4 -> PRESET_VALLEY_NIGHT;
                case 5 -> PRESET_FROSTIX;
                default -> PRESET_VALLEY_SUNSET;
            };
        }

        Render2D.image(texturePath, x, y, w, h, 0.0f, 0xFFFFFFFF);
    }

    private static void ensureCustomTextureLoaded(File file) {
        String path = file.getAbsolutePath();
        if (path.equals(lastLoadedCustomPath) && customTextureId != null) {
            return;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            NativeImage image = NativeImage.read(fis);
            customTextureId = Identifier.of("frostix", "custom_bg_" + Math.abs(path.hashCode()));
            MinecraftClient.getInstance().getTextureManager().registerTexture(
                customTextureId,
                new NativeImageBackedTexture(customTextureId::toString, image)
            );
            lastLoadedCustomPath = path;
        } catch (Throwable ignored) {
        }
    }

    public static void invalidate() {
        lastLoadedCustomPath = "";
        customTextureId = null;
    }
}

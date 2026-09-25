package fun.newrar.module.impl.render;

import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BooleanSetting;
import fun.newrar.module.api.settings.impl.ModeSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.other.Instance;
import fun.newrar.utils.render.GifTexture;
import fun.newrar.utils.render.RenderUtil;
import fun.newrar.utils.render.font.Font;
import fun.newrar.utils.render.font.Fonts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(
        name = "Gif",
        desc = "Кастомизация анимированных картинок в ClickGUI",
        category = Category.RENDER,
        autoEnabled = true
)
public class Gif extends Module {
    public static Gif getInstance() {
        return Instance.get(Gif.class);
    }

    public final ModeSetting mode = new ModeSetting(this, "Выбор", "Дефолт",
            "Дефолт", "Фурри 1", "Фурри 2", "Фурри 3", "Фурри 4", "Фурри 5");

    public final BooleanSetting inGui = new BooleanSetting(this, "Внутри меню", true);
    public final SliderSetting guiScale = new SliderSetting(this, "Размер в меню", 1.0F, 0.5F, 2.0F, 0.1F)
            .setVisible(inGui::getValue);

    public final BooleanSetting inBottomRight = new BooleanSetting(this, "Справа на экране", false);

    public final SliderSetting size = new SliderSetting(this, "Размер справа", 130.0F, 60.0F, 260.0F, 5.0F)
            .setVisible(inBottomRight::getValue);
    public final SliderSetting padding = new SliderSetting(this, "Отступ справа", 12.0F, 0.0F, 50.0F, 1.0F)
            .setVisible(inBottomRight::getValue);
    public final SliderSetting round = new SliderSetting(this, "Закругление", 10.0F, 0.0F, 25.0F, 1.0F)
            .setVisible(inBottomRight::getValue);

    public final BooleanSetting background = new BooleanSetting(this, "Подложка справа", true)
            .setVisible(inBottomRight::getValue);
    public final BooleanSetting glow = new BooleanSetting(this, "Свечение справа", true)
            .setVisible(inBottomRight::getValue);
    public final BooleanSetting label = new BooleanSetting(this, "Подпись справа", true)
            .setVisible(inBottomRight::getValue);

    private final Map<String, GifTexture> cache = new ConcurrentHashMap<>();
    private final Set<String> loading = ConcurrentHashMap.newKeySet();

    public Gif() {
        loadGifAsync("Дефолт");
        loadGifAsync("Фурри 1");
    }

    private String getTexturePath(String modeName) {
        return switch (modeName) {
            case "Дефолт" -> "textures/gui.gif";
            case "Фурри 1" -> "textures/gif/furry1.gif";
            case "Фурри 2" -> "textures/gif/furry2.gif";
            case "Фурри 3" -> "textures/gif/furry3.gif";
            case "Фурри 4" -> "textures/gif/furry4.gif";
            case "Фурри 5" -> "textures/gif/furry5.gif";
            default -> "textures/gui.gif";
        };
    }

    public GifTexture getGif(String modeName) {
        GifTexture tex = cache.get(modeName);
        if (tex != null) return tex;

        loadGifAsync(modeName);
        return null;
    }

    public void loadGifAsync(String modeName) {
        if (!loading.add(modeName)) return;

        CompletableFuture.runAsync(() -> {
            try {
                String relPath = getTexturePath(modeName);
                InputStream in = Gif.class.getResourceAsStream("/assets/client/" + relPath);
                if (in == null) {
                    in = Gif.class.getClassLoader().getResourceAsStream("assets/client/" + relPath);
                }
                if (in == null) {
                    var res = MinecraftClient.getInstance().getResourceManager()
                            .getResource(Identifier.of("client", relPath));
                    if (res.isPresent()) {
                        in = res.get().getInputStream();
                    }
                }

                if (in != null) {
                    try (InputStream stream = in) {
                        GifTexture gt = new GifTexture(stream);
                        cache.put(modeName, gt);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                loading.remove(modeName);
            }
        });
    }

    public void renderInGui(float screenWidth, float screenHeight, float globalAnim) {
        if (!isEnabled() || !inBottomRight.getValue() || globalAnim <= 0.01F) return;

        GifTexture tex = getGif(mode.getValue());
        if (tex == null || !tex.isLoaded()) return;

        float targetSize = size.getValue();
        float texW = Math.max(1, tex.getWidth());
        float texH = Math.max(1, tex.getHeight());
        float aspect = texW / texH;

        float gifW, gifH;
        if (aspect >= 1.0F) {
            gifW = targetSize;
            gifH = targetSize / aspect;
        } else {
            gifH = targetSize;
            gifW = targetSize * aspect;
        }

        float pad = padding.getValue();
        float r = round.getValue();
        boolean hasLabel = label.getValue();
        float innerMargin = 5.0F;
        float labelH = hasLabel ? 12.0F : 0.0F;

        float cardW = gifW + innerMargin * 2.0F;
        float cardH = gifH + innerMargin * 2.0F + labelH;

        float slideY = (1.0F - globalAnim) * 18.0F;
        float cardX = screenWidth - cardW - pad;
        float cardY = screenHeight - cardH - pad + slideY;

        if (glow.getValue()) {
            RenderUtil.Render2D.glow(cardX, cardY, cardW, cardH,
                    ColorUtil.multAlpha(ColorUtil.client(), 0.35F * globalAnim), r, 12, 1);
        }

        if (background.getValue()) {
            RenderUtil.Blur.blur(cardX, cardY, cardW, cardH, globalAnim, r, ColorUtil.getColor(0, 0));
            RenderUtil.Render2D.rect(cardX, cardY, cardW, cardH,
                    ColorUtil.getColor(16, 17, 22, 0.75F * globalAnim), r);
            RenderUtil.Render2D.outline(cardX, cardY, cardW, cardH, 0.7F,
                    ColorUtil.multAlpha(ColorUtil.client(), 0.45F * globalAnim), r);
        }

        float gifX = cardX + innerMargin;
        float gifY = cardY + innerMargin;
        float gifRadius = Math.max(0F, r - 2F);

        tex.render(gifX, gifY, gifW, gifH, ColorUtil.getColor(255, 255, 255, globalAnim), gifRadius);

        if (hasLabel) {
            Font font = Fonts.sf_regular;
            float textY = gifY + gifH + 2.5F;
            font.drawCentered(mode.getValue(), cardX + cardW / 2.0F, textY, 5.5F,
                    ColorUtil.getColor(220, 225, 235, 0.9F * globalAnim));
        }
    }
}

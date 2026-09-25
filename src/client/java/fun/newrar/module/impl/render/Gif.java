package fun.newrar.module.impl.render;

import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BooleanSetting;
import fun.newrar.module.api.settings.impl.ModeSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.utils.other.Instance;
import fun.newrar.utils.colors.ColorUtil;
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
        desc = "Отображает анимированные GIF в ClickGUI",
        category = Category.RENDER,
        autoEnabled = true
)
public class Gif extends Module {
    public static Gif getInstance() {
        return Instance.get(Gif.class);
    }

    public final ModeSetting mode = new ModeSetting(this, "Выбор", "Фурри 1",
            "Фурри 1", "Фурри 2", "Фурри 3", "Фурри 4", "Фурри 5");

    public final SliderSetting size = new SliderSetting(this, "Размер", 130.0F, 60.0F, 260.0F, 5.0F);
    public final SliderSetting padding = new SliderSetting(this, "Отступ", 12.0F, 0.0F, 50.0F, 1.0F);
    public final SliderSetting round = new SliderSetting(this, "Закругление", 10.0F, 0.0F, 25.0F, 1.0F);

    public final BooleanSetting background = new BooleanSetting("Подложка", true);
    public final BooleanSetting glow = new BooleanSetting("Свечение", true);
    public final BooleanSetting label = new BooleanSetting("Подпись", true);

    private final Map<String, GifTexture> cache = new ConcurrentHashMap<>();
    private final Set<String> loading = ConcurrentHashMap.newKeySet();

    public Gif() {
        // Pre-load the first GIF asynchronously
        loadGifAsync("Фурри 1");
    }

    private String getFileName(String modeName) {
        return switch (modeName) {
            case "Фурри 1" -> "furry1.gif";
            case "Фурри 2" -> "furry2.gif";
            case "Фурри 3" -> "furry3.gif";
            case "Фурри 4" -> "furry4.gif";
            case "Фурри 5" -> "furry5.gif";
            default -> "furry1.gif";
        };
    }

    public GifTexture getGif(String modeName) {
        GifTexture tex = cache.get(modeName);
        if (tex != null) return tex;

        loadGifAsync(modeName);
        return null;
    }

    private void loadGifAsync(String modeName) {
        if (!loading.add(modeName)) return;

        CompletableFuture.runAsync(() -> {
            try {
                String fileName = getFileName(modeName);
                InputStream in = Gif.class.getResourceAsStream("/assets/client/textures/gif/" + fileName);
                if (in == null) {
                    in = Gif.class.getClassLoader().getResourceAsStream("assets/client/textures/gif/" + fileName);
                }
                if (in == null) {
                    var res = MinecraftClient.getInstance().getResourceManager()
                            .getResource(Identifier.of("client", "textures/gif/" + fileName));
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
        if (!isEnabled() || globalAnim <= 0.01F) return;

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

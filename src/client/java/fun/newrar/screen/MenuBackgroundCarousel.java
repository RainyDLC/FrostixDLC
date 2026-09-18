package fun.newrar.screen;

import fun.newrar.utils.animation.Animation;
import fun.newrar.utils.animation.Easings;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.math.MathUtil;
import fun.newrar.utils.render.Draw;
import fun.newrar.utils.render.GifTexture;
import fun.newrar.utils.render.RenderUtil;
import fun.newrar.utils.render.Scissor;
import fun.newrar.utils.render.font.Fonts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MenuBackgroundCarousel {

    private static final String PREFS_PATH = "rainydlc/menu_bg.txt";
    private static final int GIF_DECODE_WIDTH = 480;

    private final List<BgEntry> entries = new ArrayList<>();
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private int selectedIndex = 0;
    private boolean carouselOpen = false;
    private final Animation openAnim = new Animation();
    private final Animation shiftAnim = new Animation();

    private float panelX, panelY, panelW, panelH;
    private float gearX, gearY, gearSize;
    private float leftArrowX, leftArrowY, rightArrowX, rightArrowY, arrowSize;

    private static class BgEntry {
        final String nameRu, nameEn;
        final Identifier staticId;
        final String gifPath;
        GifTexture gif;
        boolean loadStarted;

        BgEntry(String nameRu, String nameEn, Identifier staticId, String gifPath) {
            this.nameRu = nameRu;
            this.nameEn = nameEn;
            this.staticId = staticId;
            this.gifPath = gifPath;
        }

        String name() {
            return fun.newrar.lang.Lang.pick(nameRu, nameEn);
        }

        boolean isAnimated() { return gifPath != null; }

        void ensureLoaded(MinecraftClient mc) {
            if (loadStarted || gifPath == null) return;
            loadStarted = true;
            new Thread(() -> {
                try {
                    var res = mc.getResourceManager().getResource(Identifier.of("client", gifPath));
                    if (res.isPresent()) {
                        try (InputStream in = res.get().getInputStream()) {
                            gif = new GifTexture(in, GIF_DECODE_WIDTH);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, "MenuBg-Loader").start();
        }

        Identifier currentFrame() {
            if (gif != null && gif.isLoaded()) {
                Identifier f = gif.currentFrameId();
                if (f != null) return f;
            }
            return staticId;
        }
    }

    public MenuBackgroundCarousel() {
        entries.add(new BgEntry("Классика", "Classic",
                Identifier.of("client", "textures/bg/bg_classic.png"), null));
        entries.add(new BgEntry("Дождливый лес", "Rainy Forest",
                Identifier.of("client", "textures/bg/bg_classic.png"), "textures/bg/bg_rain_forest.gif"));
        entries.add(new BgEntry("Ливень", "Downpour",
                Identifier.of("client", "textures/bg/bg_classic.png"), "textures/bg/bg_downpour.gif"));

        selectedIndex = Math.max(0, Math.min(loadSelection(), entries.size() - 1));
        shiftAnim.set(0);
        openAnim.set(0);
        entries.get(selectedIndex).ensureLoaded(mc);
    }

    public Identifier currentBackground() {
        return entries.get(selectedIndex).currentFrame();
    }
    public void render(int screenWidth, int screenHeight, float alpha, double mouseX, double mouseY) {
        openAnim.update();
        shiftAnim.update();

        drawGear(screenWidth, alpha, mouseX, mouseY);

        float open = openAnim.get();
        if (open <= 0.01F) return;

        BgEntry sel = entries.get(selectedIndex);
        float labelW = Fonts.sf_regular.getWidth(sel.name(), 5.5F) + 26F;
        panelW = Math.max(labelW, 100F);
        panelH = 92F;
        panelX = screenWidth / 2F - panelW / 2F;
        panelY = 40F;

        float a = alpha * open;

        RenderUtil.Blur.blur(panelX, panelY, panelW, panelH, 1, 8F, ColorUtil.getColor(6, 11, 22, a * 0.5F));
        Draw.rect(panelX, panelY, panelW, panelH, ColorUtil.getColor(10, 16, 30, a * 0.55F), 8F);
        Draw.outline(panelX, panelY, panelW, panelH, 0.6F, ColorUtil.getColor(85, 135, 200, a * 0.22F), 8F);

        Fonts.sf_regular.drawCentered(L("Задний фон", "Background"), panelX + panelW / 2F, panelY + 5F, 5F,
                ColorUtil.getColor(140, 175, 215, a * 0.6F));

        float cardW = panelW - 52F;
        float cardH = 54F;
        float cardX = panelX + panelW / 2F - cardW / 2F;
        float cardY = panelY + 16F;

        arrowSize = 10F;
        leftArrowX = panelX + 7F;
        rightArrowX = panelX + panelW - 7F - arrowSize;
        leftArrowY = cardY + cardH / 2F - arrowSize / 2F;
        rightArrowY = leftArrowY;

        drawArrow(leftArrowX, leftArrowY, arrowSize, true, a, mouseX, mouseY);
        drawArrow(rightArrowX, rightArrowY, arrowSize, false, a, mouseX, mouseY);

        int n = entries.size();
        float spacing = cardW * 0.62F;
        float shift = (float) shiftAnim.getValue() - selectedIndex;

        Scissor.enable(cardX - 2, cardY - 2, cardW + 4, cardH + 4, 2);
        for (int i = 0; i < n; i++) {
            float rel = i + shift;
            if (Math.abs(rel) > 1.8F) continue;

            float cx = cardX + cardW / 2F + rel * spacing;
            float sc = 1F - Math.min(0.4F, Math.abs(rel) * 0.34F);
            float w = cardW * sc;
            float h = cardH * sc;
            float x = cx - w / 2F;
            float y = cardY + (cardH - h) / 2F;

            float cardAlpha = a * (1F - Math.min(0.72F, Math.abs(rel) * 0.62F));

            BgEntry e = entries.get(i);
            if (Math.abs(rel) < 1.2F) e.ensureLoaded(mc);

            Draw.rect(x - 1F, y - 1F, w + 2F, h + 2F, ColorUtil.getColor(4, 8, 16, cardAlpha * 0.9F), 5F);
            Identifier frame = e.currentFrame();
            if (frame != null) {
                Draw.texture(frame, x, y, w, h, 1.2F, 4F, ColorUtil.getColor(255, 255, 255, cardAlpha));
            }
            Draw.outline(x, y, w, h, 0.5F,
                    Math.abs(rel) < 0.5F
                            ? ColorUtil.getColor(120, 180, 240, cardAlpha * 0.55F)
                            : ColorUtil.getColor(80, 120, 175, cardAlpha * 0.2F), 4F);

            if (Math.abs(rel) < 0.5F && e.isAnimated()) {
                Fonts.sf_regular.drawCentered("GIF", x + w / 2F, y + h - 8F, 4F,
                        ColorUtil.getColor(180, 220, 255, cardAlpha * 0.75F));
            }
        }
        Scissor.disable();

        Fonts.sf_regular.drawCentered(sel.name(), panelX + panelW / 2F, cardY + cardH + 6F, 5.5F,
                ColorUtil.getColor(200, 225, 250, a * 0.9F));

        float dotY = panelY + panelH - 7F;
        float dotSpacing = 7F;
        float dotsW = (n - 1) * dotSpacing;
        for (int i = 0; i < n; i++) {
            float dx = panelX + panelW / 2F - dotsW / 2F + i * dotSpacing;
            boolean active = i == selectedIndex;
            Draw.rect(dx - 1.25F, dotY - 1.25F, 2.5F, 2.5F,
                    active ? ColorUtil.getColor(120, 190, 255, a * 0.95F)
                            : ColorUtil.getColor(110, 145, 190, a * 0.3F), 1.25F);
        }
    }
    private void drawGear(int screenWidth, float alpha, double mouseX, double mouseY) {
        gearSize = 16F;
        gearX = screenWidth - gearSize - 14F;
        gearY = 32F;

        boolean hov = MathUtil.isHovered((float) mouseX, (float) mouseY, gearX, gearY, gearSize, gearSize);
        float boost = hov ? 0.18F : 0F;

        RenderUtil.Blur.blur(gearX, gearY, gearSize, gearSize, 1, 5F, ColorUtil.getColor(8, 14, 28, alpha * 0.45F));
        Draw.rect(gearX, gearY, gearSize, gearSize, ColorUtil.getColor(12, 20, 38, alpha * (0.42F + boost)), 5F);
        Draw.outline(gearX, gearY, gearSize, gearSize, 0.6F,
                ColorUtil.getColor(90, 145, 210, alpha * (0.22F + (hov ? 0.3F : 0F))), 5F);
        Fonts.rainydlc_2.drawCentered("h", gearX + gearSize / 2F, gearY + gearSize / 2F - 3.2F, 6.5F,
                hov ? ColorUtil.getColor(150, 210, 255, alpha * 0.95F)
                        : ColorUtil.getColor(130, 175, 225, alpha * 0.6F));
    }

    private void drawArrow(float x, float y, float size, boolean left, float a, double mouseX, double mouseY) {
        boolean hov = MathUtil.isHovered((float) mouseX, (float) mouseY, x, y, size, size);
        Draw.rect(x, y, size, size, ColorUtil.getColor(14, 22, 40, a * (hov ? 0.7F : 0.4F)), 4F);
        Draw.outline(x, y, size, size, 0.5F,
                ColorUtil.getColor(90, 145, 210, a * (hov ? 0.55F : 0.2F)), 4F);
        Fonts.sf_medium.drawCentered(left ? "<" : ">", x + size / 2F, y + size / 2F - 3F, 5.5F,
                hov ? ColorUtil.getColor(160, 215, 255, a) : ColorUtil.getColor(140, 180, 220, a * 0.7F));
    }

    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (button != 0) return false;

        if (MathUtil.isHovered(mouseX, mouseY, gearX, gearY, gearSize, gearSize)) {
            carouselOpen = !carouselOpen;
            openAnim.run(carouselOpen ? 1 : 0, 0.3F, Easings.QUAD_OUT);
            if (carouselOpen) {
                entries.get(selectedIndex).ensureLoaded(mc);
                int prev = Math.floorMod(selectedIndex - 1, entries.size());
                int next = Math.floorMod(selectedIndex + 1, entries.size());
                entries.get(prev).ensureLoaded(mc);
                entries.get(next).ensureLoaded(mc);
            }
            return true;
        }

        if (openAnim.get() <= 0.01F) return false;

        if (MathUtil.isHovered(mouseX, mouseY, leftArrowX, leftArrowY, arrowSize, arrowSize)) {
            step(-1);
            return true;
        }
        if (MathUtil.isHovered(mouseX, mouseY, rightArrowX, rightArrowY, arrowSize, arrowSize)) {
            step(1);
            return true;
        }
        return MathUtil.isHovered(mouseX, mouseY, panelX, panelY, panelW, panelH);
    }

    public boolean mouseScrolled(float mouseX, float mouseY, double vertical) {
        if (openAnim.get() <= 0.01F) return false;
        if (!MathUtil.isHovered(mouseX, mouseY, panelX, panelY, panelW, panelH)) return false;
        step(vertical > 0 ? -1 : 1);
        return true;
    }

    private void step(int dir) {
        selectedIndex = Math.floorMod(selectedIndex + dir, entries.size());
        shiftAnim.run(selectedIndex, 0.35F, Easings.QUAD_OUT);
        entries.get(selectedIndex).ensureLoaded(mc);
        int next = Math.floorMod(selectedIndex + dir, entries.size());
        entries.get(next).ensureLoaded(mc);
        saveSelection();
    }

    private int loadSelection() {
        try {
            java.nio.file.Path p = mc.runDirectory.toPath().resolve(PREFS_PATH);
            if (java.nio.file.Files.exists(p)) {
                return Integer.parseInt(java.nio.file.Files.readString(p).trim());
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private void saveSelection() {
        try {
            java.nio.file.Path p = mc.runDirectory.toPath().resolve(PREFS_PATH);
            java.nio.file.Files.createDirectories(p.getParent());
            java.nio.file.Files.writeString(p, String.valueOf(selectedIndex));
        } catch (Exception ignored) {}
    }

    private static String L(String ru, String en) {
        return fun.newrar.lang.Lang.pick(ru, en);
    }
}

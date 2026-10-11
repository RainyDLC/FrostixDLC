package dev.hatek.client.ui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hatek.client.cosmetic.SwordModel;
import dev.hatek.client.cosmetic.SwordRenderer;
import dev.hatek.client.cosmetic.Swords;
import dev.hatek.client.module.impl.render.ClickGui;
import dev.hatek.client.module.impl.render.Cosmetics;
import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Anim;
import dev.hatek.client.ui.render.Fonts;
import dev.hatek.client.ui.render.Render2D;
import dev.hatek.client.ui.render.TexCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Колесо косметики: по кругу — мечи, в центре — моделька игрока в полный рост
 * с выбранным (или наведённым) мечом в руке.
 * Удерживай бинд и отпусти над мечом — или кликни по нему.
 */
public final class CosmeticsScreen extends Screen {
    private static final long HOLD_MS = 250L;

    private final int bindKey;
    private final long openedAt = System.currentTimeMillis();
    private final String[] names = Swords.names();
    private final List<Anim> hover = new ArrayList<>();
    private final Anim openAnim = new Anim(0.0f, 14.0f);
    private final Anim labelAnim = new Anim(0.0f, 16.0f);
    private long lastFrame = System.nanoTime();
    private boolean holdMode = true;
    private boolean closing;
    private int hovered = -1;

    public CosmeticsScreen(int bindKey) {
        super(Component.literal("Cosmetics"));
        this.bindKey = bindKey;
        for (int i = 0; i < this.names.length; i++) {
            this.hover.add(new Anim(0.0f, 18.0f));
        }
    }

    @Override
    protected void init() {
        this.openAnim.snap(0.0f);
        this.openAnim.to(1.0f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gg, int mouseX, int mouseY, float partialTick) {
        if (ClickGui.blur()) {
            extractBlurredBackground(gg);
        }
    }

    // ------------------------------------------------------------------ геометрия

    private static float unit() {
        return Render2D.screenHeight() / 1080.0f;
    }

    private static float cx() {
        return Render2D.screenWidth() / 2.0f;
    }

    private static float cy() {
        return Render2D.screenHeight() / 2.0f;
    }

    private static float innerRadius() {
        return 215.0f * unit();
    }

    private static float ringRadius() {
        return 330.0f * unit();
    }

    private static float cellSize() {
        return 92.0f * unit();
    }

    private float angleOf(int index) {
        return (float) (-Math.PI / 2.0 + index * Math.PI * 2.0 / this.names.length);
    }

    private int pick(double mx, double my) {
        double dx = mx - cx();
        double dy = my - cy();
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < innerRadius() * 0.92 || dist > ringRadius() + cellSize()) {
            return -1;
        }
        double angle = Math.atan2(dy, dx) + Math.PI / 2.0;
        double step = Math.PI * 2.0 / this.names.length;
        int index = (int) Math.floor((angle + step / 2.0) / step);
        return Math.floorMod(index, this.names.length);
    }

    // ------------------------------------------------------------------ рендер

    @Override
    public void extractRenderState(GuiGraphicsExtractor gg, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        Anim.beginFrame((now - this.lastFrame) / 1_000_000_000.0f);
        this.lastFrame = now;

        handleHold();

        this.openAnim.to(this.closing ? 0.0f : 1.0f);
        float open = this.openAnim.get();
        if (this.closing && open < 0.01f) {
            this.minecraft.gui.setScreen(null);
            return;
        }
        float eased = this.closing ? open : Anim.easeOut(open);

        double mx = Render2D.mouseX();
        double my = Render2D.mouseY();
        this.hovered = this.closing ? this.hovered : pick(mx, my);

        float cx = cx();
        float cy = cy();
        float ri = innerRadius();

        Render2D.begin(gg);
        Render2D.pushAlpha(eased);
        Render2D.rect(gg, 0, 0, Render2D.screenWidth(), Render2D.screenHeight(), 0x66000000);
        Render2D.pushScale(gg, cx, cy, 0.9f + 0.1f * eased);

        // Центральный диск со стеклом.
        Render2D.circle(gg, cx - ri, cy - ri, ri * 2.0f, ClickGui.blur() ? Style.PANEL_BLUR : Style.PANEL_GLASS);
        int d = Math.round(ri * 2.0f);
        Render2D.texture(gg, TexCache.glassRim(d, d, d / 2.0f), cx - ri, cy - ri, d, d, Style.WHITE);
        Render2D.texture(gg, TexCache.glow(Math.round(ri * 1.6f)), cx - ri * 0.8f, cy - ri * 0.8f,
                Math.round(ri * 1.6f), Math.round(ri * 1.6f),
                Render2D.withAlpha(previewAccent(), 0.18f));

        renderRing(gg, mx, my, cx, cy);
        renderLabels(gg, cx, cy, ri);

        Render2D.popTransform(gg);
        Render2D.popAlpha();
        Render2D.end(gg);

        if (eased > 0.6f) {
            renderPlayer(gg, cx, cy, ri, mouseX, mouseY);
        }
    }

    private int previewAccent() {
        SwordModel model = previewModel();
        return model == null ? Style.accent() : model.iconColor();
    }

    private SwordModel previewModel() {
        String name = this.hovered >= 0 ? this.names[this.hovered] : Cosmetics.selectedName();
        return Swords.byName(name);
    }

    private void renderRing(GuiGraphicsExtractor gg, double mx, double my, float cx, float cy) {
        float cell = cellSize();
        float radius = ringRadius();
        String selected = Cosmetics.selectedName();
        for (int i = 0; i < this.names.length; i++) {
            float a = angleOf(i);
            Anim anim = this.hover.get(i);
            anim.to(i == this.hovered);
            float k = anim.get();
            float r = radius + k * 10.0f * unit();
            float x = cx + (float) Math.cos(a) * r;
            float y = cy + (float) Math.sin(a) * r;
            float size = cell * (1.0f + 0.12f * k);
            float left = x - size / 2.0f;
            float top = y - size / 2.0f;
            boolean isSelected = this.names[i].equalsIgnoreCase(selected);

            SwordModel model = Swords.byName(this.names[i]);
            int accent = model == null ? Style.accent() : model.iconColor();
            Render2D.round(gg, left, top, size, size, size * 0.28f, Style.PANEL_GLASS);
            Render2D.round(gg, left, top, size, size, size * 0.28f,
                    Render2D.lerp(Style.WHITE_02, Render2D.withAlpha(accent, 0.22f), Math.max(k, isSelected ? 0.8f : 0.0f)));
            Render2D.texture(gg, TexCache.glassRim(Math.round(size), Math.round(size), size * 0.28f),
                    left, top, size, size, Style.WHITE);
            if (isSelected) {
                Render2D.round(gg, x - 4.0f * unit(), top + size - 7.0f * unit(), 8.0f * unit(), 2.0f * unit(), 1.0f,
                        Style.accent());
            }

            if (model == null) {
                Fonts.body().drawCentered(gg, "Vanilla", x, Fonts.body().centeredBaseline(top, size),
                        Render2D.lerp(Style.WHITE_45, Style.WHITE, k));
            } else {
                SwordRenderer.icon(gg, model, x, y, size * 0.72f);
                if (model.animated()) {
                    Render2D.circle(gg, left + size - 12.0f * unit(), top + 6.0f * unit(), 5.0f * unit(),
                            Render2D.withAlpha(accent, 0.6f + 0.4f * (float) Math.sin(SwordRenderer.time() * 4.0f)));
                }
            }
        }
    }

    private void renderLabels(GuiGraphicsExtractor gg, float cx, float cy, float ri) {
        String name = this.hovered >= 0 ? this.names[this.hovered] : Cosmetics.selectedName();
        SwordModel model = Swords.byName(name);
        this.labelAnim.to(1.0f);
        float base = cy + ri + 34.0f * unit();
        Fonts.title().drawCentered(gg, name, cx, base, Style.WHITE);
        String sub = model == null ? "Standard sword" : model.animated() ? "Animated" : "Custom 3D sword";
        Fonts.body().drawCentered(gg, sub, cx, base + 18.0f, model != null && model.animated()
                ? Render2D.withAlpha(model.iconColor(), 1.0f) : Style.WHITE_45);
        Fonts.body().drawCentered(gg, "Click a sword to equip  ·  Esc to close", cx,
                Render2D.screenHeight() - 40.0f * unit(), Style.WHITE_25);
        Fonts.title().drawCentered(gg, "Cosmetics", cx, cy - ringRadius() - cellSize() - 6.0f * unit(), Style.WHITE);
    }

    /** Моделька игрока в полный рост; если в руке не меч — на время кадра даём ему меч. */
    private void renderPlayer(GuiGraphicsExtractor gg, float cx, float cy, float ri, int mouseX, int mouseY) {
        if (this.minecraft.player == null) {
            return;
        }
        float g = Render2D.guiScale();
        int x1 = Math.round((cx - ri * 0.6f) / g);
        int y1 = Math.round((cy - ri * 0.86f) / g);
        int x2 = Math.round((cx + ri * 0.6f) / g);
        int y2 = Math.round((cy + ri * 0.80f) / g);
        int size = Math.round((y2 - y1) * 0.42f);

        SwordModel preview = previewModel();
        Inventory inventory = this.minecraft.player.getInventory();
        int slot = inventory.getSelectedSlot();
        ItemStack original = inventory.getItem(slot);
        boolean swap = preview != null && !original.is(ItemTags.SWORDS);
        SwordRenderer.beginPreview(preview);
        try {
            if (swap) {
                inventory.setItem(slot, new ItemStack(Items.NETHERITE_SWORD));
            }
            InventoryScreen.extractEntityInInventoryFollowsMouse(gg, x1, y1, x2, y2, size, 0.0625f,
                    mouseX, mouseY, this.minecraft.player);
        } finally {
            if (swap) {
                inventory.setItem(slot, original);
            }
            SwordRenderer.endPreview();
        }
    }

    // ------------------------------------------------------------------ ввод

    private void handleHold() {
        if (this.closing || this.bindKey == GLFW.GLFW_KEY_UNKNOWN) {
            return;
        }
        boolean down = InputConstants.isKeyDown(this.minecraft.getWindow(), this.bindKey);
        long held = System.currentTimeMillis() - this.openedAt;
        if (!down && this.holdMode) {
            if (held < HOLD_MS) {
                // Быстрое нажатие — колесо остаётся открытым, выбор кликом.
                this.holdMode = false;
            } else {
                if (this.hovered >= 0) {
                    choose(this.hovered);
                }
                close();
            }
        }
    }

    private void choose(int index) {
        String name = this.names[index];
        Cosmetics.select(name);
        Cosmetics module = Cosmetics.instance();
        if (module != null && Swords.byName(name) != null && !module.isEnabled()) {
            module.setEnabled(true);
        }
    }

    private void close() {
        this.closing = true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.closing) {
            return true;
        }
        int index = pick(Render2D.mouseX(), Render2D.mouseY());
        if (index >= 0 && event.button() == 0) {
            choose(index);
            close();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE || (!this.holdMode && key == this.bindKey)) {
            close();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        close();
    }
}

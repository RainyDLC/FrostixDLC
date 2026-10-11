package dev.hatek.client.ui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hatek.client.cosmetic.MajesticaSwords;
import dev.hatek.client.cosmetic.SwordCosmetics;
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
 * Колесо косметики: по кругу — 3D-мечи из Blades of Majestica (по страницам),
 * в центре — моделька игрока в полный рост с выбранным (или наведённым) мечом в руке.
 * Удерживай бинд и отпусти над мечом — или кликни по нему. Колёсико мыши листает страницы.
 */
public final class CosmeticsScreen extends Screen {
    private static final long HOLD_MS = 250L;
    private static final int PER_PAGE = 12;
    private static final String VANILLA = "Vanilla";
    private static int page;

    private final int bindKey;
    private final long openedAt = System.currentTimeMillis();
    private final List<String> all = new ArrayList<>();
    private final List<Anim> hover = new ArrayList<>();
    private final Anim pageAnim = new Anim(1.0f, 12.0f);
    private final Anim openAnim = new Anim(0.0f, 14.0f);
    private final Anim labelAnim = new Anim(0.0f, 16.0f);
    private long lastFrame = System.nanoTime();
    private boolean holdMode = true;
    private boolean closing;
    private int hovered = -1;

    public CosmeticsScreen(int bindKey) {
        super(Component.literal("Cosmetics"));
        this.bindKey = bindKey;
        this.all.add(VANILLA);
        this.all.addAll(MajesticaSwords.reload());
        for (int i = 0; i < PER_PAGE; i++) {
            this.hover.add(new Anim(0.0f, 18.0f));
        }
        // Открываемся на странице с выбранным мечом.
        int selected = this.all.indexOf(Cosmetics.selectedName());
        if (selected > 0) {
            page = selected / PER_PAGE;
        }
        page = Math.floorMod(page, pages());
    }

    private int pages() {
        return Math.max(1, (this.all.size() + PER_PAGE - 1) / PER_PAGE);
    }

    private int pageCount() {
        return Math.min(PER_PAGE, this.all.size() - page * PER_PAGE);
    }

    private String entry(int slot) {
        int index = page * PER_PAGE + slot;
        return index < this.all.size() ? this.all.get(index) : null;
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
        return (float) (-Math.PI / 2.0 + index * Math.PI * 2.0 / PER_PAGE);
    }

    private int pick(double mx, double my) {
        double dx = mx - cx();
        double dy = my - cy();
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < innerRadius() * 0.92 || dist > ringRadius() + cellSize()) {
            return -1;
        }
        double angle = Math.atan2(dy, dx) + Math.PI / 2.0;
        double step = Math.PI * 2.0 / PER_PAGE;
        int index = Math.floorMod((int) Math.floor((angle + step / 2.0) / step), PER_PAGE);
        return index < pageCount() ? index : -1;
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
                Render2D.withAlpha(Style.accent(), 0.18f));

        this.pageAnim.to(1.0f);
        float appear = Anim.easeOut(this.pageAnim.get());
        Render2D.pushAlpha(appear);
        Render2D.pushScale(gg, cx, cy, 0.94f + 0.06f * appear);
        renderRing(gg, cx, cy);
        Render2D.popTransform(gg);
        Render2D.popAlpha();
        renderLabels(gg, cx, cy, ri);

        Render2D.popTransform(gg);
        Render2D.popAlpha();
        Render2D.end(gg);

        if (eased > 0.6f) {
            renderPlayer(gg, cx, cy, ri, mouseX, mouseY);
        }
    }

    /** Название наведённого меча, иначе выбранного; null — обычный меч. */
    private String previewName() {
        String name = this.hovered >= 0 ? entry(this.hovered) : Cosmetics.selectedName();
        return name == null || name.isBlank() || VANILLA.equals(name) ? null : name;
    }

    private static ItemStack icon(String name) {
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        return name == null || VANILLA.equals(name) ? sword : SwordCosmetics.named(sword, name);
    }

    private void renderRing(GuiGraphicsExtractor gg, float cx, float cy) {
        float cell = cellSize();
        float radius = ringRadius();
        String selected = Cosmetics.selectedName();
        for (int i = 0; i < pageCount(); i++) {
            String name = entry(i);
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
            boolean isSelected = VANILLA.equals(name) ? selected == null || selected.isBlank()
                    : name.equalsIgnoreCase(selected);

            Render2D.round(gg, left, top, size, size, size * 0.28f, Style.PANEL_GLASS);
            Render2D.round(gg, left, top, size, size, size * 0.28f,
                    Render2D.lerp(Style.WHITE_02, Render2D.withAlpha(Style.accent(), 0.22f),
                            Math.max(k, isSelected ? 0.8f : 0.0f)));
            Render2D.texture(gg, TexCache.glassRim(Math.round(size), Math.round(size), size * 0.28f),
                    left, top, size, size, Style.WHITE);
            if (isSelected) {
                Render2D.round(gg, x - 4.0f * unit(), top + size - 7.0f * unit(), 8.0f * unit(),
                        2.0f * unit(), 1.0f, Style.accent());
            }

            // Иконка меча из пака (тот же рендер предметов, что в инвентаре).
            float iconSize = size * 0.62f;
            gg.pose().pushMatrix();
            gg.pose().translate(x - iconSize / 2.0f, y - iconSize / 2.0f);
            gg.pose().scale(iconSize / 16.0f, iconSize / 16.0f);
            gg.item(icon(name), 0, 0);
            gg.pose().popMatrix();
        }
    }

    private void renderLabels(GuiGraphicsExtractor gg, float cx, float cy, float ri) {
        String name = previewName();
        float base = cy + ri + 34.0f * unit();
        Fonts.title().drawCentered(gg, name == null ? VANILLA : name, cx, base, Style.WHITE);
        String sub = name == null ? "Standard sword" : "Blades of Majestica";
        Fonts.body().drawCentered(gg, sub, cx, base + 18.0f, Style.WHITE_45);

        float topY = cy - ringRadius() - cellSize() - 6.0f * unit();
        Fonts.title().drawCentered(gg, "Cosmetics", cx, topY, Style.WHITE);
        if (pages() > 1) {
            Fonts.body().drawCentered(gg, "< " + (page + 1) + " / " + pages() + " >", cx, topY + 20.0f,
                    Style.WHITE_45);
        }
        String hint = MajesticaSwords.available()
                ? "Click a sword to equip  \u00b7  Scroll to change page  \u00b7  Esc to close"
                : "Install and enable the Blades of Majestica resource pack: " + MajesticaSwords.PACK_URL;
        Fonts.body().drawCentered(gg, hint, cx, Render2D.screenHeight() - 40.0f * unit(),
                MajesticaSwords.available() ? Style.WHITE_25 : Style.accent());
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

        String preview = previewName();
        Inventory inventory = this.minecraft.player.getInventory();
        int slot = inventory.getSelectedSlot();
        ItemStack original = inventory.getItem(slot);
        boolean swap = preview != null && !original.is(ItemTags.SWORDS);
        SwordCosmetics.beginPreview(preview);
        try {
            if (swap) {
                inventory.setItem(slot, icon(preview));
            }
            InventoryScreen.extractEntityInInventoryFollowsMouse(gg, x1, y1, x2, y2, size, 0.0625f,
                    mouseX, mouseY, this.minecraft.player);
        } finally {
            if (swap) {
                inventory.setItem(slot, original);
            }
            SwordCosmetics.endPreview();
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

    private void choose(int slot) {
        String name = entry(slot);
        if (name == null) {
            return;
        }
        boolean vanilla = VANILLA.equals(name);
        Cosmetics.select(vanilla ? "" : name);
        Cosmetics module = Cosmetics.instance();
        if (module != null && !vanilla && !module.isEnabled()) {
            module.setEnabled(true);
        }
    }

    private void turnPage(int delta) {
        int next = Math.floorMod(page + delta, pages());
        if (next != page) {
            page = next;
            this.pageAnim.snap(0.0f);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0) {
            turnPage(scrollY < 0 ? 1 : -1);
        }
        return true;
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
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) {
            turnPage(1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) {
            turnPage(-1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        close();
    }
}

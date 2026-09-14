package fun.newrar.cosmetics.ui;

import fun.newrar.cosmetics.LocalCosmetics;
import fun.newrar.utils.animation.satoshi.Direction;
import fun.newrar.utils.animation.satoshi.EaseInOutQuad;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.render.Draw;
import fun.newrar.utils.render.Render2D;
import fun.newrar.utils.render.RenderUtil;
import fun.newrar.utils.render.Scissor;
import fun.newrar.utils.render.font.Fonts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class CosmeticsScreen extends Screen {
    private static final float PANEL_W = 540F;
    private static final float PANEL_H = 340F;

    private static final String[] TABS = {"Все", "Плащи", "Крылья", "Тело", "Питомцы", "Шляпы"};
    private static final String[] TYPE_FILTERS = {"", "cape", "wings", "bodywear", "pet", "hat"};

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final EaseInOutQuad openAnim = new EaseInOutQuad(250, 1.0);

    private int selectedTab = 0;
    private float scroll = 0F;
    private float scrollTarget = 0F;

    private float scaleFix = 1F;
    private float panelX;
    private float panelY;

    // Mannequin rotation state
    private float mannequinYaw = 0F;
    private boolean isDraggingMannequin = false;
    private float lastDragX = 0F;

    public CosmeticsScreen() {
        super(Text.literal("Косметика"));
    }

    @Override
    protected void init() {
        LocalCosmetics.init();
        openAnim.reset();
        openAnim.setDirection(Direction.FORWARDS);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Transparent dark overlay handled in render
    }

    @Override
    public void render(DrawContext context, int rawMouseX, int rawMouseY, float delta) {
        float targetScale = 2F;
        float currentScale = (float) mc.getWindow().getScaleFactor();
        scaleFix = targetScale / currentScale;

        float screenW = mc.getWindow().getScaledWidth() / scaleFix;
        float screenH = mc.getWindow().getScaledHeight() / scaleFix;

        float mouseX = (float) rawMouseX / scaleFix;
        float mouseY = (float) rawMouseY / scaleFix;

        float alpha = openAnim.getOutput();

        // Smooth scroll interpolation
        scroll += (scrollTarget - scroll) * 0.25F;

        panelX = screenW / 2F - PANEL_W / 2F;
        panelY = screenH / 2F - PANEL_H / 2F + (1F - alpha) * 25F;

        Render2D.beginOverlay();

        // Fullscreen backdrop
        RenderUtil.Render2D.rect(0, 0, screenW, screenH, ColorUtil.getColor(0, 0.65F * alpha));

        // Panel Glow & Blur
        Draw.glow(panelX, panelY, PANEL_W, PANEL_H, ColorUtil.getColor(110, 75, 235, (int)(70 * alpha)), 14F, 24F, 1F);
        Draw.blur(panelX, panelY, PANEL_W, PANEL_H, alpha, 14F, ColorUtil.getColor(12, 13, 22, (int)(225 * alpha)));
        Draw.outline(panelX, panelY, PANEL_W, PANEL_H, 1.2F, ColorUtil.getColor(255, 255, 255, (int)(32 * alpha)), 14F);

        // ================= LEFT PREVIEW PANEL =================
        float prevX = panelX + 14F;
        float prevY = panelY + 14F;
        float prevW = 158F;
        float prevH = PANEL_H - 28F;

        Draw.rect(prevX, prevY, prevW, prevH, ColorUtil.getColor(17, 19, 30, (int)(180 * alpha)), 10F);
        Draw.outline(prevX, prevY, prevW, prevH, 1F, ColorUtil.getColor(255, 255, 255, (int)(18 * alpha)), 10F);

        // Preview Header
        Fonts.sf_bold.draw("ПРЕДПРОСМОТР", prevX + 12F, prevY + 12F, 7F, ColorUtil.getColor(180, 185, 215, (int)(240 * alpha)));

        // 3D Player Mannequin
        Draw.flush();
        if (mc.player != null) {
            int gx1 = Math.round((prevX + 10F) * scaleFix);
            int gy1 = Math.round((prevY + 28F) * scaleFix);
            int gx2 = Math.round((prevX + prevW - 10F) * scaleFix);
            int gy2 = Math.round((prevY + prevH - 52F) * scaleFix);

            // Auto-rotate gently if not dragging
            if (!isDraggingMannequin) {
                mannequinYaw += 0.5F;
            }

            float simulatedMouseX = (prevX + prevW / 2F) + (float) Math.sin(Math.toRadians(mannequinYaw)) * 60F;
            float simulatedMouseY = (prevY + prevH / 2F - 30F);

            InventoryScreen.drawEntity(context, gx1, gy1, gx2, gy2,
                    Math.round(52F * scaleFix), 0.0625F, simulatedMouseX * scaleFix, simulatedMouseY * scaleFix, mc.player);
        }
        Render2D.beginOverlay();

        // Active Equipped Count Badge
        int equippedCount = LocalCosmetics.selectedIndices().size();
        String equippedText = "Надето: " + equippedCount + "/5";
        float badgeW = Fonts.sf_medium.getWidth(equippedText, 6.5F) + 16F;
        float badgeH = 16F;
        float badgeX = prevX + (prevW - badgeW) / 2F;
        float badgeY = prevY + prevH - 46F;

        Draw.rect(badgeX, badgeY, badgeW, badgeH, ColorUtil.getColor(95, 60, 220, (int)(90 * alpha)), 8F);
        Draw.outline(badgeX, badgeY, badgeW, badgeH, 1F, ColorUtil.getColor(160, 130, 255, (int)(140 * alpha)), 8F);
        Fonts.sf_medium.drawCentered(equippedText, badgeX + badgeW / 2F, badgeY + 4F, 6.5F, ColorUtil.getColor(235, 240, 255, (int)(255 * alpha)));

        // "Снять всё" (Unequip All) button
        float resetW = prevW - 24F;
        float resetH = 18F;
        float resetX = prevX + 12F;
        float resetY = prevY + prevH - 25F;
        boolean resetHovered = mouseX >= resetX && mouseX <= resetX + resetW && mouseY >= resetY && mouseY <= resetY + resetH;

        int resetBg = resetHovered ? ColorUtil.getColor(220, 50, 70, (int)(140 * alpha)) : ColorUtil.getColor(35, 38, 55, (int)(160 * alpha));
        Draw.rect(resetX, resetY, resetW, resetH, resetBg, 6F);
        Draw.outline(resetX, resetY, resetW, resetH, 1F, resetHovered ? ColorUtil.getColor(255, 90, 110, (int)(200 * alpha)) : ColorUtil.getColor(255, 255, 255, (int)(20 * alpha)), 6F);
        Fonts.sf_medium.drawCentered("Снять всё", resetX + resetW / 2F, resetY + 4.5F, 6F, ColorUtil.getColor(240, 240, 250, (int)(240 * alpha)));

        // ================= RIGHT CONTENT AREA =================
        float rightX = panelX + 184F;
        float rightY = panelY + 14F;
        float rightW = PANEL_W - 198F;
        float rightH = PANEL_H - 28F;

        // Title
        Fonts.sf_bold.draw("КОСМЕТИКА", rightX, rightY + 2F, 10F, ColorUtil.getColor(250, 250, 255, (int)(255 * alpha)));

        // Close button (✕)
        float closeSize = 16F;
        float closeX = panelX + PANEL_W - 24F;
        float closeY = panelY + 12F;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + closeSize && mouseY >= closeY && mouseY <= closeY + closeSize;
        Fonts.sf_bold.drawCentered("✕", closeX + closeSize / 2F, closeY + 1.5F, 8F, closeHover ? ColorUtil.getColor(255, 100, 120, (int)(255 * alpha)) : ColorUtil.getColor(170, 175, 200, (int)(200 * alpha)));

        // Category Tabs
        float tabY = rightY + 22F;
        float tabH = 17F;
        float currTabX = rightX;

        for (int i = 0; i < TABS.length; i++) {
            String tabName = TABS[i];
            float textW = Fonts.sf_medium.getWidth(tabName, 6.5F);
            float tabW = textW + 14F;
            boolean isTabActive = (i == selectedTab);
            boolean isTabHover = mouseX >= currTabX && mouseX <= currTabX + tabW && mouseY >= tabY && mouseY <= tabY + tabH;

            int tabBg;
            int tabBorder;
            int tabTextColor;

            if (isTabActive) {
                tabBg = ColorUtil.getColor(125, 75, 255, (int)(210 * alpha));
                tabBorder = ColorUtil.getColor(180, 140, 255, (int)(240 * alpha));
                tabTextColor = ColorUtil.getColor(255, 255, 255, (int)(255 * alpha));
                Draw.glow(currTabX, tabY, tabW, tabH, ColorUtil.getColor(125, 75, 255, (int)(90 * alpha)), 6F, 10F, 0.8F);
            } else if (isTabHover) {
                tabBg = ColorUtil.getColor(40, 44, 65, (int)(180 * alpha));
                tabBorder = ColorUtil.getColor(255, 255, 255, (int)(35 * alpha));
                tabTextColor = ColorUtil.getColor(220, 225, 245, (int)(230 * alpha));
            } else {
                tabBg = ColorUtil.getColor(24, 27, 40, (int)(140 * alpha));
                tabBorder = ColorUtil.getColor(255, 255, 255, (int)(16 * alpha));
                tabTextColor = ColorUtil.getColor(160, 165, 190, (int)(190 * alpha));
            }

            Draw.rect(currTabX, tabY, tabW, tabH, tabBg, 6F);
            Draw.outline(currTabX, tabY, tabW, tabH, 1F, tabBorder, 6F);
            Fonts.sf_medium.drawCentered(tabName, currTabX + tabW / 2F, tabY + 4F, 6.5F, tabTextColor);

            currTabX += tabW + 5F;
        }

        // ================= COSMETICS GRID =================
        float gridX = rightX;
        float gridY = tabY + tabH + 8F;
        float gridW = rightW;
        float gridH = rightH - (gridY - rightY);

        List<Integer> items = getFilteredItems();
        int cols = 3;
        float gap = 6F;
        float cardW = (gridW - (cols - 1) * gap) / cols;
        float cardH = 68F;

        int totalRows = Math.max(1, (items.size() + cols - 1) / cols);
        float totalContentH = totalRows * cardH + (totalRows - 1) * gap;
        float maxScroll = Math.max(0F, totalContentH - gridH);

        // Clamp scroll
        scrollTarget = Math.max(0F, Math.min(scrollTarget, maxScroll));
        if (scroll < 0F) scroll = 0F;
        if (scroll > maxScroll) scroll = maxScroll;

        // Scissor viewport
        Draw.flush();
        Scissor.enable(gridX, gridY, gridW, gridH, 2);

        for (int i = 0; i < items.size(); i++) {
            int itemIndex = items.get(i);
            int col = i % cols;
            int row = i / cols;

            float cx = gridX + col * (cardW + gap);
            float cy = gridY + row * (cardH + gap) - scroll;

            if (cy + cardH < gridY || cy > gridY + gridH) {
                continue;
            }

            boolean isHovered = mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH && mouseY >= gridY && mouseY <= gridY + gridH;
            boolean isEquipped = LocalCosmetics.isSelected(itemIndex);

            int cardBg;
            int cardBorder;

            if (isEquipped) {
                cardBg = ColorUtil.getColor(90, 50, 200, (int)(85 * alpha));
                cardBorder = ColorUtil.getColor(155, 110, 255, (int)(230 * alpha));
                Draw.glow(cx, cy, cardW, cardH, ColorUtil.getColor(125, 75, 255, (int)(55 * alpha)), 8F, 8F, 0.7F);
            } else if (isHovered) {
                cardBg = ColorUtil.getColor(28, 32, 48, (int)(210 * alpha));
                cardBorder = ColorUtil.getColor(255, 255, 255, (int)(45 * alpha));
            } else {
                cardBg = ColorUtil.getColor(20, 23, 35, (int)(170 * alpha));
                cardBorder = ColorUtil.getColor(255, 255, 255, (int)(18 * alpha));
            }

            Draw.rect(cx, cy, cardW, cardH, cardBg, 7F);
            Draw.outline(cx, cy, cardW, cardH, 1.1F, cardBorder, 7F);

            // Thumbnail
            float iconSize = 32F;
            float iconX = cx + 6F;
            float iconY = cy + 6F;

            // Subtle icon frame
            Draw.rect(iconX, iconY, iconSize, iconSize, ColorUtil.getColor(12, 14, 22, (int)(160 * alpha)), 5F);
            try {
                Draw.texture(LocalCosmetics.texture(itemIndex), iconX + 2F, iconY + 2F, iconSize - 4F, iconSize - 4F, ColorUtil.getColor(255, 255, 255, (int)(255 * alpha)));
            } catch (Throwable ignored) {
            }

            // Category tag
            String catTag = getCategoryTag(LocalCosmetics.type(itemIndex));
            float tagW = Fonts.sf_regular.getWidth(catTag, 5F) + 8F;
            float tagH = 11F;
            float tagX = cx + cardW - tagW - 6F;
            float tagY = cy + 7F;

            Draw.rect(tagX, tagY, tagW, tagH, ColorUtil.getColor(45, 48, 70, (int)(160 * alpha)), 4F);
            Fonts.sf_regular.drawCentered(catTag, tagX + tagW / 2F, tagY + 2.2F, 5F, ColorUtil.getColor(185, 190, 220, (int)(220 * alpha)));

            // Action / Status Badge button
            float btnW = cardW - 12F;
            float btnH = 13F;
            float btnX = cx + 6F;
            float btnY = cy + cardH - btnH - 6F;

            if (isEquipped) {
                Draw.rect(btnX, btnY, btnW, btnH, ColorUtil.getColor(125, 75, 255, (int)(210 * alpha)), 4F);
                Fonts.sf_bold.drawCentered("НАДЕТО", btnX + btnW / 2F, btnY + 2.5F, 5.5F, ColorUtil.getColor(255, 255, 255, (int)(255 * alpha)));
            } else {
                Draw.rect(btnX, btnY, btnW, btnH, isHovered ? ColorUtil.getColor(42, 46, 68, (int)(220 * alpha)) : ColorUtil.getColor(28, 31, 46, (int)(160 * alpha)), 4F);
                Draw.outline(btnX, btnY, btnW, btnH, 0.8F, isHovered ? ColorUtil.getColor(255, 255, 255, (int)(40 * alpha)) : ColorUtil.getColor(255, 255, 255, (int)(15 * alpha)), 4F);
                Fonts.sf_medium.drawCentered("НАДЕТЬ", btnX + btnW / 2F, btnY + 2.5F, 5.5F, isHovered ? ColorUtil.getColor(240, 245, 255, (int)(240 * alpha)) : ColorUtil.getColor(160, 165, 190, (int)(190 * alpha)));
            }

            // Name
            String itemName = LocalCosmetics.name(itemIndex);
            if (itemName.length() > 14) {
                itemName = itemName.substring(0, 12) + "..";
            }
            Fonts.sf_medium.draw(itemName, iconX + iconSize + 5F, cy + 24F, 6F, ColorUtil.getColor(245, 245, 255, (int)(245 * alpha)));
        }

        Draw.flush();
        Scissor.disable();

        // Scrollbar
        if (maxScroll > 0F) {
            float sbW = 3F;
            float sbX = gridX + gridW + 3F;
            float sbH = gridH;
            float thumbH = Math.max(20F, (gridH / totalContentH) * gridH);
            float thumbY = gridY + (scroll / maxScroll) * (gridH - thumbH);

            Draw.rect(sbX, gridY, sbW, sbH, ColorUtil.getColor(255, 255, 255, (int)(12 * alpha)), 1.5F);
            Draw.rect(sbX, thumbY, sbW, thumbH, ColorUtil.getColor(140, 95, 255, (int)(180 * alpha)), 1.5F);
        }

        Draw.flush();
    }

    private List<Integer> getFilteredItems() {
        String filter = TYPE_FILTERS[selectedTab];
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < LocalCosmetics.size(); i++) {
            if (filter.isEmpty() || filter.equalsIgnoreCase(LocalCosmetics.type(i))) {
                list.add(i);
            }
        }
        return list;
    }

    private String getCategoryTag(String type) {
        if (type == null) return "ПРЕДМЕТ";
        return switch (type.toLowerCase()) {
            case "cape" -> "ПЛАЩ";
            case "wings" -> "КРЫЛЬЯ";
            case "bodywear" -> "ТЕЛО";
            case "pet" -> "ПИТОМЕЦ";
            case "hat" -> "ШЛЯПА";
            default -> type.toUpperCase();
        };
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);

        float mouseX = (float) (click.x() / scaleFix);
        float mouseY = (float) (click.y() / scaleFix);

        // Close button
        float closeSize = 16F;
        float closeX = panelX + PANEL_W - 24F;
        float closeY = panelY + 12F;
        if (mouseX >= closeX && mouseX <= closeX + closeSize && mouseY >= closeY && mouseY <= closeY + closeSize) {
            close();
            return true;
        }

        // Preview panel interaction (mannequin drag)
        float prevX = panelX + 14F;
        float prevY = panelY + 14F;
        float prevW = 158F;
        float prevH = PANEL_H - 28F;

        if (mouseX >= prevX && mouseX <= prevX + prevW && mouseY >= prevY && mouseY <= prevY + prevH - 50F) {
            isDraggingMannequin = true;
            lastDragX = mouseX;
            return true;
        }

        // Reset button ("Снять всё")
        float resetW = prevW - 24F;
        float resetH = 18F;
        float resetX = prevX + 12F;
        float resetY = prevY + prevH - 25F;
        if (mouseX >= resetX && mouseX <= resetX + resetW && mouseY >= resetY && mouseY <= resetY + resetH) {
            LocalCosmetics.clearAll();
            return true;
        }

        // Category Tabs
        float rightX = panelX + 184F;
        float rightY = panelY + 14F;
        float tabY = rightY + 22F;
        float tabH = 17F;
        float currTabX = rightX;

        for (int i = 0; i < TABS.length; i++) {
            float textW = Fonts.sf_medium.getWidth(TABS[i], 6.5F);
            float tabW = textW + 14F;
            if (mouseX >= currTabX && mouseX <= currTabX + tabW && mouseY >= tabY && mouseY <= tabY + tabH) {
                if (selectedTab != i) {
                    selectedTab = i;
                    scroll = 0F;
                    scrollTarget = 0F;
                }
                return true;
            }
            currTabX += tabW + 5F;
        }

        // Grid cards click
        float gridX = rightX;
        float gridY = tabY + tabH + 8F;
        float gridW = PANEL_W - 198F;
        float gridH = PANEL_H - 28F - (gridY - rightY);

        if (mouseX >= gridX && mouseX <= gridX + gridW && mouseY >= gridY && mouseY <= gridY + gridH) {
            List<Integer> items = getFilteredItems();
            int cols = 3;
            float gap = 6F;
            float cardW = (gridW - (cols - 1) * gap) / cols;
            float cardH = 68F;

            for (int i = 0; i < items.size(); i++) {
                int col = i % cols;
                int row = i / cols;

                float cx = gridX + col * (cardW + gap);
                float cy = gridY + row * (cardH + gap) - scroll;

                if (mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH) {
                    LocalCosmetics.toggle(items.get(i));
                    return true;
                }
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        isDraggingMannequin = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (isDraggingMannequin) {
            float mouseX = (float) (click.x() / scaleFix);
            float diff = mouseX - lastDragX;
            mannequinYaw += diff * 1.5F;
            lastDragX = mouseX;
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollTarget -= (float) vertical * 26F;
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }
}

package rainy.fun.gui;

import rainy.fun.module.Module;
import rainy.fun.module.ModuleCategory;
import rainy.fun.module.ModuleManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ClickGuiScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 250;
    private static final int MUTED_COLOR = 0xFF9299A8;

    private final ModuleManager moduleManager;
    private final ModuleCategory selectedCategory;

    public ClickGuiScreen(ModuleManager moduleManager) {
        this(moduleManager, ModuleCategory.COMBAT);
    }

    public ClickGuiScreen(ModuleManager moduleManager, ModuleCategory selectedCategory) {
        super(Component.literal("Rainy.fun"));
        this.moduleManager = moduleManager;
        this.selectedCategory = selectedCategory;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // The custom separable Gaussian post effect blurs the full game frame before
        // this screen is drawn; the dark veil and panel make it read as matte glass.
        graphics.fill(0, 0, width, height, 0x6503070B);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;
        graphics.fill(left, top, right, bottom, 0xD90B0D10);
        graphics.fill(left + 1, top + 1, right - 1, top + 2, 0x24FFFFFF);
        graphics.outline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0x503B414B);
        graphics.fill(left + 12, top + 39, right - 12, top + 40, 0x302F343D);
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int categoryX = left + 12;

        for (ModuleCategory category : ModuleCategory.values()) {
            int x = categoryX;
            this.addRenderableWidget(Button.builder(Component.literal(category.getTitle()), button ->
                            this.minecraft.gui.setScreen(new ClickGuiScreen(moduleManager, category)))
                    .bounds(x, top + 49, 55, 20)
                    .build());
            categoryX += 56;
        }

        int y = top + 81;
        var modules = moduleManager.getByCategory(selectedCategory);
        if (modules.isEmpty()) {
            this.addRenderableWidget(Button.builder(Component.literal("No modules registered"), button -> { })
                    .bounds(left + 70, y + 12, PANEL_WIDTH - 140, 20)
                    .build());
            return;
        }

        for (Module module : modules) {
            if (y + 20 > top + PANEL_HEIGHT - 22) break;
            String label = module.getName() + (module.isEnabled() ? "  ON" : "  OFF");
            this.addRenderableWidget(Button.builder(Component.literal(label), button -> {
                        module.toggle();
                        button.setMessage(Component.literal(module.getName()
                                + (module.isEnabled() ? "  ON" : "  OFF")));
                    })
                    .bounds(left + 16, y, PANEL_WIDTH - 32, 20)
                    .build());
            y += 23;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        graphics.text(font, Component.literal("Rainy.fun  /  Framework"), left + 16, top + 15, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal("Right Shift opens this screen. Modules are empty by default."),
                left + 16, top + PANEL_HEIGHT - 18, MUTED_COLOR, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

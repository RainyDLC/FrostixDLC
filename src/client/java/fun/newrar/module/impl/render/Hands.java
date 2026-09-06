package fun.newrar.module.impl.render;

import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.ButtonSetting;
import fun.newrar.module.api.settings.impl.DelimiterSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.screen.DropdownScreen;
import fun.newrar.screen.Menu;
import fun.newrar.utils.other.Instance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;

@ModuleInfo(
        name = "Hands",
        desc = "Точная настройка трехмерного положения, смещения и масштаба рук от первого лица",
        category = Category.RENDER
)
public class Hands extends Module {
    public static Hands get() {
        return Instance.get(Hands.class);
    }

    private boolean editorActive;
    private boolean restoreSwingAnimation;
    private boolean restoreGlassHands;

    public ButtonSetting openEditor = new ButtonSetting(this, "Редактор рук", () -> {
        if (mc.currentScreen instanceof Menu menu) {
            menu.openHandsEditor();
        } else if (mc.currentScreen instanceof DropdownScreen dropdown) {
            dropdown.openHandsEditor();
        }
    });

    public DelimiterSetting rightTitle = new DelimiterSetting(this, "Правая рука");
    public SliderSetting rx = new SliderSetting(this, "Правая X", 0.0F, -2.0F, 2.0F, 0.05F);
    public SliderSetting ry = new SliderSetting(this, "Правая Y", 0.0F, -2.0F, 2.0F, 0.05F);
    public SliderSetting rz = new SliderSetting(this, "Правая Z", 0.0F, -2.0F, 2.0F, 0.05F);
    public SliderSetting rScale = new SliderSetting(this, "Правая размер", 1.0F, 0.1F, 3.0F, 0.05F);
    public ButtonSetting resetRightButton = new ButtonSetting(this, "Сбросить правую", this::resetRight);

    public DelimiterSetting leftTitle = new DelimiterSetting(this, "Левая рука");
    public SliderSetting lx = new SliderSetting(this, "Левая X", 0.0F, -2.0F, 2.0F, 0.05F);
    public SliderSetting ly = new SliderSetting(this, "Левая Y", 0.0F, -2.0F, 2.0F, 0.05F);
    public SliderSetting lz = new SliderSetting(this, "Левая Z", 0.0F, -2.0F, 2.0F, 0.05F);
    public SliderSetting lScale = new SliderSetting(this, "Левая размер", 1.0F, 0.1F, 3.0F, 0.05F);
    public ButtonSetting resetLeftButton = new ButtonSetting(this, "Сбросить левую", this::resetLeft);

    public ButtonSetting resetAllButton = new ButtonSetting(this, "Сбросить всё", this::resetAll);

    @Override
    protected void onEnable() {
        SwingAnimation swingAnimation = SwingAnimation.get();
        if (restoreSwingAnimation && swingAnimation != null && !swingAnimation.isEnabled()) {
            swingAnimation.setEnabled(true, false);
        }

        GlassHands glassHands = GlassHands.getInstance();
        if (restoreGlassHands && glassHands != null && !glassHands.isEnabled()) {
            glassHands.setEnabled(true, false);
        }
    }

    @Override
    protected void onDisable() {
        SwingAnimation swingAnimation = SwingAnimation.get();
        restoreSwingAnimation = swingAnimation != null && swingAnimation.isEnabled();
        if (swingAnimation != null && swingAnimation.isEnabled()) {
            swingAnimation.setEnabled(false, false);
        }

        GlassHands glassHands = GlassHands.getInstance();
        restoreGlassHands = glassHands != null && glassHands.isEnabled();
        if (glassHands != null && glassHands.isEnabled()) {
            glassHands.setEnabled(false, false);
        }
    }

    public void applyHandTranslation(MatrixStack matrices, Arm arm) {
        if (!shouldApplyTransforms()) return;
        float x = arm == Arm.RIGHT ? rx.getValue() : lx.getValue();
        float y = arm == Arm.RIGHT ? ry.getValue() : ly.getValue();
        float z = arm == Arm.RIGHT ? rz.getValue() : lz.getValue();
        matrices.translate(x, y, z);
    }

    public void applyHandScale(MatrixStack matrices, Arm arm) {
        if (!shouldApplyTransforms()) return;
        float s = arm == Arm.RIGHT ? rScale.getValue() : lScale.getValue();
        matrices.scale(s, s, s);
    }

    public void applyEmptyHandAlignment(MatrixStack matrices, Arm arm) {
        if (!shouldApplyTransforms()) return;
        matrices.translate(arm == Arm.RIGHT ? -0.08F : 0.08F, 0.08F, 0F);
    }

    public boolean shouldApplyTransforms() {
        return isEnabled() || editorActive;
    }

    public boolean isEditorActive() {
        return editorActive;
    }

    public void setEditorActive(boolean editorActive) {
        this.editorActive = editorActive;
    }

    public void resetRight() {
        rx.set(0.0F);
        ry.set(0.0F);
        rz.set(0.0F);
        rScale.set(1.0F);
    }

    public void resetLeft() {
        lx.set(0.0F);
        ly.set(0.0F);
        lz.set(0.0F);
        lScale.set(1.0F);
    }

    public void resetAll() {
        resetRight();
        resetLeft();
    }
}


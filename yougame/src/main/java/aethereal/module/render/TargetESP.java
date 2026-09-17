package aethereal.module.render;

import aethereal.core.Category;
import aethereal.core.Delta;
import aethereal.core.EventTarget;
import aethereal.core.Interface;
import aethereal.core.Module;
import aethereal.core.ModuleRegister;
import aethereal.event.DrawEvent;
import aethereal.event.TickEvent;
import aethereal.module.combat.Aura;
import aethereal.module.combat.TriggerBot;
import aethereal.render.AnimationUtil;
import aethereal.render.ColorUtil;
import aethereal.render.EasingList;
import aethereal.setting.BooleanSetting;
import aethereal.setting.ColorSetting;
import aethereal.setting.SliderSetting;
import net.minecraft.world.entity.LivingEntity;

import static aethereal.core.Interface.aM_;

/** Public scan-only TargetESP module for the Delta base. */
@ModuleRegister(
        a = "Target ESP",
        b = "Сканирующая линия на текущей цели Aura или Trigger Bot",
        c = Category.Render)
public class TargetESP extends Module implements Interface {
    private final SliderSetting saturation = new SliderSetting(
            "Насыщенность", 100.0f, 0.0f, 200.0f, 5.0f);
    private final SliderSetting scanSpeed = new SliderSetting(
            "Скорость переливания", 1.0f, 0.25f, 3.0f, 0.05f);
    private final ColorSetting scanColor = new ColorSetting(
            "Цвет переливания", ColorUtil.a(55, 170, 255, 255));
    private final BooleanSetting scanColorFlow = new BooleanSetting(
            "Переливание цветов", false);
    private final ColorSetting scanSecondColor = (ColorSetting) new ColorSetting(
            "Второй цвет", ColorUtil.a(190, 75, 255, 255))
            .a(this.scanColorFlow::c);
    private final SliderSetting scanGlow = new SliderSetting(
            "Яркость переливания", 100.0f, 25.0f, 200.0f, 5.0f);

    private AnimationUtil targetAnimation = new AnimationUtil();
    private LivingEntity target;
    private Object targetLevel;
    private boolean targetActive;

    public TargetESP() {
        a(
                this.saturation,
                this.scanSpeed,
                this.scanColor,
                this.scanColorFlow,
                this.scanSecondColor,
                this.scanGlow);
    }

    public float saturation() {
        return this.saturation.c().floatValue() / 100.0f;
    }

    public float scanSpeed() {
        return this.scanSpeed.c().floatValue();
    }

    public int scanColor() {
        return this.scanColor.c().intValue();
    }

    public int scanSecondColor() {
        return this.scanColorFlow.c()
                ? this.scanSecondColor.c().intValue()
                : this.scanColor.c().intValue();
    }

    public float scanGlow() {
        return this.scanGlow.c().floatValue() / 100.0f;
    }

    public float renderAnimation() {
        return this.targetAnimation.c();
    }

    public boolean isRenderedTarget(LivingEntity entity) {
        return entity != null && entity == this.target;
    }

    public boolean hasActiveTarget() {
        return this.targetActive;
    }

    @Override
    public void b() {
        resetTargetState();
        super.b();
    }

    @Override
    public void c() {
        resetTargetState();
        super.c();
    }

    @EventTarget
    public void onDraw(DrawEvent event) {
        if (event.c()) {
            this.targetAnimation.a(0.0f, 1.0f, 0.2f, EasingList.g, event.g());
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        Object currentLevel = aM_.level;
        if (currentLevel != this.targetLevel) {
            resetTargetState();
            this.targetLevel = currentLevel;
        }

        LivingEntity current = currentTarget();
        if (!isRenderable(current)) {
            current = null;
        }

        boolean changed = current != null && this.target != null && current != this.target;
        boolean visible = current != null && !changed;
        this.targetActive = visible;
        if (visible) {
            this.target = current;
        }

        this.targetAnimation.a(visible);
        if (!visible && this.targetAnimation.a() <= 0.0f) {
            this.target = changed ? current : null;
        }
    }

    private static LivingEntity currentTarget() {
        Aura aura = Delta.h().d().t().B();
        LivingEntity current = aura.m() ? aura.s() : null;
        TriggerBot triggerBot = Delta.h().d().t().X();
        if (current == null && triggerBot.m()) {
            current = triggerBot.s();
        }
        return current;
    }

    private static boolean isRenderable(LivingEntity entity) {
        return entity != null
                && aM_.level != null
                && entity.isAlive()
                && !entity.isRemoved()
                && entity.level() == aM_.level;
    }

    private void resetTargetState() {
        this.targetAnimation = new AnimationUtil();
        this.target = null;
        this.targetLevel = null;
        this.targetActive = false;
    }
}

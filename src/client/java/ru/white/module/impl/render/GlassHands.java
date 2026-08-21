package ru.white.module.impl.render;

import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.GlassHandsRenderEvent;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.GlassHandsRenderer;
import ru.white.utils.render.HandLightningRenderer;
import lombok.Getter;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@Getter
@ModuleInfo(name = "Glass Hands", category = Category.RENDER, desc = "Делает руки и предметы стеклянными")
public class GlassHands extends Module {

    private static GlassHands instance;

    public BooleanSetting enableBlur = new BooleanSetting(this, "Блюр", true);
    public SliderSetting blurRadius = new SliderSetting(this, "Сила размытия", 2.5f, 1.0f, 5.0f, 0.1f)
            .setVisible(() -> enableBlur.getValue());
    public SliderSetting blurIterations = new SliderSetting(this, "Качество", 3, 1, 5, 1)
            .setVisible(() -> enableBlur.getValue());
    public SliderSetting saturation = new SliderSetting(    this, "Насыщенность", 0, 0.0f, 2.0f, 0.1f)
            .setVisible(() -> enableBlur.getValue());

    public SliderSetting tintIntensity = new SliderSetting(this, "Сила оттенка", 0.2f, 0.0f, 1.0f, 0.01f)
            .setVisible(() -> enableBlur.getValue());

    /** 0 — кромка повторяет силуэт предмета, больше — «оплавленное» стекло с круглыми углами. */
    public SliderSetting edgeSoftness = new SliderSetting(this, "Сглаживание краёв", 0f, 0f, 28f, 1f)
            .setVisible(() -> enableBlur.getValue());

    public ModeSetting typeColor = new ModeSetting(this,"Режим цвета","Тема","Свой");

    public ColorSetting tintColor = new ColorSetting(this, "Цвет", 0xFF00FFFF).setVisible(() -> typeColor.is("Свой"));

    public int getColor() {

        if(typeColor.is("Тема")) {
            return ColorUtil.getClientColor1(1);
        }

        return tintColor.getValue();
    }

    public BooleanSetting enableIce = new BooleanSetting(this, "Искажение", false);
    public SliderSetting iceIntensity = new SliderSetting(this, "Сила искажения", 1.0f, 0.0f, 2.0f, 0.05f)
            .setVisible(() -> enableIce.getValue());
    public SliderSetting refractReach = new SliderSetting(this, "Ширина искажения", 25f, 0, 80f, 1f)
            .setVisible(() -> enableIce.getValue());

    public BooleanSetting enableFire = new BooleanSetting(this, "Огонь", false);
    public SliderSetting fireIntensity = new SliderSetting(this, "Сила огня", 1.18f, 0.2f, 2.0f, 0.02f)
            .setVisible(() -> enableFire.getValue());
    public SliderSetting fireRadius = new SliderSetting(this, "Размер пламени", 15f, 4f, 40f, 1f)
            .setVisible(() -> enableFire.getValue());
    public SliderSetting fireSpeed = new SliderSetting(this, "Скорость огня", 1.1f, 0.2f, 3.0f, 0.05f)
            .setVisible(() -> enableFire.getValue());
    public SliderSetting fireHeight = new SliderSetting(this, "Высота дыма", 28f, 5f, 56f, 1f)
            .setVisible(() -> enableFire.getValue());
    public SliderSetting fireTrail = new SliderSetting(this, "Сила дыма", 1.16f, 0.0f, 2.0f, 0.02f)
            .setVisible(() -> enableFire.getValue());
    public ModeSetting fireColorMode = new ModeSetting(this, "Цвет огня", "Предмет", "Тема", "Свой")
            .setVisible(() -> enableFire.getValue());
    public ColorSetting fireCustomColor = new ColorSetting(this, "Свой цвет огня", 0xFFFF8026)
            .setVisible(() -> enableFire.getValue() && fireColorMode.is("Свой"));
    public BooleanSetting fireBurnThrough = new BooleanSetting(this, "Прожиг", false)
            .setVisible(() -> enableFire.getValue());

    public BooleanSetting enableEdgeGlow = new BooleanSetting(this, "Свечение краёв", true);
    public SliderSetting edgeGlowIntensity = new SliderSetting(this, "Сила свечения", 0.2f, 0.0f, 1.0f, 0.01f)
            .setVisible(() -> enableEdgeGlow.getValue());
    public ModeSetting edgeGlowColorMode = new ModeSetting(this, "Цвет свечения",  "Тема", "Свой")
            .setVisible(() -> enableEdgeGlow.getValue());
    public ColorSetting edgeGlowCustomColor = new ColorSetting(this, "Свой цвет свечения", 0xFF00FFFF)
            .setVisible(() -> enableEdgeGlow.getValue() && edgeGlowColorMode.is("Свой"));
    public BooleanSetting shimmer = new BooleanSetting(this, "Шиммер", true)
            .setVisible(() -> enableEdgeGlow.getValue());
    public SliderSetting shimmerWidth = new SliderSetting(this, "Ширина шиммера", 0.04f, 0.01f, 0.15f, 0.01f)
            .setVisible(() -> enableEdgeGlow.getValue() && shimmer.getValue());
    public SliderSetting shimmerPeriod = new SliderSetting(this, "Период шиммера", 5f, 1f, 15f, 0.5f)
            .setVisible(() -> enableEdgeGlow.getValue() && shimmer.getValue());

    // ── молнии вокруг рук ────────────────────────────────────────────────
    // Рисуются в EventRender3D, т.е. до отрисовки рук: разряды вьются в воздухе
    // вокруг кисти и перекрываются самим предметом.

    public BooleanSetting enableLightning = new BooleanSetting(this, "Молнии", false);
    public ModeSetting lightningHands = new ModeSetting(this, "Руки", "Обе", "Главная", "Вторая")
            .setVisible(() -> enableLightning.getValue());
    public ModeSetting lightningItems = new ModeSetting(this, "Предметы", "Любой", "Оружие и инструменты")
            .setVisible(() -> enableLightning.getValue());
    public SliderSetting lightningCount = new SliderSetting(this, "Кол-во молний", 12, 2, 32, 1)
            .setVisible(() -> enableLightning.getValue());
    public SliderSetting lightningSpeed = new SliderSetting(this, "Скорость молний", 42, 10, 120, 1)
            .setVisible(() -> enableLightning.getValue());
    public SliderSetting lightningRadius = new SliderSetting(this, "Радиус молний", 0.18f, 0.05f, 0.5f, 0.01f)
            .setVisible(() -> enableLightning.getValue());
    public SliderSetting lightningLength = new SliderSetting(this, "Длина разряда", 0.25f, 0.1f, 0.6f, 0.01f)
            .setVisible(() -> enableLightning.getValue());
    public SliderSetting lightningSwing = new SliderSetting(this, "Реакция на удар", 1.0f, 0f, 2f, 0.05f)
            .setVisible(() -> enableLightning.getValue());
    public SliderSetting lightningAlpha = new SliderSetting(this, "Прозрачность молний", 1.0f, 0.1f, 1.0f, 0.05f)
            .setVisible(() -> enableLightning.getValue());
    public ModeSetting lightningColorMode = new ModeSetting(this, "Цвет молний", "Тема", "Свой")
            .setVisible(() -> enableLightning.getValue());
    public ColorSetting lightningColor = new ColorSetting(this, "Свой цвет молний", 0xFF66CCFF)
            .setVisible(() -> enableLightning.getValue() && lightningColorMode.is("Свой"));
    public SliderSetting lightningForward = new SliderSetting(this, "Смещение вперёд", 0.25f, -0.2f, 1.0f, 0.01f)
            .setVisible(() -> enableLightning.getValue());
    public SliderSetting lightningSide = new SliderSetting(this, "Смещение в сторону", 0.32f, 0f, 1.0f, 0.01f)
            .setVisible(() -> enableLightning.getValue());
    public SliderSetting lightningDown = new SliderSetting(this, "Смещение вниз", 0.18f, -0.3f, 0.8f, 0.01f)
            .setVisible(() -> enableLightning.getValue());

    private final BufferAllocator lightningAllocator = new BufferAllocator(1 << 16);
    private final HandLightningRenderer mainHandLightning = new HandLightningRenderer();
    private final HandLightningRenderer offHandLightning = new HandLightningRenderer();

    public GlassHands() {
        instance = this;
    }

    public static GlassHands getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        renderer.invalidate();
        renderer.setEnabled(true);
        applyRendererSettings();
    }

    @Override
    protected void onDisable() {
        GlassHandsRenderer.getInstance().setEnabled(false);
        resetLightning();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        resetLightning();
        lightningAllocator.clear();
        if (!isEnabled()) return;
        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        renderer.invalidate();
        renderer.setEnabled(true);
        applyRendererSettings();
    }

    private void resetLightning() {
        mainHandLightning.reset();
        offHandLightning.reset();
    }

    /**
     * Молнии вокруг кистей. Идут через EventRender3D (конец рендера мира), поэтому
     * руки и предмет, которые рисуются позже, перекрывают разряды — именно этот
     * слой и нужен.
     */
    @EventHandler
    public void onRender3D(EventRender3D event) {
        if (!isEnabled() || !enableLightning.getValue()) {
            resetLightning();
            return;
        }
        if (mc.player == null || mc.world == null) return;
        if (mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) return;

        boolean wantMain = !lightningHands.is("Вторая");
        boolean wantOff = !lightningHands.is("Главная");

        ItemStack mainStack = mc.player.getMainHandStack();
        ItemStack offStack = mc.player.getOffHandStack();

        boolean drawMain = wantMain && qualifies(mainStack);
        boolean drawOff = wantOff && qualifies(offStack);

        if (!drawMain) mainHandLightning.reset();
        if (!drawOff) offHandLightning.reset();
        if (!drawMain && !drawOff) return;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getCameraPos();

        // Базис камеры: руки живут в view-space, поэтому смещения считаем по нему,
        // а не по мировым осям. Соглашение для «вправо» — как в Trajectories.
        Vec3d forward = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        Vec3d right = Vec3d.fromPolar(0, camera.getYaw() + 90);
        Vec3d up = right.crossProduct(forward).normalize();

        float fwd = lightningForward.getValue();
        float side = lightningSide.getValue();
        float down = lightningDown.getValue();

        // Свинг усиливает эффект: больше разрядов и чуть шире разброс
        float swing = MathHelper.clamp(mc.player.handSwingProgress, 0f, 1f);
        float boost = 1f + lightningSwing.getValue() * swing;

        int rgb = (lightningColorMode.is("Тема")
                ? ColorUtil.getClientColor1(1)
                : lightningColor.getValue()) & 0xFFFFFF;

        float intensity = lightningAlpha.getValue();
        int bolts = (int) Math.min(32, Math.max(1, lightningCount.getValue() * boost));
        long interval = Math.max(10L, (long) lightningSpeed.getValue().floatValue());
        float radius = lightningRadius.getValue() * (1f + 0.35f * (boost - 1f));
        float length = lightningLength.getValue();

        boolean mainOnRight = mc.player.getMainArm() == Arm.RIGHT;

        VertexConsumerProvider.Immediate immediate =
                VertexConsumerProvider.immediate(lightningAllocator);

        if (drawMain) {
            Vec3d anchor = anchor(cameraPos, forward, right, up, fwd, mainOnRight ? side : -side, down);
            mainHandLightning.render(event.getMatrixStack(), immediate, anchor,
                    right, up, forward, radius, length, rgb, intensity, bolts, interval);
        }

        if (drawOff) {
            Vec3d anchor = anchor(cameraPos, forward, right, up, fwd, mainOnRight ? -side : side, down);
            offHandLightning.render(event.getMatrixStack(), immediate, anchor,
                    right, up, forward, radius, length, rgb, intensity, bolts, interval);
        }

        immediate.draw();
    }

    private Vec3d anchor(Vec3d cameraPos, Vec3d forward, Vec3d right, Vec3d up,
                         float fwd, float side, float down) {
        return cameraPos
                .add(forward.multiply(fwd))
                .add(right.multiply(side))
                .add(up.multiply(-down));
    }

    /** Пустая рука эффекта не даёт; режим «Оружие и инструменты» сужает до снаряжения. */
    private boolean qualifies(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (lightningItems.is("Любой")) return true;
        // В 1.21.11 мечей-классов больше нет — тип определяется компонентами
        return stack.contains(DataComponentTypes.TOOL) || stack.contains(DataComponentTypes.WEAPON);
    }

    @EventHandler
    public void onGlassHandsRender(GlassHandsRenderEvent event) {
        if (!isEnabled()) return;
        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        applyRendererSettings();
        if (event.getPhase() == GlassHandsRenderEvent.Phase.PRE) {
            renderer.captureSceneBeforeHands();
        } else if (event.getPhase() == GlassHandsRenderEvent.Phase.POST) {
            renderer.captureSceneAfterHands();
            renderer.renderGlassEffect();
        }
    }

    public void applyRendererSettings() {
        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        renderer.setBlurEnabled(enableBlur.getValue());
        renderer.setBlurRadius(blurRadius.getValue());
        renderer.setBlurIterations(blurIterations.getValue().intValue());
        renderer.setSaturation(saturation.getValue());
        renderer.setEdgeSoftness(edgeSoftness.getValue());

        // reflect и crackIntensity — тоже входы преломления: reflect уходит в шейдер отдельным
        // юниформом, а crack вообще никто не сбрасывал и он висел на дефолтных 0.5
        if (enableIce.getValue()) {
            renderer.setReflect(true);
            renderer.setIceIntensity(iceIntensity.getValue());
            renderer.setFrostScale(refractReach.getValue());
            renderer.setCrackIntensity(0.5f);
        } else {
            renderer.setReflect(false);
            renderer.setIceIntensity(0.0f);
            renderer.setFrostScale(0.0f);
            renderer.setCrackIntensity(0.0f);
        }

        renderer.setSmokeAmount(0.0f);

        renderer.setFireEnabled(enableFire.getValue());
        if (enableFire.getValue()) {
            renderer.setFireStrength(fireIntensity.getValue());
            renderer.setFireRadius(fireRadius.getValue());
            renderer.setFireSpeed(fireSpeed.getValue());
            renderer.setFireHeight(fireHeight.getValue());
            renderer.setFireTrailStrength(fireTrail.getValue());
            renderer.setFireFillMode(fireBurnThrough.getValue());

            if (fireColorMode.is("Предмет")) {
                renderer.setFireColorMix(0.0f);
            } else if (fireColorMode.is("Тема")) {
                renderer.setFireColor(ColorUtil.getClientColor1(1) & 0xFFFFFF);
                renderer.setFireColorMix(1.0f);
            } else {
                renderer.setFireColor(fireCustomColor.getValue() & 0xFFFFFF);
                renderer.setFireColorMix(1.0f);
            }
        }

        if (enableBlur.getValue()) {
            renderer.setTintColor(getColor());
            renderer.setTintIntensity(tintIntensity.getValue());
        } else {
            renderer.setTintColor(0x00000000);
            renderer.setTintIntensity(0.0f);
        }


        renderer.setOutlineEnabled(enableEdgeGlow.getValue());
        if (enableEdgeGlow.getValue()) {
            float strength = edgeGlowIntensity.getValue() * 20.0f;
            renderer.setOutlineGlowStrength(strength);

        if (edgeGlowColorMode.is("Тема")) {
                renderer.setOutlineUseItemColor(false);
                renderer.setOutlineColor(0xFF000000 | (ColorUtil.getClientColor1(1) & 0xFFFFFF));
            } else {
                renderer.setOutlineUseItemColor(false);
                renderer.setOutlineColor(0xFF000000 | (edgeGlowCustomColor.getValue() & 0xFFFFFF));
            }

            renderer.setShimmerEnabled(shimmer.getValue());
            renderer.setShimmerWidth(shimmerWidth.getValue());
            renderer.setShimmerPeriodSec(shimmerPeriod.getValue());
        }
    }
}

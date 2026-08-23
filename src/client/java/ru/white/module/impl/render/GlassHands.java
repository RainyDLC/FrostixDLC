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
import lombok.Getter;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.white.utils.render.RenderUtil;

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

    public BooleanSetting lightning = new BooleanSetting(this, "Молнии", false);
    public SliderSetting lightningSize = new SliderSetting(this, "Размер молний", 0.4f, 0.15f, 0.8f, 0.05f)
            .setVisible(() -> lightning.getValue());
    /** Подъём центра разрядов от якоря руки к визуальному центру предмета. */
    public SliderSetting lightningOffset = new SliderSetting(this, "Смещение молний", 0.35f, -0.2f, 0.8f, 0.05f)
            .setVisible(() -> lightning.getValue());

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
        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        renderer.setEnabled(false);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (!isEnabled()) return;
        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        renderer.invalidate();
        renderer.setEnabled(true);
        applyRendererSettings();
    }

    @EventHandler
    public void onGlassHandsRender(GlassHandsRenderEvent event) {
        if (!isEnabled()) return;
        GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
        applyRendererSettings();
        if (event.getPhase() == GlassHandsRenderEvent.Phase.PRE) {
            renderer.captureSceneBeforeHands();
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

    // молнии вокруг предмета в руках — та же стилистика, что "Молнии" в Attack Aura
    private final LightningRenderer[] handLightning = {new LightningRenderer(), new LightningRenderer()};
    private final boolean[] handHolding = new boolean[2];
    /** Матрица мирового прохода этого кадра (из EventRender3D) — нужна для отрисовки после композита стекла. */
    private MatrixStack frame3dStack;
    /** UBO мировой проекции для отрисовки молний поверх рук. Создаётся лениво: при старте модуля GpuDevice ещё не инициализирован. */
    private RawProjectionMatrix lightningProjection;

    @EventHandler
    public void onRender3D(EventRender3D event) {
        frame3dStack = event.getMatrixStack();

        boolean active = isEnabled() && lightning.getValue() && mc.player != null && mc.world != null
                && mc.options.getPerspective().isFirstPerson();
        float spread = active ? lightningSize.getValue() : 0f;

        handHolding[0] = active && !mc.player.getMainHandStack().isEmpty();
        handHolding[1] = active && !mc.player.getOffHandStack().isEmpty();

        // только спавн/обновление болтов — сама отрисовка идёт после рук и стекла,
        // иначе молнии остаются под предметом в руке
        handLightning[0].updatePointBolts(handHolding[0] ? getHeldItemPos(Hand.MAIN_HAND) : null, spread);
        handLightning[1].updatePointBolts(handHolding[1] ? getHeldItemPos(Hand.OFF_HAND) : null, spread);
    }

    /** Молнии поверх рук и стеклянного эффекта — вызывается сразу после renderGlassEffect(). */
    public void renderHandLightningPostHands() {
        if (!isEnabled() || !lightning.getValue() || mc.player == null || mc.world == null
                || !mc.options.getPerspective().isFirstPerson() || frame3dStack == null) {
            return;
        }
        if (!handHolding[0] && !handHolding[1]) return;

        // после фазы рук RenderSystem держит hud-проекцию (GameRenderer.renderWorld
        // переключает её перед renderHand) — временно возвращаем мировую, как в мировом проходе
        if (lightningProjection == null) {
            lightningProjection = new RawProjectionMatrix("glass_hands_lightning");
        }
        RenderSystem.backupProjectionMatrix();
        try {
            RenderSystem.setProjectionMatrix(
                    lightningProjection.set(new Matrix4f(RenderUtil.Render3D.lastProjMat)),
                    ProjectionType.PERSPECTIVE);
            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            try {
                modelView.set(RenderUtil.Render3D.lastModMat);
                drawAllLightning(1.0f);
            } finally {
                modelView.popMatrix();
            }
        } finally {
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private void drawAllLightning(float alpha) {
        if (handHolding[0]) handLightning[0].drawPointBolts(frame3dStack, alpha);
        if (handHolding[1]) handLightning[1].drawPointBolts(frame3dStack, alpha);
    }

    /** Мировая позиция центра предмета в руке: оффект экипировки (±0.56, -0.52, -0.72) + подъём к центру модели, повёрнутый камерой. */
    private Vec3d getHeldItemPos(Hand hand) {
        boolean mainSide = (hand == Hand.MAIN_HAND) == (mc.player.getMainArm() == Arm.RIGHT);
        float side = mainSide ? 0.56f : -0.56f;
        Vector3f local = new Vector3f(side, -0.52f + lightningOffset.getValue(), -0.72f);
        local.rotate(mc.gameRenderer.getCamera().getRotation().conjugate(new Quaternionf()));
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        return camPos.add(local.x, local.y, local.z);
    }
}

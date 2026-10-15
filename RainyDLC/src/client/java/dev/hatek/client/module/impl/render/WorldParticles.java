package dev.hatek.client.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ColorSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import dev.hatek.client.render.particles.FadeParticle;
import dev.hatek.client.render.particles.ParticleCollision;
import dev.hatek.client.render.particles.ParticleColors;
import dev.hatek.client.render.particles.ParticleRenderer;
import dev.hatek.client.render.particles.ParticleTexturePicker;
import dev.hatek.client.render.particles.dashlines.DashLineField;
import dev.hatek.client.render.particles.dashlines.DashLineRenderer;
import dev.hatek.client.ui.Style;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Частицы вокруг игрока («Частицы») или неоновые штрихи скорости («Штрихи»).
 * Порт rtx.kimiko WorldParticles под FrostixDLC.
 *
 * Отличия от оригинала (см. PORT_NOTES.md):
 * - события kimiko заменены на onClientTick() и хук ParticlesWorldMixin
 *   (TAIL LevelRenderer.submitEntities);
 * - SeparatorSetting в hatek нет — разделители опущены;
 * - visibleWhen-условия опущены (в hatek Setting нет такого API);
 * - «Клиент»-цвет = Style.accent();
 * - isInPlayerView использует player.getLookAngle() вместо pitch+cameraYaw;
 * - интерполяция renderPos — partialTicks=1.0 (в хуке submitEntities partial tick недоступен).
 */
public final class WorldParticles extends Module {
    private static final int MAX_PARTICLES = 1500;

    private static final String MODE_PARTICLES = "Частицы";
    private static final String MODE_DASH = "Штрихи";

    private static WorldParticles instance;

    private final ModeSetting mode;
    private final SliderSetting dashCount;
    private final SliderSetting dashLength;
    private final SliderSetting dashThickness;
    private final SliderSetting dashSpeed;
    private final SliderSetting dashRadius;
    private final SliderSetting dashSpread;
    private final SliderSetting dashOpacity;
    private final SliderSetting dashGlow;
    private final BoolSetting dashMotionOnly;

    private final ModeSetting display;
    private final SliderSetting size;
    private final BoolSetting scaleWithAlpha;
    private final SliderSetting count;
    private final SliderSetting range;
    private final SliderSetting rangeY;
    private final SliderSetting lifetime;
    private final SliderSetting motionPower;
    private final SliderSetting gravity;
    private final SliderSetting inclineX;
    private final SliderSetting inclineZ;
    private final ModeSetting collideMode;
    private final ModeSetting colorMode;
    private final BoolSetting useSecondColor;
    private final ColorSetting customColor;
    private final ColorSetting customSecondColor;

    private final List<FadeParticle> particles = new ArrayList<>();
    private final Random random = new Random();
    private final ParticleRenderer renderer = new ParticleRenderer();
    private final DashLineField dashField = new DashLineField();
    private final DashLineRenderer dashRenderer = new DashLineRenderer();
    private long lastFrameNanos;

    public WorldParticles() {
        super("WorldParticles", "Частицы и неоновые штрихи вокруг игрока", Category.RENDER);
        instance = this;

        this.mode = new ModeSetting("Режим", 0, MODE_PARTICLES, MODE_DASH);
        this.dashCount = new SliderSetting("Плотность", 70.0, 10.0, 220.0, 5.0);
        this.dashLength = new SliderSetting("Длина", 100.0, 25.0, 300.0, 5.0);
        this.dashThickness = new SliderSetting("Толщина", 100.0, 25.0, 300.0, 5.0);
        this.dashSpeed = new SliderSetting("Скорость", 100.0, 30.0, 250.0, 5.0);
        this.dashRadius = new SliderSetting("Радиус", 4.0, 1.0, 12.0, 0.5);
        this.dashSpread = new SliderSetting("Разброс наклона", 20.0, 0.0, 60.0, 1.0);
        this.dashOpacity = new SliderSetting("Прозрачность", 85.0, 5.0, 100.0, 1.0);
        this.dashGlow = new SliderSetting("Свечение", 60.0, 0.0, 100.0, 1.0);
        this.dashMotionOnly = new BoolSetting("Только при движении", true);

        this.display = new ModeSetting("Отображать", 0, ParticleTexturePicker.modeOptions());
        this.size = new SliderSetting("Размер", 150.0, 100.0, 200.0, 10.0);
        this.scaleWithAlpha = new BoolSetting("Скейл", true);
        this.count = new SliderSetting("Количество", 15.0, 1.0, 60.0, 1.0);
        this.range = new SliderSetting("Радиус спавна", 50.0, 10.0, 50.0, 1.0);
        this.rangeY = new SliderSetting("Высота спавна", 30.0, 0.05, 30.0, 0.05);
        this.lifetime = new SliderSetting("Время жизни", 800.0, 150.0, 1500.0, 10.0);
        this.motionPower = new SliderSetting("Сила движения", 1.0, 0.1, 2.0, 0.1);
        this.gravity = new SliderSetting("Сила гравитации", 0.0, -10.0, 10.0, 1.0);
        this.inclineX = new SliderSetting("Наклон X", 0.0, -17.5, 17.5, 0.5);
        this.inclineZ = new SliderSetting("Наклон Z", 17.5, -17.5, 17.5, 0.5);
        this.collideMode = new ModeSetting("При столкновении", 0, ParticleCollision.MODES);
        this.colorMode = new ModeSetting("Режим цвета", 0,
                ParticleColors.CLIENT, ParticleColors.RAINBOW, ParticleColors.CUSTOM);
        this.useSecondColor = new BoolSetting("Второй цвет", false);
        this.customColor = new ColorSetting("Цвет", 0xFFFFFFFF);
        this.customSecondColor = new ColorSetting("Цвет 2",
                ParticleColors.lerpColor(0xFFFFFFFF, 0x4B101010, 0.7f));

        with(mode,
                dashCount, dashLength, dashThickness, dashSpeed, dashRadius,
                dashSpread, dashOpacity, dashGlow, dashMotionOnly,
                display, size, scaleWithAlpha, count, range, rangeY, lifetime,
                motionPower, gravity, inclineX, inclineZ, collideMode,
                colorMode, useSecondColor, customColor, customSecondColor);
    }

    public static WorldParticles instance() {
        return instance;
    }

    /** Вызывается из ParticlesWorldMixin (TAIL LevelRenderer.submitEntities). */
    public static void renderWorld(PoseStack stack, LevelRenderState state, SubmitNodeCollector collector) {
        WorldParticles m = instance;
        if (m == null || !m.isEnabled()) {
            return;
        }
        if (collector instanceof OrderedSubmitNodeCollector ordered) {
            if (m.mode.is(MODE_DASH)) {
                m.renderDashLines(stack, state, ordered);
            } else {
                m.renderParticles(stack, state, ordered);
            }
        }
    }

    @Override
    protected void onEnable() {
        this.particles.clear();
        this.dashField.clear();
        this.lastFrameNanos = 0L;
    }

    @Override
    protected void onDisable() {
        this.particles.clear();
        this.dashField.clear();
        this.lastFrameNanos = 0L;
    }

    @Override
    public void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (this.mode.is(MODE_DASH)) {
            this.particles.clear();
            return;
        }
        this.dashField.clear();
        Player player = mc.player;
        if (mc.level == null || player == null) {
            this.particles.clear();
            return;
        }
        long now = System.currentTimeMillis();
        this.particles.removeIf(p -> p.isDead(now));
        float power = (float) this.motionPower.value();
        float gravityPerTick = (float) (this.gravity.value() / 80.0 * 0.05 * power);
        float collisionGravity = Mth.clamp((float) (this.gravity.value() / 10.0), 0.0f, 1.0f);
        String collide = this.collideMode.value();
        float yaw = player.getYRot();
        double xY = Math.sin(Math.toRadians(yaw));
        double zY = -Math.cos(Math.toRadians(yaw));
        double xX = -Math.sin(Math.toRadians(yaw + 90.0));
        double zX = Math.cos(Math.toRadians(yaw + 90.0));
        double addX = (xY * this.inclineZ.value() / 50.0 + xX * this.inclineX.value() / 50.0) * 0.05 * power;
        double addZ = (zY * this.inclineZ.value() / 50.0 + zX * this.inclineX.value() / 50.0) * 0.05 * power;
        for (FadeParticle particle : this.particles) {
            stepParticle(mc, particle, power, addX, gravityPerTick, addZ, collide, collisionGravity);
        }
        spawnParticles(mc, player);
    }

    private static void stepParticle(Minecraft mc, FadeParticle particle, float power,
                                     double addX, double gravityPerTick, double addZ,
                                     String collide, float collisionGravity) {
        particle.beginStep();
        if (!particle.holdCollide) {
            particle.posX += particle.motionX * power;
            particle.posY += particle.motionY * power;
            particle.posZ += particle.motionZ * power;
            particle.motionX += addX;
            particle.motionY += gravityPerTick;
            particle.motionZ += addZ;
        }
        particle.motionY -= 6.0E-4;
        boolean cancelMove = ParticleCollision.apply(mc, particle, collide, collisionGravity);
        if (!cancelMove) {
            particle.posX += particle.motionX;
            particle.posY += particle.motionY;
            particle.posZ += particle.motionZ;
        }
    }

    private void spawnParticles(Minecraft mc, Player player) {
        int attempts = (int) this.count.value();
        float rangeValue = (float) this.range.value();
        float rangeYValue = Math.max(0.55f, (float) this.rangeY.value());
        float lifetimeValue = (float) this.lifetime.value();
        for (int i = 0; i < attempts && this.particles.size() < MAX_PARTICLES; i++) {
            double offsetX = randomRange(-rangeValue, rangeValue);
            double offsetZ = randomRange(-rangeValue, rangeValue);
            double offsetY = randomRange(0.5, rangeYValue);
            Vec3 pos = player.position().add(offsetX, offsetY, offsetZ);
            if (!mc.level.getBlockState(BlockPos.containing(pos)).isAir() || !isInPlayerView(mc, player, pos)) {
                continue;
            }
            Vec3 motion = new Vec3(randomRange(-0.04, 0.04), randomRange(0.0, 0.05), randomRange(-0.04, 0.04));
            float gradientT = attempts <= 1 ? 0.0f : (float) i / (attempts - 1);
            this.particles.add(new FadeParticle(pos, motion, (float) randomRange(0.0, 180.0),
                    lifetimeValue, ParticleTexturePicker.pick(this.display, this.random), gradientT));
        }
    }

    private static boolean isInPlayerView(Minecraft mc, Player player, Vec3 pos) {
        Vec3 view = player.getLookAngle();
        Vec3 to = pos.subtract(player.position()).normalize();
        return view.x * to.x + view.y * to.y + view.z * to.z > 0.1;
    }

    private void renderParticles(PoseStack stack, LevelRenderState state, OrderedSubmitNodeCollector ordered) {
        if (this.particles.isEmpty()) {
            return;
        }
        Vec3 cameraPos = state.cameraRenderState.pos;
        Quaternionf camOrientation = state.cameraRenderState.orientation;
        Vector3f forward = new Vector3f(0.0f, 0.0f, -1.0f).rotate(camOrientation);
        long now = System.currentTimeMillis();
        float worldSize = (float) (this.size.value() * 0.012f);
        boolean scaleAlpha = this.scaleWithAlpha.value();
        this.renderer.clear();
        for (FadeParticle particle : this.particles) {
            float alpha = particle.alpha01(now);
            if (alpha <= 0.004f) {
                continue;
            }
            Vec3 renderPos = particle.renderPos(1.0f);
            double toX = renderPos.x - cameraPos.x;
            double toY = renderPos.y - cameraPos.y;
            double toZ = renderPos.z - cameraPos.z;
            if (toX * forward.x() + toY * forward.y() + toZ * forward.z() < -worldSize * 2.0f) {
                continue;
            }
            int color = particleColor(particle, alpha);
            float renderSize = scaleAlpha ? worldSize * alpha : worldSize;
            this.renderer.drawTexture(particle.texture, renderPos, renderSize,
                    particle.rotationDeg, color, true);
        }
        this.renderer.flush(stack, ordered, cameraPos, camOrientation);
    }

    private void renderDashLines(PoseStack stack, LevelRenderState state, OrderedSubmitNodeCollector ordered) {
        long now = System.nanoTime();
        float frameSeconds = this.lastFrameNanos == 0L ? 0.0f : (now - this.lastFrameNanos) / 1.0E9f;
        this.lastFrameNanos = now;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cameraPos = state.cameraRenderState.pos;
        this.dashField.update(mc, cameraPos, frameSeconds,
                (int) this.dashCount.value(),
                (float) this.dashRadius.value(),
                (float) (this.dashLength.value() / 100.0),
                (float) (this.dashThickness.value() / 100.0),
                (float) (this.dashSpeed.value() / 100.0),
                (float) this.dashSpread.value(),
                this.dashMotionOnly.value());
        if (this.dashField.alive().isEmpty()) {
            return;
        }
        this.dashRenderer.render(stack, ordered, this.dashField, cameraPos,
                gradientColor(0), gradientColor(150),
                (float) (this.dashOpacity.value() / 100.0),
                (float) (this.dashGlow.value() / 100.0));
    }

    private int gradientColor(int offset) {
        if (this.colorMode.is(ParticleColors.RAINBOW)) {
            return ParticleColors.rainbow(8, offset, 1.0f, 1.0f, 1.0f);
        }
        if (this.colorMode.is(ParticleColors.CLIENT)) {
            return Style.accent() | 0xFF000000;
        }
        if (offset == 0 || !this.useSecondColor.value()) {
            return this.customColor.value() | 0xFF000000;
        }
        return this.customSecondColor.value() | 0xFF000000;
    }

    private int particleColor(FadeParticle particle, float alpha) {
        int index = (int) (particle.gradientT * 360.0f);
        if (this.colorMode.is(ParticleColors.RAINBOW)) {
            return ParticleColors.rainbow(8, index, 1.0f, 1.0f, alpha);
        }
        int firstColor;
        int secondColor;
        if (this.colorMode.is(ParticleColors.CLIENT)) {
            firstColor = Style.accent() | 0xFF000000;
            secondColor = firstColor;
        } else {
            firstColor = this.customColor.value();
            secondColor = this.useSecondColor.value() ? this.customSecondColor.value() : this.customColor.value();
        }
        if (firstColor == secondColor) {
            return ParticleColors.multAlpha(firstColor, alpha);
        }
        return ParticleColors.multAlpha(ParticleColors.fade(8, index, firstColor, secondColor), alpha);
    }

    private double randomRange(double min, double max) {
        return min + this.random.nextDouble() * (max - min);
    }
}

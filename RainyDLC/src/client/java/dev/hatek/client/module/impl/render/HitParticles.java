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
import dev.hatek.client.render.particles.WorldParticleUtil;
import dev.hatek.client.ui.Style;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Частицы при ударе по сущности. Порт rtx.kimiko HitParticles под FrostixDLC.
 *
 * Отличия от оригинала (см. PORT_NOTES.md):
 * - события kimiko (AttackEntityEvent/TickEvent/WorldRenderEvent) заменены на:
 *   хук ParticlesAttackMixin (TAIL MultiPlayerGameMode.attack), onClientTick(),
 *   хук ParticlesWorldMixin (TAIL LevelRenderer.submitEntities);
 * - визуальный fade модуля (visualAlpha) убран — в hatek Module нет анимации затухания;
 * - «Клиент»-цвет = Style.accent() (у kimiko была палитра темы);
 * - центр спавна = position + bbHeight/2 (проверенные 26.2 API).
 */
public final class HitParticles extends Module {
    private static final int MAX_PARTICLES = 1000;
    private static final double TWIST_TURN_PER_TICK = 0.09;

    private static final String DIR_RANDOM = "Случайно";
    private static final String DIR_FROM_TARGET = "От цели";
    private static final String DIR_TO_TARGET = "К цели";
    private static final String DIR_TWIST = "Закручивание";

    private static HitParticles instance;

    private final ModeSetting display;
    private final SliderSetting size;
    private final BoolSetting scaleWithAlpha;
    private final SliderSetting count;
    private final SliderSetting spread;
    private final ModeSetting spawnDirection;
    private final SliderSetting lifetime;
    private final SliderSetting speed;
    private final SliderSetting gravity;
    private final ModeSetting collideMode;
    private final ModeSetting colorMode;
    private final BoolSetting useSecondColor;
    private final ColorSetting customColor;
    private final ColorSetting customSecondColor;

    private final List<FadeParticle> particles = new ArrayList<>();
    private final Random random = new Random();
    private final ParticleRenderer renderer = new ParticleRenderer();

    public HitParticles() {
        super("HitParticles", "Частицы при ударе по сущности", Category.RENDER);
        instance = this;

        this.display = new ModeSetting("Отображать", 0, ParticleTexturePicker.modeOptions());
        this.size = new SliderSetting("Размер", 25.0, 15.0, 56.0, 1.0);
        this.scaleWithAlpha = new BoolSetting("Скейл", true);
        this.count = new SliderSetting("Количество", 56.0, 3.0, 56.0, 1.0);
        this.spread = new SliderSetting("Разброс", 0.5, 0.5, 6.0, 0.5);
        this.spawnDirection = new ModeSetting("Пресет полёта", 3,
                DIR_RANDOM, DIR_FROM_TARGET, DIR_TO_TARGET, DIR_TWIST);
        this.lifetime = new SliderSetting("Время жизни", 2000.0, 350.0, 2000.0, 25.0);
        this.speed = new SliderSetting("Скорость", 0.3, 0.05, 1.0, 0.05);
        this.gravity = new SliderSetting("Гравитация", 0.2, -0.5, 1.0, 0.05);
        this.collideMode = new ModeSetting("При столкновении", 0, ParticleCollision.MODES);
        this.colorMode = new ModeSetting("Режим цвета", 0,
                ParticleColors.CLIENT, ParticleColors.RAINBOW, ParticleColors.CUSTOM);
        this.useSecondColor = new BoolSetting("Второй цвет", false);
        this.customColor = new ColorSetting("Цвет", 0xFFFFFFFF);
        this.customSecondColor = new ColorSetting("Цвет 2",
                ParticleColors.lerpColor(0xFFFFFFFF, 0x4B101010, 0.7f));

        with(display, size, scaleWithAlpha, count, spread, spawnDirection, lifetime,
                speed, gravity, collideMode, colorMode, useSecondColor, customColor, customSecondColor);
    }

    public static HitParticles instance() {
        return instance;
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    /** Вызывается из ParticlesAttackMixin (TAIL attack) — атака реально произошла. */
    public static void onAttack(Entity target) {
        HitParticles m = instance;
        if (m == null || !m.isEnabled() || target == null) {
            return;
        }
        m.spawnBurst(target);
    }

    /** Вызывается из ParticlesWorldMixin (TAIL LevelRenderer.submitEntities). */
    public static void renderWorld(PoseStack stack, LevelRenderState state, SubmitNodeCollector collector) {
        HitParticles m = instance;
        if (m == null || !m.isEnabled() || m.particles.isEmpty()) {
            return;
        }
        if (collector instanceof OrderedSubmitNodeCollector ordered) {
            m.render(stack, state, ordered);
        }
    }

    @Override
    protected void onEnable() {
        this.particles.clear();
    }

    @Override
    protected void onDisable() {
        this.particles.clear();
    }

    private void spawnBurst(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        int spawnCount = (int) this.count.value();
        float lifetimeValue = (float) this.lifetime.value();
        float spreadValue = (float) this.spread.value();
        float speedValue = (float) this.speed.value();
        Vec3 center = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        float volleyTwist = this.spawnDirection.is(DIR_TWIST) ? (this.random.nextBoolean() ? 1.0f : -1.0f) : 0.0f;
        for (int i = 0; i < spawnCount && this.particles.size() < MAX_PARTICLES; i++) {
            Vec3 pos = WorldParticleUtil.randomSpawnPosition(mc, this.random, spreadValue, center);
            if (pos == null) {
                continue;
            }
            double motionSpeed = randomRange(speedValue / 1.5, speedValue * 1.5);
            Vec3 motion = initialMotion(center, pos, motionSpeed, volleyTwist);
            float gradientT = spawnCount <= 1 ? 0.0f : (float) i / (spawnCount - 1);
            FadeParticle particle = new FadeParticle(pos, motion, (float) randomRange(0.0, 180.0),
                    lifetimeValue, ParticleTexturePicker.pick(this.display, this.random), gradientT);
            particle.twist = volleyTwist;
            this.particles.add(particle);
        }
    }

    private Vec3 initialMotion(Vec3 center, Vec3 pos, double motionSpeed, float volleyTwist) {
        if (this.spawnDirection.is(DIR_FROM_TARGET)) {
            Vec3 out = pos.subtract(center);
            if (out.x * out.x + out.y * out.y + out.z * out.z < 1.0E-4) {
                return randomUnitDirection().scale(motionSpeed);
            }
            return out.normalize().scale(motionSpeed);
        }
        if (this.spawnDirection.is(DIR_TO_TARGET)) {
            Vec3 in = center.subtract(pos);
            if (in.x * in.x + in.y * in.y + in.z * in.z < 1.0E-4) {
                return randomUnitDirection().scale(motionSpeed);
            }
            return in.normalize().scale(motionSpeed);
        }
        if (this.spawnDirection.is(DIR_TWIST)) {
            Vec3 radial = horizontalDir(pos.subtract(center));
            double tx = -radial.z * volleyTwist;
            double tz = radial.x * volleyTwist;
            Vec3 dir = new Vec3(tx * 0.85 + radial.x * 0.25, randomRange(0.15, 0.45), tz * 0.85 + radial.z * 0.25)
                    .normalize();
            return dir.scale(motionSpeed);
        }
        return randomUnitDirection().scale(motionSpeed);
    }

    private Vec3 randomUnitDirection() {
        double yaw = randomRange(0.0, Math.PI * 2.0);
        double y = randomRange(-0.35, 0.75);
        double horizontal = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        return new Vec3(Math.cos(yaw) * horizontal, y, Math.sin(yaw) * horizontal);
    }

    private Vec3 horizontalDir(Vec3 delta) {
        Vec3 flat = new Vec3(delta.x, 0.0, delta.z);
        if (flat.x * flat.x + flat.z * flat.z < 1.0E-4) {
            double angle = randomRange(0.0, Math.PI * 2.0);
            return new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
        }
        return flat.normalize();
    }

    @Override
    public void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            this.particles.clear();
            return;
        }
        long now = System.currentTimeMillis();
        this.particles.removeIf(p -> p.isDead(now));
        float gravityValue = (float) this.gravity.value();
        String collide = this.collideMode.value();
        for (FadeParticle particle : this.particles) {
            stepParticle(mc, particle, gravityValue, collide);
        }
    }

    private static void stepParticle(Minecraft mc, FadeParticle particle, float gravityValue, String collide) {
        particle.beginStep();
        if (particle.twist != 0.0f && !particle.holdCollide) {
            double angle = TWIST_TURN_PER_TICK * particle.twist;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double mx = particle.motionX * cos - particle.motionZ * sin;
            double mz = particle.motionX * sin + particle.motionZ * cos;
            particle.motionX = mx;
            particle.motionZ = mz;
        }
        particle.motionY -= gravityValue / 17.5;
        if (gravityValue > 0.0f && particle.motionY > 0.0) {
            particle.motionY /= 1.0 + gravityValue * 0.05;
        }
        boolean cancelMove = ParticleCollision.apply(mc, particle, collide, gravityValue);
        if (!cancelMove) {
            particle.posX += particle.motionX;
            particle.posY += particle.motionY;
            particle.posZ += particle.motionZ;
        }
    }

    private void render(PoseStack stack, LevelRenderState state, OrderedSubmitNodeCollector ordered) {
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
            Vec3 renderPos = particle.renderPos(1.0f).add(0.0, 0.2, 0.0);
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

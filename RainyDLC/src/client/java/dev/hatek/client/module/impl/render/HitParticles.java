package dev.hatek.client.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ColorSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import dev.hatek.client.render.particles.Particle;
import dev.hatek.client.render.particles.ParticleCollision;
import dev.hatek.client.render.particles.ParticleColors;
import dev.hatek.client.render.particles.ParticleRenderer;
import dev.hatek.client.render.particles.ParticleSystem;
import dev.hatek.client.render.particles.ParticleTexturePicker;
import dev.hatek.client.render.particles.WorldParticleUtil;
import dev.hatek.client.ui.Style;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Частицы при ударе по сущности.
 * Чистая реализация с нуля (не порт декомпила).
 *
 * «Количество» — сколько частиц спавнится за удар (читается в момент удара).
 * «Время жизни» — сколько миллисекунд живёт каждая частица (передаётся в Particle).
 */
public final class HitParticles extends Module {
    private static final int MAX_PARTICLES = 1000;
    /** Шаг закручивания потока за тик, радиан. */
    private static final float SWIRL_STEP = 0.09f;

    private static final String DIR_RANDOM = "Случайно";
    private static final String DIR_FROM_TARGET = "От цели";
    private static final String DIR_TO_TARGET = "К цели";
    private static final String DIR_TWIST = "Закручивание";

    private static HitParticles instance;

    private final SliderSetting count;
    private final SliderSetting lifetime;
    private final ModeSetting display;
    private final SliderSetting size;
    private final BoolSetting scaleWithAlpha;
    private final SliderSetting spread;
    private final ModeSetting spawnDirection;
    private final SliderSetting speed;
    private final SliderSetting gravity;
    private final ModeSetting collideMode;
    private final ModeSetting colorMode;
    private final BoolSetting useSecondColor;
    private final ColorSetting customColor;
    private final ColorSetting customSecondColor;

    private final ParticleSystem system = new ParticleSystem();
    private final ParticleRenderer renderer = new ParticleRenderer();
    private final Random random = new Random();

    public HitParticles() {
        super("HitParticles", "Частицы при ударе по сущности", Category.RENDER);
        instance = this;

        this.count = new SliderSetting("Количество", 56.0, 3.0, 56.0, 1.0);
        this.lifetime = new SliderSetting("Время жизни", 2000.0, 350.0, 2000.0, 25.0);
        this.display = new ModeSetting("Отображать", 0, ParticleTexturePicker.modeOptions());
        this.size = new SliderSetting("Размер", 25.0, 15.0, 56.0, 1.0);
        this.scaleWithAlpha = new BoolSetting("Скейл", true);
        this.spread = new SliderSetting("Разброс", 0.5, 0.5, 6.0, 0.5);
        this.spawnDirection = new ModeSetting("Пресет полёта", 3,
                DIR_RANDOM, DIR_FROM_TARGET, DIR_TO_TARGET, DIR_TWIST);
        this.speed = new SliderSetting("Скорость", 0.3, 0.05, 1.0, 0.05);
        this.gravity = new SliderSetting("Гравитация", 0.2, -0.5, 1.0, 0.05);
        this.collideMode = new ModeSetting("При столкновении", 0, ParticleCollision.MODES);
        this.colorMode = new ModeSetting("Режим цвета", 0,
                ParticleColors.CLIENT, ParticleColors.RAINBOW, ParticleColors.CUSTOM);
        this.useSecondColor = new BoolSetting("Второй цвет", false);
        this.customColor = new ColorSetting("Цвет", 0xFFFFFFFF);
        this.customSecondColor = new ColorSetting("Цвет 2",
                ParticleColors.lerpColor(0xFFFFFFFF, 0x4B101010, 0.7f));

        with(count, lifetime, display, size, scaleWithAlpha, spread, spawnDirection,
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
        if (m == null || !m.isEnabled() || m.system.isEmpty()) {
            return;
        }
        if (collector instanceof OrderedSubmitNodeCollector ordered) {
            m.render(stack, state, ordered);
        }
    }

    @Override
    protected void onEnable() {
        this.system.clear();
    }

    @Override
    protected void onDisable() {
        this.system.clear();
    }

    @Override
    public void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            this.system.clear();
            return;
        }
        // Настройки применяются здесь: гравитация — в физику каждый тик.
        this.system.tick(mc, this.gravity.value() * 0.05, this.collideMode.value());
    }

    /** Спавн залпа: «Количество» частиц, каждой — «Время жизни» миллисекунд. */
    private void spawnBurst(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        int spawnCount = (int) Math.round(this.count.value());
        float lifeMs = (float) this.lifetime.value();
        float spread = (float) this.spread.value();
        float baseSpeed = (float) this.speed.value();
        float size = (float) (this.size.value() * 0.012f);
        Vec3 center = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        long now = System.currentTimeMillis();

        boolean twist = this.spawnDirection.is(DIR_TWIST);
        float swirl = twist ? (this.random.nextBoolean() ? SWIRL_STEP : -SWIRL_STEP) : 0.0f;

        for (int i = 0; i < spawnCount && this.system.size() < MAX_PARTICLES; i++) {
            Vec3 pos = spawnPosition(mc, center, spread);
            if (pos == null) {
                continue;
            }
            double speed = baseSpeed * (0.5 + this.random.nextDouble());
            double[] v = initialVelocity(center, pos, speed, swirl);
            Identifier texture = ParticleTexturePicker.pick(this.display, this.random);
            float tintSeed = spawnCount <= 1 ? 0.0f : (float) i / (spawnCount - 1);
            this.system.add(new Particle(
                    pos.x, pos.y, pos.z, v[0], v[1], v[2],
                    now, lifeMs, size,
                    (float) (this.random.nextDouble() * 180.0),
                    (float) (this.random.nextDouble() * 6.0 - 3.0),
                    texture, tintSeed, swirl));
        }
    }

    /** Случайная точка в сфере радиуса spread вокруг центра (только воздух). */
    private Vec3 spawnPosition(Minecraft mc, Vec3 center, float spread) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double theta = this.random.nextDouble() * Math.PI * 2.0;
            double cosPhi = this.random.nextDouble() * 2.0 - 1.0;
            double sinPhi = Math.sqrt(Math.max(0.0, 1.0 - cosPhi * cosPhi));
            double r = spread * Math.cbrt(this.random.nextDouble());
            double x = center.x + Math.cos(theta) * sinPhi * r;
            double y = center.y + cosPhi * r;
            double z = center.z + Math.sin(theta) * sinPhi * r;
            if (!WorldParticleUtil.isSolidCollisionBlock(mc, x, y, z)) {
                return new Vec3(x, y, z);
            }
        }
        return null;
    }

    /** Начальная скорость по пресету направления, блоков/тик. */
    private double[] initialVelocity(Vec3 center, Vec3 pos, double speed, float swirl) {
        double dx = pos.x - center.x;
        double dy = pos.y - center.y;
        double dz = pos.z - center.z;
        if (this.spawnDirection.is(DIR_FROM_TARGET)) {
            return scaledDirection(dx, dy, dz, speed);
        }
        if (this.spawnDirection.is(DIR_TO_TARGET)) {
            return scaledDirection(-dx, -dy, -dz, speed);
        }
        if (this.spawnDirection.is(DIR_TWIST)) {
            // Касательное направление вокруг Y + немного вверх.
            double len = Math.sqrt(dx * dx + dz * dz);
            double tx;
            double tz;
            if (len < 1.0E-4) {
                double a = this.random.nextDouble() * Math.PI * 2.0;
                tx = Math.cos(a);
                tz = Math.sin(a);
            } else {
                float s = swirl >= 0.0f ? 1.0f : -1.0f;
                tx = -dz / len * s;
                tz = dx / len * s;
            }
            double ux = tx * 0.9 + dx * 0.2;
            double uy = 0.35;
            double uz = tz * 0.9 + dz * 0.2;
            return scaledDirection(ux, uy, uz, speed);
        }
        // Случайно: равномерное направление.
        double yaw = this.random.nextDouble() * Math.PI * 2.0;
        double y = this.random.nextDouble() * 1.1 - 0.35;
        double h = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        return new double[]{Math.cos(yaw) * h * speed, y * speed, Math.sin(yaw) * h * speed};
    }

    private static double[] scaledDirection(double dx, double dy, double dz, double speed) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6) {
            return new double[]{0.0, speed * 0.5, 0.0};
        }
        double k = speed / len;
        return new double[]{dx * k, dy * k, dz * k};
    }

    private void render(PoseStack stack, LevelRenderState state, OrderedSubmitNodeCollector ordered) {
        Vec3 cameraPos = state.cameraRenderState.pos;
        Quaternionf camOrientation = state.cameraRenderState.orientation;
        Vector3f forward = new Vector3f(0.0f, 0.0f, -1.0f).rotate(camOrientation);
        long now = System.currentTimeMillis();
        boolean scaleAlpha = this.scaleWithAlpha.value();
        this.renderer.clear();
        for (Particle p : this.system.particles()) {
            float alpha = p.alpha(now);
            if (alpha <= 0.004f) {
                continue;
            }
            // Отсечение за камерой.
            double relX = p.x - cameraPos.x;
            double relY = p.y - cameraPos.y;
            double relZ = p.z - cameraPos.z;
            if (relX * forward.x() + relY * forward.y() + relZ * forward.z() < -p.size) {
                continue;
            }
            float drawSize = scaleAlpha ? p.size * (0.35f + 0.65f * alpha) : p.size;
            this.renderer.drawTexture(p.texture, new Vec3(p.x, p.y, p.z),
                    drawSize, p.rotationDeg, particleColor(p, alpha), true);
        }
        this.renderer.flush(stack, ordered, cameraPos, camOrientation);
    }

    private int particleColor(Particle p, float alpha) {
        int index = (int) (p.tintSeed * 360.0f);
        if (this.colorMode.is(ParticleColors.RAINBOW)) {
            return ParticleColors.rainbow(8, index, 1.0f, 1.0f, alpha);
        }
        int first;
        int second;
        if (this.colorMode.is(ParticleColors.CLIENT)) {
            first = Style.accent() | 0xFF000000;
            second = first;
        } else {
            first = this.customColor.value();
            second = this.useSecondColor.value() ? this.customSecondColor.value() : this.customColor.value();
        }
        if (first == second) {
            return ParticleColors.multAlpha(first, alpha);
        }
        return ParticleColors.multAlpha(ParticleColors.fade(8, index, first, second), alpha);
    }
}

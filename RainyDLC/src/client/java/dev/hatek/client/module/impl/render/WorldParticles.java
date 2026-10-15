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
import dev.hatek.client.render.particles.speedlines.SpeedLineField;
import dev.hatek.client.render.particles.speedlines.SpeedLineRenderer;
import dev.hatek.client.ui.Style;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Частицы вокруг игрока («Частицы») или неоновые штрихи скорости («Штрихи»).
 * Чистая реализация с нуля (не порт декомпила).
 *
 * «Количество» — целевое число живых частиц вокруг игрока (доспавн каждый тик).
 * «Время жизни» — сколько миллисекунд живёт каждая частица (передаётся в Particle).
 */
public final class WorldParticles extends Module {
    private static final int MAX_PARTICLES = 1500;
    /** Сколько частиц доспавнить за тик максимум (без шипов). */
    private static final int SPAWN_BUDGET = 6;

    private static final String MODE_PARTICLES = "Частицы";
    private static final String MODE_DASH = "Штрихи";

    private static WorldParticles instance;

    private final SliderSetting count;
    private final SliderSetting lifetime;
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
    private final SliderSetting range;
    private final SliderSetting rangeY;
    private final SliderSetting motionPower;
    private final SliderSetting gravity;
    private final SliderSetting inclineX;
    private final SliderSetting inclineZ;
    private final ModeSetting collideMode;
    private final ModeSetting colorMode;
    private final BoolSetting useSecondColor;
    private final ColorSetting customColor;
    private final ColorSetting customSecondColor;

    private final ParticleSystem system = new ParticleSystem();
    private final ParticleRenderer renderer = new ParticleRenderer();
    private final SpeedLineField dashField = new SpeedLineField();
    private final SpeedLineRenderer dashRenderer = new SpeedLineRenderer();
    private final Random random = new Random();
    private long lastFrameNanos;

    public WorldParticles() {
        super("WorldParticles", "Частицы и неоновые штрихи вокруг игрока", Category.RENDER);
        instance = this;

        this.count = new SliderSetting("Количество", 15.0, 1.0, 60.0, 1.0);
        this.lifetime = new SliderSetting("Время жизни", 800.0, 150.0, 1500.0, 10.0);
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
        this.range = new SliderSetting("Радиус спавна", 50.0, 10.0, 50.0, 1.0);
        this.rangeY = new SliderSetting("Высота спавна", 30.0, 0.05, 30.0, 0.05);
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

        with(count, lifetime, mode,
                dashCount, dashLength, dashThickness, dashSpeed, dashRadius,
                dashSpread, dashOpacity, dashGlow, dashMotionOnly,
                display, size, scaleWithAlpha, range, rangeY,
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
        this.system.clear();
        this.dashField.clear();
        this.lastFrameNanos = 0L;
    }

    @Override
    protected void onDisable() {
        this.system.clear();
        this.dashField.clear();
        this.lastFrameNanos = 0L;
    }

    @Override
    public void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (this.mode.is(MODE_DASH)) {
            this.system.clear();
            return;
        }
        Player player = mc.player;
        if (mc.level == null || player == null) {
            this.system.clear();
            return;
        }
        // Наклон — постоянное ускорение дрейфа.
        double power = this.motionPower.value();
        double ax = this.inclineX.value() * 0.0002 * power;
        double az = this.inclineZ.value() * 0.0002 * power;
        if (ax != 0.0 || az != 0.0) {
            for (Particle p : this.system.particles()) {
                if (!p.stuck) {
                    p.vx += ax;
                    p.vz += az;
                }
            }
        }
        this.system.tick(mc, this.gravity.value() * 0.0006, this.collideMode.value());
        // Доспавн до целевого «Количества» живых частиц.
        int target = (int) Math.round(this.count.value());
        int budget = Math.min(SPAWN_BUDGET, target - this.system.size());
        for (int i = 0; i < budget; i++) {
            spawnAmbient(mc, player, power);
        }
    }

    /** Одна фоновая частица: «Время жизни» применяется здесь, при создании. */
    private void spawnAmbient(Minecraft mc, Player player, double power) {
        float rangeValue = (float) this.range.value();
        float heightValue = Math.max(0.55f, (float) this.rangeY.value());
        float lifeMs = (float) this.lifetime.value();
        double x = player.getX() + (this.random.nextDouble() * 2.0 - 1.0) * rangeValue;
        double y = player.getY() + 0.5 + this.random.nextDouble() * heightValue;
        double z = player.getZ() + (this.random.nextDouble() * 2.0 - 1.0) * rangeValue;
        if (WorldParticleUtil.isSolidCollisionBlock(mc, x, y, z)) {
            return;
        }
        Vec3 pos = new Vec3(x, y, z);
        if (!inView(player, pos)) {
            return;
        }
        double vx = (this.random.nextDouble() * 2.0 - 1.0) * 0.03 * power;
        double vy = this.random.nextDouble() * 0.04 * power;
        double vz = (this.random.nextDouble() * 2.0 - 1.0) * 0.03 * power;
        Identifier texture = ParticleTexturePicker.pick(this.display, this.random);
        this.system.add(new Particle(
                x, y, z, vx, vy, vz,
                System.currentTimeMillis(), lifeMs,
                (float) (this.size.value() * 0.012f),
                (float) (this.random.nextDouble() * 180.0),
                (float) (this.random.nextDouble() * 4.0 - 2.0),
                texture, this.random.nextFloat(), 0.0f));
    }

    /** Точка впереди игрока (в конусе взгляда). */
    private static boolean inView(Player player, Vec3 pos) {
        Vec3 look = player.getLookAngle();
        Vec3 to = pos.subtract(player.position());
        double len = Math.sqrt(to.x * to.x + to.y * to.y + to.z * to.z);
        if (len < 1.0E-6) {
            return true;
        }
        double dot = (look.x * to.x + look.y * to.y + look.z * to.z) / len;
        return dot > 0.1;
    }

    private void renderParticles(PoseStack stack, LevelRenderState state, OrderedSubmitNodeCollector ordered) {
        if (this.system.isEmpty()) {
            return;
        }
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

    private void renderDashLines(PoseStack stack, LevelRenderState state, OrderedSubmitNodeCollector ordered) {
        long now = System.nanoTime();
        float dtSeconds = this.lastFrameNanos == 0L ? 0.0f : (now - this.lastFrameNanos) / 1.0E9f;
        this.lastFrameNanos = now;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cameraPos = state.cameraRenderState.pos;
        this.dashField.update(mc, cameraPos, dtSeconds,
                (int) Math.round(this.dashCount.value()),
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

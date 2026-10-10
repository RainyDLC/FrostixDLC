package dev.hatek.client.module.impl.combat;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.impl.combat.aimassist.AimBrain;
import dev.hatek.client.module.impl.combat.aimassist.AimDataset;
import dev.hatek.client.module.impl.combat.aimassist.AimHud;
import dev.hatek.client.module.impl.combat.aimassist.AimSample;
import dev.hatek.client.module.impl.combat.aimassist.AimTrainScreen;
import dev.hatek.client.module.impl.combat.aimassist.DynamicButtonSetting;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * AimAssist — мягкая доводка прицела с раздельными хитбоксами
 * головы и тела и обучаемой нейронкой.
 *
 * <p>Как пользоваться обучением:
 * <ol>
 *   <li>Нажми «Обучить наводку» — откроется меню, введи название и нажми «Начать».</li>
 *   <li>Меню закроется, сверху появится прогресс. Играй как обычно:
 *       наводись и бей — записывается ТОЛЬКО наводка, не удары.</li>
 *   <li>Открой меню модуля снова и нажми «Стоп».</li>
 *   <li>Нажми «Обучить нейронку» — MLP научится повторять твою наводку.</li>
 * </ol>
 */
public final class AimAssist extends Module {
    private static AimAssist instance;

    public static final String POINT_HEAD = "Голова";
    public static final String POINT_BODY = "Тело";
    public static final String POINT_NEAREST = "Ближайшая";

    private static final int MIN_SAMPLES = 50;
    private static final int EPOCHS = 300;

    // ---- состояние обучения (читает HUD) ----
    public static volatile boolean training;
    public static volatile String trainingName = "";
    public static volatile int trainingSamples;
    public static volatile long trainingStartMs;
    public static volatile boolean learning;
    public static volatile double learnProgress;
    public static volatile String learnStatus = "";

    private final ModeSetting aimPoint = new ModeSetting("Прицел", 2,
            POINT_HEAD, POINT_BODY, POINT_NEAREST);
    private final SliderSetting strength = new SliderSetting("Сила доводки, %", 55.0, 0.0, 100.0, 1.0);
    private final SliderSetting speed = new SliderSetting("Скорость, °/сек", 120.0, 20.0, 360.0, 5.0);
    private final SliderSetting smoothness = new SliderSetting("Плавность", 6.0, 1.0, 20.0, 1.0);
    private final SliderSetting range = new SliderSetting("Дистанция", 4.5, 1.0, 8.0, 0.1);
    private final SliderSetting fov = new SliderSetting("FOV захвата", 60.0, 10.0, 180.0, 1.0);
    private final BoolSetting onlyWhileAttacking = new BoolSetting("Только при атаке", true);
    private final BoolSetting useBrain = new BoolSetting("Умная доводка (нейронка)", true);

    private final DynamicButtonSetting trainButton =
            new DynamicButtonSetting("Обучение", "Обучить наводку", this::onTrainButton);
    private final DynamicButtonSetting learnButton =
            new DynamicButtonSetting("Нейронка", "Обучить нейронку", this::onLearnButton);

    private AimDataset dataset;
    private AimBrain brain = new AimBrain();
    private String activeProfile = "";

    private float lastYaw;
    private float lastPitch;
    private boolean hasLast;
    private float[] prevFeatures;
    private Vec3 lastTargetPos;
    /** Текущая скорость доводки (°/тик) — сглаживается для плавности. */
    private float velYaw;
    private float velPitch;

    public AimAssist() {
        super("AimAssist", "Мягко доводит прицел (голова/тело), учится твоей наводке",
                Category.COMBAT);
        instance = this;
        keybind(GLFW.GLFW_KEY_UNKNOWN);
        with(this.aimPoint, this.strength, this.speed, this.smoothness, this.range, this.fov,
                this.onlyWhileAttacking, this.useBrain, this.trainButton, this.learnButton);
        this.learnButton.enabled(false);
        AimHud.init();
        loadActiveProfile();
    }

    public static AimAssist instance() {
        return instance;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    // ---------------- хитбоксы ----------------

    /** Центр хитбокса головы: между уровнем глаз и макушкой. */
    public static Vec3 headPoint(LivingEntity entity) {
        AABB box = entity.getBoundingBox();
        return new Vec3((box.minX + box.maxX) * 0.5,
                (entity.getEyeY() + box.maxY) * 0.5,
                (box.minZ + box.maxZ) * 0.5);
    }

    /** Центр хитбокса тела. */
    public static Vec3 bodyPoint(LivingEntity entity) {
        return entity.getBoundingBox().getCenter();
    }

    /** Углы (yaw, pitch), чтобы смотреть из {@code from} в {@code to}. */
    public static float[] rotTo(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        float yaw = (float) (Math.toDegrees(Mth.atan2(d.z, d.x)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Mth.atan2(d.y, Math.hypot(d.x, d.z))));
        return new float[]{yaw, pitch};
    }

    // ---------------- обучение: кнопки ----------------

    private void onTrainButton() {
        if (training) {
            stopTraining();
        } else {
            mc().gui.setScreen(new AimTrainScreen(this));
        }
    }

    private void onLearnButton() {
        if (learning || training || this.dataset == null || this.dataset.size() < MIN_SAMPLES) {
            return;
        }
        startLearning();
    }

    /** Вызывается из {@link AimTrainScreen} после ввода названия. */
    public void startTraining(String name) {
        this.dataset = new AimDataset(name);
        trainingName = name;
        trainingSamples = 0;
        trainingStartMs = System.currentTimeMillis();
        training = true;
        this.hasLast = false;
        this.prevFeatures = null;
        this.lastTargetPos = null;
        this.trainButton.caption("Стоп");
        this.learnButton.enabled(false);
    }

    public void stopTraining() {
        training = false;
        this.trainButton.caption("Обучить наводку");
        if (this.dataset != null) {
            try {
                this.dataset.save();
                learnStatus = "Записано " + this.dataset.size()
                        + " сэмплов — нажми «Обучить нейронку»";
            } catch (IOException e) {
                learnStatus = "Ошибка сохранения: " + e.getMessage();
            }
            this.learnButton.enabled(this.dataset.size() >= MIN_SAMPLES && !learning);
        }
        refreshCaptions();
    }

    public boolean isTraining() {
        return training;
    }

    private void refreshCaptions() {
        this.trainButton.caption(training ? "Стоп" : "Обучить наводку");
        boolean canLearn = !training && !learning
                && this.dataset != null && this.dataset.size() >= MIN_SAMPLES;
        this.learnButton.enabled(canLearn);
        this.learnButton.caption(learning ? "Обучение..."
                : this.brain.isTrained() ? "Переобучить нейронку" : "Обучить нейронку");
    }

    private void startLearning() {
        learning = true;
        learnProgress = 0.0;
        learnStatus = "Подготовка...";
        refreshCaptions();
        List<AimSample> samples = this.dataset.copy();
        String profile = this.dataset.name();
        new Thread(() -> {
            try {
                AimBrain next = new AimBrain();
                next.train(samples, EPOCHS, (p, loss) -> {
                    learnProgress = p;
                    learnStatus = "Эпоха " + (int) Math.round(p * EPOCHS) + "/" + EPOCHS
                            + " · loss " + String.format("%.4f", loss);
                });
                next.save(AimBrain.weightsFileOf(profile));
                this.brain = next;
                this.activeProfile = profile;
                saveActiveProfile();
                learnStatus = "Готово: «" + profile + "», " + samples.size()
                        + " сэмплов · loss " + String.format("%.4f", next.lastLoss);
            } catch (Exception e) {
                learnStatus = "Ошибка: " + e.getMessage();
            } finally {
                learning = false;
                learnProgress = 1.0;
                mc().execute(this::refreshCaptions);
            }
        }, "aimassist-learn").start();
    }

    // ---------------- тик ----------------

    @Override
    public void onClientTick() {
        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) {
            return;
        }

        LivingEntity target = findTarget();

        if (training) {
            // Записываем только наводку: признаки цели ДО доворота мыши
            // в паре с реальным доворотом игрока за этот тик.
            float yawDelta = this.hasLast
                    ? Mth.wrapDegrees(mc.player.getYRot() - this.lastYaw) : 0.0f;
            float pitchDelta = this.hasLast
                    ? mc.player.getXRot() - this.lastPitch : 0.0f;
            float[] feat = target == null ? null
                    : features(target, mc.player.getEyePosition(1.0f));
            if (target != null && this.hasLast && this.prevFeatures != null
                    && Math.abs(yawDelta) + Math.abs(pitchDelta) > 0.005f) {
                this.dataset.add(new AimSample(this.prevFeatures,
                        new float[]{yawDelta, pitchDelta}));
                trainingSamples = this.dataset.size();
            }
            this.prevFeatures = feat;
            this.hasLast = true;
            this.lastYaw = mc.player.getYRot();
            this.lastPitch = mc.player.getXRot();
            decayVelocity(); // во время обучения асист выключен
            return;
        }

        this.hasLast = false;
        this.prevFeatures = null;
        if (target == null
                || (this.onlyWhileAttacking.value() && !mc.options.keyAttack.isDown())) {
            decayVelocity(); // цели нет — плавно останавливаем доводку
            applyVelocity(mc);
            return;
        }

        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 point = choosePoint(target);
        float[] rot = rotTo(eye, point);
        float dYaw = Mth.wrapDegrees(rot[0] - mc.player.getYRot());
        float dPitch = rot[1] - mc.player.getXRot();

        // Уже наведены — плавно гасим доводку, не мешаем мыши.
        if (Math.abs(dYaw) < 0.2f && Math.abs(dPitch) < 0.2f) {
            decayVelocity();
            applyVelocity(mc);
            return;
        }

        float k = (float) (this.strength.value() / 100.0);
        float turnYaw;
        float turnPitch;
        if (this.useBrain.value() && this.brain.isTrained()) {
            float[] features = features(target, eye);
            float[] pred = this.brain.predict(features);
            // Нейронка повторяет ТЕБЯ: берём не абсолютный доворот, а долю
            // ошибки, которую ты сам закрываешь за тик. Доля всегда в [0, 1],
            // поэтому сеть физически не может дёрнуть мимо цели
            // или крутануть камеру не в ту сторону.
            turnYaw = dYaw * brainFraction(pred[0], dYaw) * k;
            turnPitch = dPitch * brainFraction(pred[1], dPitch) * k;
        } else {
            // Без нейронки: классика — доля ошибки за тик.
            float aggression = 0.15f + 0.85f * k;
            turnYaw = dYaw * aggression;
            turnPitch = dPitch * aggression;
        }

        // Плавность: сглаживание итогового доворота (чем выше — тем мягче).
        float alpha = Mth.clamp(2.7f / (float) this.smoothness.value(), 0.05f, 1.0f);
        this.velYaw += (turnYaw - this.velYaw) * alpha;
        this.velPitch += (turnPitch - this.velPitch) * alpha;

        // Скорость: жёсткий лимит в градусах/секунду.
        float maxTurn = (float) (this.speed.value() / 20.0);
        this.velYaw = Mth.clamp(this.velYaw, -maxTurn, maxTurn);
        this.velPitch = Mth.clamp(this.velPitch, -maxTurn, maxTurn);

        // Анти-overshoot: доводка никогда не перелетает через цель,
        // поэтому не может осциллировать и дёргаться вокруг неё.
        if (Math.signum(this.velYaw) == Math.signum(dYaw)
                && Math.abs(this.velYaw) > Math.abs(dYaw)) {
            this.velYaw = dYaw;
        }
        if (Math.signum(this.velPitch) == Math.signum(dPitch)
                && Math.abs(this.velPitch) > Math.abs(dPitch)) {
            this.velPitch = dPitch;
        }

        applyVelocity(mc);
    }

    private void applyVelocity(Minecraft mc) {
        mc.player.setYRot(mc.player.getYRot() + this.velYaw);
        mc.player.setXRot(Mth.clamp(mc.player.getXRot() + this.velPitch, -90.0f, 90.0f));
    }

    /**
     * Доля ошибки, которую игрок закрывает за тик, по предсказанию сети.
     * Всегда в [0, 1]: предсказания «не в ту сторону» дают 0, безумно большие —
     * дают 1 (не больше самой ошибки). Поэтому сеть физически не способна
     * крутить камеру непонятно как — худшее, что она может, это ничего не делать.
     */
    private static float brainFraction(float predicted, float error) {
        float denom = error + (error >= 0.0f ? 0.5f : -0.5f);
        return Mth.clamp(predicted / denom, 0.0f, 1.0f);
    }

    private void decayVelocity() {
        float alpha = Mth.clamp(2.7f / (float) this.smoothness.value(), 0.05f, 1.0f);
        this.velYaw *= 1.0f - alpha;
        this.velPitch *= 1.0f - alpha;
        if (Math.abs(this.velYaw) < 0.001f) {
            this.velYaw = 0.0f;
        }
        if (Math.abs(this.velPitch) < 0.001f) {
            this.velPitch = 0.0f;
        }
    }

    /** Признаки для нейронки: углы до головы/тела, дистанция, скорость цели. */
    private float[] features(LivingEntity target, Vec3 eye) {
        float[] head = rotTo(eye, headPoint(target));
        float[] body = rotTo(eye, bodyPoint(target));
        float dYawH = Mth.wrapDegrees(head[0] - mc().player.getYRot());
        float dPitchH = head[1] - mc().player.getXRot();
        float dYawB = Mth.wrapDegrees(body[0] - mc().player.getYRot());
        float dPitchB = body[1] - mc().player.getXRot();
        float dist = (float) mc().player.distanceTo(target);
        Vec3 pos = target.position();
        float speed = this.lastTargetPos == null ? 0.0f
                : (float) (pos.distanceTo(this.lastTargetPos) * 20.0); // блоков/сек
        this.lastTargetPos = pos;
        return new float[]{dYawH, dPitchH, dYawB, dPitchB, dist, speed};
    }

    private Vec3 choosePoint(LivingEntity target) {
        String mode = this.aimPoint.value();
        if (POINT_HEAD.equals(mode)) {
            return headPoint(target);
        }
        if (POINT_BODY.equals(mode)) {
            return bodyPoint(target);
        }
        Vec3 eye = mc().player.getEyePosition(1.0f);
        Vec3 head = headPoint(target);
        Vec3 body = bodyPoint(target);
        return eye.distanceToSqr(head) <= eye.distanceToSqr(body) ? head : body;
    }

    private LivingEntity findTarget() {
        Minecraft mc = mc();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        double maxDist = this.range.value();
        double maxFov = this.fov.value();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living == mc.player || !living.isAlive()) {
                continue;
            }
            double dist = mc.player.distanceTo(living);
            if (dist > maxDist) {
                continue;
            }
            Vec3 eye = mc.player.getEyePosition(1.0f);
            float[] rot = rotTo(eye, bodyPoint(living));
            double fovDist = Math.hypot(
                    Mth.wrapDegrees(rot[0] - mc.player.getYRot()),
                    rot[1] - mc.player.getXRot());
            if (fovDist > maxFov) {
                continue;
            }
            double score = fovDist + dist * 2.0;
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }

    // ---------------- профиль ----------------

    private void loadActiveProfile() {
        try {
            var file = AimDataset.dir().resolve("active_profile.txt");
            if (Files.isRegularFile(file)) {
                String name = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (!name.isEmpty() && Files.isRegularFile(AimBrain.weightsFileOf(name))) {
                    this.brain = AimBrain.load(AimBrain.weightsFileOf(name));
                    this.activeProfile = name;
                }
            }
        } catch (Exception ignored) {
        }
        refreshCaptions();
    }

    private void saveActiveProfile() {
        try {
            var dir = AimDataset.dir();
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("active_profile.txt"),
                    this.activeProfile, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    @Override
    protected void onDisable() {
        if (training) {
            stopTraining();
        }
        this.velYaw = 0.0f;
        this.velPitch = 0.0f;
    }
}

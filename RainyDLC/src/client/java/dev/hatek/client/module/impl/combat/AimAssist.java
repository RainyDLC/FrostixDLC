package dev.hatek.client.module.impl.combat;

import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.impl.combat.aimassist.AimDataset;
import dev.hatek.client.module.impl.combat.aimassist.AimHud;
import dev.hatek.client.module.impl.combat.aimassist.AimNet;
import dev.hatek.client.module.impl.combat.aimassist.AimSample;
import dev.hatek.client.module.impl.combat.aimassist.AimStore;
import dev.hatek.client.module.impl.combat.aimassist.AimTrainScreen;
import dev.hatek.client.module.impl.combat.aimassist.DynamicButtonSetting;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.List;

/**
 * AimAssist — мягкая доводка прицела с раздельными хитбоксами
 * головы и тела и по-настоящему обучаемой нейронкой.
 *
 * <p>Как обучить:
 * <ol>
 *   <li>Нажми «Обучить наводку» — откроется меню, введи название и нажми «Обучить».</li>
 *   <li>Меню само закроется, сверху появится прогресс. Играй как обычно:
 *       наводись и бей — записывается ТОЛЬКО наводка, не удары.</li>
 *   <li>Открой меню снова и нажми «Стоп».</li>
 *   <li>Нажми «Обучить нейронку» — MLP (Adam) научится один в один
 *       повторять твою наводку: скорость, плавность и манеру.</li>
 * </ol>
 */
public final class AimAssist extends Module {
    private static AimAssist instance;

    public static final String POINT_HEAD = "Голова";
    public static final String POINT_BODY = "Тело";
    public static final String POINT_NEAREST = "Ближайшая";

    /** Минимум сэмплов для осмысленного обучения. */
    public static final int MIN_SAMPLES = 120;
    private static final int MAX_SAMPLES = 20000;
    private static final int EPOCHS = 250;
    private static final float FALLBACK_MAX_TURN = 8.0f;

    // ---- состояние для HUD и менюшки ----
    public static volatile boolean training;
    public static volatile String trainingName = "";
    public static volatile int trainingSamples;
    public static volatile long trainingStartMs;
    public static volatile boolean learning;
    public static volatile double learnProgress;
    public static volatile String learnStatus = "";

    private final ModeSetting aimPoint = new ModeSetting("Прицел", 0,
            POINT_HEAD, POINT_BODY, POINT_NEAREST);
    private final SliderSetting strength = new SliderSetting("Сила доводки, %", 40.0, 0.0, 100.0, 1.0);
    private final SliderSetting range = new SliderSetting("Дистанция", 4.5, 1.0, 8.0, 0.1);
    private final SliderSetting fov = new SliderSetting("FOV захвата", 60.0, 10.0, 180.0, 1.0);
    private final BoolSetting onlyWhileAttacking = new BoolSetting("Только при атаке", true);
    private final BoolSetting useBrain = new BoolSetting("Умная доводка (нейронка)", true);
    private final SliderSetting smoothness = new SliderSetting("Плавность", 45.0, 0.0, 90.0, 1.0);

    private final DynamicButtonSetting trainButton =
            new DynamicButtonSetting("Обучение", "Обучить наводку", this::openTrainScreen);
    private final DynamicButtonSetting learnButton =
            new DynamicButtonSetting("Нейронка", "Обучить нейронку", this::onLearnButton);

    private AimDataset dataset;
    private volatile AimNet net = new AimNet();
    private String activeProfile = "";

    // ---- трекинг между тиками ----
    private float lastViewYaw;
    private float lastViewPitch;
    private boolean hasLastView;
    private float[] prevFeat;
    private int prevTargetId = -1;
    private float lastFeatYawErr;
    private float lastFeatPitchErr;
    private int ticksSinceAttack = 999;
    private float smoothYaw;
    private float smoothPitch;

    /** Точка прицеливания: позиция + углы до неё + это голова или тело. */
    private record AimPoint(Vec3 pos, float yaw, float pitch, boolean head) {
    }

    public AimAssist() {
        super("AimAssist", "Доводит прицел (голова/тело), учится твоей наводке",
                Category.COMBAT);
        instance = this;
        with(this.aimPoint, this.strength, this.range, this.fov,
                this.onlyWhileAttacking, this.useBrain, this.smoothness,
                this.trainButton, this.learnButton);
        this.learnButton.enabled(false);
        AimHud.init();
        loadActiveProfile();
        refreshCaptions();
    }

    public static AimAssist instance() {
        return instance;
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

    private AimPoint pointOf(LivingEntity target, Vec3 pos, boolean head, Vec3 eye) {
        float[] rot = rotTo(eye, pos);
        return new AimPoint(pos, rot[0], rot[1], head);
    }

    /** Точка прицеливания по настройке режима. */
    private AimPoint chooseAimPoint(LivingEntity target, Vec3 eye,
                                    float viewYaw, float viewPitch) {
        String mode = this.aimPoint.value();
        if (POINT_HEAD.equals(mode)) {
            return pointOf(target, headPoint(target), true, eye);
        }
        if (POINT_BODY.equals(mode)) {
            return pointOf(target, bodyPoint(target), false, eye);
        }
        return nearestPoint(target, eye, viewYaw, viewPitch);
    }

    /** Та из двух точек, к которой ближе текущий прицел игрока. */
    private AimPoint nearestPoint(LivingEntity target, Vec3 eye, float viewYaw, float viewPitch) {
        Vec3 head = headPoint(target);
        Vec3 body = bodyPoint(target);
        float[] rh = rotTo(eye, head);
        float[] rb = rotTo(eye, body);
        double ah = Math.hypot(Mth.wrapDegrees(rh[0] - viewYaw), rh[1] - viewPitch);
        double ab = Math.hypot(Mth.wrapDegrees(rb[0] - viewYaw), rb[1] - viewPitch);
        return ah <= ab ? new AimPoint(head, rh[0], rh[1], true)
                : new AimPoint(body, rb[0], rb[1], false);
    }

    // ---------------- обучение: кнопки и меню ----------------

    private void openTrainScreen() {
        Minecraft.getInstance().gui.setScreen(new AimTrainScreen(this));
    }

    private void onLearnButton() {
        startLearning();
    }

    /** Вызывается из {@link AimTrainScreen} после ввода названия. */
    public void startTraining(String name) {
        this.dataset = new AimDataset(name);
        trainingName = name;
        trainingSamples = 0;
        trainingStartMs = System.currentTimeMillis();
        training = true;
        this.hasLastView = false;
        this.prevFeat = null;
        this.prevTargetId = -1;
        this.ticksSinceAttack = 999;
        this.smoothYaw = 0.0f;
        this.smoothPitch = 0.0f;
        refreshCaptions();
    }

    /** Остановить запись. Датасет сохраняется, появляется кнопка обучения. */
    public void stopTraining() {
        if (!training) {
            return;
        }
        training = false;
        if (this.dataset != null) {
            try {
                AimStore.saveDataset(this.dataset);
            } catch (IOException e) {
                learnStatus = "Ошибка сохранения: " + e.getMessage();
            }
        }
        refreshCaptions();
    }

    public boolean isTraining() {
        return training;
    }

    public int datasetSize() {
        return this.dataset == null ? 0 : this.dataset.size();
    }

    public String datasetName() {
        return this.dataset == null ? "" : this.dataset.name();
    }

    public String learnStatus() {
        return learnStatus;
    }

    public String activeProfile() {
        return this.activeProfile;
    }

    public boolean brainTrained() {
        return this.net.isTrained();
    }

    private void refreshCaptions() {
        this.trainButton.caption(training ? "Стоп" : "Обучить наводку");
        boolean canLearn = !training && !learning
                && this.dataset != null && this.dataset.size() >= MIN_SAMPLES;
        this.learnButton.enabled(canLearn);
        this.learnButton.caption(learning ? "Обучение…"
                : this.net.isTrained() ? "Переобучить нейронку" : "Обучить нейронку");
    }

    /** Обучить нейронку на записанном датасете — в фоновом потоке. */
    public void startLearning() {
        if (learning || training) {
            return;
        }
        AimDataset ds = this.dataset;
        if (ds == null || ds.size() < MIN_SAMPLES) {
            learnStatus = "Сначала запиши обучение (кнопка «Обучить наводку»)";
            return;
        }
        learning = true;
        learnProgress = 0.0;
        learnStatus = "Подготовка…";
        refreshCaptions();
        List<AimSample> samples = ds.copy();
        String profile = ds.name();
        new Thread(() -> {
            try {
                AimNet next = new AimNet();
                boolean ok = next.train(samples, EPOCHS, (ep, total, trainLoss, valLoss) -> {
                    learnProgress = (double) ep / total;
                    learnStatus = "Эпоха " + ep + "/" + total
                            + " · train " + fmt(trainLoss)
                            + " · val " + (Double.isNaN(valLoss) ? "—" : fmt(valLoss));
                });
                if (!ok) {
                    learnStatus = "Мало данных для обучения";
                } else {
                    AimStore.saveWeights(profile, next);
                    this.net = next;
                    this.activeProfile = profile;
                    AimStore.saveActiveProfile(profile);
                    learnStatus = "Готово ✓ «" + profile + "» · val loss "
                            + fmt(next.finalValLoss());
                }
            } catch (Exception e) {
                learnStatus = "Ошибка: " + e.getMessage();
            } finally {
                learning = false;
                learnProgress = 1.0;
                Minecraft.getInstance().execute(this::refreshCaptions);
            }
        }, "aimassist-learn").start();
    }

    private static String fmt(double v) {
        return Double.isNaN(v) ? "—" : String.format("%.4f", v);
    }

    /** Сделать профиль активным (загрузить его веса). */
    public void activateProfile(String name) {
        try {
            AimNet loaded = AimStore.loadWeights(name);
            this.net = loaded;
            this.activeProfile = name;
            AimStore.saveActiveProfile(name);
            learnStatus = "Профиль «" + name + "» активен";
        } catch (Exception e) {
            learnStatus = "Нет обученной нейронки для «" + name + "»";
        }
        refreshCaptions();
    }

    /** Удалить профиль (датасет + веса). */
    public void deleteProfile(String name) {
        AimStore.deleteProfile(name);
        if (name.equals(this.activeProfile)) {
            this.net = new AimNet();
            this.activeProfile = "";
        }
        refreshCaptions();
    }

    // ---------------- тик ----------------

    @Override
    public void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mc.screen != null) {
            resetFrame();
            return;
        }

        boolean attackDown = mc.options.keyAttack.isDown();
        if (attackDown) {
            this.ticksSinceAttack = 0;
        } else if (this.ticksSinceAttack < 1000) {
            this.ticksSinceAttack++;
        }

        float viewYaw = mc.player.getYRot();
        float viewPitch = mc.player.getXRot();
        LivingEntity target = findTarget(mc);

        if (training) {
            recordTick(mc, target, viewYaw, viewPitch, attackDown);
        } else {
            assistTick(mc, target, viewYaw, viewPitch, attackDown);
        }

        this.lastViewYaw = viewYaw;
        this.lastViewPitch = viewPitch;
        this.hasLastView = true;
    }

    private void resetFrame() {
        this.hasLastView = false;
        this.prevFeat = null;
        this.prevTargetId = -1;
        this.smoothYaw *= 0.6f;
        this.smoothPitch *= 0.6f;
    }

    /**
     * Запись: признаки цели прошлого тика + реальный доворот игрока за тик.
     * Во время записи асист выключен — пишется чистая наводка игрока.
     */
    private void recordTick(Minecraft mc, LivingEntity target,
                            float viewYaw, float viewPitch, boolean attackDown) {
        float dYaw = this.hasLastView ? Mth.wrapDegrees(viewYaw - this.lastViewYaw) : 0.0f;
        float dPitch = this.hasLastView ? viewPitch - this.lastViewPitch : 0.0f;

        // Пишем только в бою: игрок атакует или только что атаковал.
        boolean engaged = attackDown || this.ticksSinceAttack < 8;
        if (target != null && this.hasLastView && this.prevFeat != null && engaged
                && this.dataset != null && this.dataset.size() < MAX_SAMPLES
                && Math.abs(dYaw) + Math.abs(dPitch) > 0.004f
                && Math.abs(dYaw) < 30.0f && Math.abs(dPitch) < 30.0f) {
            this.dataset.add(new AimSample(this.prevFeat, new float[]{dYaw, dPitch}));
            trainingSamples = this.dataset.size();
        }

        if (target != null) {
            Vec3 eye = mc.player.getEyePosition(1.0f);
            AimPoint ap = nearestPoint(target, eye, viewYaw, viewPitch);
            this.prevFeat = buildFeatures(target, eye, viewYaw, viewPitch, ap,
                    Mth.wrapDegrees(ap.yaw() - viewYaw), ap.pitch() - viewPitch);
        } else {
            this.prevFeat = null;
            this.prevTargetId = -1;
        }
    }

    /** Доводка прицела: нейронка предсказывает доворот игрока. */
    private void assistTick(Minecraft mc, LivingEntity target,
                            float viewYaw, float viewPitch, boolean attackDown) {
        if (target == null) {
            decaySmooth();
            this.prevTargetId = -1;
            return;
        }
        if (this.onlyWhileAttacking.value() && !attackDown && this.ticksSinceAttack > 8) {
            decaySmooth();
            return;
        }

        Vec3 eye = mc.player.getEyePosition(1.0f);
        AimPoint ap = chooseAimPoint(target, eye, viewYaw, viewPitch);
        float yawErr = Mth.wrapDegrees(ap.yaw() - viewYaw);
        float pitchErr = ap.pitch() - viewPitch;
        if (Math.hypot(yawErr, pitchErr) < 0.25) {
            decaySmooth();
            return;
        }

        float[] feat = buildFeatures(target, eye, viewYaw, viewPitch, ap, yawErr, pitchErr);
        float turnYaw;
        float turnPitch;
        AimNet brain = this.net;
        if (this.useBrain.value() && brain.isTrained()) {
            float[] pred = brain.predict(feat);
            float k = (float) (this.strength.value() / 100.0);
            turnYaw = pred[0] * k;
            turnPitch = pred[1] * k;
            // Не крутимся дальше цели — убирает дрожание и осцилляции.
            turnYaw = clampToErr(turnYaw, yawErr);
            turnPitch = clampToErr(turnPitch, pitchErr);
            // Кап скорости — p95 скорости самого игрока из обучения.
            float cap = (float) (brain.autoMaxDegPerSec() / 20.0);
            turnYaw = Mth.clamp(turnYaw, -cap, cap);
            turnPitch = Mth.clamp(turnPitch, -cap, cap);
        } else {
            float k = (float) (this.strength.value() / 100.0) * 0.35f;
            turnYaw = Mth.clamp(yawErr * k, -FALLBACK_MAX_TURN, FALLBACK_MAX_TURN);
            turnPitch = Mth.clamp(pitchErr * k, -FALLBACK_MAX_TURN, FALLBACK_MAX_TURN);
        }

        // Плавность: экспоненциальное сглаживание доворота.
        float a = 1.0f - (float) (this.smoothness.value() / 100.0);
        this.smoothYaw += (turnYaw - this.smoothYaw) * a;
        this.smoothPitch += (turnPitch - this.smoothPitch) * a;

        mc.player.setYRot(viewYaw + this.smoothYaw);
        mc.player.setXRot(Mth.clamp(viewPitch + this.smoothPitch, -90.0f, 90.0f));
    }

    private void decaySmooth() {
        this.smoothYaw *= 0.6f;
        this.smoothPitch *= 0.6f;
    }

    private static float clampToErr(float turn, float err) {
        float a = Math.abs(err);
        return Mth.clamp(turn, -a, a);
    }

    /**
     * Признаки для нейронки: угловая ошибка до точки, дистанция,
     * угловая скорость цели и флаг «голова/тело».
     */
    private float[] buildFeatures(LivingEntity target, Vec3 eye,
                                  float viewYaw, float viewPitch, AimPoint ap,
                                  float yawErr, float pitchErr) {
        float tgtYawVel;
        float tgtPitchVel;
        int id = target.getId();
        if (this.hasLastView && id == this.prevTargetId) {
            tgtYawVel = Mth.wrapDegrees((yawErr - this.lastFeatYawErr)
                    + (viewYaw - this.lastViewYaw));
            tgtPitchVel = (pitchErr - this.lastFeatPitchErr)
                    + (viewPitch - this.lastViewPitch);
        } else {
            tgtYawVel = 0.0f;
            tgtPitchVel = 0.0f;
        }
        this.lastFeatYawErr = yawErr;
        this.lastFeatPitchErr = pitchErr;
        this.prevTargetId = id;

        float dist = (float) eye.distanceTo(ap.pos());
        return new float[]{yawErr, pitchErr, dist, tgtYawVel, tgtPitchVel,
                ap.head() ? 1.0f : 0.0f};
    }

    private LivingEntity findTarget(Minecraft mc) {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        double maxDist = this.range.value();
        double maxFov = this.fov.value();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living == mc.player || !living.isAlive() || entity instanceof ArmorStand) {
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
            String name = AimStore.loadActiveProfile();
            if (!name.isEmpty() && AimStore.hasWeights(name)) {
                this.net = AimStore.loadWeights(name);
                this.activeProfile = name;
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDisable() {
        if (training) {
            stopTraining();
        }
    }
}

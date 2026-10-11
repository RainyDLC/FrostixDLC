package dev.hatek.client.module.impl.combat.aura.neuro;

import dev.hatek.client.module.impl.combat.AttackAura;
import dev.hatek.client.module.impl.combat.aimassist.AimNet;
import dev.hatek.client.module.impl.combat.aimassist.AimSample;
import dev.hatek.client.module.impl.combat.aura.rotation.Rotation;
import dev.hatek.client.module.impl.combat.aura.rotation.RotationProcess;
import dev.hatek.client.module.impl.combat.aura.rotation.ServerView;
import dev.hatek.client.module.impl.combat.aura.util.EngageDelay;
import dev.hatek.client.module.impl.combat.aura.util.GCDUtil;
import dev.hatek.client.module.impl.combat.aura.util.RayTraceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Нейро-ротации AttackAura: боевой модуль ауры, где скорость и углы
 * формируются нейросетью с человекоподобными кривыми ускорения, GCD-квантованием
 * и автоматической подстройкой скорости под кулдаун удара и криты.
 */
public final class NeuroRotation {
    /** Минимум сэмплов для осмысленного обучения. */
    public static final int MIN_SAMPLES = 80;
    private static final int MAX_SAMPLES = 20000;

    // ---- состояние для панели ----
    public static volatile boolean training;
    public static volatile String trainingName = "";
    public static volatile int trainingSamples;
    public static volatile long trainingStartMs;
    public static volatile boolean learning;
    public static volatile double learnProgress;
    public static volatile String learnStatus = "";
    public static volatile String learnLog = "";

    private static volatile AimNet net = new AimNet();
    private static volatile String activeProfile = "";

    // ---- запись датасета ----
    private static NeuroDataset dataset;
    private static float lastViewYaw;
    private static float lastViewPitch;
    private static boolean hasLastView;
    private static float[] prevFeat;
    private static int prevTargetId = -1;
    private static float lastFeatYawErr;
    private static float lastFeatPitchErr;
    private static int ticksSinceAttack = 999;
    private static int stabilizationSamples;

    // ---- инференс ----
    private static LivingEntity target;
    private static float wantVelYaw;
    private static float wantVelPitch;
    private static float curVelYaw;
    private static float curVelPitch;
    private static float gcdAccumYaw;
    private static float gcdAccumPitch;
    private static int infPrevTargetId = -1;
    private static float infLastYawErr;
    private static float infLastPitchErr;
    private static boolean aimed;
    private static long lastFrameNanos;

    // ---- анти-детект: блуждание точки прицела ----
    private static Vec3 neuroWander = Vec3.ZERO;
    private static Vec3 neuroWanderVel = Vec3.ZERO;
    private static float neuroJitterYaw;
    private static float neuroJitterPitch;

    // Калиброванный анти-детект (статистики из AimNet.Calib, собранные из руки игрока)
    private static int freezeUntilTick = Integer.MIN_VALUE;
    private static int lastFreezeRollTick = Integer.MIN_VALUE;
    private static float residYaw;
    private static float residPitch;
    private static int residHoldUntilTick = Integer.MIN_VALUE;

    private NeuroRotation() {
    }

    /** Загрузить активный профиль при старте (с дефолтными весами при отсутствии). */
    public static void init() {
        try {
            NeuroStore.ensureDefaultWeights();
            String name = NeuroStore.loadActiveProfile();
            if (!name.isEmpty() && NeuroStore.hasWeights(name)) {
                net = NeuroStore.loadWeights(name);
                activeProfile = name;
            } else if (NeuroStore.hasWeights("default")) {
                net = NeuroStore.loadWeights("default");
                activeProfile = "default";
            } else if (NeuroStore.hasWeights("qwer")) {
                net = NeuroStore.loadWeights("qwer");
                activeProfile = "qwer";
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean hasBrain() {
        return net != null && net.isTrained();
    }

    public static boolean brainTrained() {
        return hasBrain();
    }

    public static String activeProfile() {
        return activeProfile;
    }

    public static boolean isTraining() {
        return training;
    }

    public static int datasetSize() {
        return dataset == null ? 0 : dataset.size();
    }

    public static String datasetName() {
        return dataset == null ? "" : dataset.name();
    }

    // ---------------- обучение: запись ----------------

    /** Начать запись. Ауру включает вызывающий код. */
    public static void startTraining(String name) {
        dataset = new NeuroDataset(name);
        trainingName = name;
        trainingSamples = 0;
        trainingStartMs = System.currentTimeMillis();
        training = true;
        hasLastView = false;
        prevFeat = null;
        prevTargetId = -1;
        ticksSinceAttack = 999;
        stabilizationSamples = 0;
        learnStatus = "";
        learnLog = "";
        resetInference();
        // Полностью глушим авто-ротации и отдаём управление руками игроку:
        dev.hatek.client.module.impl.combat.aura.rotation.SpaceTime.forget();
        RotationProcess.halt();
        dev.hatek.client.module.impl.combat.aura.rotation.FreeLookUtil.active = false;
        dev.hatek.client.module.impl.combat.aura.util.MoveUtil.restoreMovementKeys();
        dev.hatek.client.module.impl.combat.aura.util.MoveUtil.restoreSprintKey();
        // AimAssist тоже глушим — иначе он будет «притягивать» прицел и испортит запись.
        try {
            dev.hatek.client.module.impl.combat.AimAssist aim =
                    dev.hatek.client.module.impl.combat.AimAssist.instance();
            if (aim != null && aim.isEnabled()) {
                aim.setEnabled(false);
                tell("§e⚠ §fAimAssist отключён на время записи (портил бы датасет).");
            }
        } catch (Exception ignored) {
        }
        tell("§d● §fЗапись нейро-ротации «" + name + "»: целься и бей сам, как обычно.");
        tell("§7Аура НЕ вмешивается: целься мышкой во врага/жителя для записи датасета.");
    }

    /** Остановить запись, сохранить датасет. */
    public static void stopTraining() {
        if (!training) {
            return;
        }
        training = false;
        if (trainingSamples < MIN_SAMPLES) {
            // Мало сэмплов — запись отбрасываем, чтобы не плодить мусорные профили.
            dataset = null;
            trainingSamples = 0;
            learnStatus = "";
            tell("§c■ §fЗапись отброшена: мало сэмплов (нужно минимум " + MIN_SAMPLES
                    + "). Начни заново.");
            return;
        }
        if (dataset != null) {
            try {
                NeuroStore.saveDataset(dataset);
            } catch (IOException e) {
                learnStatus = "Ошибка сохранения: " + e.getMessage();
            }
        }
        tell("§a■ §fЗапись остановлена: " + trainingSamples
                + " сэмплов. Нажми «Обучить (быстро, Java)» в меню.");
    }

    /** Вызывается каждый клиентский тик из AttackAura. */
    public static void onClientTick() {
        if (!training) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mc.gui.screen() != null) {
            hasLastView = false;
            prevFeat = null;
            prevTargetId = -1;
            return;
        }

        boolean attackDown = mc.options.keyAttack.isDown();
        if (attackDown) {
            ticksSinceAttack = 0;
        } else if (ticksSinceAttack < 1000) {
            ticksSinceAttack++;
        }

        float viewYaw = mc.player.getYRot();
        float viewPitch = mc.player.getXRot();
        LivingEntity aimTarget = findAimTarget(mc, viewYaw, viewPitch);

        recordTick(mc, aimTarget, viewYaw, viewPitch, attackDown);

        lastViewYaw = viewYaw;
        lastViewPitch = viewPitch;
        hasLastView = true;
    }

    /**
     * Цель для записи: любое живое существо, на которое смотрит игрок.
     * Стойки для брони, жители и мобы на спавне — тоже подходят,
     * поэтому обучаться можно прямо на спавне без боя.
     */
    private static LivingEntity findAimTarget(Minecraft mc, float viewYaw, float viewPitch) {
        LivingEntity best = null;
        double bestAng = 45.0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living == mc.player || living instanceof LocalPlayer) {
                continue;
            }
            if (!living.isAlive() || living.isRemoved()) {
                continue;
            }
            if (mc.player.distanceTo(living) > 12.0) {
                continue;
            }
            Vec3 eye = mc.player.getEyePosition(1.0f);
            float[] rot = rotTo(eye, living.getBoundingBox().getCenter());
            double ang = Math.hypot(Mth.wrapDegrees(rot[0] - viewYaw), rot[1] - viewPitch);
            if (ang < bestAng) {
                bestAng = ang;
                best = living;
            }
        }
        return best;
    }

    /**
     * Запись датасета: только реальная моторика игрока.
     * Фильтрует смену целей, увороты и аномалии телепортации.
     */
    private static void recordTick(Minecraft mc, LivingEntity aimTarget,
                                   float viewYaw, float viewPitch, boolean attackDown) {
        float dYaw = hasLastView ? Mth.wrapDegrees(viewYaw - lastViewYaw) : 0.0f;
        float dPitch = hasLastView ? viewPitch - lastViewPitch : 0.0f;

        boolean engaged = attackDown || ticksSinceAttack < 20
                || (prevFeat != null && Math.hypot(dYaw, dPitch) > 0.04f && Math.hypot(prevFeat[0], prevFeat[1]) < 35.0f);
        int targetId = aimTarget != null ? aimTarget.getId() : -1;

        if (aimTarget != null && hasLastView && prevFeat != null && engaged
                && prevTargetId == targetId
                && dataset != null && dataset.size() < MAX_SAMPLES) {

            float yawErr = prevFeat[0];
            float pitchErr = prevFeat[1];
            float absErr = (float) Math.hypot(yawErr, pitchErr);
            float absTurn = (float) Math.hypot(dYaw, dPitch);

            if (Math.abs(dYaw) < 45.0f && Math.abs(dPitch) < 45.0f) {
                boolean valid = false;

                if (absErr < 2.0f) {
                    // Стабилизация на цели: учим останавливаться, а не дёргаться
                    if (stabilizationSamples < dataset.size() * 0.25 + 10) {
                        valid = true;
                        stabilizationSamples++;
                    }
                } else {
                    boolean yawMatches = Math.abs(yawErr) < 2.5f
                            || Math.signum(dYaw) == Math.signum(yawErr);
                    boolean pitchMatches = Math.abs(pitchErr) < 2.0f
                            || Math.signum(dPitch) == Math.signum(pitchErr);
                    if ((yawMatches || Math.abs(dYaw) < 0.5f)
                            && (pitchMatches || Math.abs(dPitch) < 0.5f)
                            && absTurn > 0.02f) {
                        valid = true;
                    }
                }

                if (valid) {
                    dataset.add(new NeuroSample(prevFeat, new float[]{dYaw, dPitch}));
                    trainingSamples = dataset.size();
                }
            }
        }

        if (aimTarget != null) {
            Vec3 eye = mc.player.getEyePosition(1.0f);
            float[] rot = rotTo(eye, aimTarget.getBoundingBox().getCenter());
            float curYawErr = Mth.wrapDegrees(rot[0] - viewYaw);
            float curPitchErr = rot[1] - viewPitch;
            prevFeat = buildRecFeatures(aimTarget, eye, viewYaw, viewPitch,
                    curYawErr, curPitchErr);
            prevTargetId = targetId;
        } else {
            prevFeat = null;
            prevTargetId = -1;
        }
    }

    // ---------------- обучение ----------------

    /** Обучить нейронку прямо в Java (Adam, 250 эпох) — быстро и без зависимостей. */
    public static void startJavaTraining() {
        if (learning || training) {
            return;
        }
        NeuroDataset ds = dataset;
        if (ds == null || ds.size() < MIN_SAMPLES) {
            learnStatus = "Сначала запиши обучение (нужно минимум " + MIN_SAMPLES + " сэмплов)";
            return;
        }
        try {
            NeuroStore.saveDataset(ds);
        } catch (IOException e) {
            learnStatus = "Ошибка сохранения: " + e.getMessage();
            return;
        }
        learning = true;
        learnProgress = 0.0;
        learnStatus = "Обучение в Java (Adam)…";
        learnLog = "";
        String profile = ds.name();
        List<NeuroSample> raw = ds.copy();
        List<AimSample> aimSamples = new ArrayList<>(raw.size());
        for (NeuroSample s : raw) {
            aimSamples.add(new AimSample(s.input, s.output));
        }

        new Thread(() -> {
            try {
                AimNet next = new AimNet();
                boolean ok = next.train(aimSamples, 250, (ep, total, trainLoss, valLoss) -> {
                    learnProgress = (double) ep / total;
                    learnStatus = "Эпоха " + ep + "/" + total
                            + " · train " + fmt(trainLoss)
                            + " · val " + (Double.isNaN(valLoss) ? "—" : fmt(valLoss));
                });
                if (!ok) {
                    learnStatus = "Мало данных для обучения";
                } else {
                    NeuroStore.saveWeights(profile, next);
                    net = next;
                    activeProfile = profile;
                    NeuroStore.saveActiveProfile(profile);
                    learnStatus = "Готово ✓ «" + profile + "» · val loss " + fmt(next.finalValLoss())
                            + " · макс. скорость " + (int) next.autoMaxDegPerSec() + "°/с";
                    tell("§a✔ §fНейро-ротация «" + profile + "» обучена и активна.");
                }
            } catch (Exception e) {
                learnStatus = "Ошибка: " + e.getMessage();
            } finally {
                learning = false;
                learnProgress = 1.0;
            }
        }, "neuro-learn-java").start();
    }

    // ---------------- обучение: Python ----------------

    /** Обучить нейронку через локальный Python — в фоновом потоке. */
    public static void startPythonTraining() {
        if (learning || training) {
            return;
        }
        NeuroDataset ds = dataset;
        if (ds == null || ds.size() < MIN_SAMPLES) {
            learnStatus = "Сначала запиши обучение (нужно минимум " + MIN_SAMPLES + " сэмплов)";
            return;
        }
        try {
            NeuroStore.saveDataset(ds);
        } catch (IOException e) {
            learnStatus = "Ошибка сохранения датасета: " + e.getMessage();
            return;
        }
        learning = true;
        learnProgress = 0.0;
        learnStatus = "Запуск Python…";
        learnLog = "";
        String profile = ds.name();
        NeuroTrainer.start(profile, new NeuroTrainer.Listener() {
            @Override
            public void onProgress(double progress, String status) {
                learnProgress = progress;
                learnStatus = status;
            }

            @Override
            public void onLog(String line) {
                if (line != null && line.length() < 200) {
                    learnLog = line;
                }
            }

            @Override
            public void onDone(boolean ok, String message) {
                learning = false;
                learnProgress = 1.0;
                if (ok) {
                    try {
                        net = NeuroStore.loadWeights(profile);
                        activeProfile = profile;
                        NeuroStore.saveActiveProfile(profile);
                        learnStatus = message + " · val loss " + fmt(net.finalValLoss())
                                + " · макс. скорость " + (int) net.autoMaxDegPerSec() + "°/с";
                        tell("§a✔ §fНейро-ротация «" + profile + "» обучена и активна.");
                    } catch (Exception e) {
                        learnStatus = "Веса не загрузились: " + e.getMessage();
                    }
                } else {
                    learnStatus = message;
                }
            }
        });
    }

    private static String fmt(double v) {
        return Double.isNaN(v) ? "—" : String.format("%.4f", v);
    }

    /** Сделать профиль активным (загрузить его веса). */
    public static void activateProfile(String name) {
        try {
            net = NeuroStore.loadWeights(name);
            activeProfile = name;
            NeuroStore.saveActiveProfile(name);
            learnStatus = "Профиль «" + name + "» активен";
        } catch (Exception e) {
            learnStatus = "Нет обученной нейронки для «" + name + "»";
        }
    }

    /** Удалить профиль (датасет + веса). */
    public static void deleteProfile(String name) {
        NeuroStore.deleteProfile(name);
        if (name.equals(activeProfile)) {
            net = new AimNet();
            activeProfile = "";
        }
    }

    // ---------------- инференс ----------------

    /**
     * Вызывается каждый тик из AttackAura, когда выбран режим «Нейро».
     */
    public static void setTarget(LivingEntity t) {
        target = t;
        if (t == null) {
            wantVelYaw = 0.0f;
            wantVelPitch = 0.0f;
            aimed = false;
            neuroWander = Vec3.ZERO;
            neuroWanderVel = Vec3.ZERO;
            freezeUntilTick = Integer.MIN_VALUE;
            residHoldUntilTick = Integer.MIN_VALUE;
        } else if (t.getId() != infPrevTargetId) {
            // Новая цель — сбрасываем блуждание, чтобы не тянуть старое смещение.
            neuroWander = Vec3.ZERO;
            neuroWanderVel = Vec3.ZERO;
            freezeUntilTick = Integer.MIN_VALUE;
            residHoldUntilTick = Integer.MIN_VALUE;
        }
    }

    /**
     * Вызывается каждый клиентский тик перед попыткой удара в AttackAura.
     * Синхронизирует кадровую ротацию прямо перед проверкой shouldAttack.
     */
    public static void onTickRotation(LivingEntity t) {
        if (t == null) {
            return;
        }
        target = t;
        onFrame();
    }

    /**
     * Покадровое применение: вызывается из ComponentManager.render3D().
     * Ротации передаются в RotationProcess: это активирует FreeLook/сайлент ротации,
     * не дёргает экран игрока, квантует углы по GCD мыши и ускоряет наводку так,
     * чтобы она никогда не длилась дольше кулдауна удара (с учётом критов).
     */
    public static void onFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        LivingEntity t = target;
        if (t == null || !t.isAlive() || t.isRemoved() || mc.gui.screen() != null) {
            decayVelocity();
            if (RotationProcess.isRotating()) {
                RotationProcess.requestReset();
            }
            aimed = false;
            return;
        }

        // Человеческая задержка реакции на новую цель (анти-детект).
        // aimed пересчитываем честно: ротация ещё не начиналась, stale true
        // пропускал бы удары без луча в цель.
        if (EngageDelay.hold(t)) {
            decayVelocity();
            Vec3 eye0 = mc.player.getEyePosition(1.0f);
            aimed = RayTraceUtil.rayTraceEntity(eye0, mc.player.getYRot(), mc.player.getXRot(),
                    AttackAura.attackRange() + 0.5, t, 0.12);
            return;
        }

        long now = System.nanoTime();
        float dt = (lastFrameNanos == 0L) ? 0.016f : (float) ((now - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = now;
        dt = Mth.clamp(dt, 0.001f, 0.05f);

        Vec3 eye = mc.player.getEyePosition(1.0f);
        boolean inCritPhase = !mc.player.onGround()
                && !mc.player.isFallFlying()
                && !mc.player.getAbilities().flying
                && mc.player.getDeltaMovement().y < -0.01;
        float attackScale = mc.player.getAttackStrengthScale(0.5f);
        boolean readyToHit = attackScale >= 0.88f || inCritPhase
                || (dev.hatek.client.module.impl.combat.aura.util.AttackPlanner.hasPlan() && attackScale >= 0.75f);

        var leadBox = dev.hatek.client.module.impl.combat.aura.rotation.SpaceTime.leadedBox(t);
        Vec3 aimPoint = wanderAimPoint(leadBox, readyToHit);
        float[] targetRot = rotTo(eye, aimPoint);

        // Калиброванная неточность руки: целимся не в ноль ошибки, а в остаточную
        // ошибку из распределения самого игрока (error-quantile targeting).
        // В момент удара не применяем — луч должен гарантированно попадать.
        if (!readyToHit) {
            updateResidual();
            targetRot[0] = Mth.wrapDegrees(targetRot[0] + residYaw);
            targetRot[1] = Mth.clamp(targetRot[1] + residPitch, -90.0f, 90.0f);
        }

        // Гарантируем, что луч попадает в хитбокс цели (реальный или упреждённый).
        if (!RayTraceUtil.rayTraceEntity(eye, targetRot[0], targetRot[1], AttackAura.attackRange() + 0.5, t, 0.12)) {
            targetRot = rotTo(eye, t.getBoundingBox().getCenter());
            neuroWander = neuroWander.scale(0.5);
        }

        float curYaw = mc.player.getYRot();
        float curPitch = mc.player.getXRot();
        float yawErr = Mth.wrapDegrees(targetRot[0] - curYaw);
        float pitchErr = targetRot[1] - curPitch;
        float totErr = (float) Math.hypot(yawErr, pitchErr);

        // Микро-пауза из моторики игрока: в слежении рука иногда замирает.
        // Вероятность и длительность — из его калибровки, не из головы.
        if (checkFreeze(totErr, readyToHit)) {
            decayVelocity();
            gcdAccumYaw = 0.0f;
            gcdAccumPitch = 0.0f;
            aimed = RayTraceUtil.rayTraceEntity(eye, curYaw, curPitch,
                    AttackAura.attackRange() + 0.5, t, 0.12);
            return;
        }

        float maxSpeed;
        if (readyToHit) {
            maxSpeed = 480.0f;
        } else if (attackScale < 0.60f && !inCritPhase) {
            maxSpeed = hasBrain() ? (float) Math.min(net.autoMaxDegPerSec() * 0.75, 180.0) : 150.0f;
        } else {
            maxSpeed = hasBrain() ? (float) Math.max(net.autoMaxDegPerSec(), 220.0) : 260.0f;
        }
        if (mc.player.isFallFlying()) {
            maxSpeed = Math.min(maxSpeed, 360.0f);
        }

        double attackSpeed = mc.player.getAttributeValue(Attributes.ATTACK_SPEED);
        float timeUntilHit = Math.max(0.04f, (1.0f - attackScale) / (float) attackSpeed);
        float neededVelYaw = Mth.clamp(yawErr / timeUntilHit, -maxSpeed, maxSpeed);
        float neededVelPitch = Mth.clamp(pitchErr / timeUntilHit, -maxSpeed, maxSpeed);

        if (hasBrain() && totErr < 30.0f) {
            float[] feat = buildInfFeatures(t, eye, curYaw, curPitch, yawErr, pitchErr);
            float[] pred = net.predict(feat);
            float predVelYaw = Mth.clamp(pred[0] * 20.0f, -maxSpeed, maxSpeed);
            float predVelPitch = Mth.clamp(pred[1] * 20.0f, -maxSpeed, maxSpeed);
            float aiWeight = readyToHit ? 0.20f : 0.60f;
            wantVelYaw = predVelYaw * aiWeight + neededVelYaw * (1.0f - aiWeight);
            wantVelPitch = predVelPitch * aiWeight + neededVelPitch * (1.0f - aiWeight);
        } else {
            wantVelYaw = neededVelYaw;
            wantVelPitch = neededVelPitch;
        }

        // Человекоподобное ускорение (deg/s^2) — устраняет резкие рывки и дёргание руки
        float accel = (readyToHit || inCritPhase) ? 3800.0f : 2400.0f;
        float maxVelChange = accel * dt;
        curVelYaw += Mth.clamp(wantVelYaw - curVelYaw, -maxVelChange, maxVelChange);
        curVelPitch += Mth.clamp(wantVelPitch - curVelPitch, -maxVelChange, maxVelChange);

        float rawStepYaw = curVelYaw * dt;
        float rawStepPitch = curVelPitch * dt;

        // Не перелетаем за цель за один кадр
        if (Math.abs(rawStepYaw) > Math.abs(yawErr)) {
            rawStepYaw = yawErr;
            curVelYaw = 0.0f;
        }
        if (Math.abs(rawStepPitch) > Math.abs(pitchErr)) {
            rawStepPitch = pitchErr;
            curVelPitch = 0.0f;
        }

        // Квантование по сетке GCD чувствительности мыши игрока
        gcdAccumYaw += rawStepYaw;
        gcdAccumPitch += rawStepPitch;
        float gcd = GCDUtil.getGCDValue();
        float stepYaw;
        float stepPitch;
        if (gcd > 0.0001f) {
            stepYaw = Math.round(gcdAccumYaw / gcd) * gcd;
            stepPitch = Math.round(gcdAccumPitch / gcd) * gcd;
            gcdAccumYaw -= stepYaw;
            gcdAccumPitch -= stepPitch;
        } else {
            stepYaw = gcdAccumYaw;
            stepPitch = gcdAccumPitch;
            gcdAccumYaw = 0.0f;
            gcdAccumPitch = 0.0f;
        }

        RotationProcess.updateFrame(
                new Rotation(curYaw + stepYaw, curPitch + stepPitch),
                maxSpeed, maxSpeed, maxSpeed, maxSpeed, 10, 1);

        float eps = Math.max(7.0f, AttackAura.targetAngularHalf() * 0.95f);
        aimed = (Math.abs(yawErr) <= eps && Math.abs(pitchErr) <= eps)
                || RayTraceUtil.rayTraceEntity(curYaw, curPitch, AttackAura.attackRange() + 0.5, t)
                || RayTraceUtil.rayTraceEntity(eye, curYaw, curPitch, AttackAura.attackRange() + 0.5, t, 0.12);
    }

    /**
     * Остаточная ошибка наведения из калибровки игрока.
     * Смещение держится 12–30 тиков — как устойчивый «почерк» руки,
     * а не покадровый шум.
     */
    private static void updateResidual() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        int tick = mc.player.tickCount;
        if (tick < residHoldUntilTick) {
            return;
        }
        java.util.concurrent.ThreadLocalRandom rnd =
                java.util.concurrent.ThreadLocalRandom.current();
        residHoldUntilTick = tick + 12 + rnd.nextInt(19);
        if (!hasBrain()) {
            residYaw = 0.0f;
            residPitch = 0.0f;
            return;
        }
        AimNet.Calib c = net.calib();
        residYaw = sampleCalibMag(c.residYawP50, c.residYawP90, rnd)
                * (rnd.nextBoolean() ? 1.0f : -1.0f);
        residPitch = sampleCalibMag(c.residPitchP50, c.residPitchP90, rnd)
                * (rnd.nextBoolean() ? 1.0f : -1.0f);
    }

    /** Треугольное распределение между p50 и p90 — типичный разброс руки. */
    private static float sampleCalibMag(double p50, double p90,
                                        java.util.concurrent.ThreadLocalRandom rnd) {
        double u = (rnd.nextDouble() + rnd.nextDouble()) * 0.5;
        return (float) (p50 + Math.max(0.0, p90 - p50) * u);
    }

    /**
     * Микро-пауза из моторики игрока: вероятность и длительность — из его
     * калибровки. Решение принимается раз в тик, длительность — в тиках,
     * поэтому поведение не зависит от FPS.
     */
    private static boolean checkFreeze(float totErr, boolean readyToHit) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        int tick = mc.player.tickCount;
        if (readyToHit) {
            freezeUntilTick = Integer.MIN_VALUE;
            return false;
        }
        if (tick < freezeUntilTick) {
            return true;
        }
        if (tick == lastFreezeRollTick) {
            return false;
        }
        lastFreezeRollTick = tick;
        if (totErr > 12.0f || !hasBrain()) {
            return false;
        }
        AimNet.Calib c = net.calib();
        double pStart = (c.pausePer100 / 100.0) / Math.max(1.0, c.pauseP50Ticks);
        java.util.concurrent.ThreadLocalRandom rnd =
                java.util.concurrent.ThreadLocalRandom.current();
        if (rnd.nextDouble() < pStart) {
            double u = rnd.nextDouble();
            int dur = (int) Math.max(2, Math.round(
                    c.pauseP50Ticks + Math.max(0.0, c.pauseP90Ticks - c.pauseP50Ticks) * u));
            freezeUntilTick = tick + dur;
            return true;
        }
        return false;
    }

    /**
     * Блуждание точки прицела внутри бокса цели.
     * Случайное блуждание с отражением от границ — безопасно внутри хитбокса.
     */
    private static Vec3 wanderAimPoint(net.minecraft.world.phys.AABB box, boolean readyToHit) {
        if (readyToHit) {
            neuroWander = neuroWander.scale(0.85);
            return new Vec3(
                    (box.minX + box.maxX) / 2.0 + neuroWander.x,
                    box.minY + box.getYsize() * 0.55 + neuroWander.y,
                    (box.minZ + box.maxZ) / 2.0 + neuroWander.z);
        }

        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        if (neuroWanderVel.equals(Vec3.ZERO)) {
            neuroWanderVel = new Vec3(
                    (rnd.nextDouble() - 0.5) * 0.02,
                    (rnd.nextDouble() - 0.5) * 0.015,
                    (rnd.nextDouble() - 0.5) * 0.02);
        }
        double xLim = Math.max(0.04, (box.getXsize() - 0.20) / 2.0);
        double zLim = Math.max(0.04, (box.getZsize() - 0.20) / 2.0);
        double yMin = box.minY + box.getYsize() * 0.35;
        double yMax = box.minY + box.getYsize() * 0.75;

        neuroWanderVel = neuroWanderVel.scale(0.92).add(
                (rnd.nextDouble() - 0.5) * 0.003,
                (rnd.nextDouble() - 0.5) * 0.002,
                (rnd.nextDouble() - 0.5) * 0.003);

        neuroWander = neuroWander.add(neuroWanderVel);
        double wx = Mth.clamp(neuroWander.x, -xLim, xLim);
        double wz = Mth.clamp(neuroWander.z, -zLim, zLim);
        double wy = Mth.clamp(box.minY + box.getYsize() * 0.55 + neuroWander.y, yMin, yMax);
        neuroWander = new Vec3(wx, wy - (box.minY + box.getYsize() * 0.55), wz);

        if (Math.abs(neuroWander.x) >= xLim * 0.9) {
            neuroWanderVel = new Vec3(-Math.signum(neuroWander.x) * (0.006 + rnd.nextDouble() * 0.012),
                    neuroWanderVel.y, neuroWanderVel.z);
        }
        if (Math.abs(neuroWander.z) >= zLim * 0.9) {
            neuroWanderVel = new Vec3(neuroWanderVel.x, neuroWanderVel.y,
                    -Math.signum(neuroWander.z) * (0.006 + rnd.nextDouble() * 0.012));
        }
        if (wy >= yMax * 0.95 || wy <= yMin * 1.05) {
            neuroWanderVel = new Vec3(neuroWanderVel.x,
                    -Math.signum(neuroWander.y) * (0.005 + rnd.nextDouble() * 0.01),
                    neuroWanderVel.z);
        }

        double cx = (box.minX + box.maxX) / 2.0;
        double cz = (box.minZ + box.maxZ) / 2.0;
        return new Vec3(cx + neuroWander.x, wy, cz + neuroWander.z);
    }

    /** Наведена ли нейронка на цель (гейт для ударов ауры). */
    public static boolean aimed() {
        Minecraft mc = Minecraft.getInstance();
        if (target == null || mc.player == null) {
            return false;
        }
        float curYaw = mc.player.getYRot();
        float curPitch = mc.player.getXRot();
        float eps = Math.max(7.0f, AttackAura.targetAngularHalf() * 0.95f);
        Vec3 eye = mc.player.getEyePosition(1.0f);
        float[] rot = rotTo(eye, target.getBoundingBox().getCenter());
        float yawErr = Math.abs(Mth.wrapDegrees(rot[0] - curYaw));
        float pitchErr = Math.abs(rot[1] - curPitch);
        if (yawErr <= eps && pitchErr <= eps) {
            return true;
        }
        float sYaw = ServerView.yaw(curYaw);
        float sPitch = ServerView.pitch(curPitch);
        float sYawErr = Math.abs(Mth.wrapDegrees(rot[0] - sYaw));
        float sPitchErr = Math.abs(rot[1] - sPitch);
        if (sYawErr <= eps && sPitchErr <= eps) {
            return true;
        }
        double range = AttackAura.attackRange() + 0.5;
        return RayTraceUtil.rayTraceEntity(eye, curYaw, curPitch, range, target, 0.12)
                || RayTraceUtil.rayTraceEntity(eye, sYaw, sPitch, range, target, 0.12);
    }

    /** Сбросить состояние инференса (при выключении ауры). */
    public static void reset() {
        if (training) {
            stopTraining();
        }
        resetInference();
    }

    private static void resetInference() {
        target = null;
        wantVelYaw = 0.0f;
        wantVelPitch = 0.0f;
        curVelYaw = 0.0f;
        curVelPitch = 0.0f;
        gcdAccumYaw = 0.0f;
        gcdAccumPitch = 0.0f;
        infPrevTargetId = -1;
        aimed = false;
        lastFrameNanos = 0;
        freezeUntilTick = Integer.MIN_VALUE;
        lastFreezeRollTick = Integer.MIN_VALUE;
        residYaw = 0.0f;
        residPitch = 0.0f;
        residHoldUntilTick = Integer.MIN_VALUE;
        RotationProcess.requestReset();
    }

    private static void decayVelocity() {
        curVelYaw *= 0.7f;
        curVelPitch *= 0.7f;
        if (Math.abs(curVelYaw) < 0.01f) {
            curVelYaw = 0.0f;
        }
        if (Math.abs(curVelPitch) < 0.01f) {
            curVelPitch = 0.0f;
        }
    }

    // ---------------- признаки ----------------

    /** Углы (yaw, pitch), чтобы смотреть из {@code from} в {@code to}. */
    private static float[] rotTo(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        float yaw = (float) (Math.toDegrees(Mth.atan2(d.z, d.x)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Mth.atan2(d.y, Math.hypot(d.x, d.z))));
        return new float[]{yaw, pitch};
    }

    private static float[] buildRecFeatures(LivingEntity tgt, Vec3 eye,
                                            float viewYaw, float viewPitch,
                                            float yawErr, float pitchErr) {
        float tgtYawVel;
        float tgtPitchVel;
        int id = tgt.getId();
        if (hasLastView && id == prevTargetId) {
            tgtYawVel = Mth.wrapDegrees(yawErr - lastFeatYawErr + (viewYaw - lastViewYaw));
            tgtPitchVel = (pitchErr - lastFeatPitchErr) + (viewPitch - lastViewPitch);
            tgtYawVel = Mth.clamp(tgtYawVel, -30.0f, 30.0f);
            tgtPitchVel = Mth.clamp(tgtPitchVel, -30.0f, 30.0f);
        } else {
            tgtYawVel = 0.0f;
            tgtPitchVel = 0.0f;
        }
        lastFeatYawErr = yawErr;
        lastFeatPitchErr = pitchErr;

        float dist = (float) eye.distanceTo(tgt.getBoundingBox().getCenter());
        return new float[]{yawErr, pitchErr, dist, tgtYawVel, tgtPitchVel, 0.0f};
    }

    private static float[] buildInfFeatures(LivingEntity tgt, Vec3 eye,
                                            float viewYaw, float viewPitch,
                                            float yawErr, float pitchErr) {
        float tgtYawVel;
        float tgtPitchVel;
        int id = tgt.getId();
        if (id == infPrevTargetId) {
            tgtYawVel = Mth.wrapDegrees(yawErr - infLastYawErr);
            tgtPitchVel = pitchErr - infLastPitchErr;
            tgtYawVel = Mth.clamp(tgtYawVel, -30.0f, 30.0f);
            tgtPitchVel = Mth.clamp(tgtPitchVel, -30.0f, 30.0f);
        } else {
            tgtYawVel = 0.0f;
            tgtPitchVel = 0.0f;
        }
        infLastYawErr = yawErr;
        infLastPitchErr = pitchErr;
        infPrevTargetId = id;

        float dist = (float) eye.distanceTo(tgt.getBoundingBox().getCenter());
        return new float[]{yawErr, pitchErr, dist, tgtYawVel, tgtPitchVel, 0.0f};
    }

    private static void tell(String msg) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui != null) {
                Component text = Component.literal(msg);
                mc.execute(() -> ((dev.hatek.mixin.accessor.GuiAccessor) mc.gui)
                        .hatek$hud().setOverlayMessage(text, false));
            }
        } catch (Exception ignored) {
        }
    }
}

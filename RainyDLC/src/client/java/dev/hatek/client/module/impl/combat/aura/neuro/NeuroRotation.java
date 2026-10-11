package dev.hatek.client.module.impl.combat.aura.neuro;

import dev.hatek.client.module.impl.combat.AttackAura;
import dev.hatek.client.module.impl.combat.aimassist.AimNet;
import dev.hatek.client.module.impl.combat.aimassist.AimSample;
import dev.hatek.client.module.impl.combat.aura.rotation.Rotation;
import dev.hatek.client.module.impl.combat.aura.rotation.RotationProcess;
import dev.hatek.client.module.impl.combat.aura.rotation.ServerView;
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
        tell("§d● §fЗапись нейро-ротации «" + name + "»: целься и бей сам, как обычно.");
        tell("§7Аура НЕ вмешивается: целься мышкой во врага/жителя для записи датасета.");
    }

    /** Остановить запись, сохранить датасет. */
    public static void stopTraining() {
        if (!training) {
            return;
        }
        training = false;
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
        }
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

        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 center = t.getBoundingBox().getCenter();
        float[] targetRot = rotTo(eye, center);

        float curYaw = ServerView.yaw(mc.player.getYRot());
        float curPitch = ServerView.pitch(mc.player.getXRot());
        float yawErr = Mth.wrapDegrees(targetRot[0] - curYaw);
        float pitchErr = targetRot[1] - curPitch;
        float totErr = (float) Math.hypot(yawErr, pitchErr);

        float attackScale = mc.player.getAttackStrengthScale(0.5f);
        boolean inCritPhase = !mc.player.onGround() && mc.player.getDeltaMovement().y < -0.01;

        // Если оружие только перезаряжается (кулдаун < 60%) и мы не в крит-фазе — не насилуем ротации,
        // даём телу и руке двигаться свободно без рывков:
        if (attackScale < 0.60f && !inCritPhase) {
            if (totErr > 35.0f && RotationProcess.isRotating()) {
                RotationProcess.requestReset();
            }
            aimed = false;
            return;
        }

        // Расчёт динамической скорости, чтобы наводка НЕ длилась дольше кулдауна атаки:
        double attackSpeed = mc.player.getAttributeValue(Attributes.ATTACK_SPEED);
        float timeUntilHit = Math.max(0.02f, (1.0f - attackScale) / (float) attackSpeed);
        float neededSpeed = totErr / timeUntilHit;

        float baseSpeed = hasBrain() ? (float) Math.max(net.autoMaxDegPerSec(), 200.0) : 260.0f;
        float finalSpeed = Math.max(baseSpeed, neededSpeed);

        // Если оружие готово к удару или в прыжке фаза падения для крита — мгновенно доводим на цель
        if (attackScale >= 0.88f || inCritPhase) {
            finalSpeed = Math.max(finalSpeed, 560.0f);
        }
        finalSpeed = Math.min(finalSpeed, 720.0f);

        RotationProcess.updateFrame(new Rotation(targetRot[0], targetRot[1]),
                finalSpeed, finalSpeed, finalSpeed, finalSpeed, 10, 1);

        float eps = Math.max(6.5f, AttackAura.targetAngularHalf() * 0.95f);
        aimed = (Math.abs(yawErr) <= eps && Math.abs(pitchErr) <= eps)
                || RayTraceUtil.rayTraceEntity(curYaw, curPitch, AttackAura.attackRange() + 0.5, t);
    }

    /** Наведена ли нейронка на цель (гейт для ударов ауры). */
    public static boolean aimed() {
        Minecraft mc = Minecraft.getInstance();
        if (target == null || mc.player == null) {
            return false;
        }
        float yaw = ServerView.yaw(mc.player.getYRot());
        float pitch = ServerView.pitch(mc.player.getXRot());
        float eps = Math.max(6.5f, AttackAura.targetAngularHalf() * 0.95f);
        Vec3 eye = mc.player.getEyePosition(1.0f);
        float[] rot = rotTo(eye, target.getBoundingBox().getCenter());
        float yawErr = Math.abs(Mth.wrapDegrees(rot[0] - yaw));
        float pitchErr = Math.abs(rot[1] - pitch);
        return (yawErr <= eps && pitchErr <= eps)
                || RayTraceUtil.rayTraceEntity(yaw, pitch, AttackAura.attackRange() + 0.5, target);
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

package dev.hatek.client.module.impl.combat.aura.neuro;

import dev.hatek.client.module.impl.combat.AttackAura;
import dev.hatek.client.module.impl.combat.aimassist.AimNet;
import dev.hatek.client.module.impl.combat.aura.util.GCDUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;

/**
 * Нейро-ротации AttackAura: вместо скриптованного SpaceTime прицелом
 * управляет MLP 6 -&gt; 24 -&gt; 24 -&gt; 2, обученная повторять наводку
 * самого игрока.
 *
 * <p>Обучение:
 * <ol>
 *   <li>Панель «Нейро-обучение»: введи название, нажми «Обучить».</li>
 *   <li>Аура включится сама, но бить НЕ будет — целься и бей сам,
 *       как обычно пвпешишься. Можно тренироваться на спавне
 *       (мобы, жители, стойки для брони тоже считаются).</li>
 *   <li>«Стоп» — датасет сохранится.</li>
 *   <li>«Обучить нейронку (Python)» — локальный {@code train_neuro.py}
 *       (numpy, Adam) обучит сеть. Всё выполняется на твоём ПК,
 *       в сеть ничего не уходит.</li>
 * </ol>
 *
 * <p>Почему это не палится: инференс идёт покадрово с квантованием
 * по сетке GCD чувствительности мыши (как настоящее движение мыши),
 * скорость ограничена p95 твоей же скорости из датасета, плюс
 * инерционное сглаживание и мёртвая зона. Статистически это ты сам.
 */
public final class NeuroRotation {
    /** Минимум сэмплов для осмысленного обучения. */
    public static final int MIN_SAMPLES = 120;
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

    /** Загрузить активный профиль при старте. */
    public static void init() {
        try {
            String name = NeuroStore.loadActiveProfile();
            if (!name.isEmpty() && NeuroStore.hasWeights(name)) {
                net = NeuroStore.loadWeights(name);
                activeProfile = name;
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean hasBrain() {
        return net.isTrained();
    }

    public static boolean brainTrained() {
        return net.isTrained();
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
        tell("§d● §fЗапись нейро-ротации «" + name + "»: целься и бей сам, как обычно.");
        tell("§7Аура включена, но НЕ бьёт. Можно тренироваться на спавне.");
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
                + " сэмплов. Теперь обучи нейронку через Python.");
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
        double bestAng = 25.0;
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

        boolean engaged = attackDown || ticksSinceAttack < 10;
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
     * Считает признаки и прогоняет сеть, запоминая желаемую скорость.
     */
    public static void setTarget(LivingEntity t) {
        target = t;
        Minecraft mc = Minecraft.getInstance();
        if (t == null || mc.player == null || !hasBrain()) {
            wantVelYaw = 0.0f;
            wantVelPitch = 0.0f;
            aimed = false;
            return;
        }
        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 center = t.getBoundingBox().getCenter();
        float[] rot = rotTo(eye, center);
        float viewYaw = mc.player.getYRot();
        float viewPitch = mc.player.getXRot();
        float yawErr = Mth.wrapDegrees(rot[0] - viewYaw);
        float pitchErr = rot[1] - viewPitch;

        float[] feat = buildInfFeatures(t, eye, viewYaw, viewPitch, yawErr, pitchErr);
        float[] pred = net.predict(feat); // [dYaw, dPitch] за тик (50мс)

        // Направленная безопасность: сеть обязана тянуть к цели, а не от неё
        float dy = pred[0];
        float dp = pred[1];
        if (yawErr > 0.0f && dy < 0.0f) {
            dy = -dy * 0.5f;
        } else if (yawErr < 0.0f && dy > 0.0f) {
            dy = -dy * 0.5f;
        }
        if (pitchErr > 0.0f && dp < 0.0f) {
            dp = -dp * 0.5f;
        } else if (pitchErr < 0.0f && dp > 0.0f) {
            dp = -dp * 0.5f;
        }

        // Кап скорости — p95 твоей же скорости из датасета
        float cap = (float) Math.max(net.autoMaxDegPerSec(), 180.0);
        wantVelYaw = Mth.clamp(dy * 20.0f, -cap, cap);
        wantVelPitch = Mth.clamp(dp * 20.0f, -cap, cap);

        float eps = Math.max(3.5f, AttackAura.targetAngularHalf() * 0.55f);
        aimed = Math.abs(yawErr) <= eps && Math.abs(pitchErr) <= eps;
    }

    /**
     * Покадровое применение: вызывается из ComponentManager.render3D().
     * Плавное доведение до желаемой скорости с квантованием по сетке GCD —
     * как настоящее движение мыши.
     */
    public static void onFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !hasBrain()) {
            return;
        }
        LivingEntity t = target;
        if (t == null || !t.isAlive() || t.isRemoved() || mc.gui.screen() != null) {
            decayVelocity();
            return;
        }

        long now = System.nanoTime();
        if (lastFrameNanos == 0) {
            lastFrameNanos = now;
            return;
        }
        float deltaSec = (float) ((now - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = now;
        deltaSec = Mth.clamp(deltaSec, 0.0005f, 0.05f);

        float viewYaw = mc.player.getYRot();
        float viewPitch = mc.player.getXRot();
        Vec3 eye = mc.player.getEyePosition(1.0f);
        float[] rot = rotTo(eye, t.getBoundingBox().getCenter());
        float yawErr = Mth.wrapDegrees(rot[0] - viewYaw);
        float pitchErr = rot[1] - viewPitch;
        float totErr = (float) Math.hypot(yawErr, pitchErr);

        if (totErr < 0.05f) {
            decayVelocity();
            return;
        }
        float deadzoneScale = Mth.clamp((totErr - 0.05f) / 0.35f, 0.0f, 1.0f);

        float targetVelYaw = wantVelYaw * deadzoneScale;
        float targetVelPitch = wantVelPitch * deadzoneScale;

        float blend = Mth.clamp(deltaSec * 30.0f, 0.05f, 1.0f);
        curVelYaw += (targetVelYaw - curVelYaw) * blend;
        curVelPitch += (targetVelPitch - curVelPitch) * blend;

        float rawStepYaw = curVelYaw * deltaSec;
        float rawStepPitch = curVelPitch * deltaSec;

        // Не перелетаем за цель за один кадр
        rawStepYaw = Mth.clamp(rawStepYaw, -Math.abs(yawErr) * 0.9f, Math.abs(yawErr) * 0.9f);
        rawStepPitch = Mth.clamp(rawStepPitch, -Math.abs(pitchErr) * 0.9f, Math.abs(pitchErr) * 0.9f);

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

        if (Math.abs(stepYaw) > 1e-5f || Math.abs(stepPitch) > 1e-5f) {
            float newYaw = viewYaw + stepYaw;
            float newPitch = Mth.clamp(viewPitch + stepPitch, -90.0f, 90.0f);
            mc.player.setYRot(newYaw);
            mc.player.setXRot(newPitch);
            mc.player.yRotO += stepYaw;
            mc.player.xRotO += stepPitch;
        }
    }

    /** Наведена ли нейронка на цель (гейт для ударов ауры). */
    public static boolean aimed() {
        return hasBrain() && aimed;
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
            if (mc.gui != null && mc.gui.getChat() != null) {
                Component text = Component.literal(msg);
                mc.execute(() -> mc.gui.getChat().addMessage(text));
            }
        } catch (Exception ignored) {
        }
    }
}

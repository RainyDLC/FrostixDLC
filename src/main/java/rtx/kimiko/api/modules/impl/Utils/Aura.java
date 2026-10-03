package rtx.kimiko.api.modules.impl.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rtx.kimiko.api.events.EventHandler;
import rtx.kimiko.api.events.impl.game.TickEvent;
import rtx.kimiko.api.liteapi.Feature;
import rtx.kimiko.api.modules.Category;
import rtx.kimiko.api.modules.Module;
import rtx.kimiko.api.modules.SubCategory;
import rtx.kimiko.api.modules.impl.Utils.neuro.AimStateSnapshot;
import rtx.kimiko.api.modules.impl.Utils.neuro.AuraHumanStyle;
import rtx.kimiko.api.modules.impl.Utils.neuro.NeuroAimSolver;
import rtx.kimiko.api.modules.impl.Utils.neuro.NeuroModel;
import rtx.kimiko.api.modules.impl.Visuals.custompet.entity.CustomPetEntity;
import rtx.kimiko.api.modules.settings.Setting;
import rtx.kimiko.api.modules.settings.impl.BooleanSetting;
import rtx.kimiko.api.modules.settings.impl.ModeSetting;
import rtx.kimiko.api.modules.settings.impl.MultiSelectSetting;
import rtx.kimiko.api.modules.settings.impl.SeparatorSetting;
import rtx.kimiko.api.modules.settings.impl.SliderSetting;
import rtx.kimiko.utils.storage.friend.FriendUtils;

@Feature(value={"aura"})
public final class Aura extends Module {

    private static final String TARGET_PLAYERS = "Игроки";
    private static final String TARGET_MOBS = "Мобы";

    private static final String PRIORITY_DISTANCE = "Ближайший";
    private static final String PRIORITY_HEALTH = "Меньше HP";
    private static final String PRIORITY_ANGLE = "В поле зрения";

    private static final String ROTATION_OFF = "Выкл";
    private static final String ROTATION_INSTANT = "Мгновенно";
    private static final String ROTATION_SMOOTH = "Плавно";
    private static final String ROTATION_NEURO = "Нейро";

    private final MultiSelectSetting targets = (MultiSelectSetting) register(
            new MultiSelectSetting("Цели", "Кого атаковать", TARGET_PLAYERS, TARGET_MOBS).selected(TARGET_PLAYERS, TARGET_MOBS)
    );
    private final SliderSetting range = (SliderSetting) register(
            new SliderSetting("Радиус", "Максимальная дистанция атаки").range(1.0f, 6.0f).increment(0.1f).setValue(3.5f)
    );
    private final SliderSetting cps = (SliderSetting) register(
            new SliderSetting("CPS", "Атак в секунду").range(1.0f, 20.0f).increment(1.0f).setValue(12.0f)
    );
    private final SliderSetting wallsRange = (SliderSetting) register(
            new SliderSetting("Радиус за стеной", "Дистанция атаки без прямой видимости").range(0.5f, 6.0f).increment(0.1f).setValue(2.5f)
    );
    private final ModeSetting priority = (ModeSetting) register(
            new ModeSetting("Приоритет", "Как выбирать цель", PRIORITY_DISTANCE, PRIORITY_DISTANCE, PRIORITY_HEALTH, PRIORITY_ANGLE)
    );
    private final BooleanSetting cooldownCheck = (BooleanSetting) register(
            new BooleanSetting("Ждать кулдаун", "Атаковать только когда удар заряжен", false)
    );
    private final BooleanSetting swing = (BooleanSetting) register(
            new BooleanSetting("Анимация руки", "Воспроизводить взмах руки при атаке", true)
    );

    private final SeparatorSetting rotationSep = (SeparatorSetting) register(new SeparatorSetting("Поворот"));
    private final ModeSetting rotationMode = (ModeSetting) register(
            new ModeSetting("Поворот", "Наводить камеру на цель перед ударом. Нейро — человекоподобная GRU-сеть из Rockstar", ROTATION_SMOOTH, ROTATION_OFF, ROTATION_INSTANT, ROTATION_SMOOTH, ROTATION_NEURO)
    );
    private final SliderSetting rotationSpeed = (SliderSetting) register(
            new SliderSetting("Скорость поворота", "Градусов за тик в плавном режиме").range(5.0f, 180.0f).increment(5.0f).setValue(45.0f)
                    .visible(() -> this.rotationMode.is(ROTATION_SMOOTH))
    );
    private final BooleanSetting requireRotation = (BooleanSetting) register(
            new BooleanSetting("Только в прицеле", "Бить только когда цель уже в поле зрения", true)
    );

    // === Neuro state ===
    private final NeuroAimSolver solver = new NeuroAimSolver();
    private final float[] rotationOutput = new float[2];
    private int lastTargetId = -1;
    private float reachError;
    private int reachRefresh;
    private boolean modelWarned;

    private int attackTimer = 0;

    public Aura() {
        super("Aura", "Автоматически атакует игроков и мобов рядом с вами.", Category.COMBAT, SubCategory.PVP);
    }

    @Override
    protected void onEnable() {
        NeuroModel.invalidate();
        this.solver.reset();
        this.lastTargetId = -1;
        this.modelWarned = false;
        this.reachRefresh = 0;
    }

    @Override
    protected void onDisable() {
        this.attackTimer = 0;
        this.solver.reset();
        this.lastTargetId = -1;
    }

    @EventHandler
    private void onTick(@NotNull TickEvent event) {
        if (!event.isPre()) {
            return;
        }
        ClientPlayerEntity player = this.mc.player;
        ClientWorld world = this.mc.world;
        ClientPlayerInteractionManager manager = this.mc.interactionManager;
        if (player == null || world == null || manager == null || this.mc.currentScreen != null) {
            return;
        }
        if (player.isSpectator() || !player.isAlive()) {
            return;
        }

        if (this.attackTimer > 0) {
            this.attackTimer--;
        }

        LivingEntity target = this.findTarget(player, world);
        if (target == null) {
            if (this.lastTargetId != -1) {
                this.solver.reset();
                this.lastTargetId = -1;
            }
            return;
        }

        if (this.rotationMode.is(ROTATION_INSTANT)) {
            this.lookAt(player, target);
        } else if (this.rotationMode.is(ROTATION_SMOOTH)) {
            this.smoothLookAt(player, target);
        } else if (this.rotationMode.is(ROTATION_NEURO)) {
            if (!this.neuroRotate(player, target)) {
                return;
            }
        }

        if (this.requireRotation.getValue() && !this.rotationMode.is(ROTATION_OFF)) {
            if (!this.isLookingAt(player, target)) {
                return;
            }
        }

        if (this.attackTimer > 0) {
            return;
        }
        if (this.cooldownCheck.getValue() && player.getAttackCooldownProgress(0.5f) < 0.95f) {
            return;
        }

        manager.attackEntity(player, target);
        if (this.swing.getValue()) {
            player.swingHand(Hand.MAIN_HAND);
        }
        this.attackTimer = Math.max(1, Math.round(20.0f / this.cps.getValue()));
        if (this.rotationMode.is(ROTATION_NEURO)) {
            this.solver.resetHitStreak();
        }
    }

    /**
     * Neuro rotation step: GRU inference produces a human-like yaw/pitch delta
     * (variable speed, overshoot, micro-pauses). Returns false while the model
     * is unavailable or the target is too far off-aim.
     */
    private boolean neuroRotate(ClientPlayerEntity player, LivingEntity target) {
        NeuroModel model = NeuroModel.getActive();
        if (model == null) {
            this.warnModelMissing();
            return false;
        }

        Box box = target.getBoundingBox();
        Vec3d eye = player.getEyePos();
        Vec3d center = box.getCenter().subtract(eye);
        double horizontal = Math.max(Math.hypot(center.x, center.z), 0.05);
        float targetYaw = (float) Math.toDegrees(Math.atan2(center.z, center.x)) - 90.0f;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(center.y, horizontal));
        float yawWindow = Math.max((float) Math.toDegrees(Math.atan2((box.maxX - box.minX) / 2.0, horizontal)), 0.5f);
        float pitchWindow = Math.max((float) Math.toDegrees(Math.atan2((box.maxY - box.minY) / 2.0, horizontal)), 0.5f);
        double distance = distanceToBox(eye, box);

        if (target.getId() != this.lastTargetId || !this.solver.isInitialized()) {
            this.solver.init(model, player.getYaw(), player.getPitch(), targetYaw, targetPitch);
            this.lastTargetId = target.getId();
        }

        this.solver.setInputState(AimStateSnapshot.capture(player, target));

        if (--this.reachRefresh <= 0) {
            this.reachError = AuraHumanStyle.getInstance().getSlack() * model.sampleErrorCurve(ThreadLocalRandom.current().nextFloat());
            this.reachRefresh = model.getHold();
        }

        boolean solved = this.solver.solve(
                model,
                player.getYaw(), player.getPitch(),
                targetYaw, targetPitch,
                yawWindow, pitchWindow,
                distance,
                this.reachError,
                12,
                AuraHumanStyle.getInstance().getTemperature(),
                2,
                AuraHumanStyle.getInstance().getSpeed(),
                this.rotationOutput);
        if (!solved) {
            return false;
        }
        this.solver.incrementHitStreak();

        player.setYaw(player.getYaw() + this.rotationOutput[0]);
        player.setPitch(MathHelper.clamp(player.getPitch() + this.rotationOutput[1], -90.0f, 90.0f));
        return this.solver.isOnTarget() || this.isLookingAt(player, target);
    }

    private void warnModelMissing() {
        if (this.modelWarned) {
            return;
        }
        this.modelWarned = true;
        rtx.kimiko.api.modules.impl.Interface.NotificationsModule.Companion.notify(
                "Neuro-модель '" + NeuroModel.getActiveName() + "' не загрузилась — поворот недоступен", 3000L);
    }

    @Nullable
    private LivingEntity findTarget(ClientPlayerEntity player, ClientWorld world) {
        float range = this.range.getValue();
        float walls = this.wallsRange.getValue();
        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (!this.isValidTarget(player, living)) {
                continue;
            }
            float dist = player.distanceTo(living);
            boolean visible = player.canSee(living);
            if (visible ? dist > range : dist > walls) {
                continue;
            }
            candidates.add(living);
        }
        if (candidates.isEmpty()) {
            return null;
        }

        Vec3d eye = player.getEyePos();
        LivingEntity best = null;
        float bestScore = Float.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            float score;
            if (this.priority.is(PRIORITY_HEALTH)) {
                score = candidate.getHealth();
            } else if (this.priority.is(PRIORITY_ANGLE)) {
                score = this.angleTo(player, eye, candidate);
            } else {
                score = player.distanceTo(candidate);
            }
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private boolean isValidTarget(ClientPlayerEntity player, LivingEntity entity) {
        if (entity == player || !entity.isAlive() || entity.isRemoved()) {
            return false;
        }
        if (entity instanceof CustomPetEntity) {
            return false;
        }
        if (entity instanceof PlayerEntity) {
            if (FriendUtils.isFriend(entity)) {
                return false;
            }
            return this.targets.is(TARGET_PLAYERS);
        }
        if (entity instanceof MobEntity) {
            return this.targets.is(TARGET_MOBS);
        }
        return false;
    }

    private static double distanceToBox(Vec3d point, Box box) {
        double dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private Vec3d aimPoint(LivingEntity target) {
        return target.getEntityPos().add(0.0, target.getHeight() * 0.75f, 0.0);
    }

    private void lookAt(ClientPlayerEntity player, LivingEntity target) {
        Vec3d eye = player.getEyePos();
        Vec3d point = this.aimPoint(target);
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, horizontal)), -90.0f, 90.0f);
        player.setYaw(yaw);
        player.setPitch(pitch);
    }

    private void smoothLookAt(ClientPlayerEntity player, LivingEntity target) {
        Vec3d eye = player.getEyePos();
        Vec3d point = this.aimPoint(target);
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, horizontal)), -90.0f, 90.0f);
        float speed = this.rotationSpeed.getValue();
        float deltaYaw = MathHelper.wrapDegrees(targetYaw - player.getYaw());
        float deltaPitch = targetPitch - player.getPitch();
        player.setYaw(player.getYaw() + MathHelper.clamp(deltaYaw, -speed, speed));
        player.setPitch(player.getPitch() + MathHelper.clamp(deltaPitch, -speed, speed));
    }

    private float angleTo(ClientPlayerEntity player, Vec3d eye, LivingEntity target) {
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d toTarget = this.aimPoint(target).subtract(eye).normalize();
        double dot = MathHelper.clamp(look.dotProduct(toTarget), -1.0, 1.0);
        return (float) Math.toDegrees(Math.acos(dot));
    }

    private boolean isLookingAt(ClientPlayerEntity player, LivingEntity target) {
        return this.angleTo(player, player.getEyePos(), target) < 45.0f;
    }
}

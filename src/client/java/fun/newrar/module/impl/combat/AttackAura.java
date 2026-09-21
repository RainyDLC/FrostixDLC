package fun.newrar.module.impl.combat;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.*;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import fun.newrar.Client;
import fun.newrar.manager.rotation.RotationProcess;
import fun.newrar.utils.animation.Easings;
import fun.newrar.manager.event_impl.*;
import fun.newrar.manager.event_impl.EventMoveInput;
import fun.newrar.manager.event_impl.EventTick;
import fun.newrar.manager.event_impl.EventUpdate;
import fun.newrar.manager.event_impl.WorldLoadEvent;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.manager.rotation.Rotation;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.impl.combat.aura.RotationType;
import fun.newrar.module.impl.utils.FakePlayer;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BooleanSetting;
import fun.newrar.module.api.settings.impl.ButtonSetting;
import fun.newrar.module.api.settings.impl.ModeSetting;
import fun.newrar.module.api.settings.impl.MultiBooleanSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.screen.DropdownScreen;
import fun.newrar.screen.Menu;
import fun.newrar.screen.RotationBuilderScreen;
import fun.newrar.utils.aura.AttackUtil;
import fun.newrar.utils.aura.AuraUtil;
import fun.newrar.utils.aura.LagCompensation;
import fun.newrar.utils.aura.ServerReach;
import fun.newrar.utils.aura.UAttack;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.math.ServerUtil;
import fun.newrar.utils.other.Instance;
import fun.newrar.utils.other.TimerUtil;
import fun.newrar.utils.player.MoveUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import fun.newrar.utils.render.RenderUtil;

import static net.minecraft.util.Hand.MAIN_HAND;

@ModuleInfo(
        name = "Attack Aura",
        desc = "Автоматическое наведение и атака выбранных целей в заданном радиусе",
        category = Category.COMBAT,
        key = GLFW.GLFW_KEY_R
)
public class AttackAura extends Module {
    public static AttackAura get() {
        return Instance.get(AttackAura.class);
    }

    public SliderSetting attackRange = new SliderSetting(this, "Радиус атаки", 3.0F, 2.5F, 6, 0.1F);
    public SliderSetting preRange = new SliderSetting(this, "Радиус обнаружения", 1.0F, 0.0F, 3, 0.1F);

    public MultiBooleanSetting targets = new MultiBooleanSetting(this, "Кого атаковать",
            new BooleanSetting("Игроков", true),
            new BooleanSetting("Голых игроков", true),
            new BooleanSetting("Мобов", false),
            new BooleanSetting("Друзей", false));

    public ModeSetting typeRotation = new ModeSetting(this,"Тип наведения", "FunTime","SpookyTime","Default","Snap","HvH","Custom","Legit","Sloth","Matrix","Neuro","Grim","RellyWorld");
    public ModeSetting typeSnap = new ModeSetting(this,"Режим снапа", "360","Fov").setVisible(() -> typeRotation.is("Snap"));
    public SliderSetting fov = new SliderSetting(this, "Fov", 50.0F, 25.0F, 90.0F, 1.0F).setVisible(() -> typeRotation.is("Snap") && typeSnap.is("Fov"));

    public BooleanSetting fovRender = new BooleanSetting(this,"Отображать Fov",false).setVisible(() -> typeRotation.is("Snap") && typeSnap.is("Fov"));

    public ModeSetting spookyMode = new ModeSetting(this, "Режим Spooky", "1.21", "Дуэли", "1.16").setVisible(() -> typeRotation.is("SpookyTime"));

    public boolean isConstructorType() {
        return typeRotation.is("Custom") || typeRotation.is("Matrix")
                || typeRotation.is("Neuro") || typeRotation.is("Grim");
    }

    public ModeSetting targetSort = new ModeSetting(this, "Сортировать по", "Умный", "Здоровью", "Дистанции", "Прицелу");

    public ButtonSetting rotationBuilder = new ButtonSetting(this, "Конструктор ротации", () ->
            mc.setScreen(new RotationBuilderScreen())).setVisible(this::isConstructorType);

    public SliderSetting cYawMin = new SliderSetting(this, "Скорость Yaw мин", 120F, 1F, 360F, 1F).setVisible(this::isConstructorType);
    public SliderSetting cYawMax = new SliderSetting(this, "Скорость Yaw макс", 180F, 1F, 360F, 1F).setVisible(this::isConstructorType);
    public SliderSetting cPitchMin = new SliderSetting(this, "Скорость Pitch мин", 80F, 1F, 180F, 1F).setVisible(this::isConstructorType);
    public SliderSetting cPitchMax = new SliderSetting(this, "Скорость Pitch макс", 120F, 1F, 180F, 1F).setVisible(this::isConstructorType);
    public SliderSetting cHitYaw = new SliderSetting(this, "Скорость удара Yaw", 200F, 50F, 400F, 5F).setVisible(this::isConstructorType);
    public SliderSetting cHitPitch = new SliderSetting(this, "Скорость удара Pitch", 180F, 50F, 400F, 5F).setVisible(this::isConstructorType);
    public SliderSetting cRandomYaw = new SliderSetting(this, "Рандом Yaw", 1.0F, 0F, 10F, 0.05F).setVisible(this::isConstructorType);
    public SliderSetting cRandomPitch = new SliderSetting(this, "Рандом Pitch", 1.0F, 0F, 10F, 0.05F).setVisible(this::isConstructorType);
    public SliderSetting cOscX = new SliderSetting(this, "Осцилляция X", 0.0F, -3F, 3F, 0.05F).setVisible(this::isConstructorType);
    public SliderSetting cOscY = new SliderSetting(this, "Осцилляция Y", 0.0F, -3F, 3F, 0.05F).setVisible(this::isConstructorType);

    public ModeSetting typeSprint = new ModeSetting(this,"Тип спринта", "Packet","Silent","Legit");
    public ModeSetting typeMove = new ModeSetting(this,"Коррекция движения","Сфокусированная","Свободная");

    public MultiBooleanSetting others = new MultiBooleanSetting(this, "Доп. проверки",
            new BooleanSetting("Умные криты", true),
            new BooleanSetting("Только криты", false),
            new BooleanSetting("Бить через блоки", false),
            new BooleanSetting("Ломать щит", false),
            new BooleanSetting("Сброс щита", false));

    public MultiBooleanSetting noattackto = new MultiBooleanSetting(this, "Не бить если",
            new BooleanSetting("Используешь еду", false),
            new BooleanSetting("Открыт контейнер", false));

    public static LivingEntity target = null;

    public float[] getRanges() {
        return new float[]{attackRange.getValue(), preRange.getValue()};
    }

    public AttackAura() {
        typeRotation.onAction(this::applyConstructorPreset);
    }

    private void applyConstructorPreset() {
        switch (typeRotation.getValue()) {
            case "Matrix" -> RotationBuilderScreen.applyPreset("Matrix");
            case "Neuro" -> RotationBuilderScreen.applyPreset("Neuro");
            case "Grim" -> RotationBuilderScreen.applyPreset("Grim");
            default -> { }
        }
    }

    @EventHandler

    public void onEvent(EventUpdate e) {
        LagCompensation.record(target);

        boolean recheck = ++retargetClock % 4 == 0 && !attackReadyNow();
        if (target == null || !isValidTarget(target) || recheck) {
            updateTarget();
        }

        if(stoptick != 0) {
            stoptick--;
        }

        updateCritSprint();

        if (!checkToAttack() && target != null && !postMotionAlive()) {
            attackEntity();
        }
    }

    /**
     * Хвост ClientPlayerEntity#sendMovementPackets: позиция и ротация уже ушли на сервер,
     * поэтому его проверка дистанции считает нас по текущему тику, а не по прошлому.
     */
    @EventHandler
    public void onPostMotion(EventPostMotion e) {
        lastPostMotionMs = System.currentTimeMillis();
        if (!ServerReach.postMotion() || target == null || checkToAttack()) return;
        attackEntity();
    }

    /** Если пакет движения в этом тике не уходил — бьём как раньше, из EventUpdate. */
    private boolean postMotionAlive() {
        return ServerReach.postMotion() && System.currentTimeMillis() - lastPostMotionMs < 90L;
    }

    private boolean wantsCrit() {
        return others.getValue("Только криты") || others.getValue("Умные криты");
    }

    private boolean sprintKeyForcedOff;

    private void updateCritSprint() {
        boolean hold = target != null && wantsCrit()
                && !AttackUtil.hasMovementRestrictions()
                && UAttack.resetSprintTick(target, getRanges());

        if (hold) {
            if (mc.options.sprintKey.isPressed()) {
                mc.options.sprintKey.setPressed(false);
                sprintKeyForcedOff = true;
            }
            if (mc.player.isSprinting()) {
                mc.player.setSprinting(false);
            }
        } else if (sprintKeyForcedOff) {
            mc.options.sprintKey.setPressed(true);
            sprintKeyForcedOff = false;
        }
    }

    private void releaseCritSprint() {
        if (sprintKeyForcedOff) {
            mc.options.sprintKey.setPressed(true);
            sprintKeyForcedOff = false;
        }
    }

    private boolean checkToAttack() {
        if (mc.player == null) return true;

        boolean baseCheck = mc.player.isUsingItem() && noattackto.getValue("Используешь еду");

        boolean screenCheck = noattackto.getValue("Открыт контейнер") && mc.currentScreen != null  && !(mc.currentScreen instanceof Menu || mc.currentScreen instanceof DropdownScreen);

        return baseCheck ||  screenCheck;
    }

    private boolean attackReadyNow() {
        if (target == null || mc.player == null || checkToAttack()) {
            return false;
        }
        if (!ServerReach.canReach(target, attackRange.getValue())) {
            return false;
        }
        float[] ranges = getRanges();
        ranges = new float[]{ranges[0], ranges[1], ranges[0] + ranges[1]};
        return UAttack.shouldAttack(target, !typeRotation.is("HvH"), true, true, 0L, ranges);
    }

    public void attackEntity() {
        if (target == null) {
            return;
        }

        if (!ServerReach.canReach(target, attackRange.getValue())) {
            return;
        }

        float[] ranges = getRanges();
        ranges = new float[]{ranges[0], ranges[1], ranges[0] + ranges[1]};

        boolean canAttack = UAttack.shouldAttack(target, !typeRotation.is("HvH"), true, true, 0L, ranges);

        if (canAttack) {
            final Runnable[] shieldBreak = UAttack.hitShieldBreakTaskForUse(target, others.getValue("Ломать щит")),
                    shieldPressBypass = UAttack.resetShieldSilentTaskForUse(others.getValue("Сброс щита")),
                    skipSilentSprint = UAttack.skipSilentSprintingTaskForUse((typeSprint.is("Packet") || typeSprint.is("Silent")));
            final Runnable preHitSendCodeSingleTick = () -> {
                skipSilentSprint[0].run();
                shieldPressBypass[0].run();
                shieldBreak[0].run();
            }, postHitSendCodeSingleTick = () -> {
                shieldBreak[1].run();
                shieldPressBypass[1].run();
                skipSilentSprint[1].run();
            };

            UAttack.useEntity(target, preHitSendCodeSingleTick, postHitSendCodeSingleTick, MAIN_HAND);

            justAttacked = true;
        }
    }

    public static int stoptick = 0;

    @EventHandler
    private void setCorrection(EventMoveInput eventMoveInput) {
        if (target != null && mc.player != null && mc.world != null) {
            if (UAttack.resetSprintTick(target, getRanges()) && target != null && !AttackUtil.hasMovementRestrictions() && typeSprint.is("Legit")) {
                eventMoveInput.setForward(0);
                eventMoveInput.setStrafe(0);
            }
        }
        if (target != null && mc.player != null && mc.world != null && typeMove.is("Свободная")) {
            MoveUtil.fixMovement(eventMoveInput, mc.player.getYaw(), mc.gameRenderer.getCamera().getYaw());
        }

            if (target != null && mc.player != null && mc.world != null && typeMove.is("Сфокусированная") && ServerUtil.isFunTime()) {
                if (!mc.player.isSubmergedInWater()) {
                    if (ServerUtil.isCopyTime()) {
                        Vec3d vec3d = AuraUtil.getVector2(target);
                        float yaw = (float) Math.toDegrees(Math.atan2(-vec3d.x, vec3d.z));
                        MoveUtil.fixMovement(eventMoveInput, mc.player.getYaw(), yaw);
                    } else {
                        Vec3d vec = target.getEntityPos().subtract(mc.player.getEyePos()).normalize();

                        float yaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
                        MoveUtil.fixMovement(eventMoveInput, mc.player.getYaw(), yaw);
                    }
                }
            }
            if (target != null && mc.player != null && mc.world != null && typeMove.is("Сфокусированная") && !ServerUtil.isFunTime()) {
                if (ServerUtil.isCopyTime()) {
                    Vec3d vec3d = AuraUtil.getVector2(target);
                    float yaw = (float) Math.toDegrees(Math.atan2(-vec3d.x, vec3d.z));
                    MoveUtil.fixMovement(eventMoveInput, mc.player.getYaw(), yaw);
                } else {
                    Vec3d vec = target.getEntityPos().subtract(mc.player.getEyePos()).normalize();

                    float yaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
                    MoveUtil.fixMovement(eventMoveInput, mc.player.getYaw(), yaw);
                }
        }
    }

    public static long lastAttackTime = 0L;

    public float tick = 0;
    public Vec3d targetPoint;
    public Vec3d currentPoint;
    public boolean justAttacked = false;

    private int retargetClock = 0;

    private long lastPostMotionMs = 0L;

    public TimerUtil timeSped1 = new TimerUtil();
    public TimerUtil timeSped2 = new TimerUtil();

    @EventHandler
    public void onRotate(EventTick e) {
        doRotation();
    }

    private void doRotation() {
        if (target == null || mc.player == null || mc.world == null) {
            return;
        }
        float[] ranges = getRanges();
        ranges = new float[]{ranges[0], ranges[1], ranges[0] + ranges[1]};

        boolean canAttack = UAttack.shouldAttack(target, false, true, false, -350, ranges);

        for (RotationType type : RotationType.values()) {
            if (typeRotation.is(type.getName())) {
                type.getRotation().onRotation(this, target, ranges, canAttack);
                break;
            }
        }
    }

    @EventHandler
    private void onResetOnWorld(WorldLoadEvent event) {
        target = null;
        targetPoint = null;
        currentPoint = null;
        LagCompensation.reset();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        target = null;
        targetPoint = null;
        currentPoint = null;
        releaseCritSprint();
        RotationProcess.resetParentTimeout();
        LagCompensation.reset();
    }

    private void updateTarget() {
        if (FakePlayer.fakePlayer != null
                && FakePlayer.fakePlayer.isAlive()
                && mc.player.distanceTo(FakePlayer.fakePlayer) <= auraDist()) {
            target = FakePlayer.fakePlayer;
            return;
        }

        LivingEntity bestTarget = null;
        double bestScore = Double.MAX_VALUE;
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0F).normalize();
        float atkRange = attackRange.getValue();
        boolean smart = targetSort.is("Умный");

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!isValidTarget(living)) continue;
            Vec3d targetPos = living.getEntityPos().add(0, living.getHeight() * 0.5, 0);
            Vec3d toTarget = targetPos.subtract(eyePos).normalize();
            double angle = Math.acos(MathHelper.clamp(lookVec.dotProduct(toTarget), -1.0, 1.0));
            double dist = eyePos.distanceTo(targetPos);

            double score;
            switch (targetSort.getValue()) {
                case "Здоровью" -> score = living.getHealth() + dist * 0.02;
                case "Дистанции" -> score = dist + angle * 2.5;
                case "Прицелу" -> score = angle;
                default -> {
                    score = angle * 0.55;

                    double over = Math.max(0, dist - atkRange);
                    score += over * 0.06;

                    if (over > 0) score += 5.0;

                    score += living.getHealth() * 0.06;

                    if (dist <= atkRange * 1.4 && living.handSwingTicks >= 0 && living.handSwingTicks < 8)
                        score -= 0.30;
                }
            }

            if (!smart && living == target) score -= 0.10;

            if (score < bestScore) {
                bestScore = score;
                bestTarget = living;
            }
        }
        target = bestTarget;
    }

    private float auraDist() {
        return attackRange.getValue() + preRange.getValue();
    }

    public double getTargetFov(LivingEntity entity) {
        Vec3d targetPos = entity.getEntityPos().add(0, entity.getHeight() * 0.5, 0);
        Vec3d vec = targetPos.subtract(mc.player.getEyePos());

        float yaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
        float pitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))), -90F, 90F);

        float yawDelta = MathHelper.wrapDegrees(yaw - Rotation.cameraYaw());
        float pitchDelta = pitch - Rotation.cameraPitch();
        return Math.hypot(yawDelta, pitchDelta);
    }

    private boolean isValidTarget(LivingEntity entity) {
        return isValidTarget(entity, auraDist());
    }

    public boolean isValidTarget(LivingEntity entity, double maxDist) {
        if (entity instanceof ClientPlayerEntity) return false;

        if (ServerReach.attackDistance(entity) > maxDist) return false;

        if (!others.getValue("Бить через блоки")) {
            if (!LagCompensation.isVisibleLoose(entity)) return false;
        }

        if (entity instanceof PlayerEntity p) {
            if (AntiBot.getInstance().isBot(p)) return false;
            if (Client.get().friendManager().isFriend(p.getName().getString()) && !targets.getValue("Друзей")) return false;
        }
        if (entity instanceof PlayerEntity && !targets.getValue("Игроков")) return false;

        if (entity instanceof PlayerEntity && entity.getArmorVisibility() <= 0 && !targets.getValue("Голых игроков")) return false;

        if (entity instanceof PlayerEntity && ((PlayerEntity) entity).isCreative()) return false;

        if ((entity instanceof Monster
                || entity instanceof SlimeEntity
                || entity instanceof VillagerEntity
                || entity instanceof AnimalEntity
                || entity instanceof FishEntity
                || entity instanceof SchoolingFishEntity
                || entity instanceof CodEntity
                || entity instanceof SalmonEntity
                || entity instanceof TropicalFishEntity
                || entity instanceof PufferfishEntity
                || entity instanceof SquidEntity
                || entity instanceof GlowSquidEntity
                || entity instanceof DolphinEntity
                || entity instanceof TurtleEntity
                || entity instanceof AxolotlEntity)
                && !targets.getValue("Мобов")) {
            return false;
        }

        return !entity.isInvulnerable() && entity.isAlive() && !(entity instanceof ArmorStandEntity);
    }

    @EventHandler
    public void onDisplay(VisualRotEvent e) {
    }

    @EventHandler
    public void onDisplay(EventDisplay e) {
        if ((mc.player != null && mc.world != null && target != null)) {
            if (!(typeRotation.is("Snap") && typeSnap.is("Fov") &&  fovRender.getValue() ) || mc.player == null || mc.world == null) return;

            float targetScale = 2F;
            float currentScale = (float) mc.getWindow().getScaleFactor();
            float scaleFix = targetScale / currentScale;

            int screenWidth = (int) (mc.getWindow().getScaledWidth() / scaleFix);
            int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);

            float screenW = screenWidth;
            float screenH = screenHeight;
            float cx = screenW / 2f;
            float cy = screenH / 2f;

            float m00 = RenderUtil.Render3D.lastProjMat.m00();
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 10D));
            int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0F));
            float pixelRadius = (float) (Math.tan(Math.toRadians(fov.getValue() - 15)) * (screenW / 2f) * m00);
            float diameter = pixelRadius * 2f ;
            float x = cx - pixelRadius;
            float y = cy - pixelRadius;

            boolean hasTarget = target != null;
            int baseAlpha = 255;

            int color = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.getColor(255), 255), redColor, hurtPC);

            RenderUtil.Render2D.outline(x, y, diameter, diameter, 1, ColorUtil.replAlpha(color, 0.1F), pixelRadius);
        }
    }
}

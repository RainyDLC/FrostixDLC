package rtx.kimiko.utils.math.rotation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import rtx.kimiko.api.events.EventHandler;
import rtx.kimiko.api.events.impl.network.PacketSendEvent;
import rtx.kimiko.utils.math.MathUtil;

public class RotationManager {
    public static final RotationManager INSTANCE = new RotationManager();

    private Rotation currentRotation = Rotation.ZERO;
    private Rotation serverRotation = Rotation.ZERO;
    private Rotation prevRotation = Rotation.ZERO;
    private Rotation renderRotation = Rotation.ZERO;
    private Rotation sentRotation = null;
    private RotationState state = RotationState.IDLE;
    private RotationRequest activeRequest = null;
    private long lastRequestTime = 0L;

    public RotationManager() {
    }

    public static RotationManager getInstance() {
        return INSTANCE;
    }

    public Rotation getPlayerRotation() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return Rotation.ZERO;
        }
        return new Rotation(mc.player.getYaw(), mc.player.getPitch());
    }

    public Rotation getEntityRotation(LivingEntity entity) {
        return new Rotation(entity.getYaw(), entity.getPitch());
    }

    public void aimAtEntity(Entity entity, float yawStep, float pitchStep, float pitchSpeed, RotationPriority priority, RotationApplyMode applyMode) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity == null || mc.player == null) {
            return;
        }
        double dx = entity.getX() - mc.player.getX();
        double dy = (entity.getY() + entity.getEyeHeight(entity.getPose())) - mc.player.getEyeY();
        double dz = entity.getZ() - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));
        this.applyRotation(new Rotation(yaw, pitch), applyMode, yawStep, pitchStep, pitchSpeed, priority);
    }

    public void applyRotation(Rotation rotation, RotationApplyMode applyMode, float yawStep, float pitchStep, float pitchSpeed, RotationPriority priority) {
        this.submitRotation(rotation, applyMode, yawStep, pitchStep, pitchSpeed, priority, true);
    }

    public void submitRotation(Rotation rotation, RotationApplyMode applyMode, float yawStep, float pitchStep, float pitchSpeed, RotationPriority priority, boolean smooth) {
        int weight = priority.getWeight();
        if (this.activeRequest == null || this.activeRequest.getPriority() <= weight || this.state != RotationState.ROTATING) {
            float baseYaw = this.activeRequest == null ? this.getPlayerRotation().getYaw() : this.activeRequest.getRotation().getYaw();
            rotation.setYaw(RotationUtil.wrapTargetYaw(baseYaw, rotation.getYaw()));
            this.activeRequest = new RotationRequest(rotation, applyMode, yawStep, pitchStep, pitchSpeed, weight, smooth);
            this.lastRequestTime = System.currentTimeMillis();
            this.state = RotationState.ROTATING;
            this.stepTowardTarget();
        }
    }

    public void onTick() {
        this.prevRotation = this.currentRotation;
        if (this.activeRequest == null) {
            this.currentRotation = this.getPlayerRotation();
            return;
        }

        if (System.currentTimeMillis() - this.lastRequestTime > 70L) {
            RotationMode returnMode = this.activeRequest.getMode();
            if (returnMode == RotationMode.CAMERA) {
                float pitch = MathHelper.clamp(this.currentRotation.getPitch(), -90.0f, 90.0f);
                float yaw = setClientRotation(this.currentRotation.getYaw(), pitch);
                this.currentRotation = new Rotation(yaw, pitch);
                this.state = RotationState.IDLE;
                this.activeRequest = null;
                return;
            }
            if (returnMode == RotationMode.NONE) {
                this.currentRotation = this.getPlayerRotation();
                this.state = RotationState.IDLE;
                this.activeRequest = null;
                return;
            }

            if (this.getPlayerRotation().distanceTo(this.currentRotation) < Math.max(0.1f, RotationUtil.getRotationStep())) {
                setClientRotation(this.currentRotation.getYaw(), this.currentRotation.getPitch());
                this.state = RotationState.IDLE;
                this.activeRequest = null;
            } else {
                this.state = RotationState.ROTATING_BACK;
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null) {
                    mc.player.setYaw(RotationUtil.snapYaw(mc.player.getYaw(), RotationUtil.wrapTargetYaw(this.currentRotation.getYaw(), mc.player.getYaw())));
                }

                Rotation stepRotation = this.activeRequest.getStepFunction() == null ? null : this.activeRequest.getStepFunction().returnStep(this.currentRotation, this.getPlayerRotation());
                if (stepRotation == null) {
                    float minStep = 5.0f;
                    float maxStep = 88.0f;
                    stepRotation = new Rotation(
                            approach(this.currentRotation.getYaw(), this.getPlayerRotation().getYaw(), MathUtil.randomYawVariant(minStep, maxStep)),
                            approach(this.currentRotation.getPitch(), this.getPlayerRotation().getPitch(), MathUtil.randomYawVariant(minStep, maxStep) / MathUtil.randomYawVariant(1.9f, 2.2f))
                    );
                }
                this.currentRotation = RotationUtil.snapRotation(this.currentRotation, stepRotation);
            }
            return;
        }

        this.state = RotationState.ROTATING;
        this.stepTowardTarget();
    }

    public void interpolate(float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        float yaw = MathUtil.lerp(this.prevRotation.getYaw(), this.currentRotation.getYaw(), tickDelta);
        float pitch = this.prevRotation.getPitch() + (this.currentRotation.getPitch() - this.prevRotation.getPitch()) * tickDelta;
        this.renderRotation = new Rotation(yaw, pitch);
    }

    private void stepTowardTarget() {
        if (this.activeRequest == null) return;
        Rotation target = new Rotation(
                approach(this.currentRotation.getYaw(), this.activeRequest.getRotation().getYaw(), this.activeRequest.getYawStep()),
                approach(this.currentRotation.getPitch(), this.activeRequest.getRotation().getPitch(), this.activeRequest.getPitchStep())
        );
        this.currentRotation = this.activeRequest.isSmooth() ? RotationUtil.snapRotation(this.currentRotation, target) : target;
    }

    public static float approach(float from, float to, float step) {
        float diff = RotationUtil.getAngleDifference(from, to);
        if (Math.abs(diff) <= step) {
            return from + diff;
        }
        return from + Math.signum(diff) * step;
    }

    public static float setClientRotation(float yaw, float pitch) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return yaw;
        float newYaw = mc.player.getYaw() + MathHelper.wrapDegrees(yaw - mc.player.getYaw());
        float newPitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);
        mc.player.lastYaw = newYaw;
        mc.player.lastPitch = newPitch;
        return newYaw;
    }

    @EventHandler
    public void onPacketSend(PacketSendEvent event) {
        if (this.isIdle()) return;

        if (event.getPacket() instanceof PlayerInteractItemC2SPacket packet) {
            if (this.sentRotation == null) {
                this.sentRotation = new Rotation(this.currentRotation.getYaw(), this.currentRotation.getPitch());
            }
            this.serverRotation.setYaw(this.sentRotation.getYaw());
            this.serverRotation.setPitch(this.sentRotation.getPitch());
            if (packet.getYaw() != this.sentRotation.getYaw() || packet.getPitch() != this.sentRotation.getPitch()) {
                event.setPacket(new PlayerInteractItemC2SPacket(packet.getHand(), packet.getSequence(), this.sentRotation.getYaw(), this.sentRotation.getPitch()));
            }
            return;
        }

        if (event.getPacket() instanceof PlayerMoveC2SPacket packet) {
            if (this.sentRotation == null) {
                this.sentRotation = new Rotation(this.currentRotation.getYaw(), this.currentRotation.getPitch());
            }
            this.serverRotation.setYaw(this.sentRotation.getYaw());
            this.serverRotation.setPitch(this.sentRotation.getPitch());

            if (packet.changesLook()) {
                float targetYaw = this.sentRotation.getYaw();
                float targetPitch = this.sentRotation.getPitch();

                if (packet.changesPosition()) {
                    event.setPacket(new PlayerMoveC2SPacket.Full(
                            packet.getX(0.0),
                            packet.getY(0.0),
                            packet.getZ(0.0),
                            targetYaw,
                            targetPitch,
                            packet.isOnGround(),
                            packet.horizontalCollision()
                    ));
                } else {
                    event.setPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                            targetYaw,
                            targetPitch,
                            packet.isOnGround(),
                            packet.horizontalCollision()
                    ));
                }
            }
        }
    }

    public boolean isIdle() {
        return this.state == RotationState.IDLE;
    }

    public Rotation getTargetRotation() {
        return this.state == RotationState.IDLE ? this.getPlayerRotation() : this.getCurrentRotation();
    }

    public void resetTimer() {
        if (this.activeRequest != null && this.state == RotationState.ROTATING) {
            this.lastRequestTime = System.currentTimeMillis();
        }
    }

    public Rotation getCurrentRotation() {
        return this.currentRotation;
    }

    public void setCurrentRotation(Rotation currentRotation) {
        this.currentRotation = currentRotation;
    }

    public Rotation getServerRotation() {
        return this.serverRotation;
    }

    public Rotation getPrevRotation() {
        return this.prevRotation;
    }

    public void setPrevRotation(Rotation prevRotation) {
        this.prevRotation = prevRotation;
    }

    public Rotation getRenderRotation() {
        return this.renderRotation;
    }

    public void setRenderRotation(Rotation renderRotation) {
        this.renderRotation = renderRotation;
    }

    public Rotation getSentRotation() {
        return this.sentRotation;
    }

    public void setSentRotation(Rotation sentRotation) {
        this.sentRotation = sentRotation;
    }

    public RotationState getState() {
        return this.state;
    }

    public void setState(RotationState state) {
        this.state = state;
    }

    public RotationRequest getActiveRequest() {
        return this.activeRequest;
    }

    public void setActiveRequest(RotationRequest activeRequest) {
        this.activeRequest = activeRequest;
    }
}

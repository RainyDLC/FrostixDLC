package ru.white.manager.rotation;


import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.white.manager.event_impl.EventMoveInput;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.events.orbit.EventPriority;
import ru.white.module.impl.combat.AttackAura;
import ru.white.module.impl.render.LightningRenderer;
import ru.white.module.impl.combat.aura.rotation.FunTimeRotation;
import ru.white.module.impl.player.ClickHelper;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.aura.GCDUtil;
import ru.white.utils.aura.RayTraceUtil;
import ru.white.utils.aura.UAttack;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.MathUtil;
import ru.white.utils.math.ServerUtil;
import ru.white.utils.player.MoveUtil;
import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import static net.minecraft.client.gl.RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET;
import static net.minecraft.util.math.MathHelper.wrapDegrees;

public class RotationProcess extends Component {

    public static RotationTask currentTask = RotationTask.IDLE;
    public static float currentYawSpeed;
    public static float currentPitchSpeed;
    public static float currentYawReturnSpeed;
    public static float currentPitchReturnSpeed;
    public static int currentPriority;
    public static int currentTimeout;
    public static int idleTicks;
    public static Rotation targetRotation;

    public static boolean isRotating() {
        return !currentTask.equals(currentTask.IDLE);
    }

    private void resetRotation() {
        Rotation targetRotation = new Rotation(FreeLookUtil.freeYaw, FreeLookUtil.freePitch);


        if (ServerUtil.isHolyWorld()) {
            stopRotation();
        } else {
            if (updateRotation(targetRotation, currentYawReturnSpeed, currentPitchReturnSpeed)) {
                stopRotation();
            }
        }
    }

    public static void resetParentTimeout() {
        currentTimeout = 0;
        currentTask = RotationTask.IDLE;
        currentPriority = 0;

        FreeLookUtil.setActive(false);
    }


    @EventHandler
    public void onEventMovement(EventMoveInput eventMoveInput) {

        if (currentTask.equals(RotationTask.RESET)) {
            MoveUtil.fixMovement(eventMoveInput, mc.player.getYaw(), mc.gameRenderer.getCamera().getYaw());
        }

    }


    @EventHandler
    public void onEvent(EventTick event) {

        System.out.print("Мы переходим в новый проект https://t.me/RainyDLC \n");

        if (currentTask.equals(RotationTask.AIM) && idleTicks > currentTimeout) {
            currentTask = (RotationTask.RESET);
        }


        if (currentTask.equals(RotationTask.RESET)) {
            if (ServerUtil.isHolyWorld() || ServerUtil.isCopyTime()) {
                stopRotation();
            } else {
                resetRotation();
            }
        }
        idleTicks++;
    }



    public static void update(Rotation target, float yawSpeed, float pitchSpeed, float yawReturnSpeed,
                              float pitchReturnSpeed, int timeout, int priority, boolean clientRotation) {
        if (currentPriority > priority) {
            return;
        }

        if (currentTask.equals(RotationTask.IDLE) && !clientRotation) {
            FreeLookUtil.active = true;
        }

        currentYawSpeed = yawSpeed;
        currentPitchSpeed = pitchSpeed;
        currentYawReturnSpeed = yawReturnSpeed;
        currentPitchReturnSpeed = pitchReturnSpeed;
        currentTimeout = timeout;
        currentPriority = priority;
        currentTask = RotationTask.AIM;
        targetRotation = target;

        updateRotation(target, yawSpeed, pitchSpeed);
    }

    public static void update(Rotation targetRotation, float turnSpeed, float returnSpeed, int timeout, int priority) {
        update(targetRotation, turnSpeed, turnSpeed, returnSpeed, returnSpeed, timeout, priority, false);
    }


    static boolean updateRotation(Rotation targetRotation, float yawSpeed, float pitchSpeed) {
        if (mc.player == null)
            return false;

        Rotation currentRotation = new Rotation(mc.player);


        float pitchDelta = targetRotation.pitch - currentRotation.pitch;
        float yawDelta = wrapDegrees(targetRotation.yaw - currentRotation.yaw);

        float clampedYaw = Math.min(Math.abs(yawDelta), yawSpeed);
        float clampedPitch = Math.min(Math.abs(pitchDelta), pitchSpeed);


        mc.player.setYaw(
                mc.player.headYaw += GCDUtil.getSensitivity(MathHelper.clamp(yawDelta, -clampedYaw, clampedYaw)));

        mc.player.setPitch(MathHelper.clamp(
                mc.player.getPitch()
                        + GCDUtil.getSensitivity(MathHelper.clamp(pitchDelta, -clampedPitch, clampedPitch)),
                -90F, 90F));


        idleTicks = 0;
        return new Rotation(mc.player).getDelta(targetRotation) < 1F;
    }

    public void stopRotation() {
        currentTask = (RotationTask.IDLE);
        currentPriority = (0);
        FreeLookUtil.setActive(false);

    }


    public enum RotationTask {
        AIM,
        RESET,
        IDLE
    }

    private final BufferAllocator boxAllocator = new BufferAllocator(1 << 18);
    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        boxAllocator.clear();
    }
    private float animationNurik = 0.0F;
    private long currentTimeSpirits = 0;
    /** Фаза сердцебиения для режима «Сердце»: интеграл частоты по времени. */
    private long heartLastTime = 0L;
    private float heartPhase = 0f;

    // ── скретч-буферы кадра для ESP-режимов: ноль аллокаций в цикле рендера ──
    private static final float[] SNOW_PX = new float[24], SNOW_PY = new float[24],
            SNOW_PZ = new float[24], SNOW_SPIN = new float[24];
    private static final float[] SWORD_PX = new float[16], SWORD_PY = new float[16],
            SWORD_PZ = new float[16], SWORD_TILT = new float[16];
    private static final float[] FIRE_OX = new float[64], FIRE_OY = new float[64], FIRE_OZ = new float[64],
            FIRE_ALPHA = new float[64], FIRE_H = new float[64];
    private static final int[] FIRE_RGB_OUT = new int[64], FIRE_RGB_CORE = new int[64];
    private static final float[] HEART_HX = new float[48], HEART_HY = new float[48];
    public LivingEntity target = null;
    public Animation alpha = new Animation();
    public Animation alpha_2 = new Animation();
    private final LightningRenderer lightningRenderer = new LightningRenderer();

    public static final RenderPipeline ROMB_ESP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/wtex")
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );
    public static final Function<Identifier, RenderLayer> ROMB_ESP =
            Util.memoize(texture -> {
                RenderSetup setup = RenderSetup.builder(ROMB_ESP_PIPELINE)
                        .texture("Sampler0", texture)
                        .translucent()
                        .expectedBufferSize(1536)
                        .build();
                return RenderLayer.of("wtex", setup);
            });
    private static final RenderPipeline RING_FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "ring_esp_fill"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );
    private static final RenderPipeline RING_LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "ring_esp_line"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );
    private static final RenderLayer RING_FILL_LAYER = RenderLayer.of("ring_esp_fill",
            RenderSetup.builder(RING_FILL_PIPELINE).expectedBufferSize(1 << 16).build());
    private static final RenderLayer RING_LINE_LAYER = RenderLayer.of("ring_esp_line",
            RenderSetup.builder(RING_LINE_PIPELINE).expectedBufferSize(1 << 14).build());



    private interface JitterPreset {
        float getYaw(long time);
        float getPitch(long time);
    }
    private final JitterPreset[] jitterPresets = new JitterPreset[] {

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) ((Math.sin(t / 80D) + Math.cos(t / 35D) * 0.35) * 13);
                }
                public float getPitch(long t) {
                    return (float) ((Math.cos(t / 95D) + Math.sin(t / 42D) * 0.4) * 11);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) (Math.sin(t / 55D + Math.sin(t / 350D)) * 15);
                }
                public float getPitch(long t) {
                    return (float) (Math.cos(t / 65D + Math.cos(t / 280D)) * 14);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) ((Math.cos(t / 42D) * 0.7 + Math.sin(t / 120D)) * 17);
                }
                public float getPitch(long t) {
                    return (float) ((Math.sin(t / 58D) * 0.5 + Math.cos(t / 140D)) * 12);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) (Math.sin(t / 25D) * (9 + Math.sin(t / 300D) * 4));
                }
                public float getPitch(long t) {
                    return (float) (Math.cos(t / 48D) * (13 + Math.cos(t / 250D) * 3));
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) ((Math.sin(t / 70D) + Math.sin(t / 17D) * 0.25) * 16);
                }
                public float getPitch(long t) {
                    return (float) ((Math.cos(t / 90D) + Math.cos(t / 23D) * 0.2) * 13);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) ((Math.cos(t / 110D) * 0.8 + Math.sin(t / 38D) * 0.6) * 12);
                }
                public float getPitch(long t) {
                    return (float) ((Math.sin(t / 100D) * 0.7 + Math.cos(t / 29D) * 0.4) * 15);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) (Math.sin(t / 170D + 1.2) * 21);
                }
                public float getPitch(long t) {
                    return (float) ((Math.cos(t / 52D) + Math.sin(t / 240D)) * 10);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) ((Math.cos(t / 36D) * 0.45 + Math.sin(t / 145D)) * 18);
                }
                public float getPitch(long t) {
                    return (float) ((Math.sin(t / 44D) * 0.35 + Math.cos(t / 180D)) * 14);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) (Math.sin(t / 60D + Math.cos(t / 180D)) * 14);
                }
                public float getPitch(long t) {
                    return (float) (Math.cos(t / 73D + Math.sin(t / 210D)) * 17);
                }
            },

            new JitterPreset() {
                public float getYaw(long t) {
                    return (float) ((Math.sin(t / 48D) * 0.9 + Math.cos(t / 140D) * 0.5) * 15);
                }
                public float getPitch(long t) {
                    return (float) ((Math.cos(t / 84D) * 0.8 + Math.sin(t / 32D) * 0.3) * 12);
                }
            },
    };

    private interface SpeedPreset {
        float getYawSpeed();
        float getPitchSpeed();
    }

    private final SpeedPreset[] speedPresets = new SpeedPreset[]{

            new SpeedPreset() {
                public float getYawSpeed() { return 40F; }
                public float getPitchSpeed() { return 12F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 55F; }
                public float getPitchSpeed() { return 15F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 30F; }
                public float getPitchSpeed() { return 10F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 48F; }
                public float getPitchSpeed() { return 18F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 60F; }
                public float getPitchSpeed() { return 14F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 35F; }
                public float getPitchSpeed() { return 16F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 50F; }
                public float getPitchSpeed() { return 13F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 44F; }
                public float getPitchSpeed() { return 17F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 58F; }
                public float getPitchSpeed() { return 11F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 32F; }
                public float getPitchSpeed() { return 19F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 46F; }
                public float getPitchSpeed() { return 12F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 53F; }
                public float getPitchSpeed() { return 15F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 37F; }
                public float getPitchSpeed() { return 18F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 42F; }
                public float getPitchSpeed() { return 13F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 57F; }
                public float getPitchSpeed() { return 16F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 34F; }
                public float getPitchSpeed() { return 11F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 49F; }
                public float getPitchSpeed() { return 20F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 62F; }
                public float getPitchSpeed() { return 14F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 39F; }
                public float getPitchSpeed() { return 17F; }
            },

            new SpeedPreset() {
                public float getYawSpeed() { return 52F; }
                public float getPitchSpeed() { return 12F; }
            }
    };

    private float currentSpeedYaw = 0;
    private float currentSpeedPitch = 0;

    float jitterIntensity = 0;


    private long jitterSwitchTime = ThreadLocalRandom.current().nextLong(1000L, 2001L);
    private long nextJitterSwitch = System.currentTimeMillis() + jitterSwitchTime;
    private int currentJitterPreset = 0;
    private int nextJitterPreset = 1;

    private long speedSwitchTime = ThreadLocalRandom.current().nextLong(2000L, 4001L);
    private long nextSpeedSwitch = System.currentTimeMillis() + speedSwitchTime;
    private int currentSpeedPreset = 0;
    private int nextSpeedPreset = 1;


    private static final long JITTER_SWITCH_TIME = 1000L;

    private float lastYawJitter;
    private float lastPitchJitter;

    private static final long SPEED_SWITCH_TIME = 2500L;

    private float lastYawSpeed;
    private float lastPitchSpeed;

    int tick;


  /*  @EventHandler
    public void onTick(EventTick eventTick) {
        if (AttackAura.get().typeRotation.is("FunTime Snap")) {
            if (mc.player != null && mc.world != null) {

                LivingEntity target = AttackAura.target;

                boolean isActive = AttackAura.get().isEnabled() && target != null ;

                float rawYaw;
                float rawPitch;

                long speedTime = System.currentTimeMillis();

                if (speedTime >= nextSpeedSwitch) {
                    currentSpeedPreset = nextSpeedPreset;
                    nextSpeedPreset = ThreadLocalRandom.current().nextInt(speedPresets.length);

                    while (nextSpeedPreset == currentSpeedPreset)
                        nextSpeedPreset = ThreadLocalRandom.current().nextInt(speedPresets.length);

                    speedSwitchTime = ThreadLocalRandom.current().nextLong(2000L, 4001L);
                    nextSpeedSwitch = speedTime + speedSwitchTime;
                }

                SpeedPreset currentSpeed = speedPresets[currentSpeedPreset];
                SpeedPreset nextSpeed = speedPresets[nextSpeedPreset];

                float speedBlend = 1F - (nextSpeedSwitch - speedTime) / (float) speedSwitchTime;
                speedBlend = MathHelper.clamp(speedBlend, 0F, 1F);
                speedBlend = speedBlend * speedBlend * (3F - 2F * speedBlend);


                speedBlend = speedBlend * speedBlend * (3F - 2F * speedBlend);

                float targetYawSpeed = MathHelper.lerp(
                        speedBlend,
                        currentSpeed.getYawSpeed(),
                        nextSpeed.getYawSpeed()
                );

                float targetPitchSpeed = MathHelper.lerp(
                        speedBlend,
                        currentSpeed.getPitchSpeed(),
                        nextSpeed.getPitchSpeed()
                );


                float activeSpeedYaw = MathUtil.randomLerp(30,35);
                float activeSpeedPitch = MathUtil.randomLerp(8,14);


                lastYawSpeed += (targetYawSpeed - lastYawSpeed) * 0.5F;
                lastPitchSpeed += (targetPitchSpeed - lastPitchSpeed) * 0.5F;

                float speed = lastYawSpeed + MathUtil.randomLerp(-2.5F, 2.5F);
                float speed2 = lastPitchSpeed + MathUtil.randomLerp(-1.0F, 1.0F);



                activeSpeedYaw = speed;
                activeSpeedPitch = speed2;

                AttackAura aura = AttackAura.get();



                if (isActive) {
                    FreeLookUtil.active = true;

                    jitterIntensity = Math.min(1.0f, jitterIntensity + 0.1f);
                } else {

                    jitterIntensity = Math.max(0.0f, jitterIntensity - 0.05f);
                }






                if (aura.justAttacked && System.currentTimeMillis() >= aura.attackFlickAt) {
                    aura.justAttacked = false;
                }

                if (isActive) {

                    Vec3d vec = target.getEntityPos().add(0.15F * Math.sin(System.currentTimeMillis() / 250D),target.getHeight() / 2 + (target.getHeight() /  4) *
                                            Math.cos(System.currentTimeMillis() / 200D),
                                    0.15F * Math.cos(System.currentTimeMillis() / 250D))
                            .subtract(mc.player.getEyePos())
                            .normalize();

                    rawYaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
                    rawPitch = (float) MathHelper.clamp(
                            -Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))),
                            -90F, 90F
                    );


                    float[] ranges = aura.getRanges();
                    ranges = new float[]{ranges[0], ranges[1], ranges[0] + ranges[1]};
                    boolean canAttack = UAttack.shouldAttack(target, false, true, true, -MathUtil.randomInt(400,450), ranges);

                    activeSpeedYaw *= 1.4f;
                    activeSpeedPitch *= 2.7F;


                    if(canAttack) {
                        tick += MathUtil.randomInt(2,3);
                    }

                    boolean canAttackSnap = false;

                    if(tick != 0) {
                        canAttackSnap = true;
                        tick--;
                    }

                    if(canAttackSnap) {
                       AttackAura.lastPitch = rawPitch;
                       AttackAura.lastYaw = rawYaw;
                    }
                    if(!canAttackSnap) {
                        AttackAura.lastYaw = FreeLookUtil.freeYaw;
                        AttackAura.lastPitch = FreeLookUtil.freePitch;
                    }


                } else {



                    AttackAura.lastYaw = FreeLookUtil.freeYaw;
                    AttackAura.lastPitch = FreeLookUtil.freePitch;
                }


                long currentTime = System.currentTimeMillis();
                int presetIndex = (int) ((currentTime / JITTER_SWITCH_TIME) % jitterPresets.length);

                if (currentTime >= nextJitterSwitch) {
                    currentJitterPreset = nextJitterPreset;
                    nextJitterPreset = ThreadLocalRandom.current().nextInt(jitterPresets.length);

                    while (nextJitterPreset == currentJitterPreset)
                        nextJitterPreset = ThreadLocalRandom.current().nextInt(jitterPresets.length);

                    jitterSwitchTime = ThreadLocalRandom.current().nextLong(1000L, 2001L);
                    nextJitterSwitch = currentTime + jitterSwitchTime;
                }

                JitterPreset currentPreset = jitterPresets[currentJitterPreset];
                JitterPreset nextPreset = jitterPresets[nextJitterPreset];

                float blend = 1F - (nextJitterSwitch - currentTime) / (float) jitterSwitchTime;
                blend = MathHelper.clamp(blend, 0F, 1F);
                blend = blend * blend * (3F - 2F * blend);

                blend = blend * blend * (3F - 2F * blend);

                float currentYaw = currentPreset.getYaw(currentTime);
                float nextYaw = nextPreset.getYaw(currentTime);

                float currentPitch = currentPreset.getPitch(currentTime);
                float nextPitch = nextPreset.getPitch(currentTime);

                float yawJitter = MathHelper.lerp(blend, currentYaw, nextYaw);
                float pitchJitter = MathHelper.lerp(blend, currentPitch, nextPitch);

                lastYawJitter += (yawJitter - lastYawJitter) * 0.12F;
                lastPitchJitter += (pitchJitter - lastPitchJitter) * 0.12F;

                yawJitter = lastYawJitter * jitterIntensity;
                pitchJitter = lastPitchJitter *jitterIntensity;

                Rotation targetRotation = new Rotation(   AttackAura.lastYaw + yawJitter, AttackAura.lastPitch  + pitchJitter);
                Rotation currentRotation = new Rotation(mc.player);

                float pitchDelta = targetRotation.pitch - currentRotation.pitch;
                float yawDelta = wrapDegrees(targetRotation.yaw - currentRotation.yaw);

                if (!isActive && jitterIntensity <= 0.0f && Math.abs(yawDelta) < 0.5f && Math.abs(pitchDelta) < 0.5f) {
                    FreeLookUtil.active = false;

                    return;
                }


                float clampedYaw = Math.min(Math.abs(yawDelta), activeSpeedYaw);
                float clampedPitch = Math.min(Math.abs(pitchDelta), activeSpeedPitch);


                mc.player.setYaw(
                        mc.player.headYaw += GCDUtil.getSensitivity(MathHelper.clamp(yawDelta, -clampedYaw, clampedYaw)));

                mc.player.setPitch(MathHelper.clamp(
                        mc.player.getPitch()
                                + GCDUtil.getSensitivity(MathHelper.clamp(pitchDelta, -clampedPitch, clampedPitch)),
                        -90F, 90F));
            }
        }
       if (AttackAura.get().typeRotation.is("FunTime")) {
            if (mc.player != null && mc.world != null) {

                LivingEntity target = AttackAura.target;

                boolean isActive = AttackAura.get().isEnabled() && target != null ;

                float rawYaw;
                float rawPitch;

                long speedTime = System.currentTimeMillis();

                if (speedTime >= nextSpeedSwitch) {
                    currentSpeedPreset = nextSpeedPreset;
                    nextSpeedPreset = ThreadLocalRandom.current().nextInt(speedPresets.length);

                    while (nextSpeedPreset == currentSpeedPreset)
                        nextSpeedPreset = ThreadLocalRandom.current().nextInt(speedPresets.length);

                    speedSwitchTime = ThreadLocalRandom.current().nextLong(2000L, 4001L);
                    nextSpeedSwitch = speedTime + speedSwitchTime;
                }

                SpeedPreset currentSpeed = speedPresets[currentSpeedPreset];
                SpeedPreset nextSpeed = speedPresets[nextSpeedPreset];

                float speedBlend = 1F - (nextSpeedSwitch - speedTime) / (float) speedSwitchTime;
                speedBlend = MathHelper.clamp(speedBlend, 0F, 1F);
                speedBlend = speedBlend * speedBlend * (3F - 2F * speedBlend);


                speedBlend = speedBlend * speedBlend * (3F - 2F * speedBlend);

                float targetYawSpeed = MathHelper.lerp(
                        speedBlend,
                        currentSpeed.getYawSpeed(),
                        nextSpeed.getYawSpeed()
                );

                float targetPitchSpeed = MathHelper.lerp(
                        speedBlend,
                        currentSpeed.getPitchSpeed(),
                        nextSpeed.getPitchSpeed()
                );


                float activeSpeedYaw = MathUtil.randomLerp(30,35);
                float activeSpeedPitch = MathUtil.randomLerp(8,14);


                lastYawSpeed += (targetYawSpeed - lastYawSpeed) * 0.5F;
                lastPitchSpeed += (targetPitchSpeed - lastPitchSpeed) * 0.5F;

                float speed = lastYawSpeed + MathUtil.randomLerp(-2.5F, 2.5F);
                float speed2 = lastPitchSpeed + MathUtil.randomLerp(-1.0F, 1.0F);

                activeSpeedYaw = speed;
                activeSpeedPitch = speed2;

                AttackAura aura = AttackAura.get();





                if (isActive) {
                    FreeLookUtil.active = true;

                    jitterIntensity = Math.min(1.0f, jitterIntensity + 0.1f);
                } else {

                    jitterIntensity = Math.max(0.0f, jitterIntensity - 0.05f);
                }


                if (aura.pitchFlickActive) {
                    if (System.currentTimeMillis() > aura.pitchFlickEndTime) {
                        aura.pitchFlickActive = false;
                    } else {
                        AttackAura.lastYaw += MathUtil.random(-15, 15);
                        AttackAura.lastPitch = -MathUtil.random(85, 90);
                    }
                }




                if (aura.justAttacked && System.currentTimeMillis() >= aura.attackFlickAt) {
                    aura.justAttacked = false;
                }

                if (isActive) {

                    activeSpeedYaw *= 1.8f;
                    activeSpeedPitch *= 1.6F;

                    Vec3d vec = target.getEntityPos().add(0.15F * Math.sin(System.currentTimeMillis() / 250D),target.getHeight() / 2 + (target.getHeight() /  4) *
                                    Math.cos(System.currentTimeMillis() / 200D),
                                    0.15F * Math.cos(System.currentTimeMillis() / 250D))
                            .subtract(mc.player.getEyePos())
                            .normalize();

                    rawYaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
                    rawPitch = (float) MathHelper.clamp(
                            -Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))),
                            -90F, 90F
                    );


                    float[] ranges = aura.getRanges();
                    ranges = new float[]{ranges[0], ranges[1], ranges[0] + ranges[1]};
                    boolean canAttack = UAttack.shouldAttack(target, false, true, true, (long) -MathUtil.random(100,200), ranges) && !aura.pitchFlickActive;

                    if(canAttack) {
                        if (!aura.pitchFlickActive) AttackAura.lastPitch = rawPitch;
                        if (!aura.pitchFlickActive) AttackAura.lastYaw = rawYaw;
                    }


                } else {



                    AttackAura.lastYaw = FreeLookUtil.freeYaw;
                    AttackAura.lastPitch = FreeLookUtil.freePitch;
                }


                long currentTime = System.currentTimeMillis();
                int presetIndex = (int) ((currentTime / JITTER_SWITCH_TIME) % jitterPresets.length);

                if (currentTime >= nextJitterSwitch) {
                    currentJitterPreset = nextJitterPreset;
                    nextJitterPreset = ThreadLocalRandom.current().nextInt(jitterPresets.length);

                    while (nextJitterPreset == currentJitterPreset)
                        nextJitterPreset = ThreadLocalRandom.current().nextInt(jitterPresets.length);

                    jitterSwitchTime = ThreadLocalRandom.current().nextLong(1000L, 2001L);
                    nextJitterSwitch = currentTime + jitterSwitchTime;
                }

                JitterPreset currentPreset = jitterPresets[currentJitterPreset];
                JitterPreset nextPreset = jitterPresets[nextJitterPreset];

                float blend = 1F - (nextJitterSwitch - currentTime) / (float) jitterSwitchTime;
                blend = MathHelper.clamp(blend, 0F, 1F);
                blend = blend * blend * (3F - 2F * blend);

                blend = blend * blend * (3F - 2F * blend);

                float currentYaw = currentPreset.getYaw(currentTime);
                float nextYaw = nextPreset.getYaw(currentTime);

                float currentPitch = currentPreset.getPitch(currentTime);
                float nextPitch = nextPreset.getPitch(currentTime);

                float yawJitter = MathHelper.lerp(blend, currentYaw, nextYaw);
                float pitchJitter = MathHelper.lerp(blend, currentPitch, nextPitch);

                lastYawJitter += (yawJitter - lastYawJitter) * 0.9F;
                lastPitchJitter += (pitchJitter - lastPitchJitter) * 0.9F;

                float waveA = (float) Math.cos(System.currentTimeMillis() / 40D);
                float waveB = (float) Math.sin(System.currentTimeMillis() / 70D);

                float yawJitter2 = waveA * MathUtil.randomLerp(9, 17);
                float pitchJitter2 = waveB * MathUtil.randomLerp(4, 13);

                yawJitter = yawJitter2 * jitterIntensity;
                pitchJitter = pitchJitter2 *jitterIntensity;

                Rotation targetRotation = new Rotation(   AttackAura.lastYaw + yawJitter, AttackAura.lastPitch  + pitchJitter);
                Rotation currentRotation = new Rotation(mc.player);

                float pitchDelta = targetRotation.pitch - currentRotation.pitch;
                float yawDelta = wrapDegrees(targetRotation.yaw - currentRotation.yaw);

                if (!isActive && jitterIntensity <= 0.0f && Math.abs(yawDelta) < 0.5f && Math.abs(pitchDelta) < 0.5f) {
                    FreeLookUtil.active = false;

                    return;
                }


                float clampedYaw = Math.min(Math.abs(yawDelta), activeSpeedYaw);
                float clampedPitch = Math.min(Math.abs(pitchDelta), activeSpeedPitch);


                mc.player.setYaw(
                        mc.player.headYaw += GCDUtil.getSensitivity(MathHelper.clamp(yawDelta, -clampedYaw, clampedYaw)));

                mc.player.setPitch(MathHelper.clamp(
                        mc.player.getPitch()
                                + GCDUtil.getSensitivity(MathHelper.clamp(pitchDelta, -clampedPitch, clampedPitch)),
                        -90F, 90F));


            }
        }
    } */
    private void renderCorner(MatrixStack matrices,
                              VertexConsumer consumer,
                              float x,
                              float y,
                              float rotation,
                              float scale,
                              int c1,
                              int c2,
                              int c3,
                              int c4,
                              float alpha) {

        matrices.push();

        matrices.translate(x, y, 0);

        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));

        matrices.scale(scale * 0.6F , scale * 0.6F , 1F);

        Matrix4f mat = matrices.peek().getPositionMatrix();

        drawGradientQuad(
                consumer,
                mat,
                c1,
                c2,
                c3,
                c4,
                (int)(255 * alpha)
        );

        matrices.pop();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEventMovement(EventRender3D e) {
        AttackAura aura = AttackAura.get();

        alpha.update();

        LivingEntity currentTarget = AttackAura.target;

        if (currentTarget != null) {
            target = currentTarget;
        }

        if (mc.world == null || mc.player == null) return;

        alpha.run(currentTarget != null ? 1 : 0, 0.2F, Easings.SINE_OUT);
        float alphaPC = alpha.get();



        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(boxAllocator);
        if (alphaPC > 0.001f && target != null && aura.typeTargetESP.is("Картинка")) {

            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));

            alpha_2.update();
            alpha_2.run(hurtPC,0.12F,Easings.SINE_OUT);

            int redColor = ColorUtil.getColor(185, 80, 80, (int) (255.0F * alphaPC));
            int color = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(0), alphaPC), redColor, alpha_2.get());
            int color2 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(90), alphaPC), redColor, alpha_2.get());
            int color3 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(180), alphaPC), redColor, alpha_2.get());
            int color4 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(360), alphaPC), redColor,alpha_2.get());



            MatrixStack matrices = e.getMatrixStack();

            VertexConsumer consumer = immediate
                    .getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/union.png")));


            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            double x = lerpedPos.x;
            double y = lerpedPos.y;
            double z = lerpedPos.z;

            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            matrices.push();
            matrices.translate(x - cameraPos.x, y - cameraPos.y + target.getHeight() / 1.75F, z - cameraPos.z);

            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));

            long currentTimeMillis = System.currentTimeMillis();
            float rotate = (float) MathUtil.clamps(0, 360 * 2,
                    ((Math.sin(currentTimeMillis / (1000D)) + 1F) / 2F) * 360 * 2);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotate));



            Matrix4f bloomMatrix = matrices.peek().getPositionMatrix();
            float size = (0.7F );
            float offset = 0.3F * size;

            float hitAnim = alpha_2.get();

            long time = System.currentTimeMillis();
            float rotation = 0;

            float sizeA = 0.1F;

            float xan = 0.2F - 0.2F * alphaPC;


            renderCorner(matrices, consumer, offset + sizeA * hitAnim + xan, offset + sizeA * hitAnim + xan, 135, size, color, color2, color3, color4, alphaPC);      // ↖
            renderCorner(matrices, consumer, -offset - sizeA * hitAnim - xan, offset + sizeA * hitAnim + xan, -135   , size, color, color2, color3, color4, alphaPC); // ↗
            renderCorner(matrices, consumer, -offset - sizeA * hitAnim - xan, -offset - sizeA * hitAnim - xan, -45 , size, color, color2, color3, color4, alphaPC);// ↘
            renderCorner(matrices, consumer, offset + sizeA * hitAnim + xan, -offset - sizeA * hitAnim - xan, 45 , size, color, color2, color3, color4, alphaPC); // ↙






            matrices.pop();
        }



        if (alphaPC > 0.001f && target != null && aura.typeTargetESP.is("Кольцо")) {
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 10.0));
            int redColor = ColorUtil.getColor(255, 100, 100, (int)(255.0F * alphaPC));

            double duration = 1200;
            double elapsed  = System.currentTimeMillis() % duration;
            boolean side    = elapsed > duration / 2.0;
            double raw      = elapsed / (duration / 2.0);
            raw = side ? (raw - 1.0) : 1.0 - raw;
            double progress = raw < 0.5
                    ? 2.0 * raw * raw
                    : 1.0 - Math.pow(-2.0 * raw + 2.0, 2.0) / 2.0;

            float height2 = target.getHeight() ;
            double eased  = (height2 / 1.7) * (progress > 0.5 ? 1.0 - progress : progress) * (side ? -1 : 1) /0.7;

            MatrixStack matrices = e.getMatrixStack();
            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            matrices.push();
            matrices.translate(lerpedPos.x - cameraPos.x, lerpedPos.y - cameraPos.y, lerpedPos.z - cameraPos.z);
            Matrix4f matrix = matrices.peek().getPositionMatrix();

            float radius = (target.getWidth() - 0.1F) + 0.35F - 0.35F * alphaPC;
            float yBase  = (float)(height2 * progress);
            float yTop   = (float)(height2 * progress + eased);


            VertexConsumer fillBuf = immediate.getBuffer(RING_FILL_LAYER);
            for (int seg = 0; seg < 360; seg++) {
                float a0 = (float) Math.toRadians(seg);
                float a1 = (float) Math.toRadians(seg + 1);
                int c0 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(seg ), alphaPC), redColor, hurtPC);
                int c1 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade((seg  )), alphaPC), redColor, hurtPC);
                float x0 = (float)(Math.cos(a0) * radius), z0 = (float)(Math.sin(a0) * radius);
                float x1 = (float)(Math.cos(a1) * radius), z1 = (float)(Math.sin(a1) * radius);
                fillBuf.vertex(matrix, x0, yBase, z0).color(ColorUtil.replAlpha(c0, (int)(120 * alphaPC)));
                fillBuf.vertex(matrix, x1, yBase, z1).color(ColorUtil.replAlpha(c1, (int)(120 * alphaPC)));
                fillBuf.vertex(matrix, x1, yTop,  z1).color(ColorUtil.replAlpha(c1, 0));
                fillBuf.vertex(matrix, x0, yTop,  z0).color(ColorUtil.replAlpha(c0, 0));
            }

            VertexConsumer lineBuf = immediate.getBuffer(RING_LINE_LAYER);
            for (int seg = 0; seg < 360; seg++) {
                float a0 = (float) Math.toRadians(seg);
                float a1 = (float) Math.toRadians(seg + 1);
                int c =ColorUtil.multAlpha(ColorUtil.overCol(ColorUtil.multBright(ColorUtil.fade(1),0.7F),redColor,hurtPC), alphaPC);
                lineBuf.vertex(matrix, (float)(Math.cos(a0) * radius), yBase, (float)(Math.sin(a0) * radius)).color(ColorUtil.replAlpha(c, (int)(150 * alphaPC)));
                lineBuf.vertex(matrix, (float)(Math.cos(a1) * radius), yBase, (float)(Math.sin(a1) * radius)).color(ColorUtil.replAlpha(c, (int)(150 * alphaPC)));
            }

            matrices.pop();
        }
        if (alphaPC > 0.001f && target != null && aura.typeTargetESP.is("Духи")) {


            long currentTime = System.currentTimeMillis();
            if (currentTimeSpirits == 0) {
                currentTimeSpirits = currentTime;
            }

            long timeDiff = currentTime - currentTimeSpirits;
            if (timeDiff > 0) {
                animationNurik += (float) (5L * timeDiff) /  700;
            }
            currentTimeSpirits = currentTime;

            MatrixStack matrices = e.getMatrixStack();


            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            double x = lerpedPos.x - cameraPos.x;
            double y = lerpedPos.y  - cameraPos.y;
            double z = lerpedPos.z - cameraPos.z;

            alphaPC = (float) alpha.getValue();

            alpha_2.update();
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));
            alpha_2.run(hurtPC,0.1F,Easings.SINE_OUT);

            float atts = alpha_2.get();

            int fadeColor = ColorUtil.fade(1);
            int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * alphaPC));
            int baseColor = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC), redColor, atts);


            int n2 = 3;
            int n3 = 12;
            int n4 = 3 * n2;

            matrices.push();

            Camera camera = mc.gameRenderer.getCamera();

            for (int i = 0; i < n4; i += n2) {
                for (int j = 0; j < n3; j++) {
                    float f2 = animationNurik + (float) j * 0.1F;
                    float f3 = 0.6F;
                    float f4 = 0.4F;
                    int n5 = (int) Math.pow((double) i, 2.0F);

                    matrices.push();

                    double particleX = x + (double) (f3 * Math.sin(f2 + (float) n5));
                    double particleY = y + (double) f4 + (double) (0.3F * Math.sin(animationNurik + (float) j * 0.2F))
                            + (double) (0.2F * (float) i);
                    double particleZ = z + (double) (f3 * Math.cos(f2 - (float) n5));

                    matrices.translate(particleX, particleY, particleZ);

                    float scale =  (0.005F + (float) j / 2000.0F ) * alphaPC;
                    matrices.scale(scale, scale, scale);

                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

                    Matrix4f matrix = matrices.peek().getPositionMatrix();
                    VertexConsumer consumer = immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_3.png")));

                    int color = baseColor;


                    int n7 = -20;
                    int n8 = 35;

                    consumer.vertex(matrix, (float) n7, (float) (n7 + n8), 0.0f)
                            .color(baseColor)
                            .texture(0.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) (n7 + n8), 0.0f)
                            .color(baseColor)
                            .texture(1.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) n7, 0.0f)
                            .color(baseColor)
                            .texture(1.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) n7, (float) n7, 0.0f)
                            .color(baseColor)
                            .texture(0.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);



                    n7 = (int) (-20  - 20 * 1.5F);
                    n8 = (int) (35 + 40 *1.5F);

                    consumer.vertex(matrix, (float) n7, (float) (n7 + n8), 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC*0.1F))
                            .texture(0.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) (n7 + n8), 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC *0.1F))
                            .texture(1.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) n7, 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC *0.1F))
                            .texture(1.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) n7, (float) n7, 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC *0.1F))
                            .texture(0.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    matrices.pop();
                }
            }

            matrices.pop();

        }

        if (alphaPC > 0.001f && target != null && aura.typeTargetESP.is("Фантомы")) {
            renderTargetPhantoms(e, immediate, aura, target, alphaPC);
        }

        if (alphaPC > 0.001f && target != null && aura.typeTargetESP.is("Души")) {
            renderTargetSouls(e, immediate, aura, target, alphaPC);
        }

        if (alphaPC > 0.001f && target != null && aura.typeTargetESP.is("Астрал")) {
            renderTargetAstral(e, immediate, aura, target, alphaPC);
        }

        if (alphaPC > 0.001f && target != null && aura.typeTargetESP.is("Кубики")) {
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 10.0));

            alpha_2.update();
            alpha_2.run(hurtPC,0.1F,Easings.SINE_OUT);

            int redColor = ColorUtil.getColor(255, 100, 100, (int)(255.0f * alphaPC));
            int color = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(1), alphaPC), redColor, alpha_2.get());

            MatrixStack matrices = e.getMatrixStack();
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
            Vec3d targetPos = target.getLerpedPos(e.getTickDelta());

            long speed_f = 40 ;

            long currentTime = System.currentTimeMillis();
            if (currentTimeSpirits == 0) {
                currentTimeSpirits = currentTime;
            }

            long timeDiff = currentTime - currentTimeSpirits;
            if (timeDiff > 0) {
                animationNurik += (float) (5L * timeDiff) / speed_f;
            }
            currentTimeSpirits = currentTime;

            float time =animationNurik - 12 * alpha_2.get();
            int cound = 12;
            float width = target.getWidth() * 1.5f ;
            float sizeFI = (1f - 0.3F * alpha_2.get()) * alphaPC;

            Camera camera = mc.gameRenderer.getCamera();

            for (int i = 0; i < 360; i += cound) {
                float val = 1.2f - 0.5f ;
                float sin = (float)(Math.sin((float) Math.toRadians(i + time)) * width * val);
                float cos = (float)(Math.cos((float) Math.toRadians(i + time)) * width * val);

                double x = targetPos.x + sin;
                double z = targetPos.z + cos;
                double y = targetPos.y + target.getHeight() * Math.abs(MathUtil.sin(i));

                matrices.push();
                matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
                matrices.multiply(camera.getRotation());
                float gs = 0.6f * sizeFI;
                matrices.scale(gs, gs, gs);
                drawGradientQuad(immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_1.png"))),
                        matrices.peek().getPositionMatrix(),
                        ColorUtil.multAlpha(color, 0.3f), ColorUtil.multAlpha(color, 0.3f),
                        ColorUtil.multAlpha(color, 0.3f), ColorUtil.multAlpha(color, 0.3f),
                        (int)(alphaPC * 0.35f * 255));
                matrices.pop();
            }

            // Pass 2: cube fills
            for (int i = 0; i < 360; i += cound) {
                float val = 1.2f - 0.5f ;
                float sin = (float)(Math.sin((float) Math.toRadians(i + time)) * width * val);
                float cos = (float)(Math.cos((float) Math.toRadians(i + time)) * width * val);

                double x = targetPos.x + sin;
                double z = targetPos.z + cos;
                double y = targetPos.y + target.getHeight() * Math.abs(MathUtil.sin(i));

                Vec3d cubePos = new Vec3d(x, y, z);
                Vector3f directionToTarget = new Vector3f(
                        (float)(targetPos.x - cubePos.x),
                        (float)(targetPos.y - cubePos.y),
                        (float)(targetPos.z - cubePos.z)
                ).normalize();

                matrices.push();
                matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
                matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), directionToTarget));
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                float size = 0.06f * sizeFI;
                drawCubeFillTESP(immediate.getBuffer(RING_FILL_LAYER), matrix, size,
                        ColorUtil.replAlpha(color, (int)(alphaPC * 0.2f * 255)));
                matrices.pop();
            }

            // Pass 3: cube outlines
            for (int i = 0; i < 360; i += cound) {
                float val = 1.2f - 0.5f ;
                float sin = (float)(Math.sin((float) Math.toRadians(i + time)) * width * val);
                float cos = (float)(Math.cos((float) Math.toRadians(i + time)) * width * val);

                double x = targetPos.x + sin;
                double z = targetPos.z + cos;
                double y = targetPos.y + target.getHeight() * Math.abs(MathUtil.sin(i));

                Vec3d cubePos = new Vec3d(x, y, z);
                Vector3f directionToTarget = new Vector3f(
                        (float)(targetPos.x - cubePos.x),
                        (float)(targetPos.y - cubePos.y),
                        (float)(targetPos.z - cubePos.z)
                ).normalize();

                matrices.push();
                matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
                matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), directionToTarget));
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                float size = 0.06f * sizeFI;
                drawCubeOutlineTESP(immediate.getBuffer(RING_LINE_LAYER), matrix, size,
                        ColorUtil.replAlpha(color, (int)(alphaPC * 255)));
                matrices.pop();
            }
        }

        if (aura.typeTargetESP.is("Куб")) {
            // осколки переживают смерть цели, поэтому рисуются вне проверки на таргет
            aura.renderTargetCubeFragments(e, immediate);

            if (alphaPC > 0.001f && target != null) {
                aura.renderTargetCube(e, target, alphaPC, immediate);
            }
        }

        if (alphaPC > 0.001f && target != null && aura.typeTargetESP.is("Молнии")) {
            lightningRenderer.maxBolts = aura.lightningCount.getValue().intValue();
            lightningRenderer.spawnIntervalMs = Math.max(10L, 132L - aura.lightningSpeed.getValue().longValue());
            lightningRenderer.redOnHit = aura.lightningHit.getValue();
            lightningRenderer.render(e, immediate, target, alphaPC);
        }

        if (alphaPC > 0.001f && target != null && aura.typeTargetESP.is("Кристаллы")) {
            renderTargetCrystals(e, immediate, aura, target, alphaPC);
        }

        if (alphaPC > 0.001f && target != null && aura.typeTargetESP.is("Пентаграмма")) {
            renderTargetPentagram(e, immediate, aura, target, alphaPC);
        }

        if (alphaPC > 0.001f && target != null && aura.typeTargetESP.is("Снег")) {
            renderTargetSnow(e, immediate, aura, target, alphaPC);
        }

        if (alphaPC > 0.001f && target != null && aura.typeTargetESP.is("Сердце")) {
            renderTargetHeart(e, immediate, aura, target, alphaPC);
        }

        if (alphaPC > 0.001f && target != null && aura.typeTargetESP.is("Огонь")) {
            renderTargetFire(e, immediate, aura, target, alphaPC);
        }

        if (alphaPC > 0.001f && target != null && aura.typeTargetESP.is("Мечи")) {
            renderTargetSwords(e, immediate, aura, target, alphaPC);
        }

        if (alphaPC > 0.001f && target != null && aura.typeTargetESP.is("Цепь")) {
            renderTargetChainRing(e, immediate, aura, target, alphaPC);
        }


        immediate.draw();

    }

    private static void drawCubeFillTESP(VertexConsumer buf, Matrix4f m, float s, int color) {
        // +Y
        buf.vertex(m, -s,  s, -s).color(color); buf.vertex(m,  s,  s, -s).color(color);
        buf.vertex(m,  s,  s,  s).color(color); buf.vertex(m, -s,  s,  s).color(color);
        // -Y
        buf.vertex(m, -s, -s,  s).color(color); buf.vertex(m,  s, -s,  s).color(color);
        buf.vertex(m,  s, -s, -s).color(color); buf.vertex(m, -s, -s, -s).color(color);
        // +X
        buf.vertex(m,  s, -s, -s).color(color); buf.vertex(m,  s, -s,  s).color(color);
        buf.vertex(m,  s,  s,  s).color(color); buf.vertex(m,  s,  s, -s).color(color);
        // -X
        buf.vertex(m, -s, -s,  s).color(color); buf.vertex(m, -s, -s, -s).color(color);
        buf.vertex(m, -s,  s, -s).color(color); buf.vertex(m, -s,  s,  s).color(color);
        // +Z
        buf.vertex(m, -s, -s,  s).color(color); buf.vertex(m,  s, -s,  s).color(color);
        buf.vertex(m,  s,  s,  s).color(color); buf.vertex(m, -s,  s,  s).color(color);
        // -Z
        buf.vertex(m,  s, -s, -s).color(color); buf.vertex(m, -s, -s, -s).color(color);
        buf.vertex(m, -s,  s, -s).color(color); buf.vertex(m,  s,  s, -s).color(color);
    }

    private static void drawCubeOutlineTESP(VertexConsumer buf, Matrix4f m, float s, int color) {
        // bottom ring
        buf.vertex(m, -s, -s, -s).color(color); buf.vertex(m,  s, -s, -s).color(color);
        buf.vertex(m,  s, -s, -s).color(color); buf.vertex(m,  s, -s,  s).color(color);
        buf.vertex(m,  s, -s,  s).color(color); buf.vertex(m, -s, -s,  s).color(color);
        buf.vertex(m, -s, -s,  s).color(color); buf.vertex(m, -s, -s, -s).color(color);
        // top ring
        buf.vertex(m, -s,  s, -s).color(color); buf.vertex(m,  s,  s, -s).color(color);
        buf.vertex(m,  s,  s, -s).color(color); buf.vertex(m,  s,  s,  s).color(color);
        buf.vertex(m,  s,  s,  s).color(color); buf.vertex(m, -s,  s,  s).color(color);
        buf.vertex(m, -s,  s,  s).color(color); buf.vertex(m, -s,  s, -s).color(color);
        // verticals
        buf.vertex(m, -s, -s, -s).color(color); buf.vertex(m, -s,  s, -s).color(color);
        buf.vertex(m,  s, -s, -s).color(color); buf.vertex(m,  s,  s, -s).color(color);
        buf.vertex(m,  s, -s,  s).color(color); buf.vertex(m,  s,  s,  s).color(color);
        buf.vertex(m, -s, -s,  s).color(color); buf.vertex(m, -s,  s,  s).color(color);
    }

    /** Рой кристаллов-октаэдров, вращающихся вокруг таргета. */
    private void renderTargetCrystals(EventRender3D e, VertexConsumerProvider.Immediate immediate,
                                      AttackAura aura, LivingEntity target, float alphaPC) {
        int hurtTicks = target.hurtTime;
        float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 10.0));

        alpha_2.update();
        alpha_2.run(hurtPC, 0.1F, Easings.SINE_OUT);

        int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0f * alphaPC));
        int color = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(1), alphaPC), redColor, alpha_2.get());

        long currentTime = System.currentTimeMillis();
        if (currentTimeSpirits == 0) currentTimeSpirits = currentTime;
        long timeDiff = currentTime - currentTimeSpirits;
        if (timeDiff > 0) animationNurik += timeDiff / 16.666F;
        currentTimeSpirits = currentTime;

        float count = Math.max(1, aura.crystalCount.getValue().intValue());
        float orbitSpeed = aura.crystalSpeed.getValue();
        float radius = aura.crystalRadius.getValue() + target.getWidth() * 0.3f + 0.15f;
        float size = aura.crystalSize.getValue();

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d targetPos = target.getLerpedPos(e.getTickDelta());

        float centerY = target.getHeight() * 0.55f;
        float heightSpread = target.getHeight() * 0.28f;

        int glowColor = ColorUtil.multAlpha(color, 0.3f);
        int fillTop = ColorUtil.replAlpha(color, (int) (alphaPC * 80));
        int fillBottom = ColorUtil.replAlpha(color, (int) (alphaPC * 40));
        int lineColor = ColorUtil.replAlpha(color, (int) (alphaPC * 235));

        for (int pass = 0; pass < 3; pass++) {
            VertexConsumer buf = switch (pass) {
                case 0 -> immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_1.png")));
                case 1 -> immediate.getBuffer(RING_FILL_LAYER);
                default -> immediate.getBuffer(RING_LINE_LAYER);
            };

            for (int i = 0; i < (int) count; i++) {
                float baseAngle = i * (360f / count) + animationNurik * 2.0f * orbitSpeed;

                float sin = (float) Math.sin(Math.toRadians(baseAngle));
                float cos = (float) Math.cos(Math.toRadians(baseAngle));
                double x = targetPos.x + cos * radius;
                double z = targetPos.z + sin * radius;
                double y = targetPos.y + centerY
                        + Math.sin(Math.toRadians(baseAngle * 3.0f + i * 53.0f)) * heightSpread;

                matrices.push();
                matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);

                if (pass == 0) {
                    matrices.multiply(mc.gameRenderer.getCamera().getRotation());
                    float gs = size * 5.0f;
                    matrices.scale(gs, gs, gs);
                    drawGradientQuad(buf, matrices.peek().getPositionMatrix(),
                            glowColor, glowColor, glowColor, glowColor,
                            (int) (alphaPC * 0.35f * 255));
                } else {
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(animationNurik * 4.0f * orbitSpeed + i * 37.0f));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(24.0f
                            + 8.0f * (float) Math.sin(Math.toRadians(animationNurik * 1.7f + i * 29.0f))));
                    Matrix4f matrix = matrices.peek().getPositionMatrix();

                    if (pass == 1) {
                        drawCrystalFill(buf, matrix, size, size * 1.7f, fillTop, fillBottom);
                    } else {
                        drawCrystalOutline(buf, matrix, size, size * 1.7f, lineColor);
                    }
                }

                matrices.pop();
            }
        }
    }

    /** Октаэдр: 8 треугольных граней (в QUADS дублируем последнюю вершину). */
    private static void drawCrystalFill(VertexConsumer buf, Matrix4f m, float r, float h, int topColor, int bottomColor) {
        float ax = r, az = 0;
        float bx = 0, bz = r;
        float cx = -r, cz = 0;
        float dx = 0, dz = -r;

        buf.vertex(m, 0, h, 0).color(topColor);  buf.vertex(m, ax, 0, az).color(topColor);
        buf.vertex(m, bx, 0, bz).color(topColor); buf.vertex(m, bx, 0, bz).color(topColor);

        buf.vertex(m, 0, h, 0).color(topColor);  buf.vertex(m, bx, 0, bz).color(topColor);
        buf.vertex(m, cx, 0, cz).color(topColor); buf.vertex(m, cx, 0, cz).color(topColor);

        buf.vertex(m, 0, h, 0).color(topColor);  buf.vertex(m, cx, 0, cz).color(topColor);
        buf.vertex(m, dx, 0, dz).color(topColor); buf.vertex(m, dx, 0, dz).color(topColor);

        buf.vertex(m, 0, h, 0).color(topColor);  buf.vertex(m, dx, 0, dz).color(topColor);
        buf.vertex(m, ax, 0, az).color(topColor); buf.vertex(m, ax, 0, az).color(topColor);

        buf.vertex(m, 0, -h, 0).color(bottomColor); buf.vertex(m, bx, 0, bz).color(bottomColor);
        buf.vertex(m, ax, 0, az).color(bottomColor); buf.vertex(m, ax, 0, az).color(bottomColor);

        buf.vertex(m, 0, -h, 0).color(bottomColor); buf.vertex(m, cx, 0, cz).color(bottomColor);
        buf.vertex(m, bx, 0, bz).color(bottomColor); buf.vertex(m, bx, 0, bz).color(bottomColor);

        buf.vertex(m, 0, -h, 0).color(bottomColor); buf.vertex(m, dx, 0, dz).color(bottomColor);
        buf.vertex(m, cx, 0, cz).color(bottomColor); buf.vertex(m, cx, 0, cz).color(bottomColor);

        buf.vertex(m, 0, -h, 0).color(bottomColor); buf.vertex(m, ax, 0, az).color(bottomColor);
        buf.vertex(m, dx, 0, dz).color(bottomColor); buf.vertex(m, dx, 0, dz).color(bottomColor);
    }

    /** Рёбра октаэдра для DEBUG_LINES: парами вершин. */
    private static void drawCrystalOutline(VertexConsumer buf, Matrix4f m, float r, float h, int color) {
        float[][] eq = {{r, 0}, {0, r}, {-r, 0}, {0, -r}};
        for (int k = 0; k < 4; k++) {
            float[] cur = eq[k];
            float[] next = eq[(k + 1) % 4];

            buf.vertex(m, 0, h, 0).color(color);
            buf.vertex(m, cur[0], 0, cur[1]).color(color);

            buf.vertex(m, 0, -h, 0).color(color);
            buf.vertex(m, cur[0], 0, cur[1]).color(color);

            buf.vertex(m, cur[0], 0, cur[1]).color(color);
            buf.vertex(m, next[0], 0, next[1]).color(color);
        }
    }

    /** «Фантомы»: маленькие призрачки с глазками кружат вокруг корпуса цели. */
    private void renderTargetPhantoms(EventRender3D e, VertexConsumerProvider.Immediate immediate,
                                      AttackAura aura, LivingEntity target, float alphaPC) {
        float t = System.currentTimeMillis() / 1000.0F;

        alpha_2.update();
        float hurtPC = (float) Math.sin(target.hurtTime * (Math.PI / 10.0));
        alpha_2.run(hurtPC, 0.1F, Easings.SINE_OUT);

        int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0f * alphaPC));
        float atts = alpha_2.get();

        int count = Math.max(3, aura.phantomCount.getValue().intValue());
        float speed = aura.phantomSpeed.getValue();

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d targetPos = target.getLerpedPos(e.getTickDelta());
        float bodyH = target.getHeight();
        Camera camera = mc.gameRenderer.getCamera();

        VertexConsumer texBuf = immediate.getBuffer(
                ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_1.png")));
        VertexConsumer fillBuf = immediate.getBuffer(RING_FILL_LAYER);
        VertexConsumer lineBuf = immediate.getBuffer(RING_LINE_LAYER);

        for (int i = 0; i < count; i++) {
            float ang = t * 55F * speed + i * (360F / count);
            double rad = Math.toRadians(ang);
            float orbit = 0.55F + 0.12F * (float) Math.sin(t * 0.9 + i * 1.9);
            float bob = 0.09F * (float) Math.sin(t * 2.2 + i * 1.7);
            float gx = targetPos.x - cameraPos.x + (float) Math.cos(rad) * orbit;
            float gz = targetPos.z - cameraPos.z + (float) Math.sin(rad) * orbit;
            float gy = targetPos.y - cameraPos.y
                    + bodyH * (0.52F + 0.05F * (float) Math.sin(t * 1.3 + i * 2.3)) + bob;

            float pulse = 0.62F + 0.38F * (float) Math.sin(t * 2.6 + i * 2.1);
            float gAlpha = alphaPC * pulse;

            int bodyCol = ColorUtil.replAlpha(
                    ColorUtil.overCol(ColorUtil.getColor(205, 240, 255), redColor, atts),
                    (int) (gAlpha * 120));
            int eyeCol = ColorUtil.replAlpha(ColorUtil.getColor(35, 55, 95), (int) (gAlpha * 190));
            int coreCol = ColorUtil.replAlpha(
                    ColorUtil.overCol(ColorUtil.getColor(235, 250, 255), redColor, atts),
                    (int) (gAlpha * 200));
            int glowCol = ColorUtil.replAlpha(
                    ColorUtil.overCol(ColorUtil.getColor(150, 215, 255), redColor, atts),
                    (int) (gAlpha * 70));

            matrices.push();
            matrices.translate(gx, gy, gz);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

            float gs = 0.42F * pulse + 0.10F;
            matrices.push();
            matrices.scale(gs, gs, gs);
            drawTexQuad(texBuf, matrices.peek().getPositionMatrix(), 1.0F, glowCol);
            matrices.pop();

            Matrix4f m = matrices.peek().getPositionMatrix();
            ghostSilhouette(fillBuf, m, 0.16F, bodyCol, eyeCol);
            ghostOutline(lineBuf, m, 0.16F, coreCol);

            matrices.pop();
        }
    }

    /** «Души»: потоки холодного посмертного пламени поднимаются вокруг цели. */
    private void renderTargetSouls(EventRender3D e, VertexConsumerProvider.Immediate immediate,
                                   AttackAura aura, LivingEntity target, float alphaPC) {
        float t = System.currentTimeMillis() / 1000.0F;

        alpha_2.update();
        float hurtPC = (float) Math.sin(target.hurtTime * (Math.PI / 10.0));
        alpha_2.run(hurtPC, 0.1F, Easings.SINE_OUT);

        int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0f * alphaPC));
        float atts = alpha_2.get();

        int streams = Math.max(2, aura.soulsCount.getValue().intValue());
        float speed = aura.soulsSpeed.getValue();

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d targetPos = target.getLerpedPos(e.getTickDelta());
        float bodyH = target.getHeight();
        Camera camera = mc.gameRenderer.getCamera();

        VertexConsumer softBuf = immediate.getBuffer(
                ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_1.png")));
        VertexConsumer coreBuf = immediate.getBuffer(
                ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_4.png")));

        int soulSoft = ColorUtil.overCol(ColorUtil.getColor(90, 200, 255), redColor, atts);
        int soulCore = ColorUtil.overCol(ColorUtil.getColor(195, 245, 255), redColor, atts);

        for (int s = 0; s < streams; s++) {
            for (int k = 0; k < 10; k++) {
                float u = (t * 0.35F * speed + k * 0.1F + s * 0.37F) % 1.0F;
                float wave = (float) Math.sin(u * Math.PI);
                if (wave <= 0.02F) continue;

                float y = 0.05F + u * bodyH * 1.1F;
                float radius = (0.32F + 0.14F * (float) Math.sin(t * 1.1 + s * 2.4)) * (1.0F - 0.4F * u);
                float ang = t * 70F * speed + s * 137.5F + k * 24.0F + u * 160.0F;
                double rad = Math.toRadians(ang);

                float px = targetPos.x - cameraPos.x + (float) Math.cos(rad) * radius;
                float pz = targetPos.z - cameraPos.z + (float) Math.sin(rad) * radius;
                float py = targetPos.y - cameraPos.y + y;

                matrices.push();
                matrices.translate(px, py, pz);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

                float gs = 0.16F * (1.0F + u * 0.8F);
                matrices.push();
                matrices.scale(gs, gs, gs);
                drawTexQuad(softBuf, matrices.peek().getPositionMatrix(), 1.0F,
                        ColorUtil.replAlpha(soulSoft, (int) (alphaPC * wave * 60)));
                matrices.pop();

                float cs = 0.05F * (1.0F + u);
                matrices.push();
                matrices.scale(cs, cs, cs);
                drawTexQuad(coreBuf, matrices.peek().getPositionMatrix(), 1.0F,
                        ColorUtil.replAlpha(soulCore, (int) (alphaPC * wave * 220)));
                matrices.pop();

                matrices.pop();
            }
        }
    }

    /** «Астрал»: световой столп и две спиральные ленты обвивают цель. */
    private void renderTargetAstral(EventRender3D e, VertexConsumerProvider.Immediate immediate,
                                    AttackAura aura, LivingEntity target, float alphaPC) {
        float t = System.currentTimeMillis() / 1000.0F;

        alpha_2.update();
        float hurtPC = (float) Math.sin(target.hurtTime * (Math.PI / 10.0));
        alpha_2.run(hurtPC, 0.1F, Easings.SINE_OUT);

        int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0f * alphaPC));
        float atts = alpha_2.get();
        float speed = aura.astralSpeed.getValue();

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d targetPos = target.getLerpedPos(e.getTickDelta());
        float bodyH = target.getHeight();

        matrices.push();
        matrices.translate(targetPos.x - cameraPos.x,
                targetPos.y - cameraPos.y,
                targetPos.z - cameraPos.z);

        VertexConsumer texBuf = immediate.getBuffer(
                ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_1.png")));
        VertexConsumer fillBuf = immediate.getBuffer(RING_FILL_LAYER);

        // свечение у ног
        matrices.push();
        matrices.translate(0, 0.02F, 0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0f));
        float cg = bodyH * 0.55F;
        matrices.scale(cg, cg, cg);
        drawGradientQuad(texBuf, matrices.peek().getPositionMatrix(),
                ColorUtil.getColor(160, 235, 255),
                ColorUtil.getColor(160, 235, 255),
                ColorUtil.getColor(160, 235, 255),
                ColorUtil.getColor(160, 235, 255),
                (int) (alphaPC * 45));
        matrices.pop();

        // столп света: два скрещённых вертикальных квада с затуханием кверху
        int colA = ColorUtil.overCol(ColorUtil.getColor(175, 240, 255), redColor, atts);
        for (int j = 0; j < 2; j++) {
            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(j * 90.0F));
            Matrix4f pm = matrices.peek().getPositionMatrix();
            float halfW = 0.26F;
            int aBot = (int) (alphaPC * 70);
            int aTop = 0;
            fillBuf.vertex(pm, -halfW, 0, 0).color(ColorUtil.replAlpha(colA, aBot));
            fillBuf.vertex(pm, halfW, 0, 0).color(ColorUtil.replAlpha(colA, aBot));
            fillBuf.vertex(pm, halfW, bodyH * 1.05F, 0).color(ColorUtil.replAlpha(colA, aTop));
            fillBuf.vertex(pm, -halfW, bodyH * 1.05F, 0).color(ColorUtil.replAlpha(colA, aTop));
            matrices.pop();
        }

        // спиральные ленты
        Matrix4f m = matrices.peek().getPositionMatrix();
        int band = ColorUtil.overCol(ColorUtil.getColor(160, 235, 255), redColor, atts);
        int segs = 26;
        float w = 0.05F;
        for (int j = 0; j < 2; j++) {
            float dir = (j == 0) ? 1.0F : -1.0F;
            float r = 0.34F + 0.05F * (float) Math.sin(t * 1.7 + j * 2.1);

            float prevA = t * 80F * speed * dir + j * 180.0F;
            float prevF = 0.0F;
            float prevX = (float) Math.cos(Math.toRadians(prevA)) * r;
            float prevZ = (float) Math.sin(Math.toRadians(prevA)) * r;
            float prevY = 0.06F;

            for (int i = 1; i <= segs; i++) {
                float f = (float) i / segs;
                float a = prevA + dir * (300.0F / segs);
                float x = (float) Math.cos(Math.toRadians(a)) * r;
                float z = (float) Math.sin(Math.toRadians(a)) * r;
                float y = 0.06F + f * bodyH * 1.02F;

                int a0 = Math.max(0, (int) (alphaPC * (55 + 45 * (float) Math.sin(prevF * 9.0 - t * 4.0 + j))));
                int a1 = Math.max(0, (int) (alphaPC * (55 + 45 * (float) Math.sin(f * 9.0 - t * 4.0 + j))));

                fillBuf.vertex(m, prevX, prevY, prevZ).color(ColorUtil.replAlpha(band, a0));
                fillBuf.vertex(m, x, y, z).color(ColorUtil.replAlpha(band, a1));
                fillBuf.vertex(m, x, y + w, z).color(ColorUtil.replAlpha(band, a1));
                fillBuf.vertex(m, prevX, prevY + w, prevZ).color(ColorUtil.replAlpha(band, a0));

                prevA = a;
                prevX = x;
                prevY = y;
                prevZ = z;
                prevF = f;
            }
        }

        matrices.pop();
    }

    /** Текстурный квад с центром в origin (для билбордов). */
    private static void drawTexQuad(VertexConsumer buf, Matrix4f m, float s, int color) {
        buf.vertex(m, -s,  s, 0).color(color).texture(0.0F, 1.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buf.vertex(m,  s,  s, 0).color(color).texture(1.0F, 1.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buf.vertex(m,  s, -s, 0).color(color).texture(1.0F, 0.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buf.vertex(m, -s, -s, 0).color(color).texture(0.0F, 0.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
    }

    /** Силуэт призрачка: купол, корпус и волнистая юбка. */
    private static void ghostSilhouette(VertexConsumer buf, Matrix4f m, float s, int bodyCol, int eyeCol) {
        float cy = 1.05F * s;
        int segs = 8;
        for (int i = 0; i < segs; i++) {
            float a0 = (float) Math.PI * i / segs;
            float a1 = (float) Math.PI * (i + 1) / segs;
            buf.vertex(m, 0, cy, 0).color(bodyCol);
            buf.vertex(m, (float) Math.cos(a0) * s, cy + (float) Math.sin(a0) * s, 0).color(bodyCol);
            buf.vertex(m, (float) Math.cos(a1) * s, cy + (float) Math.sin(a1) * s, 0).color(bodyCol);
            buf.vertex(m, 0, cy, 0).color(bodyCol);
        }

        float skirtY = -0.55F * s;
        buf.vertex(m, -s, cy, 0).color(bodyCol);
        buf.vertex(m, s, cy, 0).color(bodyCol);
        buf.vertex(m, s, skirtY, 0).color(bodyCol);
        buf.vertex(m, -s, skirtY, 0).color(bodyCol);

        for (int k = 0; k < 3; k++) {
            float cxk = -s + (k + 0.5F) * (2.0F * s / 3.0F);
            float rr = s / 3.0F;
            int ksegs = 6;
            for (int i = 0; i < ksegs; i++) {
                float a0 = (float) Math.PI + (float) Math.PI * i / ksegs;
                float a1 = (float) Math.PI + (float) Math.PI * (i + 1) / ksegs;
                buf.vertex(m, cxk, skirtY, 0).color(bodyCol);
                buf.vertex(m, cxk + (float) Math.cos(a0) * rr, skirtY + (float) Math.sin(a0) * rr, 0).color(bodyCol);
                buf.vertex(m, cxk + (float) Math.cos(a1) * rr, skirtY + (float) Math.sin(a1) * rr, 0).color(bodyCol);
                buf.vertex(m, cxk, skirtY, 0).color(bodyCol);
            }
        }

        ghostEye(buf, m, -0.34F * s, cy + 0.12F * s, 0.10F * s, 0.15F * s, eyeCol);
        ghostEye(buf, m, 0.34F * s, cy + 0.12F * s, 0.10F * s, 0.15F * s, eyeCol);
    }

    private static void ghostEye(VertexConsumer buf, Matrix4f m, float x, float y, float w, float h, int col) {
        buf.vertex(m, x - w, y + h, 0).color(col);
        buf.vertex(m, x + w, y + h, 0).color(col);
        buf.vertex(m, x + w, y - h, 0).color(col);
        buf.vertex(m, x - w, y - h, 0).color(col);
    }

    /** Контур призрачка яркими линиями. */
    private static void ghostOutline(VertexConsumer buf, Matrix4f m, float s, int col) {
        float cy = 1.05F * s;
        int segs = 8;
        float px = s, py = cy;
        for (int i = 1; i <= segs; i++) {
            float a = (float) Math.PI * i / segs;
            float nx = (float) Math.cos(a) * s;
            float ny = cy + (float) Math.sin(a) * s;
            buf.vertex(m, px, py, 0).color(col);
            buf.vertex(m, nx, ny, 0).color(col);
            px = nx;
            py = ny;
        }
        buf.vertex(m, -s, cy, 0).color(col);
        buf.vertex(m, -s, -0.55F * s, 0).color(col);
        buf.vertex(m, s, cy, 0).color(col);
        buf.vertex(m, s, -0.55F * s, 0).color(col);
    }

    /** Светящаяся пентаграмма на земле под целью. */
    private void renderTargetPentagram(EventRender3D e, VertexConsumerProvider.Immediate immediate,
                                       AttackAura aura, LivingEntity target, float alphaPC) {
        int hurtTicks = target.hurtTime;
        float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 10.0));

        alpha_2.update();
        alpha_2.run(hurtPC, 0.1F, Easings.SINE_OUT);

        int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0f * alphaPC));
        int base = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(1), alphaPC), redColor, alpha_2.get());

        long currentTime = System.currentTimeMillis();
        if (currentTimeSpirits == 0) currentTimeSpirits = currentTime;
        long timeDiff = currentTime - currentTimeSpirits;
        if (timeDiff > 0) animationNurik += timeDiff / 16.666F;
        currentTimeSpirits = currentTime;

        float speed = aura.pentaSpeed.getValue();
        float r = (aura.pentaRadius.getValue() + target.getWidth() * 0.35f)
                * (1.0f + 0.04f * (float) Math.sin(Math.toRadians(animationNurik * 6.0f)));
        float spin = animationNurik * 2.2f * speed;
        int aSoft = (int) (alphaPC * 36);
        int aRibbon = (int) (alphaPC * 150);
        int aCore = (int) (alphaPC * 235);

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d targetPos = target.getLerpedPos(e.getTickDelta());

        matrices.push();
        matrices.translate(targetPos.x - cameraPos.x,
                targetPos.y - cameraPos.y + 0.06f,
                targetPos.z - cameraPos.z);
        Matrix4f m = matrices.peek().getPositionMatrix();

        // вершины пентакля
        float[] tipX = new float[5];
        float[] tipZ = new float[5];
        for (int k = 0; k < 5; k++) {
            double a = Math.toRadians(spin + k * 72.0);
            tipX[k] = (float) (Math.cos(a) * r);
            tipZ[k] = (float) (Math.sin(a) * r);
        }

        // --- Pass 1: свечение (текстурные квады лежат на земле) ---
        VertexConsumer texBuf = immediate.getBuffer(
                ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_1.png")));

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0f));
        float cg = r * 1.6f;
        matrices.scale(cg, cg, cg);
        drawGradientQuad(texBuf, matrices.peek().getPositionMatrix(),
                base, base, base, base, aSoft * 2);
        matrices.pop();

        for (int k = 0; k < 5; k++) {
            matrices.push();
            matrices.translate(tipX[k], 0, tipZ[k]);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0f));
            float gs = r * 0.34f;
            matrices.scale(gs, gs, gs);
            int tipCol = ColorUtil.overCol(ColorUtil.fade(k * 48), redColor, alpha_2.get());
            drawGradientQuad(texBuf, matrices.peek().getPositionMatrix(),
                    tipCol, tipCol, tipCol, tipCol, (int) (alphaPC * 110));
            matrices.pop();
        }

        // --- Pass 2: заливки-ленты ---
        VertexConsumer fillBuf = immediate.getBuffer(RING_FILL_LAYER);

        // мягкий диск под сигилой: центр ярче, край растворяется
        int discSegs = 64;
        for (int i = 0; i < discSegs; i++) {
            float a0 = (float) (Math.PI * 2.0 * i / discSegs);
            float a1 = (float) (Math.PI * 2.0 * (i + 1) / discSegs);
            float x0 = (float) Math.cos(a0) * r * 1.32f;
            float z0 = (float) Math.sin(a0) * r * 1.32f;
            float x1 = (float) Math.cos(a1) * r * 1.32f;
            float z1 = (float) Math.sin(a1) * r * 1.32f;
            fillBuf.vertex(m, 0, 0, 0).color(ColorUtil.replAlpha(base, aSoft));
            fillBuf.vertex(m, x0, 0, z0).color(ColorUtil.replAlpha(base, 0));
            fillBuf.vertex(m, x1, 0, z1).color(ColorUtil.replAlpha(base, 0));
            fillBuf.vertex(m, x1, 0, z1).color(ColorUtil.replAlpha(base, 0));
        }

        float w = r * 0.032f + 0.008f;

        // классический пентакль: хорды 0-2-4-1-3 с градиентом по кончикам
        for (int k = 0; k < 5; k++) {
            int nk = (k + 2) % 5;
            int c0 = ColorUtil.overCol(ColorUtil.multBright(ColorUtil.fade(k * 48), 0.85F), redColor, alpha_2.get());
            int c1 = ColorUtil.overCol(ColorUtil.multBright(ColorUtil.fade(nk * 48), 0.85F), redColor, alpha_2.get());
            pentagramRibbon(fillBuf, m, tipX[k], tipZ[k], tipX[nk], tipZ[nk], w,
                    ColorUtil.replAlpha(c0, aRibbon), ColorUtil.replAlpha(c1, aRibbon));
        }

        // внутренний пятиугольник (пересечения хорд)
        for (int k = 0; k < 5; k++) {
            int nk = (k + 1) % 5;
            pentagramRibbon(fillBuf, m,
                    tipX[k] * 0.382f, tipZ[k] * 0.382f,
                    tipX[nk] * 0.382f, tipZ[nk] * 0.382f,
                    w * 0.7f,
                    ColorUtil.replAlpha(base, (int) (alphaPC * 110)),
                    ColorUtil.replAlpha(base, (int) (alphaPC * 110)));
        }

        // круги через кончики и внешний контур
        for (int i = 0; i < discSegs; i++) {
            float a0 = (float) (Math.PI * 2.0 * i / discSegs);
            float a1 = (float) (Math.PI * 2.0 * (i + 1) / discSegs);
            pentagramRibbon(fillBuf, m,
                    (float) Math.cos(a0) * r, (float) Math.sin(a0) * r,
                    (float) Math.cos(a1) * r, (float) Math.sin(a1) * r,
                    w * 0.75f,
                    ColorUtil.replAlpha(base, (int) (alphaPC * 130)),
                    ColorUtil.replAlpha(base, (int) (alphaPC * 130)));
            pentagramRibbon(fillBuf, m,
                    (float) Math.cos(a0) * r * 1.16f, (float) Math.sin(a0) * r * 1.16f,
                    (float) Math.cos(a1) * r * 1.16f, (float) Math.sin(a1) * r * 1.16f,
                    w * 0.55f,
                    ColorUtil.replAlpha(base, (int) (alphaPC * 80)),
                    ColorUtil.replAlpha(base, (int) (alphaPC * 80)));
        }

        // внешняя контр-вращающаяся пентаграмма
        float rOuter = r * 1.32f;
        int faint = ColorUtil.replAlpha(ColorUtil.overCol(ColorUtil.multBright(ColorUtil.fade(180), 0.8F), redColor, alpha_2.get()),
                (int) (alphaPC * 60));
        float spin2 = -spin * 0.7f + 36.0f;
        for (int k = 0; k < 5; k++) {
            double a0 = Math.toRadians(spin2 + k * 72.0);
            double a1 = Math.toRadians(spin2 + ((k + 2) % 5) * 72.0);
            pentagramRibbon(fillBuf, m,
                    (float) (Math.cos(a0) * rOuter), (float) (Math.sin(a0) * rOuter),
                    (float) (Math.cos(a1) * rOuter), (float) (Math.sin(a1) * rOuter),
                    w * 0.5f, faint, faint);
        }

        // --- Pass 3: тонкие яркие сердцевины линий ---
        VertexConsumer lineBuf = immediate.getBuffer(RING_LINE_LAYER);
        int coreCol = ColorUtil.replAlpha(base, aCore);
        for (int i = 0; i < discSegs; i++) {
            float a0 = (float) (Math.PI * 2.0 * i / discSegs);
            float a1 = (float) (Math.PI * 2.0 * (i + 1) / discSegs);
            lineBuf.vertex(m, (float) Math.cos(a0) * r, 0, (float) Math.sin(a0) * r).color(coreCol);
            lineBuf.vertex(m, (float) Math.cos(a1) * r, 0, (float) Math.sin(a1) * r).color(coreCol);
        }
        for (int k = 0; k < 5; k++) {
            int nk = (k + 2) % 5;
            lineBuf.vertex(m, tipX[k], 0, tipZ[k]).color(coreCol);
            lineBuf.vertex(m, tipX[nk], 0, tipZ[nk]).color(coreCol);
        }
        for (int k = 0; k < 5; k++) {
            int nk = (k + 1) % 5;
            lineBuf.vertex(m, tipX[k] * 0.382f, 0, tipZ[k] * 0.382f).color(coreCol);
            lineBuf.vertex(m, tipX[nk] * 0.382f, 0, tipZ[nk] * 0.382f).color(coreCol);
        }

        matrices.pop();
    }

    /** Отрезок на плоскости XZ как тонкая лента-квад шириной width. */
    private static void pentagramRibbon(VertexConsumer buf, Matrix4f m,
                                        float x0, float z0, float x1, float z1,
                                        float width, int c0, int c1) {
        float dx = x1 - x0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-5f) return;
        float nx = (dz / len) * width;
        float nz = (-dx / len) * width;

        buf.vertex(m, x0 + nx, 0, z0 + nz).color(c0);
        buf.vertex(m, x1 + nx, 0, z1 + nz).color(c1);
        buf.vertex(m, x1 - nx, 0, z1 - nz).color(c1);
        buf.vertex(m, x0 - nx, 0, z0 - nz).color(c0);
    }

    /**
     * «Цепь»: светящееся кольцо из звеньев-овалов вокруг пояса цели.
     * Соседние звенья смещены радиально в противоположные стороны — эффект плетения.
     */
    private void renderTargetChainRing(EventRender3D e, VertexConsumerProvider.Immediate immediate,
                                       AttackAura aura, LivingEntity target, float alphaPC) {
        int hurtTicks = target.hurtTime;
        float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 10.0));

        alpha_2.update();
        alpha_2.run(hurtPC, 0.1F, Easings.SINE_OUT);

        int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0f * alphaPC));
        float atts = alpha_2.get();
        // раскалённо-белые звенья как на референсе
        int coreCol = ColorUtil.overCol(ColorUtil.overCol(
                ColorUtil.multAlpha(ColorUtil.fade(1), alphaPC),
                ColorUtil.getColor(238, 244, 255), 0.75f), redColor, atts);

        long currentTime = System.currentTimeMillis();
        if (currentTimeSpirits == 0) currentTimeSpirits = currentTime;
        long timeDiff = currentTime - currentTimeSpirits;
        if (timeDiff > 0) animationNurik += timeDiff / 16.666F;
        currentTimeSpirits = currentTime;

        int count = Math.max(6, aura.linkCount.getValue().intValue());
        float speed = aura.linkSpeed.getValue();
        float radius = aura.linkRadius.getValue() + target.getWidth() * 0.35f + 0.12f;
        float size = aura.linkSize.getValue();          // полуширина овала звена
        float bodyH = target.getHeight();

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d targetPos = target.getLerpedPos(e.getTickDelta());

        matrices.push();
        matrices.translate(targetPos.x - cameraPos.x, targetPos.y - cameraPos.y, targetPos.z - cameraPos.z);
        matrices.translate(0, bodyH * 0.47f
                + 0.02f * (float) Math.sin(Math.toRadians(animationNurik * 1.4f)), 0);
        Matrix4f m = matrices.peek().getPositionMatrix();

        float step = 360f / count;
        float span = step * 0.62f;                       // зазор между звеньями
        float baseAng = animationNurik * 1.15f * speed;

        // ── Pass 1: свечение под каждым звеном ──
        VertexConsumer texBuf = immediate.getBuffer(
                ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_2.png")));
        for (int i = 0; i < count; i++) {
            float midA = baseAng + i * step + span * 0.5f;
            double offAmp = size * 0.55f * Math.sin(i * Math.PI); // вплетение: чередование ±
            float gx = (float) Math.cos(Math.toRadians(midA)) * (radius + (float) offAmp);
            float gz = (float) Math.sin(Math.toRadians(midA)) * (radius + (float) offAmp);

            matrices.push();
            matrices.translate(gx, 0, gz);
            matrices.multiply(mc.gameRenderer.getCamera().getRotation());
            float g = size * 4.2f;
            matrices.scale(g, g, g);
            drawGradientQuad(texBuf, matrices.peek().getPositionMatrix(),
                    coreCol, coreCol, coreCol, coreCol, (int) (alphaPC * 80));
            matrices.pop();
        }

        // ── Pass 2: сами звенья — объёмные торы с плетением ──
        VertexConsumer fillBuf = immediate.getBuffer(RING_FILL_LAYER);
        int colTop = ColorUtil.replAlpha(ColorUtil.overCol(coreCol, ColorUtil.getColor(255), 0.55f),
                (int) (alphaPC * 245));
        int colSide = ColorUtil.replAlpha(coreCol, (int) (alphaPC * 205));
        int colBot = ColorUtil.replAlpha(
                ColorUtil.overCol(coreCol, ColorUtil.getColor(110, 120, 145), 0.5f),
                (int) (alphaPC * 160));

        for (int i = 0; i < count; i++) {
            double offAmp = Math.sin(i * Math.PI);
            float midA = baseAng + i * step + span * 0.5f;
            float cx = (float) Math.cos(Math.toRadians(midA)) * (radius + (float) (offAmp * size * 0.55));
            float cz = (float) Math.sin(Math.toRadians(midA)) * (radius + (float) (offAmp * size * 0.55));
            // плетение: соседние звенья чуть выше/ниже
            float cy = (float) (offAmp * size * 0.5);

            drawTorusLink(fillBuf, m, cx, cy, cz, size, size * 0.40f, 16, 8,
                    colTop, colSide, colBot);
        }

        matrices.pop();
    }

    /** Объёмное звено-тор в горизонтальной плоскости; шейдинг по высоте трубки. */
    private static void drawTorusLink(VertexConsumer buf, Matrix4f m,
                                      float cx, float cy, float cz,
                                      float rMain, float rTube,
                                      int segU, int segV,
                                      int colTop, int colSide, int colBot) {
        float[] px = new float[segU + 1];
        float[] py = new float[segU + 1];
        float[] pz = new float[segU + 1];
        float[] dxs = new float[segU + 1];
        float[] dzs = new float[segU + 1];

        for (int u = 0; u <= segU; u++) {
            double a = Math.PI * 2.0 * u / segU;
            float dxc = (float) Math.cos(a);
            float dzc = (float) Math.sin(a);
            dxs[u] = dxc;
            dzs[u] = dzc;
            px[u] = cx + dxc * rMain;
            py[u] = cy;
            pz[u] = cz + dzc * rMain;
        }

        // цвет вершины трубки зависит только от её высоты на сечении
        int[] vCols = new int[segV];
        for (int v = 0; v < segV; v++) {
            double b = Math.PI * 2.0 * v / segV;
            float h = (float) Math.sin(b);
            if (h > 0.35f) vCols[v] = colTop;
            else if (h > -0.35f) vCols[v] = colSide;
            else vCols[v] = colBot;
        }

        for (int u = 0; u < segU; u++) {
            int u1 = u + 1;
            for (int v = 0; v < segV; v++) {
                double b0 = Math.PI * 2.0 * v / segV;
                double b1 = Math.PI * 2.0 * (v + 1) / segV;

                float o00 = (float) Math.cos(b0) * rTube, y00 = (float) Math.sin(b0) * rTube;
                float o10 = (float) Math.cos(b1) * rTube, y10 = (float) Math.sin(b1) * rTube;

                buf.vertex(m, px[u] + dxs[u] * o00, py[u] + y00, pz[u] + dzs[u] * o00).color(vCols[v]);
                buf.vertex(m, px[u] + dxs[u] * o10, py[u] + y10, pz[u] + dzs[u] * o10).color(vCols[(v + 1) % segV]);
                buf.vertex(m, px[u1] + dxs[u1] * o10, py[u1] + y10, pz[u1] + dzs[u1] * o10).color(vCols[(v + 1) % segV]);
                buf.vertex(m, px[u1] + dxs[u1] * o00, py[u1] + y00, pz[u1] + dzs[u1] * o00).color(vCols[v]);
            }
        }
    }

    /**
     * Снег вокруг цели: снежинки-биллборды на орбитах.
     * Каждая — шесть заострённых лучей (три перекрестия) + мягкое свечение,
     * крутится вокруг своей оси и плавно покачивается по высоте.
     * Слои рисуются строго последовательно: сначала всё свечение, потом все лучи.
     */
    private void renderTargetSnow(EventRender3D e, VertexConsumerProvider.Immediate immediate,
                                  AttackAura aura, LivingEntity target, float alphaPC) {
        int hurtTicks = target.hurtTime;
        float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 10.0));

        alpha_2.update();
        alpha_2.run(hurtPC, 0.1F, Easings.SINE_OUT);

        int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0f * alphaPC));
        int snowWhite = ColorUtil.getColor(235, 245, 255);
        int color = ColorUtil.overCol(
                ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(1), alphaPC), snowWhite, 0.55f),
                redColor, alpha_2.get());

        long currentTime = System.currentTimeMillis();
        if (currentTimeSpirits == 0) currentTimeSpirits = currentTime;
        long timeDiff = currentTime - currentTimeSpirits;
        if (timeDiff > 0) animationNurik += timeDiff / 16.666F;
        currentTimeSpirits = currentTime;

        int count = Math.max(1, aura.snowCount.getValue().intValue());
        float speed = aura.snowSpeed.getValue();
        float radius = aura.snowRadius.getValue() + target.getWidth() * 0.35f + 0.1f;
        float size = aura.snowSize.getValue();
        float bodyH = target.getHeight();

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d targetPos = target.getLerpedPos(e.getTickDelta());

        // геометрия считаем один раз, используем в обоих проходах (буферы без аллокаций)
        float[] pxArr = SNOW_PX;
        float[] pyArr = SNOW_PY;
        float[] pzArr = SNOW_PZ;
        float[] spinArr = SNOW_SPIN;

        for (int i = 0; i < count; i++) {
            // золотое сечение — равномерный разброс высот без «рядов»
            float heightFrac = 0.22f + 0.6f * ((i * 0.618f) % 1f);
            double orbA = Math.toRadians(
                    animationNurik * 1.4f * speed * ((i % 2 == 0) ? 1f : -1f) + i * (360.0 / count));

            pxArr[i] = (float) Math.cos(orbA) * radius;
            pzArr[i] = (float) Math.sin(orbA) * radius;
            pyArr[i] = bodyH * heightFrac
                    + (float) Math.sin(Math.toRadians(animationNurik * 1.3f + i * 61.0)) * bodyH * 0.08f;
            spinArr[i] = animationNurik * 2.6f * speed * ((i % 2 == 0) ? 1f : -1f) + i * 53f;
        }

        matrices.push();
        matrices.translate(targetPos.x - cameraPos.x, targetPos.y - cameraPos.y, targetPos.z - cameraPos.z);

        // --- Pass 1: мягкое свечение под каждой снежинкой ---
        VertexConsumer texBuf = immediate.getBuffer(
                ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_1.png")));

        for (int i = 0; i < count; i++) {
            matrices.push();
            matrices.translate(pxArr[i], pyArr[i], pzArr[i]);
            matrices.multiply(mc.gameRenderer.getCamera().getRotation());
            float g = size * 4.5f;
            matrices.scale(g, g, g);
            drawGradientQuad(texBuf, matrices.peek().getPositionMatrix(),
                    color, color, color, color, (int) (alphaPC * 110));
            matrices.pop();
        }

        // --- Pass 2: сами снежинки — три перекрестия заострённых лучей ---
        VertexConsumer fillBuf = immediate.getBuffer(RING_FILL_LAYER);

        float L = size * 1.5f;
        float W = Math.max(size * 0.24f, 0.012f);
        int col = ColorUtil.replAlpha(color, (int) (alphaPC * 230));
        int dotCol = ColorUtil.replAlpha(snowWhite, (int) (alphaPC * 255));

        for (int i = 0; i < count; i++) {
            matrices.push();
            matrices.translate(pxArr[i], pyArr[i], pzArr[i]);
            matrices.multiply(mc.gameRenderer.getCamera().getRotation());
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spinArr[i]));

            for (int k = 0; k < 3; k++) {
                matrices.push();
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(k * 60f));
                Matrix4f bm = matrices.peek().getPositionMatrix();

                // луч-«шип»: вытянутый шестиугольник из двух квадов
                fillBuf.vertex(bm, -L, -W * 0.45f, 0).color(col);
                fillBuf.vertex(bm, -L * 0.42f, -W, 0).color(col);
                fillBuf.vertex(bm, L * 0.42f, -W, 0).color(col);
                fillBuf.vertex(bm, L, -W * 0.45f, 0).color(col);

                fillBuf.vertex(bm, L, W * 0.45f, 0).color(col);
                fillBuf.vertex(bm, L * 0.42f, W, 0).color(col);
                fillBuf.vertex(bm, -L * 0.42f, W, 0).color(col);
                fillBuf.vertex(bm, -L, W * 0.45f, 0).color(col);

                matrices.pop();
            }

            // яркая сердцевина
            Matrix4f dm = matrices.peek().getPositionMatrix();
            fillBuf.vertex(dm, -W, -W, 0).color(dotCol);
            fillBuf.vertex(dm, W, -W, 0).color(dotCol);
            fillBuf.vertex(dm, W, W, 0).color(dotCol);
            fillBuf.vertex(dm, -W, W, 0).color(dotCol);

            matrices.pop();
        }

        matrices.pop();
    }

    /**
     * Сердце над целью: бьётся тем быстрее, чем меньше здоровья у противника.
     * «Тук-тук» — два толчка за цикл, на каждом ударе расходится кольцо-волна.
     */
    private void renderTargetHeart(EventRender3D e, VertexConsumerProvider.Immediate immediate,
                                   AttackAura aura, LivingEntity target, float alphaPC) {
        int hurtTicks = target.hurtTime;
        float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20.0));

        alpha_2.update();
        alpha_2.run(hurtPC, 0.1F, Easings.SINE_OUT);

        // доля здоровья цели
        float hpMax = target.getMaxHealth() + target.getAbsorptionAmount();
        float hpNow = target.getHealth() + target.getAbsorptionAmount();
        float hpFrac = hpMax <= 0f ? 1f : Math.min(hpNow / hpMax, 1f);
        float lowHp = 1f - hpFrac;

        // фаза сердцебиения: интегрируем частоту по времени (при низком ХП — быстрее)
        long now = System.currentTimeMillis();
        if (heartLastTime == 0L) heartLastTime = now;
        long dtMs = now - heartLastTime;
        heartLastTime = now;
        float bpm = (55f + 170f * lowHp * lowHp) * aura.heartSpeed.getValue();
        heartPhase += dtMs / 1000f * (bpm / 60f);

        float f = heartPhase % 1f;
        // «тук-тук»: основной толчок + второй слабее
        float pulse = (float) (Math.exp(-7.0 * f) + 0.5 * Math.exp(-11.0 * Math.abs(f - 0.24)));
        pulse = Math.min(pulse, 1.4f);

        float beatScale = 1f + 0.20f * pulse;

        int hurtRed = ColorUtil.getColor(255, 90, 90, (int) (255.0f * alphaPC));
        int baseCol = ColorUtil.overCol(ColorUtil.getColor(255, 92, 120),
                ColorUtil.getColor(255, 34, 56), lowHp);
        int col = ColorUtil.overCol(ColorUtil.multAlpha(baseCol, alphaPC), hurtRed, alpha_2.get());
        int hotCol = ColorUtil.replAlpha(
                ColorUtil.overCol(ColorUtil.getColor(255, 195, 210), ColorUtil.getColor(255, 125, 145), lowHp),
                (int) (alphaPC * 235));
        int fillCol = ColorUtil.replAlpha(col, (int) (alphaPC * (70 + 50 * pulse)));

        float size = aura.heartSize.getValue();

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d targetPos = target.getLerpedPos(e.getTickDelta());

        matrices.push();
        matrices.translate(targetPos.x - cameraPos.x, targetPos.y - cameraPos.y, targetPos.z - cameraPos.z);
        matrices.push();
        matrices.translate(0,
                target.getHeight() * 0.62f + 0.05f * (float) Math.sin(now / 420.0),
                0);
        matrices.multiply(mc.gameRenderer.getCamera().getRotation());

        float k = size / 34f * beatScale;

        // контур сердца: классическая параметрическая кривая (буферы без аллокаций)
        final int SEGS = 48;
        float[] hxArr = HEART_HX;
        float[] hyArr = HEART_HY;
        for (int i = 0; i < SEGS; i++) {
            double t = Math.PI * 2.0 * i / SEGS;
            hxArr[i] = (float) (16.0 * Math.pow(Math.sin(t), 3)) * k;
            hyArr[i] = (float) (13.0 * Math.cos(t) - 5.0 * Math.cos(2 * t)
                    - 2.0 * Math.cos(3 * t) - Math.cos(4 * t)) * k
                    + 6f * k;   // вертикальное центрирование
        }

        // --- Pass 1: свечение ---
        VertexConsumer texBuf = immediate.getBuffer(
                ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_1.png")));
        matrices.push();
        float g = size * beatScale * (1.6f + 0.5f * pulse);
        matrices.scale(g, g, g);
        drawGradientQuad(texBuf, matrices.peek().getPositionMatrix(),
                col, col, col, col, (int) (alphaPC * (85 + 55 * pulse)));
        matrices.pop();

        // --- Pass 2: заливка сердца веером ---
        VertexConsumer fillBuf = immediate.getBuffer(RING_FILL_LAYER);
        Matrix4f m = matrices.peek().getPositionMatrix();
        for (int i = 0; i < SEGS; i++) {
            int j = (i + 1) % SEGS;
            fillBuf.vertex(m, 0, 0, 0).color(fillCol);
            fillBuf.vertex(m, hxArr[i], hyArr[i], 0).color(fillCol);
            fillBuf.vertex(m, hxArr[j], hyArr[j], 0).color(fillCol);
            fillBuf.vertex(m, hxArr[j], hyArr[j], 0).color(fillCol);
        }

        // волна от удара: расширяющееся кольцо в плоскости биллборда
        if (f < 0.38f) {
            float rf = f / 0.38f;
            float ringR = size * (0.65f + 0.85f * rf) * beatScale;
            float ringW = size * 0.05f;
            int ringCol = ColorUtil.replAlpha(col, (int) (alphaPC * (1f - rf) * 120));
            for (int i = 0; i < SEGS; i++) {
                double a0 = Math.PI * 2.0 * i / SEGS;
                double a1 = Math.PI * 2.0 * (i + 1) / SEGS;
                fillBuf.vertex(m, (float) Math.cos(a0) * ringR, (float) Math.sin(a0) * ringR, 0).color(ringCol);
                fillBuf.vertex(m, (float) Math.cos(a1) * ringR, (float) Math.sin(a1) * ringR, 0).color(ringCol);
                fillBuf.vertex(m, (float) Math.cos(a1) * (ringR - ringW), (float) Math.sin(a1) * (ringR - ringW), 0).color(ringCol);
                fillBuf.vertex(m, (float) Math.cos(a0) * (ringR - ringW), (float) Math.sin(a0) * (ringR - ringW), 0).color(ringCol);
            }
        }

        // --- Pass 3: яркий контур ---
        VertexConsumer lineBuf = immediate.getBuffer(RING_LINE_LAYER);
        for (int i = 0; i < SEGS; i++) {
            int j = (i + 1) % SEGS;
            lineBuf.vertex(m, hxArr[i], hyArr[i], 0).color(hotCol);
            lineBuf.vertex(m, hxArr[j], hyArr[j], 0).color(hotCol);
        }

        matrices.pop();
        matrices.pop();
    }

    /**
     * Огненный вихрь вокруг цели: частицы закручиваются по спирали и поднимаются
     * вверх, меняя цвет красный → оранжевый → жёлтый и сужаясь к вершине.
     * Слои рисуются строго последовательно: сначала внешнее свечение, потом ядра.
     */
    private void renderTargetFire(EventRender3D e, VertexConsumerProvider.Immediate immediate,
                                  AttackAura aura, LivingEntity target, float alphaPC) {
        int hurtTicks = target.hurtTime;
        float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 10.0));

        alpha_2.update();
        alpha_2.run(hurtPC, 0.1F, Easings.SINE_OUT);

        int hurtRed = ColorUtil.getColor(255, 60, 40);

        long currentTime = System.currentTimeMillis();
        if (currentTimeSpirits == 0) currentTimeSpirits = currentTime;
        long timeDiff = currentTime - currentTimeSpirits;
        if (timeDiff > 0) animationNurik += timeDiff / 16.666F;
        currentTimeSpirits = currentTime;

        int count = Math.max(4, aura.fireCount.getValue().intValue());
        float speed = aura.fireSpeed.getValue();
        float radiusMul = aura.fireRadius.getValue();
        float heightMul = aura.fireHeight.getValue();
        float atts = alpha_2.get();

        float bodyH = target.getHeight();
        float baseR = (target.getWidth() * 0.55f + 0.22f) * radiusMul;
        float riseH = bodyH * 0.95f * heightMul;
        float tSec = animationNurik / 60f;

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d targetPos = target.getLerpedPos(e.getTickDelta());
        var camRot = mc.gameRenderer.getCamera().getRotation();

        // предрасчёт частиц кадра (буферы без аллокаций)
        float[] oxArr = FIRE_OX;
        float[] oyArr = FIRE_OY;
        float[] ozArr = FIRE_OZ;
        int[] outRgb = FIRE_RGB_OUT;
        int[] coreRgb = FIRE_RGB_CORE;
        float[] outAlpha = FIRE_ALPHA;
        float[] outH = FIRE_H;

        for (int i = 0; i < count; i++) {
            float seed = (i * 0.618034f) % 1f;
            float cyc = (tSec * 0.85f * speed + seed * 13.7f) % 1f;   // цикл жизни частицы

            // вихрь: подъём с докручиванием, сужение кверху
            float ang = seed * (float) Math.PI * 2f + tSec * 1.5f * speed + cyc * 2.6f;
            float r = baseR * (1f - 0.45f * cyc) * (0.82f + 0.36f * (float) Math.sin(seed * 41f));
            oxArr[i] = (float) Math.cos(ang) * r;
            ozArr[i] = (float) Math.sin(ang) * r;
            oyArr[i] = 0.05f + cyc * riseH
                    + 0.03f * (float) Math.sin(tSec * 3f + seed * 31f);

            // появление/затухание за цикл
            float fadeIn = smooth01(cyc / 0.14f);
            float fadeOut = 1f - smooth01((cyc - 0.7f) / 0.3f);
            float env = Math.max(0f, fadeIn * fadeOut);
            float flicker = 0.72f + 0.28f * (float) Math.sin(tSec * 13f + seed * 97f);

            // цвет по высоте пламени: красный → оранжевый → жёлтый
            int rgb;
            if (cyc < 0.5f) rgb = fireLerp(0xFF3C0A, 0xFF8C19, cyc * 2f);
            else rgb = fireLerp(0xFF8C19, 0xFFE16E, (cyc - 0.5f) * 2f);
            outRgb[i] = ColorUtil.overCol(rgb, hurtRed, atts);
            coreRgb[i] = ColorUtil.overCol(fireLerp(rgb, 0xFFF0B4, 0.55f), hurtRed, atts);

            outAlpha[i] = alphaPC * env * flicker;
            outH[i] = bodyH * (0.10f + 0.13f * cyc) * heightMul * (0.85f + 0.3f * flicker);
        }

        matrices.push();
        matrices.translate(targetPos.x - cameraPos.x, targetPos.y - cameraPos.y, targetPos.z - cameraPos.z);

        // --- Pass 1: мягкое внешнее свечение ---
        VertexConsumer glowBuf = immediate.getBuffer(
                ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_2.png")));
        for (int i = 0; i < count; i++) {
            if (outAlpha[i] <= 0.02f) continue;
            float h = outH[i] * 1.6f;

            matrices.push();
            matrices.translate(oxArr[i], oyArr[i], ozArr[i]);
            matrices.multiply(camRot);
            float s = h * 2f;
            matrices.scale(s, s, s);
            Matrix4f mm = matrices.peek().getPositionMatrix();
            drawGradientQuad(glowBuf, mm, outRgb[i], outRgb[i], outRgb[i], outRgb[i],
                    (int) (outAlpha[i] * 105));
            matrices.pop();
        }

        // --- Pass 2: яркие ядра языков ---
        VertexConsumer coreBuf = immediate.getBuffer(
                ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_1.png")));
        for (int i = 0; i < count; i++) {
            if (outAlpha[i] <= 0.02f) continue;
            float h = outH[i] * 0.75f;

            matrices.push();
            matrices.translate(oxArr[i], oyArr[i], ozArr[i]);
            matrices.multiply(camRot);
            float s = h * 2f;
            matrices.scale(s, s, s);
            Matrix4f mm = matrices.peek().getPositionMatrix();
            drawGradientQuad(coreBuf, mm, coreRgb[i], coreRgb[i], coreRgb[i], coreRgb[i],
                    (int) (outAlpha[i] * 220));
            matrices.pop();
        }

        matrices.pop();
    }

    /** Плавный step 0..1 с клампом. */
    private static float smooth01(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    private static int fireLerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ra = (a >> 16) & 0xFF, ga = (a >> 8) & 0xFF, ba = a & 0xFF;
        int rb = (b >> 16) & 0xFF, gb = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (ra + (rb - ra) * t) << 16)
                | ((int) (ga + (gb - ga) * t) << 8)
                | (int) (ba + (bb - ba) * t);
    }

    /** Лента-отрезок в плоскости XY (для билбордов: мечи и т.п.). */
    private static void xyRibbon(VertexConsumer buf, Matrix4f m,
                                 float x0, float y0, float x1, float y1,
                                 float w, int c) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-5f) return;
        float nx = (-dy / len) * w;
        float ny = (dx / len) * w;

        buf.vertex(m, x0 + nx, y0 + ny, 0).color(c);
        buf.vertex(m, x1 + nx, y1 + ny, 0).color(c);
        buf.vertex(m, x1 - nx, y1 - ny, 0).color(c);
        buf.vertex(m, x0 - nx, y0 - ny, 0).color(c);
    }

    /**
     * Мечи вокруг цели: пары клинков остриём вниз кружат по орбите.
     * Каждый меч — биллборд: заострённый клинок, гарда, рукоять, навершие;
     * лёгкое покачивание и вертикальный дрейф. Слои строго последовательные.
     */
    private void renderTargetSwords(EventRender3D e, VertexConsumerProvider.Immediate immediate,
                                    AttackAura aura, LivingEntity target, float alphaPC) {
        int hurtTicks = target.hurtTime;
        float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 10.0));

        alpha_2.update();
        alpha_2.run(hurtPC, 0.1F, Easings.SINE_OUT);

        int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0f * alphaPC));
        int color = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(1), alphaPC), redColor, alpha_2.get());
        float atts = alpha_2.get();

        long currentTime = System.currentTimeMillis();
        if (currentTimeSpirits == 0) currentTimeSpirits = currentTime;
        long timeDiff = currentTime - currentTimeSpirits;
        if (timeDiff > 0) animationNurik += timeDiff / 16.666F;
        currentTimeSpirits = currentTime;

        int count = Math.max(2, aura.swordsCount.getValue().intValue());
        float speed = aura.swordsSpeed.getValue();
        float radius = aura.swordsRadius.getValue() + target.getWidth() * 0.35f + 0.1f;
        float size = aura.swordsSize.getValue();
        float bodyH = target.getHeight();

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d targetPos = target.getLerpedPos(e.getTickDelta());

        // геометрия кадра (переиспользуемые буферы)
        float[] pxArr = SWORD_PX;
        float[] pyArr = SWORD_PY;
        float[] pzArr = SWORD_PZ;
        float[] tiltArr = SWORD_TILT;

        for (int i = 0; i < count; i++) {
            double orbA = Math.toRadians(
                    animationNurik * 1.5f * speed * ((i % 2 == 0) ? 1f : -1f) + i * (360.0 / count));
            pxArr[i] = (float) (Math.cos(orbA) * radius);
            pzArr[i] = (float) (Math.sin(orbA) * radius);
            pyArr[i] = bodyH * 0.55f
                    + (float) Math.sin(Math.toRadians(animationNurik * 1.2f + i * 71.0)) * bodyH * 0.06f;
            // лёгкий наклон в сторону вращения
            tiltArr[i] = 10f * (float) Math.sin(Math.toRadians(animationNurik * 1.8f + i * 47f))
                    * ((i % 2 == 0) ? 1f : -1f);
        }

        matrices.push();
        matrices.translate(targetPos.x - cameraPos.x, targetPos.y - cameraPos.y, targetPos.z - cameraPos.z);

        // ── Pass 1: мягкое свечение под каждым мечом ──
        VertexConsumer texBuf = immediate.getBuffer(
                ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_2.png")));
        for (int i = 0; i < count; i++) {
            matrices.push();
            matrices.translate(pxArr[i], pyArr[i], pzArr[i]);
            matrices.multiply(mc.gameRenderer.getCamera().getRotation());
            float g = size * 2.6f;
            matrices.scale(g, g, g);
            drawGradientQuad(texBuf, matrices.peek().getPositionMatrix(),
                    color, color, color, color, (int) (alphaPC * 70));
            matrices.pop();
        }

        // ── Pass 2: сам меч ──
        VertexConsumer fillBuf = immediate.getBuffer(RING_FILL_LAYER);

        for (int i = 0; i < count; i++) {
            matrices.push();
            matrices.translate(pxArr[i], pyArr[i], pzArr[i]);
            matrices.multiply(mc.gameRenderer.getCamera().getRotation());
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(tiltArr[i]));

            Matrix4f m = matrices.peek().getPositionMatrix();

            // пропорции меча (остриё вниз): s — полная длина
            float bladeTipY = -size * 0.50f;   // остриё
            float bladeBaseY = size * 0.18f;   // основание клинка у гарды
            float wb = size * 0.055f;          // полуширина клинка
            float shoulderY = -size * 0.30f;   // начало скоса к острию

            int bladeCol = ColorUtil.replAlpha(
                    ColorUtil.overCol(ColorUtil.getColor(225, 232, 245), redColor, atts),
                    (int) (alphaPC * 200));
            int edgeCol = ColorUtil.replAlpha(color, (int) (alphaPC * 160));
            int guardCol = ColorUtil.replAlpha(
                    ColorUtil.overCol(ColorUtil.fade(90), redColor, atts), (int) (alphaPC * 220));
            int gripCol = ColorUtil.replAlpha(
                    ColorUtil.overCol(ColorUtil.getColor(120, 90, 60), redColor, atts), (int) (alphaPC * 210));
            int pommelCol = guardCol;

            // клинок: вытянутый шестиугольник остриём вниз (2 квада)
            fillBuf.vertex(m, 0, bladeTipY, 0).color(bladeCol);
            fillBuf.vertex(m, -wb, shoulderY, 0).color(edgeCol);
            fillBuf.vertex(m, -wb, bladeBaseY, 0).color(bladeCol);
            fillBuf.vertex(m, -wb * 0.4f, bladeBaseY, 0).color(bladeCol);

            fillBuf.vertex(m, wb * 0.4f, bladeBaseY, 0).color(bladeCol);
            fillBuf.vertex(m, wb, bladeBaseY, 0).color(bladeCol);
            fillBuf.vertex(m, wb, shoulderY, 0).color(edgeCol);
            fillBuf.vertex(m, 0, bladeTipY, 0).color(bladeCol);

            // дол: тонкая светлая линия вдоль середины клинка
            xyRibbon(fillBuf, m, 0, bladeTipY + size * 0.06f, 0, bladeBaseY - size * 0.02f,
                    size * 0.012f, ColorUtil.replAlpha(ColorUtil.getColor(255), (int) (alphaPC * 150)));

            // гарда: горизонтальная перекладина
            xyRibbon(fillBuf, m, -size * 0.13f, bladeBaseY + size * 0.02f,
                    size * 0.13f, bladeBaseY + size * 0.02f, size * 0.032f, guardCol);

            // рукоять
            xyRibbon(fillBuf, m, 0, bladeBaseY + size * 0.04f,
                    0, bladeBaseY + size * 0.17f, size * 0.024f, gripCol);

            // навершие
            float py = bladeBaseY + size * 0.20f;
            float pq = size * 0.028f;
            fillBuf.vertex(m, -pq, py - pq, 0).color(pommelCol);
            fillBuf.vertex(m, pq, py - pq, 0).color(pommelCol);
            fillBuf.vertex(m, pq, py + pq, 0).color(pommelCol);
            fillBuf.vertex(m, -pq, py + pq, 0).color(pommelCol);

            matrices.pop();
        }

        matrices.pop();
    }




    private static void drawGradientQuad(VertexConsumer buffer, Matrix4f matrix,int color,int color2,int color3,int color4, int alpha) {

        buffer.vertex(matrix, -0.5f, -0.5f, 0.0f).color(ColorUtil.replAlpha(color, alpha)).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, 0.5f, -0.5f, 0.0f).color(ColorUtil.replAlpha(color2, alpha)).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, 0.5f, 0.5f, 0.0f).color(ColorUtil.replAlpha(color3, alpha)).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, -0.5f, 0.5f, 0.0f).color(ColorUtil.replAlpha(color4, alpha)).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
    }

}

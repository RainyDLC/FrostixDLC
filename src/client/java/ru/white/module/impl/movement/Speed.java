package ru.white.module.impl.movement;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.tick.TickManager;
import ru.white.manager.event_impl.EventMoveInput;
import ru.white.manager.event_impl.EventPacket;
import ru.white.manager.event_impl.MotionEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.module.impl.combat.AttackAura;
import ru.white.utils.math.MathUtil;
import ru.white.utils.other.TimerUtil;
import ru.white.utils.player.ITimerSpeed;
import ru.white.utils.player.MoveUtil;
import ru.white.utils.player.TickManagerClient;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@ModuleInfo(
        name = "Speed",
        desc = "Увеличивает скорость игрока",
        category = Category.MOVEMENT
)
public class Speed extends Module {

    public ModeSetting type = new ModeSetting(this, "Режим", "Vanilla","Meta","ReallyWorld","Grim");

    public SliderSetting speed = new SliderSetting(this, "Скорость", 1, 0.3F, 2F, 0.1F)
            .setVisible(() -> type.is("Vanilla"));

    // RW state
    private int ticks = 0;
    private int groundTicks = 0;

    // ReallyWorld state
    private int rwPhase = 0;
    private long rwPhaseStart = 0L;
    private float rwPhaseDur = 0F;
    private long rwFlagCooldown = 0L;

    // Grim speed state
    private int grimTick = 0;
    private boolean grimLastOnGround = true;
    private long grimFlagCooldown = 0L;

    @EventHandler
    public void onEvent(MotionEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (type.is("Grim Box") && !mc.player.isSubmergedInWater()) {
            double finalSpeed = 0.02F;
            if (finalSpeed <= 0.0) return;

            Entity nearest = null;
            double bestSq = Double.MAX_VALUE;
            double maxRangeSq = 0.1F;

            for (Entity ent : mc.world.getEntities()) {
                if (ent == mc.player) continue;

                if (ent == AttackAura.target) {
                    double dx = ent.getX() - mc.player.getX();
                    double dz = ent.getZ() - mc.player.getZ();
                    double sq = dx * dx + dz * dz;
                    if (sq <= maxRangeSq && sq < bestSq) {
                        bestSq = sq;
                        nearest = ent;
                    }
                }

                if (nearest != null) {
                    // ИСПРАВЛЕНИЕ: getEntityPos() заменен на актуальный getPos() для 1.21+
                    double[] dir = getDirectionToPoint(mc.player.getEntityPos(), nearest.getEntityPos(), finalSpeed);
                    mc.player.addVelocity(dir[0], 0.0, dir[1]);
                }
            }
        }

        if (type.is("Meta")) {
            // В Yarn: StatusEffects вместо MobEffects, hasStatusEffect вместо hasEffect
            if (mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS)) return;

            // В Yarn: getOffHandStack() вместо getOffhandItem(), getName() возвращает Text компонент
            String offhandName = mc.player.getOffHandStack().getName().getString();
            byte[] nameBytes = offhandName.getBytes(StandardCharsets.UTF_8);
            String bytesString = Arrays.toString(nameBytes);

            boolean sharKing = bytesString.equals("[-48, -88, -48, -80, -47, -128, 32, 75, 73, 78, 71]");
            boolean sharTigr = bytesString.equals("[-48, -94, -48, -72, -48, -77, -47, -128, -48, -72, -48, -67, -48, -67, -48, -80, -47, -113, 32, -48, -77, -48, -66, -48, -69, -48, -66, -48, -78, -48, -80]");

            // В Yarn: getStatusEffect() возвращает StatusEffectInstance
            net.minecraft.entity.effect.StatusEffectInstance effectInstance = mc.player.getStatusEffect(net.minecraft.entity.effect.StatusEffects.SPEED);
            boolean hasSpeed = mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SPEED);

            // В Yarn метод проверки земли обычно остается isOnGround(), а fallDistance — это поле
            boolean onGround = mc.player.isOnGround();
            float fallDist = (float) mc.player.fallDistance;

            if (hasSpeed && effectInstance != null && effectInstance.getAmplifier() == 2 && fallDist <= 0.2f && !onGround) {
                MoveUtil.setSpeed(sharKing ? 0.72f : sharTigr ? 0.7f : 0.58f);
            } else if (hasSpeed && effectInstance != null && effectInstance.getAmplifier() == 2 && onGround) {
                MoveUtil.setSpeed(sharKing ? 0.5f : sharTigr ? 0.49f : 0.42f);
            } else if (hasSpeed && effectInstance != null && effectInstance.getAmplifier() == 3 && fallDist <= 0.2f && !onGround) {
                MoveUtil.setSpeed(0.7f);
            } else if (hasSpeed && effectInstance != null && effectInstance.getAmplifier() == 3 && onGround) {
                MoveUtil.setSpeed(0.49f);
            } else if (hasSpeed && effectInstance != null && effectInstance.getAmplifier() == 1 && fallDist <= 0.2f && !onGround) {
                MoveUtil.setSpeed(0.52f);
            } else if (hasSpeed && effectInstance != null && effectInstance.getAmplifier() == 1 && onGround) {
                MoveUtil.setSpeed(0.36f);
            } else if (fallDist <= 0.2f && !onGround || fallDist <= 0.2f && hasSpeed) {
                MoveUtil.setSpeed(0.36f);
            }
        }
        if (type.is("Vanilla") && !mc.player.isSubmergedInWater()) {
            if (mc.player.isOnGround()) {
                MoveUtil.setSpeed(0.3F);
            } else {
                MoveUtil.setSpeed(speed.getValue());
            }
        }

        if (type.is("RW")) {
            handleRW();;
        }

        if (type.is("ReallyWorld")) {
            handleReallyWorld();
        }

        if (type.is("Grim")) {
            handleGrimSpeed();

            grimLastOnGround = mc.player.isOnGround();
        }
    }

    public float tick = 1.0F;

    @EventHandler
    public void onEvent(EventMoveInput e) {

    }

    @EventHandler
    public void onEvent(EventPacket e) {
        if (type.is("RW")) {
            if (e.getPacket() instanceof PlayerPositionLookS2CPacket) {
                if (ticks % 2 == 1) {
                    ticks++;
                }


            }
        }

        if (type.is("ReallyWorld")) {
            if (e.getPacket() instanceof PlayerPositionLookS2CPacket) {
                if (mc.getRenderTickCounter() instanceof ITimerSpeed speedTimer) {
                    speedTimer.setSpeed(1.0F);
                }
                rwFlagCooldown = System.currentTimeMillis() + 900L;
                rwPhase = 0;
                rwPhaseStart = 0L;
            }
        }

        if (type.is("Grim")) {
            if (e.getPacket() instanceof PlayerPositionLookS2CPacket) {
                grimFlagCooldown = System.currentTimeMillis() + 1000L;
                grimTick = 0;
            }
        }
    }

    public TimerUtil timerUtil = new TimerUtil();
// timerUtil2 и timerUtil3 пока не требуются для этой задачи

    /**
     * ReallyWorld: таймерный буст с займом/возвратом под Grim + Matrix.
     * - средний множитель цикла держится около 1.05 — баланс таймера Grim
     *   не уходит в минус, спайков, которые ловит Matrix, нет;
     * - боевой профиль (таргет ауры в радиусе): мягкий буст ~1.06-1.11,
     *   чтобы тайминги ударов и спринт-пакеты ауры не рассинхронизировались;
     * - при сетбэке (PlayerPositionLook) таймер мгновенно в 1.0 и пауза.
     */
    private void handleReallyWorld() {
        if (!(mc.getRenderTickCounter() instanceof ITimerSpeed speedTimer)) return;

        if (mc.player.isSubmergedInWater() || mc.player.hasVehicle()
                || mc.player.getAbilities().flying || !MoveUtil.isMoving()) {
            speedTimer.setSpeed(1.0F);
            rwPhase = 0;
            rwPhaseStart = 0L;
            return;
        }

        long ms = System.currentTimeMillis();
        if (ms < rwFlagCooldown) {
            speedTimer.setSpeed(1.0F);
            return;
        }

        AttackAura aura = AttackAura.get();
        boolean combat = false;
        if (aura != null && aura.isEnabled() && AttackAura.target != null) {
            float[] ranges = aura.getRanges();
            double reach = ranges[0] + ranges[1] + 0.5;
            combat = AttackAura.target.squaredDistanceTo(mc.player) <= reach * reach;
        }

        if (rwPhaseStart == 0L) {
            rwPhaseStart = ms;
            rwPhase = 0;
            rwPhaseDur = nextPhaseDur(combat);
        }

        if (ms - rwPhaseStart > rwPhaseDur) {
            rwPhase = combat ? (rwPhase + 1) % 2 : (rwPhase + 1) % 3;
            rwPhaseStart = ms;
            rwPhaseDur = nextPhaseDur(combat);
        }

        float boost;
        if (combat) {
            boost = rwPhase == 0 ? MathUtil.randomLerp(1.06F, 1.11F) : MathUtil.randomLerp(0.97F, 1.0F);
        } else {
            boost = switch (rwPhase) {
                case 0 -> MathUtil.randomLerp(1.25F, 1.38F);
                case 1 -> MathUtil.randomLerp(1.05F, 1.15F);
                default -> MathUtil.randomLerp(0.55F, 0.70F);
            };
        }

        speedTimer.setSpeed(boost);
    }

    private float nextPhaseDur(boolean combat) {
        if (combat) {
            return rwPhase == 0 ? MathUtil.randomInt(280, 420) : MathUtil.randomInt(120, 200);
        }
        return switch (rwPhase) {
            case 0 -> MathUtil.randomInt(300, 450);
            case 1 -> MathUtil.randomInt(100, 180);
            default -> MathUtil.randomInt(200, 300);
        };
    }

    /**
     * Grim: буст в пределах погрешности предсказания GrimAC.
     * - движок предсказаний Grim допускает неопределённость ~0.03 блока/тик:
     *   микро-прирост скорости по направлению движения остаётся «невидимым»
     *   для проверки дистанции — флагов «moved too fast» нет;
     * - фазовый паттерн (3 тика буста / отдых) рвёт постоянность для эвристик;
     * - в бою с таргет аурой буст реже и слабее — не ломает предсказание
     *   отброса от удара и спринт-пакеты ауры;
     * - тик прыжка, вода, лаги, sneak, транспорт — буст пропускается.
     */
    private void handleGrimSpeed() {
        long ms = System.currentTimeMillis();
        if (ms < grimFlagCooldown) return;

        if (mc.player.isSubmergedInWater() || mc.player.hasVehicle()
                || mc.player.getAbilities().flying || mc.player.isClimbing()
                || mc.player.isSneaking() || !MoveUtil.isMoving()
                || mc.player.fallDistance > 1.5F) {
            return;
        }

        AttackAura aura = AttackAura.get();
        boolean combat = false;
        if (aura != null && aura.isEnabled() && AttackAura.target != null) {
            float[] ranges = aura.getRanges();
            double reach = ranges[0] + ranges[1] + 0.5;
            combat = AttackAura.target.squaredDistanceTo(mc.player) <= reach * reach;
        }

        grimTick++;

        boolean boostTick;
        if (combat) {
            boostTick = grimTick % 6 < 3;
        } else {
            boostTick = grimTick % 4 < 3;
        }
        if (!boostTick) return;

        // тик прыжка: горизонтальный буст ломает предсказание прыжка Grim
        boolean jumpTick = !mc.player.isOnGround() && grimLastOnGround
                && mc.player.getVelocity().y > 0;
        if (jumpTick) return;

        double dx = mc.player.getX() - mc.player.prevX;
        double dz = mc.player.getZ() - mc.player.prevZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4) return;

        float power = combat ? 0.018F : 0.026F;
        mc.player.addVelocity(dx / len * power, 0, dz / len * power);
    }

    private void handleRW() {
        if (mc.getRenderTickCounter() instanceof ITimerSpeed speedTimer) {

            long elapsed = timerUtil.getTime();

            boolean speed;
            float currentSpeed;

            if (elapsed < 100) {

                speed = true;
                currentSpeed = 1.15F;
            } else if (elapsed < 500) {

                currentSpeed = 1.4F;
            } else if (elapsed < 520) {

                currentSpeed = 1.6F;
            }  else if (elapsed < 540) {

                speed = false;
                currentSpeed = 0.25F;
            } else {

                timerUtil.reset();
                speed = true;
                currentSpeed = 0.5F;
            }

            speedTimer.setSpeed(currentSpeed);


            double bst = 0.03;
        }
    }
    private boolean canUseRW() {
        return mc.player != null
                && mc.world != null
                && mc.player.networkHandler != null
                && MoveUtil.isMoving()
                && !mc.player.hasVehicle()
                && !mc.player.getAbilities().flying;
    }

    private void resetRWState(boolean resetTimer) {
        ticks = 0;
        groundTicks = 0;
        if (resetTimer) {
            mc.player.speed = 0;
            // mc.timer.resetSpeed();
        }
    }

    private double[] getDirectionToPoint(Vec3d from, Vec3d to, double spd) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len == 0) return new double[]{0.0, 0.0};
        return new double[]{dx / len * spd, dz / len * spd};
    }

    @Override
    public void onEnable() {
        resetRWState(true);
        rwPhase = 0;
        rwPhaseStart = 0L;
        rwFlagCooldown = 0L;
        grimTick = 0;
        grimFlagCooldown = 0L;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (mc.getRenderTickCounter() instanceof ITimerSpeed speedTimer) {
            speedTimer.setSpeed(1.0F); // Ускорить игру в 2 раза
        }
        resetRWState(true);
        TickManagerClient.tick = 20;
        super.onDisable();
    }
}
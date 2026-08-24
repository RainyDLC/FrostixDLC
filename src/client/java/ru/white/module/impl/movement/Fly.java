package ru.white.module.impl.movement;

import net.minecraft.block.SignBlock;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SignItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.white.manager.event_impl.MotionEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.manager.rotation.Rotation;
import ru.white.manager.rotation.RotationProcess;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.other.TimerUtil;

import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(
        name = "Fly",
        desc = "Полёт на табличках (обход Polar / FunTime)",
        category = Category.MOVEMENT
)
public class Fly extends Module {

    public ModeSetting type = new ModeSetting(this, "Режим", "FunTime", "Vanilla");

    public BooleanSetting autoJump = new BooleanSetting(this, "Автопрыжок", true)
            .setVisible(() -> type.is("FunTime"));

    public SliderSetting placeDelay = new SliderSetting(this, "Задержка установки", 120, 70, 350, 5)
            .setVisible(() -> type.is("FunTime"));

    public SliderSetting flySpeed = new SliderSetting(this, "Скорость", 1, 0.1F, 3F, 0.05F)
            .setVisible(() -> type.is("Vanilla"));

    private final TimerUtil placeTimer = new TimerUtil();
    private final TimerUtil jumpTimer = new TimerUtil();
    private BlockPos lastPlacedCell = null;

    @Override
    protected void onEnable() {
        placeTimer.reset();
        jumpTimer.reset();
        lastPlacedCell = null;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        if (mc.player != null && type.is("Vanilla")) {
            mc.player.getAbilities().flying = false;
            mc.player.sendAbilitiesUpdate();
        }
        super.onDisable();
    }

    @EventHandler
    public void onMotion(MotionEvent e) {
        if (mc.player == null || mc.world == null) return;

        if (type.is("Vanilla")) {
            handleVanilla();
            return;
        }
        if (type.is("FunTime")) {
            handleSignFly(e);
        }
    }

    /**
     * Непрерывный подъём на табличках под Polar (FunTime):
     * - редактор таблички закрывается автоматически — ничто не прерывает цикл;
     * - отскок в тот же тик приземления, установка ещё в полёте — без пауз;
     * - поворот доводится каждый тик, установка только при валидном взгляде —
     *   Polar проверяет ротацию на интеракции;
     * - точка клика джиттерится, задержки плавают — нет машинного паттерна.
     */
    private void handleSignFly(MotionEvent e) {
        if (mc.player.hasVehicle()
                || mc.player.getAbilities().flying
                || mc.player.isSubmergedInWater()
                || mc.player.isInLava()
                || mc.player.isClimbing()) return;

        // редактор таблички сам себя закрывает — цикл не прерывается
        if (mc.currentScreen instanceof SignEditScreen) {
            mc.setScreen(null);
            return;
        }

        // Shift — плавное снижение, ничего не делаем
        if (mc.player.input.playerInput.sneak()) return;
        if (mc.player.isUsingItem()) return;

        int signSlot = findSignSlot();
        if (signSlot == -1) return;

        // клиентская коллизия: на Funtime таблички твёрдые, в ванилле — нет.
        // Сами держим игрока на верхней грани последней поставленной таблички,
        // иначе клиент проваливается сквозь неё и рассинхрон флагает Polar'ом.
        simulateSignCollision();

        // мгновенный отскок: прыгаем в тот же тик приземления
        if (autoJump.getValue() && mc.player.isOnGround() && jumpTimer.finished(jitter(35))) {
            mc.player.jump();
            mc.player.fallDistance = 0;
            jumpTimer.reset();
        }

        BlockPos target = getTargetCell();
        if (target == null) return;

        BlockHitResult hit = findAnchorHit(target);
        if (hit == null) return;

        // доводим взгляд каждый тик — установка только по валидной ротации
        lookAt(hit.getPos());

        if (!placeTimer.finished(jitter(placeDelay.getValue()))) return;
        if (!rotationCloseEnough(hit)) return;

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(signSlot);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, jitterHit(hit));
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.getInventory().setSelectedSlot(oldSlot);

        lastPlacedCell = target;
        placeTimer.reset();
    }

    /**
     * Симуляция твёрдой таблички: при падении на последнюю поставленную ячейку
     * ставим игрока на её верхнюю грань и объявляем землю. Позиции клиента и
     * сервера совпадают — Polar не видит рассинхрона.
     */
    private void simulateSignCollision() {
        if (lastPlacedCell == null || mc.player.isOnGround()) return;
        if (mc.player.getVelocity().y > 0) return;

        double top = lastPlacedCell.getY() + 1.0;
        Vec3d p = mc.player.getEntityPos();
        double dx = p.x - (lastPlacedCell.getX() + 0.5);
        double dz = p.z - (lastPlacedCell.getZ() + 0.5);
        if (dx * dx + dz * dz > 0.42) return;
        if (p.y < top - 0.8 || p.y > top + 0.2) return;

        mc.player.setPosition(p.x, top, p.z);
        mc.player.setVelocity(mc.player.getVelocity().x, 0, mc.player.getVelocity().z);
        mc.player.setOnGround(true);
        mc.player.fallDistance = 0;
    }

    /** Polar: интеракция засчитывается только при правдоподобном взгляде. */
    private boolean rotationCloseEnough(BlockHitResult hit) {
        Vec3d delta = hit.getPos().subtract(mc.player.getEyePos());
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        float yaw = (float) (MathHelper.atan2(delta.z, delta.x) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-(MathHelper.atan2(delta.y, horiz) * 180.0 / Math.PI));

        float yawDiff = Math.abs(MathHelper.wrapDegrees(yaw - Rotation.cameraYaw()));
        float pitchDiff = Math.abs(pitch - Rotation.cameraPitch());
        return Math.hypot(yawDiff, pitchDiff) <= 25.0F;
    }

    /** Лёгкий джиттер точки клика внутри грани — против паттернов установки. */
    private BlockHitResult jitterHit(BlockHitResult hit) {
        Vec3d p = hit.getPos();
        Vec3d jittered = p.add(
                ThreadLocalRandom.current().nextDouble(-0.03, 0.03),
                ThreadLocalRandom.current().nextDouble(-0.03, 0.03),
                ThreadLocalRandom.current().nextDouble(-0.03, 0.03));
        return new BlockHitResult(jittered, hit.getSide(), hit.getBlockPos(), hit.isInsideBlock());
    }

    /**
     * Ячейка чуть ниже ног: во время прыжка это последняя освобождённая клетка —
     * именно туда кладём табличку, чтобы приземлиться сверху и прыгнуть снова.
     */
    private BlockPos getTargetCell() {
        Vec3d pos = mc.player.getEntityPos();
        BlockPos cell = BlockPos.ofFloored(pos.x, pos.y - 0.4, pos.z);

        if (!mc.world.getBlockState(cell).isReplaceable()) return null;
        if (mc.player.getEyePos().distanceTo(Vec3d.ofCenter(cell)) > 5.0) return null;

        return cell;
    }

    /**
     * Ищем соседний блок, кликом по грани которого табличка встанет в target.
     * Приоритет: блок снизу (грань вверх) -> боковые -> верх.
     * Предыдущая табличка снизу — валидный якорь (на Funtime знаки твёрдые),
     * хотя её коллизия в ванилле пустая.
     */
    private BlockHitResult findAnchorHit(BlockPos target) {
        Direction[] order = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP};

        for (Direction dir : order) {
            BlockPos neighbor = target.offset(dir);
            var state = mc.world.getBlockState(neighbor);

            if (state.isReplaceable()) continue;

            boolean isSign = state.getBlock() instanceof SignBlock;
            if (!isSign && state.getCollisionShape(mc.world, neighbor).isEmpty()) continue;

            Vec3d point = Vec3d.ofCenter(neighbor).add(
                    Vec3d.of(dir.getOpposite().getVector()).multiply(0.5));

            if (mc.player.getEyePos().distanceTo(point) > 4.5) continue;

            return new BlockHitResult(point, dir.getOpposite(), neighbor, false);
        }
        return null;
    }

    private void lookAt(Vec3d point) {
        Vec3d delta = point.subtract(mc.player.getEyePos());
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        float yaw = (float) (MathHelper.atan2(delta.z, delta.x) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-(MathHelper.atan2(delta.y, horiz) * 180.0 / Math.PI));

        // быстрый, но не мгновенный доворот — Polar ловит телепорт-повороты
        RotationProcess.update(new Rotation(yaw, pitch), 170, 170, 0, 50);
    }

    private long jitter(double base) {
        return (long) base + ThreadLocalRandom.current().nextLong(-15, 16);
    }

    private int findSignSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof SignItem) return i;
        }
        return -1;
    }

    private void handleVanilla() {
        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().setFlySpeed(flySpeed.getValue() * 0.1F);

        Vec2Input input = getInput();
        double yawRad = Math.toRadians(mc.player.getYaw());

        double x = 0, z = 0;
        if (input.forward != 0) {
            x -= Math.sin(yawRad) * input.forward;
            z += Math.cos(yawRad) * input.forward;
        }
        if (input.strafe != 0) {
            x += Math.cos(yawRad) * input.strafe;
            z += Math.sin(yawRad) * input.strafe;
        }

        double vy = 0;
        if (mc.options.jumpKey.isPressed()) vy = flySpeed.getValue() * 0.5;
        else if (mc.player.input.playerInput.sneak()) vy = -flySpeed.getValue() * 0.5;

        mc.player.setVelocity(x, vy, z);
        mc.player.fallDistance = 0;
        mc.player.setOnGround(false);

        if (mc.world.getTime() % 20 == 0) {
            mc.player.sendAbilitiesUpdate();
        }
    }

    private Vec2Input getInput() {
        var move = mc.player.input.getMovementInput();
        return new Vec2Input(move.y, move.x);
    }

    private record Vec2Input(float forward, float strafe) {
    }
}

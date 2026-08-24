package ru.white.module.impl.movement;


import ru.white.Client;
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
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

@ModuleInfo(
        name = "Spider",
        desc = "Позволяет лазить по стенам",
        category = Category.MOVEMENT
)
public class Spider extends Module {

    public ModeSetting modeSpider = new ModeSetting(this,"Режим","Matrix","FT - 2","FT - 2 2","Grim");

    public BooleanSetting grimBoost = new BooleanSetting(this, "Буст прыжка", true)
            .setVisible(() -> modeSpider.is("Grim"));
    public SliderSetting boostPower = new SliderSetting(this, "Сила буста", 0.0008f, 0.0002f, 0.0009f, 0.0001f)
            .setVisible(() -> modeSpider.is("Grim"));

    private double lastGrimY = Double.NaN;
    private int grimSetbackPause = 0;

    private final TimerUtil timerUtil = new TimerUtil();
    TimerUtil placeTimer = new TimerUtil();

    @EventHandler
    public void onMotion(MotionEvent e) {
        Direction facing = mc.player.getHorizontalFacing();



        BlockPos posInFront = BlockPos.ofFloored(mc.player.getEntityPos()).offset(facing);
        BlockState stateInFront = mc.world.getBlockState(posInFront);
        Block blockInFront = stateInFront.getBlock();

        BlockPos posBelow = BlockPos.ofFloored(mc.player.getEntityPos());
        BlockState stateBelow = mc.world.getBlockState(posBelow);
        Block blockBelow = stateBelow.getBlock();

        boolean penisF = blockInFront instanceof TrapdoorBlock
                && Boolean.TRUE.equals(stateInFront.get(Properties.OPEN))
                && stateInFront.contains(Properties.HORIZONTAL_FACING);

        boolean penisB = blockBelow instanceof TrapdoorBlock
                && Boolean.TRUE.equals(stateBelow.get(Properties.OPEN))
                && stateBelow.contains(Properties.HORIZONTAL_FACING);

        boolean xuiF = blockInFront instanceof FenceBlock
                || stateInFront.isIn(BlockTags.WALLS)
                || blockInFront instanceof FenceGateBlock
                || blockInFront instanceof LanternBlock
                || blockInFront instanceof LightningRodBlock
                || penisF;

        boolean glubgseB = blockBelow instanceof FenceBlock
                || stateBelow.isIn(BlockTags.WALLS)
                || blockBelow instanceof FenceGateBlock
                || blockBelow instanceof LanternBlock
                || blockBelow instanceof LightningRodBlock
                || penisB;
        // ===== GrimAC 2.0 =====
        // Вертикаль у стены у грима замкнута предиктом (Simulation.threshold = 0.001,
        // immediate-setback = 0.1). Подъём возможен ТОЛЬКО прыжком с реального
        // онграунда — всё остальное грим симулирует как падение.
        //
        // Разбор флагов из теста: накопительный дожим каждый тик расширял разрыв
        // d = 0.98*d + b (грим пересинхронизирует свою скорость с ближайшим
        // предсказанным вектором, а буст оставался в клиентской скорости и
        // протаскивался через гравитацию) -> на 2-й тик d = 0.0018 > порога ->
        // сетбэк -> прыжки во время подтверждения телепорта -> каскад
        // GroundSpoof "claimed true" и Simulation 0.40-0.42.
        //
        // Теперь: чистый ванильный прыжок по факту приземления (грим сам его
        // предсказывает, офсет ~1e-16) + ОДНОРАЗОВЫЙ буст в тике прыжка — его
        // расхождение затухает как 0.98^n и никогда не накапливается.
        if (modeSpider.is("Grim")) {
            // детект телепорта (сетбэк грима / /tp): сдвиг Y > 1.5 за один тик
            if (!Double.isNaN(lastGrimY) && Math.abs(mc.player.getY() - lastGrimY) > 1.5) {
                grimSetbackPause = 5;
            }
            lastGrimY = mc.player.getY();

            if (grimSetbackPause > 0) {
                grimSetbackPause--;
            } else if (hozColl()
                    && mc.options.forwardKey.isPressed()
                    && mc.player.isOnGround()
                    && mc.player.isAlive()
                    && !mc.player.hasVehicle()
                    && !mc.player.isTouchingWater()
                    && !mc.player.isSubmergedInWater()
                    && !mc.player.isClimbing()
                    && !standingOnEntity()
                    && !conflictingMovementModule()) {

                mc.player.jump();
                mc.player.fallDistance = 0;

                if (grimBoost.getValue()) {
                    Vec3d vel = mc.player.getVelocity();
                    mc.player.setVelocity(vel.x, vel.y + boostPower.getValue(), vel.z);
                }
            }
        }

        if (modeSpider.is("Matrix")) {

            ;

            if (timerUtil.finished(70) && hozColl()) {
                e.ground(true);
                mc.player.setOnGround(true);
                mc.player.jump();
                mc.player.fallDistance = 0;
                timerUtil.reset();
            }
        }
        if (modeSpider.is("FT - 2 2") && hozColl() && !mc.player.isOnGround()) {


            RotationProcess.update(new Rotation(mc.gameRenderer.getCamera().getYaw(), 60), 255, 255, 0, 50);


            BlockHitResult hitResult = (BlockHitResult) mc.crosshairTarget;
            BlockPos hitPos = hitResult.getBlockPos();
            BlockState hitState = mc.world.getBlockState(hitPos);

            boolean isTopSide = hitResult.getSide() == Direction.UP;
            boolean isSolidBlockOrRod = !hitState.isReplaceable();
            boolean isSpaceFree = mc.world.getBlockState(hitPos.up()).isReplaceable();


            if (placeTimer.hasTimeElapsed(70)) {

                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                mc.player.swingHand(Hand.MAIN_HAND);

                placeTimer.reset();

                if (timerUtil.finished(90)) {
                    e.ground(true);
                    mc.player.setOnGround(true);
                    mc.player.jump();
                    mc.player.fallDistance = 0;
                    timerUtil.reset();
                }
            }
        }
        if (modeSpider.is("FT - 2") && hozColl() && !mc.player.isOnGround()) {
            int rodSlot = findLightningRodBlockInHotBar();

            if (rodSlot != -1) {


               // RotationProcess.update(new Rotation(mc.gameRenderer.getCamera().getYaw(),60),255,255,0,50);


                BlockHitResult hitResult = (BlockHitResult) mc.crosshairTarget;
                BlockPos hitPos = hitResult.getBlockPos();
                BlockState hitState = mc.world.getBlockState(hitPos);

                boolean isTopSide = hitResult.getSide() == Direction.UP;
                boolean isSolidBlockOrRod = !hitState.isReplaceable();
                boolean isSpaceFree = mc.world.getBlockState(hitPos.up()).isReplaceable();



                if (placeTimer.hasTimeElapsed(120)) {
                    int oldSlot = mc.player.getInventory().getSelectedSlot();
                    mc.player.getInventory().setSelectedSlot(rodSlot);

                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                    mc.player.swingHand(Hand.MAIN_HAND);

                    mc.player.getInventory().setSelectedSlot(oldSlot);
                    placeTimer.reset();


                }
                if (timerUtil.finished(150)) {
                    e.ground(true);
                    mc.player.setOnGround(true);
                    mc.player.jump();
                    mc.player.fallDistance = 0;
                    timerUtil.reset();
                }
            }
        }

    }    private int findLightningRodBlockInHotBar() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.COPPER_TRAPDOOR) {
                return i;
            }
        }
        return -1;
    }

    public boolean hozColl() {
        return mc.player.horizontalCollision;
    }

    /**
     * Стоим на энтити (лодка, моб): клиент даёт onGround=true, а грим
     * симулирует воздух -> прыжок оттуда = GroundSpoof "claimed true".
     * Предметы/опыт не считаются — только живность и лодки.
     */
    private boolean standingOnEntity() {
        Box box = mc.player.getBoundingBox().stretch(0, -0.2, 0);
        for (Entity entity : mc.world.getOtherEntities(mc.player, box)) {
            if (entity instanceof LivingEntity || entity instanceof BoatEntity) return true;
        }
        return false;
    }

    /**
     * Другие movement-модули правят скорость одновременно с нами —
     * их правки + наш прыжок = гарантированный Simulation. Не мешаем им.
     */
    private boolean conflictingMovementModule() {
        return Client.get().moduleManager().get(Fly.class).isEnabled()
                || Client.get().moduleManager().get(Speed.class).isEnabled()
                || Client.get().moduleManager().get(WaterSpeed.class).isEnabled()
                || Client.get().moduleManager().get(AirStuck.class).isEnabled()
                || Client.get().moduleManager().get(NoWeb.class).isEnabled();
    }
}

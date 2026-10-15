package dev.hatek.client.module.impl.combat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hatek.client.module.Category;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.setting.BindSetting;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.MultiSetting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * AutoMace — удары булавой с выбором режима.
 *
 * <p>Легитный: visibly берёт булаву в руку (остаётся в руке) и бьёт.
 * Пакетный: тихо свапает булаву из хотбара пакетами под каждый удар и возвращает слот.</p>
 *
 * <p>Бьёт только в воздухе: на земле модуль ждёт. С включённым Wait Crit удар
 * откладывается до настоящего крит-момента (падение, без воды/эффектов/полёта).</p>
 *
 * <p>Заряд ветра: бинд (Wind Key, клавиша или кнопка мыши) бросает виндчард
 * строго под себя. Ищет в хотбаре,
 * при включённом Wind From Inventory подтягивает из инвентаря шифт-кликом.</p>
 */
public final class AutoMace extends Module {
    public static final String LEGIT = "Легитный";
    public static final String PACKET = "Пакетный";

    public static final String PLAYERS = "Players";
    public static final String NAKED = "Naked";
    public static final String MOBS = "Mobs";
    public static final String ANIMALS = "Animals";
    public static final String FRIENDS = "Friends";

    /** Падение, с которого булава даёт smash-удар. */
    private static final float SMASH_FALL = 1.5F;
    /** Зарядка для удара — только полная, как у ауры (сервер даёт урон при scale > 0.9). */
    private static final float CHARGE_NEED = 0.9F;
    /** Сколько тиков ждём серверный мув предмета после шифт-клика. */
    private static final int MOVE_WAIT_TICKS = 8;

    private static AutoMace instance;

    private final ModeSetting mode = new ModeSetting("Mode", 0, LEGIT, PACKET);
    private final MultiSetting targets = new MultiSetting("Targets",
            List.of(PLAYERS, NAKED, MOBS, ANIMALS, FRIENDS), PLAYERS, NAKED, MOBS);
    private final SliderSetting range = new SliderSetting("Range", 3.2, 3.0, 6.0, 0.1);
    private final BoolSetting aim = new BoolSetting("Aim", true);
    private final BoolSetting waitCrit = new BoolSetting("Wait Crit", true);
    private final BoolSetting smashOnly = new BoolSetting("Only Smash", false);
    private final BoolSetting throughWalls = new BoolSetting("Through Walls", false);
    private final BindSetting windKey = new BindSetting("Wind Key");
    private final BoolSetting autoWind = new BoolSetting("Auto Wind", false);
    private final BoolSetting windFromInventory = new BoolSetting("Wind From Inventory", true);
    private final SliderSetting windCooldown = new SliderSetting("Wind Cooldown", 800, 200, 2000, 50);

    private LivingEntity target;
    private boolean windWasDown;
    private long lastWindMs;
    private int moveWaitTicks;

    public AutoMace() {
        super("AutoMace", "Hits with mace, throws wind charge under you", Category.COMBAT);
        instance = this;
        with(this.mode, this.targets, this.range, this.aim, this.waitCrit, this.smashOnly,
                this.throughWalls, this.windKey, this.autoWind, this.windFromInventory,
                this.windCooldown);
    }

    public static AutoMace instance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    private void resetState() {
        this.target = null;
        this.windWasDown = false;
        this.lastWindMs = 0L;
        this.moveWaitTicks = 0;
    }

    @Override
    public void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        if (mc.gui.screen() != null) {
            this.windWasDown = false;
            this.moveWaitTicks = 0;
            return;
        }
        if (!mc.player.isAlive()) {
            this.target = null;
            return;
        }

        pollWindKey(mc);

        if (this.moveWaitTicks > 0) {
            this.moveWaitTicks--;
            return;
        }

        updateTarget(mc);

        if (this.autoWind.value() && this.target != null && mc.player.onGround()
                && mc.player.fallDistance < SMASH_FALL && !mc.player.isUsingItem()) {
            useWind(mc);
        }

        if (this.target != null) {
            tryAttack(mc, this.target);
        }
    }

    // ---------- wind charge ----------

    private void pollWindKey(Minecraft mc) {
        if (!this.windKey.bound()) {
            this.windWasDown = false;
            return;
        }
        boolean down = this.windKey.isDown(mc);
        if (down && !this.windWasDown) {
            useWind(mc);
        }
        this.windWasDown = down;
    }

    private void useWind(Minecraft mc) {
        LocalPlayer self = mc.player;
        if (self == null || self.isUsingItem()) {
            return;
        }
        if (System.currentTimeMillis() - this.lastWindMs < (long) this.windCooldown.value()) {
            return;
        }
        int slot = findWindSlot(mc, true);
        if (slot < 0) {
            return;
        }
        int prevSlot = self.getInventory().getSelectedSlot();
        float prevPitch = self.getXRot();

        self.getInventory().setSelectedSlot(slot);
        sendCarried(mc, slot);

        self.setXRot(90.0F);
        mc.gameMode.useItem(self, InteractionHand.MAIN_HAND);

        self.setXRot(prevPitch);
        self.getInventory().setSelectedSlot(prevSlot);
        sendCarried(mc, prevSlot);

        this.lastWindMs = System.currentTimeMillis();
    }

    /** Слот виндчарда в хотбаре; при moveAllowed подтягивает из инвентаря шифт-кликом. */
    private int findWindSlot(Minecraft mc, boolean moveAllowed) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.WIND_CHARGE)) {
                return i;
            }
        }
        if (!moveAllowed || !this.windFromInventory.value()) {
            return -1;
        }
        if (pullFromInventory(mc, Items.WIND_CHARGE)) {
            this.moveWaitTicks = MOVE_WAIT_TICKS;
        }
        return -1;
    }

    /**
     * Шифт-клик стака из основного инвентаря в хотбар.
     * Маппинг слотов резолвится через контейнерное меню, а не хардкодом.
     */
    private boolean pullFromInventory(Minecraft mc, net.minecraft.world.item.Item item) {
        LocalPlayer self = mc.player;
        if (self.containerMenu != self.inventoryMenu) {
            return false;
        }
        int invSlot = -1;
        for (int i = 9; i < 36; i++) {
            if (self.getInventory().getItem(i).is(item)) {
                invSlot = i;
                break;
            }
        }
        if (invSlot < 0) {
            return false;
        }
        int containerSlot = -1;
        for (int i = 0; i < self.containerMenu.slots.size(); i++) {
            Slot slot = self.containerMenu.slots.get(i);
            if (slot.container == self.getInventory() && slot.getContainerSlot() == invSlot) {
                containerSlot = i;
                break;
            }
        }
        if (containerSlot < 0) {
            return false;
        }
        mc.gameMode.handleContainerInput(self.containerMenu.containerId, containerSlot,
                0, ContainerInput.QUICK_MOVE, self);
        return true;
    }

    // ---------- mace attack ----------

    private void tryAttack(Minecraft mc, LivingEntity target) {
        LocalPlayer self = mc.player;
        if (self.isUsingItem() || self.isBlocking()) {
            return;
        }
        // Только воздух: на земле модуль ждёт, а не бьёт.
        if (self.onGround()) {
            return;
        }
        // Ждём настоящий крит-момент: падение без блокеров (вода, паутина-эффекты, полёт).
        if (this.waitCrit.value() && !critMoment(self)) {
            return;
        }
        if (this.smashOnly.value() && !(self.fallDistance > SMASH_FALL)) {
            return;
        }
        if (self.getAttackStrengthScale(0.5F) < CHARGE_NEED) {
            return;
        }
        int maceSlot = findMaceSlot(mc);
        if (maceSlot < 0) {
            return;
        }

        float[] rot = aimAt(self, target);
        boolean packet = this.mode.is(PACKET);

        if (this.aim.value() && !packet) {
            self.setYRot(rot[0]);
            self.setXRot(rot[1]);
        }

        if (packet) {
            if (this.aim.value() && mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                        rot[0], rot[1], self.onGround(), self.horizontalCollision));
            }
            int prevSlot = self.getInventory().getSelectedSlot();
            if (prevSlot != maceSlot) {
                sendCarried(mc, maceSlot);
            }
            mc.gameMode.attack(self, target);
            self.swing(InteractionHand.MAIN_HAND);
            if (prevSlot != maceSlot) {
                sendCarried(mc, prevSlot);
            }
        } else {
            if (self.getInventory().getSelectedSlot() != maceSlot) {
                self.getInventory().setSelectedSlot(maceSlot);
                sendCarried(mc, maceSlot);
            }
            mc.gameMode.attack(self, target);
            self.swing(InteractionHand.MAIN_HAND);
        }
    }

    private int findMaceSlot(Minecraft mc) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.MACE)) {
                return i;
            }
        }
        return -1;
    }

    /** Крит-момент по ванильной логике: падение (fallDistance > 0) без блокеров крита. */
    private static boolean critMoment(LocalPlayer self) {
        return self.fallDistance > 0.0F
                && !self.onClimbable()
                && !self.isInWater()
                && !self.isInLava()
                && !self.isPassenger()
                && !self.isFallFlying()
                && !self.hasEffect(MobEffects.BLINDNESS)
                && !self.hasEffect(MobEffects.SLOW_FALLING)
                && !self.hasEffect(MobEffects.LEVITATION)
                && !self.getAbilities().flying;
    }

    private void sendCarried(Minecraft mc, int slot) {
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        }
    }

    // ---------- targets ----------

    private void updateTarget(Minecraft mc) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !isValidTarget(mc, living)) {
                continue;
            }
            double distance = mc.player.distanceToSqr(living);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        this.target = best;
    }

    private boolean isValidTarget(Minecraft mc, LivingEntity entity) {
        if (entity == mc.player || !entity.isAlive() || entity.isRemoved()
                || entity.isInvulnerable() || entity instanceof ArmorStand) {
            return false;
        }
        if (entity instanceof Player player) {
            if (player.isCreative()) {
                return false;
            }
            if (player.isAlliedTo(mc.player)) {
                if (!this.targets.isSelected(FRIENDS)) {
                    return false;
                }
            } else if (!this.targets.isSelected(PLAYERS)) {
                return false;
            }
            if (player.getArmorValue() == 0 && !this.targets.isSelected(NAKED)) {
                return false;
            }
        } else if (entity instanceof Monster || entity instanceof Slime) {
            if (!this.targets.isSelected(MOBS)) {
                return false;
            }
        } else if (entity instanceof Animal || entity instanceof Villager) {
            if (!this.targets.isSelected(ANIMALS)) {
                return false;
            }
        } else if (!this.targets.isSelected(MOBS)) {
            return false;
        }
        if (!this.throughWalls.value() && !mc.player.hasLineOfSight(entity)) {
            return false;
        }
        return strictDistance(mc, entity) <= this.range.value();
    }

    private static double strictDistance(Minecraft mc, LivingEntity entity) {
        Vec3 eye = mc.player.getEyePosition();
        AABB box = entity.getBoundingBox();
        double dx = Mth.clamp(eye.x, box.minX, box.maxX) - eye.x;
        double dy = Mth.clamp(eye.y, box.minY, box.maxY) - eye.y;
        double dz = Mth.clamp(eye.z, box.minZ, box.maxZ) - eye.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static float[] aimAt(LocalPlayer self, LivingEntity target) {
        Vec3 eye = self.getEyePosition();
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 d = center.subtract(eye);
        float yaw = Mth.wrapDegrees((float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0));
        float pitch = Mth.clamp(
                (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), -90.0F, 90.0F);
        return new float[]{yaw, pitch};
    }
}

package ru.white.module.impl.combat;

import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BindSetting;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.aura.UAttack;

/**
 * Mace Helper: во время падения заранее берёт булаву и бьёт цель в нужный тайминг.
 * Сам не наводится — цель берёт только от AttackAura, без включённой ауры не бьёт.
 * Не бьёт по кулдауну/hurtTime вслепую и ретраит удар до попадания.
 */
@ModuleInfo(
        name = "Mace Helper",
        desc = "Автоматический удар булавой при падении на цель",
        category = Category.COMBAT
)
public class MaceHelper extends Module {

    public ModeSetting heightMode = new ModeSetting(this, "Высота", "Умный", "Ручной");
    public SliderSetting height = new SliderSetting(this, "Высота удара", 2.5F, 1.0F, 10.0F, 0.1F)
            .setVisible(() -> heightMode.is("Ручной"));
    public ModeSetting swapMode = new ModeSetting(this, "Свап", "Легит", "Пакетный");
    public ModeSetting maceChoice = new ModeSetting(this, "Выбор булавы", "Авто", "Бинд");
    public BindSetting selectBind = new BindSetting(this, "Выбрать булаву")
            .setVisible(() -> maceChoice.is("Бинд"));
    public BooleanSetting returnItem = new BooleanSetting(this, "Возвращать предмет", true);

    /** Минимальная высота падения для умного режима — ниже урона булавы почти нет. */
    private static final float MIN_SMART_FALL = 1.5F;
    private static final double REACH = 3.0D;

    private boolean switched;
    private boolean struck;
    private boolean bindHeld;
    private int maceSlotOverride = -1;

    /** Слот хотбара, куда встала булава. */
    private int maceSlot = -1;
    /** Слот, который был выбран до свапа. */
    private int prevHotbar = -1;
    /** Слот инвентаря (9..35), откуда булаву свапнули в хотбар. */
    private int invSwapFrom = -1;

    @EventHandler
    public void onTick(EventTick event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            resetAll();
            return;
        }

        handleBind();

        // приземление: возврат предмета и сброс
        if (mc.player.isOnGround() || mc.player.fallDistance <= 0F) {
            finishFall();
            return;
        }

        LivingEntity target = findTarget();
        boolean fallingDown = mc.player.getVelocity().y < 0;
        float fd = (float) mc.player.fallDistance;

        if (!fallingDown || target == null) return;

        boolean wantStrike = shouldStrikeNow(fd);

        // пока готовим удар — глушим атак ауру, чтобы она не била раньше времени
        // и не сбивала общий кулдаун перед нашим окном
        if (!struck) {
            AttackAura.stoptick = 3;
        }

        if (swapMode.is("Пакетный")) {
            if (!wantStrike) return;

            int slot = resolveMaceSlot();
            if (slot == -1) return;

            if (prevHotbar == -1) {
                prevHotbar = mc.player.getInventory().getSelectedSlot();
            }
            if (slot != mc.player.getInventory().getSelectedSlot()) {
                selectSlot(slot);
            }

            if (tryStrike(target)) {
                struck = true;
                if (returnItem.getValue() && prevHotbar >= 0 && prevHotbar <= 8
                        && prevHotbar != mc.player.getInventory().getSelectedSlot()) {
                    selectSlot(prevHotbar);
                }
            }
            return;
        }

        // ── Легитный режим: заранее переключаемся на булаву ──
        float prepFd = heightMode.is("Ручной") ? height.getValue() * 0.5F : MIN_SMART_FALL;
        if (!switched && fd >= prepFd) {
            int slot = resolveMaceSlot();
            if (slot != -1) {
                if (prevHotbar == -1) {
                    prevHotbar = mc.player.getInventory().getSelectedSlot();
                }
                if (slot != mc.player.getInventory().getSelectedSlot()) {
                    selectSlot(slot);
                }
                switched = true;
            }
        }

        if (switched && !struck && wantStrike && tryStrike(target)) {
            struck = true;
        }
    }

    /**
     * Умный: бьём в последний тик перед землёй — fallDistance максимальный,
     * весь бонус булавы сохраняется. Ручной: строго по слайдеру.
     */
    private boolean shouldStrikeNow(float fd) {
        if (heightMode.is("Ручной")) {
            return fd >= height.getValue();
        }

        if (fd < MIN_SMART_FALL) return false;

        double distToGround = getDistanceToGround();
        double fallSpeed = Math.abs(mc.player.getVelocity().y);
        return distToGround <= Math.max(0.55D, fallSpeed * 1.25D);
    }

    /** Удар только когда цель в зоне, взгляд на ней и кулдаун прошёл. Иначе ретрай на следующем тике. */
    private boolean tryStrike(LivingEntity target) {
        if (!withinReach(target)) return false;
        if (!UAttack.anyEntityOnRay(target, 3.2F)) return false;
        if (!UAttack.msCooldownReached(0L)) return false;

        return UAttack.useEntity(target, null, null, Hand.MAIN_HAND, false);
    }

    private boolean withinReach(LivingEntity target) {
        Vec3d eye = mc.player.getEyePos();
        Box box = target.getBoundingBox();
        Vec3d closest = new Vec3d(
                MathHelper.clamp(eye.x, box.minX, box.maxX),
                MathHelper.clamp(eye.y, box.minY, box.maxY),
                MathHelper.clamp(eye.z, box.minZ, box.maxZ)
        );
        return eye.distanceTo(closest) <= REACH;
    }

    private double getDistanceToGround() {
        if (mc.world == null) return 999.0;

        Vec3d start = mc.player.getEntityPos();
        BlockHitResult result = mc.world.raycast(new RaycastContext(
                start,
                start.add(0, -10.0, 0),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        return result != null && result.getType() == HitResult.Type.BLOCK
                ? start.y - result.getPos().y
                : 999.0;
    }

    @Override
    public void onDisable() {
        finishFall();
        super.onDisable();
    }

    /** Приземление: вернуть прежний слот / предмет из инвентаря, обнулить состояние. */
    private void finishFall() {
        if (returnItem.getValue()) {
            if (invSwapFrom != -1 && maceSlot >= 0) {
                mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        invSwapFrom,
                        maceSlot,
                        SlotActionType.SWAP,
                        mc.player
                );
            } else if (switched && prevHotbar >= 0 && prevHotbar <= 8) {
                selectSlot(prevHotbar);
            }
        }
        resetAll();
    }

    private void resetAll() {
        switched = false;
        struck = false;
        bindHeld = false;
        maceSlot = -1;
        prevHotbar = -1;
        invSwapFrom = -1;
    }

    private void handleBind() {
        int code = selectBind.get();
        boolean pressed = code > 0 && InputUtil.isKeyPressed(mc.getWindow(), code);
        if (pressed && !bindHeld) {
            maceSlotOverride = mc.player.getInventory().getSelectedSlot();
        }
        bindHeld = pressed;
    }

    private int resolveMaceSlot() {
        if (maceChoice.is("Бинд")) {
            return maceSlotOverride;
        }

        for (int i = 0; i < 9; i++) {
            if (isMace(mc.player.getInventory().getStack(i))) {
                return i;
            }
        }

        // авто: булава в инвентаре — один честный свап в текущий слот хотбара
        int current = mc.player.getInventory().getSelectedSlot();
        for (int i = 9; i < 36; i++) {
            if (isMace(mc.player.getInventory().getStack(i))) {
                mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        i,
                        current,
                        SlotActionType.SWAP,
                        mc.player
                );
                invSwapFrom = i;
                maceSlot = current;
                return current;
            }
        }
        return -1;
    }

    private void selectSlot(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        mc.interactionManager.syncSelectedSlot();
    }

    private boolean isMace(ItemStack stack) {
        return stack.getItem() == Items.MACE;
    }

    /** Цель: только таргет Attack Aura — без включённой ауры хелпер не бьёт. */
    private LivingEntity findTarget() {
        AttackAura aura = AttackAura.get();
        if (aura != null && aura.isEnabled() && AttackAura.target != null) {
            LivingEntity t = AttackAura.target;
            if (t.isAlive() && withinReach(t)) {
                return t;
            }
        }
        return null;
    }
}

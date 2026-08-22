package ru.white.module.impl.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BindSetting;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.math.StopGPT;
import net.minecraft.client.util.InputUtil;

/**
 * Mace Helper: во время падения заранее берёт булаву и бьёт цель в нужный тайминг.
 * Легитный свап — видимое переключение хотбара заранее, удар после короткой паузы,
 * возврат предмета после приземления.
 */
@ModuleInfo(
        name = "Mace Helper",
        desc = "Автоматический удар булавой при падении на цель",
        category = Category.COMBAT
)
public class MaceHelper extends Module {

    public SliderSetting height = new SliderSetting(this, "Высота удара", 2.5F, 1.0F, 10.0F, 0.1F);
    public ModeSetting swapMode = new ModeSetting(this, "Свап", "Легит", "Пакетный");
    public ModeSetting maceChoice = new ModeSetting(this, "Выбор булавы", "Авто", "Бинд");
    public BindSetting selectBind = new BindSetting(this, "Выбрать булаву")
            .setVisible(() -> maceChoice.is("Бинд"));
    public BooleanSetting returnItem = new BooleanSetting(this, "Возвращать предмет", true);

    private final StopGPT timer = new StopGPT();

    private boolean switched;
    private boolean struck;
    private boolean bindHeld;

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

        // запоминание слота с булавой по бинду
        handleBind();

        // приземление: возврат предмета и сброс
        if (mc.player.isOnGround() || mc.player.fallDistance <= 0F) {
            finishFall();
            return;
        }

        LivingEntity target = findTarget();
        float h = height.getValue();
        float fd = (float) mc.player.fallDistance;
        boolean fallingDown = mc.player.getVelocity().y < 0;

        if (swapMode.is("Пакетный")) {
            if (struck || !fallingDown || fd < h) return;
            LivingEntity t = inReach(target) ? target : null;
            if (t == null) return;

            int slot = resolveMaceSlot();
            if (slot == -1) return;

            int prev = mc.player.getInventory().getSelectedSlot();
            boolean swapped = slot != prev;
            if (swapped) selectSlot(slot);

            attack(t);
            struck = true;

            if (swapped && returnItem.getValue()) selectSlot(prev);
            return;
        }

        // ── Легитный режим ──

        // подготовка: заранее переключаемся на булаву, пока летим к цели
        if (!switched && fallingDown && fd >= h * 0.5F && target != null) {
            int slot = resolveMaceSlot();
            if (slot != -1 && slot != mc.player.getInventory().getSelectedSlot()) {
                prevHotbar = mc.player.getInventory().getSelectedSlot();
                selectSlot(slot);
            } else if (slot == mc.player.getInventory().getSelectedSlot()) {
                prevHotbar = mc.player.getInventory().getSelectedSlot();
            }
            switched = true;
            timer.reset();
        }

        // удар: достигли нужной высоты + небольшая честная пауза после свапа
        if (switched && !struck && fallingDown && fd >= h && target != null && timer.hasTimePassed(50L)) {
            attack(target);
            struck = true;
        }
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
        timer.reset();
    }

    private void handleBind() {
        int code = selectBind.get();
        boolean pressed = code > 0 && InputUtil.isKeyPressed(mc.getWindow(), code);
        if (pressed && !bindHeld) {
            maceSlotOverride = mc.player.getInventory().getSelectedSlot();
        }
        if (!pressed) {
            // отпустил кнопку — можно выбрать новый слот следующим нажатием
        }
        bindHeld = pressed;
    }

    private int maceSlotOverride = -1;

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

    private boolean inReach(LivingEntity target) {
        return target != null && mc.player.distanceTo(target) <= 3.2F;
    }

    private void attack(LivingEntity target) {
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    /** Цель: приоритет — таргет Attack Aura, иначе ближайшая живая сущность в радиусе удара. */
    private LivingEntity findTarget() {
        AttackAura aura = AttackAura.get();
        if (aura != null && aura.isEnabled() && AttackAura.target != null) {
            LivingEntity t = AttackAura.target;
            if (t.isAlive() && mc.player.distanceTo(t) <= 3.2F) {
                return t;
            }
        }

        LivingEntity best = null;
        double bestSq = 3.2 * 3.2;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)
                    || entity == mc.player
                    || !living.isAlive()
                    || living.isSpectator()) {
                continue;
            }
            double sq = mc.player.squaredDistanceTo(entity);
            if (sq < bestSq) {
                bestSq = sq;
                best = living;
            }
        }
        return best;
    }
}

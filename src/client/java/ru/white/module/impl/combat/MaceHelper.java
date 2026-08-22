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
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.SliderSetting;

/**
 * Mace Helper: пока игрок летит вниз и высота падения достигает настроенной,
 * берёт булаву (из хотбара или инвентаря) и бьёт цель — чтобы зашёл смэш-удар.
 */
@ModuleInfo(
        name = "Mace Helper",
        desc = "Автоматический удар булавой при падении на цель",
        category = Category.COMBAT
)
public class MaceHelper extends Module {

    public SliderSetting height = new SliderSetting(this, "Высота удара", 2.5F, 1.0F, 10.0F, 0.1F);
    public BooleanSetting returnItem = new BooleanSetting(this, "Возвращать предмет", true);

    /** Уже ударили в этом падении — до приземления больше не бьём. */
    private boolean struck;

    @EventHandler
    public void onTick(EventTick event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            struck = false;
            return;
        }

        // сброс после приземления
        if (mc.player.isOnGround() || mc.player.fallDistance <= 0F) {
            struck = false;
            return;
        }

        if (struck) return;
        if (mc.player.fallDistance < height.getValue()) return;
        if (mc.player.getVelocity().y >= 0) return; // только когда реально летим вниз

        LivingEntity target = findTarget();
        if (target == null) return;

        int maceSlot = findMaceSlot();
        if (maceSlot == -1) return;

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        boolean swapped = maceSlot != prevSlot;
        if (swapped) selectSlot(maceSlot);

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        struck = true;

        // сервер получает «свап → удар → свап» одним пакетным бурстом
        if (swapped && returnItem.getValue()) selectSlot(prevSlot);
    }

    @Override
    public void onDisable() {
        struck = false;
        super.onDisable();
    }

    private void selectSlot(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        mc.interactionManager.syncSelectedSlot();
    }

    /** Слот с булавой: сначала хотбар, иначе свап из инвентаря в текущий слот. */
    private int findMaceSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.MACE) {
                return i;
            }
        }

        int current = mc.player.getInventory().getSelectedSlot();
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.MACE) {
                mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        i,
                        current,
                        SlotActionType.SWAP,
                        mc.player
                );
                return current;
            }
        }
        return -1;
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

package ru.white.module.impl.movement;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.event_impl.InputEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;

@ModuleInfo(
        name = "Elytra Speed",
        desc = "Автоматический взлёт на элитре и пикирование для максимальной скорости",
        category = Category.MOVEMENT
)
public class ElytraSpeed extends Module {

    private static final float TARGET_PITCH = 68.0F;
    private static final float ROTATION_SPEED = 0.65F;

    private boolean elytraWasOnGround = false;

    @Override
    public void onDisable() {
        elytraWasOnGround = false;
        super.onDisable();
    }

    @EventHandler
    public void onInput(InputEvent event) {
        if (mc.player == null || !hasElytra()) return;

        applyDirectionalInput(event);
        handleElytraActivation(event);
    }

    @EventHandler
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || !hasElytra() || !isGliding()) return;

        applyElytraRotation();
    }

    private boolean hasElytra() {
        return mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private boolean isGliding() {
        return mc.player.isGliding();
    }

    private void applyDirectionalInput(InputEvent event) {
        event.setDirectional(
                true,
                event.getInput().backward(),
                event.getInput().left(),
                event.getInput().right()
        );
    }

    private void handleElytraActivation(InputEvent event) {
        boolean onGround = mc.player.isOnGround();
        boolean gliding = mc.player.isGliding();

        if (onGround) {
            event.setJumping(true);
            elytraWasOnGround = true;
            return;
        }

        if (gliding) {
            elytraWasOnGround = false;
            return;
        }

        if (!elytraWasOnGround) {
            startFallFlying();
        } else {
            elytraWasOnGround = false;
        }
    }

    /** Аналог PlayerIntersectionUtil.startFallFlying(): просим сервер начать полёт на элитре. */
    private void startFallFlying() {
        mc.player.networkHandler.sendPacket(
                new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING)
        );
    }

    private void applyElytraRotation() {
        float pitch = mc.player.getPitch();
        mc.player.setPitch(pitch + (TARGET_PITCH - pitch) * ROTATION_SPEED);
    }
}

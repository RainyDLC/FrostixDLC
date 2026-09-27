package fun.newrar.utils.render.models;

import fun.newrar.module.impl.render.Models;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;

public final class CustomModelManager {
    private CustomModelManager() {}

    public static void apply(PlayerEntityModel model, PlayerEntityRenderState state) {
        Models models = Models.getInstance();
        if (models == null || !models.isEnabled()) return;
        if (!models.isTarget(state)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // Wheelchair sitting pose
        if (models.isWheelchairActive() && models.sittingPose.getValue()) {
            // Thighs bent forward horizontally (-90 deg / -1.41 rad)
            model.rightLeg.pitch = -1.4137167F;
            model.leftLeg.pitch = -1.4137167F;
            model.rightLeg.yaw = 0.16F;
            model.leftLeg.yaw = -0.16F;
            model.rightLeg.roll = 0.05F;
            model.leftLeg.roll = -0.05F;

            // Arms resting naturally on armrests / wheel handrims
            model.rightArm.pitch = -0.62F;
            model.leftArm.pitch = -0.62F;
            model.rightArm.yaw = -0.12F;
            model.leftArm.yaw = 0.12F;
            model.rightArm.roll = 0.18F;
            model.leftArm.roll = -0.18F;
        } else if (models.cutePose.getValue() && (models.modelPreset.is("Тянка") || models.modelPreset.is("Фурри") || models.modelPreset.is("Зайка"))) {
            // Cute anime stance when standing still
            if (Math.abs(model.rightLeg.pitch) < 0.15f && Math.abs(model.leftLeg.pitch) < 0.15f) {
                model.rightLeg.roll = -0.07f;
                model.leftLeg.roll = 0.07f;
                model.rightLeg.yaw = -0.08f;
                model.leftLeg.yaw = 0.08f;
                model.rightArm.roll = 0.08f;
                model.leftArm.roll = -0.08f;
            }
        }
    }
}

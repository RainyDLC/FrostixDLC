package fun.newrar.mixin;

import fun.newrar.interfaces.TargetScanRenderState;
import fun.newrar.module.impl.render.TargetEsp;
import fun.newrar.utils.render.TargetScanRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentModelLoader;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin<
        S extends BipedEntityRenderState,
        M extends BipedEntityModel<S>,
        A extends BipedEntityModel<S>> extends FeatureRenderer<S, M> {

    public ArmorFeatureRendererMixin(FeatureRendererContext<S, M> context) {
        super(context);
    }

    @Shadow
    @Final
    private EquipmentRenderer equipmentRenderer;

    @Shadow
    private boolean usesInnerModel(EquipmentSlot slot) {
        throw new AssertionError();
    }

    @Unique
    private S nightix$capturedState;

    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V",
            at = @At("HEAD"))
    private void nightix$captureState(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            S state,
            float limbAngle,
            float limbDistance,
            CallbackInfo ci) {
        this.nightix$capturedState = state;
    }

    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V",
            at = @At("RETURN"))
    private void nightix$clearState(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            S state,
            float limbAngle,
            float limbDistance,
            CallbackInfo ci) {
        this.nightix$capturedState = null;
    }

    @Inject(
            method = "renderArmor",
            at = @At("TAIL"))
    private void nightix$submitTargetScanOnArmor(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            ItemStack itemStack,
            EquipmentSlot slot,
            int light,
            A model,
            CallbackInfo ci) {
        S state = this.nightix$capturedState;
        if (state == null || !((TargetScanRenderState) state).nightix$isTargetScanTarget()) {
            return;
        }

        EquippableComponent equippable = itemStack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable == null || equippable.assetId().isEmpty()) {
            return;
        }

        EquipmentModel.LayerType layerType = this.usesInnerModel(slot)
                ? EquipmentModel.LayerType.HUMANOID_LEGGINGS
                : EquipmentModel.LayerType.HUMANOID;

        EquipmentModelLoader loader = ((EquipmentRendererAccessor) this.equipmentRenderer).nightix$getEquipmentModelLoader();
        EquipmentModel equipmentModel = loader.get(equippable.assetId().orElseThrow());
        List<EquipmentModel.Layer> layers = equipmentModel.getLayers(layerType);
        if (layers.isEmpty()) {
            return;
        }

        Identifier armorTexture = layers.getFirst().getFullTextureId(layerType);

        TargetEsp targetESP = TargetEsp.getInstance();
        if (targetESP == null || !targetESP.isEnabled() || !targetESP.type.is("Переливание")) {
            return;
        }

        TargetScanRenderer.submitArmor(
                model,
                state,
                matrices,
                vertexConsumers,
                armorTexture,
                slot,
                targetESP.scanColor(),
                targetESP.scanSecondColor(),
                targetESP.saturation(),
                targetESP.renderAnimation(),
                targetESP.scanSpeed(),
                targetESP.scanGlow());
    }
}

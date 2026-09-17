package fun.newrar.mixin;

import fun.newrar.interfaces.TargetScanRenderState;
import fun.newrar.module.impl.render.TargetEsp;
import fun.newrar.utils.render.TargetScanRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
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

    @Shadow
    private A getModel(S state, EquipmentSlot slot) {
        throw new AssertionError();
    }

    @Inject(
            method = "renderArmor",
            at = @At("TAIL"))
    private void nightix$submitTargetScanOnArmor(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            ItemStack itemStack,
            EquipmentSlot slot,
            int light,
            S state,
            CallbackInfo ci) {
        if (!((TargetScanRenderState) state).nightix$isTargetScanTarget()) {
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

        A model = this.getModel(state, slot);

        TargetScanRenderer.submitArmor(
                model,
                state,
                matrices,
                queue,
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

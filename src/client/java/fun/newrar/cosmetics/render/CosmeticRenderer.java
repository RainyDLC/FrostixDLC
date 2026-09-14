package fun.newrar.cosmetics.render;

import fun.newrar.cosmetics.geckolib.GeckolibCosmeticRenderer;
import fun.newrar.cosmetics.model.CosmeticModel;
import fun.newrar.cosmetics.model.ModelPosition;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

public class CosmeticRenderer {
   private static CosmeticRenderer instance;
   private final GeckolibCosmeticRenderer geckolibRenderer = GeckolibCosmeticRenderer.getInstance();
   private final RenderStack stack = new RenderStack();

   public static CosmeticRenderer getInstance() {
      if (instance == null) {
         instance = new CosmeticRenderer();
      }
      return instance;
   }

   public void renderCosmetic(CosmeticModel model, AbstractClientPlayerEntity player, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, PlayerEntityModel playerModel, float tickDelta) {
      if (model != null && model.getTextureId() != null) {
         this.stack.update(matrices);
         this.stack.push();
         float yOffset = this.transformToPosition(model, playerModel);
         this.stack.rotateZDegrees(180.0F);
         this.stack.translate(model.getX(), model.getY() + yOffset, model.getZ());
         this.stack.rotateYDegrees(model.getYaw());
         this.stack.rotateXDegrees(model.getPitch());
         this.stack.rotateZDegrees(model.getRoll());
         this.stack.scale(model.getScale(), model.getScale(), model.getScale());
         this.geckolibRenderer.renderCosmetic(model, matrices, vertexConsumers, light);
         this.stack.pop();
      }
   }

   public void renderCosmetic(CosmeticModel cosmetic, AbstractClientPlayerEntity player, MatrixStack matrices, VertexConsumer vertexConsumer, int light, PlayerEntityModel playerModel, float tickDelta) {
      if (cosmetic != null && cosmetic.getTextureId() != null && vertexConsumer != null) {
         this.stack.update(matrices);
         this.stack.push();
         float yOffset = this.transformToPosition(cosmetic, playerModel);
         this.stack.rotateZDegrees(180.0F);
         this.stack.translate(cosmetic.getX(), cosmetic.getY() + yOffset, cosmetic.getZ());
         this.stack.rotateYDegrees(cosmetic.getYaw());
         this.stack.rotateXDegrees(cosmetic.getPitch());
         this.stack.rotateZDegrees(cosmetic.getRoll());
         this.stack.scale(cosmetic.getScale(), cosmetic.getScale(), cosmetic.getScale());
         this.geckolibRenderer.renderCosmetic(cosmetic, matrices, vertexConsumer, light);
         this.stack.pop();
      }
   }

   private float transformToPosition(CosmeticModel model, PlayerEntityModel playerModel) {
      float yOffset = 0.0F;
      ModelPosition pos = model.getPosition();
      if (playerModel == null) {
         return yOffset;
      }

      switch (pos) {
         case HEAD:
            this.transformToModelPart(playerModel.head);
            yOffset = 0.5F;
            break;
         case ABOVE_HEAD:
            yOffset = 0.75F;
            break;
         case BODY:
            this.transformToModelPart(playerModel.body);
            yOffset = -0.3F;
            break;
         case RIGHT_ARM:
            this.transformToModelPart(playerModel.rightArm);
            yOffset = -0.25F;
            break;
         case LEFT_ARM:
            this.transformToModelPart(playerModel.leftArm);
            yOffset = -0.25F;
            break;
         case RIGHT_LEG:
            this.transformToModelPart(playerModel.rightLeg);
            yOffset = -0.35F;
            break;
         case LEFT_LEG:
            this.transformToModelPart(playerModel.leftLeg);
            yOffset = -0.35F;
            break;
         case FREE:
            break;
      }

      return yOffset;
   }

   private void transformToModelPart(ModelPart part) {
      this.stack.translate(part.originX * 0.0625F, part.originY * 0.0625F, part.originZ * 0.0625F);
      this.stack.rotateDegrees(
         part.pitch * (180.0F / (float) Math.PI),
         part.yaw * (180.0F / (float) Math.PI),
         part.roll * (180.0F / (float) Math.PI)
      );
   }
}

package fun.newrar.cosmetics.render;

import fun.newrar.cosmetics.model.CosmeticModel;
import fun.newrar.utils.player.MoveUtil;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PetFollower {
   private static final PetFollower INSTANCE = new PetFollower();

   public static PetFollower getInstance() {
      return INSTANCE;
   }

   private final Map<Integer, PetState> states = new ConcurrentHashMap<>();

   public static class PetState {
      public double worldX;
      public double worldY;
      public double worldZ;
      public float petWorldYaw;

      public float localX;
      public float localY;
      public float localZ;
      public float localYaw;
      public float petYaw; // compatibility alias

      public boolean isMoving;
      public float walkTimer;
      public float idleTimer;

      public long lastTimeMs;
      public boolean initialized;
   }

   public boolean isGroundPet(CosmeticModel model) {
      if (model == null) {
         return false;
      }
      return model.getCategory() == 2 && model.getY() > 0.1F;
   }

   public PetState updateAndGetState(AbstractClientPlayerEntity player, CosmeticModel model, float tickDelta) {
      if (model == null) {
         return null;
      }

      int id = model.getId();
      PetState state = this.states.computeIfAbsent(id, k -> new PetState());
      long now = System.currentTimeMillis();

      float restX = model.getX() != 0.0F ? model.getX() : 0.85F;
      float restY = model.getY();
      float restZ = model.getZ() != 0.0F ? model.getZ() : 0.1F;

      if (!state.initialized || player == null) {
         state.localX = restX;
         state.localY = restY;
         state.localZ = restZ;
         state.localYaw = 0.0F;
         state.petYaw = 0.0F;

         if (player != null) {
            double bodyYawRad = Math.toRadians(player.bodyYaw);
            double cosYaw = Math.cos(bodyYawRad);
            double sinYaw = Math.sin(bodyYawRad);
            state.worldX = player.getX() + (restX * cosYaw + restZ * sinYaw);
            state.worldY = player.getY();
            state.worldZ = player.getZ() + (restX * sinYaw - restZ * cosYaw);
            state.petWorldYaw = player.bodyYaw;
         }

         state.isMoving = false;
         state.lastTimeMs = now;
         state.initialized = true;
         return state;
      }

      float dt = (now - state.lastTimeMs) / 1000.0F;
      if (dt < 0.001F || dt > 0.2F) {
         dt = 0.05F;
      }
      state.lastTimeMs = now;

      double playerX = player.getX();
      double playerY = player.getY();
      double playerZ = player.getZ();
      float playerBodyYaw = player.bodyYaw;
      double bodyYawRad = Math.toRadians(playerBodyYaw);
      double cosYaw = Math.cos(bodyYawRad);
      double sinYaw = Math.sin(bodyYawRad);

      // Resting position in world coordinates (at player's side)
      double restWorldX = playerX + (restX * cosYaw + restZ * sinYaw);
      double restWorldZ = playerZ + (restX * sinYaw - restZ * cosYaw);

      // Check distance from pet to player - snap if teleported or too far
      double distToPlayerSq = (playerX - state.worldX) * (playerX - state.worldX) + (playerZ - state.worldZ) * (playerZ - state.worldZ);
      if (distToPlayerSq > 64.0) { // > 8 blocks away
         state.worldX = restWorldX;
         state.worldY = playerY;
         state.worldZ = restWorldZ;
         state.petWorldYaw = playerBodyYaw;
         state.isMoving = false;
         state.localX = restX;
         state.localY = restY;
         state.localZ = restZ;
         state.localYaw = 0.0F;
         state.petYaw = 0.0F;
         return state;
      }

      // Check if player is actually moving (input keys or velocity)
      boolean playerInputMoving = MoveUtil.isMoving();
      double velX = player.getVelocity().x;
      double velZ = player.getVelocity().z;
      double hVelSq = velX * velX + velZ * velZ;
      boolean playerMoving = playerInputMoving || hVelSq > 0.002;

      // Calculate target world position for the ground pet
      double targetX;
      double targetZ;

      if (playerMoving) {
         // Follow behind the player along movement path
         double moveDist = Math.sqrt(hVelSq);
         double dirX;
         double dirZ;
         if (moveDist > 0.04) {
            dirX = velX / moveDist;
            dirZ = velZ / moveDist;
         } else {
            // Derive facing direction from body yaw
            dirX = -Math.sin(bodyYawRad);
            dirZ = Math.cos(bodyYawRad);
         }
         // 1.35 blocks behind player
         targetX = playerX - dirX * 1.35;
         targetZ = playerZ - dirZ * 1.35;
      } else {
         // Stand at the resting side position
         targetX = restWorldX;
         targetZ = restWorldZ;
      }

      double toTargetX = targetX - state.worldX;
      double toTargetZ = targetZ - state.worldZ;
      double distToTarget = Math.sqrt(toTargetX * toTargetX + toTargetZ * toTargetZ);

      if (distToTarget > 0.16) {
         state.isMoving = true;
         // Heading in Minecraft degrees: 0=South, 90=West, 180=North, 270=East
         float moveHeading = (float) Math.toDegrees(Math.atan2(-toTargetX, toTargetZ));
         state.petWorldYaw = MathHelper.lerpAngleDegrees(Math.min(1.0F, dt * 12.0F), state.petWorldYaw, moveHeading);

         double runSpeed = Math.max(distToTarget * 4.2, 3.2);
         double step = Math.min(distToTarget, runSpeed * dt);
         state.worldX += (toTargetX / distToTarget) * step;
         state.worldZ += (toTargetZ / distToTarget) * step;
         state.worldY = playerY;

         state.walkTimer += dt * (float) (runSpeed * 1.5);
      } else {
         if (!playerMoving) {
            state.isMoving = false;
            state.petWorldYaw = MathHelper.lerpAngleDegrees(Math.min(1.0F, dt * 8.0F), state.petWorldYaw, playerBodyYaw);
            state.idleTimer += dt;
         } else {
            // Still in motion behind player
            state.isMoving = true;
            state.walkTimer += dt * 3.5F;
         }
         state.worldY = playerY;
      }

      // Convert pet world coordinates into player local matrix frame
      double worldDx = state.worldX - playerX;
      double worldDz = state.worldZ - playerZ;

      state.localX = (float) (worldDx * cosYaw + worldDz * sinYaw);
      state.localY = restY;
      state.localZ = (float) (worldDx * sinYaw - worldDz * cosYaw);
      state.localYaw = MathHelper.wrapDegrees(state.petWorldYaw - playerBodyYaw);
      state.petYaw = state.localYaw;

      return state;
   }
}

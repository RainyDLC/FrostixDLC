package fun.newrar.cosmetics.render;

import fun.newrar.cosmetics.model.CosmeticModel;
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
      public float localX;
      public float localY;
      public float localZ;
      public float petYaw;
      public boolean isMoving;
      public float walkTimer;
      public float idleTimer;
      public double lastPlayerX;
      public double lastPlayerY;
      public double lastPlayerZ;
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

      if (!state.initialized) {
         state.localX = model.getX() != 0.0F ? model.getX() : 0.85F;
         state.localY = model.getY();
         state.localZ = model.getZ() != 0.0F ? model.getZ() : 0.15F;
         state.petYaw = 0.0F;
         state.lastPlayerX = player != null ? player.getX() : 0.0;
         state.lastPlayerY = player != null ? player.getY() : 0.0;
         state.lastPlayerZ = player != null ? player.getZ() : 0.0;
         state.lastTimeMs = now;
         state.initialized = true;
         return state;
      }

      float dt = (now - state.lastTimeMs) / 1000.0F;
      if (dt < 0.001F || dt > 0.2F) {
         dt = 0.05F;
      }
      state.lastTimeMs = now;

      if (player == null) {
         state.idleTimer += dt;
         state.isMoving = false;
         return state;
      }

      // Check player movement speed
      double dx = player.getX() - state.lastPlayerX;
      double dz = player.getZ() - state.lastPlayerZ;
      double distSq = dx * dx + dz * dz;
      state.lastPlayerX = player.getX();
      state.lastPlayerY = player.getY();
      state.lastPlayerZ = player.getZ();

      boolean playerMoving = distSq > 0.0004 || (player.limbAnimator != null && player.limbAnimator.isLimbMoving());

      // Target position in player local space
      float baseX = model.getX() != 0.0F ? model.getX() : 0.85F;
      float baseY = model.getY();
      float baseZ = model.getZ() != 0.0F ? model.getZ() : 0.15F;

      float targetX;
      float targetZ;
      float targetYaw;

      if (playerMoving) {
         // Trail slightly behind and to the side
         targetX = baseX * 0.85F;
         targetZ = Math.max(baseZ + 0.65F, 0.75F);
         // Slightly angle inward towards the player's path
         targetYaw = -Math.signum(baseX) * 12.0F;
         state.isMoving = true;
         float moveSpeedMultiplier = (float) Math.min(Math.sqrt(distSq) * 35.0, 4.0);
         state.walkTimer += dt * Math.max(moveSpeedMultiplier, 1.6F);
      } else {
         // Return to default side stance
         targetX = baseX;
         targetZ = baseZ;
         targetYaw = 0.0F;
         state.isMoving = false;
         state.idleTimer += dt;
      }

      // Smooth lerp towards target in local space
      float lerpSpeed = state.isMoving ? 7.5F : 4.5F;
      state.localX = MathHelper.lerp(Math.min(1.0F, dt * lerpSpeed), state.localX, targetX);
      state.localY = baseY;
      state.localZ = MathHelper.lerp(Math.min(1.0F, dt * lerpSpeed), state.localZ, targetZ);
      state.petYaw = MathHelper.lerp(Math.min(1.0F, dt * 6.0F), state.petYaw, targetYaw);

      return state;
   }
}

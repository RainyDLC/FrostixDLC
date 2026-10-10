package su.DSF.calcite.mixin.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.DSF.calcite.api.events.impl.game.EventMotion;
import su.DSF.calcite.api.events.impl.game.EventUpdate;
import su.DSF.calcite.api.util.game.SentMotion;

@Mixin({LocalPlayer.class})
public abstract class ClientPlayerEntityMixin extends AbstractClientPlayer implements SentMotion {
   @Unique
   private EventMotion calcite$motion;

   @Shadow
   private double xLast;

   @Shadow
   private double yLast;

   @Shadow
   private double zLast;

   @Shadow
   private int positionReminder;

   @Shadow
   private float yRotLast;

   @Shadow
   private float xRotLast;

   @Shadow
   private boolean lastOnGround;

   @Shadow
   private boolean lastHorizontalCollision;

   @Shadow
   protected abstract void sendIsSprintingIfNeeded();

   public ClientPlayerEntityMixin(ClientLevel level, GameProfile profile) {
      super(level, profile);
   }

   @Inject(
      method = {"tick()V"},
      at = {@At("HEAD")}
   )
   private void calcite$onUpdate(CallbackInfo ci) {
      new EventUpdate().call();
   }

   @Inject(
      method = {"sendPosition"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void calcite$onSendPosition(CallbackInfo ci) {
      EventMotion event = new EventMotion(
         this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot(), this.onGround(), this.isShiftKeyDown(), this.isSprinting()
      )
         .call();
      this.calcite$motion = event;
      if (event.isCancelled()) {
         this.sendIsSprintingIfNeeded();
         if (!event.getReplacement().isEmpty()) {
            this.calcite$sendReplacement(event);
         } else if (event.isRotationOnly()) {
            this.calcite$sendRotationOnly(event);
         }

         ci.cancel();
      }
   }

   @Override
   public float calcite$sentYaw() {
      return this.yRotLast;
   }

   @Override
   public float calcite$sentPitch() {
      return this.xRotLast;
   }

   @Unique
   private void calcite$sendReplacement(EventMotion event) {
      LocalPlayer self = (LocalPlayer)(Object)this;
      for (Packet<?> packet : event.getReplacement()) {
         self.connection.send(packet);
         if (packet instanceof ServerboundMovePlayerPacket move) {
            if (move.hasPosition()) {
               this.xLast = move.getX(this.xLast);
               this.yLast = move.getY(this.yLast);
               this.zLast = move.getZ(this.zLast);
               this.positionReminder = 0;
            }

            if (move.hasRotation()) {
               this.yRotLast = move.getYRot(this.yRotLast);
               this.xRotLast = move.getXRot(this.xRotLast);
            }

            this.lastOnGround = move.isOnGround();
            this.lastHorizontalCollision = move.horizontalCollision();
         }
      }

      event.executePostActions();
   }

   @Unique
   private void calcite$sendRotationOnly(EventMotion event) {
      float yaw = event.getYaw();
      float pitch = event.getPitch();
      if (yaw == this.yRotLast && pitch == this.xRotLast) return;

      ((LocalPlayer)(Object)this).connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, this.lastOnGround, this.lastHorizontalCollision));
      this.yRotLast = yaw;
      this.xRotLast = pitch;
      event.executePostActions();
   }

   @Inject(
      method = {"sendPosition"},
      at = {@At("TAIL")}
   )
   private void calcite$onSendPositionTail(CallbackInfo ci) {
      if (this.calcite$motion != null) {
         this.calcite$motion.executePostActions();
      }
   }

   @Redirect(
      method = {"sendPosition"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;getX()D"
      )
   )
   private double calcite$motionX(LocalPlayer instance) {
      return this.calcite$motion != null ? this.calcite$motion.getX() : instance.getX();
   }

   @Redirect(
      method = {"sendPosition"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;getY()D"
      )
   )
   private double calcite$motionY(LocalPlayer instance) {
      return this.calcite$motion != null ? this.calcite$motion.getY() : instance.getY();
   }

   @Redirect(
      method = {"sendPosition"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;getZ()D"
      )
   )
   private double calcite$motionZ(LocalPlayer instance) {
      return this.calcite$motion != null ? this.calcite$motion.getZ() : instance.getZ();
   }

   @Redirect(
      method = {"sendPosition"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"
      )
   )
   private float calcite$motionYaw(LocalPlayer instance) {
      return this.calcite$motion != null ? this.calcite$motion.getYaw() : instance.getYRot();
   }

   @Redirect(
      method = {"sendPosition"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"
      )
   )
   private float calcite$motionPitch(LocalPlayer instance) {
      return this.calcite$motion != null ? this.calcite$motion.getPitch() : instance.getXRot();
   }

   @Redirect(
      method = {"sendPosition"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;onGround()Z"
      )
   )
   private boolean calcite$motionOnGround(LocalPlayer instance) {
      return this.calcite$motion != null ? this.calcite$motion.isOnGround() : instance.onGround();
   }

   @Redirect(
      method = {"sendPosition"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;position()Lnet/minecraft/world/phys/Vec3;"
      )
   )
   private Vec3 calcite$motionPosition(LocalPlayer instance) {
      return this.calcite$motion != null ? new Vec3(this.calcite$motion.getX(), this.calcite$motion.getY(), this.calcite$motion.getZ()) : instance.position();
   }
}

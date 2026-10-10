package su.DSF.calcite.api.events.impl.game;

import su.DSF.calcite.api.events.Event;

public class EventInputMove extends Event {
   private float forward;
   private float strafe;
   private boolean jump;
   private boolean sneaking;
   private boolean sprint;

   public EventInputMove(float forward, float strafe, boolean jump, boolean sneaking, boolean sprint) {
      this.forward = forward;
      this.strafe = strafe;
      this.jump = jump;
      this.sneaking = sneaking;
      this.sprint = sprint;
   }

   public boolean isMoving() {
      return this.forward != 0.0F || this.strafe != 0.0F;
   }

   public float getForward() {
      return this.forward;
   }

   public float getStrafe() {
      return this.strafe;
   }

   public boolean isJump() {
      return this.jump;
   }

   public boolean isSneaking() {
      return this.sneaking;
   }

   public boolean isSprint() {
      return this.sprint;
   }

   public void setForward(float forward) {
      this.forward = forward;
   }

   public void setStrafe(float strafe) {
      this.strafe = strafe;
   }

   public void setJump(boolean jump) {
      this.jump = jump;
   }

   public void setSneaking(boolean sneaking) {
      this.sneaking = sneaking;
   }

   public void setSprint(boolean sprint) {
      this.sprint = sprint;
   }
}

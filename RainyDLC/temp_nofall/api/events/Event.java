package su.DSF.calcite.api.events;

import su.DSF.calcite.Calcite;

public class Event {
   boolean cancelled;

   public <T extends Event> T call() {
      Calcite.getInstance().getBus().post(this);
      return (T)this;
   }

   public boolean isCancelled() {
      return this.cancelled;
   }

   public void setCancelled(boolean cancelled) {
      this.cancelled = cancelled;
   }
}

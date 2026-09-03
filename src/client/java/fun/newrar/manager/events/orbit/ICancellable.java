package fun.newrar.manager.events.orbit;

public interface ICancellable {
    void setCancelled(boolean cancelled);

    default void cancel() {
        setCancelled(true);
    }

    boolean isCancelled();
}


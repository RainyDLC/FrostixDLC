package dev.hatek.client.render.killeffect;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Packet-confirmed deaths must not be cleared by a remote player's stale health=20. */
public final class KillEffectDeathLatch {
    private final Map<Integer, UUID> captured = new HashMap<>();
    public boolean mark(int entityId, UUID uuid) {
        UUID old = captured.put(entityId, uuid);
        return !uuid.equals(old);
    }
    public boolean contains(int entityId) { return captured.containsKey(entityId); }
    public void remove(int entityId) { captured.remove(entityId); }
    public void retain(Set<Integer> loadedIds) { captured.keySet().retainAll(loadedIds); }
    public void clear() { captured.clear(); }
}

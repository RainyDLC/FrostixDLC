package rtx.kimiko.api.combat.target;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.LinkedHashSet;
import java.util.Set;

public class TargetManager {
    public static final TargetManager INSTANCE = new TargetManager();

    private Entity targetEntity = null;
    private final Set<String> targetNames = new LinkedHashSet<>();

    public TargetManager() {
    }

    public static TargetManager getInstance() {
        return INSTANCE;
    }

    public LivingEntity getTargetLivingEntity() {
        return this.targetEntity instanceof LivingEntity living ? living : null;
    }

    public boolean contains(String name) {
        return this.targetNames.contains(name);
    }

    public Entity findTarget(TargetQuery query) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            return null;
        }
        Entity best = null;
        boolean bestIsPriority = false;

        for (Entity entity : mc.world.getEntities()) {
            if (!query.matches(entity)) {
                continue;
            }
            boolean isPriority = !this.targetNames.isEmpty() && this.targetNames.contains(entity.getName().getString());
            if (best == null) {
                best = entity;
                bestIsPriority = isPriority;
                continue;
            }
            if (isPriority && !bestIsPriority) {
                best = entity;
                bestIsPriority = true;
                continue;
            }
            if (isPriority == bestIsPriority && query.getComparator().compare(entity, best) < 0) {
                best = entity;
            }
        }
        return best;
    }

    public void updateTarget(TargetQuery query) {
        this.targetEntity = this.findTarget(query);
    }

    public Entity getTargetEntity() {
        return this.targetEntity;
    }

    public void setTargetEntity(Entity entity) {
        this.targetEntity = entity;
    }

    public void resetTarget() {
        this.targetEntity = null;
    }

    public Set<String> getTargets() {
        return this.targetNames;
    }

    public void addTarget(String name) {
        this.targetNames.add(name);
    }

    public void removeTarget(String name) {
        this.targetNames.remove(name);
    }

    public void clearTargets() {
        this.targetNames.clear();
    }
}

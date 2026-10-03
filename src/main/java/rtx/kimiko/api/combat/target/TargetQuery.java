package rtx.kimiko.api.combat.target;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import rtx.kimiko.utils.combat.DistanceComparators;
import rtx.kimiko.utils.math.rotation.RotationUtil;
import rtx.kimiko.utils.storage.friend.FriendUtils;

import java.util.Comparator;
import java.util.function.Function;

public class TargetQuery {
    private float range = -1.0f;
    private Function<Entity, Float> rangeFunction = e -> this.range;
    private Comparator<Entity> comparator = DistanceComparators.BY_DISTANCE;
    private Vec3d origin;
    private boolean playersEnabled = true;
    private boolean armorStandsEnabled = false;
    private boolean invisiblesEnabled = true;
    private boolean friendsEnabled = false;
    private boolean teammatesEnabled = false;
    private boolean nakedPlayersEnabled = true;
    private boolean animalsEnabled = false;
    private boolean mobsEnabled = false;

    public TargetQuery() {
    }

    public TargetQuery range(float range) {
        this.range = range;
        return this;
    }

    public TargetQuery rangeFunction(Function<Entity, Float> rangeFunction) {
        this.rangeFunction = rangeFunction;
        return this;
    }

    public TargetQuery comparator(Comparator<Entity> comparator) {
        this.comparator = comparator;
        return this;
    }

    public TargetQuery origin(Vec3d origin) {
        this.origin = origin;
        return this;
    }

    public TargetQuery players(boolean enabled) {
        this.playersEnabled = enabled;
        return this;
    }

    public TargetQuery armorStands(boolean enabled) {
        this.armorStandsEnabled = enabled;
        return this;
    }

    public TargetQuery invisibles(boolean enabled) {
        this.invisiblesEnabled = enabled;
        return this;
    }

    public TargetQuery friends(boolean enabled) {
        this.friendsEnabled = enabled;
        return this;
    }

    public TargetQuery teammates(boolean enabled) {
        this.teammatesEnabled = enabled;
        return this;
    }

    public TargetQuery nakedPlayers(boolean enabled) {
        this.nakedPlayersEnabled = enabled;
        return this;
    }

    public TargetQuery animals(boolean enabled) {
        this.animalsEnabled = enabled;
        return this;
    }

    public TargetQuery mobs(boolean enabled) {
        this.mobsEnabled = enabled;
        return this;
    }

    public float getRange() {
        return this.range;
    }

    public Vec3d getOrigin() {
        return this.origin;
    }

    public Comparator<Entity> getComparator() {
        return this.comparator;
    }

    public boolean isPlayersEnabled() {
        return this.playersEnabled;
    }

    public boolean isAnimalsEnabled() {
        return this.animalsEnabled;
    }

    public boolean isMobsEnabled() {
        return this.mobsEnabled;
    }

    public boolean isInvisiblesEnabled() {
        return this.invisiblesEnabled;
    }

    public boolean isNakedPlayersEnabled() {
        return this.nakedPlayersEnabled;
    }

    public boolean isFriendsEnabled() {
        return this.friendsEnabled;
    }

    public boolean isTeammatesEnabled() {
        return this.teammatesEnabled;
    }

    public static boolean isNaked(PlayerEntity player) {
        EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : armorSlots) {
            ItemStack stack = player.getEquippedStack(slot);
            if (stack != null && !stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean isInRange(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return false;
        }
        float r = this.rangeFunction.apply(entity);
        if (r <= 0.0f) {
            return true;
        }
        Vec3d center = this.origin != null ? this.origin : mc.player.getEyePos();
        return center.squaredDistanceTo(RotationUtil.clampPointToBox(entity, center)) <= (double) (r * r);
    }

    public boolean matches(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || entity == null) {
            return false;
        }
        if (!(entity instanceof LivingEntity living) || entity == mc.player) {
            return false;
        }
        if (living.isDead() || !living.isAlive()) {
            return false;
        }
        if (!this.isInRange(entity)) {
            return false;
        }
        if (entity instanceof ArmorStandEntity) {
            return this.armorStandsEnabled && (this.invisiblesEnabled || !entity.isInvisible());
        }

        if (entity instanceof PlayerEntity player) {
            boolean isFriend = FriendUtils.isFriend(player.getNameForScoreboard()) || FriendUtils.isFriend(player);
            if (!this.friendsEnabled && isFriend) {
                return false;
            }
            if (this.teammatesEnabled && mc.player.isTeammate(player)) {
                return false;
            }
            boolean naked = isNaked(player);
            boolean invisible = player.isInvisible();
            if (invisible && !this.invisiblesEnabled && (!this.playersEnabled || naked)) {
                return false;
            }
            if (this.playersEnabled || this.nakedPlayersEnabled) {
                if (this.playersEnabled && this.nakedPlayersEnabled) {
                    return true;
                }
                if (!this.playersEnabled) {
                    return false;
                }
                return !naked;
            }
            return false;
        }

        if (entity instanceof AnimalEntity) {
            if (entity.isInvisible() && !this.invisiblesEnabled) {
                return false;
            }
            return this.animalsEnabled;
        }

        if (entity instanceof MobEntity) {
            if (entity.isInvisible() && !this.invisiblesEnabled) {
                return false;
            }
            return this.mobsEnabled;
        }

        return false;
    }
}

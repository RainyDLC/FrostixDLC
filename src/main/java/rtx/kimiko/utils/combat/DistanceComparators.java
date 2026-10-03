package rtx.kimiko.utils.combat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import rtx.kimiko.utils.math.rotation.Rotation;
import rtx.kimiko.utils.math.rotation.RotationUtil;

import java.util.Comparator;

public final class DistanceComparators {
    private DistanceComparators() {
    }

    public static final Comparator<Entity> BY_DISTANCE = (e1, e2) -> {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return 0;
        return Double.compare(mc.player.squaredDistanceTo(e1), mc.player.squaredDistanceTo(e2));
    };

    public static final Comparator<Entity> BY_HEALTH = (e1, e2) -> {
        float h1 = e1 instanceof LivingEntity l1 ? l1.getHealth() : Float.MAX_VALUE;
        float h2 = e2 instanceof LivingEntity l2 ? l2.getHealth() : Float.MAX_VALUE;
        return Float.compare(h1, h2);
    };

    public static final Comparator<Entity> BY_FOV = (e1, e2) -> {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return 0;
        Rotation current = new Rotation(mc.player.getYaw(), mc.player.getPitch());
        Rotation rot1 = RotationUtil.getRotationToVector(e1.getEyePos());
        Rotation rot2 = RotationUtil.getRotationToVector(e2.getEyePos());
        return Float.compare(current.distanceTo(rot1), current.distanceTo(rot2));
    };

    public static Comparator<Entity> byDistanceTo(Vec3d origin) {
        if (origin == null) return BY_DISTANCE;
        return (e1, e2) -> Double.compare(origin.squaredDistanceTo(e1.getEntityPos()), origin.squaredDistanceTo(e2.getEntityPos()));
    }
}

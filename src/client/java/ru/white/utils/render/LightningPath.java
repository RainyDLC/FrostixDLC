package ru.white.utils.render;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class LightningPath {
    private LightningPath() {
    }

    public static List<Vec3d> generate(Vec3d start, Vec3d end, int depth, double maxOffset, Random random) {
        if (depth <= 0) {
            List<Vec3d> result = new ArrayList<>(2);
            result.add(start);
            result.add(end);
            return result;
        }

        Vec3d dir = end.subtract(start);
        Vec3d mid = start.add(dir.multiply(0.5));
        Vec3d perp = randomPerpendicular(dir, random);
        double offset = (random.nextDouble() - 0.5) * 2.0 * maxOffset;
        Vec3d displacedMid = mid.add(perp.multiply(offset));

        List<Vec3d> left = generate(start, displacedMid, depth - 1, maxOffset * 0.5, random);
        List<Vec3d> right = generate(displacedMid, end, depth - 1, maxOffset * 0.5, random);
        left.remove(left.size() - 1);
        left.addAll(right);
        return left;
    }

    public static Vec3d randomPerpendicular(Vec3d dir, Random random) {
        Vec3d normDir = dir.normalize();
        Vec3d arbitrary = Math.abs(normDir.y) < 0.9 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
        Vec3d perp1 = normDir.crossProduct(arbitrary).normalize();
        Vec3d perp2 = normDir.crossProduct(perp1).normalize();
        double angle = random.nextDouble() * Math.PI * 2;
        return perp1.multiply(Math.cos(angle)).add(perp2.multiply(Math.sin(angle)));
    }
}

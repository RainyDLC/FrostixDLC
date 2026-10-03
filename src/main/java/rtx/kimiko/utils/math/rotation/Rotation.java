package rtx.kimiko.utils.math.rotation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import rtx.kimiko.utils.math.MathUtil;

public class Rotation {
    public float yaw;
    public float pitch;
    public static final Rotation ZERO = new Rotation(0.0f, 0.0f);

    public Rotation(double yaw, double pitch) {
        this.yaw = (float) yaw;
        this.pitch = (float) pitch;
    }

    public Rotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Vec3d toLookVector() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            return mc.player.getRotationVector(this.pitch, this.yaw);
        }
        return MathUtil.getVectorForRotation(this.pitch, this.yaw);
    }

    public Rotation diff(Rotation other) {
        float dYaw = MathUtil.angleDifference(this.yaw, other.yaw);
        float dPitch = MathUtil.angleDifference(this.pitch, other.pitch);
        return new Rotation(dYaw, dPitch);
    }

    public float distanceTo(Rotation other) {
        float dYaw = MathUtil.angleDifference(this.yaw, other.yaw);
        float dPitch = MathUtil.angleDifference(this.pitch, other.pitch);
        return Math.abs(dYaw) + Math.abs(dPitch);
    }

    public Rotation add(float yaw, float pitch) {
        return new Rotation(this.yaw + yaw, this.pitch + pitch);
    }

    public float getYaw() {
        return this.yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Rotation rotation)) return false;
        return Float.compare(rotation.yaw, this.yaw) == 0 && Float.compare(rotation.pitch, this.pitch) == 0;
    }

    @Override
    public int hashCode() {
        int result = Float.floatToIntBits(this.yaw);
        result = 31 * result + Float.floatToIntBits(this.pitch);
        return result;
    }

    @Override
    public String toString() {
        return "Rotation(yaw=" + this.yaw + ", pitch=" + this.pitch + ")";
    }
}

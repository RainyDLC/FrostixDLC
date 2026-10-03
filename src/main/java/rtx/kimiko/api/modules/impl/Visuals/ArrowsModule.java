package rtx.kimiko.api.modules.impl.Visuals;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.gui.DrawContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import rtx.kimiko.api.drags.Position;
import rtx.kimiko.api.events.EventHandler;
import rtx.kimiko.api.events.impl.render.HudRenderEvent;
import rtx.kimiko.api.events.impl.render.WorldRenderEvent;
import rtx.kimiko.api.liteapi.Feature;
import rtx.kimiko.api.modules.Category;
import rtx.kimiko.api.modules.Module;
import rtx.kimiko.api.modules.SubCategory;
import rtx.kimiko.api.modules.settings.Setting;
import rtx.kimiko.api.modules.settings.impl.ColorSetting;
import rtx.kimiko.api.modules.settings.impl.SeparatorSetting;
import rtx.kimiko.api.modules.settings.impl.SliderSetting;
import rtx.kimiko.utils.render.render2d.Render2D;
import rtx.kimiko.utils.storage.friend.FriendUtils;

import java.awt.Color;

@Feature(value={"arrows"})
public final class ArrowsModule extends Module {

    private static final String TEXTURE = "assets/kimiko/textures/arrows.png";
    private static final float DEFAULT_RADIUS = 42.0f;
    private static final float DEFAULT_SIZE = 9.0f;

    private final Vector4f projectionScratch = new Vector4f();
    private final List<ArrowTarget> targets = new ArrayList<>();

    private final SliderSetting radius;
    private final SliderSetting size;
    private final ColorSetting color;
    private final ColorSetting friendColor;

    public ArrowsModule() {
        super("Arrows", "Показывает стрелки вокруг прицела, указывающие на игроков рядом.", Category.VISUALS, SubCategory.VISUALS);
        this.radius = (SliderSetting) this.register((Setting) new SliderSetting("Радиус стрелок", "Расстояние от центра прицела до стрелок.").range(20.0f, 100.0f).increment(1.0f).setValue(DEFAULT_RADIUS));
        this.size = (SliderSetting) this.register((Setting) new SliderSetting("Размер стрелок", "Размер текстуры стрелки на экране.").range(5.0f, 20.0f).increment(0.5f).setValue(DEFAULT_SIZE));
        this.color = (ColorSetting) this.register((Setting) new ColorSetting("Цвет стрелки", "Цвет стрелки, включая прозрачность.").value(new Color(120, 200, 255, 220).getRGB()));
        this.friendColor = (ColorSetting) this.register((Setting) new ColorSetting("Цвет друзей", "Цвет стрелки для друзей.").value(new Color(80, 255, 140, 220).getRGB()));
    }

    @Override
    protected void onDisable() {
        this.targets.clear();
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent event) {
        if (event.isPortalPass()) {
            return;
        }
        this.targets.clear();

        ClientPlayerEntity player = this.mc.player;
        ClientWorld level = this.mc.world;
        if (player == null || level == null) {
            return;
        }

        Vec3d cameraPos = event.getCamera() != null
                ? event.getCamera().getCameraPos()
                : this.mc.gameRenderer.getCamera().getCameraPos();

        Matrix4f positionMatrix = event.getPositionMatrix();
        Matrix4f projectionMatrix = event.getProjectionMatrix();

        float alpha = this.visualAlpha();
        if (alpha <= 0.01f) {
            return;
        }

        for (AbstractClientPlayerEntity other : level.getPlayers()) {
            if (other == player || !other.isAlive() || other.isInvisible()) continue;

            Vec3d pos = other.getEntityPos();
            Vector4f projected = this.project(positionMatrix, projectionMatrix, cameraPos, pos.x, pos.y, pos.z);
            if (projected == null) continue;

            float x = (projected.x / projected.w * 0.5f + 0.5f) * Position.Companion.screenWidth();
            float y = (1.0f - (projected.y / projected.w * 0.5f + 0.5f)) * Position.Companion.screenHeight();
            if (!Float.isFinite(x) || !Float.isFinite(y)) continue;

            float dx = x - Position.Companion.screenWidth() / 2.0f;
            float dy = y - Position.Companion.screenHeight() / 2.0f;

            double dist = player.squaredDistanceTo((PlayerEntity) other);
            double maxDist = 50.0;
            float fade = MathHelper.clamp((float) (1.0 - dist / (maxDist * maxDist)), 0.0f, 1.0f);
            if (fade <= 0.0f) continue;

            boolean isFriend = FriendUtils.isFriend((net.minecraft.entity.Entity) other);
            this.targets.add(new ArrowTarget(dx, dy, fade, isFriend));
        }
    }

    @EventHandler
    private void onHud(HudRenderEvent event) {
        if (this.targets.isEmpty()) {
            return;
        }

        DrawContext graphics = event.getGraphics();
        float centerX = Position.Companion.screenWidth() / 2.0f;
        float centerY = Position.Companion.screenHeight() / 2.0f;
        float radius = this.radius.getValue();
        float size = this.size.getValue();
        int baseColor = this.color.getColor();
        int friendColor = this.friendColor.getColor();

        Render2D.beginFrame(graphics);

        for (ArrowTarget target : this.targets) {
            float len = (float) Math.sqrt(target.dx() * target.dx() + target.dy() * target.dy());
            if (len < 0.01f) continue;

            float nx = target.dx() / len;
            float ny = target.dy() / len;

            float x = centerX + nx * radius - size / 2.0f;
            float y = centerY + ny * radius - size / 2.0f;

            float angle = (float) Math.toDegrees(Math.atan2(ny, nx)) + 90.0f;
            int color = this.tintAlpha(target.friend() ? friendColor : baseColor, target.fade() * this.visualAlpha());

            Render2D.rotatedImage(TEXTURE, x, y, size, size / 6.0f, angle, x + size / 2.0f, y + size / 2.0f, color);
        }

        Render2D.flush();
    }

    private int tintAlpha(int color, float alpha) {
        int a = MathHelper.clamp(Math.round(alpha * 255.0f), 0, 255);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    @Nullable
    private Vector4f project(Matrix4f positionMatrix, Matrix4f projectionMatrix, Vec3d cameraPos, double x, double y, double z) {
        Vector4f vector = this.projectionScratch.set((float)(x - cameraPos.x), (float)(y - cameraPos.y), (float)(z - cameraPos.z), 1.0f);
        positionMatrix.transform(vector);
        projectionMatrix.transform(vector);
        return vector.w > 1.0E-4f ? vector : null;
    }

    private static final class ArrowTarget {
        private final float dx;
        private final float dy;
        private final float fade;
        private final boolean friend;

        private ArrowTarget(float dx, float dy, float fade, boolean friend) {
            this.dx = dx;
            this.dy = dy;
            this.fade = fade;
            this.friend = friend;
        }

        private float dx() { return this.dx; }
        private float dy() { return this.dy; }
        private float fade() { return this.fade; }
        private boolean friend() { return this.friend; }
    }
}

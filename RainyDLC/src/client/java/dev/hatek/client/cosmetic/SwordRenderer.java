package dev.hatek.client.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hatek.client.module.impl.render.Cosmetics;
import dev.hatek.client.ui.render.Render2D;
import dev.hatek.client.ui.render.TexCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Подменяет рендер меча у локального игрока на кастомную 3D-модель.
 * 1) ItemModelResolver помечает состояние предмета (какой меч, какой контекст);
 * 2) ItemStackRenderState.submit для помеченного состояния рисует нашу геометрию.
 */
public final class SwordRenderer {
    private static final int FULL_BRIGHT = 0xF000F0;

    // Стандартные трансформы minecraft:item/handheld (правая рука; левая зеркалится ItemTransform.apply).
    private static final ItemTransform THIRD_PERSON = new ItemTransform(
            new Vector3f(0.0f, -90.0f, 55.0f), new Vector3f(0.0f, 4.0f / 16.0f, 0.5f / 16.0f),
            new Vector3f(0.85f, 0.85f, 0.85f));
    private static final ItemTransform FIRST_PERSON = new ItemTransform(
            new Vector3f(0.0f, -90.0f, 25.0f), new Vector3f(1.13f / 16.0f, 3.2f / 16.0f, 1.13f / 16.0f),
            new Vector3f(0.68f, 0.68f, 0.68f));

    private static final Map<ItemStackRenderState, Mark> MARKS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Меч, который временно показывается на модельке в колесе (наведение). */
    private static volatile SwordModel previewOverride;
    private static volatile boolean previewActive;

    private record Mark(SwordModel model, ItemDisplayContext context) {
    }

    private SwordRenderer() {
    }

    public static void beginPreview(SwordModel model) {
        previewOverride = model;
        previewActive = true;
    }

    public static void endPreview() {
        previewActive = false;
        previewOverride = null;
    }

    private static boolean handContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    /** Вызывается после ItemModelResolver.updateForTopItem. */
    public static void onUpdate(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context,
                                ItemOwner owner) {
        SwordModel model = null;
        try {
            if (stack != null && !stack.isEmpty() && handContext(context) && owner != null) {
                LivingEntity living = owner.asLivingEntity();
                Minecraft mc = Minecraft.getInstance();
                if (living != null && living == mc.player && stack.is(ItemTags.SWORDS)) {
                    model = previewActive ? previewOverride : Cosmetics.selectedSword();
                }
            }
        } catch (Throwable ignored) {
            model = null;
        }
        if (model != null) {
            MARKS.put(state, new Mark(model, context));
        } else {
            MARKS.remove(state);
        }
    }

    /** @return true, если меч нарисован нами и ванильный рендер нужно отменить. */
    public static boolean submit(ItemStackRenderState state, PoseStack poseStack,
                                 SubmitNodeCollector collector, int light, int overlay) {
        Mark mark = MARKS.get(state);
        if (mark == null) {
            return false;
        }
        SwordModel model = mark.model();
        ItemTransform transform = mark.context().firstPerson() ? FIRST_PERSON : THIRD_PERSON;
        float time = time();

        poseStack.pushPose();
        transform.apply(mark.context().leftHand(), poseStack.last());
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        poseStack.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
        // Меч по диагонали, как ванильный спрайт: рукоять снизу слева, острие сверху справа.
        poseStack.translate(1.8f, 1.8f, 8.0f);
        poseStack.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(-45.0)));

        RenderType type = RenderTypes.entityTranslucent(white());
        collector.submitCustomGeometry(poseStack, type,
                (pose, consumer) -> emit(pose, consumer, model, time, light));
        poseStack.popPose();
        return true;
    }

    public static float time() {
        return (System.nanoTime() / 1_000_000L % 3_600_000L) / 1000.0f;
    }

    private static Identifier white() {
        return TexCache.whitePixel();
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer consumer, SwordModel model,
                             float time, int light) {
        float[] off = new float[3];
        for (SwordModel.Part part : model.parts()) {
            int color = part.paint.color(time, part.centerY());
            if ((color >>> 24) == 0) {
                continue;
            }
            off[0] = 0.0f;
            off[1] = 0.0f;
            off[2] = 0.0f;
            if (part.motion != null) {
                part.motion.offset(time, off);
            }
            cube(pose, consumer, part.x0 + off[0], part.y0 + off[1], part.z0 + off[2],
                    part.x1 + off[0], part.y1 + off[1], part.z1 + off[2], color,
                    part.glow ? FULL_BRIGHT : light);
        }
    }

    private static void cube(PoseStack.Pose pose, VertexConsumer c, float x0, float y0, float z0,
                             float x1, float y1, float z1, int color, int light) {
        Matrix4f m = pose.pose();
        // +Z (перед)
        quad(c, pose, m, color, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        // -Z (зад)
        quad(c, pose, m, color, light, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        // +X
        quad(c, pose, m, color, light, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        // -X
        quad(c, pose, m, color, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        // +Y
        quad(c, pose, m, color, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        // -Y
        quad(c, pose, m, color, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
    }

    private static void quad(VertexConsumer c, PoseStack.Pose pose, Matrix4f m, int color, int light,
                             float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(c, pose, m, ax, ay, az, color, light, nx, ny, nz, 0.0f, 1.0f);
        vertex(c, pose, m, bx, by, bz, color, light, nx, ny, nz, 1.0f, 1.0f);
        vertex(c, pose, m, cx, cy, cz, color, light, nx, ny, nz, 1.0f, 0.0f);
        vertex(c, pose, m, dx, dy, dz, color, light, nx, ny, nz, 0.0f, 0.0f);
    }

    private static void vertex(VertexConsumer c, PoseStack.Pose pose, Matrix4f m, float x, float y, float z,
                               int color, int light, float nx, float ny, float nz, float u, float v) {
        c.addVertex(m, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
    }

    // ------------------------------------------------------------------ 2D-иконка для колеса

    /**
     * Рисует меч плоской иконкой (вид спереди) в пиксельном пространстве Render2D.
     * cx, cy — центр, size — размер ячейки.
     */
    public static void icon(GuiGraphicsExtractor gg, SwordModel model, float cx, float cy, float size) {
        float time = time();
        List<SwordModel.Part> parts = new ArrayList<>(model.parts());
        parts.sort((a, b) -> Float.compare(a.z1, b.z1));
        float length = 0.0f;
        for (SwordModel.Part part : parts) {
            length = Math.max(length, part.y1);
        }
        float scale = size * 0.92f / Math.max(1.0f, length);
        float[] off = new float[3];

        Matrix3x2fStack pose = gg.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate((float) Math.toRadians(45.0));
        // Мелкая сетка: координаты ×4, чтобы дробные части не терялись при округлении fill().
        float unit = scale / 4.0f;
        pose.scale(unit, -unit);
        pose.translate(0.0f, -length * 2.0f);
        for (SwordModel.Part part : parts) {
            int color = part.paint.color(time, part.centerY());
            if ((color >>> 24) == 0) {
                continue;
            }
            off[0] = 0.0f;
            off[1] = 0.0f;
            if (part.motion != null) {
                part.motion.offset(time, off);
            }
            float x0 = (part.x0 + off[0]) * 4.0f;
            float x1 = (part.x1 + off[0]) * 4.0f;
            float y0 = (part.y0 + off[1]) * 4.0f;
            float y1 = (part.y1 + off[1]) * 4.0f;
            // Передние части светлее задних — даёт объём на плоской иконке.
            float depth = Math.max(0.0f, Math.min(1.0f, (part.z1 + 1.5f) / 3.0f));
            int shaded = SwordModel.lerp(SwordModel.lerp(color, 0xFF000000 | (color & 0x00FFFFFF), 0.0f),
                    darken(color, 0.55f), 1.0f - depth);
            Render2D.rect(gg, x0, y0, Math.max(1.0f, x1 - x0), Math.max(1.0f, y1 - y0), shaded);
        }
        pose.popMatrix();
    }

    private static int darken(int argb, float k) {
        int a = argb >>> 24;
        int r = Math.round(((argb >> 16) & 0xFF) * k);
        int g = Math.round(((argb >> 8) & 0xFF) * k);
        int b = Math.round((argb & 0xFF) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}

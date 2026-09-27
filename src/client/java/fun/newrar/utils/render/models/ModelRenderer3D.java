package fun.newrar.utils.render.models;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import fun.newrar.module.impl.render.Models;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import static net.minecraft.client.gl.RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET;

public final class ModelRenderer3D {
    private ModelRenderer3D() {}

    private static final BufferAllocator ALLOCATOR = new BufferAllocator(1 << 18);

    public static final RenderPipeline MODEL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/custom_models")
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(true)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final RenderLayer MODEL_LAYER = RenderLayer.of(
            "custom_models",
            RenderSetup.builder(MODEL_PIPELINE)
                    .translucent()
                    .expectedBufferSize(65536)
                    .build()
    );

    public static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/custom_models_lines")
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .build()
    );

    public static final RenderLayer LINE_LAYER = RenderLayer.of(
            "custom_models_lines",
            RenderSetup.builder(LINE_PIPELINE)
                    .translucent()
                    .expectedBufferSize(16384)
                    .build()
    );

    public static VertexConsumerProvider.Immediate getImmediate() {
        return VertexConsumerProvider.immediate(ALLOCATOR);
    }

    public static int shade(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        int r = MathHelper.clamp((int) (((color >>> 16) & 0xFF) * factor), 0, 255);
        int g = MathHelper.clamp((int) (((color >>> 8) & 0xFF) * factor), 0, 255);
        int b = MathHelper.clamp((int) ((color & 0xFF) * factor), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void drawBox(MatrixStack stack, VertexConsumer buf,
                               float minX, float minY, float minZ,
                               float maxX, float maxY, float maxZ, int color) {
        Matrix4f m = stack.peek().getPositionMatrix();

        int cTop    = shade(color, 1.15f);
        int cBottom = shade(color, 0.60f);
        int cFront  = shade(color, 0.95f);
        int cBack   = shade(color, 0.80f);
        int cLeft   = shade(color, 0.88f);
        int cRight  = shade(color, 0.88f);

        // Top face (+Y is down in model coordinates, so min Y is top)
        buf.vertex(m, minX, minY, minZ).color(cTop);
        buf.vertex(m, maxX, minY, minZ).color(cTop);
        buf.vertex(m, maxX, minY, maxZ).color(cTop);
        buf.vertex(m, minX, minY, maxZ).color(cTop);

        // Bottom face (+Y max)
        buf.vertex(m, minX, maxY, maxZ).color(cBottom);
        buf.vertex(m, maxX, maxY, maxZ).color(cBottom);
        buf.vertex(m, maxX, maxY, minZ).color(cBottom);
        buf.vertex(m, minX, maxY, minZ).color(cBottom);

        // Front face (-Z)
        buf.vertex(m, minX, maxY, minZ).color(cFront);
        buf.vertex(m, maxX, maxY, minZ).color(cFront);
        buf.vertex(m, maxX, minY, minZ).color(cFront);
        buf.vertex(m, minX, minY, minZ).color(cFront);

        // Back face (+Z)
        buf.vertex(m, minX, minY, maxZ).color(cBack);
        buf.vertex(m, maxX, minY, maxZ).color(cBack);
        buf.vertex(m, maxX, maxY, maxZ).color(cBack);
        buf.vertex(m, minX, maxY, maxZ).color(cBack);

        // Left face (-X)
        buf.vertex(m, minX, maxY, maxZ).color(cLeft);
        buf.vertex(m, minX, maxY, minZ).color(cLeft);
        buf.vertex(m, minX, minY, minZ).color(cLeft);
        buf.vertex(m, minX, minY, maxZ).color(cLeft);

        // Right face (+X)
        buf.vertex(m, maxX, minY, maxZ).color(cRight);
        buf.vertex(m, maxX, minY, minZ).color(cRight);
        buf.vertex(m, maxX, maxY, minZ).color(cRight);
        buf.vertex(m, maxX, maxY, maxZ).color(cRight);
    }

    public static void drawQuad(MatrixStack stack, VertexConsumer buf,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float x4, float y4, float z4, int color) {
        Matrix4f m = stack.peek().getPositionMatrix();
        buf.vertex(m, x1, y1, z1).color(color);
        buf.vertex(m, x2, y2, z2).color(color);
        buf.vertex(m, x3, y3, z3).color(color);
        buf.vertex(m, x4, y4, z4).color(color);
    }

    public static void drawTriangle(MatrixStack stack, VertexConsumer buf,
                                    float x1, float y1, float z1,
                                    float x2, float y2, float z2,
                                    float x3, float y3, float z3, int color) {
        Matrix4f m = stack.peek().getPositionMatrix();
        buf.vertex(m, x1, y1, z1).color(color);
        buf.vertex(m, x2, y2, z2).color(color);
        buf.vertex(m, x3, y3, z3).color(color);
        buf.vertex(m, x3, y3, z3).color(color); // Degenerate quad for triangle
    }

    public static void drawTorusRing(MatrixStack stack, VertexConsumer buf,
                                     float radius, float tubeRadius, int ringSegs, int color) {
        Matrix4f m = stack.peek().getPositionMatrix();
        float step = (float) (Math.PI * 2.0 / ringSegs);

        for (int i = 0; i < ringSegs; i++) {
            float a1 = i * step;
            float a2 = (i + 1) * step;

            float cos1 = MathHelper.cos(a1);
            float sin1 = MathHelper.sin(a1);
            float cos2 = MathHelper.cos(a2);
            float sin2 = MathHelper.sin(a2);

            // Outer tire face
            float rOut = radius + tubeRadius;
            float rIn  = radius - tubeRadius;
            float tH   = tubeRadius * 0.9f;

            int cTop = shade(color, 0.95f + 0.15f * cos1);
            int cBot = shade(color, 0.70f + 0.15f * cos2);

            // Outer rim ring
            buf.vertex(m, -tH, sin1 * rOut, cos1 * rOut).color(cTop);
            buf.vertex(m,  tH, sin1 * rOut, cos1 * rOut).color(cTop);
            buf.vertex(m,  tH, sin2 * rOut, cos2 * rOut).color(cBot);
            buf.vertex(m, -tH, sin2 * rOut, cos2 * rOut).color(cBot);

            // Side face 1 (+X)
            buf.vertex(m, tH, sin1 * rIn,  cos1 * rIn).color(cTop);
            buf.vertex(m, tH, sin1 * rOut, cos1 * rOut).color(cTop);
            buf.vertex(m, tH, sin2 * rOut, cos2 * rOut).color(cBot);
            buf.vertex(m, tH, sin2 * rIn,  cos2 * rIn).color(cBot);

            // Side face 2 (-X)
            buf.vertex(m, -tH, sin1 * rOut, cos1 * rOut).color(cTop);
            buf.vertex(m, -tH, sin1 * rIn,  cos1 * rIn).color(cTop);
            buf.vertex(m, -tH, sin2 * rIn,  cos2 * rIn).color(cBot);
            buf.vertex(m, -tH, sin2 * rOut, cos2 * rOut).color(cBot);

            // Inner rim ring
            buf.vertex(m,  tH, sin1 * rIn, cos1 * rIn).color(cBot);
            buf.vertex(m, -tH, sin1 * rIn, cos1 * rIn).color(cBot);
            buf.vertex(m, -tH, sin2 * rIn, cos2 * rIn).color(cBot);
            buf.vertex(m,  tH, sin2 * rIn, cos2 * rIn).color(cBot);
        }
    }

    public static void drawCylinder(MatrixStack stack, VertexConsumer buf,
                                    float x, float y, float z,
                                    float radius, float length, int segments, int color) {
        Matrix4f m = stack.peek().getPositionMatrix();
        float step = (float) (Math.PI * 2.0 / segments);

        for (int i = 0; i < segments; i++) {
            float a1 = i * step;
            float a2 = (i + 1) * step;

            float y1 = y + MathHelper.sin(a1) * radius;
            float z1 = z + MathHelper.cos(a1) * radius;
            float y2 = y + MathHelper.sin(a2) * radius;
            float z2 = z + MathHelper.cos(a2) * radius;

            int c = shade(color, 0.85f + 0.15f * MathHelper.cos(a1));

            buf.vertex(m, x,          y1, z1).color(c);
            buf.vertex(m, x + length, y1, z1).color(c);
            buf.vertex(m, x + length, y2, z2).color(c);
            buf.vertex(m, x,          y2, z2).color(c);
        }
    }

    // ==========================================
    // 1. WHEELCHAIR MODEL (Инвалидная коляска)
    // ==========================================
    public static void renderWheelchair(MatrixStack stack, VertexConsumer quads, VertexConsumer lines,
                                        Models models, float wheelRotation) {
        int primary = models.getPrimaryColor();
        int frameColor = 0xFF2B2D35; // Sleek matte dark titanium
        int seatColor  = 0xFF1C1D22; // Dark luxury leather
        int gripColor  = 0xFF111113; // Black rubber
        int tireColor  = 0xFF1A1A1E; // Tread rubber
        int rimColor   = primary;    // Anodized colored aluminum rims
        int chrome     = 0xFFD8DCE4; // Chrome handrims & spokes

        // Seat Cushion (Hips rest right at y = 0.72)
        drawBox(stack, quads, -0.28f, 0.73f, -0.24f, 0.28f, 0.78f, 0.24f, seatColor);
        // Seat accent piping
        drawBox(stack, quads, -0.29f, 0.73f, -0.25f, 0.29f, 0.75f, -0.23f, primary);

        // Backrest (Ergonomic support pad behind back)
        drawBox(stack, quads, -0.26f, 0.18f, 0.23f, 0.26f, 0.72f, 0.28f, seatColor);
        // Backrest cross accent
        drawBox(stack, quads, -0.24f, 0.40f, 0.282f, 0.24f, 0.44f, 0.29f, primary);

        // Frame: Main bottom rails (Left & Right)
        drawBox(stack, quads, -0.28f, 0.78f, -0.24f, -0.24f, 0.82f, 0.24f, frameColor);
        drawBox(stack, quads,  0.24f, 0.78f, -0.24f,  0.28f, 0.82f, 0.24f, frameColor);

        // Frame: Vertical back posts
        drawBox(stack, quads, -0.27f, 0.08f, 0.24f, -0.23f, 0.80f, 0.28f, frameColor);
        drawBox(stack, quads,  0.23f, 0.08f, 0.24f,  0.27f, 0.80f, 0.28f, frameColor);

        // Rear Push Handles with ergonomic rubber grips
        drawBox(stack, quads, -0.27f, 0.08f, 0.28f, -0.23f, 0.12f, 0.42f, frameColor);
        drawBox(stack, quads,  0.23f, 0.08f, 0.28f,  0.27f, 0.12f, 0.42f, frameColor);
        drawBox(stack, quads, -0.28f, 0.07f, 0.32f, -0.22f, 0.13f, 0.44f, gripColor);
        drawBox(stack, quads,  0.22f, 0.07f, 0.32f,  0.28f, 0.13f, 0.44f, gripColor);

        // Armrests (Left & Right side rails and padded pads)
        drawBox(stack, quads, -0.30f, 0.48f, -0.16f, -0.26f, 0.52f, 0.20f, seatColor);
        drawBox(stack, quads,  0.26f, 0.48f, -0.16f,  0.30f, 0.52f, 0.20f, seatColor);
        drawBox(stack, quads, -0.29f, 0.52f, -0.05f, -0.27f, 0.76f, -0.02f, frameColor);
        drawBox(stack, quads,  0.27f, 0.52f, -0.05f,  0.29f, 0.76f, -0.02f, frameColor);

        // Side guards / Mudguards (prevent clothing touching wheels)
        drawBox(stack, quads, -0.305f, 0.52f, -0.14f, -0.30f, 0.76f, 0.22f, 0xFF353842);
        drawBox(stack, quads,  0.30f,  0.52f, -0.14f,  0.305f, 0.76f, 0.22f, 0xFF353842);

        // Front down-tubes and Footplate
        drawBox(stack, quads, -0.24f, 0.82f, -0.24f, -0.20f, 1.38f, -0.20f, frameColor);
        drawBox(stack, quads,  0.20f, 0.82f, -0.24f,  0.24f, 1.38f, -0.20f, frameColor);
        // Footrests (dual textured footplate where feet rest)
        drawBox(stack, quads, -0.26f, 1.37f, -0.34f, 0.26f, 1.41f, -0.18f, 0xFF22242A);
        drawBox(stack, quads, -0.24f, 1.365f, -0.33f, 0.24f, 1.375f, -0.19f, primary);

        // Rear Anti-tipper tubes & mini wheels
        drawBox(stack, quads, -0.24f, 0.82f, 0.24f, -0.21f, 1.42f, 0.42f, frameColor);
        drawBox(stack, quads,  0.21f, 0.82f, 0.24f,  0.24f, 1.42f, 0.42f, frameColor);
        drawBox(stack, quads, -0.25f, 1.40f, 0.40f, -0.20f, 1.46f, 0.46f, gripColor);
        drawBox(stack, quads,  0.20f, 1.40f, 0.40f,  0.25f, 1.46f, 0.46f, gripColor);

        // Front Caster Forks & Wheels
        renderFrontCaster(stack, quads, -0.22f, 1.36f, -0.24f, frameColor, tireColor, chrome);
        renderFrontCaster(stack, quads,  0.22f, 1.36f, -0.24f, frameColor, tireColor, chrome);

        // Large Drive Wheels: Left & Right
        // Left wheel: slight negative camber (-4 degrees)
        stack.push();
        stack.translate(0.33f, 0.90f, 0.06f);
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-4.0f));
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(wheelRotation));
        renderDriveWheel(stack, quads, lines, tireColor, rimColor, chrome, true);
        stack.pop();

        // Right wheel: slight negative camber (+4 degrees)
        stack.push();
        stack.translate(-0.33f, 0.90f, 0.06f);
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(4.0f));
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(wheelRotation));
        renderDriveWheel(stack, quads, lines, tireColor, rimColor, chrome, false);
        stack.pop();
    }

    private static void renderFrontCaster(MatrixStack stack, VertexConsumer quads,
                                          float x, float y, float z,
                                          int frameColor, int tireColor, int chrome) {
        // Caster fork
        drawBox(stack, quads, x - 0.035f, y - 0.12f, z - 0.035f, x + 0.035f, y, z + 0.035f, frameColor);
        // Caster wheel (radius 0.08m)
        drawBox(stack, quads, x - 0.025f, y, z - 0.08f, x + 0.025f, y + 0.13f, z + 0.08f, tireColor);
        drawBox(stack, quads, x - 0.028f, y + 0.04f, z - 0.03f, x + 0.028f, y + 0.09f, z + 0.03f, chrome);
    }

    private static void renderDriveWheel(MatrixStack stack, VertexConsumer quads, VertexConsumer lines,
                                         int tireColor, int rimColor, int chrome, boolean isLeft) {
        float wheelRadius = 0.58f;
        float tireThickness = 0.028f;

        // 1. 3D Tire (16-sided torus ring)
        drawTorusRing(stack, quads, wheelRadius, tireThickness, 16, tireColor);

        // 2. Anodized Rim
        drawTorusRing(stack, quads, wheelRadius - tireThickness * 1.2f, tireThickness * 0.45f, 16, rimColor);

        // 3. Central Hubcap
        float hubOffset = isLeft ? 0.015f : -0.055f;
        drawCylinder(stack, quads, hubOffset, 0, 0, 0.065f, 0.04f, 12, rimColor);
        drawCylinder(stack, quads, hubOffset - (isLeft ? 0.005f : -0.045f), 0, 0, 0.035f, 0.05f, 8, chrome);

        // 4. Spokes (12 radial wire spokes)
        Matrix4f m = stack.peek().getPositionMatrix();
        float step = (float) (Math.PI * 2.0 / 12.0);
        float spokeX = isLeft ? 0.01f : -0.01f;
        for (int i = 0; i < 12; i++) {
            float angle = i * step;
            float rInner = 0.06f;
            float rOuter = wheelRadius - 0.04f;

            float y1 = MathHelper.sin(angle) * rInner;
            float z1 = MathHelper.cos(angle) * rInner;
            float y2 = MathHelper.sin(angle) * rOuter;
            float z2 = MathHelper.cos(angle) * rOuter;

            lines.vertex(m, spokeX, y1, z1).color(chrome);
            lines.vertex(m, spokeX, y2, z2).color(chrome);
        }

        // 5. Outer Push Handrim (stands off by 0.035m outward)
        float handrimX = isLeft ? 0.045f : -0.045f;
        stack.push();
        stack.translate(handrimX, 0, 0);
        drawTorusRing(stack, quads, wheelRadius - 0.06f, 0.015f, 16, chrome);
        stack.pop();

        // Handrim standoffs (4 tabs connecting handrim to wheel rim)
        for (int i = 0; i < 4; i++) {
            float angle = (float) (i * Math.PI / 2.0 + Math.PI / 4.0);
            float tabR = wheelRadius - 0.06f;
            float ty = MathHelper.sin(angle) * tabR;
            float tz = MathHelper.cos(angle) * tabR;
            if (isLeft) {
                drawBox(stack, quads, 0.01f, ty - 0.01f, tz - 0.01f, 0.045f, ty + 0.01f, tz + 0.01f, chrome);
            } else {
                drawBox(stack, quads, -0.045f, ty - 0.01f, tz - 0.01f, -0.01f, ty + 0.01f, tz + 0.01f, chrome);
            }
        }
    }

    // ==========================================
    // 2. CAT / FOX / BUNNY EARS (Attached to Head)
    // ==========================================
    public static void renderEars(MatrixStack stack, VertexConsumer quads,
                                  Models models, String type, float time) {
        int primary   = models.getPrimaryColor();
        int secondary = models.getSecondaryColor();
        int pinkInner = 0xFFFF99BB;
        int darkTip   = 0xFF1C1D24;

        boolean anim = models.physics.getValue();
        // Organic twitch every ~4 seconds
        float twitchPhase = (time * 0.25f) % 1.0f;
        float twitchAngleR = 0f;
        float twitchAngleL = 0f;
        if (anim && twitchPhase < 0.06f) {
            twitchAngleR = MathHelper.sin(twitchPhase / 0.06f * (float) Math.PI * 4) * 0.16f;
        } else if (anim && twitchPhase > 0.50f && twitchPhase < 0.56f) {
            twitchAngleL = MathHelper.sin((twitchPhase - 0.50f) / 0.06f * (float) Math.PI * 4) * 0.16f;
        }

        if ("Заячьи".equalsIgnoreCase(type)) {
            // Bunny Ears: Tall, expressive, one ear floppy!
            // Right ear (upright)
            stack.push();
            stack.translate(-0.16f, -0.50f, 0.0f);
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-8.0f));
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(twitchAngleR * 30f));
            renderSingleBunnyEar(stack, quads, primary, pinkInner, false);
            stack.pop();

            // Left ear (cute floppy fold!)
            stack.push();
            stack.translate(0.16f, -0.50f, 0.0f);
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(8.0f));
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(twitchAngleL * 30f));
            renderSingleBunnyEar(stack, quads, primary, pinkInner, true);
            stack.pop();

        } else if ("Лисьи".equalsIgnoreCase(type)) {
            // Fox / Wolf Ears: Larger, plush, two-toned dark tips
            stack.push();
            stack.translate(-0.20f, -0.50f, -0.02f);
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-15.0f + twitchAngleR * 40f));
            renderSingleFurryEar(stack, quads, primary, pinkInner, darkTip, false);
            stack.pop();

            stack.push();
            stack.translate(0.20f, -0.50f, -0.02f);
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(15.0f + twitchAngleL * 40f));
            renderSingleFurryEar(stack, quads, primary, pinkInner, darkTip, true);
            stack.pop();

        } else {
            // Neko Cat Ears: Cute anime triangular ears with ribbons & bells
            stack.push();
            stack.translate(-0.18f, -0.50f, -0.02f);
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-12.0f + twitchAngleR * 35f));
            renderSingleCatEar(stack, quads, primary, pinkInner, false);
            stack.pop();

            stack.push();
            stack.translate(0.18f, -0.50f, -0.02f);
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(12.0f + twitchAngleL * 35f));
            renderSingleCatEar(stack, quads, primary, pinkInner, true);
            stack.pop();

            // Ear Ribbons and Golden Bells
            if (models.isBowsActive()) {
                renderEarBow(stack, quads, -0.19f, -0.49f, -0.04f, secondary);
                renderEarBow(stack, quads,  0.19f, -0.49f, -0.04f, secondary);
            }
        }
    }

    private static void renderSingleCatEar(MatrixStack stack, VertexConsumer quads,
                                           int outerColor, int innerColor, boolean isLeft) {
        // Outer triangular shell (base at y=0, tip at y=-0.22)
        drawTriangle(stack, quads, -0.07f, 0.0f, -0.04f,
                0.07f, 0.0f, -0.04f,
                0.0f, -0.22f, -0.01f, outerColor);
        drawTriangle(stack, quads, 0.07f, 0.0f, 0.04f,
                -0.07f, 0.0f, 0.04f,
                0.0f, -0.22f, -0.01f, shade(outerColor, 0.85f));
        // Inner pink cavity fluff
        drawTriangle(stack, quads, -0.045f, 0.01f, -0.042f,
                0.045f, 0.01f, -0.042f,
                0.0f, -0.18f, -0.012f, innerColor);
    }

    private static void renderSingleFurryEar(MatrixStack stack, VertexConsumer quads,
                                             int furColor, int innerColor, int tipColor, boolean isLeft) {
        // Lower base fur
        drawTriangle(stack, quads, -0.09f, 0.0f, -0.05f,
                0.09f, 0.0f, -0.05f,
                0.0f, -0.26f, -0.01f, furColor);
        drawTriangle(stack, quads, 0.09f, 0.0f, 0.05f,
                -0.09f, 0.0f, 0.05f,
                0.0f, -0.26f, -0.01f, shade(furColor, 0.85f));

        // Dark Fox Tip on upper portion
        drawTriangle(stack, quads, -0.035f, -0.16f, -0.045f,
                0.035f, -0.16f, -0.045f,
                0.0f, -0.26f, -0.01f, tipColor);

        // Fluffy inner ear tufts
        drawTriangle(stack, quads, -0.055f, 0.01f, -0.052f,
                0.055f, 0.01f, -0.052f,
                0.0f, -0.17f, -0.015f, innerColor);
    }

    private static void renderSingleBunnyEar(MatrixStack stack, VertexConsumer quads,
                                             int outerColor, int innerColor, boolean floppy) {
        if (!floppy) {
            // Straight upright ear (0.42m tall)
            drawBox(stack, quads, -0.045f, -0.42f, -0.025f, 0.045f, 0.0f, 0.025f, outerColor);
            // Inner pink strip
            drawBox(stack, quads, -0.028f, -0.38f, -0.028f, 0.028f, -0.02f, -0.024f, innerColor);
        } else {
            // Lower half upright
            drawBox(stack, quads, -0.045f, -0.24f, -0.025f, 0.045f, 0.0f, 0.025f, outerColor);
            drawBox(stack, quads, -0.028f, -0.22f, -0.028f, 0.028f, -0.02f, -0.024f, innerColor);

            // Upper half folded adorably forward & down!
            stack.push();
            stack.translate(0, -0.24f, 0);
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-110.0f));
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-15.0f));
            drawBox(stack, quads, -0.042f, -0.19f, -0.022f, 0.042f, 0.0f, 0.022f, outerColor);
            drawBox(stack, quads, -0.025f, -0.17f, -0.025f, 0.025f, -0.01f, -0.021f, innerColor);
            stack.pop();
        }
    }

    private static void renderEarBow(MatrixStack stack, VertexConsumer quads,
                                     float x, float y, float z, int bowColor) {
        int gold = 0xFFFFD700;
        // Central knot
        drawBox(stack, quads, x - 0.018f, y - 0.018f, z - 0.012f, x + 0.018f, y + 0.018f, z + 0.012f, bowColor);
        // Left loop
        drawBox(stack, quads, x - 0.05f, y - 0.015f, z - 0.008f, x - 0.015f, y + 0.015f, z + 0.008f, bowColor);
        // Right loop
        drawBox(stack, quads, x + 0.015f, y - 0.015f, z - 0.008f, x + 0.05f, y + 0.015f, z + 0.008f, bowColor);
        // Golden bell in center
        drawBox(stack, quads, x - 0.012f, y - 0.032f, z - 0.012f, x + 0.012f, y - 0.015f, z + 0.012f, gold);
    }

    // ==========================================
    // 3. MAID HEADPIECE / HAIR RIBBON (Attached to Head)
    // ==========================================
    public static void renderHeadpiece(MatrixStack stack, VertexConsumer quads, int primary, int secondary) {
        // Frilly lace headband over top of head
        int lace = 0xFFFFFFFF;
        for (int i = -6; i <= 6; i++) {
            float x = i * 0.042f;
            float y = -0.51f - MathHelper.cos(i * 0.25f) * 0.02f;
            float ruffH = (i % 2 == 0) ? 0.035f : 0.022f;
            drawBox(stack, quads, x - 0.02f, y - ruffH, 0.02f, x + 0.02f, y, 0.06f, lace);
        }
        // Central ribbon bow on headpiece
        drawBox(stack, quads, -0.04f, -0.53f, 0.015f, 0.04f, -0.50f, 0.065f, primary);
    }

    // ==========================================
    // 4. PLEATED SKIRT (Attached to Body)
    // ==========================================
    public static void renderPleatedSkirt(MatrixStack stack, VertexConsumer quads,
                                          Models models, float limbAngle, float limbDistance) {
        int primary   = models.getPrimaryColor();
        int secondary = models.getSecondaryColor();
        int laceWhite = 0xFFFFFFFF;

        float sway = 0f;
        if (models.physics.getValue()) {
            sway = MathHelper.sin(limbAngle) * limbDistance * 0.10f;
        }

        stack.push();
        // Position at waist
        stack.translate(0, 0.72f, 0);
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sway * 20f));

        int pleats = 12;
        float rWaist = 0.25f;
        float rHem   = 0.38f;
        float skirtH = 0.32f;
        float step   = (float) (Math.PI * 2.0 / pleats);

        for (int i = 0; i < pleats; i++) {
            float a1 = i * step;
            float a2 = (i + 1) * step;

            float xW1 = MathHelper.sin(a1) * rWaist;
            float zW1 = MathHelper.cos(a1) * rWaist;
            float xW2 = MathHelper.sin(a2) * rWaist;
            float zW2 = MathHelper.cos(a2) * rWaist;

            float xH1 = MathHelper.sin(a1) * rHem;
            float zH1 = MathHelper.cos(a1) * rHem;
            float xH2 = MathHelper.sin(a2) * rHem;
            float zH2 = MathHelper.cos(a2) * rHem;

            // Alternating shaded folds
            int pleatColor = (i % 2 == 0) ? shade(primary, 1.10f) : shade(primary, 0.85f);
            drawQuad(stack, quads,
                    xW1, 0.0f, zW1,
                    xW2, 0.0f, zW2,
                    xH2, skirtH, zH2,
                    xH1, skirtH, zH1, pleatColor);

            // White lace frill along bottom hem
            drawQuad(stack, quads,
                    xH1, skirtH, zH1,
                    xH2, skirtH, zH2,
                    xH2 * 1.05f, skirtH + 0.035f, zH2 * 1.05f,
                    xH1 * 1.05f, skirtH + 0.035f, zH1 * 1.05f, laceWhite);
        }

        // Waistband
        drawTorusRing(stack, quads, rWaist, 0.015f, 16, secondary);

        stack.pop();
    }

    // ==========================================
    // 5. TAILS (Attached to Body lower back)
    // ==========================================
    public static void renderTail(MatrixStack stack, VertexConsumer quads,
                                  Models models, String type, float time,
                                  float limbAngle, float limbDistance) {
        int primary   = models.getPrimaryColor();
        int whiteTip  = 0xFFFFFFFF;
        boolean anim  = models.physics.getValue();

        stack.push();
        stack.translate(0, 0.72f, 0.14f); // Base of spine

        if ("Заячий".equalsIgnoreCase(type)) {
            // Fluffy Round Bunny Pom-Pom Tail
            float wiggle = anim ? MathHelper.sin(time * 6f) * 0.02f : 0f;
            drawBox(stack, quads, -0.06f, -0.05f + wiggle, 0.02f, 0.06f, 0.07f + wiggle, 0.14f, whiteTip);
            drawBox(stack, quads, -0.05f, -0.04f + wiggle, 0.13f, 0.05f, 0.06f + wiggle, 0.17f, shade(whiteTip, 0.95f));

        } else if ("Демон".equalsIgnoreCase(type)) {
            // Sleek flexible tail with 3D Arrowhead / Spade tip
            renderSegmentedTail(stack, quads, primary, 0xFFCC1133, 7, 0.025f, 0.012f, 0.08f, anim, time, limbAngle, limbDistance, true);

        } else if ("Лисий".equalsIgnoreCase(type)) {
            // Thick, voluminous fluffy Fox Tail
            renderSegmentedTail(stack, quads, primary, whiteTip, 6, 0.085f, 0.035f, 0.12f, anim, time, limbAngle, limbDistance, false);

        } else {
            // Cat Tail: Graceful S-curve with white fluffy tip
            renderSegmentedTail(stack, quads, primary, whiteTip, 6, 0.045f, 0.022f, 0.10f, anim, time, limbAngle, limbDistance, false);
        }

        stack.pop();
    }

    private static void renderSegmentedTail(MatrixStack stack, VertexConsumer quads,
                                            int baseColor, int tipColor,
                                            int segments, float maxRadius, float minRadius, float segLen,
                                            boolean anim, float time, float limbAngle, float limbDistance,
                                            boolean isDemon) {
        for (int i = 0; i < segments; i++) {
            float progress = (float) i / (segments - 1);
            float radius = MathHelper.lerp(progress, maxRadius, minRadius);
            if (!isDemon && i == 2 || i == 3) radius *= 1.35f; // Extra fluffy midsection!

            int segColor = (i >= segments - 2) ? tipColor : baseColor;

            // Tail swaying physics
            float swayX = 0f;
            float curveY = 0.22f; // curves upward
            if (anim) {
                float wave = MathHelper.sin(time * 3.0f - i * 0.55f) * 0.14f;
                float walkSway = MathHelper.sin(limbAngle * 0.7f - i * 0.45f) * limbDistance * 0.22f;
                swayX = wave + walkSway;
            }

            stack.multiply(RotationAxis.POSITIVE_Y.rotation(swayX));
            stack.multiply(RotationAxis.POSITIVE_X.rotation(curveY));

            // Draw tail segment box
            drawBox(stack, quads, -radius, -radius, 0.0f, radius, radius, segLen, segColor);
            stack.translate(0, 0, segLen);
        }

        // Demon Spade arrowhead tip at final segment
        if (isDemon) {
            int spadeColor = tipColor;
            drawTriangle(stack, quads, -0.06f, 0.0f, 0.0f,
                    0.06f, 0.0f, 0.0f,
                    0.0f, 0.0f, 0.12f, spadeColor);
            drawTriangle(stack, quads, 0.0f, -0.06f, 0.0f,
                    0.0f, 0.06f, 0.0f,
                    0.0f, 0.0f, 0.12f, shade(spadeColor, 0.90f));
        }
    }

    // ==========================================
    // 6. DEMON HORNS (Attached to Head)
    // ==========================================
    public static void renderDemonHorns(MatrixStack stack, VertexConsumer quads, int primary) {
        int darkObsidian = 0xFF18151E;
        int glowTip = primary;

        // Right horn (curves back & up)
        stack.push();
        stack.translate(-0.18f, -0.48f, -0.08f);
        renderSingleHorn(stack, quads, darkObsidian, glowTip, false);
        stack.pop();

        // Left horn
        stack.push();
        stack.translate(0.18f, -0.48f, -0.08f);
        renderSingleHorn(stack, quads, darkObsidian, glowTip, true);
        stack.pop();
    }

    private static void renderSingleHorn(MatrixStack stack, VertexConsumer quads,
                                         int baseColor, int tipColor, boolean isLeft) {
        float dir = isLeft ? 1.0f : -1.0f;
        int segs = 4;
        for (int i = 0; i < segs; i++) {
            float t = (float) i / segs;
            float r = MathHelper.lerp(t, 0.045f, 0.012f);
            int col = (i >= segs - 1) ? tipColor : baseColor;

            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(dir * 18.0f));
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-24.0f));
            drawBox(stack, quads, -r, -r, 0.0f, r, r, 0.07f, col);
            stack.translate(0, 0, 0.065f);
        }
    }

    // ==========================================
    // 7. DEMON WINGS (Attached to Body upper back)
    // ==========================================
    public static void renderDemonWings(MatrixStack stack, VertexConsumer quads,
                                        Models models, float time) {
        int boneColor = 0xFF221E2A;
        int membraneColor = models.getPrimaryColor();
        boolean anim = models.physics.getValue();

        // Flapping animation
        float flap = anim ? MathHelper.sin(time * 3.5f) * 18.0f : 12.0f;

        // Right wing
        stack.push();
        stack.translate(-0.08f, 0.18f, 0.14f);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-20.0f - flap));
        renderSingleWing(stack, quads, boneColor, membraneColor, false);
        stack.pop();

        // Left wing
        stack.push();
        stack.translate(0.08f, 0.18f, 0.14f);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(20.0f + flap));
        renderSingleWing(stack, quads, boneColor, membraneColor, true);
        stack.pop();
    }

    private static void renderSingleWing(MatrixStack stack, VertexConsumer quads,
                                         int boneColor, int membraneColor, boolean isLeft) {
        float side = isLeft ? 1.0f : -1.0f;

        // Bone structure
        // Arm 1: extending out & up
        drawBox(stack, quads, 0.0f, -0.02f, 0.0f, side * 0.35f, 0.02f, 0.03f, boneColor);
        // Arm 2: elbow joint going down-out
        stack.push();
        stack.translate(side * 0.35f, 0.0f, 0.0f);
        drawBox(stack, quads, 0.0f, -0.02f, 0.0f, side * 0.32f, 0.35f, 0.025f, boneColor);

        // Wing Membrane (spanning between spine and struts)
        drawTriangle(stack, quads,
                -side * 0.35f, 0.0f, 0.01f,
                0.0f, 0.0f, 0.01f,
                side * 0.30f, 0.35f, 0.01f, membraneColor);
        drawTriangle(stack, quads,
                -side * 0.35f, 0.0f, 0.01f,
                side * 0.30f, 0.35f, 0.01f,
                side * 0.10f, 0.42f, 0.01f, shade(membraneColor, 0.88f));

        stack.pop();
    }

    // ==========================================
    // 8. CUTE MUZZLE / SNOUT (Attached to Head)
    // ==========================================
    public static void renderMuzzle(MatrixStack stack, VertexConsumer quads, int primary) {
        int noseColor = 0xFF141416; // Black nose pad
        int furColor = primary;

        // Snout box protruding from face
        drawBox(stack, quads, -0.09f, -0.25f, -0.34f, 0.09f, -0.15f, -0.24f, furColor);
        // Cute nose on tip
        drawBox(stack, quads, -0.035f, -0.25f, -0.352f, 0.035f, -0.21f, -0.338f, noseColor);
        // Whisker dots / lips
        drawBox(stack, quads, -0.07f, -0.19f, -0.345f, 0.07f, -0.16f, -0.339f, shade(furColor, 0.85f));
    }

    // ==========================================
    // 9. CHEST BOW / RIBBON (Attached to Body)
    // ==========================================
    public static void renderChestBow(MatrixStack stack, VertexConsumer quads, int bowColor) {
        float y = 0.16f;
        float z = -0.14f; // Front of chest

        // Center knot
        drawBox(stack, quads, -0.022f, y - 0.022f, z - 0.015f, 0.022f, y + 0.022f, z, bowColor);
        // Loops
        drawBox(stack, quads, -0.075f, y - 0.03f, z - 0.01f, -0.02f, y + 0.015f, z, bowColor);
        drawBox(stack, quads,  0.02f,  y - 0.03f, z - 0.01f,  0.075f, y + 0.015f, z, bowColor);
        // Trailing ribbon tails
        drawBox(stack, quads, -0.045f, y + 0.02f, z - 0.008f, -0.015f, y + 0.12f, z, shade(bowColor, 0.90f));
        drawBox(stack, quads,  0.015f, y + 0.02f, z - 0.008f,  0.045f, y + 0.12f, z, shade(bowColor, 0.90f));
    }
}

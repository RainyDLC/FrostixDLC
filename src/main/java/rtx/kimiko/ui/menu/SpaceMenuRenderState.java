package rtx.kimiko.ui.menu;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

/**
 * Полноэкранный квад, который рисует фон меню одним фрагментным шейдером
 * в полном разрешении окна (без текстур, поэтому нет пикселизации).
 *
 * Параметры передаются прямо в вершинах, UBO не нужен:
 *  Position.z  -> прогресс засасывания (снаружи) / угол вращения галактики (внутри)
 *  UV0.x       -> прогресс появления внутри (-1 = мы снаружи)
 *  UV0.y       -> соотношение сторон
 *  LineWidth   -> время
 *  Color.rg    -> экранные UV, Color.b -> индекс наведенной иконки + 1, Color.a -> сила наведения
 */
public final class SpaceMenuRenderState implements SimpleGuiElementRenderState {

    public static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("Color", VertexFormatElement.COLOR)
            .add("LineWidth", VertexFormatElement.LINE_WIDTH)
            .build();

    public static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("kimiko", "pipeline/main_menu_space"))
            .withVertexShader(Identifier.of("kimiko", "ui/mainmenu/space"))
            .withFragmentShader(Identifier.of("kimiko", "ui/mainmenu/space"))
            .withVertexFormat(FORMAT, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .build();

    private final Matrix3x2f pose;
    private final int width, height;
    private final float p0, p1, aspect, time;
    private final int hoverIndex, hoverAmount;
    private final ScreenRect bounds;

    public SpaceMenuRenderState(Matrix3x2fc pose, int width, int height, float p0, float p1, float time, int hoverIndex, float hover) {
        this.pose = new Matrix3x2f(pose);
        this.width = width;
        this.height = height;
        this.p0 = p0;
        this.p1 = p1;
        this.aspect = width / (float) Math.max(1, height);
        this.time = time;
        this.hoverIndex = Math.max(0, Math.min(254, hoverIndex + 1));
        this.hoverAmount = Math.max(0, Math.min(255, Math.round(hover * 255f)));
        this.bounds = new ScreenRect(0, 0, width, height).transformEachVertex(this.pose);
    }

    @Override
    public void setupVertices(VertexConsumer consumer) {
        vertex(consumer, 0, 0, 0, 255);
        vertex(consumer, 0, height, 0, 0);
        vertex(consumer, width, height, 255, 0);
        vertex(consumer, width, 0, 255, 255);
    }

    private void vertex(VertexConsumer consumer, float x, float y, int u, int v) {
        Vector2f p = this.pose.transformPosition(x, y, new Vector2f());
        consumer.vertex(p.x, p.y, this.p0)
                .texture(this.p1, this.aspect)
                .color(u, v, this.hoverIndex, this.hoverAmount)
                .lineWidth(this.time);
    }

    @Override
    public RenderPipeline pipeline() {
        return PIPELINE;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.empty();
    }

    @Override
    public @Nullable ScreenRect scissorArea() {
        return null;
    }

    @Override
    public @Nullable ScreenRect bounds() {
        return this.bounds;
    }
}

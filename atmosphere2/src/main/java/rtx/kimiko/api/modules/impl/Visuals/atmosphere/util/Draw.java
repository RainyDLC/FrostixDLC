package rtx.kimiko.api.modules.impl.Visuals.atmosphere.util;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Draws a whole texture stretched over an arbitrary rect. */
public final class Draw {

    private Draw() {}

    public static void texture(DrawContext ctx, Identifier tex, float x, float y, float w, float h, int argb) {
        int iw = Math.max(1, Math.round(w));
        int ih = Math.max(1, Math.round(h));
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, Math.round(x), Math.round(y), 0f, 0f, iw, ih, iw, ih, argb);
    }

    public static void textureCentered(DrawContext ctx, Identifier tex, float cx, float cy, float w, float h, int argb) {
        texture(ctx, tex, cx - w * 0.5f, cy - h * 0.5f, w, h, argb);
    }
}

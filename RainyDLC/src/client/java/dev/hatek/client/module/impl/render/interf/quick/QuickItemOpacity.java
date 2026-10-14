package dev.hatek.client.module.impl.render.interf.quick;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import java.util.Map;
import java.util.WeakHashMap;
/** Extraction happens before the item atlas is composited. Keep opacity with each item state. */
public final class QuickItemOpacity {
    private static final Map<GuiItemRenderState,Float> VALUES=new WeakHashMap<>();
    private QuickItemOpacity() {}
    public static void capture(GuiItemRenderState item) {
        if(QuickStyle.active() && Render2D.alpha()<1) VALUES.put(item,Render2D.alpha());
    }
    public static int color(GuiItemRenderState item,int original) {
        return premultiplied(original,VALUES.getOrDefault(item,1f));
    }
    public static int premultiplied(int original,float opacity) {
        opacity=Math.max(0,Math.min(1,opacity));
        int a=Math.round((original>>>24)*opacity), r=Math.round(((original>>>16)&255)*opacity);
        int g=Math.round(((original>>>8)&255)*opacity), b=Math.round((original&255)*opacity);
        return (a<<24)|(r<<16)|(g<<8)|b;
    }
}

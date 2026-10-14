package dev.hatek.client.module.impl.render.interf.quick;

import dev.hatek.client.module.impl.render.interf.HudLayout;
import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.LiquidBackdrop;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Vector2f;

/** Original Quick geometry: 16px headers, 11px rows, 6px padding, radius 4.5px. */
public final class QuickStyle {
    public static final float HEADER=16, ROW=11, PAD=6, RADIUS=4.5f;
    private static float glow=1;
    private static boolean active;
    public static boolean active() { return active; }

    private QuickStyle() {}
    public static void begin(HudLayout.Entry entry) { active=true; glow=entry.glow; Render2D.pushAlpha(entry.opacity); }
    public static void end() { Render2D.popAlpha(); glow=1; active=false; }
    public static void panel(GuiGraphicsExtractor gg,float w,float h,float s) {
        float r=RADIUS*s;
        for(int i=8;i>0;i--) {
            float spread=i*s;
            int color=Render2D.withAlpha(Style.accent(),0.009f*(9-i)*glow);
            Render2D.round(gg,-spread,-spread,w+spread*2,h+spread*2,r+spread,color);
        }
        if(LiquidBackdrop.sharpReady()) {
            var p=gg.pose().transformPosition(0,0,new Vector2f());
            float sx=p.x*Render2D.guiScale(), sy=p.y*Render2D.guiScale();
            int tw=LiquidBackdrop.width(), th=LiquidBackdrop.height();
            int height=Math.max(1,Math.round(h));
            // Rounded coverage without applying a global fullscreen blur.
            for(int y=0;y<height;y+=Math.max(1,Math.round(s))) {
                int band=Math.min(Math.max(1,Math.round(s)),height-y);
                float cy=y+band/2f, dy=cy<r?r-cy:cy>h-r?cy-(h-r):0;
                float inset=dy>0?r-(float)Math.sqrt(Math.max(0,r*r-dy*dy)):0;
                int left=Math.round(inset), dw=Math.max(1,Math.round(w-2*inset));
                gg.blit(QuickPipelines.BLUR,LiquidBackdrop.SHARP,left,y,sx+left,th-(sy+y),dw,band,
                        dw,-band,tw,th,Render2D.withAlpha(0xFFFFFFFF,Render2D.alpha()));
            }
        }
        Render2D.round(gg,0,0,w,h,r,0xA0000000);
        Render2D.round(gg,0,0,w,Math.min(h,HEADER*s),r,0x141C1C22);
    }
    public static void text(GuiGraphicsExtractor gg,String text,float x,float y,float h,float s,int color) {
        QuickFont font=QuickFont.of("bold",7*s);font.draw(gg,text,x,font.visualBaseline("H",y,h),color);
    }
}

package dev.hatek.client.module.impl.render.interf.quick;

import dev.hatek.client.module.impl.render.interf.HudLayout;
import dev.hatek.client.module.impl.render.interf.InterfaceHud;
import dev.hatek.client.ui.Style;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;

/** Consumes menu clicks before HUD dragging. Closing the chat saves and releases all grabs. */
public final class QuickElementMenu {
    private static HudLayout.Element selected;
    private static float x,y;
    private static int slider=-1;
    private static float scale() { return Render2D.guiScale(); }
    private static float width() { return 124*scale(); }
    private static float height() { return 86*scale(); }
    private QuickElementMenu() {}
    public static boolean open() { return selected!=null; }
    public static void close() { if(selected!=null) HudLayout.save(); selected=null; slider=-1; }
    public static boolean mouseButton(double mx,double my,int button,int action) {
        if(action==GLFW.GLFW_RELEASE && slider>=0) { slider=-1; HudLayout.save(); return true; }
        if(action!=GLFW.GLFW_PRESS) return false;
        if(selected!=null) {
            if(Render2D.hovered(mx,my,x,y,width(),height())) {
                if(button==GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    float localY=((float)my-y)/scale();
                    if(localY>=66) { HudLayout.resetAppearance(selected); return true; }
                    if(localY>=20 && localY<65) { slider=Math.min(2,(int)((localY-20)/15)); update(mx); }
                }
                return true;
            }
            close();
            return true; // Outside click closes the menu; never drags the element underneath.
        }
        if(button==GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            InterfaceHud.Hit hit=InterfaceHud.hitTest(mx,my);
            if(hit!=null && !hit.media) {
                InterfaceHud.stopDrag(); selected=hit.element;
                x=Math.max(4,Math.min((float)mx,Render2D.screenWidth()-width()-4));
                y=Math.max(4,Math.min((float)my,Render2D.screenHeight()-height()-4)); return true;
            }
        }
        return false;
    }
    private static void update(double mx) {
        if(selected==null || slider<0) return;
        float t=Math.max(0,Math.min(1,((float)mx-x-64*scale())/(50*scale())));
        HudLayout.Entry e=HudLayout.get(selected);
        if(slider==0) HudLayout.setScale(selected,0.5f+1.5f*t);
        if(slider==1) HudLayout.setAppearance(selected,0.1f+0.9f*t,e.glow);
        if(slider==2) HudLayout.setAppearance(selected,e.opacity,2*t);
    }
    public static void render(GuiGraphicsExtractor gg) {
        if(!(Minecraft.getInstance().gui.screen() instanceof ChatScreen)) { close(); return; }
        if(selected==null) return;
        if(slider>=0) update(Render2D.mouseX());
        float s=scale(); x=Math.max(4,Math.min(x,Render2D.screenWidth()-width()-4));
        y=Math.max(4,Math.min(y,Render2D.screenHeight()-height()-4));
        Render2D.pushTranslate(gg,x,y);
        Render2D.round(gg,0,0,width(),height(),4.5f*s,0xF018181F);
        QuickFont font=QuickFont.of("bold",7*s);
        font.draw(gg,selected.label(),6*s,12*s,Style.accent());
        HudLayout.Entry e=HudLayout.get(selected);
        String[] labels={"Размер","Прозрачность","Свечение"};
        float[] values={(e.scale-0.5f)/1.5f,(e.opacity-0.1f)/0.9f,e.glow/2};
        float[] raw={e.scale,e.opacity,e.glow};
        for(int i=0;i<3;i++) {
            float yy=(24+i*15)*s;
            font.draw(gg,labels[i],6*s,yy+4*s,Style.WHITE);
            float left=64*s, right=114*s, knob=left+values[i]*(right-left);
            Render2D.round(gg,left,yy,50*s,2*s,s,0xFF494952);
            Render2D.round(gg,left,yy,Math.max(s,knob-left),2*s,s,Style.accent());
            Render2D.circle(gg,Math.max(left+2*s,Math.min(right-2*s,knob))-2*s,yy-s,4*s,Style.WHITE);
            QuickFont.of("medium",5*s).drawRight(gg,Math.round(raw[i]*100)+"%",114*s,yy-2*s,0xFFB3B3BA);
        }
        Render2D.round(gg,6*s,68*s,112*s,13*s,3*s,0xFF303039);
        font.drawCentered(gg,"Сбросить",62*s,font.visualBaseline("H",68*s,13*s),Style.WHITE);
        Render2D.popTransform(gg);
    }
}

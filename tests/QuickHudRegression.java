package dev.hatek.client.module.impl.render.interf.quick;
import com.google.gson.JsonParser;
import dev.hatek.client.module.impl.render.interf.HudLayout;
import dev.hatek.quickembedded.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
/** Headless font/layout/packaging/shader-binding regression; no fabricated in-game rendering claims. */
public final class QuickHudRegression {
    private static int assertions;
    private static void check(boolean value,String message) { assertions++;if(!value) throw new AssertionError(message); }
    private static void near(float a,float b,String message) { check(Math.abs(a-b)<0.001f,message+": "+a+" vs "+b); }
    private static void bytes(byte[] data,int size,String hash) throws Exception {
        check(data.length==size,"embedded length");check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)).equals(hash),"embedded SHA-256");
    }
    public static void main(String[] args) throws Exception {
        check(HudLayout.Element.values().length==16,"15 Quick widgets + existing hotbar");
        near(HudLayout.clampFinite(Float.NaN,.5f,2,1),1,"NaN rejected");
        near(HudLayout.clampFinite(Float.POSITIVE_INFINITY,.5f,2,1),1,"infinity rejected");
        near(HudLayout.clampFinite(-10,.5f,2,1),.5f,"min scale");
        near(HudLayout.clampFinite(100,.5f,2,1),2,"max scale");
        check(QuickItemOpacity.premultiplied(0xFFFFFFFF,.5f)==0x80808080,"premultiplied item opacity");
        check(QuickItemOpacity.premultiplied(0xFFFFFFFF,1)==0xFFFFFFFF,"ordinary item unaffected");
        check(QuickItemOpacity.premultiplied(0xFFFFFFFF,0)==0,"hidden item");
        bytes(QuickBoldAtlas.bytes(),QuickBoldAtlas.SIZE,QuickBoldAtlas.SHA256);
        bytes(QuickMediumAtlas.bytes(),QuickMediumAtlas.SIZE,QuickMediumAtlas.SHA256);
        bytes(QuickQuickAtlas.bytes(),QuickQuickAtlas.SIZE,QuickQuickAtlas.SHA256);
        bytes(QuickIconsNurikAtlas.bytes(),QuickIconsNurikAtlas.SIZE,QuickIconsNurikAtlas.SHA256);
        bytes(QuickUtilAtlas.bytes(),QuickUtilAtlas.SIZE,QuickUtilAtlas.SHA256);
        for(String face:List.of("bold","medium","quick","icons_nurik")) {
            var data=JsonParser.parseString(new String(QuickAssets.metrics(face),StandardCharsets.UTF_8)).getAsJsonObject();
            check(data.getAsJsonObject("atlas").get("distanceRange").getAsFloat()==10,"shader distance range");
            QuickFont small=QuickFont.of(face,7),large=QuickFont.of(face,14);
            String text=face.equals("quick")?"NA":face.equals("icons_nurik")?"EVJK":"Quick [RSHIFT] Скорость II »";
            check(small.width(text)>0,"font width "+face);near(large.width(text),small.width(text)*2,"scale width "+face);
            String probe=face.equals("quick")?"N":face.equals("icons_nurik")?"E":"H";
            var glyph=data.getAsJsonArray("glyphs").asList().stream().map(e->e.getAsJsonObject())
                    .filter(g->g.get("unicode").getAsInt()==probe.codePointAt(0)).findFirst().orElseThrow();
            var bounds=glyph.getAsJsonObject("planeBounds");
            float baseline=small.visualBaseline(probe,0,16);
            float visualCenter=baseline-(bounds.get("top").getAsFloat()+bounds.get("bottom").getAsFloat())*7/2;
            near(visualCenter,8,"visual centering "+face);
        }
        QuickFont font=QuickFont.of("bold",7);near(font.width("«»"),QuickFont.of("medium",7).width("«»"),"missing bold glyphs use original medium atlas");check(font.width(font.trim("VeryLongPlayerName",25))<=25.001f,"target trim fits");
        check(QuickShaderSources.MSDF.contains("median") && !QuickShaderSources.MSDF.contains("#moj_import"),"embedded MSDF source expanded");
        check(QuickShaderSources.BLUR.contains("textureSize") && !QuickShaderSources.BLUR.contains("#moj_import"),"embedded blur source expanded");
        check(QuickPipelines.MSDF.getVertexShader().equals(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED.getVertexShader()),"GUI vertex shader compatibility");
        check(QuickPipelines.BLUR.getBindGroupLayouts().equals(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED.getBindGroupLayouts()),"GUI texture/UBO bindings");
        QuickNotifications.clear();for(int i=0;i<10;i++) QuickNotifications.post("Test"+i,true);
        check(QuickNotifications.current().size()==6,"notifications bounded");QuickNotifications.clear();
        System.out.println("Quick HUD regression: "+assertions+" assertions passed");
    }
}

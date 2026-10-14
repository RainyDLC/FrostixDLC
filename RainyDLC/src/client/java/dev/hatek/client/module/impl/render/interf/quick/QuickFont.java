package dev.hatek.client.module.impl.render.interf.quick;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hatek.client.ui.render.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Original MSDF atlases, not substituted TTFs. Baseline and left bearing remain in em units. */
public final class QuickFont {
    private record Glyph(float advance, float left, float bottom, float right, float top,
                         float u, float v, int sw, int sh) {}
    private static final Map<String, Face> FACES = new HashMap<>();
    private record Face(String name, Identifier texture, int w, int h, float ascent, float descent,
                        Map<Integer, Glyph> glyphs) {}
    private final Face face;
    private final float size;
    private QuickFont(Face face, float size) { this.face = face; this.size = size; }
    public static QuickFont of(String face, float size) {
        return new QuickFont(FACES.computeIfAbsent(face, QuickFont::load), Math.max(1, size));
    }
    private static Face load(String name) {
        String path = "/assets/hatek_client/fonts/quick/" + name;
        try (var in = new java.io.ByteArrayInputStream(QuickAssets.metrics(name))) {
            if (in == null) throw new IllegalStateException("Missing Quick font: " + name);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            var atlas = root.getAsJsonObject("atlas"); var metrics = root.getAsJsonObject("metrics");
            int w = atlas.get("width").getAsInt(), h = atlas.get("height").getAsInt();
            boolean bottomOrigin = "bottom".equals(atlas.get("yOrigin").getAsString());
            Map<Integer, Glyph> glyphs = new HashMap<>();
            for (var element : root.getAsJsonArray("glyphs")) {
                var g = element.getAsJsonObject(); float advance = g.get("advance").getAsFloat();
                int code = g.get("unicode").getAsInt();
                if (!g.has("planeBounds")) { glyphs.put(code,new Glyph(advance,0,0,0,0,0,0,0,0)); continue; }
                var p = g.getAsJsonObject("planeBounds"); var a = g.getAsJsonObject("atlasBounds");
                float l = a.get("left").getAsFloat(), b = a.get("bottom").getAsFloat();
                float r = a.get("right").getAsFloat(), t = a.get("top").getAsFloat();
                glyphs.put(code, new Glyph(advance, p.get("left").getAsFloat(), p.get("bottom").getAsFloat(),
                        p.get("right").getAsFloat(), p.get("top").getAsFloat(), l, bottomOrigin ? h-t : b,
                        Math.round(r-l), Math.round(t-b)));
            }
            return new Face(name,Identifier.fromNamespaceAndPath("hatek_client","dyn/quick/"+name),w,h,
                    metrics.get("ascender").getAsFloat(),-metrics.get("descender").getAsFloat(),glyphs);
        } catch (Exception e) { throw new IllegalStateException("Cannot load Quick font "+name,e); }
    }
    private Face glyphFace(int code) {
        if(face.glyphs.containsKey(code) || !"bold".equals(face.name)) return face;
        return FACES.computeIfAbsent("medium",QuickFont::load);
    }
    private Glyph glyph(int code) { Face f=glyphFace(code);return f.glyphs.getOrDefault(code,f.glyphs.get(63)); }
    public float width(String text) {
        if (text == null) return 0; float w = 0;
        for (int code : text.codePoints().toArray()) { Glyph g=glyph(code); if (g!=null) w+=g.advance*size; }
        return w;
    }
    public float ascent() { return face.ascent*size; }
    public float descent() { return face.descent*size; }
    public float lineHeight() { return ascent()+descent(); }
    public float centeredBaseline(float y, float h) { return y+(h+ascent()-descent())/2; }
    /** Center capital letters, separator or icon by its actual plane bounds (Quick alignment fixes). */
    public float visualBaseline(String probe, float y, float h) {
        Glyph g = glyph(probe.codePointAt(0));
        return g == null ? centeredBaseline(y,h) : y+h/2+(g.top+g.bottom)*size/2;
    }
    public float draw(GuiGraphicsExtractor gg,String text,float x,float baseline,int color) {
        if (text == null) return x;
        QuickAssets.texture(face.name);
        color=Render2D.withAlpha(color,Render2D.alpha());
        for (int code : text.codePoints().toArray()) {
            Face glyphFace=glyphFace(code);Glyph g=glyph(code); if(g==null) continue;
            if(g.sw>0 && (color>>>24)>0) {
                // The original MsdfGlyph leftPosition fix is applied here, before rounding.
                int px=Math.round(x+g.left*size), py=Math.round(baseline-g.top*size);
                int dw=Math.max(1,Math.round((g.right-g.left)*size));
                int dh=Math.max(1,Math.round((g.top-g.bottom)*size));
                gg.blit(QuickPipelines.MSDF,QuickAssets.texture(glyphFace.name),px,py,g.u,g.v,dw,dh,g.sw,g.sh,glyphFace.w,glyphFace.h,color);
            }
            x+=g.advance*size;
        }
        return x;
    }
    public float drawRight(GuiGraphicsExtractor gg,String text,float right,float baseline,int color) {
        return draw(gg,text,right-width(text),baseline,color);
    }
    public float drawCentered(GuiGraphicsExtractor gg,String text,float center,float baseline,int color) {
        return draw(gg,text,center-width(text)/2,baseline,color);
    }
    public String trim(String text,float width) {
        if(width(text)<=width) return text;
        String suffix=".."; float available=width-width(suffix); StringBuilder b=new StringBuilder();
        for(int code:text.codePoints().toArray()) { String next=b.toString()+new String(Character.toChars(code));
            if(width(next)>available) break; b.appendCodePoint(code); }
        return b+suffix;
    }
}

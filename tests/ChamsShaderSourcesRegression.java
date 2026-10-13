package dev.hatek.client.render;

import java.nio.file.Files;
import java.nio.file.Path;

/** Validate/dump the actual embedded GLSL without loading Minecraft. */
public final class ChamsShaderSourcesRegression {
    public static void main(String[] args) throws Exception {
        String v = ChamsShaderSources.VERTEX, f = ChamsShaderSources.FRAGMENT;
        require(v.startsWith("#version 330\n") && f.startsWith("#version 330\n"), "GLSL version");
        require(!v.contains("#moj_import") && !f.contains("#moj_import"), "self-contained sources");
        require(block(v).equals(block(f)), "same DynamicTransforms layout in both stages");
        require(v.contains("texCoord0 = UV0;"), "skin coordinates unchanged");
        require(f.contains("if (skin.a < 0.05) discard;"), "transparent skin cutouts");
        for (int i = 0; i < 4; i++) require(f.contains("mode == " + i), "procedural mode " + i);
        require(f.contains("mode == 4"), "classic fallback");
        require(f.contains("primary.a * vertexColor.a * ColorModulator.a"), "opacity preserved");
        require(ChamsShaderSources.SHADER_ID.equals("hatek:core/chams_material"), "scoped shader ID");
        if (args.length == 1) {
            Path dir = Path.of(args[0]);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("chams.vert"), v);
            Files.writeString(dir.resolve("chams.frag"), f);
        }
        System.out.println("Chams shader sources: 12 checks passed");
    }

    private static String block(String s) {
        int begin = s.indexOf("layout(std140) uniform DynamicTransforms {");
        if (begin < 0) throw new AssertionError("missing DynamicTransforms");
        return s.substring(begin, s.indexOf("};", begin) + 2);
    }

    private static void require(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }
}

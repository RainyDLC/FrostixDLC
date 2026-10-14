package dev.hatek.client.module.impl.render.interf.quick;
import com.mojang.blaze3d.platform.NativeImage;
import dev.hatek.quickembedded.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
/** Class bytes survive the LegitBuilder resource bridge, without a late resource-pack reload. */
public final class QuickAssets {
    private static final Map<String,Identifier> TEXTURES=new HashMap<>();
    private QuickAssets() {}
    private static final class LinearTexture extends DynamicTexture {
        LinearTexture(String name,NativeImage image) {
            super(()->"quick/"+name,image);
            this.sampler=com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
                    .getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
        }
    }
    public static byte[] metrics(String face) { return switch(face) {
        case "bold" -> QuickBoldMetrics.bytes(); case "medium" -> QuickMediumMetrics.bytes();
        case "quick" -> QuickQuickMetrics.bytes(); case "icons_nurik" -> QuickIconsNurikMetrics.bytes();
        default -> throw new IllegalArgumentException(face);
    }; }
    public static Identifier texture(String face) {
        return TEXTURES.computeIfAbsent(face,key->{
            byte[] bytes=switch(key) {
                case "bold" -> QuickBoldAtlas.bytes(); case "medium" -> QuickMediumAtlas.bytes();
                case "quick" -> QuickQuickAtlas.bytes(); case "icons_nurik" -> QuickIconsNurikAtlas.bytes();
                case "util" -> QuickUtilAtlas.bytes(); default -> throw new IllegalArgumentException(key);
            };
            try {
                NativeImage image=NativeImage.read(new ByteArrayInputStream(bytes));
                Identifier id=Identifier.fromNamespaceAndPath("hatek_client","dyn/quick/"+key);
                Minecraft.getInstance().getTextureManager().register(id,new LinearTexture(key,image));return id;
            } catch(Exception e) { throw new IllegalStateException("Cannot upload Quick texture "+key,e); }
        });
    }
}

package rainy.fun.mixin;

import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Makes shader and post-effect files embedded in the input JAR visible during late resource reloads. */
@Mixin(ShaderManager.class)
abstract class ShaderManagerAmbienceResourcesMixin {
    @Redirect(method = "prepare", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/packs/resources/ResourceManager;listResources(Ljava/lang/String;Ljava/util/function/Predicate;)Ljava/util/Map;"), require = 0)
    private Map<Identifier, Resource> rainyfun$addShaderSources(ResourceManager manager, String path,
                                                                Predicate<Identifier> filter) {
        Map<Identifier, Resource> found = manager.listResources(path, filter);
        if (!"shaders".equals(path)) return found;
        return addEmbedded(manager, found, "assets/rainyfun/shaders/", "shaders/", filter);
    }

    @Redirect(method = "prepare", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/resources/FileToIdConverter;listMatchingResources(Lnet/minecraft/server/packs/resources/ResourceManager;)Ljava/util/Map;"), require = 0)
    private Map<Identifier, Resource> rainyfun$addPostEffects(FileToIdConverter converter,
                                                               ResourceManager manager) {
        Map<Identifier, Resource> found = converter.listMatchingResources(manager);
        if (!"post_effect".equals(converter.prefix())) return found;
        return addEmbedded(manager, found, "assets/rainyfun/post_effect/", "post_effect/", id -> true);
    }

    private static Map<Identifier, Resource> addEmbedded(ResourceManager manager,
                                                          Map<Identifier, Resource> existing,
                                                          String jarPrefix, String gamePrefix,
                                                          Predicate<Identifier> filter) {
        String[] names = resourceNames();
        if (names.length == 0) return existing;
        PackResources source = manager.listPacks().findFirst().orElse(null);
        if (source == null) return existing;

        Map<Identifier, Resource> merged = new HashMap<>(existing);
        for (String name : names) {
            if (!name.startsWith(jarPrefix)) continue;
            String relative = name.substring(jarPrefix.length());
            Identifier id = Identifier.fromNamespaceAndPath("rainyfun", gamePrefix + relative);
            if (!filter.test(id) || merged.containsKey(id)) continue;
            byte[] bytes = resourceBytes(name);
            if (bytes == null) continue;
            IoSupplier<java.io.InputStream> stream = () -> new ByteArrayInputStream(bytes.clone());
            merged.put(id, new Resource(source, stream));
        }
        return merged;
    }

    private static String[] resourceNames() {
        try {
            Class<?> bridge = Class.forName("mod.runtime.NativeBridge");
            Method list = bridge.getMethod("listMixinResources0");
            return (String[]) list.invoke(null);
        } catch (ReflectiveOperationException | LinkageError failure) {
            System.err.println("[Rainy.fun] Unable to list embedded resources: " + failure);
            return new String[0];
        }
    }

    private static byte[] resourceBytes(String name) {
        try {
            Class<?> bridge = Class.forName("mod.runtime.NativeBridge");
            Method get = bridge.getMethod("getMixinResource0", String.class);
            return (byte[]) get.invoke(null, name);
        } catch (ReflectiveOperationException | LinkageError failure) {
            System.err.println("[Rainy.fun] Unable to read embedded resource " + name + ": " + failure);
            return null;
        }
    }
}

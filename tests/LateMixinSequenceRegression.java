package mod.runtime;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.service.*;
import org.spongepowered.asm.logging.LoggerAdapterDefault;
import org.spongepowered.asm.util.ReEntranceLock;




public final class LateMixinSequenceRegression implements Opcodes {
    static native void resource(String name, byte[] data);
    static Object field(Class<?> type, Object object, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    static void set(Class<?> type, Object object, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    static byte[] mixin() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(V21, ACC_PUBLIC, "fixtures/mixin/SequenceMixin", null, "java/lang/Object", null);
        AnnotationVisitor annotation = writer.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
        AnnotationVisitor targets = annotation.visitArray("value");
        targets.visit(null, org.objectweb.asm.Type.getObjectType("fixtures/TableTarget")); targets.visitEnd(); annotation.visitEnd();
        MethodVisitor method = writer.visitMethod(ACC_PRIVATE | ACC_STATIC, "onValue",
                "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V", null, null);
        AnnotationVisitor inject = method.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", true);
        AnnotationVisitor methods = inject.visitArray("method"); methods.visit(null, "value()I"); methods.visitEnd();
        AnnotationVisitor locations = inject.visitArray("at");
        AnnotationVisitor at = locations.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
        at.visit("value", "HEAD"); at.visitEnd(); locations.visitEnd();
        inject.visit("cancellable", true); inject.visit("remap", false); inject.visitEnd();
        method.visitCode(); method.visitVarInsn(ALOAD, 0); method.visitIntInsn(BIPUSH, 42);
        method.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        method.visitMethodInsn(INVOKEVIRTUAL, "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable",
                "setReturnValue", "(Ljava/lang/Object;)V", false);
        method.visitInsn(RETURN);
        method.visitMaxs(2, 1); method.visitEnd(); writer.visitEnd();
        return writer.toByteArray();
    }
    static void platform(Map<String, byte[]> resources) throws Exception {
        ClassLoader loader = LateMixinSequenceRegression.class.getClassLoader();
        org.spongepowered.asm.service.IClassBytecodeProvider bytes = (org.spongepowered.asm.service.IClassBytecodeProvider) Proxy.newProxyInstance(loader,
                new Class<?>[]{org.spongepowered.asm.service.IClassBytecodeProvider.class}, (p, method, args) -> {
                    String path = ((String) args[0]).replace('.', '/') + ".class";
                    byte[] data = resources.get(path);
                    if (data == null) try (var stream = loader.getResourceAsStream(path)) {
                        if (stream == null) throw new ClassNotFoundException(path);
                        data = stream.readAllBytes();
                    }
                    ClassNode node = new ClassNode(); new ClassReader(data).accept(node, 0); return node;
                });
        IClassProvider classes = (IClassProvider) Proxy.newProxyInstance(loader,
                new Class<?>[]{IClassProvider.class}, (p, method, args) -> {
                    if (method.getName().equals("getClassPath")) return new java.net.URL[0];
                    return Class.forName((String) args[0], args.length > 1 && (boolean) args[1], loader);
                });
        IClassTracker tracker = (IClassTracker) Proxy.newProxyInstance(loader,
                new Class<?>[]{IClassTracker.class}, (p, method, args) -> switch (method.getName()) {
                    case "isClassLoaded" -> args[0].equals("fixtures.TableTarget");
                    case "getClassRestrictions" -> "";
                    default -> null;
                });
        ReEntranceLock lock = new ReEntranceLock(1);
        IMixinService host = (IMixinService) Proxy.newProxyInstance(loader,
                new Class<?>[]{IMixinService.class}, (p, method, args) -> switch (method.getName()) {
                    case "getLogger" -> new LoggerAdapterDefault((String) args[0]);
                    case "getBytecodeProvider" -> bytes;
                    case "getClassProvider" -> classes;
                    case "getClassTracker" -> tracker;
                    case "getName" -> "isolated-sequence-regression";
                    case "isValid" -> true;
                    case "getSideName" -> "CLIENT";
                    case "getInitialPhase" -> MixinEnvironment.Phase.DEFAULT;
                    case "getReEntranceLock" -> lock;
                    case "getPlatformAgents", "getMixinContainers" -> List.of();
                    default -> null;
                });
        Constructor<MixinService> constructor = MixinService.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        MixinService manager = constructor.newInstance();
        set(MixinService.class, manager, "service", new DllMixinServiceWrapper(host));
        set(MixinService.class, null, "instance", manager);
        Map<String, Object> properties = new HashMap<>();
        IGlobalPropertyService globals = (IGlobalPropertyService) Proxy.newProxyInstance(loader,
                new Class<?>[]{IGlobalPropertyService.class}, (p, method, args) -> switch (method.getName()) {
                    case "resolveKey" -> new IPropertyKey() { public String toString() { return (String) args[0]; } };
                    case "getProperty" -> "mixin.initialised".equals(args[0].toString()) ? "0.8.7" : properties.get(args[0].toString());
                    case "getPropertyString" -> args.length > 1 ? args[1] : null;
                    case "getPropertyWithDefault" -> properties.getOrDefault(args[0].toString(), args[1]);
                    case "setProperty" -> { properties.put(args[0].toString(), args[1]); yield null; }
                    default -> null;
                });
        set(MixinService.class, manager, "propertyService", globals);
    }
    public static void main(String[] args) throws Throwable {
        System.load(Path.of(args[0]).toAbsolutePath().toString());
        var target = new JvmtiMethodTableRegression.Loader().define(JvmtiMethodTableRegression.fixture(17, false));
        JvmtiMethodTableRegression.check(JvmtiMethodTableRegression.start() == 0, "Native install failed");
        try {
            JvmtiMethodTableRegression.registerBridge();
            Map<String, byte[]> resources = new HashMap<>();
            resources.put("fixtures/TableTarget.class", JvmtiMethodTableRegression.fixture(17, false));
            resources.put("fixtures/mixin/SequenceMixin.class", mixin());
            resources.put("sequence.mixins.json", """
                {"required":true,"minVersion":"0.8.7","package":"fixtures.mixin",
                 "compatibilityLevel":"JAVA_21","mixins":["SequenceMixin"]}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for (var entry : resources.entrySet()) resource(entry.getKey(), entry.getValue());
            platform(resources);
            MixinEnvironment.init(MixinEnvironment.Phase.DEFAULT);
            Constructor<?> constructor = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer")
                    .getDeclaredConstructor();
            constructor.setAccessible(true);
            Object transformer = constructor.newInstance();
            NativeBridge.prepareLateConfigs(new String[]{"sequence.mixins.json"});
            Object processor = field(transformer.getClass(), transformer, "processor");
            JvmtiMethodTableRegression.check(((List<?>) field(processor.getClass(), processor, "configs")).size() == 1,
                    "Config not active before capture");
            byte[] captured = NativeBridge.getLoadedClassBytes0(target);
            byte[] transformed = NativeBridge.transformOwned(transformer, "fixtures.TableTarget", captured,
                    new String[]{"sequence.mixins.json"});
            byte[] adapted = LoadedClassAdapter.adapt(captured, transformed, target, new String[]{"fixtures.mixin"});
            NativeBridge.redefineClass0(target, adapted);
            JvmtiMethodTableRegression.check(target.getMethod("value").invoke(null).equals(42),
                    "Prepared real Mixin did not change executed target code");
            System.out.println("PASS real Mixin prepare -> primary capture -> transform -> recovered adapter -> primary redefine -> value=42");
        } finally { JvmtiMethodTableRegression.stop(); }
    }
}

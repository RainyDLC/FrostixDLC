package mod.runtime;

import java.io.*;
import java.nio.file.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.zip.ZipFile;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.service.IMixinService;


public final class LateMixinResourceRegression {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static Map<String, Object> values(AnnotationNode annotation) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (annotation.values != null) {
            for (int i = 0; i < annotation.values.size(); i += 2)
                result.put((String) annotation.values.get(i), annotation.values.get(i + 1));
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("mod JAR [generated payload.cpp]");
        int injectors = 0;
        try (ZipFile jar = new ZipFile(args[0])) {
            for (var entries = jar.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                byte[] bytes;
                try (InputStream stream = jar.getInputStream(entry)) {
                    bytes = stream.readAllBytes();
                }
                ClassNode node = new ClassNode();
                new ClassReader(bytes).accept(node, 0);
                boolean mixin = false;
                if (node.visibleAnnotations != null)
                    for (AnnotationNode annotation : node.visibleAnnotations)
                        mixin |= annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;");
                if (node.invisibleAnnotations != null)
                    for (AnnotationNode annotation : node.invisibleAnnotations)
                        mixin |= annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;");
                if (!mixin) continue;
                check(NativeBridge.readMixinTargetNames(bytes).length > 0,
                        "Mixin has no target: " + entry.getName());
                for (MethodNode method : node.methods) {
                    List<AnnotationNode> all = new ArrayList<>();
                    if (method.visibleAnnotations != null) all.addAll(method.visibleAnnotations);
                    if (method.invisibleAnnotations != null) all.addAll(method.invisibleAnnotations);
                    for (AnnotationNode annotation : all) {
                        Map<String, Object> before = values(annotation);
                        int count = DllMixinServiceWrapper.disableInjectorRemapping(List.of(annotation));
                        Map<String, Object> after = values(annotation);
                        if (annotation.desc.endsWith("Inject;")) {
                            check(count == 1 && Boolean.FALSE.equals(after.get("remap")), "remap not disabled");
                            check(before.containsKey("require") == after.containsKey("require")
                                            && Objects.equals(before.get("require"), after.get("require")),
                                    "Injection requirements were altered");
                            before.remove("remap"); after.remove("remap");
                            check(before.equals(after), "Other injection arguments were altered");
                            List<Object> once = new ArrayList<>(annotation.values);
                            check(DllMixinServiceWrapper.disableInjectorRemapping(List.of(annotation)) == 1,
                                    "Repeated call count mismatch");
                            check(once.equals(annotation.values), "Repeated remap produced duplicate keys");
                            injectors++;
                        } else check(count == 0 && before.equals(after), "Unrelated annotation changed");
                    }
                }
            }
        }
        System.out.println("PASS mod JAR: " + injectors + " injectors checked; requirements and other arguments preserved");

        AnnotationNode required = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
        required.values = new ArrayList<>(List.of("require", 7, "remap", true));
        DllMixinServiceWrapper.disableInjectorRemapping(List.of(required));
        check(values(required).get("require").equals(7), "Explicit require weakened");
        AnnotationNode empty = new AnnotationNode(required.desc);
        check(DllMixinServiceWrapper.disableInjectorRemapping(List.of(empty)) == 1, "Null values not handled");
        check(values(empty).equals(Map.of("remap", false)), "Wrong default annotation values");
        check(DllMixinServiceWrapper.disableInjectorRemapping(null) == 0, "Null list not handled");

        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "fixture/Targets", null, "java/lang/Object", null);
        AnnotationVisitor annotation = writer.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
        AnnotationVisitor array = annotation.visitArray("targets");
        array.visit(null, "fixture/One"); array.visit(null, "fixture.One"); array.visitEnd();
        annotation.visitEnd(); writer.visitEnd();
        check(Arrays.equals(NativeBridge.readMixinTargetNames(writer.toByteArray()), new String[]{"fixture.One"}),
                "String targets must be normalized and deduplicated");
        try {
            NativeBridge.readMixinTargetNames(new byte[]{0, 1, 2});
            throw new AssertionError("Malformed bytecode silently accepted");
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException expectedFailure) { }
        ClassWriter plain = new ClassWriter(0);
        plain.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "fixture/NoMixin", null, "java/lang/Object", null);
        plain.visitEnd();
        try {
            NativeBridge.readMixinTargetNames(plain.toByteArray());
            throw new AssertionError("Zero targets silently accepted");
        } catch (IllegalArgumentException expectedFailure) { }
        System.out.println("PASS null annotations, explicit require, string targets, malformed bytes, zero targets");

        ClassLoader loader = LateMixinResourceRegression.class.getClassLoader();
        Class<?> providerType = org.spongepowered.asm.service.IClassBytecodeProvider.class;
        int[] calls = {0};
        ClassNode delegated = new ClassNode();
        Object provider = Proxy.newProxyInstance(loader, new Class<?>[]{providerType}, (p, m, a) -> {
            calls[0]++;
            check(a.length == 3 && Boolean.FALSE.equals(a[1]) && (int) a[2] == ClassReader.SKIP_DEBUG,
                    "Delegate flags were dropped");
            if (a[0].equals("fixture.Failure")) throw new IOException("original provider failure");
            return delegated;
        });
        IMixinService service = (IMixinService) Proxy.newProxyInstance(loader,
                new Class<?>[]{IMixinService.class}, (p, m, a) -> {
                    if (m.getName().equals("getBytecodeProvider")) return provider;
                    throw new AssertionError("Unexpected service call: " + m.getName());
                });
        DllMixinServiceWrapper wrapper = new DllMixinServiceWrapper(service);
        check(wrapper.getClassNode("fixture.Delegate", false, ClassReader.SKIP_DEBUG) == delegated,
                "Wrong delegated node");
        try {
            wrapper.getClassNode("fixture.Failure", false, ClassReader.SKIP_DEBUG);
            throw new AssertionError("Provider failure swallowed");
        } catch (IOException failure) {
            check(failure.getMessage().equals("original provider failure"), "Original failure not preserved");
        }
        check(calls[0] == 2, "Failed provider was retried");
        System.out.println("PASS actual Mixin interface: all 3 arguments delegated; exceptions not retried");

        if (args.length == 2) {
            String generated = Files.readString(Path.of(args[1]));
            int published = 0;
            int expectedClasses = 0;
            try (ZipFile jar = new ZipFile(args[0])) {
                for (var entries = jar.entries(); entries.hasMoreElements();) {
                    var entry = entries.nextElement();
                    if (!entry.getName().endsWith(".class")) continue;
                    expectedClasses++;
                    var reference = java.util.regex.Pattern.compile("g_resources\\[\""
                            + java.util.regex.Pattern.quote(entry.getName())
                            + "\"\\] = std::vector<uint8_t>\\(\\(const uint8_t\\*\\)(\\w+),")
                            .matcher(generated);
                    check(reference.find(), "Embedded class not published: " + entry.getName());
                    int publication = reference.start();
                    check(publication < generated.indexOf("    LoadRuntimeClasses();"),
                            "Resources published after class loading");
                    String variable = reference.group(1);
                    var declaration = java.util.regex.Pattern.compile("static jbyte "
                            + variable + "\\[\\] = \\{([^}]+)\\};", java.util.regex.Pattern.DOTALL)
                            .matcher(generated);
                    check(declaration.find(), "Embedded byte array missing: " + variable);
                    String[] numbers = declaration.group(1).trim().split("\\s*,\\s*");
                    byte[] actual = new byte[numbers.length];
                    for (int i = 0; i < actual.length; i++) actual[i] = Byte.parseByte(numbers[i]);
                    try (InputStream stream = jar.getInputStream(entry)) {
                        check(Arrays.equals(actual, stream.readAllBytes()), "Resource bytes differ: " + entry.getName());
                    }
                    published++;
                }
            }
            check(published == expectedClasses, "Not all mod classes were embedded");
            System.out.println("PASS generated payload: " + published + " class resources byte-identical to the input JAR");
        }
    }
}

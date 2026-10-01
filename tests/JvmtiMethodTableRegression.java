package mod.runtime;

import java.nio.file.Path;
import org.objectweb.asm.*;


public final class JvmtiMethodTableRegression implements Opcodes {
    static native int start();
    static native int stop();
    static native byte[] capture(Class<?> target);
    static native int redefine(Class<?> target, byte[] bytes);
    static native int retransform(Class<?> target);
    static native int protect(Class<?> target);
    static native int visible(Class<?> target);
    static native int methodCount(Class<?> target, boolean savedOriginal);
    static native int originalTableError();
    static native int instances(Class<?> target);
    static native void changeTable();
    static native boolean tableRestored();
    static native int plainEnvironment();
    static native void registerBridge();
    static native int hookCount();
    static native void lookupWithGc();
    private static Class<?> lookupFixture;
    private static java.lang.ref.WeakReference<ClassLoader> lookupLoader;
    static void createLookupFixture() {
        Loader loader = new Loader();
        lookupFixture = loader.define(fixture(7, false));
        lookupLoader = new java.lang.ref.WeakReference<>(loader);
    }
    static void releaseLookupFixture() { lookupFixture = null; }
    static boolean lookupFixtureReleased() { return lookupLoader.get() == null; }

    static final class Loader extends ClassLoader {
        Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
    }
    static byte[] fixture(int value, boolean extraField) {
        ClassWriter w = new ClassWriter(0);
        w.visit(V21, ACC_PUBLIC, "fixtures/TableTarget", null, "java/lang/Object", new String[]{"java/io/Serializable"});
        if (extraField) w.visitField(ACC_PUBLIC, "additional", "I", null, null).visitEnd();
        MethodVisitor m = w.visitMethod(ACC_PUBLIC | ACC_STATIC, "value", "()I", null, null);
        m.visitCode(); m.visitIntInsn(BIPUSH, value); m.visitInsn(IRETURN);
        m.visitMaxs(1, 0); m.visitEnd(); w.visitEnd();
        return w.toByteArray();
    }
    static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
    public static void main(String[] args) throws Exception {
        System.load(Path.of(args[0]).toAbsolutePath().toString());
        Class<?> target = new Loader().define(fixture(17, false));
        check(plainEnvironment() == 0, "Unmodified JVMTI environment failed");
        check(start() == 0, "Table install failed");
        if (args.length > 1 && args[1].equals("lookup-gc")) {
            try { lookupWithGc(); }
            finally { check(stop() == 0, "Table cleanup failed"); }
            System.out.println("PASS repeated loaded-class lookup with GC");
            return;
        }
        if (args.length > 1 && args[1].equals("vmdeath")) {
            check(capture(target).length > 0, "Capture before VMDeath failed");
            System.out.println("PASS VMDeath test body; watcher cleanup must allow normal JVM exit");
            return;
        }
        try {
            check(originalTableError() == 112, "Original table was not blocked");
            check(target.getMethod("value").invoke(null).equals(17), "Fixture preparation failed");
            int hiddenMethodCount = methodCount(target, false);
            int originalMethodCount = methodCount(target, true);
            check(hiddenMethodCount == 0, "Owned-package GetClassMethods was not hidden: " + hiddenMethodCount);
            check(originalMethodCount > 0, "Owned-package original methods were altered: " + originalMethodCount);
            check(methodCount(String.class, false) > 0, "Unrelated GetClassMethods was not forwarded");
            check(target.getMethod("value").invoke(null).equals(17), "Method hiding changed Java reflection or execution");
            check(visible(target) == 1, "Unprotected target missing from enumeration");
            byte[] captured = capture(target);
            check(new ClassReader(captured).getClassName().equals("fixtures/TableTarget"), "Wrong captured class");
            int hooks = hookCount();
            check(retransform(target) == 0 && hookCount() == hooks + 1,
                    "Primary callback/event was not restored after capture");
            check(redefine(target, fixture(29, false)) == 0, "Ordinary redefine failed");
            check(target.getMethod("value").invoke(null).equals(29), "Redefined code not executed");
            for (int i = 0; i < 12; ++i) check(capture(target).length > 0, "Repeated capture failed at " + i);
            check(redefine(target, fixture(29, true)) == 64, "Schema-change error not preserved");
            byte[] adapted = LoadedClassAdapter.adapt(capture(target), fixture(29, true), target, new String[]{"fixtures.mixin"});
            org.objectweb.asm.tree.ClassNode adaptedClass = LoadedClassAdapter.read(adapted);
            check(adaptedClass.fields.stream().anyMatch(field -> field.name.equals("additional") && field.desc.equals("I")),
                    "Recovered adapter did not retain field addition");
            check(redefine(target, adapted) == 64, "Recovered adapter unexpectedly bypassed schema restriction");
            try {
                target.getDeclaredField("additional");
                throw new AssertionError("Rejected field appeared in loaded class");
            } catch (NoSuchFieldException expected) { }
            check(instances(target) == 104, "Instance-enumeration return code differs");
            changeTable();
            long until = System.nanoTime() + 3_000_000_000L;
            while (!tableRestored() && System.nanoTime() < until) Thread.sleep(10);
            check(tableRestored(), "100ms watcher did not restore table");
            check(protect(target) == 0, "Tagging failed");
            check(visible(target) == 0, "Protected target remained visible");
            check(redefine(target, fixture(17, false)) == 111, "Protected redefine must fail with ACCESS_DENIED");
            check(retransform(target) == 111, "Protected retransform must fail with ACCESS_DENIED");
            registerBridge();
            try {
                NativeBridge.redefineClass0(target, fixture(17, false));
                throw new AssertionError("NativeBridge swallowed denied redefine");
            } catch (IllegalStateException expected) {
                check(expected.getMessage().contains("111"), "Wrong bridge redefine failure: " + expected);
            }
            try {
                NativeBridge.retransformClasses0(new Class<?>[]{target});
                throw new AssertionError("NativeBridge swallowed denied retransform");
            } catch (IllegalStateException expected) {
                check(expected.getMessage().contains("111"), "Wrong bridge retransform failure: " + expected);
            }
            check(target.getMethod("value").invoke(null).equals(29), "Denied redefine changed code");
            Class<?> other = new Loader().define(fixture(17, false));
            check(other.getMethod("value").invoke(null).equals(17), "Second fixture preparation failed");
            check(methodCount(other, false) == 0 && methodCount(other, true) > 0,
                    "Method filter used a class tag instead of the package signature");
            check(visible(other) == 1, "Tag leaked across classloaders");
            check(capture(other).length > 0, "Unprotected class stopped working after denial");
            try {
                capture(target);
                throw new AssertionError("Protected capture unexpectedly succeeded");
            } catch (IllegalStateException expected) { }
            hooks = hookCount();
            check(retransform(other) == 0 && hookCount() == hooks + 1,
                    "Primary callback/event was not restored after failed capture");
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(3);
            try {
                java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
                for (int thread = 0; thread < 3; ++thread)
                    futures.add(pool.submit(() -> {
                        for (int n = 0; n < 8; ++n)
                            check(new ClassReader(capture(other)).getClassName().equals("fixtures/TableTarget"),
                                    "Concurrent primary capture failed");
                    }));
                for (var future : futures) future.get();
            } finally { pool.shutdownNow(); }
        } finally {
            check(stop() == 0, "Table restoration/disposal failed");
        }
        Path log = Path.of(args[0]).toAbsolutePath().getParent().resolve("mod_payload_log.txt");
        String logged = java.nio.file.Files.readString(log);
        check(logged.contains("RedefineClasses denied:") && logged.contains("RetransformClasses denied:"), "Denials missing from log");
        check(logged.contains("err=64"), "Schema failure missing from log");
        java.nio.file.Files.copy(log, log.resolveSibling("first-pass.log"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        check(plainEnvironment() == 0, "Original table unusable after restoration");
        check(start() == 0, "Second installation failed");
        check(visible(target) == 1, "Disposed environment retained protection tag");
        check(stop() == 0, "Second restoration failed");
        System.out.println("PASS: redefine executes; capture repeats; errors 64/104/111/112; bridge propagates denial; tags isolated; watchdog; restore; reinstall");
    }
}

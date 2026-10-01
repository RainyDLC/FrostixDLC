package mod.runtime;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;


public final class LoadedCaptureRegression implements Opcodes {
    static native byte[] capture(Class<?> target);
    static native int redefine(Class<?> target, byte[] bytes);
    static class Loader extends ClassLoader {
        Class<?> define(byte[] b) { return defineClass(null, b, 0, b.length); }
    }
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        System.load(new java.io.File(args[0]).getAbsolutePath());
        ClassNode n = new ClassNode();
        n.version = V21; n.access = ACC_PUBLIC; n.name = "fixtures/AlreadyTransformed";
        n.superName = "java/lang/Object";
        n.interfaces.add("java/io/Serializable");
        MethodNode m = new MethodNode(ACC_PUBLIC | ACC_STATIC, "addedByEarlierMixin", "()I", null, null);
        m.instructions.add(new IntInsnNode(BIPUSH, 17)); m.instructions.add(new InsnNode(IRETURN));
        n.methods.add(m);
        ClassWriter w = new ClassWriter(3); n.accept(w);
        Class<?> target = new Loader().define(w.toByteArray());
        byte[] baseline = capture(target);
        ClassNode actual = LoadedClassAdapter.read(baseline);
        check(actual.interfaces.contains("java/io/Serializable"), "Capture lost an existing interface");
        MethodNode found = LoadedClassAdapter.find(actual.methods, m.name, m.desc);
        check(found != null, "Capture lost an existing method");
        found.instructions.clear();
        found.instructions.add(new IntInsnNode(BIPUSH, 29)); found.instructions.add(new InsnNode(IRETURN));
        ClassWriter changed = new ClassWriter(3); actual.accept(changed);
        byte[] adapted = LoadedClassAdapter.adapt(baseline, changed.toByteArray(), target, new String[]{"ours.mixin"});
        int err = redefine(target, adapted);
        check(err == 0, "Redefine failed: " + err);
        check(target.getMethod(m.name).invoke(null).equals(29), "Redefined code did not execute");
        check(capture(target).length > 0, "Second capture failed");
        System.out.println("PASS native capture retains loaded schema; adapted redefine executes; repeat capture succeeds");
    }
}

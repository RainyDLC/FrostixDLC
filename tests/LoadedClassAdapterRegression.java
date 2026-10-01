package mod.runtime;

import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;


public final class LoadedClassAdapterRegression implements Opcodes {
    static class Loader extends ClassLoader {
        Loader(ClassLoader parent) { super(parent); }
        Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static MethodNode constant(String name, int value) {
        MethodNode method = new MethodNode(ACC_PUBLIC | ACC_STATIC, name, "()I", null, null);
        method.instructions.add(new LdcInsnNode(value)); method.instructions.add(new InsnNode(IRETURN)); method.maxStack = 1;
        return method;
    }
    static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(3); node.accept(writer); return writer.toByteArray(); }
    static ClassNode fixture() {
        ClassNode node = new ClassNode(); node.version = V21; node.access = ACC_PUBLIC; node.name = "fixtures/AdapterTarget"; node.superName = "java/lang/Object";
        node.fields.add(new FieldNode(ACC_PUBLIC, "counter", "I", null, null));
        MethodNode init = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        init.instructions.add(new VarInsnNode(ALOAD, 0)); init.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)); init.instructions.add(new InsnNode(RETURN)); init.maxStack=1; init.maxLocals=1;
        node.methods.add(init);
        node.methods.add(constant("value", 1)); return node;
    }
    public static void main(String[] args) throws Exception {
        Loader loader = new Loader(LoadedClassAdapterRegression.class.getClassLoader());
        ClassNode original = fixture(), transformed = fixture(); transformed.methods.set(0, constant("value", 27));
        Class<?> target = loader.define(bytes(original));
        byte[] result = LoadedClassAdapter.adapt(bytes(original), bytes(transformed), target, new String[]{"ours.mixin"});
        Class<?> adapted = new Loader(loader.getParent()).define(result);
        check((Integer) adapted.getMethod("value").invoke(null) == 27, "Actual transformed method did not execute");
        check((Integer) target.getMethod("value").invoke(null) == 1, "Original class changed during adaptation");
        System.out.println("PASS adapted method executes on JVM; original class unchanged");


        ClassNode foreign = fixture();
        MethodNode readForeign = new MethodNode(ACC_PUBLIC | ACC_STATIC, "foreign", "()Ljava/io/PrintStream;", null, null);
        readForeign.instructions.add(new FieldInsnNode(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
        readForeign.instructions.add(new InsnNode(ARETURN));
        foreign.methods.add(readForeign);
        byte[] foreignBytes = bytes(foreign);
        Class<?> foreignTarget = new Loader(loader.getParent()).define(foreignBytes);
        byte[] foreignAdapted = LoadedClassAdapter.adapt(foreignBytes, foreignBytes, foreignTarget, new String[]{"ours.mixin"});
        Class<?> foreignResult = new Loader(loader.getParent()).define(foreignAdapted);
        check(foreignResult.getMethod("foreign").invoke(null) == System.out, "Foreign field access must not reference an undefined companion");
        System.out.println("PASS no-moved-method adaptation executes foreign GETSTATIC without a companion");

        MethodNode moved = constant("handler", 42);
        byte[] helper = LoadedClassAdapter.companion("fixtures/Companion", transformed, new ArrayList<>(List.of(moved)), loader);
        Class<?> companion = loader.define(helper);
        check((Integer) companion.getMethod("handler").invoke(null) == 42, "Companion method execution failed");
        ClassNode helperNode = LoadedClassAdapter.read(helper);
        check(helperNode.version == 52 && helperNode.methods.size() == 4, "Companion schema differs");
        check(LoadedClassAdapter.find(helperNode.methods, "stripFinal", "(Ljava/lang/reflect/Field;)V").tryCatchBlocks.size() == 1, "Missing original exception handler");
        System.out.println("PASS companion loads and executes with reflection helper methods");

        MethodNode left = constant("old", 1), right = constant("new", 99);
        check(LoadedClassAdapter.sameBody(left, right), "Original opcode-based comparison must ignore LDC operands");
        check("handler".equals(LoadedClassAdapter.prefix("handler$+12")), "parseInt suffix semantics changed");
        check(LoadedClassAdapter.prefix("handler$2147483648") == null, "Suffix overflow must not match");
        check(LoadedClassAdapter.prefix("handler$") == null, "Empty suffix must not match");
        check(LoadedClassAdapter.prefix("handler") == null, "Missing suffix must not match");

        MethodNode calls = new MethodNode(ACC_STATIC, "calls", "()V", null, null);
        MethodNode instance = new MethodNode(ACC_PUBLIC, "instance", "()I", null, null);
        MethodInsnNode call = new MethodInsnNode(INVOKEVIRTUAL, original.name, "instance", "()I", false);
        calls.instructions.add(call);
        InvokeDynamicInsnNode dynamic = new InvokeDynamicInsnNode("lambda", "()Ljava/lang/Runnable;",
                new Handle(H_INVOKESTATIC, "fixtures/Factory", "bootstrap", "()V", false),
                new Handle(H_INVOKEVIRTUAL, original.name, "instance", "()I", true));
        calls.instructions.add(dynamic);
        LoadedClassAdapter.rewriteCalls(calls, original.name, "fixtures/Companion", List.of(instance), Map.of());
        check(call.getOpcode() == INVOKESTATIC && call.desc.equals("(Lfixtures/AdapterTarget;)I") && !call.itf, "Receiver call rewrite");
        Handle handle = (Handle) dynamic.bsmArgs[0];
        check(handle.getTag() == H_INVOKESTATIC && handle.isInterface() && handle.getDesc().equals(call.desc), "Remaining-method Handle semantics");
        System.out.println("PASS suffix edge cases, original comparison rules, receiver and lambda rewrites");

        MethodNode write = new MethodNode(ACC_PUBLIC, "write", "()V", null, null);
        write.instructions.add(new VarInsnNode(ALOAD, 0)); write.instructions.add(new InsnNode(ICONST_1));
        write.instructions.add(new FieldInsnNode(PUTFIELD, original.name, "counter", "I")); write.instructions.add(new InsnNode(RETURN));
        write.maxStack = 2; write.maxLocals = 1;
        byte[] fieldHelper = LoadedClassAdapter.companion("fixtures/FieldWriteCompanion", original, new ArrayList<>(List.of(write)), loader);
        Class<?> fieldCompanion = loader.define(fieldHelper);
        Object targetObject = target.getConstructor().newInstance();
        fieldCompanion.getMethod("write", target).invoke(null, targetObject);
        check(target.getField("counter").getInt(targetObject) == 1, "Field write helper did not execute");
        System.out.println("PASS corrected field-write operand order and reflection update");
    }
}

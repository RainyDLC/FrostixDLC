package mod.runtime;

import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;


public final class LoadedClassAdapter implements Opcodes {
    private static int counter;
    private static final Set<String> usedNames = new HashSet<>();
    private static final String GET = "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;";
    private static final String SET = "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V";
    private LoadedClassAdapter() {}

    public static byte[] adapt(byte[] original, byte[] transformed, Class<?> target, String[] packages) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(packages, "packages");
        if (packages.length == 0) throw new IllegalArgumentException("Missing owned Mixin packages");
        for (String marker : packages)
            if (marker == null || marker.isEmpty()) throw new IllegalArgumentException("Empty owned Mixin package");
        usedNames.clear();
        ClassNode before = read(original), after = read(transformed);
        String helper = "com/minecraft/class__" + counter++ + "_" + System.currentTimeMillis();
        List<MethodNode> moved = new ArrayList<>(), normalized = new ArrayList<>();
        Map<Integer, MethodNode> candidates = new HashMap<>();
        for (MethodNode method : before.methods) normalized.add(normalizedCopy(method));
        int index = 0;
        for (Iterator<MethodNode> iterator = after.methods.iterator(); iterator.hasNext(); index++) {
            MethodNode method = iterator.next();
            if (method.invisibleAnnotations == null && method.visibleAnnotations == null && specialName(method.name)) {
                MethodNode copy = normalizedCopy(method);
                boolean matched = false;
                for (MethodNode old : normalized) if (sameBody(copy, old)) {
                    candidates.put(index, method); matched = true; break;
                }
                if (!matched) {
                    boolean specialCall = false;
                    for (AbstractInsnNode insn : method.instructions.toArray())
                        if (insn instanceof MethodInsnNode call && call.getOpcode() == INVOKESPECIAL
                                && call.owner.equals(after.name) && !call.name.equals("<init>")) { specialCall = true; break; }
                    if (!specialCall) { moved.add(method); iterator.remove(); }
                }
                continue;
            }
            boolean removed = false;
            if (method.visibleAnnotations != null) for (AnnotationNode annotation : method.visibleAnnotations) {
                if (annotation.desc.contains("MixinMerged") && annotation.values != null && annotation.values.size() >= 2) {
                    String origin = (String) annotation.values.get(1);
                    boolean ours = false;
                    for (String marker : packages) if (origin.contains(marker)) { ours = true; break; }
                    if (ours) { moved.add(copy(method)); iterator.remove(); removed = true; break; }
                }
            }
            if (!removed && method.visibleAnnotations != null) for (AnnotationNode annotation : method.visibleAnnotations)
                if (annotation.desc.contains("MixinMerged")) { candidates.put(index, method); break; }
        }
        Map<String, String> names = new HashMap<>();
        for (Map.Entry<Integer, MethodNode> entry : candidates.entrySet()) {
            MethodNode method = entry.getValue(), comparison = normalizedCopy(method);
            for (int n = 0; n < before.methods.size(); n++) {
                MethodNode old = before.methods.get(n);
                if (!usedNames.contains(old.name) && sameBody(comparison, normalized.get(n))) {
                    if (!method.name.equals(old.name)) { names.put(method.name, old.name); method.name = old.name; }
                    usedNames.add(old.name); break;
                }
            }
        }
        if (!moved.isEmpty()) {
            byte[] bytes = companion(helper, after, moved, target.getClassLoader());
            if (NativeBridge.defineClassNative(helper.replace('/', '.'), bytes, target.getClassLoader()) == null)
                throw new IllegalStateException("Companion definition returned null: " + helper);
        }
        for (MethodNode method : after.methods) rewriteCalls(method, after.name, helper, moved, names);


        for (FieldNode field : after.fields) {
            boolean present = false;
            for (FieldNode old : before.fields)
                if (old.name.equals(field.name) && old.desc.equals(field.desc)) { present = true; break; }
            if (!present) before.fields.add(field);
        }
        for (MethodNode old : before.methods) {
            MethodNode method = find(after.methods, old.name, old.desc);
            if (method != null) body(old, method);
        }
        Map<String, String> suffixNames = new HashMap<>();
        for (MethodNode method : after.methods) {
            if (find(before.methods, method.name, method.desc) != null) continue;
            String prefix = prefix(method.name);
            if (prefix == null) continue;
            for (MethodNode old : before.methods) {
                if (!old.name.equals(method.name) && old.desc.equals(method.desc) && prefix.equals(prefix(old.name))) {
                    suffixNames.put(method.name, old.name); body(old, method); break;
                }
            }
        }
        if (!suffixNames.isEmpty()) for (MethodNode method : before.methods)
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call) {
                    String name = suffixNames.get(call.name); if (name != null) call.name = name;
                } else if (insn instanceof InvokeDynamicInsnNode dynamic) {
                    for (int n = 0; n < dynamic.bsmArgs.length; n++) if (dynamic.bsmArgs[n] instanceof Handle handle) {
                        String name = suffixNames.get(handle.getName());
                        if (name != null) dynamic.bsmArgs[n] = new Handle(handle.getTag(), handle.getOwner(), name, handle.getDesc(), handle.isInterface());
                    }
                }
            }
        for (MethodNode method : before.methods) {
            Iterator<AbstractInsnNode> iterator = method.instructions.iterator();
            while (iterator.hasNext()) if (iterator.next() instanceof FrameNode) iterator.remove();
        }
        ClassWriter writer = new LoaderWriter(3, target.getClassLoader());
        before.accept(writer);
        return writer.toByteArray();
    }

    static ClassNode read(byte[] bytes) { ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 8); return node; }
    static MethodNode copy(MethodNode method) {
        MethodNode result = new MethodNode(method.access, method.name, method.desc, method.signature,
                method.exceptions == null ? null : method.exceptions.toArray(new String[0]));
        method.accept(result); return result;
    }
    static MethodNode normalizedCopy(MethodNode method) {
        MethodNode result = copy(method);
        Iterator<AbstractInsnNode> iterator = result.instructions.iterator();
        while (iterator.hasNext()) { AbstractInsnNode insn = iterator.next(); if (insn instanceof LineNumberNode || insn instanceof FrameNode) iterator.remove(); }
        result.localVariables = null; result.tryCatchBlocks = null; return result;
    }
    static boolean specialName(String name) {
        return name.startsWith("mixinextras") || name.startsWith("wrapOperation") || name.contains("$bridge$")
                || name.contains("$mixinextras$") || name.contains("$wrapped$");
    }
    static boolean comparableName(String name) { return name.contains("$mixinextras$") || name.contains("$wrapped$") || name.startsWith("mixinextras"); }
    static boolean sameBody(MethodNode left, MethodNode right) {
        if (left.instructions.size() != right.instructions.size() || !left.desc.equals(right.desc)) return false;
        for (int n = 0; n < left.instructions.size(); n++) {
            AbstractInsnNode a = left.instructions.get(n), b = right.instructions.get(n);
            if (a.getOpcode() != b.getOpcode()) return false;
            if (a instanceof FieldInsnNode field) {
                FieldInsnNode other = (FieldInsnNode) b;
                if (!field.owner.equals(other.owner) || !field.name.equals(other.name) || !field.desc.equals(other.desc)) return false;
            }
            if (a instanceof MethodInsnNode call) {
                MethodInsnNode other = (MethodInsnNode) b;
                if ((comparableName(call.name) || comparableName(other.name)) && call.owner.equals(other.owner) && call.desc.equals(other.desc)) continue;
                if (!call.owner.equals(other.owner) || !call.name.equals(other.name) || !call.desc.equals(other.desc)) return false;
            }
        }
        return true;
    }
    static MethodNode find(List<MethodNode> methods, String name, String desc) {
        for (MethodNode method : methods) if (method.name.equals(name) && method.desc.equals(desc)) return method;
        return null;
    }
    static String prefix(String name) {
        int split = name.lastIndexOf('$'); if (split < 0) return null;
        try { Integer.parseInt(name.substring(split + 1)); return name.substring(0, split); }
        catch (NumberFormatException failure) { return null; }
    }
    static void body(MethodNode destination, MethodNode source) {
        destination.instructions = source.instructions; destination.localVariables = source.localVariables;
        destination.tryCatchBlocks = source.tryCatchBlocks; destination.maxStack = source.maxStack; destination.maxLocals = source.maxLocals;
    }
    static String receiver(String owner, String desc) { return "(L" + owner + ";" + desc.substring(1); }
    static void rewriteCalls(MethodNode method, String owner, String helper, List<MethodNode> moved, Map<String, String> names) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof MethodInsnNode call) {
                if (call.owner.equals(owner) && find(moved, call.name, call.desc) != null) {
                    boolean wasStatic = call.getOpcode() == INVOKESTATIC;
                    call.setOpcode(INVOKESTATIC); call.owner = helper; call.itf = false;
                    if (!wasStatic) call.desc = receiver(owner, call.desc);
                }
                if (names.containsKey(call.name)) call.name = names.get(call.name);
            } else if (insn instanceof InvokeDynamicInsnNode dynamic) {
                for (int n = 0; n < dynamic.bsmArgs.length; n++) if (dynamic.bsmArgs[n] instanceof Handle handle) {
                    String name = names.get(handle.getName()), newOwner = handle.getOwner(), desc = handle.getDesc();
                    int tag = handle.getTag(); boolean changed = name != null;
                    if (handle.getOwner().equals(owner) && find(moved, handle.getName(), handle.getDesc()) != null) {
                        newOwner = helper; tag = H_INVOKESTATIC;
                        if (handle.getTag() != H_INVOKESTATIC) desc = receiver(owner, desc);
                        changed = true;
                    }
                    if (changed) dynamic.bsmArgs[n] = new Handle(tag, newOwner, name != null ? name : handle.getName(), desc, handle.isInterface());
                }
            }
        }
    }

    static void movedHandles(InvokeDynamicInsnNode dynamic, String owner, String helper, List<MethodNode> moved) {
        for (int n = 0; n < dynamic.bsmArgs.length; n++) if (dynamic.bsmArgs[n] instanceof Handle handle
                && handle.getOwner().equals(owner) && find(moved, handle.getName(), handle.getDesc()) != null) {
            dynamic.bsmArgs[n] = new Handle(H_INVOKESTATIC, helper, handle.getName(),
                    handle.getTag() == H_INVOKESTATIC ? handle.getDesc() : receiver(owner, handle.getDesc()), false);
        }
    }
    static byte[] companion(String name, ClassNode target, List<MethodNode> moved, ClassLoader loader) {
        ClassWriter writer = new LoaderWriter(2, loader);
        writer.visit(52, ACC_PUBLIC, name, null, "java/lang/Object", null);
        for (MethodNode method : moved) {
            boolean isStatic = (method.access & ACC_STATIC) != 0;
            MethodVisitor visitor = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, method.name,
                    isStatic ? method.desc : receiver(target.name, method.desc), null, null);
            if (isStatic) {
                for (AbstractInsnNode insn : method.instructions.toArray())
                    if (insn instanceof InvokeDynamicInsnNode dynamic) movedHandles(dynamic, target.name, name, moved);
            } else {
                ListIterator<AbstractInsnNode> iterator = method.instructions.iterator();
                while (iterator.hasNext()) {
                    AbstractInsnNode insn = iterator.next();
                    if (insn instanceof FieldInsnNode field) {
                        if (field.owner.equals(target.name)) {
                            boolean get = field.getOpcode() == GETFIELD;
                            InsnList replacement = new InsnList();
                            if (!get) {
                                box(replacement, Type.getType(field.desc));




                                int valueSlot = method.maxLocals++;
                                replacement.add(new VarInsnNode(ASTORE, valueSlot));
                                replacement.add(new VarInsnNode(ALOAD, 0));
                            }
                            replacement.add(new LdcInsnNode(field.name)); replacement.add(new LdcInsnNode(field.desc));
                            if (!get) replacement.add(new VarInsnNode(ALOAD, method.maxLocals - 1));
                            replacement.add(new MethodInsnNode(INVOKESTATIC, name, get ? "getField" : "setField", get ? GET : SET, false));
                            if (get) unbox(replacement, Type.getType(field.desc));
                            method.instructions.insertBefore(insn, replacement); iterator.remove();
                        }
                    } else if (insn instanceof MethodInsnNode call) {
                        if (call.getOpcode() == INVOKESPECIAL && call.owner.equals(target.name) && !call.name.equals("<init>")) call.setOpcode(INVOKEVIRTUAL);
                    } else if (insn instanceof InvokeDynamicInsnNode dynamic) movedHandles(dynamic, target.name, name, moved);
                }
            }
            method.accept(visitor);
        }
        fieldHelpers(name, writer); writer.visitEnd(); return writer.toByteArray();
    }
    static String wrapper(Type type) {
        return switch (type.getSort()) {
            case Type.BOOLEAN -> "java/lang/Boolean"; case Type.CHAR -> "java/lang/Character";
            case Type.BYTE -> "java/lang/Byte"; case Type.SHORT -> "java/lang/Short";
            case Type.INT -> "java/lang/Integer"; case Type.FLOAT -> "java/lang/Float";
            case Type.LONG -> "java/lang/Long"; case Type.DOUBLE -> "java/lang/Double";
            default -> "java/lang/Object";
        };
    }
    static void box(InsnList instructions, Type type) {
        if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) return;
        String wrapper = wrapper(type);
        instructions.add(new MethodInsnNode(INVOKESTATIC, wrapper, "valueOf", "(" + type.getDescriptor() + ")L" + wrapper + ";", false));
    }
    static void unbox(InsnList instructions, Type type) {
        if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) { instructions.add(new TypeInsnNode(CHECKCAST, type.getInternalName())); return; }
        String wrapper = wrapper(type); instructions.add(new TypeInsnNode(CHECKCAST, wrapper));
        instructions.add(new MethodInsnNode(INVOKEVIRTUAL, wrapper, type.getClassName() + "Value", "()" + type.getDescriptor(), false));
    }
    static void fieldHelpers(String owner, ClassWriter writer) {
        for (boolean get : new boolean[]{true, false}) {
            MethodVisitor visitor = writer.visitMethod(ACC_PRIVATE | ACC_STATIC, get ? "getField" : "setField", get ? GET : SET, null, null);
            visitor.visitCode(); visitor.visitVarInsn(ALOAD, 0);
            visitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
            visitor.visitVarInsn(ALOAD, 1);
            visitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
            visitor.visitInsn(DUP); visitor.visitInsn(ICONST_1);
            visitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "setAccessible", "(Z)V", false);
            if (!get) { visitor.visitInsn(DUP); visitor.visitMethodInsn(INVOKESTATIC, owner, "stripFinal", "(Ljava/lang/reflect/Field;)V", false); }
            visitor.visitVarInsn(ALOAD, 0);
            if (!get) visitor.visitVarInsn(ALOAD, 3);
            visitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", get ? "get" : "set",
                    get ? "(Ljava/lang/Object;)Ljava/lang/Object;" : "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
            visitor.visitInsn(get ? ARETURN : RETURN); visitor.visitMaxs(get ? 3 : 4, get ? 3 : 4); visitor.visitEnd();
        }
        MethodVisitor visitor = writer.visitMethod(ACC_PRIVATE | ACC_STATIC, "stripFinal", "(Ljava/lang/reflect/Field;)V", null, null);
        visitor.visitCode(); Label start = new Label(), end = new Label(), handler = new Label();
        visitor.visitTryCatchBlock(start, end, handler, "java/lang/Exception"); visitor.visitLabel(start);
        visitor.visitLdcInsn(Type.getType(java.lang.reflect.Field.class)); visitor.visitLdcInsn("modifiers");
        visitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
        visitor.visitInsn(DUP); visitor.visitInsn(ICONST_1);
        visitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "setAccessible", "(Z)V", false);
        visitor.visitVarInsn(ALOAD, 0); visitor.visitVarInsn(ALOAD, 0);
        visitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "getModifiers", "()I", false);
        visitor.visitLdcInsn(-17); visitor.visitInsn(IAND);
        visitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "setInt", "(Ljava/lang/Object;I)V", false);
        visitor.visitLabel(end); visitor.visitInsn(RETURN); visitor.visitLabel(handler); visitor.visitInsn(POP); visitor.visitInsn(RETURN);
        visitor.visitMaxs(3, 1); visitor.visitEnd();
    }
    static final class LoaderWriter extends ClassWriter {
        private final ClassLoader loader;
        LoaderWriter(int flags, ClassLoader loader) { super(flags); this.loader = loader; }
        @Override protected String getCommonSuperClass(String left, String right) {
            try {
                Class<?> a = Class.forName(left.replace('/', '.'), false, loader), b = Class.forName(right.replace('/', '.'), false, loader);
                if (a.isAssignableFrom(b)) return left;
                if (b.isAssignableFrom(a)) return right;
                if (a.isInterface() || b.isInterface()) return "java/lang/Object";
                do { a = a.getSuperclass(); } while (!a.isAssignableFrom(b));
                return a.getName().replace('.', '/');
            } catch (Exception failure) { return "java/lang/Object"; }
        }
    }
}

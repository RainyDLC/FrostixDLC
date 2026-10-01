package mod.runtime;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

public final class LoadedAdapterInputRegression {
    private static final class FixtureLoader extends ClassLoader {
        Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
    }

    private static void expect(Class<? extends Throwable> type, Runnable operation) {
        try {
            operation.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("Wrong failure type", failure);
        }
        throw new AssertionError("Missing " + type.getSimpleName());
    }

    public static void main(String[] args) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "fixtures/AdapterInput", null, "java/lang/Object", null);
        writer.visitEnd();
        byte[] original = writer.toByteArray();
        Class<?> target = new FixtureLoader().define(original);

        expect(NullPointerException.class,
                () -> LoadedClassAdapter.adapt(original, original, null, new String[]{"fixtures.mixin"}));
        expect(NullPointerException.class,
                () -> LoadedClassAdapter.adapt(original, original, target, null));
        expect(IllegalArgumentException.class,
                () -> LoadedClassAdapter.adapt(original, original, target, new String[0]));
        expect(IllegalArgumentException.class,
                () -> LoadedClassAdapter.adapt(original, original, target, new String[]{""}));
        expect(IllegalArgumentException.class,
                () -> LoadedClassAdapter.adapt(original, original, target, new String[]{null}));

        byte[] adapted = LoadedClassAdapter.adapt(original, original, target, new String[]{"fixtures.mixin"});
        if (!LoadedClassAdapter.read(original).name.equals(LoadedClassAdapter.read(adapted).name)) {
            throw new AssertionError("Target class name changed");
        }
        new FixtureLoader().define(adapted);
        System.out.println("PASS adapter input validation and unchanged class verification");
    }
}

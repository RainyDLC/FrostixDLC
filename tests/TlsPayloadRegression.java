import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TlsPayloadRegression {
    private static void expectTlsFailure(Method validate, byte[] image, Object pe) throws Exception {
        try {
            validate.invoke(null, image, pe);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof java.io.IOException error && error.getMessage().contains("static TLS")) return;
            throw failure;
        }
        throw new AssertionError("Static TLS larger than the carrier allowance was accepted");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("payload DLL");
        Class<?> loader = Class.forName("LegitBuilder");
        Class<?> peType = Class.forName("LegitBuilder$Pe");
        Method parse = loader.getDeclaredMethod("parsePe", byte[].class);
        Method map = loader.getDeclaredMethod("mapRawToVirtual", byte[].class, peType);
        Method validate = loader.getDeclaredMethod("validateTlsTemplate", byte[].class, peType);
        parse.setAccessible(true);
        map.setAccessible(true);
        validate.setAccessible(true);

        byte[] raw = Files.readAllBytes(Path.of(args[0]));
        Object pe = parse.invoke(null, (Object) raw);
        byte[] image = (byte[]) map.invoke(null, raw, pe);
        validate.invoke(null, image, pe);

        var directory = peType.getDeclaredField("dataDirectoryOffset");
        directory.setAccessible(true);
        int entry = directory.getInt(pe) + 9 * 8;
        int tlsRva = image.length - 64;
        byte[] oversized = image.clone();
        ByteBuffer buffer = ByteBuffer.wrap(oversized).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(entry, tlsRva);
        buffer.putInt(entry + 4, 40);
        buffer.putLong(tlsRva, 100);
        buffer.putLong(tlsRva + 8, 109);
        expectTlsFailure(validate, oversized, pe);
        System.out.println("PASS payload TLS accepted; oversized static TLS rejected");
    }
}

package nightix;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Продвинутый шифратор и обфускатор байткода ядра Nightix / RainyDLC.
 *
 * 1. Трансформирует байткод каждого класса:
 *    - Полное удаление отладочной информации (LineNumberTable, LocalVariableTable, SourceFile).
 *    - Установка флага ACC_SYNTHETIC для усложнения анализа декомпиляторами (CFR, JD-GUI, Fernflower).
 * 2. Упаковывает ВСЕ ~590 классов ядра в сжатый (Deflate) бинарный пакет.
 * 3. Шифрует пакет с использованием AES-256-GCM с динамическим солевым ключом.
 * 4. Очищает метаданные у остающихся открытых классов (миксины и точки входа).
 */
public final class ClassEncryptor {

    private static final String CORE_BIN_PATH = "assets/nightix/core.bin";
    private static final int MAGIC = 0x4E495843; // NIXC
    private final SecureRandom random = new SecureRandom();

    public void run(File inJar, File outJar) {
        try {
            execute(inJar, outJar);
        } catch (Exception e) {
            throw new RuntimeException("Protection packaging failed", e);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: ClassEncryptor <in.jar> <out.jar>");
            System.exit(2);
        }
        new ClassEncryptor().execute(new File(args[0]), new File(args[1]));
    }

    private void execute(File inJar, File outJar) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        readJar(inJar, entries, order);

        List<String> toEncrypt = new ArrayList<>();
        for (String path : order) {
            if (!path.startsWith("ru/white/") || !path.endsWith(".class")) continue;
            // Пропускаем миксины и входные точки Fabric
            if (path.startsWith("ru/white/mixin/")) continue;
            if (path.equals("ru/white/Client.class")) continue;
            if (path.equals("ru/white/NightixLoader.class")) continue;
            if (path.equals("ru/white/NightixPreLaunch.class")) continue;

            toEncrypt.add(path);
        }

        // 1. Формируем сжатый бинарный пакет всех классов с трансформацией байткода
        ByteArrayOutputStream payloadBuf = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(payloadBuf);
        dos.writeInt(toEncrypt.size());

        for (String path : toEncrypt) {
            String className = path.substring(0, path.length() - ".class".length()).replace('/', '.');
            byte[] rawBytes = entries.get(path);
            byte[] transformedBytes = transformClass(rawBytes, true);
            byte[] compressed = compress(transformedBytes);

            dos.writeUTF(className);
            dos.writeInt(compressed.length);
            dos.write(compressed);
        }
        dos.flush();
        byte[] plainPayload = payloadBuf.toByteArray();

        // 2. Шифруем пакет с AES-256-GCM
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] keyBytes = deriveKey(salt);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");

        byte[] iv = new byte[12];
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] cipherText = cipher.doFinal(plainPayload);

        // 3. Формируем бинарный файл core.bin
        ByteArrayOutputStream coreBin = new ByteArrayOutputStream();
        DataOutputStream coreDos = new DataOutputStream(coreBin);
        coreDos.writeInt(MAGIC);
        coreDos.writeShort(1); // version
        coreDos.write(salt);
        coreDos.write(iv);
        coreDos.write(cipherText);
        coreDos.flush();

        // 4. Записываем выходной JAR, очищая отладку в оставшихся stub/mixin классах
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(outJar))) {
            for (String path : order) {
                if (toEncrypt.contains(path)) continue; // Зашифрованные .class удаляются из JAR!

                byte[] data = entries.get(path);
                if (path.startsWith("ru/white/") && path.endsWith(".class")) {
                    data = transformClass(data, false);
                }

                writeEntry(zip, path, data);
            }
            writeEntry(zip, CORE_BIN_PATH, coreBin.toByteArray());
        }

        long plainCount = order.stream()
                .filter(p -> p.startsWith("ru/white/") && p.endsWith(".class") && !toEncrypt.contains(p))
                .count();

        System.out.println("[protection] ========================================");
        System.out.println("[protection] Encrypted & obfuscated classes: " + toEncrypt.size() + " (in " + CORE_BIN_PATH + ")");
        System.out.println("[protection] Plain stub & mixin classes   : " + plainCount + " (debug info stripped)");
        System.out.println("[protection] Output Protected JAR         : " + outJar.getAbsolutePath());
        System.out.println("[protection] ========================================");
    }

    /**
     * Очищает метаданные, имена локальных переменных, номера строк и помечает методы как синтетические.
     */
    private byte[] transformClass(byte[] classBytes, boolean makeSynthetic) {
        try {
            ClassReader cr = new ClassReader(classBytes);
            ClassWriter cw = new ClassWriter(cr, 0);

            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    int acc = makeSynthetic ? (access | Opcodes.ACC_SYNTHETIC) : access;
                    super.visit(version, acc, name, signature, superName, interfaces);
                }

                @Override
                public void visitSource(String source, String debug) {
                    // Удаляем SourceFile
                }

                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    int acc = makeSynthetic ? (access | Opcodes.ACC_SYNTHETIC) : access;
                    return super.visitField(acc, name, descriptor, signature, value);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    int acc = (makeSynthetic && !name.startsWith("<")) ? (access | Opcodes.ACC_SYNTHETIC) : access;
                    MethodVisitor mv = super.visitMethod(acc, name, descriptor, signature, exceptions);
                    if (mv == null) return null;

                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitLineNumber(int line, Label start) {
                            // Удаляем номера строк (LineNumberTable)
                        }

                        @Override
                        public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
                            // Удаляем имена локальных переменных (LocalVariableTable)
                        }
                    };
                }
            };

            cr.accept(cv, ClassReader.SKIP_DEBUG);
            return cw.toByteArray();
        } catch (Throwable t) {
            return classBytes;
        }
    }

    private static byte[] deriveKey(byte[] salt) throws Exception {
        byte[] seed = getSeed();
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update(seed);
        sha.update(salt);
        return sha.digest();
    }

    private static byte[] getSeed() {
        byte[] s = new byte[32];
        int v = 0x8F3A2C17;
        for (int i = 0; i < 32; i++) {
            v = ((v ^ 0x6D) * 0x41C64E6D + 0x3039) ^ (i * 0x5A827999);
            s[i] = (byte) ((v ^ (v >>> 16) ^ (v >>> 8)) & 0xFF);
        }
        return s;
    }

    private static byte[] compress(byte[] input) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream output = new ByteArrayOutputStream(input.length);
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            output.write(buffer, 0, count);
        }
        deflater.end();
        return output.toByteArray();
    }

    private static void readJar(File jar, Map<String, byte[]> entries, List<String> order) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(jar))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int r;
                while ((r = zip.read(buf)) >= 0) out.write(buf, 0, r);
                entries.put(e.getName(), out.toByteArray());
                order.add(e.getName());
            }
        }
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] data) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }
}

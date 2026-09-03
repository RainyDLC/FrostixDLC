package rainydlc;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class ClassEncryptor {

    private static final String CORE_BIN_PATH = "assets/rainydlc/core.bin";
    private static final String MANIFEST_PATH = "assets/rainydlc/manifest.txt";

    private static final int MAGIC = 0x4E495843;
    private static final int VERSION = 3;

    private static final Set<String> PLAIN_CLASSES = Set.of(
            "fun/newrar/Client.class",
            "fun/newrar/RainyDlcLoader.class",
            "fun/newrar/RainyDlcPreLaunch.class"
    );

    private static final Set<String> EXTRA_INTEGRITY = Set.of();

    private static final String LOADER_CLASS = "fun/newrar/RainyDlcLoader.class";
    private static final String SYNTHESIZE_KEY_METHOD = "synthesizeKey";
    private static final String SYNTHESIZE_KEY_DESC = "([B[B)[B";
    private static final String KNOT_NAME = "net/fabricmc/loader/impl/launch/knot/KnotClassLoader";
    private static final String PROBE_METHOD = "$$probe";

    private static final String FABRIC_IMPL_STUB = "net/fabricmc/loader/impl/FabricLoaderImpl";
    private static final String CERT_ALIAS = "rainydlc";

    private static final int SECRET_LEN = 32;
    private static final int FRAGMENT_LEN = 8;
    private static final int FRAGMENT_COUNT = 12;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder b64 = Base64.getEncoder();

    private File keystoreFile;
    private char[] keystorePassword;
    private boolean certBind = true;

    private int encryptedStrings = 0;
    private int plainStringsHidden = 0;

    private String modId = "white";

    public void setKeystore(File file, String password) {
        this.keystoreFile = file;
        this.keystorePassword = password == null ? new char[0] : password.toCharArray();
    }

    public void setCertBind(boolean enabled) {
        this.certBind = enabled;
    }

    public void run(File inJar, File outJar) {
        try {
            execute(inJar, outJar);
        } catch (Exception e) {
            throw new RuntimeException("Protection packaging failed", e);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: ClassEncryptor <in.jar> <out.jar> [keystore.jks] [password]");
            System.exit(2);
        }
        ClassEncryptor e = new ClassEncryptor();
        if (args.length >= 4) {
            File ks = new File(args[2]);
            e.setKeystore(ks, args[3]);
        }
        e.execute(new File(args[0]), new File(args[1]));
    }

    private void execute(File inJar, File outJar) throws Exception {
        Map<String, byte[]> in = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        readJar(inJar, in, order);

        List<String> coreClasses = new ArrayList<>();

        Set<String> mixinReferenced = mixinReferenced(in, order);
        for (String path : order) {
            if (!path.startsWith("fun/newrar/") || !path.endsWith(".class")) continue;
            if (path.startsWith("fun/newrar/mixin/")) continue;
            if (PLAIN_CLASSES.contains(path)) continue;
            if (mixinReferenced.contains(path)) continue;
            coreClasses.add(path);
        }

        byte[] modJson = in.get("fabric.mod.json");
        if (modJson != null) {
            modId = parseModId(new String(modJson, StandardCharsets.UTF_8));
        }

        Map<String, byte[]> out = new LinkedHashMap<>();
        Set<String> integrityNames = new TreeSet<>();

        for (String path : order) {
            if (coreClasses.contains(path)) continue;

            byte[] data = in.get(path);
            if (path.startsWith("fun/newrar/") && path.endsWith(".class")) {
                data = stripDebug(data);
                data = obfuscateClass(data, false);
                integrityNames.add(path);
            } else if (EXTRA_INTEGRITY.contains(path)) {
                integrityNames.add(path);
            }
            out.put(path, data);
        }

        byte[] master = new byte[32];
        random.nextBytes(master);
        byte[] loaderBytes = out.get(LOADER_CLASS);
        if (loaderBytes == null) {
            throw new IllegalStateException(LOADER_CLASS + " not found in jar");
        }
        byte[] entropy = runtimeEntropy();
        byte[] injectedLoader = injectSecret(loaderBytes, master, entropy);
        byte[] obfuscatedLoader = obfuscateClass(injectedLoader, false);
        out.put(LOADER_CLASS, obfuscatedLoader);

        StringBuilder sb = new StringBuilder();
        for (String n : integrityNames) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(n);
        }
        byte[] manifest = sb.toString().getBytes(StandardCharsets.UTF_8);
        out.put(MANIFEST_PATH, manifest);

        byte[] integrity = digestOf(manifest, integrityNames, out);

        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] key = deriveKey(master, entropy, integrity, salt);

        byte[] payloadPlain = buildPayload(coreClasses, in);
        byte[] payloadPacked = deflate(payloadPlain);

        byte[] iv = new byte[12];
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] cipherText = cipher.doFinal(payloadPacked);

        verifyRoundTrip(cipher, key, iv, cipherText, payloadPlain, coreClasses);

        java.util.Arrays.fill(master, (byte) 0);
        java.util.Arrays.fill(key, (byte) 0);

        ByteArrayOutputStream coreBuf = new ByteArrayOutputStream();
        DataOutputStream coreDos = new DataOutputStream(coreBuf);
        coreDos.writeInt(MAGIC);
        coreDos.writeShort(VERSION);
        coreDos.write(salt);
        coreDos.write(iv);
        coreDos.write(cipherText);
        coreDos.flush();
        byte[] coreBin = coreBuf.toByteArray();

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(outJar))) {
            for (Map.Entry<String, byte[]> e : out.entrySet()) {
                writeEntry(zip, e.getKey(), e.getValue());
            }
            writeEntry(zip, CORE_BIN_PATH, coreBin);
        }

        System.out.println("[protection] ================================================");
        System.out.println("[protection] core classes encrypted : " + coreClasses.size());
        System.out.println("[protection] mixin-linked plain     : " + mixinReferenced(in, order).size());
        System.out.println("[protection] string constants hidden: " + encryptedStrings + " (core) + "
                + plainStringsHidden + " (open classes)");
        System.out.println("[protection] integrity-locked files : " + (integrityNames.size()));
        System.out.println("[protection] master secret          : single env-gated entry point in " + LOADER_CLASS);
        System.out.println("[protection] environment binding    : Fabric Knot + jar certificate ("
                + (certBind ? "ON" : "OFF") + ")");
        System.out.println("[protection] container              : " + CORE_BIN_PATH);
        System.out.println("[protection] output                 : " + outJar.getAbsolutePath());
        System.out.println("[protection] ================================================");
    }

    private Set<String> mixinReferenced(Map<String, byte[]> in, List<String> order) {
        Set<String> result = new TreeSet<>();
        for (String path : order) {
            if (path.startsWith("fun/newrar/manager/event_impl/")
                    || path.startsWith("fun/newrar/manager/events/orbit/")
                    || path.equals("fun/newrar/utils/player/ITimerSpeed.class")
                    || path.equals("fun/newrar/utils/annotation/IMinecraft.class")) {
                result.add(path);
            }
        }
        for (String plain : PLAIN_CLASSES) {
            result.remove(plain);
        }
        return result;
    }

    private byte[] buildPayload(List<String> coreClasses, Map<String, byte[]> in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(buf);
        dos.writeInt(coreClasses.size());
        for (String path : coreClasses) {
            String className = path.substring(0, path.length() - ".class".length()).replace('/', '.');
            byte[] transformed = obfuscateClass(in.get(path), true);
            dos.writeUTF(className);
            dos.writeInt(transformed.length);
            dos.write(transformed);
        }
        dos.flush();
        return buf.toByteArray();
    }

    private void verifyRoundTrip(Cipher cipher, byte[] key, byte[] iv, byte[] cipherText,
                                 byte[] expectedPlain, List<String> coreClasses) throws Exception {
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] packed = cipher.doFinal(cipherText);
        byte[] plain = inflate(packed);
        if (!java.util.Arrays.equals(plain, expectedPlain)) {
            throw new IllegalStateException("payload round-trip mismatch");
        }
        int checked = 0;
        java.io.DataInputStream dis = new java.io.DataInputStream(new java.io.ByteArrayInputStream(plain));
        int count = dis.readInt();
        for (int i = 0; i < count; i++) {
            dis.readUTF();
            int len = dis.readInt();
            byte[] cb = dis.readNBytes(len);

            new ClassReader(cb).accept(new ClassVisitor(Opcodes.ASM9) {
            }, ClassReader.SKIP_FRAMES);
            checked++;
        }
        if (checked != coreClasses.size()) {
            throw new IllegalStateException("class count mismatch after round-trip");
        }
    }

    private byte[] runtimeEntropy() throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update(KNOT_NAME.replace('/', '.').getBytes(StandardCharsets.UTF_8));
        sha.update((byte) 0xAA);
        sha.update(KNOT_NAME.replace('/', '.').getBytes(StandardCharsets.UTF_8));
        sha.update((byte) 0x55);
        sha.update(modId.getBytes(StandardCharsets.UTF_8));
        sha.update((byte) 0x33);
        sha.update(modId.getBytes(StandardCharsets.UTF_8));
        sha.update((byte) 0x77);
        return sha.digest();
    }

    private byte[] deriveKey(byte[] master, byte[] entropy, byte[] integrity, byte[] salt) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(master);
        md.update(entropy);
        md.update(integrity);
        md.update(salt);
        return md.digest();
    }

    private byte[] digestOf(byte[] manifest, Set<String> names, Map<String, byte[]> entries) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update(manifest);
        for (String name : new TreeSet<>(names)) {
            byte[] data = entries.get(name);
            if (data == null) throw new IllegalStateException("missing integrity entry: " + name);
            sha.update(name.getBytes(StandardCharsets.UTF_8));
            sha.update((byte) 0x1F);
            sha.update(data);
        }
        return sha.digest();
    }

    private byte[] stripDebug(byte[] classBytes) {
        try {
            ClassReader cr = new ClassReader(classBytes);
            ClassWriter cw = new ClassWriter(cr, 0);
            cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public void visitSource(String source, String debug) {
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (mv == null) return null;
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitLineNumber(int line, Label start) {
                        }

                        @Override
                        public void visitLocalVariable(String name, String descriptor, String signature,
                                                       Label start, Label end, int index) {
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG);
            return cw.toByteArray();
        } catch (Throwable t) {
            return classBytes;
        }
    }

    private byte[] obfuscateClass(byte[] classBytes, boolean core) {
        ClassReader header = new ClassReader(classBytes);
        if ((header.getAccess() & Opcodes.ACC_INTERFACE) != 0) {
            return classBytes;
        }
        String owner = header.getClassName();

        Set<String> used = memberNames(header);
        String decName = uniqueName(used);
        String seedAName = uniqueName(used);
        String seedBName = uniqueName(used);
        String cacheName = uniqueName(used);

        Codec codec = Codec.random(random);
        codecName(codec);

        byte[] probeBytes = buildProbeClass(owner, decName, seedAName, seedBName, cacheName, codec);
        MethodNode decryptor = extractMethod(probeBytes, decName);
        runtimeCheck(owner, probeBytes, decName, codec);

        try {
            ClassReader cr = new ClassReader(classBytes);
            ClassWriter cw = new ClassWriter(cr, 0);
            cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public void visit(int version, int access, String name, String signature,
                                  String superName, String[] interfaces) {
                    int acc = core ? (access | Opcodes.ACC_SYNTHETIC) : access;
                    super.visit(version, acc, name, signature, superName, interfaces);
                }

                @Override
                public void visitSource(String source, String debug) {
                }

                @Override
                public FieldVisitor visitField(int access, String name, String descriptor,
                                               String signature, Object value) {
                    int acc = core ? (access | Opcodes.ACC_SYNTHETIC) : access;
                    return super.visitField(acc, name, descriptor, signature, value);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    int acc = core && !name.startsWith("<") ? (access | Opcodes.ACC_SYNTHETIC) : access;
                    MethodVisitor mv = super.visitMethod(acc, name, descriptor, signature, exceptions);
                    if (mv == null) return null;

                    boolean isSelf = name.equals(decName);
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitLdcInsn(Object value) {
                            if (value instanceof String s && !isSelf) {
                                if (core) {
                                    encryptedStrings++;
                                } else {
                                    plainStringsHidden++;
                                }
                                super.visitLdcInsn(codec.encrypt(s));
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, owner, decName,
                                        "(Ljava/lang/String;)Ljava/lang/String;", false);
                                return;
                            }
                            super.visitLdcInsn(value);
                        }

                        @Override
                        public void visitLineNumber(int line, Label start) {
                        }

                        @Override
                        public void visitLocalVariable(String name, String descriptor, String signature,
                                                       Label start, Label end, int index) {
                        }
                    };
                }

                @Override
                public void visitEnd() {
                    emitIntField(cw, seedAName, codec.seedA);
                    emitIntField(cw, seedBName, codec.seedB);
                    emitCacheField(cw, cacheName);
                    decryptor.accept(cw);
                    super.visitEnd();
                }
            }, ClassReader.SKIP_DEBUG);

            byte[] result = cw.toByteArray();
            sanityCheck(result);
            return result;
        } catch (Throwable t) {
            throw new IllegalStateException("failed to obfuscate " + owner, t);
        }
    }

    private static String codecName(Codec c) {
        return "m" + c.mode + (c.reversed ? "r" : "") + "j" + c.junk;
    }

    private void emitIntField(ClassWriter cw, String name, int value) {
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                name, "I", null, value).visitEnd();
    }

    private void emitCacheField(ClassWriter cw, String name) {
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                name, "Ljava/util/Map;", null, null).visitEnd();
    }

    private static final class Codec {
        final int seedA;
        final int seedB;
        final int mul;
        final int mode;
        final boolean reversed;
        final int junk;

        Codec(int seedA, int seedB, int mul, int mode, boolean reversed, int junk) {
            this.seedA = seedA;
            this.seedB = seedB;
            this.mul = mul;
            this.mode = mode;
            this.reversed = reversed;
            this.junk = junk;
        }

        static Codec random(SecureRandom r) {
            int mul = 0x9E3779B1 ^ r.nextInt(1 << 16);
            if (mul == 0) mul = 0x9E3779B1;
            return new Codec(r.nextInt() | 1, r.nextInt() | 1, mul, r.nextInt(3), r.nextBoolean(), r.nextInt(3));
        }

        int st1() {
            return seedA ^ (seedB * mul);
        }

        int st2() {
            return seedB ^ 0x5A5A5A5A;
        }

        int ks(int st1, int st2, int i) {
            return switch (mode) {
                case 0 -> (st1 >>> 16) ^ (i * 31 + 7);
                case 1 -> (st1 >>> 16) ^ (st2 >>> 8) ^ i;
                default -> (st1 >>> 24) ^ (st2 >>> 16) ^ (i * mul);
            };
        }

        String encrypt(String s) {
            byte[] raw = s.getBytes(StandardCharsets.UTF_8);
            byte[] enc = new byte[raw.length];
            int a = st1();
            int b = st2();
            for (int i = 0; i < raw.length; i++) {
                a = a * 1103515245 + 12345;
                b = b * 214013 + 2531011;

                enc[pi(i, raw.length)] = (byte) (raw[i] ^ ks(a, b, i));
            }
            String result = Base64.getEncoder().encodeToString(enc);
            if (!s.equals(decrypt(result))) {
                throw new IllegalStateException("string codec mismatch for: " + s);
            }
            return result;
        }

        String decrypt(String s) {
            byte[] enc = Base64.getDecoder().decode(s);
            byte[] out = new byte[enc.length];
            int a = st1();
            int b = st2();
            for (int i = 0; i < enc.length; i++) {
                a = a * 1103515245 + 12345;
                b = b * 214013 + 2531011;
                out[i] = (byte) (enc[pi(i, enc.length)] ^ ks(a, b, i));
            }
            return new String(out, StandardCharsets.UTF_8);
        }

        private int pi(int i, int len) {
            return reversed ? len - 1 - i : i;
        }
    }

    private byte[] buildProbeClass(String owner, String decName, String seedA, String seedB,
                                   String cacheName, Codec codec) {
        ClassWriter probe = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        probe.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, owner, null, "java/lang/Object", null);
        emitIntField(probe, seedA, codec.seedA);
        emitIntField(probe, seedB, codec.seedB);
        emitCacheField(probe, cacheName);
        emitDecryptor(probe, owner, decName, seedA, seedB, cacheName, codec);
        probe.visitEnd();
        return probe.toByteArray();
    }

    private MethodNode extractMethod(byte[] probeBytes, String name) {
        ClassNode cn = new ClassNode();
        new ClassReader(probeBytes).accept(cn, 0);
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals(name)) {
                mn.access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
                return mn;
            }
        }
        throw new IllegalStateException("decryptor generation failed");
    }

    private void runtimeCheck(String owner, byte[] probeBytes, String decName, Codec codec) {
        Class<?> probe = null;
        try {

            probe = new ProbeLoader().define(owner.replace('/', '.'), probeBytes);
            Method m = probe.getDeclaredMethod(decName, String.class);
            m.setAccessible(true);
            String sample = probeSample(codec.seedA);
            Object out = m.invoke(null, codec.encrypt(sample));
            if (!sample.equals(out)) {
                throw new IllegalStateException("decryptor returned garbage");
            }

            Object again = m.invoke(null, codec.encrypt(sample));
            if (!sample.equals(again)) {
                throw new IllegalStateException("decryptor cache is broken");
            }

            String tricky = "\u0000\u00FF\u2028\\\"'{}\u4E2D";
            if (!tricky.equals(m.invoke(null, codec.encrypt(tricky)))) {
                throw new IllegalStateException("decryptor corrupts non-ascii input");
            }
        } catch (Throwable t) {
            StringBuilder diag = new StringBuilder();
            diag.append(" [want ").append(decName).append("(Ljava/lang/String;)Ljava/lang/String;]");
            if (probe != null) {
                diag.append(" [defined ").append(probe.getName())
                        .append(" by ").append(probe.getClassLoader()).append(']');
                for (Method dm : probe.getDeclaredMethods()) {
                    diag.append("\n    method: ").append(dm);
                }
                for (java.lang.reflect.Field df : probe.getDeclaredFields()) {
                    diag.append("\n    field : ").append(df);
                }
            } else {
                diag.append(" [probe class was never defined]");
            }
            File dump = new File("build/protection/probe-failed.class");
            try {
                dump.getParentFile().mkdirs();
                java.nio.file.Files.write(dump.toPath(), probeBytes);
            } catch (Throwable ignored) {

            }
            throw new IllegalStateException("generated decryptor failed verification for " + owner
                    + diag + " (dump: " + dump.getAbsolutePath() + ")", t);
        }
    }

    private static String probeSample(int seed) {
        return "rainydlc\0self\u0430test-" + seed + "-<>\"'\\{}[]";
    }

    private static final class ProbeLoader extends ClassLoader {
        private final Map<String, Class<?>> defined = new java.util.HashMap<>();

        Class<?> define(String name, byte[] b) {
            Class<?> existing = findLoadedClass(name);
            if (existing != null) return existing;
            Class<?> c = defineClass(name, b, 0, b.length);
            defined.put(name, c);
            return c;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            Class<?> c = defined.get(name);
            if (c != null) return c;
            return super.findClass(name);
        }
    }

    private void emitDecryptor(ClassWriter cw, String owner, String decName, String seedA, String seedB,
                               String cacheName, Codec codec) {
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                decName, "(Ljava/lang/String;)Ljava/lang/String;", null, null);
        mv.visitCode();

        mv.visitFieldInsn(Opcodes.GETSTATIC, owner, cacheName, "Ljava/util/Map;");
        mv.visitVarInsn(Opcodes.ASTORE, 1);

        Label compute = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitJumpInsn(Opcodes.IFNULL, compute);

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;", true);
        mv.visitVarInsn(Opcodes.ASTORE, 2);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitJumpInsn(Opcodes.IFNULL, compute);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
        mv.visitInsn(Opcodes.ARETURN);

        mv.visitLabel(compute);

        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Base64", "getDecoder",
                "()Ljava/util/Base64$Decoder;", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Base64$Decoder", "decode",
                "(Ljava/lang/String;)[B", false);
        mv.visitVarInsn(Opcodes.ASTORE, 3);

        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitInsn(Opcodes.ARRAYLENGTH);
        mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
        mv.visitVarInsn(Opcodes.ASTORE, 4);

        mv.visitFieldInsn(Opcodes.GETSTATIC, owner, seedA, "I");
        mv.visitFieldInsn(Opcodes.GETSTATIC, owner, seedB, "I");
        mv.visitLdcInsn(codec.mul);
        mv.visitInsn(Opcodes.IMUL);
        mv.visitInsn(Opcodes.IXOR);
        mv.visitVarInsn(Opcodes.ISTORE, 5);

        mv.visitFieldInsn(Opcodes.GETSTATIC, owner, seedB, "I");
        mv.visitLdcInsn(0x5A5A5A5A);
        mv.visitInsn(Opcodes.IXOR);
        mv.visitVarInsn(Opcodes.ISTORE, 6);

        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitVarInsn(Opcodes.ISTORE, 7);

        Label loop = new Label();
        Label end = new Label();
        mv.visitLabel(loop);
        mv.visitVarInsn(Opcodes.ILOAD, 7);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitInsn(Opcodes.ARRAYLENGTH);
        mv.visitJumpInsn(Opcodes.IF_ICMPGE, end);

        mv.visitVarInsn(Opcodes.ILOAD, 5);
        mv.visitLdcInsn(1103515245);
        mv.visitInsn(Opcodes.IMUL);
        mv.visitLdcInsn(12345);
        mv.visitInsn(Opcodes.IADD);
        mv.visitVarInsn(Opcodes.ISTORE, 5);

        mv.visitVarInsn(Opcodes.ILOAD, 6);
        mv.visitLdcInsn(214013);
        mv.visitInsn(Opcodes.IMUL);
        mv.visitLdcInsn(2531011);
        mv.visitInsn(Opcodes.IADD);
        mv.visitVarInsn(Opcodes.ISTORE, 6);

        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitVarInsn(Opcodes.ILOAD, 7);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        emitIndex(mv, codec.reversed, 7, 3);
        mv.visitInsn(Opcodes.BALOAD);
        emitKeystream(mv, codec);
        mv.visitInsn(Opcodes.IXOR);
        mv.visitInsn(Opcodes.BASTORE);

        emitJunk(mv, codec.junk);

        mv.visitIincInsn(7, 1);
        mv.visitJumpInsn(Opcodes.GOTO, loop);
        mv.visitLabel(end);

        mv.visitTypeInsn(Opcodes.NEW, "java/lang/String");
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitFieldInsn(Opcodes.GETSTATIC, "java/nio/charset/StandardCharsets", "UTF_8",
                "Ljava/nio/charset/Charset;");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>",
                "([BLjava/nio/charset/Charset;)V", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "intern",
                "()Ljava/lang/String;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 8);

        Label put = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitJumpInsn(Opcodes.IFNONNULL, put);
        mv.visitTypeInsn(Opcodes.NEW, "java/util/concurrent/ConcurrentHashMap");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/concurrent/ConcurrentHashMap", "<init>", "()V", false);
        mv.visitInsn(Opcodes.DUP);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, owner, cacheName, "Ljava/util/Map;");
        mv.visitVarInsn(Opcodes.ASTORE, 1);
        mv.visitLabel(put);

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 8);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
        mv.visitInsn(Opcodes.POP);

        mv.visitVarInsn(Opcodes.ALOAD, 8);
        mv.visitInsn(Opcodes.ARETURN);

        mv.visitMaxs(8, 9);
        mv.visitEnd();
    }

    private void emitIndex(MethodVisitor mv, boolean reversed, int iSlot, int arrSlot) {
        if (!reversed) {
            mv.visitVarInsn(Opcodes.ILOAD, iSlot);
            return;
        }
        mv.visitVarInsn(Opcodes.ALOAD, arrSlot);
        mv.visitInsn(Opcodes.ARRAYLENGTH);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.ISUB);
        mv.visitVarInsn(Opcodes.ILOAD, iSlot);
        mv.visitInsn(Opcodes.ISUB);
    }

    private void emitKeystream(MethodVisitor mv, Codec codec) {
        switch (codec.mode) {
            case 0 -> {
                mv.visitVarInsn(Opcodes.ILOAD, 5);
                mv.visitIntInsn(Opcodes.BIPUSH, 16);
                mv.visitInsn(Opcodes.IUSHR);
                mv.visitVarInsn(Opcodes.ILOAD, 7);
                mv.visitIntInsn(Opcodes.BIPUSH, 31);
                mv.visitInsn(Opcodes.IMUL);
                mv.visitIntInsn(Opcodes.BIPUSH, 7);
                mv.visitInsn(Opcodes.IADD);
                mv.visitInsn(Opcodes.IXOR);
            }
            case 1 -> {
                mv.visitVarInsn(Opcodes.ILOAD, 5);
                mv.visitIntInsn(Opcodes.BIPUSH, 16);
                mv.visitInsn(Opcodes.IUSHR);
                mv.visitVarInsn(Opcodes.ILOAD, 6);
                mv.visitIntInsn(Opcodes.BIPUSH, 8);
                mv.visitInsn(Opcodes.IUSHR);
                mv.visitInsn(Opcodes.IXOR);
                mv.visitVarInsn(Opcodes.ILOAD, 7);
                mv.visitInsn(Opcodes.IXOR);
            }
            default -> {
                mv.visitVarInsn(Opcodes.ILOAD, 5);
                mv.visitIntInsn(Opcodes.BIPUSH, 24);
                mv.visitInsn(Opcodes.IUSHR);
                mv.visitVarInsn(Opcodes.ILOAD, 6);
                mv.visitIntInsn(Opcodes.BIPUSH, 16);
                mv.visitInsn(Opcodes.IUSHR);
                mv.visitInsn(Opcodes.IXOR);
                mv.visitVarInsn(Opcodes.ILOAD, 7);
                mv.visitLdcInsn(codec.mul);
                mv.visitInsn(Opcodes.IMUL);
                mv.visitInsn(Opcodes.IXOR);
            }
        }
    }

    private void emitJunk(MethodVisitor mv, int amount) {
        for (int n = 0; n < amount; n++) {
            switch ((n + amount) % 3) {
                case 0 -> {
                    mv.visitInsn(Opcodes.ICONST_0);
                    mv.visitInsn(Opcodes.POP);
                }
                case 1 -> {
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitInsn(Opcodes.POP);
                }
                default -> {
                    mv.visitVarInsn(Opcodes.ILOAD, 7);
                    mv.visitVarInsn(Opcodes.ILOAD, 7);
                    mv.visitInsn(Opcodes.IADD);
                    mv.visitInsn(Opcodes.POP);
                }
            }
        }
    }

    private byte[] injectSecret(byte[] loaderBytes, byte[] master, byte[] expectedEntropy) {
        requireSecretApi(loaderBytes);
        SecretPlan plan = buildPlan(master, expectedEntropy);

        byte[] shipped = instrumentLoader(loaderBytes, plan, false);
        sanityCheck(shipped);

        verifySecretAssembly(instrumentLoader(loaderBytes, plan, true), master, expectedEntropy);

        return shipped;
    }

    private void requireSecretApi(byte[] loaderBytes) {
        Set<String> present = new HashSet<>();
        new ClassReader(loaderBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                present.add(name + descriptor);
                return null;
            }
        }, ClassReader.SKIP_CODE);

        if (!present.contains(SYNTHESIZE_KEY_METHOD + SYNTHESIZE_KEY_DESC)) {
            throw new IllegalStateException("ru.white.RainyDlcLoader is missing "
                    + SYNTHESIZE_KEY_METHOD + SYNTHESIZE_KEY_DESC
                    + " — ProGuard must keep the loader members intact (see protection/proguard.rules)");
        }
    }

    private byte[] instrumentLoader(byte[] loaderBytes, SecretPlan plan, boolean withProbe) {
        ClassReader cr = new ClassReader(loaderBytes);
        ClassWriter cw = new ClassWriter(cr, 0);
        String owner = cr.getClassName();
        boolean[] found = {false};

        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (SYNTHESIZE_KEY_METHOD.equals(name) && SYNTHESIZE_KEY_DESC.equals(descriptor)) {
                    found[0] = true;
                    return null;
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }

            @Override
            public void visitEnd() {
                if (!found[0]) {
                    throw new IllegalStateException("ru.white.RainyDlcLoader#" + SYNTHESIZE_KEY_METHOD
                            + SYNTHESIZE_KEY_DESC + " not found — ProGuard must keep the loader intact");
                }
                emitSynthesizeKey(cw, owner, plan);
                if (withProbe) {
                    MethodVisitor mv = cw.visitMethod(
                            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, PROBE_METHOD, SYNTHESIZE_KEY_DESC, null, null);
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitVarInsn(Opcodes.ALOAD, 1);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, SYNTHESIZE_KEY_METHOD, SYNTHESIZE_KEY_DESC, false);
                    mv.visitInsn(Opcodes.ARETURN);
                    mv.visitMaxs(2, 2);
                    mv.visitEnd();
                }
                super.visitEnd();
            }
        }, 0);

        return cw.toByteArray();
    }

    private void emitSynthesizeKey(ClassWriter cw, String owner, SecretPlan plan) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                SYNTHESIZE_KEY_METHOD, SYNTHESIZE_KEY_DESC, null, null);
        mv.visitCode();

        mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "internalEntropy", "()[B", false);
        mv.visitVarInsn(Opcodes.ASTORE, 2);

        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitInsn(Opcodes.BALOAD);
        mv.visitIntInsn(Opcodes.SIPUSH, 255);
        mv.visitInsn(Opcodes.IAND);
        mv.visitIntInsn(Opcodes.BIPUSH, 24);
        mv.visitInsn(Opcodes.ISHL);

        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.BALOAD);
        mv.visitIntInsn(Opcodes.SIPUSH, 255);
        mv.visitInsn(Opcodes.IAND);
        mv.visitIntInsn(Opcodes.BIPUSH, 16);
        mv.visitInsn(Opcodes.ISHL);
        mv.visitInsn(Opcodes.IOR);

        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitInsn(Opcodes.ICONST_2);
        mv.visitInsn(Opcodes.BALOAD);
        mv.visitIntInsn(Opcodes.SIPUSH, 255);
        mv.visitInsn(Opcodes.IAND);
        mv.visitIntInsn(Opcodes.BIPUSH, 8);
        mv.visitInsn(Opcodes.ISHL);
        mv.visitInsn(Opcodes.IOR);

        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitInsn(Opcodes.ICONST_3);
        mv.visitInsn(Opcodes.BALOAD);
        mv.visitIntInsn(Opcodes.SIPUSH, 255);
        mv.visitInsn(Opcodes.IAND);
        mv.visitInsn(Opcodes.IOR);

        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitInsn(Opcodes.ICONST_4);
        mv.visitInsn(Opcodes.BALOAD);
        mv.visitIntInsn(Opcodes.SIPUSH, 255);
        mv.visitInsn(Opcodes.IAND);
        mv.visitIntInsn(Opcodes.BIPUSH, 24);
        mv.visitInsn(Opcodes.ISHL);

        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitIntInsn(Opcodes.BIPUSH, 5);
        mv.visitInsn(Opcodes.BALOAD);
        mv.visitIntInsn(Opcodes.SIPUSH, 255);
        mv.visitInsn(Opcodes.IAND);
        mv.visitIntInsn(Opcodes.BIPUSH, 16);
        mv.visitInsn(Opcodes.ISHL);
        mv.visitInsn(Opcodes.IOR);

        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitIntInsn(Opcodes.BIPUSH, 6);
        mv.visitInsn(Opcodes.BALOAD);
        mv.visitIntInsn(Opcodes.SIPUSH, 255);
        mv.visitInsn(Opcodes.IAND);
        mv.visitIntInsn(Opcodes.BIPUSH, 8);
        mv.visitInsn(Opcodes.ISHL);
        mv.visitInsn(Opcodes.IOR);

        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitIntInsn(Opcodes.BIPUSH, 7);
        mv.visitInsn(Opcodes.BALOAD);
        mv.visitIntInsn(Opcodes.SIPUSH, 255);
        mv.visitInsn(Opcodes.IAND);
        mv.visitInsn(Opcodes.IOR);

        mv.visitInsn(Opcodes.IXOR);
        mv.visitLdcInsn(plan.kAdd);
        mv.visitInsn(Opcodes.IXOR);
        mv.visitVarInsn(Opcodes.ISTORE, 3);

        mv.visitIntInsn(Opcodes.BIPUSH, SECRET_LEN);
        mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
        mv.visitVarInsn(Opcodes.ASTORE, 4);

        for (int i = 0; i < plan.fragments.length; i++) {
            mv.visitLdcInsn(plan.fragments[i]);
            mv.visitVarInsn(Opcodes.ASTORE, 5 + i);
        }

        for (int j = 0; j < SECRET_LEN; j++) {
            SecretPlan.Piece p = plan.pieces[j];

            mv.visitVarInsn(Opcodes.ILOAD, 3);
            mv.visitLdcInsn(1103515245);
            mv.visitInsn(Opcodes.IMUL);
            mv.visitLdcInsn(12345);
            mv.visitInsn(Opcodes.IADD);
            mv.visitVarInsn(Opcodes.ISTORE, 3);

            mv.visitVarInsn(Opcodes.ALOAD, 4);
            mv.visitIntInsn(Opcodes.BIPUSH, j);

            mv.visitVarInsn(Opcodes.ALOAD, 5 + p.fa);
            mv.visitIntInsn(Opcodes.BIPUSH, p.pa);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
            mv.visitVarInsn(Opcodes.ALOAD, 5 + p.fb);
            mv.visitIntInsn(Opcodes.BIPUSH, p.pb);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
            mv.visitInsn(Opcodes.IXOR);
            mv.visitLdcInsn(p.mask);
            mv.visitInsn(Opcodes.IXOR);

            mv.visitVarInsn(Opcodes.ILOAD, 3);
            mv.visitIntInsn(Opcodes.BIPUSH, 16);
            mv.visitInsn(Opcodes.IUSHR);
            mv.visitInsn(Opcodes.IXOR);

            mv.visitInsn(Opcodes.I2B);
            mv.visitInsn(Opcodes.BASTORE);
        }

        int shaSlot = 5 + plan.fragments.length;
        mv.visitLdcInsn("SHA-256");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/security/MessageDigest", "getInstance",
                "(Ljava/lang/String;)Ljava/security/MessageDigest;", false);
        mv.visitVarInsn(Opcodes.ASTORE, shaSlot);

        mv.visitVarInsn(Opcodes.ALOAD, shaSlot);
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/security/MessageDigest", "update",
                "([B)V", false);

        mv.visitVarInsn(Opcodes.ALOAD, shaSlot);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/security/MessageDigest", "update",
                "([B)V", false);

        mv.visitVarInsn(Opcodes.ALOAD, shaSlot);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/security/MessageDigest", "update",
                "([B)V", false);

        mv.visitVarInsn(Opcodes.ALOAD, shaSlot);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/security/MessageDigest", "update",
                "([B)V", false);

        int keySlot = shaSlot + 1;
        mv.visitVarInsn(Opcodes.ALOAD, shaSlot);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/security/MessageDigest", "digest",
                "()[B", false);
        mv.visitVarInsn(Opcodes.ASTORE, keySlot);

        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Arrays", "fill",
                "([BB)V", false);

        mv.visitVarInsn(Opcodes.ALOAD, keySlot);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(6, keySlot + 1);
        mv.visitEnd();
    }

    private SecretPlan buildPlan(byte[] master, byte[] expectedEntropy) {
        String[] fragments = new String[FRAGMENT_COUNT];
        for (int i = 0; i < FRAGMENT_COUNT; i++) {
            fragments[i] = randomFragment(FRAGMENT_LEN);
        }

        int e0 = ((expectedEntropy[0] & 0xFF) << 24)
                | ((expectedEntropy[1] & 0xFF) << 16)
                | ((expectedEntropy[2] & 0xFF) << 8)
                | (expectedEntropy[3] & 0xFF);
        int e1 = ((expectedEntropy[4] & 0xFF) << 24)
                | ((expectedEntropy[5] & 0xFF) << 16)
                | ((expectedEntropy[6] & 0xFF) << 8)
                | (expectedEntropy[7] & 0xFF);
        int kAdd = random.nextInt();
        int a0 = e0 ^ e1 ^ kAdd;

        SecretPlan.Piece[] pieces = new SecretPlan.Piece[master.length];

        int a = a0;
        for (int j = 0; j < master.length; j++) {
            a = a * 1103515245 + 12345;
            int kb = (a >>> 16) & 0xFF;

            SecretPlan.Piece p = new SecretPlan.Piece();
            p.fa = random.nextInt(FRAGMENT_COUNT);
            p.fb = random.nextInt(FRAGMENT_COUNT);
            p.pa = random.nextInt(FRAGMENT_LEN);
            p.pb = random.nextInt(FRAGMENT_LEN);

            int c1 = fragments[p.fa].charAt(p.pa);
            int c2 = fragments[p.fb].charAt(p.pb);
            int low = (master[j] & 0xFF) ^ (c1 & 0xFF) ^ (c2 & 0xFF) ^ kb;
            p.mask = (random.nextInt() & ~0xFF) | low;
            pieces[j] = p;
        }

        return new SecretPlan(kAdd, fragments, pieces);
    }

    private static final class SecretPlan {
        final int kAdd;
        final String[] fragments;
        final Piece[] pieces;

        SecretPlan(int kAdd, String[] fragments, Piece[] pieces) {
            this.kAdd = kAdd;
            this.fragments = fragments;
            this.pieces = pieces;
        }

        static final class Piece {
            int fa, fb, pa, pb, mask;
        }
    }

    private void verifySecretAssembly(byte[] probeBytes, byte[] master, byte[] expectedEntropy) {
        Certificate[] chain = signingChain();
        if (chain == null) {
            System.out.println("[protection] WARNING: no signing certificate available —"
                    + " master secret self-check skipped (build with 'protectJar' to enable it)");
            return;
        }

        byte[] integrity = new byte[32];
        byte[] salt = new byte[16];
        random.nextBytes(integrity);
        random.nextBytes(salt);

        byte[] expectedKey;
        try {
            expectedKey = deriveKey(master, expectedEntropy, integrity, salt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        byte[] good;
        byte[] bare;
        try {
            good = callProbe(defineInFakeKnot(probeBytes, chain, modId), integrity, salt);
            bare = callProbe(bareLoader(probeBytes), integrity, salt);
        } catch (Throwable t) {
            throw new IllegalStateException("master secret self-check failed to run", t);
        }

        if (!java.util.Arrays.equals(good, expectedKey)) {
            throw new IllegalStateException(
                    "master secret does not reassemble key inside a real Fabric environment"
                            + " (got " + hex(good) + ")");
        }
        if (java.util.Arrays.equals(bare, expectedKey)) {
            throw new IllegalStateException(
                    "master secret reassembles key in a bare ClassLoader — the environment gate is dead");
        }
        System.out.println("[protection] master secret self-check: assembled key under Fabric (mod '" + modId
                + "'), garbage in a bare loader and without our mod");
    }

    private static Class<?> bareLoader(byte[] probeBytes) {
        ProbeLoader parent = new ProbeLoader();
        parent.define("fun.newrar.Client", emptyStub("fun/newrar/Client"));
        return parent.define("fun.newrar.RainyDlcLoader", probeBytes);
    }

    private static byte[] callProbe(Class<?> probeClass, byte[] integrity, byte[] salt) throws Exception {
        Method m = probeClass.getDeclaredMethod(PROBE_METHOD, byte[].class, byte[].class);
        m.setAccessible(true);
        return (byte[]) m.invoke(null, integrity, salt);
    }

    private Class<?> defineInFakeKnot(byte[] probeBytes, Certificate[] chain, String fabricModId) throws Exception {
        ProbeLoader parent = new ProbeLoader();
        Class<?> knot = parent.define(KNOT_NAME.replace('/', '.'), knotLoaderStub());

        File simJar = new File(System.getProperty("java.io.tmpdir", "."), "rainydlc-sim-mod.jar");
        if (simJar.getParentFile() != null) simJar.getParentFile().mkdirs();
        if (!simJar.exists()) {
            java.nio.file.Files.write(simJar.toPath(), new byte[]{'P', 'K', 3, 4});
        }
        parent.define(FABRIC_IMPL_STUB.replace('/', '.'), fabricLoaderStub(fabricModId, simJar));

        parent.define("fun.newrar.Client", emptyStub("fun/newrar/Client"));

        Object loader = knot.getConstructor(ClassLoader.class).newInstance((ClassLoader) parent);
        Method define = knot.getMethod("define",
                String.class, byte[].class, java.security.ProtectionDomain.class);

        java.security.CodeSource cs = new java.security.CodeSource(simJar.toURI().toURL(), chain);
        java.security.ProtectionDomain pd =
                new java.security.ProtectionDomain(cs, null, (ClassLoader) loader, null);

        return (Class<?>) define.invoke(loader, "fun.newrar.RainyDlcLoader", probeBytes, pd);
    }

    private static byte[] emptyStub(String internalName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] knotLoaderStub() {

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, KNOT_NAME, null, "java/lang/ClassLoader", null);

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/ClassLoader;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/ClassLoader", "<init>",
                "(Ljava/lang/ClassLoader;)V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "define",
                "(Ljava/lang/String;[BLjava/security/ProtectionDomain;)Ljava/lang/Class;", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitInsn(Opcodes.ARRAYLENGTH);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KNOT_NAME, "defineClass",
                "(Ljava/lang/String;[BIILjava/security/ProtectionDomain;)Ljava/lang/Class;", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(6, 4);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_PROTECTED, "loadClass",
                "(Ljava/lang/String;Z)Ljava/lang/Class;", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KNOT_NAME, "findLoadedClass",
                "(Ljava/lang/String;)Ljava/lang/Class;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 3);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        Label known = new Label();
        mv.visitJumpInsn(Opcodes.IFNONNULL, known);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KNOT_NAME, "getParent", "()Ljava/lang/ClassLoader;", false);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/ClassLoader", "loadClass",
                "(Ljava/lang/String;)Ljava/lang/Class;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 3);
        mv.visitLabel(known);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(3, 4);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] fabricLoaderStub(String fabricModId, File simJar) {
        String name = FABRIC_IMPL_STUB;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, name, null, "java/lang/Object", null);
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "INSTANCE", "L" + name + ";", null, null).visitEnd();

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, name);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, name, "<init>", "()V", false);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, name, "INSTANCE", "L" + name + ";");
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 0);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "isModLoaded", "(Ljava/lang/String;)Z", null, null);
        mv.visitCode();
        mv.visitLdcInsn(fabricModId);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getModContainer",
                "(Ljava/lang/String;)Ljava/util/Optional;", null, null);
        mv.visitCode();
        mv.visitLdcInsn(fabricModId);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label empty = new Label();
        mv.visitJumpInsn(Opcodes.IFEQ, empty);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Optional", "of",
                "(Ljava/lang/Object;)Ljava/util/Optional;", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitLabel(empty);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Optional", "empty",
                "()Ljava/util/Optional;", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getRootPath", "()Ljava/nio/file/Path;", null, null);
        mv.visitCode();
        mv.visitLdcInsn(simJar.getAbsolutePath());
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/nio/file/Path", "of",
                "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private Certificate[] signingChain() {
        if (!certBind || keystoreFile == null || !keystoreFile.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(keystoreFile)) {
            KeyStore ks = KeyStore.getInstance(keystoreFile.getName().endsWith(".p12") ? "PKCS12" : "JKS");
            ks.load(fis, keystorePassword);
            Certificate cert = ks.getCertificate(CERT_ALIAS);
            return cert == null ? null : new Certificate[]{cert};
        } catch (Exception e) {
            return null;
        }
    }

    private static String hex(byte[] data) {
        if (data == null) return "null";
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String parseModId(String json) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"id\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(json);
        return m.find() ? m.group(1) : "white";
    }

    private String randomFragment(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(randomChar());
        }
        return sb.toString();
    }

    private char randomChar() {
        String alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_$#@!%&*+-=<>?/|~";
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }

    private Set<String> memberNames(ClassReader cr) {
        Set<String> names = new HashSet<>();
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                                           String signature, Object value) {
                names.add(name);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                names.add(name);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return names;
    }

    private String uniqueName(Set<String> used) {
        while (true) {
            StringBuilder sb = new StringBuilder();
            int len = 6 + random.nextInt(6);
            for (int i = 0; i < len; i++) {
                sb.append(random.nextBoolean() ? 'I' : 'l');
            }
            String candidate = sb.toString();
            if (used.add(candidate)) return candidate;
        }
    }

    private void sanityCheck(byte[] classBytes) {
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
        }, ClassReader.SKIP_DEBUG);
    }

    private static byte[] deflate(byte[] input) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream output = new ByteArrayOutputStream(input.length);
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            if (count == 0) break;
            output.write(buffer, 0, count);
        }
        deflater.end();
        return output.toByteArray();
    }

    private static byte[] inflate(byte[] input) throws Exception {
        java.util.zip.Inflater inflater = new java.util.zip.Inflater();
        inflater.setInput(input);
        ByteArrayOutputStream output = new ByteArrayOutputStream(input.length * 2);
        byte[] buffer = new byte[4096];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            if (count == 0) {
                if (inflater.needsInput()) throw new java.util.zip.DataFormatException("truncated stream");
                break;
            }
            output.write(buffer, 0, count);
        }
        inflater.end();
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

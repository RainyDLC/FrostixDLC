package fun.newrar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class RainyDlcLoader {

    private static final String CORE_RES = "/assets/rainydlc/core.bin";
    private static final String MANIFEST_RES = "/assets/rainydlc/manifest.txt";
    private static final int MAGIC = 0x4E495843;
    private static final int VERSION = 4;
    private static final int HEADER = 4 + 2 + 16 + 12 + 32;

    private static final String KNOT_CLASS = "net.fabricmc.loader.impl.launch.knot.KnotClassLoader";
    private static final String MOD_ID = "white";

    private static final String[] FABRIC_SINGLETONS = {
            "net.fabricmc.loader.impl.FabricLoaderImpl",
            "net.fabricmc.loader.api.FabricLoader",
            "net.fabricmc.loader.impl.FabricLoader",
    };

    private static final String[] SUSPECT_CLASSES = {
            "net.bytebuddy.agent.Installer",
            "net.bytebuddy.agent.ByteBuddyAgent",
            "net.bytebuddy.dynamic.loading.ClassInjector",
            "javassist.ClassPool",
            "org.hotswap.agent.HotswapAgent",
            "me.coley.recaf.agent.Agent",
            "io.github.nickacpt.kraken.KrakenAgent",
            "sun.instrument.InstrumentationImpl",
            "org.objectweb.asm.ClassReader"
    };

    private static volatile boolean loaded = false;
    private static volatile byte[] integritySnapshot = null;
    private static volatile boolean poisoned = false;
    private static volatile long watchdogPulse = System.currentTimeMillis();

    private RainyDlcLoader() {
    }

    public static boolean encryptedBuild() {
        return RainyDlcLoader.class.getResourceAsStream(CORE_RES) != null;
    }

    public static void pulseCheck() {
        if (!loaded) return;
        if (isNativeDebuggerPresent() || isCompromised()) {
            poison();
        }
    }

    public static synchronized void loadClasses() {
        if (loaded) return;
        if (!encryptedBuild()) return;

        byte[] raw = null;
        byte[] salt = new byte[16];
        byte[] iv = new byte[12];
        byte[] tag = new byte[32];
        byte[] cipherText = null;
        byte[] keyBytes = null;
        byte[] payload = null;

        try {
            System.setProperty("jdk.attach.allowAttachSelf", "false");
            System.setProperty("disable.attach.mechanism", "true");
            if (isCompromised() || knotTampered()) {
                poison();
            }

            raw = readResource(CORE_RES);
            if (raw.length < HEADER + 16) {
                throw new IllegalStateException("Corrupted core container");
            }

            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(raw));
            int magic = dis.readInt();
            int version = dis.readShort();
            if (magic != MAGIC || version != VERSION) {
                throw new IllegalStateException("Invalid core signature");
            }

            dis.readFully(salt);
            dis.readFully(iv);
            dis.readFully(tag);
            cipherText = new byte[raw.length - HEADER];
            dis.readFully(cipherText);

            byte[] integrity = integrityDigest();
            integritySnapshot = integrity.clone();

            long t0 = System.nanoTime();
            keyBytes = synthesizeKey(integrity, salt);
            long dt = System.nanoTime() - t0;
            if (dt > 300_000_000L || dt < 0L) {
                poison();
            }

            byte[] expectedTag = computeTag(keyBytes, salt, iv, cipherText);
            if (!MessageDigest.isEqual(tag, expectedTag)) {
                poison();
            }

            startWatchdog();

            byte[] payloadPacked = chacha20(keyBytes, iv, cipherText, 1);
            payload = inflate(payloadPacked);
            Arrays.fill(payloadPacked, (byte) 0);

            DataInputStream payloadStream = new DataInputStream(new ByteArrayInputStream(payload));
            int count = payloadStream.readInt();

            ClassLoader knot = RainyDlcLoader.class.getClassLoader();
            String[] classNames = new String[count];
            byte[][] classBytesArr = new byte[count][];
            for (int i = 0; i < count; i++) {
                classNames[i] = payloadStream.readUTF();
                int len = payloadStream.readInt();
                classBytesArr[i] = payloadStream.readNBytes(len);
            }

            boolean[] defined = new boolean[count];
            int definedCount = 0;
            int passes = 0;
            while (definedCount < count && passes < 16) {
                passes++;
                int prevDefined = definedCount;
                for (int i = 0; i < count; i++) {
                    if (defined[i]) continue;
                    try {
                        defineInClassLoader(knot, classNames[i], classBytesArr[i]);
                        defined[i] = true;
                        definedCount++;
                        Arrays.fill(classBytesArr[i], (byte) 0);
                        classBytesArr[i] = null;
                        classNames[i] = null;
                    } catch (Throwable ignored) {
                    }
                }
                if (definedCount == prevDefined) {
                    break;
                }
            }

            for (int i = 0; i < count; i++) {
                if (classBytesArr[i] != null) {
                    Arrays.fill(classBytesArr[i], (byte) 0);
                    classBytesArr[i] = null;
                }
                classNames[i] = null;
            }

            if (definedCount < count) {
                throw new IllegalStateException("Initialization failure: core definition incomplete");
            }

            loaded = true;
        } catch (Throwable t) {
            throw new IllegalStateException("Initialization failure", t);
        } finally {
            if (keyBytes != null) Arrays.fill(keyBytes, (byte) 0);
            if (cipherText != null) Arrays.fill(cipherText, (byte) 0);
            if (payload != null) Arrays.fill(payload, (byte) 0);
            if (raw != null) Arrays.fill(raw, (byte) 0);
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(iv, (byte) 0);
            Arrays.fill(tag, (byte) 0);
        }
    }

    public static void bootstrap(fun.newrar.Client client) {
        try {
            if (!encryptedBuild()) {
                launchPlain(client);
            } else {
                loadClasses();
                Class<?> main = Class.forName("fun.newrar.core.ClientMain", true, RainyDlcLoader.class.getClassLoader());
                main.getMethod("init", fun.newrar.Client.class).invoke(null, client);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to launch client", t);
        }
    }

    private static void launchPlain(fun.newrar.Client client) throws Exception {
        Class<?> main = Class.forName("fun.newrar.core.ClientMain");
        main.getMethod("init", fun.newrar.Client.class).invoke(null, client);
    }

    private static byte[] synthesizeKey(byte[] integrity, byte[] salt) {
        return new byte[32];
    }

    private static byte[] getCertificateSha() {
        try {
            CodeSource cs = RainyDlcLoader.class.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getCertificates() != null && cs.getCertificates().length > 0) {
                return MessageDigest.getInstance("SHA-256").digest(cs.getCertificates()[0].getEncoded());
            }
        } catch (Throwable ignored) {
        }
        try {
            byte[] rsaBytes = readResource("/META-INF/RAINYDLC.RSA");
            if (rsaBytes != null && rsaBytes.length > 0) {
                java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
                java.util.Collection<? extends java.security.cert.Certificate> certs = cf.generateCertificates(new ByteArrayInputStream(rsaBytes));
                for (java.security.cert.Certificate c : certs) {
                    return MessageDigest.getInstance("SHA-256").digest(c.getEncoded());
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static byte[] internalEntropy() {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            ClassLoader cl = RainyDlcLoader.class.getClassLoader();
            if (cl != null && cl.getClass().getName().contains("Knot")) {
                sha.update(KNOT_CLASS.getBytes(StandardCharsets.UTF_8));
                sha.update((byte) 0xAA);
                sha.update(KNOT_CLASS.getBytes(StandardCharsets.UTF_8));
            } else {
                sha.update((byte) 0xEE);
            }

            sha.update((byte) 0x55);
            sha.update(MOD_ID.getBytes(StandardCharsets.UTF_8));

            if (fabricModLoaded()) {
                sha.update((byte) 0x33);
                sha.update(MOD_ID.getBytes(StandardCharsets.UTF_8));
            } else {
                sha.update((byte) 0x99);
            }

            if (validCaller()) {
                sha.update((byte) 0x77);
            } else {
                sha.update((byte) 0x44);
            }

            byte[] certSha = getCertificateSha();
            if (certSha != null) {
                sha.update((byte) 0xCC);
                sha.update(certSha);
            }

            return sha.digest();
        } catch (Throwable t) {
            return new byte[32];
        }
    }

    private static boolean validCaller() {
        try {
            return StackWalker.getInstance().walk(s -> {
                List<StackWalker.StackFrame> frames = s.limit(16).toList();

                for (StackWalker.StackFrame f : frames) {
                    if (f.getClassName().equals("fun.newrar.RainyDlcLoader") && f.getMethodName().equals("$$probe")) {
                        return true;
                    }
                    String cn = f.getClassName();
                    if (cn.contains("Audit")
                            || cn.contains("Crack")
                            || cn.contains("Harness")
                            || cn.contains("ByteBuddy")
                            || cn.contains("Recaf")) {
                        return false;
                    }
                }

                int idx = 0;
                while (idx < frames.size() && frames.get(idx).getClassName().equals("fun.newrar.RainyDlcLoader")) {
                    idx++;
                }
                if (idx >= frames.size()) return false;

                StackWalker.StackFrame directCaller = frames.get(idx);
                String directClass = directCaller.getClassName();
                if (directClass.contains("reflect.Method")
                        || directClass.contains("MethodAccessor")
                        || directClass.contains("DelegatingMethodAccessorImpl")) {
                    return false;
                }

                if (!directClass.equals("fun.newrar.RainyDlcPreLaunch") && !directClass.equals("fun.newrar.Client")) {
                    return false;
                }

                if (idx + 1 < frames.size()) {
                    String parentClass = frames.get(idx + 1).getClassName();
                    if (!parentClass.startsWith("net.fabricmc.loader.") && !parentClass.startsWith("fun.newrar.")) {
                        return false;
                    }
                }

                return true;
            });
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean fabricModLoaded() {
        for (String singleton : FABRIC_SINGLETONS) {
            if (modVisibleTo(singleton)) return true;
        }
        return false;
    }

    private static boolean modVisibleTo(String className) {
        try {
            Class<?> type = Class.forName(className, false, RainyDlcLoader.class.getClassLoader());
            Object loader = singleton(type);
            if (loader == null) return false;
            return Boolean.TRUE.equals(invoke(loader, "isModLoaded", new Class<?>[]{String.class}, MOD_ID));
        } catch (Throwable t) {
            return false;
        }
    }

    private static Object singleton(Class<?> type) throws Exception {
        try {
            Field f = type.getDeclaredField("INSTANCE");
            f.setAccessible(true);
            return f.get(null);
        } catch (NoSuchMethodError | NoSuchFieldException e) {
            Method m = type.getMethod("getInstance");
            return m.invoke(null);
        }
    }

    private static Object invoke(Object target, String name, Class<?>[] signature, Object... args) throws Exception {
        Method m = target.getClass().getMethod(name, signature == null ? new Class<?>[0] : signature);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static boolean loadedByKnot() {
        try {
            Class<?> knot = Class.forName(KNOT_CLASS, false, RainyDlcLoader.class.getClassLoader());
            return knot.isInstance(RainyDlcLoader.class.getClassLoader());
        } catch (Throwable t) {
            return false;
        }
    }

    private static byte[] integrityDigest() throws Exception {
        byte[] manifest = readResource(MANIFEST_RES);
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update(manifest);
        for (String name : new String(manifest, StandardCharsets.UTF_8).split("\n")) {
            name = name.trim();
            if (name.isEmpty()) continue;
            sha.update(name.getBytes(StandardCharsets.UTF_8));
            sha.update((byte) 0x1F);
            sha.update(readResource("/" + name));
        }
        return sha.digest();
    }

    private static void startWatchdog() {
        watchdogPulse = System.currentTimeMillis();
        Thread watchdog = new Thread(() -> {
            int strikes = 0;
            int tick = 0;
            while (true) {
                try {
                    Thread.sleep(1200L + (System.nanoTime() & 255L));
                    watchdogPulse = System.currentTimeMillis();
                    if (isCompromised() || isNativeDebuggerPresent() || knotTampered()) {
                        strikes++;
                    } else {
                        strikes = 0;
                    }
                    if (strikes >= 2) {
                        poison();
                        return;
                    }
                    if (++tick >= 10) {
                        tick = 0;
                        byte[] snapshot = integritySnapshot;
                        boolean ok;
                        try {
                            ok = snapshot != null && MessageDigest.isEqual(snapshot, integrityDigest());
                        } catch (Throwable t) {
                            ok = false;
                        }
                        if (!ok) {
                            poison();
                            return;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }, "Async-Channel-Service");
        watchdog.setDaemon(true);
        watchdog.setPriority(Thread.MIN_PRIORITY);
        watchdog.start();
    }

    private static boolean isNativeDebuggerPresent() {
        try {
            String os = System.getProperty("os.name", "");
            if (os.toLowerCase(Locale.ROOT).contains("win")) {
                Class<?> functionClass = Class.forName("com.sun.jna.Function");
                Method getFunction = functionClass.getMethod("getFunction", String.class, String.class);
                Object func = getFunction.invoke(null, "kernel32", "IsDebuggerPresent");
                Method invokeInt = functionClass.getMethod("invokeInt", Object[].class);
                int res = (int) invokeInt.invoke(func, (Object) new Object[0]);
                if (res != 0) return true;
                Object checkRemote = getFunction.invoke(null, "kernel32", "CheckRemoteDebuggerPresent");
                Object getCurrentProc = getFunction.invoke(null, "kernel32", "GetCurrentProcess");
                Object hProcess = getCurrentProc.getClass().getMethod("invokePointer", Object[].class).invoke(getCurrentProc, (Object) new Object[0]);
                Class<?> ptrClass = Class.forName("com.sun.jna.Memory");
                Object pbDebuggerPresent = ptrClass.getConstructor(long.class).newInstance(4L);
                Object[] args = new Object[]{hProcess, pbDebuggerPresent};
                invokeInt.invoke(checkRemote, (Object) args);
                Method getInt = ptrClass.getMethod("getInt", long.class);
                int remoteRes = (int) getInt.invoke(pbDebuggerPresent, 0L);
                if (remoteRes != 0) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean knotTampered() {
        try {
            ClassLoader cl = RainyDlcLoader.class.getClassLoader();
            if (cl == null) return false;
            String clName = cl.getClass().getName();
            if (!clName.equals(KNOT_CLASS)) return true;
            if (cl.getParent() == null) return true;

            for (Field f : cl.getClass().getDeclaredFields()) {
                String fn = f.getName().toLowerCase(Locale.ROOT);
                if (fn.contains("dump") || fn.contains("hook") || fn.contains("agent") || fn.contains("capture")) {
                    return true;
                }
            }

            for (Method m : cl.getClass().getDeclaredMethods()) {
                String mn = m.getName().toLowerCase(Locale.ROOT);
                if (mn.contains("dump") || mn.contains("saveclass") || mn.contains("writeclass")) {
                    return true;
                }
            }

            String[] dumperProps = {
                    "fabric.classDumper", "fabric.debug.dump", "mixin.debug.export",
                    "mixin.debug.dump", "dumpClasses", "asm.dump"
            };
            for (String prop : dumperProps) {
                if (System.getProperty(prop) != null) {
                    return true;
                }
            }

            InputStream in = cl.getResourceAsStream("net/fabricmc/loader/impl/launch/knot/KnotClassLoader.class");
            if (in == null) return false;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
            in.close();
            byte[] bytes = out.toByteArray();

            String[] suspects = {
                    "dump", "FileOutputStream", "java/nio/file/Files", "writeBytes", "ClassDumper", "DumpAgent", "ClassFileTransformer"
            };
            for (String suspect : suspects) {
                if (containsBytes(bytes, suspect.getBytes(StandardCharsets.UTF_8))) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean containsBytes(byte[] src, byte[] pat) {
        if (pat.length == 0 || src.length < pat.length) return false;
        outer:
        for (int i = 0; i <= src.length - pat.length; i++) {
            for (int j = 0; j < pat.length; j++) {
                if (src[i + j] != pat[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    private static boolean isCompromised() {
        try {
            if (isNativeDebuggerPresent()) {
                return true;
            }

            try {
                if (ManagementFactory.getClassLoadingMXBean().isVerbose()) {
                    return true;
                }
            } catch (Throwable ignored) {
            }

            for (String a : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                String low = a.toLowerCase(Locale.ROOT);
                if (low.startsWith("-agentlib:jdwp")
                        || low.startsWith("-agentpath:")
                        || low.startsWith("-xrunjdwp")
                        || low.startsWith("-xdebug")
                        || low.startsWith("-javaagent:")
                        || low.contains("jdwp")
                        || low.contains("arthas")
                        || low.contains("recaf")
                        || low.contains("byteman")
                        || low.contains("hotswap")
                        || low.contains("bytebuddy")
                        || low.contains("dumper")) {
                    return true;
                }
            }

            for (String var : new String[]{"JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"}) {
                String v = System.getenv(var);
                if (v != null) {
                    String low = v.toLowerCase(Locale.ROOT);
                    if (low.contains("jdwp")
                            || low.contains("arthas")
                            || low.contains("recaf")
                            || low.contains("byteman")
                            || low.contains("-agentlib:jdwp")
                            || low.contains("-xrunjdwp")
                            || low.contains("-javaagent:")) {
                        return true;
                    }
                }
            }

            for (String suspect : SUSPECT_CLASSES) {
                try {
                    Class.forName(suspect, false, RainyDlcLoader.class.getClassLoader());
                    return true;
                } catch (ClassNotFoundException ignored) {
                } catch (LinkageError ignored) {
                    return true;
                }
            }

            ThreadGroup root = Thread.currentThread().getThreadGroup();
            while (root.getParent() != null) {
                root = root.getParent();
            }
            Thread[] threads = new Thread[root.activeCount() + 16];
            int count = root.enumerate(threads, true);
            for (int i = 0; i < count; i++) {
                Thread t = threads[i];
                if (t == null) continue;
                String name = t.getName();
                if (name == null) continue;
                String low = name.toLowerCase(Locale.ROOT);
                if (low.contains("jdwp")
                        || low.contains("arthas")
                        || low.contains("jdi")
                        || low.contains("debugger")
                        || low.contains("bytebuddy")
                        || low.contains("attach listener")
                        || low.contains("agent")
                        || low.contains("dump")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void poison() {
        poisoned = true;
        try {
            Thread.sleep(40L + (System.nanoTime() & 63L));
        } catch (Throwable ignored) {
        }
        Runtime.getRuntime().halt(1);
    }

    private static void defineInClassLoader(ClassLoader loader, String name, byte[] bytes) throws Exception {
        CodeSource coreCs = RainyDlcLoader.class.getProtectionDomain().getCodeSource();
        try {
            Method m = loader.getClass().getMethod("defineClassFwd",
                    String.class, byte[].class, int.class, int.class, CodeSource.class);
            m.setAccessible(true);
            m.invoke(loader, name, bytes, 0, bytes.length, coreCs);
            return;
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof Exception e) throw e;
            if (cause instanceof Error err) throw err;
            throw ite;
        } catch (Throwable ignored) {
        }

        try {
            Method m = ClassLoader.class.getDeclaredMethod("defineClass",
                    String.class, byte[].class, int.class, int.class, ProtectionDomain.class);
            m.setAccessible(true);
            m.invoke(loader, name, bytes, 0, bytes.length, RainyDlcLoader.class.getProtectionDomain());
            return;
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof Exception e) throw e;
            if (cause instanceof Error err) throw err;
            throw ite;
        }
    }

    private static void qr(int[] x, int a, int b, int c, int d) {
        x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] ^ x[a], 16);
        x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] ^ x[c], 12);
        x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] ^ x[a], 8);
        x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] ^ x[c], 7);
    }

    private static byte[] chacha20(byte[] key, byte[] nonce, byte[] input, int counter) {
        int[] state = new int[16];
        state[0] = 0x61707865;
        state[1] = 0x3320646e;
        state[2] = 0x79622d32;
        state[3] = 0x6b206574;
        for (int i = 0; i < 8; i++) {
            state[4 + i] = (key[i * 4] & 0xFF)
                    | ((key[i * 4 + 1] & 0xFF) << 8)
                    | ((key[i * 4 + 2] & 0xFF) << 16)
                    | ((key[i * 4 + 3] & 0xFF) << 24);
        }
        state[12] = counter;
        for (int i = 0; i < 3; i++) {
            state[13 + i] = (nonce[i * 4] & 0xFF)
                    | ((nonce[i * 4 + 1] & 0xFF) << 8)
                    | ((nonce[i * 4 + 2] & 0xFF) << 16)
                    | ((nonce[i * 4 + 3] & 0xFF) << 24);
        }

        byte[] output = new byte[input.length];
        int[] working = new int[16];
        byte[] keyStream = new byte[64];

        int offset = 0;
        int currentCounter = counter;

        while (offset < input.length) {
            System.arraycopy(state, 0, working, 0, 16);
            working[12] = currentCounter;
            for (int i = 0; i < 10; i++) {
                qr(working, 0, 4, 8, 12);
                qr(working, 1, 5, 9, 13);
                qr(working, 2, 6, 10, 14);
                qr(working, 3, 7, 11, 15);
                qr(working, 0, 5, 10, 15);
                qr(working, 1, 6, 11, 12);
                qr(working, 2, 7, 8, 13);
                qr(working, 3, 4, 9, 14);
            }
            for (int i = 0; i < 16; i++) {
                int val = working[i] + (i == 12 ? currentCounter : state[i]);
                keyStream[i * 4] = (byte) (val & 0xFF);
                keyStream[i * 4 + 1] = (byte) ((val >>> 8) & 0xFF);
                keyStream[i * 4 + 2] = (byte) ((val >>> 16) & 0xFF);
                keyStream[i * 4 + 3] = (byte) ((val >>> 24) & 0xFF);
            }
            int blockSize = Math.min(64, input.length - offset);
            for (int i = 0; i < blockSize; i++) {
                output[offset + i] = (byte) (input[offset + i] ^ keyStream[i]);
            }
            offset += blockSize;
            currentCounter++;
        }
        return output;
    }

    private static byte[] computeTag(byte[] key, byte[] salt, byte[] iv, byte[] cipherText) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update(key);
        sha.update(salt);
        sha.update(iv);
        sha.update(cipherText);
        return sha.digest();
    }

    private static byte[] inflate(byte[] input) throws Exception {
        Inflater inflater = new Inflater();
        inflater.setInput(input);
        ByteArrayOutputStream output = new ByteArrayOutputStream(input.length * 2);
        byte[] buffer = new byte[4096];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            if (count == 0) {
                if (inflater.needsInput()) throw new DataFormatException("truncated stream");
                break;
            }
            output.write(buffer, 0, count);
        }
        inflater.end();
        return output.toByteArray();
    }

    private static byte[] readResource(String path) throws Exception {
        InputStream in = RainyDlcLoader.class.getResourceAsStream(path);
        if (in == null && path.startsWith("/")) {
            in = RainyDlcLoader.class.getResourceAsStream(path.substring(1));
        }
        if (in == null && RainyDlcLoader.class.getClassLoader() != null) {
            in = RainyDlcLoader.class.getClassLoader().getResourceAsStream(path.startsWith("/") ? path.substring(1) : path);
        }
        if (in == null) throw new IllegalStateException("Resource not found: " + path);
        try (InputStream stream = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = stream.read(buf)) >= 0) out.write(buf, 0, r);
            return out.toByteArray();
        }
    }
}

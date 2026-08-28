package ru.white;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Inflater;

public final class NightixLoader {
    private static final String CORE_RES = "/assets/nightix/core.bin";
    private static final int MAGIC = 0x4E495843;
    private static volatile boolean loaded = false;

    private NightixLoader() {
    }

    public static boolean encryptedBuild() {
        return NightixLoader.class.getResourceAsStream(CORE_RES) != null;
    }

    public static synchronized void loadClasses() {
        if (loaded) return;
        if (!encryptedBuild()) return;

        try {
            System.setProperty("jdk.attach.allowAttachSelf", "false");

            if (isCompromised()) {
                corruptMemory();
            }

            startWatchdog();

            byte[] raw = readResource(CORE_RES);
            if (raw.length < 34) {
                throw new IllegalStateException("Corrupted core container");
            }

            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(raw));
            int magic = dis.readInt();
            int version = dis.readShort();
            if (magic != MAGIC || version != 1) {
                throw new IllegalStateException("Invalid core signature");
            }

            byte[] salt = new byte[16];
            dis.readFully(salt);
            byte[] iv = new byte[12];
            dis.readFully(iv);

            int cipherLen = raw.length - 4 - 2 - 16 - 12;
            byte[] cipherText = new byte[cipherLen];
            dis.readFully(cipherText);

            byte[] keyBytes = deriveKey(salt);
            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] decompressedPayload = decompress(cipher.doFinal(cipherText));

            Arrays.fill(keyBytes, (byte) 0);
            Arrays.fill(cipherText, (byte) 0);

            DataInputStream payloadStream = new DataInputStream(new ByteArrayInputStream(decompressedPayload));
            int count = payloadStream.readInt();

            ClassLoader knot = NightixLoader.class.getClassLoader();
            for (int i = 0; i < count; i++) {
                String className = payloadStream.readUTF();
                int len = payloadStream.readInt();
                byte[] classBytes = payloadStream.readNBytes(len);

                defineInClassLoader(knot, className, classBytes);
                Arrays.fill(classBytes, (byte) 0);
            }

            Arrays.fill(decompressedPayload, (byte) 0);
            loaded = true;
        } catch (Throwable t) {
            throw new IllegalStateException("Initialization failure", t);
        }
    }

    public static void bootstrap(Client client) {
        try {
            if (!encryptedBuild()) {
                launchPlain(client);
            } else {
                loadClasses();
                Class<?> main = Class.forName("ru.white.core.ClientMain", true, NightixLoader.class.getClassLoader());
                main.getMethod("init", Client.class).invoke(null, client);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to launch client", t);
        }
    }

    private static void launchPlain(Client client) throws Exception {
        Class<?> main = Class.forName("ru.white.core.ClientMain");
        main.getMethod("init", Client.class).invoke(null, client);
    }

    private static void startWatchdog() {
        Thread watchdog = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(2500);
                    if (isCompromised()) {
                        Runtime.getRuntime().halt(0);
                    }
                } catch (Throwable ignored) {
                }
            }
        }, "Watchdog-Service");
        watchdog.setDaemon(true);
        watchdog.setPriority(Thread.MIN_PRIORITY);
        watchdog.start();
    }

    private static byte[] deriveKey(byte[] salt) throws Exception {
        byte[] seed = getSeed();
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update(seed);
        sha.update(salt);
        byte[] res = sha.digest();
        Arrays.fill(seed, (byte) 0);
        return res;
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

    private static boolean isCompromised() {
        try {
            List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String a : args) {
                String low = a.toLowerCase();
                if (low.contains("javaagent") || low.contains("agentlib")
                        || low.contains("agentpath") || low.contains("-xdebug")
                        || low.contains("jdwp") || low.contains("bytebuddy")
                        || low.contains("debugger") || low.contains("hotswap")) {
                    return true;
                }
            }

            ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
            while (rootGroup.getParent() != null) {
                rootGroup = rootGroup.getParent();
            }
            Thread[] threads = new Thread[rootGroup.activeCount() + 16];
            int count = rootGroup.enumerate(threads, true);
            for (int i = 0; i < count; i++) {
                Thread t = threads[i];
                if (t == null) continue;
                String name = t.getName().toLowerCase();
                if (name.contains("attach listener") || name.contains("arthas")
                        || name.contains("jdwp") || name.contains("agent-main")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void corruptMemory() {
        try {
            Thread.sleep(50);
        } catch (Throwable ignored) {
        }
    }

    private static byte[] decompress(byte[] input) throws Exception {
        Inflater inflater = new Inflater();
        inflater.setInput(input);
        ByteArrayOutputStream output = new ByteArrayOutputStream(input.length * 2);
        byte[] buffer = new byte[4096];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            output.write(buffer, 0, count);
        }
        inflater.end();
        return output.toByteArray();
    }

    private static void defineInClassLoader(ClassLoader loader, String name, byte[] bytes) {
        try {
            Method m = ClassLoader.class.getDeclaredMethod("defineClass",
                    String.class, byte[].class, int.class, int.class, ProtectionDomain.class);
            m.setAccessible(true);
            m.invoke(loader, name, bytes, 0, bytes.length, loader.getClass().getProtectionDomain());
            return;
        } catch (Throwable ignored) {
        }

        try {
            Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Object unsafe = f.get(null);
            Method m = unsafe.getClass().getMethod("defineClass",
                    String.class, byte[].class, int.class, int.class, ClassLoader.class, ProtectionDomain.class);
            m.invoke(unsafe, name, bytes, 0, bytes.length, loader, loader.getClass().getProtectionDomain());
            return;
        } catch (Throwable ignored) {
        }

        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(ClassLoader.class, MethodHandles.lookup());
            MethodHandle mh = lookup.findVirtual(ClassLoader.class, "defineClass",
                    MethodType.methodType(Class.class, String.class, byte[].class, int.class, int.class, ProtectionDomain.class));
            mh.invoke(loader, name, bytes, 0, bytes.length, loader.getClass().getProtectionDomain());
        } catch (Throwable ignored) {
        }
    }

    private static byte[] readResource(String path) throws Exception {
        try (InputStream in = NightixLoader.class.getResourceAsStream(path);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("Resource not found: " + path);
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) >= 0) out.write(buf, 0, r);
            return out.toByteArray();
        }
    }
}

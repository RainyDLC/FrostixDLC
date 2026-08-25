package ru.white;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Загрузчик зашифрованного ядра Nightix.
 * Ключ = SHA-256(секрет || SHA-256(index.bin)) — любое изменение зашифрованных
 * классов ломает ключ, клиент молча отказывает вместо честного сообщения.
 * Анти-инструментирование: javaagent/agentlib/jdwp в аргументах JVM дают
 * неверный ключ — тот же тихий отказ.
 */
public final class NightixLoader extends ClassLoader {

    private static final String IDX = "assets/nightix/enc/index.bin";
    private static final String ENC = "assets/nightix/enc/";
    private static final String MARKER = "NIXSEC:";
    private static final String SECRET = "NIXSEC:R4inyDLC9x2K7qWm5Tz3Vb8Nc4Jd6Hf";

    private NightixLoader() {
        super(NightixLoader.class.getClassLoader());
    }

    /** В защищённой сборке в JAR лежит index.bin — значит классы зашифрованы. */
    public static boolean encryptedBuild() {
        return NightixLoader.class.getResourceAsStream("/" + IDX) != null;
    }

    public static void bootstrap(Client client) {
        try {
            if (!encryptedBuild()) {
                launchPlain(client);
            } else {
                new NightixLoader().decryptAndLaunch(client);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("nightix: core integrity failure", t);
        }
    }

    private static void launchPlain(Client client) throws Exception {
        Class<?> main = Class.forName("ru.white.core.ClientMain");
        main.getMethod("init", Client.class).invoke(null, client);
    }

    private void decryptAndLaunch(Client client) throws Exception {
        byte[] index = readResource("/" + IDX);
        SecretKeySpec key = deriveKey(index);

        List<String> names = new ArrayList<>();
        for (String n : new String(index, StandardCharsets.UTF_8).split("\n")) {
            if (!n.isBlank()) names.add(n);
        }
        for (String name : names) {
            byte[] blob = readResource("/" + ENC + name.replace('.', '_') + ".enc");
            byte[] cls = decrypt(blob, key);
            defineClass(name, cls, 0, cls.length);
        }

        Class<?> main = Class.forName("ru.white.core.ClientMain", true, this);
        main.getMethod("init", Client.class).invoke(null, client);
    }

    private static SecretKeySpec deriveKey(byte[] index) throws Exception {
        byte[] s = secretBytes();
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] idxHash = sha.digest(index);
        sha.reset();
        byte[] material = new byte[s.length + idxHash.length];
        System.arraycopy(s, 0, material, 0, s.length);
        System.arraycopy(idxHash, 0, material, s.length, idxHash.length);
        return new SecretKeySpec(sha.digest(material), "AES");
    }

    private static byte[] secretBytes() {
        for (String a : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            String low = a.toLowerCase();
            if (low.contains("javaagent") || low.contains("agentlib")
                    || low.contains("-xdebug") || low.contains("jdwp")) {
                return new byte[]{0};
            }
        }
        return SECRET.substring(MARKER.length()).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] decrypt(byte[] blob, SecretKeySpec key) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, blob, 0, 12));
        return c.doFinal(blob, 12, blob.length - 12);
    }

    private static byte[] readResource(String path) throws Exception {
        try (InputStream in = NightixLoader.class.getResourceAsStream(path);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("missing " + path);
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) >= 0) out.write(buf, 0, r);
            return out.toByteArray();
        }
    }
}

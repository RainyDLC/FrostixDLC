package nightix;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Шифратор классов Nightix.
 *
 * 1. Сид-набор: entrypoint (ru/white/Client), NightixLoader, всё под
 *    ru/white/mixin/ и entrypoints из fabric.mod.json — эти классы Fabric
 *    обязан читать сам, они остаются открытыми.
 * 2. Транзитивное замыкание: из константных пулов сидов вытаскиваются все
 *    ссылки Lru/white/...; и dotted-строки ru.white... — замкнутое множество
 *    остаётся открытым (Knot должен уметь грузить их напрямую).
 * 3. Всё остальное под ru/white/** шифруется AES/GCM. Ключ =
 *    SHA-256(секрет || SHA-256(index.bin)): изменение любого байта шифроблока
 *    или индекса делает расшифровку невозможной. Секрет извлекается из
 *    константного пула NightixLoader (маркер NIXSEC:) — единственный источник.
 */
public final class ClassEncryptor {

    private static final String IDX_PATH = "assets/nightix/enc/index.bin";
    private static final String ENC_DIR = "assets/nightix/enc/";
    private static final byte[] MARKER = "NIXSEC:".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern L_FORM = Pattern.compile("L(ru/white/[\\w$/]+);");
    private static final Pattern DOT_FORM = Pattern.compile("(ru\\.white(?:\\.\\w+)+)");

    private final SecureRandom random = new SecureRandom();

    public void run(File inJar, File outJar) {
        try {
            execute(inJar, outJar);
        } catch (Exception e) {
            throw new RuntimeException("nightix protect failed", e);
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

        byte[] loaderClass = entries.get("ru/white/NightixLoader.class");
        if (loaderClass == null) throw new IllegalStateException("NightixLoader not found in jar");
        String secret = extractSecret(loaderClass);

        Set<String> plain = closure(entries);

        List<String> toEncrypt = new ArrayList<>();
        for (String path : order) {
            if (!path.startsWith("ru/white/") || !path.endsWith(".class")) continue;
            if (plain.contains(path)) continue;
            toEncrypt.add(path);
        }

        byte[] index = String.join("\n", toEncrypt).getBytes(StandardCharsets.UTF_8);
        SecretKeySpec key = deriveKey(secret, index);

        Map<String, byte[]> blobs = new LinkedHashMap<>();
        for (String path : toEncrypt) {
            String className = path.substring(0, path.length() - ".class".length()).replace('/', '.');
            blobs.put(ENC_DIR + className.replace('.', '_') + ".enc", encrypt(entries.get(path), key));
        }

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(outJar))) {
            for (String path : order) {
                if (toEncrypt.contains(path)) continue; // исходный .class удалён
                writeEntry(zip, path, entries.get(path));
            }
            for (Map.Entry<String, byte[]> b : blobs.entrySet()) {
                writeEntry(zip, b.getKey(), b.getValue());
            }
            writeEntry(zip, IDX_PATH, index);
        }

        System.out.println("[nightix] encrypted classes : " + toEncrypt.size());
        System.out.println("[nightix] plaintext classes : " + plain.size());
        System.out.println("[nightix] protected jar     : " + outJar.getAbsolutePath());
    }

    /** Сид-набор + транзитивное замыкание по константным пулам. */
    private Set<String> closure(Map<String, byte[]> entries) {
        Set<String> plain = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();

        for (String path : entries.keySet()) {
            if (path.startsWith("ru/white/mixin/") && path.endsWith(".class")) {
                queue.add(path);
            }
        }
        enqueueIfExists(queue, entries, "ru/white/Client.class");
        enqueueIfExists(queue, entries, "ru/white/NightixLoader.class");

        // entrypoints из fabric.mod.json (client-секция)
        byte[] fmj = entries.get("fabric.mod.json");
        if (fmj != null) {
            String json = new String(fmj, StandardCharsets.UTF_8);
            Matcher m = DOT_FORM.matcher(json);
            while (m.find()) {
                enqueueIfExists(queue, entries, m.group(1).replace('.', '/') + ".class");
            }
        }

        while (!queue.isEmpty()) {
            String path = queue.poll();
            if (!plain.add(path)) continue;
            byte[] bytes = entries.get(path);
            if (bytes == null) continue;

            for (String ref : scanReferences(bytes)) {
                String refPath = ref.replace('.', '/') + ".class";
                if (entries.containsKey(refPath) && !plain.contains(refPath)) {
                    queue.add(refPath);
                }
            }
        }
        return plain;
    }

    /** Все ru.white-имена из константного пула класса (L-форма и dotted-строки). */
    private List<String> scanReferences(byte[] bytes) {
        List<String> out = new ArrayList<>();
        Matcher l = L_FORM.matcher(new String(bytes, StandardCharsets.ISO_8859_1));
        while (l.find()) out.add(l.group(1).replace('/', '.'));
        Matcher d = DOT_FORM.matcher(new String(bytes, StandardCharsets.ISO_8859_1));
        while (d.find()) out.add(d.group(1));
        return out;
    }

    /** Секрет — в константном пуле NightixLoader после маркера NIXSEC:. */
    private String extractSecret(byte[] loaderClass) {
        for (int i = 0; i <= loaderClass.length - MARKER.length; i++) {
            boolean found = true;
            for (int j = 0; j < MARKER.length; j++) {
                if (loaderClass[i + j] != MARKER[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                StringBuilder sb = new StringBuilder();
                for (int k = i + MARKER.length; k < loaderClass.length; k++) {
                    char c = (char) (loaderClass[k] & 0xFF);
                    if (Character.isLetterOrDigit(c)) sb.append(c);
                    else break;
                }
                if (sb.length() >= 16) return sb.toString();
            }
        }
        throw new IllegalStateException("NIXSEC secret not found in NightixLoader");
    }

    private static SecretKeySpec deriveKey(String secret, byte[] index) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] idxHash = sha.digest(index);
        sha.reset();
        byte[] s = secret.getBytes(StandardCharsets.UTF_8);
        byte[] material = new byte[s.length + idxHash.length];
        System.arraycopy(s, 0, material, 0, s.length);
        System.arraycopy(idxHash, 0, material, s.length, idxHash.length);
        return new SecretKeySpec(sha.digest(material), "AES");
    }

    private byte[] encrypt(byte[] data, SecretKeySpec key) throws Exception {
        byte[] iv = new byte[12];
        random.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(data);
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return out;
    }

    private static void enqueueIfExists(Deque<String> queue, Map<String, byte[]> entries, String path) {
        if (entries.containsKey(path)) queue.add(path);
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

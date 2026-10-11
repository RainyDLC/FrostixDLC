package dev.hatek.client.module.impl.combat.aura.neuro;

import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Запуск обучения нейро-ротаций через локальный Python.
 *
 * <p>Скрипт {@code train_neuro.py} встроен в клиент и распаковывается в
 * {@code config/frostix/neuroaura/}. Обучение выполняется отдельным
 * процессом {@code python} на твоём ПК: читает локальный датасет,
 * пишет локальные веса. В сеть ничего не уходит, в процесс игры
 * ничего не инжектится — банить тут не за что.
 */
public final class NeuroTrainer {
    private static final Pattern EPOCH_LINE = Pattern.compile(
            "epoch\\s+(\\d+)/(\\d+)\\s+train=([0-9.]+)\\s+val=([^\\s]+)");

    public interface Listener {
        void onProgress(double progress, String status);

        void onLog(String line);

        void onDone(boolean ok, String message);
    }

    private NeuroTrainer() {
    }

    /** Запустить обучение в фоновом потоке. */
    public static void start(String profileName, Listener listener) {
        Thread thread = new Thread(() -> run(profileName, listener), "neuro-train");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run(String profileName, Listener listener) {
        try {
            Path script = extractScript();
            if (script == null) {
                listener.onDone(false, "Скрипт train_neuro.py не найден в клиенте");
                return;
            }
            Path dataset = NeuroStore.datasetPath(profileName);
            if (!Files.isRegularFile(dataset)) {
                listener.onDone(false, "Датасет не найден: " + dataset.getFileName());
                return;
            }
            String python = findPython();
            if (python == null) {
                listener.onDone(false,
                        "Python не найден. Установи Python 3 и numpy: pip install numpy");
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(python,
                    script.toAbsolutePath().toString(),
                    dataset.toAbsolutePath().toString());
            pb.directory(Minecraft.getInstance().gameDirectory);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String lastLine = "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    listener.onLog(line);
                    Matcher m = EPOCH_LINE.matcher(line);
                    if (m.find()) {
                        try {
                            int epoch = Integer.parseInt(m.group(1));
                            int total = Integer.parseInt(m.group(2));
                            listener.onProgress((double) epoch / total,
                                    "Эпоха " + epoch + "/" + total
                                            + " · train " + m.group(3)
                                            + " · val " + m.group(4));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    if (line.length() < 300) {
                        lastLine = line;
                    }
                }
            }
            int exit = process.waitFor();

            Path weights = NeuroStore.weightsPath(profileName);
            if (exit == 0 && Files.isRegularFile(weights)) {
                listener.onDone(true, "Готово ✓ «" + profileName + "»");
            } else {
                listener.onDone(false, "Ошибка Python (код " + exit + "): " + lastLine);
            }
        } catch (Exception e) {
            listener.onDone(false, "Ошибка запуска: " + e.getMessage());
        }
    }

    /** Распаковать встроенный скрипт рядом с датасетами. */
    private static Path extractScript() {
        try (InputStream in = NeuroTrainer.class.getResourceAsStream("/neuro/train_neuro.py")) {
            if (in == null) {
                return null;
            }
            Path script = NeuroStore.scriptPath();
            Files.createDirectories(script.getParent());
            Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
            return script;
        } catch (Exception e) {
            return null;
        }
    }

    /** Найти интерпретатор Python в PATH или в стандартном месте per-user установки. */
    public static String findPython() {
        // Стандартное место тихой per-user установки (NeuroDeps ставит сюда)
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            Path known = Path.of(localAppData, "Programs", "Python", "Python312", "python.exe");
            if (Files.isRegularFile(known) && probe(known.toAbsolutePath().toString())) {
                return known.toAbsolutePath().toString();
            }
        }
        for (String cmd : new String[]{"python", "python3", "py"}) {
            if (probe(cmd)) {
                return cmd;
            }
        }
        return null;
    }

    private static boolean probe(String cmd) {
        try {
            Process probe = new ProcessBuilder(cmd, "--version")
                    .redirectErrorStream(true)
                    .start();
            return probe.waitFor(8, TimeUnit.SECONDS) && probe.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }
}

package dev.hatek.client.module.impl.combat.aura.neuro;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Автоустановка зависимостей для обучения нейро-ротаций.
 *
 * <p>Кнопка «Скачать зависимости» в панели обучения:
 * <ol>
 *   <li>ищет Python (PATH + стандартное место per-user установки);</li>
 *   <li>если нет — качает официальный установщик с python.org
 *       и ставит тихо, только для текущего пользователя
 *       (прав админа не нужно);</li>
 *   <li>проверяет numpy, если нет — ставит через {@code pip install numpy}.</li>
 * </ol>
 * Всё выполняется в фоновом потоке, прогресс виден в панели.
 */
public final class NeuroDeps {
    /** Официальный установщик Python (python.org, 64-bit). */
    private static final String PYTHON_URL =
            "https://www.python.org/ftp/python/3.12.7/python-3.12.7-amd64.exe";

    public static volatile boolean busy;
    public static volatile String status = "";

    private NeuroDeps() {
    }

    /** Запустить проверку/установку в фоне. Повторные вызовы игнорируются. */
    public static void ensureAsync() {
        if (busy) {
            return;
        }
        Thread thread = new Thread(NeuroDeps::run, "neuro-deps");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        busy = true;
        try {
            if (!isWindows()) {
                status = "Автоустановка только для Windows. Скачай вручную: python.org";
                return;
            }

            status = "Ищу Python…";
            String python = NeuroTrainer.findPython();
            if (python == null) {
                python = installPython();
                if (python == null) {
                    return; // status уже выставлен
                }
            }

            status = "Проверяю numpy…";
            if (!hasNumpy(python)) {
                status = "Ставлю numpy через pip…";
                if (!pipInstallNumpy(python)) {
                    return; // status уже выставлен
                }
                if (!hasNumpy(python)) {
                    status = "numpy не встал. Попробуй вручную: pip install numpy";
                    return;
                }
            }
            status = "Готово ✓ Python + numpy установлены";
        } finally {
            busy = false;
        }
    }

    /** Скачать и тихо установить Python. Возвращает путь к python.exe или null. */
    private static String installPython() {
        Path installer = NeuroStore.dir().resolve("python-setup.exe");
        try {
            status = "Скачиваю Python 3.12 с python.org…";
            download(PYTHON_URL, installer);
        } catch (Exception e) {
            status = "Не скачалось: " + shortMsg(e);
            return null;
        }

        try {
            status = "Устанавливаю Python (тихо, без прав админа)…";
            Process process = new ProcessBuilder(
                    installer.toAbsolutePath().toString(),
                    "/quiet",
                    "InstallAllUsers=0",
                    "PrependPath=1",
                    "Include_pip=1",
                    "Include_test=0",
                    "Include_doc=0",
                    "Include_dev=0",
                    "Include_debug=0").start();
            if (!process.waitFor(15, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                status = "Установка зависла (таймаут 15 мин)";
                return null;
            }
            if (process.exitValue() != 0) {
                status = "Установщик вернул код " + process.exitValue();
                return null;
            }
        } catch (Exception e) {
            status = "Ошибка установки: " + shortMsg(e);
            return null;
        } finally {
            try {
                Files.deleteIfExists(installer);
            } catch (IOException ignored) {
            }
        }

        String python = NeuroTrainer.findPython();
        if (python == null) {
            status = "Python поставился, но не найден. Перезапусти игру.";
            return null;
        }
        return python;
    }

    private static boolean hasNumpy(String python) {
        try {
            Process process = new ProcessBuilder(python, "-c", "import numpy")
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean pipInstallNumpy(String python) {
        try {
            ProcessBuilder pb = new ProcessBuilder(python, "-m", "pip", "install",
                    "--disable-pip-version-check", "numpy");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            // Стримим последнюю строку в статус, чтобы было видно прогресс
            byte[] buf = new byte[4096];
            StringBuilder tail = new StringBuilder();
            try (InputStream in = process.getInputStream()) {
                int n;
                while ((n = in.read(buf)) != -1) {
                    tail.append(new String(buf, 0, n));
                    int nl = tail.lastIndexOf("\n");
                    if (nl >= 0) {
                        String last = tail.substring(tail.lastIndexOf("\n",
                                Math.max(0, nl - 1)) + 1, nl).trim();
                        if (!last.isEmpty() && last.length() < 120) {
                            status = "pip: " + last;
                        }
                        tail.delete(0, nl + 1);
                    }
                }
            }
            if (!process.waitFor(15, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                status = "pip завис (таймаут 15 мин)";
                return false;
            }
            if (process.exitValue() != 0) {
                status = "pip вернул код " + process.exitValue();
                return false;
            }
            return true;
        } catch (Exception e) {
            status = "Ошибка pip: " + shortMsg(e);
            return false;
        }
    }

    private static void download(String url, Path dest) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "FrostixDLC")
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode());
        }
        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        Files.createDirectories(dest.getParent());
        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(dest)) {
            byte[] buf = new byte[65536];
            long done = 0L;
            long lastUpdate = 0L;
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                done += n;
                long now = System.currentTimeMillis();
                if (now - lastUpdate > 500) {
                    lastUpdate = now;
                    if (total > 0) {
                        status = String.format(Locale.ROOT,
                                "Скачиваю Python: %.1f / %.1f МБ",
                                done / 1048576.0, total / 1048576.0);
                    } else {
                        status = String.format(Locale.ROOT, "Скачиваю Python: %.1f МБ",
                                done / 1048576.0);
                    }
                }
            }
        }
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    private static String shortMsg(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) {
            return e.getClass().getSimpleName();
        }
        return m.length() > 120 ? m.substring(0, 120) : m;
    }
}

package rtx.kimiko.api.combat.neuro;

import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class NeuroTrainerRuntime {
    private static volatile boolean setupStarted = false;
    private static volatile boolean training = false;
    private static volatile Process activeProcess = null;
    private static volatile String statusMessage = "Idle";

    private NeuroTrainerRuntime() {
    }

    public static boolean isBusy() {
        return setupStarted || training;
    }

    public static boolean isTraining() {
        return training;
    }

    public static String getStatusMessage() {
        return statusMessage;
    }

    public static File findPython() {
        // 1. Check common system paths
        String[] candidates = {
                "C:\\Program Files\\Python311\\python.exe",
                "C:\\Program Files\\Python312\\python.exe",
                "C:\\Program Files\\Python310\\python.exe",
                System.getProperty("user.home") + "\\AppData\\Local\\Programs\\Python\\Python311\\python.exe",
                System.getProperty("user.home") + "\\AppData\\Local\\Programs\\Python\\Python312\\python.exe"
        };
        for (String c : candidates) {
            File f = new File(c);
            if (f.isFile() && f.canExecute()) {
                return f;
            }
        }

        // 2. Check PATH via 'where python'
        try {
            Process p = new ProcessBuilder("where.exe", "python").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    File f = new File(line.trim());
                    // Skip Windows Store alias if it's 0 bytes or restricted
                    if (f.isFile() && f.length() > 0 && !line.contains("WindowsApps")) {
                        return f;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // 3. Fallback to "python"
        return new File("python");
    }

    public static boolean isPackageInstalled(File python, String packageName) {
        try {
            Process p = new ProcessBuilder(python.getAbsolutePath(), "-c", "import " + packageName).start();
            return p.waitFor() == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isReady() {
        File py = findPython();
        return isPackageInstalled(py, "torch") && isPackageInstalled(py, "numpy");
    }

    public static void setupAsync(Consumer<String> logger) {
        if (setupStarted) {
            logger.accept("Setup is already running.");
            return;
        }
        setupStarted = true;
        statusMessage = "Installing dependencies...";

        Thread thread = new Thread(() -> {
            try {
                File py = findPython();
                logger.accept("Found Python: " + py.getAbsolutePath());

                if (isReady()) {
                    logger.accept("PyTorch and NumPy are already installed and ready!");
                    statusMessage = "Ready";
                    setupStarted = false;
                    return;
                }

                logger.accept("Installing torch and numpy via pip... (this might take 1-3 minutes)");
                ProcessBuilder pb = new ProcessBuilder(py.getAbsolutePath(), "-m", "pip", "install", "torch", "numpy");
                pb.redirectErrorStream(true);
                Process proc = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("Successfully installed") || line.contains("Requirement already satisfied")) {
                            logger.accept(line);
                        }
                    }
                }

                int code = proc.waitFor();
                if (code == 0) {
                    logger.accept("Setup completed successfully! Environment is ready for training.");
                    statusMessage = "Ready";
                } else {
                    logger.accept("Pip exited with code " + code + ". Please run 'pip install torch numpy' manually in terminal.");
                    statusMessage = "Setup failed";
                }
            } catch (Throwable t) {
                logger.accept("Setup error: " + t.getMessage());
                statusMessage = "Setup error";
            } finally {
                setupStarted = false;
            }
        }, "Kimiko-Neuro-Setup");
        thread.setDaemon(true);
        thread.start();
    }

    public static Path extractTrainerScript() {
        Path trainerDir = NeuroModel.getTrainerDir();
        try {
            Files.createDirectories(trainerDir);
            Path target = trainerDir.resolve("train_aura.py");

            InputStream is = NeuroTrainerRuntime.class.getClassLoader().getResourceAsStream("assets/kimiko/neuro/trainer/train_aura.py");
            if (is != null) {
                try (is) {
                    Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return target;
        } catch (Throwable t) {
            return trainerDir.resolve("train_aura.py");
        }
    }

    public static synchronized boolean startTraining(Path datasetPath, Path outputModelPath, int epochs, String modelName, Consumer<String> logger) {
        if (training) {
            logger.accept("A training session is already running.");
            return false;
        }

        File py = findPython();
        if (!isReady()) {
            logger.accept("Python dependencies (torch, numpy) not found. Run '.neuro train setup' first!");
            return false;
        }

        Path script = extractTrainerScript();
        if (!Files.isRegularFile(script)) {
            logger.accept("Trainer script missing at: " + script);
            return false;
        }

        training = true;
        statusMessage = "Training " + modelName + " (0/" + epochs + ")";

        Thread thread = new Thread(() -> {
            try {
                List<String> cmd = new ArrayList<>();
                cmd.add(py.getAbsolutePath());
                cmd.add("-u");
                cmd.add(script.toAbsolutePath().toString());
                cmd.add("--data");
                cmd.add(datasetPath.toAbsolutePath().toString());
                cmd.add("--out");
                cmd.add(outputModelPath.toAbsolutePath().toString());
                cmd.add("--epochs");
                cmd.add(String.valueOf(epochs));
                cmd.add("--session");
                cmd.add(modelName);

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                pb.environment().put("PYTHONIOENCODING", "utf-8");
                pb.environment().put("PYTHONDONTWRITEBYTECODE", "1");

                logger.accept("Started training process: " + modelName + " (" + epochs + " epochs)");
                activeProcess = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("[Trainer] Epoch") || trimmed.startsWith("[Trainer] Model weights") || trimmed.startsWith("[Trainer] Loaded")) {
                            logger.accept(trimmed);
                            if (trimmed.startsWith("[Trainer] Epoch")) {
                                statusMessage = trimmed.replace("[Trainer] ", "");
                            }
                        }
                    }
                }

                int exitCode = activeProcess.waitFor();
                if (exitCode == 0) {
                    logger.accept("Training of model '" + modelName + "' finished successfully!");
                    logger.accept("Model saved to: " + outputModelPath.getFileName());

                    // Auto switch to newly trained model
                    NeuroModel.setActive(modelName);
                    logger.accept("Activated model profile: " + modelName);
                    statusMessage = "Trained: " + modelName;
                } else {
                    logger.accept("Training failed with exit code: " + exitCode);
                    statusMessage = "Training failed";
                }
            } catch (Throwable t) {
                logger.accept("Training exception: " + t.getMessage());
                statusMessage = "Error: " + t.getMessage();
            } finally {
                training = false;
                activeProcess = null;
            }
        }, "Kimiko-Neuro-Trainer");
        thread.setDaemon(true);
        thread.start();

        return true;
    }

    public static void stopTraining() {
        if (activeProcess != null && activeProcess.isAlive()) {
            activeProcess.destroyForcibly();
            activeProcess = null;
            training = false;
            statusMessage = "Stopped by user";
        }
    }
}

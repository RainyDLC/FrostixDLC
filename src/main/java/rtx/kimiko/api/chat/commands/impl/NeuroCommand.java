package rtx.kimiko.api.chat.commands.impl;

import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import rtx.kimiko.api.chat.commands.Command;
import rtx.kimiko.api.combat.neuro.NeuroDataRecorder;
import rtx.kimiko.api.combat.neuro.NeuroModel;
import rtx.kimiko.api.combat.neuro.NeuroTrainerRuntime;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class NeuroCommand extends Command {
    public NeuroCommand() {
        super("neuro", "Controls neural aim telemetry recording and model training", "aimbot");
    }

    @Override
    public void execute(@NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            this.sendHelp();
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "record" -> handleRecord(args);
            case "train" -> handleTrain(args);
            case "list" -> handleList();
            case "models" -> handleModels();
            case "load" -> handleLoad(args);
            case "status", "info" -> handleStatus();
            default -> this.sendHelp();
        }
    }

    private void handleRecord(String[] args) {
        if (args.length < 2) {
            this.logDirect("Usage: .neuro record <start|stop|status> [name]", Formatting.YELLOW);
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        NeuroDataRecorder recorder = NeuroDataRecorder.getInstance();

        switch (action) {
            case "start" -> {
                String name = args.length >= 3 ? args[2] : "session";
                String msg = recorder.start(name);
                this.logDirect(msg, recorder.isRecording() ? Formatting.GREEN : Formatting.RED);
            }
            case "stop" -> {
                String msg = recorder.stop();
                this.logDirect(msg, Formatting.AQUA);
            }
            case "status" -> {
                if (recorder.isRecording()) {
                    this.logDirect(String.format(Locale.ROOT, "Recording active: %s.csv (%d ticks, %d hits)",
                            recorder.getName(), recorder.getSessionTicks(), recorder.getHitCount()), Formatting.GREEN);
                } else {
                    this.logDirect("Recording is currently idle.", Formatting.GRAY);
                }
            }
            default -> this.logDirect("Unknown record action: " + action + ". Use start, stop, or status.", Formatting.RED);
        }
    }

    private void handleTrain(String[] args) {
        if (args.length < 2) {
            this.logDirect("Usage: .neuro train <setup|start|stop> [dataset] [epochs]", Formatting.YELLOW);
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "setup" -> {
                this.logDirect("Starting Python environment verification & setup...", Formatting.YELLOW);
                NeuroTrainerRuntime.setupAsync(msg -> this.logDirect("[Setup] " + msg, Formatting.GRAY));
            }
            case "start" -> {
                if (args.length < 3) {
                    this.logDirect("Usage: .neuro train start <dataset_name> [epochs]", Formatting.RED);
                    this.logDirect("Available datasets can be viewed via '.neuro list'", Formatting.GRAY);
                    return;
                }

                String datasetName = args[2].replaceFirst("\\.csv$", "");
                Path dataFile = NeuroModel.getDataDir().resolve(datasetName + ".csv");
                if (!Files.isRegularFile(dataFile)) {
                    this.logDirect("Dataset file not found: " + datasetName + ".csv", Formatting.RED);
                    return;
                }

                int epochs = 30;
                if (args.length >= 4) {
                    try {
                        epochs = Math.max(1, Integer.parseInt(args[3]));
                    } catch (NumberFormatException e) {
                        this.logDirect("Invalid epochs count. Defaulting to 30.", Formatting.YELLOW);
                    }
                }

                Path outModel = NeuroModel.getProfilePath(datasetName);
                boolean started = NeuroTrainerRuntime.startTraining(dataFile, outModel, epochs, datasetName, msg -> {
                    this.logDirect(msg, msg.contains("successfully") ? Formatting.GREEN : Formatting.AQUA);
                });

                if (!started) {
                    this.logDirect("Could not start training. Check if Python is configured or setup is required.", Formatting.RED);
                }
            }
            case "stop" -> {
                NeuroTrainerRuntime.stopTraining();
                this.logDirect("Training process stopped.", Formatting.YELLOW);
            }
            default -> this.logDirect("Unknown train command. Use setup, start, or stop.", Formatting.RED);
        }
    }

    private void handleList() {
        List<String> datasets = NeuroDataRecorder.listDatasets();
        if (datasets.isEmpty()) {
            this.logDirect("No datasets recorded yet. Use '.neuro record start <name>' during PvP.", Formatting.YELLOW);
            return;
        }

        this.logDirect("=== Recorded Datasets (" + datasets.size() + ") ===", Formatting.GOLD);
        for (String d : datasets) {
            this.logDirect(" - " + d, Formatting.AQUA);
        }
        int total = NeuroDataRecorder.totalTicks();
        this.logDirect("Total ticks: " + total + " (" + NeuroDataRecorder.remainingUntilTrain(total) + ")", Formatting.GREEN);
    }

    private void handleModels() {
        List<String> models = NeuroModel.listProfiles();
        String active = NeuroModel.getActiveName();

        this.logDirect("=== Available Aim Models ===", Formatting.GOLD);
        for (String m : models) {
            if (m.equals(active)) {
                this.logDirect(" * " + m + " (Active)", Formatting.GREEN);
            } else {
                this.logDirect(" - " + m, Formatting.GRAY);
            }
        }
    }

    private void handleLoad(String[] args) {
        if (args.length < 2) {
            this.logDirect("Usage: .neuro load <model_name>", Formatting.YELLOW);
            return;
        }

        String name = args[1].replaceFirst("\\.json$", "");
        if (NeuroModel.setActive(name)) {
            this.logDirect("Successfully switched active neuro model to: " + name, Formatting.GREEN);
        } else {
            this.logDirect("Failed to load model profile: " + name, Formatting.RED);
        }
    }

    private void handleStatus() {
        NeuroDataRecorder rec = NeuroDataRecorder.getInstance();
        File py = NeuroTrainerRuntime.findPython();

        this.logDirect("=== Neuro Engine Status ===", Formatting.GOLD);
        this.logDirect("Active Model: " + NeuroModel.getActiveName(), Formatting.AQUA);
        this.logDirect("Recorder: " + (rec.isRecording() ? "Recording (" + rec.getName() + ", " + rec.getSessionTicks() + " ticks)" : "Idle"),
                rec.isRecording() ? Formatting.GREEN : Formatting.GRAY);
        this.logDirect("Trainer Runtime: " + NeuroTrainerRuntime.getStatusMessage(), Formatting.AQUA);
        this.logDirect("Python: " + (py != null ? py.getAbsolutePath() : "Not found"), py != null ? Formatting.GRAY : Formatting.RED);
        this.logDirect("Environment Ready: " + (NeuroTrainerRuntime.isReady() ? "YES (PyTorch + NumPy)" : "NO (Run .neuro train setup)"),
                NeuroTrainerRuntime.isReady() ? Formatting.GREEN : Formatting.YELLOW);
    }

    private void sendHelp() {
        this.logDirect("=== Neuro Aim Commands ===", Formatting.GOLD);
        this.logDirect(".neuro record start <name> - Start recording PvP telemetry", Formatting.GRAY);
        this.logDirect(".neuro record stop         - Stop and save recording", Formatting.GRAY);
        this.logDirect(".neuro list                - View recorded datasets", Formatting.GRAY);
        this.logDirect(".neuro train setup         - Auto-install PyTorch & NumPy", Formatting.GRAY);
        this.logDirect(".neuro train start <data> [epochs] - Train model on dataset", Formatting.GRAY);
        this.logDirect(".neuro models              - List available models", Formatting.GRAY);
        this.logDirect(".neuro load <model>        - Switch active model", Formatting.GRAY);
        this.logDirect(".neuro status              - View current status", Formatting.GRAY);
    }

    @Override
    public @NotNull Stream<String> tabComplete(@NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return Stream.of("record", "train", "list", "models", "load", "status", "info")
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)));
        }
        if (args.length == 2 && "record".equalsIgnoreCase(args[0])) {
            return Stream.of("start", "stop", "status")
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)));
        }
        if (args.length == 2 && "train".equalsIgnoreCase(args[0])) {
            return Stream.of("setup", "start", "stop")
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)));
        }
        if (args.length == 2 && "load".equalsIgnoreCase(args[0])) {
            return NeuroModel.listProfiles().stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)));
        }
        return Stream.empty();
    }
}

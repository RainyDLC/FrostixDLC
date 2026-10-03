package rtx.kimiko.api.combat.neuro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import rtx.kimiko.api.events.EventBus;
import rtx.kimiko.api.events.EventHandler;
import rtx.kimiko.api.events.impl.game.TickEvent;
import rtx.kimiko.api.events.impl.player.AttackEntityEvent;
import rtx.kimiko.utils.combat.PlayerStateSnapshot;
import rtx.kimiko.utils.math.rotation.RotationUtil;
import rtx.kimiko.utils.player.PlayerWorldHelper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class NeuroDataRecorder {
    public static final NeuroDataRecorder INSTANCE = new NeuroDataRecorder();

    public static final String CSV_HEADER = "t,gcd,clean,yaw,pitch,dyaw,dpitch,has,tid,rx,ry,rz,bw,bh,dist,vis,on,atk,hp,ground,sprint,cd,vy,fall,w,jmp,water,hurt\n";
    private static final String CSV_ROW_FORMAT = "%d,%.6f,%d,%.4f,%.4f,%.4f,%.4f,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%d,%d,%d,%.1f,%d,%d,%.4f,%.4f,%.3f,%d,%d,%d,%d\n";

    private final MinecraftClient client = MinecraftClient.getInstance();
    private String name = "session";
    private boolean recording = false;
    private Path file = null;
    private BufferedWriter writer = null;

    private int tick = 0;
    private int sessionTicks = 0;
    private int hitCount = 0;
    private int flushCounter = 0;
    private boolean hasPrevRotation = false;
    private float prevYaw = 0.0f;
    private float prevPitch = 0.0f;
    private boolean attackedThisTick = false;
    private int lastTargetId = -1;

    public NeuroDataRecorder() {
    }

    public static NeuroDataRecorder getInstance() {
        return INSTANCE;
    }

    public synchronized String start(String datasetName) {
        if (this.recording) {
            return "Recording is already in progress (" + this.name + "). Use .neuro record stop";
        }
        if (datasetName == null || datasetName.trim().isEmpty()) {
            datasetName = "session_" + (System.currentTimeMillis() / 1000L);
        }
        datasetName = datasetName.trim().replaceAll("[^a-zA-Z0-9_-]", "_");

        try {
            Path dir = NeuroModel.getDataDir();
            Files.createDirectories(dir);
            Path targetFile = dir.resolve(datasetName + ".csv");

            boolean isNew = !Files.isRegularFile(targetFile) || Files.size(targetFile) == 0;
            if (!isNew && !hasCurrentHeader(targetFile)) {
                return "File " + datasetName + ".csv has an incompatible header format. Choose another name.";
            }

            this.writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            if (isNew) {
                this.writer.write(CSV_HEADER);
                this.writer.flush();
            }

            this.name = datasetName;
            this.file = targetFile;
            this.recording = true;
            this.sessionTicks = 0;
            this.hitCount = 0;
            this.tick = countTicks(targetFile);
            this.hasPrevRotation = false;

            EventBus.Companion.get().subscribe(this);

            return "Recording started: " + datasetName + ".csv (Existing ticks: " + this.tick + ")";
        } catch (Throwable t) {
            this.closeWriter();
            return "Failed to start recording: " + t.getMessage();
        }
    }

    public synchronized String stop() {
        if (!this.recording) {
            return "Recording is not active.";
        }
        this.recording = false;
        EventBus.Companion.get().unsubscribe(this);
        this.closeWriter();

        int recorded = this.sessionTicks;
        int total = this.file != null ? countTicks(this.file) : recorded;
        int allTotal = totalTicks();

        return String.format(Locale.ROOT, "Recording stopped: %s.csv (+%d ticks, %s, %d hits). Total dataset size: %d ticks (%s)",
                this.name, recorded, formatMinutes(recorded), this.hitCount, total, remainingUntilTrain(allTotal));
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (!this.recording) return;
        LivingEntity target = findTarget();
        recordTick(target, false);
    }

    @EventHandler
    public void onAttack(AttackEntityEvent event) {
        if (!this.recording) return;
        if (event.getTarget() instanceof LivingEntity living) {
            recordTick(living, true);
        }
    }

    private LivingEntity findTarget() {
        if (this.client.player == null || this.client.world == null) return null;
        if (this.client.targetedEntity instanceof LivingEntity living && living.isAlive()) {
            return living;
        }
        LivingEntity closest = null;
        double bestFov = 60.0;
        for (Entity e : this.client.world.getOtherEntities(this.client.player, this.client.player.getBoundingBox().expand(6.0))) {
            if (e instanceof LivingEntity living && living.isAlive()) {
                double dx = e.getX() - this.client.player.getX();
                double dy = e.getEyeY() - this.client.player.getEyeY();
                double dz = e.getZ() - this.client.player.getZ();
                double distXZ = Math.sqrt(dx * dx + dz * dz);
                float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
                float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
                float yawDiff = Math.abs(MathHelper.wrapDegrees(this.client.player.getYaw() - targetYaw));
                float pitchDiff = Math.abs(this.client.player.getPitch() - targetPitch);
                double fov = Math.hypot(yawDiff, pitchDiff);
                if (fov < bestFov) {
                    bestFov = fov;
                    closest = living;
                }
            }
        }
        return closest;
    }

    public synchronized void recordTick(LivingEntity target, boolean attacked) {
        if (!this.recording || this.writer == null || this.client.player == null || this.client.world == null) {
            return;
        }

        this.tick++;
        this.sessionTicks++;
        if (attacked) {
            this.hitCount++;
        }

        float currentYaw = this.client.player.getYaw();
        float currentPitch = this.client.player.getPitch();

        float dyaw = this.hasPrevRotation ? MathHelper.wrapDegrees(currentYaw - this.prevYaw) : 0.0f;
        float dpitch = this.hasPrevRotation ? (currentPitch - this.prevPitch) : 0.0f;

        this.prevYaw = currentYaw;
        this.prevPitch = currentPitch;
        this.hasPrevRotation = true;

        float gcd = RotationUtil.getRotationStep();
        PlayerStateSnapshot snapshot = PlayerStateSnapshot.capture(this.client.player, target);

        boolean hasTarget = target != null && target.isAlive();
        int tid = hasTarget ? target.getId() : -1;
        this.lastTargetId = tid;

        double rx = 0.0, ry = 0.0, rz = 0.0;
        double bw = 0.0, bh = 0.0, dist = -1.0;
        int vis = 0, on = 0;

        if (hasTarget) {
            Vec3d playerEye = this.client.player.getEyePos();
            Box box = target.getBoundingBox();
            Vec3d center = box.getCenter();

            rx = center.x - playerEye.x;
            ry = center.y - playerEye.y;
            rz = center.z - playerEye.z;
            bw = box.getLengthX();
            bh = box.getLengthY();
            dist = Math.sqrt(box.squaredMagnitude(playerEye));

            vis = this.client.player.canSee(target) ? 1 : 0;
            on = isTargetOnScreen(currentYaw, currentPitch, box) ? 1 : 0;
        }

        int clean = attacked && hasTarget && vis == 1 ? 1 : 0;
        int atk = attacked ? 1 : 0;
        float hp = hasTarget ? target.getHealth() : -1.0f;
        int hurt = hasTarget ? target.hurtTime : -1;

        int ground = snapshot.onGround() ? 1 : 0;
        int sprint = snapshot.sprinting() ? 1 : 0;
        int jmp = snapshot.jumpInput() ? 1 : 0;
        int water = snapshot.inWater() ? 1 : 0;
        int w = PlayerWorldHelper.hasMovementInput() ? 1 : 0;

        try {
            String row = String.format(Locale.ROOT, CSV_ROW_FORMAT,
                    this.tick,
                    gcd,
                    clean,
                    currentYaw,
                    currentPitch,
                    dyaw,
                    dpitch,
                    hasTarget ? 1 : 0,
                    tid,
                    rx, ry, rz,
                    bw, bh, dist,
                    vis, on,
                    atk,
                    hp,
                    ground,
                    sprint,
                    snapshot.attackCooldown(),
                    snapshot.velocityY(),
                    snapshot.fallDistance(),
                    w,
                    jmp,
                    water,
                    hurt
            );
            this.writer.write(row);

            if (++this.flushCounter >= 50) {
                this.flushCounter = 0;
                this.writer.flush();
            }
        } catch (Throwable t) {
            // Write failure handling
        }
    }

    private boolean isTargetOnScreen(float yaw, float pitch, Box box) {
        if (this.client.player == null) return false;
        Vec3d eye = this.client.player.getEyePos();
        Vec3d center = box.getCenter();
        double dx = center.x - eye.x;
        double dy = center.y - eye.y;
        double dz = center.z - eye.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));

        float yawDiff = Math.abs(MathHelper.wrapDegrees(yaw - targetYaw));
        float pitchDiff = Math.abs(pitch - targetPitch);
        return yawDiff < 60.0f && pitchDiff < 50.0f;
    }

    public synchronized void onAttack(Entity target) {
        if (!this.recording) return;
        this.attackedThisTick = true;
        if (target instanceof LivingEntity living) {
            recordTick(living, true);
            this.attackedThisTick = false;
        }
    }

    private void closeWriter() {
        if (this.writer != null) {
            try {
                this.writer.flush();
                this.writer.close();
            } catch (Throwable ignored) {
            }
            this.writer = null;
        }
    }

    public static boolean hasCurrentHeader(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String firstLine = reader.readLine();
            return firstLine != null && firstLine.trim().equals(CSV_HEADER.trim());
        } catch (Throwable t) {
            return false;
        }
    }

    public static int countTicks(Path path) {
        if (!Files.isRegularFile(path)) return 0;
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            long count = lines.count();
            return count > 1 ? (int) (count - 1) : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int totalTicks() {
        int total = 0;
        Path dir = NeuroModel.getDataDir();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                total = stream.filter(p -> p.getFileName().toString().endsWith(".csv"))
                        .mapToInt(NeuroDataRecorder::countTicks)
                        .sum();
            } catch (Throwable ignored) {
            }
        }
        return total;
    }

    public static List<String> listDatasets() {
        List<String> list = new ArrayList<>();
        Path dir = NeuroModel.getDataDir();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".csv"))
                        .sorted()
                        .forEach(p -> {
                            int ticks = countTicks(p);
                            String name = p.getFileName().toString().replaceFirst("\\.csv$", "");
                            list.add(String.format(Locale.ROOT, "%s (%d ticks, %s)", name, ticks, formatMinutes(ticks)));
                        });
            } catch (Throwable ignored) {
            }
        }
        return list;
    }

    public static String formatMinutes(int ticks) {
        return String.format(Locale.ROOT, "%.1f min", ticks / 1200.0);
    }

    public static String remainingUntilTrain(int totalTicks) {
        int target = 50000;
        if (totalTicks >= target) {
            return "Ready to train (" + totalTicks + " >= " + target + " ticks)";
        }
        return (target - totalTicks) + " more ticks needed for optimal training";
    }

    public boolean isRecording() {
        return this.recording;
    }

    public String getName() {
        return this.name;
    }

    public int getSessionTicks() {
        return this.sessionTicks;
    }

    public int getHitCount() {
        return this.hitCount;
    }
}

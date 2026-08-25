package ru.white.module.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.lyrics.LrcLibClient;
import ru.white.lyrics.LyricLine;
import ru.white.lyrics.LyricLineSplitter;
import ru.white.lyrics.LyricParticle3D;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.colors.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 3D Lyrics: слова трека всплывают в 3D-пространстве перед игроком —
 * частицы с фиксированной мировой позицией, статичным размером,
 * 3D-окклюзией и анимациями (LyricFlow / Typewriter / PopScale / KineticSlide / Fade).
 */
@FieldDefaults(level = AccessLevel.PRIVATE)
@ModuleInfo(name = "3D Lyrics", desc = "Kinetic lyrics in 3D world space", category = Category.RENDER)
public class Lyrics3D extends Module {

    public ModeSetting animation = new ModeSetting(this, "Animation", "LyricFlow", "Typewriter", "PopScale", "KineticSlide", "Fade");
    public ModeSetting colorMode = new ModeSetting(this, "Color", "White", "Theme");
    public ModeSetting splitMode = new ModeSetting(this, "Split Mode", "SmartSplit", "FullLine");
    public SliderSetting maxLines = new SliderSetting(this, "Max Lines", 3.0F, 1.0F, 6.0F, 1.0F);
    public SliderSetting minDistance = new SliderSetting(this, "Min Distance", 2.0F, 1.2F, 4.0F, 0.1F);
    public SliderSetting maxDistance = new SliderSetting(this, "Max Distance", 4.5F, 2.0F, 7.0F, 0.1F);
    public SliderSetting arcSpread = new SliderSetting(this, "Arc Spread", 70.0F, 20.0F, 85.0F, 5.0F);
    public SliderSetting floatHeight = new SliderSetting(this, "Float Height", 0.5F, 0.1F, 2.0F, 0.1F);
    public SliderSetting timeOffset = new SliderSetting(this, "Offset (ms)", 0.0F, -5000.0F, 5000.0F, 50.0F);
    public SliderSetting textSize = new SliderSetting(this, "Text Scale", 1.0F, 0.5F, 2.0F, 0.1F);
    public BooleanSetting debugMode = new BooleanSetting(this, "Debug Mode", false);

    private final List<LyricLine> rawLyrics = new ArrayList<>();
    private final List<LyricLine> lyricsQueue = new ArrayList<>();
    private final List<LyricParticle3D> activeParticles = new CopyOnWriteArrayList<>();
    private int currentLyricIndex = 0;

    // внутренние аудио-часы
    private long internalAudioClockMs = 0L;
    private long lastUpdateRealTimeMs = 0L;
    private long lastWindowsReportedPosMs = 0L;
    private long lastEffectiveAudioTimeMs = 0L;
    private long trackStartTimeSys = 0L;
    private boolean isPlaying = false;

    private String lastTrackKey = "";
    private String currentTrackTitle = "";
    private String currentTrackArtist = "";

    // кэш для рендера: нативные вызовы медиа — только из executor'а
    private volatile boolean mediaPlaying = false;
    private volatile long smtcReportedPos = 0L;
    private volatile long smtcReportedReal = 0L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nightix-lyrics");
        t.setDaemon(true);
        return t;
    });

    @Override
    public void onEnable() {
        super.onEnable();
        resetPlaybackState();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetPlaybackState();
    }

    private void resetPlaybackState() {
        activeParticles.clear();
        rawLyrics.clear();
        lyricsQueue.clear();
        lastTrackKey = "";
        currentTrackTitle = "";
        currentTrackArtist = "";
        currentLyricIndex = 0;
        internalAudioClockMs = 0L;
        lastEffectiveAudioTimeMs = 0L;
        lastUpdateRealTimeMs = System.currentTimeMillis();
        trackStartTimeSys = System.currentTimeMillis();
        lastWindowsReportedPosMs = 0L;
        isPlaying = false;
    }

    private void playTrack(String trackName, String artistName) {
        this.currentTrackTitle = trackName != null ? trackName.trim() : "";
        this.currentTrackArtist = artistName != null ? artistName.trim() : "";
        this.lastTrackKey = (currentTrackTitle + " - " + currentTrackArtist).toLowerCase();

        activeParticles.clear();
        rawLyrics.clear();
        lyricsQueue.clear();
        currentLyricIndex = 0;
        internalAudioClockMs = 0L;
        lastEffectiveAudioTimeMs = 0L;
        trackStartTimeSys = System.currentTimeMillis();
        lastUpdateRealTimeMs = System.currentTimeMillis();
        lastWindowsReportedPosMs = 0L;
        isPlaying = false;

        String expectedKey = this.lastTrackKey;

        LrcLibClient.fetchLyricsAsync(currentTrackTitle, currentTrackArtist, debugMode.getValue()).thenAccept(lines -> {
            if (!this.lastTrackKey.equalsIgnoreCase(expectedKey)) {
                return;
            }

            activeParticles.clear();
            rawLyrics.clear();
            lyricsQueue.clear();
            rawLyrics.addAll(lines);
            rebuildLyricsQueue();
            currentLyricIndex = 0;
            isPlaying = !lyricsQueue.isEmpty();
        });
    }

    private void rebuildLyricsQueue() {
        lyricsQueue.clear();
        if (splitMode.is("SmartSplit")) {
            lyricsQueue.addAll(LyricLineSplitter.splitLongLines(rawLyrics));
        } else {
            lyricsQueue.addAll(rawLyrics);
        }
    }

    @EventHandler
    public void onUpdate(EventUpdate e) {
        // нативный доступ к медиа (Windows SMTC) — ТОЛЬКО из фонового потока:
        // вызовы из рендера параллельно с MusicHud рушат процесс на нативе
        if (mc.player != null && mc.player.age % 5 == 0) {
            executor.submit(this::pollMedia);
        }
        syncPlaybackTime();
    }

    private void pollMedia() {
        try {
            dev.redstones.mediaplayerinfo.MediaInfo media = currentMedia();
            if (media == null || media.getTitle() == null || media.getTitle().isBlank()
                    || media.getTitle().equalsIgnoreCase("Unknown")) {
                if (isPlaying) {
                    isPlaying = false;
                    activeParticles.clear();
                }
                return;
            }

            String title = media.getTitle().trim();
            String artist = media.getArtist() != null ? media.getArtist().trim() : "";
            String key = (title + " - " + artist).toLowerCase();

            // смена трека — из фонового потока безопасно
            if (!key.equalsIgnoreCase(lastTrackKey)) {
                playTrack(title, artist);
                return;
            }

            // синхронизация с позицией Windows: якорь + интерполяция.
            // SMTC отдаёт позицию снапшотами раз в ~5с (устаревшую), поэтому
            // храним момент репорта и достраиваем время сами — без рывков и лагов.
            long reported = media.getPosition();
            if (reported != smtcReportedPos) {
                smtcReportedPos = reported;
                smtcReportedReal = System.currentTimeMillis();
            }

            mediaPlaying = media.getPlaying();
        } catch (Throwable ignored) {
        }
    }

    private void syncPlaybackTime() {
        if (!isEnabled() || mc.player == null) return;

        long now = System.currentTimeMillis();

        // только кэш: никаких нативных вызовов из этого метода
        if (!mediaPlaying) return;
        if (smtcReportedReal == 0) return;
        if (!isPlaying && !lyricsQueue.isEmpty()) {
            isPlaying = true;
        }

        // оценка реальной позиции: последний снапшот SMTC + прошедшее с него время
        long estPos = smtcReportedPos + (now - smtcReportedReal);
        long effectiveAudioTime = Math.max(0, estPos + timeOffset.getValue().longValue());

        // перемотка: резкий скачок — пересобираем активные частицы
        if (Math.abs(effectiveAudioTime - lastEffectiveAudioTimeMs) > 1500L) {
            resyncQueue(effectiveAudioTime);
        }
        lastEffectiveAudioTimeMs = effectiveAudioTime;

        update(effectiveAudioTime);
    }

    private dev.redstones.mediaplayerinfo.MediaInfo currentMedia() {
        try {
            return dev.redstones.mediaplayerinfo.MediaPlayerInfo.Instance.getMediaSessions().stream()
                    .max(java.util.Comparator.comparingInt(s -> s.getMedia().getPlaying() ? 1 : 0))
                    .map(dev.redstones.mediaplayerinfo.IMediaSession::getMedia)
                    .orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private void resyncQueue(long audioTimeMs) {
        activeParticles.clear();
        currentLyricIndex = 0;
        long now = System.currentTimeMillis();

        for (int i = 0; i < lyricsQueue.size(); i++) {
            LyricLine line = lyricsQueue.get(i);
            long nextStartMs = (i + 1 < lyricsQueue.size()) ? lyricsQueue.get(i + 1).timestampMs() : line.timestampMs() + 3500L;
            long durationMs = Math.max(1200L, Math.min(5000L, nextStartMs - line.timestampMs()));

            if (audioTimeMs >= line.timestampMs()) {
                long timeSinceLine = audioTimeMs - line.timestampMs();
                if (timeSinceLine < durationMs) {
                    spawnLyricParticle(line.text(), now - timeSinceLine, durationMs);
                }
                currentLyricIndex = i + 1;
            } else {
                break;
            }
        }
    }

    private void update(long audioTimeMs) {
        if (!isPlaying || lyricsQueue.isEmpty() || mc.player == null) return;

        long now = System.currentTimeMillis();

        while (currentLyricIndex < lyricsQueue.size()) {
            LyricLine line = lyricsQueue.get(currentLyricIndex);
            long nextStartMs = (currentLyricIndex + 1 < lyricsQueue.size()) ? lyricsQueue.get(currentLyricIndex + 1).timestampMs() : line.timestampMs() + 3500L;
            long durationMs = Math.max(1400L, Math.min(5500L, nextStartMs - line.timestampMs()));

            if (audioTimeMs >= line.timestampMs()) {
                long timeSinceLine = audioTimeMs - line.timestampMs();
                if (timeSinceLine < durationMs) {
                    spawnLyricParticle(line.text(), now - timeSinceLine, durationMs);
                }
                currentLyricIndex++;
            } else {
                break;
            }
        }

        activeParticles.removeIf(particle -> particle.isDead(now));
    }

    /** Позиция спавна: случайный угол в дуге перед камерой, без перекрытий. */
    private Vec3d findNonOverlappingSpawnPos(Vec3d headPos, float cameraYaw) {
        double spread = arcSpread.getValue();
        double minD = minDistance.getValue();
        double maxD = Math.max(minD + 0.5, maxDistance.getValue());

        Vec3d bestPos = null;
        double maxMinDistance = -1.0;

        for (int attempt = 0; attempt < 16; attempt++) {
            double randomAngleOffset = ThreadLocalRandom.current().nextDouble(-spread, spread);
            double targetYawDeg = cameraYaw + randomAngleOffset;
            double distance = ThreadLocalRandom.current().nextDouble(minD, maxD);
            double yOffset = ThreadLocalRandom.current().nextDouble(-0.25, 0.55);

            double yawRad = Math.toRadians(targetYawDeg);
            double dirX = -Math.sin(yawRad);
            double dirZ = Math.cos(yawRad);

            Vec3d candidate = new Vec3d(
                    headPos.x + (dirX * distance),
                    headPos.y + yOffset,
                    headPos.z + (dirZ * distance));

            if (activeParticles.isEmpty()) {
                return candidate;
            }

            double minDistanceToOthers = Double.MAX_VALUE;
            for (LyricParticle3D active : activeParticles) {
                double d = candidate.distanceTo(active.getBasePosition());
                if (d < minDistanceToOthers) {
                    minDistanceToOthers = d;
                }
            }

            if (minDistanceToOthers >= 1.35) {
                return candidate;
            }

            if (minDistanceToOthers > maxMinDistance) {
                maxMinDistance = minDistanceToOthers;
                bestPos = candidate;
            }
        }

        return bestPos;
    }

    private void spawnLyricParticle(String text, long spawnTimeMs, long durationMs) {
        if (mc.player == null || text == null || text.isBlank()) return;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d headPos = mc.player.getEyePos();
        float cameraYaw = camera.getYaw();

        int maxAllowed = maxLines.getValue().intValue();
        while (activeParticles.size() >= maxAllowed) {
            activeParticles.remove(0);
        }

        Vec3d spawnPos = findNonOverlappingSpawnPos(headPos, cameraYaw);
        if (spawnPos == null) return;

        float riseHeight = floatHeight.getValue();
        activeParticles.add(new LyricParticle3D(text, spawnPos, spawnTimeMs, durationMs, riseHeight));
    }

    private int getActiveColorRgb() {
        if (colorMode.is("Theme")) {
            int c = ColorUtil.getClientColor1(1);
            return c & 0x00FFFFFF;
        }
        return 0xFFFFFF;
    }

    @EventHandler
    public void onDisplay(EventDisplay e) {
        if (!isEnabled() || mc.world == null || mc.player == null) return;

        syncPlaybackTime();

        if (activeParticles.isEmpty()) return;

        Camera camera = mc.gameRenderer.getCamera();
        ru.white.utils.render.font.Font font = ru.white.utils.render.font.Fonts.sf_bold;
        String animMode = animation.getValue();
        int colorRgb = getActiveColorRgb();
        long now = System.currentTimeMillis();

        for (LyricParticle3D particle : activeParticles) {
            particle.render(camera, font, animMode, colorRgb, now, textSize.getValue());
        }
    }

    @EventHandler
    public void onDebug(EventDisplay e) {
        if (!isEnabled() || !debugMode.getValue() || mc.player == null) return;

        net.minecraft.client.font.TextRenderer font = MinecraftClient.getInstance().textRenderer;

        long displayTimeMs = Math.max(0, internalAudioClockMs + timeOffset.getValue().longValue());
        String titleStr = "Track: " + (currentTrackTitle.isEmpty() ? "None" : currentTrackTitle + " - " + currentTrackArtist);
        String timerInfo = String.format("Audio Time: %02d:%02d.%03d (%d ms)",
                (displayTimeMs / 60_000),
                (displayTimeMs % 60_000) / 1000,
                displayTimeMs % 1000,
                displayTimeMs);
        String particlesInfo = String.format("Active 3D Particles: %d", activeParticles.size());
        String statusInfo = String.format("Queue: %d / %d lines", currentLyricIndex, lyricsQueue.size());

        float x = 10.0f;
        float y = 70.0f;

        e.getDrawContext().drawText(font, "[3D Lyrics Debug]", (int) x, (int) y, 0xFFFF5A5A, false);
        e.getDrawContext().drawText(font, titleStr, (int) x, (int) y + 12, 0xFFDCDCDC, false);
        e.getDrawContext().drawText(font, timerInfo, (int) x, (int) y + 24, 0xFFFFFFFF, false);
        e.getDrawContext().drawText(font, particlesInfo, (int) x, (int) y + 36, 0xFF78FF78, false);
        e.getDrawContext().drawText(font, statusInfo, (int) x, (int) y + 48, 0xFFB4B4FF, false);
    }
}

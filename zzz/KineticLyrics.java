package wtf.wyvern.client.modules.impl.render.lyrics;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import wtf.wyvern.Wyvern;
import wtf.wyvern.base.events.impl.player.EventUpdate;
import wtf.wyvern.base.events.impl.render.EventRender2D;
import wtf.wyvern.base.events.impl.render.EventRender3D;
import wtf.wyvern.base.font.Font;
import wtf.wyvern.base.font.Fonts;
import wtf.wyvern.base.font.MsdfFont;
import wtf.wyvern.client.modules.api.Category;
import wtf.wyvern.client.modules.api.Module;
import wtf.wyvern.client.modules.api.ModuleAnnotation;
import wtf.wyvern.client.modules.api.setting.impl.BooleanSetting;
import wtf.wyvern.client.modules.api.setting.impl.ModeSetting;
import wtf.wyvern.client.modules.api.setting.impl.NumberSetting;
import wtf.wyvern.utility.media.MediaUtils;
import wtf.wyvern.utility.render.display.base.color.ColorRGBA;
import wtf.wyvern.utility.render.display.base.color.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

@ModuleAnnotation(
        name = "KineticLyrics",
        category = Category.RENDER,
        description = "Рендерит слова трека в 3D-пространстве с абсолютным статичным размером и 3D-окклюзией"
)
public class KineticLyrics extends Module {
    public static final KineticLyrics INSTANCE = new KineticLyrics();

    // Module Settings
    public final ModeSetting animation = new ModeSetting("Animation", "LyricFlow", "Typewriter", "PopScale", "KineticSlide", "Fade");
    public final ModeSetting colorMode = new ModeSetting("Color", "Theme", "ThemeGradient", "White");
    public final ModeSetting fontMode = new ModeSetting("Font", "Bold", "RoundBold", "Semibold", "Medium");
    public final ModeSetting splitMode = new ModeSetting("Split Mode", "SmartSplit", "FullLine");
    public final NumberSetting maxLines = new NumberSetting("Max Lines", 3.0f, 1.0f, 6.0f, 1.0f);
    public final NumberSetting minDistance = new NumberSetting("Min Distance", 2.0f, 1.2f, 4.0f, 0.1f);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", 4.5f, 2.0f, 7.0f, 0.1f);
    public final NumberSetting arcSpread = new NumberSetting("Arc Spread", 70.0f, 20.0f, 85.0f, 5.0f);
    public final NumberSetting floatHeight = new NumberSetting("Float Height", 0.5f, 0.1f, 2.0f, 0.1f);
    public final NumberSetting timeOffset = new NumberSetting("Offset (ms)", 0.0f, -5000.0f, 5000.0f, 50.0f);
    public final BooleanSetting debugMode = new BooleanSetting("Debug Mode", false);
    public final ModeSetting source = new ModeSetting("Source", "MediaTransport", "Manual");

    // Internal State
    private final List<LyricLine> rawLyrics = new ArrayList<>();
    private final List<LyricLine> lyricsQueue = new ArrayList<>();
    private final List<LyricParticle3D> activeParticles = new CopyOnWriteArrayList<>();
    private int currentLyricIndex = 0;

    // Real-Time Playback Clock
    private long internalAudioClockMs = 0L;
    private long lastUpdateRealTimeMs = 0L;
    private long lastWindowsReportedPosMs = 0L;
    private long lastEffectiveAudioTimeMs = 0L;
    private long trackStartTimeSys = 0L;
    private boolean isPlaying = false;

    private String lastTrackKey = "";
    private String currentTrackTitle = "";
    private String currentTrackArtist = "";

    private KineticLyrics() {
    }

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

    public void playTrack(String trackName, String artistName) {
        this.currentTrackTitle = trackName != null ? trackName.trim() : "";
        this.currentTrackArtist = artistName != null ? artistName.trim() : "";
        this.lastTrackKey = (currentTrackTitle + " - " + currentTrackArtist).toLowerCase();

        // Полный сброс состояния при загрузке нового трека
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

        LrcLibClient.fetchLyricsAsync(currentTrackTitle, currentTrackArtist, debugMode.isEnabled()).thenAccept(lines -> {
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

    @EventTarget
    public void onUpdate(EventUpdate e) {
        if (!isEnabled() || mc.player == null) return;
        syncPlaybackTime();
    }

    public void syncPlaybackTime() {
        if (!isEnabled() || mc.player == null) return;

        long now = System.currentTimeMillis();
        long dt = (lastUpdateRealTimeMs > 0) ? (now - lastUpdateRealTimeMs) : 0;
        lastUpdateRealTimeMs = now;

        if (source.is("MediaTransport")) {
            MediaUtils.MediaInfo media = MediaUtils.getCurrentMedia();
            if (media != null && media.title != null && !media.title.isBlank() && !media.title.equalsIgnoreCase("Unknown")) {
                String title = media.title.trim();
                String artist = media.artist != null ? media.artist.trim() : "";
                String key = (title + " - " + artist).toLowerCase();

                // 1. Идентифицируем смену трека
                if (!key.equalsIgnoreCase(lastTrackKey)) {
                    playTrack(title, artist);
                    return;
                }
                
                // 2. Восстанавливаем isPlaying, если он был случайно отключен "Unknown" буферизацией
                if (!isPlaying && !lyricsQueue.isEmpty()) {
                    isPlaying = true;
                }

                // 3. Синхронизация времени с Windows Media
                // Защита: не прыгаем в первые 1.5 сек трека, чтобы избежать получения времени старого трека
                if (now - trackStartTimeSys > 1500L && media.position > 0 && Math.abs(media.position - lastWindowsReportedPosMs) > 1500L) {
                    lastWindowsReportedPosMs = media.position;
                    internalAudioClockMs = media.position;
                }

                boolean isPaused = (media.status == MediaUtils.Status.PAUSED);
                if (!isPaused) {
                    internalAudioClockMs += dt;
                }

                long effectiveAudioTime = Math.max(0, internalAudioClockMs + (long) timeOffset.getCurrent());
                
                // 4. Детект скачков времени (если пользователь перемотал трек вперед или назад)
                if (Math.abs(effectiveAudioTime - lastEffectiveAudioTimeMs) > 1500L) {
                    resyncQueue(effectiveAudioTime);
                }
                lastEffectiveAudioTimeMs = effectiveAudioTime;
                
                update(effectiveAudioTime);
            } else {
                // Если статус плеера на долю секунды стал Unknown (например, при переключении)
                if (isPlaying) {
                    isPlaying = false;
                    activeParticles.clear();
                }
            }
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

    public void update(long audioTimeMs) {
        if (!isEnabled() || !isPlaying || lyricsQueue.isEmpty() || mc.player == null) return;

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

    private Vec3d findNonOverlappingSpawnPos(Vec3d headPos, float cameraYaw) {
        double spread = arcSpread.getCurrent();
        double minD = minDistance.getCurrent();
        double maxD = Math.max(minD + 0.5, maxDistance.getCurrent());

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

            double spawnX = headPos.x + (dirX * distance);
            double spawnY = headPos.y + yOffset;
            double spawnZ = headPos.z + (dirZ * distance);
            Vec3d candidate = new Vec3d(spawnX, spawnY, spawnZ);

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

        int maxAllowed = (int) maxLines.getCurrent();
        while (activeParticles.size() >= maxAllowed) {
            activeParticles.remove(0);
        }

        Vec3d spawnPos = findNonOverlappingSpawnPos(headPos, cameraYaw);
        if (spawnPos == null) return;

        float riseHeight = floatHeight.getCurrent();
        activeParticles.add(new LyricParticle3D(text, spawnPos, spawnTimeMs, durationMs, riseHeight));
    }

    private MsdfFont getSelectedFont() {
        return switch (fontMode.get()) {
            case "RoundBold" -> Fonts.ROUND_BOLD;
            case "Semibold" -> Fonts.SEMIBOLD;
            case "Medium" -> Fonts.MEDIUM;
            default -> Fonts.BOLD;
        };
    }

    private int getActiveColorRgb() {
        switch (colorMode.get()) {
            case "Theme" -> {
                if (Wyvern.getInstance().getThemeManager() != null && Wyvern.getInstance().getThemeManager().getCurrentTheme() != null) {
                    return Wyvern.getInstance().getThemeManager().getCurrentTheme().getColor().getRGB() & 0x00FFFFFF;
                }
                return 0xFFFFFF;
            }
            case "ThemeGradient" -> {
                if (Wyvern.getInstance().getThemeManager() != null && Wyvern.getInstance().getThemeManager().getCurrentTheme() != null) {
                    ColorRGBA c1 = Wyvern.getInstance().getThemeManager().getCurrentTheme().getColor();
                    ColorRGBA c2 = Wyvern.getInstance().getThemeManager().getCurrentTheme().getSecondColor();
                    float ratio = (float) (Math.sin(System.currentTimeMillis() / 450.0) * 0.5 + 0.5);
                    ColorRGBA blended = ColorUtil.interpolate(c1, c2, ratio);
                    return blended.getRGB() & 0x00FFFFFF;
                }
                return 0xFFFFFF;
            }
            default -> {
                return 0xFFFFFF;
            }
        }
    }

    @EventTarget
    public void onRender3D(EventRender3D event) {
        if (!isEnabled() || mc.world == null || mc.player == null) return;

        syncPlaybackTime();

        if (activeParticles.isEmpty()) return;

        Camera camera = mc.gameRenderer.getCamera();
        MsdfFont font = getSelectedFont();
        String animMode = animation.get();
        int colorRgb = getActiveColorRgb();
        long now = System.currentTimeMillis();

        for (LyricParticle3D particle : activeParticles) {
            particle.render(event.getMatrix(), camera, font, animMode, colorRgb, now);
        }
    }

    @EventTarget
    public void onRender2D(EventRender2D event) {
        if (!isEnabled() || !debugMode.isEnabled() || mc.player == null) return;

        Font font = new Font(Fonts.MEDIUM, 14.0f);
        Font boldFont = new Font(Fonts.BOLD, 15.0f);

        long displayTimeMs = Math.max(0, internalAudioClockMs + (long) timeOffset.getCurrent());
        String titleStr = "Track: " + (currentTrackTitle.isEmpty() ? "None" : currentTrackTitle + " - " + currentTrackArtist);
        String timerInfo = String.format("Audio Time: %02d:%02d.%03d (%d ms)",
                (displayTimeMs / 60_000),
                (displayTimeMs % 60_000) / 1000,
                displayTimeMs % 1000,
                displayTimeMs
        );
        String particlesInfo = String.format("Active 3D Particles: %d", activeParticles.size());
        String statusInfo = String.format("Queue: %d / %d lines", currentLyricIndex, lyricsQueue.size());

        float x = 10.0f;
        float y = 70.0f;

        event.getContext().drawText(boldFont, "[KineticLyrics Debug]", x, y, new ColorRGBA(255, 90, 90, 255));
        event.getContext().drawText(font, titleStr, x, y + 12, new ColorRGBA(220, 220, 220, 255));
        event.getContext().drawText(font, timerInfo, x, y + 24, new ColorRGBA(255, 255, 255, 255));
        event.getContext().drawText(font, particlesInfo, x, y + 36, new ColorRGBA(120, 255, 120, 255));
        event.getContext().drawText(font, statusInfo, x, y + 48, new ColorRGBA(180, 180, 255, 255));
    }
}

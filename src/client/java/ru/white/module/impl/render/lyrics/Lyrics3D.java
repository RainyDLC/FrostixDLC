package ru.white.module.impl.render.lyrics;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.math.Vec3d;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.preview.ModulePreview;
import ru.white.module.api.preview.PreviewContext;
import ru.white.module.api.preview.PreviewSettings;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ButtonSetting;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(
        name = "3D Lyrics",
        desc = "3D отображение текста играющего трека в мире",
        category = Category.RENDER
)
public class Lyrics3D extends Module implements ModulePreview {

    public final ButtonSetting previewButton = PreviewSettings.button(this);

    // Только 2 режима анимации по требованию пользователя:
    public final ModeSetting animation = new ModeSetting(this, "Анимация", "Печатание", "Плавное появление");

    public final ModeSetting colorMode = new ModeSetting(this, "Цвет", "Тема", "Градиент", "Свой", "Белый");
    public final ColorSetting customColor = new ColorSetting(this, "Свой цвет", 0xFFFFFFFF).setVisible(() -> colorMode.is("Свой"));

    public final ModeSetting splitMode = new ModeSetting(this, "Разбиение", "Умное", "Вся строка");
    public final SliderSetting maxLines = new SliderSetting(this, "Макс. строк", 3.0f, 1.0f, 6.0f, 1.0f);
    public final SliderSetting sizeScale = new SliderSetting(this, "Размер", 1.0f, 0.5f, 2.5f, 0.1f);
    public final SliderSetting minDistance = new SliderSetting(this, "Мин. дистанция", 2.0f, 1.0f, 4.0f, 0.1f);
    public final SliderSetting maxDistance = new SliderSetting(this, "Макс. дистанция", 4.0f, 2.0f, 7.0f, 0.1f);
    public final SliderSetting arcSpread = new SliderSetting(this, "Угол разброса", 70.0f, 20.0f, 90.0f, 5.0f);
    public final SliderSetting floatHeight = new SliderSetting(this, "Высота подъёма", 0.5f, 0.0f, 2.0f, 0.1f);
    public final SliderSetting timeOffset = new SliderSetting(this, "Смещение (мс)", 0.0f, -5000.0f, 5000.0f, 50.0f);
    public final BooleanSetting throughWalls = new BooleanSetting(this, "Сквозь стены", false);
    public final BooleanSetting shadow = new BooleanSetting(this, "Тень текста", true);
    public final BooleanSetting debugMode = new BooleanSetting(this, "Debug Mode", false);

    private final PreviewSettings previewSettings = PreviewSettings.of(this, 3.5F, 0F, 2F);

    private final BufferAllocator allocator = new BufferAllocator(1 << 16);

    // Внутреннее состояние
    private final List<LyricLine> rawLyrics = new ArrayList<>();
    private final List<LyricLine> lyricsQueue = new ArrayList<>();
    private final List<LyricParticle3D> activeParticles = new CopyOnWriteArrayList<>();
    private int currentLyricIndex = 0;

    // Часы воспроизведения
    private long internalAudioClockMs = 0L;
    private long lastUpdateRealTimeMs = 0L;
    private long lastWindowsReportedPosMs = 0L;
    private long lastEffectiveAudioTimeMs = 0L;
    private long trackStartTimeSys = 0L;
    private boolean isPlaying = false;

    private String lastTrackKey = "";
    private String currentTrackTitle = "";
    private String currentTrackArtist = "";

    private String fetchStatus = "Ожидание трека...";

    @Override
    protected void onEnable() {
        super.onEnable();
        resetPlaybackState();
    }

    @Override
    protected void onDisable() {
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
        fetchStatus = "Ожидание трека...";
        currentLyricIndex = 0;
        internalAudioClockMs = 0L;
        lastEffectiveAudioTimeMs = 0L;
        lastUpdateRealTimeMs = System.currentTimeMillis();
        trackStartTimeSys = System.currentTimeMillis();
        lastWindowsReportedPosMs = 0L;
        isPlaying = false;
    }

    public void playTrack(String trackName, String artistName, long durationMs) {
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
        fetchStatus = "Загрузка текста с lrclib...";

        String expectedKey = this.lastTrackKey;

        LrcLibClient.fetchLyricsAsync(currentTrackTitle, currentTrackArtist, durationMs, debugMode.getValue()).thenAccept(lines -> {
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
            fetchStatus = isPlaying ? "Найдено " + lyricsQueue.size() + " строк" : "Текст не найден в базе";

            if (isPlaying) {
                long effectiveAudioTime = Math.max(0, internalAudioClockMs + timeOffset.getValue().longValue());
                resyncQueue(effectiveAudioTime);
            }
        });
    }

    private void rebuildLyricsQueue() {
        lyricsQueue.clear();
        if (splitMode.is("Умное")) {
            lyricsQueue.addAll(LyricLineSplitter.splitLongLines(rawLyrics));
        } else {
            lyricsQueue.addAll(rawLyrics);
        }
    }

    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (!isEnabled() || mc.player == null) return;
        syncPlaybackTime();
    }

    public void syncPlaybackTime() {
        if (!isEnabled() || mc.player == null) return;

        long now = System.currentTimeMillis();
        long dt = (lastUpdateRealTimeMs > 0) ? (now - lastUpdateRealTimeMs) : 0;
        lastUpdateRealTimeMs = now;

        MediaUtils.MediaTrackInfo media = MediaUtils.getCurrentMedia();
        if (media != null && media.title() != null && !media.title().isBlank() && !media.title().equalsIgnoreCase("Unknown")) {
            String title = media.title().trim();
            String artist = media.artist() != null ? media.artist().trim() : "";
            String key = (title + " - " + artist).toLowerCase();

            // 1. Смена трека
            if (!key.equalsIgnoreCase(lastTrackKey)) {
                playTrack(title, artist, media.durationMs());
                return;
            }

            // 2. Восстановление воспроизведения
            if (!isPlaying && !lyricsQueue.isEmpty()) {
                isPlaying = true;
            }

            // 3. Синхронизация времени
            if (now - trackStartTimeSys > 1500L && media.positionMs() > 0 && Math.abs(media.positionMs() - lastWindowsReportedPosMs) > 1500L) {
                lastWindowsReportedPosMs = media.positionMs();
                internalAudioClockMs = media.positionMs();
            }

            if (media.playing()) {
                internalAudioClockMs += dt;
            }

            long effectiveAudioTime = Math.max(0, internalAudioClockMs + timeOffset.getValue().longValue());

            // 4. Детект перемотки трека
            if (Math.abs(effectiveAudioTime - lastEffectiveAudioTimeMs) > 1500L) {
                resyncQueue(effectiveAudioTime);
            }
            lastEffectiveAudioTimeMs = effectiveAudioTime;

            update(effectiveAudioTime);
        } else {
            if (isPlaying && !isPreviewActive()) {
                isPlaying = false;
                activeParticles.clear();
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

        int maxAllowed = maxLines.getValue().intValue();
        while (activeParticles.size() >= maxAllowed) {
            activeParticles.remove(0);
        }

        Vec3d spawnPos = findNonOverlappingSpawnPos(headPos, cameraYaw);
        if (spawnPos == null) return;

        float rise = floatHeight.getValue();
        activeParticles.add(new LyricParticle3D(text, spawnPos, spawnTimeMs, durationMs, rise));
    }

    private int getActiveColorRgb() {
        if (colorMode.is("Тема")) {
            return ColorUtil.client() & 0x00FFFFFF;
        } else if (colorMode.is("Градиент")) {
            return ColorUtil.fade(1) & 0x00FFFFFF;
        } else if (colorMode.is("Свой")) {
            return customColor.getValue() & 0x00FFFFFF;
        } else {
            return 0xFFFFFF;
        }
    }

    @EventHandler
    public void onRender3D(EventRender3D event) {
        if (!isEnabled() || mc.world == null || mc.player == null) return;

        syncPlaybackTime();

        if (activeParticles.isEmpty()) return;

        Camera camera = mc.gameRenderer.getCamera();
        String animMode = animation.getValue();
        int colorRgb = getActiveColorRgb();
        boolean walls = throughWalls.getValue();
        boolean textShadow = shadow.getValue();
        float scale = sizeScale.getValue();
        long now = System.currentTimeMillis();

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);

        for (LyricParticle3D particle : activeParticles) {
            particle.render(event.getMatrixStack(), camera, immediate, animMode, colorRgb, walls, textShadow, scale, now);
        }

        immediate.draw();
    }

    @EventHandler
    public void onRenderDisplay(EventDisplay event) {
        if (!isEnabled() || !debugMode.getValue() || mc.player == null) return;

        DrawContext context = event.getDrawContext();
        Font font = Fonts.sf_regular;
        Font boldFont = Fonts.sf_bold;

        long displayTimeMs = Math.max(0, internalAudioClockMs + timeOffset.getValue().longValue());
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

        boldFont.draw("[3D Lyrics Debug]", x, y, 7f, ColorUtil.getColor(255, 90, 90));
        font.draw(titleStr, x, y + 10, 6f, ColorUtil.getColor(220, 220, 220));
        font.draw("Status: " + fetchStatus, x, y + 20, 6f, ColorUtil.getColor(255, 220, 100));
        font.draw(timerInfo, x, y + 30, 6f, ColorUtil.getColor(255, 255, 255));
        font.draw(particlesInfo, x, y + 40, 6f, ColorUtil.getColor(120, 255, 120));
        font.draw(statusInfo, x, y + 50, 6f, ColorUtil.getColor(180, 180, 255));
    }

    // ───────────────────────────── Предпоказ (ModulePreview) ─────────────────────────────

    @Override
    public PreviewSettings previewSettings() {
        return previewSettings;
    }

    @Override
    public void previewSpawn(PreviewContext ctx) {
        long now = System.currentTimeMillis();
        String[] samples = {
                "♪ 3D Lyrics ♪",
                "Печатание и появление",
                "Синхронизация с музыкой"
        };
        String sampleText = samples[ThreadLocalRandom.current().nextInt(samples.length)];
        Vec3d spawnPos = ctx.anchor().add(
                ThreadLocalRandom.current().nextDouble(-0.6, 0.6),
                ThreadLocalRandom.current().nextDouble(0.2, 0.8),
                ThreadLocalRandom.current().nextDouble(-0.6, 0.6)
        );
        activeParticles.add(new LyricParticle3D(sampleText, spawnPos, now, 3500L, floatHeight.getValue()));
    }

    @Override
    public void previewStop() {
        activeParticles.clear();
    }
}

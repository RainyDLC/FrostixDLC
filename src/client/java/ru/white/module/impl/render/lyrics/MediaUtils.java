package ru.white.module.impl.render.lyrics;

import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MediaUtils {
    private static boolean initialized = false;
    private static ScheduledExecutorService scheduler;
    private static volatile MediaTrackInfo currentTrackInfo = null;

    public record MediaTrackInfo(
            String title,
            String artist,
            boolean playing,
            long positionMs,
            long durationMs,
            long lastFetchSysTimeMs
    ) {}

    public static MediaTrackInfo getCurrentMedia() {
        if (!initialized) {
            init();
        }
        return currentTrackInfo;
    }

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "white-3dlyrics-media");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<IMediaSession> sessions = MediaPlayerInfo.Instance.getMediaSessions();
                if (sessions != null && !sessions.isEmpty()) {
                    IMediaSession session = sessions.stream()
                            .max(Comparator.comparingInt(s -> s.getMedia() != null && s.getMedia().getPlaying() ? 1 : 0))
                            .orElse(null);

                    if (session != null && session.getMedia() != null) {
                        MediaInfo media = session.getMedia();
                        String title = media.getTitle() != null ? media.getTitle().trim() : "";
                        String artist = media.getArtist() != null ? media.getArtist().trim() : "";

                        if (!title.isEmpty() || !artist.isEmpty()) {
                            boolean playing = media.getPlaying();
                            long rawPos = media.getPosition();
                            long rawDur = media.getDuration();

                            // Normalize Windows GSMTC seconds to milliseconds
                            long posMs = (rawPos > 0 && rawPos < 10000) ? rawPos * 1000L : rawPos;
                            long durMs = (rawDur > 0 && rawDur < 10000) ? rawDur * 1000L : rawDur;

                            currentTrackInfo = new MediaTrackInfo(
                                    title.isEmpty() ? "Unknown" : title,
                                    artist,
                                    playing,
                                    posMs,
                                    durMs,
                                    System.currentTimeMillis()
                            );
                            return;
                        }
                    }
                }
                currentTrackInfo = null;
            } catch (Throwable ignored) {
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
    }

    public static synchronized void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        initialized = false;
        currentTrackInfo = null;
    }
}

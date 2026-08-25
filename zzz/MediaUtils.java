package wtf.wyvern.utility.media;

import by.bonenaut7.mediatransport4j.api.MediaSession;
import by.bonenaut7.mediatransport4j.api.MediaTransport;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MediaUtils {
    private static boolean initialized = false;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static volatile MediaInfo mediaInfo = null;
    private static final Map<String, net.minecraft.util.Identifier> textureCache = new ConcurrentHashMap<>();

    private static String lastMetadataKey = "";
    private static String currentTextureHash = "";

    private static final float[] waveHeights = new float[4];
    private static final float[] waveTargets = new float[4];
    private static long lastWaveUpdate = 0L;

    public enum Status {
        PLAYING, PAUSED
    }

    public static class MediaInfo {
        public final String title;
        public final String artist;
        public final String textureHash;
        public final Status status;
        public final float[] heights;
        public final long position;
        public final long duration;

        public MediaInfo(String title, String artist, String textureHash, Status status, float[] heights, long position, long duration) {
            this.title = title;
            this.artist = artist;
            this.textureHash = textureHash;
            this.status = status;
            this.heights = heights.clone();
            this.position = position;
            this.duration = duration;
        }

        public net.minecraft.util.Identifier getTexture() {
            return textureCache.get(textureHash);
        }
    }

    public static MediaInfo getCurrentMedia() {
        if (!initialized) {
            try {
                MediaTransport.init();
            } catch (Exception e) {
                e.printStackTrace();
            }
            initialized = true;

            scheduler.scheduleAtFixedRate(() -> {
                try {
                    List<MediaSession> sessions = MediaTransport.getMediaSessions();
                    if (sessions != null && !sessions.isEmpty()) {
                        MediaSession session = sessions.get(0);

                        String title = session.getTitle() != null ? session.getTitle() : "Unknown";
                        String artist = session.getArtist() != null ? session.getArtist() : "Artist";
                        String metadataKey = title + artist;

                        if (!metadataKey.equals(lastMetadataKey)) {
                            clearCache();

                            if (session.hasThumbnail()) {
                                ByteBuffer buffer = session.getThumbnail();
                                net.minecraft.util.Identifier texture = convertTexture(buffer);
                                if (texture != null) {
                                    currentTextureHash = String.valueOf(metadataKey.hashCode());
                                    textureCache.put(currentTextureHash, texture);
                                }
                            } else {
                                currentTextureHash = "";
                            }
                            lastMetadataKey = metadataKey;
                        }

                        boolean playing = session.isPlaying();
                        long rawPos = session.getPosition();
                        long rawDur = session.getDuration();

                        // Windows GSMTC / mediatransport4j normalization:
                        // If duration or position are small values (< 10000), they are in seconds, so convert to ms
                        long dur = (rawDur > 0 && rawDur < 10000) ? rawDur * 1000L : rawDur;
                        long pos = (rawPos > 0 && rawPos < 10000) ? rawPos * 1000L : rawPos;

                        updateWaveLogic(playing);

                        mediaInfo = new MediaInfo(
                                title,
                                artist,
                                currentTextureHash,
                                playing ? Status.PLAYING : Status.PAUSED,
                                waveHeights,
                                pos,
                                dur
                        );
                    } else {
                        if (mediaInfo != null) {
                            clearCache();
                            mediaInfo = null;
                            lastMetadataKey = "";
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 0, 50, TimeUnit.MILLISECONDS);
        }
        return mediaInfo;
    }

    private static void updateWaveLogic(boolean playing) {
        if (playing) {
            if (System.currentTimeMillis() - lastWaveUpdate > 90) {
                lastWaveUpdate = System.currentTimeMillis();
                waveTargets[0] = 4.0f + (float) (Math.random() * 6.0f);
                waveTargets[1] = 2.0f + (float) (Math.random() * 10.0f);
                waveTargets[2] = 5.0f + (float) (Math.random() * 4.0f);
                waveTargets[3] = 3.0f + (float) (Math.random() * 5.0f);
            }
        } else {
            for (int i = 0; i < 4; i++) waveTargets[i] = 0f;
        }

        for (int i = 0; i < 4; i++) {
            float smoothness = (i == 1) ? 0.1f : 0.25f;
            waveHeights[i] += (waveTargets[i] - waveHeights[i]) * smoothness;
        }
    }

    private static net.minecraft.util.Identifier convertTexture(ByteBuffer buffer) {
        try {
            ByteBuffer duplicate = buffer.asReadOnlyBuffer();
            duplicate.clear();
            byte[] bytes = new byte[duplicate.remaining()];
            duplicate.get(bytes);

            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) return null;

            NativeImage ni = new NativeImage(img.getWidth(), img.getHeight(), false);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    ni.setColorArgb(x, y, img.getRGB(x, y));
                }
            }
            return wtf.wyvern.utility.render.display.BufferUtil.registerDynamicTexture("media_thumb_", ni);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void clearCache() {
        // We can't easily close Identifiers in TextureManager without custom tracking, 
        // but we can just clear our cache of IDs. The JVM/Minecraft will clean up unreferenced textures eventually.
        textureCache.clear();
        currentTextureHash = "";
    }
}

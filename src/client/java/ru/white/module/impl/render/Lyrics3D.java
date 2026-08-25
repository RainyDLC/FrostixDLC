package ru.white.module.impl.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.math.Vec3d;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.event_impl.EventUpdate;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.other.Projection;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 3D Lyrics: слова песни из текущей медиасессии подтягиваются с LRCLIB
 * (синхронизированный LRC), нарезаются на куски по 3-5 слов и плавно
 * всплывают перед игроком в мировых координатах — появление/исчезание
 * с мягкими фейдами, как в клипах.
 */
@FieldDefaults(level = AccessLevel.PRIVATE)
@ModuleInfo(name = "3D Lyrics", desc = "Floating song lyrics in front of you", category = Category.RENDER)
public class Lyrics3D extends Module {

    public SliderSetting distance = new SliderSetting(this, "Дистанция", 5.0F, 3.0F, 10.0F, 0.5F);
    public SliderSetting size = new SliderSetting(this, "Размер текста", 9.0F, 6.0F, 14.0F, 0.5F);
    public SliderSetting wordsPerChunk = new SliderSetting(this, "Слов в строке", 4.0F, 3.0F, 5.0F, 1.0F);

    /** Кусок лирики: окно времени [start,end) и текст. */
    public record Chunk(long startMs, long endMs, String text, float lateral, float up) {}

    private static final long FADE_IN_MS = 260;
    private static final long FADE_OUT_MS = 320;

    private volatile List<Chunk> chunks = List.of();
    private volatile long basePosMs = 0L;
    private volatile long baseRealMs = 0L;
    private volatile boolean playing = false;
    private String lastTrackKey = "";

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nightix-lyrics");
        t.setDaemon(true);
        return t;
    });

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (!isEnabled()) return;
        if (mc.player == null || mc.world == null) return;
        if (mc.player.age % 5 != 0) return;

        executor.submit(this::pollMedia);
    }

    private void pollMedia() {
        try {
            IMediaSession session = MediaPlayerInfo.Instance.getMediaSessions().stream()
                    .max(java.util.Comparator.comparingInt(s -> s.getMedia().getPlaying() ? 1 : 0))
                    .orElse(null);
            if (session == null) {
                playing = false;
                return;
            }

            MediaInfo info = session.getMedia();
            if (info.getTitle().isEmpty() && info.getArtist().isEmpty()) {
                playing = false;
                return;
            }

            long pos = info.getPosition();
            long dur = info.getDuration();
            if (dur > 86_400_000L) { // микросекунды -> миллисекунды
                pos /= 1000L;
                dur /= 1000L;
            }

            basePosMs = pos;
            baseRealMs = System.currentTimeMillis();
            playing = info.getPlaying();

            String key = info.getArtist() + "|" + info.getTitle();
            if (!key.equals(lastTrackKey)) {
                lastTrackKey = key;
                chunks = List.of();
                String title = info.getTitle();
                String artist = info.getArtist();
                executor.submit(() -> fetchLyrics(title, artist));
            }
        } catch (Throwable ignored) {
        }
    }

    /** Загрузка синхронизированной лирики с LRCLIB (без ключей, публичный API). */
    private void fetchLyrics(String title, String artist) {
        try {
            String cleanTitle = cleanTitle(title);
            String url = "https://lrclib.net/api/search?track_name="
                    + URLEncoder.encode(cleanTitle, StandardCharsets.UTF_8)
                    + "&artist_name=" + URLEncoder.encode(artist, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Nightix/1.0 (Minecraft client)")
                    .timeout(java.time.Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> resp = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return;

            JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
            String synced = null;
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                String s = obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull()
                        ? obj.get("syncedLyrics").getAsString() : "";
                if (!s.isBlank()) {
                    synced = s;
                    break;
                }
            }
            if (synced == null) return;

            chunks = buildChunks(parseLrc(synced));
            System.out.println("[Lyrics3D] loaded " + chunks.size() + " chunks for: " + cleanTitle);
        } catch (Throwable t) {
            System.out.println("[Lyrics3D] fetch failed: " + t);
        }
    }

    /** Убираем из названия мусор вида "(Official Video)", "[HD]", "- Remastered 2011". */
    private static String cleanTitle(String title) {
        String t = title.replaceAll("\\(([^)]*)\\)", " ").replaceAll("\\[([^]]*)\\]", " ");
        t = t.replaceAll("(?i)\\b(official|video|audio|lyrics?|hd|4k|remaster(?:ed)?|mv|visualizer)\\b", " ");
        return t.replaceAll("\\s{2,}", " ").trim();
    }

    private record LyricLine(long timeMs, String text) {}

    /** Парсер LRC: [mm:ss.xx] текст, возможны несколько меток в строке. */
    private static List<LyricLine> parseLrc(String lrc) {
        Pattern stamp = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?\\]");
        List<LyricLine> out = new ArrayList<>();
        for (String raw : lrc.split("\n")) {
            Matcher m = stamp.matcher(raw);
            List<Long> times = new ArrayList<>();
            int last = 0;
            while (m.find() && m.start() == last) {
                long min = Long.parseLong(m.group(1));
                long sec = Long.parseLong(m.group(2));
                long frac = m.group(3) == null ? 0 : Long.parseLong(m.group(3));
                // сотые или тысячные — нормализуем к мс
                long ms = frac < 100 ? frac * 10 : (frac < 1000 ? frac : frac / 10);
                times.add(min * 60_000 + sec * 1000 + ms);
                last = m.end();
            }
            if (times.isEmpty()) continue;
            String text = raw.substring(last).trim();
            if (text.isEmpty()) continue;
            for (Long t : times) out.add(new LyricLine(t, text));
        }
        out.sort(java.util.Comparator.comparingLong(LyricLine::timeMs));
        return out;
    }

    /** Нарезка строк на куски по 3-5 слов с пропорциональным таймингом. */
    private static List<Chunk> buildChunks(List<LyricLine> lines) {
        List<Chunk> chunks = new ArrayList<>();
        int maxWords = 4;

        for (int i = 0; i < lines.size(); i++) {
            long start = lines.get(i).timeMs();
            long end = i + 1 < lines.size() ? lines.get(i + 1).timeMs() : start + 5000;
            if (end - start < 400) end = start + 400;

            String[] words = lines.get(i).text().split("\\s+");
            if (words.length == 0) continue;

            int per = maxWords;
            int chunkCount = Math.max(1, (int) Math.ceil(words.length / (double) per));
            double dur = end - start;

            int idx = 0;
            for (int c = 0; c < chunkCount; c++) {
                int from = idx;
                int count = Math.min(per, words.length - idx);
                if (count <= 0) break;
                idx += count;

                StringBuilder sb = new StringBuilder();
                for (int w = from; w < from + count; w++) sb.append(words[w]).append(' ');

                long cs = start + (long) (dur * from / (double) words.length);
                long ce = start + (long) (dur * idx / (double) words.length);

                // детерминированный разброс позиций: каждое появление в своём месте
                int seed = chunks.size() * 31 + 7;
                float lateral = (Float.intBitsToFloat(seed) % 1.0f) * 4.4f - 2.2f;
                float up = ((seed >>> 8) % 100) / 100.0f * 1.8f - 0.2f;

                chunks.add(new Chunk(cs, ce, sb.toString().trim(), lateral, up));
            }
        }
        return chunks;
    }

    @EventHandler
    public void onDisplay(EventDisplay e) {
        if (!isEnabled() || chunks.isEmpty()) return;
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
        if (Fonts.sf_medium == null) return;

        long now = System.currentTimeMillis();
        long pos = playing ? basePosMs + (now - baseRealMs) : basePosMs;

        Font font = Fonts.sf_medium;
        float fontSize = size.getValue();

        Vec3d eye = mc.player.getEyePos();
        Vec3d forward = mc.player.getRotationVec(1.0F).normalize();
        Vec3d right = new Vec3d(-forward.z, 0, forward.x).normalize();
        float dist = distance.getValue();

        for (Chunk c : chunks) {
            if (pos < c.startMs() - FADE_IN_MS || pos > c.endMs() + FADE_OUT_MS) continue;

            float alphaIn = Math.min(1.0F, Math.max(0.0F, (pos - c.startMs()) / (float) FADE_IN_MS));
            float alphaOut = Math.min(1.0F, Math.max(0.0F, (c.endMs() + FADE_OUT_MS - pos) / (float) FADE_OUT_MS));
            float alpha = Math.min(alphaIn, alphaOut);
            if (alpha <= 0.02F) continue;

            Vec3d world = eye
                    .add(forward.multiply(dist))
                    .add(right.multiply(c.lateral()))
                    .add(0, c.up(), 0);

            Vec3d screen = Projection.worldSpaceToScreenSpace(world);
            if (screen.z <= 0 || screen.z >= 1) continue;

            int a = (int) (255 * alpha);
            int color = (a << 24) | 0xFFFFFF;
            int shadow = ((int) (180 * alpha) << 24);

            // мягкая тень-подложка для читаемости и «свечения»
            font.drawCentered(c.text(), (float) screen.x + 0.7F, (float) screen.y + 0.7F, fontSize, (shadow & 0xFF000000) | 0x101018);
            font.drawCentered(c.text(), (float) screen.x, (float) screen.y, fontSize, color);
        }
    }

    @Override
    public void onDisable() {
        chunks = List.of();
        lastTrackKey = "";
        super.onDisable();
    }
}

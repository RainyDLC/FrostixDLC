package ru.white.module.impl.render.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class LrcLibClient {
    private static final String API_GET = "https://lrclib.net/api/get";
    private static final String API_SEARCH = "https://lrclib.net/api/search";

    // In-memory cache to avoid repeated HTTP calls on loop/rewind
    private static final Map<String, List<LyricLine>> LYRICS_CACHE = new ConcurrentHashMap<>();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private LrcLibClient() {}

    /**
     * Performs an asynchronous request with multi-tiered fallback search to fetch synced or plain lyrics.
     */
    public static CompletableFuture<List<LyricLine>> fetchLyricsAsync(String trackName, String artistName, long durationMs, boolean debugMode) {
        if (trackName == null || trackName.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String cacheKey = (trackName.trim() + " - " + (artistName != null ? artistName.trim() : "")).toLowerCase();
        if (LYRICS_CACHE.containsKey(cacheKey)) {
            List<LyricLine> cached = LYRICS_CACHE.get(cacheKey);
            if (!cached.isEmpty()) {
                if (debugMode) {
                    System.out.printf("[3D Lyrics] Взято из кэша: %s (%d строк)%n", cacheKey, cached.size());
                }
                return CompletableFuture.completedFuture(cached);
            }
        }

        return CompletableFuture.supplyAsync(() -> {
            String cleanTrack = cleanTrackName(trackName);
            String cleanArtist = artistName != null ? cleanTrackName(artistName) : "";

            if (debugMode) {
                System.out.printf("[3D Lyrics/Search] Поиск слов для: '%s' (автор: '%s')%n", cleanTrack, cleanArtist);
            }

            // Strategy 1: Exact GET endpoint
            if (!cleanArtist.isEmpty()) {
                List<LyricLine> lines = tryFetchGet(cleanTrack, cleanArtist, debugMode);
                if (!lines.isEmpty()) {
                    LYRICS_CACHE.put(cacheKey, lines);
                    return lines;
                }
            }

            // Strategy 2: Search with title + artist
            if (!cleanArtist.isEmpty()) {
                List<LyricLine> lines = tryFetchSearch(cleanTrack + " " + cleanArtist, durationMs, debugMode);
                if (!lines.isEmpty()) {
                    LYRICS_CACHE.put(cacheKey, lines);
                    return lines;
                }
            }

            // Strategy 3: Check if title contains ' - ' (e.g. 'Artist - Song' or 'Song - Artist')
            String[] splitParts = trackName.split("\\s+[-—–]\\s+");
            if (splitParts.length >= 2) {
                String p0 = cleanTrackName(splitParts[0]);
                String p1 = cleanTrackName(splitParts[1]);

                List<LyricLine> lines = tryFetchGet(p1, p0, debugMode);
                if (lines.isEmpty()) lines = tryFetchGet(p0, p1, debugMode);
                if (lines.isEmpty()) lines = tryFetchSearch(p0 + " " + p1, durationMs, debugMode);
                if (lines.isEmpty()) lines = tryFetchSearch(p1, durationMs, debugMode);
                if (lines.isEmpty()) lines = tryFetchSearch(p0, durationMs, debugMode);

                if (!lines.isEmpty()) {
                    LYRICS_CACHE.put(cacheKey, lines);
                    return lines;
                }
            }

            // Strategy 4: Search by clean track title alone (ignoring artist)
            List<LyricLine> lines = tryFetchSearch(cleanTrack, durationMs, debugMode);
            if (!lines.isEmpty()) {
                LYRICS_CACHE.put(cacheKey, lines);
                return lines;
            }

            // Strategy 5: If artist has at least 3 chars, try searching artist
            if (!cleanArtist.isEmpty() && cleanArtist.length() >= 3) {
                lines = tryFetchSearch(cleanArtist, durationMs, debugMode);
                if (!lines.isEmpty()) {
                    LYRICS_CACHE.put(cacheKey, lines);
                    return lines;
                }
            }

            if (debugMode) {
                System.out.printf("[3D Lyrics/Search] Слова не найдены на lrclib для: '%s'%n", trackName);
            }

            return Collections.emptyList();
        });
    }

    private static List<LyricLine> tryFetchGet(String trackName, String artistName, boolean debugMode) {
        String cleanTrack = cleanTrackName(trackName);
        String cleanArtist = artistName != null ? artistName.trim() : "";

        String query = String.format("?track_name=%s&artist_name=%s",
                URLEncoder.encode(cleanTrack, StandardCharsets.UTF_8),
                URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8));

        return executeGetRequest(API_GET + query, debugMode);
    }

    private static List<LyricLine> tryFetchSearch(String searchQuery, long durationMs, boolean debugMode) {
        if (searchQuery == null || searchQuery.isBlank()) return Collections.emptyList();

        String query = "?q=" + URLEncoder.encode(searchQuery.trim(), StandardCharsets.UTF_8);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_SEARCH + query))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonArray array = JsonParser.parseString(response.body()).getAsJsonArray();
                
                // Pass 1: Look for items with syncedLyrics
                for (JsonElement elem : array) {
                    if (elem.isJsonObject()) {
                        JsonObject obj = elem.getAsJsonObject();
                        if (obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull()) {
                            String syncedLrc = obj.get("syncedLyrics").getAsString();
                            List<LyricLine> parsed = LrcParser.parse(syncedLrc);
                            if (!parsed.isEmpty()) {
                                if (debugMode) {
                                    System.out.printf("[3D Lyrics] Найден синхронизированный текст по запросу: '%s'%n", searchQuery);
                                }
                                return parsed;
                            }
                        }
                    }
                }

                // Pass 2: Fallback to items with plainLyrics
                for (JsonElement elem : array) {
                    if (elem.isJsonObject()) {
                        JsonObject obj = elem.getAsJsonObject();
                        if (obj.has("plainLyrics") && !obj.get("plainLyrics").isJsonNull()) {
                            String plain = obj.get("plainLyrics").getAsString();
                            long trackDur = durationMs;
                            if (trackDur <= 0 && obj.has("duration") && !obj.get("duration").isJsonNull()) {
                                trackDur = (long) (obj.get("duration").getAsDouble() * 1000L);
                            }
                            List<LyricLine> parsed = LrcParser.parsePlain(plain, trackDur);
                            if (!parsed.isEmpty()) {
                                if (debugMode) {
                                    System.out.printf("[3D Lyrics] Найден обычный текст (plain lyrics) по запросу: '%s'%n", searchQuery);
                                }
                                return parsed;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (debugMode) {
                System.err.printf("[3D Lyrics/Debug] Ошибка поиска '%s': %s%n", searchQuery, e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    private static List<LyricLine> executeGetRequest(String url, boolean debugMode) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (json.has("syncedLyrics") && !json.get("syncedLyrics").isJsonNull()) {
                    String syncedLrc = json.get("syncedLyrics").getAsString();
                    List<LyricLine> parsed = LrcParser.parse(syncedLrc);
                    if (!parsed.isEmpty()) {
                        return parsed;
                    }
                }
                if (json.has("plainLyrics") && !json.get("plainLyrics").isJsonNull()) {
                    String plain = json.get("plainLyrics").getAsString();
                    long dur = 180_000L;
                    if (json.has("duration") && !json.get("duration").isJsonNull()) {
                        dur = (long) (json.get("duration").getAsDouble() * 1000L);
                    }
                    return LrcParser.parsePlain(plain, dur);
                }
            }
        } catch (Exception e) {
            if (debugMode) {
                System.err.printf("[3D Lyrics/Debug] Ошибка GET: %s%n", e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    private static String cleanTrackName(String trackName) {
        if (trackName == null) return "";
        return trackName
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("\\[.*?\\]", "")
                .replaceAll("(?i)feat\\..*$", "")
                .replaceAll("(?i)ft\\..*$", "")
                .trim();
    }
}

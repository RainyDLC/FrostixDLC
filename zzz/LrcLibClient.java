package wtf.wyvern.client.modules.impl.render.lyrics;

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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class LrcLibClient {
    private static final String API_GET = "https://lrclib.net/api/get";
    private static final String API_SEARCH = "https://lrclib.net/api/search";
    
    // In-memory cache to avoid repeated HTTP calls on loop/rewind
    private static final Map<String, List<LyricLine>> LYRICS_CACHE = new ConcurrentHashMap<>();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private LrcLibClient() {}

    /**
     * Performs an asynchronous request with caching, retry logic, and fallback search to fetch synced lyrics.
     */
    public static CompletableFuture<List<LyricLine>> fetchLyricsAsync(String trackName, String artistName, boolean debugMode) {
        if (trackName == null || trackName.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String cacheKey = (trackName.trim() + " - " + (artistName != null ? artistName.trim() : "")).toLowerCase();
        if (LYRICS_CACHE.containsKey(cacheKey)) {
            if (debugMode) {
                System.out.printf("[KineticLyrics] Взято из кэша: %s (%d строк)\n", cacheKey, LYRICS_CACHE.get(cacheKey).size());
            }
            return CompletableFuture.completedFuture(LYRICS_CACHE.get(cacheKey));
        }

        return CompletableFuture.supplyAsync(() -> {
            // Attempt 1: Exact GET endpoint
            List<LyricLine> lines = tryFetchGet(trackName, artistName, debugMode);
            if (!lines.isEmpty()) {
                LYRICS_CACHE.put(cacheKey, lines);
                return lines;
            }

            // Attempt 2: Search Fallback endpoint
            lines = tryFetchSearch(trackName, artistName, debugMode);
            if (!lines.isEmpty()) {
                LYRICS_CACHE.put(cacheKey, lines);
                return lines;
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

        return executeRequest(API_GET + query, debugMode);
    }

    private static List<LyricLine> tryFetchSearch(String trackName, String artistName, boolean debugMode) {
        String cleanTrack = cleanTrackName(trackName);
        String cleanArtist = artistName != null ? artistName.trim() : "";
        String searchQuery = (cleanTrack + " " + cleanArtist).trim();

        String query = "?q=" + URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_SEARCH + query))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(14))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonArray array = JsonParser.parseString(response.body()).getAsJsonArray();
                for (JsonElement elem : array) {
                    if (elem.isJsonObject()) {
                        JsonObject obj = elem.getAsJsonObject();
                        if (obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull()) {
                            String syncedLrc = obj.get("syncedLyrics").getAsString();
                            List<LyricLine> parsed = LrcParser.parse(syncedLrc);
                            if (!parsed.isEmpty()) {
                                if (debugMode) {
                                    System.out.println("[KineticLyrics] Найден текст через Search Fallback!");
                                }
                                return parsed;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (debugMode) {
                System.err.println("[KineticLyrics/Debug] Search Fallback Exception: " + e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    private static List<LyricLine> executeRequest(String url, boolean debugMode) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(Duration.ofSeconds(14))
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (json.has("syncedLyrics") && !json.get("syncedLyrics").isJsonNull()) {
                        String syncedLrc = json.get("syncedLyrics").getAsString();
                        return LrcParser.parse(syncedLrc);
                    }
                }
            } catch (Exception e) {
                if (debugMode) {
                    System.err.printf("[KineticLyrics/Debug] Попытка %d не удалась: %s\n", attempt, e.getMessage());
                }
                if (attempt < 2) {
                    try {
                        Thread.sleep(400);
                    } catch (InterruptedException ignored) {}
                }
            }
        }
        return Collections.emptyList();
    }

    private static String cleanTrackName(String trackName) {
        if (trackName == null) return "";
        return trackName
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("\\[.*?\\]", "")
                .trim();
    }
}

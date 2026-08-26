package ru.white.lyrics;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class LrcLibClient {
    private static final String API_GET = "https://lrclib.net/api/get";
    private static final String API_SEARCH = "https://lrclib.net/api/search";

    private static final Map<String, List<LyricLine>> LYRICS_CACHE = new ConcurrentHashMap<>();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private LrcLibClient() {}

    /** Асинхронная загрузка с кэшем, расширенной очисткой и многоуровневым поиском. */
    public static CompletableFuture<List<LyricLine>> fetchLyricsAsync(String trackName, String artistName, boolean debugMode) {
        if (trackName == null || trackName.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String cacheKey = (cleanTrackName(trackName) + " - " + cleanArtistName(artistName)).toLowerCase();
        if (LYRICS_CACHE.containsKey(cacheKey)) {
            return CompletableFuture.completedFuture(LYRICS_CACHE.get(cacheKey));
        }

        return CompletableFuture.supplyAsync(() -> {
            String cleanTrack = cleanTrackName(trackName);
            String cleanArtist = cleanArtistName(artistName);

            // 1. Попытка прямого GET по очищенным именам
            List<LyricLine> lines = tryFetchGet(cleanTrack, cleanArtist, debugMode);
            if (!lines.isEmpty()) {
                LYRICS_CACHE.put(cacheKey, lines);
                return lines;
            }

            // 2. Попытка поиска: cleanTrack + cleanArtist
            lines = tryFetchSearch(cleanTrack + " " + cleanArtist, debugMode);
            if (!lines.isEmpty()) {
                LYRICS_CACHE.put(cacheKey, lines);
                return lines;
            }

            // 3. Если у артиста несколько имён (feat, запятая, &), пробуем первого
            String firstArtist = getFirstArtist(cleanArtist);
            if (!firstArtist.isEmpty() && !firstArtist.equalsIgnoreCase(cleanArtist)) {
                lines = tryFetchSearch(cleanTrack + " " + firstArtist, debugMode);
                if (!lines.isEmpty()) {
                    LYRICS_CACHE.put(cacheKey, lines);
                    return lines;
                }
            }

            // 4. Поиск только по названию трека
            if (!cleanTrack.isEmpty()) {
                lines = tryFetchSearch(cleanTrack, debugMode);
                if (!lines.isEmpty()) {
                    LYRICS_CACHE.put(cacheKey, lines);
                    return lines;
                }
            }

            // 5. Поиск по сырым данным (на случай если чистка удалила что-то нужное)
            if (!trackName.equalsIgnoreCase(cleanTrack)) {
                lines = tryFetchSearch((trackName + " " + (artistName != null ? artistName : "")).trim(), debugMode);
                if (!lines.isEmpty()) {
                    LYRICS_CACHE.put(cacheKey, lines);
                    return lines;
                }
            }

            return Collections.emptyList();
        });
    }

    private static List<LyricLine> tryFetchGet(String cleanTrack, String cleanArtist, boolean debugMode) {
        if (cleanTrack.isEmpty()) return Collections.emptyList();

        String query = String.format("?track_name=%s&artist_name=%s",
                URLEncoder.encode(cleanTrack, StandardCharsets.UTF_8),
                URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8));

        return executeRequest(API_GET + query, debugMode);
    }

    private static List<LyricLine> tryFetchSearch(String searchQuery, boolean debugMode) {
        if (searchQuery.isBlank()) return Collections.emptyList();

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
                JsonElement root = JsonParser.parseString(response.body());
                if (root.isJsonArray()) {
                    JsonArray array = root.getAsJsonArray();
                    // Сначала ищем элемент с синхронизированными субтитрами
                    for (JsonElement elem : array) {
                        if (elem.isJsonObject()) {
                            JsonObject obj = elem.getAsJsonObject();
                            if (obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull()) {
                                String syncedLrc = obj.get("syncedLyrics").getAsString();
                                if (!syncedLrc.isBlank()) {
                                    List<LyricLine> parsed = LrcParser.parse(syncedLrc);
                                    if (!parsed.isEmpty()) {
                                        return parsed;
                                    }
                                }
                            }
                        }
                    }
                    // Если синхронизированных нет, проверяем plainLyrics как запасной вариант
                    for (JsonElement elem : array) {
                        if (elem.isJsonObject()) {
                            JsonObject obj = elem.getAsJsonObject();
                            if (obj.has("plainLyrics") && !obj.get("plainLyrics").isJsonNull()) {
                                String plain = obj.get("plainLyrics").getAsString();
                                if (!plain.isBlank()) {
                                    long durMs = 0L;
                                    if (obj.has("duration") && !obj.get("duration").isJsonNull()) {
                                        durMs = (long) (obj.get("duration").getAsDouble() * 1000.0);
                                    }
                                    List<LyricLine> parsed = LrcParser.parsePlain(plain, durMs);
                                    if (!parsed.isEmpty()) {
                                        return parsed;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (debugMode) {
                System.err.println("[Lyrics3D] search error for '" + searchQuery + "': " + e.getMessage());
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
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (json.has("syncedLyrics") && !json.get("syncedLyrics").isJsonNull()) {
                        String syncedLrc = json.get("syncedLyrics").getAsString();
                        if (!syncedLrc.isBlank()) {
                            return LrcParser.parse(syncedLrc);
                        }
                    }
                    if (json.has("plainLyrics") && !json.get("plainLyrics").isJsonNull()) {
                        String plain = json.get("plainLyrics").getAsString();
                        if (!plain.isBlank()) {
                            long durMs = 0L;
                            if (json.has("duration") && !json.get("duration").isJsonNull()) {
                                durMs = (long) (json.get("duration").getAsDouble() * 1000.0);
                            }
                            return LrcParser.parsePlain(plain, durMs);
                        }
                    }
                }
            } catch (Exception e) {
                if (debugMode) {
                    System.err.printf("[Lyrics3D] attempt %d failed: %s%n", attempt, e.getMessage());
                }
                if (attempt < 2) {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException ignored) {}
                }
            }
        }
        return Collections.emptyList();
    }

    public static String cleanTrackName(String trackName) {
        if (trackName == null) return "";
        String t = trackName;
        // Удаляем скобки () [] {} 【】 «»
        t = t.replaceAll("(?i)\\([^)]*\\)", " ");
        t = t.replaceAll("(?i)\\[[^]]*\\]", " ");
        t = t.replaceAll("(?i)\\{[^}]*\\}", " ");
        t = t.replaceAll("(?i)【[^】]*】", " ");
        t = t.replaceAll("(?i)«[^»]*»", " ");
        // Удаляем суффиксы feat, ft, prod, remix, slowed, remaster и т.д.
        t = t.replaceAll("(?i)\\b(feat\\.?|ft\\.?|prod\\.? by|remix|slowed|sped up|acoustic|remaster(ed)?|official video|audio|edit)\\b.*", " ");
        // Удаляем спецсимволы и эмодзи (✩, ★, ✦, ✧, ♥, ♡, ♫, ♪ и др.), оставляя буквы и цифры любых языков
        t = t.replaceAll("[^\\p{L}\\p{N}\\s\\-_']", " ");
        return t.replaceAll("\\s+", " ").trim();
    }

    public static String cleanArtistName(String artistName) {
        if (artistName == null) return "";
        String a = artistName;
        a = a.replaceAll("(?i)\\([^)]*\\)", " ");
        a = a.replaceAll("(?i)\\[[^]]*\\]", " ");
        a = a.replaceAll("(?i)【[^】]*】", " ");
        a = a.replaceAll("[^\\p{L}\\p{N}\\s\\-_',&]", " ");
        return a.replaceAll("\\s+", " ").trim();
    }

    private static String getFirstArtist(String artistName) {
        if (artistName == null) return "";
        String[] parts = artistName.split("[,&/]|\\b(?i)(feat\\.?|ft\\.?|x)\\b");
        return parts.length > 0 ? parts[0].trim() : "";
    }
}

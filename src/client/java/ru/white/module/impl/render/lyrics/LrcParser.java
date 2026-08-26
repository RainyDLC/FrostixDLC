package ru.white.module.impl.render.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LrcParser {
    // Regex matching all timestamp patterns: [mm:ss.xx], [m:ss.xx], [mm:ss:xx], [mm:ss.xxx], [mm:ss]
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[\\.:](\\d{1,3}))?\\]");

    private LrcParser() {}

    /**
     * Parses a raw LRC string into a chronologically sorted List of LyricLine.
     */
    public static List<LyricLine> parse(String lrcContent) {
        if (lrcContent == null || lrcContent.isBlank()) {
            return Collections.emptyList();
        }

        List<LyricLine> lines = new ArrayList<>();
        String[] rawLines = lrcContent.split("\\r?\\n");

        for (String line : rawLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            Matcher matcher = TIMESTAMP_PATTERN.matcher(trimmed);
            List<Long> timestamps = new ArrayList<>();
            int lastMatchEnd = 0;

            while (matcher.find()) {
                try {
                    long minutes = Long.parseLong(matcher.group(1));
                    long seconds = Long.parseLong(matcher.group(2));
                    String fractionStr = matcher.group(3);

                    long millis = 0;
                    if (fractionStr != null && !fractionStr.isEmpty()) {
                        millis = Long.parseLong(fractionStr);
                        if (fractionStr.length() == 1) {
                            millis *= 100;
                        } else if (fractionStr.length() == 2) {
                            millis *= 10;
                        }
                    }

                    long totalTimeMs = (minutes * 60_000L) + (seconds * 1_000L) + millis;
                    timestamps.add(totalTimeMs);
                    lastMatchEnd = matcher.end();
                } catch (NumberFormatException ignored) {
                }
            }

            if (!timestamps.isEmpty()) {
                String text = trimmed.substring(lastMatchEnd).trim();
                text = text.replaceAll("^\\s*\\[.*?\\]\\s*", "").trim();
                if (!text.isEmpty()) {
                    for (Long ts : timestamps) {
                        lines.add(new LyricLine(ts, text));
                    }
                }
            }
        }

        Collections.sort(lines);
        return lines;
    }

    /**
     * Parses plain text lyrics without timestamps by distributing lines across track duration.
     */
    public static List<LyricLine> parsePlain(String plainLyrics, long trackDurationMs) {
        if (plainLyrics == null || plainLyrics.isBlank()) {
            return Collections.emptyList();
        }

        List<String> validLines = new ArrayList<>();
        String[] rawLines = plainLyrics.split("\\r?\\n");

        for (String raw : rawLines) {
            String trimmed = raw.trim();
            // Skip structural markers like [Verse], [Chorus], [Intro] or empty lines
            if (trimmed.isEmpty() || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                continue;
            }
            validLines.add(trimmed);
        }

        if (validLines.isEmpty()) {
            return Collections.emptyList();
        }

        long totalDur = (trackDurationMs > 10_000L) ? trackDurationMs : 180_000L;
        long startOffset = Math.min(8000L, totalDur / 10);
        long usableDuration = Math.max(10_000L, totalDur - startOffset - 5000L);
        long step = Math.max(2200L, usableDuration / validLines.size());

        List<LyricLine> result = new ArrayList<>();
        for (int i = 0; i < validLines.size(); i++) {
            long ts = startOffset + (i * step);
            result.add(new LyricLine(ts, validLines.get(i)));
        }

        return result;
    }
}

package ru.white.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LrcParser {
    // Поддержка всех форматов: [mm:ss.xx], [m:ss.xxx], [mm:ss:xx], [mm:ss]
    private static final Pattern TAG_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");

    private LrcParser() {}

    public static List<LyricLine> parse(String lrcContent) {
        if (lrcContent == null || lrcContent.isBlank()) {
            return Collections.emptyList();
        }

        List<LyricLine> lines = new ArrayList<>();
        for (String rawLine : lrcContent.split("\\r?\\n")) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty()) continue;

            // Пропускаем служебные теги [ar:...], [ti:...], [length:...]
            if (trimmed.matches("^\\[[a-zA-Z]+:.*?\\]$")) continue;

            Matcher matcher = TAG_PATTERN.matcher(trimmed);
            List<Long> timestamps = new ArrayList<>();
            int lastEnd = 0;

            while (matcher.find()) {
                try {
                    long minutes = Long.parseLong(matcher.group(1));
                    long seconds = Long.parseLong(matcher.group(2));
                    String fractionStr = matcher.group(3);

                    long millis = 0L;
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
                    lastEnd = matcher.end();
                } catch (NumberFormatException ignored) {
                }
            }

            if (!timestamps.isEmpty()) {
                String text = trimmed.substring(lastEnd).trim();
                // Удаляем оставшиеся теги караоке <00:12.34> если есть
                text = text.replaceAll("<\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?>", "").trim();
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

    /** Резервный парсинг несинхронизированного текста по строкам. */
    public static List<LyricLine> parsePlain(String plainText, long durationMs) {
        if (plainText == null || plainText.isBlank()) {
            return Collections.emptyList();
        }

        String[] rawLines = plainText.split("\\r?\\n");
        List<String> valid = new ArrayList<>();
        for (String l : rawLines) {
            String s = l.trim();
            if (!s.isEmpty() && !s.startsWith("[") && !s.equalsIgnoreCase("Chorus") && !s.equalsIgnoreCase("Verse")) {
                valid.add(s);
            }
        }
        if (valid.isEmpty()) return Collections.emptyList();

        long dur = durationMs > 10_000L ? durationMs : (valid.size() * 3500L);
        long lineStep = Math.max(1500L, dur / valid.size());

        List<LyricLine> list = new ArrayList<>();
        for (int i = 0; i < valid.size(); i++) {
            list.add(new LyricLine(i * lineStep, valid.get(i)));
        }
        return list;
    }
}

package ru.white.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LrcParser {
    // [mm:ss.xx] или [mm:ss.xxx]
    private static final Pattern LRC_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)");

    private LrcParser() {}

    public static List<LyricLine> parse(String lrcContent) {
        if (lrcContent == null || lrcContent.isBlank()) {
            return Collections.emptyList();
        }

        List<LyricLine> lines = new ArrayList<>();
        for (String line : lrcContent.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            Matcher matcher = LRC_PATTERN.matcher(trimmed);
            if (matcher.matches()) {
                try {
                    long minutes = Long.parseLong(matcher.group(1));
                    long seconds = Long.parseLong(matcher.group(2));
                    String fractionStr = matcher.group(3);

                    long millis = Long.parseLong(fractionStr);
                    if (fractionStr.length() == 2) {
                        millis *= 10;
                    }

                    long totalTimeMs = (minutes * 60_000L) + (seconds * 1_000L) + millis;
                    String text = matcher.group(4).trim();

                    if (!text.isEmpty()) {
                        lines.add(new LyricLine(totalTimeMs, text));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        Collections.sort(lines);
        return lines;
    }
}

package ru.white.manager.event_impl;

import ru.white.manager.events.Event;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Setter
@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TextFactoryEvent extends Event {
    private static final Map<String, Pattern> REGEX_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Pattern> LITERAL_CACHE = new ConcurrentHashMap<>();

    String text;

    public void replaceText(String protect, String replaced) {
        if (text == null || text.isEmpty() || protect == null || protect.isEmpty()) return;

        Matcher matcher = LITERAL_CACHE
                .computeIfAbsent(protect, key -> Pattern.compile("(?ui)" + Pattern.quote(key)))
                .matcher(text);
        if (!matcher.find()) return;

        text = matcher.replaceAll(replaced);
    }

    public void replaceRegex(String regex, String replaced) {
        if (text == null || text.isEmpty()) return;

        Matcher matcher = REGEX_CACHE.computeIfAbsent(regex, Pattern::compile).matcher(text);
        if (!matcher.find()) return;

        text = matcher.replaceAll(replaced);
    }
}

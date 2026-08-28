package ru.white.lang;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class Lang {
    public enum Language { RUSSIAN, ENGLISH }

    private static final Path LANG_FILE = Path.of("C:/rainydlc/client1_21_11/language.txt");

    private static final Map<String, String> DICT = Translations.build();

    private static Language current = readSavedLanguage();

    private Lang() {}

    public static void init() {
        if (!Files.exists(LANG_FILE)) {
            String game = gameLanguageCode();
            boolean en = game != null && game.toLowerCase(java.util.Locale.ROOT).startsWith("en");
            current = en ? Language.ENGLISH : Language.RUSSIAN;
            saveLanguage();
        }
        System.out.println("[Lang] переводов в словаре: " + DICT.size() + ", язык: " + tag());
    }

    public static Language getLanguage() {
        return current;
    }

    public static boolean isEnglish() {
        return current == Language.ENGLISH;
    }

    public static String tag() {
        return current == Language.ENGLISH ? "EN" : "RU";
    }

    public static void setLanguage(Language language) {
        if (language == null || language == current) return;
        current = language;
        saveLanguage();
        applyToGame();
    }

    public static void toggle() {
        setLanguage(current == Language.ENGLISH ? Language.RUSSIAN : Language.ENGLISH);
    }

    private static void applyToGame() {
        try {
            var client = net.minecraft.client.MinecraftClient.getInstance();
            if (client == null || client.getLanguageManager() == null || client.options == null) return;

            String code = current == Language.ENGLISH ? "en_us" : "ru_ru";
            if (code.equalsIgnoreCase(client.getLanguageManager().getLanguage())) return;

            client.getLanguageManager().setLanguage(code);
            client.options.language = code;
            client.reloadResources();
        } catch (Exception ignored) {
        }
    }

    private static String gameLanguageCode() {
        try {
            var client = net.minecraft.client.MinecraftClient.getInstance();
            if (client == null || client.getLanguageManager() == null) return null;
            return client.getLanguageManager().getLanguage();
        } catch (Exception e) {
            return null;
        }
    }

    public static void reloadDict() {  }

    public static String tr(String ru) {
        if (ru == null || current != Language.ENGLISH) return ru;
        String en = DICT.get(ru);
        return en != null ? en : ru;
    }

    public static String pick(String ru, String en) {
        return current == Language.ENGLISH ? en : ru;
    }

    private static Language readSavedLanguage() {
        try {
            if (Files.exists(LANG_FILE)) {
                String s = Files.readString(LANG_FILE, StandardCharsets.UTF_8).trim();
                if (s.equalsIgnoreCase("EN") || s.equalsIgnoreCase("ENGLISH")) {
                    return Language.ENGLISH;
                }
            }
        } catch (Exception ignored) {}
        return Language.RUSSIAN;
    }

    private static void saveLanguage() {
        try {
            Files.createDirectories(LANG_FILE.getParent());
            Files.writeString(LANG_FILE, current == Language.ENGLISH ? "EN" : "RU", StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }
}

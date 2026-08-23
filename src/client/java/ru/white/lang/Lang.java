package ru.white.lang;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Локализация клиента: переключение РУС/АНГ.
 *
 * Все исходные строки в коде написаны на русском. На английском строка прогоняется
 * через словарь рус→англ из {@link Translations}. Если перевода нет — возвращается
 * исходная русская строка, поэтому ничего не ломается.
 *
 * Словарь зашит в код ({@link Translations#build()}), грузится один раз при загрузке
 * класса. Никаких json-ресурсов и файлов — поэтому ничего не "не находится" и {@link #tr}
 * это просто O(1) поиск по HashMap (без I/O, без лагов).
 */
public final class Lang {

    public enum Language { RUSSIAN, ENGLISH }

    /** Файл, где запоминается выбранный язык между сессиями. */
    private static final Path LANG_FILE = Path.of("C:/rainydlc/client1_21_11/language.txt");

    /** Словарь рус→англ. Строится один раз при инициализации класса. */
    private static final Map<String, String> DICT = Translations.build();

    private static Language current = readSavedLanguage();

    private Lang() {}

    /** Оставлено для совместимости со старым вызовом в Client. */
    public static void init() {
        // первый запуск (нет сохранённого файла) — стартуем с языка самой игры
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

    /** Короткая метка для кнопки в меню. */
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

    /**
     * Применяет выбранный язык к самой игре: меняет язык в настройках Minecraft
     * и перезагружает ресурсы — как это делает ванильный экран выбора языка.
     */
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
            // игра ещё не готова — язык применится при следующем переключении
        }
    }

    /** Текущий код языка игры (например «ru_ru») или null, если клиент ещё не создан. */
    private static String gameLanguageCode() {
        try {
            var client = net.minecraft.client.MinecraftClient.getInstance();
            if (client == null || client.getLanguageManager() == null) return null;
            return client.getLanguageManager().getLanguage();
        } catch (Exception e) {
            return null;
        }
    }

    /** Совместимость: словарь зашит в код, перечитывать нечего. */
    public static void reloadDict() { /* no-op */ }

    /**
     * Переводит русскую строку на текущий язык. На русском возвращает как есть.
     * На английском — значение из словаря либо исходную строку, если перевода нет.
     */
    public static String tr(String ru) {
        if (ru == null || current != Language.ENGLISH) return ru;
        String en = DICT.get(ru);
        return en != null ? en : ru;
    }

    /** Явный выбор строки по текущему языку (когда есть оба варианта). */
    public static String pick(String ru, String en) {
        return current == Language.ENGLISH ? en : ru;
    }

    // ----------------------------------------------------------------- saved language

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

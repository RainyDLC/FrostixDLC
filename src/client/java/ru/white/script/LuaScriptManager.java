package ru.white.script;

import ru.white.Client;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.utils.math.ChatUtils;
import org.luaj.vm2.LuaError;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Хранилище пользовательских Lua-модулей.
 *
 * Скрипты лежат в C:/rainydlc/client1_21_11/scripts/&lt;Категория&gt;/&lt;Имя&gt;.lua
 * и грузятся при старте клиента. Каждый скрипт получает собственное
 * изолированное окружение из {@link LuaSandbox}.
 */
public final class LuaScriptManager {

    private static final Path ROOT = Path.of("C:/rainydlc/client1_21_11/scripts");

    private static LuaScriptManager instance;

    /** Загруженные скрипты: файл → модуль. */
    private final Map<Path, LuaModule> loaded = new HashMap<>();

    private LuaScriptManager() {
    }

    public static synchronized LuaScriptManager get() {
        if (instance == null) instance = new LuaScriptManager();
        return instance;
    }

    public static Path root() {
        return ROOT;
    }

    /** Загрузка всех скриптов при старте клиента. */
    public synchronized void loadAll() {
        if (!Files.isDirectory(ROOT)) return;

        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(ROOT, 2)) {
            walk.filter(p -> !Files.isDirectory(p))
                    .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".lua"))
                    .forEach(files::add);
        } catch (IOException e) {
            ChatUtils.addChatMessage("§cLua: не удалось прочитать папку скриптов: " + e.getMessage());
            return;
        }
        files.sort(Comparator.comparing(Path::toString));

        for (Path file : files) {
            Category category = categoryOf(file);
            if (category == null) continue;
            try {
                String code = Files.readString(file, StandardCharsets.UTF_8);
                if (register(file, code, category) == null) {
                    // ошибка уже показана в register(); молча продолжаем остальные
                }
            } catch (IOException e) {
                ChatUtils.addChatMessage("§cLua: не удалось прочитать " + file.getFileName() + ": " + e.getMessage());
            }
        }
    }

    public record Result(LuaModule module, String error) {
        public boolean ok() {
            return module != null;
        }
    }

    /**
     * Создание нового скрипта: проверка синтаксиса → сохранение файла →
     * регистрация живого модуля в клик-ГУИ.
     */
    public synchronized Result create(Category category, String displayName, String code) {
        String name = sanitizeName(displayName);
        if (name.isEmpty()) return new Result(null, "Введите имя модуля");

        String syntax = validate(code);
        if (syntax != null) return new Result(null, syntax);

        Path dir = ROOT.resolve(category.getName());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            return new Result(null, "Не создать папку: " + e.getMessage());
        }

        Path file = dir.resolve(name + ".lua");
        int copy = 2;
        while (Files.exists(file)) {
            file = dir.resolve(name + "-" + (copy++) + ".lua");
        }

        try {
            Files.writeString(file, code, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new Result(null, "Не сохранить файл: " + e.getMessage());
        }

        LuaModule m = register(file, code, category);
        return m != null ? new Result(m, null)
                : new Result(null, "Ошибка выполнения скрипта (см. чат)");
    }

    /** Проверка синтаксиса без запуска. null — ошибок нет. */
    public static String validate(String code) {
        try {
            LuaSandbox.compilerGlobals().load(code, "=check");
            return null;
        } catch (LuaError e) {
            String msg = String.valueOf(e.getMessage());
            return msg.length() > 160 ? msg.substring(0, 160) + "…" : msg;
        }
    }

    /** Перезапись существующего скрипта: сохранить файл и перезагрузить модуль. */
    public synchronized Result update(Path file, Category category, String code) {
        String syntax = validate(code);
        if (syntax != null) return new Result(null, syntax);

        try {
            Files.writeString(file, code, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new Result(null, "Не сохранить файл: " + e.getMessage());
        }

        LuaModule m = register(file, code, category);
        return m != null ? new Result(m, null)
                : new Result(null, "Ошибка выполнения скрипта (см. чат)");
    }

    /** Открытые вкладки редактора: файл → категория (чтобы новые вкладки знали, куда сохранять). */
    public synchronized LuaModule moduleOf(Path file) {
        return loaded.get(file);
    }

    /** Удаление модуля: снять с регистрации и стереть .lua-файл с диска. */
    public synchronized boolean delete(LuaModule module) {
        Path file = module.getFile();
        unload(file);
        if (file == null) return true;
        try {
            boolean removed = Files.deleteIfExists(file);
            if (!removed) {
                ChatUtils.addChatMessage("§7[Lua] §cфайл не найден: " + file.getFileName());
            }
            return removed;
        } catch (IOException e) {
            ChatUtils.addChatMessage("§c[Lua] не удалось удалить файл: " + e.getMessage());
            return false;
        }
    }

    private synchronized LuaModule register(Path file, String code, Category category) {
        unload(file);

        // анонимный подкласс → уникальный ключ в ModuleManager на каждый скрипт
        LuaModule module = new LuaModule(file, category) {};
        try {
            module.initialize(code);
        } catch (LuaError e) {
            String msg = String.valueOf(e.getMessage());
            ChatUtils.addChatMessage("§cLua «" + file.getFileName() + "»: "
                    + msg.substring(0, Math.min(120, msg.length())));
            return null;
        }

        Module prev = Client.get().moduleManager().put(module.getClass(), module);
        loaded.put(file, module);
        return module;
    }

    private void unload(Path file) {
        LuaModule old = loaded.remove(file);
        if (old != null) {
            old.setEnabled(false, false);
            Client.get().moduleManager().remove(old.getClass());
        }
    }

    private static Category categoryOf(Path file) {
        if (file.getNameCount() < 2) return null;
        String dir = file.getParent().getFileName().toString();
        for (Category c : Category.values()) {
            if (c.getName().equalsIgnoreCase(dir)) return c;
        }
        return null;
    }

    private static String sanitizeName(String raw) {
        String cleaned = raw.replaceAll("[^\\w А-Яа-яЁё\\-]", "").trim();
        return cleaned.isEmpty() ? "" : cleaned.substring(0, Math.min(24, cleaned.length()));
    }
}

package dev.hatek.client.feature.config;

import dev.hatek.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Чат-команды клиента на префиксе «.». В чат на сервер ничего не уходит.
 *
 * <p>.cfg save &lt;имя&gt; — сохранить, .cfg load &lt;имя&gt; — загрузить,
 * .cfg list — список, .cfg reset — сброс до заводских, .cfg dir — открыть
 * папку конфигов (скачанный из браузера .json кидаешь туда), .cfg delete.</p>
 */
public final class ChatCommands {
    private ChatCommands() {
    }

    /** @return true — сообщение обработано, на сервер не отправлять. */
    public static boolean handle(String message) {
        if (message == null || !message.startsWith(".")) {
            return false;
        }
        String body = message.substring(1).trim();
        if (body.isEmpty()) {
            tell("Подсказка: §fcfg §7— конфиги (save/load/list/reset/dir)");
            return true;
        }
        String[] parts = body.split("\\s+");
        if (!parts[0].equalsIgnoreCase("cfg")) {
            tell("Неизвестная команда. Используй §f.cfg help");
            return true;
        }
        String sub = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "help";
        String arg = parts.length > 2 ? String.join(" ", List.of(parts).subList(2, parts.length)) : "";
        switch (sub) {
            case "save" -> {
                String name = arg.isBlank() ? ConfigManager.nextFreeName() : arg.strip();
                ConfigManager.save(name);
                tell("Конфиг сохранён: §f" + ConfigManager.loadedName());
            }
            case "load" -> {
                if (arg.isBlank()) {
                    list();
                    break;
                }
                ConfigEntry entry = ConfigManager.byName(arg);
                if (entry == null) {
                    tell("Нет конфига: §f" + arg.strip() + " §7(.cfg list)");
                    break;
                }
                ConfigManager.load(entry);
                tell("Конфиг загружен: §f" + entry.name());
            }
            case "list" -> list();
            case "reset" -> {
                ModuleManager.resetDefaults();
                tell("Конфиг сброшен до заводских");
            }
            case "dir" -> {
                ConfigManager.openFolder();
                tell("Папка открыта — кинь туда .json из браузера и напиши §f.cfg load <имя>");
            }
            case "delete" -> {
                if (arg.isBlank()) {
                    tell("Использование: §f.cfg delete <имя>");
                    break;
                }
                ConfigEntry entry = ConfigManager.byName(arg);
                if (entry == null) {
                    tell("Нет конфига: §f" + arg.strip());
                    break;
                }
                ConfigManager.delete(entry);
                tell("Конфиг удалён: §f" + entry.name());
            }
            default -> help();
        }
        return true;
    }

    private static void list() {
        List<ConfigEntry> all = ConfigManager.all();
        if (all.isEmpty()) {
            tell("Конфигов нет. Сохрани: §f.cfg save <имя>");
            return;
        }
        String loaded = ConfigManager.loadedName();
        StringBuilder out = new StringBuilder("Конфиги (" + all.size() + "): ");
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) {
                out.append("§7, ");
            }
            out.append("§f").append(all.get(i).name());
            if (all.get(i).name().equals(loaded)) {
                out.append("§a*");
            }
        }
        tell(out.toString());
    }

    private static void help() {
        tell("§fcfg save <имя>§7 — сохранить, §fcfg load <имя>§7 — загрузить");
        tell("§fcfg list§7 — список, §fcfg reset§7 — сброс до заводских");
        tell("§fcfg dir§7 — папка конфигов, §fcfg delete <имя>§7 — удалить");
    }

    private static void tell(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.chatListener().handleSystemMessage(
                    Component.literal("§8[§fHatek§8] §7" + text), false);
        }
    }
}

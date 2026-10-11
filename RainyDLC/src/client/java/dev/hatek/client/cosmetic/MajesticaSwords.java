package dev.hatek.client.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Список 3D-мечей из ресурспака Blades of Majestica.
 * Пак выбирает модель меча по его названию (компонент custom_name), поэтому
 * мы читаем названия прямо из включённого пака: assets/minecraft/items/netherite_sword.json.
 * Сам пак в клиент не входит — его нужно скачать и включить в "Пакетах ресурсов".
 */
public final class MajesticaSwords {
    public static final String PACK_URL = "modrinth.com/resourcepack/blades-of-majestica";

    private static final Identifier SOURCE = Identifier.withDefaultNamespace("items/netherite_sword.json");
    private static List<String> names = Collections.emptyList();

    private MajesticaSwords() {
    }

    /** Перечитывает названия из текущих ресурсов (вызывается при открытии колеса). */
    public static List<String> reload() {
        Set<String> found = new LinkedHashSet<>();
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(SOURCE);
            if (resource.isPresent()) {
                try (Reader reader = resource.get().openAsReader()) {
                    collect(JsonParser.parseReader(reader), found);
                }
            }
        } catch (Throwable ignored) {
            // Нет пака или битый JSON — просто пустой список.
        }
        names = Collections.unmodifiableList(new ArrayList<>(found));
        return names;
    }

    public static List<String> names() {
        return names;
    }

    public static boolean available() {
        return !names.isEmpty();
    }

    private static void collect(JsonElement node, Set<String> out) {
        if (node == null || node.isJsonNull()) {
            return;
        }
        if (node.isJsonArray()) {
            for (JsonElement child : node.getAsJsonArray()) {
                collect(child, out);
            }
            return;
        }
        if (!node.isJsonObject()) {
            return;
        }
        JsonObject object = node.getAsJsonObject();
        boolean byName = object.has("component")
                && object.get("component").isJsonPrimitive()
                && object.get("component").getAsString().endsWith("custom_name")
                && object.has("cases") && object.get("cases").isJsonArray();
        if (byName) {
            for (JsonElement entry : object.getAsJsonArray("cases")) {
                if (!entry.isJsonObject() || !entry.getAsJsonObject().has("when")) {
                    continue;
                }
                JsonElement when = entry.getAsJsonObject().get("when");
                if (when.isJsonPrimitive()) {
                    out.add(when.getAsString());
                } else if (when.isJsonArray()) {
                    JsonArray list = when.getAsJsonArray();
                    if (!list.isEmpty() && list.get(0).isJsonPrimitive()) {
                        out.add(list.get(0).getAsString());
                    }
                }
            }
        }
        for (String key : object.keySet()) {
            collect(object.get(key), out);
        }
    }
}

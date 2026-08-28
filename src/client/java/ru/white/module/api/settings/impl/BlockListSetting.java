package ru.white.module.api.settings.impl;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import ru.white.module.api.Module;
import ru.white.module.api.settings.Setting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class BlockListSetting extends Setting<List<String>> {
    public String input = "";

    public BlockListSetting(Module parent, String name) {
        super(parent, name, new ArrayList<>());
    }

    public List<String> getIds() {
        return getValue();
    }

    public boolean addId(String raw) {
        String id = normalize(raw);
        if (id == null || getValue().contains(id)) return false;
        getValue().add(id);
        return true;
    }

    public boolean addCurrentInput() {
        if (addId(input)) {
            input = "";
            return true;
        }
        return false;
    }

    public void remove(String id) {
        getValue().remove(id);
    }

    public Set<Block> resolveBlocks() {
        Set<Block> blocks = new HashSet<>();
        for (String id : getValue()) {
            Identifier ident = Identifier.tryParse(id);
            if (ident != null) {
                Registries.BLOCK.getOptionalValue(ident).ifPresent(blocks::add);
            }
        }
        return blocks;
    }

    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase();
        if (s.isEmpty()) return null;
        if (!s.contains(":")) s = "minecraft:" + s;
        Identifier id = Identifier.tryParse(s);
        if (id == null || !Registries.BLOCK.containsId(id)) return null;
        return s;
    }

    public static String shortName(String id) {
        int i = id.indexOf(':');
        return i >= 0 ? id.substring(i + 1) : id;
    }

    private static List<String> ALL_IDS;
    private static String[] SEARCH_KEYS;
    private static final java.util.Map<String, String> DISPLAY = new java.util.HashMap<>();
    private static final java.util.Map<String, Integer> SWATCH = new java.util.HashMap<>();

    private static void buildCache() {
        List<String> ids = new ArrayList<>();
        try {
            for (Identifier id : Registries.BLOCK.getIds()) ids.add(id.toString());
        } catch (Exception ignored) {}
        ids.sort(String::compareTo);

        String[] keys = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            String name;
            int sw;
            try { name = computeDisplayName(id); } catch (Exception e) { name = shortName(id); }
            try { sw = computeSwatch(id); } catch (Exception e) { sw = 0xFF555555; }
            DISPLAY.put(id, name);
            SWATCH.put(id, sw);
            keys[i] = (shortName(id) + " " + name + " " + id).toLowerCase();
        }

        SEARCH_KEYS = keys;
        ALL_IDS = ids;
    }

    public static List<String> allIds() {
        if (ALL_IDS == null) buildCache();
        return ALL_IDS;
    }

    public static List<String> search(String query) {
        if (ALL_IDS == null) buildCache();
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) return ALL_IDS;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < ALL_IDS.size(); i++) {
            if (SEARCH_KEYS[i].contains(q)) out.add(ALL_IDS.get(i));
        }
        return out;
    }

    public static String displayName(String id) {
        return DISPLAY.computeIfAbsent(id, BlockListSetting::computeDisplayName);
    }

    public static int swatchColor(String id) {
        return SWATCH.computeIfAbsent(id, BlockListSetting::computeSwatch);
    }

    private static String computeDisplayName(String id) {
        Identifier ident = Identifier.tryParse(id);
        if (ident != null) {
            var block = Registries.BLOCK.getOptionalValue(ident);
            if (block.isPresent()) return block.get().getName().getString();
        }
        return shortName(id);
    }

    private static int computeSwatch(String id) {
        Identifier ident = Identifier.tryParse(id);
        if (ident != null) {
            var block = Registries.BLOCK.getOptionalValue(ident);
            if (block.isPresent()) {
                int c = block.get().getDefaultMapColor().color;
                if (c != 0) return 0xFF000000 | (c & 0xFFFFFF);
            }
        }
        return 0xFF555555;
    }

    @Override
    public BlockListSetting setVisible(Supplier<Boolean> value) {
        return (BlockListSetting) super.setVisible(value);
    }
}

package ru.white.module.api.settings.impl;

import lombok.Getter;
import ru.white.module.api.Module;
import ru.white.module.api.settings.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Список анархий с пресетами, управляется полностью из клик гуи.
 * Первая анархия в пресете — «домашняя» (там лежит сундук с воронкой, туда сдаётся лут),
 * остальные — фарм анархии, по которым бот крутится.
 * Сохраняется через ConfigManager, поэтому живёт между перезаходами.
 */
@Getter
public class AnarchySetting extends Setting<List<List<Integer>>> {
    public static final int PRESETS = 4;
    public static final int MAX_PER_PRESET = 12;

    private int active;
    public String input = "";

    public AnarchySetting(Module parent, String name) {
        super(parent, name, empty());
    }

    private static List<List<Integer>> empty() {
        List<List<Integer>> presets = new ArrayList<>();
        for (int i = 0; i < PRESETS; i++) presets.add(new ArrayList<>());
        return presets;
    }

    public List<List<Integer>> presets() {
        List<List<Integer>> presets = getValue();
        while (presets.size() < PRESETS) presets.add(new ArrayList<>());
        return presets;
    }

    public List<Integer> preset(int index) {
        return presets().get(clamp(index));
    }

    public List<Integer> list() {
        return preset(active);
    }

    public void setActive(int index) {
        this.active = clamp(index);
    }

    private static int clamp(int index) {
        return Math.max(0, Math.min(PRESETS - 1, index));
    }

    public boolean add(int anarchy) {
        if (anarchy < 0 || anarchy > 999) return false;
        List<Integer> list = list();
        if (list.size() >= MAX_PER_PRESET || list.contains(anarchy)) return false;
        return list.add(anarchy);
    }

    public boolean commitInput() {
        String raw = input.replaceAll("[^0-9]", "");
        input = "";
        if (raw.isEmpty()) return false;
        try {
            return add(Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void remove(int anarchy) {
        list().remove((Integer) anarchy);
    }

    public void clear() {
        list().clear();
    }

    public int size() {
        return list().size();
    }

    public boolean isEmpty() {
        return list().isEmpty();
    }

    /** «Домашняя» анархия — куда сдаём лут. -1 если список пустой. */
    public int home() {
        return list().isEmpty() ? -1 : list().get(0);
    }

    /** Анархия по индексу с оборачиванием по кругу. -1 если список пустой. */
    public int at(int index) {
        List<Integer> list = list();
        if (list.isEmpty()) return -1;
        return list.get(((index % list.size()) + list.size()) % list.size());
    }

    /** Делает анархию домашней (двигает в начало списка). */
    public void moveToFront(int anarchy) {
        if (anarchy < 0) return;
        List<Integer> list = list();
        list.remove((Integer) anarchy);
        list.add(0, anarchy);
        while (list.size() > MAX_PER_PRESET) list.remove(list.size() - 1);
    }

    @Override
    public AnarchySetting setVisible(Supplier<Boolean> value) {
        return (AnarchySetting) super.setVisible(value);
    }
}

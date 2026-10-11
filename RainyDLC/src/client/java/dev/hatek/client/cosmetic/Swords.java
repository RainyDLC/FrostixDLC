package dev.hatek.client.cosmetic;

import static dev.hatek.client.cosmetic.SwordModel.bob;
import static dev.hatek.client.cosmetic.SwordModel.flicker;
import static dev.hatek.client.cosmetic.SwordModel.flow;
import static dev.hatek.client.cosmetic.SwordModel.orbit;
import static dev.hatek.client.cosmetic.SwordModel.pulse;
import static dev.hatek.client.cosmetic.SwordModel.rainbow;
import static dev.hatek.client.cosmetic.SwordModel.solid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Каталог кастомных 3D-мечей. Индекс 0 — обычный меч Minecraft. */
public final class Swords {
    public static final String DEFAULT = "Default";

    private static final List<SwordModel> ALL = new ArrayList<>();

    static {
        ALL.add(katana());
        ALL.add(excalibur());
        ALL.add(dragonSlayer());
        ALL.add(frostbite());
        ALL.add(inferno());
        ALL.add(voidEdge());
        ALL.add(prism());
        ALL.add(plasmaSaber());
        ALL.add(crimsonRapier());
        ALL.add(thunderFang());
        ALL.add(boneCleaver());
        ALL.add(sakura());
        ALL.add(obsidian());
        ALL.add(royal());
        ALL.add(tidecaller());
        ALL.add(crystalShard());
    }

    private Swords() {
    }

    public static List<SwordModel> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static SwordModel byName(String name) {
        for (SwordModel model : ALL) {
            if (model.name().equalsIgnoreCase(name)) {
                return model;
            }
        }
        return null;
    }

    public static String[] names() {
        String[] out = new String[ALL.size() + 1];
        out[0] = DEFAULT;
        for (int i = 0; i < ALL.size(); i++) {
            out[i + 1] = ALL.get(i).name();
        }
        return out;
    }

    // ------------------------------------------------------------------ статичные

    private static SwordModel katana() {
        SwordModel.Builder b = SwordModel.builder("Katana").accent(0xFFD8DEE6);
        b.bar(0.0f, 1.0f, 0.9f, 0.9f, 0xFF2A2A2E);
        b.grip(1.0f, 7.0f, 0.75f, 0xFF1B1B1F, 0xFFB3262E);
        b.box(-2.2f, 7.0f, -1.6f, 2.2f, 7.8f, 1.6f, 0xFFC9A44A);
        b.bar(7.8f, 8.6f, 0.95f, 0.8f, 0xFFC9A44A);
        // Изогнутый клинок: сегменты со смещением по X.
        for (int i = 0; i < 15; i++) {
            float y = 8.6f + i;
            float bend = 0.012f * i * i;
            float w = i > 12 ? 0.9f - (i - 12) * 0.25f : 0.9f;
            b.box(-w + bend, y, -0.3f, w + bend, y + 1.0f, 0.3f, 0xFFE4E9EF);
            b.box(-w + bend, y, -0.32f, -w + bend + 0.45f, y + 1.0f, 0.32f, 0xFF9BA4AE);
        }
        return b.build();
    }

    private static SwordModel excalibur() {
        SwordModel.Builder b = SwordModel.builder("Excalibur").accent(0xFF4A86FF);
        b.bar(0.0f, 1.4f, 1.1f, 1.1f, 0xFFE2B33C);
        b.bar(0.3f, 1.1f, 0.6f, 1.25f, 0xFF3D7BFF);
        b.grip(1.4f, 5.6f, 0.7f, 0xFF4B2E1A, 0xFF6A4226);
        b.box(-4.2f, 5.6f, -0.9f, 4.2f, 6.8f, 0.9f, 0xFFE2B33C);
        b.box(-4.8f, 5.9f, -0.7f, -4.2f, 7.4f, 0.7f, 0xFFE2B33C);
        b.box(4.2f, 5.9f, -0.7f, 4.8f, 7.4f, 0.7f, 0xFFE2B33C);
        b.box(-0.6f, 5.8f, -1.05f, 0.6f, 6.6f, 1.05f, 0xFF3D7BFF);
        b.blade(6.8f, 16.0f, 1.6f, 0.55f, 4, 0xFFE8EEF6, 0xFFB7C3D3);
        return b.build();
    }

    private static SwordModel dragonSlayer() {
        SwordModel.Builder b = SwordModel.builder("Dragon Slayer").accent(0xFF8A8F99);
        b.bar(0.0f, 1.2f, 1.0f, 1.0f, 0xFF3A3A40);
        b.grip(1.2f, 6.0f, 0.8f, 0xFF2B2018, 0xFF3D2E22);
        b.box(-3.0f, 6.0f, -1.2f, 3.0f, 7.2f, 1.2f, 0xFF4A4C52);
        b.bar(7.2f, 21.0f, 2.6f, 0.75f, 0xFF7C818B);
        b.bar(7.2f, 21.0f, 1.2f, 0.95f, 0xFF5C6069);
        b.box(-2.6f, 21.0f, -0.7f, 2.6f, 22.0f, 0.7f, 0xFF7C818B);
        b.box(-2.2f, 22.0f, -0.6f, 1.6f, 23.0f, 0.6f, 0xFF7C818B);
        // Зазубрины по краю.
        for (int i = 0; i < 4; i++) {
            float y = 9.0f + i * 3.0f;
            b.box(2.6f, y, -0.4f, 3.1f, y + 1.0f, 0.4f, 0xFF9AA0AA);
        }
        return b.build();
    }

    private static SwordModel crimsonRapier() {
        SwordModel.Builder b = SwordModel.builder("Crimson Rapier").accent(0xFFD0203A);
        b.bar(0.0f, 1.2f, 0.9f, 0.9f, 0xFFB01A2E);
        b.grip(1.2f, 5.2f, 0.6f, 0xFF2A0D12, 0xFF4A1620);
        // Корзинчатая гарда.
        b.box(-2.6f, 5.2f, -2.0f, 2.6f, 6.0f, 2.0f, 0xFFB8BFC8);
        b.box(-2.6f, 2.0f, 1.4f, -2.0f, 5.2f, 2.0f, 0xFFB8BFC8);
        b.box(2.0f, 3.0f, -2.0f, 2.6f, 5.2f, -1.4f, 0xFFB8BFC8);
        b.box(-0.9f, 6.0f, -0.9f, 0.9f, 6.8f, 0.9f, 0xFFD0203A);
        b.blade(6.8f, 17.0f, 0.55f, 0.4f, 3, 0xFFE2E6EC, 0xFFC0C6CE);
        return b.build();
    }

    private static SwordModel boneCleaver() {
        SwordModel.Builder b = SwordModel.builder("Bone Cleaver").accent(0xFFE8DFC6);
        b.bar(0.0f, 1.6f, 1.2f, 1.2f, 0xFFE8DFC6);
        b.bar(1.6f, 6.0f, 0.75f, 0.75f, 0xFFD6CBAF);
        b.bar(2.6f, 3.1f, 0.85f, 0.85f, 0xFF8C7F63);
        b.bar(4.4f, 4.9f, 0.85f, 0.85f, 0xFF8C7F63);
        b.box(-1.8f, 6.0f, -1.0f, 1.8f, 7.0f, 1.0f, 0xFF8C7F63);
        b.box(-1.0f, 7.0f, -0.6f, 3.8f, 19.0f, 0.6f, 0xFFEEE6D0);
        b.box(-1.0f, 7.0f, -0.7f, 0.0f, 19.0f, 0.7f, 0xFFCFC4A6);
        b.box(-1.0f, 19.0f, -0.6f, 3.0f, 20.0f, 0.6f, 0xFFEEE6D0);
        for (int i = 0; i < 3; i++) {
            float y = 10.0f + i * 3.0f;
            b.box(1.0f, y, -0.75f, 2.6f, y + 0.6f, 0.75f, 0xFFB4A684);
        }
        return b.build();
    }

    private static SwordModel royal() {
        SwordModel.Builder b = SwordModel.builder("Royal Gold").accent(0xFFF2C23A);
        b.bar(0.0f, 1.4f, 1.2f, 1.2f, 0xFFF2C23A);
        b.bar(0.3f, 1.1f, 0.5f, 1.35f, 0xFFE0263E);
        b.grip(1.4f, 5.6f, 0.7f, 0xFF7A1424, 0xFFF2C23A);
        b.box(-3.6f, 5.6f, -1.0f, 3.6f, 6.8f, 1.0f, 0xFFF2C23A);
        b.box(-3.6f, 6.8f, -0.8f, -2.6f, 7.8f, 0.8f, 0xFFF2C23A);
        b.box(2.6f, 6.8f, -0.8f, 3.6f, 7.8f, 0.8f, 0xFFF2C23A);
        b.box(-0.7f, 5.8f, -1.15f, 0.7f, 6.6f, 1.15f, 0xFF35D07F);
        b.blade(6.8f, 16.0f, 1.4f, 0.55f, 4, 0xFFF7D774, 0xFFD9A62A);
        return b.build();
    }

    // ------------------------------------------------------------------ анимированные

    private static SwordModel frostbite() {
        SwordModel.Builder b = SwordModel.builder("Frostbite").accent(0xFF7FE3FF).animated();
        b.bar(0.0f, 1.3f, 1.0f, 1.0f, 0xFFBFEFFF);
        b.grip(1.3f, 5.6f, 0.7f, 0xFF2C4A66, 0xFF3E6A8E);
        b.box(-3.0f, 5.6f, -1.0f, 3.0f, 6.6f, 1.0f, pulse(0xFF9DE8FF, 0xFFE6FBFF, 0.6f), true, null);
        b.box(-3.6f, 6.0f, -0.6f, -3.0f, 8.2f, 0.6f, solid(0xFFBFEFFF), true, null);
        b.box(3.0f, 6.0f, -0.6f, 3.6f, 8.2f, 0.6f, solid(0xFFBFEFFF), true, null);
        b.blade(6.6f, 16.0f, 1.5f, 0.55f, 5,
                flow(0xCC8FE6FF, 0xCCE9FCFF, 0.5f, 0.08f),
                pulse(0xEE4FC8FF, 0xEEBDF2FF, 0.8f), true);
        // Ледяные кристаллы, парящие вокруг клинка.
        for (int i = 0; i < 3; i++) {
            float y = 11.0f + i * 4.0f;
            b.box(-0.4f, y, -0.4f, 0.4f, y + 0.8f, 0.4f, solid(0xDDE6FBFF), true,
                    orbit(2.8f, 0.35f, i / 3.0f, 0.6f));
        }
        return b.build();
    }

    private static SwordModel inferno() {
        SwordModel.Builder b = SwordModel.builder("Inferno").accent(0xFFFF7A1A).animated();
        b.bar(0.0f, 1.3f, 1.0f, 1.0f, 0xFF2A1A12);
        b.grip(1.3f, 5.6f, 0.7f, 0xFF1C120D, 0xFF3A2216);
        b.box(-3.2f, 5.6f, -1.0f, 3.2f, 6.8f, 1.0f, flicker(0xFFFF4A0A, 0xFFFFB02A, 1.2f), true, null);
        b.blade(6.8f, 16.0f, 1.6f, 0.6f, 4,
                flicker(0xFFFF3A00, 0xFFFFC23A, 1.5f),
                flicker(0xFFFFE07A, 0xFFFF7A1A, 1.9f), true);
        // Язычки пламени, поднимающиеся вдоль клинка.
        for (int i = 0; i < 4; i++) {
            float y = 9.0f + i * 3.5f;
            float side = i % 2 == 0 ? 1.0f : -1.0f;
            b.box(side * 1.6f - 0.35f, y, -0.35f, side * 1.6f + 0.35f, y + 0.9f, 0.35f,
                    flicker(0xFFFF6A00, 0xFFFFE07A, 2.2f), true, bob(0.9f, 0.9f, i * 0.25f));
        }
        return b.build();
    }

    private static SwordModel voidEdge() {
        SwordModel.Builder b = SwordModel.builder("Void Edge").accent(0xFFA24BFF).animated();
        b.bar(0.0f, 1.3f, 1.0f, 1.0f, 0xFF140A1F);
        b.grip(1.3f, 5.8f, 0.7f, 0xFF0D0614, 0xFF2A1240);
        b.box(-3.4f, 5.8f, -0.9f, 3.4f, 6.8f, 0.9f, 0xFF1B0F2A);
        b.box(-0.7f, 5.9f, -1.05f, 0.7f, 6.7f, 1.05f, pulse(0xFF7A2BFF, 0xFFE0B4FF, 0.7f), true, null);
        b.blade(6.8f, 16.0f, 1.6f, 0.55f, 4,
                solid(0xFF1A0E28),
                flow(0xFF6A1BFF, 0xFFE6B8FF, 0.7f, 0.07f), true);
        for (int i = 0; i < 4; i++) {
            b.box(-0.35f, 14.0f, -0.35f, 0.35f, 14.7f, 0.35f, solid(0xFFC48BFF), true,
                    orbit(3.2f, 0.25f, i / 4.0f, 6.0f));
        }
        return b.build();
    }

    private static SwordModel prism() {
        SwordModel.Builder b = SwordModel.builder("Prism").accent(0xFFFF5ACD).animated();
        b.bar(0.0f, 1.3f, 1.0f, 1.0f, rainbow(0.3f, 0.0f, 0.7f, 1.0f), true);
        b.grip(1.3f, 5.6f, 0.7f, 0xFF1E1E24, 0xFF33333C);
        b.bar(5.6f, 6.8f, 3.0f, 0.9f, rainbow(0.3f, 0.02f, 0.75f, 1.0f), true);
        b.blade(6.8f, 16.0f, 1.5f, 0.55f, 4,
                rainbow(0.4f, 0.05f, 0.65f, 1.0f),
                rainbow(0.4f, 0.05f, 0.35f, 1.0f), true);
        return b.build();
    }

    private static SwordModel plasmaSaber() {
        SwordModel.Builder b = SwordModel.builder("Plasma Saber").accent(0xFF3DFF7A).animated();
        b.bar(0.0f, 1.0f, 0.9f, 0.9f, 0xFF2E3238);
        b.bar(1.0f, 6.2f, 0.75f, 0.75f, 0xFFB9C0C9);
        b.bar(2.0f, 2.6f, 0.85f, 0.85f, 0xFF2E3238);
        b.bar(3.4f, 4.0f, 0.85f, 0.85f, 0xFF2E3238);
        b.box(0.75f, 4.6f, -0.25f, 1.1f, 5.2f, 0.25f, pulse(0xFFFF3030, 0xFF7A1010, 1.5f), true, null);
        b.bar(6.2f, 7.0f, 1.0f, 1.0f, 0xFF2E3238);
        // Светящийся клинок: яркое ядро + полупрозрачный ореол.
        b.bar(7.0f, 22.5f, 0.35f, 0.35f, pulse(0xFFEFFFF2, 0xFFFFFFFF, 2.0f), true);
        b.bar(7.0f, 22.8f, 0.7f, 0.7f, pulse(0x993DFF7A, 0xCC3DFF7A, 2.0f), true);
        return b.build();
    }

    private static SwordModel thunderFang() {
        SwordModel.Builder b = SwordModel.builder("Thunder Fang").accent(0xFFFFE23A).animated();
        b.bar(0.0f, 1.3f, 1.0f, 1.0f, 0xFF1F2A44);
        b.grip(1.3f, 5.6f, 0.7f, 0xFF1F2A44, 0xFF34508A);
        b.box(-2.8f, 5.6f, -0.9f, 2.8f, 6.6f, 0.9f, 0xFF34508A);
        // Клинок-зигзаг.
        float[] shift = {0.0f, 0.4f, 0.8f, 0.4f, 0.0f, -0.4f, -0.8f, -0.4f, 0.0f, 0.4f, 0.8f, 0.4f, 0.0f, -0.3f, -0.4f};
        for (int i = 0; i < shift.length; i++) {
            float y = 6.6f + i;
            float w = i > 11 ? 1.2f - (i - 11) * 0.3f : 1.2f;
            b.box(shift[i] - w, y, -0.45f, shift[i] + w, y + 1.0f, 0.45f,
                    flicker(0xFFFFD21A, 0xFFFFFFE0, 2.5f), true, null);
        }
        for (int i = 0; i < 3; i++) {
            float y = 9.0f + i * 4.0f;
            b.box(1.8f, y, -0.25f, 2.6f, y + 0.4f, 0.25f, flicker(0x00FFFFFF, 0xFFBFE8FF, 3.0f), true, null);
            b.box(-2.6f, y + 2.0f, -0.25f, -1.8f, y + 2.4f, 0.25f, flicker(0x00FFFFFF, 0xFFBFE8FF, 2.7f), true, null);
        }
        return b.build();
    }

    private static SwordModel sakura() {
        SwordModel.Builder b = SwordModel.builder("Sakura").accent(0xFFFF9EC8).animated();
        b.bar(0.0f, 1.0f, 0.9f, 0.9f, 0xFF3A1E2A);
        b.grip(1.0f, 6.6f, 0.7f, 0xFFF6F0F2, 0xFFFF9EC8);
        b.box(-2.0f, 6.6f, -2.0f, 2.0f, 7.3f, 2.0f, 0xFF3A1E2A);
        b.blade(7.3f, 15.5f, 0.95f, 0.35f, 3,
                flow(0xFFFFD3E6, 0xFFFF8DBE, 0.35f, 0.06f), solid(0xFFFFF2F8), false);
        for (int i = 0; i < 5; i++) {
            float y = 10.0f + i * 2.5f;
            b.box(-0.4f, y, -0.15f, 0.4f, y + 0.6f, 0.15f, solid(0xFFFFB3D4), true,
                    orbit(2.4f + (i % 2) * 0.6f, 0.18f + i * 0.02f, i / 5.0f, 1.2f));
        }
        return b.build();
    }

    private static SwordModel obsidian() {
        SwordModel.Builder b = SwordModel.builder("Obsidian").accent(0xFFFF5A1F).animated();
        b.bar(0.0f, 1.4f, 1.2f, 1.2f, 0xFF16121C);
        b.grip(1.4f, 6.0f, 0.8f, 0xFF120E16, 0xFF241C2C);
        b.box(-3.4f, 6.0f, -1.1f, 3.4f, 7.2f, 1.1f, 0xFF1C1724);
        b.bar(7.2f, 20.5f, 2.0f, 0.65f, 0xFF1E1826);
        b.bar(20.5f, 21.5f, 1.4f, 0.6f, 0xFF1E1826);
        b.bar(21.5f, 22.5f, 0.7f, 0.55f, 0xFF1E1826);
        // Лавовые трещины пульсируют.
        b.bar(7.6f, 20.0f, 0.35f, 0.75f, pulse(0xFFFF3A0A, 0xFFFFB03A, 0.5f), true);
        for (int i = 0; i < 4; i++) {
            float y = 9.0f + i * 3.0f;
            float s = i % 2 == 0 ? 1.0f : -1.0f;
            b.box(s * 0.35f, y, -0.72f, s * 1.5f, y + 0.4f, 0.72f, pulse(0xFFFF3A0A, 0xFFFFB03A, 0.5f), true, null);
        }
        return b.build();
    }

    private static SwordModel tidecaller() {
        SwordModel.Builder b = SwordModel.builder("Tidecaller").accent(0xFF2FB8FF).animated();
        b.bar(0.0f, 1.3f, 1.0f, 1.0f, 0xFF0E3B5A);
        b.grip(1.3f, 5.6f, 0.7f, 0xFF0E3B5A, 0xFF1C6A8E);
        b.box(-3.0f, 5.6f, -0.9f, 3.0f, 6.6f, 0.9f, 0xFFD9B44A);
        // Волнистый клинок.
        for (int i = 0; i < 15; i++) {
            float y = 6.6f + i;
            float wave = 0.45f * (float) Math.sin(i * 0.9f);
            float w = i > 11 ? 1.3f - (i - 11) * 0.3f : 1.3f;
            b.box(wave - w, y, -0.45f, wave + w, y + 1.0f, 0.45f,
                    flow(0xFF1A8CFF, 0xFF9AF3FF, 0.6f, 0.09f), true, null);
        }
        return b.build();
    }

    private static SwordModel crystalShard() {
        SwordModel.Builder b = SwordModel.builder("Crystal Shard").accent(0xFFB06CFF).animated();
        b.bar(0.0f, 1.2f, 1.0f, 1.0f, 0xFF4A2A6A);
        b.grip(1.2f, 5.6f, 0.7f, 0xFF2A1840, 0xFF4A2A6A);
        b.box(-2.4f, 5.6f, -0.9f, 2.4f, 6.6f, 0.9f, 0xFF6A3A9A);
        // Клинок из отдельных парящих осколков.
        float[][] shards = {{7.2f, 3.0f, 1.4f}, {10.6f, 3.2f, 1.3f}, {14.2f, 3.0f, 1.1f}, {17.6f, 2.6f, 0.9f}, {20.6f, 2.0f, 0.6f}};
        for (int i = 0; i < shards.length; i++) {
            float[] s = shards[i];
            b.box(-s[2], s[0], -0.5f, s[2], s[0] + s[1], 0.5f,
                    pulse(0xDD9A5BFF, 0xDDE9D2FF, 0.5f + i * 0.07f), true, bob(0.35f, 0.5f, i * 0.2f));
        }
        return b.build();
    }
}

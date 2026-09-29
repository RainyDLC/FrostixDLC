package rtx.kimiko.api.ui.mainmenu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class MenuConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MenuConfig INSTANCE;

    public int backgroundPreset = 0; // 0 = Auto (Real-world day/night cycle), 1 = Morning, 2 = Day, 3 = Sunset, 4 = Night, 5 = Frostix 4K, 6 = Custom File
    public String customBackgroundName = "";
    public float backgroundBlur = 0.0f;
    public float backgroundDarkness = 0.22f;
    public boolean particlesEnabled = false;
    public int particleCount = 70;
    public boolean mouseParallax = true;
    public boolean freeMove = false;
    public boolean showClock = true;
    public float buttonScale = 1.0f;

    // Button positions: normalized coordinates [0..1] for screen width/height
    // 0: Multiplayer, 1: Singleplayer, 2: Account, 3: Settings, 4: Quit
    public float[][] buttonPositions = new float[5][2];
    public boolean hasCustomPositions = false;

    public String greetingText = "РАД ВИДЕТЬ";
    public String titleText = "Сборка собрана и прогрета —\nэто Frostix Client.";
    public String subtitleText = "Куда сегодня — на сервер или в свой мир?";

    public static MenuConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static File getConfigDir() {
        File dir = new File(MinecraftClient.getInstance().runDirectory, "frostix");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File getBackgroundsDir() {
        File dir = new File(getConfigDir(), "backgrounds");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static void openBackgroundsFolder() {
        try {
            File dir = getBackgroundsDir();
            Util.getOperatingSystem().open(dir);
        } catch (Throwable ignored) {
        }
    }

    public static List<String> listCustomBackgrounds() {
        List<String> list = new ArrayList<>();
        File dir = getBackgroundsDir();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".webp")) {
                        list.add(file.getName());
                    }
                }
            }
        }
        return list;
    }

    public static MenuConfig load() {
        File file = new File(getConfigDir(), "mainmenu.json");
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                MenuConfig config = GSON.fromJson(reader, MenuConfig.class);
                if (config != null) {
                    if (config.buttonPositions == null || config.buttonPositions.length < 5) {
                        config.buttonPositions = new float[5][2];
                        config.hasCustomPositions = false;
                    }
                    return config;
                }
            } catch (Throwable ignored) {
            }
        }
        MenuConfig def = new MenuConfig();
        def.save();
        return def;
    }

    public void save() {
        try {
            File file = new File(getConfigDir(), "mainmenu.json");
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(this, writer);
            }
        } catch (Throwable ignored) {
        }
    }

    public void resetLayout() {
        hasCustomPositions = false;
        buttonPositions = new float[5][2];
        freeMove = false;
        save();
    }
}

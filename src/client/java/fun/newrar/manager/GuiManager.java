package fun.newrar.manager;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

public class GuiManager {
    public static final File file = new File("C:/rainydlc/client1_21_11/config", "theme/theme.json");
    private Theme currentTheme = Theme.NIGHT;

    public void init() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            if (!file.exists()) {
                file.createNewFile();
                saveSettings();
            } else {
                readSettings();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void setGuiTheme(Theme theme) {
        currentTheme = theme;
        saveSettings();
    }

    public Theme getCurrentTheme() {
        return currentTheme;
    }

    private void saveSettings() {
        try (FileWriter writer = new FileWriter(file)) {
            Properties props = new Properties();
            props.setProperty("theme", currentTheme.name());
            props.setProperty("custom", Integer.toHexString(Theme.customAccent));
            props.store(writer, "GUI Settings");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readSettings() {
        try (FileReader reader = new FileReader(file)) {
            Properties props = new Properties();
            props.load(reader);
            currentTheme = Theme.valueOf(props.getProperty("theme", Theme.NIGHT.name()));
            String custom = props.getProperty("custom");
            if (custom != null) {
                try {
                    Theme.customAccent = (int) Long.parseLong(custom, 16);
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            e.printStackTrace();
        }
    }
}


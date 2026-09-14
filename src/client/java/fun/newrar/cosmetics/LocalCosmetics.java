package fun.newrar.cosmetics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fun.newrar.cosmetics.loader.CosmeticLoader;
import fun.newrar.cosmetics.model.CosmeticModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public final class LocalCosmetics {
   private static final Logger LOGGER = LoggerFactory.getLogger("Cosmetics-Local");
   private static final int MAX_SCAN_INDEX = 256;
   private static final List<Entry> ENTRIES = loadEntries();
   private static final LinkedHashMap<String, Integer> SELECTED = new LinkedHashMap<>();
   private static final Map<Integer, CosmeticModel> MODELS = new HashMap<>();
   private static boolean initialized = false;

   private LocalCosmetics() {
   }

   public static void init() {
      if (!initialized) {
         initialized = true;
         loadConfig();
      }
   }

   public static int size() {
      return ENTRIES.size();
   }

   public static List<Entry> getEntries() {
      return ENTRIES;
   }

   public static int getIndexAt(int i) {
      return entry(i).index;
   }

   public static String name(int i) {
      return entry(i).name;
   }

   public static String type(int i) {
      return entry(i).type;
   }

   public static Identifier texture(int i) {
      int idx = entry(i).index;
      return Identifier.of("pulse", "textures/cosmetics/cosmetic_" + idx + ".png");
   }

   public static Identifier iconTexture(int i) {
      int idx = entry(i).index;
      return Identifier.of("pulse", "textures/cosmetics/item_" + idx + ".png");
   }

   public static boolean isSelected(int i) {
      return SELECTED.containsValue(i);
   }

   public static Identifier selectedCapeTexture() {
      Integer i = SELECTED.get("cape");
      if (i == null) {
         return null;
      }
      int idx = entry(i).index;
      return Identifier.of("pulse", "cosmetics/cosmetic_" + idx);
   }

   public static List<Integer> selectedIndices() {
      return List.copyOf(SELECTED.values());
   }

   public static void toggle(int i) {
      String type = type(i);
      Integer old = SELECTED.get(type);
      if (old != null && old == i) {
         SELECTED.remove(type);
      } else {
         SELECTED.put(type, i);
         if (!"cape".equals(type)) {
            model(i);
         }
      }
      saveConfig();
   }

   public static void clearAll() {
      SELECTED.clear();
      saveConfig();
   }

   public static CosmeticModel modelFor(int i) {
      return model(i);
   }

   private static CosmeticModel model(int i) {
      int resourceIndex = entry(i).index;
      return MODELS.computeIfAbsent(resourceIndex, k -> {
         try (InputStream in = LocalCosmetics.class.getResourceAsStream("/assets/pulse/cosmetics/models/cosmetic_" + k + ".json")) {
            if (in == null) return null;
            return CosmeticLoader.getInstance().loadFromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
         } catch (Exception e) {
            LOGGER.error("Failed to load model cosmetic_{}.json", k, e);
            return null;
         }
      });
   }

   public static Entry entry(int i) {
      if (i < 0 || i >= ENTRIES.size()) {
         throw new IndexOutOfBoundsException("Cosmetic index " + i + " out of " + ENTRIES.size());
      }
      return ENTRIES.get(i);
   }

   public static int indexByResourceIndex(int resourceIndex) {
      for (int i = 0; i < ENTRIES.size(); i++) {
         if (ENTRIES.get(i).index == resourceIndex) {
            return i;
         }
      }
      return -1;
   }

   private static List<Entry> loadEntries() {
      List<Entry> entries = new ArrayList<>();

      for (int i = 0; i < MAX_SCAN_INDEX; i++) {
         String path = "/assets/pulse/cosmetics/models/cosmetic_" + i + ".json";
         try (InputStream in = LocalCosmetics.class.getResourceAsStream(path)) {
            if (in != null) {
               String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
               JsonObject object = JsonParser.parseString(json).getAsJsonObject();
               String rawName = object.has("name") ? object.get("name").getAsString() : "Cosmetic " + (i + 1);
               String rawType = object.has("type") ? object.get("type").getAsString() : "";
               int pos = object.has("pos") ? object.get("pos").getAsInt() : -1;
               entries.add(new Entry(i, displayName(rawName, i), classifyType(i, rawName, rawType, pos)));
            }
         } catch (Exception ignored) {
         }
      }

      entries.sort(Comparator.comparingInt(entry -> entry.index));
      LOGGER.info("Discovered {} local cosmetic entries", entries.size());
      return List.copyOf(entries);
   }

   private static String displayName(String rawName, int index) {
      String name = rawName == null || rawName.isBlank() ? "Cosmetic " + (index + 1) : rawName;
      if (name.startsWith("pulse_")) {
         name = name.substring("pulse_".length());
      }
      return name.replace('_', ' ').trim();
   }

   public static String classifyType(int index, String name, String rawType, int pos) {
      if (rawType != null && !rawType.isBlank()) {
         return rawType.trim().toLowerCase();
      }

      String lower = name == null ? "" : name.toLowerCase();
      if (lower.contains("cape")) {
         return "cape";
      } else if (lower.contains("wing")) {
         return "wings";
      } else if (lower.contains("pet") || lower.contains("bee") || lower.contains("radish")) {
         return "pet";
      } else if (lower.contains("hat") || lower.contains("nimb") || lower.contains("horn") || pos == 2) {
         return "hat";
      } else if (index <= 13) {
         return "cape";
      } else if (index <= 26) {
         return "wings";
      } else if (index <= 37) {
         return "bodywear";
      } else if (index <= 49) {
         return "pet";
      } else {
         return "bodywear";
      }
   }

   private static File getConfigFile() {
      File dir = MinecraftClient.getInstance().runDirectory;
      if (dir == null) dir = new File(".");
      return new File(dir, "cosmetics.json");
   }

   public static void saveConfig() {
      try {
         File file = getConfigFile();
         JsonObject obj = new JsonObject();
         for (Map.Entry<String, Integer> entry : SELECTED.entrySet()) {
            if (entry.getValue() != null && entry.getValue() >= 0 && entry.getValue() < ENTRIES.size()) {
               obj.addProperty(entry.getKey(), ENTRIES.get(entry.getValue()).index);
            }
         }
         Files.writeString(file.toPath(), obj.toString());
      } catch (Exception e) {
         LOGGER.error("Failed to save cosmetics config", e);
      }
   }

   public static void loadConfig() {
      try {
         File file = getConfigFile();
         if (!file.exists()) return;
         String content = Files.readString(file.toPath());
         JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
         SELECTED.clear();
         MODELS.clear();

         for (String type : obj.keySet()) {
            int resourceIndex = obj.get(type).getAsInt();
            int idx = indexByResourceIndex(resourceIndex);
            if (idx >= 0) {
               SELECTED.put(type, idx);
               if (!"cape".equals(type)) {
                  model(idx);
               }
            }
         }
         LOGGER.info("Loaded cosmetics config: {} equipped items", SELECTED.size());
      } catch (Exception e) {
         LOGGER.error("Failed to load cosmetics config", e);
      }
   }

   public static final class Entry {
      public final int index;
      public final String name;
      public final String type;

      private Entry(int index, String name, String type) {
         this.index = index;
         this.name = name;
         this.type = type;
      }
   }
}

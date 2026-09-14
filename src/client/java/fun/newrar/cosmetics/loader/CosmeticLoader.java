package fun.newrar.cosmetics.loader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fun.newrar.cosmetics.model.CosmeticModel;
import fun.newrar.cosmetics.model.ModelPosition;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CosmeticLoader {
   private static final Logger LOGGER = LoggerFactory.getLogger("Cosmetics-Loader");
   private static CosmeticLoader instance;
   private final Map<Integer, CosmeticModel> loadedCosmetics = new ConcurrentHashMap<>();
   private final Map<String, Identifier> textureCache = new ConcurrentHashMap<>();

   public static CosmeticLoader getInstance() {
      if (instance == null) {
         instance = new CosmeticLoader();
      }
      return instance;
   }

   public CosmeticModel loadFromJson(String json) {
      return this.loadFromJson(json, null, -1);
   }

   public CosmeticModel loadFromJson(String json, Identifier textureId) {
      return this.loadFromJson(json, textureId, -1);
   }

   public CosmeticModel loadFromJson(String json, Identifier textureId, int customId) {
      try {
         JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
         return this.loadFromJson(obj, textureId, customId);
      } catch (Exception e) {
         LOGGER.error("Error loading cosmetic from JSON string", e);
         return null;
      }
   }

   public CosmeticModel loadFromJson(JsonObject obj) {
      return this.loadFromJson(obj, null, -1);
   }

   public CosmeticModel loadFromJson(JsonObject obj, Identifier textureId, int customId) {
      try {
         if (obj.has("name") && obj.has("model")) {
            String name = obj.get("name").getAsString();
            int id = customId > 0 ? customId : (obj.has("id") ? obj.get("id").getAsInt() : name.hashCode());
            int category = obj.has("category") ? obj.get("category").getAsInt() : 1;
            CosmeticModel model = new CosmeticModel(name, id, category);

            JsonObject modelObj = obj.getAsJsonObject("model");
            model.setRawModelJson(modelObj.toString());

            if (textureId != null) {
               model.setTextureId(textureId);
            } else if (obj.has("texture")) {
               String b64 = obj.get("texture").getAsString();
               Identifier loadedTex = this.loadTextureFromBase64(name, id, b64);
               model.setTextureId(loadedTex);
            }

            this.parseModelPosition(obj, model);

            if (obj.has("height")) {
               model.setHeight(obj.get("height").getAsFloat());
            }
            if (obj.has("scale")) {
               model.setScale(obj.get("scale").getAsFloat());
            }
            if (obj.has("previewScale")) {
               model.setPreviewScale(obj.get("previewScale").getAsFloat());
            }
            if (obj.has("previewY")) {
               model.setPreviewY(obj.get("previewY").getAsFloat());
            }
            if (obj.has("animation")) {
               model.setAnimationJson(obj.getAsJsonObject("animation"));
            }

            this.loadedCosmetics.put(id, model);
            LOGGER.info("Loaded cosmetic: {} (id={}), texture={}", name, id, model.getTextureId());
            return model;
         } else {
            LOGGER.error("Invalid cosmetic JSON: missing name or model");
            return null;
         }
      } catch (Exception e) {
         LOGGER.error("Error loading cosmetic", e);
         return null;
      }
   }

   private void parseModelPosition(JsonObject obj, CosmeticModel model) {
      if (obj.has("pos")) {
         model.setPosition(ModelPosition.getById(obj.get("pos").getAsInt()));
      }
      if (obj.has("scale")) {
         model.setScale(obj.get("scale").getAsFloat());
      }
      if (obj.has("x")) {
         model.setX(obj.get("x").getAsFloat());
      }
      if (obj.has("y")) {
         model.setY(obj.get("y").getAsFloat());
      }
      if (obj.has("z")) {
         model.setZ(obj.get("z").getAsFloat());
      }
      if (obj.has("yaw")) {
         model.setYaw(obj.get("yaw").getAsFloat());
      }
      if (obj.has("pitch")) {
         model.setPitch(obj.get("pitch").getAsFloat());
      }
      if (obj.has("roll")) {
         model.setRoll(obj.get("roll").getAsFloat());
      }
   }

   private Identifier loadTextureFromBase64(String name, int id, String b64) {
      try {
         String safeName = name.replace(" ", "").toLowerCase() + "_" + id;
         String cacheKey = "cosmetic_" + safeName;
         if (this.textureCache.containsKey(cacheKey)) {
            return this.textureCache.get(cacheKey);
         }

         byte[] decoded = Base64.getDecoder().decode(b64);
         ByteArrayInputStream in = new ByteArrayInputStream(decoded);
         NativeImage nativeImage = NativeImage.read(in);
         Identifier texIdentifier = Identifier.of("pulse", "cosmetic/" + safeName);

         Runnable registerTask = () -> {
            NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "pulse cosmetic", nativeImage);
            MinecraftClient.getInstance().getTextureManager().registerTexture(texIdentifier, tex);
            this.textureCache.put(cacheKey, texIdentifier);
            LOGGER.info("Registered texture: {} ({}x{})", texIdentifier, nativeImage.getWidth(), nativeImage.getHeight());
         };

         if (MinecraftClient.getInstance().isOnThread()) {
            registerTask.run();
         } else {
            MinecraftClient.getInstance().execute(registerTask);
         }

         this.textureCache.put(cacheKey, texIdentifier);
         return texIdentifier;
      } catch (Exception e) {
         LOGGER.error("Failed to load texture for cosmetic: {}", name, e);
         return null;
      }
   }

   public CosmeticModel getCosmetic(int id) {
      return this.loadedCosmetics.get(id);
   }

   public Map<Integer, CosmeticModel> getAllCosmetics() {
      return this.loadedCosmetics;
   }

   public void loadFromInputStream(InputStream in) {
      try {
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         byte[] buf = new byte[1024];
         int read;
         while ((read = in.read(buf)) != -1) {
            out.write(buf, 0, read);
         }
         String json = out.toString(StandardCharsets.UTF_8.name());
         this.loadFromJson(json);
      } catch (Exception e) {
         LOGGER.error("Error loading cosmetic from input stream", e);
      }
   }
}

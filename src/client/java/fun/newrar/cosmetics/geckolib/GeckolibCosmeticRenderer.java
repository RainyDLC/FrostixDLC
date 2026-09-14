package fun.newrar.cosmetics.geckolib;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fun.newrar.cosmetics.geo.GeoBone;
import fun.newrar.cosmetics.geo.GeoCube;
import fun.newrar.cosmetics.geo.GeoModel;
import fun.newrar.cosmetics.geo.GeoQuad;
import fun.newrar.cosmetics.geo.GeoVertex;
import fun.newrar.cosmetics.model.CosmeticModel;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GeckolibCosmeticRenderer {
   private static final Logger LOGGER = LoggerFactory.getLogger("Cosmetics-GeckolibRenderer");
   private static GeckolibCosmeticRenderer instance;

   private final Map<Integer, GeoModel> modelCache = new ConcurrentHashMap<>();
   private final Map<Integer, CosmeticAnimationData> animationCache = new ConcurrentHashMap<>();
   private final Set<Integer> noAnimationSet = ConcurrentHashMap.newKeySet();
   private final Map<Integer, Long> animationStartTime = new ConcurrentHashMap<>();
   private final Map<Integer, Map<String, float[]>> initialBoneTransforms = new ConcurrentHashMap<>();
   private final GeckolibModelParser modelParser = new GeckolibModelParser();

   public static GeckolibCosmeticRenderer getInstance() {
      if (instance == null) {
         instance = new GeckolibCosmeticRenderer();
      }
      return instance;
   }

   public void renderCosmetic(CosmeticModel cosmetic, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
      if (cosmetic != null && cosmetic.getTextureId() != null) {
         GeoModel model = this.getOrParseModel(cosmetic);
         if (model != null) {
            CosmeticAnimationData anim = this.getOrParseAnimation(cosmetic);
            if (anim != null) {
               this.applyAnimations(model, anim, cosmetic.getId());
            }

            Identifier tex = cosmetic.getTextureId();
            RenderLayer layer = RenderLayers.entityCutoutNoCull(tex);
            VertexConsumer consumer = vertexConsumers.getBuffer(layer);

            for (GeoBone bone : model.topLevelBones) {
               this.renderBone(bone, matrices, consumer, light, OverlayTexture.DEFAULT_UV, 1.0F, 1.0F, 1.0F, 1.0F);
            }
         }
      }
   }

   public void renderCosmetic(CosmeticModel cosmetic, MatrixStack matrices, VertexConsumer vertexConsumer, int light) {
      if (cosmetic != null && cosmetic.getTextureId() != null && vertexConsumer != null) {
         GeoModel geoModel = this.getOrParseModel(cosmetic);
         if (geoModel != null) {
            CosmeticAnimationData animationData = this.getOrParseAnimation(cosmetic);
            if (animationData != null) {
               this.applyAnimations(geoModel, animationData, cosmetic.getId());
            }

            for (GeoBone bone : geoModel.topLevelBones) {
               this.renderBone(bone, matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV, 1.0F, 1.0F, 1.0F, 1.0F);
            }
         }
      }
   }

   private GeoModel getOrParseModel(CosmeticModel cosmetic) {
      int id = cosmetic.getId();
      if (this.modelCache.containsKey(id)) {
         return this.modelCache.get(id);
      }

      GeoModel model = this.modelParser.parseModel(cosmetic);
      if (model != null) {
         this.modelCache.put(id, model);
         this.saveInitialBoneTransforms(id, model);
         LOGGER.info("Cached model for cosmetic: {}", cosmetic.getName());
      }
      return model;
   }

   private void saveInitialBoneTransforms(int id, GeoModel model) {
      HashMap<String, float[]> transforms = new HashMap<>();
      for (GeoBone bone : model.topLevelBones) {
         this.saveBonesRecursive(bone, transforms);
      }
      this.initialBoneTransforms.put(id, transforms);
   }

   private void saveBonesRecursive(GeoBone bone, Map<String, float[]> transforms) {
      transforms.put(
         bone.name,
         new float[]{
            bone.getRotationX(),
            bone.getRotationY(),
            bone.getRotationZ(),
            bone.getPositionX(),
            bone.getPositionY(),
            bone.getPositionZ(),
            bone.getScaleX(),
            bone.getScaleY(),
            bone.getScaleZ()
         }
      );

      for (GeoBone child : bone.childBones) {
         this.saveBonesRecursive(child, transforms);
      }
   }

   private void renderBone(GeoBone bone, MatrixStack matrices, VertexConsumer consumer, int light, int overlay, float r, float g, float b, float a) {
      if (!bone.isHidden) {
         matrices.push();
         GeckoRenderHelper.translate(bone, matrices);
         GeckoRenderHelper.moveToPivot(bone, matrices);
         GeckoRenderHelper.rotate(bone, matrices);
         GeckoRenderHelper.scale(bone, matrices);
         GeckoRenderHelper.moveBackFromPivot(bone, matrices);

         for (GeoCube cube : bone.childCubes) {
            this.renderCube(cube, matrices, consumer, light, overlay, r, g, b, a);
         }

         for (GeoBone child : bone.childBones) {
            this.renderBone(child, matrices, consumer, light, overlay, r, g, b, a);
         }

         matrices.pop();
      }
   }

   private void renderCube(GeoCube cube, MatrixStack matrices, VertexConsumer consumer, int light, int overlay, float r, float g, float b, float a) {
      matrices.push();
      GeckoRenderHelper.moveToPivot(cube, matrices);
      GeckoRenderHelper.rotate(cube, matrices);
      GeckoRenderHelper.moveBackFromPivot(cube, matrices);

      Matrix4f posMat = matrices.peek().getPositionMatrix();
      Matrix3f normMat = matrices.peek().getNormalMatrix();

      for (GeoQuad quad : cube.quads) {
         if (quad != null) {
            Vector3f normal = new Vector3f(quad.normal.getX(), quad.normal.getY(), quad.normal.getZ());
            normMat.transform(normal);
            float nx = normal.x();
            float ny = normal.y();
            float nz = normal.z();

            if ((cube.size.getY() == 0.0F || cube.size.getZ() == 0.0F) && nx < 0.0F) {
               nx = -nx;
            }
            if ((cube.size.getX() == 0.0F || cube.size.getZ() == 0.0F) && ny < 0.0F) {
               ny = -ny;
            }
            if ((cube.size.getX() == 0.0F || cube.size.getY() == 0.0F) && nz < 0.0F) {
               nz = -nz;
            }

            for (GeoVertex vert : quad.vertices) {
               consumer.vertex(posMat, vert.position.getX(), vert.position.getY(), vert.position.getZ())
                  .color(r, g, b, a)
                  .texture(vert.textureU, vert.textureV)
                  .overlay(overlay)
                  .light(light)
                  .normal(nx, ny, nz);
            }
         }
      }

      matrices.pop();
   }

   private CosmeticAnimationData getOrParseAnimation(CosmeticModel cosmetic) {
      int id = cosmetic.getId();
      if (this.animationCache.containsKey(id)) {
         return this.animationCache.get(id);
      }
      if (this.noAnimationSet.contains(id)) {
         return null;
      }

      JsonObject animJson = cosmetic.getAnimationJson();
      if (animJson == null) {
         this.noAnimationSet.add(id);
         return null;
      }

      try {
         CosmeticAnimationData data = this.parseAnimationData(animJson);
         if (data != null) {
            this.animationCache.put(id, data);
            LOGGER.info("Cached animation for cosmetic: {}", cosmetic.getName());
         } else {
            this.noAnimationSet.add(id);
         }
         return data;
      } catch (Exception e) {
         LOGGER.error("Failed to parse animation for: {}", cosmetic.getName(), e);
         this.noAnimationSet.add(id);
         return null;
      }
   }

   private CosmeticAnimationData parseAnimationData(JsonObject obj) {
      CosmeticAnimationData data = new CosmeticAnimationData();
      if (!obj.has("animations")) {
         return null;
      }

      JsonObject animations = obj.getAsJsonObject("animations");
      Iterator<Map.Entry<String, JsonElement>> it = animations.entrySet().iterator();
      if (it.hasNext()) {
         Map.Entry<String, JsonElement> entry = it.next();
         String animName = entry.getKey();
         JsonObject entryObj = entry.getValue().getAsJsonObject();
         data.animationName = animName;
         data.loop = entryObj.has("loop") && entryObj.get("loop").getAsBoolean();
         data.length = entryObj.has("animation_length") ? entryObj.get("animation_length").getAsFloat() : 1.0F;

         if (entryObj.has("bones")) {
            JsonObject bones = entryObj.getAsJsonObject("bones");
            for (Map.Entry<String, JsonElement> boneEntry : bones.entrySet()) {
               String boneName = boneEntry.getKey();
               JsonObject boneAnim = boneEntry.getValue().getAsJsonObject();
               BoneAnimationData bData = new BoneAnimationData();

               if (boneAnim.has("rotation")) {
                  bData.rotationKeyframes = this.parseKeyframes(boneAnim.get("rotation"));
               }
               if (boneAnim.has("position")) {
                  bData.positionKeyframes = this.parseKeyframes(boneAnim.get("position"));
               }
               if (boneAnim.has("scale")) {
                  bData.scaleKeyframes = this.parseKeyframes(boneAnim.get("scale"));
               }

               data.boneAnimations.put(boneName, bData);
            }
         }
      }
      return data;
   }

   private Map<Float, float[]> parseKeyframes(JsonElement elem) {
      HashMap<Float, float[]> map = new HashMap<>();
      if (elem.isJsonObject()) {
         JsonObject obj = elem.getAsJsonObject();
         for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            try {
               float time = Float.parseFloat(entry.getKey());
               JsonElement val = entry.getValue();
               float[] vec = new float[3];
               if (val.isJsonObject()) {
                  JsonObject valObj = val.getAsJsonObject();
                  if (valObj.has("vector")) {
                     JsonArray vArr = valObj.getAsJsonArray("vector");
                     vec[0] = vArr.get(0).getAsFloat();
                     vec[1] = vArr.get(1).getAsFloat();
                     vec[2] = vArr.get(2).getAsFloat();
                  }
               } else if (val.isJsonArray()) {
                  JsonArray vArr = val.getAsJsonArray();
                  vec[0] = vArr.get(0).getAsFloat();
                  vec[1] = vArr.get(1).getAsFloat();
                  vec[2] = vArr.get(2).getAsFloat();
               }
               map.put(time, vec);
            } catch (NumberFormatException ignored) {}
         }
      }
      return map;
   }

   private void applyAnimations(GeoModel model, CosmeticAnimationData anim, int id) {
      Map<String, float[]> initial = this.initialBoneTransforms.get(id);
      if (initial != null) {
         long startTime = this.animationStartTime.computeIfAbsent(id, k -> System.currentTimeMillis());
         float elapsed = (float) (System.currentTimeMillis() - startTime) / 1000.0F;
         float animTime;
         if (anim.loop && anim.length > 0.0F) {
            animTime = elapsed % anim.length;
         } else {
            animTime = Math.min(elapsed, anim.length);
         }

         for (GeoBone bone : model.topLevelBones) {
            this.resetBoneRecursive(bone, initial);
         }

         for (Map.Entry<String, BoneAnimationData> entry : anim.boneAnimations.entrySet()) {
            String boneName = entry.getKey();
            BoneAnimationData bAnim = entry.getValue();
            GeoBone bone = this.findBone(model, boneName);
            if (bone != null) {
               float[] init = initial.get(boneName);
               if (init == null) {
                  init = new float[]{0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F};
               }

               if (!bAnim.rotationKeyframes.isEmpty()) {
                  float[] rot = this.interpolateKeyframes(bAnim.rotationKeyframes, animTime);
                  bone.setRotationX(init[0] + (float) Math.toRadians(-rot[0]));
                  bone.setRotationY(init[1] + (float) Math.toRadians(-rot[1]));
                  bone.setRotationZ(init[2] + (float) Math.toRadians(rot[2]));
               }

               if (!bAnim.positionKeyframes.isEmpty()) {
                  float[] pos = this.interpolateKeyframes(bAnim.positionKeyframes, animTime);
                  bone.setPositionX(init[3] + pos[0]);
                  bone.setPositionY(init[4] + pos[1]);
                  bone.setPositionZ(init[5] + pos[2]);
               }

               if (!bAnim.scaleKeyframes.isEmpty()) {
                  float[] scl = this.interpolateKeyframes(bAnim.scaleKeyframes, animTime);
                  bone.setScaleX(init[6] * scl[0]);
                  bone.setScaleY(init[7] * scl[1]);
                  bone.setScaleZ(init[8] * scl[2]);
               }
            }
         }
      }
   }

   private void resetBoneRecursive(GeoBone bone, Map<String, float[]> initial) {
      float[] t = initial.get(bone.name);
      if (t != null) {
         bone.setRotationX(t[0]);
         bone.setRotationY(t[1]);
         bone.setRotationZ(t[2]);
         bone.setPositionX(t[3]);
         bone.setPositionY(t[4]);
         bone.setPositionZ(t[5]);
         bone.setScaleX(t[6]);
         bone.setScaleY(t[7]);
         bone.setScaleZ(t[8]);
      }
      for (GeoBone child : bone.childBones) {
         this.resetBoneRecursive(child, initial);
      }
   }

   private GeoBone findBone(GeoModel model, String name) {
      for (GeoBone bone : model.topLevelBones) {
         GeoBone found = this.findBoneRecursive(bone, name);
         if (found != null) {
            return found;
         }
      }
      return null;
   }

   private GeoBone findBoneRecursive(GeoBone bone, String name) {
      if (bone.name.equals(name)) {
         return bone;
      }
      for (GeoBone child : bone.childBones) {
         GeoBone found = this.findBoneRecursive(child, name);
         if (found != null) {
            return found;
         }
      }
      return null;
   }

   private float[] interpolateKeyframes(Map<Float, float[]> keyframes, float time) {
      if (keyframes.isEmpty()) {
         return new float[]{0.0F, 0.0F, 0.0F};
      }

      Float t1 = null;
      Float t2 = null;
      float[] v1 = null;
      float[] v2 = null;

      for (Map.Entry<Float, float[]> entry : keyframes.entrySet()) {
         float t = entry.getKey();
         if (t <= time && (t1 == null || t > t1)) {
            t1 = t;
            v1 = entry.getValue();
         }
         if (t >= time && (t2 == null || t < t2)) {
            t2 = t;
            v2 = entry.getValue();
         }
      }

      if (v1 == null && v2 == null) {
         return new float[]{0.0F, 0.0F, 0.0F};
      }
      if (v1 == null) {
         return v2;
      }
      if (v2 == null) {
         return v1;
      }
      if (t1.equals(t2)) {
         return v1;
      }

      float factor = (time - t1) / (t2 - t1);
      return new float[]{
         v1[0] + factor * (v2[0] - v1[0]),
         v1[1] + factor * (v2[1] - v1[1]),
         v1[2] + factor * (v2[2] - v1[2])
      };
   }

   private static class BoneAnimationData {
      Map<Float, float[]> rotationKeyframes = new HashMap<>();
      Map<Float, float[]> positionKeyframes = new HashMap<>();
      Map<Float, float[]> scaleKeyframes = new HashMap<>();
   }

   private static class CosmeticAnimationData {
      String animationName;
      boolean loop;
      float length;
      Map<String, BoneAnimationData> boneAnimations = new HashMap<>();
   }
}

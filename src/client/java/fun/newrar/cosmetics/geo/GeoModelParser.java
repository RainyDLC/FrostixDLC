package fun.newrar.cosmetics.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class GeoModelParser {
   private static final Logger LOGGER = LoggerFactory.getLogger("Cosmetics-Geo");

   public static GeoModel parse(String jsonString) {
      try {
         JsonObject obj = JsonParser.parseString(jsonString).getAsJsonObject();
         return parseModel(obj);
      } catch (Exception e) {
         LOGGER.error("Failed to parse Bedrock model", e);
         return null;
      }
   }

   private static GeoModel parseModel(JsonObject root) {
      JsonArray geometryArray = root.getAsJsonArray("minecraft:geometry");
      if (geometryArray != null && !geometryArray.isEmpty()) {
         JsonObject geomObj = geometryArray.get(0).getAsJsonObject();
         JsonObject desc = geomObj.getAsJsonObject("description");
         int texWidth = desc != null && desc.has("texture_width") ? desc.get("texture_width").getAsInt() : 64;
         int texHeight = desc != null && desc.has("texture_height") ? desc.get("texture_height").getAsInt() : 64;

         GeoModel model = new GeoModel();
         model.textureWidth = texWidth;
         model.textureHeight = texHeight;

         JsonArray bonesArray = geomObj.getAsJsonArray("bones");
         if (bonesArray != null) {
            HashMap<String, GeoBone> boneMap = new HashMap<>();

            for (JsonElement elem : bonesArray) {
               JsonObject boneObj = elem.getAsJsonObject();
               GeoBone bone = parseBone(boneObj, texWidth, texHeight);
               boneMap.put(bone.name, bone);
            }

            for (JsonElement elem : bonesArray) {
               JsonObject boneObj = elem.getAsJsonObject();
               String name = boneObj.get("name").getAsString();
               GeoBone bone = boneMap.get(name);
               if (boneObj.has("parent")) {
                  String parentName = boneObj.get("parent").getAsString();
                  GeoBone parent = boneMap.get(parentName);
                  if (parent != null) {
                     parent.childBones.add(bone);
                     bone.parent = parent;
                  }
               } else {
                  model.topLevelBones.add(bone);
               }
            }
         }

         return model;
      } else {
         LOGGER.error("No minecraft:geometry found in model");
         return null;
      }
   }

   private static GeoBone parseBone(JsonObject obj, int texWidth, int texHeight) {
      String name = obj.get("name").getAsString();
      GeoBone bone = new GeoBone(name);

      if (obj.has("pivot")) {
         JsonArray pivot = obj.getAsJsonArray("pivot");
         bone.rotationPointX = -pivot.get(0).getAsFloat();
         bone.rotationPointY = pivot.get(1).getAsFloat();
         bone.rotationPointZ = pivot.get(2).getAsFloat();
      }

      if (obj.has("rotation")) {
         JsonArray rot = obj.getAsJsonArray("rotation");
         bone.setRotationX((float) Math.toRadians(-rot.get(0).getAsFloat()));
         bone.setRotationY((float) Math.toRadians(-rot.get(1).getAsFloat()));
         bone.setRotationZ((float) Math.toRadians(rot.get(2).getAsFloat()));
      }

      if (obj.has("cubes")) {
         for (JsonElement elem : obj.getAsJsonArray("cubes")) {
            JsonObject cubeObj = elem.getAsJsonObject();
            GeoCube cube = parseCube(cubeObj, texWidth, texHeight);
            bone.childCubes.add(cube);
         }
      }

      return bone;
   }

   private static GeoCube parseCube(JsonObject obj, int texWidth, int texHeight) {
      float[] origin = parseFloatArray(obj, "origin", new float[]{0.0F, 0.0F, 0.0F});
      float[] size = parseFloatArray(obj, "size", new float[]{1.0F, 1.0F, 1.0F});
      float[] pivot = parseFloatArray(obj, "pivot", (float[]) origin.clone());
      float[] rot = parseFloatArray(obj, "rotation", new float[]{0.0F, 0.0F, 0.0F});
      float inflate = obj.has("inflate") ? obj.get("inflate").getAsFloat() : 0.0F;
      boolean mirror = obj.has("mirror") && obj.get("mirror").getAsBoolean();

      GeoCube cube = new GeoCube(size[0], size[1], size[2]);
      cube.pivot = new Vec3F(-pivot[0], pivot[1], pivot[2]);
      cube.rotation = new Vec3F((float) Math.toRadians(-rot[0]), (float) Math.toRadians(-rot[1]), (float) Math.toRadians(rot[2]));
      cube.inflate = inflate;
      cube.mirror = mirror;

      buildCubeQuads(cube, origin, size, inflate, mirror, obj, texWidth, texHeight);
      return cube;
   }

   private static void buildCubeQuads(GeoCube cube, float[] origin, float[] size, float inflate, boolean mirror, JsonObject obj, int texWidth, int texHeight) {
      float ox = origin[0] - inflate;
      float oy = origin[1] - inflate;
      float oz = origin[2] - inflate;
      float sx = size[0] + inflate * 2.0F;
      float sy = size[1] + inflate * 2.0F;
      float sz = size[2] + inflate * 2.0F;

      float u = 0.0F;
      float v = 0.0F;
      boolean perFace = false;
      JsonObject faces = null;

      if (obj.has("uv")) {
         JsonElement uvElem = obj.get("uv");
         if (uvElem.isJsonArray()) {
            JsonArray uvArr = uvElem.getAsJsonArray();
            u = uvArr.get(0).getAsFloat();
            v = uvArr.get(1).getAsFloat();
         } else if (uvElem.isJsonObject()) {
            perFace = true;
            faces = uvElem.getAsJsonObject();
         }
      }

      float minX = -(ox + sx) / 16.0F;
      float minY = oy / 16.0F;
      float minZ = oz / 16.0F;
      float maxX = -ox / 16.0F;
      float maxY = (oy + sy) / 16.0F;
      float maxZ = (oz + sz) / 16.0F;

      if (perFace && faces != null) {
         cube.quads[0] = buildQuadPerFace(faces, "west", minX, minY, minZ, minX, maxY, maxZ, -1.0F, 0.0F, 0.0F, texWidth, texHeight);
         cube.quads[1] = buildQuadPerFace(faces, "east", maxX, minY, minZ, maxX, maxY, maxZ, 1.0F, 0.0F, 0.0F, texWidth, texHeight);
         cube.quads[2] = buildQuadPerFace(faces, "down", minX, minY, minZ, maxX, minY, maxZ, 0.0F, -1.0F, 0.0F, texWidth, texHeight);
         cube.quads[3] = buildQuadPerFace(faces, "up", minX, maxY, minZ, maxX, maxY, maxZ, 0.0F, 1.0F, 0.0F, texWidth, texHeight);
         cube.quads[4] = buildQuadPerFace(faces, "north", minX, minY, minZ, maxX, maxY, minZ, 0.0F, 0.0F, -1.0F, texWidth, texHeight);
         cube.quads[5] = buildQuadPerFace(faces, "south", minX, minY, maxZ, maxX, maxY, maxZ, 0.0F, 0.0F, 1.0F, texWidth, texHeight);
      } else {
         cube.quads[0] = buildQuadBox(minX, minY, minZ, minX, maxY, maxZ, -1.0F, 0.0F, 0.0F, u, v, sz, sy, sx, texWidth, texHeight, "west");
         cube.quads[1] = buildQuadBox(maxX, minY, minZ, maxX, maxY, maxZ, 1.0F, 0.0F, 0.0F, u, v, sz, sy, sx, texWidth, texHeight, "east");
         cube.quads[2] = buildQuadBox(minX, minY, minZ, maxX, minY, maxZ, 0.0F, -1.0F, 0.0F, u, v, sz, sy, sx, texWidth, texHeight, "down");
         cube.quads[3] = buildQuadBox(minX, maxY, minZ, maxX, maxY, maxZ, 0.0F, 1.0F, 0.0F, u, v, sz, sy, sx, texWidth, texHeight, "up");
         cube.quads[4] = buildQuadBox(minX, minY, minZ, maxX, maxY, minZ, 0.0F, 0.0F, -1.0F, u, v, sz, sy, sx, texWidth, texHeight, "north");
         cube.quads[5] = buildQuadBox(minX, minY, maxZ, maxX, maxY, maxZ, 0.0F, 0.0F, 1.0F, u, v, sz, sy, sx, texWidth, texHeight, "south");
      }
   }

   private static GeoQuad buildQuadPerFace(JsonObject faces, String faceName, float x1, float y1, float z1, float x2, float y2, float z2, float nx, float ny, float nz, int tw, int th) {
      if (!faces.has(faceName)) {
         return null;
      }
      JsonObject faceObj = faces.getAsJsonObject(faceName);
      JsonArray uvArr = faceObj.getAsJsonArray("uv");
      JsonArray sizeArr = faceObj.getAsJsonArray("uv_size");
      float u = uvArr.get(0).getAsFloat() / tw;
      float v = uvArr.get(1).getAsFloat() / th;
      float uw = sizeArr.get(0).getAsFloat() / tw;
      float vh = sizeArr.get(1).getAsFloat() / th;
      GeoVertex[] verts = buildFaceVertices(faceName, x1, y1, z1, x2, y2, z2, u, v, uw, vh);
      return new GeoQuad(verts, nx, ny, nz);
   }

   private static GeoQuad buildQuadBox(float x1, float y1, float z1, float x2, float y2, float z2, float nx, float ny, float nz, float u, float v, float sz, float sy, float sx, float tw, float th, String faceName) {
      float fu;
      float fv;
      float fw;
      float fh;
      switch (faceName) {
         case "north":
            fu = (u + sz + sx) / tw;
            fv = (v + sz) / th;
            fw = sx / tw;
            fh = sy / th;
            break;
         case "south":
            fu = (u + sz + sx + sz) / tw;
            fv = (v + sz) / th;
            fw = sx / tw;
            fh = sy / th;
            break;
         case "east":
            fu = u / tw;
            fv = (v + sz) / th;
            fw = sz / tw;
            fh = sy / th;
            break;
         case "west":
            fu = (u + sz + sx) / tw;
            fv = (v + sz) / th;
            fw = sz / tw;
            fh = sy / th;
            break;
         case "up":
            fu = (u + sz) / tw;
            fv = v / th;
            fw = sx / tw;
            fh = sz / th;
            break;
         case "down":
            fu = (u + sz + sx) / tw;
            fv = v / th;
            fw = sx / tw;
            fh = sz / th;
            break;
         default:
            fu = 0.0F;
            fv = 0.0F;
            fw = 0.0F;
            fh = 0.0F;
      }
      GeoVertex[] verts = buildFaceVertices(faceName, x1, y1, z1, x2, y2, z2, fu, fv, fw, fh);
      return new GeoQuad(verts, nx, ny, nz);
   }

   private static GeoVertex[] buildFaceVertices(String faceName, float x1, float y1, float z1, float x2, float y2, float z2, float u, float v, float uw, float vh) {
      GeoVertex[] verts = new GeoVertex[4];
      float u2 = u + uw;
      float v2 = v + vh;
      switch (faceName) {
         case "north":
            verts[0] = new GeoVertex(x2, y2, z1, u, v);
            verts[1] = new GeoVertex(x1, y2, z1, u2, v);
            verts[2] = new GeoVertex(x1, y1, z1, u2, v2);
            verts[3] = new GeoVertex(x2, y1, z1, u, v2);
            break;
         case "south":
            verts[0] = new GeoVertex(x1, y2, z2, u, v);
            verts[1] = new GeoVertex(x2, y2, z2, u2, v);
            verts[2] = new GeoVertex(x2, y1, z2, u2, v2);
            verts[3] = new GeoVertex(x1, y1, z2, u, v2);
            break;
         case "east":
            verts[0] = new GeoVertex(x2, y2, z2, u, v);
            verts[1] = new GeoVertex(x2, y2, z1, u2, v);
            verts[2] = new GeoVertex(x2, y1, z1, u2, v2);
            verts[3] = new GeoVertex(x2, y1, z2, u, v2);
            break;
         case "west":
            verts[0] = new GeoVertex(x1, y2, z1, u, v);
            verts[1] = new GeoVertex(x1, y2, z2, u2, v);
            verts[2] = new GeoVertex(x1, y1, z2, u2, v2);
            verts[3] = new GeoVertex(x1, y1, z1, u, v2);
            break;
         case "up":
            verts[0] = new GeoVertex(x1, y2, z1, u, v);
            verts[1] = new GeoVertex(x1, y2, z2, u, v2);
            verts[2] = new GeoVertex(x2, y2, z2, u2, v2);
            verts[3] = new GeoVertex(x2, y2, z1, u2, v);
            break;
         case "down":
            verts[0] = new GeoVertex(x2, y1, z1, u, v);
            verts[1] = new GeoVertex(x2, y1, z2, u, v2);
            verts[2] = new GeoVertex(x1, y1, z2, u2, v2);
            verts[3] = new GeoVertex(x1, y1, z1, u2, v);
            break;
      }
      return verts;
   }

   private static float[] parseFloatArray(JsonObject obj, String key, float[] def) {
      if (!obj.has(key)) {
         return def;
      }
      JsonArray arr = obj.getAsJsonArray(key);
      float[] res = new float[arr.size()];
      for (int i = 0; i < arr.size(); i++) {
         res[i] = arr.get(i).getAsFloat();
      }
      return res;
   }
}

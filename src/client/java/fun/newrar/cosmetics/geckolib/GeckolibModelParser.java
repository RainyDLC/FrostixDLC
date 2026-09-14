package fun.newrar.cosmetics.geckolib;

import fun.newrar.cosmetics.geo.GeoModel;
import fun.newrar.cosmetics.geo.GeoModelParser;
import fun.newrar.cosmetics.model.CosmeticModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeckolibModelParser {
   private static final Logger LOGGER = LoggerFactory.getLogger("Cosmetics-GeckolibParser");

   public GeoModel parseModel(CosmeticModel model) {
      try {
         String rawJson = model.getRawModelJson();
         if (rawJson == null) {
            LOGGER.error("No raw model JSON available for cosmetic: {}", model.getName());
            return null;
         } else {
            GeoModel geoModel = GeoModelParser.parse(rawJson);
            if (geoModel == null) {
               LOGGER.error("Failed to parse GeoModel for: {}", model.getName());
               return null;
            } else {
               LOGGER.info("Successfully parsed model for: {} (bones: {})", model.getName(), geoModel.topLevelBones.size());
               return geoModel;
            }
         }
      } catch (Exception e) {
         LOGGER.error("Error parsing model for: {}", model.getName(), e);
         return null;
      }
   }
}

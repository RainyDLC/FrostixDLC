package fun.newrar.cosmetics.geo;

import java.util.ArrayList;
import java.util.List;

public class GeoBone {
   public GeoBone parent;
   public List<GeoBone> childBones = new ArrayList<>();
   public List<GeoCube> childCubes = new ArrayList<>();
   public String name;
   public boolean isHidden = false;
   public float rotationPointX;
   public float rotationPointY;
   public float rotationPointZ;
   private float rotateX;
   private float rotateY;
   private float rotateZ;
   private float positionX;
   private float positionY;
   private float positionZ;
   private float scaleX = 1.0F;
   private float scaleY = 1.0F;
   private float scaleZ = 1.0F;

   public GeoBone(String name) {
      this.name = name;
   }

   public float getRotationX() {
      return this.rotateX;
   }

   public float getRotationY() {
      return this.rotateY;
   }

   public float getRotationZ() {
      return this.rotateZ;
   }

   public void setRotationX(float val) {
      this.rotateX = val;
   }

   public void setRotationY(float val) {
      this.rotateY = val;
   }

   public void setRotationZ(float val) {
      this.rotateZ = val;
   }

   public float getPositionX() {
      return this.positionX;
   }

   public float getPositionY() {
      return this.positionY;
   }

   public float getPositionZ() {
      return this.positionZ;
   }

   public void setPositionX(float val) {
      this.positionX = val;
   }

   public void setPositionY(float val) {
      this.positionY = val;
   }

   public void setPositionZ(float val) {
      this.positionZ = val;
   }

   public float getScaleX() {
      return this.scaleX;
   }

   public float getScaleY() {
      return this.scaleY;
   }

   public float getScaleZ() {
      return this.scaleZ;
   }

   public void setScaleX(float val) {
      this.scaleX = val;
   }

   public void setScaleY(float val) {
      this.scaleY = val;
   }

   public void setScaleZ(float val) {
      this.scaleZ = val;
   }

   public float getPivotX() {
      return this.rotationPointX;
   }

   public float getPivotY() {
      return this.rotationPointY;
   }

   public float getPivotZ() {
      return this.rotationPointZ;
   }

   public void setPivotX(float val) {
      this.rotationPointX = val;
   }

   public void setPivotY(float val) {
      this.rotationPointY = val;
   }

   public void setPivotZ(float val) {
      this.rotationPointZ = val;
   }

   public String getName() {
      return this.name;
   }

   public boolean isHidden() {
      return this.isHidden;
   }

   public void setHidden(boolean val) {
      this.isHidden = val;
   }
}

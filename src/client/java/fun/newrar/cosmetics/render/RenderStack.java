package fun.newrar.cosmetics.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

public class RenderStack {
   private MatrixStack stack;

   public void update(MatrixStack stack) {
      this.stack = stack;
   }

   public void push() {
      this.stack.push();
   }

   public void pop() {
      this.stack.pop();
   }

   public MatrixStack get() {
      return this.stack;
   }

   public void translate(float x, float y, float z) {
      this.stack.translate(x, y, z);
   }

   public void scale(float x, float y, float z) {
      this.stack.scale(x, y, z);
   }

   public void rotateX(float deg) {
      this.stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(deg));
   }

   public void rotateY(float deg) {
      this.stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(deg));
   }

   public void rotateZ(float deg) {
      this.stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(deg));
   }

   public void rotate(float x, float y, float z) {
      if (z != 0.0F) {
         this.rotateZ(z);
      }
      if (y != 0.0F) {
         this.rotateY(y);
      }
      if (x != 0.0F) {
         this.rotateX(x);
      }
   }

   public void rotateDegrees(float x, float y, float z) {
      this.rotate(x, y, z);
   }

   public void rotateXDegrees(float deg) {
      this.rotateX(deg);
   }

   public void rotateYDegrees(float deg) {
      this.rotateY(deg);
   }

   public void rotateZDegrees(float deg) {
      this.rotateZ(deg);
   }
}

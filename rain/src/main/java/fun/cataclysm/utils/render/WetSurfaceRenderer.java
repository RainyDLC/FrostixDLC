package fun.cataclysm.utils.render;

import com.mojang.blaze3d.systems.RenderSystem;
import fun.cataclysm.utils.render.shader.Shader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.Window;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Depth-aware wet-ground post effect used by Rain.
 *
 * The renderer copies the finished world color before the HUD, reconstructs
 * view/world positions from the depth attachment and applies wetness only to
 * upward-facing surfaces. Reflections are sampled from the real scene color:
 * a short screen-space ray trace is combined with a direction-projected
 * fallback, so puddles still reflect the sky and distant silhouettes when an
 * on-screen ray does not find local geometry.
 */
public final class WetSurfaceRenderer implements AutoCloseable {
    private static final WetSurfaceRenderer INSTANCE = new WetSurfaceRenderer();
    private static final float EPSILON = 1.0E-4f;

    private final SceneTarget scene = new SceneTarget();

    private Shader shader;
    private int blitFramebuffer;
    private int drawFramebuffer;
    private int vertexArray;
    private int vertexBuffer;
    private boolean initialized;
    private boolean disabled;

    private WetSurfaceRenderer() {
    }

    public static WetSurfaceRenderer getInstance() {
        return INSTANCE;
    }

    public static final class Parameters {
        public float wetness = 1.0f;
        public float puddleCoverage = 0.82f;
        public float reflectionStrength = 1.25f;
        public float maxDistance = 56.0f;
        public float rippleStrength = 0.8f;
        public float rainAmount = 1.0f;
        public int reflectionSteps = 10;
        public boolean ripples = true;
    }

    private static final class SceneTarget {
        int framebuffer;
        int texture;
        int width;
        int height;
    }

    public void apply(MinecraftClient mc, Camera camera, Matrix4f viewMatrix,
                      Matrix4f projectionMatrix, Parameters parameters) {
        if (this.disabled || mc == null || camera == null || viewMatrix == null
                || projectionMatrix == null || parameters == null) {
            return;
        }
        if (mc.world == null || mc.player == null || !isWindowValid(mc)
                || parameters.wetness <= EPSILON || parameters.reflectionStrength <= EPSILON) {
            return;
        }

        Window window = mc.getWindow();
        int width = window.getFramebufferWidth();
        int height = window.getFramebufferHeight();
        if (width <= 1 || height <= 1) return;

        Framebuffer framebuffer = mc.getFramebuffer();
        if (framebuffer == null) return;

        int colorTexture = framebuffer.getColorAttachment();
        int depthTexture = framebuffer.getDepthAttachment();
        if (colorTexture <= 0 || depthTexture <= 0) return;

        GlStateSnapshot snapshot = GlStateSnapshot.capture();
        boolean attached = false;
        try {
            ensureInitialized();
            if (this.disabled || !ensureScene(width, height)
                    || !copyColorToScene(colorTexture, width, height)) {
                return;
            }

            Matrix4f inverseProjection = new Matrix4f(projectionMatrix).invert();
            Matrix4f inverseView = new Matrix4f(viewMatrix).invert();
            Vec3d cameraPos = camera.getPos();
            inverseView.m30((float) cameraPos.x);
            inverseView.m31((float) cameraPos.y);
            inverseView.m32((float) cameraPos.z);

            attached = renderPass(
                    colorTexture,
                    depthTexture,
                    width,
                    height,
                    viewMatrix,
                    projectionMatrix,
                    inverseProjection,
                    inverseView,
                    parameters
            );
        } catch (Throwable throwable) {
            this.disabled = true;
            System.err.println("[Rain] wet-surface renderer disabled: " + throwable.getMessage());
            throwable.printStackTrace();
        } finally {
            if (attached && this.drawFramebuffer != 0) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.drawFramebuffer);
                GL30.glFramebufferTexture2D(
                        GL30.GL_FRAMEBUFFER,
                        GL30.GL_COLOR_ATTACHMENT0,
                        GL11.GL_TEXTURE_2D,
                        0,
                        0
                );
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL20.glUseProgram(0);
            snapshot.restore();
        }
    }

    private boolean renderPass(int colorTexture, int depthTexture, int width, int height,
                               Matrix4f viewMatrix, Matrix4f projectionMatrix,
                               Matrix4f inverseProjection, Matrix4f inverseView,
                               Parameters parameters) {
        if (this.drawFramebuffer == 0) {
            this.drawFramebuffer = GL30.glGenFramebuffers();
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.drawFramebuffer);
        GL30.glFramebufferTexture2D(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D,
                colorTexture,
                0
        );
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            return true;
        }

        GL11.glViewport(0, 0, width, height);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(false);

        this.shader.bind();
        this.shader.setUniform1i("u_SceneTexture", 0);
        this.shader.setUniform1i("u_DepthTexture", 1);
        this.shader.setUniform2f("u_Resolution", (float) width, (float) height);
        this.shader.setUniformMatrix4f("u_ViewMatrix", viewMatrix);
        this.shader.setUniformMatrix4f("u_ProjectionMatrix", projectionMatrix);
        this.shader.setUniformMatrix4f("u_InverseProjectionMatrix", inverseProjection);
        this.shader.setUniformMatrix4f("u_InverseViewMatrix", inverseView);
        this.shader.setUniform1f("u_Time", (System.currentTimeMillis() % 1_000_000L) / 1000.0f);
        this.shader.setUniform1f("u_Wetness", clamp(parameters.wetness, 0.0f, 2.0f));
        this.shader.setUniform1f("u_PuddleCoverage", clamp(parameters.puddleCoverage, 0.0f, 1.0f));
        this.shader.setUniform1f("u_ReflectionStrength", clamp(parameters.reflectionStrength, 0.0f, 2.5f));
        this.shader.setUniform1f("u_MaxDistance", clamp(parameters.maxDistance, 4.0f, 128.0f));
        this.shader.setUniform1f("u_RippleStrength", clamp(parameters.rippleStrength, 0.0f, 2.0f));
        this.shader.setUniform1f("u_RainAmount", clamp(parameters.rainAmount, 0.0f, 2.0f));
        this.shader.setUniform1i("u_ReflectionSteps", Math.max(4, Math.min(16, parameters.reflectionSteps)));
        this.shader.setUniform1i("u_Ripples", parameters.ripples ? 1 : 0);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.scene.texture);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        GL30.glBindVertexArray(this.vertexArray);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);
        return true;
    }

    private boolean copyColorToScene(int colorTexture, int width, int height) {
        if (colorTexture <= 0 || this.scene.framebuffer <= 0 || width <= 0 || height <= 0) {
            return false;
        }
        if (this.blitFramebuffer == 0) {
            this.blitFramebuffer = GL30.glGenFramebuffers();
        }

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.blitFramebuffer);
        GL30.glFramebufferTexture2D(
                GL30.GL_READ_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D,
                colorTexture,
                0
        );
        if (GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            GL30.glFramebufferTexture2D(
                    GL30.GL_READ_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D,
                    0,
                    0
            );
            return false;
        }

        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.scene.framebuffer);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL30.glBlitFramebuffer(
                0, 0, width, height,
                0, 0, width, height,
                GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_NEAREST
        );

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.blitFramebuffer);
        GL30.glFramebufferTexture2D(
                GL30.GL_READ_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D,
                0,
                0
        );
        return true;
    }

    private boolean ensureScene(int width, int height) {
        if (this.scene.texture != 0
                && (this.scene.width != width || this.scene.height != height || this.scene.framebuffer == 0)) {
            deleteScene();
        }

        if (this.scene.texture == 0) {
            this.scene.texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.scene.texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
            allocTexture2D(GL30.GL_RGBA8, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);

            this.scene.framebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.scene.framebuffer);
            GL30.glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D,
                    this.scene.texture,
                    0
            );
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                deleteScene();
                return false;
            }
        }

        this.scene.width = width;
        this.scene.height = height;
        return true;
    }

    private static void allocTexture2D(int internalFormat, int width, int height, int format, int type) {
        int unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        if (unpackBuffer != 0) {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        }
        try {
            GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    internalFormat,
                    width,
                    height,
                    0,
                    format,
                    type,
                    (java.nio.ByteBuffer) null
            );
        } finally {
            if (unpackBuffer != 0) {
                GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            }
        }
    }

    private void ensureInitialized() {
        if (this.initialized) return;

        this.vertexArray = GL30.glGenVertexArrays();
        this.vertexBuffer = GL15.glGenBuffers();
        GL30.glBindVertexArray(this.vertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vertexBuffer);

        float[] vertices = new float[]{
                -1.0f, -1.0f, 0.0f, 0.0f,
                1.0f, -1.0f, 1.0f, 0.0f,
                1.0f, 1.0f, 1.0f, 1.0f,
                -1.0f, -1.0f, 0.0f, 0.0f,
                1.0f, 1.0f, 1.0f, 1.0f,
                -1.0f, 1.0f, 0.0f, 1.0f
        };
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 16, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 16, 8L);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);

        this.shader = new Shader("postfx", "wet_surface");
        this.initialized = true;
        System.out.println("[Rain] Wet Ground renderer initialized");
    }

    /** Allows a clean retry after toggling Rain if shader creation failed transiently. */
    public void reset() {
        this.disabled = false;
    }

    private static boolean isWindowValid(MinecraftClient mc) {
        Window window = mc == null ? null : mc.getWindow();
        return window != null && window.getFramebufferWidth() > 0 && window.getFramebufferHeight() > 0;
    }

    private static float clamp(float value, float min, float max) {
        return !Float.isFinite(value) ? min : Math.max(min, Math.min(max, value));
    }

    private static boolean canModifyGlObjects() {
        return RenderSystem.isOnRenderThread() && GLFW.glfwGetCurrentContext() != 0L;
    }

    private void deleteScene() {
        if (this.scene.framebuffer != 0 && canModifyGlObjects()) {
            GL30.glDeleteFramebuffers(this.scene.framebuffer);
        }
        if (this.scene.texture != 0 && canModifyGlObjects()) {
            GL11.glDeleteTextures(this.scene.texture);
        }
        this.scene.framebuffer = 0;
        this.scene.texture = 0;
        this.scene.width = 0;
        this.scene.height = 0;
    }

    @Override
    public void close() {
        if (!canModifyGlObjects()) {
            clearObjectIds();
            return;
        }

        deleteScene();
        if (this.blitFramebuffer != 0) GL30.glDeleteFramebuffers(this.blitFramebuffer);
        if (this.drawFramebuffer != 0) GL30.glDeleteFramebuffers(this.drawFramebuffer);
        if (this.vertexArray != 0) GL30.glDeleteVertexArrays(this.vertexArray);
        if (this.vertexBuffer != 0) GL15.glDeleteBuffers(this.vertexBuffer);
        if (this.shader != null) this.shader.delete();
        clearObjectIds();
    }

    private void clearObjectIds() {
        this.scene.framebuffer = 0;
        this.scene.texture = 0;
        this.scene.width = 0;
        this.scene.height = 0;
        this.blitFramebuffer = 0;
        this.drawFramebuffer = 0;
        this.vertexArray = 0;
        this.vertexBuffer = 0;
        this.shader = null;
        this.initialized = false;
        this.disabled = false;
    }

    private static final class GL21 {
        private static final int GL_PIXEL_UNPACK_BUFFER = 0x88EC;
        private static final int GL_PIXEL_UNPACK_BUFFER_BINDING = 0x88EF;
    }
}

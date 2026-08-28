package ru.white.screen;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import ru.white.Client;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.DrawBatcher;
import ru.white.utils.render.ShardPipeline;
import ru.white.utils.render.ShardTexturePipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class MenuShards {
    private static final int COLS = 8;
    private static final int ROWS = 6;

    private static final float ASSEMBLE_MS = 560F;
    private static final float DISSOLVE_MS = 900F;

    private static final float FOCAL = 640F;

    private enum Phase { IDLE, ASSEMBLE, DISSOLVE }

    private static final class Shard {
        float[] local;
        float cx, cy;
        float offX, offY;
        float spin;
        float tumble;
        float z;
        float fall;
        float delay, span;
        float shade;

        float[] localUv;

        int tint;
    }

    private final List<Shard> shards = new ArrayList<>();
    private final Random random = new Random();
    private final float[] faces = new float[12];

    private final float[] faceXyuv = new float[24];

    private final ShardTexturePipeline texturedPipeline = new ShardTexturePipeline();

    private static GpuTexture panelTexture;
    private static GpuTextureView panelView;
    private static int panelTexW, panelTexH;

    private static float capX0, capY0, capW, capH;

    private boolean texturedRun;

    private float panelPx, panelPy, panelPw, panelPh;

    private Phase phase = Phase.IDLE;
    private long startTime;
    private float duration = ASSEMBLE_MS;

    private float centerX, centerY, maxDist, screenW, screenH, scaleFactor;
    private boolean incoming;

    public boolean isRunning() {
        return phase != Phase.IDLE;
    }

    public boolean isDone() {
        return phase == Phase.IDLE || progress() >= 1F;
    }

    public boolean isAssembling() {
        return phase == Phase.ASSEMBLE && progress() < 1F;
    }

    public boolean isDissolving() {
        return phase == Phase.DISSOLVE && progress() < 1F;
    }

    public float progress() {
        if (phase == Phase.IDLE) return 1F;
        return clamp01((System.currentTimeMillis() - startTime) / duration);
    }

    public void reset() {
        phase = Phase.IDLE;
        shards.clear();
    }

    public void capturePanel(float x, float y, float w, float h) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getFramebuffer() == null
                || client.getFramebuffer().getColorAttachment() == null) {
            return;
        }

        Framebuffer fb = client.getFramebuffer();

        float fx0 = (float) Math.floor(x * 2F);
        float fy0 = (float) Math.floor(y * 2F);
        float fx1 = (float) Math.ceil((x + w) * 2F);
        float fy1 = (float) Math.ceil((y + h) * 2F);
        int srcX = (int) fx0;
        int srcY = (int) fy0;
        int sizeW = Math.max(1, (int) (fx1 - fx0));
        int sizeH = Math.max(1, (int) (fy1 - fy0));
        if (srcX < 0 || srcY < 0
                || srcX + sizeW > fb.textureWidth
                || srcY + sizeH > fb.textureHeight) {
            return;
        }

        if (panelTexture == null || panelTexW != sizeW || panelTexH != sizeH) {
            if (panelView != null) { panelView.close(); panelView = null; }
            if (panelTexture != null) { panelTexture.close(); panelTexture = null; }
            panelTexture = RenderSystem.getDevice().createTexture(
                    () -> "client:menu_shard_panel",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8,
                    sizeW, sizeH, 1, 1
            );
            panelView = RenderSystem.getDevice().createTextureView(panelTexture);
            panelTexW = sizeW;
            panelTexH = sizeH;
        }

        capX0 = fx0 / 2F;
        capY0 = fy0 / 2F;
        capW = sizeW / 2F;
        capH = sizeH / 2F;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                fb.getColorAttachment(), panelTexture,
                0, 0, 0,
                srcX, srcY, sizeW, sizeH
        );
    }

    private boolean hasCapture() {
        return panelView != null;
    }

    public void assemble(float px, float py, float pw, float ph, float radius,
                         float screenWidth, float screenHeight, float scale) {
        build(px, py, pw, ph, radius, screenWidth, screenHeight, scale, true);
        phase = Phase.ASSEMBLE;
        duration = ASSEMBLE_MS;
        startTime = System.currentTimeMillis();
    }

    public void dissolve(float px, float py, float pw, float ph, float radius,
                         float screenWidth, float screenHeight, float scale) {
        build(px, py, pw, ph, radius, screenWidth, screenHeight, scale, false);
        phase = Phase.DISSOLVE;
        duration = DISSOLVE_MS;
        startTime = System.currentTimeMillis();
    }

    private void build(float px, float py, float pw, float ph, float radius,
                       float screenWidth, float screenHeight, float scale, boolean assembling) {
        shards.clear();

        centerX = px + pw / 2F;
        centerY = py + ph / 2F;
        maxDist = (float) Math.hypot(pw / 2F, ph / 2F);
        screenW = screenWidth;
        screenH = screenHeight;
        scaleFactor = scale;
        incoming = assembling;
        panelPx = px;
        panelPy = py;
        panelPw = pw;
        panelPh = ph;

        texturedRun = hasCapture()
                && Math.abs(capX0 - px) <= 2F
                && Math.abs(capY0 - py) <= 2F
                && Math.abs(capW - pw) <= 2F
                && Math.abs(capH - ph) <= 2F;

        float[] bx = boundaries(px, pw, COLS);
        float[] by = boundaries(py, ph, ROWS);

        float minCellW = Float.MAX_VALUE;
        float minCellH = Float.MAX_VALUE;
        for (int i = 0; i < COLS; i++) minCellW = Math.min(minCellW, bx[i + 1] - bx[i]);
        for (int j = 0; j < ROWS; j++) minCellH = Math.min(minCellH, by[j + 1] - by[j]);

        float[][] vx = new float[COLS + 1][ROWS + 1];
        float[][] vy = new float[COLS + 1][ROWS + 1];
        for (int i = 0; i <= COLS; i++) {
            for (int j = 0; j <= ROWS; j++) {
                float x = bx[i];
                float y = by[j];

                if (i > 0 && i < COLS) x += rand(-0.3F, 0.3F) * minCellW;
                if (j > 0 && j < ROWS) y += rand(-0.3F, 0.3F) * minCellH;
                vx[i][j] = x;
                vy[i][j] = y;
            }
        }

        float bevel = Math.min(radius * 0.75F, Math.min(minCellW, minCellH) * 0.45F);

        boolean[][] used = new boolean[COLS][ROWS];
        for (int i = 0; i < COLS; i++) {
            for (int j = 0; j < ROWS; j++) {
                if (used[i][j]) continue;
                used[i][j] = true;

                if (bevel > 0.5F && (i == 0 || i == COLS - 1) && (j == 0 || j == ROWS - 1)) {
                    addShard(cornerPiece(vx, vy, i, j, bevel));
                    continue;
                }

                float roll = random.nextFloat();
                if (roll < 0.46F) {
                    if (random.nextBoolean()) {
                        addShard(tri(vx, vy, i, j, i + 1, j, i + 1, j + 1));
                        addShard(tri(vx, vy, i, j, i + 1, j + 1, i, j + 1));
                    } else {
                        addShard(tri(vx, vy, i, j, i + 1, j, i, j + 1));
                        addShard(tri(vx, vy, i + 1, j, i + 1, j + 1, i, j + 1));
                    }
                } else if (roll < 0.78F) {
                    addShard(quad(vx, vy, i, j));
                } else {
                    boolean right = i + 1 < COLS && !used[i + 1][j] && random.nextBoolean();
                    boolean down = !right && j + 1 < ROWS && !used[i][j + 1];
                    if (right) {
                        used[i + 1][j] = true;
                        addShard(merge(quad(vx, vy, i, j), quad(vx, vy, i + 1, j)));
                    } else if (down) {
                        used[i][j + 1] = true;
                        addShard(merge(quad(vx, vy, i, j), quad(vx, vy, i, j + 1)));
                    } else {
                        addShard(quad(vx, vy, i, j));
                    }
                }
            }
        }
    }

    private float[] boundaries(float from, float size, int count) {
        float[] weights = new float[count];
        float total = 0F;
        for (int i = 0; i < count; i++) {
            weights[i] = rand(0.62F, 1.38F);
            total += weights[i];
        }
        float[] out = new float[count + 1];
        out[0] = from;
        float acc = 0F;
        for (int i = 0; i < count; i++) {
            acc += weights[i];
            out[i + 1] = from + size * (acc / total);
        }
        out[count] = from + size;
        return out;
    }

    private float[] tri(float[][] vx, float[][] vy,
                        int i0, int j0, int i1, int j1, int i2, int j2) {
        return new float[]{
                vx[i0][j0], vy[i0][j0],
                vx[i1][j1], vy[i1][j1],
                vx[i2][j2], vy[i2][j2]
        };
    }

    private float[] quad(float[][] vx, float[][] vy, int i, int j) {
        return new float[]{
                vx[i][j], vy[i][j],
                vx[i + 1][j], vy[i + 1][j],
                vx[i + 1][j + 1], vy[i + 1][j + 1],

                vx[i][j], vy[i][j],
                vx[i + 1][j + 1], vy[i + 1][j + 1],
                vx[i][j + 1], vy[i][j + 1]
        };
    }

    private float[] cornerPiece(float[][] vx, float[][] vy, int i, int j, float bevel) {
        float ax = vx[i][j], ay = vy[i][j];
        float bx = vx[i + 1][j], by = vy[i + 1][j];
        float cx = vx[i + 1][j + 1], cy = vy[i + 1][j + 1];
        float dx = vx[i][j + 1], dy = vy[i][j + 1];

        boolean left = i == 0;
        boolean top = j == 0;

        if (left && top) {
            return fan(ax + bevel, ay, bx, by, cx, cy, dx, dy, ax, ay + bevel);
        }
        if (!left && top) {
            return fan(ax, ay, bx - bevel, by, bx, by + bevel, cx, cy, dx, dy);
        }
        if (!left) {
            return fan(ax, ay, bx, by, cx, cy - bevel, cx - bevel, cy, dx, dy);
        }

        return fan(ax, ay, bx, by, cx, cy, dx + bevel, dy, dx, dy - bevel);
    }

    private float[] fan(float x0, float y0, float x1, float y1, float x2, float y2,
                        float x3, float y3, float x4, float y4) {
        return new float[]{
                x0, y0, x1, y1, x2, y2,
                x0, y0, x2, y2, x3, y3,
                x0, y0, x3, y3, x4, y4
        };
    }

    private float[] merge(float[] a, float[] b) {
        float[] out = new float[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private void addShard(float[] verts) {
        Shard s = new Shard();

        int points = verts.length / 2;
        float sumX = 0F;
        float sumY = 0F;
        for (int k = 0; k < points; k++) {
            sumX += verts[k * 2];
            sumY += verts[k * 2 + 1];
        }
        s.cx = sumX / points;
        s.cy = sumY / points;

        s.local = new float[verts.length];
        s.localUv = new float[verts.length];
        for (int k = 0; k < points; k++) {
            float ax = verts[k * 2];
            float ay = verts[k * 2 + 1];
            s.local[k * 2] = ax - s.cx;
            s.local[k * 2 + 1] = ay - s.cy;

            float u = capW <= 0F ? 0F : (ax - capX0) / capW;
            float v = capH <= 0F ? 0F : 1F - (ay - capY0) / capH;
            s.localUv[k * 2] = clamp01(u);
            s.localUv[k * 2 + 1] = clamp01(v);
        }

        float dx = s.cx - centerX;
        float dy = s.cy - centerY;
        float dist = (float) Math.hypot(dx, dy);
        float distPc = maxDist <= 0F ? 0F : clamp01(dist / maxDist);

        s.shade = rand(0.72F, 1F);

        int accent = ColorUtil.client();
        float posMix = clamp01(distPc * 0.85F + rand(-0.15F, 0.15F));
        s.tint = ColorUtil.overCol(accent, ColorUtil.multDark(accent, 0.45F), posMix);

        if (random.nextFloat() < 0.18F) {
            s.tint = ColorUtil.overCol(s.tint, ColorUtil.getColor(255), rand(0.25F, 0.55F));
        }

        if (incoming) {
            float[] spawn = offScreenPoint(s.cx, s.cy);
            s.offX = spawn[0] - s.cx;
            s.offY = spawn[1] - s.cy;
            s.spin = (float) Math.toRadians(rand(-300F, 300F));
            s.tumble = rand(-2.2F, 2.2F);
            s.z = random.nextFloat() < 0.72F ? rand(200F, 640F) : rand(-70F, -200F);
            s.fall = 0F;

            s.delay = clamp01(0.18F * distPc + rand(0F, 0.1F));
        } else {
            float len = Math.max(1F, dist);
            float spread = (70F + 170F * random.nextFloat()) * scaleFactor;
            s.offX = (dx / len + rand(-0.4F, 0.4F)) * spread;
            s.offY = (dy / len + rand(-0.4F, 0.4F)) * spread;
            s.spin = (float) Math.toRadians(rand(-210F, 210F));
            s.tumble = rand(-2F, 2F);
            s.z = random.nextFloat() < 0.65F ? rand(140F, 520F) : rand(-70F, -220F);
            s.fall = rand(10F, 46F) * scaleFactor;

            s.delay = clamp01(0.16F * distPc + rand(0F, 0.07F));
        }
        s.span = Math.max(0.05F, 1F - s.delay);

        shards.add(s);
    }

    private float[] offScreenPoint(float x, float y) {
        float margin = 90F;

        int edge;
        if (random.nextFloat() < 0.62F) {
            float left = x;
            float right = screenW - x;
            float top = y;
            float bottom = screenH - y;
            float min = Math.min(Math.min(left, right), Math.min(top, bottom));
            if (min == left) edge = 0;
            else if (min == right) edge = 1;
            else if (min == top) edge = 2;
            else edge = 3;
        } else {
            edge = random.nextInt(4);
        }

        return switch (edge) {
            case 0 -> new float[]{-margin - rand(0F, 0.3F) * screenW, rand(-margin, screenH + margin)};
            case 1 -> new float[]{screenW + margin + rand(0F, 0.3F) * screenW, rand(-margin, screenH + margin)};
            case 2 -> new float[]{rand(-margin, screenW + margin), -margin - rand(0F, 0.3F) * screenH};
            default -> new float[]{rand(-margin, screenW + margin), screenH + margin + rand(0F, 0.3F) * screenH};
        };
    }

    public void render() {
        if (phase == Phase.IDLE) return;

        float p = progress();
        if (p >= 1F) {
            reset();
            return;
        }

        boolean assembling = phase == Phase.ASSEMBLE;
        boolean textured = texturedRun && hasCapture();
        ShardPipeline pipeline = Client.get().render2D().getShardPipeline();
        if (textured) {
            texturedPipeline.setPanel(panelView);
        }

        boolean batchHere = !DrawBatcher.isEnabled();
        if (batchHere) DrawBatcher.setEnabled(true);

        try {
            for (Shard s : shards) {
                float t = clamp01((p - s.delay) / s.span);
                if (assembling && t <= 0F) continue;

                float move = assembling ? easeOutCubic(t) : easeOutQuad(t);

                float k = assembling ? 1F - move : move;

                float alpha;
                if (assembling) {
                    alpha = smoothstep(0F, 0.12F, t)
                            * (1F - smoothstep(0.82F, 1F, t))
                            * lerp(s.shade, 1F, move);
                } else {
                    alpha = (1F - smoothstep(0.16F, 1F, t)) * lerp(1F, s.shade, move);
                }
                if (alpha <= 0.004F) continue;

                float angle = s.spin * k;
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);
                float squish = Math.max(0.18F, Math.abs((float) Math.cos(s.tumble * k)));
                float scale = persp(s.z * k);
                float ox = s.cx + s.offX * k;
                float oy = s.cy + s.offY * k + s.fall * k * k;

                int color = ColorUtil.replAlpha(s.tint, alpha);
                int faceCount = s.local.length / 6;

                for (int f = 0; f < faceCount; f += 2) {
                    boolean pair = f + 1 < faceCount;
                    System.arraycopy(s.local, f * 6, faces, 0, pair ? 12 : 6);
                    if (!pair) {
                        for (int v = 3; v < 6; v++) {
                            faces[v * 2] = s.local[f * 6];
                            faces[v * 2 + 1] = s.local[f * 6 + 1];
                        }
                    }
                    for (int v = 0; v < 6; v++) {
                        float lx = faces[v * 2];
                        float ly = faces[v * 2 + 1];
                        faces[v * 2] = ox + (lx * cos - ly * sin) * squish * scale;
                        faces[v * 2 + 1] = oy + (lx * sin + ly * cos) * scale;
                    }

                    if (textured) {
                        int realVerts = pair ? 6 : 3;
                        for (int v = 0; v < 6; v++) {
                            int vi = Math.min(v, realVerts - 1);
                            faceXyuv[v * 4] = faces[v * 2];
                            faceXyuv[v * 4 + 1] = faces[v * 2 + 1];
                            faceXyuv[v * 4 + 2] = s.localUv[f * 6 + vi * 2];
                            faceXyuv[v * 4 + 3] = s.localUv[f * 6 + vi * 2 + 1];
                        }

                        float bright = lerp(0.78F, 1F, move);
                        int texCol = ColorUtil.getColor((int) (bright * 255F), alpha);
                        texturedPipeline.drawFacePair(faceXyuv, texCol);
                    } else {
                        pipeline.drawFaces(faces, color);
                    }
                }
            }
        } finally {
            if (batchHere) DrawBatcher.setEnabled(false);
        }
    }

    private float rand(float from, float to) {
        return from + random.nextFloat() * (to - from);
    }

    private static float persp(float z) {
        return FOCAL / Math.max(120F, FOCAL + z);
    }

    private static float clamp01(float v) {
        return v < 0F ? 0F : (v > 1F ? 1F : v);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3F - 2F * t);
    }

    private static float easeOutCubic(float t) {
        float f = 1F - t;
        return 1F - f * f * f;
    }

    private static float easeOutQuad(float t) {
        float f = 1F - t;
        return 1F - f * f;
    }
}

package fun.newrar.utils.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class GifTexture {
    private final List<Identifier> frameIds  = new ArrayList<>();
    private final List<Integer>    delays     = new ArrayList<>();
    private int   totalDuration = 0;
    private long  startTime     = -1;
    private volatile boolean loaded = false;
    private int width = 0, height = 0;

    public GifTexture(InputStream stream) {
        load(stream);
    }

    private void load(InputStream stream) {
        try {
            ImageInputStream iis = ImageIO.createImageInputStream(stream);
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) return;

            ImageReader reader = readers.next();
            reader.setInput(iis, false);

            int frameCount = reader.getNumImages(true);
            String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

            IIOMetadataNode globalMeta = null;
            try {
                globalMeta = (IIOMetadataNode) reader.getStreamMetadata()
                        .getAsTree(reader.getStreamMetadata().getNativeMetadataFormatName());
            } catch (Exception ignored) {}

            BufferedImage firstFrame = reader.read(0);
            int canvasW = firstFrame.getWidth();
            int canvasH = firstFrame.getHeight();
            if (globalMeta != null) {
                IIOMetadataNode ld = findNode(globalMeta, "LogicalScreenDescriptor");
                if (ld != null) {
                    try { canvasW = Integer.parseInt(ld.getAttribute("logicalScreenWidth"));  } catch (Exception ignored) {}
                    try { canvasH = Integer.parseInt(ld.getAttribute("logicalScreenHeight")); } catch (Exception ignored) {}
                }
            }
            width  = canvasW;
            height = canvasH;

            List<int[]>   pixelData = new ArrayList<>();
            BufferedImage canvas    = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
            BufferedImage prevCanvas = null;

            for (int i = 0; i < frameCount; i++) {
                BufferedImage rawFrame = (i == 0) ? firstFrame : reader.read(i);
                IIOMetadataNode frameMeta = null;
                try {
                    frameMeta = (IIOMetadataNode) reader.getImageMetadata(i)
                            .getAsTree(reader.getImageMetadata(i).getNativeMetadataFormatName());
                } catch (Exception ignored) {}

                int fx = 0, fy = 0;
                String disposal = "none";
                if (frameMeta != null) {
                    IIOMetadataNode id = findNode(frameMeta, "ImageDescriptor");
                    if (id != null) {
                        try { fx = Integer.parseInt(id.getAttribute("imageLeftPosition")); } catch (Exception ignored) {}
                        try { fy = Integer.parseInt(id.getAttribute("imageTopPosition"));  } catch (Exception ignored) {}
                    }
                    IIOMetadataNode gce = findNode(frameMeta, "GraphicControlExtension");
                    if (gce != null) disposal = gce.getAttribute("disposalMethod");
                }

                if ("restoreToPrevious".equalsIgnoreCase(disposal)) {
                    prevCanvas = copyImage(canvas);
                }

                java.awt.Graphics2D g = canvas.createGraphics();
                g.drawImage(rawFrame, fx, fy, null);
                g.dispose();

                BufferedImage transCanvas = makeTransparent(canvas);
                pixelData.add(transCanvas.getRGB(0, 0, canvasW, canvasH, null, 0, canvasW));
                delays.add(readDelay(reader, i));
                frameIds.add(Identifier.of("white", "gif/" + uid + "/" + i));

                java.awt.Graphics2D gc = canvas.createGraphics();
                gc.setComposite(java.awt.AlphaComposite.Clear);
                if ("restoreToBackgroundColor".equalsIgnoreCase(disposal)) {
                    gc.fillRect(fx, fy, rawFrame.getWidth(), rawFrame.getHeight());
                } else if ("restoreToPrevious".equalsIgnoreCase(disposal) && prevCanvas != null) {
                    gc.setComposite(java.awt.AlphaComposite.Src);
                    gc.drawImage(prevCanvas, 0, 0, null);
                }
                gc.dispose();
            }

            reader.dispose();
            for (int d : delays) totalDuration += d;

            final int uploadW = canvasW;
            final int uploadH = canvasH;
            Runnable uploadTask = () -> {
                for (int i = 0; i < pixelData.size(); i++) {
                    int[] px = pixelData.get(i);

                    NativeImage img = new NativeImage(NativeImage.Format.RGBA, uploadW, uploadH, false);
                    for (int y = 0; y < uploadH; y++) {
                        for (int x = 0; x < uploadW; x++) {
                            img.setColorArgb(x, y, px[y * uploadW + x]);
                        }
                    }

                    final int idx = i;
                    NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "white:gif_" + uid + "_" + idx, img);
                    tex.upload();
                    MinecraftClient.getInstance().getTextureManager().registerTexture(frameIds.get(idx), tex);
                }
                loaded = true;
            };
            if (MinecraftClient.getInstance().isOnThread()) {
                uploadTask.run();
            } else {
                MinecraftClient.getInstance().execute(uploadTask);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int readDelay(ImageReader reader, int index) {
        try {
            IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(index)
                    .getAsTree(reader.getImageMetadata(index).getNativeMetadataFormatName());
            IIOMetadataNode gce = findNode(root, "GraphicControlExtension");
            if (gce != null) {
                int cs = Integer.parseInt(gce.getAttribute("delayTime"));
                return Math.max(cs * 10, 20);
            }
        } catch (Exception ignored) {}
        return 100;
    }

    private IIOMetadataNode findNode(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equalsIgnoreCase(name))
                return (IIOMetadataNode) root.item(i);
            if (root.item(i) instanceof IIOMetadataNode child) {
                IIOMetadataNode found = findNode(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private BufferedImage copyImage(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    private BufferedImage makeTransparent(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();

        int c0 = img.getRGB(0, 0);
        int alpha0 = (c0 >>> 24);
        if (alpha0 < 100) {
            // Already transparent
            return img;
        }

        // Check if corners are light/pastel background
        int[] corners = {img.getRGB(0, 0), img.getRGB(w - 1, 0), img.getRGB(0, h - 1), img.getRGB(w - 1, h - 1)};
        boolean isBrightBg = false;
        for (int c : corners) {
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF;
            if (r > 180 && g > 180) { isBrightBg = true; break; }
        }
        if (!isBrightBg) return img;

        boolean[][] isLine = new boolean[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = img.getRGB(x, y);
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                if (r < 215 && g < 215 && b < 215) {
                    isLine[x][y] = true;
                }
            }
        }

        final int R = 3;
        // Phase 1: Thick barrier (radius R)
        boolean[][] thickBarrier = new boolean[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (isLine[x][y]) {
                    for (int dy = -R; dy <= R; dy++) {
                        for (int dx = -R; dx <= R; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx >= 0 && nx < w && ny >= 0 && ny < h) thickBarrier[nx][ny] = true;
                        }
                    }
                }
            }
        }

        // Find bottom cut endpoints
        int leftCutX = -1, leftCutY = -1;
        int rightCutX = -1, rightCutY = -1;
        for (int y = h - 1; y >= (int)(h * 0.65); y--) {
            for (int x = 0; x < w / 2; x++) {
                if (isLine[x][y] && leftCutX == -1) {
                    leftCutX = x; leftCutY = y;
                }
            }
            for (int x = w - 1; x >= w / 2; x--) {
                if (isLine[x][y] && rightCutX == -1) {
                    rightCutX = x; rightCutY = y;
                }
            }
            if (leftCutX != -1 && rightCutX != -1) break;
        }

        boolean hasBottomCut = (leftCutX != -1 && rightCutX != -1 
                && (rightCutX - leftCutX) > (w * 0.15) 
                && Math.abs(leftCutY - rightCutY) <= Math.max(15, (int)(h * 0.05)));
        boolean[][] cutBarrier = new boolean[w][h];
        if (hasBottomCut) {
            int steps = Math.abs(rightCutX - leftCutX);
            for (int s = 0; s <= steps; s++) {
                int px = leftCutX + s;
                int py = leftCutY + (rightCutY - leftCutY) * s / steps;
                for (int dy = -R; dy <= R; dy++) {
                    int ny = py + dy;
                    if (ny >= 0 && ny < h && px >= 0 && px < w) {
                        thickBarrier[px][ny] = true;
                        cutBarrier[px][ny] = true;
                    }
                }
            }
        }

        // BFS: Outer background with thick barrier
        int[][] bgDist = new int[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                bgDist[x][y] = -1;
            }
        }
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            if (!thickBarrier[x][0] && bgDist[x][0] == -1) { queue.add(new int[]{x, 0}); bgDist[x][0] = 0; }
            if (!thickBarrier[x][h - 1] && bgDist[x][h - 1] == -1) { queue.add(new int[]{x, h - 1}); bgDist[x][h - 1] = 0; }
        }
        for (int y = 0; y < h; y++) {
            if (!thickBarrier[0][y] && bgDist[0][y] == -1) { queue.add(new int[]{0, y}); bgDist[0][y] = 0; }
            if (!thickBarrier[w - 1][y] && bgDist[w - 1][y] == -1) { queue.add(new int[]{w - 1, y}); bgDist[w - 1][y] = 0; }
        }

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            int[] pt = queue.poll();
            int x = pt[0], y = pt[1];
            for (int i = 0; i < 4; i++) {
                int nx = x + dx[i];
                int ny = y + dy[i];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h && bgDist[nx][ny] == -1 && !thickBarrier[nx][ny]) {
                    bgDist[nx][ny] = 0;
                    queue.add(new int[]{nx, ny});
                }
            }
        }

        // Phase 2: Expand at most R steps toward isLine to completely eliminate halo
        ArrayDeque<int[]> expandQueue = new ArrayDeque<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (bgDist[x][y] == 0) {
                    expandQueue.add(new int[]{x, y});
                }
            }
        }

        while (!expandQueue.isEmpty()) {
            int[] pt = expandQueue.poll();
            int x = pt[0], y = pt[1];
            int curDist = bgDist[x][y];
            if (curDist >= R) continue;

            for (int i = 0; i < 4; i++) {
                int nx = x + dx[i];
                int ny = y + dy[i];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h && bgDist[nx][ny] == -1 && !isLine[nx][ny] && !cutBarrier[nx][ny]) {
                    int c = img.getRGB(nx, ny);
                    int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                    if (r >= 180 && g >= 180 && b >= 180) {
                        bgDist[nx][ny] = curDist + 1;
                        expandQueue.add(new int[]{nx, ny});
                    }
                }
            }
        }

        BufferedImage res = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean clear = (bgDist[x][y] >= 0);
                if (clear && !isLine[x][y]) {
                    res.setRGB(x, y, 0x00000000);
                } else {
                    res.setRGB(x, y, img.getRGB(x, y));
                }
            }
        }
        return res;
    }

    public Identifier currentFrame() {
        if (!loaded || frameIds.isEmpty()) return null;
        if (frameIds.size() == 1) return frameIds.get(0);
        if (startTime == -1) startTime = System.currentTimeMillis();
        if (totalDuration <= 0) return frameIds.get(0);

        long elapsed = (System.currentTimeMillis() - startTime) % totalDuration;
        int acc = 0;
        for (int i = 0; i < frameIds.size(); i++) {
            acc += delays.get(i);
            if (elapsed < acc) return frameIds.get(i);
        }
        return frameIds.get(frameIds.size() - 1);
    }

    public void draw(DrawContext ctx, int x, int y, int w, int h) {
        draw(ctx, x, y, w, h, 1f);
    }

    public void draw(DrawContext ctx, int x, int y, int w, int h, float alpha) {
        int a = (int)(Math.min(Math.max(alpha, 0f), 1f) * 255) & 0xFF;
        draw(ctx, x, y, w, h, (a << 24) | 0x00FFFFFF);
    }

    public void draw(DrawContext ctx, int x, int y, int w, int h, int color) {
        Identifier frame = currentFrame();
        if (frame == null) return;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, frame, x, y, 0f, 0f, w, h, w, h, color);
    }

    public void render(float x, float y, float w, float h, int color) {
        render(x, y, w, h, color, 0f);
    }

    public void render(float x, float y, float w, float h, int color, float radius) {
        Identifier frame = currentFrame();
        if (frame == null) return;
        Draw.texture(frame, x, y, w, h, 0f, 0f, 1f, 1f, color, 1f, radius);
    }

    public void reset()           { startTime = System.currentTimeMillis(); }
    public boolean isLoaded()     { return loaded; }
    public int getWidth()         { return width; }
    public int getHeight()        { return height; }

    public void destroy() {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<Identifier> copy = new ArrayList<>(frameIds);
        mc.execute(() -> copy.forEach(id -> mc.getTextureManager().destroyTexture(id)));
        frameIds.clear();
        delays.clear();
        loaded = false;
    }
}


package dev.hatek.client.media;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Загрузка картинок и GIF из папки config/hatek_client/media.
 * Кадры декодируются через ImageIO и заливаются в текстуры;
 * результат кэшируется по имени файла.
 */
public final class MediaManager {
    private static final Map<String, MediaClip> CACHE = new ConcurrentHashMap<>();
    private static final int MAX_FRAMES = 60;
    private static final int MAX_DIM = 256;

    private MediaManager() {
    }

    public static Path mediaDir() {
        Path dir = FabricLoader.getInstance().getConfigDir()
                .resolve("hatek_client").resolve("media");
        try {
            Files.createDirectories(dir);
            Path readme = dir.resolve("readme.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme,
                        "Put .gif / .png / .jpg files here and reference them by file name\n"
                                + "in the Interface module settings (Media File).\n"
                                + "Example: logo.gif\n");
            }
        } catch (IOException ignored) {
        }
        return dir;
    }

    /** Имена файлов, доступных для выбора. */
    public static List<String> listMedia() {
        List<String> out = new ArrayList<>();
        Path dir = mediaDir();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (Files.isRegularFile(path)
                        && (name.endsWith(".gif") || name.endsWith(".png")
                        || name.endsWith(".jpg") || name.endsWith(".jpeg"))) {
                    out.add(path.getFileName().toString());
                }
            }
        } catch (IOException ignored) {
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Возвращает клип или null, если файл не найден / не декодируется. */
    public static MediaClip get(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.strip();
        MediaClip cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        MediaClip loaded = load(key);
        if (loaded != null) {
            CACHE.put(key, loaded);
        }
        return loaded;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static MediaClip load(String name) {
        try {
            Path dir = mediaDir();
            Path file = dir.resolve(name).normalize();
            if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
                return null;
            }
            List<MediaClip.Frame> frames;
            if (name.toLowerCase(Locale.ROOT).endsWith(".gif")) {
                frames = decodeGif(file);
            } else {
                frames = decodeStatic(file);
            }
            if (frames.isEmpty()) {
                return null;
            }
            return new MediaClip(frames);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<MediaClip.Frame> decodeStatic(Path file) throws IOException {
        BufferedImage img = ImageIO.read(file.toFile());
        if (img == null) {
            return List.of();
        }
        return List.of(upload(fit(img), Integer.MAX_VALUE));
    }

    private static List<MediaClip.Frame> decodeGif(Path file) throws IOException {
        List<MediaClip.Frame> out = new ArrayList<>();
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
            reader.setInput(in);
            int count = Math.min(reader.getNumImages(true), MAX_FRAMES);
            int w = reader.getWidth(0);
            int h = reader.getHeight(0);
            BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();

            int prevDisposal = 0;
            int prevX = 0, prevY = 0, prevW = 0, prevH = 0;
            for (int i = 0; i < count; i++) {
                BufferedImage frame = reader.read(i);
                IIOMetadata meta = reader.getImageMetadata(i);
                int[] geo = frameGeometry(meta);
                int delay = frameDelay(meta);
                int disposal = frameDisposal(meta);

                if (i > 0 && prevDisposal == 2) {
                    g.setComposite(AlphaComposite.Clear);
                    g.fillRect(prevX, prevY, prevW, prevH);
                    g.setComposite(AlphaComposite.SrcOver);
                }
                g.drawImage(frame, geo[0], geo[1], null);
                out.add(upload(fit(copy(canvas)), delay));

                prevDisposal = disposal;
                prevX = geo[0];
                prevY = geo[1];
                prevW = frame.getWidth();
                prevH = frame.getHeight();
            }
            g.dispose();
            reader.dispose();
        }
        return out;
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }

    /** Уменьшает слишком большие кадры, сохраняя пропорции. */
    private static BufferedImage fit(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int m = Math.max(w, h);
        if (m <= MAX_DIM) {
            return src;
        }
        float k = MAX_DIM / (float) m;
        BufferedImage dst = new BufferedImage(Math.max(1, Math.round(w * k)),
                Math.max(1, Math.round(h * k)), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, 0, 0, dst.getWidth(), dst.getHeight(), null);
        g.dispose();
        return dst;
    }

    private static int[] frameGeometry(IIOMetadata meta) {
        try {
            NodeList nodes = tree(meta).getElementsByTagName("ImageDescriptor");
            IIOMetadataNode d = (IIOMetadataNode) nodes.item(0);
            return new int[]{
                    Integer.parseInt(d.getAttribute("imageLeftPosition")),
                    Integer.parseInt(d.getAttribute("imageTopPosition"))};
        } catch (Exception e) {
            return new int[]{0, 0};
        }
    }

    private static int frameDelay(IIOMetadata meta) {
        try {
            NodeList nodes = tree(meta).getElementsByTagName("GraphicControlExtension");
            int hundredths = Integer.parseInt(
                    ((IIOMetadataNode) nodes.item(0)).getAttribute("delayTime"));
            return Math.max(20, hundredths * 10);
        } catch (Exception e) {
            return 100;
        }
    }

    private static int frameDisposal(IIOMetadata meta) {
        try {
            NodeList nodes = tree(meta).getElementsByTagName("GraphicControlExtension");
            String method = ((IIOMetadataNode) nodes.item(0)).getAttribute("disposalMethod");
            return "restoreToBackgroundColor".equals(method) ? 2 : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static IIOMetadataNode tree(IIOMetadata meta) {
        return (IIOMetadataNode) meta.getAsTree("javax_imageio_gif_image_1.0");
    }

    private static MediaClip.Frame upload(BufferedImage img, int delayMs) {
        int w = img.getWidth();
        int h = img.getHeight();
        NativeImage nativeImage = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int abgr = (argb & 0xFF000000)
                        | ((argb & 0xFF) << 16)
                        | (argb & 0xFF00)
                        | ((argb & 0xFF0000) >> 16);
                nativeImage.setPixel(x, y, abgr);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath("hatek_client",
                "media/" + UUID.randomUUID());
        Minecraft.getInstance().getTextureManager().register(id,
                new DynamicTexture(() -> "hatek_media", nativeImage));
        return new MediaClip.Frame(id, w, h, delayMs);
    }
}

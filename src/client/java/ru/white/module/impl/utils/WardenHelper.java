package ru.white.module.impl.utils;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import ru.white.Client;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.ServerUtil;
import ru.white.utils.other.Instance;
import ru.white.utils.other.Projection;
import ru.white.utils.render.ItemRender;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ModuleInfo(name = "Warden Helper", desc = "Визуальный помощник в зоне Вардена: 2D-таймеры над сундуками и ESP доступного лута", category = Category.OTHER)
public class WardenHelper extends Module {
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");
    private static final Pattern SECONDS_PATTERN = Pattern.compile("(\\d+)\\s*(с|s|сек|sec)");

    public final BooleanSetting timerTags = new BooleanSetting(this, "Таймеры", true);
    public final BooleanSetting readyBox = new BooleanSetting(this, "Бокс готовых", true);
    public final BooleanSetting timerBox = new BooleanSetting(this, "Бокс с таймером", false);

    private final List<BlockPos> chests = new CopyOnWriteArrayList<>();
    private final Map<BlockPos, TimerEntry> timers = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> remaining = new ConcurrentHashMap<>();

    private int scanTimer;

    private static final int COLOR_RED = ColorUtil.getColor(255, 60, 60, 180);
    private static final int COLOR_YELLOW = ColorUtil.getColor(255, 220, 50, 180);
    private static final int COLOR_GREEN = ColorUtil.getColor(60, 255, 60, 180);
    private static final int COLOR_READY = ColorUtil.getColor(255, 100, 100, 200);

    private final BufferAllocator allocator = new BufferAllocator(1 << 18);

    private static final RenderPipeline BOX_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "warden_helper_esp"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer BOX_LAYER = RenderLayer.of(
            "warden_helper_esp",
            RenderSetup.builder(BOX_PIPELINE).expectedBufferSize(1 << 12).build()
    );

    public static WardenHelper get() {
        return Instance.get(WardenHelper.class);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        reset();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        reset();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        reset();
    }

    private void reset() {
        chests.clear();
        timers.clear();
        remaining.clear();
        scanTimer = 0;
    }

    @EventHandler
    public void onTick(EventTick event) {
        if (mc.player == null || mc.world == null) return;

        if (!isInWardenZone()) {
            if (!chests.isEmpty()) chests.clear();
            remaining.clear();
            return;
        }

        if (--scanTimer <= 0) {
            scanTimer = 5;
            scanChests();
        }

        for (BlockPos pos : chests) {
            remaining.put(pos, computeRemaining(pos));
        }
    }

    /** Сундуки Варден зоны из загруженных чанков. */
    public List<BlockPos> getChests() {
        return chests;
    }

    /** Остаток таймера в мс, либо -1 если сундук готов (голограммы нет). */
    public long getRemaining(BlockPos pos) {
        Long cached = remaining.get(pos);
        if (cached != null) return cached;
        long value = computeRemaining(pos);
        remaining.put(pos, value);
        return value;
    }

    public boolean isReady(BlockPos pos) {
        return getRemaining(pos) < 0;
    }

    private void scanChests() {
        List<BlockPos> found = new ArrayList<>();
        ChunkPos playerChunk = mc.player.getChunkPos();
        int radius = Math.min(10, mc.options.getViewDistance().getValue());

        for (int chunkX = playerChunk.x - radius; chunkX <= playerChunk.x + radius; chunkX++) {
            for (int chunkZ = playerChunk.z - radius; chunkZ <= playerChunk.z + radius; chunkZ++) {
                WorldChunk chunk = mc.world.getChunk(chunkX, chunkZ);
                if (chunk == null) continue;

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity.isRemoved()) continue;
                    if (!(blockEntity instanceof ChestBlockEntity)) continue;

                    BlockPos pos = blockEntity.getPos();
                    if (isChestInZone(pos)) found.add(pos.toImmutable());
                }
            }
        }

        chests.clear();
        chests.addAll(found);
        remaining.keySet().removeIf(pos -> !found.contains(pos));
    }

    private long computeRemaining(BlockPos pos) {
        int anarchy = ServerUtil.anarchy;
        long hologram = readHologram(pos);

        if (hologram >= 0) {
            TimerEntry entry = timers.get(pos);
            if (entry == null || entry.anarchy != anarchy || Math.abs(entry.remaining() - hologram) > 5000L) {
                timers.put(pos, new TimerEntry(hologram, anarchy));
            }
            return hologram;
        }

        TimerEntry entry = timers.get(pos);
        if (entry != null) {
            if (entry.anarchy != anarchy) return -1;
            long left = entry.remaining();
            if (left > 0) return left;
            timers.remove(pos);
        }
        return -1;
    }

    /** Есть ли прямо сейчас голограмма с таймером над сундуком. */
    public boolean hasHologram(BlockPos pos) {
        return mc.world != null && readHologram(pos) >= 0;
    }

    /** Читает голограмму над сундуком. Возвращает мс либо -1. */
    private long readHologram(BlockPos pos) {
        Box search = new Box(pos).expand(1.0, 4.0, 1.0);
        List<ArmorStandEntity> stands = mc.world.getEntitiesByClass(ArmorStandEntity.class, search, e -> e.hasCustomName());

        for (ArmorStandEntity stand : stands) {
            BlockPos standPos = stand.getBlockPos();
            if (standPos.getX() != pos.getX() || standPos.getZ() != pos.getZ()) continue;

            String name = stand.getCustomName() == null ? null : stand.getCustomName().getString();
            if (name == null) continue;

            String clean = ColorUtil.removeFormatting(name);
            if (clean == null) continue;

            long seconds = parseTimeToSeconds(clean);
            if (seconds >= 0) return seconds * 1000L;
        }
        return -1;
    }

    private long parseTimeToSeconds(String text) {
        Matcher timeMatcher = TIME_PATTERN.matcher(text);
        if (timeMatcher.find()) {
            if (timeMatcher.group(3) != null) {
                int hours = Integer.parseInt(timeMatcher.group(1));
                int minutes = Integer.parseInt(timeMatcher.group(2));
                int seconds = Integer.parseInt(timeMatcher.group(3));
                return hours * 3600L + minutes * 60L + seconds;
            }
            int minutes = Integer.parseInt(timeMatcher.group(1));
            int seconds = Integer.parseInt(timeMatcher.group(2));
            return minutes * 60L + seconds;
        }

        Matcher secMatcher = SECONDS_PATTERN.matcher(text);
        if (secMatcher.find()) return Long.parseLong(secMatcher.group(1));

        return -1;
    }

    public boolean isInWardenZone() {
        if (mc.player == null) return false;
        double x = mc.player.getX();
        double z = mc.player.getZ();
        return Math.abs(Math.abs(x) - 2000.0) <= 150.0 && Math.abs(Math.abs(z) - 2000.0) <= 150.0;
    }

    public boolean isChestInZone(BlockPos pos) {
        if (mc.player == null) return false;
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        boolean inXZ = Math.abs(Math.abs(x) - 2000.0) <= 150.0 && Math.abs(Math.abs(z) - 2000.0) <= 150.0;
        boolean inY = Math.abs(y - mc.player.getY()) <= 30.0;
        return inXZ && inY;
    }

    private int getTimerColor(long remainingMs) {
        if (remainingMs < 0) {
            float pulse = (float) (Math.sin(System.currentTimeMillis() / 200.0) * 0.3 + 0.7);
            return ColorUtil.getColor(255, (int) (100 * pulse), (int) (100 * pulse), 220);
        }

        float remaining = remainingMs / 1000f;

        if (remaining > 120) return COLOR_RED;
        if (remaining > 20) {
            float factor = 1.0f - (remaining - 20f) / 100f;
            return ColorUtil.interpolateColor(COLOR_RED, COLOR_YELLOW, factor);
        }
        float factor = 1.0f - remaining / 20f;
        return ColorUtil.interpolateColor(COLOR_YELLOW, COLOR_GREEN, factor);
    }

    @EventHandler(priority = -500)
    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.world == null) return;
        if (chests.isEmpty()) return;
        if (!readyBox.getValue() && !timerBox.getValue()) return;

        Matrix4f matrix = event.getMatrixStack().peek().getPositionMatrix();
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
        VertexConsumer buffer = immediate.getBuffer(BOX_LAYER);

        boolean drawn = false;

        for (BlockPos pos : chests) {
            long left = getRemaining(pos);
            boolean ready = left < 0;

            if (ready && !readyBox.getValue()) continue;
            if (!ready && !timerBox.getValue()) continue;

            int color = ready ? COLOR_READY : getTimerColor(left);

            float x1 = (float) (pos.getX() - camPos.x);
            float y1 = (float) (pos.getY() - camPos.y);
            float z1 = (float) (pos.getZ() - camPos.z);

            int cB = ColorUtil.replAlpha(color, 0.8f);
            int cT = ColorUtil.replAlpha(color, 0.0f);

            drawGradientBox(buffer, matrix, x1, y1, z1, x1 + 1f, y1 + 1f, z1 + 1f, cB, cT);
            drawn = true;
        }

        if (drawn) immediate.draw();
    }

    private void drawGradientBox(VertexConsumer b, Matrix4f m,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 int cB, int cT) {
        b.vertex(m, x1, y1, z1).color(cB);
        b.vertex(m, x2, y1, z1).color(cB);
        b.vertex(m, x2, y1, z2).color(cB);
        b.vertex(m, x1, y1, z2).color(cB);

        b.vertex(m, x1, y2, z1).color(cT);
        b.vertex(m, x1, y2, z2).color(cT);
        b.vertex(m, x2, y2, z2).color(cT);
        b.vertex(m, x2, y2, z1).color(cT);

        b.vertex(m, x1, y1, z1).color(cB);
        b.vertex(m, x1, y2, z1).color(cT);
        b.vertex(m, x2, y2, z1).color(cT);
        b.vertex(m, x2, y1, z1).color(cB);

        b.vertex(m, x1, y1, z2).color(cB);
        b.vertex(m, x2, y1, z2).color(cB);
        b.vertex(m, x2, y2, z2).color(cT);
        b.vertex(m, x1, y2, z2).color(cT);

        b.vertex(m, x1, y1, z1).color(cB);
        b.vertex(m, x1, y1, z2).color(cB);
        b.vertex(m, x1, y2, z2).color(cT);
        b.vertex(m, x1, y2, z1).color(cT);

        b.vertex(m, x2, y1, z1).color(cB);
        b.vertex(m, x2, y2, z1).color(cT);
        b.vertex(m, x2, y2, z2).color(cT);
        b.vertex(m, x2, y1, z2).color(cB);
    }

    private static final class TimerEntry {
        private final long duration;
        private final long start;
        private final int anarchy;

        TimerEntry(long duration, int anarchy) {
            this.duration = duration;
            this.start = System.currentTimeMillis();
            this.anarchy = anarchy;
        }

        long remaining() {
            return Math.max(0L, duration - (System.currentTimeMillis() - start));
        }
    }

    private static final ItemStack CHEST_ICON = new ItemStack(Items.CHEST);

    @EventHandler
    public void onDisplay(EventDisplay event) {
        if (!timerTags.getValue()) return;
        if (mc.player == null || mc.world == null || chests.isEmpty()) return;

        DrawContext context = event.getDrawContext();
        float scaleFix = 2F / (float) mc.getWindow().getScaleFactor();
        Font font = Fonts.sf_medium;
        float size = 6.5F;
        int background = ColorUtil.getColor(11, 11, 13, 200);

        for (BlockPos pos : chests) {
            long left = getRemaining(pos);
            if (left < 0) continue;

            Vec3d screen = Projection.worldSpaceToScreenSpace(
                    new Vec3d(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5));
            if (screen.z <= 0 || screen.z >= 1) continue;

            int totalSec = (int) (left / 1000L);
            String text = String.format("%02d:%02d", totalSec / 60, totalSec % 60);

            float textWidth = font.getWidth(text, size);
            float width = 16.5F + textWidth;
            float height = 12F;
            float x = (float) screen.x - width / 2F;
            float y = (float) screen.y - 6F;

            RenderUtil.Blur.blur(x, y, width, height, 1F, 3.5F, background);
            RenderUtil.Render2D.rect(x, y, width, height, ColorUtil.replAlpha(background, 0.55F), 3.5F);
            RenderUtil.Render2D.outline(x, y, width, height, 0.5F,
                    ColorUtil.replAlpha(getTimerColor(left), 0.65F), 3.5F);

            Client.get().render2D().flushAll();

            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate((x + 3F + 3.5F) * scaleFix, (y + height / 2F) * scaleFix);
            matrices.scale(7F / 16F, 7F / 16F);
            ItemRender.drawItemWithContext(context, CHEST_ICON, -8, -8, 1F, 1F);
            matrices.popMatrix();

            font.draw(text, x + 13F, y + height / 2F - font.getHeight(size) / 2F - 0.1F,
                    size, getTimerColor(left));
        }
    }
}

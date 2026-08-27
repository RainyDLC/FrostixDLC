package ru.white.utils.aura;

import lombok.experimental.UtilityClass;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.white.utils.annotation.IMinecraft;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Лаг-компенсация цели — ядро обхода reach-проверок GrimAC и Matrix.
 *
 * Как считают античиты: сервер (Grim) хранит историю позиций цели и проверяет
 * удар по хитбоксу, интерполированному на момент «сейчас - пинг игрока», а не
 * по актуальному положению. Matrix делает то же самое попроще. Если клиент
 * бьёт по актуальному (клиентскому) хитбоксу, то при пинге сервер видит удар
 * по «будущей» позиции цели — флаг Reach.
 *
 * Решение: держим кольцевой буфер позиций цели (time, pos, размеры), берём
 * бокс на момент now - delay (delay = пинг + запас), и дистанцию удара, и
 * рейкаст считаем по этому отложенному боксу — ровно как античит.
 */
@UtilityClass
public class LagCompensation implements IMinecraft {

    public record Sample(long time, double x, double y, double z, float width, float height) {}

    private static final Map<UUID, ArrayDeque<Sample>> HISTORY = new ConcurrentHashMap<>();
    private static final int MAX_SAMPLES = 64;
    private static final double MAX_TRACK_DIST = 12.0;

    /** Запись позиции цели каждый тик (вызывать из ауры). */
    public static void record(LivingEntity target) {
        if (target == null || mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        HISTORY.values().removeIf(deque -> {
            Sample last = deque.peekLast();
            return last == null || now - last.time() > 5000L;
        });
        if (target.isRemoved()) {
            HISTORY.remove(target.getUuid());
            return;
        }
        if (mc.player.squaredDistanceTo(target) > MAX_TRACK_DIST * MAX_TRACK_DIST) return;

        ArrayDeque<Sample> deque = HISTORY.computeIfAbsent(target.getUuid(), k -> new ArrayDeque<>());
        float w = target.getWidth();
        float h = target.getHeight();
        Sample last = deque.peekLast();
        if (last != null && now - last.time() < 25L
                && Math.abs(last.x() - target.getX()) < 0.01
                && Math.abs(last.y() - target.getY()) < 0.01
                && Math.abs(last.z() - target.getZ()) < 0.01
                && last.width() == w && last.height() == h) {
            return; // без движения — не спамим сэмплами
        }
        deque.addLast(new Sample(now, target.getX(), target.getY(), target.getZ(), w, h));
        while (deque.size() > MAX_SAMPLES) deque.removeFirst();
    }

    public static void reset() {
        HISTORY.clear();
    }

    /** Пинг игрока (мс). */
    private static int ping() {
        try {
            if (mc.getNetworkHandler() != null && mc.player != null) {
                PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                if (entry != null) return Math.max(0, entry.getLatency());
            }
        } catch (Exception ignored) {
        }
        return 50;
    }

    /**
     * Задержка бокса: Grim проверяет по транзакционному пингу (пинг + запас),
     * запас сверху гасит джиттер пинга. Такой проверки хватает и для Matrix —
     * его лаг-компенсация мягче.
     */
    public static long delayMs() {
        long base = ping() + 60L;
        return MathHelper.clamp(base, 50L, 1200L);
    }

    /** Отложенный (лаг-компенсированный) хитбокс цели. */
    public static Box delayedBox(LivingEntity target) {
        ArrayDeque<Sample> deque = HISTORY.get(target.getUuid());
        if (deque == null || deque.isEmpty()) return target.getBoundingBox();

        long targetTime = System.currentTimeMillis() - delayMs();

        Sample s0 = null;
        Sample s1 = null;
        for (Sample s : deque) {
            if (s.time() <= targetTime) {
                s0 = s;
            } else {
                s1 = s;
                break;
            }
        }
        if (s0 == null) {
            // история слишком свежая — берём самый старый сэмпл (самый «серверный»)
            return boxOf(deque.peekFirst());
        }
        if (s1 == null) {
            // целиком в прошлом — берём самый новый отложенный
            return boxOf(deque.peekLast());
        }
        float alpha = MathHelper.clamp(
                (targetTime - s0.time()) / (float) Math.max(1L, s1.time() - s0.time()), 0F, 1F);
        double x = MathHelper.lerp(alpha, s0.x(), s1.x());
        double y = MathHelper.lerp(alpha, s0.y(), s1.y());
        double z = MathHelper.lerp(alpha, s0.z(), s1.z());
        return boxAround(x, y, z, s1.width(), s1.height());
    }

    private static Box boxOf(Sample s) {
        return boxAround(s.x(), s.y(), s.z(), s.width(), s.height());
    }

    private static Box boxAround(double x, double y, double z, float width, float height) {
        double half = width / 2.0;
        return new Box(x - half, y, z - half, x + half, y + height, z + half);
    }

    /** Дистанция от глаз до произвольного бокса. */
    private static double distanceToBox(Box box) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d closest = new Vec3d(
                MathHelper.clamp(eye.x, box.minX, box.maxX),
                MathHelper.clamp(eye.y, box.minY, box.maxY),
                MathHelper.clamp(eye.z, box.minZ, box.maxZ));
        return closest.subtract(eye).length();
    }

    /** Дистанция от глаз до отложенного хитбокса (как getStrictDistance, но по-гримовски). */
    public static double distanceToDelayed(LivingEntity target) {
        return distanceToBox(delayedBox(target));
    }

    /** Дистанция от глаз до живого (клиентского) хитбокса. */
    public static double distanceToLive(LivingEntity target) {
        return distanceToBox(target.getBoundingBox());
    }

    /**
     * Дистанция, по которой решаем, можно ли бить. Считается по живому
     * (клиентскому) хитбоксу.
     *
     * Живой бокс — и есть правильная система отсчёта. Клиент видит цель уже с
     * запозданием примерно в половину RTT, а Grim при проверке удара отматывает
     * цель назад именно к тому положению, которое атакующий клиент мог видеть.
     * Прежний отложенный бокс добавлял к этому ещё пинг+60 мс, то есть считал
     * задержку дважды: аура думала, что цель дальше, чем её видит сервер, и
     * отказывалась бить.
     *
     * Минимум из живого и отложенного бокса тоже не годится: он принимал удар по
     * положению, которого у сервера в этот момент не было, а расходятся эти два
     * представления сильнее всего как раз в движении — отсюда флаги на reach при
     * ходьбе.
     */
    public static double attackDistance(LivingEntity target) {
        return distanceToLive(target);
    }

    /**
     * Рейкаст текущего взгляда по живому хитбоксу цели плюс проверка блоков на
     * пути — та же система отсчёта, что и у {@link #attackDistance}.
     */
    public static boolean rayHits(LivingEntity target, float range) {
        return rayHits(target, range, false);
    }

    /**
     * @param ignoreBlocks не отбрасывать удар из-за блока на пути (настройка
     *                     «Бить через блоки»). Без этого настройка не работала:
     *                     выбор цели её учитывал, а рейкаст всё равно отклонял удар.
     */
    public static boolean rayHits(LivingEntity target, float range, boolean ignoreBlocks) {
        if (mc.player == null || mc.world == null) return false;
        return rayHitsBox(target.getBoundingBox(), range, ignoreBlocks);
    }

    /** Совместимость: старое имя, теперь учитывает оба представления цели. */
    public static boolean rayHitsDelayed(LivingEntity target, float range) {
        return rayHits(target, range, false);
    }

    private static boolean rayHitsBox(Box raw, float range, boolean ignoreBlocks) {
        Box box = raw.expand(0.03); // эпсилон на погрешность интерполяции
        Vec3d eye = mc.player.getEyePos();
        Vec3d dir = rotationVector(mc.player.getYaw(), mc.player.getPitch());
        Vec3d end = eye.add(dir.multiply(range));

        var hit = box.raycast(eye, end);
        if (hit.isEmpty()) return false;
        if (ignoreBlocks) return true;

        // блок ближе точки попадания — удара нет
        BlockHitResult block = mc.world.raycast(new net.minecraft.world.RaycastContext(
                eye, end, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, mc.player));
        if (block.getType() != HitResult.Type.MISS
                && block.getPos().squaredDistanceTo(eye) < hit.get().squaredDistanceTo(eye)) {
            return false;
        }
        return true;
    }

    /**
     * Мягкая проверка видимости для выбора цели.
     *
     * Ванильный {@code Entity.canSee} стреляет лучом строго глаза→глаза, поэтому
     * цель за забором, полублоком или углом считалась невидимой целиком, и аура
     * отказывалась её брать, хотя корпус открыт. Проверяем несколько точек по
     * высоте хитбокса — достаточно, чтобы была видна хоть одна.
     */
    public static boolean isVisibleLoose(LivingEntity target) {
        if (mc.player == null || mc.world == null) return false;

        Vec3d eye = mc.player.getEyePos();
        Box box = target.getBoundingBox();
        double cx = (box.minX + box.maxX) / 2.0;
        double cz = (box.minZ + box.maxZ) / 2.0;

        double[] heights = {
                target.getEyeY() - target.getY(),   // голова
                target.getHeight() * 0.5,           // корпус
                0.15                                // ноги
        };
        for (double h : heights) {
            Vec3d point = new Vec3d(cx, target.getY() + h, cz);
            BlockHitResult block = mc.world.raycast(new net.minecraft.world.RaycastContext(
                    eye, point, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE, mc.player));
            if (block.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    /** Вектор направления по yaw/pitch (как в ванильной камере). */
    private static Vec3d rotationVector(float yaw, float pitch) {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        float f = MathHelper.cos(-yawRad - (float) Math.PI);
        float g = MathHelper.sin(-yawRad - (float) Math.PI);
        float h = -MathHelper.cos(-pitchRad);
        float i = MathHelper.sin(-pitchRad);
        return new Vec3d(g * h, i, f * h);
    }

    /** Безопасный reach: чуть меньше лимита античита (эпсилон Grim). */
    public static double safeReach(float attackRange) {
        return attackRange - 0.06;
    }
}

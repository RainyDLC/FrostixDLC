package fun.newrar.utils.aura;

import fun.newrar.module.impl.combat.AttackAura;
import fun.newrar.utils.annotation.IMinecraft;
import fun.newrar.utils.math.ServerUtil;
import lombok.experimental.UtilityClass;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Серверный лимит удара — то, что проверяет сервер, а не клиент. Настроек в GUI нет,
 * всё подбирается по серверу автоматически.
 *
 * Клиентская тройка (2.95) это только рендер-хелп ванильного прицела. Удар принимается
 * сервером, если дистанция "глаз -> ближайшая точка бокса" укладывается в ЕГО лимит:
 *   • ваниль 1.20.5+: ENTITY_INTERACTION_RANGE (3.0) + серверный запас на пинг;
 *   • античиты с лаг-компенсацией (Grim и его форки: ReallyWorld, FunTime, SpookyTime)
 *     считают дистанцию до ЗАДЕРЖАННОГО бокса цели — её позиция ~пинг назад, а не живая.
 *
 * Отсюда 4+ блока: цель, уходящая от нас, на стороне сервера ещё "ближе", поэтому удар
 * с 4+ блоков визуальной дистанции проходит проверку — целиться и валидировать дистанцию
 * надо по тому же задержанному боксу.
 *
 * Порядок пакетов тоже учитывается: удар уходит из EventPostMotion (после пакета движения),
 * т.е. сервер считает дистанцию по текущему тику, а не по прошлому.
 */
@UtilityClass
public class ServerReach implements IMinecraft {

    /** limit — что даёт сервер, tolerance — запас на пинг/округление, lagComp — целиться в задержанный бокс. */
    private record Preset(String name, double limit, double tolerance, boolean lagComp) {}

    private static final Preset UNKNOWN = new Preset("Неизвестный", 3.15D, 0.40D, true);
    private static final Preset REALLY_WORLD = new Preset("ReallyWorld", 3.35D, 0.45D, true);
    private static final Preset FUN_TIME = new Preset("FunTime", 3.20D, 0.40D, true);
    private static final Preset SPOOKY_TIME = new Preset("SpookyTime", 3.40D, 0.45D, true);
    private static final Preset HOLY_WORLD = new Preset("HolyWorld", 3.60D, 0.45D, true);
    private static final Preset VANILLA = new Preset("Vanilla", 3.90D, 0.30D, false);
    /** Сервера без валидации дистанции — предел даёт только клиент. */
    private static final Preset NO_CHECK = new Preset("NoCheck", 5.95D, 0.50D, true);

    private static AttackAura aura() {
        try {
            return AttackAura.get();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean enabled() {
        AttackAura aura = aura();
        return aura != null && aura.isEnabled();
    }

    private static Preset preset() {
        try {
            if (ServerUtil.isReallyWorld()) return REALLY_WORLD;
            if (ServerUtil.isFunTime()) return FUN_TIME;
            if (ServerUtil.isCopyTime()) return SPOOKY_TIME;
            if (ServerUtil.isHolyWorld()) return HOLY_WORLD;
            if (ServerUtil.isVanilla()) return VANILLA;

            String raw = ServerUtil.server;
            if (raw != null && raw.trim().isEmpty()) return VANILLA;
            if (raw != null && raw.contains("anarchy")) return NO_CHECK;
            return UNKNOWN;
        } catch (Throwable ignored) {
            return UNKNOWN;
        }
    }

    /** Что реально даёт сервер. */
    public static double limit() {
        return preset().limit();
    }

    public static double tolerance() {
        return preset().tolerance();
    }

    /** Максимум, который сервер ещё съест. */
    public static double accepted() {
        return limit() + tolerance();
    }

    public static boolean lagComp() {
        return enabled() && preset().lagComp();
    }

    /** Удар после пакета движения: сервер считает дистанцию по свежему тику. Всегда включён. */
    public static boolean postMotion() {
        return true;
    }

    public static boolean throughWalls() {
        AttackAura aura = aura();
        try {
            return aura != null && aura.others.getValue("Бить через блоки");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Бокс, по которому считает сервер: задержанный при лаг-компенсации, иначе живой. */
    public static Box attackBox(LivingEntity target) {
        if (target == null) return null;
        return lagComp() ? LagCompensation.delayedBox(target) : target.getBoundingBox();
    }

    public static double attackDistance(LivingEntity target) {
        if (target == null || mc.player == null) return Double.MAX_VALUE;
        return distanceToBox(attackBox(target));
    }

    /** Рабочая дистанция: клиентский слайдер, урезанный серверным лимитом. */
    public static double reach(double clientRange) {
        return enabled()
                ? Math.min(clientRange - 0.05D, accepted())
                : LagCompensation.safeReach((float) clientRange);
    }

    public static boolean canReach(LivingEntity target, double clientRange) {
        return target != null && attackDistance(target) < reach(clientRange);
    }

    /** Точка на серверном боксе в пределах лимита; стены игнорируются по флагу ауры. */
    public static Vec3d aimPoint(LivingEntity target, double reach) {
        Box box = attackBox(target);
        if (box == null || mc.player == null) return mc.player == null ? Vec3d.ZERO : mc.player.getEyePos();

        Vec3d reachable = UBoxPoints.getReachablePoint(box, reach, throughWalls());
        return reachable != null ? reachable : box.getCenter();
    }

    private static double distanceToBox(Box box) {
        if (box == null || mc.player == null) return Double.MAX_VALUE;
        Vec3d eye = mc.player.getEyePos();
        Vec3d closest = new Vec3d(
                MathHelper.clamp(eye.x, box.minX, box.maxX),
                MathHelper.clamp(eye.y, box.minY, box.maxY),
                MathHelper.clamp(eye.z, box.minZ, box.maxZ));
        return closest.subtract(eye).length();
    }
}

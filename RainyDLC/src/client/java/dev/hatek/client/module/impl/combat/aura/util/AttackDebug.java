package dev.hatek.client.module.impl.combat.aura.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Диагностика причин отклонения ударов (идея: attackDebugStats из obrti-sources).
 * Включается опцией "Attack Debug" в Checks: раз в 5 секунд показывает
 * в оверлее топ причин, по которым гейт не пустил удар, и счётчик ударов
 * (всего / по плану).
 */
public final class AttackDebug {
    private static final Map<String, Long> rejects = new LinkedHashMap<>();
    private static long plannedHits;
    private static long normalHits;
    private static long lastDumpMs;

    // --- детектор ghost hits: замах был, урона нет ---
    private static int pendingGhostId = -1;
    private static int pendingGhostTick;
    private static float pendingGhostDist;
    private static boolean pendingGhostBlocking;
    private static int ghostHits;
    private static float lastGhostDist;

    private AttackDebug() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void reset() {
        rejects.clear();
        plannedHits = 0L;
        normalHits = 0L;
        lastDumpMs = 0L;
        pendingGhostId = -1;
        ghostHits = 0;
        lastGhostDist = 0.0f;
    }

    public static void noteReject(String reason) {
        if (reason == null) {
            return;
        }
        rejects.merge(reason, 1L, Long::sum);
    }

    public static void noteHit(boolean planned) {
        if (planned) {
            plannedHits++;
        } else {
            normalHits++;
        }
    }

    /** Вызывать после отправки удара: запоминаем цель для проверки урона. */
    public static void noteAttackSent(LivingEntity target) {
        Minecraft mc = Minecraft.getInstance();
        if (target == null || mc.player == null) {
            return;
        }
        pendingGhostId = target.getId();
        pendingGhostTick = mc.player.tickCount;
        pendingGhostDist = (float) AuraUtil.getStrictDistance(target);
        pendingGhostBlocking = target.isBlocking();
    }

    /**
     * Вызывать каждый тик. Если через 8 тиков после нашего удара у цели нет
     * hurtTime (анимации урона), она жива и не блокировала щитом — удар был
     * отменён сервером/античитом (ghost hit): частицы есть, урона нет.
     */
    public static void tickGhostCheck() {
        if (pendingGhostId == -1) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            pendingGhostId = -1;
            return;
        }
        if (mc.player.tickCount - pendingGhostTick < 8) {
            return;
        }
        Entity e = mc.level.getEntity(pendingGhostId);
        pendingGhostId = -1;
        if (!(e instanceof LivingEntity living) || !living.isAlive()) {
            return; // цель умерла — урон очевидно прошёл
        }
        if (pendingGhostBlocking || living.isBlocking()) {
            return; // блок щитом — отсутствие урона ожидаемо
        }
        if (living.hurtTime > 0) {
            return; // анимация урона есть — всё честно
        }
        ghostHits++;
        lastGhostDist = pendingGhostDist;
    }

    public static void maybeDump() {
        long now = System.currentTimeMillis();
        if (now - lastDumpMs < 5000L) {
            return;
        }
        lastDumpMs = now;
        if (rejects.isEmpty() && plannedHits == 0L && normalHits == 0L && ghostHits == 0) {
            return;
        }
        StringBuilder sb = new StringBuilder("§8[AuraDbg]§7 ");
        rejects.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(4)
                .forEach(e -> sb.append(e.getKey()).append('=').append(e.getValue()).append(' '));
        sb.append("§a✔").append(normalHits + plannedHits)
                .append(" §b⚡").append(plannedHits);
        if (ghostHits > 0) {
            sb.append(" §c👻").append(ghostHits)
                    .append('@').append(String.format("%.2f", lastGhostDist));
        }
        tell(sb.toString());
    }

    private static void tell(String msg) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui != null) {
                Component text = Component.literal(msg);
                mc.execute(() -> ((dev.hatek.mixin.accessor.GuiAccessor) mc.gui)
                        .hatek$hud().setOverlayMessage(text, false));
            }
        } catch (Exception ignored) {
        }
    }
}

package dev.hatek.client.module.impl.combat.aura.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

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

    private AttackDebug() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void reset() {
        rejects.clear();
        plannedHits = 0L;
        normalHits = 0L;
        lastDumpMs = 0L;
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

    public static void maybeDump() {
        long now = System.currentTimeMillis();
        if (now - lastDumpMs < 5000L) {
            return;
        }
        lastDumpMs = now;
        if (rejects.isEmpty() && plannedHits == 0L && normalHits == 0L) {
            return;
        }
        StringBuilder sb = new StringBuilder("§8[AuraDbg]§7 ");
        rejects.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(4)
                .forEach(e -> sb.append(e.getKey()).append('=').append(e.getValue()).append(' '));
        sb.append("§a✔").append(normalHits + plannedHits)
                .append(" §b⚡").append(plannedHits);
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

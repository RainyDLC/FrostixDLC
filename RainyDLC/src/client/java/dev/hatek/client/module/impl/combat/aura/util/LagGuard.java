package dev.hatek.client.module.impl.combat.aura.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.LivingEntity;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Гейт ударов вокруг лагов — причина «после подвисания бьёт, а урона нет».
 *
 * <ul>
 *   <li><b>Фриз клиента</b> (тик пришёл позже 150 мс): дальше идут догоняющие тики
 *   пачкой, пакеты движения и удара уходят на сервер одним комом. Держим удары,
 *   пока пачка не разгребётся.</li>
 *   <li><b>Стопор сервера/сети</b> (входящие пакеты пропали, хотя цель двигалась):
 *   пока сервер стоит, его тикер зарядки и i-frames цели не идут, а клиентский
 *   тикер идёт. Удар, отправленный в стопор, сервер обработает с почти нулевой
 *   зарядкой. Во время тишины не бьём, после — ждём бэклог и отдаём «долг»
 *   зарядки длиной в стопор.</li>
 *   <li><b>Сетбек</b> (сервер телепортнул назад): пока клиент не подтвердил телепорт
 *   и античит не синхронизировался, удары отменяются. Пережидаем пинг + запас.</li>
 * </ul>
 */
public final class LagGuard {
    private static final long FREEZE_MS = 150L;
    private static final long SILENCE_MS = 300L;
    private static final long MAX_DEBT_MS = 1000L;
    private static final long BACKLOG_SLACK_MS = 100L;
    private static final long SETBACK_SLACK_MS = 150L;
    private static final double MOVE_EPS_SQR = 1.0E-6;

    /** Пишутся из netty-потока. */
    private static volatile long lastInboundMs;
    private static volatile long setbackAtMs;
    private static final AtomicLong PENDING_STALL_MS = new AtomicLong();

    /** Только главный поток. */
    private static long lastTickNanos;
    private static long handledSetbackMs;
    private static long holdUntilMs;
    private static long debtMs;
    private static long lastAttackMs;
    private static long lastTargetMoveMs;

    private LagGuard() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /** Netty-поток, Connection.channelRead0 HEAD. */
    public static void onPacketReceived(Packet<?> packet) {
        long now = System.currentTimeMillis();
        long prev = lastInboundMs;
        lastInboundMs = now;
        if (prev != 0L && now - prev > SILENCE_MS) {
            long gap = now - prev;
            PENDING_STALL_MS.accumulateAndGet(gap, Math::max);
        }
        if (packet instanceof ClientboundPlayerPositionPacket) {
            setbackAtMs = now;
        }
    }

    /** Каждый тик LocalPlayer (HEAD), независимо от того, включена ли аура. */
    public static void tick(LivingEntity target) {
        long now = System.currentTimeMillis();
        long nanos = System.nanoTime();
        long ping = pingMs();

        if (lastTickNanos != 0L && (nanos - lastTickNanos) / 1_000_000L > FREEZE_MS) {
            hold(now + Math.max(ping, 50L) + BACKLOG_SLACK_MS);
        }
        lastTickNanos = nanos;

        long stall = PENDING_STALL_MS.getAndSet(0L);
        if (stall > 0L) {
            hold(now + ping + BACKLOG_SLACK_MS);
            debtMs = Math.min(MAX_DEBT_MS, debtMs + stall);
        }

        long setback = setbackAtMs;
        if (setback != handledSetbackMs) {
            handledSetbackMs = setback;
            hold(now + ping + SETBACK_SLACK_MS);
        }

        if (target != null && moved(target)) {
            lastTargetMoveMs = now;
        }
    }

    /** Сейчас бить нельзя: фриз/бэклог/сетбек или поток пакетов оборвался посреди движения цели. */
    public static boolean blocking() {
        long now = System.currentTimeMillis();
        if (now < holdUntilMs) {
            return true;
        }
        long inbound = lastInboundMs;
        return inbound != 0L && now - inbound > SILENCE_MS
                && lastTargetMoveMs >= inbound - BACKLOG_SLACK_MS;
    }

    /**
     * Сервер простоял {@code debtMs} после нашего прошлого удара — его тикер зарядки
     * отстаёт от клиентского на столько же. Бьём, только когда реальное время с
     * прошлого удара покрывает полный кулдаун плюс долг.
     */
    public static boolean chargeDebtPending(long fullCooldownMs) {
        if (debtMs <= 0L || lastAttackMs == 0L) {
            return false;
        }
        long since = System.currentTimeMillis() - lastAttackMs;
        if (since >= fullCooldownMs + debtMs) {
            debtMs = 0L;
            return false;
        }
        return true;
    }

    public static void onAttack() {
        lastAttackMs = System.currentTimeMillis();
        debtMs = 0L;
    }

    public static void reset() {
        holdUntilMs = 0L;
        debtMs = 0L;
        lastAttackMs = 0L;
        lastTargetMoveMs = 0L;
    }

    private static void hold(long untilMs) {
        holdUntilMs = Math.max(holdUntilMs, untilMs);
    }

    private static boolean moved(LivingEntity e) {
        double dx = e.getX() - e.xo;
        double dy = e.getY() - e.yo;
        double dz = e.getZ() - e.zo;
        return dx * dx + dy * dy + dz * dz > MOVE_EPS_SQR
                || Math.abs(e.yHeadRot - e.yHeadRotO) > 0.01F;
    }

    public static long pingMs() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return 0L;
        }
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0L : Math.max(0, info.getLatency());
    }
}

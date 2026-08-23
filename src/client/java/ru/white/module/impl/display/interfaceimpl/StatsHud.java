package ru.white.module.impl.display.interfaceimpl;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.scoreboard.Scoreboard;
import ru.white.manager.event_impl.AttackEvent;
import ru.white.manager.event_impl.EventPacket;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.module.api.settings.impl.DragSetting;
import ru.white.module.impl.display.InterFace;
import ru.white.theme.ThemeColor;
import ru.white.utils.colors.ColorFormatting;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.RollingText;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

/**
 * Панель статистики второго вида худа — как на референсе:
 * ник в шапке, монеты с сайдбара, пинг и счётчики убийств/смертей за сессию.
 */
public class StatsHud implements IMinecraft {

    /** Окно, в течение которого смерть атакованной цели считается нашим киллом. */
    private static final long KILL_WINDOW_MS = 5000L;
    private static final int DEATH_STATUS = 3;

    private static float S = 1.0F;

    private static float H = 16F * S;
    private static float MIN_W = 70F * S;
    private static float RADIUS = 5F * S;

    private static float TITLE_TEXT = 7F * S;
    private static float TITLE_ICON = 5F * S;
    private static float ROW_TEXT = 6.5F * S;

    private static float TITLE_ICON_X = 5F * S;
    private static float TITLE_ICON_Y = 5.5F * S;
    private static float TITLE_TEXT_X = 12.5F * S;
    private static float TITLE_TEXT_Y = 3.4F * S;

    private static float ROW_HEIGHT = 12F * S;
    private static float ROW_BASE_W = 27F * S;
    private static float ROW_START_Y = 4F * S;
    private static float HEADER_GAP = 3F * S;
    private static float ROW_PADDING_X = 5F * S;

    // счётчики сессии
    private int kills;
    private int deaths;

    private LivingEntity lastTarget;
    private long lastAttackTime;

    /** защита от повторного счёта одной смерти */
    private boolean deathCounted;

    // кэш строк
    private final RollingText killsText = new RollingText(3F);
    private final RollingText deathsText = new RollingText(3F);
    private final RollingText pingText = new RollingText(3F);

    private String moneyCache = "";
    private long lastMoneyCheckMs;
    private int lastKills = Integer.MIN_VALUE;
    private int lastDeaths = Integer.MIN_VALUE;
    private int lastPing = Integer.MIN_VALUE;

    public void onRender(DragSetting dragSetting, InterFace interFace, EventDisplay eventDisplay) {
        S = InterFace.getInstance().sizeHud.getValue();
        H = 16F * S;
        MIN_W = 70F * S;
        RADIUS = 5F * S;
        TITLE_TEXT = 7F * S;
        TITLE_ICON = 5F * S;
        ROW_TEXT = 6.5F * S;
        TITLE_ICON_X = 5F * S;
        TITLE_ICON_Y = 5.5F * S;
        TITLE_TEXT_X = 12.5F * S;
        TITLE_TEXT_Y = 3.4F * S;
        ROW_HEIGHT = 12F * S;
        ROW_BASE_W = 27F * S;
        ROW_START_Y = 4F * S;
        HEADER_GAP = 3F * S;
        ROW_PADDING_X = 5F * S;

        float x = dragSetting.position.x;
        float y = dragSetting.position.y;

        Font font = Fonts.sf_regular;
        float alpha = 1;

        // ---- данные ----
        if (kills != lastKills) { killsText.set(String.valueOf(kills)); lastKills = kills; }
        if (deaths != lastDeaths) { deathsText.set(String.valueOf(deaths)); lastDeaths = deaths; }

        int ping = 0;
        if (mc.getNetworkHandler() != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) ping = entry.getLatency();
        }
        if (ping != lastPing) { pingText.set(String.valueOf(ping)); lastPing = ping; }

        // деньги читаем не чаще раза в секунду
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastMoneyCheckMs >= 1000L) {
            lastMoneyCheckMs = nowMs;
            long money = readScoreboardMoney();
            if (money >= 0) moneyCache = formatNumber(money);
            else if (!mc.isInSingleplayer() && mc.getNetworkHandler() == null) moneyCache = "";
        }
        boolean hasMoney = !moneyCache.isEmpty();

        String nick = mc.getSession().getUsername();

        // ---- размеры ----
        float w = ROW_BASE_W + font.getWidth(nick, TITLE_TEXT);

        float rowW1 = hasMoney ? labelWidth(font, "Монет: ", moneyCache) : 0;
        float rowW2 = labelWidth(font, "Пинг: ", pingText.get() + " мс");
        float rowW3 = labelWidth(font, "Убийств: ", killsText.get());
        float rowW4 = labelWidth(font, "Смертей: ", deathsText.get());

        float maxRowW = Math.max(Math.max(rowW1, rowW2), Math.max(rowW3, rowW4));
        w = Math.max(w, maxRowW + ROW_PADDING_X * 2F);

        int rowsCount = 2 + (hasMoney ? 1 : 0);
        float h = H + HEADER_GAP + ROW_HEIGHT * rowsCount + ROW_START_Y;

        RenderUtil.Render2D.hudPlate(x, y, w, h, alpha, RADIUS, interFace.alphaHUD.getValue());

        // акцентная рамка слева, как на референсе
        RenderUtil.Render2D.hudAccent(x, y, h, alpha, RADIUS);

        Fonts.rainydlc_2.draw("N", x + TITLE_ICON_X, y + TITLE_ICON_Y, TITLE_ICON,
                ThemeColor.getHudColor());
        Fonts.sf_medium.draw(nick, x + TITLE_TEXT_X, y + TITLE_TEXT_Y, TITLE_TEXT,
                ThemeColor.getTextColor());

        float offsetY = y + H + HEADER_GAP;

        // монеты — зелёным, как на референсе
        if (hasMoney) {
            drawRow(font, "$ Монет: ", moneyCache, x, offsetY, w,
                    ColorUtil.getColor(200, alpha),
                    ColorUtil.getColor(90, 230, 120, alpha));
            offsetY += ROW_HEIGHT;
        }

        drawRow(font, "Пинг: ", pingText.get() + " мс", x, offsetY, w,
                ColorUtil.getColor(200, alpha), ColorUtil.getColor(240, alpha));
        offsetY += ROW_HEIGHT;

        drawRow(font, "Убийств: ", killsText.get(), x, offsetY, w,
                ColorUtil.getColor(200, alpha), ColorUtil.getColor(240, alpha));
        offsetY += ROW_HEIGHT;

        drawRow(font, "Смертей: ", deathsText.get(), x, offsetY, w,
                ColorUtil.getColor(200, alpha), ColorUtil.getColor(240, alpha));

        dragSetting.size.set(w, h);
    }

    private float labelWidth(Font font, String label, String value) {
        return font.getWidth(label, ROW_TEXT) + font.getWidth(value, ROW_TEXT);
    }

    /** Подпись серым слева, значение белым справа. */
    private void drawRow(Font font, String label, String value,
                         float x, float y, float plateW, int labelColor, int valueColor) {
        font.draw(label, x + ROW_PADDING_X, y, ROW_TEXT, labelColor);

        float valueW = font.getWidth(value, ROW_TEXT);
        font.draw(value, x + plateW - ROW_PADDING_X - valueW, y, ROW_TEXT, valueColor);
    }

    // ---- учёт убийств/смертей ----

    public void onAttack(AttackEvent event) {
        if (!(event.getTarget() instanceof LivingEntity living) || living == mc.player) return;

        lastTarget = living;
        lastAttackTime = System.currentTimeMillis();
    }

    public void onPacket(EventPacket event) {
        if (!(event.getPacket() instanceof EntityStatusS2CPacket packet)) return;
        if (packet.getStatus() != DEATH_STATUS || mc.world == null) return;

        Entity entity = packet.getEntity(mc.world);
        if (entity == null) return;

        if (entity == mc.player) {
            if (!deathCounted) {
                deaths++;
                deathCounted = true;
            }
            return;
        }

        if (entity == lastTarget
                && System.currentTimeMillis() - lastAttackTime <= KILL_WINDOW_MS) {
            kills++;
            lastTarget = null;
        }
    }

    /** Сброс флага смерти после возрождения. Вызывается из EventUpdate. */
    public void onUpdate() {
        if (deathCounted && mc.player != null && mc.player.isAlive()) {
            deathCounted = false;
        }
    }

    // ---- чтение баланса с сайдбара (как в AuraCrafter) ----

    private long readScoreboardMoney() {
        if (mc.world == null) return -1;
        Scoreboard scoreboard = mc.world.getScoreboard();
        var objective = scoreboard.getObjectiveForSlot(net.minecraft.scoreboard.ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) return -1;

        long titleMoney = parseMoneyLine(objective.getDisplayName().getString());
        if (titleMoney > 0) return titleMoney;

        for (var entry : scoreboard.getScoreboardEntries(objective)) {
            String line = net.minecraft.scoreboard.Team
                    .decorateName(scoreboard.getScoreHolderTeam(entry.owner()), entry.name()).getString();
            long money = parseMoneyLine(line);
            if (money > 0) return money;
        }
        return -1;
    }

    private long parseMoneyLine(String line) {
        String text = ColorFormatting.removeFormatting(line);
        if (text == null) return -1;
        text = text.toLowerCase(java.util.Locale.ROOT).replace(',', '.');
        if (!text.contains("монет") && !text.contains("баланс")) return -1;

        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return -1;

        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 19653076 -> «19 653 076». */
    private String formatNumber(long value) {
        StringBuilder sb = new StringBuilder();
        String digits = Long.toString(value);
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) sb.append(' ');
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }
}

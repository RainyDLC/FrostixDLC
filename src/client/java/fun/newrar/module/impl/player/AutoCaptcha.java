package fun.newrar.module.impl.player;

import fun.newrar.manager.event_impl.EventPacket;
import fun.newrar.manager.event_impl.EventUpdate;
import fun.newrar.manager.event_impl.WorldLoadEvent;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BooleanSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.utils.notification.NotificationManager;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ModuleInfo(
        name = "Auto Captcha",
        category = Category.PLAYER,
        desc = "Автоматическое прохождение капчи в инвентаре (GUI) и чате на серверах SpookyTime и FunTime"
)
public class AutoCaptcha extends Module {
    public final BooleanSetting guiCaptcha = new BooleanSetting(this, "Капча в меню (GUI)", true);
    public final BooleanSetting chatCaptcha = new BooleanSetting(this, "Капча в чате", true);
    public final SliderSetting delay = new SliderSetting(this, "Задержка (мс)", 300, 50, 1500, 25);

    private static final Pattern MATH_PATTERN = Pattern.compile("(\\d+)\\s*([+\\-*xX×])\\s*(\\d+)");
    private static final Pattern CODE_CMD_PATTERN = Pattern.compile("/(?:captcha|code|check)\\s+([0-9a-zA-Z]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_IN_CHAT_PATTERN = Pattern.compile("(?:код|число|цифры|символы|code|answer)[^0-9a-zA-Z]*([0-9a-zA-Z]{3,8})", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENTER_CODE_PATTERN = Pattern.compile("(?:введи(?:те)?|type|enter)\\s+([0-9a-zA-Z]{3,8})", Pattern.CASE_INSENSITIVE);

    private String pendingChatResponse = null;
    private long executeChatTime = 0L;
    private long nextGuiClickTime = 0L;
    private long cooldownUntil = 0L;

    public AutoCaptcha() {
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        pendingChatResponse = null;
        executeChatTime = 0L;
        nextGuiClickTime = 0L;
        cooldownUntil = 0L;
    }

    @EventHandler
    public void onPacket(EventPacket packetEvent) {
        if (!chatCaptcha.getValue()) return;
        if (mc.player == null || mc.world == null) return;
        if (System.currentTimeMillis() < cooldownUntil) return;
        if (pendingChatResponse != null) return;

        var packet = packetEvent.getPacket();
        Text text = null;

        if (packet instanceof GameMessageS2CPacket m) {
            text = m.content();
        } else if (packet instanceof OverlayMessageS2CPacket m) {
            text = m.text();
        } else if (packet instanceof TitleS2CPacket m) {
            text = m.text();
        } else if (packet instanceof SubtitleS2CPacket m) {
            text = m.text();
        }

        if (text == null) return;

        String raw = text.getString();
        if (raw == null || raw.trim().isEmpty()) return;

        String clean = raw.replaceAll("§.", "").toLowerCase();

        if (clean.contains("успешно") || clean.contains("success") || clean.contains("пройдена") || clean.contains("добро пожаловать")) {
            return;
        }

        boolean isCaptchaPrompt = clean.contains("капч")
                || clean.contains("captcha")
                || clean.contains("проверк")
                || clean.contains("решите")
                || clean.contains("пример")
                || clean.contains("сколько")
                || clean.contains("ответ")
                || clean.contains("код");

        if (!isCaptchaPrompt) return;

        Matcher mathMatcher = MATH_PATTERN.matcher(clean);
        if (mathMatcher.find()) {
            try {
                int a = Integer.parseInt(mathMatcher.group(1));
                String op = mathMatcher.group(2);
                int b = Integer.parseInt(mathMatcher.group(3));
                int result = switch (op) {
                    case "+" -> a + b;
                    case "-" -> a - b;
                    case "*", "x", "X", "×" -> a * b;
                    default -> a + b;
                };
                queueChatResponse(String.valueOf(result));
                return;
            } catch (Exception ignored) {
            }
        }

        Matcher cmdMatcher = CODE_CMD_PATTERN.matcher(clean);
        if (cmdMatcher.find()) {
            String code = cmdMatcher.group(1);
            queueChatResponse("/captcha " + code);
            return;
        }

        Matcher codeMatcher = CODE_IN_CHAT_PATTERN.matcher(clean);
        if (codeMatcher.find()) {
            String code = codeMatcher.group(1);
            queueChatResponse(code);
            return;
        }

        Matcher enterMatcher = ENTER_CODE_PATTERN.matcher(clean);
        if (enterMatcher.find()) {
            String code = enterMatcher.group(1);
            queueChatResponse(code);
        }
    }

    private void queueChatResponse(String response) {
        pendingChatResponse = response;
        long delayMs = delay.getValue() != null ? delay.getValue().longValue() : 300L;
        executeChatTime = System.currentTimeMillis() + delayMs;
        cooldownUntil = executeChatTime + 3000L;
    }

    @EventHandler
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        if (pendingChatResponse != null && System.currentTimeMillis() >= executeChatTime) {
            String resp = pendingChatResponse;
            pendingChatResponse = null;

            if (mc.player.networkHandler != null) {
                if (resp.startsWith("/")) {
                    mc.player.networkHandler.sendChatCommand(resp.substring(1));
                } else {
                    mc.player.networkHandler.sendChatMessage(resp);
                }
                NotificationManager.send("AutoCaptcha: отправлен ответ '" + resp + "'", NotificationManager.Type.INFO, "Captcha");
            }
        }

        if (guiCaptcha.getValue() && mc.currentScreen instanceof GenericContainerScreen screen) {
            if (System.currentTimeMillis() < nextGuiClickTime) return;

            String title = screen.getTitle().getString();
            if (title == null) return;
            String cleanTitle = title.replaceAll("§.", "").toLowerCase();

            boolean isCaptchaGui = cleanTitle.contains("капч")
                    || cleanTitle.contains("captcha")
                    || cleanTitle.contains("проверк")
                    || cleanTitle.contains("бот")
                    || cleanTitle.contains("нажми")
                    || cleanTitle.contains("выбер")
                    || cleanTitle.contains("кликн");

            if (!isCaptchaGui) return;

            int rows = screen.getScreenHandler().getRows();
            int containerSlots = rows * 9;

            int targetSlot = findCaptchaSlot(screen, containerSlots, cleanTitle);
            if (targetSlot != -1) {
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
                long delayMs = delay.getValue() != null ? delay.getValue().longValue() : 300L;
                nextGuiClickTime = System.currentTimeMillis() + delayMs + 800L;
                NotificationManager.send("AutoCaptcha: нажат слот " + targetSlot, NotificationManager.Type.INFO, "Captcha");
            }
        }
    }

    private int findCaptchaSlot(GenericContainerScreen screen, int containerSlots, String cleanTitle) {
        boolean wantGreen = cleanTitle.contains("зелен") || cleanTitle.contains("зелён") || cleanTitle.contains("green") || cleanTitle.contains("lime");
        boolean wantDiamond = cleanTitle.contains("алмаз") || cleanTitle.contains("diamond");
        boolean wantEmerald = cleanTitle.contains("изумруд") || cleanTitle.contains("emerald");
        boolean wantGold = cleanTitle.contains("золот") || cleanTitle.contains("gold");
        boolean wantIron = cleanTitle.contains("желез") || cleanTitle.contains("iron");
        boolean wantSword = cleanTitle.contains("меч") || cleanTitle.contains("sword");
        boolean wantApple = cleanTitle.contains("яблок") || cleanTitle.contains("apple");

        for (int i = 0; i < containerSlots; i++) {
            Slot slot = screen.getScreenHandler().getSlot(i);
            if (!slot.hasStack()) continue;
            ItemStack stack = slot.getStack();
            Item item = stack.getItem();
            String itemName = stack.getName().getString().toLowerCase();

            if (wantGreen && (isGreenItem(item) || itemName.contains("зелен") || itemName.contains("зелён") || itemName.contains("лайм"))) {
                return i;
            }
            if (wantEmerald && (item == Items.EMERALD || item == Items.EMERALD_BLOCK || itemName.contains("изумруд"))) {
                return i;
            }
            if (wantDiamond && (item == Items.DIAMOND || item == Items.DIAMOND_BLOCK || item == Items.DIAMOND_SWORD || itemName.contains("алмаз"))) {
                return i;
            }
            if (wantGold && (item == Items.GOLD_INGOT || item == Items.GOLD_BLOCK || itemName.contains("золот"))) {
                return i;
            }
            if (wantIron && (item == Items.IRON_INGOT || item == Items.IRON_BLOCK || itemName.contains("желез"))) {
                return i;
            }
            if (wantSword && (item.toString().contains("sword") || itemName.contains("меч"))) {
                return i;
            }
            if (wantApple && (item == Items.APPLE || item == Items.GOLDEN_APPLE || itemName.contains("яблок"))) {
                return i;
            }
        }

        for (int i = 0; i < containerSlots; i++) {
            Slot slot = screen.getScreenHandler().getSlot(i);
            if (!slot.hasStack()) continue;
            ItemStack stack = slot.getStack();
            Item item = stack.getItem();
            String itemName = stack.getName().getString().toLowerCase();

            if (itemName.contains("нажми") || itemName.contains("сюда") || itemName.contains("клик")
                    || itemName.contains("подтверд") || itemName.contains("правильн") || itemName.contains("верн")) {
                return i;
            }
            if (isGreenItem(item)) {
                return i;
            }
        }

        int nonFillerSlot = -1;
        int nonFillerCount = 0;
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = screen.getScreenHandler().getSlot(i);
            if (!slot.hasStack()) continue;
            Item item = slot.getStack().getItem();
            if (!isFillerItem(item)) {
                nonFillerSlot = i;
                nonFillerCount++;
            }
        }
        if (nonFillerCount == 1) {
            return nonFillerSlot;
        }

        return -1;
    }

    private boolean isGreenItem(Item item) {
        return item == Items.EMERALD
                || item == Items.EMERALD_BLOCK
                || item == Items.LIME_DYE
                || item == Items.LIME_CONCRETE
                || item == Items.LIME_WOOL
                || item == Items.LIME_TERRACOTTA
                || item == Items.LIME_STAINED_GLASS
                || item == Items.LIME_STAINED_GLASS_PANE
                || item == Items.GREEN_DYE
                || item == Items.GREEN_CONCRETE
                || item == Items.GREEN_WOOL
                || item == Items.GREEN_TERRACOTTA
                || item == Items.GREEN_STAINED_GLASS
                || item == Items.GREEN_STAINED_GLASS_PANE;
    }

    private boolean isFillerItem(Item item) {
        return item == Items.BARRIER
                || item == Items.STRUCTURE_VOID
                || item == Items.GRAY_STAINED_GLASS_PANE
                || item == Items.BLACK_STAINED_GLASS_PANE
                || item == Items.RED_STAINED_GLASS_PANE
                || item == Items.WHITE_STAINED_GLASS_PANE
                || item == Items.GRAY_STAINED_GLASS
                || item == Items.BLACK_STAINED_GLASS
                || item == Items.RED_STAINED_GLASS
                || item == Items.AIR;
    }
}

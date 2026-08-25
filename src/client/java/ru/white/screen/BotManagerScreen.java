package ru.white.screen;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.white.bot.PotatoGraphics;
import ru.white.bot.model.HeadlessBot;
import ru.white.module.impl.utils.BotManager;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.MathUtil;
import ru.white.utils.render.Draw;
import ru.white.utils.render.Render2D;
import ru.white.utils.render.Scissor;
import ru.white.utils.render.ScreenBlur;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.util.ArrayList;
import java.util.List;

public class BotManagerScreen extends Screen implements IMinecraft {

    private final Screen parent;
    private final Animation alphaAnim = new Animation();
    private float scaleFix = 1.0F;
    private float mouseX, mouseY;

    // Text inputs
    private String nameInput = "Bot_1";
    private String hostInput = "localhost";
    private String portInput = "25565";
    private String chatInput = "";

    private int focusedField = 0; // 0 = none, 1 = name, 2 = host, 3 = port, 4 = chat

    // Scroll
    private float scroll = 0, scrollTarget = 0;

    // Layout coordinates
    private float panelX, panelY, panelW, panelH;
    private float listX, listY, listW, listH;

    // Clickable hitboxes
    private final List<float[]> botCardRects = new ArrayList<>();
    private final List<float[]> botDeleteRects = new ArrayList<>();
    private final List<float[]> botTpRects = new ArrayList<>();
    private final List<HeadlessBot> renderedBots = new ArrayList<>();

    public BotManagerScreen(Screen parent) {
        super(Text.literal("Bot Manager"));
        this.parent = parent;

        BotManager manager = BotManager.get();
        if (manager != null) {
            this.hostInput = manager.getCurrentServerHost();
            this.portInput = String.valueOf(manager.getCurrentServerPort());
        }
    }

    @Override
    protected void init() {
        alphaAnim.set(0);
        alphaAnim.run(1, 0.35F, Easings.QUAD_OUT);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.mouseX = mouseX / scaleFix;
        this.mouseY = mouseY / scaleFix;
        alphaAnim.update();

        float targetScale = 2.0F;
        float currentScale = (float) mc.getWindow().getScaleFactor();
        scaleFix = targetScale / currentScale;

        int screenWidth = (int) (mc.getWindow().getScaledWidth() / scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);

        if (context != null) context.getMatrices().pushMatrix();
        Render2D.beginOverlay();

        float a = alphaAnim.get();

        // 1. Background blur & overlay
        ScreenBlur.capture(3);
        Draw.blur(0, 0, screenWidth, screenHeight, a, ColorUtil.getColor(10, 10, 15, (int) (a * 150)));

        // 2. Main Window Panel
        panelW = 540;
        panelH = 340;
        panelX = (screenWidth - panelW) / 2.0F;
        panelY = (screenHeight - panelH) / 2.0F;

        // Panel background
        Draw.blur(panelX, panelY, panelW, panelH, a, 10, ColorUtil.getColor(18, 20, 28, (int) (a * 230)));
        Draw.rect(panelX, panelY, panelW, panelH, ColorUtil.getColor(25, 28, 38, (int) (a * 210)), 10);
        Draw.outline(panelX, panelY, panelW, panelH, 1.2F, ColorUtil.getColor(255, 255, 255, (int) (a * 25)), 10);

        Font regular = Fonts.sf_regular;
        Font bold = Fonts.sf_bold;
        Font medium = Fonts.sf_medium;

        // Header
        bold.draw("BOT CONTROLLER", panelX + 16, panelY + 14, 9.5F, ColorUtil.getColor(255, 255, 255, (int) (a * 240)));

        BotManager manager = BotManager.get();
        int onlineCount = 0;
        if (manager != null) {
            for (HeadlessBot b : manager.getBots()) {
                if (b.isConnected()) onlineCount++;
            }
        }
        String statusSummary = "Онлайн: " + onlineCount + " / " + (manager != null ? manager.getBots().size() : 0);
        regular.draw(statusSummary, panelX + 145, panelY + 16, 7.0F, ColorUtil.getColor(120, 220, 140, (int) (a * 220)));

        // Close hint
        regular.draw("ESC чтобы закрыть (боты работают в фоне)", panelX + panelW - 200, panelY + 16, 6.5F, ColorUtil.getColor(140, 150, 170, (int) (a * 180)));

        // ── Input Bar (Nick, Host, Port, Add buttons) ──
        float inputBarY = panelY + 36;

        // Nick field
        drawInputField("Никнейм", nameInput, panelX + 16, inputBarY, 110, 22, focusedField == 1, a);
        // Host field
        drawInputField("Хост / IP", hostInput, panelX + 132, inputBarY, 120, 22, focusedField == 2, a);
        // Port field
        drawInputField("Порт", portInput, panelX + 258, inputBarY, 50, 22, focusedField == 3, a);

        // Action buttons: + Добавить, 🎲 Рандом, +3, +5
        drawButton("+ Добавить", panelX + 314, inputBarY, 65, 22, 0xFF44AAFF, a);
        drawButton("🎲 Рандом", panelX + 384, inputBarY, 58, 22, 0xFF7766FF, a);
        drawButton("+3", panelX + 447, inputBarY, 35, 22, 0xFF55CC88, a);
        drawButton("+5", panelX + 487, inputBarY, 35, 22, 0xFF55CC88, a);

        // ── Quick Command Toolbar ──
        float toolBarY = inputBarY + 28;
        boolean follow = manager != null && manager.globalFollow.getValue();
        boolean assist = manager != null && manager.globalAttack.getValue();
        boolean autoReg = manager != null && manager.autoRegister.getValue();
        boolean potato = PotatoGraphics.isEnabled();

        drawToggleButton("🚶 Следовать", panelX + 16, toolBarY, 82, 20, follow, a);
        drawToggleButton("⚔ Ассист", panelX + 103, toolBarY, 70, 20, assist, a);
        drawButton("⬆ Прыгнуть", panelX + 178, toolBarY, 68, 20, 0xFF5599DD, a);
        drawToggleButton("🔑 Авто-рег", panelX + 251, toolBarY, 75, 20, autoReg, a);
        drawToggleButton("🥔 Потато", panelX + 331, toolBarY, 68, 20, potato, a);
        drawButton("❌ Отключить всех", panelX + 404, toolBarY, 118, 20, 0xFFFF4455, a);

        // ── Chat / Command Bar ──
        float chatBarY = toolBarY + 26;
        drawInputField("Сообщение или /команда для всех ботов...", chatInput, panelX + 16, chatBarY, 410, 22, focusedField == 4, a);
        drawButton("Отправить", panelX + 432, chatBarY, 90, 22, 0xFF44AAEE, a);

        // ── Scrollable Bot List ──
        listX = panelX + 16;
        listY = chatBarY + 30;
        listW = panelW - 32;
        listH = panelH - (listY - panelY) - 14;

        Draw.rect(listX, listY, listW, listH, ColorUtil.getColor(14, 16, 22, (int) (a * 180)), 6);
        Draw.outline(listX, listY, listW, listH, 1.0F, ColorUtil.getColor(255, 255, 255, (int) (a * 15)), 6);

        // Smooth scroll
        scroll = MathUtil.lerp(scroll, scrollTarget, 0.25F);

        botCardRects.clear();
        botDeleteRects.clear();
        botTpRects.clear();
        renderedBots.clear();

        List<HeadlessBot> bots = manager != null ? manager.getBots() : new ArrayList<>();

        if (bots.isEmpty()) {
            medium.draw("Нет подключенных ботов. Введите ник и нажмите «+ Добавить».", listX + listW / 2.0F - 130, listY + listH / 2.0F - 4, 7.5F, ColorUtil.getColor(120, 130, 150, (int) (a * 160)));
        } else {
            Scissor.enable(listX, listY, listW, listH, (float) mc.getWindow().getScaleFactor() / scaleFix);

            float cardH = 34;
            float gap = 4;
            float totalHeight = bots.size() * (cardH + gap);
            float maxScroll = Math.max(0, totalHeight - listH + 8);
            scrollTarget = MathUtil.clamp(scrollTarget, 0, maxScroll);
            scroll = MathUtil.clamp(scroll, 0, maxScroll);

            float curY = listY + 6 - scroll;

            for (HeadlessBot bot : bots) {
                if (curY + cardH >= listY && curY <= listY + listH) {
                    float cardX = listX + 6;
                    float cardW = listW - 12;

                    boolean hovered = MathUtil.isHovered(this.mouseX, this.mouseY, cardX, curY, cardW, cardH);
                    int cardBg = hovered
                            ? ColorUtil.getColor(28, 32, 44, (int) (a * 220))
                            : ColorUtil.getColor(22, 25, 34, (int) (a * 200));

                    Draw.rect(cardX, curY, cardW, cardH, cardBg, 5);
                    Draw.outline(cardX, curY, cardW, cardH, 1.0F, hovered ? ColorUtil.getColor(100, 160, 255, (int) (a * 80)) : ColorUtil.getColor(255, 255, 255, (int) (a * 15)), 5);

                    // Bot state dot & name
                    int dotColor = bot.getState().getColor();
                    Draw.rect(cardX + 8, curY + 12, 8, 8, ColorUtil.replAlpha(dotColor, a * 0.9F), 4);

                    bold.draw(bot.getName(), cardX + 22, curY + 8, 8.0F, ColorUtil.getColor(240, 245, 255, (int) (a * 240)));

                    // State display + status message
                    String stateText = bot.getState().getDisplay() + (!bot.getStatusMessage().isEmpty() ? " (" + bot.getStatusMessage() + ")" : "");
                    regular.draw(stateText, cardX + 22, curY + 20, 6.0F, ColorUtil.getColor(150, 165, 185, (int) (a * 200)));

                    // Health bar & stats
                    float barX = cardX + 180;
                    float barY = curY + 12;
                    float barW = 70;
                    float barH = 8;
                    float hpFrac = MathUtil.clamp(bot.getHealth() / 20.0F, 0F, 1F);

                    Draw.rect(barX, barY, barW, barH, ColorUtil.getColor(40, 45, 55, (int) (a * 180)), 3);
                    Draw.rect(barX, barY, barW * hpFrac, barH, ColorUtil.getColor(240, 70, 80, (int) (a * 220)), 3);
                    regular.draw("HP: " + (int) bot.getHealth(), barX + barW + 6, curY + 13, 6.0F, ColorUtil.getColor(200, 210, 225, (int) (a * 200)));

                    // Distance from player
                    if (mc.player != null && bot.isConnected()) {
                        double dist = Math.sqrt(
                                Math.pow(bot.getX() - mc.player.getX(), 2) +
                                Math.pow(bot.getY() - mc.player.getY(), 2) +
                                Math.pow(bot.getZ() - mc.player.getZ(), 2)
                        );
                        regular.draw(String.format("Дист: %.1fм", dist), cardX + 300, curY + 13, 6.5F, ColorUtil.getColor(160, 175, 200, (int) (a * 190)));
                    }

                    // Per-bot buttons: ТП, Кик
                    float tpBtnX = cardX + cardW - 100;
                    float delBtnX = cardX + cardW - 46;
                    float btnH = 18;
                    float btnY = curY + 8;

                    drawMiniButton("ТП ко мне", tpBtnX, btnY, 50, btnH, 0xFF44AAFF, a);
                    drawMiniButton("Кик", delBtnX, btnY, 40, btnH, 0xFFFF4455, a);

                    botCardRects.add(new float[]{cardX, curY, cardW, cardH});
                    botTpRects.add(new float[]{tpBtnX, btnY, 50, btnH});
                    botDeleteRects.add(new float[]{delBtnX, btnY, 40, btnH});
                    renderedBots.add(bot);
                }
                curY += cardH + gap;
            }

            Scissor.disable();
        }

        Draw.flush();
        Render2D.endOverlay();
        if (context != null) context.getMatrices().popMatrix();
    }

    private void drawInputField(String placeholder, String text, float x, float y, float w, float h, boolean focused, float alpha) {
        int bg = focused
                ? ColorUtil.getColor(32, 36, 50, (int) (alpha * 240))
                : ColorUtil.getColor(20, 23, 32, (int) (alpha * 200));

        Draw.rect(x, y, w, h, bg, 4);
        Draw.outline(x, y, w, h, 1.0F, focused ? ColorUtil.getColor(80, 160, 255, (int) (alpha * 180)) : ColorUtil.getColor(255, 255, 255, (int) (alpha * 20)), 4);

        Font font = Fonts.sf_regular;
        boolean blink = focused && (System.currentTimeMillis() / 450L) % 2L == 0L;
        String toDraw = text.isEmpty() && !focused ? placeholder : text + (blink ? "_" : "");
        int txtCol = text.isEmpty() && !focused
                ? ColorUtil.getColor(120, 130, 150, (int) (alpha * 150))
                : ColorUtil.getColor(240, 245, 255, (int) (alpha * 240));

        font.draw(toDraw, x + 6, y + 7, 6.8F, txtCol);
    }

    private void drawButton(String label, float x, float y, float w, float h, int color, float alpha) {
        boolean hovered = MathUtil.isHovered(this.mouseX, this.mouseY, x, y, w, h);
        int bg = hovered
                ? ColorUtil.replAlpha(color, alpha * 0.9F)
                : ColorUtil.replAlpha(color, alpha * 0.65F);

        Draw.rect(x, y, w, h, bg, 4);
        Draw.outline(x, y, w, h, 1.0F, ColorUtil.getColor(255, 255, 255, (int) (alpha * 40)), 4);

        Font font = Fonts.sf_medium;
        float textW = font.getWidth(label, 7.0F);
        font.draw(label, x + (w - textW) / 2.0F, y + 7, 7.0F, ColorUtil.getColor(255, 255, 255, (int) (alpha * 255)));
    }

    private void drawToggleButton(String label, float x, float y, float w, float h, boolean active, float alpha) {
        boolean hovered = MathUtil.isHovered(this.mouseX, this.mouseY, x, y, w, h);
        int baseCol = active ? 0xFF33BB66 : 0xFF353A4C;
        int bg = hovered
                ? ColorUtil.replAlpha(baseCol, alpha * 0.95F)
                : ColorUtil.replAlpha(baseCol, alpha * 0.75F);

        Draw.rect(x, y, w, h, bg, 4);
        Draw.outline(x, y, w, h, 1.0F, active ? ColorUtil.getColor(100, 255, 150, (int) (alpha * 120)) : ColorUtil.getColor(255, 255, 255, (int) (alpha * 25)), 4);

        Font font = Fonts.sf_medium;
        float textW = font.getWidth(label, 6.8F);
        font.draw(label, x + (w - textW) / 2.0F, y + 6, 6.8F, active ? ColorUtil.getColor(255, 255, 255, (int) (alpha * 255)) : ColorUtil.getColor(180, 190, 210, (int) (alpha * 220)));
    }

    private void drawMiniButton(String label, float x, float y, float w, float h, int color, float alpha) {
        boolean hovered = MathUtil.isHovered(this.mouseX, this.mouseY, x, y, w, h);
        int bg = hovered
                ? ColorUtil.replAlpha(color, alpha * 0.85F)
                : ColorUtil.replAlpha(color, alpha * 0.5F);

        Draw.rect(x, y, w, h, bg, 3);
        Draw.outline(x, y, w, h, 0.8F, ColorUtil.getColor(255, 255, 255, (int) (alpha * 30)), 3);

        Font font = Fonts.sf_regular;
        float textW = font.getWidth(label, 6.0F);
        font.draw(label, x + (w - textW) / 2.0F, y + 5.5F, 6.0F, ColorUtil.getColor(255, 255, 255, (int) (alpha * 240)));
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        float mx = (float) (click.x() / scaleFix);
        float my = (float) (click.y() / scaleFix);

        if (click.button() != 0) return super.mouseClicked(click, doubled);

        if (!MathUtil.isHovered(mx, my, panelX, panelY, panelW, panelH)) {
            mc.setScreen(parent);
            return true;
        }

        float inputBarY = panelY + 36;
        float toolBarY = inputBarY + 28;
        float chatBarY = toolBarY + 26;

        BotManager manager = BotManager.get();

        // 1. Text input focus
        if (MathUtil.isHovered(mx, my, panelX + 16, inputBarY, 110, 22)) {
            focusedField = 1; return true;
        } else if (MathUtil.isHovered(mx, my, panelX + 132, inputBarY, 120, 22)) {
            focusedField = 2; return true;
        } else if (MathUtil.isHovered(mx, my, panelX + 258, inputBarY, 50, 22)) {
            focusedField = 3; return true;
        } else if (MathUtil.isHovered(mx, my, panelX + 16, chatBarY, 410, 22)) {
            focusedField = 4; return true;
        } else {
            focusedField = 0;
        }

        // 2. Action buttons
        // + Добавить
        if (MathUtil.isHovered(mx, my, panelX + 314, inputBarY, 65, 22)) {
            tryAddBot();
            return true;
        }
        // 🎲 Рандом
        if (MathUtil.isHovered(mx, my, panelX + 384, inputBarY, 58, 22)) {
            nameInput = "Bot_" + (int) MathUtil.random(100, 999);
            tryAddBot();
            return true;
        }
        // +3
        if (MathUtil.isHovered(mx, my, panelX + 447, inputBarY, 35, 22)) {
            if (manager != null) {
                int port = parsePort();
                manager.createRandomBots(3, "Bot", hostInput, port);
            }
            return true;
        }
        // +5
        if (MathUtil.isHovered(mx, my, panelX + 487, inputBarY, 35, 22)) {
            if (manager != null) {
                int port = parsePort();
                manager.createRandomBots(5, "Bot", hostInput, port);
            }
            return true;
        }

        // 3. Quick Toolbar
        // 🚶 Следовать
        if (MathUtil.isHovered(mx, my, panelX + 16, toolBarY, 82, 20)) {
            if (manager != null) {
                manager.followAll(!manager.globalFollow.getValue());
            }
            return true;
        }
        // ⚔ Ассист
        if (MathUtil.isHovered(mx, my, panelX + 103, toolBarY, 70, 20)) {
            if (manager != null) {
                manager.attackAll(!manager.globalAttack.getValue());
            }
            return true;
        }
        // ⬆ Прыгнуть
        if (MathUtil.isHovered(mx, my, panelX + 178, toolBarY, 68, 20)) {
            if (manager != null) manager.jumpAll();
            return true;
        }
        // 🔑 Авто-рег
        if (MathUtil.isHovered(mx, my, panelX + 251, toolBarY, 75, 20)) {
            if (manager != null) {
                manager.autoRegister.set(!manager.autoRegister.getValue());
            }
            return true;
        }
        // 🥔 Потато
        if (MathUtil.isHovered(mx, my, panelX + 331, toolBarY, 68, 20)) {
            PotatoGraphics.toggle();
            if (manager != null) manager.potatoGraphics.set(PotatoGraphics.isEnabled());
            return true;
        }
        // ❌ Отключить всех
        if (MathUtil.isHovered(mx, my, panelX + 404, toolBarY, 118, 20)) {
            if (manager != null) manager.disconnectAll();
            return true;
        }

        // 4. Chat Send button
        if (MathUtil.isHovered(mx, my, panelX + 432, chatBarY, 90, 22)) {
            sendChatToAll();
            return true;
        }

        // 5. Per-bot buttons
        for (int i = 0; i < botTpRects.size(); i++) {
            float[] r = botTpRects.get(i);
            if (MathUtil.isHovered(mx, my, r[0], r[1], r[2], r[3])) {
                HeadlessBot b = renderedBots.get(i);
                if (mc.player != null && b.isConnected()) {
                    b.moveTo(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), true);
                }
                return true;
            }
        }

        for (int i = 0; i < botDeleteRects.size(); i++) {
            float[] r = botDeleteRects.get(i);
            if (MathUtil.isHovered(mx, my, r[0], r[1], r[2], r[3])) {
                HeadlessBot b = renderedBots.get(i);
                if (manager != null) manager.removeBot(b);
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    private void tryAddBot() {
        if (nameInput.trim().isEmpty()) return;
        BotManager manager = BotManager.get();
        if (manager != null) {
            int port = parsePort();
            manager.createBot(nameInput.trim(), hostInput.trim(), port);
            nameInput = "Bot_" + (int) MathUtil.random(100, 999);
        }
    }

    private void sendChatToAll() {
        if (chatInput.trim().isEmpty()) return;
        BotManager manager = BotManager.get();
        if (manager != null) {
            manager.chatAll(chatInput.trim());
            chatInput = "";
        }
    }

    private int parsePort() {
        try {
            return Integer.parseInt(portInput.trim());
        } catch (Exception e) {
            return 25565;
        }
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int key = keyInput.key();

        if (focusedField != 0) {
            if (key == GLFW.GLFW_KEY_ENTER) {
                if (focusedField == 1) tryAddBot();
                else if (focusedField == 4) sendChatToAll();
                else focusedField = 0;
                return true;
            } else if (key == GLFW.GLFW_KEY_ESCAPE) {
                focusedField = 0;
                return true;
            } else if (key == GLFW.GLFW_KEY_BACKSPACE) {
                handleBackspace();
                return true;
            }
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            mc.setScreen(parent);
            return true;
        }

        return super.keyPressed(keyInput);
    }

    private void handleBackspace() {
        switch (focusedField) {
            case 1 -> {
                if (!nameInput.isEmpty()) nameInput = nameInput.substring(0, nameInput.length() - 1);
            }
            case 2 -> {
                if (!hostInput.isEmpty()) hostInput = hostInput.substring(0, hostInput.length() - 1);
            }
            case 3 -> {
                if (!portInput.isEmpty()) portInput = portInput.substring(0, portInput.length() - 1);
            }
            case 4 -> {
                if (!chatInput.isEmpty()) chatInput = chatInput.substring(0, chatInput.length() - 1);
            }
        }
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (focusedField != 0 && input.isValidChar()) {
            String s = input.asString();
            switch (focusedField) {
                case 1 -> {
                    if (nameInput.length() < 16) nameInput += s;
                }
                case 2 -> {
                    if (hostInput.length() < 32) hostInput += s;
                }
                case 3 -> {
                    char c = s.charAt(0);
                    if (Character.isDigit(c) && portInput.length() < 5) portInput += s;
                }
                case 4 -> {
                    if (chatInput.length() < 120) chatInput += s;
                }
            }
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scrollTarget -= (float) vertical * 26F;
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}

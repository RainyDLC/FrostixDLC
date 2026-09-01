package ru.white.module.impl.display.interfaceimpl;

import org.joml.Matrix3x2fStack;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.entity.effect.StatusEffectInstance;
import ru.white.Client;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.module.api.settings.impl.DragSetting;
import ru.white.module.impl.combat.AttackAura;
import ru.white.module.impl.display.InterFace;
import ru.white.module.impl.utils.NameProtect;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.animation.satoshi.Direction;
import ru.white.utils.animation.satoshi.EaseInOutQuad;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.ItemRender;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.other.UseCooldowns;
import ru.white.utils.render.font.Fonts;
import ru.white.utils.taskript.StopWatch;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.MutableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static net.minecraft.client.gui.hud.InGameHud.getEffectTexture;

public class TargetHud implements element {
    private static float S = 1.0F;

    private static float ITEM_SIZE = 8F * S;
    private static float ARMOR_PAD = 2.5F * S;
    private static float ARMOR_X = 10F * S;
    private static float ARMOR_Y = 12F * S;

    private static float W_BASE = 148F * S;
    private static float H_BASE = 34F * S;
    private static float RADIUS = 6F * S;
    private static float ANIM_OFFSET = 8F * S;

    private static float HEAD_CONTAINER_W = 30F * S;
    private static float HEAD_OFFSET_X = 4F * S;
    private static float HEAD_OFFSET_Y = 4F * S;
    private static float CONTENT_OFFSET_X = 35F * S;

    private static float BAR_OFFSET_X = 5F * S;
    private static float BAR_OFFSET_Y = 7F * S;
    private static float BAR_MARGIN_R = 10F * S;
    private static float BAR_HEIGHT = 3F * S;

    private static float NAME_Y = 4.5F * S;
    private static float HP_Y = 13.5F * S;
    private static float NAME_SIZE = 7F * S;
    private static float HP_SIZE = 6F * S;

    private static float GLOW_RADIUS = 12F * S;
    private static float BLUR_RADIUS = 20F * S;
    private static float FACE_SIZE = 22F * S;

    // Правая панель статистики: ХП цели + сколько раз съела зачарованное яблоко
    private static float STATS_W = 34F * S;
    private static float STATS_ICON = 8.5F * S;
    private static float STATS_GAP = 2.5F * S;
    private static float STATS_TEXT = 6.5F * S;

    private static final Identifier HEART_ICON = Identifier.ofVanilla("hud/heart/full");

    private static ItemStack notchIcon;

    private static ItemStack notchIcon() {
        if (notchIcon == null) notchIcon = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
        return notchIcon;
    }

    private final ru.white.utils.animation.satoshi.Animation openAnimation = new EaseInOutQuad(200, 1);
    private final Animation settingsAnimation = new Animation();
    private final Animation animHP = new Animation();
    private final Animation animHpText = new Animation();
    private final StopWatch time = new StopWatch();
    private LivingEntity target;
    private boolean inWorld;
    private boolean settingsOpen;
    private float lastX, lastY, lastW, lastH;
    private float popupX, popupY, popupW, popupH;
    private float hpColorX, hpColorY, hpColorW, hpColorH;
    private float view1X, view1Y, view1W, view1H;
    private float view2X, view2Y, view2W, view2H;

    @Override
    public void onRender(DragSetting drag, InterFace interFace) {
    }

    public void onRender(DragSetting drag, InterFace interFace, EventDisplay eventDisplay) {
        S = InterFace.getInstance().sizeHud.getValue();
        ITEM_SIZE = 8F * S;
        ARMOR_PAD = 2.5F * S;
        ARMOR_X = 10F * S;
        ARMOR_Y = 12F * S;
        W_BASE = 148F * S;
        H_BASE = 34F * S;
        RADIUS = 6F * S;
        ANIM_OFFSET = 8F * S;
        HEAD_CONTAINER_W = 30F * S;
        HEAD_OFFSET_X = 4F * S;
        HEAD_OFFSET_Y = 4F * S;
        CONTENT_OFFSET_X = 35F * S;
        BAR_OFFSET_X = 5F * S;
        BAR_OFFSET_Y = 7F * S;
        BAR_MARGIN_R = 10F * S;
        BAR_HEIGHT = 3F * S;
        NAME_Y = 4.5F * S;
        HP_Y = 13.5F * S;
        NAME_SIZE = 7F * S;
        HP_SIZE = 6F * S;
        GLOW_RADIUS = 12F * S;
        BLUR_RADIUS = 20F * S;
        FACE_SIZE = 22F * S;
        STATS_W = 34F * S;
        STATS_ICON = 8.5F * S;
        STATS_GAP = 2.5F * S;
        STATS_TEXT = 6.5F * S;

        if (mc.player == null || mc.world == null) return;

        LivingEntity auraTarget = AttackAura.target;
        if (Client.get().moduleManager().get(AttackAura.class).isEnabled() && auraTarget != null) {
            target = auraTarget;
            time.reset();
        }

        if(auraTarget == null && mc.targetedEntity != null) {
            target = mc.targetedEntity.getEntity();
            time.reset();
        }

        if (mc.currentScreen instanceof ChatScreen) {
            target = mc.player;
            time.reset();
        }

        inWorld = target != null && mc.world != null && !target.isRemoved();
        boolean out = !inWorld || time.finished(400);
        openAnimation.setDirection(out ? Direction.BACKWARDS : Direction.FORWARDS);

        if (openAnimation.getOutput() <= 0.0 || target == null) {
            drag.active = false;
            return;
        }

        drag.active = true;

        String mode = interFace.targetHudMode.getValue();

        if (mode.equals("Компактный")) {
            renderCompact(drag, interFace, eventDisplay, target);
            return;
        }

        boolean showStats = mode.equals("Полный");
        float statsW = showStats ? STATS_W : 0F;

        drag.size.set(W_BASE - STATS_W + statsW, H_BASE);

        float x = drag.position.x;
        float y = drag.position.y;
        float w = drag.size.x;
        float h = drag.size.y;
        float alpha = openAnimation.getOutput();
        lastX = x;
        lastY = y;
        lastW = w;
        lastH = h;

        float rad = RADIUS;

        float addALL = ANIM_OFFSET - ANIM_OFFSET * alpha;

        float hudOpacity = InterFace.getInstance().alphaHUD.getValue();

        float px = x + addALL;

        float mainW = w - statsW;

        RenderUtil.Render2D.hudPlate(px, y, w, h, alpha, rad, hudOpacity);

        RenderUtil.Render2D.rect(px + HEAD_CONTAINER_W, y + HEAD_OFFSET_Y,
                0.5F * S, h - HEAD_OFFSET_Y * 2, ColorUtil.getColor(255, 0.07F * alpha), 0.25F);

        drawFace(target, eventDisplay.getPartialTicks(), px + HEAD_OFFSET_X, y + HEAD_OFFSET_Y, FACE_SIZE, alpha);

        if (showStats) {
            RenderUtil.Render2D.rect(px + mainW, y + HEAD_OFFSET_Y,
                    0.5F * S, h - HEAD_OFFSET_Y * 2, ColorUtil.getColor(255, 0.07F * alpha), 0.25F);
        }

        float contentX = px + CONTENT_OFFSET_X + BAR_OFFSET_X;
        float ringR = 8.5F * S;
        float ringCx = px + mainW - ringR - 6F * S;
        float ringCy = y + h / 2F;

        float hpNow = getHealth(target);
        float hpMax = Math.max(1F, target.getMaxHealth() + target.getAbsorptionAmount());
        float hpFrac = MathHelper.clamp(hpNow / hpMax, 0F, 1F);

        animHpText.update();
        animHpText.run(hpNow, 0.15F, Easings.LINEAR);

        int accent = ColorUtil.getClientColor1(1);
        int redCol = ColorUtil.getColor(255, 80, 85);
        int ringMain = ColorUtil.overCol(accent, redCol, 1F - hpFrac);

        String name = target.getName().getString().replace(mc.player.getName().getString(),
                Client.get().moduleManager().get(NameProtect.class).isEnabled()
                        ? "rainydlc.fun"
                        : mc.player.getName().getString());
        Fonts.sf_regular.drawFadingText(name, contentX, y + NAME_Y,
                ringCx - 4F * S - contentX,
                ColorUtil.getColor(255, alpha), NAME_SIZE);

        RenderUtil.Render2D.roundedCircleProgress(eventDisplay.getDrawContext(),
                ringCx, ringCy, ringR, 2.1F * S, hpFrac,
                ColorUtil.replAlpha(ColorUtil.getColor(255, 0.10F), alpha),
                ColorUtil.replAlpha(ringMain, alpha), ColorUtil.replAlpha(ringMain, alpha));
        Fonts.sf_medium.drawCentered(String.format("%.0f", animHpText.get()),
                ringCx, ringCy - 3.0F * S, 5.2F * S, ColorUtil.getColor(255, alpha));

        List<ItemStack> wornItems = new ArrayList<>();
        wornItems.add(target.getEquippedStack(EquipmentSlot.MAINHAND));
        wornItems.add(target.getEquippedStack(EquipmentSlot.OFFHAND));
        wornItems.add(target.getEquippedStack(EquipmentSlot.HEAD));
        wornItems.add(target.getEquippedStack(EquipmentSlot.CHEST));
        wornItems.add(target.getEquippedStack(EquipmentSlot.LEGS));
        wornItems.add(target.getEquippedStack(EquipmentSlot.FEET));

        float itemY = y + 12.0F * S;
        float itemSize = 8.5F * S;
        float itemStep = itemSize + 1.5F * S;
        float maxItemX = ringCx - ringR - 2F * S;
        int wi = 0;
        Matrix3x2fStack itemMatrix = eventDisplay.getDrawContext().getMatrices();
        for (ItemStack stack : wornItems) {
            if (stack == null || stack.isEmpty()) continue;
            float ix = contentX + wi * itemStep;
            if (ix + itemSize > maxItemX) break;
            itemMatrix.pushMatrix();
            itemMatrix.translate(ix + itemSize / 2f, itemY + itemSize / 2f);
            itemMatrix.scale(0.5F * S, 0.5F * S);
            ItemRender.drawItemWithContext(eventDisplay.getDrawContext(), stack, -8, -8, alpha, alpha);
            itemMatrix.popMatrix();
            wi++;
        }

        float iconY = y + 22.0F * S;
        float iconSize = 8F * S;
        float maxIconX = ringCx - ringR - 3F * S;
        int shown = 0;
        if (!target.getStatusEffects().isEmpty()) {
            for (StatusEffectInstance effect : target.getStatusEffects()) {
                float ix = contentX + shown * (iconSize + 2.5F * S);
                if (ix + iconSize > maxIconX || shown >= 8) break;
                Identifier tex = getEffectTexture(effect.getEffectType());
                eventDisplay.getDrawContext().drawGuiTexture(RenderPipelines.GUI_TEXTURED, tex,
                        (int) ix, (int) iconY, (int) iconSize, (int) iconSize,
                        ColorUtil.getColor(255, (int) (255F * alpha)));
                shown++;
            }
        }

        if (showStats) {
            float panelX = px + mainW;
            float hpRowCy = y + h * 0.31F;
            float appleRowCy = y + h * 0.71F;

            drawStatRow(eventDisplay, panelX, statsW, hpRowCy, HEART_ICON, null,
                    String.format("%.0f", animHpText.get()), getHealthColor(target), alpha);

            int notchCount = target instanceof PlayerEntity
                    ? UseCooldowns.count(target.getUuid(), UseCooldowns.Item.NOTCH)
                    : 0;

            int notchColor = notchCount > 0
                    ? ColorUtil.getColor(255, 205, 90)
                    : ColorUtil.getColor(150, 150, 160);

            drawStatRow(eventDisplay, panelX, statsW, appleRowCy, null, notchIcon(),
                    String.valueOf(notchCount), notchColor, alpha);
        }
    }

    /**
     * Компактный режим: голова + ник + ХП/чарка справа, без кольца и эффектов.
     */
    private void renderCompact(DragSetting drag, InterFace interFace, EventDisplay eventDisplay, LivingEntity target) {
        float compactW = 100F * S;
        float compactH = 22F * S;

        float x = drag.position.x;
        float y = drag.position.y;
        float w = compactW;
        float h = compactH;
        float alpha = openAnimation.getOutput();
        lastX = x;
        lastY = y;
        lastW = w;
        lastH = h;

        float rad = RADIUS;
        float addALL = ANIM_OFFSET - ANIM_OFFSET * alpha;
        float hudOpacity = InterFace.getInstance().alphaHUD.getValue();
        float px = x + addALL;

        drag.size.set(w, h);

        RenderUtil.Render2D.hudPlate(px, y, w, h, alpha, rad, hudOpacity);

        // Голова
        float headSize = 16F * S;
        float headX = px + 3F * S;
        float headY = y + (h - headSize) / 2F;
        drawFace(target, eventDisplay.getPartialTicks(), headX, headY, headSize, alpha);

        // Разделитель
        RenderUtil.Render2D.rect(px + 22F * S, y + 3F * S,
                0.5F * S, h - 6F * S, ColorUtil.getColor(255, 0.07F * alpha), 0.25F);

        // Имя цели
        String name = target.getName().getString().replace(mc.player.getName().getString(),
                Client.get().moduleManager().get(NameProtect.class).isEnabled()
                        ? "rainydlc.fun"
                        : mc.player.getName().getString());

        float nameX = px + 25F * S;
        float nameMaxW = w - 25F * S - 40F * S;
        Fonts.sf_regular.drawFadingText(name, nameX, y + h / 2F - NAME_SIZE / 2F,
                nameMaxW, ColorUtil.getColor(255, alpha), NAME_SIZE);

        // Правая колонка статов: ХП сверху, чарка снизу
        float statsX = px + w - 36F * S;
        float iconSize = 7F * S;
        float gap = 2F * S;
        float textSize = 6F * S;

        // ХП
        String hpText = String.format("%.0f", animHpText.get());
        eventDisplay.getDrawContext().drawGuiTexture(RenderPipelines.GUI_TEXTURED, HEART_ICON,
                (int) statsX, (int) (y + 3F * S), (int) iconSize, (int) iconSize,
                ColorUtil.getColor(255, (int) (255F * alpha)));
        Fonts.sf_medium.draw(hpText, statsX + iconSize + gap,
                y + 3F * S + iconSize / 2F - textSize / 2F,
                textSize, ColorUtil.replAlpha(getHealthColor(target), alpha));

        // Чарка
        int notchCount = target instanceof PlayerEntity
                ? UseCooldowns.count(target.getUuid(), UseCooldowns.Item.NOTCH)
                : 0;
        int notchColor = notchCount > 0
                ? ColorUtil.getColor(255, 205, 90)
                : ColorUtil.getColor(150, 150, 160);

        float appleY = y + h - iconSize - 3F * S;
        DrawContext ctx = eventDisplay.getDrawContext();
        float scaleFix = 2F / Math.max(1F, (float) mc.getWindow().getScaleFactor());
        float scale = iconSize / 16F;
        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate((statsX + iconSize / 2F) * scaleFix, (appleY + iconSize / 2F) * scaleFix);
        matrices.scale(scaleFix * scale, scaleFix * scale);
        ctx.drawItem(notchIcon(), -8, -8);
        matrices.popMatrix();

        Fonts.sf_medium.draw(String.valueOf(notchCount), statsX + iconSize + gap,
                appleY + iconSize / 2F - textSize / 2F,
                textSize, ColorUtil.replAlpha(notchColor, alpha));
    }

    /**
     * Строка правой панели: иконка + значение, отцентрованные по ширине панели.
     */
    private void drawStatRow(EventDisplay eventDisplay, float panelX, float panelW, float rowCy,
                             Identifier sprite, ItemStack icon, String text, int color, float alpha) {
        float textW = Fonts.sf_medium.getWidth(text, STATS_TEXT);
        float contentW = STATS_ICON + STATS_GAP + textW;
        float startX = panelX + (panelW - contentW) / 2F;

        if (sprite != null) {
            eventDisplay.getDrawContext().drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite,
                    (int) startX, (int) (rowCy - STATS_ICON / 2F),
                    (int) STATS_ICON, (int) STATS_ICON,
                    ColorUtil.getColor(255, (int) (255F * alpha)));
        } else if (icon != null && !icon.isEmpty()) {
            DrawContext ctx = eventDisplay.getDrawContext();

            float scaleFix = 2F / Math.max(1F, (float) mc.getWindow().getScaleFactor());
            float scale = STATS_ICON / 16F;

            Matrix3x2fStack matrices = ctx.getMatrices();
            matrices.pushMatrix();
            matrices.translate((startX + STATS_ICON / 2F) * scaleFix, rowCy * scaleFix);
            matrices.scale(scaleFix * scale, scaleFix * scale);
            ctx.drawItem(icon, -8, -8);
            matrices.popMatrix();
        }

        Fonts.sf_medium.draw(text, startX + STATS_ICON + STATS_GAP,
                rowCy - STATS_TEXT / 2F, STATS_TEXT, ColorUtil.replAlpha(color, alpha));
    }

    private int getHealthColor(LivingEntity entity) {
        float pct = MathHelper.clamp(getHealth(entity) / Math.max(1F, entity.getMaxHealth()), 0F, 1F);
        int red = ColorUtil.getColor(255, 75, 75, 255);
        int yellow = ColorUtil.getColor(255, 205, 85, 255);
        int green = ColorUtil.getColor(95, 220, 115, 255);
        return pct < 0.5F
                ? ColorUtil.overCol(red, yellow, pct * 2F)
                : ColorUtil.overCol(yellow, green, (pct - 0.5F) * 2F);
    }

    private float getHealth(LivingEntity entity) {
        float hp = entity.getHealth() + entity.getAbsorptionAmount();
        if (entity instanceof PlayerEntity player && mc.world != null) {
            ScoreboardObjective scoreBoard = mc.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME);
            if (scoreBoard != null) {
                MutableText text = ReadableScoreboardScore.getFormattedScore(
                        mc.world.getScoreboard().getScore(player, scoreBoard),
                        scoreBoard.getNumberFormatOr(StyledNumberFormat.EMPTY)
                );
                try {
                    hp = Float.parseFloat(ColorUtil.removeFormatting(text.getString()));
                } catch (Exception ignored) {
                }
            }
        }
        return MathHelper.clamp(hp, 0, entity.getMaxHealth() + entity.getAbsorptionAmount());
    }

    private void drawFace(LivingEntity lastTarget, float lastTickDelta, float x, float y, float size, float alpha) {
        try {
            EntityRenderer<? super LivingEntity, ?> baseRenderer = mc.getEntityRenderDispatcher().getRenderer(lastTarget);
            if (!(baseRenderer instanceof LivingEntityRenderer<?, ?, ?>)) return;

            LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?> renderer =
                    (LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>) baseRenderer;

            LivingEntityRenderState state = renderer.getAndUpdateRenderState(lastTarget, lastTickDelta);
            Identifier textureLocation = renderer.getTexture(state);

            float hurtPercent = lastTarget.hurtTime > 0 ? lastTarget.hurtTime / 10.0f : 0.0f;
            int r = 255;
            int g = (int) (255 * (1.0f - hurtPercent));
            int b = (int) (255 * (1.0f - hurtPercent));
            int color = new Color(r, g, b, (int) (255 * alpha)).getRGB();

            RenderUtil.Images.texture(textureLocation, x, y, size, size,
                    8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f, color, 0, 4);
        } catch (Exception ignored) {
        }
    }

    private boolean inRect(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseY >= y && mouseX <= x + width && mouseY <= y + height;
    }
}

package ru.white.module.impl.display.interfaceimpl;

import org.joml.Matrix3x2fStack;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.entity.effect.StatusEffectInstance;
import ru.white.Client;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.event_impl.MousePressEvent;
import ru.white.module.api.settings.impl.DragSetting;
import ru.white.module.impl.combat.AttackAura;
import ru.white.module.impl.display.InterFace;
import ru.white.module.impl.utils.NameProtect;
import ru.white.theme.ThemeColor;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.animation.satoshi.Direction;
import ru.white.utils.animation.satoshi.EaseInOutQuad;
import ru.white.utils.colors.ColorFormatting;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.ServerUtil;
import ru.white.utils.render.ItemRender;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.font.Fonts;
import ru.white.utils.taskript.StopWatch;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
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

    /** Общий масштаб плашки */
    private static float S = 1.0F;

    /** Размер предмета в координатах HUD и отступы */
    private static float ITEM_SIZE = 8F * S;
    private static float ARMOR_PAD = 2.5F * S;
    private static float ARMOR_X = 10F * S;
    private static float ARMOR_Y = 12F * S;

    /** Базовые размеры и отступы */
    private static float W_BASE = 100F * S;
    private static float H_BASE = 30F * S;
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
        // Обновляем масштаб перед рендером
        S = InterFace.getInstance().sizeHud.getValue();
        ITEM_SIZE = 8F * S;
        ARMOR_PAD = 2.5F * S;
        ARMOR_X = 10F * S;
        ARMOR_Y = 12F * S;
        W_BASE = 100F * S;
        H_BASE = 30F * S;
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
        drag.size.set(W_BASE, H_BASE);

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

        // Единая панель: лицо и информация в одной подложке
        float px = x + addALL;

        RenderUtil.Render2D.hudPlate(px, y, w, h, alpha, rad, hudOpacity);
        RenderUtil.Render2D.hudAccent(px, y, h, alpha, rad);

        // Тонкая вертикальная линия между лицом и блоком информации
        RenderUtil.Render2D.rect(px + HEAD_CONTAINER_W, y + HEAD_OFFSET_Y,
                0.5F * S, h - HEAD_OFFSET_Y * 2, ColorUtil.getColor(255, 0.07F * alpha), 0.25F);

        drawFace(target, eventDisplay.getPartialTicks(), px + HEAD_OFFSET_X, y + HEAD_OFFSET_Y, alpha);

        // ── компоновка: имя → полоска ХП → иконки эффектов → кольцо с числом ──
        float contentX = px + CONTENT_OFFSET_X + BAR_OFFSET_X;
        float ringR = 8.5F * S;
        float ringCx = px + w - ringR - 6F * S;
        float ringCy = y + h / 2F;

        float hpNow = getHealth(target);
        float hpMax = Math.max(1F, target.getMaxHealth() + target.getAbsorptionAmount());
        float hpFrac = MathHelper.clamp(hpNow / hpMax, 0F, 1F);

        animHP.update();
        float barW = ringCx - 4F * S - contentX;
        animHP.run(Math.round(barW * hpFrac), 0.5F, Easings.BACK_OUT);

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

        // тонкая полоска здоровья под именем
        RenderUtil.Render2D.rect(contentX, y + 13F * S, barW, BAR_HEIGHT,
                ColorUtil.multAlpha(ColorUtil.getColor(255, 0.10F), alpha), 2);
        RenderUtil.Render2D.gradientRect(
                contentX, y + 13F * S, Math.min(barW, animHP.get()), BAR_HEIGHT,
                new int[]{
                        ColorUtil.multDark(ColorUtil.replAlpha(ringMain, alpha), 0.5F),
                        ColorUtil.replAlpha(ringMain, alpha),
                        ColorUtil.replAlpha(ringMain, alpha),
                        ColorUtil.multDark(ColorUtil.replAlpha(ringMain, alpha), 0.5F)
                }, 2);

        // кольцо прогресса ХП с целым числом внутри
        RenderUtil.Render2D.roundedCircleProgress(eventDisplay.getDrawContext(),
                ringCx, ringCy, ringR, 2.1F * S, hpFrac,
                ColorUtil.replAlpha(ColorUtil.getColor(255, 0.10F), alpha),
                ColorUtil.replAlpha(ringMain, alpha), ColorUtil.replAlpha(ringMain, alpha));
        Fonts.sf_medium.drawCentered(String.format("%.0f", animHpText.get()),
                ringCx, ringCy - 3.0F * S, 5.2F * S, ColorUtil.getColor(255, alpha));

        // иконки активных эффектов цели — ряд под полоской
        float iconY = y + 19.5F * S;
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

        renderArmor(eventDisplay, target, px + ARMOR_X, y - ARMOR_Y - ANIM_OFFSET + ANIM_OFFSET * alpha, alpha);
    }

    private void renderArmor(EventDisplay eventDisplay, LivingEntity entity, float armorX, float armorY, float alpha) {
        List<ItemStack> armorItems = new ArrayList<>();
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.FEET,
                EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
        }) {
            ItemStack stack = entity.getEquippedStack(slot);
            if (!stack.isEmpty()) armorItems.add(stack);
        }

        if (armorItems.isEmpty()) return;

        float bgX = armorX - ITEM_SIZE / 2 - ARMOR_PAD;
        float bgY = armorY - ITEM_SIZE / 2 - ARMOR_PAD + 0.5F * S;
        float bgW = armorItems.size() * ITEM_SIZE + ARMOR_PAD * 2;
        float bgH = ITEM_SIZE + ARMOR_PAD * 2;

        RenderUtil.Render2D.hudPlate(bgX, bgY, bgW, bgH, alpha, 4 * S, InterFace.getInstance().alphaHUD.getValue());

        for (ItemStack stack : armorItems) {
            Matrix3x2fStack matrix = eventDisplay.getDrawContext().getMatrices();
            matrix.pushMatrix();
            matrix.translate(armorX, armorY);
            // Масштабируем отрисовку самого айтема пропорционально S
            matrix.scale(0.5F * S, 0.5F * S);
            ItemRender.drawItemWithContext(eventDisplay.getDrawContext(), stack, -8, -8, alpha, alpha);
            matrix.popMatrix();
            armorX += ITEM_SIZE;
        }
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

    private void drawFace(LivingEntity lastTarget, float lastTickDelta, float x, float y, float alpha) {
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

            RenderUtil.Images.texture(textureLocation, x, y, FACE_SIZE, FACE_SIZE,
                    8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f, color, 0, 4);
        } catch (Exception ignored) {
        }
    }

    private boolean inRect(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseY >= y && mouseX <= x + width && mouseY <= y + height;
    }
}

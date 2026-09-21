package fun.newrar.module.impl.display.interfaceimpl;

import org.joml.Matrix3x2fStack;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.entity.effect.StatusEffectInstance;
import fun.newrar.Client;
import fun.newrar.manager.event_impl.EventDisplay;
import fun.newrar.module.api.settings.impl.DragSetting;
import fun.newrar.module.impl.combat.AttackAura;
import fun.newrar.module.impl.display.InterFace;
import fun.newrar.module.impl.utils.NameProtect;
import fun.newrar.utils.animation.Animation;
import fun.newrar.utils.animation.Easings;
import fun.newrar.utils.animation.satoshi.Direction;
import fun.newrar.utils.animation.satoshi.EaseInOutQuad;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.render.ItemRender;
import fun.newrar.utils.render.RenderUtil;
import fun.newrar.utils.other.UseCooldowns;
import fun.newrar.utils.render.font.Fonts;
import fun.newrar.utils.taskript.StopWatch;
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

import net.minecraft.item.consume.UseAction;
import org.lwjgl.glfw.GLFW;
import fun.newrar.screen.Menu;
import fun.newrar.screen.DropdownScreen;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

    private static float STATS_W = 34F * S;
    private static float STATS_ICON = 8.5F * S;
    private static float STATS_GAP = 2.5F * S;
    private static float STATS_TEXT = 6.5F * S;

    private static final Identifier HEART_ICON = Identifier.ofVanilla("hud/heart/full");
    private static final Identifier GLOW_TEX = Identifier.of("client", "textures/particles/glow.png");
    private static final Identifier HEART_TEX = Identifier.of("client", "textures/particles/heart.png");

    private static final Identifier[] EMPTY_ARMOR_SLOTS = new Identifier[]{
            Identifier.ofVanilla("container/slot/helmet"),
            Identifier.ofVanilla("container/slot/chestplate"),
            Identifier.ofVanilla("container/slot/leggings"),
            Identifier.ofVanilla("container/slot/boots")
    };

    private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private static ItemStack notchIcon;

    private static ItemStack notchIcon() {
        if (notchIcon == null) notchIcon = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
        return notchIcon;
    }

    private final fun.newrar.utils.animation.satoshi.Animation openAnimation = new EaseInOutQuad(200, 1);
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

    // Sadnes TargetHud animations
    private final Animation sadnesHp = new Animation();
    private final Animation sadnesHpTrail = new Animation();
    private final Animation sadnesAbsorption = new Animation();
    private final Animation sadnesAbsorptionTrail = new Animation();
    private final Animation sadnesItemsLayout = new Animation();
    private final Animation sadnesCopyAnim = new Animation();
    private final Animation sadnesCheckAnim = new Animation();
    private final Animation sadnesEatAnim = new Animation();

    // Sadnes TargetHud interaction & tracking
    private final StopWatch sadnesHoverWatch = new StopWatch();
    private long sadnesCopiedUntil = 0L;
    private ItemStack sadnesEatingStack = ItemStack.EMPTY;
    private LivingEntity sadnesLastEatTarget = null;
    private long sadnesLastEatParticleTime = 0L;
    private int sadnesLastHurtTime = 0;
    private LivingEntity sadnesLastTarget = null;
    private boolean sadnesWasMouseDown = false;

    // Sadnes HUD Particles
    private final List<SadnesHudParticle> sadnesParticles = new ArrayList<>();

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

        if (mode.equals("Полоса") || mode.equals("4") || mode.equals("Четвёртый") || mode.equals("Четвертый") || mode.equalsIgnoreCase("Sadnes") || mode.equalsIgnoreCase("Sadness")) {
            renderSadnes(drag, interFace, eventDisplay, target);
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

        float easeAlpha = (float) Easings.BACK_OUT.ease(MathHelper.clamp(alpha, 0F, 1F));
        float popScale = 0.88F + 0.12F * easeAlpha;
        float actualW = w * popScale;
        float actualH = h * popScale;
        float px = x + (w - actualW) / 2F + (ANIM_OFFSET - ANIM_OFFSET * easeAlpha);
        float py = y + (h - actualH) / 2F;

        float mainW = actualW - statsW;

        float hudOpacity = InterFace.getInstance().alphaHUD.getValue();

        if (target.hurtTime > 0) {
            float hurtFrac = target.hurtTime / 10.0F;
            RenderUtil.Render2D.glow(px, py, actualW, actualH, ColorUtil.getColor(255, 55, 55, 0.35F * alpha * hurtFrac), rad + 4F * S, 12, 1);
        }

        RenderUtil.Render2D.hudPlate(px, py, actualW, actualH, alpha, rad, hudOpacity);

        RenderUtil.Render2D.rect(px + HEAD_CONTAINER_W, py + HEAD_OFFSET_Y,
                0.5F * S, actualH - HEAD_OFFSET_Y * 2, ColorUtil.getColor(255, 0.07F * alpha), 0.25F);

        drawFace(target, eventDisplay.getPartialTicks(), px + HEAD_OFFSET_X, py + HEAD_OFFSET_Y, FACE_SIZE, alpha);

        if (showStats) {
            RenderUtil.Render2D.rect(px + mainW, py + HEAD_OFFSET_Y,
                    0.5F * S, actualH - HEAD_OFFSET_Y * 2, ColorUtil.getColor(255, 0.07F * alpha), 0.25F);
        }

        float contentX = px + CONTENT_OFFSET_X + BAR_OFFSET_X;
        float ringR = 8.5F * S;
        float ringCx = px + mainW - ringR - 6F * S;
        float ringCy = py + actualH / 2F;

        float hpNow = getHealth(target);
        float hpMax = Math.max(1F, target.getMaxHealth() + target.getAbsorptionAmount());
        float hpFrac = MathHelper.clamp(hpNow / hpMax, 0F, 1F);

        animHpText.update();
        animHpText.run(hpNow, 0.15F, Easings.LINEAR);
        animHP.update();
        animHP.run(hpFrac, 0.16F, Easings.QUAD_OUT);
        float animatedHpFrac = animHP.get();

        int accent = ColorUtil.getClientColor1(1);
        int redCol = ColorUtil.getColor(255, 80, 85);
        int ringMain = ColorUtil.overCol(accent, redCol, 1F - animatedHpFrac);

        String name = target.getName().getString().replace(mc.player.getName().getString(),
                Client.get().moduleManager().get(NameProtect.class).isEnabled()
                        ? "RainyProject"
                        : mc.player.getName().getString());
        Fonts.sf_regular.drawFadingText(name, contentX, py + NAME_Y,
                ringCx - 4F * S - contentX,
                ColorUtil.getColor(255, alpha), NAME_SIZE);

        RenderUtil.Render2D.roundedCircleProgress(eventDisplay.getDrawContext(),
                ringCx, ringCy, ringR, 2.1F * S, animatedHpFrac,
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
        float easeAlpha = (float) Easings.BACK_OUT.ease(MathHelper.clamp(alpha, 0F, 1F));
        float popScale = 0.88F + 0.12F * easeAlpha;
        float actualW = w * popScale;
        float actualH = h * popScale;
        float px = x + (w - actualW) / 2F + (ANIM_OFFSET - ANIM_OFFSET * easeAlpha);
        float py = y + (h - actualH) / 2F;

        float hpNow = getHealth(target);
        animHpText.update();
        animHpText.run(hpNow, 0.15F, Easings.LINEAR);

        drag.size.set(w, h);

        float hudOpacity = InterFace.getInstance().alphaHUD.getValue();

        if (target.hurtTime > 0) {
            float hurtFrac = target.hurtTime / 10.0F;
            RenderUtil.Render2D.glow(px, py, actualW, actualH, ColorUtil.getColor(255, 55, 55, 0.35F * alpha * hurtFrac), rad + 3F * S, 10, 1);
        }

        RenderUtil.Render2D.hudPlate(px, py, actualW, actualH, alpha, rad, hudOpacity);

        float headSize = 16F * S * popScale;
        float headX = px + 3F * S;
        float headY = py + (actualH - headSize) / 2F;
        drawFace(target, eventDisplay.getPartialTicks(), headX, headY, headSize, alpha);

        RenderUtil.Render2D.rect(px + 22F * S, py + 3F * S,
                0.5F * S, actualH - 6F * S, ColorUtil.getColor(255, 0.07F * alpha), 0.25F);

        String name = target.getName().getString().replace(mc.player.getName().getString(),
                Client.get().moduleManager().get(NameProtect.class).isEnabled()
                        ? "RainyProject"
                        : mc.player.getName().getString());

        float nameX = px + 25F * S;
        float nameMaxW = w - 25F * S - 40F * S;
        Fonts.sf_regular.drawFadingText(name, nameX, y + h / 2F - NAME_SIZE / 2F,
                nameMaxW, ColorUtil.getColor(255, alpha), NAME_SIZE);

        float statsX = px + w - 36F * S;
        float iconSize = 7F * S;
        float gap = 2F * S;
        float textSize = 6F * S;

        String hpText = String.format("%.0f", animHpText.get());
        eventDisplay.getDrawContext().drawGuiTexture(RenderPipelines.GUI_TEXTURED, HEART_ICON,
                (int) statsX, (int) (y + 3F * S), (int) iconSize, (int) iconSize,
                ColorUtil.getColor(255, (int) (255F * alpha)));
        Fonts.sf_medium.draw(hpText, statsX + iconSize + gap,
                y + 3F * S + iconSize / 2F - textSize / 2F,
                textSize, ColorUtil.replAlpha(getHealthColor(target), alpha));

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

    private void renderStyle4(DragSetting drag, InterFace interFace, EventDisplay eventDisplay, LivingEntity target) {
        renderSadnes(drag, interFace, eventDisplay, target);
    }

    private void renderSadnes(DragSetting drag, InterFace interFace, EventDisplay eventDisplay, LivingEntity target) {
        float w = 100.0F * S;
        float h = 34.0F * S;
        drag.size.set(w, h);

        float x = drag.position.x;
        float y = drag.position.y;
        lastX = x;
        lastY = y;
        lastW = w;
        lastH = h;

        float alpha = openAnimation.getOutput();
        float easeAlpha = (float) Easings.BACK_OUT.ease(MathHelper.clamp(alpha, 0F, 1F));
        float popScale = 0.90F + 0.10F * easeAlpha;
        float actualW = w * popScale;
        float actualH = h * popScale;
        float px = x + (w - actualW) / 2F;
        float py = y + (h - actualH) / 2F;

        float rad = 8.0F * S;
        float hudOpacity = InterFace.getInstance().alphaHUD.getValue();

        // 1. Target hurt glow
        if (target.hurtTime > 0) {
            float hurtFrac = target.hurtTime / 10.0F;
            RenderUtil.Render2D.glow(px, py, actualW, actualH, ColorUtil.getColor(255, 55, 55, 0.35F * alpha * hurtFrac), rad + 4.0F * S, 12, 1);
        }

        // 2. Background plate (Glass blur + plate)
        RenderUtil.Render2D.hudPlate(px, py, actualW, actualH, alpha, rad, hudOpacity);
        RenderUtil.Render2D.rect(px, py, actualW, actualH, ColorUtil.getColor(18, 18, 26, (int) (180 * alpha * hudOpacity)), rad);

        // 3. Particles
        updateAndRenderSadnesParticles(px, py, actualW, actualH, alpha, target);

        // 4. Head / Face Avatar
        float headPad = 5.0F * S;
        float headSize = 24.0F * S;
        float headX = px + headPad;
        float headY = py + headPad;
        float headRad = 4.0F * S;

        // Head plate frame
        RenderUtil.Render2D.rect(headX, headY, headSize, headSize, ColorUtil.getColor(20, 20, 28, (int) (200 * alpha)), headRad);
        RenderUtil.Render2D.outline(headX, headY, headSize, headSize, 0.5F * S, ColorUtil.getColor(255, 255, 255, (int) (25 * alpha)), headRad);

        // Draw player face
        drawFace(target, eventDisplay.getPartialTicks(), headX, headY, headSize, headRad, alpha);

        // Hurt overlay on head
        if (target.hurtTime > 0) {
            float hurtPercent = target.maxHurtTime > 0 ? (float) target.hurtTime / target.maxHurtTime : 0.0F;
            if (hurtPercent > 0.0F) {
                RenderUtil.Render2D.rect(headX, headY, headSize, headSize,
                        ColorUtil.getColor(255, 0, 0, (int) (140 * hurtPercent * alpha)), headRad);
            }
        }

        // Eating / drinking item overlay on avatar
        renderSadnesEatingItem(eventDisplay.getDrawContext(), target, headX, headY, headSize, alpha);

        // 5. Dynamic Layout calculation (Items present vs no items)
        boolean hasItems = hasArmorOrHands(target);
        sadnesItemsLayout.run(hasItems ? 1.0 : 0.0, 0.35, Easings.QUAD_OUT);
        sadnesItemsLayout.update();
        float itemsFactor = (float) sadnesItemsLayout.getValue();

        float yWhenItems = py + 5.0F * S;
        float yItemsRow = yWhenItems + 6.0F * S + 3.0F * S; // py + 14*S
        float yBarWithItems = yItemsRow + 8.0F * S + 3.0F * S; // py + 25*S

        float yNoItems = py + 9.0F * S;
        float yBarNoItems = yNoItems + 6.0F * S + 5.0F * S; // py + 20*S

        float currentNameY = MathHelper.lerp(itemsFactor, yNoItems, yWhenItems);
        float currentItemsY = MathHelper.lerp(itemsFactor, yNoItems + 9.0F * S, yItemsRow);
        float currentBarY = MathHelper.lerp(itemsFactor, yBarNoItems, yBarWithItems);

        float contentX = px + 34.0F * S;
        float barW = 61.0F * S;
        float rightEdge = px + 100.0F * S - 5.0F * S;

        // 6. Health & Absorption data calculation
        float currentHp = getHealth(target);
        float maxHp = Math.max(1.0F, Math.max(target.getMaxHealth(), currentHp));
        currentHp = MathHelper.clamp(currentHp, 0.0F, maxHp);

        float absorption = MathHelper.clamp(target.getAbsorptionAmount(), 0.0F, maxHp);
        boolean hasAbsorption = absorption > 0.01F;

        float hpFrac = currentHp / maxHp;
        float absorpFrac = hasAbsorption ? absorption / maxHp : 0.0F;

        sadnesHp.run(hpFrac, 0.50, Easings.EXPO_OUT);
        sadnesHpTrail.run(hpFrac, 2.50, Easings.EXPO_OUT);
        sadnesHp.update();
        sadnesHpTrail.update();

        if (hasAbsorption) {
            sadnesAbsorption.run(absorpFrac, 0.50, Easings.EXPO_OUT);
            sadnesAbsorptionTrail.run(absorpFrac, 2.50, Easings.EXPO_OUT);
        } else {
            sadnesAbsorption.setValue(0.0);
            sadnesAbsorptionTrail.setValue(0.0);
        }
        sadnesAbsorption.update();
        sadnesAbsorptionTrail.update();

        float animatedHpFrac = MathHelper.clamp((float) sadnesHp.getValue(), 0.0F, 1.0F);
        float animatedHpTrailFrac = MathHelper.clamp((float) sadnesHpTrail.getValue(), 0.0F, 1.0F);
        float animAbsorpFrac = MathHelper.clamp((float) sadnesAbsorption.getValue(), 0.0F, 1.0F);
        float animAbsorpTrailFrac = MathHelper.clamp((float) sadnesAbsorptionTrail.getValue(), 0.0F, 1.0F);

        // 7. Line 1: HP Text & Heart Icon
        String hpText = formatHp(currentHp);
        fun.newrar.utils.render.font.Font font = interFace.fontMode.is("Уникальный") ? Fonts.unique_medium : Fonts.sf_medium;
        float fontSize = 6.0F * S;
        float hpTextW = font.getWidth(hpText, fontSize);

        float heartSize = 7.0F * S;
        float heartX = rightEdge - heartSize;
        float heartY = currentNameY + (fontSize - heartSize) / 2.0F + 0.5F * S;
        float hpTextX = heartX - 2.5F * S - hpTextW;

        int hpMinCol = ColorUtil.getColor(235, 75, 75, 255);
        int hpMaxCol = ColorUtil.getColor(110, 215, 120, 255);
        int hpCol = ColorUtil.overCol(hpMinCol, hpMaxCol, animatedHpFrac);
        int hpRenderColor = ColorUtil.replAlpha(hpCol, (int) (255 * alpha));

        // Draw HP text
        font.draw(hpText, hpTextX, currentNameY, fontSize, hpRenderColor);

        // Draw Heart Icon
        try {
            RenderUtil.Images.texture(HEART_TEX, heartX, heartY, heartSize, heartSize, hpRenderColor);
        } catch (Exception ignored) {
            try {
                eventDisplay.getDrawContext().drawGuiTexture(
                        RenderPipelines.GUI_TEXTURED,
                        HEART_ICON,
                        (int) heartX, (int) heartY, (int) heartSize, (int) heartSize,
                        hpRenderColor
                );
            } catch (Exception ignored2) {
                RenderUtil.Render2D.rect(heartX, heartY, heartSize, heartSize, hpRenderColor, heartSize / 2F);
            }
        }

        // 8. Line 1: Name and Copy to Clipboard
        String cleanName = target.getName().getString();
        String displayName = cleanName.replace(mc.player.getName().getString(),
                Client.get().moduleManager().get(NameProtect.class).isEnabled()
                        ? "RainyProject"
                        : mc.player.getName().getString());

        float nameMaxW = Math.max(0.0F, hpTextX - contentX - 3.0F * S);
        handleSadnesCopyInteraction(contentX, currentNameY, nameMaxW, 8.0F * S, cleanName, alpha);

        float copyAnimVal = (float) sadnesCopyAnim.getValue();
        float checkAnimVal = (float) sadnesCheckAnim.getValue();
        float copyAlpha = copyAnimVal * (1.0F - checkAnimVal) * alpha;
        float checkAlpha = copyAnimVal * checkAnimVal * alpha;

        float iconShift = 9.5F * S * copyAnimVal;

        if (copyAlpha > 0.001F) {
            drawCopyIcon(contentX, currentNameY + 0.5F * S, 7.5F * S, ColorUtil.getColor(255, 255, 255, (int) (200 * copyAlpha)));
        }
        if (checkAlpha > 0.001F) {
            drawCheckIcon(eventDisplay.getDrawContext(), contentX, currentNameY + 0.5F * S, 7.5F * S, ColorUtil.getColor(95, 220, 130, (int) (255 * checkAlpha)));
        }

        float actualNameX = contentX + iconShift;
        float actualNameMaxW = Math.max(0.0F, nameMaxW - iconShift);
        font.drawFadingText(displayName, actualNameX, currentNameY, actualNameMaxW,
                ColorUtil.getColor(255, 255, 255, (int) (255 * alpha)), fontSize);

        // 9. Line 2: Items (Armor & Hands)
        if (itemsFactor > 0.01F) {
            renderSadnesItemsRow(eventDisplay, target, contentX, currentItemsY, rightEdge, alpha * itemsFactor);
        }

        // 10. Line 3: Health Bar & Absorption
        renderSadnesHealthBar(contentX, currentBarY, barW, 5.0F * S, alpha,
                animatedHpFrac, animatedHpTrailFrac, hasAbsorption, animAbsorpFrac, animAbsorpTrailFrac);
    }

    private void renderSadnesHealthBar(float x, float y, float w, float h, float alpha,
                                      float hpFrac, float trailFrac,
                                      boolean hasAbsorption, float absorpFrac, float absorpTrailFrac) {
        float rad = 2.5F * S;
        int accentCol = ColorUtil.getClientColor1(1);
        int accentDark = ColorUtil.multDark(accentCol, 0.65F);

        // 1. Trail / delayed damage fill
        float trailW = w * trailFrac;
        if (trailW > 0.5F) {
            int c1 = ColorUtil.replAlpha(accentCol, (int) (255 * alpha * 0.35F));
            int c2 = ColorUtil.replAlpha(accentDark, (int) (255 * alpha * 0.35F));
            RenderUtil.Render2D.gradientRect(x, y, trailW, h, new int[]{c1, c1, c2, c2}, rad);
        }

        // 2. Track background
        int bg1 = ColorUtil.replAlpha(accentCol, (int) (255 * alpha * 0.15F));
        int bg2 = ColorUtil.replAlpha(accentDark, (int) (255 * alpha * 0.15F));
        RenderUtil.Render2D.gradientRect(x, y, w, h, new int[]{bg1, bg1, bg2, bg2}, rad);

        // 3. Main HP fill
        float fillW = w * hpFrac;
        if (fillW > 0.5F) {
            int col1 = ColorUtil.replAlpha(accentCol, (int) (255 * alpha));
            int col2 = ColorUtil.replAlpha(accentDark, (int) (255 * alpha));
            RenderUtil.Render2D.gradientRect(x, y, fillW, h, new int[]{col1, col1, col2, col2}, rad);
        }

        // 4. Absorption (from right edge)
        if (hasAbsorption) {
            int goldMin = ColorUtil.getColor(130, 79, 4, 255);
            int goldMax = ColorUtil.getColor(235, 161, 52, 255);

            float absorpTrailW = w * absorpTrailFrac;
            if (absorpTrailW > 0.5F) {
                float ax = x + w - absorpTrailW;
                int c1 = ColorUtil.replAlpha(goldMin, (int) (255 * alpha * 0.70F));
                int c2 = ColorUtil.replAlpha(goldMax, (int) (255 * alpha * 0.70F));
                RenderUtil.Render2D.gradientRect(ax, y, absorpTrailW, h, new int[]{c1, c1, c2, c2}, rad);
            }

            float absorpFillW = w * absorpFrac;
            if (absorpFillW > 0.5F) {
                float ax = x + w - absorpFillW;
                int c1 = ColorUtil.replAlpha(goldMin, (int) (255 * alpha * 0.90F));
                int c2 = ColorUtil.replAlpha(goldMax, (int) (255 * alpha * 0.90F));
                RenderUtil.Render2D.gradientRect(ax, y, absorpFillW, h, new int[]{c1, c1, c2, c2}, rad);
            }
        }
    }

    private void renderSadnesItemsRow(EventDisplay eventDisplay, LivingEntity target,
                                      float contentX, float rowY, float rightEdge, float alpha) {
        float itemSize = 8.0F * S;
        float itemSpacing = 0.5F * S;

        // Left side: Armor (HEAD, CHEST, LEGS, FEET)
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            float ix = contentX + i * (itemSize + itemSpacing);
            ItemStack stack = target.getEquippedStack(ARMOR_SLOTS[i]);
            renderSadnesSlot(eventDisplay, stack, ix, rowY, itemSize, alpha);
        }

        // Right side: Mainhand and Offhand
        float twoItemsW = 2 * itemSize + itemSpacing;
        float handsStartX = rightEdge - twoItemsW;

        ItemStack mainHand = target.getEquippedStack(EquipmentSlot.MAINHAND);
        ItemStack offHand = target.getEquippedStack(EquipmentSlot.OFFHAND);

        renderSadnesSlot(eventDisplay, mainHand, handsStartX, rowY, itemSize, alpha);
        renderSadnesSlot(eventDisplay, offHand, handsStartX + itemSize + itemSpacing, rowY, itemSize, alpha);
    }

    private void renderSadnesSlot(EventDisplay eventDisplay, ItemStack stack, float ix, float iy, float itemSize, float alpha) {
        if (stack == null || stack.isEmpty()) {
            float dotSize = 3.0F * S;
            float dx = ix + (itemSize - dotSize) / 2.0F;
            float dy = iy + (itemSize - dotSize) / 2.0F;
            RenderUtil.Render2D.rect(dx, dy, dotSize, dotSize, ColorUtil.getColor(255, 255, 255, (int) (45 * alpha)), dotSize / 2.0F);
            return;
        }

        ItemRender.drawItemWithContext(eventDisplay.getDrawContext(), stack, ix, iy, itemSize / 16.0F, alpha);

        // Durability bar
        if (stack.isItemBarVisible()) {
            int step = stack.getItemBarStep();
            int barCol = stack.getItemBarColor();
            float barW = 6.5F * S;
            float barH = 1.0F * S;
            float bx = ix + 0.75F * S;
            float by = iy + itemSize - barH;
            RenderUtil.Render2D.rect(bx, by, barW, barH, ColorUtil.getColor(0, 0, 0, (int) (180 * alpha)), 0.5F * S);
            RenderUtil.Render2D.rect(bx, by, (step / 13.0F) * barW, barH, ColorUtil.replAlpha(barCol, (int) (255 * alpha)), 0.5F * S);
        } else if (stack.getCount() > 1) {
            String count = String.valueOf(stack.getCount());
            float cntSize = 4.5F * S;
            float cw = Fonts.sf_medium.getWidth(count, cntSize);
            Fonts.sf_medium.draw(count, ix + itemSize - cw + 0.5F * S, iy + itemSize - cntSize + 0.5F * S, cntSize, ColorUtil.getColor(255, 255, 255, (int) (255 * alpha)));
        }
    }

    private void renderSadnesEatingItem(DrawContext drawContext, LivingEntity target,
                                        float headX, float headY, float headSize, float alpha) {
        if (target != sadnesLastEatTarget) {
            sadnesEatAnim.setValue(0.0);
            sadnesEatingStack = ItemStack.EMPTY;
            sadnesLastEatTarget = target;
        }

        boolean isEating = target.isUsingItem() && isFoodOrDrink(target.getActiveItem());
        if (isEating) {
            sadnesEatingStack = target.getActiveItem();
        }

        sadnesEatAnim.run(isEating ? 1.0 : 0.0, 0.25, Easings.QUAD_OUT);
        sadnesEatAnim.update();
        float eatProgress = (float) sadnesEatAnim.getValue();

        if (eatProgress > 0.01F && !sadnesEatingStack.isEmpty()) {
            float itemSize = 8.0F * S;
            float ix = headX + headSize - itemSize * 0.6F;
            float iy = headY + headSize - itemSize * 0.6F;
            double time = System.currentTimeMillis() * 0.0157;
            float bounceY = iy - (float) Math.abs(Math.cos(time)) * 2.5F * S;
            float rot = (float) Math.sin(time) * 9.0F;
            float centerX = ix + itemSize / 2.0F;
            float centerY = bounceY + itemSize / 2.0F;

            Matrix3x2fStack matrices = drawContext.getMatrices();
            matrices.pushMatrix();
            matrices.translate(centerX, centerY);
            matrices.rotate((float) Math.toRadians(rot));
            matrices.translate(-centerX, -centerY);

            ItemRender.drawItemWithContext(drawContext, sadnesEatingStack, ix, bounceY, itemSize / 16.0F, eatProgress * alpha);
            matrices.popMatrix();
        } else if (!isEating) {
            sadnesEatingStack = ItemStack.EMPTY;
        }
    }

    private void updateAndRenderSadnesParticles(float px, float py, float actualW, float actualH, float alpha, LivingEntity target) {
        // Check for hurt event
        if (target != sadnesLastTarget) {
            sadnesLastTarget = target;
            sadnesLastHurtTime = target.hurtTime;
            sadnesParticles.clear();
        } else {
            if (target.hurtTime > sadnesLastHurtTime && target.hurtTime >= 8) {
                float originX = px + 5.0F * S + 12.0F * S;
                float originY = py + 5.0F * S + 12.0F * S;
                int clientCol = ColorUtil.getClientColor1(1);
                Random rnd = new Random();
                for (int i = 0; i < 16; i++) {
                    double angle = rnd.nextDouble() * Math.PI * 2.0;
                    float speed = (35.0F + rnd.nextFloat() * 40.0F) * S;
                    float vx = (float) Math.cos(angle) * speed;
                    float vy = (float) Math.sin(angle) * speed;
                    float size = (1.5F + rnd.nextFloat() * 1.5F) * S;
                    long life = 900L + rnd.nextInt(600);
                    sadnesParticles.add(new SadnesHudParticle(originX, originY, vx, vy, size, clientCol, life, 1.0F));
                }
            }
            sadnesLastHurtTime = target.hurtTime;
        }

        // Check for eating crumbs
        if (target.isUsingItem() && isFoodOrDrink(target.getActiveItem())) {
            long now = System.currentTimeMillis();
            if (now - sadnesLastEatParticleTime >= 90L) {
                sadnesLastEatParticleTime = now;
                float mouthX = px + 5.0F * S + 12.0F * S;
                float mouthY = py + 5.0F * S + 19.5F * S;
                int foodCol = getFoodColor(target.getActiveItem());
                Random rnd = new Random();
                for (int i = 0; i < 2; i++) {
                    float vx = (-28.0F + rnd.nextFloat() * 56.0F) * S;
                    float vy = (-12.0F + rnd.nextFloat() * 34.0F) * S;
                    float size = (1.5F + rnd.nextFloat() * 1.3F) * S;
                    long life = 700L + rnd.nextInt(500);
                    sadnesParticles.add(new SadnesHudParticle(mouthX, mouthY, vx, vy, size, foodCol, life, 0.85F));
                }
            }
        }

        if (sadnesParticles.isEmpty()) return;

        sadnesParticles.removeIf(SadnesHudParticle::isDead);
        while (sadnesParticles.size() > 40) {
            sadnesParticles.remove(0);
        }

        float minX = px + 2.0F * S;
        float minY = py + 2.0F * S;
        float maxX = px + actualW - 2.0F * S;
        float maxY = py + actualH - 2.0F * S;

        for (SadnesHudParticle p : sadnesParticles) {
            p.update(minX, minY, maxX, maxY);
            float lifeFrac = p.getLifeFrac();
            float partAlpha = lifeFrac * p.alphaMult * alpha;
            if (partAlpha <= 0.001F) continue;
            int col = ColorUtil.replAlpha(p.color, (int) (255 * partAlpha));
            try {
                RenderUtil.Images.texture(GLOW_TEX, p.x - p.size, p.y - p.size, p.size * 2F, p.size * 2F, col);
            } catch (Exception ignored) {
                RenderUtil.Render2D.rect(p.x - p.size / 2F, p.y - p.size / 2F, p.size, p.size, col, p.size / 2F);
            }
        }
    }

    private int getFoodColor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ColorUtil.getColor(255, 205, 50, 255);
        if (stack.isOf(Items.ENCHANTED_GOLDEN_APPLE) || stack.isOf(Items.GOLDEN_APPLE)) {
            return ColorUtil.getColor(255, 215, 0, 255);
        } else if (stack.isOf(Items.GOLDEN_CARROT)) {
            return ColorUtil.getColor(245, 175, 40, 255);
        } else if (stack.isOf(Items.POTION)) {
            return ColorUtil.getColor(180, 75, 235, 255);
        } else if (stack.isOf(Items.COOKED_BEEF) || stack.isOf(Items.COOKED_PORKCHOP)) {
            return ColorUtil.getColor(160, 82, 45, 255);
        }
        return ColorUtil.getClientColor1(1);
    }

    private void handleSadnesCopyInteraction(float nameX, float nameY, float nameMaxW, float nameH, String cleanName, float alpha) {
        boolean inGui = mc.currentScreen instanceof ChatScreen || mc.currentScreen instanceof Menu || mc.currentScreen instanceof DropdownScreen;
        boolean hovered = false;
        if (inGui && mc.getWindow() != null) {
            double mouseX = mc.mouse.getX() * (double) mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth();
            double mouseY = mc.mouse.getY() * (double) mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight();
            hovered = mouseX >= nameX && mouseX <= nameX + nameMaxW && mouseY >= nameY - 1.0F * S && mouseY <= nameY + nameH;
        }

        if (!hovered) {
            sadnesHoverWatch.reset();
        }

        boolean showCopy = hovered && sadnesHoverWatch.finished(500);
        sadnesCopyAnim.run(showCopy ? 1.0 : 0.0, 0.30, Easings.QUAD_OUT);
        sadnesCheckAnim.run(System.currentTimeMillis() < sadnesCopiedUntil ? 1.0 : 0.0, 0.25, Easings.EXPO_OUT);
        sadnesCopyAnim.update();
        sadnesCheckAnim.update();

        boolean isMouseDown = mc.getWindow() != null && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (hovered && showCopy && isMouseDown && !sadnesWasMouseDown) {
            mc.keyboard.setClipboard(cleanName);
            sadnesCopiedUntil = System.currentTimeMillis() + 1000L;
        }
        sadnesWasMouseDown = isMouseDown;
    }

    private void drawCopyIcon(float x, float y, float size, int color) {
        float sheetW = size * 0.65F;
        float sheetH = size * 0.75F;
        float offset = size * 0.22F;
        float rad = 1.0F * S;
        RenderUtil.Render2D.outline(x + offset, y, sheetW, sheetH, 0.75F * S, color, rad);
        RenderUtil.Render2D.rect(x, y + offset, sheetW, sheetH, ColorUtil.getColor(18, 18, 24, (color >> 24) & 0xFF), rad);
        RenderUtil.Render2D.outline(x, y + offset, sheetW, sheetH, 0.75F * S, color, rad);
    }

    private void drawCheckIcon(DrawContext drawContext, float x, float y, float size, int color) {
        float x1 = x + size * 0.15F;
        float y1 = y + size * 0.50F;
        float x2 = x + size * 0.40F;
        float y2 = y + size * 0.75F;
        float x3 = x + size * 0.85F;
        float y3 = y + size * 0.20F;
        float th = 1.0F * S;

        drawSegment(drawContext.getMatrices(), x1, y1, x2, y2, th, color);
        drawSegment(drawContext.getMatrices(), x2, y2, x3, y3, th, color);
    }

    private void drawSegment(Matrix3x2fStack matrices, float x1, float y1, float x2, float y2, float th, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len <= 0.001F) return;
        float angle = (float) Math.atan2(dy, dx);
        matrices.pushMatrix();
        matrices.translate(x1, y1);
        matrices.rotate(angle);
        RenderUtil.Render2D.rect(0, -th / 2F, len, th, color, th / 2F);
        matrices.popMatrix();
    }

    private boolean isFoodOrDrink(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        UseAction action = stack.getUseAction();
        return action == UseAction.EAT || action == UseAction.DRINK;
    }

    private boolean hasArmorOrHands(LivingEntity entity) {
        if (!entity.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty() || !entity.getEquippedStack(EquipmentSlot.OFFHAND).isEmpty()) {
            return true;
        }
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!entity.getEquippedStack(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String formatHp(float hp) {
        int whole = (int) hp;
        int dec = Math.round((hp - whole) * 10.0F);
        if (dec >= 10) {
            return Integer.toString(whole + 1);
        } else {
            return dec <= 0 ? Integer.toString(whole) : whole + "." + dec;
        }
    }

    private static class SadnesHudParticle {
        float x, y, vx, vy, size;
        int color;
        long spawnTime, maxLife;
        float alphaMult;

        SadnesHudParticle(float x, float y, float vx, float vy, float size, int color, long maxLife, float alphaMult) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.color = color;
            this.maxLife = maxLife;
            this.alphaMult = alphaMult;
            this.spawnTime = System.currentTimeMillis();
        }

        boolean isDead() {
            return System.currentTimeMillis() - spawnTime >= maxLife;
        }

        void update(float minX, float minY, float maxX, float maxY) {
            x += vx * 0.016F;
            y += vy * 0.016F;
            vx *= 0.95F;
            vy *= 0.95F;

            if (x < minX) { x = minX; vx = -vx * 0.5F; }
            if (x > maxX) { x = maxX; vx = -vx * 0.5F; }
            if (y < minY) { y = minY; vy = -vy * 0.5F; }
            if (y > maxY) { y = maxY; vy = -vy * 0.5F; }
        }

        float getLifeFrac() {
            return 1.0F - MathHelper.clamp((float) (System.currentTimeMillis() - spawnTime) / (float) maxLife, 0F, 1F);
        }
    }

    private void drawFace(LivingEntity lastTarget, float lastTickDelta, float x, float y, float size, float alpha) {
        drawFace(lastTarget, lastTickDelta, x, y, size, 4F, alpha);
    }

    private void drawFace(LivingEntity lastTarget, float lastTickDelta, float x, float y, float size, float radius, float alpha) {
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
                    8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f, color, 0, radius);
            RenderUtil.Images.texture(textureLocation, x, y, size, size,
                    40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f, color, 0, radius);
        } catch (Exception ignored) {
        }
    }

    private boolean inRect(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseY >= y && mouseX <= x + width && mouseY <= y + height;
    }
}

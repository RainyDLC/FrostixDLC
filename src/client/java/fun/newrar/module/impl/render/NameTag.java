package fun.newrar.module.impl.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;
import org.joml.Vector4d;
import fun.newrar.Client;
import fun.newrar.manager.event_impl.EventDisplay;
import fun.newrar.manager.event_impl.EventTick;
import fun.newrar.manager.event_impl.TextFactoryEvent;
import fun.newrar.manager.event_impl.WorldLoadEvent;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.manager.events.orbit.EventPriority;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BooleanSetting;
import fun.newrar.module.impl.display.InterFace;
import fun.newrar.module.impl.player.WorldTracker;
import fun.newrar.module.impl.utils.NameProtect;
import fun.newrar.module.impl.utils.ReportHelper;
import fun.newrar.theme.ThemeColor;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.math.ServerUtil;
import fun.newrar.utils.other.Instance;
import fun.newrar.utils.other.Projection;
import fun.newrar.utils.render.ItemRender;
import fun.newrar.utils.render.RenderUtil;
import fun.newrar.utils.render.ScreenBlur;
import fun.newrar.utils.render.font.Fonts;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ModuleInfo(
        name = "Name Tag",
        category = Category.RENDER,
        desc = "Детализированные таблички с никами, здоровьем и экипировкой сущностей"
)
public class NameTag extends Module {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private static final float FONT_SIZE = 6F;
    private static final float ICON_SIZE = 8F;
    private static final float ITEM_STEP = 10F;
    private static final float TAG_HEIGHT = 15.5F;
    private static final float TAG_PADDING = 5.5F;
    private static final float TAG_SPACING = 4.5F;
    private static final float TAG_RADIUS = 4.5F;
    private static final float ROW_HEIGHT = 13.5F;
    private static final float ROW_PADDING = 4.5F;
    private static final float ROW_SPACING = 3F;
    private static final float ROW_GAP = 2.5F;
    private static final float ROW_RADIUS = 4F;

    public static NameTag get() {
        return Instance.get(NameTag.class);
    }

    public BooleanSetting player       = new BooleanSetting(this, "Отображения игроков", true);
    public BooleanSetting ignoreNaked  = new BooleanSetting(this, "Игнорировать голых", false)
            .setVisible(() -> player.getValue());
    public BooleanSetting handItems    = new BooleanSetting(this, "Предметы в руках", false)
            .setVisible(() -> player.getValue());
    public BooleanSetting mobs         = new BooleanSetting(this, "Отображения мобов", true);
    public BooleanSetting items        = new BooleanSetting(this, "Отображения предметов", true);

    private final List<Entity> entities = new ArrayList<>();
    private final List<ItemStack> equipment = new ArrayList<>();
    private final StringBuilder colored = new StringBuilder();

    private float scaleFix = 1F;
    private float bgAlpha;
    private int neutralColor;
    private int friendColor;

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        entities.clear();
    }

    @EventHandler
    public void onTick(EventTick e) {
        entities.clear();
        if (mc.world == null) return;

        boolean showPlayers = player.getValue();
        boolean skipNaked = ignoreNaked.getValue();
        boolean showMobs = mobs.getValue();
        boolean showItems = items.getValue();

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity p) {
                if (!showPlayers) continue;
                Text custom = p.getCustomName();
                if (custom != null && custom.getString().startsWith("Ghost_")) continue;
                if (skipNaked && !hasArmor(p)) continue;
                entities.add(p);
            } else if (entity instanceof LivingEntity) {
                if (showMobs) entities.add(entity);
            } else if (entity instanceof ItemEntity) {
                if (showItems) entities.add(entity);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDisplay(EventDisplay e) {
        ScreenBlur.capture();

        if (mc.world == null || mc.player == null || entities.isEmpty()) return;

        DrawContext context = e.getDrawContext();
        float tickDelta = e.getPartialTicks();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

        scaleFix = 2F / (float) mc.getWindow().getScaleFactor();
        bgAlpha = InterFace.getInstance().alphaHUD.getValue() * 0.5F;
        neutralColor = ColorUtil.background();
        friendColor = ColorUtil.getColor(0, 255, 0);

        boolean showPlayers = player.getValue();
        boolean showHands = showPlayers && handItems.getValue();
        NameProtect nameProtect = Client.get().moduleManager().get(NameProtect.class);
        boolean hideFriends = nameProtect != null && nameProtect.isEnabled() && nameProtect.friends.getValue();
        var friends = Client.get().friendManager();
        String selfName = mc.player.getName().getString();

        for (Entity entity : entities) {
            Box box = entity.getBoundingBox();
            double dx = (box.minX + box.maxX) * 0.5D - cameraPos.x;
            double dy = (box.minY + box.maxY) * 0.5D - cameraPos.y;
            double dz = (box.minZ + box.maxZ) * 0.5D - cameraPos.z;
            if (dx * dx + dy * dy + dz * dz < 1.0D) continue;

            Vector4d projected = Projection.getVector4D(entity, tickDelta);
            if (Projection.cantSee(projected)) continue;

            float x = (float) Projection.centerX(projected);
            float y = (float) projected.y - 12;

            boolean friend = false;
            String tag = "";

            float hp = entity instanceof LivingEntity living ? getHealth(living) : 0F;
            float maxHp = entity instanceof LivingEntity living ? living.getMaxHealth() : 20F;
            int hpColor = getHealthColor(hp, maxHp);

            if (entity instanceof PlayerEntity p) {
                friend = friends.isFriend(p.getName().getString());
                String shown = hideFriends && friends.isFriend(p.getNameForScoreboard())
                        ? "Friend"
                        : toColoredString(p.getDisplayName()).replace("⚡", "");
                tag = shown.replace(selfName, "RainyProject") + " §r " + (entity.isInvisible() ? "null" : (int) hp) + "hp";
            } else if (entity instanceof LivingEntity living) {
                tag = living.getType().getName().getString() + " §r " + (int) hp + "hp";
            } else if (entity instanceof ItemEntity item) {
                ItemStack stack = item.getStack();
                tag = stack.getFormattedName().getString() + " §7x§f" + stack.getCount();
            }

            renderTag(context, entity, tag, x, y, friend, hpColor);

            if (showPlayers && entity instanceof PlayerEntity p) {
                if (showHands) renderHandItems(context, p, friend, x, (float) projected.w);
                WorldTracker.get().render(context, p, x, y - 3);
            }
        }
    }

    private void renderTag(DrawContext context, Entity entity, String name, float x, float y, boolean friend, int hpColor) {
        TextFactoryEvent nameEvent = new TextFactoryEvent(name);
        nameEvent.hook();
        name = nameEvent.getText();

        equipment.clear();
        if (entity instanceof LivingEntity living) {
            for (EquipmentSlot slot : EquipmentSlot.VALUES) {
                ItemStack stack = living.getEquippedStack(slot);
                if (!stack.isEmpty()) equipment.add(stack);
            }
        }

        String mainText = name;
        String hpPart = null;
        if (name.endsWith("hp") && name.contains(" §r ")) {
            int sepIdx = name.lastIndexOf(" §r ");
            mainText = name.substring(0, sepIdx);
            hpPart = name.substring(sepIdx + 4);
        }

        float mainTextWidth = Fonts.sf_medium.getWidth(mainText, FONT_SIZE);
        float hpWidth = hpPart != null ? Fonts.sf_medium.getWidth(hpPart, FONT_SIZE) + 4.5F : 0F;
        float textWidth = mainTextWidth + hpWidth;
        float itemsWidth = equipment.isEmpty() ? 0 : TAG_SPACING + (equipment.size() - 1) * ITEM_STEP + ICON_SIZE;
        float wr = textWidth + itemsWidth + TAG_PADDING * 2;

        float bgX = x - wr / 2;
        float bgY = y - 3;

        float hudOpacity = InterFace.getInstance() != null ? InterFace.getInstance().alphaHUD.getValue() : 0.6F;

        RenderUtil.Render2D.hudPlate(bgX, bgY, wr, TAG_HEIGHT, 1.0F, TAG_RADIUS, hudOpacity);

        if (friend || ReportHelper.isReported(entity)) {
            int accentColor = ReportHelper.isReported(entity)
                    ? ReportHelper.getReportColor(neutralColor)
                    : friendColor;
            RenderUtil.Render2D.rect(bgX + 1.5F, bgY + 3F, 1.5F, TAG_HEIGHT - 6F, accentColor, 0.75F);
        }

        Client.get().render2D().flushAll();

        float textX = bgX + TAG_PADDING;
        float textY = bgY + (TAG_HEIGHT - Fonts.sf_medium.getHeight(FONT_SIZE)) / 2F - 0.2F;

        int nameColor = friend ? friendColor : ThemeColor.getTextColor();
        Fonts.sf_medium.draw(mainText, textX, textY, FONT_SIZE, nameColor);

        if (hpPart != null) {
            float sepX = textX + mainTextWidth;
            Fonts.sf_regular.draw(" • ", sepX, textY, FONT_SIZE, ThemeColor.getSeparatorColor());
            float hpX = sepX + Fonts.sf_regular.getWidth(" • ", FONT_SIZE);
            Fonts.sf_medium.draw(hpPart, hpX, textY, FONT_SIZE, hpColor);
        }

        if (equipment.isEmpty()) return;

        Matrix3x2fStack matrices = context.getMatrices();
        float itemX = textX + textWidth + TAG_SPACING;
        float itemCenterY = bgY + TAG_HEIGHT / 2F - 0.5F;

        for (int i = 0; i < equipment.size(); i++) {
            matrices.pushMatrix();
            matrices.translate((itemX + i * ITEM_STEP + ICON_SIZE / 2F) * scaleFix, itemCenterY * scaleFix);
            matrices.scale(0.5F, 0.5F);
            ItemRender.drawItemWithContext(context, equipment.get(i), -8, -8, 1F, 1.0F);
            matrices.popMatrix();
        }
    }

    private void renderHandItems(DrawContext context, PlayerEntity entity, boolean friend, float centerX, float feetY) {
        ItemStack mainHand = entity.getMainHandStack();
        ItemStack offHand = entity.getOffHandStack();
        if (mainHand.isEmpty() && offHand.isEmpty()) return;

        float hudOpacity = InterFace.getInstance() != null ? InterFace.getInstance().alphaHUD.getValue() : 0.6F;
        Matrix3x2fStack matrices = context.getMatrices();

        float y = feetY + 2;
        if (!mainHand.isEmpty()) y = renderHandRow(context, matrices, mainHand, hudOpacity, centerX, y);
        if (!offHand.isEmpty()) renderHandRow(context, matrices, offHand, hudOpacity, centerX, y);
    }

    private float renderHandRow(DrawContext context, Matrix3x2fStack matrices, ItemStack stack,
                                float hudOpacity, float centerX, float y) {
        String name = toColoredString(stack.getFormattedName());
        float textWidth = Fonts.sf_medium.getWidth(name, FONT_SIZE);
        float wr = ROW_PADDING * 2 + ICON_SIZE + ROW_SPACING + textWidth;
        float bgX = centerX - wr / 2f;

        RenderUtil.Render2D.hudPlate(bgX, y - 0.75F, wr, ROW_HEIGHT, 1.0F, ROW_RADIUS, hudOpacity);

        matrices.pushMatrix();
        matrices.translate((bgX + ROW_PADDING + ICON_SIZE / 2f) * scaleFix, (y + ROW_HEIGHT / 2f - 1) * scaleFix);
        matrices.scale(0.5F, 0.5F);
        ItemRender.drawItemWithContext(context, stack, -8, -8, 1F, 1.0F);
        matrices.popMatrix();

        Fonts.sf_medium.draw(name, bgX + ROW_PADDING + ICON_SIZE + ROW_SPACING,
                y + (ROW_HEIGHT - Fonts.sf_medium.getHeight(FONT_SIZE)) / 2F - 0.2F, FONT_SIZE, ThemeColor.getTextColor());

        return y + ROW_HEIGHT + ROW_GAP;
    }

    private int getHealthColor(float hp, float maxHp) {
        float pct = MathHelper.clamp(hp / Math.max(1F, maxHp), 0F, 1F);
        int red = ColorUtil.getColor(255, 80, 85);
        int yellow = ColorUtil.getColor(255, 205, 85);
        int green = ColorUtil.getColor(95, 225, 120);
        return pct < 0.5F
                ? ColorUtil.overCol(red, yellow, pct * 2F)
                : ColorUtil.overCol(yellow, green, (pct - 0.5F) * 2F);
    }

    public static boolean hasArmor(PlayerEntity p) {
        return !p.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
                || !p.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
                || !p.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
                || !p.getEquippedStack(EquipmentSlot.FEET).isEmpty();
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
                } catch (Exception ignored) {}
            }
        }
        return ServerUtil.isCopyTime() ? entity.getHealth() : MathHelper.clamp(hp, 0, entity.getMaxHealth() + entity.getAbsorptionAmount());
    }

    private String toColoredString(Text text) {
        colored.setLength(0);
        text.visit((style, string) -> {
            TextColor tc = style.getColor();
            if (tc != null) {
                int rgb = tc.getRgb();
                colored.append("§#");
                for (int shift = 20; shift >= 0; shift -= 4) colored.append(HEX[(rgb >> shift) & 0xF]);
            }
            colored.append(string);
            return Optional.empty();
        }, Style.EMPTY);
        return colored.toString();
    }
}

package fun.newrar.module.impl.display;

import fun.newrar.Client;
import fun.newrar.manager.event_impl.EventDisplay;
import fun.newrar.manager.event_impl.EventTick;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.settings.impl.BooleanSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;
import fun.newrar.utils.animation.Animation;
import fun.newrar.utils.animation.Easings;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.math.MathUtil;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.*;

@ModuleInfo(name = "Arrows", category = Category.RENDER, desc = "Указатели направления на экране в сторону ближайших игроков")
public class Arrows extends Module {
    public BooleanSetting onlyArmored = new BooleanSetting(this, "Игнорировать голых", false);
    public SliderSetting size = new SliderSetting(this, "Размер", 30, 12, 80, 1);
    public SliderSetting radius = new SliderSetting(this, "Радиус от прицела", 50, 20, 80, 1);

    private final Animation animation = new Animation();

    private final List<AbstractClientPlayerEntity> cachedPlayers = new ArrayList<>();
    private final Map<UUID, ArrowState> arrowStates = new HashMap<>();
    private static final Identifier ARROW_TEX = Identifier.of("client", "textures/arrow.png");

    private static class ArrowState {
        float smoothYaw;
        float alpha = 0F;
        boolean initialized;
    }

    @Override
    protected void onDisable() {
        arrowStates.clear();
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (mc.player != null) {
            animation.run(mc.player.isSprinting() ? 1 : mc.currentScreen == null ? 0 : 6, 0.1f, Easings.SINE_OUT);
        }
    }

    @EventHandler(priority = -500)
    public void onDisplay(EventDisplay e) {
        if (mc.player == null || mc.world == null) return;
        if (mc.options.hudHidden || !mc.options.getPerspective().equals(Perspective.FIRST_PERSON)) return;

        cachedPlayers.clear();
        Set<UUID> visibleUuids = new HashSet<>();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player && (!onlyArmored.getValue() || hasArmor(p))) {
                cachedPlayers.add(p);
                visibleUuids.add(p.getUuid());
            }
        }

        arrowStates.keySet().removeIf(uuid -> !visibleUuids.contains(uuid));

        if (cachedPlayers.isEmpty()) return;

        animation.update();

        float targetScale = 2F;
        float currentScale = (float) mc.getWindow().getScaleFactor();
        float scaleFix = targetScale / currentScale;

        int screenWidth  = (int) (mc.getWindow().getScaledWidth()  / scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);

        float partialTicks = e.getPartialTicks();
        float middleW = screenWidth / 2f;
        float middleH = screenHeight / 2f;
        float size = this.size.getValue() / 4;
        float posY = middleH - radius.getValue() - (radius.getValue() / 4) * animation.get();
        float offset = posY + size / 2f - middleH;

        for (AbstractClientPlayerEntity player : cachedPlayers) {
            ArrowState state = arrowStates.computeIfAbsent(player.getUuid(), k -> new ArrowState());

            float targetYaw = getAngle(player, partialTicks) - mc.gameRenderer.getCamera().getYaw();
            targetYaw = MathHelper.wrapDegrees(targetYaw);

            if (!state.initialized) {
                state.smoothYaw = targetYaw;
                state.initialized = true;
            } else {
                float diff = MathHelper.wrapDegrees(targetYaw - state.smoothYaw);
                state.smoothYaw = MathHelper.wrapDegrees(state.smoothYaw + diff * 0.28F);
            }

            state.alpha = MathHelper.clamp(state.alpha + 0.12F, 0F, 1F);

            double dist = mc.player.distanceTo(player);
            float distFactor = (float) MathHelper.clamp(1.0 - (dist / 64.0), 0.55, 1.0);
            float finalAlpha = state.alpha * distFactor;

            int baseColor = Client.get().friendManager().isFriend(player.getName().getString())
                    ? ColorUtil.GREEN
                    : ColorUtil.fade(1);

            int color = ColorUtil.multAlpha(baseColor, finalAlpha);

            float rad = (float) Math.toRadians(state.smoothYaw);
            float cx = middleW - offset * (float) Math.sin(rad);
            float cy = middleH + offset * (float) Math.cos(rad);

            int glowColor = ColorUtil.multAlpha(baseColor, finalAlpha * 0.45F);
            Client.get().render2D().getTexturePipeline().drawGlowTexture(
                    ARROW_TEX,
                    cx - size / 2f, cy - size / 2f, size, size,
                    0f, 0f, 1f, 1f,
                    new int[]{glowColor, glowColor, glowColor, glowColor},
                    new float[]{0f, 0f, 0f, 0f},
                    1f,
                    state.smoothYaw
            );

            Client.get().render2D().getTexturePipeline().drawTexture(
                    ARROW_TEX,
                    cx - size / 2f, cy - size / 2f, size, size,
                    0f, 0f, 1f, 1f,
                    new int[]{color, color, color, color},
                    new float[]{0f, 0f, 0f, 0f},
                    1f,
                    state.smoothYaw
            );
        }
    }

    private static boolean hasArmor(AbstractClientPlayerEntity player) {
        return !player.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
            || !player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
            || !player.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
            || !player.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }

    private static float getAngle(Entity entity, float partialTicks) {
        double x = MathUtil.interpolate(entity.lastRenderX, entity.getX(), partialTicks)
                 - MathUtil.interpolate(mc.player.lastRenderX, mc.player.getX(), partialTicks);
        double z = MathUtil.interpolate(entity.lastRenderZ, entity.getZ(), partialTicks)
                 - MathUtil.interpolate(mc.player.lastRenderZ, mc.player.getZ(), partialTicks);
        return (float) -(Math.atan2(x, z) * (180.0 / Math.PI));
    }
}


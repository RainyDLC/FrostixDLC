package fun.newrar.screen;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import fun.newrar.Client;
import fun.newrar.lang.Lang;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.preview.ModulePreview;
import fun.newrar.module.api.preview.PreviewContext;
import fun.newrar.module.api.preview.PreviewSettings;
import fun.newrar.screen.editor.EditorButton;
import fun.newrar.screen.editor.EditorWidgets;
import fun.newrar.screen.editor.OverlayEditor;
import fun.newrar.screen.editor.Rect;
import fun.newrar.screen.editor.SettingsPanel;
import fun.newrar.utils.animation.Animation;
import fun.newrar.utils.animation.Easings;
import fun.newrar.utils.annotation.IMinecraft;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.render.RenderUtil;
import fun.newrar.utils.render.font.Fonts;

import java.util.UUID;

import static fun.newrar.screen.editor.EditorTheme.PANEL_W;
import static fun.newrar.screen.editor.EditorTheme.ROW_H;

public final class PreviewEditor implements OverlayEditor, IMinecraft {
    private static final PreviewEditor INSTANCE = new PreviewEditor();

    private static final UUID DUMMY_UUID = UUID.fromString("32d0a964-137a-2c03-7cc9-df6700000101");
    private static final String DUMMY_NAME = "RAINY_PREVIEW";

    private final EditorWidgets widgets = new EditorWidgets();
    private final SettingsPanel panel = new SettingsPanel();
    private final PreviewContext context = new PreviewContext();

    private final EditorButton saveButton = new EditorButton("Выйти и сохранить", true);
    private final EditorButton spawnButton = new EditorButton("Показать сейчас", false);

    private final Animation visibility = new Animation();

    private boolean active;
    private Module module;
    private ModulePreview preview;
    private boolean restoreEnabled;

    private OtherClientPlayerEntity dummy;
    private long nextSpawnAt;

    private boolean freeCamera;
    private Rect freeCameraBounds = Rect.EMPTY;
    private boolean draggingCamera;
    private float lastCameraMouseX;
    private float lastCameraMouseY;

    private Vec3d frozenOrigin;
    private double frozenForwardX;
    private double frozenForwardZ;
    private float frozenFacingYaw;

    private PreviewEditor() {
    }

    public static PreviewEditor getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    public boolean isEditing(Module candidate) {
        return active && module == candidate;
    }

    public Module module() {
        return module;
    }

    public void open(Module target) {
        if (!(target instanceof ModulePreview modulePreview)) return;
        if (active) deactivate();

        active = true;
        module = target;
        preview = modulePreview;

        restoreEnabled = target.isEnabled();
        if (!target.isEnabled()) target.setEnabled(true, false);

        context.reset();
        panel.reset();
        panel.unplace();
        setFreeCamera(false);

        updateAnchor();
        if (modulePreview.previewNeedsDummy()) spawnDummy();
        context.setDummy(dummy);
        modulePreview.previewStart(context);

        nextSpawnAt = System.currentTimeMillis() + 250L;
        visibility.set(0);
        visibility.run(1, 0.24F, Easings.EXPO_OUT);
    }

    @Override
    public void saveAndExit() {
        if (!active) return;
        if (Client.get() != null && Client.get().configManager() != null) {
            Client.get().configManager().autoSave();
        }
        deactivate();
    }

    @Override
    public void closeFromMenuRemoval() {
        saveAndExit();
    }

    private void deactivate() {
        if (preview != null) preview.previewStop();
        setFreeCamera(false);
        removeDummy();

        if (module != null && module.isEnabled() != restoreEnabled) {
            module.setEnabled(restoreEnabled, false);
        }

        panel.releaseDrag();
        panel.closePickers();

        active = false;
        module = null;
        preview = null;
        context.reset();
    }

    private void spawnDummy() {
        if (mc.world == null || mc.player == null) return;

        removeDummy();

        OtherClientPlayerEntity entity =
                new OtherClientPlayerEntity(mc.world, new GameProfile(DUMMY_UUID, DUMMY_NAME));

        Vec3d anchor = context.anchor();
        entity.refreshPositionAndAngles(anchor.x, anchor.y, anchor.z, facingYaw(), 0F);
        entity.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.NETHERITE_SWORD));
        entity.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        entity.setHealth(20F);
        entity.setAbsorptionAmount(0F);

        mc.world.addEntity(entity);
        dummy = entity;
        context.setDummy(entity);
    }

    private void removeDummy() {
        if (dummy != null) {
            dummy.discard();
            dummy = null;
        }
        context.setDummy(null);
    }

    private float facingYaw() {
        if (frozenOrigin != null) return frozenFacingYaw;
        return mc.player == null ? 0F : MathHelper.wrapDegrees(mc.player.getYaw() + 180F);
    }

    private void updateAnchor() {
        if (module == null || preview == null || mc.player == null) return;

        PreviewSettings settings = preview.previewSettings();
        float distance = settings.distance(4F);
        float height = settings.height(0F);

        Vec3d origin = frozenOrigin != null ? frozenOrigin : mc.player.getEntityPos();
        double forwardX;
        double forwardZ;

        if (frozenOrigin != null) {
            forwardX = frozenForwardX;
            forwardZ = frozenForwardZ;
        } else {
            double rad = Math.toRadians(mc.player.getYaw());
            forwardX = -Math.sin(rad);
            forwardZ = Math.cos(rad);
        }

        context.setDistance(distance);
        context.setHeight(height);
        context.setAnchor(new Vec3d(
                origin.x + forwardX * distance,
                origin.y + height,
                origin.z + forwardZ * distance
        ));
    }

    private void setFreeCamera(boolean enabled) {
        freeCamera = enabled;
        draggingCamera = false;

        if (!enabled || mc.player == null) {
            frozenOrigin = null;
            return;
        }

        double rad = Math.toRadians(mc.player.getYaw());
        frozenOrigin = mc.player.getEntityPos();
        frozenForwardX = -Math.sin(rad);
        frozenForwardZ = Math.cos(rad);
        frozenFacingYaw = MathHelper.wrapDegrees(mc.player.getYaw() + 180F);
    }

    private void rotateCamera(float mouseX, float mouseY) {
        if (mc.player == null) return;

        float dx = (mouseX - lastCameraMouseX) * 0.6F;
        float dy = (mouseY - lastCameraMouseY) * 0.6F;
        lastCameraMouseX = mouseX;
        lastCameraMouseY = mouseY;

        float yaw = mc.player.getYaw() + dx;
        float pitch = MathHelper.clamp(mc.player.getPitch() + dy, -90F, 90F);

        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
        mc.player.lastYaw = yaw;
        mc.player.lastPitch = pitch;
        mc.player.setHeadYaw(yaw);
    }

    private void updatePreview() {
        if (!active || preview == null) return;
        if (mc.world == null || mc.player == null) return;

        updateAnchor();

        if (preview.previewNeedsDummy()) {
            if (dummy == null || dummy.isRemoved()) {
                spawnDummy();
            } else if (!preview.previewControlsDummy()) {
                Vec3d anchor = context.anchor();

                dummy.setPosition(anchor.x, anchor.y, anchor.z);
                float yaw = facingYaw();
                dummy.setYaw(yaw);
                dummy.setBodyYaw(yaw);
                dummy.setHeadYaw(yaw);
                dummy.setPitch(0F);
            }
        }

        preview.previewTick(context);

        if (!preview.previewSettings().repeats()) return;

        long now = System.currentTimeMillis();
        if (now >= nextSpawnAt) {
            preview.previewSpawn(context);
            context.nextPhase();
            nextSpawnAt = now + Math.max(200L, preview.previewSettings().intervalMs());
        }
    }

    @Override
    public void render(float width, float height, float mouseX, float mouseY, float parentAlpha) {
        if (!active || module == null) return;

        widgets.setMouse(mouseX, mouseY);
        updatePreview();

        panel.layout(module, module.getBigName(), width - PANEL_W - 16F, 14F, width, height, widgets);

        boolean repeats = preview.previewSettings().repeats();

        layoutButtons(width, height, repeats);
        saveButton.update(mouseX, mouseY);
        if (repeats) spawnButton.update(mouseX, mouseY);

        visibility.update();
        float alpha = visibility.get() * parentAlpha;

        drawBand(0F, 0F, width, 62F, alpha, true);
        drawBand(0F, height - 62F, width, 62F, alpha, false);

        Client.get().render2D().flushAll();

        Fonts.sf_regular.drawCentered(Lang.tr("Предпоказ") + " — " + module.getBigName(),
                width / 2F, 16F, 8F, ColorUtil.getColor(240, alpha));
        Fonts.sf_regular.drawCentered(
                Lang.tr(repeats ? "Эффект повторяется сам — крутите настройки справа"
                        : "Эффект виден постоянно — крутите настройки справа"),
                width / 2F, 29F, 6F, ColorUtil.getColor(255, alpha * 0.45F));
        Fonts.sf_regular.drawCentered(
                Lang.tr(freeCamera ? "Тащите по пустому месту, чтобы осмотреться"
                        : "Настройки предпоказа — в самом низу панели") + "   •   " + Lang.tr("Esc — выйти"),
                width / 2F, 39F, 5.5F, ColorUtil.getColor(255, alpha * 0.3F));

        widgets.drawToggleRow(freeCameraBounds, Lang.tr("Свободная камера"), freeCamera,
                "preview:freecamera", alpha);

        if (repeats) widgets.drawButton(spawnButton, alpha);
        widgets.drawButton(saveButton, alpha);

        panel.draw(widgets, alpha);
    }

    private void drawBand(float x, float y, float width, float height, float alpha, boolean fadeDown) {
        int solid = ColorUtil.getColor(0, 0.55F * alpha);
        int clear = ColorUtil.getColor(0, 0F);
        int top = fadeDown ? solid : clear;
        int bottom = fadeDown ? clear : solid;
        RenderUtil.Render2D.gradientRect(x, y, width, height, new int[]{top, top, bottom, bottom}, 0);
    }

    private void layoutButtons(float width, float height, boolean repeats) {
        float buttonH = 18F;
        float saveW = 120F;
        float spawnW = 118F;
        float bottomY = height - 30F;

        saveButton.set(width / 2F - saveW / 2F, bottomY, saveW, buttonH);
        spawnButton.set(width / 2F - spawnW / 2F, bottomY - 24F, spawnW, repeats ? buttonH : 0F);

        float rowY = bottomY - 24F - (repeats ? 22F : 0F);
        freeCameraBounds = new Rect(width / 2F - 80F, rowY, 160F, ROW_H);
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (!active) return false;

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (panel.handleClick(mouseX, mouseY, button)) return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (freeCameraBounds.contains(mouseX, mouseY)) {
                setFreeCamera(!freeCamera);
            } else if (saveButton.contains(mouseX, mouseY)) {
                saveButton.press();
                saveAndExit();
            } else if (spawnButton.contains(mouseX, mouseY)) {
                spawnButton.press();
                spawnNow();
            }
        }

        return true;
    }

    private boolean overControls(float mouseX, float mouseY) {
        return panel.bounds().contains(mouseX, mouseY)
                || freeCameraBounds.contains(mouseX, mouseY)
                || saveButton.contains(mouseX, mouseY)
                || spawnButton.contains(mouseX, mouseY);
    }

    private void spawnNow() {
        if (preview == null || mc.player == null) return;
        if (!preview.previewSettings().repeats()) return;
        updateAnchor();
        preview.previewSpawn(context);
        context.nextPhase();
        nextSpawnAt = System.currentTimeMillis() + Math.max(200L, preview.previewSettings().intervalMs());
    }

    @Override
    public boolean mouseReleased(int button) {
        if (!active) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            panel.releaseDrag();
            draggingCamera = false;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(float mouseX, float mouseY, double verticalAmount) {
        if (!active) return false;
        panel.handleScroll(mouseX, mouseY, verticalAmount);
        return true;
    }

    @Override
    public void rawMouseButton(float mouseX, float mouseY, int button, boolean pressed) {
        if (!active) return;

        if (!pressed) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                panel.releaseDrag();
                draggingCamera = false;
            }
            return;
        }

        if (freeCamera && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !overControls(mouseX, mouseY)) {
            draggingCamera = true;
            lastCameraMouseX = mouseX;
            lastCameraMouseY = mouseY;
        }
    }

    @Override
    public void rawMouseMoved(float mouseX, float mouseY) {
        if (!active) return;
        if (panel.rawMouseMoved(mouseX, mouseY)) return;

        if (draggingCamera) rotateCamera(mouseX, mouseY);
    }

    public Rect panelBounds() {
        return panel.bounds();
    }
}


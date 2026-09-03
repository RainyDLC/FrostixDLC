package fun.cataclysm.mixins.client.screen.ingame;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fun.cataclysm.utils.display.interfaces.QuickImports;
import fun.cataclysm.utils.display.geometry.Render2D;
import fun.cataclysm.Cataclysm;
import fun.cataclysm.utils.client.managers.event.EventManager;
import fun.cataclysm.events.render.DrawEvent;
import fun.cataclysm.utils.math.calc.Calculate;
import fun.cataclysm.features.impl.render.CrossHair;
import fun.cataclysm.features.impl.render.Hud;
import fun.cataclysm.features.impl.render.ShaderHands;
import fun.cataclysm.utils.render.ShaderHandsRenderer;
import fun.cataclysm.features.impl.render.MotionBlur;
import fun.cataclysm.utils.render.MotionBlurRenderer;
import fun.cataclysm.features.impl.render.AttackEffect;
import fun.cataclysm.features.impl.render.BetterMinecraft;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

import java.util.ConcurrentModificationException;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin implements QuickImports {
    @Unique private final Hud hud = Hud.getInstance();

    @Final @Shadow private MinecraftClient client;

    @Final @Shadow private PlayerListHud playerListHud;

    @Shadow protected abstract void renderStatusBars(DrawContext context);

    @Shadow protected abstract void renderMountHealth(DrawContext context);

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/LayeredDrawer;render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            shift = At.Shift.AFTER))
    public void onRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        blur.setup();
        DrawEvent event = new DrawEvent(context, drawEngine, tickCounter.getTickDelta(false));
        EventManager.callEvent(event);
        Render2D.onRender(context);

        boolean debugHudVisible = client.getDebugHud().shouldShowDebugHud();
        boolean tabVisible = client.options.playerListKey.isPressed();

        if (!client.options.hudHidden && !debugHudVisible) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0F, 0.0F, 400.0F);

            Cataclysm.getInstance().getDraggableRepository().draggable().forEach(draggable -> {
                if (draggable.canDraw(hud, draggable)) draggable.startAnimation();
                else draggable.stopAnimation();

                float scale = draggable.getScaleAnimation().getOutput().floatValue();
                if (!draggable.isCloseAnimationFinished()) {
                    draggable.validPosition();
                    try {
                        Calculate.setAlpha(scale, () -> draggable.drawDraggable(context));
                    } catch (ConcurrentModificationException ignored) {}
                }
            });

            context.getMatrices().pop();
        }
    }

    @Inject(method = "renderCrosshair", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/hud/InGameHud;CROSSHAIR_TEXTURE:Lnet/minecraft/util/Identifier;"), cancellable = true)
    public void renderCrosshairHook(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        CrossHair crossHair = CrossHair.getInstance();
        if (crossHair.isState()) {
            crossHair.onRenderCrossHair();
            ci.cancel();
        }
    }

    @Inject(at = @At(value = "HEAD"), method = "renderStatusEffectOverlay", cancellable = true)
    public void renderStatusEffectOverlayHook(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (hud.isState() && hud.interfaceSettings.isSelected("Potions")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At(value = "HEAD"), cancellable = true)
    private void renderScoreboardSidebarHook(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        if (hud.isState() && hud.interfaceSettings.isSelected("Scoreboard")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderOverlayMessage", at = @At(value = "HEAD"), cancellable = true)
    private void renderOverlayMessage(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (hud.isState() && hud.interfaceSettings.isSelected("Hotbar")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderExperienceLevel", at = @At(value = "HEAD"), cancellable = true)
    private void renderExperienceLevel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (hud.isState() && hud.interfaceSettings.isSelected("Hotbar")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderHotbar", at = @At(value = "HEAD"), cancellable = true)
    private void renderHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (hud.isState() && hud.interfaceSettings.isSelected("Hotbar")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderExperienceBar", at = @At(value = "HEAD"), cancellable = true)
    private void renderExperienceBar(DrawContext context, int x, CallbackInfo ci) {
        if (hud.isState() && hud.interfaceSettings.isSelected("Hotbar")) {
            ci.cancel();
        }
    }

    // Delta -> Animations: "Поднятие хотбара" при открытии чата
    @Inject(method = "renderMainHud", at = @At("HEAD"))
    private void cataclysm$raiseHotbarHead(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        BetterMinecraft bm = BetterMinecraft.getInstance();
        if (bm != null && bm.isAnimationSelected("Поднятие хотбара")) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0f, -16.0f * bm.getHotbarRaiseAnimation().getOutput().floatValue(), 0.0f);
        }
    }

    @Inject(method = "renderMainHud", at = @At("RETURN"))
    private void cataclysm$raiseHotbarReturn(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        BetterMinecraft bm = BetterMinecraft.getInstance();
        if (bm != null && bm.isAnimationSelected("Поднятие хотбара")) {
            context.getMatrices().pop();
        }
    }

    @Inject(method = "renderExperienceLevel", at = @At("HEAD"))
    private void cataclysm$raiseExpLevelHead(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        BetterMinecraft bm = BetterMinecraft.getInstance();
        boolean hotbarHidden = hud.isState() && hud.interfaceSettings.isSelected("Hotbar");
        if (!hotbarHidden && bm != null && bm.isAnimationSelected("Поднятие хотбара")) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0f, -16.0f * bm.getHotbarRaiseAnimation().getOutput().floatValue(), 0.0f);
        }
    }

    @Inject(method = "renderExperienceLevel", at = @At("RETURN"))
    private void cataclysm$raiseExpLevelReturn(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        BetterMinecraft bm = BetterMinecraft.getInstance();
        boolean hotbarHidden = hud.isState() && hud.interfaceSettings.isSelected("Hotbar");
        if (!hotbarHidden && bm != null && bm.isAnimationSelected("Поднятие хотбара")) {
            context.getMatrices().pop();
        }
    }

    // Delta -> Animations: "Слот хотбара" (плавное скольжение рамки слота)
    @ModifyArg(method = "renderHotbar", index = 2, require = 0, at = @At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V"))
    private int cataclysm$smoothHotbarSlot(int x) {
        BetterMinecraft bm = BetterMinecraft.getInstance();
        if (bm != null && bm.isAnimationSelected("Слот хотбара") && client.player != null) {
            return Math.round((x - (client.player.getInventory().selectedSlot * 20)) + (bm.getHotbarSlotPosition() * 20.0f));
        }
        return x;
    }

    // Delta -> Animations: дорендер таба во время анимации скрытия
    @Inject(method = "render", at = @At("TAIL"))
    private void cataclysm$renderTabFade(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        BetterMinecraft bm = BetterMinecraft.getInstance();
        if (bm != null && bm.isAnimationSelected("TAB")
                && bm.getTabAnimation().getOutput().floatValue() > 0.0f
                && !client.options.playerListKey.isPressed()
                && client.world != null) {
            this.playerListHud.render(context, client.getWindow().getScaledWidth(), client.world.getScoreboard(),
                    client.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.LIST));
        }
    }

    @Inject(method = "renderMainHud", at = @At("HEAD"))
    private void renderMainHudHook(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ShaderHands shaderHands = Cataclysm.getInstance().getModuleRepository().getModule(ShaderHands.class);
        if (shaderHands != null && shaderHands.state) {
            ShaderHandsRenderer.getInstance().renderOverlayIfPending();
        }
    }


    @Inject(method = "render", at = @At("HEAD"))
    private void cataclysm$applyMotionBlur(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Cataclysm.getInstance() == null) return;
        MotionBlur motionBlur = Cataclysm.getInstance().getModuleRepository().getModule(MotionBlur.class);
        if (motionBlur == null || !motionBlur.state) return;

        MinecraftClient mc = this.client;
        if (mc == null || mc.world == null || mc.player == null || mc.gameRenderer == null) return;
        if (mc.currentScreen != null) {
            MotionBlurRenderer.getInstance().reset();
            return;
        }

        try {
            Camera camera = mc.gameRenderer.getCamera();
            MatrixStack matrices = new MatrixStack();
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
            float fov = mc.gameRenderer.getFov(camera, tickCounter.getTickDelta(false), true);
            MotionBlurRenderer.getInstance().apply(
                    mc,
                    camera,
                    matrices.peek().getPositionMatrix(),
                    mc.gameRenderer.getBasicProjectionMatrix(fov),
                    motionBlur.resolveParameters()
            );
        } catch (Throwable throwable) {
            System.err.println("[MotionBlur] apply failed: " + throwable.getMessage());
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void cataclysm$applyAttackEffect(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Cataclysm.getInstance() == null) return;
        AttackEffect attackEffect = Cataclysm.getInstance().getModuleRepository().getModule(AttackEffect.class);
        if (attackEffect == null || !attackEffect.state) return;

        MinecraftClient mc = this.client;
        if (mc == null || mc.world == null || mc.player == null || mc.gameRenderer == null) return;
        if (mc.currentScreen != null) return;

        try {
            Camera camera = mc.gameRenderer.getCamera();
            MatrixStack matrices = new MatrixStack();
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
            float fov = mc.gameRenderer.getFov(camera, tickCounter.getTickDelta(false), true);
            attackEffect.renderFrame(
                    camera,
                    matrices.peek().getPositionMatrix(),
                    mc.gameRenderer.getBasicProjectionMatrix(fov)
            );
        } catch (Throwable throwable) {
            System.err.println("[AttackEffect] apply failed: " + throwable.getMessage());
        }
    }
}

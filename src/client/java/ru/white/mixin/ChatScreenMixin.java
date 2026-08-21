package ru.white.mixin;

import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import ru.white.Client;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Shadow
    protected TextFieldWidget chatField;

    @Unique private List<String> rainydlc$suggestions = Collections.emptyList();
    @Unique private int rainydlc$selected = 0;

    // геометрия попапа (для кликов) — заполняется в render
    @Unique private int rainydlc$boxX, rainydlc$boxY, rainydlc$boxW, rainydlc$boxCount;
    @Unique private static final int RAINYDLC_LINE_H = 12;

    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private void interceptMessage(String message, boolean addToHistory, CallbackInfo ci) {
        if (message.isEmpty()) return;

        char prefix = Client.get().commandManager().getPrefix();
        if (message.charAt(0) != prefix) return;
        ci.cancel();
        Client.get().commandManager().handleMessage(message);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void rainydlc$renderSuggestions(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        rainydlc$boxCount = 0;

        String text = chatField.getText();
        char prefix = Client.get().commandManager().getPrefix();
        if (text.isEmpty() || text.charAt(0) != prefix) {
            rainydlc$suggestions = Collections.emptyList();
            return;
        }

        rainydlc$suggestions = Client.get().commandManager().getSuggestions(text);
        if (rainydlc$suggestions.isEmpty()) return;

        if (rainydlc$selected >= rainydlc$suggestions.size()) rainydlc$selected = 0;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int count = Math.min(rainydlc$suggestions.size(), 10);

        int width = 0;
        for (int i = 0; i < count; i++) {
            width = Math.max(width, tr.getWidth(rainydlc$suggestions.get(i)));
        }
        width += 6;

        int boxH = count * RAINYDLC_LINE_H;
        int x = chatField.getX() - 2;
        int y = chatField.getY() - boxH - 1;

        rainydlc$boxX = x;
        rainydlc$boxY = y;
        rainydlc$boxW = width;
        rainydlc$boxCount = count;

        context.fill(x, y, x + width, y + boxH, 0xE6000000);
        context.fill(x, y, x + width, y + 1, 0x40FFFFFF);

        for (int i = 0; i < count; i++) {
            int ly = y + i * RAINYDLC_LINE_H;
            boolean sel = i == rainydlc$selected;
            if (sel) context.fill(x, ly, x + width, ly + RAINYDLC_LINE_H, 0x55FFFFFF);
            context.drawText(tr, rainydlc$suggestions.get(i), x + 3, ly + 2,
                    sel ? 0xFFFFFF55 : 0xFFBBBBBB, false);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void rainydlc$keyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (rainydlc$suggestions.isEmpty()) return;
        int n = rainydlc$suggestions.size();

        switch (input.getKeycode()) {
            case GLFW.GLFW_KEY_TAB -> {
                rainydlc$apply(rainydlc$suggestions.get(Math.min(rainydlc$selected, n - 1)));
                cir.setReturnValue(true);
            }
            case GLFW.GLFW_KEY_UP -> {
                rainydlc$selected = (rainydlc$selected - 1 + n) % n;
                cir.setReturnValue(true);
            }
            case GLFW.GLFW_KEY_DOWN -> {
                rainydlc$selected = (rainydlc$selected + 1) % n;
                cir.setReturnValue(true);
            }
            default -> {}
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void rainydlc$mouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {

        int button = click.button();

        double mouseX = click.x();
        double mouseY = click.y();

        if (rainydlc$suggestions.isEmpty() || rainydlc$boxCount == 0 || button != 0) return;

        if (mouseX >= rainydlc$boxX && mouseX <= rainydlc$boxX + rainydlc$boxW
                && mouseY >= rainydlc$boxY && mouseY <= rainydlc$boxY + rainydlc$boxCount * RAINYDLC_LINE_H) {
            int idx = (int) ((mouseY - rainydlc$boxY) / RAINYDLC_LINE_H);
            if (idx >= 0 && idx < rainydlc$boxCount && idx < rainydlc$suggestions.size()) {
                rainydlc$apply(rainydlc$suggestions.get(idx));
                cir.setReturnValue(true);
            }
        }
    }

    @Unique
    private void rainydlc$apply(String suggestion) {
        chatField.setText(suggestion);
        chatField.setCursorToEnd(false);
        rainydlc$selected = 0;
    }
}

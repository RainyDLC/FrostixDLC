package rtx.kimiko.api.ui.settings.impl;

import net.minecraft.client.gui.DrawContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rtx.kimiko.api.drags.Position;
import rtx.kimiko.api.ui.settings.RenderHelper;
import rtx.kimiko.api.ui.settings.Setting;
import rtx.kimiko.utils.animations.Decelerate;
import rtx.kimiko.utils.animations.Direction;
import rtx.kimiko.utils.color.ColorEngine;
import rtx.kimiko.utils.render.fonts.Fonts;
import rtx.kimiko.utils.render.others.RectUtil;
import rtx.kimiko.utils.render.render2d.Render2D;
import rtx.kimiko.utils.sounds.Sounds;

import java.awt.Color;

public class ColorSetting implements Setting {
    @NotNull
    private final rtx.kimiko.api.modules.settings.impl.ColorSetting backend;
    private boolean open;
    @NotNull
    private final Decelerate dropAnim;

    private static final float DROP_W = 82.0f;
    private static final float DROP_H = 78.0f;
    private static final float SV_W = 70.0f;
    private static final float SV_H = 50.0f;
    private static final float BAR_H = 4.0f;

    private int activeDrag = 0; // 0: none, 1: SV, 2: Hue, 3: Alpha

    public ColorSetting(@NotNull rtx.kimiko.api.modules.settings.impl.ColorSetting backend) {
        this.backend = backend;
        this.dropAnim = new Decelerate();
        this.dropAnim.setMs(200);
        this.dropAnim.setValue(1.0);
        this.dropAnim.setDirection(Direction.BACKWARDS);
        this.dropAnim.counter.setTime(System.currentTimeMillis() - 10000L);
    }

    @NotNull
    @Override
    public String name() {
        return this.backend.getName();
    }

    @Override
    public float height() {
        return 16.0f;
    }

    @Override
    public boolean isVisible() {
        return this.backend.isVisible();
    }

    private String hexString() {
        int c = this.backend.getColor();
        int a = (c >>> 24) & 0xFF;
        int r = (c >>> 16) & 0xFF;
        int g = (c >>> 8) & 0xFF;
        int b = c & 0xFF;
        if (a < 255) {
            return String.format("#%02X%02X%02X%02X", r, g, b, a);
        } else {
            return String.format("#%02X%02X%02X", r, g, b);
        }
    }

    @Override
    public float preferredWidth() {
        float hexW = Fonts.MEDIUM.width(hexString(), 5.5f);
        float btnW = 8.0f + 4.0f + hexW + 10.0f;
        return 6.0f + Fonts.MEDIUM.width(this.backend.getDisplayName(), 6.5f) + 6.0f + btnW + 4.0f;
    }

    @Override
    public void render(float x, float y, float w, float alpha) {
        String hex = hexString();
        float hexW = Fonts.MEDIUM.width(hex, 5.5f);
        float inner = 8.0f;
        float btnW = inner + 4.0f + hexW + 10.0f;
        float btnX = x + w - btnW - 4.0f;
        float btnY = y + 2.0f;
        RenderHelper.drawName(this.backend.getDisplayName(), x, y, btnX - (x + 6.0f) - 4.0f, alpha);
        RenderHelper.drawPanelBg(btnX, btnY, btnW, 12.0f, 3.0f, alpha);

        int color = this.backend.getColor();
        int drawAlpha = Math.max(0, Math.min(255, Math.round(255.0f * alpha)));
        int swatch = (drawAlpha << 24) | (color & 0x00FFFFFF);
        Render2D.rect(btnX + 3.0f, btnY + (12.0f - inner) * 0.5f, inner, inner, 2.0f, swatch);
        Render2D.outline(btnX + 3.0f, btnY + (12.0f - inner) * 0.5f, inner, inner, 2.0f, 0.5f,
                ColorEngine.rgba(255, 255, 255, Math.round(40.0f * alpha)));

        int textColor = ColorEngine.rgba(255, 255, 255, Math.round(200.0f * alpha));
        Fonts.MEDIUM.draw(hex, btnX + 3.0f + inner + 4.0f, btnY + 3.0f, 5.5f, textColor);
    }

    @Override
    public boolean click(float x, float y, float w, float mx, float my) {
        String hex = hexString();
        float hexW = Fonts.MEDIUM.width(hex, 5.5f);
        float inner = 8.0f;
        float btnW = inner + 4.0f + hexW + 10.0f;
        float btnX = x + w - btnW - 4.0f;
        float btnY = y + 2.0f;

        if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + 12.0f) {
            this.open = !this.open;
            if (!this.open) {
                this.activeDrag = 0;
            }
            Sounds.play(this.open ? "settings_open" : "settings_close");
            this.dropAnim.setDirection(this.open ? Direction.FORWARDS : Direction.BACKWARDS);
            this.dropAnim.counter.resetCounter();
            return true;
        }
        return false;
    }

    @Override
    public boolean hasOverlay() {
        Double d = this.dropAnim.getOutput();
        return d != null && d > 0.01;
    }

    @Override
    public boolean isOverlayOpen() {
        return this.open;
    }

    @Override
    public void closeOverlay() {
        this.activeDrag = 0;
        if (this.open) {
            this.open = false;
            Sounds.play("settings_close");
            this.dropAnim.setDirection(Direction.BACKWARDS);
            this.dropAnim.counter.resetCounter();
        }
    }

    @Override
    public void releaseDrag() {
        this.activeDrag = 0;
    }

    @Override
    public void renderOverlay(@Nullable DrawContext graphics, float x, float y, float w, float alpha) {
        Double d = this.dropAnim.getOutput();
        if (d == null || d <= 0.01) {
            return;
        }
        float t = (float) d.doubleValue();
        float dAlpha = t * alpha;

        float dropX = x + w - DROP_W - 4.0f;
        float dropY = RenderHelper.overlayY(y, DROP_H);
        float svX = dropX + 6.0f;
        float svY = dropY + 6.0f;
        float hueY = svY + SV_H + 4.0f;
        float alphaY = hueY + BAR_H + 4.0f;

        if (this.activeDrag != 0) {
            handleDrag(Position.Companion.mouseX(), Position.Companion.mouseY(), svX, svY, hueY, alphaY);
        }

        RenderHelper.drawDropBackground(dropX, dropY, DROP_W, DROP_H, dAlpha);

        float hue = this.backend.getHue();
        float saturation = this.backend.getSaturation();
        float brightness = this.backend.getBrightness();
        float colorAlpha = this.backend.getAlpha();

        int solidAlphaInt = Math.max(0, Math.min(255, Math.round(255.0f * dAlpha)));
        int solid = solidAlphaInt << 24;
        int hueTint = (Color.HSBtoRGB(hue, 1.0f, 1.0f) & 0x00FFFFFF) | solid;
        int white = solid | 0x00FFFFFF;
        int ringAlphaInt = Math.max(0, Math.min(255, Math.round(220.0f * dAlpha)));
        int ring = (ringAlphaInt << 24) | 0x00FFFFFF;

        Render2D.rect(svX, svY, SV_W, SV_H, 2.0f, white, hueTint, hueTint, white);
        Render2D.rect(svX, svY, SV_W, SV_H, 2.0f, 0, 0, solid, solid);

        float cursorX = svX + SV_W * saturation;
        float cursorY = svY + SV_H * (1.0f - brightness);
        Render2D.outline(cursorX - 2.5f, cursorY - 2.5f, 5.0f, 5.0f, 2.5f, 1.0f, ring);

        Render2D.pickerHue(svX, hueY, SV_W, BAR_H, dAlpha);
        float hueCursorX = svX + SV_W * hue;
        Render2D.rect(hueCursorX - 1.0f, hueY - 1.0f, 2.0f, BAR_H + 2.0f, 1.0f, ring);

        int opaqueRgb = 0xFF000000 | (Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF);
        Render2D.pickerAlpha(svX, alphaY, SV_W, BAR_H, opaqueRgb, dAlpha);
        float alphaCursorX = svX + SV_W * colorAlpha;
        Render2D.rect(alphaCursorX - 1.0f, alphaY - 1.0f, 2.0f, BAR_H + 2.0f, 1.0f, ring);
    }

    private void handleDrag(float mx, float my, float svX, float svY, float hueY, float alphaY) {
        if (this.activeDrag == 1) {
            float s = clamp((mx - svX) / SV_W, 0.0f, 1.0f);
            float b = clamp(1.0f - (my - svY) / SV_H, 0.0f, 1.0f);
            this.backend.setHSB(this.backend.getHue(), s, b);
        } else if (this.activeDrag == 2) {
            float h = clamp((mx - svX) / SV_W, 0.0f, 1.0f);
            this.backend.setHSB(h, this.backend.getSaturation(), this.backend.getBrightness());
        } else if (this.activeDrag == 3) {
            float a = clamp((mx - svX) / SV_W, 0.0f, 1.0f);
            this.backend.setAlpha(a);
        }
    }

    @Override
    public boolean clickOverlay(float x, float y, float w, float mx, float my) {
        Double d = this.dropAnim.getOutput();
        if (!this.open || d == null || d <= 0.1) {
            return false;
        }

        float dropX = x + w - DROP_W - 4.0f;
        float dropY = RenderHelper.overlayY(y, DROP_H);
        float svX = dropX + 6.0f;
        float svY = dropY + 6.0f;
        float hueY = svY + SV_H + 4.0f;
        float alphaY = hueY + BAR_H + 4.0f;

        if (mx < dropX || mx > dropX + DROP_W || my < dropY || my > dropY + DROP_H) {
            return false;
        }

        if (mx >= svX && mx <= svX + SV_W && my >= svY && my <= svY + SV_H) {
            this.activeDrag = 1;
            handleDrag(mx, my, svX, svY, hueY, alphaY);
            return true;
        }

        if (mx >= svX && mx <= svX + SV_W && my >= hueY - 2.0f && my <= hueY + BAR_H + 2.0f) {
            this.activeDrag = 2;
            handleDrag(mx, my, svX, svY, hueY, alphaY);
            return true;
        }

        if (mx >= svX && mx <= svX + SV_W && my >= alphaY - 2.0f && my <= alphaY + BAR_H + 2.0f) {
            this.activeDrag = 3;
            handleDrag(mx, my, svX, svY, hueY, alphaY);
            return true;
        }

        return true;
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }
}

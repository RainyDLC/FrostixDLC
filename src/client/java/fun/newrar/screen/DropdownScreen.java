package fun.newrar.screen;

import fun.newrar.Client;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.settings.Setting;
import fun.newrar.module.api.settings.impl.*;
import fun.newrar.module.impl.display.ClickGui;
import fun.newrar.screen.editor.OverlayEditor;
import fun.newrar.screen.editor.OverlayEditors;
import fun.newrar.utils.animation.Animation;
import fun.newrar.utils.animation.Easings;
import fun.newrar.utils.animation.satoshi.Direction;
import fun.newrar.utils.animation.satoshi.EaseInOutQuad;
import fun.newrar.utils.annotation.IMinecraft;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.math.Keyboard;
import fun.newrar.utils.math.MathUtil;
import fun.newrar.utils.other.GuiMusicPlayer;
import fun.newrar.utils.other.GuiSounds;
import fun.newrar.utils.render.*;
import fun.newrar.utils.render.font.Font;
import fun.newrar.utils.render.font.Fonts;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.awt.Color;
import java.util.*;

public class DropdownScreen extends Screen implements IMinecraft {
    public static float S = 1.0F;

    public DropdownScreen() {
        super(Text.literal("RainyDLC Dropdown"));
    }

    private boolean exit = false;
    public Animation glomalAnim = new Animation();
    private float scaleFix = 1F;
    private double lastMouseX;
    private double lastMouseY;
    private boolean toggleArmed = false;

    private static boolean textActive = false;

    public static boolean isTextActive() {
        return textActive;
    }

    private final GrayscalePipeline grayscalePipeline = new GrayscalePipeline();
    private final ClickGuiDotsPipeline dotsPipeline = new ClickGuiDotsPipeline();
    private final ScanLinesPipeline scanLinesPipeline = new ScanLinesPipeline();
    private final MenuRaysPipeline raysPipeline = new MenuRaysPipeline();
    private final HalftoneDotsPipeline halftonePipeline = new HalftoneDotsPipeline();

    private final HandsEditor handsEditor = HandsEditor.getInstance();

    private static final class GuiParticle {
        final float x, y;
        final float maxRadius;
        final float lifeMs;
        final float drift;
        final long born = System.currentTimeMillis();

        GuiParticle(float x, float y) {
            this.x = x;
            this.y = y;
            this.maxRadius = 10F + (float) Math.random() * 7F;
            this.lifeMs = 1400F + (float) Math.random() * 700F;
            this.drift = 4F + (float) Math.random() * 6F;
        }

        float progress() {
            return (System.currentTimeMillis() - born) / lifeMs;
        }
    }

    private final List<GuiParticle> particles = new ArrayList<>();
    private long lastParticle;
    private long particleDelay = 90;

    private void spawnParticle(int screenWidth, int screenHeight) {
        if (System.currentTimeMillis() - lastParticle < particleDelay) return;
        lastParticle = System.currentTimeMillis();
        particleDelay = 70 + (long) (Math.random() * 90);
        if (particles.size() > 60) return;
        particles.add(new GuiParticle((float) (Math.random() * screenWidth), (float) (Math.random() * screenHeight)));
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = MathHelper.clamp((value - edge0) / (edge1 - edge0), 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    private void renderParticles(float globalAnim) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            GuiParticle p = particles.get(i);
            float t = p.progress();
            if (t >= 1F) { particles.remove(i); continue; }
            float grow = 1F - (1F - t) * (1F - t) * (1F - t);
            float radius = p.maxRadius * grow * S;
            if (radius < 0.4F) continue;
            float fade = smoothstep(0F, 0.18F, t) * (1F - smoothstep(0.45F, 1F, t));
            float alpha = fade * globalAnim * 0.75F;
            if (alpha < 0.004F) continue;
            float thickness = 1.1F + 2.6F * (1F - grow);
            float py = p.y - p.drift * grow * S;
            RenderUtil.Render2D.outline(p.x - radius, py - radius, radius * 2, radius * 2, thickness, ColorUtil.replAlpha(ColorUtil.client(), alpha), radius);
        }
    }

    private void drawScanLines(int screenWidth, int screenHeight, float globalAnim) {
        if (globalAnim <= 0.01F) return;
        scanLinesPipeline.draw(screenWidth, screenHeight, globalAnim * 0.18F, ColorUtil.getColor(255), 4F * S, 1F, 50F, 3000F, 100F);
    }

    private boolean effect(String name) {
        ClickGui gui = Client.get().moduleManager().get(ClickGui.class);
        return gui != null && gui.effect.getValue(name);
    }

    public static class Column {
        public final Category category;
        public float x;
        public float y;
        public float width;
        public boolean dragging;
        public float dragX;
        public float dragY;
        public boolean collapsed = false;
        public float scroll = 0F;
        public float scrollTarget = 0F;
        public float maxScroll = 0F;
        public final fun.newrar.utils.animation.satoshi.Animation animCollapse = new EaseInOutQuad(220, 1);

        public Column(Category category, float x, float y, float width) {
            this.category = category;
            this.x = x;
            this.y = y;
            this.width = width;
        }
    }

    private final List<Column> columns = new ArrayList<>();
    private final Set<Module> expandedModules = new HashSet<>();
    private final Map<Module, fun.newrar.utils.animation.satoshi.Animation> moduleExpandAnims = new IdentityHashMap<>();
    private final Map<Module, fun.newrar.utils.animation.satoshi.Animation> moduleToggleAnims = new IdentityHashMap<>();
    private final Map<Setting<?>, fun.newrar.utils.animation.satoshi.Animation> settingAnims = new IdentityHashMap<>();
    private final Map<String, fun.newrar.utils.animation.satoshi.Animation> chipAnims = new HashMap<>();

    private fun.newrar.utils.animation.satoshi.Animation chipAnim(String key) {
        return chipAnims.computeIfAbsent(key, k -> new EaseInOutQuad(220, 1, Direction.BACKWARDS));
    }

    private fun.newrar.utils.animation.satoshi.Animation visAnim(Setting<?> setting) {
        return settingAnims.computeIfAbsent(setting, k -> new EaseInOutQuad(220, 1));
    }

    private SliderSetting draggingSlider;
    private ColorSetting draggingColor;
    private int draggingColorBar = -1;
    private float dragSliderTrackX;
    private float dragSliderTrackW;
    private float dragColorTrackX;
    private float dragColorTrackW;

    private StringSetting activeString;
    private String stringBuffer = "";

    private BindSetting activeBind;
    private Module bindingModule;

    private int toggleKey() {
        ClickGui gui = Client.get().moduleManager().get(ClickGui.class);
        return gui == null ? -1 : gui.getKey();
    }

    private void armToggleKey() {
        if (toggleArmed) return;
        int key = toggleKey();
        if (key == -1 || !InputUtil.isKeyPressed(mc.getWindow(), key)) toggleArmed = true;
    }

    private void initColumns() {
        if (!columns.isEmpty()) return;
        Category[] cats = Category.values();
        float colW = 106F;
        float gap = 12F;
        float totalW = cats.length * colW + (cats.length - 1) * gap;
        float startX = Math.max(16F, (mc.getWindow().getScaledWidth() * 2F / (float) mc.getWindow().getScaleFactor() - totalW) / 2F);
        float startY = 32F;
        for (int i = 0; i < cats.length; i++) {
            columns.add(new Column(cats[i], startX + i * (colW + gap), startY, colW));
        }
    }

    @Override
    protected void init() {
        exit = false;
        toggleArmed = false;
        bindingModule = null;
        activeBind = null;
        activeString = null;
        textActive = false;
        draggingSlider = null;
        draggingColor = null;
        draggingColorBar = -1;
        initColumns();
        glomalAnim.run(1, 0.22F, Easings.SINE_OUT);
        GuiSounds.open();
        GuiMusicPlayer.start(0.15F);
    }

    private void startExit() {
        if (exit) return;
        exit = true;
        glomalAnim.run(0, 0.18F, Easings.SINE_IN);
        GuiSounds.close();
    }

    private void closeCheck() {
        if (exit && glomalAnim.isFinished()) {
            close();
            GuiMusicPlayer.stop();
            exit = false;
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        closeCheck();
        armToggleKey();
        glomalAnim.update();
        mouseX = (int) (mouseX / scaleFix);
        mouseY = (int) (mouseY / scaleFix);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    public void renderOverlay(DrawContext context, RenderTickCounter tickCounter) {
        float targetScale = 2F;
        float currentScale = (float) mc.getWindow().getScaleFactor();
        scaleFix = targetScale / currentScale;

        int screenWidth = (int) (mc.getWindow().getScaledWidth() / scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);

        float globalAnim = glomalAnim.get();

        if (context != null) context.getMatrices().pushMatrix();

        OverlayEditor editor = OverlayEditors.active();
        if (editor != null) {
            float editorMouseX = (float) (mc.mouse.getScaledX(mc.getWindow()) / scaleFix);
            float editorMouseY = (float) (mc.mouse.getScaledY(mc.getWindow()) / scaleFix);
            editor.render(screenWidth, screenHeight, editorMouseX, editorMouseY, globalAnim);
            Render2D.endOverlay();
            if (context != null) context.getMatrices().popMatrix();
            return;
        }

        if (effect("Серый фон")) grayscalePipeline.draw(globalAnim);
        ScreenBlur.capture(2);
        if (effect("Размывать фон")) {
            RenderUtil.Blur.blur(0, 0, screenWidth, screenHeight, globalAnim, 0, ColorUtil.getColor(0, 0));
        }
        if (effect("Затемнять фон")) {
            RenderUtil.Images.texture(Identifier.of("client", "textures/frame/background.png"), 0, 0, screenWidth, screenHeight, ColorUtil.multAlpha(ColorUtil.client(), globalAnim));
        }

        float shaderMouseX = (float) (mc.mouse.getScaledX(mc.getWindow()) / scaleFix);
        float shaderMouseY = (float) (mc.mouse.getScaledY(mc.getWindow()) / scaleFix);

        if (effect("Шейдер")) {
            int rayBase = ColorUtil.client();
            raysPipeline.draw(globalAnim, ColorUtil.replAlpha(ColorUtil.multDark(rayBase, 0.5F), 0.9F * globalAnim), ColorUtil.replAlpha(rayBase, 0.9F * globalAnim), 0.1F, 0.08F, 0.26F);
        }

        if (effect("Точки")) {
            ClickGui guiModule = Client.get().moduleManager().get(ClickGui.class);
            float patternMode = guiModule != null && guiModule.dotsPattern.is("Соты") ? 1.0F : 0.0F;
            halftonePipeline.draw(screenWidth, screenHeight, shaderMouseX, shaderMouseY, globalAnim * 0.12F,
                    ColorUtil.getColor(255), 7, 0.7F, 3, 130, patternMode);
        }

        if (effect("Скан линии")) drawScanLines(screenWidth, screenHeight, globalAnim);

        if (effect("Свечение")) {
            int glowCol = ColorUtil.client();
            Draw.gradientRect(0, 0, screenWidth, Math.max(1F, screenHeight * 0.22F),
                    new int[]{
                            ColorUtil.replAlpha(glowCol, 0.15F * globalAnim),
                            ColorUtil.replAlpha(glowCol, 0.15F * globalAnim),
                            ColorUtil.getColor(0, 0),
                            ColorUtil.getColor(0, 0)
                    }, 0);
        }

        if (effect("Частицы")) {
            spawnParticle(screenWidth, screenHeight);
            renderParticles(globalAnim);
        } else if (!particles.isEmpty()) {
            particles.clear();
        }

        ScreenBlur.capture();

        S = Client.get().moduleManager().get(ClickGui.class).size.getValue();

        Font regular = Fonts.sf_regular;
        Font bold = Fonts.sf_bold;
        Font iconFont = Fonts.rainydlc_2;

        float headerH = 21 * S;

        for (Column col : columns) {
            if (col.dragging) {
                col.x = (float) lastMouseX - col.dragX;
                col.y = (float) lastMouseY - col.dragY;
                col.x = MathUtil.clamp(col.x, 2 * S, screenWidth - col.width * S - 2 * S);
                col.y = MathUtil.clamp(col.y, 2 * S, screenHeight - headerH - 10 * S);
            }

            col.animCollapse.setDirection(col.collapsed ? Direction.BACKWARDS : Direction.FORWARDS);
            float collapseProgress = col.animCollapse.getOutput();

            col.scroll += (col.scrollTarget - col.scroll) * 0.25F;

            float cx = col.x;
            float cy = col.y;
            float cw = col.width * S;

            RenderUtil.Render2D.glow(cx, cy, cw, headerH, ColorUtil.getColor(0, 0.15F * globalAnim), 5 * S, 9, 1);
            RenderUtil.Render2D.rect(cx, cy, cw, headerH, ColorUtil.getColor(18, 18, 22, 0.92F * globalAnim), 5 * S);
            RenderUtil.Render2D.outline(cx, cy, cw, headerH, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), 0.4F * globalAnim), 5 * S);
            RenderUtil.Render2D.rect(cx + 6 * S, cy + headerH - 1.2F * S, cw - 12 * S, 1F * S, ColorUtil.replAlpha(ColorUtil.client(), 0.65F * globalAnim), 0.5F * S);

            iconFont.drawCentered(col.category.getIcon(), cx + 11 * S, cy + 6.5F * S, 7.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim));
            bold.draw(col.category.getName(), cx + 20 * S, cy + 6.5F * S, 6.5F * S, ColorUtil.getColor(235, globalAnim));

            String ind = col.collapsed ? "+" : "-";
            bold.draw(ind, cx + cw - 11 * S, cy + 6.5F * S, 6.5F * S, ColorUtil.getColor(160, globalAnim * 0.8F));

            if (collapseProgress <= 0.001F) continue;

            List<Module> list = new ArrayList<>();
            for (Module m : Client.get().moduleManager().values()) {
                if (m.getCategory() == col.category) list.add(m);
            }

            float contentH = 0F;
            for (Module m : list) {
                contentH += 17 * S;
                fun.newrar.utils.animation.satoshi.Animation expAnim = moduleExpandAnims.computeIfAbsent(m, k -> new EaseInOutQuad(220, 1, Direction.BACKWARDS));
                float exp = expAnim.getOutput();
                if (exp > 0.001F) {
                    contentH += getSettingsHeight(m) * exp;
                }
            }

            float maxViewH = screenHeight - cy - headerH - 12 * S;
            float viewH = Math.min(contentH, Math.max(30 * S, maxViewH));
            col.maxScroll = Math.max(0F, contentH - viewH);
            col.scrollTarget = MathUtil.clamp(col.scrollTarget, 0F, col.maxScroll);

            float panelH = (headerH + 2 * S + viewH) * collapseProgress;
            float bodyY = cy + headerH + 2 * S;

            RenderUtil.Render2D.glow(cx, bodyY, cw, viewH * collapseProgress, ColorUtil.getColor(0, 0.12F * globalAnim * collapseProgress), 4 * S, 8, 1);
            RenderUtil.Render2D.rect(cx, bodyY, cw, viewH * collapseProgress, ColorUtil.getColor(14, 14, 18, 0.88F * globalAnim * collapseProgress), 4 * S);
            RenderUtil.Render2D.outline(cx, bodyY, cw, viewH * collapseProgress, 0.5F * S, ColorUtil.getColor(45, 45, 52, 0.55F * globalAnim * collapseProgress), 4 * S);

            Scissor.enable(cx, bodyY, cw, viewH * collapseProgress, 2);

            float modY = bodyY + 3 * S - col.scroll;
            for (Module m : list) {
                fun.newrar.utils.animation.satoshi.Animation togAnim = moduleToggleAnims.computeIfAbsent(m, k -> new EaseInOutQuad(240, 1, Direction.BACKWARDS));
                togAnim.setDirection(m.isEnabled() ? Direction.FORWARDS : Direction.BACKWARDS);
                float tog = togAnim.getOutput();

                fun.newrar.utils.animation.satoshi.Animation expAnim = moduleExpandAnims.computeIfAbsent(m, k -> new EaseInOutQuad(220, 1, Direction.BACKWARDS));
                expAnim.setDirection(expandedModules.contains(m) ? Direction.FORWARDS : Direction.BACKWARDS);
                float exp = expAnim.getOutput();

                boolean isHovered = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, cx + 3 * S, modY, cw - 6 * S, 15 * S);
                fun.newrar.utils.animation.satoshi.Animation hovAnim = chipAnim("mod:hov:" + m.getName());
                hovAnim.setDirection(isHovered ? Direction.FORWARDS : Direction.BACKWARDS);
                float hov = hovAnim.getOutput();

                float cardAlpha = globalAnim * collapseProgress;
                int bgCol = ColorUtil.overCol(
                        ColorUtil.overCol(ColorUtil.getColor(24, 24, 30, 0.65F * cardAlpha), ColorUtil.getColor(35, 35, 44, 0.75F * cardAlpha), hov),
                        ColorUtil.replAlpha(ColorUtil.client(), 0.32F * cardAlpha),
                        tog
                );

                RenderUtil.Render2D.rect(cx + 3 * S, modY, cw - 6 * S, 15 * S, bgCol, 3.5F * S);
                if (tog > 0.01F) {
                    RenderUtil.Render2D.outline(cx + 3 * S, modY, cw - 6 * S, 15 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), 0.55F * cardAlpha * tog), 3.5F * S);
                    RenderUtil.Render2D.rect(cx + 4 * S, modY + 3.5F * S, 1.5F * S, 8 * S, ColorUtil.replAlpha(ColorUtil.client(), cardAlpha * tog), 0.75F * S);
                }

                int textColor = ColorUtil.overCol(ColorUtil.getColor(175, cardAlpha * 0.85F), ColorUtil.getColor(255, cardAlpha), Math.max(tog, hov));
                float titleX = cx + (tog > 0.01F ? 8.5F : 6.5F) * S;
                regular.draw(m.getName(), titleX, modY + 4F * S, 5.8F * S, textColor);

                float badgeRight = cx + cw - 6 * S;
                if (!m.getSettings().isEmpty()) {
                    badgeRight -= 8 * S;
                    String arrow = exp > 0.5F ? "v" : ">";
                    regular.draw(arrow, badgeRight + 1 * S, modY + 4F * S, 5.5F * S, ColorUtil.getColor(160, cardAlpha * (0.6F + 0.4F * exp)));
                }

                if (bindingModule == m) {
                    badgeRight -= 14 * S;
                    regular.draw("[...]", badgeRight, modY + 4F * S, 5F * S, ColorUtil.replAlpha(ColorUtil.client(), cardAlpha));
                } else if (m.getKey() != -1) {
                    String kn = "[" + Keyboard.keyName(m.getKey()) + "]";
                    badgeRight -= regular.getWidth(kn, 5F * S) + 2 * S;
                    regular.draw(kn, badgeRight, modY + 4F * S, 5F * S, ColorUtil.getColor(150, cardAlpha * 0.7F));
                }

                modY += 17 * S;

                if (exp > 0.001F) {
                    float sH = getSettingsHeight(m);
                    float setBoxY = modY;
                    RenderUtil.Render2D.rect(cx + 5 * S, setBoxY, cw - 10 * S, sH * exp, ColorUtil.getColor(18, 18, 24, 0.7F * cardAlpha * exp), 3 * S);
                    RenderUtil.Render2D.outline(cx + 5 * S, setBoxY, cw - 10 * S, sH * exp, 0.5F * S, ColorUtil.getColor(40, 40, 48, 0.45F * cardAlpha * exp), 3 * S);

                    renderSettings(m, cx + 7 * S, setBoxY + 3 * S, cw - 14 * S, cardAlpha * exp);
                    modY += sH * exp;
                }
            }

            Scissor.disable();
        }

        Render2D.endOverlay();
        if (context != null) context.getMatrices().popMatrix();
    }

    private float getSettingsHeight(Module m) {
        Font regular = Fonts.sf_regular;
        float h = 4 * S;
        for (Setting<?> s : m.getSettings()) {
            if (!s.getVisible().get()) continue;
            if (s instanceof BooleanSetting) {
                h += 14 * S;
            } else if (s instanceof SliderSetting) {
                h += 20 * S;
            } else if (s instanceof ModeSetting mode) {
                h += 13 * S;
                float px = 0;
                float chipMaxW = 86 * S;
                for (String val : mode.values) {
                    float tw = regular.getWidth(val, 5F * S) + 6 * S;
                    if (px + tw > chipMaxW && px > 0) { px = 0; h += 11 * S; }
                    px += tw + 2 * S;
                }
                h += 12 * S;
            } else if (s instanceof MultiBooleanSetting multi) {
                h += 13 * S;
                float px = 0;
                float chipMaxW = 86 * S;
                for (BooleanSetting b : multi.getValues()) {
                    float tw = regular.getWidth(b.getName(), 5F * S) + 6 * S;
                    if (px + tw > chipMaxW && px > 0) { px = 0; h += 11 * S; }
                    px += tw + 2 * S;
                }
                h += 12 * S;
            } else if (s instanceof ColorSetting col) {
                h += 14 * S;
                if (col.pickerOpen) h += 36 * S;
            } else if (s instanceof BindSetting || s instanceof StringSetting) {
                h += 15 * S;
            } else if (s instanceof ButtonSetting) {
                h += 15 * S;
            } else if (s instanceof DelimiterSetting) {
                h += 11 * S;
            }
        }
        return h;
    }

    private void renderSettings(Module m, float sx, float sy, float sw, float alpha) {
        Font regular = Fonts.sf_regular;
        float curY = sy;

        for (Setting<?> setting : m.getSettings()) {
            if (!setting.getVisible().get()) continue;

            if (setting instanceof BooleanSetting s) {
                boolean hov = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, sx, curY, sw, 13 * S);
                fun.newrar.utils.animation.satoshi.Animation han = chipAnim(m.getName() + ":" + s.getName() + ":bhov");
                han.setDirection(hov ? Direction.FORWARDS : Direction.BACKWARDS);
                float hovProgress = han.getOutput();

                regular.draw(s.getName(), sx + 2 * S, curY + 3.5F * S, 5.5F * S, ColorUtil.getColor(215, alpha * (0.75F + 0.25F * hovProgress)));

                float togX = sx + sw - 14 * S;
                float togY = curY + 3F * S;
                float togW = 12 * S;
                float togH = 6.5F * S;

                s.animation2.setDirection(s.getValue() ? Direction.FORWARDS : Direction.BACKWARDS);
                float prog = s.animation2.getOutput();

                RenderUtil.Render2D.rect(togX, togY, togW, togH, ColorUtil.overCol(ColorUtil.getColor(35, 35, 42, 0.7F * alpha), ColorUtil.replAlpha(ColorUtil.client(), 0.75F * alpha), prog), 3.25F * S);
                RenderUtil.Render2D.rect(togX + 1F * S + 5.5F * S * prog, togY + 1F * S, 4.5F * S, 4.5F * S, ColorUtil.getColor(255, alpha * (0.4F + 0.6F * prog)), 2.25F * S);

                curY += 14 * S;
            } else if (setting instanceof SliderSetting s) {
                boolean hov = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, sx, curY, sw, 19 * S);
                fun.newrar.utils.animation.satoshi.Animation han = chipAnim(m.getName() + ":" + s.getName() + ":shov");
                han.setDirection(hov ? Direction.FORWARDS : Direction.BACKWARDS);
                float hovProgress = han.getOutput();

                regular.draw(s.getName(), sx + 2 * S, curY + 2F * S, 5.5F * S, ColorUtil.getColor(210, alpha * (0.7F + 0.3F * hovProgress)));
                String valStr = String.valueOf(Math.round(s.getValue() * 100.0) / 100.0);
                regular.draw(valStr, sx + sw - regular.getWidth(valStr, 5.2F * S) - 2 * S, curY + 2.2F * S, 5.2F * S, ColorUtil.replAlpha(ColorUtil.client(), alpha * (0.7F + 0.3F * hovProgress)));

                float trackX = sx + 2 * S;
                float trackY = curY + 12F * S;
                float trackW = sw - 4 * S;
                float trackH = 2.5F * S;

                float perc = MathHelper.clamp((s.getValue() - s.min) / (s.max - s.min), 0F, 1F);
                s.getAnimation().update();
                s.getAnimation().run(perc, 0.08F, Easings.LINEAR);
                float animPerc = s.getAnimation().get();

                RenderUtil.Render2D.rect(trackX, trackY, trackW, trackH, ColorUtil.getColor(28, 28, 35, 0.6F * alpha), 1.25F * S);
                RenderUtil.Render2D.rect(trackX, trackY, trackW * animPerc, trackH, ColorUtil.replAlpha(ColorUtil.client(), 0.85F * alpha), 1.25F * S);
                RenderUtil.Render2D.rect(trackX + trackW * animPerc - 2.5F * S, trackY - 1.2F * S, 5 * S, 5 * S, ColorUtil.getColor(255, alpha), 2.5F * S);

                if (draggingSlider == s) {
                    float np = MathUtil.clamp(((float) lastMouseX - trackX) / trackW, 0F, 1F);
                    float newVal = MathUtil.clamp(MathUtil.round(s.min + (s.max - s.min) * np, s.increment), s.min, s.max);
                    if (s.getValue() != newVal) {
                        s.set(newVal);
                        GuiSounds.sliderTick(np);
                    }
                }

                curY += 20 * S;
            } else if (setting instanceof ModeSetting s) {
                regular.draw(s.getName(), sx + 2 * S, curY + 2F * S, 5.5F * S, ColorUtil.getColor(210, alpha * 0.8F));
                regular.draw(s.getValue(), sx + sw - regular.getWidth(s.getValue(), 5.2F * S) - 2 * S, curY + 2.2F * S, 5.2F * S, ColorUtil.replAlpha(ColorUtil.client(), alpha * 0.9F));

                curY += 12 * S;
                float px = 0;
                float py = 0;
                float chipMaxW = sw - 4 * S;

                for (String val : s.values) {
                    float tw = regular.getWidth(val, 5F * S) + 6 * S;
                    if (px + tw > chipMaxW && px > 0) { px = 0; py += 11 * S; }
                    float cx = sx + 2 * S + px;
                    float cyy = curY + py;

                    boolean sel = val.equals(s.getValue());
                    fun.newrar.utils.animation.satoshi.Animation ca = chipAnim(m.getName() + ":" + s.getName() + ":" + val);
                    ca.setDirection(sel ? Direction.FORWARDS : Direction.BACKWARDS);
                    float selProgress = ca.getOutput();

                    RenderUtil.Render2D.rect(cx, cyy, tw, 9 * S, ColorUtil.overCol(ColorUtil.getColor(28, 28, 35, 0.55F * alpha), ColorUtil.replAlpha(ColorUtil.client(), 0.65F * alpha), selProgress), 2.5F * S);
                    regular.drawCentered(val, cx + tw / 2F, cyy + 2F * S, 5F * S, ColorUtil.getColor(255, alpha * (0.45F + 0.55F * selProgress)));
                    px += tw + 2 * S;
                }
                curY += py + 12 * S;
            } else if (setting instanceof MultiBooleanSetting s) {
                int total = s.getValues().size();
                int onCount = 0;
                for (BooleanSetting b : s.getValues()) if (b.getValue()) onCount++;
                String cnt = onCount + "/" + total;

                regular.draw(s.getName(), sx + 2 * S, curY + 2F * S, 5.5F * S, ColorUtil.getColor(210, alpha * 0.8F));
                regular.draw(cnt, sx + sw - regular.getWidth(cnt, 5.2F * S) - 2 * S, curY + 2.2F * S, 5.2F * S, ColorUtil.replAlpha(ColorUtil.client(), alpha * 0.85F));

                curY += 12 * S;
                float px = 0;
                float py = 0;
                float chipMaxW = sw - 4 * S;

                for (BooleanSetting b : s.getValues()) {
                    float tw = regular.getWidth(b.getName(), 5F * S) + 6 * S;
                    if (px + tw > chipMaxW && px > 0) { px = 0; py += 11 * S; }
                    float cx = sx + 2 * S + px;
                    float cyy = curY + py;

                    fun.newrar.utils.animation.satoshi.Animation ca = chipAnim(m.getName() + ":" + s.getName() + ":" + b.getName());
                    ca.setDirection(b.getValue() ? Direction.FORWARDS : Direction.BACKWARDS);
                    float selProgress = ca.getOutput();

                    RenderUtil.Render2D.rect(cx, cyy, tw, 9 * S, ColorUtil.overCol(ColorUtil.getColor(28, 28, 35, 0.55F * alpha), ColorUtil.replAlpha(ColorUtil.client(), 0.65F * alpha), selProgress), 2.5F * S);
                    regular.drawCentered(b.getName(), cx + tw / 2F, cyy + 2F * S, 5F * S, ColorUtil.getColor(255, alpha * (0.45F + 0.55F * selProgress)));
                    px += tw + 2 * S;
                }
                curY += py + 12 * S;
            } else if (setting instanceof ColorSetting s) {
                regular.draw(s.getName(), sx + 2 * S, curY + 3.5F * S, 5.5F * S, ColorUtil.getColor(210, alpha * 0.85F));

                float previewX = sx + sw - 12 * S;
                float previewY = curY + 3F * S;
                int col = s.getValue();
                RenderUtil.Render2D.rect(previewX, previewY, 8 * S, 8 * S, ColorUtil.replAlpha(col, alpha), 4 * S);
                RenderUtil.Render2D.outline(previewX - 0.5F * S, previewY - 0.5F * S, 9 * S, 9 * S, 0.5F * S, ColorUtil.getColor(255, alpha * 0.5F), 4 * S);

                curY += 14 * S;

                if (s.pickerOpen) {
                    float bx = sx + 2 * S;
                    float bw = sw - 4 * S;
                    float[] hsb = Color.RGBtoHSB((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);

                    int segs = 14;
                    float seg = bw / segs;

                    for (int bar = 0; bar < 3; bar++) {
                        float by = curY + bar * 11 * S;
                        for (int i = 0; i < segs; i++) {
                            float t0 = (float) i / segs;
                            float t1 = (float) (i + 1) / segs;
                            int c0 = switch (bar) { case 0 -> Color.HSBtoRGB(t0, 1F, 1F); case 1 -> Color.HSBtoRGB(hsb[0], t0, hsb[2]); default -> Color.HSBtoRGB(hsb[0], hsb[1], t0); };
                            int c1 = switch (bar) { case 0 -> Color.HSBtoRGB(t1, 1F, 1F); case 1 -> Color.HSBtoRGB(hsb[0], t1, hsb[2]); default -> Color.HSBtoRGB(hsb[0], hsb[1], t1); };
                            RenderUtil.Render2D.gradientRect(bx + i * seg, by, seg + 0.5F * S, 3.5F * S, new int[]{ColorUtil.replAlpha(c0, alpha), ColorUtil.replAlpha(c1, alpha), ColorUtil.replAlpha(c1, alpha), ColorUtil.replAlpha(c0, alpha)}, i == 0 ? 1.5F * S : 0, i == segs - 1 ? 1.5F * S : 0, i == segs - 1 ? 1.5F * S : 0, i == 0 ? 1.5F * S : 0);
                        }

                        float kx = bx + bw * hsb[bar];
                        RenderUtil.Render2D.rect(kx - 2F * S, by - 0.5F * S, 4 * S, 4.5F * S, ColorUtil.getColor(255, alpha), 2 * S);
                    }

                    if (draggingColor == s && draggingColorBar != -1) {
                        float t = MathUtil.clamp(((float) lastMouseX - bx) / bw, 0F, 1F);
                        float[] nh = {hsb[0], hsb[1], hsb[2]};
                        nh[draggingColorBar] = t;
                        int rgb = Color.HSBtoRGB(nh[0], nh[1], nh[2]);
                        int newCol = (col & 0xFF000000) | (rgb & 0x00FFFFFF);
                        if (newCol != col) {
                            s.set(newCol);
                            GuiSounds.colorTick(t);
                        }
                    }

                    curY += 36 * S;
                }
            } else if (setting instanceof BindSetting s) {
                regular.draw(s.getName(), sx + 2 * S, curY + 3.5F * S, 5.5F * S, ColorUtil.getColor(210, alpha * 0.85F));

                boolean binding = activeBind == s;
                String keyStr = binding ? "..." : (s.get() == -1 ? "None" : Keyboard.keyName(s.get()));
                float kw = regular.getWidth(keyStr, 5F * S) + 6 * S;
                float kx = sx + sw - kw - 2 * S;
                float ky = curY + 2.5F * S;

                RenderUtil.Render2D.rect(kx, ky, kw, 9 * S, ColorUtil.getColor(28, 28, 35, 0.65F * alpha), 2.5F * S);
                RenderUtil.Render2D.outline(kx, ky, kw, 9 * S, 0.5F * S, binding ? ColorUtil.replAlpha(ColorUtil.client(), alpha) : ColorUtil.getColor(50, 50, 60, 0.45F * alpha), 2.5F * S);
                regular.drawCentered(keyStr, kx + kw / 2F, ky + 2F * S, 5F * S, binding ? ColorUtil.replAlpha(ColorUtil.client(), alpha) : ColorUtil.getColor(235, alpha * 0.8F));

                curY += 15 * S;
            } else if (setting instanceof StringSetting s) {
                regular.draw(s.getName(), sx + 2 * S, curY + 3.5F * S, 5.5F * S, ColorUtil.getColor(210, alpha * 0.85F));

                boolean editing = activeString == s;
                String textStr = editing ? stringBuffer + ((System.currentTimeMillis() / 400) % 2 == 0 ? "_" : "") : s.getValue();
                if (textStr.isEmpty()) textStr = "...";

                float tw = Math.min(regular.getWidth(textStr, 5F * S) + 6 * S, sw * 0.55F);
                float tx = sx + sw - tw - 2 * S;
                float ty = curY + 2.5F * S;

                RenderUtil.Render2D.rect(tx, ty, tw, 9 * S, ColorUtil.getColor(28, 28, 35, 0.65F * alpha), 2.5F * S);
                RenderUtil.Render2D.outline(tx, ty, tw, 9 * S, 0.5F * S, editing ? ColorUtil.replAlpha(ColorUtil.client(), alpha) : ColorUtil.getColor(50, 50, 60, 0.45F * alpha), 2.5F * S);
                regular.drawFadingText(textStr, tx + 3 * S, ty + 2F * S, tw - 5 * S, editing ? ColorUtil.replAlpha(ColorUtil.client(), alpha) : ColorUtil.getColor(235, alpha * 0.8F), 5F * S);

                curY += 15 * S;
            } else if (setting instanceof ButtonSetting s) {
                float bx = sx + 2 * S;
                float by = curY + 1.5F * S;
                float bw = sw - 4 * S;
                float bh = 11 * S;

                boolean hov = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, bx, by, bw, bh);
                s.pressAnim.update();
                float press = s.pressAnim.get();

                RenderUtil.Render2D.rect(bx, by, bw, bh, ColorUtil.overCol(ColorUtil.getColor(30, 30, 38, 0.7F * alpha), ColorUtil.replAlpha(ColorUtil.client(), 0.3F * alpha), Math.max(hov ? 0.4F : 0F, press)), 3 * S);
                RenderUtil.Render2D.outline(bx, by, bw, bh, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), alpha * (0.3F + 0.5F * Math.max(hov ? 0.5F : 0F, press))), 3 * S);
                regular.drawCentered(s.getName(), bx + bw / 2F, by + 2.5F * S, 5.5F * S, ColorUtil.getColor(240, alpha));

                curY += 15 * S;
            } else if (setting instanceof DelimiterSetting s) {
                float dy = curY + 5F * S;
                RenderUtil.Render2D.rect(sx + 2 * S, dy, sw - 4 * S, 0.5F * S, ColorUtil.getColor(55, 55, 65, 0.45F * alpha), 0.25F * S);
                if (!s.getName().isEmpty()) {
                    regular.drawCentered(s.getName(), sx + sw / 2F, dy - 3.5F * S, 5F * S, ColorUtil.getColor(170, alpha * 0.65F));
                }
                curY += 11 * S;
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        float mouseX = (float) (click.x() / scaleFix);
        float mouseY = (float) (click.y() / scaleFix);

        OverlayEditor editor = OverlayEditors.active();
        if (editor != null) {
            return editor.mouseClicked(mouseX, mouseY, click.button());
        }

        if (bindingModule != null) {
            bindingModule = null;
            GuiSounds.bindReset();
            return true;
        }

        if (activeBind != null) {
            activeBind = null;
            GuiSounds.bindReset();
            return true;
        }

        if (activeString != null) {
            activeString.set(stringBuffer);
            activeString = null;
            textActive = false;
            GuiSounds.editCommit();
        }

        float headerH = 21 * S;

        for (int i = columns.size() - 1; i >= 0; i--) {
            Column col = columns.get(i);
            float cx = col.x;
            float cy = col.y;
            float cw = col.width * S;

            if (MathUtil.isHovered(mouseX, mouseY, cx, cy, cw, headerH)) {
                if (click.button() == 0) {
                    col.dragging = true;
                    col.dragX = mouseX - col.x;
                    col.dragY = mouseY - col.y;
                    columns.remove(i);
                    columns.add(col);
                    return true;
                } else if (click.button() == 1) {
                    col.collapsed = !col.collapsed;
                    GuiSounds.expand(!col.collapsed);
                    return true;
                }
            }

            if (col.collapsed) continue;

            float bodyY = cy + headerH + 2 * S;
            List<Module> list = new ArrayList<>();
            for (Module m : Client.get().moduleManager().values()) {
                if (m.getCategory() == col.category) list.add(m);
            }

            float contentH = 0F;
            for (Module m : list) {
                contentH += 17 * S;
                fun.newrar.utils.animation.satoshi.Animation expAnim = moduleExpandAnims.computeIfAbsent(m, k -> new EaseInOutQuad(220, 1, Direction.BACKWARDS));
                float exp = expAnim.getOutput();
                if (exp > 0.001F) contentH += getSettingsHeight(m) * exp;
            }
            float maxViewH = (mc.getWindow().getScaledHeight() / scaleFix) - cy - headerH - 12 * S;
            float viewH = Math.min(contentH, Math.max(30 * S, maxViewH));

            if (!MathUtil.isHovered(mouseX, mouseY, cx, bodyY, cw, viewH)) continue;

            float modY = bodyY + 3 * S - col.scroll;
            for (Module m : list) {
                if (MathUtil.isHovered(mouseX, mouseY, cx + 3 * S, modY, cw - 6 * S, 15 * S)) {
                    if (click.button() == 0) {
                        m.toggle();
                        GuiSounds.toggle(m.isEnabled());
                        return true;
                    } else if (click.button() == 1) {
                        if (expandedModules.contains(m)) expandedModules.remove(m);
                        else expandedModules.add(m);
                        GuiSounds.expand(expandedModules.contains(m));
                        return true;
                    } else if (click.button() == 2) {
                        bindingModule = m;
                        GuiSounds.editStart();
                        return true;
                    }
                }

                modY += 17 * S;

                fun.newrar.utils.animation.satoshi.Animation expAnim = moduleExpandAnims.computeIfAbsent(m, k -> new EaseInOutQuad(220, 1, Direction.BACKWARDS));
                float exp = expAnim.getOutput();

                if (exp > 0.001F) {
                    float sx = cx + 7 * S;
                    float sw = cw - 14 * S;
                    float curY = modY + 3 * S;

                    for (Setting<?> setting : m.getSettings()) {
                        if (!setting.getVisible().get()) continue;

                        if (setting instanceof BooleanSetting s) {
                            if (MathUtil.isHovered(mouseX, mouseY, sx, curY, sw, 13 * S) && click.button() == 0) {
                                s.set(!s.getValue());
                                GuiSounds.toggle(s.getValue());
                                return true;
                            }
                            curY += 14 * S;
                        } else if (setting instanceof SliderSetting s) {
                            float trackX = sx + 2 * S;
                            float trackY = curY + 10F * S;
                            float trackW = sw - 4 * S;
                            if (MathUtil.isHovered(mouseX, mouseY, trackX - 2 * S, trackY - 3 * S, trackW + 4 * S, 10 * S) && click.button() == 0) {
                                draggingSlider = s;
                                float np = MathUtil.clamp((mouseX - trackX) / trackW, 0F, 1F);
                                float newVal = MathUtil.clamp(MathUtil.round(s.min + (s.max - s.min) * np, s.increment), s.min, s.max);
                                s.set(newVal);
                                GuiSounds.sliderTick(np);
                                return true;
                            }
                            curY += 20 * S;
                        } else if (setting instanceof ModeSetting s) {
                            curY += 12 * S;
                            float px = 0;
                            float py = 0;
                            float chipMaxW = sw - 4 * S;
                            for (String val : s.values) {
                                float tw = Fonts.sf_regular.getWidth(val, 5F * S) + 6 * S;
                                if (px + tw > chipMaxW && px > 0) { px = 0; py += 11 * S; }
                                float chipX = sx + 2 * S + px;
                                float chipY = curY + py;
                                if (MathUtil.isHovered(mouseX, mouseY, chipX, chipY, tw, 9 * S) && click.button() == 0) {
                                    s.set(val);
                                    GuiSounds.chip(0, 1);
                                    return true;
                                }
                                px += tw + 2 * S;
                            }
                            curY += py + 12 * S;
                        } else if (setting instanceof MultiBooleanSetting s) {
                            curY += 12 * S;
                            float px = 0;
                            float py = 0;
                            float chipMaxW = sw - 4 * S;
                            for (BooleanSetting b : s.getValues()) {
                                float tw = Fonts.sf_regular.getWidth(b.getName(), 5F * S) + 6 * S;
                                if (px + tw > chipMaxW && px > 0) { px = 0; py += 11 * S; }
                                float chipX = sx + 2 * S + px;
                                float chipY = curY + py;
                                if (MathUtil.isHovered(mouseX, mouseY, chipX, chipY, tw, 9 * S) && click.button() == 0) {
                                    b.set(!b.getValue());
                                    GuiSounds.chipMulti(b.getValue());
                                    return true;
                                }
                                px += tw + 2 * S;
                            }
                            curY += py + 12 * S;
                        } else if (setting instanceof ColorSetting s) {
                            float previewX = sx + sw - 12 * S;
                            float previewY = curY + 3F * S;
                            if (MathUtil.isHovered(mouseX, mouseY, previewX, previewY, 8 * S, 8 * S) && (click.button() == 0 || click.button() == 1)) {
                                s.pickerOpen = !s.pickerOpen;
                                GuiSounds.button();
                                return true;
                            }

                            curY += 14 * S;

                            if (s.pickerOpen) {
                                float bx = sx + 2 * S;
                                float bw = sw - 4 * S;
                                for (int bar = 0; bar < 3; bar++) {
                                    float by = curY + bar * 11 * S;
                                    if (MathUtil.isHovered(mouseX, mouseY, bx, by - 2 * S, bw, 8 * S) && click.button() == 0) {
                                        draggingColor = s;
                                        draggingColorBar = bar;
                                        return true;
                                    }
                                }
                                curY += 36 * S;
                            }
                        } else if (setting instanceof BindSetting s) {
                            if (MathUtil.isHovered(mouseX, mouseY, sx, curY, sw, 15 * S) && click.button() == 0) {
                                activeBind = s;
                                GuiSounds.editStart();
                                return true;
                            }
                            curY += 15 * S;
                        } else if (setting instanceof StringSetting s) {
                            if (MathUtil.isHovered(mouseX, mouseY, sx, curY, sw, 15 * S) && click.button() == 0) {
                                activeString = s;
                                stringBuffer = s.getValue();
                                textActive = true;
                                GuiSounds.editStart();
                                return true;
                            }
                            curY += 15 * S;
                        } else if (setting instanceof ButtonSetting s) {
                            float bx = sx + 2 * S;
                            float by = curY + 1.5F * S;
                            float bw = sw - 4 * S;
                            float bh = 11 * S;
                            if (MathUtil.isHovered(mouseX, mouseY, bx, by, bw, bh) && click.button() == 0) {
                                s.press();
                                GuiSounds.button();
                                return true;
                            }
                            curY += 15 * S;
                        } else if (setting instanceof DelimiterSetting) {
                            curY += 11 * S;
                        }
                    }

                    modY += getSettingsHeight(m) * exp;
                }
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        OverlayEditor editor = OverlayEditors.active();
        if (editor != null) { return editor.mouseDragged((float) (deltaX / scaleFix), (float) (deltaY / scaleFix)); }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        OverlayEditor editor = OverlayEditors.active();
        if (editor != null) { return editor.mouseReleased(click.button()); }

        for (Column col : columns) col.dragging = false;

        if (draggingSlider != null || draggingColor != null) GuiSounds.sliderRelease();
        draggingSlider = null;
        draggingColor = null;
        draggingColorBar = -1;

        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseXRaw, double mouseYRaw, double horizontalAmount, double verticalAmount) {
        float mouseX = (float) (mouseXRaw / scaleFix);
        float mouseY = (float) (mouseYRaw / scaleFix);

        OverlayEditor editor = OverlayEditors.active();
        if (editor != null) { return editor.mouseScrolled(mouseX, mouseY, verticalAmount); }

        float headerH = 21 * S;
        for (Column col : columns) {
            float cx = col.x;
            float cy = col.y;
            float cw = col.width * S;
            float maxViewH = (mc.getWindow().getScaledHeight() / scaleFix) - cy - headerH - 12 * S;

            if (MathUtil.isHovered(mouseX, mouseY, cx, cy, cw, maxViewH + headerH)) {
                float before = col.scrollTarget;
                col.scrollTarget = MathUtil.clamp((float) (col.scrollTarget - verticalAmount * 22 * S), 0F, col.maxScroll);
                if (col.scrollTarget != before) GuiSounds.scroll();
                return true;
            }
        }

        return super.mouseScrolled(mouseXRaw, mouseYRaw, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

        OverlayEditor editor = OverlayEditors.active();
        if (editor != null) {
            if (key == 256) editor.saveAndExit();
            return true;
        }

        if (bindingModule != null) {
            boolean reset = key == 256 || key == 261;
            bindingModule.setKey(reset ? -1 : key);
            bindingModule = null;
            if (reset) GuiSounds.bindReset(); else GuiSounds.bindSet();
            return true;
        }

        if (activeBind != null) {
            boolean reset = key == 256 || key == 261;
            activeBind.set(reset ? -1 : key);
            activeBind = null;
            if (reset) GuiSounds.bindReset(); else GuiSounds.bindSet();
            return true;
        }

        if (activeString != null) {
            if (key == 257 || key == 335) {
                activeString.set(stringBuffer);
                activeString = null;
                textActive = false;
                GuiSounds.editCommit();
            } else if (key == 256) {
                activeString = null;
                textActive = false;
                GuiSounds.editCancel();
            } else if (key == 259 && !stringBuffer.isEmpty()) {
                stringBuffer = stringBuffer.substring(0, stringBuffer.length() - 1);
                GuiSounds.erase();
            }
            return true;
        }

        int toggle = toggleKey();
        if (toggle != -1 && key == toggle) {
            if (toggleArmed) startExit();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (activeString != null) {
            if (input.isValidChar()) {
                String str = input.asString();
                if (!activeString.isOnlyNumber() || str.matches("[0-9.,-]+")) {
                    stringBuffer += str;
                    GuiSounds.type();
                }
            }
            return true;
        }
        return super.charTyped(input);
    }

    public void openHandsEditor() { beforeEditorOpen(); handsEditor.open(); GuiSounds.editor(); }
    public void openCrosshairEditor() { beforeEditorOpen(); CrosshairEditor.getInstance().open(); GuiSounds.editor(); }
    public void openPreviewEditor(Module target) { beforeEditorOpen(); PreviewEditor.getInstance().open(target); GuiSounds.editor(); }

    private void beforeEditorOpen() {
        exit = false;
        glomalAnim.run(1, 0.2F, Easings.QUAD_OUT);
    }

    @Override
    public void removed() {
        OverlayEditors.closeAll();
        textActive = false;
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        OverlayEditor editor = OverlayEditors.active();
        if (editor != null) {
            editor.saveAndExit();
            return false;
        }
        startExit();
        return false;
    }
}

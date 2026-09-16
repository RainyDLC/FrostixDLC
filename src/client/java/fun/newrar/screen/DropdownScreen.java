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
import fun.newrar.utils.other.SoundUtil;
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
        public float height;
        public boolean dragging;
        public float dragX;
        public float dragY;
        public float scroll = 0F;
        public float scrollTarget = 0F;
        public float maxScroll = 0F;
        public Module errorModule = null;
        public long errorTime = 0L;

        public Column(Category category, float x, float y, float width, float height) {
            this.category = category;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private static final Category[] ORDERED_CATEGORIES = {
            Category.COMBAT,
            Category.MOVEMENT,
            Category.RENDER,
            Category.PLAYER,
            Category.OTHER
    };

    private final List<Column> columns = new ArrayList<>();
    private final Set<Module> expandedModules = new HashSet<>();
    private final Map<Module, fun.newrar.utils.animation.satoshi.Animation> moduleExpandAnims = new IdentityHashMap<>();
    private final Map<Module, fun.newrar.utils.animation.satoshi.Animation> moduleToggleAnims = new IdentityHashMap<>();
    private final Map<String, fun.newrar.utils.animation.satoshi.Animation> chipAnims = new HashMap<>();

    private fun.newrar.utils.animation.satoshi.Animation chipAnim(String key) {
        return chipAnims.computeIfAbsent(key, k -> {
            EaseInOutQuad a = new EaseInOutQuad(200, 1, Direction.BACKWARDS);
            a.timerUtil.setTime(0);
            return a;
        });
    }

    private fun.newrar.utils.animation.satoshi.Animation getExpandAnim(Module m) {
        return moduleExpandAnims.computeIfAbsent(m, k -> {
            boolean exp = expandedModules.contains(m);
            EaseInOutQuad a = new EaseInOutQuad(250, 1, exp ? Direction.FORWARDS : Direction.BACKWARDS);
            a.timerUtil.setTime(0);
            return a;
        });
    }

    private SliderSetting draggingSlider;
    private ColorSetting draggingColor;
    private int draggingColorBar = -1;

    private StringSetting activeString;
    private String stringBuffer = "";

    private BindSetting activeBind;
    private Module bindingModule;

    private boolean searchFocused = false;
    private String searchQuery = "";

    private int toggleKey() {
        ClickGui gui = Client.get().moduleManager().get(ClickGui.class);
        return gui == null ? -1 : gui.getKey();
    }

    private void armToggleKey() {
        if (toggleArmed) return;
        int key = toggleKey();
        if (key == -1 || !InputUtil.isKeyPressed(mc.getWindow(), key)) toggleArmed = true;
    }

    private String getCategoryDisplayName(Category c) {
        if (c == Category.RENDER) return "Visuals";
        if (c == Category.OTHER) return "Other";
        return c.getName();
    }

    private void initColumns() {
        if (!columns.isEmpty()) return;
        float colW = 118F;
        float colH = 240F;
        float gap = 8F;
        float totalW = ORDERED_CATEGORIES.length * colW + (ORDERED_CATEGORIES.length - 1) * gap;
        float screenW = mc.getWindow().getScaledWidth() * 2F / (float) mc.getWindow().getScaleFactor();
        float screenH = mc.getWindow().getScaledHeight() * 2F / (float) mc.getWindow().getScaleFactor();
        float startX = Math.max(10F, (screenW - totalW) / 2F);
        float startY = Math.max(26F, (screenH - colH - 32F) / 2F);

        for (int i = 0; i < ORDERED_CATEGORIES.length; i++) {
            columns.add(new Column(ORDERED_CATEGORIES[i], startX + i * (colW + gap), startY, colW, colH));
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
        searchFocused = false;
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

    private boolean hasVisibleSettings(Module m) {
        if (m == null || m.getSettings() == null || m.getSettings().isEmpty()) return false;
        for (Setting<?> s : m.getSettings()) {
            if (s instanceof DelimiterSetting) continue;
            if (s.getVisible() == null || s.getVisible().get()) return true;
        }
        return false;
    }

    private float getSettingsHeight(Module m) {
        if (m == null || m.getSettings() == null) return 0F;
        float h = 4F * S;
        for (Setting<?> setting : m.getSettings()) {
            if (setting.getVisible() != null && !setting.getVisible().get()) continue;
            if (setting instanceof ModeSetting ms) {
                h += 13F * S + ms.values.size() * 13.5F * S + 4F * S;
            } else if (setting instanceof BooleanSetting) {
                h += 17F * S;
            } else if (setting instanceof SliderSetting) {
                h += 22F * S;
            } else if (setting instanceof ColorSetting) {
                h += 17F * S;
            } else if (setting instanceof BindSetting) {
                h += 17F * S;
            } else if (setting instanceof MultiBooleanSetting mbs) {
                h += 13F * S + mbs.getValues().size() * 13.5F * S + 4F * S;
            } else if (setting instanceof StringSetting) {
                h += 27F * S;
            } else if (setting instanceof ButtonSetting) {
                h += 18F * S;
            } else if (setting instanceof DelimiterSetting) {
                h += 9F * S;
            }
        }
        return h;
    }

    private List<Module> getCategoryModules(Category cat) {
        List<Module> list = new ArrayList<>();
        String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase();
        for (Module m : Client.get().moduleManager().values()) {
            if (m.getCategory() == cat) {
                if (q.isEmpty() || m.getName().toLowerCase().contains(q)) {
                    list.add(m);
                }
            }
        }
        return list;
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

        float headerH = 24F * S;
        float colCardRadius = 10F * S;
        float bottomColsY = 0F;

        for (int i = 0; i < columns.size(); i++) {
            Column col = columns.get(i);
            if (col.dragging) {
                col.x = (float) lastMouseX - col.dragX;
                col.y = (float) lastMouseY - col.dragY;
                col.x = MathUtil.clamp(col.x, 2 * S, screenWidth - col.width * S - 2 * S);
                col.y = MathUtil.clamp(col.y, 2 * S, screenHeight - col.height * S - 2 * S);
            }

            float colDelay = i * 0.04F;
            float colProgress = MathHelper.clamp((globalAnim - colDelay) / Math.max(0.01F, 1.0F - colDelay), 0F, 1F);
            float easeCol = (float) Easings.BACK_OUT.ease(colProgress);
            float staggerY = (1.0F - easeCol) * 28F * S;
            float cy = col.y - (exit ? (1.0F - globalAnim) * 25F * S : staggerY);

            float cx = col.x;
            float cw = col.width * S;
            float ch = col.height * S;
            bottomColsY = Math.max(bottomColsY, cy + ch);

            RenderUtil.Blur.blur(cx, cy, cw, ch, globalAnim * colProgress, colCardRadius, ColorUtil.getColor(0, 0));
            RenderUtil.Render2D.rect(cx, cy, cw, ch, ColorUtil.getColor(18, 20, 24, 0.78F * globalAnim * colProgress), colCardRadius);
            RenderUtil.Render2D.outline(cx, cy, cw, ch, 0.5F * S, ColorUtil.getColor(255, 255, 255, 0.08F * globalAnim * colProgress), colCardRadius);

            String catDisplayName = getCategoryDisplayName(col.category);
            bold.draw(catDisplayName, cx + 11F * S, cy + 8F * S, 7.5F * S, ColorUtil.getColor(255, 255, 255, 0.95F * globalAnim));
            iconFont.drawCentered(col.category.getIcon(), cx + cw - 14F * S, cy + 12F * S, 7.5F * S, ColorUtil.getColor(210, 215, 225, 0.75F * globalAnim));

            float bodyY = cy + headerH;
            float bodyH = ch - headerH - 8F * S;

            col.scroll += (col.scrollTarget - col.scroll) * 0.25F;

            List<Module> list = getCategoryModules(col.category);

            float rowH = 16.5F * S;
            float totalListH = 0F;
            for (Module m : list) {
                totalListH += rowH;
                fun.newrar.utils.animation.satoshi.Animation expAnim = getExpandAnim(m);
                expAnim.setDirection(expandedModules.contains(m) ? Direction.FORWARDS : Direction.BACKWARDS);
                float exp = expAnim.getOutput();
                if (exp > 0.001F) {
                    totalListH += getSettingsHeight(m) * exp;
                }
            }

            col.maxScroll = Math.max(0F, totalListH - bodyH + 10F * S);
            col.scrollTarget = MathUtil.clamp(col.scrollTarget, 0F, col.maxScroll);

            Scissor.enable(cx, bodyY, cw, bodyH, 2);

            float modY = bodyY + 2F * S - col.scroll;
            for (Module m : list) {
                boolean onScreen = modY + rowH >= bodyY && modY <= bodyY + bodyH;
                boolean isHovered = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, cx + 4F * S, modY, cw - 8F * S, rowH);

                fun.newrar.utils.animation.satoshi.Animation togAnim = moduleToggleAnims.computeIfAbsent(m, k -> {
                    EaseInOutQuad a = new EaseInOutQuad(200, 1, m.isEnabled() ? Direction.FORWARDS : Direction.BACKWARDS);
                    a.timerUtil.setTime(0);
                    return a;
                });
                togAnim.setDirection(m.isEnabled() ? Direction.FORWARDS : Direction.BACKWARDS);
                float tog = togAnim.getOutput();

                fun.newrar.utils.animation.satoshi.Animation expAnim = getExpandAnim(m);
                expAnim.setDirection(expandedModules.contains(m) ? Direction.FORWARDS : Direction.BACKWARDS);
                float exp = expAnim.getOutput();

                float flash = 0F;
                if (col.errorModule == m) {
                    long elapsed = System.currentTimeMillis() - col.errorTime;
                    if (elapsed < 420) {
                        flash = 1F - (float) elapsed / 420F;
                    } else {
                        col.errorModule = null;
                    }
                }

                if (onScreen) {
                    fun.newrar.utils.animation.satoshi.Animation hovAnim = chipAnim("modhov:" + m.getName());
                    hovAnim.setDirection(isHovered ? Direction.FORWARDS : Direction.BACKWARDS);
                    float hov = hovAnim.getOutput();

                    if (hov > 0.01F && flash <= 0.01F) {
                        RenderUtil.Render2D.rect(cx + 4F * S, modY, cw - 8F * S, rowH - 1.5F * S, ColorUtil.getColor(255, 255, 255, 0.06F * globalAnim * hov), 3.5F * S);
                    }

                    if (flash > 0.01F) {
                        RenderUtil.Render2D.rect(cx + 4F * S, modY, cw - 8F * S, rowH - 1.5F * S, ColorUtil.getColor(235, 45, 45, 0.28F * flash * globalAnim), 3.5F * S);
                        RenderUtil.Render2D.outline(cx + 4F * S, modY, cw - 8F * S, rowH - 1.5F * S, 0.5F * S, ColorUtil.getColor(255, 60, 60, 0.65F * flash * globalAnim), 3.5F * S);
                    }

                    if (tog > 0.01F) {
                        RenderUtil.Render2D.rect(cx + 4F * S, modY + 2.5F * S, 1.5F * S, (rowH - 6.5F * S) * tog, ColorUtil.replAlpha(ColorUtil.client(), 0.85F * globalAnim * tog), 1F);
                    }

                    int disCol = ColorUtil.getColor(170, 175, 185, 0.6F * globalAnim);
                    int enCol = ColorUtil.getColor(255, 255, 255, 0.95F * globalAnim);
                    int nameCol = ColorUtil.overCol(disCol, enCol, tog);

                    if (flash > 0.01F) {
                        nameCol = ColorUtil.overCol(nameCol, ColorUtil.getColor(255, 80, 80, globalAnim), flash);
                    }

                    regular.draw(m.getName(), cx + (tog > 0.01F ? 11F * S : 10F * S), modY + 4F * S, 6.5F * S, nameCol);
                }

                modY += rowH;

                if (exp > 0.001F) {
                    float setH = getSettingsHeight(m);
                    float animH = setH * exp;

                    Scissor.enable(cx, modY, cw, animH, 2);

                    RenderUtil.Render2D.rect(cx + 6F * S, modY, cw - 12F * S, animH, ColorUtil.getColor(12, 14, 18, 0.45F * globalAnim * exp), 3.5F * S);

                    float currentY = modY + 2F * S;

                    for (Setting<?> setting : m.getSettings()) {
                        if (setting.getVisible() != null && !setting.getVisible().get()) continue;

                        if (setting instanceof ModeSetting ms) {
                            regular.draw(ms.getName(), cx + 12F * S, currentY + 2F * S, 6F * S, ColorUtil.getColor(155, 160, 170, 0.65F * globalAnim * exp));
                            currentY += 13F * S;

                            for (String val : ms.values) {
                                boolean isSel = val.equalsIgnoreCase(ms.getValue());
                                boolean hovOpt = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, cx + 8F * S, currentY - 1F * S, cw - 16F * S, 12.5F * S);

                                if (hovOpt) {
                                    RenderUtil.Render2D.rect(cx + 8F * S, currentY - 1F * S, cw - 16F * S, 12.5F * S, ColorUtil.getColor(255, 255, 255, 0.04F * globalAnim * exp), 3F * S);
                                }

                                int optColor = isSel ? ColorUtil.getColor(255, 255, 255, 0.95F * globalAnim * exp) : ColorUtil.getColor(165, 170, 180, 0.6F * globalAnim * exp);
                                regular.draw(val, cx + 14F * S, currentY + 2.5F * S, 6F * S, optColor);
                                currentY += 13.5F * S;
                            }
                            currentY += 4F * S;
                        } else if (setting instanceof BooleanSetting bs) {
                            boolean hovBool = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, cx + 8F * S, currentY, cw - 16F * S, 16F * S);
                            if (hovBool) {
                                RenderUtil.Render2D.rect(cx + 8F * S, currentY, cw - 16F * S, 16F * S, ColorUtil.getColor(255, 255, 255, 0.04F * globalAnim * exp), 3F * S);
                            }

                            regular.draw(bs.getName(), cx + 12F * S, currentY + 4.5F * S, 6.5F * S, ColorUtil.getColor(235, 235, 240, 0.9F * globalAnim * exp));

                            fun.newrar.utils.animation.satoshi.Animation switchAnim = chipAnim("bool:" + m.getName() + ":" + bs.getName());
                            switchAnim.setDirection(bs.getValue() ? Direction.FORWARDS : Direction.BACKWARDS);
                            float sp = switchAnim.getOutput();

                            float trackW = 17F * S;
                            float trackH = 9F * S;
                            float trackX = cx + cw - 29F * S;
                            float trackY = currentY + 3.5F * S;

                            int offTrack = ColorUtil.getColor(36, 38, 46, 0.8F * globalAnim * exp);
                            int onTrack = ColorUtil.getColor(145, 60, 245, 0.95F * globalAnim * exp);
                            RenderUtil.Render2D.rect(trackX, trackY, trackW, trackH, ColorUtil.overCol(offTrack, onTrack, sp), 4.5F * S);

                            float knobR = 3.2F * S;
                            float knobX = trackX + 4.5F * S + (trackW - 9F * S) * sp;
                            float knobY = trackY + 4.5F * S;
                            RenderUtil.Render2D.rect(knobX - knobR, knobY - knobR, knobR * 2F, knobR * 2F, ColorUtil.getColor(255, 255, 255, 0.98F * globalAnim * exp), knobR);

                            currentY += 17F * S;
                        } else if (setting instanceof SliderSetting ss) {
                            boolean hovSlide = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, cx + 8F * S, currentY, cw - 16F * S, 21F * S);
                            if (hovSlide) {
                                RenderUtil.Render2D.rect(cx + 8F * S, currentY, cw - 16F * S, 21F * S, ColorUtil.getColor(255, 255, 255, 0.04F * globalAnim * exp), 3F * S);
                            }

                            regular.draw(ss.getName(), cx + 12F * S, currentY + 2F * S, 6F * S, ColorUtil.getColor(235, 235, 240, 0.9F * globalAnim * exp));
                            String valStr = String.format(Locale.US, ss.increment >= 1F ? "%.0f" : "%.1f", ss.getValue());
                            regular.draw(valStr, cx + cw - 14F * S - regular.getWidth(valStr, 6F * S), currentY + 2F * S, 6F * S, ColorUtil.getColor(160, 165, 175, 0.7F * globalAnim * exp));

                            float trackX = cx + 12F * S;
                            float trackY = currentY + 13F * S;
                            float trackW = cw - 24F * S;
                            float trackH = 3F * S;

                            float pct = MathHelper.clamp((ss.getValue() - ss.min) / (ss.max - ss.min), 0F, 1F);
                            RenderUtil.Render2D.rect(trackX, trackY, trackW, trackH, ColorUtil.getColor(36, 38, 46, 0.75F * globalAnim * exp), 1.5F * S);
                            if (pct > 0.01F) {
                                RenderUtil.Render2D.rect(trackX, trackY, trackW * pct, trackH, ColorUtil.getColor(145, 60, 245, 0.95F * globalAnim * exp), 1.5F * S);
                            }
                            float thumbR = 2.5F * S;
                            RenderUtil.Render2D.rect(trackX + trackW * pct - thumbR, trackY + trackH / 2F - thumbR, thumbR * 2F, thumbR * 2F, ColorUtil.getColor(255, 255, 255, 0.98F * globalAnim * exp), thumbR);

                            if (draggingSlider == ss) {
                                float newPct = MathHelper.clamp(((float) lastMouseX - trackX) / trackW, 0F, 1F);
                                float rawVal = ss.min + newPct * (ss.max - ss.min);
                                float stepped = Math.round(rawVal / ss.increment) * ss.increment;
                                ss.set(MathHelper.clamp(stepped, ss.min, ss.max));
                                GuiSounds.sliderTick(newPct);
                            }

                            currentY += 22F * S;
                        } else if (setting instanceof ColorSetting cs) {
                            boolean hovCol = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, cx + 8F * S, currentY, cw - 16F * S, 16F * S);
                            if (hovCol) {
                                RenderUtil.Render2D.rect(cx + 8F * S, currentY, cw - 16F * S, 16F * S, ColorUtil.getColor(255, 255, 255, 0.04F * globalAnim * exp), 3F * S);
                            }

                            regular.draw(cs.getName(), cx + 12F * S, currentY + 4F * S, 6F * S, ColorUtil.getColor(235, 235, 240, 0.9F * globalAnim * exp));
                            RenderUtil.Render2D.rect(cx + cw - 26F * S, currentY + 3.5F * S, 14F * S, 8.5F * S, ColorUtil.multAlpha(cs.getValue(), globalAnim * exp), 2.5F * S);
                            RenderUtil.Render2D.outline(cx + cw - 26F * S, currentY + 3.5F * S, 14F * S, 8.5F * S, 0.5F * S, ColorUtil.getColor(255, 255, 255, 0.25F * globalAnim * exp), 2.5F * S);

                            currentY += 17F * S;
                        } else if (setting instanceof BindSetting bs) {
                            boolean hovBind = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, cx + 8F * S, currentY, cw - 16F * S, 16F * S);
                            if (hovBind) {
                                RenderUtil.Render2D.rect(cx + 8F * S, currentY, cw - 16F * S, 16F * S, ColorUtil.getColor(255, 255, 255, 0.04F * globalAnim * exp), 3F * S);
                            }

                            regular.draw(bs.getName(), cx + 12F * S, currentY + 4F * S, 6F * S, ColorUtil.getColor(235, 235, 240, 0.9F * globalAnim * exp));
                            String keyText = activeBind == bs ? "..." : (bs.getValue() == -1 ? "NONE" : Keyboard.keyName(bs.getValue()));
                            float kw = regular.getWidth(keyText, 5.5F * S) + 6F * S;
                            RenderUtil.Render2D.rect(cx + cw - 12F * S - kw, currentY + 3F * S, kw, 9F * S, ColorUtil.getColor(36, 38, 46, 0.8F * globalAnim * exp), 2F * S);
                            regular.draw(keyText, cx + cw - 12F * S - kw + 3F * S, currentY + 4.5F * S, 5.5F * S, ColorUtil.getColor(220, 220, 225, 0.85F * globalAnim * exp));

                            currentY += 17F * S;
                        } else if (setting instanceof MultiBooleanSetting mbs) {
                            regular.draw(mbs.getName(), cx + 12F * S, currentY + 2F * S, 6F * S, ColorUtil.getColor(155, 160, 170, 0.65F * globalAnim * exp));
                            currentY += 13F * S;

                            for (BooleanSetting sub : mbs.getValues()) {
                                boolean isSubSel = sub.getValue();
                                boolean hovSub = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, cx + 8F * S, currentY - 1F * S, cw - 16F * S, 12.5F * S);

                                if (hovSub) {
                                    RenderUtil.Render2D.rect(cx + 8F * S, currentY - 1F * S, cw - 16F * S, 12.5F * S, ColorUtil.getColor(255, 255, 255, 0.04F * globalAnim * exp), 3F * S);
                                }

                                int optColor = isSubSel ? ColorUtil.getColor(255, 255, 255, 0.95F * globalAnim * exp) : ColorUtil.getColor(165, 170, 180, 0.6F * globalAnim * exp);
                                regular.draw(sub.getName(), cx + 14F * S, currentY + 2.5F * S, 6F * S, optColor);
                                currentY += 13.5F * S;
                            }
                            currentY += 4F * S;
                        } else if (setting instanceof StringSetting strSet) {
                            regular.draw(strSet.getName(), cx + 12F * S, currentY + 2F * S, 6F * S, ColorUtil.getColor(155, 160, 170, 0.65F * globalAnim * exp));
                            currentY += 11F * S;

                            String text = activeString == strSet ? stringBuffer : strSet.getValue();
                            RenderUtil.Render2D.rect(cx + 10F * S, currentY, cw - 20F * S, 12F * S, ColorUtil.getColor(30, 32, 38, 0.75F * globalAnim * exp), 2.5F * S);
                            regular.draw(text.isEmpty() ? "..." : text, cx + 14F * S, currentY + 3F * S, 5.5F * S, ColorUtil.getColor(220, 220, 225, 0.85F * globalAnim * exp));

                            currentY += 16F * S;
                        } else if (setting instanceof ButtonSetting bs) {
                            float bx = cx + 10F * S;
                            float by = currentY + 1.5F * S;
                            float bw = cw - 20F * S;
                            float bh = 14F * S;

                            boolean hovBtn = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, bx, by, bw, bh);
                            bs.pressAnim.update();
                            float press = bs.pressAnim.get();

                            int btnBg = ColorUtil.overCol(ColorUtil.getColor(32, 35, 42, 0.85F * globalAnim * exp), ColorUtil.getColor(145, 60, 245, 0.4F * globalAnim * exp), Math.max(hovBtn ? 0.35F : 0F, press));
                            RenderUtil.Render2D.rect(bx, by, bw, bh, btnBg, 3.5F * S);
                            int borderCol = ColorUtil.overCol(ColorUtil.getColor(255, 255, 255, 0.08F * globalAnim * exp), ColorUtil.getColor(145, 60, 245, 0.9F * globalAnim * exp), Math.max(hovBtn ? 0.6F : 0F, press));
                            RenderUtil.Render2D.outline(bx, by, bw, bh, 0.5F * S, borderCol, 3.5F * S);
                            int txtCol = hovBtn ? ColorUtil.getColor(255, 255, 255, 0.98F * globalAnim * exp) : ColorUtil.getColor(220, 225, 235, 0.85F * globalAnim * exp);
                            regular.drawCentered(bs.getName(), bx + bw / 2F, by + 3.5F * S, 6F * S, txtCol);

                            currentY += 18F * S;
                        } else if (setting instanceof DelimiterSetting) {
                            RenderUtil.Render2D.rect(cx + 12F * S, currentY + 4F * S, cw - 24F * S, 0.5F * S, ColorUtil.getColor(255, 255, 255, 0.08F * globalAnim * exp));
                            currentY += 9F * S;
                        }
                    }

                    Scissor.disable();

                    modY += animH;
                }
            }

            Scissor.disable();
            Scissor.reset();
        }

        float searchW = 160F * S;
        float searchH = 18F * S;
        float searchX = (screenWidth - searchW) / 2F;
        float searchY = Math.min(screenHeight - 24F * S, bottomColsY + 10F * S);

        RenderUtil.Render2D.rect(searchX, searchY, searchW, searchH, ColorUtil.getColor(18, 20, 24, 0.85F * globalAnim), 5F * S);
        int outlineCol = searchFocused ? ColorUtil.getColor(145, 60, 245, 0.9F * globalAnim) : ColorUtil.getColor(255, 255, 255, 0.08F * globalAnim);
        RenderUtil.Render2D.outline(searchX, searchY, searchW, searchH, 0.5F * S, outlineCol, 5F * S);

        if (searchQuery.isEmpty()) {
            regular.drawCentered("Поиск...", searchX + searchW / 2F, searchY + 5.5F * S, 6F * S, ColorUtil.getColor(150, 155, 165, 0.5F * globalAnim));
        } else {
            regular.draw(searchQuery, searchX + 8F * S, searchY + 5.5F * S, 6F * S, ColorUtil.getColor(255, 255, 255, 0.95F * globalAnim));
        }

        if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            float textOffset = searchQuery.isEmpty() ? (searchW / 2F + regular.getWidth("Поиск...", 6F * S) / 2F + 2F * S) : (8F * S + regular.getWidth(searchQuery, 6F * S) + 1.5F * S);
            RenderUtil.Render2D.rect(searchX + textOffset, searchY + 5F * S, 0.6F * S, 8F * S, ColorUtil.getColor(255, 255, 255, 0.9F * globalAnim), 0.3F * S);
        }

        Render2D.endOverlay();
        if (context != null) context.getMatrices().popMatrix();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        float mouseX = (float) (click.x() / scaleFix);
        float mouseY = (float) (click.y() / scaleFix);
        int button = click.button();

        OverlayEditor editor = OverlayEditors.active();
        if (editor != null) {
            return editor.mouseClicked(mouseX, mouseY, button);
        }

        if (bindingModule != null) {
            bindingModule.setKey(button == 0 ? -1 : -100 - button);
            bindingModule = null;
            GuiSounds.bindSet();
            return true;
        }

        if (activeBind != null) {
            activeBind.set(button == 0 ? -1 : -100 - button);
            activeBind = null;
            GuiSounds.bindSet();
            return true;
        }

        int screenWidth = (int) (mc.getWindow().getScaledWidth() / scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);
        float searchW = 160F * S;
        float searchH = 18F * S;
        float searchX = (screenWidth - searchW) / 2F;

        float bottomColsY = 0F;
        for (Column col : columns) {
            bottomColsY = Math.max(bottomColsY, col.y + col.height * S);
        }
        float searchY = Math.min(screenHeight - 24F * S, bottomColsY + 10F * S);

        boolean inSearch = MathUtil.isHovered(mouseX, mouseY, searchX, searchY, searchW, searchH);
        if (inSearch && button == 0) {
            searchFocused = true;
            textActive = true;
            GuiSounds.button();
            return true;
        } else if (button == 0 && searchFocused) {
            searchFocused = false;
            textActive = false;
        }

        float headerH = 24F * S;

        for (Column col : columns) {
            float cx = col.x;
            float cy = col.y;
            float cw = col.width * S;
            float ch = col.height * S;
            float bodyY = cy + headerH;
            float bodyH = ch - headerH - 8F * S;

            boolean inHeader = MathUtil.isHovered(mouseX, mouseY, cx, cy, cw, headerH);
            boolean inBody = MathUtil.isHovered(mouseX, mouseY, cx, bodyY, cw, bodyH);

            if (inHeader && button == 0) {
                col.dragging = true;
                col.dragX = mouseX - col.x;
                col.dragY = mouseY - col.y;
                return true;
            }

            if (inBody) {
                List<Module> list = getCategoryModules(col.category);

                float rowH = 16.5F * S;
                float modY = bodyY + 2F * S - col.scroll;

                for (Module m : list) {
                    boolean isHovered = MathUtil.isHovered(mouseX, mouseY, cx + 4F * S, modY, cw - 8F * S, rowH);

                    if (isHovered) {
                        if (button == 0) {
                            m.toggle();
                            fun.newrar.utils.animation.satoshi.Animation togAnim = moduleToggleAnims.computeIfAbsent(m, k -> {
                                EaseInOutQuad a = new EaseInOutQuad(200, 1, Direction.BACKWARDS);
                                a.timerUtil.setTime(0);
                                return a;
                            });
                            togAnim.setDirection(m.isEnabled() ? Direction.FORWARDS : Direction.BACKWARDS);
                            GuiSounds.toggle(m.isEnabled());
                            return true;
                        } else if (button == 1) {
                            if (hasVisibleSettings(m)) {
                                fun.newrar.utils.animation.satoshi.Animation expAnim = getExpandAnim(m);
                                if (expandedModules.contains(m)) {
                                    expandedModules.remove(m);
                                    expAnim.setDirection(Direction.BACKWARDS);
                                    GuiSounds.expand(false);
                                } else {
                                    expandedModules.add(m);
                                    expAnim.setDirection(Direction.FORWARDS);
                                    GuiSounds.expand(true);
                                }
                            } else {
                                col.errorModule = m;
                                col.errorTime = System.currentTimeMillis();
                                GuiSounds.editCancel();
                                SoundUtil.playSound_wav("gui/gui_clear", 0.6F, 0.85F);
                            }
                            return true;
                        }
                    }

                    modY += rowH;

                    fun.newrar.utils.animation.satoshi.Animation expAnim = getExpandAnim(m);
                    float exp = expAnim.getOutput();

                    if (exp > 0.001F) {
                        float setH = getSettingsHeight(m);
                        float animH = setH * exp;
                        float currentY = modY + 2F * S;

                        boolean inSettingsBounds = mouseY >= modY && mouseY <= modY + animH && mouseY >= bodyY && mouseY <= bodyY + bodyH;

                        if (inSettingsBounds) {
                            for (Setting<?> setting : m.getSettings()) {
                            if (setting.getVisible() != null && !setting.getVisible().get()) continue;

                            if (setting instanceof ModeSetting ms) {
                                currentY += 13F * S;
                                for (String val : ms.values) {
                                    boolean hovOpt = MathUtil.isHovered(mouseX, mouseY, cx + 8F * S, currentY - 1F * S, cw - 16F * S, 12.5F * S);
                                    if (hovOpt && button == 0) {
                                        ms.set(val);
                                        GuiSounds.chip(ms.getIndex(), ms.values.size());
                                        return true;
                                    }
                                    currentY += 13.5F * S;
                                }
                                currentY += 4F * S;
                            } else if (setting instanceof BooleanSetting bs) {
                                boolean hovBool = MathUtil.isHovered(mouseX, mouseY, cx + 8F * S, currentY, cw - 16F * S, 16F * S);
                                if (hovBool && button == 0) {
                                    bs.set(!bs.getValue());
                                    GuiSounds.toggle(bs.getValue());
                                    return true;
                                }
                                currentY += 17F * S;
                            } else if (setting instanceof SliderSetting ss) {
                                boolean hovSlide = MathUtil.isHovered(mouseX, mouseY, cx + 8F * S, currentY, cw - 16F * S, 21F * S);
                                if (hovSlide && button == 0) {
                                    draggingSlider = ss;
                                    float trackX = cx + 12F * S;
                                    float trackW = cw - 24F * S;
                                    float newPct = MathHelper.clamp((mouseX - trackX) / trackW, 0F, 1F);
                                    float rawVal = ss.min + newPct * (ss.max - ss.min);
                                    float stepped = Math.round(rawVal / ss.increment) * ss.increment;
                                    ss.set(MathHelper.clamp(stepped, ss.min, ss.max));
                                    GuiSounds.sliderGrab();
                                    return true;
                                }
                                currentY += 22F * S;
                            } else if (setting instanceof ColorSetting cs) {
                                boolean hovCol = MathUtil.isHovered(mouseX, mouseY, cx + 8F * S, currentY, cw - 16F * S, 16F * S);
                                if (hovCol && button == 0) {
                                    cs.pickerOpen = !cs.pickerOpen;
                                    GuiSounds.picker(cs.pickerOpen);
                                    return true;
                                }
                                currentY += 17F * S;
                            } else if (setting instanceof BindSetting bs) {
                                boolean hovBind = MathUtil.isHovered(mouseX, mouseY, cx + 8F * S, currentY, cw - 16F * S, 16F * S);
                                if (hovBind) {
                                    if (button == 0) {
                                        activeBind = bs;
                                        GuiSounds.bindStart();
                                    } else if (button == 1) {
                                        bs.set(-1);
                                        GuiSounds.bindReset();
                                    }
                                    return true;
                                }
                                currentY += 17F * S;
                            } else if (setting instanceof MultiBooleanSetting mbs) {
                                currentY += 13F * S;
                                for (BooleanSetting sub : mbs.getValues()) {
                                    boolean hovSub = MathUtil.isHovered(mouseX, mouseY, cx + 8F * S, currentY - 1F * S, cw - 16F * S, 12.5F * S);
                                    if (hovSub && button == 0) {
                                        sub.set(!sub.getValue());
                                        GuiSounds.chipMulti(sub.getValue());
                                        return true;
                                    }
                                    currentY += 13.5F * S;
                                }
                                currentY += 4F * S;
                            } else if (setting instanceof StringSetting strSet) {
                                boolean hovStr = MathUtil.isHovered(mouseX, mouseY, cx + 10F * S, currentY + 11F * S, cw - 20F * S, 12F * S);
                                if (hovStr && button == 0) {
                                    activeString = strSet;
                                    stringBuffer = strSet.getValue();
                                    textActive = true;
                                    GuiSounds.editStart();
                                    return true;
                                }
                                currentY += 27F * S;
                            } else if (setting instanceof ButtonSetting bs) {
                                float bx = cx + 10F * S;
                                float by = currentY + 1.5F * S;
                                float bw = cw - 20F * S;
                                float bh = 14F * S;
                                boolean hovBtn = MathUtil.isHovered(mouseX, mouseY, bx, by, bw, bh);
                                if (hovBtn && button == 0) {
                                    bs.press();
                                    GuiSounds.button();
                                    return true;
                                }
                                currentY += 18F * S;
                            } else if (setting instanceof DelimiterSetting) {
                                currentY += 9F * S;
                            }
                        }
                    }

                    modY += animH;
                }
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

        for (Column col : columns) {
            float cx = col.x;
            float cy = col.y;
            float cw = col.width * S;
            float ch = col.height * S;

            if (MathUtil.isHovered(mouseX, mouseY, cx, cy, cw, ch)) {
                float before = col.scrollTarget;
                col.scrollTarget = MathUtil.clamp((float) (col.scrollTarget - verticalAmount * 20 * S), 0F, col.maxScroll);
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

        if (searchFocused) {
            if (key == 256 || key == 257 || key == 335) {
                searchFocused = false;
                textActive = false;
                return true;
            } else if (key == 259 && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                GuiSounds.erase();
                return true;
            }
        }

        if (key == 256) {
            startExit();
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

        if (searchFocused) {
            if (input.isValidChar()) {
                searchQuery += input.asString();
                GuiSounds.type();
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
        searchFocused = false;
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

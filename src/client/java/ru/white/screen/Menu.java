package ru.white.screen;

import net.minecraft.client.sound.Sound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import ru.white.Client;

import ru.white.manager.Theme;
import ru.white.module.impl.display.ClickGui;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.settings.Setting;
import ru.white.module.api.settings.impl.BindSetting;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ButtonSetting;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.MultiBooleanSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.module.api.settings.impl.StringSetting;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import ru.white.screen.editor.OverlayEditor;
import ru.white.screen.editor.OverlayEditors;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.animation.satoshi.Direction;
import ru.white.utils.animation.satoshi.EaseInOutQuad;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.colors.ColorFormatting;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.Keyboard;
import ru.white.utils.math.MathUtil;
import ru.white.utils.other.GuiMusicPlayer;
import ru.white.utils.other.GuiSounds;
import ru.white.utils.render.*;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;

public class Menu extends Screen implements IMinecraft {

    /** Общий масштаб меню: множитель для шрифтов, иконок и всех размеров. */
    public static float S = 1.0F;

    public Menu() {
        super(Text.literal("RainyDLC Menu"));
    }

    boolean exit = false;

    public Animation glomalAnim = new Animation();

    /** Сборка/распад меню из треугольных осколков. */
    private final MenuShards shards = new MenuShards();
    /** Осколки стартуют в первом кадре — там уже известен прямоугольник панели. */
    private boolean pendingAssemble = false;
    /** Панель начинает проявляться, только когда осколки почти долетели. */
    private boolean panelAnimStarted = true;
    private float panelX, panelY, panelW, panelH, panelScreenW, panelScreenH;
    private boolean panelKnown = false;
    /** Клавишу открытия ждём отпущенной, иначе она же сразу закроет меню. */
    private boolean toggleArmed = false;
    /** Пауза между «закрыть» и распадом: меню успевает замереть на месте. */
    private static final long SHATTER_HOLD_MS = 240;
    private long exitHoldStart;
    private boolean dissolveStarted = true;
    /** Панель захвачена в конце кадра — в следующем запускаем распад. */
    private boolean pendingDissolveSwap = false;

    private final HandsEditor handsEditor = HandsEditor.getInstance();

    private float scaleFix = 1F;

    double lastMouseX;
    double lastMouseY;

    public static ru.white.manager.Theme selectedTheme;
    public static ru.white.manager.Theme preSelectedTheme;
    public static ru.white.manager.Theme[] themes;
    public static ru.white.utils.animation.satoshi.Animation animation14 = new EaseInOutQuad(300, 1);
    public static ru.white.utils.animation.satoshi.Animation animCategoryReset = new EaseInOutQuad(300, 1);
    public static ru.white.utils.animation.satoshi.Animation animUserInfo = new EaseInOutQuad(300, 1);

    public static ru.white.utils.animation.satoshi.Animation animation1 = new EaseInOutQuad(300, 1);
    public static ru.white.utils.animation.satoshi.Animation animation2 = new EaseInOutQuad(300, 1);
    public static ru.white.utils.animation.satoshi.Animation animation3 = new EaseInOutQuad(300, 1);
    public static ru.white.utils.animation.satoshi.Animation animation4 = new EaseInOutQuad(300, 1);

    public Module select = null;
    public Category active = Category.COMBAT;

    private float scrollTarget = 0;
    private float scrollAnim = 0;
    private float maxScroll = 0;

    private float settingScrollTarget = 0;
    private float settingScrollAnim = 0;
    private float settingMaxScroll = 0;

    private SliderSetting draggingSlider = null;
    private BindSetting activeBind = null;

    /** Модуль, которому сейчас назначают клавишу прямо из списка. */
    private Module bindingModule = null;

    /** Кнопка «Добавить модуль» внизу списка (геометрия из последнего кадра). */
    private float[] addModuleRect = null;

    /** Кнопка удаления Lua-модуля в панели настроек + состояние подтверждения. */
    private float[] deleteRect = null;
    private Module deleteArmModule = null;
    private long deleteArmUntil = 0L;

    private StringSetting activeString = null;
    private String stringBuffer = "";

    private ColorSetting draggingColor = null;
    private int draggingColorBar = 0; // 0 - hue, 1 - saturation, 2 - brightness

    public static boolean searchActive = false;
    /** Запрос живёт между открытиями меню. */
    private static String searchQuery = "";
    private long searchTypeTime = System.currentTimeMillis();

    private final ru.white.utils.animation.satoshi.Animation animSearchFocus = new EaseInOutQuad(300, 1, Direction.BACKWARDS);
    private final ru.white.utils.animation.satoshi.Animation animSearchText = new EaseInOutQuad(300, 1, Direction.BACKWARDS);
    private final ru.white.utils.animation.satoshi.Animation animSearchEmpty = new EaseInOutQuad(300, 1, Direction.BACKWARDS);

    private static final int SEARCH_LIMIT = 24;

    private final HashMap<String, ru.white.utils.animation.satoshi.Animation> chipAnims = new HashMap<>();

    private ru.white.utils.animation.satoshi.Animation chipAnim(String key) {
        return chipAnims.computeIfAbsent(key, k -> new EaseInOutQuad(300, 1));
    }

    // ——— фоновые эффекты ———
    private final GrayscalePipeline grayscalePipeline = new GrayscalePipeline();
    private final ClickGuiDotsPipeline dotsPipeline = new ClickGuiDotsPipeline();
    private final ScanLinesPipeline scanLinesPipeline = new ScanLinesPipeline();
    private final MenuRaysPipeline raysPipeline = new MenuRaysPipeline();
    private final HalftoneDotsPipeline halftonePipeline = new HalftoneDotsPipeline();

    private boolean effect(String name) {
        ClickGui gui = Client.get().moduleManager().get(ClickGui.class);
        return gui != null && gui.effect.getValue(name);
    }

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

    private final java.util.List<GuiParticle> particles = new java.util.ArrayList<>();
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

    private final HashMap<String, Float> descHeights = new HashMap<>();

    private float descHeight(Font font, String desc) {
        return descHeights.computeIfAbsent(desc + "_" + S, d -> font.getWrappedHeight(desc, 132 * S, 6 * S));
    }

    private final HashMap<String, float[]> smoothVals = new HashMap<>();

    private float smooth(String key, float target) {
        float[] v = smoothVals.computeIfAbsent(key, k -> new float[]{target});
        v[0] += (target - v[0]) * 0.2F;
        return v[0];
    }

    private final java.util.IdentityHashMap<Setting<?>, ru.white.utils.animation.satoshi.Animation> visAnims = new java.util.IdentityHashMap<>();

    private float visAnim(Module f, Setting<?> setting) {
        ru.white.utils.animation.satoshi.Animation a = visAnims.computeIfAbsent(setting, k -> new EaseInOutQuad(300, 1));
        a.setDirection(setting.getVisible().get() ? Direction.FORWARDS : Direction.BACKWARDS);
        return a.getOutput();
    }

    private float chipsHeight(Iterable<String> values, float width) {
        Font font = Fonts.sf_regular;
        float px = 0, py = 0;
        for (String val : values) {
            float tw = font.getWidth(val, 6 * S) + 8 * S;
            if (px + tw > width && px > 0) { px = 0; py += 12 * S; }
            px += tw + 3 * S;
        }
        return py + 10 * S;
    }

    private final java.util.IdentityHashMap<Setting<?>, float[]> chipsHeights = new java.util.IdentityHashMap<>();

    private float chipsHeight(Setting<?> key, Iterable<String> values, float width) {
        float[] cached = chipsHeights.get(key);
        if (cached != null && cached[0] == width) return cached[1];
        float height = chipsHeight(values, width);
        chipsHeights.put(key, new float[]{width, height});
        return height;
    }

    private float modeChipsHeight(ModeSetting s, float width) {
        return chipsHeight(s, s.values, width);
    }

    private float multiChipsHeight(MultiBooleanSetting s, float width) {
        float[] cached = chipsHeights.get(s);
        if (cached != null && cached[0] == width) return cached[1];
        return chipsHeight(s, multiNames(s), width);
    }

    // Нормализованный запрос кэшируется: query() зовётся для каждого модуля
    // каждый кадр, а searchQuery меняется только при вводе с клавиатуры
    private static String cachedQuerySource;
    private static String cachedQuery = "";

    private String query() {
        String source = searchQuery;
        if (!source.equals(cachedQuerySource)) {
            cachedQuerySource = source;
            cachedQuery = source.trim().toLowerCase();
        }
        return cachedQuery;
    }

    private boolean searching() { return !query().isEmpty(); }

    // Поля модуля в нижнем регистре — имя/описание/категория неизменны после
    // конструктора, поэтому приводим их один раз, а не 4 раза на модуль за кадр
    private static final java.util.Map<Module, String[]> searchFields = new java.util.IdentityHashMap<>();

    private static String[] searchFields(Module f) {
        String[] cached = searchFields.get(f);
        if (cached == null) {
            cached = new String[] {
                    f.getName().toLowerCase(),
                    f.getBigName().toLowerCase(),
                    f.getDesc().toLowerCase(),
                    f.getCategory().getName().toLowerCase()
            };
            searchFields.put(f, cached);
        }
        return cached;
    }

    private boolean moduleVisible(Module f) {
        String q = query();
        if (q.isEmpty()) return f.getCategory() == active;
        String[] fields = searchFields(f);
        return fields[0].contains(q) || fields[1].contains(q)
                || fields[2].contains(q) || fields[3].contains(q);
    }

    private int searchResults() {
        int count = 0;
        for (Module f : Client.get().moduleManager().values()) if (moduleVisible(f)) count++;
        return count;
    }

    private void searchChanged() {
        searchTypeTime = System.currentTimeMillis();
        scrollTarget = 0;
    }

    private void clearSearch() {
        if (searchQuery.isEmpty()) return;
        searchQuery = "";
        searchChanged();
    }

    private java.util.List<String> multiNames(MultiBooleanSetting s) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (BooleanSetting b : s.getValues()) names.add(b.getName());
        return names;
    }

    @Override
    protected void init() {
        exit = false;
        searchActive = false;
        searchTypeTime = System.currentTimeMillis();
        bindingModule = null;
        toggleArmed = false;
        dissolveStarted = true;
        pendingDissolveSwap = false;
        GuiSounds.open();
        GuiMusicPlayer.start(0.15F);
        if (shatter()) {
            // панель проявится под осколками, когда они почти соберутся
            shards.reset();
            pendingAssemble = true;
            panelAnimStarted = false;
            glomalAnim.set(0);
        } else {
            shards.reset();
            pendingAssemble = false;
            panelAnimStarted = true;
            glomalAnim.run(1, 0.25F, Easings.SINE_OUT);
        }
        selectedTheme = Client.get().guiManager().getCurrentTheme();
        preSelectedTheme = Client.get().guiManager().getCurrentTheme();
        themes = ru.white.manager.Theme.values();
    }

    private boolean shatter() {
        return effect("Сборка");
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    private GifTexture gif;

    private void loadGif() {
        if (gif != null) return;
        try {
            var res = mc.getResourceManager().getResource(Identifier.of("client", "textures/gui.gif"));
            if (res.isPresent()) {
                try (InputStream in = res.get().getInputStream()) {
                    gif = new GifTexture(in);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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

    private int toggleKey() {
        ClickGui gui = Client.get().moduleManager().get(ClickGui.class);
        return gui == null ? -1 : gui.getKey();
    }

    /**
     * Клавиша открытия (по умолчанию правый шифт) закрывает меню только после того,
     * как её отпустили: то самое нажатие, что открыло экран, доходит и до
     * {@link #keyPressed}, а удержание клавиши даёт GLFW-повторы — иначе меню
     * закрывалось бы в тот же кадр, в котором открылось.
     */
    private void armToggleKey() {
        if (toggleArmed) return;
        int key = toggleKey();
        if (key == -1 || !InputUtil.isKeyPressed(mc.getWindow(), key)) toggleArmed = true;
    }

    public void renderOverlay(DrawContext context, RenderTickCounter tickCounter) {
        float targetScale = 2F;
        float currentScale = (float) mc.getWindow().getScaleFactor();
        scaleFix = targetScale / currentScale;

        int screenWidth  = (int) (mc.getWindow().getScaledWidth()  / scaleFix);
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

        // фон гаснет/размывается уже пока осколки летят — сама панель проявится позже
        float bgAnim = (shatter() && shards.isAssembling())
                ? Math.max(globalAnim, smoothstep(0F, 0.55F, shards.progress()))
                : globalAnim;

        if (effect("Серый фон")) grayscalePipeline.draw(bgAnim);
        ScreenBlur.capture(2); // чуть сильнее размываем фон меню
        if (effect("Размывать фон")) {
            RenderUtil.Blur.blur(0, 0, screenWidth, screenHeight, bgAnim, 0, ColorUtil.getColor(0, 0));
        }
        if (effect("Затемнять фон")) {
            RenderUtil.Images.texture(Identifier.of("client","textures/frame/background.png"), 0, 0, screenWidth, screenHeight, ColorUtil.multAlpha(ColorUtil.client(), bgAnim));
        }

        float shaderMouseX = (float) (mc.mouse.getScaledX(mc.getWindow()) / scaleFix);
        float shaderMouseY = (float) (mc.mouse.getScaledY(mc.getWindow()) / scaleFix);

        if (effect("Шейдер")) {
            int rayBase = ColorUtil.client();
            raysPipeline.draw(bgAnim, ColorUtil.replAlpha(ColorUtil.multDark(rayBase, 0.5F), 0.9F * bgAnim), ColorUtil.replAlpha(rayBase, 0.9F * bgAnim), 0.1F, 0.08F, 0.26F);
        }

        if (effect("Точки")) {
            ClickGui guiModule = Client.get().moduleManager().get(ClickGui.class);
            float patternMode = guiModule != null && guiModule.dotsPattern.is("Соты") ? 1.0F : 0.0F;
            halftonePipeline.draw(screenWidth, screenHeight, shaderMouseX, shaderMouseY, bgAnim * 0.12F,
                    ColorUtil.getColor(255), 7, 0.7F, 3, 130, patternMode);
        }

        if (effect("Скан линии")) drawScanLines(screenWidth, screenHeight, bgAnim);

        if (effect("Свечение")) {
            int glowCol = ColorUtil.client();

            // мягкая светлая шапка сверху экрана
            Draw.gradientRect(0, 0, screenWidth, Math.max(1F, screenHeight * 0.24F),
                    new int[]{
                            ColorUtil.replAlpha(glowCol, 0.16F * bgAnim),
                            ColorUtil.replAlpha(glowCol, 0.16F * bgAnim),
                            ColorUtil.getColor(0, 0),
                            ColorUtil.getColor(0, 0)
                    }, 0);

            // медленно дышащие кольца у верхней кромки
            float ringCx = screenWidth * 0.5F;
            float ringCy = -screenHeight * 0.05F;
            long ringMs = System.currentTimeMillis();
            for (int i = 0; i < 3; i++) {
                float fi = i;
                float breathe = 0.5F + 0.5F * (float) Math.sin(ringMs / 2600.0 + fi * 2.1);
                float radius = (90 + fi * 70) * S * (0.92F + 0.08F * breathe);
                float ringA = bgAnim * (0.14F - fi * 0.035F) * (0.6F + 0.4F * breathe);

                RenderUtil.Render2D.outline(ringCx - radius, ringCy - radius, radius * 2F, radius * 2F,
                        1.1F * S, ColorUtil.replAlpha(glowCol, ringA), radius);
                RenderUtil.Render2D.glow(ringCx - radius, ringCy - radius, radius * 2F, radius * 2F,
                        ColorUtil.replAlpha(glowCol, ringA * 0.5F), radius, 9, 1);
            }

            if (!exit && bgAnim > 0.01F && bgAnim < 0.99F) {
                float pulse = (bgAnim > 0.5F ? 1F - bgAnim : bgAnim) * 2F;
                float scale = screenWidth / 1.75F * bgAnim;
                float cx = screenWidth / 2F;
                float cy = screenHeight / 2F;
                RenderUtil.Render2D.rect(cx - scale, cy - scale, scale * 2, scale * 2, ColorUtil.getColor(255, 0.2F * pulse), scale);
                RenderUtil.Render2D.glow(cx - scale, cy - scale, scale * 2, scale * 2, ColorUtil.replAlpha(ColorUtil.client(), 0.15F * pulse), scale, 12, 1);
            }
        }

        if (effect("Частицы")) {
            spawnParticle(screenWidth, screenHeight);
            renderParticles(bgAnim);
        } else if (!particles.isEmpty()) {
            particles.clear();
        }

        ScreenBlur.capture();

        S = Client.get().moduleManager().get(ClickGui.class).size.getValue();

        float w = 440 * S;
        float h = 316 * S;
        float x = screenWidth / 2F - w / 2;
        // при сборке из осколков панель никуда не съезжает — она «остаётся на месте»
        float slide = shatter() ? 0F : (exit ? 60 * S - 60 * S * globalAnim : -60 * S + 60 * S * globalAnim);
        float y = screenHeight / 2F - h / 2 + slide;

        panelX = x;
        panelY = y;
        panelW = w;
        panelH = h;
        panelScreenW = screenWidth;
        panelScreenH = screenHeight;
        panelKnown = true;

        if (pendingAssemble) {
            pendingAssemble = false;
            shards.assemble(x, y, w, h, 8 * S, screenWidth, screenHeight, S);
        }
        if (!panelAnimStarted && shards.isAssembling() && shards.progress() >= 0.5F) {
            panelAnimStarted = true;
            glomalAnim.run(1, 0.2F, Easings.SINE_OUT);
        }
        // меню постояло на месте — начинаем смену панели мозаикой.
        // Сам захват делаем в конце ЭТОГО кадра (после endOverlay), потому что
        // батчер сбрасывает панель на экран только там, а фреймбуфер каждый
        // кадр очищается — «прошлого кадра» в момент обработки уже нет
        if (exit && !dissolveStarted && System.currentTimeMillis() - exitHoldStart >= SHATTER_HOLD_MS) {
            dissolveStarted = true;
            pendingDissolveSwap = true;
        }

        Font draw = Fonts.sf_regular;
        Font regular = Fonts.sf_regular;
        Font icons = Fonts.icon;
        Font guiicon = Fonts.gui;

        ScreenBlur.capture();

        // ── каркас ──
        RenderUtil.Render2D.glow(x, y, w, h - 0.5F * S, ColorUtil.getColor(0, 0.15F * globalAnim), 8 * S, 15, 1);
        RenderUtil.Blur.blur(x, y, w, h, globalAnim, 8 * S, ColorUtil.multAlpha(ColorUtil.multDark(ColorUtil.background(), 0.6F), globalAnim));
        RenderUtil.Images.texture(Identifier.of("client","textures/frame/rectgui.png"), x, y, w, h, ColorUtil.multAlpha(ColorUtil.client(), globalAnim));

        // ── строка 1: лого · точки тем справа ──
        float rowY = y + 6 * S;

        RenderUtil.Images.texture(Identifier.of("client", "textures/icon.png"),
                x + 10.5F * S, rowY + 3.5F * S, 9.5F * S, 9.5F * S, ColorUtil.getColor(255, globalAnim));
        draw.draw("RainyDLC", x + 25 * S, rowY + 5.5F * S, 8 * S, ColorUtil.getColor(215, globalAnim * 0.9F));
        draw.draw("1.0", x + 25 * S + draw.getWidth("RainyDLC", 8 * S) + 4 * S, rowY + 6.2F * S, 6.5F * S,
                ColorUtil.replAlpha(ColorUtil.client(), globalAnim * 0.75F));

        float dotsW = themes.length * 14 * S - 6 * S;
        float xtd = x + w - 6 * S - dotsW;
        float ytd = rowY + 5 * S;

        for (Theme theme : themes) {
            theme.animation.setDirection(theme == selectedTheme ? Direction.FORWARDS : Direction.BACKWARDS);
            float anPC = theme.animation.getOutput();

            RenderUtil.Render2D.rect(xtd + 0.75F * S * anPC, ytd + 0.75F * S * anPC, 8 * S - 1.5F * S * anPC, 8 * S - 1.5F * S * anPC, ColorUtil.replAlpha(theme.getClient(), (0.5F + 0.5F * anPC) * globalAnim), 8 * S);
            RenderUtil.Render2D.outline(xtd - 0.5F * S * anPC, ytd - 0.5F * S * anPC, 8 * S + 1 * S * anPC, 8 * S + 1 * S * anPC, 0.25F * S, ColorUtil.replAlpha(theme.getClient(), (1.0F * anPC) * globalAnim), 8 * S);
            RenderUtil.Render2D.glow(xtd + 0.75F * S * anPC, ytd + 0.75F * S * anPC, 8 * S - 1.5F * S * anPC, 8 * S - 1.5F * S * anPC, ColorUtil.replAlpha(theme.getClient(), (0.1F * anPC) * globalAnim), 8 * S, 7, 1);

            xtd += 14 * S;
        }

        float xps = x + (w - 140 * S) / 2F;
        float yps = y + 24 * S;

        boolean searchHover = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xps, yps, 140 * S, 20 * S);
        animation3.setDirection(searchHover || searchActive ? Direction.FORWARDS : Direction.BACKWARDS);
        animSearchFocus.setDirection(searchActive ? Direction.FORWARDS : Direction.BACKWARDS);
        animSearchText.setDirection(!searchQuery.isEmpty() ? Direction.FORWARDS : Direction.BACKWARDS);

        float hvs = animation3.getOutput();
        float focus = animSearchFocus.getOutput();
        float typed = animSearchText.getOutput();

        RenderUtil.Render2D.glow(xps, yps, 140 * S, 20 * S, ColorUtil.getColor(0, 0.04F * globalAnim), 6 * S, 8, 1);
        RenderUtil.Render2D.rect(xps, yps, 140 * S, 20 * S, ColorUtil.overCol(ColorUtil.getColor(0, 0.15F * globalAnim), ColorUtil.getColor(25, 0.3F * globalAnim), Math.max(hvs, focus)), 6 * S);
        RenderUtil.Render2D.outline(xps, yps, 140 * S, 20 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * focus), 6 * S);

        guiicon.draw("A", xps + 7 * S, yps + 6 * S, 6 * S, ColorUtil.overCol(ColorUtil.getColor(255, globalAnim * (0.3F + 0.5F * hvs)), ColorUtil.replAlpha(ColorUtil.client(), globalAnim * (0.3F + 0.7F * hvs)), hvs));

        float xSearchText = xps + 6.5F * S + 11 * S;
        float wSearchText = 140 * S - (xSearchText - xps) - 8 * S;

        float hidePlaceholder = Math.max(typed, focus);
        if (hidePlaceholder < 0.99F) {
            regular.draw("Search", xSearchText, yps + 5.5F * S + 4 * S * hidePlaceholder, 7 * S, ColorUtil.getColor(255, globalAnim * (1 - hidePlaceholder) * (0.3F + 0.5F * hvs)));
        }
        if (typed > 0.01F) {
            regular.drawFadingTextReverse(searchQuery, xSearchText, yps + 5.5F * S - 4 * S * (1 - typed), wSearchText, ColorUtil.getColor(255, globalAnim * typed * (0.5F + 0.5F * Math.max(hvs, focus))), 7 * S);
        }

        boolean justTyped = System.currentTimeMillis() - searchTypeTime < 500;
        float caretTarget = focus * (justTyped || (System.currentTimeMillis() / 500) % 2 == 0 ? 1F : 0F);
        float caret = smooth("search:caret", caretTarget);
        float caretX = smooth("search:caretx", xSearchText + Math.min(regular.getWidth(searchQuery, 7 * S), wSearchText) + 1.5F * S);

        RenderUtil.Render2D.rect(caretX, yps + 6.5F * S, 0.6F * S, 6.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * caret), 0.3F * S);

        // ── ряд 2: вкладки категорий по центру ──
        float tabY = y + 50 * S;
        float tabsTotal = -4 * S;
        for (Category category : Category.values())
            tabsTotal += 24 * S + category.alphaS.getOutput() * (draw.getWidth(category.getName(), 7 * S) + 10 * S) + 4 * S;
        float tabX = x + (w - tabsTotal) / 2F;
        for (Category category : Category.values()) {
            category.alphaS.setDirection(active == category ? Direction.FORWARDS : Direction.BACKWARDS);
            float act = category.alphaS.getOutput();
            ru.white.utils.animation.satoshi.Animation animUF = category.alphaS2;

            float tw = 24 * S + act * (draw.getWidth(category.getName(), 7 * S) + 10 * S);
            boolean isHv = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, tabX, tabY, tw, 22 * S);
            animUF.setDirection(isHv ? Direction.FORWARDS : Direction.BACKWARDS);
            float hv = animUF.getOutput();
            float mix = Math.max(act, hv * 0.55F);

            RenderUtil.Render2D.rect(tabX, tabY, tw, 22 * S, ColorUtil.overCol(ColorUtil.getColor(0, 0.12F * globalAnim), ColorUtil.replAlpha(ColorUtil.client(), 0.22F * globalAnim), mix), 5 * S);
            if (act > 0.01F)
                RenderUtil.Render2D.outline(tabX, tabY, tw, 22 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * act * 0.8F), 5 * S);

            Fonts.rainydlc_2.drawCentered(category.getIcon(), tabX + 12 * S, tabY + 6 * S, 8 * S,
                    ColorUtil.multAlpha(ColorUtil.overCol(ColorUtil.getColor(255, 0.35F * globalAnim + 0.45F * hv), ColorUtil.client(), Math.max(act, hv * 0.6F)), globalAnim));
            if (act > 0.01F)
                draw.draw(category.getName(), tabX + 21 * S, tabY + 7.5F * S, 7 * S, ColorUtil.getColor(220, globalAnim * act));

            RenderUtil.Render2D.rect(tabX + 5 * S, tabY + 19.25F * S, (tw - 10 * S) * act, 1.25F * S,
                    ColorUtil.replAlpha(ColorUtil.client(), globalAnim * act), 1 * S);

            tabX += tw + 4 * S;
        }

        int found = searchResults();
        String foundText = found + (found == 1 ? " result" : " results");
        draw.draw(foundText, x + w - 8 * S - draw.getWidth(foundText, 6.5F * S), tabY + 7.5F * S, 6.5F * S,
                ColorUtil.replAlpha(ColorUtil.client(), globalAnim * typed));

        float listX = x + 6 * S;
        float listW = 152 * S;

        RenderUtil.Render2D.glow(listX, y + 76 * S, listW, h - 82 * S, ColorUtil.getColor(0, 0.04F * globalAnim), 7 * S, 8, 1);
        RenderUtil.Render2D.rect(listX, y + 76 * S, listW, h - 82 * S, ColorUtil.getColor(0, 0.15F * globalAnim), 7 * S);

        Scissor.enable(listX, y + 76 * S, listW, h - 82 * S, 2);

        scrollAnim += (scrollTarget - scrollAnim) * 0.2F;

        float xModule = listX + 5 * S;
        float yModule = y + 80 * S - scrollAnim;

        float crs = animCategoryReset.getOutput();
        float listTop = y + 76 * S;
        float listBottom = listTop + (h - 82 * S);

        for (Module f : Client.get().moduleManager().values()) {
            f.getAnimation14().setDirection(moduleVisible(f) ? Direction.FORWARDS : Direction.BACKWARDS);
            float canim1 = f.getAnimation14().getOutput();

            if (canim1 > 0) {
                float descH = descHeight(draw, f.getDesc());
                float moduleH = Math.max(20 * S, 16 * S + descH + 4 * S);

                f.getAnimation16().setDirection(f.isEnabled() ? Direction.FORWARDS : Direction.BACKWARDS);
                float moduleEnable = f.getAnimation16().getOutput();

                boolean onScreen = yModule + moduleH >= listTop && yModule <= listBottom;
                boolean isHover = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xModule, yModule, 142 * S, moduleH);

                f.getAnimation12().setDirection(isHover ? Direction.FORWARDS : Direction.BACKWARDS);
                float hanim = f.getAnimation12().getOutput();
                f.animation1.setDirection(f == select ? Direction.FORWARDS : Direction.BACKWARDS);
                float selectAnim = f.animation1.getOutput();

                if (!onScreen) {
                    yModule += (moduleH + 5 * S) * canim1;
                    continue;
                }

                RenderUtil.Render2D.rect(xModule, yModule, 142 * S, moduleH, ColorUtil.overCol(ColorUtil.getColor(0, 0.15F * globalAnim * canim1), ColorUtil.getColor(25, 0.3F * globalAnim * canim1), hanim), 5 * S);
                RenderUtil.Render2D.outline(xModule - 1 * S * moduleEnable + 1 * S, yModule - 1 * S * moduleEnable + 1 * S, 142 * S + 2 * S * moduleEnable - 2 * S, moduleH + 2 * S * moduleEnable - 2 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * canim1 * moduleEnable * (1.0F - 0.3F * hanim)), 5 * S);

                RenderUtil.Render2D.rect(xModule + 142 * S - 14 * S - 5 * S, yModule + 5F * S, 14 * S, 8 * S, ColorUtil.overCol(ColorUtil.getColor(0, 0.2F * globalAnim * canim1), ColorUtil.replAlpha(ColorUtil.client(), globalAnim * canim1 * (1.0F - 0.3F * hanim)), moduleEnable), 4 * S);
                RenderUtil.Render2D.rect(xModule + 142 * S - 14 * S - 5 * S + 1.5F * S + 5.5F * S * moduleEnable, yModule + 5F * S + 1.25F * S, 5.5F * S, 5.5F * S, ColorUtil.getColor(255, globalAnim * (0.3F + 0.7F * moduleEnable) * canim1), 4 * S);

                boolean bindingNow = bindingModule == f;
                ru.white.utils.animation.satoshi.Animation bindAct = chipAnim(f.getName() + ":modbind");
                bindAct.setDirection(bindingNow ? Direction.FORWARDS : Direction.BACKWARDS);
                float bindActive = bindAct.getOutput();

                String keyName = bindingNow ? "..." : (f.getKey() == -1 ? "n/a" : Keyboard.keyName(f.getKey()).replace("NONE", "n/a"));
                float keyW = smooth(f.getName() + ":modbindw", draw.getWidth(keyName, 6 * S) + 9 * S);
                float keyX = xModule + 142 * S - 14 * S - 5 * S - 5 * S - keyW;
                float keyY = yModule + 4F * S;

                boolean keyHover = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, keyX, keyY, keyW, 10 * S);
                ru.white.utils.animation.satoshi.Animation bindHov = chipAnim(f.getName() + ":modbindhov");
                bindHov.setDirection(keyHover ? Direction.FORWARDS : Direction.BACKWARDS);
                float bindHover = bindHov.getOutput();

                float pulse = bindingNow ? 0.55F + 0.45F * (float) Math.sin((System.currentTimeMillis() % 1000L) / 1000F * Math.PI * 2F) : 0F;
                float bindAccent = Math.max(bindHover * 0.5F, bindActive);

                RenderUtil.Render2D.rect(keyX, keyY, keyW, 10 * S, ColorUtil.overCol(ColorUtil.getColor(0, (0.12F + 0.13F * bindHover) * globalAnim * canim1), ColorUtil.replAlpha(ColorUtil.client(), (0.15F + 0.2F * pulse) * globalAnim * canim1), bindActive), 3 * S);
                RenderUtil.Render2D.outline(keyX, keyY, keyW, 10 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * canim1 * bindAccent * (bindingNow ? 0.4F + 0.6F * pulse : 1F)), 3 * S);
                RenderUtil.Render2D.glow(keyX, keyY, keyW, 10 * S, ColorUtil.replAlpha(ColorUtil.client(), 0.12F * globalAnim * canim1 * bindActive * pulse), 3 * S, 6, 1);

                regular.drawCentered(keyName, keyX + keyW / 2, keyY + 1.5F * S, 6 * S, ColorUtil.replAlpha(ColorUtil.overCol(ColorUtil.getColor(255), ColorUtil.client(), Math.max(bindActive, moduleEnable)), globalAnim * canim1 * (0.35F + 0.35F * moduleEnable + 0.3F * Math.max(bindHover, bindActive))));

                draw.draw(f.getBigName(), xModule + 5 * S, yModule + 5 * S, 7 * S, ColorUtil.getColor(255, globalAnim * canim1 * (0.2F + 0.6F * moduleEnable + 0.2F * hanim)));
                draw.drawWrappedText(f.getDesc(), xModule + 5 * S, yModule + 4 * S + 12 * S, 132 * S, ColorUtil.getColor(255, globalAnim * canim1 * (0.1F + 0.5F * moduleEnable + 0.2F * hanim)), 6 * S);

                RenderUtil.Render2D.rect(xModule, yModule, 142 * S, moduleH, ColorUtil.replAlpha(ColorUtil.client(), 0.3F * globalAnim * selectAnim * (0.2F + 0.5F * moduleEnable) * canim1), 5 * S);

                yModule += (moduleH + 5 * S) * canim1;
            }
        }

        // ── кнопка «Добавить модуль» внизу списка ──
        addModuleRect = null;
        if (!searching()) {
            float abW = 142 * S;
            float abH = 18 * S;
            float abX = xModule;
            float abY = yModule + 2 * S;
            boolean abHover = MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, abX, abY, abW, abH);
            ru.white.utils.animation.satoshi.Animation addAnim = chipAnim("lua:addmodule");
            addAnim.setDirection(abHover ? Direction.FORWARDS : Direction.BACKWARDS);
            float aa = addAnim.getOutput();

            RenderUtil.Render2D.rect(abX, abY, abW, abH,
                    ColorUtil.overCol(ColorUtil.getColor(0, 0.12F * globalAnim), ColorUtil.replAlpha(ColorUtil.client(), 0.10F * globalAnim), aa), 5 * S);
            RenderUtil.Render2D.outline(abX, abY, abW, abH, 0.5F * S,
                    ColorUtil.replAlpha(ColorUtil.client(), globalAnim * (0.25F + 0.45F * aa)), 5 * S);

            String addText = "+ Добавить модуль";
            draw.drawCentered(addText, abX + abW / 2F, abY + 5.5F * S, 6.5F * S,
                    ColorUtil.overCol(ColorUtil.getColor(200, globalAnim * (0.55F + 0.35F * aa)),
                            ColorUtil.replAlpha(ColorUtil.client(), globalAnim * (0.6F + 0.4F * aa)), Math.max(aa, abHover ? 1F : 0F)));

            addModuleRect = new float[]{abX, abY, abW, abH};
            yModule += abH + 5 * S;
        }

        animSearchEmpty.setDirection(searching() && found == 0 ? Direction.FORWARDS : Direction.BACKWARDS);
        float emptyAnim = animSearchEmpty.getOutput();

        if (emptyAnim > 0.01F) {
            float xEmpty = listX + listW / 2;
            float yEmpty = y + 76 * S + (h - 82 * S) / 2 - 8 * S + 6 * S - 6 * S * emptyAnim;
            guiicon.drawCentered("A", xEmpty, yEmpty, 8 * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * emptyAnim * 0.4F));
            draw.drawCentered("Ничего не найдено", xEmpty, yEmpty + 14 * S, 7 * S, ColorUtil.getColor(255, globalAnim * emptyAnim * 0.5F));
        }

        float contentHeight = yModule + scrollAnim - (y + 80 * S);
        float viewHeight = h - 82 * S - 4 * S;
        maxScroll = Math.max(0, contentHeight - viewHeight);
        if (scrollTarget > maxScroll) scrollTarget = maxScroll;

        Scissor.reset();

        float xSetBase = listX + listW + 6 * S;
        float wSetBase = w - 12 * S - listW - 6 * S;
        float ySetBase = y + 76 * S;
        float hSetBase = h - 82 * S;

        RenderUtil.Render2D.glow(xSetBase, ySetBase, wSetBase, hSetBase, ColorUtil.getColor(0, 0.04F * globalAnim), 7 * S, 8, 1);
        RenderUtil.Render2D.rect(xSetBase, ySetBase, wSetBase, hSetBase, ColorUtil.getColor(0, 0.15F * globalAnim), 7 * S);

        Scissor.enable(xSetBase, ySetBase, wSetBase, hSetBase, 2);

        float xSetting = xSetBase;
        float ySetting = ySetBase;
        float wSetting = wSetBase;
        float hSetting = hSetBase;

        animation2.setDirection(select != null ? Direction.FORWARDS : Direction.BACKWARDS);
        float phCenter = ySetting + hSetting / 2;
        draw.drawCentered("Выберите модуль", xSetting + wSetting / 2, phCenter + 34 * S + 30 * S * animation2.getOutput(), 7 * S, ColorUtil.getColor(255, (globalAnim - animation2.getOutput()) * 0.8F));

        loadGif();
        if (gif != null) {
            gif.draw(context, (int) (xSetting + wSetting / 2 - (78 * S) / 2), (int) (phCenter - 66 * S + 30 * S - 30 * S * animation2.getOutput()), (int)(78 * S), (int)(70 * S), ColorUtil.replAlpha(ColorUtil.WHITE, globalAnim - animation2.getOutput()));
        }

        settingScrollAnim += (settingScrollTarget - settingScrollAnim) * 0.2F;

        float xST = xSetting + 10 * S;
        float yST = ySetting + 10 * S - settingScrollAnim;
        float wST = wSetting - 20 * S;
        float setTop = ySetting;
        float setBottom = ySetting + hSetting;

        for (Module f : Client.get().moduleManager().values()) {
            f.animation3.setDirection(f == select ? Direction.FORWARDS : Direction.BACKWARDS);
            float fa = f.animation3.getOutput();

            if (fa > 0) {
                for (Setting setting : f.getSettings()) {

                    if (setting instanceof SliderSetting s) {
                        float vis = visAnim(f, s);
                        if (vis > 0.01F) {
                            float sa = fa * vis;
                            float percent = MathHelper.clamp((s.getValue() - s.min) / (s.max - s.min), 0, 1);
                            s.getAnimation().update();
                            s.getAnimation().run(percent, 0.06F, Easings.LINEAR);

                            boolean onScreen = yST + 25 * S >= setTop && yST <= setBottom;
                            boolean hovS = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xST, yST, wST, 24 * S);
                            s.animation.setDirection(hovS ? Direction.FORWARDS : Direction.BACKWARDS);
                            float hover = s.animation.getOutput();
                            float track = wST - 12 * S;

                            if (onScreen) {
                                RenderUtil.Render2D.glow(xST, yST, wST, 25 * S, ColorUtil.getColor(0, 0.06F * globalAnim * sa), 5 * S, 7, 1);
                                RenderUtil.Render2D.rect(xST, yST, wST, 25 * S, ColorUtil.getColor(40, 0.15F * globalAnim * sa), 5 * S);
                                RenderUtil.Render2D.outline(xST, yST, wST, 25 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * hover), 5 * S);
                                draw.draw(s.getName(), xST + 6 * S, yST + 5 * S, 6.5F * S, ColorUtil.getColor(255, (globalAnim * sa) * (0.5F + 0.5F * hover)));

                                String val = String.valueOf((Math.round(s.getValue() * 100.0) / 100.0));
                                draw.draw(val, xST + wST - 6 * S - draw.getWidth(val, 6 * S), yST + 5.25F * S, 6 * S, ColorUtil.replAlpha(ColorUtil.client(), (globalAnim * sa) * (0.6F + 0.4F * hover)));

                                RenderUtil.Render2D.rect(xST + 6 * S, yST + 16.5F * S, track, 3 * S, ColorUtil.getColor(0, (globalAnim * sa) * (0.15F)), 1.5F * S);
                                RenderUtil.Render2D.rect(xST + 6 * S, yST + 16.5F * S, track * s.getAnimation().get(), 3 * S, ColorUtil.replAlpha(ColorUtil.client(), (globalAnim * sa) * (0.5F + 0.5F * hover)), 1.5F * S);
                                RenderUtil.Render2D.rect(xST + 6 * S + track * s.getAnimation().get() - 3 * S, yST + 15 * S, 6 * S, 6 * S, ColorUtil.getColor((int) (200 + 55 * hover), (globalAnim * sa)), 6 * S);
                                RenderUtil.Render2D.rect(xST + 6 * S + track * s.getAnimation().get() - 2 * S, yST + 16 * S, 4 * S, 4 * S, ColorUtil.replAlpha(ColorUtil.client(), (globalAnim * sa) * (0.7F + 0.3F * hover)), 6 * S);
                            }
                            if (draggingSlider == s) {
                                float perc = MathUtil.clamp(((float) lastMouseX - (xST + 6 * S)) / track, 0, 1);
                                float newVal = MathUtil.clamp(MathUtil.round(s.min + (s.max - s.min) * perc, s.increment), s.min, s.max);
                                if (s.getValue() != newVal) { s.set(newVal); GuiSounds.sliderTick(perc); }
                            }
                            yST += 30 * S * fa * vis;
                        }
                    }

                    if (setting instanceof ModeSetting s) {
                        float vis = visAnim(f, s);
                        if (vis > 0.01F) {
                            float sa = fa * vis;
                            float hMode = 15 * S + modeChipsHeight(s, wST - 12 * S) + 4 * S;
                            boolean onScreen = yST + hMode >= setTop && yST <= setBottom;
                            boolean hovS = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xST, yST, wST, hMode);
                            s.animation.setDirection(hovS ? Direction.FORWARDS : Direction.BACKWARDS);
                            float hover = s.animation.getOutput();

                            if (onScreen) {
                                RenderUtil.Render2D.glow(xST, yST, wST, hMode, ColorUtil.getColor(0, 0.06F * globalAnim * sa), 5 * S, 7, 1);
                                RenderUtil.Render2D.rect(xST, yST, wST, hMode, ColorUtil.getColor(40, 0.15F * globalAnim * sa), 5 * S);
                                RenderUtil.Render2D.outline(xST, yST, wST, hMode, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * hover), 5 * S);

                                String current = s.getValue();
                                draw.drawFadingText(s.getName(), xST + 6 * S, yST + 4.5F * S, wST - draw.getWidth(current, 6 * S) - 16 * S, ColorUtil.getColor(255, (globalAnim * sa) * (0.5F + 0.5F * hover)), 6.5F * S);
                                draw.draw(current, xST + wST - 6 * S - draw.getWidth(current, 6 * S), yST + 4.75F * S, 6 * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * (0.6F + 0.4F * hover)));

                                float chipMaxW = wST - 12 * S;
                                float px = 0, py = 0;
                                for (String val : s.values) {
                                    float tw = draw.getWidth(val, 6 * S) + 8 * S;
                                    if (px + tw > chipMaxW && px > 0) { px = 0; py += 12 * S; }
                                    float cx = xST + 6 * S + px;
                                    float cyy = yST + 15 * S + py;
                                    ru.white.utils.animation.satoshi.Animation chip = chipAnim(f.getName() + ":" + s.getName() + ":" + val);
                                    chip.setDirection(val.equals(current) ? Direction.FORWARDS : Direction.BACKWARDS);
                                    float sel = chip.getOutput();

                                    RenderUtil.Render2D.rect(cx, cyy, tw, 10 * S, ColorUtil.overCol(ColorUtil.getColor(0, 0.25F * globalAnim * sa), ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * (0.5F + 0.5F * hover)), sel), 3 * S);
                                    regular.drawCentered(val, cx + tw / 2, cyy + 1.5F * S, 6 * S, ColorUtil.getColor(255, globalAnim * sa * (0.35F + 0.65F * sel) * (0.7F + 0.3F * hover)));
                                    px += tw + 3 * S;
                                }
                            }
                            yST += (hMode + 5 * S) * fa * vis;
                        }
                    }

                    if (setting instanceof MultiBooleanSetting s) {
                        float vis = visAnim(f, s);
                        if (vis > 0.01F) {
                            float sa = fa * vis;
                            float hMulti = 15 * S + multiChipsHeight(s, wST - 12 * S) + 4 * S;
                            boolean onScreen = yST + hMulti >= setTop && yST <= setBottom;
                            boolean hovS = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xST, yST, wST, hMulti);
                            s.getAnimation1().setDirection(hovS ? Direction.FORWARDS : Direction.BACKWARDS);
                            float hover = s.getAnimation1().getOutput();

                            if (onScreen) {
                                RenderUtil.Render2D.glow(xST, yST, wST, hMulti, ColorUtil.getColor(0, 0.06F * globalAnim * sa), 5 * S, 7, 1);
                                RenderUtil.Render2D.rect(xST, yST, wST, hMulti, ColorUtil.getColor(40, 0.15F * globalAnim * sa), 5 * S);
                                RenderUtil.Render2D.outline(xST, yST, wST, hMulti, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * hover), 5 * S);

                                int total = s.getValues().size();
                                int selectedCount = 0;
                                for (BooleanSetting b : s.getValues()) if (b.getValue()) selectedCount++;
                                String counter = selectedCount + " / " + total;

                                draw.drawFadingText(s.getName(), xST + 6 * S, yST + 4.5F * S, wST - draw.getWidth(counter, 6 * S) - 16 * S, ColorUtil.getColor(255, (globalAnim * sa) * (0.5F + 0.5F * hover)), 6.5F * S);
                                draw.draw(counter, xST + wST - 6 * S - draw.getWidth(counter, 6 * S), yST + 4.75F * S, 6 * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * (0.6F + 0.4F * hover)));

                                float chipMaxW = wST - 12 * S;
                                float px = 0, py = 0;
                                for (BooleanSetting b : s.getValues()) {
                                    float tw = draw.getWidth(b.getName(), 6 * S) + 8 * S;
                                    if (px + tw > chipMaxW && px > 0) { px = 0; py += 12 * S; }
                                    float cx = xST + 6 * S + px;
                                    float cyy = yST + 15 * S + py;
                                    ru.white.utils.animation.satoshi.Animation chip = chipAnim(f.getName() + ":" + s.getName() + ":" + b.getName());
                                    chip.setDirection(b.getValue() ? Direction.FORWARDS : Direction.BACKWARDS);
                                    float sel = chip.getOutput();

                                    RenderUtil.Render2D.rect(cx, cyy, tw, 10 * S, ColorUtil.overCol(ColorUtil.getColor(0, 0.25F * globalAnim * sa), ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * (0.5F + 0.5F * hover)), sel), 3 * S);
                                    regular.drawCentered(b.getName(), cx + tw / 2, cyy + 1.5F * S, 6 * S, ColorUtil.getColor(255, globalAnim * sa * (0.35F + 0.65F * sel) * (0.7F + 0.3F * hover)));
                                    px += tw + 3 * S;
                                }
                            }
                            yST += (hMulti + 5 * S) * fa * vis;
                        }
                    }

                    if (setting instanceof BooleanSetting s) {
                        float vis = visAnim(f, s);
                        if (vis > 0.01F) {
                            float sa = fa * vis;
                            boolean onScreen = yST + 16 * S >= setTop && yST <= setBottom;
                            boolean hovS = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xST, yST, wST, 16 * S);
                            s.animation.setDirection(hovS ? Direction.FORWARDS : Direction.BACKWARDS);
                            float hover = s.animation.getOutput();
                            s.animation2.setDirection(s.getValue() ? Direction.FORWARDS : Direction.BACKWARDS);
                            float sanimation2 = s.animation2.getOutput();

                            if (onScreen) {
                                RenderUtil.Render2D.glow(xST, yST, wST, 16 * S, ColorUtil.getColor(0, 0.06F * globalAnim * sa), 5 * S, 7, 1);
                                RenderUtil.Render2D.rect(xST, yST, wST, 16 * S, ColorUtil.getColor(40, 0.15F * globalAnim * sa), 5 * S);
                                RenderUtil.Render2D.outline(xST, yST, wST, 16 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * hover), 5 * S);

                                draw.draw(s.getName(), xST + 6 * S, yST + 4 * S, 6.5F * S, ColorUtil.getColor(255, (globalAnim * sa) * (0.5F + 0.5F * hover)));
                                RenderUtil.Render2D.rect(xST + wST - 6 * S - 12 * S, yST + 4.5F * S, 12 * S, 7 * S, ColorUtil.overCol(ColorUtil.getColor(0, 0.2F * globalAnim * sa), ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * (0.5F + 0.5F * hover)), sanimation2), 3.5F * S);
                                RenderUtil.Render2D.rect(xST + wST - 6 * S - 12 * S + 1.25F * S + 4.5F * S * sanimation2, yST + 5.75F * S, 4.5F * S, 4.5F * S, ColorUtil.getColor(255, globalAnim * sa * (0.2F + 0.4F * sanimation2 + 0.4F * hover)), 2.5F * S);
                            }
                            yST += 21 * S * fa * vis;
                        }
                    }

                    if (setting instanceof ButtonSetting s) {
                        float vis = visAnim(f, s);
                        if (vis > 0.01F) {
                            float sa = fa * vis;
                            boolean onScreen = yST + 16 * S >= setTop && yST <= setBottom;
                            boolean hovS = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xST, yST, wST, 16 * S);
                            ru.white.utils.animation.satoshi.Animation btn = chipAnim(f.getName() + ":" + s.getName() + ":btn");
                            btn.setDirection(hovS ? Direction.FORWARDS : Direction.BACKWARDS);
                            float hover = btn.getOutput();

                            s.pressAnim.update();
                            float press = s.pressAnim.get();
                            float accent = Math.max(hover, press);

                            if (onScreen) {
                                RenderUtil.Render2D.glow(xST, yST, wST, 16 * S, ColorUtil.getColor(0, 0.06F * globalAnim * sa), 5 * S, 7, 1);
                                RenderUtil.Render2D.rect(xST, yST, wST, 16 * S, ColorUtil.overCol(ColorUtil.getColor(40, 0.15F * globalAnim * sa), ColorUtil.replAlpha(ColorUtil.client(), 0.25F * globalAnim * sa), accent), 5 * S);
                                RenderUtil.Render2D.outline(xST, yST, wST, 16 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * accent), 5 * S);
                                RenderUtil.Render2D.glow(xST, yST, wST, 16 * S, ColorUtil.replAlpha(ColorUtil.client(), 0.15F * globalAnim * sa * press), 5 * S, 8, 1);
                                draw.drawCentered(s.getName(), xST + wST / 2, yST + 4 * S, 6.5F * S, ColorUtil.overCol(ColorUtil.getColor(255, (globalAnim * sa) * (0.5F + 0.5F * hover)), ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa), press));
                            }
                            yST += 21 * S * fa * vis;
                        }
                    }

                    if (setting instanceof BindSetting s) {
                        float vis = visAnim(f, s);
                        if (vis > 0.01F) {
                            float sa = fa * vis;
                            boolean onScreen = yST + 16 * S >= setTop && yST <= setBottom;
                            boolean hovS = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xST, yST, wST, 16 * S);
                            s.animation.setDirection(hovS ? Direction.FORWARDS : Direction.BACKWARDS);
                            float hover = s.animation.getOutput();

                            if (onScreen) {
                                RenderUtil.Render2D.glow(xST, yST, wST, 16 * S, ColorUtil.getColor(0, 0.06F * globalAnim * sa), 5 * S, 7, 1);
                                RenderUtil.Render2D.rect(xST, yST, wST, 16 * S, ColorUtil.getColor(40, 0.15F * globalAnim * sa), 5 * S);
                                RenderUtil.Render2D.outline(xST, yST, wST, 16 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * hover), 5 * S);

                                boolean binding = activeBind == s;
                                ru.white.utils.animation.satoshi.Animation bindAct = chipAnim(f.getName() + ":" + s.getName() + ":act");
                                bindAct.setDirection(binding ? Direction.FORWARDS : Direction.BACKWARDS);
                                float act = bindAct.getOutput();

                                String keyName = binding ? "..." : (s.get() == -1 ? "n/a" : Keyboard.keyName(s.get()));
                                float bw = smooth(f.getName() + ":" + s.getName() + ":bw", draw.getWidth(keyName, 6 * S) + 10 * S);

                                draw.drawFadingText(s.getName(), xST + 6 * S, yST + 4 * S, wST - bw - 16 * S, ColorUtil.getColor(255, (globalAnim * sa) * (0.5F + 0.5F * hover)), 6.5F * S);
                                RenderUtil.Render2D.rect(xST + wST - 6 * S - bw, yST + 3 * S, bw, 10 * S, ColorUtil.overCol(ColorUtil.getColor(0, 0.25F * globalAnim * sa), ColorUtil.replAlpha(ColorUtil.client(), 0.15F * globalAnim * sa), act), 3 * S);
                                RenderUtil.Render2D.outline(xST + wST - 6 * S - bw, yST + 3 * S, bw, 10 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * act), 3 * S);
                                RenderUtil.Render2D.glow(xST + wST - 6 * S - bw, yST + 3 * S, bw, 10 * S, ColorUtil.replAlpha(ColorUtil.client(), 0.12F * globalAnim * sa * act), 3 * S, 6, 1);
                                regular.drawCentered(keyName, xST + wST - 6 * S - bw / 2, yST + 4.5F * S, 6 * S, ColorUtil.replAlpha(ColorUtil.overCol(ColorUtil.getColor(255), ColorUtil.client(), act), globalAnim * sa * (0.45F + 0.55F * Math.max(act, hover))));
                            }
                            yST += 21 * S * fa * vis;
                        }
                    }

                    if (setting instanceof StringSetting s) {
                        float vis = visAnim(f, s);
                        if (vis > 0.01F) {
                            float sa = fa * vis;
                            boolean onScreen = yST + 16 * S >= setTop && yST <= setBottom;
                            boolean hovS = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xST, yST, wST, 16 * S);
                            s.getAnimation1().setDirection(hovS ? Direction.FORWARDS : Direction.BACKWARDS);
                            float hover = s.getAnimation1().getOutput();

                            if (onScreen) {
                                RenderUtil.Render2D.glow(xST, yST, wST, 16 * S, ColorUtil.getColor(0, 0.06F * globalAnim * sa), 5 * S, 7, 1);
                                RenderUtil.Render2D.rect(xST, yST, wST, 16 * S, ColorUtil.getColor(40, 0.15F * globalAnim * sa), 5 * S);
                                RenderUtil.Render2D.outline(xST, yST, wST, 16 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * hover), 5 * S);

                                boolean editing = activeString == s;
                                ru.white.utils.animation.satoshi.Animation editAct = chipAnim(f.getName() + ":" + s.getName() + ":act");
                                editAct.setDirection(editing ? Direction.FORWARDS : Direction.BACKWARDS);
                                float act = editAct.getOutput();

                                String shown = editing ? stringBuffer + ((System.currentTimeMillis() / 400) % 2 == 0 ? "_" : "") : s.getValue();
                                if (shown.isEmpty()) shown = "...";

                                float bw = smooth(f.getName() + ":" + s.getName() + ":bw", Math.min(draw.getWidth(shown, 6 * S) + 10 * S, wST * 0.55F));

                                draw.drawFadingText(s.getName(), xST + 6 * S, yST + 4 * S, wST - bw - 16 * S, ColorUtil.getColor(255, (globalAnim * sa) * (0.5F + 0.5F * hover)), 6.5F * S);
                                RenderUtil.Render2D.rect(xST + wST - 6 * S - bw, yST + 3 * S, bw, 10 * S, ColorUtil.overCol(ColorUtil.getColor(0, 0.25F * globalAnim * sa), ColorUtil.replAlpha(ColorUtil.client(), 0.15F * globalAnim * sa), act), 3 * S);
                                RenderUtil.Render2D.outline(xST + wST - 6 * S - bw, yST + 3 * S, bw, 10 * S, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * act), 3 * S);
                                RenderUtil.Render2D.glow(xST + wST - 6 * S - bw, yST + 3 * S, bw, 10 * S, ColorUtil.replAlpha(ColorUtil.client(), 0.12F * globalAnim * sa * act), 3 * S, 6, 1);
                                regular.drawFadingText(shown, xST + wST - 6 * S - bw + 5 * S, yST + 4.5F * S, bw - 8 * S, ColorUtil.getColor(255, globalAnim * sa * (0.45F + 0.55F * Math.max(act, hover))), 6 * S);
                            }
                            yST += 21 * S * fa * vis;
                        }
                    }

                    if (setting instanceof ColorSetting s) {
                        float vis = visAnim(f, s);
                        if (vis > 0.01F) {
                            float sa = fa * vis;
                            s.pickerAnim.setDirection(s.pickerOpen ? Direction.FORWARDS : Direction.BACKWARDS);
                            float open = s.pickerAnim.getOutput();
                            float hCol = 16 * S + open * 35 * S;

                            boolean onScreen = yST + hCol >= setTop && yST <= setBottom;
                            boolean hovS = onScreen && MathUtil.isHovered((float) lastMouseX, (float) lastMouseY, xST, yST, wST, hCol);
                            s.getAnimation1().setDirection(hovS ? Direction.FORWARDS : Direction.BACKWARDS);
                            float hover = s.getAnimation1().getOutput();

                            float bx = xST + 6 * S;
                            float bwd = wST - 12 * S;
                            int col = s.getValue();
                            String kb = f.getName() + ":" + s.getName();

                            if (onScreen) {
                                RenderUtil.Render2D.glow(xST, yST, wST, hCol, ColorUtil.getColor(0, 0.06F * globalAnim * sa), 5 * S, 7, 1);
                                RenderUtil.Render2D.rect(xST, yST, wST, hCol, ColorUtil.getColor(40, 0.15F * globalAnim * sa), 5 * S);
                                RenderUtil.Render2D.outline(xST, yST, wST, hCol, 0.5F * S, ColorUtil.replAlpha(ColorUtil.client(), globalAnim * sa * hover), 5 * S);

                                draw.drawFadingText(s.getName(), xST + 6 * S, yST + 4 * S, wST - 34 * S, ColorUtil.getColor(255, (globalAnim * sa) * (0.5F + 0.5F * hover)), 6.5F * S);

                                int pr = (int) smooth(kb + ":pr", (col >> 16) & 0xFF);
                                int pg = (int) smooth(kb + ":pg", (col >> 8) & 0xFF);
                                int pb = (int) smooth(kb + ":pb", col & 0xFF);

                                float pcx = xST + wST - 6 * S - 9 * S;
                                float pcy = yST + 4 * S;

                                RenderUtil.Render2D.rect(pcx, pcy, 8 * S, 8 * S, ColorUtil.getColor(pr, pg, pb, globalAnim * sa), 8 * S);
                                RenderUtil.Render2D.outline(pcx - 0.75F * S, pcy - 0.75F * S, 9.5F * S, 9.5F * S, 0.25F * S, ColorUtil.getColor(pr, pg, pb, globalAnim * sa * (0.4F + 0.6F * hover)), 8 * S);
                                RenderUtil.Render2D.glow(pcx, pcy, 8 * S, 8 * S, ColorUtil.getColor(pr, pg, pb, 0.1F * globalAnim * sa), 8 * S, 7, 1);

                                if (open > 0.01F) {
                                    float[] hsb = java.awt.Color.RGBtoHSB((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);
                                    float shh = smooth(kb + ":h", hsb[0]);
                                    float shs = smooth(kb + ":s", hsb[1]);
                                    float shb = smooth(kb + ":b", hsb[2]);
                                    float[] smoothHsb = {shh, shs, shb};

                                    int segs = 16;
                                    float seg = bwd / segs;

                                    for (int bar = 0; bar < 3; bar++) {
                                        float by = yST + 18 * S + bar * 12 * S;

                                        for (int i = 0; i < segs; i++) {
                                            float t0 = (float) i / segs;
                                            float t1 = (float) (i + 1) / segs;
                                            int c0 = switch (bar) { case 0 -> java.awt.Color.HSBtoRGB(t0, 1F, 1F); case 1 -> java.awt.Color.HSBtoRGB(shh, t0, shb); default -> java.awt.Color.HSBtoRGB(shh, shs, t0); };
                                            int c1 = switch (bar) { case 0 -> java.awt.Color.HSBtoRGB(t1, 1F, 1F); case 1 -> java.awt.Color.HSBtoRGB(shh, t1, shb); default -> java.awt.Color.HSBtoRGB(shh, shs, t1); };
                                            int a0 = ColorUtil.replAlpha(c0, globalAnim * sa * open);
                                            int a1 = ColorUtil.replAlpha(c1, globalAnim * sa * open);

                                            RenderUtil.Render2D.gradientRect(bx + i * seg, by, seg + (i == segs - 1 ? 0 : 0.5F * S), 4 * S, new int[]{a0, a1, a1, a0}, i == 0 ? 2 * S : 0, i == segs - 1 ? 2 * S : 0, i == segs - 1 ? 2 * S : 0, i == 0 ? 2 * S : 0);
                                        }

                                        float kx = bx + bwd * smoothHsb[bar];
                                        int kc = switch (bar) { case 0 -> java.awt.Color.HSBtoRGB(shh, 1F, 1F); default -> java.awt.Color.HSBtoRGB(shh, shs, shb); };
                                        RenderUtil.Render2D.rect(kx - 3 * S, by - 1 * S, 6 * S, 6 * S, ColorUtil.getColor(255, globalAnim * sa * open), 6 * S);
                                        RenderUtil.Render2D.rect(kx - 2 * S, by, 4 * S, 4 * S, ColorUtil.replAlpha(kc, globalAnim * sa * open), 6 * S);
                                    }
                                }
                            }

                            if (open > 0.01F && draggingColor == s) {
                                float[] hsb = java.awt.Color.RGBtoHSB((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);
                                float t = MathUtil.clamp(((float) lastMouseX - bx) / bwd, 0, 1);
                                float[] nh = {hsb[0], hsb[1], hsb[2]};
                                nh[draggingColorBar] = t;
                                int rgb = java.awt.Color.HSBtoRGB(nh[0], nh[1], nh[2]);
                                int newCol = (col & 0xFF000000) | (rgb & 0x00FFFFFF);
                                if (newCol != col) { s.set(newCol); GuiSounds.colorTick(t); }
                            }
                            yST += (hCol + 5 * S) * fa * vis;
                        }
                    }
                }
            }
        }

        // ── кнопка удаления для Lua-модулей ──
        deleteRect = null;
        if (select instanceof ru.white.script.LuaModule) {
            float sa = select.animation3.getOutput();
            if (sa > 0.01F) {
                boolean armed = deleteArmModule == select && System.currentTimeMillis() < deleteArmUntil;
                float blink = armed ? (float) Math.abs(Math.sin(System.currentTimeMillis() / 170.0)) : 0F;
                float dh = 16 * S;
                float dx = xST, dy = yST + 2 * S, dw = wST;

                RenderUtil.Render2D.rect(dx, dy, dw, dh,
                        ColorUtil.overCol(
                                ColorUtil.getColor(255, 70, 70, (globalAnim * sa) * (armed ? 0.16F + 0.14F * blink : 0.07F)),
                                ColorUtil.getColor(255, 90, 90, (globalAnim * sa) * (armed ? 0.35F + 0.2F * blink : 0.18F)),
                                Math.max(blink, 0F)), 5 * S);
                RenderUtil.Render2D.outline(dx, dy, dw, dh, 0.5F * S,
                        ColorUtil.getColor(255, 100, 100, (globalAnim * sa) * (armed ? 0.9F : 0.45F)), 5 * S);
                String dLabel = armed ? "Точно удалить?" : "Удалить модуль";
                regular.drawCentered(dLabel, dx + dw / 2, dy + 4.5F * S, 6.5F * S,
                        ColorUtil.getColor(255, 130, 130, globalAnim * sa * (armed ? 1F : 0.85F)));

                deleteRect = new float[]{dx, dy, dw, dh};
                yST += (dh + 5 * S) * sa;
            }
        }

        float contentHS = yST + settingScrollAnim - (ySetting + 10 * S);
        settingMaxScroll = Math.max(0, contentHS - (hSetting - 14 * S));
        if (settingScrollTarget > settingMaxScroll) settingScrollTarget = settingMaxScroll;

        Scissor.disable();
        shards.render();
        Render2D.endOverlay();
        if (context != null) context.getMatrices().popMatrix();

        // панель этого кадра уже сброшена батчером на экран — фиксируем её
        // в текстуру и только теперь запускаем распад (задержка в 1 кадр)
        if (pendingDissolveSwap) {
            pendingDissolveSwap = false;
            if (panelKnown) {
                shards.capturePanel(panelX, panelY, panelW, panelH);
                shards.dissolve(panelX, panelY, panelW, panelH, 8 * S, panelScreenW, panelScreenH, S);
                glomalAnim.run(0, 0.1F, Easings.SINE_IN);
            }
        }
    }

    public void openHandsEditor() { beforeEditorOpen(); handsEditor.open(); GuiSounds.editor(); }
    public void openPreviewEditor(Module target) { beforeEditorOpen(); PreviewEditor.getInstance().open(target); GuiSounds.editor(); }
    public void openCrosshairEditor() { beforeEditorOpen(); CrosshairEditor.getInstance().open(); GuiSounds.editor(); }
    private void beforeEditorOpen() { exit = false; shards.reset(); pendingAssemble = false; panelAnimStarted = true; glomalAnim.run(1, 0.2F, Easings.QUAD_OUT); }
    @Override public void removed() { OverlayEditors.closeAll(); super.removed(); }
    private void closeCheck() { if (exit && dissolveStarted && glomalAnim.isFinished()) { close(); GuiMusicPlayer.stop(); exit = false; } }

    /**
     * Догоняющий распад: экран уже закрыт (управление вернулось игроку), а осколки
     * ещё разлетаются — их дорисовывает HUD. Пока меню открыто, они рисуются в
     * {@link #renderOverlay}, поэтому здесь такой кадр пропускаем.
     */
    public void renderShardsAfterClose() {
        if (mc.currentScreen == this) return;
        if (!shards.isDissolving()) return;
        shards.render();
    }

    /** Запуск закрытия: панель замирает на месте, потом рассыпается на осколки. */
    private void startExit() {
        if (exit) return;
        exit = true;
        GuiSounds.close();
        pendingAssemble = false;
        panelAnimStarted = true;
        if (shatter() && panelKnown) {
            // панель пока не трогаем — распад стартует по таймеру в renderOverlay
            exitHoldStart = System.currentTimeMillis();
            dissolveStarted = false;
        } else {
            shards.reset();
            dissolveStarted = true;
            glomalAnim.run(0, 0.3F, Easings.SINE_IN);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        float mouseX = (int) (click.x() / scaleFix);
        float mouseY = (int) (click.y() / scaleFix);

        OverlayEditor editor = OverlayEditors.active();
        if (editor != null) { return editor.mouseClicked(mouseX, mouseY, click.button()); }

        // меню уже закрывается (замерло перед распадом) — клики игнорируем
        if (exit) return true;

        if (bindingModule != null) { bindingModule = null; GuiSounds.bindReset(); return true; }

        if (activeBind != null) {
            if (activeBind.allowMouse && click.button() != 0 && click.button() != 1) { activeBind.set(click.button()); GuiSounds.bindSet(); }
            else { GuiSounds.bindReset(); }
            activeBind = null; return true;
        }

        if (activeString != null) { activeString.set(stringBuffer); activeString = null; GuiSounds.editCommit(); }

        int screenWidth  = (int) (mc.getWindow().getScaledWidth()  / scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);

        float w = 440 * S;
        float h = 316 * S;
        float x = screenWidth / 2F - w / 2;
        float y = screenHeight / 2F - h / 2;

        float xps = x + (w - 140 * S) / 2F;
        float yps = y + 24 * S;

        if (MathUtil.isHovered(mouseX, mouseY, xps, yps, 140 * S, 20 * S)) {
            if (click.button() == 1) { clearSearch(); searchActive = false; GuiSounds.searchClear(); }
            else if (click.button() == 0) { if (!searchActive) GuiSounds.editStart(); searchActive = true; searchTypeTime = System.currentTimeMillis(); }
            return true;
        }

        searchActive = false;

        float dotsWc = themes.length * 14 * S - 6 * S;
        float xtd = x + w - 6 * S - dotsWc;
        float ytd = y + 11 * S;
        int themeIndex = 0;

        for (Theme theme : themes) {
            if (MathUtil.isHovered(mouseX, mouseY, xtd, ytd, 8 * S, 8 * S) && click.button() == 0) {
                animation14.reset(); preSelectedTheme = selectedTheme; selectedTheme = theme;
                Client.get().guiManager().setGuiTheme(theme); GuiSounds.theme(themeIndex, themes.length);
            }
            xtd += 14 * S; themeIndex++;
        }

        Font draw = Fonts.sf_regular;

        float tabsTotalC = -4 * S;
        for (Category category : Category.values())
            tabsTotalC += 24 * S + category.alphaS.getOutput() * (draw.getWidth(category.getName(), 7 * S) + 10 * S) + 4 * S;
        float tabXc = x + (w - tabsTotalC) / 2F;
        float tabYc = y + 50 * S;
        int catIndex = 0;

        for (Category category : Category.values()) {
            float twc = 24 * S + category.alphaS.getOutput() * (draw.getWidth(category.getName(), 7 * S) + 10 * S);
            if (MathUtil.isHovered(mouseX, mouseY, tabXc, tabYc, twc, 22 * S) && click.button() == 0 && (active != category || searching())) {
                clearSearch();
                if (active != category) { active = category; animCategoryReset.reset(); }
                scrollTarget = 0; GuiSounds.category(catIndex, Category.values().length);
            }
            tabXc += twc + 4 * S; catIndex++;
        }

        float xModule = x + 11 * S;
        float yModule = y + 80 * S - scrollAnim;

        boolean insidePanel = MathUtil.isHovered(mouseX, mouseY, x + 6 * S, y + 76 * S, 152 * S, h - 82 * S);

        for (Module f : Client.get().moduleManager().values()) {
            float canim1 = f.getAnimation14().getOutput();
            if (canim1 > 0) {
                float descH = descHeight(draw, f.getDesc()) ;
                float moduleH = Math.max(20 * S, 16 * S + descH + 4 * S);

                if (insidePanel && canim1 > 0.5F && MathUtil.isHovered(mouseX, mouseY, xModule, yModule, 142 * S, moduleH)) {
                    String keyName = bindingModule == f ? "..." : (f.getKey() == -1 ? "n/a" : Keyboard.keyName(f.getKey()).replace("NONE", "n/a"));
                    float keyW = draw.getWidth(keyName, 6 * S) + 9 * S;
                    float keyX = xModule + 142 * S - 14 * S - 5 * S - 5 * S - keyW;

                    if (MathUtil.isHovered(mouseX, mouseY, keyX - 2 * S, yModule + 2F * S, keyW + 4 * S, 14 * S) && click.button() == 0) {
                        bindingModule = f; GuiSounds.bindStart(); return true;
                    }
                    if (click.button() == 2) { bindingModule = f; GuiSounds.bindStart(); return true; }
                    if (click.button() == 0) { f.setEnabled(!f.isEnabled()); }
                    if (click.button() == 1) { select = (f == select) ? null : f; settingScrollTarget = 0; settingScrollAnim = 0; GuiSounds.expand(select == f); }
                }
                yModule += (moduleH + 5 * S) * canim1;
            }
        }

        // ── «Добавить модуль» → редактор Lua-скрипта ──
        if (!searchActive && addModuleRect != null && click.button() == 0
                && MathUtil.isHovered(mouseX, mouseY, addModuleRect[0], addModuleRect[1], addModuleRect[2], addModuleRect[3])) {
            GuiSounds.button();
            mc.setScreen(new ScriptEditorScreen(this, active));
            return true;
        }

        float xSetting = x + 164 * S;
        float ySetting = y + 76 * S;
        float wSetting = w - 170 * S;
        float hSetting = h - 82 * S;

        if (MathUtil.isHovered(mouseX, mouseY, xSetting, ySetting, wSetting, hSetting) && select != null) {
            float xST = xSetting + 10 * S;
            float yST = ySetting + 10 * S - settingScrollAnim;
            float wST = wSetting - 20 * S;

            for (Setting setting : select.getSettings()) {
                if (setting instanceof SliderSetting s) {
                    float vis = visAnim(select, s);
                    if (vis > 0.01F) {
                        if (s.getVisible().get() && click.button() == 0 && MathUtil.isHovered(mouseX, mouseY, xST + 3 * S, yST + 11 * S, wST - 6 * S, 13 * S)) {
                            draggingSlider = s; GuiSounds.sliderGrab();
                        }
                        yST += 30 * S * vis;
                    }
                }
                if (setting instanceof ModeSetting s) {
                    float vis = visAnim(select, s);
                    if (vis > 0.01F) {
                        float hMode = 15 * S + modeChipsHeight(s, wST - 12 * S) + 4 * S;
                        if (s.getVisible().get() && click.button() == 0) {
                            float chipMaxW = wST - 12 * S; float px = 0, py = 0; int chipIndex = 0;
                            for (String val : s.values) {
                                float tw = draw.getWidth(val, 6 * S) + 8 * S;
                                if (px + tw > chipMaxW && px > 0) { px = 0; py += 12 * S; }
                                if (MathUtil.isHovered(mouseX, mouseY, xST + 6 * S + px, yST + 15 * S + py, tw, 10 * S)) {
                                    if (!val.equals(s.getValue())) GuiSounds.chip(chipIndex, s.values.size()); s.set(val);
                                }
                                px += tw + 3 * S; chipIndex++;
                            }
                        }
                        yST += (hMode + 5 * S) * vis;
                    }
                }
                if (setting instanceof MultiBooleanSetting s) {
                    float vis = visAnim(select, s);
                    if (vis > 0.01F) {
                        float hMulti = 15 * S + multiChipsHeight(s, wST - 12 * S) + 4 * S;
                        if (s.getVisible().get() && click.button() == 0) {
                            float chipMaxW = wST - 12 * S; float px = 0, py = 0;
                            for (BooleanSetting b : s.getValues()) {
                                float tw = draw.getWidth(b.getName(), 6 * S) + 8 * S;
                                if (px + tw > chipMaxW && px > 0) { px = 0; py += 12 * S; }
                                if (MathUtil.isHovered(mouseX, mouseY, xST + 6 * S + px, yST + 15 * S + py, tw, 10 * S)) { b.set(!b.getValue()); GuiSounds.chipMulti(b.getValue()); }
                                px += tw + 3 * S;
                            }
                        }
                        yST += (hMulti + 5 * S) * vis;
                    }
                }
                if (setting instanceof BooleanSetting s) {
                    float vis = visAnim(select, s);
                    if (vis > 0.01F) {
                        if (s.getVisible().get() && click.button() == 0 && MathUtil.isHovered(mouseX, mouseY, xST, yST, wST, 16 * S)) { s.set(!s.getValue()); GuiSounds.toggle(s.getValue()); }
                        yST += 21 * S * vis;
                    }
                }
                if (setting instanceof ButtonSetting s) {
                    float vis = visAnim(select, s);
                    if (vis > 0.01F) {
                        if (s.getVisible().get() && click.button() == 0 && MathUtil.isHovered(mouseX, mouseY, xST, yST, wST, 16 * S)) { s.press(); GuiSounds.button(); }
                        yST += 21 * S * vis;
                    }
                }
                if (setting instanceof BindSetting s) {
                    float vis = visAnim(select, s);
                    if (vis > 0.01F) {
                        if (s.getVisible().get() && click.button() == 0) {
                            String keyName = s.get() == -1 ? "n/a" : Keyboard.keyName(s.get());
                            float bw = draw.getWidth(keyName, 6 * S) + 10 * S;
                            if (MathUtil.isHovered(mouseX, mouseY, xST + wST - 6 * S - bw - 2 * S, yST + 1 * S, bw + 4 * S, 14 * S)) { activeBind = s; GuiSounds.bindStart(); }
                        }
                        yST += 21 * S * vis;
                    }
                }
                if (setting instanceof StringSetting s) {
                    float vis = visAnim(select, s);
                    if (vis > 0.01F) {
                        if (s.getVisible().get() && click.button() == 0) {
                            String shown = s.getValue().isEmpty() ? "..." : s.getValue();
                            float bw = Math.min(draw.getWidth(shown, 6 * S) + 10 * S, wST * 0.55F);
                            if (MathUtil.isHovered(mouseX, mouseY, xST + wST - 6 * S - bw - 2 * S, yST + 1 * S, bw + 4 * S, 14 * S)) { activeString = s; stringBuffer = s.getValue(); GuiSounds.editStart(); }
                        }
                        yST += 21 * S * vis;
                    }
                }
                if (setting instanceof ColorSetting s) {
                    float vis = visAnim(select, s);
                    if (vis > 0.01F) {
                        float open = s.pickerAnim.getOutput();
                        float hCol = 16 * S + open * 35 * S;
                        if (s.getVisible().get() && click.button() == 0) {
                            if (MathUtil.isHovered(mouseX, mouseY, xST, yST, wST, 16 * S)) { s.pickerOpen = !s.pickerOpen; GuiSounds.picker(s.pickerOpen); }
                            else if (s.pickerOpen) {
                                for (int bar = 0; bar < 3; bar++) {
                                    float by = yST + 18 * S + bar * 12 * S;
                                    if (MathUtil.isHovered(mouseX, mouseY, xST + 6 * S, by - 3 * S, wST - 12 * S, 10 * S)) { draggingColor = s; draggingColorBar = bar; GuiSounds.sliderGrab(); }
                                }
                            }
                        }
                        yST += (hCol + 5 * S) * vis;
                    }
                }
            }
        }
        // ── удаление Lua-модуля: два клика для подтверждения ──
        if (deleteRect != null && select instanceof ru.white.script.LuaModule luaDelete
                && click.button() == 0
                && MathUtil.isHovered(mouseX, mouseY, deleteRect[0], deleteRect[1], deleteRect[2], deleteRect[3])) {
            long nowMs = System.currentTimeMillis();
            if (deleteArmModule != select || nowMs > deleteArmUntil) {
                deleteArmModule = select;
                deleteArmUntil = nowMs + 3000L;
                GuiSounds.editStart();
            } else {
                String name = select.getBigName();
                if (ru.white.script.LuaScriptManager.get().delete(luaDelete)) {
                    ru.white.utils.math.ChatUtils.addChatMessage("§7[Lua] §fмодуль «" + name + "» удалён");
                }
                select = null;
                deleteArmModule = null;
                settingScrollTarget = 0;
                settingScrollAnim = 0;
                GuiSounds.button();
            }
            return true;
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
        if (draggingSlider != null || draggingColor != null) GuiSounds.sliderRelease();
        draggingSlider = null; draggingColor = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        OverlayEditor editor = OverlayEditors.active();
        if (editor != null) { if (key == 256) editor.saveAndExit(); return true; }

        if (bindingModule != null) {
            boolean reset = key == 256 || key == 261;
            bindingModule.setKey(reset ? -1 : key); bindingModule = null;
            if (reset) GuiSounds.bindReset(); else GuiSounds.bindSet(); return true;
        }

        if (activeBind != null) {
            boolean reset = key == 256 || key == 261;
            activeBind.set(reset ? -1 : key); activeBind = null;
            if (reset) GuiSounds.bindReset(); else GuiSounds.bindSet(); return true;
        }

        if (searchActive) {
            if (key == 256) { if (!searchQuery.isEmpty()) clearSearch(); else searchActive = false; GuiSounds.searchClear(); }
            else if (key == 257 || key == 335) { searchActive = false; GuiSounds.editCommit(); }
            else if (key == 259 && !searchQuery.isEmpty()) { searchQuery = searchQuery.substring(0, searchQuery.length() - 1); searchChanged(); GuiSounds.erase(); }
            return true;
        }

        if (activeString != null) {
            if (key == 257 || key == 335) { activeString.set(stringBuffer); activeString = null; GuiSounds.editCommit(); }
            else if (key == 256) { activeString = null; GuiSounds.editCancel(); }
            else if (key == 259 && !stringBuffer.isEmpty()) { stringBuffer = stringBuffer.substring(0, stringBuffer.length() - 1); GuiSounds.erase(); }
            return true;
        }

        // бинд Click Gui (по умолчанию правый шифт) закрывает меню — открытие/закрытие одной клавишей
        int toggle = toggleKey();
        if (toggle != -1 && key == toggle) {
            if (toggleArmed) startExit();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (searchActive) {
            if (input.isValidChar() && searchQuery.length() < SEARCH_LIMIT) { searchQuery += input.asString(); searchChanged(); GuiSounds.type(); }
            return true;
        }
        if (activeString != null) {
            if (input.isValidChar()) {
                String str = input.asString();
                if (!activeString.isOnlyNumber() || str.matches("[0-9.,-]+")) { stringBuffer += str; GuiSounds.type(); }
            }
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean mouseScrolled(double mouseXRaw, double mouseYRaw, double horizontalAmount, double verticalAmount) {
        float mouseX = (float) (mouseXRaw / scaleFix);
        float mouseY = (float) (mouseYRaw / scaleFix);
        OverlayEditor editor = OverlayEditors.active();
        if (editor != null) { return editor.mouseScrolled(mouseX, mouseY, verticalAmount); }

        int screenWidth  = (int) (mc.getWindow().getScaledWidth()  / scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);

        float w = 440 * S;
        float h = 316 * S;
        float x = screenWidth / 2F - w / 2;
        float y = screenHeight / 2F - h / 2;

        if (MathUtil.isHovered(mouseX, mouseY, x + 6 * S, y + 76 * S, 152 * S, h - 82 * S)) {
            float before = scrollTarget;
            scrollTarget = MathUtil.clamp((float) (scrollTarget - verticalAmount * 25 * S), 0, maxScroll);
            if (scrollTarget != before) GuiSounds.scroll(); return true;
        }

        if (MathUtil.isHovered(mouseX, mouseY, x + 164 * S, y + 76 * S, w - 170 * S, h - 82 * S)) {
            float before = settingScrollTarget;
            settingScrollTarget = MathUtil.clamp((float) (settingScrollTarget - verticalAmount * 25 * S), 0, settingMaxScroll);
            if (settingScrollTarget != before) GuiSounds.scroll(); return true;
        }

        return super.mouseScrolled(mouseXRaw, mouseYRaw, horizontalAmount, verticalAmount);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean shouldCloseOnEsc() {
        OverlayEditor editor = OverlayEditors.active();
        if (editor != null) { editor.saveAndExit(); return false; }
        startExit(); return false;
    }
}

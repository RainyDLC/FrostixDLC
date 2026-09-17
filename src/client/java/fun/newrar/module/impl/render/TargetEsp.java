package fun.newrar.module.impl.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import fun.newrar.manager.event_impl.EventRender3D;
import fun.newrar.manager.event_impl.EventTick;
import fun.newrar.manager.event_impl.WorldLoadEvent;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import fun.newrar.module.api.preview.ModulePreview;
import fun.newrar.module.api.preview.PreviewContext;
import fun.newrar.module.api.preview.PreviewSettings;
import fun.newrar.module.api.settings.impl.BooleanSetting;
import fun.newrar.module.api.settings.impl.ButtonSetting;
import fun.newrar.module.api.settings.impl.ColorSetting;
import fun.newrar.module.api.settings.impl.ModeSetting;
import fun.newrar.module.api.settings.impl.SliderSetting;

import fun.newrar.module.impl.combat.AttackAura;
import fun.newrar.module.impl.combat.TriggerBot;
import fun.newrar.utils.animation.Animation;
import fun.newrar.utils.animation.Easings;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.other.Instance;
import fun.newrar.utils.render.ScanTargetEspRenderTypes;
import fun.newrar.utils.math.MathUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import static net.minecraft.client.gl.RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET;

@ModuleInfo(
        name = "Target Esp",
        desc = "Визуальное выделение текущей цели атаки анимированными эффектами и маркерами",
        category = Category.RENDER
)
public class TargetEsp extends Module implements ModulePreview {
    public static TargetEsp getInstance() {
        return Instance.get(TargetEsp.class);
    }

    public ButtonSetting previewButton = PreviewSettings.button(this);

    public ModeSetting type = new ModeSetting(this,"Режим","Призраки","Картинка","Кольцо","Бублик","Кубики","Молнии","Кристаллы","Пентаграмма","Снег","Сердце","Огонь","Мечи","Цепь","Куб","Блум","Цепи","Переливание");

    public SliderSetting saturation = new SliderSetting(this, "Насыщенность", 100.0f, 0.0f, 200.0f, 5.0f).setVisible(() -> type.is("Переливание"));
    public SliderSetting scanSpeed = new SliderSetting(this, "Скорость переливания", 1.0f, 0.25f, 3.0f, 0.05f).setVisible(() -> type.is("Переливание"));
    public ColorSetting scanColor = new ColorSetting(this, "Цвет переливания", ColorUtil.getColor(55, 170, 255, 255)).setVisible(() -> type.is("Переливание"));
    public BooleanSetting scanColorFlow = new BooleanSetting(this, "Переливание цветов", false).setVisible(() -> type.is("Переливание"));
    public ColorSetting scanSecondColor = new ColorSetting(this, "Второй цвет", ColorUtil.getColor(190, 75, 255, 255)).setVisible(() -> type.is("Переливание") && scanColorFlow.getValue());
    public SliderSetting scanGlow = new SliderSetting(this, "Яркость переливания", 100.0f, 25.0f, 200.0f, 5.0f).setVisible(() -> type.is("Переливание"));

    public float saturation() {
        return this.saturation.getValue() / 100.0f;
    }

    public float scanSpeed() {
        return this.scanSpeed.getValue();
    }

    public int scanColor() {
        return this.scanColor.getValue();
    }

    public int scanSecondColor() {
        return this.scanColorFlow.getValue()
                ? this.scanSecondColor.getValue()
                : this.scanColor.getValue();
    }

    public float scanGlow() {
        return this.scanGlow.getValue() / 100.0f;
    }

    public float renderAnimation() {
        return this.alpha.get();
    }

    public boolean isRenderedTarget(LivingEntity entity) {
        if (!isEnabled() || !type.is("Переливание")) return false;
        LivingEntity currentTarget = previewTarget != null ? previewTarget : (AttackAura.target != null ? AttackAura.target : TriggerBot.targets);
        return entity != null && entity == currentTarget;
    }

    public SliderSetting bloomCount = new SliderSetting(this, "Количество", 36, 12, 64, 2).setVisible(() -> type.is("Блум"));
    public SliderSetting bloomSize = new SliderSetting(this, "Размер", 0.20F, 0.08F, 0.50F, 0.02F).setVisible(() -> type.is("Блум"));
    public SliderSetting bloomSpeed = new SliderSetting(this, "Скорость", 1.0F, 0.2F, 3.0F, 0.1F).setVisible(() -> type.is("Блум"));

    public final BloomEspRenderer bloomRenderer = new BloomEspRenderer();

    public Identifier getBloomTexture() {
        return BloomEspRenderer.DEFAULT_BLOOM_TEXTURE;
    }

    public ModeSetting typeGhost = new ModeSetting(this, "Тип призраков", "1", "2", "3", "4").setVisible(() -> type.is("Призраки"));
    public ModeSetting typeImages = new ModeSetting(this, "Тип картинки", "1", "2", "3", "4").setVisible(() -> type.is("Картинка"));

    public SliderSetting speed = new SliderSetting(this,"Скорость призраков",900,400,1500,50).setVisible(() -> type.is("Призраки"));
    public SliderSetting sizeGlow = new SliderSetting(this,"Сила свечения",0.35F,0.1F,0.75F,0.05F).setVisible(() -> type.is("Призраки"));
    public SliderSetting sizeGlowOFF = new SliderSetting(this,"Размер свечения",0.7F,0.1F,2F,0.05F).setVisible(() -> type.is("Призраки"));

    public SliderSetting speed_2 = new SliderSetting(this,"Скорость картинки",1000,500,3000,100).setVisible(() -> type.is("Картинка"));

    public SliderSetting size_2 = new SliderSetting(this,"Размер картинки",1,0.25F,3,0.05F).setVisible(() -> type.is("Картинка"));

    public SliderSetting ringSpeed = new SliderSetting(this,"Скорость кольца",1800,600,4000,100).setVisible(() -> type.is("Кольцо"));
    public SliderSetting bublikSpeed = new SliderSetting(this,"Скорость бублика",1400,600,4000,100).setVisible(() -> type.is("Бублик"));
    public SliderSetting bublikSize = new SliderSetting(this,"Размер бублика",1,0.5F,2.5F,0.05F).setVisible(() -> type.is("Бублик"));

    public SliderSetting lightningCount = new SliderSetting(this,"Кол-во молний",16,4,48,1).setVisible(() -> type.is("Молнии"));
    public SliderSetting lightningSpeed = new SliderSetting(this,"Скорость молний",42,10,120,1).setVisible(() -> type.is("Молнии"));
    public BooleanSetting lightningHit = new BooleanSetting(this,"Красный при ударе", false).setVisible(() -> type.is("Молнии"));

    public final LightningRenderer lightningRenderer = new LightningRenderer();

    public SliderSetting crystalCount = new SliderSetting(this,"Кол-во кристаллов",14,4,32,1).setVisible(() -> type.is("Кристаллы"));
    public SliderSetting crystalSpeed = new SliderSetting(this,"Скорость вращения",1.0F,0.1F,3.0F,0.05F).setVisible(() -> type.is("Кристаллы"));
    public SliderSetting crystalRadius = new SliderSetting(this,"Радиус орбиты",1.0F,0.4F,2.5F,0.05F).setVisible(() -> type.is("Кристаллы"));
    public SliderSetting crystalSize = new SliderSetting(this,"Размер кристаллов",0.12F,0.04F,0.35F,0.01F).setVisible(() -> type.is("Кристаллы"));

    public SliderSetting pentaRadius = new SliderSetting(this,"Радиус пентаграммы",1.4F,0.8F,3.0F,0.05F).setVisible(() -> type.is("Пентаграмма"));
    public SliderSetting pentaSpeed = new SliderSetting(this,"Скорость вращения",0.6F,0.1F,3.0F,0.05F).setVisible(() -> type.is("Пентаграмма"));

    public SliderSetting snowCount = new SliderSetting(this,"Кол-во снежинок",12,4,24,1).setVisible(() -> type.is("Снег"));
    public SliderSetting snowSpeed = new SliderSetting(this,"Скорость вращения",0.6F,0.1F,3.0F,0.05F).setVisible(() -> type.is("Снег"));
    public SliderSetting snowRadius = new SliderSetting(this,"Радиус орбиты",0.9F,0.4F,2.5F,0.05F).setVisible(() -> type.is("Снег"));
    public SliderSetting snowSize = new SliderSetting(this,"Размер снежинок",0.18F,0.06F,0.35F,0.01F).setVisible(() -> type.is("Снег"));

    public SliderSetting heartSize = new SliderSetting(this,"Размер сердца",0.9F,0.4F,1.8F,0.05F).setVisible(() -> type.is("Сердце"));
    public SliderSetting heartSpeed = new SliderSetting(this,"Множитель пульса",1.0F,0.2F,3.0F,0.05F).setVisible(() -> type.is("Сердце"));

    public SliderSetting fireCount = new SliderSetting(this,"Кол-во частиц",26,8,64,1).setVisible(() -> type.is("Огонь"));
    public SliderSetting fireSpeed = new SliderSetting(this,"Скорость вращения",1.0F,0.2F,3.0F,0.05F).setVisible(() -> type.is("Огонь"));
    public SliderSetting fireRadius = new SliderSetting(this,"Радиус вихря",0.7F,0.3F,1.5F,0.05F).setVisible(() -> type.is("Огонь"));
    public SliderSetting fireHeight = new SliderSetting(this,"Сила пламени",1.0F,0.5F,2.0F,0.05F).setVisible(() -> type.is("Огонь"));

    public SliderSetting swordsCount = new SliderSetting(this,"Кол-во мечей",4,2,10,1).setVisible(() -> type.is("Мечи"));
    public SliderSetting swordsSpeed = new SliderSetting(this,"Скорость вращения",1.0F,0.1F,3.0F,0.05F).setVisible(() -> type.is("Мечи"));
    public SliderSetting swordsRadius = new SliderSetting(this,"Радиус орбиты",0.8F,0.4F,2.0F,0.05F).setVisible(() -> type.is("Мечи"));
    public SliderSetting swordsSize = new SliderSetting(this,"Размер мечей",0.7F,0.3F,1.5F,0.05F).setVisible(() -> type.is("Мечи"));

    public SliderSetting linkCount = new SliderSetting(this,"Кол-во звеньев",12,6,24,1).setVisible(() -> type.is("Цепь"));
    public SliderSetting linkSpeed = new SliderSetting(this,"Скорость вращения",0.8F,0.1F,3.0F,0.05F).setVisible(() -> type.is("Цепь"));
    public SliderSetting linkRadius = new SliderSetting(this,"Радиус кольца",0.9F,0.5F,2.0F,0.05F).setVisible(() -> type.is("Цепь"));
    public SliderSetting linkSize = new SliderSetting(this,"Размер звеньев",0.09F,0.04F,0.2F,0.01F).setVisible(() -> type.is("Цепь"));

    public SliderSetting cubeSize = new SliderSetting(this,"Размер куба",1.15F,0.6F,2.5F,0.05F).setVisible(() -> type.is("Куб"));
    public SliderSetting cubeRotateSpeed = new SliderSetting(this,"Скорость вращения куба",1.2F,0.2F,4.0F,0.05F).setVisible(() -> type.is("Куб"));
    public SliderSetting cubeThickness = new SliderSetting(this,"Толщина куба",2.2F,0.6F,5.0F,0.1F).setVisible(() -> type.is("Куб"));
    public BooleanSetting cubeColorOnHit = new BooleanSetting(this,"Цвет куба при ударе",true).setVisible(() -> type.is("Куб"));
    public BooleanSetting cubeGlow = new BooleanSetting(this,"Свечение куба",true).setVisible(() -> type.is("Куб"));

    private final PreviewSettings previewSettings = PreviewSettings.of(this, 4F, 0F, 2F);

    private final BufferAllocator boxAllocator = new BufferAllocator(1 << 18);

    private static final RenderPipeline CUBE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/aura_target_cube"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build()
    );

    private static final RenderLayer CUBE_LAYER = RenderLayer.of(
            "aura_target_cube",
            RenderSetup.builder(CUBE_PIPELINE)
                    .translucent()
                    .expectedBufferSize(1 << 14)
                    .build()
    );

    private float cubeRotationY;
    private float cubeRotationX;
    private float cubeTargetY;
    private float cubeTargetX;
    private float cubeHitFlash;
    private int cubePrevHurtTime;
    private long cubeLastUpdate = System.currentTimeMillis();
    private LivingEntity cubeTrackedTarget;
    private Vec3d cubeLastCenter;
    private float cubeLastHalf = 0.6F;
    private double cubeLastFeetY;
    private boolean cubeShattered;
    private final List<CubeFragment> cubeFragments = new ArrayList<>();
    private final Random cubeRandom = new Random();
    private Matrix4f cubeMatrix;

    private long heartLastTime = 0L;
    private float heartPhase = 0f;

    private static final float[] SNOW_PX = new float[24], SNOW_PY = new float[24],
            SNOW_PZ = new float[24], SNOW_SPIN = new float[24];
    private static final float[] SWORD_PX = new float[16], SWORD_PY = new float[16],
            SWORD_PZ = new float[16], SWORD_TILT = new float[16];
    private static final float[] FIRE_OX = new float[64], FIRE_OY = new float[64], FIRE_OZ = new float[64],
            FIRE_ALPHA = new float[64], FIRE_H = new float[64];
    private static final int[] FIRE_RGB_OUT = new int[64], FIRE_RGB_CORE = new int[64];
    private static final float[] HEART_HX = new float[48], HEART_HY = new float[48];

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        boxAllocator.clear();
        resetCubeState();
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        resetCubeState();
    }

    @EventHandler
    public void onCubeTick(EventTick event) {
        if (!type.is("Куб")) return;

        updateCubeFragments();

        LivingEntity trackedTarget = cubeTrackedTarget;
        if (trackedTarget == null) {
            return;
        }

        boolean dead = trackedTarget.isRemoved()
                || trackedTarget.getHealth() <= 0.0F
                || trackedTarget.isDead();
        if (dead) {
            if (!cubeShattered && cubeLastCenter != null) {
                shatterCube();
            }
            cubeShattered = true;
            cubeTrackedTarget = null;
            cubePrevHurtTime = 0;
            return;
        }

        int hurt = trackedTarget.hurtTime;
        if (hurt > cubePrevHurtTime) {
            int quartersY = 1 + cubeRandom.nextInt(3);
            int quartersX = cubeRandom.nextInt(2);
            float directionY = cubeRandom.nextBoolean() ? 1.0F : -1.0F;
            float directionX = cubeRandom.nextBoolean() ? 1.0F : -1.0F;
            cubeTargetY += directionY * 90.0F * quartersY;
            cubeTargetX += directionX * 90.0F * quartersX;
            cubeHitFlash = 1.0F;
        }
        cubePrevHurtTime = hurt;
    }

    private LivingEntity previewTarget;

    public LivingEntity target = null;
    public Animation alpha = new Animation();
    public Animation alpha_2 = new Animation();

    private float animationNurik = 0.0F;
    private long currentTimeSpirits = 0;

    @Override
    public PreviewSettings previewSettings() {
        return previewSettings;
    }

    @Override
    public boolean previewNeedsDummy() {
        return true;
    }

    @Override
    public void previewStart(PreviewContext ctx) {
        previewTarget = ctx.dummy();
    }

    @Override
    public void previewTick(PreviewContext ctx) {
        previewTarget = ctx.dummy();
    }

    @Override
    public void previewSpawn(PreviewContext ctx) {
        if (ctx.dummy() != null) ctx.dummy().hurtTime = 10;
    }

    @Override
    public void previewStop() {
        previewTarget = null;
        target = null;
        resetCubeState();
    }

    @EventHandler
    public void onRender(EventRender3D e) {
        alpha.update();

        LivingEntity currentTarget = previewTarget != null ? previewTarget : (AttackAura.target != null ? AttackAura.target : TriggerBot.targets);

        if (currentTarget != null) {
            target = currentTarget;
        }

        if (mc.world == null || mc.player == null) return;

        alpha.run(currentTarget != null ? 1 : 0, 0.15F, Easings.SINE_OUT);
        float alphaPC = alpha.get();

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(boxAllocator);
        if (alphaPC > 0.001f && target != null && type.is("Призраки") && typeGhost.is("2")) {
            long currentTime = System.currentTimeMillis();
            if (currentTimeSpirits == 0) {
                currentTimeSpirits = currentTime;
            }

            long timeDiff = currentTime - currentTimeSpirits;
            if (timeDiff > 0) {
                animationNurik += (float) (5L * timeDiff) /  this.speed.getValue().longValue();
            }
            currentTimeSpirits = currentTime;

            MatrixStack matrices = e.getMatrixStack();

            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            double x = lerpedPos.x - cameraPos.x;
            double y = lerpedPos.y  - cameraPos.y;
            double z = lerpedPos.z - cameraPos.z;

            alphaPC = (float) alpha.getValue();

            alpha_2.update();
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));
            alpha_2.run(hurtPC,0.1F,Easings.SINE_OUT);

            float atts = alpha_2.get();

            int fadeColor = ColorUtil.fade(1);
            int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * alphaPC));
            int baseColor = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC), redColor, atts);

            int n2 = 3;
            int n3 = 12;
            int n4 = 3 * n2;

            matrices.push();

            Camera camera = mc.gameRenderer.getCamera();

            for (int i = 0; i < n4; i += n2) {
                for (int j = 0; j < n3; j++) {
                    float f2 = animationNurik + (float) j * 0.1F;
                    float f3 = 0.6F;
                    float f4 = 0.4F;
                    int n5 = (int) Math.pow((double) i, 2.0F);

                    matrices.push();

                    double particleX = x + (double) (f3 * Math.sin(f2 + (float) n5));
                    double particleY = y + (double) f4 + (double) (0.3F * Math.sin(animationNurik + (float) j * 0.2F))
                            + (double) (0.2F * (float) i);
                    double particleZ = z + (double) (f3 * Math.cos(f2 - (float) n5));

                    matrices.translate(particleX, particleY, particleZ);

                    float scale =  (0.006F + (float) j / 2000.0F ) * alphaPC;
                    matrices.scale(scale, scale, scale);

                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

                    Matrix4f matrix = matrices.peek().getPositionMatrix();
                    VertexConsumer consumer = immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_3.png")));

                    int color = baseColor;

                    int n7 = -20;
                    int n8 = 35;

                    consumer.vertex(matrix, (float) n7, (float) (n7 + n8), 0.0f)
                            .color(baseColor)
                            .texture(0.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) (n7 + n8), 0.0f)
                            .color(baseColor)
                            .texture(1.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) n7, 0.0f)
                            .color(baseColor)
                            .texture(1.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) n7, (float) n7, 0.0f)
                            .color(baseColor)
                            .texture(0.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                     n7 = (int) (-20  - 20 * sizeGlowOFF.getValue());
                     n8 = (int) (35 + 40 * sizeGlowOFF.getValue());

                    consumer.vertex(matrix, (float) n7, (float) (n7 + n8), 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC * sizeGlow.getValue()))
                            .texture(0.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) (n7 + n8), 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC * sizeGlow.getValue()))
                            .texture(1.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) n7, 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC * sizeGlow.getValue()))
                            .texture(1.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) n7, (float) n7, 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC * sizeGlow.getValue()))
                            .texture(0.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    matrices.pop();
                }
            }

            matrices.pop();
        }
        if (alphaPC > 0.001f && target != null && type.is("Призраки") && typeGhost.is("4")) {
            long currentTime = System.currentTimeMillis();
            if (currentTimeSpirits == 0) {
                currentTimeSpirits = currentTime;
            }

            long timeDiff = currentTime - currentTimeSpirits;
            if (timeDiff > 0) {
                animationNurik += (float) (5L * timeDiff) /  this.speed.getValue().longValue();
            }
            currentTimeSpirits = currentTime;

            MatrixStack matrices = e.getMatrixStack();

            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            double x = lerpedPos.x - cameraPos.x;
            double y = lerpedPos.y  - cameraPos.y;
            double z = lerpedPos.z - cameraPos.z;

            alphaPC = (float) alpha.getValue();

            alpha_2.update();
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));
            alpha_2.run(hurtPC,0.1F,Easings.SINE_OUT);

            float atts = alpha_2.get();

            int fadeColor = ColorUtil.fade(1);
            int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * alphaPC));
            int baseColor = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC), redColor, atts);

            int n2 = 3;
            int n3 = 24;
            int n4 = 3 * n2;

            matrices.push();

            Camera camera = mc.gameRenderer.getCamera();

            for (int i = 0; i < n4; i += n2) {
                for (int j = 0; j < n3; j++) {
                    float f2 = animationNurik + (float) j * 0.05F;
                    float f3 = target.getWidth();
                    float f4 = 0.45F;
                    int n5 = (int) Math.pow((double) i, 2.0F);

                    matrices.push();

                    double particleX = x + (double) (f3 * Math.sin(f2 + (float) n5));
                    double particleY = y + (double) f4 + (double) (0.1F * Math.sin(animationNurik + (float) j * 0.1F))
                            + (double) (0.2F * (float) i);
                    double particleZ = z + (double) (f3 * Math.cos(f2 - (float) n5));

                    matrices.translate(particleX, particleY, particleZ);

                    float scale =  (0.009F + (float) j / 2000.0F ) * alphaPC;
                    matrices.scale(scale, scale, scale);

                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

                    Matrix4f matrix = matrices.peek().getPositionMatrix();
                    VertexConsumer consumer = immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_3.png")));

                    int color = baseColor;

                    int n7 = -12;
                    int n8 = 16;

                    consumer.vertex(matrix, (float) n7, (float) (n7 + n8), 0.0f)
                            .color(baseColor)
                            .texture(0.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) (n7 + n8), 0.0f)
                            .color(baseColor)
                            .texture(1.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) n7, 0.0f)
                            .color(baseColor)
                            .texture(1.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) n7, (float) n7, 0.0f)
                            .color(baseColor)
                            .texture(0.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    n7 = (int) (-12  - 20 * sizeGlowOFF.getValue());
                    n8 = (int) (16 + 40 * sizeGlowOFF.getValue());

                    consumer.vertex(matrix, (float) n7, (float) (n7 + n8), 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC * sizeGlow.getValue()))
                            .texture(0.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) (n7 + n8), 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC * sizeGlow.getValue()))
                            .texture(1.0F, 1.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) (n7 + n8), (float) n7, 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC * sizeGlow.getValue()))
                            .texture(1.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    consumer.vertex(matrix, (float) n7, (float) n7, 0.0f)
                            .color(ColorUtil.replAlpha(baseColor,alphaPC * sizeGlow.getValue()))
                            .texture(0.0F, 0.0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(0xF000F0)
                            .normal(0, 0, 1);

                    matrices.pop();
                }
            }

            matrices.pop();
        }
        if (alphaPC > 0.001f && target != null && type.is("Призраки") && typeGhost.is("1")) {
            long speed_f = this.speed.getValue().longValue() ;

            long currentTime = System.currentTimeMillis();
            if (currentTimeSpirits == 0) {
                currentTimeSpirits = currentTime;
            }

            long timeDiff = currentTime - currentTimeSpirits;
            if (timeDiff > 0) {
                animationNurik += (float) (5L * timeDiff) / speed_f;
            }
            currentTimeSpirits = currentTime;

            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));
            int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0F * alphaPC));
            int color = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(0), alphaPC), redColor, hurtPC);
            int color2 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(90), alphaPC), redColor,  hurtPC);
            int color3 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(180), alphaPC), redColor, hurtPC);
            int color4 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(360), alphaPC), redColor, hurtPC);

            MatrixStack matrices = e.getMatrixStack();

            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            long time = System.currentTimeMillis();
            double speed = (double) time / speed_f;
            float radius = target.getWidth()  + 0.25F - 0.25F * alphaPC;
            int ghostCount = 3;
            int cound = 10;

            for (int i = 0; i < ghostCount; i++) {
                for (int s = 0; s < cound; s++) {
                    matrices.push();

                    float f2 = animationNurik + (float) s * 0.1F;

                    double angle = speed + (i * (Math.PI * 2 / ghostCount));

                    double offX = Math.cos(angle + f2) * radius;
                    double offZ = Math.sin(angle + f2) * radius;

                    double offY = Math.sin(speed + i) * 0.6 + 0.15  * Math.sin(speed + (float) s * 0.11F);

                    matrices.translate(
                            lerpedPos.x - cameraPos.x + offX,
                            lerpedPos.y - cameraPos.y + (target.getHeight() / 2.0F) + offY,
                            lerpedPos.z - cameraPos.z + offZ
                    );

                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));

                    float scale =  ((0.12F + (float) s /40.0F) * 1.25F) * alphaPC;
                    matrices.scale(scale, scale, scale);

                    Matrix4f matrix = matrices.peek().getPositionMatrix();
                    VertexConsumer consumer = immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_3.png")));

                    drawGradientQuad(consumer, matrix, color,color2,color3,color4, (int) (255 * alphaPC));

                    scale = ((0.12F + (float) s /40.0F) * (100 * sizeGlowOFF.getValue()) / 10) * alphaPC;
                    matrices.scale(scale, scale, scale);
                    VertexConsumer consumer2 = immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_2.png")));

                    drawGradientQuad(consumer2, matrix, color,color2,color3,color4, (int) ((255 * sizeGlow.getValue() * 1) * alphaPC));

                    matrices.pop();
                }
            }
        }
        if (alphaPC > 0.001f && target != null && type.is("Призраки") && typeGhost.is("3")) {
            long speed_f = this.speed.getValue().longValue() ;

            long currentTime = System.currentTimeMillis();
            if (currentTimeSpirits == 0) {
                currentTimeSpirits = currentTime;
            }

            long timeDiff = currentTime - currentTimeSpirits;
            if (timeDiff > 0) {
                animationNurik += (float) (5L * timeDiff) / speed_f;
            }
            currentTimeSpirits = currentTime;

            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));
            int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0F * alphaPC));
            int color = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(0), alphaPC), redColor, hurtPC);
            int color2 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(90), alphaPC), redColor,  hurtPC);
            int color3 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(180), alphaPC), redColor, hurtPC);
            int color4 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(360), alphaPC), redColor, hurtPC);

            MatrixStack matrices = e.getMatrixStack();

            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            long time = System.currentTimeMillis();
            double speed = (double) time / speed_f;
            float radius = target.getWidth()  + 0.25F - 0.25F * alphaPC;
            int ghostCount = 3;
            int cound = 9;

            for (int i = 0; i < ghostCount; i++) {
                for (int s = 0; s < cound; s++) {
                    matrices.push();

                    float f2 = animationNurik + (float) s * 0.1F;

                    double angle = speed + (i * (Math.PI * 2 / ghostCount));

                    double offX = Math.cos(angle + f2) * radius;
                    double offZ = Math.sin(angle + f2) * radius;

                    double offY = Math.sin(speed ) * 0.7  + 0.2  * Math.sin(speed + (float) s * 0.11F);

                    matrices.translate(
                            lerpedPos.x - cameraPos.x + offX,
                            lerpedPos.y - cameraPos.y + (target.getHeight() / 2.0F) + offY,
                            lerpedPos.z - cameraPos.z + offZ
                    );

                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));

                    float scale =  (0.12F + (float) s /50.0F) * 1.5F;
                    matrices.scale(scale, scale, scale);

                    Matrix4f matrix = matrices.peek().getPositionMatrix();
                    VertexConsumer consumer = immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_3.png")));

                    drawGradientQuad(consumer, matrix, color,color2,color3,color4, (int) (255 * alphaPC));

                    scale = (0.12F + (float) s /50.0F) * (100 * sizeGlowOFF.getValue()) / 10;
                    matrices.scale(scale, scale, scale);
                    VertexConsumer consumer2 = immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_2.png")));

                    drawGradientQuad(consumer2, matrix, color,color2,color3,color4, (int) ((255 * sizeGlow.getValue() * 1) * alphaPC));

                    matrices.pop();
                }
            }
        }

        if (alphaPC > 0.001f && target != null && type.is("Картинка")) {
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));

            alpha_2.update();
            alpha_2.run(hurtPC,0.15F,Easings.SINE_OUT);

            int redColor = ColorUtil.getColor(185, 80, 80, (int) (255.0F * alphaPC));
            int color = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(0), alphaPC), redColor, alpha_2.get());
            int color2 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(90), alphaPC), redColor, alpha_2.get());
            int color3 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(180), alphaPC), redColor, alpha_2.get());
            int color4 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(360), alphaPC), redColor,alpha_2.get());

            MatrixStack matrices = e.getMatrixStack();

            VertexConsumer consumer = immediate
                    .getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/target.png")));

            if(typeImages.is("2")) {
                 consumer = immediate
                        .getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/marker.png")));
            }
            if(typeImages.is("3")) {
                consumer = immediate
                        .getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/targets.png")));
            }
            if(typeImages.is("4")) {
                consumer = immediate
                        .getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/target1.png")));
            }

            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            double x = lerpedPos.x;
            double y = lerpedPos.y;
            double z = lerpedPos.z;

            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            matrices.push();
            matrices.translate(x - cameraPos.x, y - cameraPos.y + target.getHeight() / 1.75F, z - cameraPos.z);

            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));

            long currentTimeMillis = System.currentTimeMillis();
            float rotate = (float) MathUtil.clamps(0, 360 * 2, ((Math.sin(currentTimeMillis / speed_2.getValue().doubleValue()) + 1F) / 2F) * 360 * 2);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotate));

            float size = (size_2.getValue() - size_2.getValue() * 0.4F * alpha_2.get()) + 0.8F - 0.8F * alphaPC;
            matrices.scale(size, size, 1);

            Matrix4f bloomMatrix = matrices.peek().getPositionMatrix();

            drawGradientQuad(consumer, bloomMatrix, color,color2,color3,color4, (int) (255 * alphaPC));

            matrices.pop();
        }

        if (alphaPC > 0.001f && target != null && type.is("Бублик")) {
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));

            alpha_2.update();
            alpha_2.run(hurtPC,0.1F,Easings.SINE_OUT);

            int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0F * alphaPC));
            int color = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(0), alphaPC), redColor, alpha_2.get());
            int color2 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(90), alphaPC), redColor, alpha_2.get());
            int color3 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(180), alphaPC), redColor, alpha_2.get());
            int color4 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(360), alphaPC), redColor, alpha_2.get());

            MatrixStack matrices = e.getMatrixStack();
            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            double duration = bublikSpeed.getValue().doubleValue();
            double elapsed = System.currentTimeMillis() % duration;
            boolean down = elapsed > duration / 2.0;
            double raw = elapsed / (duration / 2.0);
            raw = down ? raw - 1.0 : 1.0 - raw;
            double progress = raw < 0.5
                    ? 2.0 * raw * raw
                    : 1.0 - Math.pow(-2.0 * raw + 2.0, 2.0) / 2.0;

            float height = target.getHeight();
            float yBase = 0.12F + (float) progress * Math.max(0.2F, height - 0.24F);
            float direction = down ? -1F : 1F;
            double spin = System.currentTimeMillis() / (bublikSpeed.getValue().doubleValue() * 0.55);
            float radius = (target.getWidth() - 0.15F ) ;
            int count = (int) (80 * alphaPC);

            for (int layer = 2; layer >= 0; layer--) {
                float layerAlpha = alphaPC * (1F - layer * 0.28F);
                float yLayer = yBase + direction * layer * 0.075F;
                float radiusLayer = radius + layer * 0.025F;

                for (int i = 0; i < count; i++) {
                    float pc = i / (float) count;
                    double angle = pc * MathHelper.TAU + spin + layer * 0.18;
                    double x = Math.cos(angle) * radiusLayer;
                    double z = Math.sin(angle) * radiusLayer;
                    float size = (0.2F + layer * 0.018F) * bublikSize.getValue();
                    int localAlpha = (int) ((layer == 0 ? 255 : 105) * layerAlpha);

                    if (layer != 0) continue;

                    matrices.push();
                    matrices.translate(
                            lerpedPos.x - cameraPos.x + x,
                            lerpedPos.y - cameraPos.y + yLayer,
                            lerpedPos.z - cameraPos.z + z
                    );
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.toDegrees(-angle) + (float) (spin * 80.0)));
                    matrices.scale(size * 1.25F, size * 1.25F, size);
                    drawGradientQuad(immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_3.png"))),
                            matrices.peek().getPositionMatrix(), color, color2, color3, color4, localAlpha);
                    matrices.pop();
                }
            }
        }

        if (alphaPC > 0.001f && target != null && type.is("Кольцо")) {
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 10.0));
            int redColor = ColorUtil.getColor(255, 100, 100, (int)(255.0F * alphaPC));

            double duration = ringSpeed.getValue().doubleValue();
            double elapsed  = System.currentTimeMillis() % duration;
            boolean side    = elapsed > duration / 2.0;
            double raw      = elapsed / (duration / 2.0);
            raw = side ? (raw - 1.0) : 1.0 - raw;
            double progress = raw < 0.5
                    ? 2.0 * raw * raw
                    : 1.0 - Math.pow(-2.0 * raw + 2.0, 2.0) / 2.0;

            float height2 = target.getHeight() ;
            double eased  = (height2 / 1.7) * (progress > 0.5 ? 1.0 - progress : progress) * (side ? -1 : 1) /0.7;

            MatrixStack matrices = e.getMatrixStack();
            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            matrices.push();
            matrices.translate(lerpedPos.x - cameraPos.x, lerpedPos.y - cameraPos.y, lerpedPos.z - cameraPos.z);
            Matrix4f matrix = matrices.peek().getPositionMatrix();

            float radius = (target.getWidth() - 0.1F) + 0.35F - 0.35F * alphaPC;
            float yBase  = (float)(height2 * progress);
            float yTop   = (float)(height2 * progress + eased);

            VertexConsumer fillBuf = immediate.getBuffer(RING_FILL_LAYER);
            for (int seg = 0; seg < 360; seg++) {
                float a0 = (float) Math.toRadians(seg);
                float a1 = (float) Math.toRadians(seg + 1);
                int c0 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(seg ), alphaPC), redColor, hurtPC);
                int c1 = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade((seg  )), alphaPC), redColor, hurtPC);
                float x0 = (float)(Math.cos(a0) * radius), z0 = (float)(Math.sin(a0) * radius);
                float x1 = (float)(Math.cos(a1) * radius), z1 = (float)(Math.sin(a1) * radius);
                fillBuf.vertex(matrix, x0, yBase, z0).color(ColorUtil.replAlpha(c0, (int)(120 * alphaPC)));
                fillBuf.vertex(matrix, x1, yBase, z1).color(ColorUtil.replAlpha(c1, (int)(120 * alphaPC)));
                fillBuf.vertex(matrix, x1, yTop,  z1).color(ColorUtil.replAlpha(c1, 0));
                fillBuf.vertex(matrix, x0, yTop,  z0).color(ColorUtil.replAlpha(c0, 0));
            }

            VertexConsumer lineBuf = immediate.getBuffer(RING_LINE_LAYER);
            for (int seg = 0; seg < 360; seg++) {
                float a0 = (float) Math.toRadians(seg);
                float a1 = (float) Math.toRadians(seg + 1);
                int c =ColorUtil.multAlpha(ColorUtil.overCol(ColorUtil.multBright(ColorUtil.fade(1),0.7F),redColor,hurtPC), alphaPC);
                lineBuf.vertex(matrix, (float)(Math.cos(a0) * radius), yBase, (float)(Math.sin(a0) * radius)).color(ColorUtil.replAlpha(c, (int)(150 * alphaPC)));
                lineBuf.vertex(matrix, (float)(Math.cos(a1) * radius), yBase, (float)(Math.sin(a1) * radius)).color(ColorUtil.replAlpha(c, (int)(150 * alphaPC)));
            }

            matrices.pop();
        }

        if (alphaPC > 0.001f && target != null && type.is("Молнии")) {
            lightningRenderer.maxBolts = lightningCount.getValue().intValue();
            lightningRenderer.spawnIntervalMs = Math.max(10L, 132L - lightningSpeed.getValue().longValue());
            lightningRenderer.redOnHit = lightningHit.getValue();
            lightningRenderer.render(e, immediate, target, alphaPC);
        }

        if (alphaPC > 0.001f && target != null && type.is("Кубики")) {
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 10.0));

            alpha_2.update();
            alpha_2.run(hurtPC,0.1F,Easings.SINE_OUT);

            int redColor = ColorUtil.getColor(255, 100, 100, (int)(255.0f * alphaPC));
            int color = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(1), alphaPC), redColor, alpha_2.get());

            MatrixStack matrices = e.getMatrixStack();
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
            Vec3d targetPos = target.getLerpedPos(e.getTickDelta());

            float time = (float) ((Math.cos(System.currentTimeMillis() / (2000D ))) ) * 360 + (alpha_2.get() * 20);
            int cound = 12;
            float width = target.getWidth() * 1.5f ;
            float sizeFI = (1f - 0.3F * alpha_2.get()) * alphaPC;

            Camera camera = mc.gameRenderer.getCamera();

            for (int i = 0; i < 360; i += cound) {
                float val = 1.2f - 0.5f ;
                float sin = (float)(Math.sin((float) Math.toRadians(i + time)) * width * val);
                float cos = (float)(Math.cos((float) Math.toRadians(i + time)) * width * val);

                double x = targetPos.x + sin;
                double z = targetPos.z + cos;
                double y = targetPos.y + target.getHeight() * Math.abs(MathUtil.sin(i));

                matrices.push();
                matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
                matrices.multiply(camera.getRotation());
                float gs = 0.6f * sizeFI;
                matrices.scale(gs, gs, gs);
                drawGradientQuad(immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_1.png"))),
                        matrices.peek().getPositionMatrix(),
                        ColorUtil.multAlpha(color, 0.3f), ColorUtil.multAlpha(color, 0.3f),
                        ColorUtil.multAlpha(color, 0.3f), ColorUtil.multAlpha(color, 0.3f),
                        (int)(alphaPC * 0.35f * 255));
                matrices.pop();
            }

            for (int i = 0; i < 360; i += cound) {
                float val = 1.2f - 0.5f ;
                float sin = (float)(Math.sin((float) Math.toRadians(i + time)) * width * val);
                float cos = (float)(Math.cos((float) Math.toRadians(i + time)) * width * val);

                double x = targetPos.x + sin;
                double z = targetPos.z + cos;
                double y = targetPos.y + target.getHeight() * Math.abs(MathUtil.sin(i));

                Vec3d cubePos = new Vec3d(x, y, z);
                Vector3f directionToTarget = new Vector3f(
                        (float)(targetPos.x - cubePos.x),
                        (float)(targetPos.y - cubePos.y),
                        (float)(targetPos.z - cubePos.z)
                ).normalize();

                matrices.push();
                matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
                matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), directionToTarget));
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                float size = 0.06f * sizeFI;
                drawCubeFillTESP(immediate.getBuffer(RING_FILL_LAYER), matrix, size,
                        ColorUtil.replAlpha(color, (int)(alphaPC * 0.2f * 255)));
                matrices.pop();
            }

            for (int i = 0; i < 360; i += cound) {
                float val = 1.2f - 0.5f ;
                float sin = (float)(Math.sin((float) Math.toRadians(i + time)) * width * val);
                float cos = (float)(Math.cos((float) Math.toRadians(i + time)) * width * val);

                double x = targetPos.x + sin;
                double z = targetPos.z + cos;
                double y = targetPos.y + target.getHeight() * Math.abs(MathUtil.sin(i));

                Vec3d cubePos = new Vec3d(x, y, z);
                Vector3f directionToTarget = new Vector3f(
                        (float)(targetPos.x - cubePos.x),
                        (float)(targetPos.y - cubePos.y),
                        (float)(targetPos.z - cubePos.z)
                ).normalize();

                matrices.push();
                matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
                matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), directionToTarget));
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                float size = 0.06f * sizeFI;
                drawCubeOutlineTESP(immediate.getBuffer(RING_LINE_LAYER), matrix, size,
                        ColorUtil.replAlpha(color, (int)(alphaPC * 255)));
                matrices.pop();
            }
        }

        if (alphaPC > 0.001f && target != null && type.is("Блум")) {
            bloomRenderer.render(e, immediate, target, alphaPC,
                    Math.round(bloomCount.getValue()),
                    bloomSize.getValue(),
                    bloomSpeed.getValue(),
                    getBloomTexture());
        }

        if (alphaPC > 0.001f && target != null && type.is("Цепи")) {
            MatrixStack matrices = e.getMatrixStack();
            Vec3d lerpedPos = target.getLerpedPos(e.getTickDelta());
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

            matrices.push();
            matrices.translate(lerpedPos.x - cameraPos.x, lerpedPos.y - cameraPos.y, lerpedPos.z - cameraPos.z);

            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));
            int primaryColor = ColorUtil.fade(1);

            TargetEspRenderContext context = new TargetEspRenderContext(
                    target,
                    alphaPC,
                    e.getTickDelta(),
                    System.currentTimeMillis(),
                    primaryColor,
                    primaryColor,
                    MathHelper.clamp(hurtPC, 0.0F, 1.0F),
                    0.0F
            );

            ChainTargetEspRenderer.render(matrices, immediate, context);
            ChainTargetEspRenderer.endBatch(immediate);
            matrices.pop();
        }

        if (alphaPC > 0.001f && target != null && type.is("Переливание")) {
            ScanTargetEspRenderTypes.initialize();
        }

        if (alphaPC > 0.001f && target != null && type.is("Кристаллы")) {
            renderTargetCrystals(e, immediate, target, alphaPC);
        }

        if (alphaPC > 0.001f && target != null && type.is("Пентаграмма")) {
            renderTargetPentagram(e, immediate, target, alphaPC);
        }

        if (alphaPC > 0.001f && target != null && type.is("Цепь")) {
            renderTargetChainRing(e, immediate, target, alphaPC);
        }

        if (alphaPC > 0.001f && target != null && type.is("Снег")) {
            renderTargetSnow(e, immediate, target, alphaPC);
        }

        if (alphaPC > 0.001f && target != null && type.is("Сердце")) {
            renderTargetHeart(e, immediate, target, alphaPC);
        }

        if (alphaPC > 0.001f && target != null && type.is("Огонь")) {
            renderTargetFire(e, immediate, target, alphaPC);
        }

        if (alphaPC > 0.001f && target != null && type.is("Мечи")) {
            renderTargetSwords(e, immediate, target, alphaPC);
        }

        if (type.is("Куб")) {
            if (alphaPC > 0.001f && target != null) {
                renderTargetCube(e, target, alphaPC, immediate);
            }
            renderTargetCubeFragments(e, immediate);
        }

        immediate.draw();
    }
    private static final RenderPipeline RING_FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "ring_esp_fill"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );
    private static final RenderPipeline RING_LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "ring_esp_line"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );
    private static final RenderLayer RING_FILL_LAYER = RenderLayer.of("ring_esp_fill",
            RenderSetup.builder(RING_FILL_PIPELINE).expectedBufferSize(1 << 16).build());
    private static final RenderLayer RING_LINE_LAYER = RenderLayer.of("ring_esp_line",
            RenderSetup.builder(RING_LINE_PIPELINE).expectedBufferSize(1 << 14).build());

    public static final RenderPipeline ROMB_ESP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation("pipeline/wtex")
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .build()
    );

    public static final Function<Identifier, RenderLayer> ROMB_ESP =
            Util.memoize(texture -> {
                RenderSetup setup = RenderSetup.builder(ROMB_ESP_PIPELINE)
                        .texture("Sampler0", texture)
                        .translucent()
                        .expectedBufferSize(1536)
                        .build();
                return RenderLayer.of("wtex", setup);
            });

    private static void drawGradientQuad(VertexConsumer buffer, Matrix4f matrix, int color, int alpha) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
    }

    private static void drawGradientQuad(VertexConsumer buffer, Matrix4f matrix,int color,int color2,int color3,int color4, int alpha) {
        buffer.vertex(matrix, -0.5f, -0.5f, 0.0f).color(ColorUtil.replAlpha(color, alpha)).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, 0.5f, -0.5f, 0.0f).color(ColorUtil.replAlpha(color2, alpha)).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, 0.5f, 0.5f, 0.0f).color(ColorUtil.replAlpha(color3, alpha)).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, -0.5f, 0.5f, 0.0f).color(ColorUtil.replAlpha(color4, alpha)).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
    }

    private static void drawCubeFillTESP(VertexConsumer buf, Matrix4f m, float s, int color) {
        buf.vertex(m, -s,  s, -s).color(color); buf.vertex(m,  s,  s, -s).color(color);
        buf.vertex(m,  s,  s,  s).color(color); buf.vertex(m, -s,  s,  s).color(color);

        buf.vertex(m, -s, -s,  s).color(color); buf.vertex(m,  s, -s,  s).color(color);
        buf.vertex(m,  s, -s, -s).color(color); buf.vertex(m, -s, -s, -s).color(color);

        buf.vertex(m,  s, -s, -s).color(color); buf.vertex(m,  s, -s,  s).color(color);
        buf.vertex(m,  s,  s,  s).color(color); buf.vertex(m,  s,  s, -s).color(color);

        buf.vertex(m, -s, -s,  s).color(color); buf.vertex(m, -s, -s, -s).color(color);
        buf.vertex(m, -s,  s, -s).color(color); buf.vertex(m, -s,  s,  s).color(color);

        buf.vertex(m, -s, -s,  s).color(color); buf.vertex(m,  s, -s,  s).color(color);
        buf.vertex(m,  s,  s,  s).color(color); buf.vertex(m, -s,  s,  s).color(color);

        buf.vertex(m,  s, -s, -s).color(color); buf.vertex(m, -s, -s, -s).color(color);
        buf.vertex(m, -s,  s, -s).color(color); buf.vertex(m,  s,  s, -s).color(color);
    }

    private static void drawCubeOutlineTESP(VertexConsumer buf, Matrix4f m, float s, int color) {
        buf.vertex(m, -s, -s, -s).color(color); buf.vertex(m,  s, -s, -s).color(color);
        buf.vertex(m,  s, -s, -s).color(color); buf.vertex(m,  s, -s,  s).color(color);
        buf.vertex(m,  s, -s,  s).color(color); buf.vertex(m, -s, -s,  s).color(color);
        buf.vertex(m, -s, -s,  s).color(color); buf.vertex(m, -s, -s, -s).color(color);

        buf.vertex(m, -s,  s, -s).color(color); buf.vertex(m,  s,  s, -s).color(color);
        buf.vertex(m,  s,  s, -s).color(color); buf.vertex(m,  s,  s,  s).color(color);
        buf.vertex(m,  s,  s,  s).color(color); buf.vertex(m, -s,  s,  s).color(color);
        buf.vertex(m, -s,  s,  s).color(color); buf.vertex(m, -s,  s, -s).color(color);

        buf.vertex(m, -s, -s, -s).color(color); buf.vertex(m, -s,  s, -s).color(color);
        buf.vertex(m,  s, -s, -s).color(color); buf.vertex(m,  s,  s, -s).color(color);
        buf.vertex(m,  s, -s,  s).color(color); buf.vertex(m,  s,  s,  s).color(color);
        buf.vertex(m, -s, -s,  s).color(color); buf.vertex(m, -s,  s,  s).color(color);
    }

    private void renderTargetCrystals(EventRender3D e, VertexConsumerProvider.Immediate immediate,
                                      LivingEntity target, float alphaPC) {
        int hurtTicks = target.hurtTime;
        float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 10.0));

        alpha_2.update();
        alpha_2.run(hurtPC, 0.1F, Easings.SINE_OUT);

        int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0f * alphaPC));
        int color = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(1), alphaPC), redColor, alpha_2.get());

        long currentTime = System.currentTimeMillis();
        if (currentTimeSpirits == 0) currentTimeSpirits = currentTime;
        long timeDiff = currentTime - currentTimeSpirits;
        if (timeDiff > 0) animationNurik += timeDiff / 16.666F;
        currentTimeSpirits = currentTime;

        float count = Math.max(1, crystalCount.getValue().intValue());
        float orbitSpeed = crystalSpeed.getValue();
        float radius = crystalRadius.getValue() + target.getWidth() * 0.3f + 0.15f;
        float size = crystalSize.getValue();

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d targetPos = target.getLerpedPos(e.getTickDelta());

        float centerY = target.getHeight() * 0.55f;
        float heightSpread = target.getHeight() * 0.28f;

        int glowColor = ColorUtil.multAlpha(color, 0.3f);
        int fillTop = ColorUtil.replAlpha(color, (int) (alphaPC * 80));
        int fillBottom = ColorUtil.replAlpha(color, (int) (alphaPC * 40));
        int lineColor = ColorUtil.replAlpha(color, (int) (alphaPC * 235));

        for (int pass = 0; pass < 3; pass++) {
            VertexConsumer buf = switch (pass) {
                case 0 -> immediate.getBuffer(ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_1.png")));
                case 1 -> immediate.getBuffer(RING_FILL_LAYER);
                default -> immediate.getBuffer(RING_LINE_LAYER);
            };

            for (int i = 0; i < (int) count; i++) {
                float baseAngle = i * (360f / count) + animationNurik * 2.0f * orbitSpeed;

                float sin = (float) Math.sin(Math.toRadians(baseAngle));
                float cos = (float) Math.cos(Math.toRadians(baseAngle));
                double x = targetPos.x + cos * radius;
                double z = targetPos.z + sin * radius;
                double y = targetPos.y + centerY
                        + Math.sin(Math.toRadians(baseAngle * 3.0f + i * 53.0f)) * heightSpread;

                matrices.push();
                matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);

                if (pass == 0) {
                    matrices.multiply(mc.gameRenderer.getCamera().getRotation());
                    float gs = size * 5.0f;
                    matrices.scale(gs, gs, gs);
                    drawGradientQuad(buf, matrices.peek().getPositionMatrix(),
                            glowColor, glowColor, glowColor, glowColor,
                            (int) (alphaPC * 0.35f * 255));
                } else {
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(animationNurik * 4.0f * orbitSpeed + i * 37.0f));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(24.0f
                            + 8.0f * (float) Math.sin(Math.toRadians(animationNurik * 1.7f + i * 29.0f))));
                    Matrix4f matrix = matrices.peek().getPositionMatrix();

                    if (pass == 1) {
                        drawCrystalFill(buf, matrix, size, size * 1.7f, fillTop, fillBottom);
                    } else {
                        drawCrystalOutline(buf, matrix, size, size * 1.7f, lineColor);
                    }
                }

                matrices.pop();
            }
        }
    }

    private static void drawCrystalFill(VertexConsumer buf, Matrix4f m, float r, float h, int topColor, int bottomColor) {
        float ax = r, az = 0;
        float bx = 0, bz = r;
        float cx = -r, cz = 0;
        float dx = 0, dz = -r;

        buf.vertex(m, 0, h, 0).color(topColor);  buf.vertex(m, ax, 0, az).color(topColor);
        buf.vertex(m, bx, 0, bz).color(topColor); buf.vertex(m, bx, 0, bz).color(topColor);

        buf.vertex(m, 0, h, 0).color(topColor);  buf.vertex(m, bx, 0, bz).color(topColor);
        buf.vertex(m, cx, 0, cz).color(topColor); buf.vertex(m, cx, 0, cz).color(topColor);

        buf.vertex(m, 0, h, 0).color(topColor);  buf.vertex(m, cx, 0, cz).color(topColor);
        buf.vertex(m, dx, 0, dz).color(topColor); buf.vertex(m, dx, 0, dz).color(topColor);

        buf.vertex(m, 0, h, 0).color(topColor);  buf.vertex(m, dx, 0, dz).color(topColor);
        buf.vertex(m, ax, 0, az).color(topColor); buf.vertex(m, ax, 0, az).color(topColor);

        buf.vertex(m, 0, -h, 0).color(bottomColor); buf.vertex(m, bx, 0, bz).color(bottomColor);
        buf.vertex(m, ax, 0, az).color(bottomColor); buf.vertex(m, ax, 0, az).color(bottomColor);

        buf.vertex(m, 0, -h, 0).color(bottomColor); buf.vertex(m, cx, 0, cz).color(bottomColor);
        buf.vertex(m, bx, 0, bz).color(bottomColor); buf.vertex(m, bx, 0, bz).color(bottomColor);

        buf.vertex(m, 0, -h, 0).color(bottomColor); buf.vertex(m, dx, 0, dz).color(bottomColor);
        buf.vertex(m, cx, 0, cz).color(bottomColor); buf.vertex(m, cx, 0, cz).color(bottomColor);

        buf.vertex(m, 0, -h, 0).color(bottomColor); buf.vertex(m, ax, 0, az).color(bottomColor);
        buf.vertex(m, dx, 0, dz).color(bottomColor); buf.vertex(m, dx, 0, dz).color(bottomColor);
    }

    private static void drawCrystalOutline(VertexConsumer buf, Matrix4f m, float r, float h, int color) {
        float[][] eq = {{r, 0}, {0, r}, {-r, 0}, {0, -r}};
        for (int k = 0; k < 4; k++) {
            float[] cur = eq[k];
            float[] next = eq[(k + 1) % 4];

            buf.vertex(m, 0, h, 0).color(color);
            buf.vertex(m, cur[0], 0, cur[1]).color(color);

            buf.vertex(m, 0, -h, 0).color(color);
            buf.vertex(m, cur[0], 0, cur[1]).color(color);

            buf.vertex(m, cur[0], 0, cur[1]).color(color);
            buf.vertex(m, next[0], 0, next[1]).color(color);
        }
    }

    private void renderTargetPentagram(EventRender3D e, VertexConsumerProvider.Immediate immediate,
                                       LivingEntity target, float alphaPC) {
        int hurtTicks = target.hurtTime;
        float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 10.0));

        alpha_2.update();
        alpha_2.run(hurtPC, 0.1F, Easings.SINE_OUT);

        int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0f * alphaPC));
        int base = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(1), alphaPC), redColor, alpha_2.get());

        long currentTime = System.currentTimeMillis();
        if (currentTimeSpirits == 0) currentTimeSpirits = currentTime;
        long timeDiff = currentTime - currentTimeSpirits;
        if (timeDiff > 0) animationNurik += timeDiff / 16.666F;
        currentTimeSpirits = currentTime;

        float speed = pentaSpeed.getValue();
        float r = (pentaRadius.getValue() + target.getWidth() * 0.35f)
                * (1.0f + 0.04f * (float) Math.sin(Math.toRadians(animationNurik * 6.0f)));
        float spin = animationNurik * 2.2f * speed;
        int aSoft = (int) (alphaPC * 36);
        int aRibbon = (int) (alphaPC * 150);
        int aCore = (int) (alphaPC * 235);

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d targetPos = target.getLerpedPos(e.getTickDelta());

        matrices.push();
        matrices.translate(targetPos.x - cameraPos.x,
                targetPos.y - cameraPos.y + 0.06f,
                targetPos.z - cameraPos.z);
        Matrix4f m = matrices.peek().getPositionMatrix();

        float[] tipX = new float[5];
        float[] tipZ = new float[5];
        for (int k = 0; k < 5; k++) {
            double a = Math.toRadians(spin + k * 72.0);
            tipX[k] = (float) (Math.cos(a) * r);
            tipZ[k] = (float) (Math.sin(a) * r);
        }

        VertexConsumer texBuf = immediate.getBuffer(
                ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_1.png")));

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0f));
        float cg = r * 1.6f;
        matrices.scale(cg, cg, cg);
        drawGradientQuad(texBuf, matrices.peek().getPositionMatrix(),
                base, base, base, base, aSoft * 2);
        matrices.pop();

        for (int k = 0; k < 5; k++) {
            matrices.push();
            matrices.translate(tipX[k], 0, tipZ[k]);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0f));
            float gs = r * 0.34f;
            matrices.scale(gs, gs, gs);
            int tipCol = ColorUtil.overCol(ColorUtil.fade(k * 48), redColor, alpha_2.get());
            drawGradientQuad(texBuf, matrices.peek().getPositionMatrix(),
                    tipCol, tipCol, tipCol, tipCol, (int) (alphaPC * 110));
            matrices.pop();
        }

        VertexConsumer fillBuf = immediate.getBuffer(RING_FILL_LAYER);

        int discSegs = 64;
        for (int i = 0; i < discSegs; i++) {
            float a0 = (float) (Math.PI * 2.0 * i / discSegs);
            float a1 = (float) (Math.PI * 2.0 * (i + 1) / discSegs);
            float x0 = (float) Math.cos(a0) * r * 1.32f;
            float z0 = (float) Math.sin(a0) * r * 1.32f;
            float x1 = (float) Math.cos(a1) * r * 1.32f;
            float z1 = (float) Math.sin(a1) * r * 1.32f;
            fillBuf.vertex(m, 0, 0, 0).color(ColorUtil.replAlpha(base, aSoft));
            fillBuf.vertex(m, x0, 0, z0).color(ColorUtil.replAlpha(base, 0));
            fillBuf.vertex(m, x1, 0, z1).color(ColorUtil.replAlpha(base, 0));
            fillBuf.vertex(m, x1, 0, z1).color(ColorUtil.replAlpha(base, 0));
        }

        float w = r * 0.032f + 0.008f;

        for (int k = 0; k < 5; k++) {
            int nk = (k + 2) % 5;
            int c0 = ColorUtil.overCol(ColorUtil.multBright(ColorUtil.fade(k * 48), 0.85F), redColor, alpha_2.get());
            int c1 = ColorUtil.overCol(ColorUtil.multBright(ColorUtil.fade(nk * 48), 0.85F), redColor, alpha_2.get());
            pentagramRibbon(fillBuf, m, tipX[k], tipZ[k], tipX[nk], tipZ[nk], w,
                    ColorUtil.replAlpha(c0, aRibbon), ColorUtil.replAlpha(c1, aRibbon));
        }

        for (int k = 0; k < 5; k++) {
            int nk = (k + 1) % 5;
            pentagramRibbon(fillBuf, m,
                    tipX[k] * 0.382f, tipZ[k] * 0.382f,
                    tipX[nk] * 0.382f, tipZ[nk] * 0.382f,
                    w * 0.7f,
                    ColorUtil.replAlpha(base, (int) (alphaPC * 110)),
                    ColorUtil.replAlpha(base, (int) (alphaPC * 110)));
        }

        for (int i = 0; i < discSegs; i++) {
            float a0 = (float) (Math.PI * 2.0 * i / discSegs);
            float a1 = (float) (Math.PI * 2.0 * (i + 1) / discSegs);
            pentagramRibbon(fillBuf, m,
                    (float) Math.cos(a0) * r, (float) Math.sin(a0) * r,
                    (float) Math.cos(a1) * r, (float) Math.sin(a1) * r,
                    w * 0.75f,
                    ColorUtil.replAlpha(base, (int) (alphaPC * 130)),
                    ColorUtil.replAlpha(base, (int) (alphaPC * 130)));
            pentagramRibbon(fillBuf, m,
                    (float) Math.cos(a0) * r * 1.16f, (float) Math.sin(a0) * r * 1.16f,
                    (float) Math.cos(a1) * r * 1.16f, (float) Math.sin(a1) * r * 1.16f,
                    w * 0.55f,
                    ColorUtil.replAlpha(base, (int) (alphaPC * 80)),
                    ColorUtil.replAlpha(base, (int) (alphaPC * 80)));
        }

        float rOuter = r * 1.32f;
        int faint = ColorUtil.replAlpha(ColorUtil.overCol(ColorUtil.multBright(ColorUtil.fade(180), 0.8F), redColor, alpha_2.get()),
                (int) (alphaPC * 60));
        float spin2 = -spin * 0.7f + 36.0f;
        for (int k = 0; k < 5; k++) {
            double a0 = Math.toRadians(spin2 + k * 72.0);
            double a1 = Math.toRadians(spin2 + ((k + 2) % 5) * 72.0);
            pentagramRibbon(fillBuf, m,
                    (float) (Math.cos(a0) * rOuter), (float) (Math.sin(a0) * rOuter),
                    (float) (Math.cos(a1) * rOuter), (float) (Math.sin(a1) * rOuter),
                    w * 0.5f, faint, faint);
        }

        VertexConsumer lineBuf = immediate.getBuffer(RING_LINE_LAYER);
        int coreCol = ColorUtil.replAlpha(base, aCore);
        for (int i = 0; i < discSegs; i++) {
            float a0 = (float) (Math.PI * 2.0 * i / discSegs);
            float a1 = (float) (Math.PI * 2.0 * (i + 1) / discSegs);
            lineBuf.vertex(m, (float) Math.cos(a0) * r, 0, (float) Math.sin(a0) * r).color(coreCol);
            lineBuf.vertex(m, (float) Math.cos(a1) * r, 0, (float) Math.sin(a1) * r).color(coreCol);
        }
        for (int k = 0; k < 5; k++) {
            int nk = (k + 2) % 5;
            lineBuf.vertex(m, tipX[k], 0, tipZ[k]).color(coreCol);
            lineBuf.vertex(m, tipX[nk], 0, tipZ[nk]).color(coreCol);
        }
        for (int k = 0; k < 5; k++) {
            int nk = (k + 1) % 5;
            lineBuf.vertex(m, tipX[k] * 0.382f, 0, tipZ[k] * 0.382f).color(coreCol);
            lineBuf.vertex(m, tipX[nk] * 0.382f, 0, tipZ[nk] * 0.382f).color(coreCol);
        }

        matrices.pop();
    }

    private static void pentagramRibbon(VertexConsumer buf, Matrix4f m,
                                        float x0, float z0, float x1, float z1,
                                        float width, int c0, int c1) {
        float dx = x1 - x0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-5f) return;
        float nx = (dz / len) * width;
        float nz = (-dx / len) * width;

        buf.vertex(m, x0 + nx, 0, z0 + nz).color(c0);
        buf.vertex(m, x1 + nx, 0, z1 + nz).color(c1);
        buf.vertex(m, x1 - nx, 0, z1 - nz).color(c1);
        buf.vertex(m, x0 - nx, 0, z0 - nz).color(c0);
    }

    private void renderTargetChainRing(EventRender3D e, VertexConsumerProvider.Immediate immediate,
                                       LivingEntity target, float alphaPC) {
        int hurtTicks = target.hurtTime;
        float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 10.0));

        alpha_2.update();
        alpha_2.run(hurtPC, 0.1F, Easings.SINE_OUT);

        int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0f * alphaPC));
        float atts = alpha_2.get();

        int coreCol = ColorUtil.overCol(ColorUtil.overCol(
                ColorUtil.multAlpha(ColorUtil.fade(1), alphaPC),
                ColorUtil.getColor(238, 244, 255), 0.75f), redColor, atts);

        long currentTime = System.currentTimeMillis();
        if (currentTimeSpirits == 0) currentTimeSpirits = currentTime;
        long timeDiff = currentTime - currentTimeSpirits;
        if (timeDiff > 0) animationNurik += timeDiff / 16.666F;
        currentTimeSpirits = currentTime;

        int count = Math.max(6, linkCount.getValue().intValue());
        float speed = linkSpeed.getValue();
        float radius = linkRadius.getValue() + target.getWidth() * 0.35f + 0.12f;
        float size = linkSize.getValue();
        float bodyH = target.getHeight();

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d targetPos = target.getLerpedPos(e.getTickDelta());

        matrices.push();
        matrices.translate(targetPos.x - cameraPos.x, targetPos.y - cameraPos.y, targetPos.z - cameraPos.z);
        matrices.translate(0, bodyH * 0.47f
                + 0.02f * (float) Math.sin(Math.toRadians(animationNurik * 1.4f)), 0);
        Matrix4f m = matrices.peek().getPositionMatrix();

        float step = 360f / count;
        float span = step * 0.62f;
        float baseAng = animationNurik * 1.15f * speed;

        VertexConsumer texBuf = immediate.getBuffer(
                ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_2.png")));
        for (int i = 0; i < count; i++) {
            float midA = baseAng + i * step + span * 0.5f;
            double offAmp = size * 0.55f * Math.sin(i * Math.PI);
            float gx = (float) Math.cos(Math.toRadians(midA)) * (radius + (float) offAmp);
            float gz = (float) Math.sin(Math.toRadians(midA)) * (radius + (float) offAmp);

            matrices.push();
            matrices.translate(gx, 0, gz);
            matrices.multiply(mc.gameRenderer.getCamera().getRotation());
            float g = size * 4.2f;
            matrices.scale(g, g, g);
            drawGradientQuad(texBuf, matrices.peek().getPositionMatrix(),
                    coreCol, coreCol, coreCol, coreCol, (int) (alphaPC * 80));
            matrices.pop();
        }

        VertexConsumer fillBuf = immediate.getBuffer(RING_FILL_LAYER);
        int colTop = ColorUtil.replAlpha(ColorUtil.overCol(coreCol, ColorUtil.getColor(255), 0.55f),
                (int) (alphaPC * 245));
        int colSide = ColorUtil.replAlpha(coreCol, (int) (alphaPC * 205));
        int colBot = ColorUtil.replAlpha(
                ColorUtil.overCol(coreCol, ColorUtil.getColor(110, 120, 145), 0.5f),
                (int) (alphaPC * 160));

        for (int i = 0; i < count; i++) {
            double offAmp = Math.sin(i * Math.PI);
            float midA = baseAng + i * step + span * 0.5f;
            float cx = (float) Math.cos(Math.toRadians(midA)) * (radius + (float) (offAmp * size * 0.55));
            float cz = (float) Math.sin(Math.toRadians(midA)) * (radius + (float) (offAmp * size * 0.55));

            float cy = (float) (offAmp * size * 0.5);

            drawTorusLink(fillBuf, m, cx, cy, cz, size, size * 0.40f, 16, 8,
                    colTop, colSide, colBot);
        }

        matrices.pop();
    }

    private static void drawTorusLink(VertexConsumer buf, Matrix4f m,
                                      float cx, float cy, float cz,
                                      float rMain, float rTube,
                                      int segU, int segV,
                                      int colTop, int colSide, int colBot) {
        float[] px = new float[segU + 1];
        float[] py = new float[segU + 1];
        float[] pz = new float[segU + 1];
        float[] dxs = new float[segU + 1];
        float[] dzs = new float[segU + 1];

        for (int u = 0; u <= segU; u++) {
            double a = Math.PI * 2.0 * u / segU;
            float dxc = (float) Math.cos(a);
            float dzc = (float) Math.sin(a);
            dxs[u] = dxc;
            dzs[u] = dzc;
            px[u] = cx + dxc * rMain;
            py[u] = cy;
            pz[u] = cz + dzc * rMain;
        }

        int[] vCols = new int[segV];
        for (int v = 0; v < segV; v++) {
            double b = Math.PI * 2.0 * v / segV;
            float h = (float) Math.sin(b);
            if (h > 0.35f) vCols[v] = colTop;
            else if (h > -0.35f) vCols[v] = colSide;
            else vCols[v] = colBot;
        }

        for (int u = 0; u < segU; u++) {
            int u1 = u + 1;
            for (int v = 0; v < segV; v++) {
                double b0 = Math.PI * 2.0 * v / segV;
                double b1 = Math.PI * 2.0 * (v + 1) / segV;

                float o00 = (float) Math.cos(b0) * rTube, y00 = (float) Math.sin(b0) * rTube;
                float o10 = (float) Math.cos(b1) * rTube, y10 = (float) Math.sin(b1) * rTube;

                buf.vertex(m, px[u] + dxs[u] * o00, py[u] + y00, pz[u] + dzs[u] * o00).color(vCols[v]);
                buf.vertex(m, px[u] + dxs[u] * o10, py[u] + y10, pz[u] + dzs[u] * o10).color(vCols[(v + 1) % segV]);
                buf.vertex(m, px[u1] + dxs[u1] * o10, py[u1] + y10, pz[u1] + dzs[u1] * o10).color(vCols[(v + 1) % segV]);
                buf.vertex(m, px[u1] + dxs[u1] * o00, py[u1] + y00, pz[u1] + dzs[u1] * o00).color(vCols[v]);
            }
        }
    }

    private void renderTargetSnow(EventRender3D e, VertexConsumerProvider.Immediate immediate,
                                  LivingEntity target, float alphaPC) {
        int hurtTicks = target.hurtTime;
        float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 10.0));

        alpha_2.update();
        alpha_2.run(hurtPC, 0.1F, Easings.SINE_OUT);

        int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0f * alphaPC));
        int snowWhite = ColorUtil.getColor(235, 245, 255);
        int color = ColorUtil.overCol(
                ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(1), alphaPC), snowWhite, 0.55f),
                redColor, alpha_2.get());

        long currentTime = System.currentTimeMillis();
        if (currentTimeSpirits == 0) currentTimeSpirits = currentTime;
        long timeDiff = currentTime - currentTimeSpirits;
        if (timeDiff > 0) animationNurik += timeDiff / 16.666F;
        currentTimeSpirits = currentTime;

        int count = Math.max(1, snowCount.getValue().intValue());
        float speed = snowSpeed.getValue();
        float radius = snowRadius.getValue() + target.getWidth() * 0.35f + 0.1f;
        float size = snowSize.getValue();
        float bodyH = target.getHeight();

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d targetPos = target.getLerpedPos(e.getTickDelta());

        float[] pxArr = SNOW_PX;
        float[] pyArr = SNOW_PY;
        float[] pzArr = SNOW_PZ;
        float[] spinArr = SNOW_SPIN;

        for (int i = 0; i < count; i++) {
            float heightFrac = 0.22f + 0.6f * ((i * 0.618f) % 1f);
            double orbA = Math.toRadians(
                    animationNurik * 1.4f * speed * ((i % 2 == 0) ? 1f : -1f) + i * (360.0 / count));

            pxArr[i] = (float) Math.cos(orbA) * radius;
            pzArr[i] = (float) Math.sin(orbA) * radius;
            pyArr[i] = bodyH * heightFrac
                    + (float) Math.sin(Math.toRadians(animationNurik * 1.3f + i * 61.0)) * bodyH * 0.08f;
            spinArr[i] = animationNurik * 2.6f * speed * ((i % 2 == 0) ? 1f : -1f) + i * 53f;
        }

        matrices.push();
        matrices.translate(targetPos.x - cameraPos.x, targetPos.y - cameraPos.y, targetPos.z - cameraPos.z);

        VertexConsumer texBuf = immediate.getBuffer(
                ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_1.png")));

        for (int i = 0; i < count; i++) {
            matrices.push();
            matrices.translate(pxArr[i], pyArr[i], pzArr[i]);
            matrices.multiply(mc.gameRenderer.getCamera().getRotation());
            float g = size * 4.5f;
            matrices.scale(g, g, g);
            drawGradientQuad(texBuf, matrices.peek().getPositionMatrix(),
                    color, color, color, color, (int) (alphaPC * 110));
            matrices.pop();
        }

        VertexConsumer fillBuf = immediate.getBuffer(RING_FILL_LAYER);

        float L = size * 1.5f;
        float W = Math.max(size * 0.24f, 0.012f);
        int col = ColorUtil.replAlpha(color, (int) (alphaPC * 230));
        int dotCol = ColorUtil.replAlpha(snowWhite, (int) (alphaPC * 255));

        for (int i = 0; i < count; i++) {
            matrices.push();
            matrices.translate(pxArr[i], pyArr[i], pzArr[i]);
            matrices.multiply(mc.gameRenderer.getCamera().getRotation());
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spinArr[i]));

            for (int k = 0; k < 3; k++) {
                matrices.push();
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(k * 60f));
                Matrix4f bm = matrices.peek().getPositionMatrix();

                fillBuf.vertex(bm, -L, -W * 0.45f, 0).color(col);
                fillBuf.vertex(bm, -L * 0.42f, -W, 0).color(col);
                fillBuf.vertex(bm, L * 0.42f, -W, 0).color(col);
                fillBuf.vertex(bm, L, -W * 0.45f, 0).color(col);

                fillBuf.vertex(bm, L, W * 0.45f, 0).color(col);
                fillBuf.vertex(bm, L * 0.42f, W, 0).color(col);
                fillBuf.vertex(bm, -L * 0.42f, W, 0).color(col);
                fillBuf.vertex(bm, -L, W * 0.45f, 0).color(col);

                matrices.pop();
            }

            Matrix4f dm = matrices.peek().getPositionMatrix();
            fillBuf.vertex(dm, -W, -W, 0).color(dotCol);
            fillBuf.vertex(dm, W, -W, 0).color(dotCol);
            fillBuf.vertex(dm, W, W, 0).color(dotCol);
            fillBuf.vertex(dm, -W, W, 0).color(dotCol);

            matrices.pop();
        }

        matrices.pop();
    }

    private void renderTargetHeart(EventRender3D e, VertexConsumerProvider.Immediate immediate,
                                   LivingEntity target, float alphaPC) {
        int hurtTicks = target.hurtTime;
        float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20.0));

        alpha_2.update();
        alpha_2.run(hurtPC, 0.1F, Easings.SINE_OUT);

        float hpMax = target.getMaxHealth() + target.getAbsorptionAmount();
        float hpNow = target.getHealth() + target.getAbsorptionAmount();
        float hpFrac = hpMax <= 0f ? 1f : Math.min(hpNow / hpMax, 1f);
        float lowHp = 1f - hpFrac;

        long now = System.currentTimeMillis();
        if (heartLastTime == 0L) heartLastTime = now;
        long dtMs = now - heartLastTime;
        heartLastTime = now;
        float bpm = (55f + 170f * lowHp * lowHp) * heartSpeed.getValue();
        heartPhase += dtMs / 1000f * (bpm / 60f);

        float f = heartPhase % 1f;

        float pulse = (float) (Math.exp(-7.0 * f) + 0.5 * Math.exp(-11.0 * Math.abs(f - 0.24)));
        pulse = Math.min(pulse, 1.4f);

        float beatScale = 1f + 0.20f * pulse;

        int hurtRed = ColorUtil.getColor(255, 90, 90, (int) (255.0f * alphaPC));
        int baseCol = ColorUtil.overCol(ColorUtil.getColor(255, 92, 120),
                ColorUtil.getColor(255, 34, 56), lowHp);
        int col = ColorUtil.overCol(ColorUtil.multAlpha(baseCol, alphaPC), hurtRed, alpha_2.get());
        int hotCol = ColorUtil.replAlpha(
                ColorUtil.overCol(ColorUtil.getColor(255, 195, 210), ColorUtil.getColor(255, 125, 145), lowHp),
                (int) (alphaPC * 235));
        int fillCol = ColorUtil.replAlpha(col, (int) (alphaPC * (70 + 50 * pulse)));

        float size = heartSize.getValue();

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d targetPos = target.getLerpedPos(e.getTickDelta());

        matrices.push();
        matrices.translate(targetPos.x - cameraPos.x, targetPos.y - cameraPos.y, targetPos.z - cameraPos.z);
        matrices.push();
        matrices.translate(0,
                target.getHeight() * 0.62f + 0.05f * (float) Math.sin(now / 420.0),
                0);
        matrices.multiply(mc.gameRenderer.getCamera().getRotation());

        float k = size / 34f * beatScale;

        final int SEGS = 48;
        float[] hxArr = HEART_HX;
        float[] hyArr = HEART_HY;
        for (int i = 0; i < SEGS; i++) {
            double t = Math.PI * 2.0 * i / SEGS;
            hxArr[i] = (float) (16.0 * Math.pow(Math.sin(t), 3)) * k;
            hyArr[i] = (float) (13.0 * Math.cos(t) - 5.0 * Math.cos(2 * t)
                    - 2.0 * Math.cos(3 * t) - Math.cos(4 * t)) * k
                    + 6f * k;
        }

        VertexConsumer texBuf = immediate.getBuffer(
                ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_1.png")));
        matrices.push();
        float g = size * beatScale * (1.6f + 0.5f * pulse);
        matrices.scale(g, g, g);
        drawGradientQuad(texBuf, matrices.peek().getPositionMatrix(),
                col, col, col, col, (int) (alphaPC * (85 + 55 * pulse)));
        matrices.pop();

        VertexConsumer fillBuf = immediate.getBuffer(RING_FILL_LAYER);
        Matrix4f m = matrices.peek().getPositionMatrix();
        for (int i = 0; i < SEGS; i++) {
            int j = (i + 1) % SEGS;
            fillBuf.vertex(m, 0, 0, 0).color(fillCol);
            fillBuf.vertex(m, hxArr[i], hyArr[i], 0).color(fillCol);
            fillBuf.vertex(m, hxArr[j], hyArr[j], 0).color(fillCol);
            fillBuf.vertex(m, hxArr[j], hyArr[j], 0).color(fillCol);
        }

        if (f < 0.38f) {
            float rf = f / 0.38f;
            float ringR = size * (0.65f + 0.85f * rf) * beatScale;
            float ringW = size * 0.05f;
            int ringCol = ColorUtil.replAlpha(col, (int) (alphaPC * (1f - rf) * 120));
            for (int i = 0; i < SEGS; i++) {
                double a0 = Math.PI * 2.0 * i / SEGS;
                double a1 = Math.PI * 2.0 * (i + 1) / SEGS;
                fillBuf.vertex(m, (float) Math.cos(a0) * ringR, (float) Math.sin(a0) * ringR, 0).color(ringCol);
                fillBuf.vertex(m, (float) Math.cos(a1) * ringR, (float) Math.sin(a1) * ringR, 0).color(ringCol);
                fillBuf.vertex(m, (float) Math.cos(a1) * (ringR - ringW), (float) Math.sin(a1) * (ringR - ringW), 0).color(ringCol);
                fillBuf.vertex(m, (float) Math.cos(a0) * (ringR - ringW), (float) Math.sin(a0) * (ringR - ringW), 0).color(ringCol);
            }
        }

        VertexConsumer lineBuf = immediate.getBuffer(RING_LINE_LAYER);
        for (int i = 0; i < SEGS; i++) {
            int j = (i + 1) % SEGS;
            lineBuf.vertex(m, hxArr[i], hyArr[i], 0).color(hotCol);
            lineBuf.vertex(m, hxArr[j], hyArr[j], 0).color(hotCol);
        }

        matrices.pop();
        matrices.pop();
    }

    private void renderTargetFire(EventRender3D e, VertexConsumerProvider.Immediate immediate,
                                  LivingEntity target, float alphaPC) {
        int hurtTicks = target.hurtTime;
        float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 10.0));

        alpha_2.update();
        alpha_2.run(hurtPC, 0.1F, Easings.SINE_OUT);

        int hurtRed = ColorUtil.getColor(255, 60, 40);

        long currentTime = System.currentTimeMillis();
        if (currentTimeSpirits == 0) currentTimeSpirits = currentTime;
        long timeDiff = currentTime - currentTimeSpirits;
        if (timeDiff > 0) animationNurik += timeDiff / 16.666F;
        currentTimeSpirits = currentTime;

        int count = Math.max(4, fireCount.getValue().intValue());
        float speed = fireSpeed.getValue();
        float radiusMul = fireRadius.getValue();
        float heightMul = fireHeight.getValue();
        float atts = alpha_2.get();

        float bodyH = target.getHeight();
        float baseR = (target.getWidth() * 0.55f + 0.22f) * radiusMul;
        float riseH = bodyH * 0.95f * heightMul;
        float tSec = animationNurik / 60f;

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d targetPos = target.getLerpedPos(e.getTickDelta());
        var camRot = mc.gameRenderer.getCamera().getRotation();

        float[] oxArr = FIRE_OX;
        float[] oyArr = FIRE_OY;
        float[] ozArr = FIRE_OZ;
        int[] outRgb = FIRE_RGB_OUT;
        int[] coreRgb = FIRE_RGB_CORE;
        float[] outAlpha = FIRE_ALPHA;
        float[] outH = FIRE_H;

        for (int i = 0; i < count; i++) {
            float seed = (i * 0.618034f) % 1f;
            float cyc = (tSec * 0.85f * speed + seed * 13.7f) % 1f;

            float ang = seed * (float) Math.PI * 2f + tSec * 1.5f * speed + cyc * 2.6f;
            float r = baseR * (1f - 0.45f * cyc) * (0.82f + 0.36f * (float) Math.sin(seed * 41f));
            oxArr[i] = (float) Math.cos(ang) * r;
            ozArr[i] = (float) Math.sin(ang) * r;
            oyArr[i] = 0.05f + cyc * riseH
                    + 0.03f * (float) Math.sin(tSec * 3f + seed * 31f);

            float fadeIn = smooth01(cyc / 0.14f);
            float fadeOut = 1f - smooth01((cyc - 0.7f) / 0.3f);
            float env = Math.max(0f, fadeIn * fadeOut);
            float flicker = 0.72f + 0.28f * (float) Math.sin(tSec * 13f + seed * 97f);

            int rgb;
            if (cyc < 0.5f) rgb = fireLerp(0xFF3C0A, 0xFF8C19, cyc * 2f);
            else rgb = fireLerp(0xFF8C19, 0xFFE16E, (cyc - 0.5f) * 2f);
            outRgb[i] = ColorUtil.overCol(rgb, hurtRed, atts);
            coreRgb[i] = ColorUtil.overCol(fireLerp(rgb, 0xFFF0B4, 0.55f), hurtRed, atts);

            outAlpha[i] = alphaPC * env * flicker;
            outH[i] = bodyH * (0.10f + 0.13f * cyc) * heightMul * (0.85f + 0.3f * flicker);
        }

        matrices.push();
        matrices.translate(targetPos.x - cameraPos.x, targetPos.y - cameraPos.y, targetPos.z - cameraPos.z);

        VertexConsumer glowBuf = immediate.getBuffer(
                ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_2.png")));
        for (int i = 0; i < count; i++) {
            if (outAlpha[i] <= 0.02f) continue;
            float h = outH[i] * 1.6f;

            matrices.push();
            matrices.translate(oxArr[i], oyArr[i], ozArr[i]);
            matrices.multiply(camRot);
            float s = h * 2f;
            matrices.scale(s, s, s);
            Matrix4f mm = matrices.peek().getPositionMatrix();
            drawGradientQuad(glowBuf, mm, outRgb[i], outRgb[i], outRgb[i], outRgb[i],
                    (int) (outAlpha[i] * 105));
            matrices.pop();
        }

        VertexConsumer coreBuf = immediate.getBuffer(
                ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_1.png")));
        for (int i = 0; i < count; i++) {
            if (outAlpha[i] <= 0.02f) continue;
            float h = outH[i] * 0.75f;

            matrices.push();
            matrices.translate(oxArr[i], oyArr[i], ozArr[i]);
            matrices.multiply(camRot);
            float s = h * 2f;
            matrices.scale(s, s, s);
            Matrix4f mm = matrices.peek().getPositionMatrix();
            drawGradientQuad(coreBuf, mm, coreRgb[i], coreRgb[i], coreRgb[i], coreRgb[i],
                    (int) (outAlpha[i] * 220));
            matrices.pop();
        }

        matrices.pop();
    }

    private static float smooth01(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    private static int fireLerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ra = (a >> 16) & 0xFF, ga = (a >> 8) & 0xFF, ba = a & 0xFF;
        int rb = (b >> 16) & 0xFF, gb = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (ra + (rb - ra) * t) << 16)
                | ((int) (ga + (gb - ga) * t) << 8)
                | (int) (ba + (bb - ba) * t);
    }

    private void renderTargetSwords(EventRender3D e, VertexConsumerProvider.Immediate immediate,
                                    LivingEntity target, float alphaPC) {
        int hurtTicks = target.hurtTime;
        float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 10.0));

        alpha_2.update();
        alpha_2.run(hurtPC, 0.1F, Easings.SINE_OUT);

        int redColor = ColorUtil.getColor(255, 100, 100, (int) (255.0f * alphaPC));
        int color = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(1), alphaPC), redColor, alpha_2.get());
        float atts = alpha_2.get();

        long currentTime = System.currentTimeMillis();
        if (currentTimeSpirits == 0) currentTimeSpirits = currentTime;
        long timeDiff = currentTime - currentTimeSpirits;
        if (timeDiff > 0) animationNurik += timeDiff / 16.666F;
        currentTimeSpirits = currentTime;

        int count = Math.max(2, swordsCount.getValue().intValue());
        float speed = swordsSpeed.getValue();
        float radius = swordsRadius.getValue() + target.getWidth() * 0.35f + 0.1f;
        float size = swordsSize.getValue();
        float bodyH = target.getHeight();

        MatrixStack matrices = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d targetPos = target.getLerpedPos(e.getTickDelta());

        float[] pxArr = SWORD_PX;
        float[] pyArr = SWORD_PY;
        float[] pzArr = SWORD_PZ;
        float[] tiltArr = SWORD_TILT;

        for (int i = 0; i < count; i++) {
            double orbA = Math.toRadians(
                    animationNurik * 1.5f * speed * ((i % 2 == 0) ? 1f : -1f) + i * (360.0 / count));
            pxArr[i] = (float) (Math.cos(orbA) * radius);
            pzArr[i] = (float) (Math.sin(orbA) * radius);
            pyArr[i] = bodyH * 0.55f
                    + (float) Math.sin(Math.toRadians(animationNurik * 1.2f + i * 71.0)) * bodyH * 0.06f;

            tiltArr[i] = 10f * (float) Math.sin(Math.toRadians(animationNurik * 1.8f + i * 47f))
                    * ((i % 2 == 0) ? 1f : -1f);
        }

        matrices.push();
        matrices.translate(targetPos.x - cameraPos.x, targetPos.y - cameraPos.y, targetPos.z - cameraPos.z);

        VertexConsumer texBuf = immediate.getBuffer(
                ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_2.png")));
        for (int i = 0; i < count; i++) {
            matrices.push();
            matrices.translate(pxArr[i], pyArr[i], pzArr[i]);
            matrices.multiply(mc.gameRenderer.getCamera().getRotation());
            float g = size * 2.6f;
            matrices.scale(g, g, g);
            drawGradientQuad(texBuf, matrices.peek().getPositionMatrix(),
                    color, color, color, color, (int) (alphaPC * 70));
            matrices.pop();
        }

        VertexConsumer fillBuf = immediate.getBuffer(RING_FILL_LAYER);

        for (int i = 0; i < count; i++) {
            matrices.push();
            matrices.translate(pxArr[i], pyArr[i], pzArr[i]);
            matrices.multiply(mc.gameRenderer.getCamera().getRotation());
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(tiltArr[i]));

            Matrix4f m = matrices.peek().getPositionMatrix();

            float bladeTipY = -size * 0.50f;
            float bladeBaseY = size * 0.18f;
            float wb = size * 0.055f;
            float shoulderY = -size * 0.30f;

            int bladeCol = ColorUtil.replAlpha(
                    ColorUtil.overCol(ColorUtil.getColor(225, 232, 245), redColor, atts),
                    (int) (alphaPC * 200));
            int edgeCol = ColorUtil.replAlpha(color, (int) (alphaPC * 160));
            int guardCol = ColorUtil.replAlpha(
                    ColorUtil.overCol(ColorUtil.fade(90), redColor, atts), (int) (alphaPC * 220));
            int gripCol = ColorUtil.replAlpha(
                    ColorUtil.overCol(ColorUtil.getColor(120, 90, 60), redColor, atts), (int) (alphaPC * 210));
            int pommelCol = guardCol;

            fillBuf.vertex(m, 0, bladeTipY, 0).color(bladeCol);
            fillBuf.vertex(m, -wb, shoulderY, 0).color(edgeCol);
            fillBuf.vertex(m, -wb, bladeBaseY, 0).color(bladeCol);
            fillBuf.vertex(m, -wb * 0.4f, bladeBaseY, 0).color(bladeCol);

            fillBuf.vertex(m, wb * 0.4f, bladeBaseY, 0).color(bladeCol);
            fillBuf.vertex(m, wb, bladeBaseY, 0).color(bladeCol);
            fillBuf.vertex(m, wb, shoulderY, 0).color(edgeCol);
            fillBuf.vertex(m, 0, bladeTipY, 0).color(bladeCol);

            xyRibbon(fillBuf, m, 0, bladeTipY + size * 0.06f, 0, bladeBaseY - size * 0.02f,
                    size * 0.012f, ColorUtil.replAlpha(ColorUtil.getColor(255), (int) (alphaPC * 150)));

            xyRibbon(fillBuf, m, -size * 0.13f, bladeBaseY + size * 0.02f,
                    size * 0.13f, bladeBaseY + size * 0.02f, size * 0.032f, guardCol);

            xyRibbon(fillBuf, m, 0, bladeBaseY + size * 0.04f,
                    0, bladeBaseY + size * 0.17f, size * 0.024f, gripCol);

            float py = bladeBaseY + size * 0.20f;
            float pq = size * 0.028f;
            fillBuf.vertex(m, -pq, py - pq, 0).color(pommelCol);
            fillBuf.vertex(m, pq, py - pq, 0).color(pommelCol);
            fillBuf.vertex(m, pq, py + pq, 0).color(pommelCol);
            fillBuf.vertex(m, -pq, py + pq, 0).color(pommelCol);

            matrices.pop();
        }

        matrices.pop();
    }

    private static void xyRibbon(VertexConsumer buf, Matrix4f m,
                                 float x0, float y0, float x1, float y1,
                                 float w, int c) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-5f) return;
        float nx = (-dy / len) * w;
        float ny = (dx / len) * w;

        buf.vertex(m, x0 + nx, y0 + ny, 0).color(c);
        buf.vertex(m, x1 + nx, y1 + ny, 0).color(c);
        buf.vertex(m, x1 - nx, y1 - ny, 0).color(c);
        buf.vertex(m, x0 - nx, y0 - ny, 0).color(c);
    }

    public void resetCubeState() {
        cubeRotationY = 0.0F;
        cubeRotationX = 0.0F;
        cubeTargetY = 0.0F;
        cubeTargetX = 0.0F;
        cubeHitFlash = 0.0F;
        cubePrevHurtTime = 0;
        cubeLastUpdate = System.currentTimeMillis();
        cubeTrackedTarget = null;
        cubeLastCenter = null;
        cubeLastHalf = 0.6F;
        cubeLastFeetY = 0.0D;
        cubeShattered = false;
        cubeFragments.clear();
        cubeMatrix = null;
    }

    private float[][] buildCubeArms(float half) {
        float armLength = half * 0.46F;
        float inner = half - armLength;
        float[][] arms = new float[24][];
        int index = 0;
        for (int sideX = -1; sideX <= 1; sideX += 2) {
            for (int sideY = -1; sideY <= 1; sideY += 2) {
                for (int sideZ = -1; sideZ <= 1; sideZ += 2) {
                    float cornerX = sideX * half;
                    float cornerY = sideY * half;
                    float cornerZ = sideZ * half;
                    arms[index++] = new float[]{cornerX, cornerY, cornerZ, sideX * inner, cornerY, cornerZ};
                    arms[index++] = new float[]{cornerX, cornerY, cornerZ, cornerX, sideY * inner, cornerZ};
                    arms[index++] = new float[]{cornerX, cornerY, cornerZ, cornerX, cornerY, sideZ * inner};
                }
            }
        }
        return arms;
    }

    public void renderTargetCube(EventRender3D event, LivingEntity renderTarget, float alphaProgress,
                                 VertexConsumerProvider.Immediate immediate) {
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Vec3d feetPos = renderTarget.getLerpedPos(event.getTickDelta());
        float entityHeight = renderTarget.getHeight();
        float entityWidth = renderTarget.getWidth();

        if (renderTarget.isAlive()) {
            if (cubeTrackedTarget != renderTarget) {
                cubePrevHurtTime = renderTarget.hurtTime;
            }
            cubeTrackedTarget = renderTarget;
            cubeShattered = false;
        }

        float appear = (float) Easings.SINE_IN_OUT.ease(MathHelper.clamp(alphaProgress, 0.0F, 1.0F));
        if (appear <= 0.001F) {
            return;
        }

        float half = (entityWidth * 0.5F + 0.08F)
                * cubeSize.getValue()
                * (0.55F + 0.45F * appear);
        Vec3d center = feetPos.add(0.0D, entityHeight * 0.5D, 0.0D);
        cubeLastCenter = center;
        cubeLastHalf = half;
        cubeLastFeetY = feetPos.y;

        long now = System.currentTimeMillis();
        float deltaTime = MathHelper.clamp((now - cubeLastUpdate) / 1000.0F, 0.0F, 0.1F);
        cubeLastUpdate = now;
        float ease = 1.0F - (float) Math.exp(-(6.0F + cubeRotateSpeed.getValue() * 3.0F) * deltaTime);
        cubeRotationY += (cubeTargetY - cubeRotationY) * ease;
        cubeRotationX += (cubeTargetX - cubeRotationX) * ease;
        if (Math.abs(cubeTargetY - cubeRotationY) < 0.05F) {
            cubeRotationY = cubeTargetY;
        }
        if (Math.abs(cubeTargetX - cubeRotationX) < 0.05F) {
            cubeRotationX = cubeTargetX;
        }
        cubeHitFlash = Math.max(0.0F, cubeHitFlash - deltaTime * 2.2F);

        int baseColor = getCubeEspColor();
        int color = baseColor;
        if (cubeColorOnHit.getValue() && cubeHitFlash > 0.0F) {
            color = blendCubeColor(baseColor, ColorUtil.getColor(255, 25, 25, 255), cubeHitFlash);
        }
        color = multiplyCubeAlpha(replaceCubeAlpha(color, 190), appear);

        Quaternionf rotation = new Quaternionf()
                .rotateY((float) Math.toRadians(cubeRotationY))
                .rotateX((float) Math.toRadians(cubeRotationX));

        float coreHalf = getCubeDistanceScale(cameraPos, center.x, center.y, center.z)
                * cubeThickness.getValue() * 0.5F;
        VertexConsumer consumer = immediate.getBuffer(CUBE_LAYER);
        double relativeX = center.x - cameraPos.x;
        double relativeY = center.y - cameraPos.y;
        double relativeZ = center.z - cameraPos.z;

        cubeMatrix = event.getMatrixStack().peek().getPositionMatrix();
        for (float[] arm : buildCubeArms(half)) {
            drawCubeEdgeBar(consumer, rotation, relativeX, relativeY, relativeZ, arm, coreHalf, color);
        }
        for (int sideX = -1; sideX <= 1; sideX += 2) {
            for (int sideY = -1; sideY <= 1; sideY += 2) {
                for (int sideZ = -1; sideZ <= 1; sideZ += 2) {
                    drawCornerCube(consumer, rotation, relativeX, relativeY, relativeZ,
                            sideX * half, sideY * half, sideZ * half, coreHalf, color);
                }
            }
        }
        immediate.draw(CUBE_LAYER);

        if (cubeGlow.getValue()) {
            renderCubeGlow(event, rotation, relativeX, relativeY, relativeZ,
                    half, coreHalf, baseColor, appear, immediate);
        }
    }

    private void renderCubeGlow(EventRender3D event, Quaternionf rotation,
                                double relativeX, double relativeY, double relativeZ,
                                float half, float coreHalf, int baseColor, float appear,
                                VertexConsumerProvider.Immediate immediate) {
        RenderLayer glowLayer = ROMB_ESP.apply(Identifier.of("client", "textures/visuals/particles_2.png"));
        VertexConsumer glowConsumer = immediate.getBuffer(glowLayer);
        MatrixStack matrices = event.getMatrixStack();
        Quaternionf cameraRotation = mc.gameRenderer.getCamera().getRotation();
        int glowColor = multiplyCubeAlpha(replaceCubeAlpha(baseColor, 55), 0.45F * appear);
        float glowSize = coreHalf * 5.0F;

        for (float[] arm : buildCubeArms(half)) {
            for (int index = 0; index <= 2; index++) {
                float progress = index * 0.5F;
                float localX = arm[0] + (arm[3] - arm[0]) * progress;
                float localY = arm[1] + (arm[4] - arm[1]) * progress;
                float localZ = arm[2] + (arm[5] - arm[2]) * progress;
                Vector3f point = rotation.transform(new Vector3f(localX, localY, localZ));
                drawCubeGlowSprite(glowConsumer, matrices, cameraRotation,
                        relativeX + point.x, relativeY + point.y, relativeZ + point.z,
                        glowSize, glowColor);
            }
        }
        for (int sideX = -1; sideX <= 1; sideX += 2) {
            for (int sideY = -1; sideY <= 1; sideY += 2) {
                for (int sideZ = -1; sideZ <= 1; sideZ += 2) {
                    Vector3f point = rotation.transform(
                            new Vector3f(sideX * half, sideY * half, sideZ * half)
                    );
                    drawCubeGlowSprite(glowConsumer, matrices, cameraRotation,
                            relativeX + point.x, relativeY + point.y, relativeZ + point.z,
                            glowSize * 1.2F, glowColor);
                }
            }
        }
        immediate.draw(glowLayer);
    }

    private void drawCubeGlowSprite(VertexConsumer consumer, MatrixStack matrices,
                                    Quaternionf cameraRotation, double x, double y, double z,
                                    float size, int color) {
        if (((color >> 24) & 0xFF) <= 0) {
            return;
        }
        matrices.push();
        matrices.translate(x, y, z);
        matrices.multiply(cameraRotation);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        cubeGlowVertex(consumer, matrix, -size, -size, 0.0F, 0.0F, 1.0F, color);
        cubeGlowVertex(consumer, matrix, size, -size, 0.0F, 1.0F, 1.0F, color);
        cubeGlowVertex(consumer, matrix, size, size, 0.0F, 1.0F, 0.0F, color);
        cubeGlowVertex(consumer, matrix, -size, size, 0.0F, 0.0F, 0.0F, color);
        matrices.pop();
    }

    private void cubeGlowVertex(VertexConsumer consumer, Matrix4f matrix,
                                float x, float y, float z, float u, float v, int color) {
        consumer.vertex(matrix, x, y, z)
                .color(color)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(0xF000F0)
                .normal(0.0F, 0.0F, 1.0F);
    }

    private void drawCornerCube(VertexConsumer consumer, Quaternionf rotation,
                                double relativeX, double relativeY, double relativeZ,
                                float centerX, float centerY, float centerZ,
                                float thickness, int color) {
        if (((color >> 24) & 0xFF) <= 0) {
            return;
        }
        Vector3f n000 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(centerX - thickness, centerY - thickness, centerZ - thickness));
        Vector3f n100 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(centerX + thickness, centerY - thickness, centerZ - thickness));
        Vector3f n110 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(centerX + thickness, centerY + thickness, centerZ - thickness));
        Vector3f n010 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(centerX - thickness, centerY + thickness, centerZ - thickness));
        Vector3f n001 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(centerX - thickness, centerY - thickness, centerZ + thickness));
        Vector3f n101 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(centerX + thickness, centerY - thickness, centerZ + thickness));
        Vector3f n111 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(centerX + thickness, centerY + thickness, centerZ + thickness));
        Vector3f n011 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(centerX - thickness, centerY + thickness, centerZ + thickness));

        faceCubeQuad(consumer, cubeMatrix, n000, n100, n110, n010, color);
        faceCubeQuad(consumer, cubeMatrix, n001, n011, n111, n101, color);
        faceCubeQuad(consumer, cubeMatrix, n000, n001, n101, n100, color);
        faceCubeQuad(consumer, cubeMatrix, n010, n110, n111, n011, color);
        faceCubeQuad(consumer, cubeMatrix, n000, n010, n011, n001, color);
        faceCubeQuad(consumer, cubeMatrix, n100, n101, n111, n110, color);
    }

    private void drawCubeEdgeBar(VertexConsumer consumer, Quaternionf rotation,
                                 double relativeX, double relativeY, double relativeZ,
                                 float[] arm, float thickness, int color) {
        if (((color >> 24) & 0xFF) <= 0) {
            return;
        }
        Vector3f start = new Vector3f(arm[0], arm[1], arm[2]);
        Vector3f end = new Vector3f(arm[3], arm[4], arm[5]);
        Vector3f direction = new Vector3f(end).sub(start);

        Vector3f firstNormal;
        Vector3f secondNormal;
        if (Math.abs(direction.x) > 1.0E-5F) {
            firstNormal = new Vector3f(0.0F, thickness, 0.0F);
            secondNormal = new Vector3f(0.0F, 0.0F, thickness);
        } else if (Math.abs(direction.y) > 1.0E-5F) {
            firstNormal = new Vector3f(thickness, 0.0F, 0.0F);
            secondNormal = new Vector3f(0.0F, 0.0F, thickness);
        } else {
            firstNormal = new Vector3f(thickness, 0.0F, 0.0F);
            secondNormal = new Vector3f(0.0F, thickness, 0.0F);
        }

        Vector3f start00 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(start).sub(firstNormal).sub(secondNormal));
        Vector3f start10 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(start).add(firstNormal).sub(secondNormal));
        Vector3f start11 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(start).add(firstNormal).add(secondNormal));
        Vector3f start01 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(start).sub(firstNormal).add(secondNormal));
        Vector3f end00 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(end).sub(firstNormal).sub(secondNormal));
        Vector3f end10 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(end).add(firstNormal).sub(secondNormal));
        Vector3f end11 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(end).add(firstNormal).add(secondNormal));
        Vector3f end01 = cubeLocalToRelative(rotation, relativeX, relativeY, relativeZ,
                new Vector3f(end).sub(firstNormal).add(secondNormal));

        emitCubeBox(consumer, start00, start10, start11, start01, end00, end10, end11, end01, color);
    }

    private Vector3f cubeLocalToRelative(Quaternionf rotation,
                                         double relativeX, double relativeY, double relativeZ,
                                         Vector3f local) {
        rotation.transform(local);
        local.add((float) relativeX, (float) relativeY, (float) relativeZ);
        return local;
    }

    private void emitCubeBox(VertexConsumer consumer,
                             Vector3f start00, Vector3f start10, Vector3f start11, Vector3f start01,
                             Vector3f end00, Vector3f end10, Vector3f end11, Vector3f end01,
                             int color) {
        faceCubeQuad(consumer, cubeMatrix, start00, end00, end10, start10, color);
        faceCubeQuad(consumer, cubeMatrix, start10, end10, end11, start11, color);
        faceCubeQuad(consumer, cubeMatrix, start11, end11, end01, start01, color);
        faceCubeQuad(consumer, cubeMatrix, start01, end01, end00, start00, color);
    }

    private void faceCubeQuad(VertexConsumer consumer, Matrix4f matrix,
                              Vector3f first, Vector3f second, Vector3f third, Vector3f fourth,
                              int color) {
        consumer.vertex(matrix, first.x, first.y, first.z).color(color);
        consumer.vertex(matrix, second.x, second.y, second.z).color(color);
        consumer.vertex(matrix, third.x, third.y, third.z).color(color);
        consumer.vertex(matrix, fourth.x, fourth.y, fourth.z).color(color);
    }

    private void shatterCube() {
        float half = cubeLastHalf;
        Vec3d center = cubeLastCenter;
        Quaternionf rotation = new Quaternionf()
                .rotateY((float) Math.toRadians(cubeRotationY))
                .rotateX((float) Math.toRadians(cubeRotationX));
        double groundY = cubeLastFeetY + 0.05D;

        for (float[] arm : buildCubeArms(half)) {
            Vector3f start = rotation.transform(new Vector3f(arm[0], arm[1], arm[2]));
            Vector3f end = rotation.transform(new Vector3f(arm[3], arm[4], arm[5]));
            Vec3d worldStart = center.add(start.x, start.y, start.z);
            Vec3d worldEnd = center.add(end.x, end.y, end.z);
            Vec3d fragmentCenter = worldStart.add(worldEnd).multiply(0.5D);
            Vector3f offset = new Vector3f(
                    (float) (worldEnd.x - fragmentCenter.x),
                    (float) (worldEnd.y - fragmentCenter.y),
                    (float) (worldEnd.z - fragmentCenter.z)
            );

            Vec3d outward = fragmentCenter.subtract(center);
            if (outward.lengthSquared() < 1.0E-6D) {
                outward = new Vec3d(cubeRandom.nextDouble() - 0.5D, 0.0D,
                        cubeRandom.nextDouble() - 0.5D);
            }
            outward = outward.normalize();
            double speed = 0.12D + cubeRandom.nextDouble() * 0.14D;
            Vec3d velocity = outward.multiply(speed).add(
                    (cubeRandom.nextDouble() - 0.5D) * 0.06D,
                    0.08D + cubeRandom.nextDouble() * 0.16D,
                    (cubeRandom.nextDouble() - 0.5D) * 0.06D
            );

            Vector3f axis = new Vector3f(
                    cubeRandom.nextFloat() - 0.5F,
                    cubeRandom.nextFloat() - 0.5F,
                    cubeRandom.nextFloat() - 0.5F
            );
            if (axis.lengthSquared() < 1.0E-6F) {
                axis.set(0.0F, 1.0F, 0.0F);
            }
            axis.normalize();
            float angularSpeed = 0.12F + cubeRandom.nextFloat() * 0.28F;
            int maxAge = 50 + cubeRandom.nextInt(30);
            cubeFragments.add(new CubeFragment(fragmentCenter, offset, velocity, axis,
                    angularSpeed, groundY, maxAge));
        }
    }

    private void updateCubeFragments() {
        Iterator<CubeFragment> iterator = cubeFragments.iterator();
        while (iterator.hasNext()) {
            CubeFragment fragment = iterator.next();
            fragment.previousCenter = fragment.center;
            fragment.previousOffset = new Vector3f(fragment.offset);
            fragment.center = fragment.center.add(fragment.velocity);
            fragment.velocity = new Vec3d(
                    fragment.velocity.x * 0.98D,
                    fragment.velocity.y - 0.028D,
                    fragment.velocity.z * 0.98D
            );

            if (fragment.center.y <= fragment.groundY && fragment.velocity.y < 0.0D) {
                fragment.center = new Vec3d(fragment.center.x, fragment.groundY, fragment.center.z);
                fragment.velocity = new Vec3d(
                        fragment.velocity.x * 0.55D,
                        -fragment.velocity.y * 0.38D,
                        fragment.velocity.z * 0.55D
                );
                fragment.angularSpeed *= 0.6F;
            }

            Quaternionf spin = new Quaternionf().fromAxisAngleRad(
                    fragment.axis.x, fragment.axis.y, fragment.axis.z, fragment.angularSpeed
            );
            fragment.offset = spin.transform(new Vector3f(fragment.offset));
            fragment.age++;
            if (fragment.age >= fragment.maxAge) {
                iterator.remove();
            }
        }
    }

    public void renderTargetCubeFragments(EventRender3D event, VertexConsumerProvider.Immediate immediate) {
        if (cubeFragments.isEmpty()) return;

        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        int baseColor = getCubeEspColor();
        VertexConsumer consumer = immediate.getBuffer(CUBE_LAYER);
        cubeMatrix = event.getMatrixStack().peek().getPositionMatrix();
        float delta = event.getTickDelta();

        for (CubeFragment fragment : cubeFragments) {
            float life = 1.0F - (float) fragment.age / Math.max(1, fragment.maxAge);
            float fade = life > 0.3F ? 1.0F : Math.max(0.0F, life / 0.3F);
            if (fade <= 0.0F) {
                continue;
            }

            Vec3d center = new Vec3d(
                    MathHelper.lerp(delta, fragment.previousCenter.x, fragment.center.x),
                    MathHelper.lerp(delta, fragment.previousCenter.y, fragment.center.y),
                    MathHelper.lerp(delta, fragment.previousCenter.z, fragment.center.z)
            );
            Vector3f offset = new Vector3f(fragment.previousOffset).lerp(fragment.offset, delta);
            double relativeX = center.x - cameraPos.x;
            double relativeY = center.y - cameraPos.y;
            double relativeZ = center.z - cameraPos.z;
            float coreHalf = getCubeDistanceScale(cameraPos, center.x, center.y, center.z)
                    * cubeThickness.getValue() * 0.5F;
            int color = multiplyCubeAlpha(replaceCubeAlpha(baseColor, 255), fade);

            drawCubeFragmentBar(consumer,
                    (float) (relativeX - offset.x), (float) (relativeY - offset.y),
                    (float) (relativeZ - offset.z),
                    (float) (relativeX + offset.x), (float) (relativeY + offset.y),
                    (float) (relativeZ + offset.z),
                    coreHalf, color);
        }
        immediate.draw(CUBE_LAYER);
    }

    private void drawCubeFragmentBar(VertexConsumer consumer,
                                     float startX, float startY, float startZ,
                                     float endX, float endY, float endZ,
                                     float thickness, int color) {
        if (((color >> 24) & 0xFF) <= 0) {
            return;
        }
        Vector3f start = new Vector3f(startX, startY, startZ);
        Vector3f end = new Vector3f(endX, endY, endZ);
        Vector3f direction = new Vector3f(end).sub(start);
        if (direction.lengthSquared() < 1.0E-10F) {
            return;
        }
        direction.normalize();
        Vector3f reference = Math.abs(direction.y) < 0.99F
                ? new Vector3f(0.0F, 1.0F, 0.0F)
                : new Vector3f(1.0F, 0.0F, 0.0F);
        Vector3f firstNormal = new Vector3f();
        direction.cross(reference, firstNormal).normalize().mul(thickness);
        Vector3f secondNormal = new Vector3f();
        direction.cross(firstNormal, secondNormal).normalize().mul(thickness);

        Vector3f start00 = new Vector3f(start).sub(firstNormal).sub(secondNormal);
        Vector3f start10 = new Vector3f(start).add(firstNormal).sub(secondNormal);
        Vector3f start11 = new Vector3f(start).add(firstNormal).add(secondNormal);
        Vector3f start01 = new Vector3f(start).sub(firstNormal).add(secondNormal);
        Vector3f end00 = new Vector3f(end).sub(firstNormal).sub(secondNormal);
        Vector3f end10 = new Vector3f(end).add(firstNormal).sub(secondNormal);
        Vector3f end11 = new Vector3f(end).add(firstNormal).add(secondNormal);
        Vector3f end01 = new Vector3f(end).sub(firstNormal).add(secondNormal);
        emitCubeBox(consumer, start00, start10, start11, start01,
                end00, end10, end11, end01, color);
    }

    private float getCubeDistanceScale(Vec3d cameraPos, double worldX, double worldY, double worldZ) {
        double deltaX = worldX - cameraPos.x;
        double deltaY = worldY - cameraPos.y;
        double deltaZ = worldZ - cameraPos.z;
        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        return (float) Math.max(0.1D, distance * 0.007D);
    }

    private int getCubeEspColor() {
        int color = ColorUtil.getClientColor1(1);
        if (((color >> 24) & 0xFF) == 0) {
            color |= 0xFF000000;
        }
        return color;
    }

    private int multiplyCubeAlpha(int color, float multiplier) {
        int alpha = MathHelper.clamp((int) (((color >> 24) & 0xFF) * multiplier), 0, 255);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private int replaceCubeAlpha(int color, int alpha) {
        return (MathHelper.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private int blendCubeColor(int first, int second, float factor) {
        float clampedFactor = MathHelper.clamp(factor, 0.0F, 1.0F);
        int firstRed = (first >> 16) & 0xFF;
        int firstGreen = (first >> 8) & 0xFF;
        int firstBlue = first & 0xFF;
        int firstAlpha = (first >> 24) & 0xFF;
        int secondRed = (second >> 16) & 0xFF;
        int secondGreen = (second >> 8) & 0xFF;
        int secondBlue = second & 0xFF;
        int secondAlpha = (second >> 24) & 0xFF;
        int red = (int) (firstRed + (secondRed - firstRed) * clampedFactor);
        int green = (int) (firstGreen + (secondGreen - firstGreen) * clampedFactor);
        int blue = (int) (firstBlue + (secondBlue - firstBlue) * clampedFactor);
        int alpha = (int) (firstAlpha + (secondAlpha - firstAlpha) * clampedFactor);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static class CubeFragment {
        private Vec3d previousCenter;
        private Vec3d center;
        private Vector3f previousOffset;
        private Vector3f offset;
        private Vec3d velocity;
        private final Vector3f axis;
        private float angularSpeed;
        private final double groundY;
        private final int maxAge;
        private int age;

        private CubeFragment(Vec3d center, Vector3f offset, Vec3d velocity, Vector3f axis,
                             float angularSpeed, double groundY, int maxAge) {
            this.previousCenter = center;
            this.center = center;
            this.previousOffset = new Vector3f(offset);
            this.offset = offset;
            this.velocity = velocity;
            this.axis = axis;
            this.angularSpeed = angularSpeed;
            this.groundY = groundY;
            this.maxAge = maxAge;
        }
    }
}


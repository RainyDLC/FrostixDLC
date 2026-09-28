package rtx.kimiko.utils.render.shaders;

import java.util.HashMap;
import java.util.Map;
import kotlin.jvm.JvmField;
import org.jetbrains.annotations.NotNull;
import rtx.kimiko.utils.render.shaders.core.CoreShaders;
import rtx.kimiko.utils.render.shaders.effects.frag_effect_scan.FragEffectScanShaders;
import rtx.kimiko.utils.render.shaders.effects.hands_flame.HandsFlameShaders;
import rtx.kimiko.utils.render.shaders.effects.hands_hologram.HandsHologramShaders;
import rtx.kimiko.utils.render.shaders.effects.lyrics_text.LyricsTextShaders;
import rtx.kimiko.utils.render.shaders.include.IncludeShaders;
import rtx.kimiko.utils.render.shaders.post.ambiencefog.AmbiencefogShaders;
import rtx.kimiko.utils.render.shaders.post.customsky.CustomskyFastShaders;
import rtx.kimiko.utils.render.shaders.post.customsky.CustomskyShaders;
import rtx.kimiko.utils.render.shaders.post.explosionwave.ExplosionwaveShaders;
import rtx.kimiko.utils.render.shaders.post.fogblur.FogblurShaders;
import rtx.kimiko.utils.render.shaders.post.glassvapor.GlassvaporShaders;
import rtx.kimiko.utils.render.shaders.post.glowesp.GlowespShaders;
import rtx.kimiko.utils.render.shaders.post.groundreflect.GroundreflectShaders;
import rtx.kimiko.utils.render.shaders.post.guilayerblur.GuilayerblurShaders;
import rtx.kimiko.utils.render.shaders.post.guimotionblur.GuimotionblurShaders;
import rtx.kimiko.utils.render.shaders.post.hitbubbles.HitbubblesShaders;
import rtx.kimiko.utils.render.shaders.post.hpfocus.HpfocusShaders;
import rtx.kimiko.utils.render.shaders.post.hudlayer.HudlayerShaders;
import rtx.kimiko.utils.render.shaders.post.itemoutline.ItemoutlineShaders;
import rtx.kimiko.utils.render.shaders.post.jumpdistort.JumpdistortShaders;
import rtx.kimiko.utils.render.shaders.post.jumpsouls.JumpsoulsShaders;
import rtx.kimiko.utils.render.shaders.post.killdistortion.KilldistortionShaders;
import rtx.kimiko.utils.render.shaders.post.liquidpanel.LiquidpanelShaders;
import rtx.kimiko.utils.render.shaders.post.saturation.SaturationShaders;
import rtx.kimiko.utils.render.shaders.post.scarglass.ScarglassShaders;
import rtx.kimiko.utils.render.shaders.post.shaderhands.ShaderhandsShaders;
import rtx.kimiko.utils.render.shaders.post.targetcircle.TargetcircleShaders;
import rtx.kimiko.utils.render.shaders.post.themeshock.ThemeshockShaders;
import rtx.kimiko.utils.render.shaders.post.trailglass.TrailglassShaders;
import rtx.kimiko.utils.render.shaders.post.wasted.WastedShaders;
import rtx.kimiko.utils.render.shaders.ui.batched_blur.BatchedBlurShaders;
import rtx.kimiko.utils.render.shaders.ui.glass.GlassShaders;
import rtx.kimiko.utils.render.shaders.ui.glow.GlowShaders;
import rtx.kimiko.utils.render.shaders.ui.kawase.KawaseShaders;
import rtx.kimiko.utils.render.shaders.ui.radialglass.RadialglassShaders;
import rtx.kimiko.utils.render.shaders.ui.sectormask.SectormaskShaders;
import rtx.kimiko.utils.render.shaders.ui.shape.ShapeShaders;

public final class EmbeddedShaders {
    @NotNull
    public static final EmbeddedShaders INSTANCE = new EmbeddedShaders();
    @JvmField
    @NotNull
    public static final Map<String, String> SOURCES = new HashMap<>();

    private EmbeddedShaders() {
    }

    static {
        RootShaders.register(SOURCES);
        CoreShaders.register(SOURCES);
        FragEffectScanShaders.register(SOURCES);
        HandsFlameShaders.register(SOURCES);
        HandsHologramShaders.register(SOURCES);
        LyricsTextShaders.register(SOURCES);
        IncludeShaders.register(SOURCES);
        AmbiencefogShaders.register(SOURCES);
        CustomskyShaders.register(SOURCES);
        // оптимизированные blackhole/composite поверх старых (картинка та же, FPS выше)
        CustomskyFastShaders.register(SOURCES);
        ExplosionwaveShaders.register(SOURCES);
        FogblurShaders.register(SOURCES);
        GlassvaporShaders.register(SOURCES);
        GlowespShaders.register(SOURCES);
        GroundreflectShaders.register(SOURCES);
        GuilayerblurShaders.register(SOURCES);
        GuimotionblurShaders.register(SOURCES);
        HitbubblesShaders.register(SOURCES);
        HpfocusShaders.register(SOURCES);
        HudlayerShaders.register(SOURCES);
        ItemoutlineShaders.register(SOURCES);
        JumpdistortShaders.register(SOURCES);
        JumpsoulsShaders.register(SOURCES);
        KilldistortionShaders.register(SOURCES);
        LiquidpanelShaders.register(SOURCES);
        SaturationShaders.register(SOURCES);
        ScarglassShaders.register(SOURCES);
        ShaderhandsShaders.register(SOURCES);
        TargetcircleShaders.register(SOURCES);
        ThemeshockShaders.register(SOURCES);
        TrailglassShaders.register(SOURCES);
        WastedShaders.register(SOURCES);
        BatchedBlurShaders.register(SOURCES);
        GlassShaders.register(SOURCES);
        GlowShaders.register(SOURCES);
        KawaseShaders.register(SOURCES);
        RadialglassShaders.register(SOURCES);
        SectormaskShaders.register(SOURCES);
        ShapeShaders.register(SOURCES);
    }
}

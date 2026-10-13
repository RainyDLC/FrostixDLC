package dev.hatek.client.render.killeffect;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hatek.client.module.impl.render.KillEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Owns snapshots, never retains dead entities. All geometry is world-depth tested. */
public final class KillEffectRenderer {
    private static final int MAX_EFFECTS = 8;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final double RANGE_SQUARED = 48 * 48;
    private static final Identifier ATLAS = Identifier.fromNamespaceAndPath("hatek_client", "killeffect/atlas");
    private static final List<Effect> EFFECTS = new ArrayList<>();
    // A death latch survives animation expiry, but not respawn/removal/world changes.
    private static final KillEffectDeathLatch DEATHS = new KillEffectDeathLatch();
    private static ClientLevel lastLevel;
    private static boolean atlasReady;
    private static boolean atlasFailed;

    private record Effect(UUID uuid, Vec3 position, float yaw, float gripYaw, PlayerSkin skin, long born,
                          double speed, double size, boolean vortex, double initialHeight) {
        double age(long now) { return Math.max(0, (now - born) / 1e9 * speed); }
        double duration() { return vortex ? KillEffectTimeline.VORTEX_SECONDS : KillEffectTimeline.DEMON_SECONDS; }
    }
    private record Vertex(float x, float y, float z, float u, float v) {
        Vertex lerp(Vertex b, float t) {
            return new Vertex(x + (b.x-x)*t, y + (b.y-y)*t, z + (b.z-z)*t,
                    u + (b.u-u)*t, v + (b.v-v)*t);
        }
    }
    private KillEffectRenderer() { }

    public static void clear() {
        EFFECTS.clear(); DEATHS.clear(); lastLevel = null;
    }
    public static boolean hides(int id) { return !atlasFailed && DEATHS.contains(id); }

    public static void tick(boolean vortex, double speed, double size) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { clear(); return; }
        if (lastLevel != mc.level) { clear(); lastLevel = mc.level; }
        long now = System.nanoTime();
        EFFECTS.removeIf(e -> e.age(now) >= e.duration());
        Set<Integer> loaded = new HashSet<>();
        for (AbstractClientPlayer player : mc.level.players()) {
            loaded.add(player.getId());
            // Health polling is only a fallback. Status 3 is the authoritative remote death event.
            if (player != mc.player && (player.isDeadOrDying() || player.deathTime > 0)) {
                onDeath(player, vortex, speed, size);
            }
        }
        DEATHS.retain(loaded);
    }

    public static void onDeath(AbstractClientPlayer player, boolean vortex, double speed, double size) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || player == mc.player || player.level() != mc.level
                || player.distanceToSqr(mc.player) > RANGE_SQUARED) return;
        if (lastLevel != mc.level) { clear(); lastLevel = mc.level; }
        if (DEATHS.mark(player.getId(), player.getUUID())) {
            add(capture(player, vortex, speed, size, System.nanoTime()));
        }
    }

    /** Removal/respawn clears suppression, never the independent cinematic snapshot. */
    public static void removed(int entityId) { DEATHS.remove(entityId); }

    private static Effect capture(AbstractClientPlayer player, boolean vortex, double speed,
                                  double size, long now) {
        Vec3 feet = player.position();
        HitResult ground = player.level().clip(new ClipContext(feet.add(0, 0.25, 0), feet.add(0, -8, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 origin = ground.getType() == HitResult.Type.MISS ? feet : ground.getLocation();
        return new Effect(player.getUUID(), origin, player.getYRot(), gripFacing(origin), player.getSkin(), now,
                speed, size, vortex, Math.max(0, feet.y - origin.y));
    }
    private static float gripFacing(Vec3 origin) {
        // The grabbing hand is on the observer-facing edge, frozen at capture time.
        Vec3 observer = Minecraft.getInstance().gameRenderer.mainCamera().position();
        return (float) Math.atan2(observer.z-origin.z, -(observer.x-origin.x));
    }
    private static void add(Effect effect) {
        if (EFFECTS.size() >= MAX_EFFECTS) EFFECTS.removeFirst();
        EFFECTS.add(effect);
    }
    public static void preview(boolean vortex, double speed, double size) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (lastLevel != mc.level) { clear(); lastLevel = mc.level; }
        Effect src = capture(mc.player, vortex, speed, size, System.nanoTime());
        Vec3 offset = mc.player.getLookAngle().multiply(1, 0, 1).normalize().scale(3);
        Vec3 point = mc.player.position().add(offset);
        HitResult ground = mc.level.clip(new ClipContext(point.add(0, 2, 0), point.add(0, -8, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (ground.getType() != HitResult.Type.MISS) point = ground.getLocation();
        add(new Effect(src.uuid, point, src.yaw, gripFacing(point), src.skin, src.born, speed, size, vortex, 0));
    }

    private static boolean ensureAtlas() {
        if (atlasReady) return true;
        if (atlasFailed) return false;
        // PNG is stored losslessly as text to keep the GitHub text-file commit path binary-safe.
        // Decode once; this is a real RGBA PNG texture, not particles standing in for an asset.
        try (InputStream in = KillEffectRenderer.class.getResourceAsStream(
                "/assets/hatek_client/textures/killeffect/atlas.png.b64")) {
            if (in == null) throw new IllegalStateException("Missing KillEffect atlas");
            byte[] png = Base64.getMimeDecoder().decode(new String(in.readAllBytes(), StandardCharsets.US_ASCII));
            NativeImage image = NativeImage.read(png);
            DynamicTexture texture;
            try { texture = new DynamicTexture(() -> "RainyDLC KillEffect atlas", image); }
            catch (Throwable error) { image.close(); throw error; }
            try { Minecraft.getInstance().getTextureManager().register(ATLAS, texture); }
            catch (Throwable error) { texture.close(); throw error; }
            atlasReady = true;
            return true;
        } catch (Exception error) {
            atlasFailed = true;
            LoggerFactory.getLogger("RainyDLC-KillEffect").error("Cannot load KillEffect texture; leaving vanilla corpses visible", error);
            return false;
        }
    }

    public static void render(PoseStack stack, LevelRenderState state, SubmitNodeCollector collector) {
        if (!KillEffect.active() || EFFECTS.isEmpty() || !(collector instanceof OrderedSubmitNodeCollector ordered)) return;
        if (Minecraft.getInstance().level != lastLevel || !ensureAtlas()) return;
        long now = System.nanoTime();
        Vec3 camera = state.cameraRenderState.pos;
        for (Effect effect : List.copyOf(EFFECTS)) {
            double t = effect.age(now);
            if (t >= effect.duration() || effect.position.distanceToSqr(camera) > RANGE_SQUARED) continue;
            KillEffectTimeline.Pose frame = effect.vortex ? KillEffectTimeline.vortex(t) : KillEffectTimeline.demon(t);
            stack.pushPose();
            stack.translate(effect.position.x-camera.x, effect.position.y-camera.y, effect.position.z-camera.z);
            stack.mulPose(new org.joml.Quaternionf().rotationY(effect.vortex
                    ? (float) Math.toRadians(180-effect.yaw) : effect.gripYaw));
            // Callbacks capture immutable samples; no game/entity access from deferred rendering.
            ordered.submitCustomGeometry(stack, RenderTypes.entityTranslucentEmissive(effect.skin.body().texturePath()),
                    (pose, out) -> body(pose, out, effect, frame, t));
            ordered.submitCustomGeometry(stack, RenderTypes.entityTranslucentEmissive(ATLAS),
                    (pose, out) -> {
                        if (effect.vortex) vortex(pose, out, effect, frame, t);
                        else demon(pose, out, effect, frame, t);
                    });
            stack.popPose();
        }
    }

    private static void body(PoseStack.Pose pose, VertexConsumer out, Effect e, KillEffectTimeline.Pose f, double t) {
        float alpha = (float) f.bodyAlpha();
        if (alpha <= 0.005f) return;
        float fall = (float) KillEffectTimeline.smooth(0.94, 1.28, t);
        float bodyY = (float) (f.bodyY() + e.initialHeight * (e.vortex ? 1 : 1-fall));
        float relativeYaw = (float) Math.atan2(Math.sin(Math.toRadians(180-e.yaw)-e.gripYaw),
                Math.cos(Math.toRadians(180-e.yaw)-e.gripYaw));
        Matrix4f root = new Matrix4f().rotateY(e.vortex ? 0 : relativeYaw*(1-fall)).translate(0, bodyY, 0);
        float minY = e.vortex || t < 0.94 ? -100 : 0.035f;
        int color = ((int)(alpha*255) << 24) | 0xFFFFFF;
        boolean slim = e.skin.model() == PlayerModelType.SLIM;
        float aw = slim ? 3f/16 : 4f/16;
        // Genuine 64x64 skin UVs, including the outer clothing/hat layer.
        part(pose, out, root, -.25f, .75f, -.125f, .5f, .75f, .25f, 16, 16, 8, 12, 4, 0, minY, color);
        part(pose, out, root, -.25f, .75f, -.125f, .5f, .75f, .25f, 16, 32, 8, 12, 4, .008f, minY, color);
        Matrix4f head = new Matrix4f(root).translate(0,1.5f,0).rotateX((float)(-.3*f.armSpread()));
        part(pose, out, head, -.25f, 0, -.25f, .5f, .5f, .5f, 0, 0, 8, 8, 8, 0, minY, color);
        part(pose, out, head, -.25f, 0, -.25f, .5f, .5f, .5f, 32, 0, 8, 8, 8, .012f, minY, color);
        for (int side : new int[]{-1,1}) {
            float spread = (float) f.armSpread();
            float grip = (float) f.grip();
            if (!e.vortex && side == -1 && grip > 0) {
                grippingArm(pose, out, e, f, root, aw, slim, minY, color);
            } else {
                Matrix4f arm = new Matrix4f(root).translate(side*(.25f+aw/2),1.45f,0)
                        .rotateZ(side*spread*2.12f).rotateX(e.vortex ? .06f : -.18f*spread);
                int u = side == -1 ? 40 : 32, v = side == -1 ? 16 : 48;
                part(pose,out,arm,-aw/2,-.75f,-.125f,aw,.75f,.25f,u,v,slim?3:4,12,4,0,minY,color);
                part(pose,out,arm,-aw/2,-.75f,-.125f,aw,.75f,.25f,side==-1?40:48,side==-1?32:48,slim?3:4,12,4,.006f,minY,color);
            }
            Matrix4f leg = new Matrix4f(root).translate(side*.125f,.75f,0).rotateZ(side*spread*.10f);
            part(pose,out,leg,-.125f,-.75f,-.125f,.25f,.75f,.25f,side==-1?0:16,side==-1?16:48,4,12,4,0,minY,color);
            part(pose,out,leg,-.125f,-.75f,-.125f,.25f,.75f,.25f,0,side==-1?32:48,4,12,4,.006f,minY,color);
        }
    }

    private static void grippingArm(PoseStack.Pose pose, VertexConsumer out, Effect e,
                                    KillEffectTimeline.Pose f, Matrix4f root, float aw,
                                    boolean slim, float minY, int color) {
        float release=(float)f.slip(),reach=(float)f.grip();
        float x=(float)(-e.size*.82 + release*e.size*.42);
        Matrix4f raised=new Matrix4f(root).translate(-.25f-aw/2,1.45f,0)
                .rotateZ((float)(-f.armSpread()*2.12)).rotateX((float)(-.18*f.armSpread()));
        Vector3f shoulder=raised.transformPosition(new Vector3f());
        Vector3f elbow=raised.transformPosition(new Vector3f(0,-.375f,0))
                .lerp(new Vector3f(x+.28f,.22f-release*.85f,.035f),reach);
        Vector3f wrist=raised.transformPosition(new Vector3f(0,-.75f,0))
                .lerp(new Vector3f(x,.105f-release*1.15f,0),reach);
        // Minecraft-style rectangular arm: the skinned forearm end is the hand.
        // No extra palm, thumb, fingers, curling phalanges or sliding finger animation.
        // Reach blends from the actual falling pose; the end is planted on the lip,
        // then the entire arm breaks free and drops in 140 ms.
        limb(pose,out,shoulder,elbow,aw,40,16,slim,minY,color);
        limb(pose,out,elbow,wrist,aw,40,22,slim,minY,color);
    }
    private static void limb(PoseStack.Pose pose,VertexConsumer out,Vector3f start,Vector3f end,
                             float width,int u,int v,boolean slim,float minY,int color) {
        Vector3f delta=new Vector3f(end).sub(start);float length=delta.length();
        if(length<.001f)return;
        Matrix4f matrix=new Matrix4f().translate(start)
                .rotate(new org.joml.Quaternionf().rotationTo(new Vector3f(0,-1,0),delta.div(length)))
                .scale(1,length/.375f,1);
        part(pose,out,matrix,-width/2,-.375f,-.125f,width,.375f,.25f,u,v,slim?3:4,6,4,0,minY,color);
        part(pose,out,matrix,-width/2,-.375f,-.125f,width,.375f,.25f,u,v+16,slim?3:4,6,4,.006f,minY,color);
    }

    private static void part(PoseStack.Pose pose, VertexConsumer out, Matrix4f m,
                             float x,float y,float z,float w,float h,float d,
                             int u,int v,int pw,int ph,int pd,float inflate,float minY,int color) {
        float a=x-inflate,b=y-inflate,c=z-inflate,X=x+w+inflate,Y=y+h+inflate,Z=z+d+inflate;
        // Front/back, side, top/bottom use the standard unfolded Minecraft cube layout.
        skinFace(pose,out,m,new float[]{a,b,c,X,b,c,X,Y,c,a,Y,c},u+pd,v+pd,pw,ph,minY,color);
        skinFace(pose,out,m,new float[]{X,b,Z,a,b,Z,a,Y,Z,X,Y,Z},u+pd+pw+pd,v+pd,pw,ph,minY,color);
        skinFace(pose,out,m,new float[]{a,b,Z,a,b,c,a,Y,c,a,Y,Z},u,v+pd,pd,ph,minY,color);
        skinFace(pose,out,m,new float[]{X,b,c,X,b,Z,X,Y,Z,X,Y,c},u+pd+pw,v+pd,pd,ph,minY,color);
        skinFace(pose,out,m,new float[]{a,Y,c,X,Y,c,X,Y,Z,a,Y,Z},u+pd,v,pw,pd,minY,color);
        skinFace(pose,out,m,new float[]{a,b,Z,X,b,Z,X,b,c,a,b,c},u+pd+pw,v,pw,pd,minY,color);
    }
    private static void skinFace(PoseStack.Pose pose, VertexConsumer out, Matrix4f m,
                                 float[] p,int u,int v,int w,int h,float minY,int color) {
        List<Vertex> poly=new ArrayList<>(4);
        float[] uv={u/64f,(v+h)/64f,(u+w)/64f,(v+h)/64f,(u+w)/64f,v/64f,u/64f,v/64f};
        for(int i=0;i<4;i++) {
            Vector3f pt=m.transformPosition(new Vector3f(p[i*3],p[i*3+1],p[i*3+2]));
            poly.add(new Vertex(pt.x,pt.y,pt.z,uv[i*2],uv[i*2+1]));
        }
        // Clip the clone itself, not the world. No fake blocks, digging or server packets.
        List<Vertex> clipped=new ArrayList<>(6);
        for(int i=0;i<poly.size();i++) {
            Vertex a=poly.get(i),b=poly.get((i+1)%poly.size());
            if(a.y>=minY) clipped.add(a);
            if((a.y>=minY)!=(b.y>=minY)) clipped.add(a.lerp(b,(minY-a.y)/(b.y-a.y)));
        }
        if(clipped.size()==4) quad(pose,out,clipped.get(0),clipped.get(1),clipped.get(2),clipped.get(3),color);
        else for(int i=1;i+1<clipped.size();i++) quad(pose,out,clipped.get(0),clipped.get(i),clipped.get(i+1),clipped.get(i+1),color);
    }

    private static void demon(PoseStack.Pose pose, VertexConsumer out, Effect e, KillEffectTimeline.Pose f, double t) {
        double radius=1.24*e.size*f.pitScale();
        if(radius<.005) return;
        // Organic crimson/charcoal stain: no circular aperture or glowing annulus.
        // Keep its silhouette still so the hand remains planted on the same edge.
        ground(pose,out,0,0,.025f,(float)radius,0,0xFFFFFFFF,0);
        ground(pose,out,(float)(.018*Math.sin(t*1.9)),(float)(.018*Math.cos(t*1.4)),.027f,
                (float)(radius*.985),0,argb(.12+.07*Math.sin(t*3),.75,.10,.55),0);
        // Seeded curved ember paths: no per-frame RNG or frame-rate-dependent integration.
        int seed=e.uuid.hashCode();
        for(int i=0;i<42;i++) {
            double phase=fract(t*.65+i*.618+seed*.000001),a=i*2.399+t*1.9;
            double r=radius*(.30+.73*noise(i,seed));
            float x=(float)(Math.cos(a)*r),z=(float)(Math.sin(a)*r),y=(float)(.08+phase*1.6);
            float sz=(float)(.028+.025*noise(i+87,seed));
            cross(pose,out,x,y,z,sz,2,argb(Math.sin(phase*Math.PI)*f.pitScale(),1,.28,.08));
        }
        // Irregular wisps emerge from the stain, never forming a circular border.
        for(int i=0;i<12;i++) {
            double a=i*2.399+t*.25;
            double rim=radius*(.45+.42*noise(i+397,seed));
            cross(pose,out,(float)(Math.cos(a)*rim),(float)(.10+.05*Math.sin(a*3+t)),
                    (float)(Math.sin(a)*rim),.22f,3,argb(.3*f.pitScale(),.38,.08,.06));
        }
    }

    private static void vortex(PoseStack.Pose pose, VertexConsumer out, Effect e, KillEffectTimeline.Pose f, double t) {
        if(f.dustAlpha()<.005) return;
        int seed=e.uuid.hashCode();
        float base=(float)(f.dustY()+e.initialHeight*(1-KillEffectTimeline.smooth(.15,1.15,t)));
        double fade=f.dustAlpha()*f.dustAlpha(),dissolve=KillEffectTimeline.smooth(1.85,3,t);
        // Three volumetric helical clouds. Feathered puffs overlap into a funnel;
        // no opaque cone mesh, hard top rim or tiny tiled-ribbon gaps.
        for(int layer=0;layer<3;layer++) for(int ring=0;ring<20;ring++) {
            double h=ring/19.0,height=h*3.15;
            double rad=(.33+h*.80)*e.size*(.48+layer*.26)*(1+dissolve*.9);
            double spin=t*(5.6-layer*.4)+height*3.4+layer*2.094;
            for(int segment=0;segment<10;segment++) {
                int n=ring*10+segment+layer*477;
                double a=segment*Math.PI*2/10+spin;
                double scatter=(noise(n,seed)-.5)*(.14+dissolve*1.6);
                float px=(float)(Math.cos(a)*(rad+scatter));
                float pz=(float)(Math.sin(a)*(rad+scatter));
                float py=base+(float)(height+scatter+dissolve*noise(n+419,seed)*.8);
                float puff=(float)((.23+h*.26+noise(n+73,seed)*.14)*e.size*(1+dissolve*.65));
                double grain=.68+.32*noise(n+107,seed);
                double top=1-KillEffectTimeline.smooth(.82,1,h);
                double alpha=fade*grain*.64*top*(1-dissolve*.4);
                double shade=.72+.28*Math.sin(a*2-height*2.4+t*7);
                cross(pose,out,px,py,pz,puff,1,argb(alpha,(.92-.12*h)*shade,(.80-.12*h)*shade,(.63-.10*h)*shade));
            }
        }
        for(int i=0;i<70;i++) {
            double phase=fract(i*.618+t*.24),angle=i*2.399+t*(4+noise(i,seed)*2);
            double radius=e.size*(.4+phase*.9)*(1+dissolve*1.7);
            float x=(float)(Math.cos(angle)*radius),z=(float)(Math.sin(angle)*radius);
            float y=base+(float)(phase*3.4+dissolve*noise(i+83,seed)*1.5);
            cross(pose,out,x,y,z,(float)(.06+.12*noise(i+71,seed)),1,
                    argb(fade*(1-dissolve)*.75,.72,.63,.49));
        }
        ground(pose,out,0,0,.034f,(float)(1.15*e.size),3,argb(fade*.55,.50,.41,.29),(float)t);
    }

    private static double fract(double n) { return n-Math.floor(n); }
    private static double noise(int i,int seed) {
        long x=(i*0x9E3779B9L)^seed;x^=x>>>16;x*=0x85ebca6bL;x^=x>>>13;
        return (x&0xFFFFFF)/(double)0xFFFFFF;
    }
    private static int argb(double a,double r,double g,double b) {
        return ((int)(Math.clamp(a,0,1)*255)<<24)|((int)(r*255)<<16)|((int)(g*255)<<8)|(int)(b*255);
    }
    private static void ground(PoseStack.Pose pose,VertexConsumer out,float x,float z,float y,float r,int tile,int color,float angle) {
        float[] points=new float[12];
        float[] xs={-r,r,r,-r},zs={-r,-r,r,r};
        for(int i=0;i<4;i++) {points[i*3]=x+xs[i]*(float)Math.cos(angle)-zs[i]*(float)Math.sin(angle);
            points[i*3+1]=y;points[i*3+2]=z+xs[i]*(float)Math.sin(angle)+zs[i]*(float)Math.cos(angle);}
        atlasQuad(pose,out,tile,points,color);
    }
    private static void cross(PoseStack.Pose pose,VertexConsumer out,float x,float y,float z,float s,int tile,int color) {
        atlasQuad(pose,out,tile,new float[]{x-s,y-s,z,x+s,y-s,z,x+s,y+s,z,x-s,y+s,z},color);
        atlasQuad(pose,out,tile,new float[]{x,y-s,z-s,x,y-s,z+s,x,y+s,z+s,x,y+s,z-s},color);
    }
    private static void atlasQuad(PoseStack.Pose pose,VertexConsumer out,int tile,float[] p,int color) {
        float u=(tile%2)*.5f+.5f/512,v=(tile/2)*.5f+.5f/512,end=.5f-1f/512;
        quad(pose,out,new Vertex(p[0],p[1],p[2],u,v+end),new Vertex(p[3],p[4],p[5],u+end,v+end),
                new Vertex(p[6],p[7],p[8],u+end,v),new Vertex(p[9],p[10],p[11],u,v),color);
    }
    private static void quad(PoseStack.Pose pose,VertexConsumer out,Vertex a,Vertex b,Vertex c,Vertex d,int color) {
        Vector3f normal=new Vector3f(b.x-a.x,b.y-a.y,b.z-a.z)
                .cross(c.x-a.x,c.y-a.y,c.z-a.z);
        if(normal.lengthSquared()<1e-10f) normal.set(0,1,0);else normal.normalize();
        for(Vertex p:new Vertex[]{a,b,c,d}) out.addVertex(pose.pose(),p.x,p.y,p.z).setColor(color)
                .setUv(p.u,p.v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(pose,normal.x,normal.y,normal.z);
    }
}

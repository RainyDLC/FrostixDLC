package dev.hatek.client.render.killeffect;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Exercises the actual deferred geometry callbacks without an OpenGL context. */
public final class VfxGeometryRegression {
    private static final class Sink implements InvocationHandler {
        final List<String> rows=new ArrayList<>();
        float x,y,z,u,v; int color=-1; boolean pending;
        void flush(){if(pending)rows.add(x+"\t"+y+"\t"+z+"\t"+u+"\t"+v+"\t"+Integer.toUnsignedString(color));pending=false;}
        @Override public Object invoke(Object proxy,Method method,Object[] args)throws Throwable {
            if(method.isDefault()) return InvocationHandler.invokeDefault(proxy,method,args);
            switch(method.getName()) {
                case "addVertex" -> {flush(); x=(Float)args[0];y=(Float)args[1];z=(Float)args[2];pending=true;}
                case "setUv" -> {u=(Float)args[0];v=(Float)args[1];}
                case "setColor" -> {
                    if(args.length==1)color=(Integer)args[0];
                    else color=((Integer)args[3]<<24)|((Integer)args[0]<<16)|((Integer)args[1]<<8)|(Integer)args[2];
                }
                default -> { }
            }
            return proxy;
        }
    }
    public static void main(String[] args)throws Exception {
        Path output=Path.of(System.getProperty("vfx.preview","build/vfx-preview"));Files.createDirectories(output);
        Class<?> effect=Arrays.stream(KillEffectRenderer.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("Effect")).findFirst().orElseThrow();
        Constructor<?> ctor=effect.getDeclaredConstructors()[0];ctor.setAccessible(true);
        Method body=KillEffectRenderer.class.getDeclaredMethod("body",PoseStack.Pose.class,VertexConsumer.class,effect,KillEffectTimeline.Pose.class,double.class);body.setAccessible(true);
        int total=0;
        for(boolean vortex:new boolean[]{false,true}) for(double t:vortex?new double[]{.55,1.45,2.4}:new double[]{.70,1.18,1.8,2.90,3.15}) {
            float gripYaw=(float)Math.atan2(-6,-4);
            float actorYaw=(float)(180-Math.toDegrees(gripYaw));
            Object sample=ctor.newInstance(new UUID(0,123),Vec3.ZERO,actorYaw,gripYaw,new PlayerSkin(null,null,null,PlayerModelType.WIDE,false),0L,1d,1d,vortex,0d);
            var frame=vortex?KillEffectTimeline.vortex(t):KillEffectTimeline.demon(t);
            String prefix=(vortex?"vortex":"demon")+"-"+t;
            for(String material:new String[]{"skin","fx"}) {
                Sink sink=new Sink();
                VertexConsumer consumer=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class[]{VertexConsumer.class},sink);
                PoseStack poses=new PoseStack();
                if(!vortex)poses.mulPose(new org.joml.Quaternionf().rotationY(gripYaw));
                if(material.equals("skin"))body.invoke(null,poses.last(),consumer,sample,frame,t);
                else {
                    Method fx=KillEffectRenderer.class.getDeclaredMethod(vortex?"vortex":"demon",PoseStack.Pose.class,VertexConsumer.class,effect,KillEffectTimeline.Pose.class,double.class);
                    fx.setAccessible(true);fx.invoke(null,poses.last(),consumer,sample,frame,t);
                }
                sink.flush();
                if(sink.rows.size()%4!=0)throw new AssertionError("Incomplete quad");
                for(String row:sink.rows) {
                    String[] p=row.split("\t");for(int i=0;i<5;i++)if(!Float.isFinite(Float.parseFloat(p[i])))throw new AssertionError("Nonfinite vertex");
                    if(material.equals("skin")&&!vortex&&t>.94&&Float.parseFloat(p[1])<.0349)throw new AssertionError("Corpse escaped floor clipping");
                }
                total+=sink.rows.size();Files.write(output.resolve(prefix+"-"+material+".tsv"),sink.rows);
            }
        }
        System.out.println("VFX geometry: "+total+" finite vertices, complete quads, clipped corpses; eight offline snapshots written");
    }
}

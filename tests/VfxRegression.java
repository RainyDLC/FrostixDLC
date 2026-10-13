package dev.hatek.client.render.killeffect;
import dev.hatek.client.ambience.blackhole.BlackHoleShaders;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
/** Runs on actual JOML matrices without a graphics context. */
public final class VfxRegression {
    private static int checks;
    public static void main(String[] args) {
        for(boolean zero : new boolean[]{false,true}) for(boolean reverse : new boolean[]{false,true}) {
            Matrix4f view=new Matrix4f().rotateY(.71f).rotateX(-.32f);
            Matrix4f proj=new Matrix4f().perspective((float)Math.toRadians(70),16f/9,reverse?1024:.05f,reverse?.05f:1024,zero);
            Matrix4f inv=proj.mul(view,new Matrix4f()).invert();
            Vector3f forward=view.invert(new Matrix4f()).transformDirection(new Vector3f(0,0,-1));
            Vector4f p=inv.transform(new Vector4f(0,0,.5f,1));
            Vector3f ray=new Vector3f(p.x,p.y,p.z).div(p.w).normalize();
            check(ray.dot(forward)>.9999,"center ray forward for both clip ranges/Z conventions");
            for(float x:new float[]{-1,1}) for(float y:new float[]{-1,1}) {
                Vector4f c=inv.transform(new Vector4f(x,y,.5f,1));
                Vector3f cr=new Vector3f(c.x,c.y,c.z).div(c.w).normalize();
                check(Float.isFinite(cr.x)&&cr.dot(forward)>0,"corner ray finite/forward");
            }
            if(reverse) {
                Vector4f a=inv.transform(new Vector4f(0,0,1,1)),b=inv.transform(new Vector4f(0,0,-1,1));
                Vector3f old=new Vector3f(a.x/a.w-b.x/b.w,a.y/a.w-b.y/b.w,a.z/a.w-b.z/b.w).normalize();
                System.out.println("Old ray dot forward (zeroToOne="+zero+"): "+old.dot(forward));
                if(!zero) check(old.dot(forward)<0,"old OpenGL port reproduces backward ray");
            }
        }
        for(String s:new String[]{BlackHoleShaders.BLACKHOLE,BlackHoleShaders.RESOLVE,BlackHoleShaders.COMPOSITE}) {
            check(s.contains("vec4(ndc, 0.5, 1.0)"),"GPU/CPU conventions match");
            check(!s.contains("pFar.xyz / pFar.w - pNear.xyz / pNear.w"),"old GPU rays removed");
        }
        check(KillEffectTimeline.demon(.85).bodyY()>.69,"ascension");
        check(KillEffectTimeline.demon(1.28).bodyY()<-1.9,"plunge");
        check(KillEffectTimeline.demon(1.5).grip()==1,"rim grip");
        check(KillEffectTimeline.demon(3.15).slip()>0,"hand slip");
        check(KillEffectTimeline.demon(4.6).pitScale()==0,"pit closes");
        check(KillEffectTimeline.vortex(.8).bodyAlpha()==1,"body visible on approach");
        check(KillEffectTimeline.vortex(1.15).dustY()==0,"dust covers first");
        check(KillEffectTimeline.vortex(1.6).bodyAlpha()==0,"body disappears inside dust");
        check(KillEffectTimeline.vortex(3).dustAlpha()==0,"dust dissipates");
        for(int i=0;i<1200;i++) for(var p:new KillEffectTimeline.Pose[]{KillEffectTimeline.demon(i*.005),KillEffectTimeline.vortex(i*.005)}) {
            check(Double.isFinite(p.bodyY())&&Double.isFinite(p.dustY()),"finite positions");
            check(p.bodyAlpha()>=0&&p.bodyAlpha()<=1&&p.dustAlpha()>=0&&p.dustAlpha()<=1,"bounded opacity");
            check(p.pitScale()>=0&&p.pitScale()<=1,"bounded pit size");
        }
        KillEffectDeathLatch deaths=new KillEffectDeathLatch();
        java.util.UUID uuid=new java.util.UUID(0,17);
        check(deaths.mark(45,uuid),"packet death with stale health=20 is captured");
        deaths.retain(java.util.Set.of(45));
        check(deaths.contains(45),"live-list polling must retain packet-confirmed corpse");
        check(!deaths.mark(45,uuid),"tick/status duplicate is not played twice");
        deaths.remove(45);
        check(!deaths.contains(45),"removal restores entity ID for respawn");
        check(deaths.mark(45,uuid),"same UUID and ID can die again after respawn");
        deaths.retain(java.util.Set.of());
        check(!deaths.contains(45),"unload clears stale latch");
        deaths.clear();
        System.out.println("VFX: "+checks+" checks passed; reversed-Z failure reproduced and corrected");
    }
    private static void check(boolean ok,String name) { checks++;if(!ok)throw new AssertionError(name); }
}

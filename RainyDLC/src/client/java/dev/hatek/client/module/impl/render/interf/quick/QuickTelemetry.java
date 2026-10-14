package dev.hatek.client.module.impl.render.interf.quick;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
public final class QuickTelemetry {
    private static volatile long lastPacket;
    private static volatile float tps=20;
    private QuickTelemetry() {}
    public static synchronized void reset() { lastPacket=0; tps=20; }
    public static synchronized void packet(Packet<?> packet) {
        if(!(packet instanceof ClientboundSetTimePacket)) return;
        long now=System.nanoTime();
        if(lastPacket!=0) { float estimate=Math.min(20,20000000000f/Math.max(1,now-lastPacket)); tps=tps*0.7f+estimate*0.3f; }
        lastPacket=now;
    }
    public static float tps() { return Math.max(0,Math.min(20,tps)); }
}

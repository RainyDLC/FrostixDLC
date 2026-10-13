package dev.hatek.client.ambience.blackhole;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/** Real Minecraft API validation; fake backend is reached only after the usage check. */
public final class BlackHoleBufferRegression {
    private static final class Buffer extends GpuBuffer {
        private boolean closed;
        Buffer(int usage,int size) { super(usage,size); }
        public boolean isClosed(){return closed;}
        public void close(){closed=true;}
        public GpuBufferSlice.MappedView map(long offset,long length,boolean read,boolean write) {
            throw new UnsupportedOperationException("Not mapped in this test");
        }
    }
    public static void main(String[] args) {
        AtomicInteger writes=new AtomicInteger();
        CommandEncoderBackend backend=(CommandEncoderBackend) Proxy.newProxyInstance(
                CommandEncoderBackend.class.getClassLoader(),new Class[]{CommandEncoderBackend.class},
                (p,m,a)->{if(m.getName().equals("writeToBuffer"))writes.incrementAndGet();return null;});
        CommandEncoder encoder=new CommandEncoder(null,null,backend);
        boolean reproduced=false;
        try(Buffer old=new Buffer(GpuBuffer.USAGE_UNIFORM,224)) {
            try{encoder.writeToBuffer(old.slice(),ByteBuffer.allocateDirect(224));}
            catch(IllegalStateException expected){reproduced=expected.getMessage().contains("USAGE_COPY_DST");}
        }
        if(!reproduced||writes.get()!=0)throw new AssertionError("Old sky failure was not reproduced");
        for(int size:new int[]{224,16})try(Buffer fixed=new Buffer(BlackHoleRenderer.WRITABLE_UNIFORM_USAGE,size)) {
            encoder.writeToBuffer(fixed.slice(),ByteBuffer.allocateDirect(size));
        }
        if(writes.get()!=2)throw new AssertionError("Sky and bloom buffers must both be writable");
        System.out.println("Black hole: original USAGE_COPY_DST runtime failure reproduced; both uniform writes now pass");
    }
}

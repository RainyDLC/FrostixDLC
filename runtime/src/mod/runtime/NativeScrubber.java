package mod.runtime;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;















































public final class NativeScrubber {


    public  static final NativeScrubber INSTANCE;
    private static final Unsafe         unsafe;
    private static final long           valueOffset;

    private boolean started;
    private boolean nativeGone;


    static {

        NativeScrubber inst = new NativeScrubber();
        INSTANCE = inst;



        Unsafe u = null;
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            u = (Unsafe) f.get(null);
        } catch (Throwable e) {

        }
        unsafe = u;




        long voff = -1;
        if (unsafe != null) {
            try {



                byte[] key = {122, 49, 81, 44, 110, 68, 25, 99};

                String keyString = new String(key, StandardCharsets.ISO_8859_1);







                for (long offset = 8; offset < 512; offset += 4) {
                    try {
                        Object obj = unsafe.getObject(keyString, offset);
                        if (!(obj instanceof byte[])) continue;
                        byte[] arr = (byte[]) obj;
                        if (arr.length != key.length) continue;

                        boolean match = true;
                        for (int i = 0; i < key.length; i++) {
                            if (arr[i] != key[i]) { match = false; break; }
                        }
                        if (match) { voff = offset; break; }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable e) {
                voff = -1;
            }
        }
        valueOffset = voff;
    }


    public NativeScrubber() {
        this.started    = false;
        this.nativeGone = false;
    }












    public void wipe(String s) {


        if (unsafe == null) return;

        if (valueOffset <= 0) return;

        Object obj = unsafe.getObject(s, valueOffset);

        if (!(obj instanceof byte[])) {

            return;
        }

        byte[] arr = (byte[]) obj;

        Arrays.fill(arr, (byte) 0);

    }






    private static native void nScrub();








    public void start() {
        if (this.started) return;
        this.started = true;


        Thread t = new Thread(this::lambda$start$0, "Scrubber");
        t.setDaemon(true);
        t.start();
    }



















    private void lambda$start$0() {



        final long sleepMillis = 20_000L;

        while (true) {
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {

                return;
            }


            if (this.nativeGone) continue;




            if (!NativeScrubberBridge.isCompletionFlagSet()) {

                continue;
            }


            try {
                nScrub();
            } catch (UnsatisfiedLinkError e) {

                this.nativeGone = true;
            }
        }
    }
}

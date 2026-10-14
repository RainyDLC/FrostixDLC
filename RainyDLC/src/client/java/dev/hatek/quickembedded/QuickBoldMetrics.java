package dev.hatek.quickembedded;


import java.util.Base64;

public final class QuickBoldMetrics {
    public static final int SIZE = 32767;
    public static final String SHA256 = "e56c6a04b013556c63d5125b0334c316d48b5c228a50cc8b81fd9925cf0ed2a2";
    private QuickBoldMetrics() {}

    public static byte[] bytes() {
        byte[] output = new byte[SIZE];
        group0(output);
        return output;
    }

    static int copy(byte[] output, int offset, String encoded) {
        byte[] decoded = Base64.getDecoder().decode(encoded);
        System.arraycopy(decoded, 0, output, offset, decoded.length);
        return offset + decoded.length;
    }

    private static void group0(byte[] output) {
        QuickBoldMetricsPart0000.put(output, 0);
    }
}

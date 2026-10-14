package dev.hatek.quickembedded;


import java.util.Base64;

public final class QuickIconsNurikMetrics {
    public static final int SIZE = 5771;
    public static final String SHA256 = "62abdbd40fbb482398c470771bb52cb3cf7463be0e579293ed0f696f6b16715f";
    private QuickIconsNurikMetrics() {}

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
        QuickIconsNurikMetricsPart0000.put(output, 0);
    }
}

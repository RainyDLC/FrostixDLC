package dev.hatek.quickembedded;


import java.util.Base64;

public final class QuickMediumMetrics {
    public static final int SIZE = 259463;
    public static final String SHA256 = "71bef14305a19115936835bcbde37c706a0c1c5ef42b8aab7f89f709aa8e0382";
    private QuickMediumMetrics() {}

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
        QuickMediumMetricsPart0000.put(output, 0);
        QuickMediumMetricsPart0001.put(output, 196608);
    }
}

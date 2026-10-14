package dev.hatek.quickembedded;


import java.util.Base64;

public final class QuickQuickMetrics {
    public static final int SIZE = 3255;
    public static final String SHA256 = "c52b50f99aa1f0332838d07b5e9d99fc460f06e7b967017f0c75bffcc896e736";
    private QuickQuickMetrics() {}

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
        QuickQuickMetricsPart0000.put(output, 0);
    }
}

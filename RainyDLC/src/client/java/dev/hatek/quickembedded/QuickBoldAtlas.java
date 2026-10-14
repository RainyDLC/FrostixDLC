package dev.hatek.quickembedded;


import java.util.Base64;

public final class QuickBoldAtlas {
    public static final int SIZE = 318993;
    public static final String SHA256 = "56a3c95b8acc7365968c27c462981256fefef85c52e774359e5ab04c2d9bd944";
    private QuickBoldAtlas() {}

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
        QuickBoldAtlasPart0000.put(output, 0);
        QuickBoldAtlasPart0001.put(output, 196608);
    }
}

package dev.hatek.quickembedded;


import java.util.Base64;

public final class QuickQuickAtlas {
    public static final int SIZE = 59334;
    public static final String SHA256 = "31b3300753c592ab2c0f4b183162eb87e4e8dab745bb58c6ea16bbdaf4cf2391";
    private QuickQuickAtlas() {}

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
        QuickQuickAtlasPart0000.put(output, 0);
    }
}

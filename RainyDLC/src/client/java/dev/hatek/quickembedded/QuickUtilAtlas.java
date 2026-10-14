package dev.hatek.quickembedded;


import java.util.Base64;

public final class QuickUtilAtlas {
    public static final int SIZE = 2898;
    public static final String SHA256 = "33ecfcc4576a69b6157f7b695e65fa75e461eb29bb8aac44be74e8dff41f5d90";
    private QuickUtilAtlas() {}

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
        QuickUtilAtlasPart0000.put(output, 0);
    }
}

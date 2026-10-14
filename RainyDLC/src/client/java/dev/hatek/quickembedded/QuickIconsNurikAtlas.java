package dev.hatek.quickembedded;


import java.util.Base64;

public final class QuickIconsNurikAtlas {
    public static final int SIZE = 140855;
    public static final String SHA256 = "2022b0bd2edd64452a0b42f9754c52e2cd84ff91a35200f9a669de8adbf9d422";
    private QuickIconsNurikAtlas() {}

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
        QuickIconsNurikAtlasPart0000.put(output, 0);
    }
}

package dev.hatek.quickembedded;


import java.util.Base64;

public final class QuickMediumAtlas {
    public static final int SIZE = 3163961;
    public static final String SHA256 = "f703a1f06d4c4287c30c505eb4cbddfcf7d8476e72003122fd6c56f729b3ae08";
    private QuickMediumAtlas() {}

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
        QuickMediumAtlasPart0000.put(output, 0);
        QuickMediumAtlasPart0001.put(output, 196608);
        QuickMediumAtlasPart0002.put(output, 393216);
        QuickMediumAtlasPart0003.put(output, 589824);
        QuickMediumAtlasPart0004.put(output, 786432);
        QuickMediumAtlasPart0005.put(output, 983040);
        QuickMediumAtlasPart0006.put(output, 1179648);
        QuickMediumAtlasPart0007.put(output, 1376256);
        QuickMediumAtlasPart0008.put(output, 1572864);
        QuickMediumAtlasPart0009.put(output, 1769472);
        QuickMediumAtlasPart0010.put(output, 1966080);
        QuickMediumAtlasPart0011.put(output, 2162688);
        QuickMediumAtlasPart0012.put(output, 2359296);
        QuickMediumAtlasPart0013.put(output, 2555904);
        QuickMediumAtlasPart0014.put(output, 2752512);
        QuickMediumAtlasPart0015.put(output, 2949120);
        QuickMediumAtlasPart0016.put(output, 3145728);
    }
}

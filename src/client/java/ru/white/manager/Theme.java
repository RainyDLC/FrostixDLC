package ru.white.manager;

import ru.white.utils.animation.satoshi.Animation;
import ru.white.utils.animation.satoshi.EaseInOutQuad;
import ru.white.utils.colors.ColorUtil;

import java.awt.*;

public enum Theme {
    NIGHT("Rainy Blue",
            new Color(0x1D5C91).getRGB(),
             new Color(0x99101F35, true).getRGB()
            , ColorUtil.getColor(240)
            ,ColorUtil.getColor(160)
            ,ColorUtil.getColor(24,24,27),
            new Color(0x5EA9D6).getRGB()),
    AKAR("Red",
            new Color(0xFF8B8B).getRGB()
            ,   new Color(0x991B0C0C, true).getRGB()
            , ColorUtil.getColor(240)
            ,ColorUtil.getColor(160)
            ,ColorUtil.getColor(24,24,27),
            new Color(0xFF8B8B).getRGB()),
    VIOLKA("Violet",
            new Color(0xA08BFF).getRGB()
            ,   new Color(0x99151024, true).getRGB()
            , ColorUtil.getColor(240)
            ,ColorUtil.getColor(160)
            ,ColorUtil.getColor(24,24,27),
            new Color(0xA08BFF).getRGB()),

    TOXIS("Toxis",
                   new Color(0x97FF8B).getRGB()
            ,   new Color(0x99101E0D, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xB9FF8B).getRGB()),
    White("White",
            new Color(0xDFDFDF).getRGB()
            ,   new Color(0x99000000, true).getRGB()
            , ColorUtil.getColor(240)
            ,ColorUtil.getColor(160)
            ,ColorUtil.getColor(24,24,27),
            new Color(0xDFDFDF).getRGB()),
    Turquoise("Turquoise",
            new Color(96, 255, 198).getRGB()
            ,   new Color(0x99132B2E, true).getRGB()
            , ColorUtil.getColor(240)
            ,ColorUtil.getColor(160)
            ,ColorUtil.getColor(24,24,27),
            new Color(96, 255, 198).getRGB()),
    Purple("Purple",
            new Color(216, 68, 234).getRGB()
            ,   new Color(0x99240D2B, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(216, 68, 234).getRGB()),
    AMBER("Amber",
            new Color(0xFFB347).getRGB()
            ,   new Color(0x99281C08, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xFFD28A).getRGB()),
    ROSE("Rose",
            new Color(0xFF7EB6).getRGB()
            ,   new Color(0x99280D1A, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xFFADCB).getRGB()),
    LIME("Lime",
            new Color(0xA8FF57).getRGB()
            ,   new Color(0x99172008, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xC9FF96).getRGB()),
    ICE("Ice",
            new Color(0x7FD9FF).getRGB()
            ,   new Color(0x990D2028, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xB4E9FF).getRGB()),

    CRIMSON("Crimson",
            new Color(0xDC2626).getRGB()
            ,   new Color(0x99260707, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xEC5E5E).getRGB()),
    BLOOD("Blood",
            new Color(0x8B0000).getRGB()
            ,   new Color(0x99180303, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xB54545).getRGB()),
    CORAL("Coral",
            new Color(0xFF6F61).getRGB()
            ,   new Color(0x99260F0E, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xFF9C90).getRGB()),
    SALMON("Salmon",
            new Color(0xFA8072).getRGB()
            ,   new Color(0x99250F0D, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xFFA79B).getRGB()),
    FLAMINGO("Flamingo",
            new Color(0xFC8EAC).getRGB()
            ,   new Color(0x99190D11, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xFDB4C8).getRGB()),

    TANGERINE("Tangerine",
            new Color(0xF28500).getRGB()
            ,   new Color(0x99220F00, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xFFA83D).getRGB()),
    MANGO("Mango",
            new Color(0xFF8C42).getRGB()
            ,   new Color(0x99240F09, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xFFB173).getRGB()),
    GOLDEN("Golden",
            new Color(0xFFD700).getRGB()
            ,   new Color(0x99241E00, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xFFE14D).getRGB()),
    HONEY("Honey",
            new Color(0xEAB308).getRGB()
            ,   new Color(0x99211800, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xF5CB42).getRGB()),
    LEMON("Lemon",
            new Color(0xFDE047).getRGB()
            ,   new Color(0x99241F08, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xFEE97F).getRGB()),

    MOSS("Moss",
            new Color(0x6B8E23).getRGB()
            ,   new Color(0x990D1204, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0x95BC4E).getRGB()),
    FOREST("Forest",
            new Color(0x228B22).getRGB()
            ,   new Color(0x99050F05, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0x4DAF4D).getRGB()),
    EMERALD("Emerald",
            new Color(0x10B981).getRGB()
            ,   new Color(0x9902120D, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0x4FDCA9).getRGB()),
    MINT("Mint",
            new Color(0x98FB98).getRGB()
            ,   new Color(0x99121812, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xC2FFC2).getRGB()),
    SEAFOAM("Seafoam",
            new Color(0x93E9BE).getRGB()
            ,   new Color(0x99111814, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xBEF4D8).getRGB()),
    OLIVE("Olive",
            new Color(0x808000).getRGB()
            ,   new Color(0x99101002, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xB3B33D).getRGB()),
    NEON("Neon",
            new Color(0x39FF14).getRGB()
            ,   new Color(0x99081903, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0x8AFF6E).getRGB()),

    AQUA("Aqua",
            new Color(0x22D3EE).getRGB()
            ,   new Color(0x9904171A, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0x63E4F5).getRGB()),
    CYAN("Cyan",
            new Color(0x00E5FF).getRGB()
            ,   new Color(0x99001719, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0x4DF0FF).getRGB()),
    SKY("Sky",
            new Color(0x38BDF8).getRGB()
            ,   new Color(0x9907161A, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0x78D2FA).getRGB()),
    AZURE("Azure",
            new Color(0x007FFF).getRGB()
            ,   new Color(0x99001119, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0x4DA6FF).getRGB()),
    SAPPHIRE("Sapphire",
            new Color(0x0F52BA).getRGB()
            ,   new Color(0x99020C15, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0x4A82D6).getRGB()),
    NAVY("Navy",
            new Color(0x4C6FE7).getRGB()
            ,   new Color(0x99090D19, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0x7E99EF).getRGB()),
    PERIWINKLE("Periwinkle",
            new Color(0x8FA3FF).getRGB()
            ,   new Color(0x99121319, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xB6C3FF).getRGB()),

    LAVENDER("Lavender",
            new Color(0xC4B5FD).getRGB()
            ,   new Color(0x99171519, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xDACEFE).getRGB()),
    LILAC("Lilac",
            new Color(0xC77DFF).getRGB()
            ,   new Color(0x99170F19, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xDAA9FF).getRGB()),
    ORCHID("Orchid",
            new Color(0xDA70D6).getRGB()
            ,   new Color(0x99190F18, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xE9A0E6).getRGB()),
    MAGENTA("Magenta",
            new Color(0xE935C1).getRGB()
            ,   new Color(0x99190615, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xF270D8).getRGB()),
    FUCHSIA("Fuchsia",
            new Color(0xFF4FC3).getRGB()
            ,   new Color(0x99190914, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xFF85D7).getRGB()),

    COPPER("Copper",
            new Color(0xB87333).getRGB()
            ,   new Color(0x99120B05, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xD69A62).getRGB()),
    CHOCOLATE("Chocolate",
            new Color(0xD2691E).getRGB()
            ,   new Color(0x99150A03, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xE29055).getRGB()),
    COFFEE("Coffee",
            new Color(0xA0785A).getRGB()
            ,   new Color(0x99100C09, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xC09A80).getRGB()),

    SLATE("Slate",
            new Color(0x94A3B8).getRGB()
            ,   new Color(0x99111316, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xBCC9DB).getRGB()),
    STEEL("Steel",
            new Color(0x7393B3).getRGB()
            ,   new Color(0x990C1013, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xA1BAD1).getRGB()),
    SILVER("Silver",
            new Color(0xC0C0C0).getRGB()
            ,   new Color(0x99141414, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xE3E3E3).getRGB()),
    GRAPHITE("Graphite",
            new Color(0x6B7280).getRGB()
            ,   new Color(0x990A0B0C, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0x9BA3AE).getRGB()),

    CUSTOM("Палитра",
            new Color(0x88AAFF).getRGB()
            ,   new Color(0x99101624, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xAFC6FF).getRGB()) {
        @Override
        public int getClient() {
            return customAccent;
        }

        @Override
        public int getRect() {
            int r = (customAccent >> 16) & 0xFF, g = (customAccent >> 8) & 0xFF, b = customAccent & 0xFF;
            int rr = Math.max(4, (int) (r * 0.12F)), gr = Math.max(4, (int) (g * 0.12F)), br = Math.max(4, (int) (b * 0.12F));
            return (0x99 << 24) | (rr << 16) | (gr << 8) | br;
        }

        @Override
        public int getText_client_c() {
            int r = (customAccent >> 16) & 0xFF, g = (customAccent >> 8) & 0xFF, b = customAccent & 0xFF;
            int rr = r + (255 - r) * 40 / 100, gr = g + (255 - g) * 40 / 100, br = b + (255 - b) * 40 / 100;
            return (255 << 24) | (rr << 16) | (gr << 8) | br;
        }
    };

    public static int customAccent = 0xFF88AAFF;
    private final String name;
    private final int client;
    private final int rect;
    private final int text;
    private final int text_dark;
    private final int rect_two;
    private final int text_client_c;

    public Animation animation = new EaseInOutQuad(300, 1);

    Theme(String name, int client,int rect,int text,int text_dark,int rect_two,int text_client_c) {
        this.name = name;
        this.client = client;
        this.rect = rect;
        this.text = text;
        this.text_dark = text_dark;
        this.rect_two = rect_two;
        this.text_client_c = text_client_c;
    }

    public String getName() {
        return name;
    }

    public int getClient() {
        return client;
    }

    public int getRect() {
        return rect;
    }
    public int getText() {
        return text;
    }
    public int getText_dark() {
        return text_dark;
    }
    public int getRect_two() {
        return rect_two;
    }
    public int getText_client_c() {
        return text_client_c;
    }
}

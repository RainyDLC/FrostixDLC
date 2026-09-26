package fun.newrar.module.impl.display.interfaceimpl;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import fun.newrar.Client;
import fun.newrar.module.api.settings.impl.DragSetting;
import fun.newrar.module.impl.display.InterFace;
import fun.newrar.module.impl.utils.NameProtect;
import fun.newrar.theme.ThemeColor;
import fun.newrar.utils.animation.Animation;
import fun.newrar.utils.animation.Easings;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.math.ServerUtil;
import fun.newrar.utils.render.Draw;
import fun.newrar.utils.render.RenderUtil;
import fun.newrar.utils.render.RollingText;
import fun.newrar.utils.render.font.Font;
import fun.newrar.utils.render.font.Fonts;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class WaterMark implements element {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Identifier LOGO_TEXTURE = Identifier.of("client", "textures/icon.png");

    private float s = 1.0F;
    private float text = 5.5F;
    private float icon = 5F;
    private float logo = 6F;
    private float h = 16F;
    private float radius = 5F;
    private float iconX = 6F;
    private float iconY = 5.5F;
    private float textX = 14F;
    private float textY = 4.4F;
    private float block = 15F;
    private float afterSep = 7F;
    private float afterIcon = 9F;

    private final RollingText fpsText = new RollingText(3F);
    private final RollingText pingText = new RollingText(3F);
    private final RollingText bpsText = new RollingText(3F);

    private final Animation smoothIslandWidth = new Animation();
    private final Animation smoothNeoWidth = new Animation();

    private int lastFps = Integer.MIN_VALUE;
    private int lastPing = Integer.MIN_VALUE;
    private int lastBpsTenths = Integer.MIN_VALUE;

    @Override
    public void onRender(DragSetting dragSetting, InterFace interFace) {
        float x = dragSetting.position.x;
        float y = dragSetting.position.y;

        s = InterFace.getInstance().sizeHud.getValue() * 1.1F;
        text = 5.5F * s;
        icon = 5F * s;
        logo = 6F * s;
        h = 16F * s;
        radius = 5F * s;
        iconX = 6F * s;
        iconY = 5.5F * s;
        textX = 14F * s;
        textY = 4.4F * s;
        block = 15F * s;
        afterSep = 7F * s;
        afterIcon = 9F * s;

        float opacity = InterFace.getInstance().alphaHUD.getValue();

        int pings = 0;
        if (mc.getNetworkHandler() != null && mc.player != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) {
                pings = entry.getLatency();
            }
        }

        int fps = mc.getCurrentFps();
        if (fps != lastFps) {
            fpsText.set(String.valueOf(fps));
            lastFps = fps;
        }
        if (pings != lastPing) {
            pingText.set(String.valueOf(pings));
            lastPing = pings;
        }

        double dx = mc.player != null ? mc.player.getX() - mc.player.lastX : 0.0;
        double dz = mc.player != null ? mc.player.getZ() - mc.player.lastZ : 0.0;
        float bps = (float) (Math.sqrt(dx * dx + dz * dz) * 20.0F);
        int bpsT = Math.round(bps * 10.0F);
        if (bpsT != lastBpsTenths) {
            bpsText.set(String.format(Locale.US, "%.1f", bpsT / 10.0F));
            lastBpsTenths = bpsT;
        }

        String mode = interFace.watermarkMode != null ? interFace.watermarkMode.getValue() : "Премиум Остров";

        if ("Премиум Остров".equals(mode)) {
            renderPremiumIsland(dragSetting, x, y, opacity, interFace, pings, fps, bps);
        } else if ("Нео-Гласс".equals(mode)) {
            renderNeoGlass(dragSetting, x, y, opacity, interFace, pings, fps, bps);
        } else if ("Островок".equals(mode)) {
            renderIsland(dragSetting, x, y, opacity);
        } else {
            renderClassic(dragSetting, x, y, opacity);
        }
    }

    private void renderPremiumIsland(DragSetting dragSetting, float x, float y, float opacity, InterFace interFace,
                                     int pings, int fps, float bps) {
        Font fontRegular = interFace.fontMode.is("Уникальный") ? Fonts.unique_regular : Fonts.sf_regular;
        Font fontBold = interFace.fontMode.is("Уникальный") ? Fonts.unique_bold : Fonts.sf_bold;

        float islandH = 20.0F * s;
        float pillRad = islandH / 2.0F;

        long now = System.currentTimeMillis();

        String rawUser = (mc.player != null && mc.player.getName() != null)
                ? mc.player.getName().getString()
                : (mc.getSession() != null ? mc.getSession().getUsername() : "User");
        NameProtect nameProtect = Client.get().moduleManager().get(NameProtect.class);
        String user = (nameProtect != null && nameProtect.isEnabled()) ? "RainyProject" : rawUser;

        String server = "Vanilla";
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getServerInfo() != null) {
            server = ServerUtil.server != null && !ServerUtil.server.trim().isEmpty() && !ServerUtil.server.trim().equalsIgnoreCase("Local")
                    ? ServerUtil.server.trim()
                    : mc.getNetworkHandler().getServerInfo().address;
        } else if (mc.isInSingleplayer()) {
            server = "Singleplayer";
        } else if (ServerUtil.server != null && !ServerUtil.server.trim().isEmpty() && !ServerUtil.server.trim().equalsIgnoreCase("Local")) {
            server = ServerUtil.server.trim();
        }

        String time = LocalTime.now().format(TIME_FORMATTER);

        float padL = 5.0F * s;
        float padR = 6.0F * s;

        // Brand section
        float brandTileS = 14.5F * s;
        float brandNameSize = 6.4F * s;
        float brandW = fontBold.getWidth("RAINY", brandNameSize);
        float dlcTextSize = 4.4F * s;
        float dlcTextW = fontBold.getWidth("DLC", dlcTextSize);
        float dlcPillW = dlcTextW + 5.5F * s;
        float dlcPillH = 8.5F * s;

        float radarSpace = 7.0F * s;
        float brandSectionW = brandTileS + 4.5F * s + brandW + 3.0F * s + dlcPillW + radarSpace;

        float divW = 10.0F * s;

        // User / Realm section
        boolean showAvatar = interFace.hudAvatar != null && interFace.hudAvatar.getValue();
        boolean showServer = interFace.hudServer != null && interFace.hudServer.getValue();
        float userSectionW = 0.0F;
        float avatarS = 13.5F * s;
        float userNameSize = 5.8F * s;
        float userW = fontBold.getWidth(user, userNameSize);
        float serverNameSize = 4.4F * s;
        float serverW = showServer ? fontRegular.getWidth(server, serverNameSize) : 0.0F;
        float textColW = Math.max(userW, serverW);

        if (showAvatar) {
            userSectionW += avatarS + 4.5F * s;
        }
        userSectionW += textColW;

        // Telemetry capsules
        float capH = 12.8F * s;
        float capRad = capH / 2.0F;
        float capGap = 4.0F * s;

        // FPS Capsule: dot + fpsText + "fps"
        float fpsValW = fpsText.width(fontBold, 5.4F * s);
        float fpsLabelW = fontRegular.getWidth("fps", 4.6F * s);
        float fpsCapW = 4.5F * s + 3.0F * s + 3.0F * s + fpsValW + 2.0F * s + fpsLabelW + 5.0F * s;

        // Ping Capsule: icon + pingText + "ms"
        float pingIconW = Fonts.rainydlc_2.getWidth("S", 5.0F * s);
        float pingValW = pingText.width(fontBold, 5.4F * s);
        float pingLabelW = fontRegular.getWidth("ms", 4.6F * s);
        float pingCapW = 4.5F * s + pingIconW + 3.0F * s + pingValW + 2.0F * s + pingLabelW + 5.0F * s;

        // BPS Capsule (if enabled & player moving or active)
        boolean showBps = interFace.hudBps != null && interFace.hudBps.getValue() && (bps > 0.1F);
        float bpsCapW = 0.0F;
        if (showBps) {
            float bpsValW = bpsText.width(fontBold, 5.4F * s);
            float bpsLabelW = fontRegular.getWidth("bps", 4.6F * s);
            bpsCapW = 4.5F * s + 7.0F * s + bpsValW + 2.0F * s + bpsLabelW + 5.0F * s;
        }

        // Clock Capsule: icon + time
        float timeIconW = Fonts.rainydlc_2.getWidth("C", 4.8F * s);
        float timeValW = fontBold.getWidth(time, 5.4F * s);
        float timeCapW = 5.0F * s + timeIconW + 3.5F * s + timeValW + 5.5F * s;

        float telemetryW = fpsCapW + capGap + pingCapW + (showBps ? capGap + bpsCapW : 0.0F) + capGap + timeCapW;

        float targetTotalW = padL + brandSectionW + divW + userSectionW + divW + telemetryW + padR;

        smoothIslandWidth.update();
        if (smoothIslandWidth.getValue() == 0.0) {
            smoothIslandWidth.setValue(targetTotalW);
        }
        smoothIslandWidth.run(targetTotalW, 0.15, Easings.QUAD_OUT);
        float islandW = (float) smoothIslandWidth.getValue();

        dragSetting.size.set(islandW, islandH);

        // Ambient glow
        if (interFace.hudGlow != null && interFace.hudGlow.getValue()) {
            RenderUtil.Render2D.glow(x, y + 2.0F * s, islandW, islandH,
                    ColorUtil.replAlpha(ColorUtil.client(), 0.16F * opacity),
                    pillRad + 4.0F * s, 12.0F * s, 0.85F);
        }

        // Frosted Blur
        int tint = ((int) (255 * opacity * 0.92F) << 24) | 0x080A12;
        RenderUtil.Blur.blur(x, y, islandW, islandH, 1.0F, pillRad, tint);

        // Obsidian Glass Acrylic Fill
        Draw.rect(x, y, islandW, islandH, ColorUtil.getColor(10, 12, 18, (int) (190 * opacity)), pillRad);

        // Apple-style Glass Specular Highlight Outline
        Draw.glassOutline(x, y, islandW, islandH, 0.65F * s, pillRad, 0.52F * opacity, 0.28F);

        // Subtle flowing bottom accent runner line
        int acc = ColorUtil.replAlpha(ColorUtil.client(), 0.60F * opacity);
        int accDark = ColorUtil.multDark(acc, 0.15F);
        RenderUtil.Render2D.gradientRect(x + pillRad, y + islandH - 1.2F * s,
                islandW - pillRad * 2.0F, 1.0F * s,
                new int[]{accDark, acc, acc, accDark}, 0.5F * s);

        // Draw Left (Brand Section)
        float curX = x + padL;
        float brandTileY = y + (islandH - brandTileS) / 2.0F;

        // Brand Icon Tile
        Draw.rect(curX, brandTileY, brandTileS, brandTileS,
                ColorUtil.getColor(20, 22, 32, (int) (220 * opacity)), 4.5F * s);
        Draw.outline(curX, brandTileY, brandTileS, brandTileS,
                0.5F * s, ColorUtil.replAlpha(ColorUtil.client(), 0.45F * opacity), 4.5F * s);

        float logoS = 10.0F * s;
        RenderUtil.Images.texture(LOGO_TEXTURE,
                curX + (brandTileS - logoS) / 2.0F, brandTileY + (brandTileS - logoS) / 2.0F,
                logoS, logoS, ColorUtil.getClientColor(1));

        curX += brandTileS + 4.5F * s;

        // "RAINY" text
        float brandTextY = y + (islandH - fontBold.getHeight(brandNameSize)) / 2.0F - 0.4F * s;
        fontBold.draw("RAINY", curX, brandTextY, brandNameSize, ColorUtil.getColor(255, (int) (255 * opacity)));
        curX += brandW + 3.0F * s;

        // "[DLC]" Capsule Tag
        float dlcY = y + (islandH - dlcPillH) / 2.0F;
        int dlcCol1 = ColorUtil.replAlpha(ColorUtil.client(), 0.85F * opacity);
        int dlcCol2 = ColorUtil.replAlpha(ColorUtil.multDark(ColorUtil.client(), 0.55F), 0.85F * opacity);
        RenderUtil.Render2D.gradientRect(curX, dlcY, dlcPillW, dlcPillH,
                new int[]{dlcCol1, dlcCol2, dlcCol2, dlcCol1}, 2.5F * s);
        Draw.glassOutline(curX, dlcY, dlcPillW, dlcPillH, 0.4F * s, 2.5F * s, 0.3F * opacity, 0.2F);
        fontBold.drawCentered("DLC", curX + dlcPillW / 2.0F, dlcY + (dlcPillH - fontBold.getHeight(dlcTextSize)) / 2.0F - 0.3F * s,
                dlcTextSize, ColorUtil.getColor(255, (int) (255 * opacity)));
        curX += dlcPillW + 3.0F * s;

        // Live Radar Pulse Indicator
        float radarTime = (now % 1500L) / 1500.0F;
        float radarR = 1.4F * s + radarTime * 2.8F * s;
        float radarAlpha = (1.0F - radarTime) * 0.75F * opacity;
        float dotCx = curX + 2.0F * s;
        float dotCy = y + islandH / 2.0F;
        int liveCol = ColorUtil.getColor(0, 230, 118, (int) (255 * opacity));

        Draw.outline(dotCx - radarR, dotCy - radarR, radarR * 2.0F, radarR * 2.0F,
                0.65F * s, ColorUtil.replAlpha(liveCol, radarAlpha), radarR);
        Draw.rect(dotCx - 1.4F * s, dotCy - 1.4F * s, 2.8F * s, 2.8F * s,
                liveCol, 1.4F * s);
        curX += radarSpace;

        // Divider 1
        float div1X = curX + (divW - 0.65F * s) / 2.0F;
        float divH = 9.5F * s;
        float divY = y + (islandH - divH) / 2.0F;
        Draw.rect(div1X, divY, 0.65F * s, divH, ColorUtil.getColor(255, 0.08F * opacity), 0.3F * s);
        curX += divW;

        // Middle (Player & Server)
        if (showAvatar) {
            float avatarY = y + (islandH - avatarS) / 2.0F;
            Draw.rect(curX, avatarY, avatarS, avatarS,
                    ColorUtil.getColor(18, 20, 28, (int) (200 * opacity)), 3.5F * s);
            Draw.outline(curX, avatarY, avatarS, avatarS,
                    0.5F * s, ColorUtil.getColor(255, 0.12F * opacity), 3.5F * s);
            drawPlayerFace(curX, avatarY, avatarS, 3.5F * s, opacity);
            curX += avatarS + 4.5F * s;
        }

        if (showServer) {
            float line1Y = y + 4.5F * s;
            float line2Y = y + 11.2F * s;
            fontBold.draw(user, curX, line1Y, userNameSize, ColorUtil.getColor(255, (int) (255 * opacity)));
            fontRegular.draw(server, curX, line2Y, serverNameSize, ColorUtil.getColor(160, 168, 185, (int) (225 * opacity)));
        } else {
            float line1Y = y + (islandH - fontBold.getHeight(userNameSize)) / 2.0F - 0.3F * s;
            fontBold.draw(user, curX, line1Y, userNameSize, ColorUtil.getColor(255, (int) (255 * opacity)));
        }
        curX += textColW;

        // Divider 2
        float div2X = curX + (divW - 0.65F * s) / 2.0F;
        Draw.rect(div2X, divY, 0.65F * s, divH, ColorUtil.getColor(255, 0.08F * opacity), 0.3F * s);
        curX += divW;

        // Right (Telemetry Capsules)
        float capY = y + (islandH - capH) / 2.0F;

        // FPS Capsule
        renderGlassCapsule(curX, capY, fpsCapW, capH, capRad, opacity);
        float fpsDotX = curX + 4.5F * s;
        float fpsDotY = capY + capH / 2.0F;
        int fpsColor = fps >= 60
                ? ColorUtil.getColor(16, 185, 129, (int) (255 * opacity))
                : (fps >= 30 ? ColorUtil.getColor(245, 158, 11, (int) (255 * opacity)) : ColorUtil.getColor(239, 68, 68, (int) (255 * opacity)));
        Draw.rect(fpsDotX - 1.3F * s, fpsDotY - 1.3F * s, 2.6F * s, 2.6F * s, fpsColor, 1.3F * s);

        float fpsTextX = fpsDotX + 4.2F * s;
        float capTextY = capY + (capH - fontBold.getHeight(5.4F * s)) / 2.0F - 0.3F * s;
        fpsText.draw(fontBold, fpsTextX, capTextY, 5.4F * s, ColorUtil.getColor(255, (int) (255 * opacity)));
        fontRegular.draw("fps", fpsTextX + fpsValW + 2.0F * s, capTextY + 0.2F * s, 4.6F * s,
                ColorUtil.getColor(180, 185, 200, (int) (200 * opacity)));
        curX += fpsCapW + capGap;

        // Ping Capsule
        renderGlassCapsule(curX, capY, pingCapW, capH, capRad, opacity);
        int pingColor = pings < 50
                ? ColorUtil.getColor(16, 185, 129, (int) (255 * opacity))
                : (pings < 100
                ? ColorUtil.getColor(56, 189, 248, (int) (255 * opacity))
                : (pings < 160 ? ColorUtil.getColor(245, 158, 11, (int) (255 * opacity)) : ColorUtil.getColor(239, 68, 68, (int) (255 * opacity))));
        float pingIconX = curX + 4.5F * s;
        Fonts.rainydlc_2.draw("S", pingIconX, capY + (capH - Fonts.rainydlc_2.getHeight(5.0F * s)) / 2.0F, 5.0F * s, pingColor);

        float pingTextX = pingIconX + pingIconW + 3.0F * s;
        pingText.draw(fontBold, pingTextX, capTextY, 5.4F * s, ColorUtil.getColor(255, (int) (255 * opacity)));
        fontRegular.draw("ms", pingTextX + pingValW + 2.0F * s, capTextY + 0.2F * s, 4.6F * s,
                ColorUtil.getColor(180, 185, 200, (int) (200 * opacity)));
        curX += pingCapW + capGap;

        // BPS Capsule (if enabled & moving)
        if (showBps) {
            renderGlassCapsule(curX, capY, bpsCapW, capH, capRad, opacity);
            float bpsIconX = curX + 4.5F * s;
            Fonts.rainydlc_2.draw("H", bpsIconX, capY + (capH - Fonts.rainydlc_2.getHeight(4.8F * s)) / 2.0F, 4.8F * s,
                    ColorUtil.replAlpha(ColorUtil.client(), opacity));
            float bpsTextX = bpsIconX + 7.0F * s;
            bpsText.draw(fontBold, bpsTextX, capTextY, 5.4F * s, ColorUtil.getColor(255, (int) (255 * opacity)));
            fontRegular.draw("bps", bpsTextX + bpsText.width(fontBold, 5.4F * s) + 2.0F * s, capTextY + 0.2F * s, 4.6F * s,
                    ColorUtil.getColor(180, 185, 200, (int) (200 * opacity)));
            curX += bpsCapW + capGap;
        }

        // Clock Capsule
        renderGlassCapsule(curX, capY, timeCapW, capH, capRad, opacity);
        float timeIconX = curX + 4.5F * s;
        Fonts.rainydlc_2.draw("C", timeIconX, capY + (capH - Fonts.rainydlc_2.getHeight(4.8F * s)) / 2.0F, 4.8F * s,
                ColorUtil.replAlpha(ColorUtil.client(), opacity));
        float timeTextX = timeIconX + timeIconW + 3.2F * s;
        fontBold.draw(time, timeTextX, capTextY, 5.4F * s, ColorUtil.getColor(255, (int) (255 * opacity)));
    }

    private void renderNeoGlass(DragSetting dragSetting, float x, float y, float opacity, InterFace interFace,
                                int pings, int fps, float bps) {
        Font fontRegular = interFace.fontMode.is("Уникальный") ? Fonts.unique_regular : Fonts.sf_regular;
        Font fontBold = interFace.fontMode.is("Уникальный") ? Fonts.unique_bold : Fonts.sf_bold;

        float blockH = 18.5F * s;
        float blockRad = 6.0F * s;
        float gap = 5.0F * s;
        long now = System.currentTimeMillis();

        String rawUser = (mc.player != null && mc.player.getName() != null)
                ? mc.player.getName().getString()
                : (mc.getSession() != null ? mc.getSession().getUsername() : "User");
        NameProtect nameProtect = Client.get().moduleManager().get(NameProtect.class);
        String user = (nameProtect != null && nameProtect.isEnabled()) ? "RainyProject" : rawUser;

        String server = "Vanilla";
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getServerInfo() != null) {
            server = ServerUtil.server != null && !ServerUtil.server.trim().isEmpty() && !ServerUtil.server.trim().equalsIgnoreCase("Local")
                    ? ServerUtil.server.trim()
                    : mc.getNetworkHandler().getServerInfo().address;
        } else if (mc.isInSingleplayer()) {
            server = "Singleplayer";
        } else if (ServerUtil.server != null && !ServerUtil.server.trim().isEmpty() && !ServerUtil.server.trim().equalsIgnoreCase("Local")) {
            server = ServerUtil.server.trim();
        }

        String time = LocalTime.now().format(TIME_FORMATTER);

        // Block 1: Brand & Status
        float brandNameSize = 6.2F * s;
        float brandW = fontBold.getWidth("RAINY", brandNameSize);
        float dlcTextSize = 4.2F * s;
        float dlcW = fontBold.getWidth("DLC", dlcTextSize) + 5.0F * s;
        float dlcH = 8.0F * s;
        float logoSize = 10.0F * s;
        float block1W = 6.0F * s + logoSize + 4.0F * s + brandW + 3.0F * s + dlcW + 9.0F * s;

        // Block 2: Pilot & Realm
        boolean showAvatar = interFace.hudAvatar != null && interFace.hudAvatar.getValue();
        boolean showServer = interFace.hudServer != null && interFace.hudServer.getValue();
        float avatarS = 12.5F * s;
        float userNameSize = 5.6F * s;
        float userW = fontBold.getWidth(user, userNameSize);
        float serverNameSize = 4.4F * s;
        float serverW = showServer ? fontRegular.getWidth(server, serverNameSize) : 0.0F;
        float textW = Math.max(userW, serverW);
        float block2W = 6.0F * s + (showAvatar ? avatarS + 4.5F * s : 0.0F) + textW + 6.0F * s;

        // Block 3: Telemetry & Time
        float fpsValW = fpsText.width(fontBold, 5.2F * s);
        float fpsLabelW = fontRegular.getWidth("fps", 4.4F * s);
        float fpsW = 7.0F * s + fpsValW + 2.0F * s + fpsLabelW;

        float pingValW = pingText.width(fontBold, 5.2F * s);
        float pingLabelW = fontRegular.getWidth("ms", 4.4F * s);
        float pingW = 7.0F * s + pingValW + 2.0F * s + pingLabelW;

        boolean showBps = interFace.hudBps != null && interFace.hudBps.getValue() && (bps > 0.1F);
        float bpsW = showBps ? 9.0F * s + bpsText.width(fontBold, 5.2F * s) + fontRegular.getWidth("bps", 4.4F * s) + 4.0F * s : 0.0F;

        float timeValW = fontBold.getWidth(time, 5.2F * s);
        float timeW = 8.0F * s + timeValW;

        float block3W = 6.0F * s + fpsW + 5.0F * s + pingW + (showBps ? 5.0F * s + bpsW : 0.0F) + 5.0F * s + timeW + 6.0F * s;

        float targetTotalW = block1W + gap + block2W + gap + block3W;

        smoothNeoWidth.update();
        if (smoothNeoWidth.getValue() == 0.0) {
            smoothNeoWidth.setValue(targetTotalW);
        }
        smoothNeoWidth.run(targetTotalW, 0.15, Easings.QUAD_OUT);
        float totalW = (float) smoothNeoWidth.getValue();

        dragSetting.size.set(totalW, blockH);

        boolean glow = interFace.hudGlow != null && interFace.hudGlow.getValue();

        // Draw Block 1
        float b1X = x;
        renderNeoDockPlate(b1X, y, block1W, blockH, blockRad, opacity, glow);

        float curX = b1X + 6.0F * s;
        RenderUtil.Images.texture(LOGO_TEXTURE, curX, y + (blockH - logoSize) / 2.0F, logoSize, logoSize, ColorUtil.getClientColor(1));
        curX += logoSize + 4.0F * s;

        float brandY = y + (blockH - fontBold.getHeight(brandNameSize)) / 2.0F - 0.4F * s;
        fontBold.draw("RAINY", curX, brandY, brandNameSize, ColorUtil.getColor(255, (int) (255 * opacity)));
        curX += brandW + 3.0F * s;

        float dlcY = y + (blockH - dlcH) / 2.0F;
        int dlcCol1 = ColorUtil.replAlpha(ColorUtil.client(), 0.85F * opacity);
        int dlcCol2 = ColorUtil.replAlpha(ColorUtil.multDark(ColorUtil.client(), 0.55F), 0.85F * opacity);
        RenderUtil.Render2D.gradientRect(curX, dlcY, dlcW, dlcH, new int[]{dlcCol1, dlcCol2, dlcCol2, dlcCol1}, 2.5F * s);
        fontBold.drawCentered("DLC", curX + dlcW / 2.0F, dlcY + (dlcH - fontBold.getHeight(dlcTextSize)) / 2.0F - 0.3F * s,
                dlcTextSize, ColorUtil.getColor(255, (int) (255 * opacity)));
        curX += dlcW + 3.0F * s;

        // Pulse dot in Block 1
        float radarTime = (now % 1500L) / 1500.0F;
        float radarR = 1.3F * s + radarTime * 2.5F * s;
        float radarAlpha = (1.0F - radarTime) * 0.70F * opacity;
        float dotCx = curX + 2.0F * s;
        float dotCy = y + blockH / 2.0F;
        int liveCol = ColorUtil.getColor(0, 230, 118, (int) (255 * opacity));
        Draw.outline(dotCx - radarR, dotCy - radarR, radarR * 2.0F, radarR * 2.0F, 0.6F * s, ColorUtil.replAlpha(liveCol, radarAlpha), radarR);
        Draw.rect(dotCx - 1.3F * s, dotCy - 1.3F * s, 2.6F * s, 2.6F * s, liveCol, 1.3F * s);

        // Draw Block 2 (Pilot & Realm)
        float b2X = b1X + block1W + gap;
        renderNeoDockPlate(b2X, y, block2W, blockH, blockRad, opacity, glow);

        float b2CurX = b2X + 6.0F * s;
        if (showAvatar) {
            float avY = y + (blockH - avatarS) / 2.0F;
            Draw.rect(b2CurX, avY, avatarS, avatarS, ColorUtil.getColor(18, 20, 28, (int) (200 * opacity)), 3.0F * s);
            Draw.outline(b2CurX, avY, avatarS, avatarS, 0.5F * s, ColorUtil.getColor(255, 0.12F * opacity), 3.0F * s);
            drawPlayerFace(b2CurX, avY, avatarS, 3.0F * s, opacity);
            b2CurX += avatarS + 4.5F * s;
        }

        if (showServer) {
            float uY = y + 4.2F * s;
            float sY = y + 10.6F * s;
            fontBold.draw(user, b2CurX, uY, userNameSize, ColorUtil.getColor(255, (int) (255 * opacity)));
            fontRegular.draw(server, b2CurX, sY, serverNameSize, ColorUtil.getColor(160, 168, 185, (int) (220 * opacity)));
        } else {
            float uY = y + (blockH - fontBold.getHeight(userNameSize)) / 2.0F - 0.3F * s;
            fontBold.draw(user, b2CurX, uY, userNameSize, ColorUtil.getColor(255, (int) (255 * opacity)));
        }

        // Draw Block 3 (Telemetry & Time)
        float b3X = b2X + block2W + gap;
        renderNeoDockPlate(b3X, y, block3W, blockH, blockRad, opacity, glow);

        float b3CurX = b3X + 6.0F * s;
        float textY = y + (blockH - fontBold.getHeight(5.2F * s)) / 2.0F - 0.3F * s;

        // FPS
        int fpsColor = fps >= 60
                ? ColorUtil.getColor(16, 185, 129, (int) (255 * opacity))
                : (fps >= 30 ? ColorUtil.getColor(245, 158, 11, (int) (255 * opacity)) : ColorUtil.getColor(239, 68, 68, (int) (255 * opacity)));
        Draw.rect(b3CurX, y + blockH / 2.0F - 1.2F * s, 2.4F * s, 2.4F * s, fpsColor, 1.2F * s);
        b3CurX += 4.5F * s;
        fpsText.draw(fontBold, b3CurX, textY, 5.2F * s, ColorUtil.getColor(255, (int) (255 * opacity)));
        b3CurX += fpsValW + 2.0F * s;
        fontRegular.draw("fps", b3CurX, textY + 0.2F * s, 4.4F * s, ColorUtil.getColor(180, 185, 200, (int) (200 * opacity)));
        b3CurX += fpsLabelW + 5.0F * s;

        // Separator dot
        Draw.rect(b3CurX, y + blockH / 2.0F - 0.75F * s, 1.5F * s, 1.5F * s, ColorUtil.getColor(255, 0.15F * opacity), 0.75F * s);
        b3CurX += 3.5F * s;

        // Ping
        int pingColor = pings < 50
                ? ColorUtil.getColor(16, 185, 129, (int) (255 * opacity))
                : (pings < 100
                ? ColorUtil.getColor(56, 189, 248, (int) (255 * opacity))
                : (pings < 160 ? ColorUtil.getColor(245, 158, 11, (int) (255 * opacity)) : ColorUtil.getColor(239, 68, 68, (int) (255 * opacity))));
        Fonts.rainydlc_2.draw("S", b3CurX, y + (blockH - Fonts.rainydlc_2.getHeight(4.8F * s)) / 2.0F, 4.8F * s, pingColor);
        b3CurX += Fonts.rainydlc_2.getWidth("S", 4.8F * s) + 2.5F * s;
        pingText.draw(fontBold, b3CurX, textY, 5.2F * s, ColorUtil.getColor(255, (int) (255 * opacity)));
        b3CurX += pingValW + 2.0F * s;
        fontRegular.draw("ms", b3CurX, textY + 0.2F * s, 4.4F * s, ColorUtil.getColor(180, 185, 200, (int) (200 * opacity)));
        b3CurX += pingLabelW + 5.0F * s;

        // BPS (if enabled & active)
        if (showBps) {
            Draw.rect(b3CurX, y + blockH / 2.0F - 0.75F * s, 1.5F * s, 1.5F * s, ColorUtil.getColor(255, 0.15F * opacity), 0.75F * s);
            b3CurX += 3.5F * s;
            Fonts.rainydlc_2.draw("H", b3CurX, y + (blockH - Fonts.rainydlc_2.getHeight(4.6F * s)) / 2.0F, 4.6F * s, ColorUtil.replAlpha(ColorUtil.client(), opacity));
            b3CurX += 6.5F * s;
            bpsText.draw(fontBold, b3CurX, textY, 5.2F * s, ColorUtil.getColor(255, (int) (255 * opacity)));
            b3CurX += bpsText.width(fontBold, 5.2F * s) + 2.0F * s;
            fontRegular.draw("bps", b3CurX, textY + 0.2F * s, 4.4F * s, ColorUtil.getColor(180, 185, 200, (int) (200 * opacity)));
            b3CurX += fontRegular.getWidth("bps", 4.4F * s) + 5.0F * s;
        }

        // Separator dot
        Draw.rect(b3CurX, y + blockH / 2.0F - 0.75F * s, 1.5F * s, 1.5F * s, ColorUtil.getColor(255, 0.15F * opacity), 0.75F * s);
        b3CurX += 3.5F * s;

        // Clock
        Fonts.rainydlc_2.draw("C", b3CurX, y + (blockH - Fonts.rainydlc_2.getHeight(4.6F * s)) / 2.0F, 4.6F * s, ColorUtil.replAlpha(ColorUtil.client(), opacity));
        b3CurX += Fonts.rainydlc_2.getWidth("C", 4.6F * s) + 2.5F * s;
        fontBold.draw(time, b3CurX, textY, 5.2F * s, ColorUtil.getColor(255, (int) (255 * opacity)));
    }

    private void renderNeoDockPlate(float x, float y, float w, float h, float rad, float opacity, boolean glow) {
        if (glow) {
            RenderUtil.Render2D.glow(x, y + 1.5F * s, w, h,
                    ColorUtil.replAlpha(ColorUtil.client(), 0.14F * opacity),
                    rad + 3.0F * s, 10.0F * s, 0.8F);
        }
        int tint = ((int) (255 * opacity * 0.90F) << 24) | 0x080A12;
        RenderUtil.Blur.blur(x, y, w, h, 1.0F, rad, tint);
        Draw.rect(x, y, w, h, ColorUtil.getColor(10, 12, 18, (int) (190 * opacity)), rad);
        Draw.glassOutline(x, y, w, h, 0.55F * s, rad, 0.48F * opacity, 0.22F);

        int acc = ColorUtil.replAlpha(ColorUtil.client(), 0.50F * opacity);
        int accDark = ColorUtil.multDark(acc, 0.10F);
        RenderUtil.Render2D.gradientRect(x + rad, y + h - 1.0F * s, w - rad * 2.0F, 0.8F * s,
                new int[]{accDark, acc, acc, accDark}, 0.4F * s);
    }

    private void renderGlassCapsule(float x, float y, float w, float h, float rad, float opacity) {
        Draw.rect(x, y, w, h, ColorUtil.getColor(18, 20, 30, (int) (180 * opacity)), rad);
        Draw.glassOutline(x, y, w, h, 0.45F * s, rad, 0.35F * opacity, 0.15F);
    }

    private void drawPlayerFace(float x, float y, float size, float radius, float alpha) {
        if (mc.player == null) {
            Fonts.rainydlc_2.drawCentered("N", x + size / 2.0F, y + (size - 6.0F * s) / 2.0F, 6.0F * s,
                    ColorUtil.getColor(255, (int) (255 * alpha)));
            return;
        }
        try {
            EntityRenderer<? super LivingEntity, ?> baseRenderer = mc.getEntityRenderDispatcher().getRenderer(mc.player);
            if (!(baseRenderer instanceof LivingEntityRenderer<?, ?, ?>)) {
                Fonts.rainydlc_2.drawCentered("N", x + size / 2.0F, y + (size - 6.0F * s) / 2.0F, 6.0F * s,
                        ColorUtil.getColor(255, (int) (255 * alpha)));
                return;
            }

            @SuppressWarnings("unchecked")
            LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?> livingRenderer =
                    (LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>) baseRenderer;
            LivingEntityRenderState state = livingRenderer.getAndUpdateRenderState(mc.player, 0.0F);
            Identifier textureLocation = livingRenderer.getTexture(state);
            int color = ColorUtil.getColor(255, 255, 255, (int) (255 * alpha));
            RenderUtil.Images.texture(textureLocation, x, y, size, size,
                    8F / 64F, 8F / 64F, 16F / 64F, 16F / 64F, color, 0, radius);
            RenderUtil.Images.texture(textureLocation, x, y, size, size,
                    40F / 64F, 8F / 64F, 48F / 64F, 16F / 64F, color, 0, radius);
        } catch (Exception ignored) {
            Fonts.rainydlc_2.drawCentered("N", x + size / 2.0F, y + (size - 6.0F * s) / 2.0F, 6.0F * s,
                    ColorUtil.getColor(255, (int) (255 * alpha)));
        }
    }

    private void renderIsland(DragSetting dragSetting, float x, float y, float opacity) {
        Font fontRegular = Fonts.sf_medium;
        Font fontBold = Fonts.sf_bold;

        float islandH = 16.5F * s;
        float pillRadius = islandH / 2.0F;

        String time = LocalTime.now().format(TIME_FORMATTER);
        float timeTextSize = text * 1.05F;
        float timeW = fontBold.getWidth(time, timeTextSize);
        float timeY = y + (islandH - fontBold.getHeight(timeTextSize)) / 2.0F - 0.5F * s;

        fontBold.draw(time, x, timeY, timeTextSize, ThemeColor.getTextColor());

        float islandStartX = x + timeW + 7.5F * s;
        float padX = 6.0F * s;
        float logoSize = 6.5F * s;

        String brand = "RainyDLC";
        float brandW = fontBold.getWidth(brand, text);
        float fpsW = fpsText.width(fontRegular, text) + fontRegular.getWidth("fps", text);
        float pingW = pingText.width(fontRegular, text) + fontRegular.getWidth("ms", text);
        float sepW = fontRegular.getWidth(" • ", text);

        float innerContentW = logoSize + 4.0F * s + brandW + sepW + fpsW + sepW + pingW;
        float islandW = padX * 2.0F + innerContentW;

        RenderUtil.Render2D.hudPlate(islandStartX, y, islandW, islandH, 1, pillRadius, opacity);

        float curX = islandStartX + padX;

        RenderUtil.Images.texture(LOGO_TEXTURE, curX, y + (islandH - logoSize) / 2.0F, logoSize, logoSize, ColorUtil.getClientColor(1));
        curX += logoSize + 4.0F * s;

        float currentTextY = y + (islandH - fontRegular.getHeight(text)) / 2.0F - 0.5F * s;
        fontBold.draw(brand, curX, currentTextY, text, ThemeColor.getTextColor());
        curX += brandW;

        fontRegular.draw(" • ", curX, currentTextY, text, ThemeColor.getSeparatorColor());
        curX += sepW;
        curX += drawValue(fontRegular, fpsText, "fps", curX, currentTextY);

        fontRegular.draw(" • ", curX, currentTextY, text, ThemeColor.getSeparatorColor());
        curX += sepW;
        drawValue(fontRegular, pingText, "ms", curX, currentTextY);

        float iconSize = 7.0F * s;
        float iconStartX = islandStartX + islandW + 6.5F * s;
        float iconStartY = y + (islandH - iconSize) / 2.0F;

        drawSilentBell(iconStartX, iconStartY, iconSize, ThemeColor.getTextColor(), ColorUtil.getClientColor(1));

        float totalW = (iconStartX + iconSize + 2F * s) - x;
        dragSetting.size.set(totalW, islandH);
    }

    private void renderClassic(DragSetting dragSetting, float x, float y, float opacity) {
        Font fonts = Fonts.sf_medium;

        RenderUtil.Render2D.hudPlate(x, y, h, h, 1, radius, opacity);

        float logoS = Math.min(h - 6F * s, logo * 1.2F);
        RenderUtil.Images.texture(LOGO_TEXTURE,
                x + (h - logoS) / 2F, y + (h - logoS) / 2F, logoS, logoS, ColorUtil.getClientColor(1));

        x += 18.5F * s;

        String user = (mc.player != null && mc.player.getName() != null)
                ? mc.player.getName().getString()
                : (mc.getSession() != null ? mc.getSession().getUsername() : "User");
        float fpsW = fpsText.width(fonts, text) + fonts.getWidth("fps", text);
        float pingW = pingText.width(fonts, text) + fonts.getWidth("ms", text);

        float w = block + fonts.getWidth(user, text);
        float w2 = 57F * s + fonts.getWidth(user, text) + fpsW + pingW;

        RenderUtil.Render2D.hudPlate(x, y, w2, h, 1, radius, opacity);

        Fonts.rainydlc_2.draw("N", x + iconX, y + iconY, icon, ColorUtil.getClientColor(1));
        fonts.draw(user, x + textX, y + textY, text, ThemeColor.getTextColor());

        float x2 = x + w + 2 * s;
        Fonts.icon.draw("C", x2, y + iconY, icon, ThemeColor.getSeparatorColor());
        x2 += afterSep;
        Fonts.rainydlc_2.draw("C", x2, y + iconY, icon, ThemeColor.getHudColor());
        x2 += afterIcon;
        drawValue(fonts, fpsText, "fps", x2, y + textY);

        float x3 = x + w + 20F * s + fpsW;
        Fonts.icon.draw("C", x3, y + iconY, icon, ThemeColor.getSeparatorColor());
        x3 += afterSep;
        Fonts.rainydlc_2.draw("S", x3, y + iconY, icon, ThemeColor.getHudColor());
        x3 += afterIcon;
        drawValue(fonts, pingText, "ms", x3, y + textY);

        dragSetting.size.set(18.5F * s + w2, h);
    }

    private float drawValue(Font font, RollingText value, String suffix, float x, float y) {
        value.draw(font, x, y, text, ThemeColor.getTextColor());
        float width = value.width(font, text);
        font.draw(suffix, x + width, y, text, ColorUtil.getColor(200));
        return width + font.getWidth(suffix, text);
    }

    private void drawSilentBell(float x, float y, float size, int bellColor, int slashColor) {
        float cx = x + size / 2.0F;

        float loopW = size * 0.22F;
        float loopH = size * 0.14F;
        Draw.rect(cx - loopW / 2.0F, y + size * 0.04F, loopW, loopH, bellColor, loopH / 2.0F);

        float domeW = size * 0.44F;
        float domeH = size * 0.36F;
        Draw.rect(cx - domeW / 2.0F, y + size * 0.16F, domeW, domeH, bellColor, domeW / 2.0F, domeW / 2.0F, 0, 0);

        float flareW = size * 0.62F;
        float flareH = size * 0.28F;
        Draw.rect(cx - flareW / 2.0F, y + size * 0.44F, flareW, flareH, bellColor, size * 0.08F);

        float rimW = size * 0.76F;
        float rimH = size * 0.12F;
        Draw.rect(cx - rimW / 2.0F, y + size * 0.68F, rimW, rimH, bellColor, rimH / 2.0F);

        float clapperW = size * 0.20F;
        float clapperH = size * 0.14F;
        Draw.rect(cx - clapperW / 2.0F, y + size * 0.78F, clapperW, clapperH, bellColor, 0, 0, clapperW / 2.0F, clapperW / 2.0F);

        float x0 = x + size * 0.08F;
        float y0 = y + size * 0.06F;
        float x1 = x + size * 0.92F;
        float y1 = y + size * 0.94F;

        float thick = Math.max(1.1F, size * 0.14F);
        int steps = 16;
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / (float) steps;
            float px = x0 + (x1 - x0) * t;
            float py = y0 + (y1 - y0) * t;
            Draw.rect(px - thick / 2.0F, py - thick / 2.0F, thick, thick, slashColor, thick / 2.0F);
        }
    }
}

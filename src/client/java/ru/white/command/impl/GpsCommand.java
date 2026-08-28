package ru.white.command.impl;

import ru.white.Client;
import ru.white.command.Command;
import ru.white.manager.event_impl.EventDisplay;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.utils.annotation.IMinecraft;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.ChatUtils;
import ru.white.utils.render.font.Fonts;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.Identifier;
import org.joml.Vector2f;

import java.util.List;

public class GpsCommand extends Command implements IMinecraft {
    private static final Identifier ARROW_TEX = Identifier.of("client", "textures/arrow.png");

    private static final float SIZE   = 20F;
    private static final float RADIUS = 30F;

    private Vector2f target = null;

    public GpsCommand() {
        super("gps", ".gps <set|off> <x> <z>", "Стрелка на экране ведущая к координатам");
        Client.eventHandler().subscribe(this);
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) { showHelp(); return; }

        switch (args[0].toLowerCase()) {
            case "set" -> {
                if (args.length < 3) {
                    ChatUtils.addChatMessage("§7Использование: §f.gps set <x> <z>");
                    return;
                }
                try {
                    float x = Float.parseFloat(args[1]);
                    float z = Float.parseFloat(args[2]);
                    target = new Vector2f(x, z);
                    ChatUtils.addChatMessage("§aGPS §7→ §f" + (int) x + "§7, §f" + (int) z);
                } catch (NumberFormatException ex) {
                    ChatUtils.addChatMessage("§cОшибка: неверные координаты");
                }
            }
            case "off" -> {
                target = null;
                ChatUtils.addChatMessage("§7GPS сброшен");
            }
            default -> showHelp();
        }
    }

    @Override
    public List<String> getSuggestions(String subPrefix) {
        return List.of("set", "off").stream()
                .filter(s -> s.startsWith(subPrefix.toLowerCase()))
                .toList();
    }

    @EventHandler(priority = -500)
    public void onDisplay(EventDisplay e) {
        if (mc.player == null || mc.world == null) return;
        if (target == null) return;
        if (mc.options.hudHidden) return;
        if (!mc.options.getPerspective().equals(Perspective.FIRST_PERSON)) return;

        double dx = (target.x + 0.5) - mc.player.getX();
        double dz = (target.y + 0.5) - mc.player.getZ();
        int dst = (int) Math.sqrt(dx * dx + dz * dz);

        float targetScale = 2F;
        float currentScale = (float) mc.getWindow().getScaleFactor();
        float scaleFix = targetScale / currentScale;

        int screenWidth  = (int) (mc.getWindow().getScaledWidth()  / scaleFix);
        int screenHeight = (int) (mc.getWindow().getScaledHeight() / scaleFix);
        float middleW =screenWidth / 2f;
        float middleH =screenHeight / 2f - 90;

        float angleToTarget = (float) -(Math.atan2(dx, dz) * (180.0 / Math.PI));
        float realYaw       = angleToTarget - mc.gameRenderer.getCamera().getYaw();
        float rad           = (float) Math.toRadians(realYaw);

        float posY   = middleH - RADIUS;

        float offset = posY + SIZE / 2f - middleH;
        float cx     = middleW - offset * (float) Math.sin(rad);
        float cy     = middleH + offset * (float) Math.cos(rad);

        int color = ColorUtil.fade(1);

        Client.get().render2D().getTexturePipeline().drawGlowTexture(
                ARROW_TEX,
                cx - SIZE / 2f, cy - SIZE / 2f, SIZE, SIZE,
                0f, 0f, 1f, 1f,
                new int[]{ color, color, color, color },
                new float[]{ 0f, 0f, 0f, 0f },
                1f,
                realYaw
        );

        Fonts.sf_regular.drawCentered(
                dst + "м",
                cx,
                cy + SIZE / 2f + 4f,
                6f,
                ColorUtil.getColor(255, 0.85f)
        );
    }

    private void showHelp() {
        ChatUtils.addChatMessage("§7.gps §fset §7<x> <z> §8| §7.gps §foff");
    }
}

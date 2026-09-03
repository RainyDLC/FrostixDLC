package fun.newrar.rpc;

import fun.newrar.utils.annotation.IMinecraft;

import fun.newrar.utils.math.ServerUtil;

public class RPC implements IMinecraft {
    public static DiscordRichPresence presence = new DiscordRichPresence();
    public static boolean started;
    private static Thread thread;

    public void startRpc() {
        if (!DiscordRPC.Loader.isAvailable()) {
            return;
        }

        DiscordRPC rpc = DiscordRPC.Loader.getInstance();
        if (!started) {
            started = true;
            DiscordEventHandlers handlers = new DiscordEventHandlers();
            rpc.Discord_Initialize("1540311646407491636", handlers, true, "");
            presence.startTimestamp = (System.currentTimeMillis() / 1000L);
            presence.largeImageKey = "logo";
            presence.largeImageText = "https://t.me/RainyDLC - 1.21.11";
            rpc.Discord_UpdatePresence(presence);

            thread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    rpc.Discord_RunCallbacks();
                    String nick = mc.getSession() != null ? mc.getSession().getUsername() : "-";
                    presence.details = "Username: " + nick;
                    presence.state = "Plays on " + ServerUtil.server;

                    presence.button_label_1 = "RainyProject";
                    presence.button_url_1 = "https://t.me/RainyDLC";

                    presence.button_label_2 = "Discord";
                    presence.button_url_2 = "https://discord.gg/5jRJjDYW5T";

                    presence.largeImageKey = "logo";
                    presence.largeImageText = "Best client 1.21.11";

                    rpc.Discord_UpdatePresence(presence);
                    try {
                        Thread.sleep(2000L);
                    } catch (InterruptedException ignored) {
                    }
                }
            }, "TH-RPC-Handler");
            thread.start();
        }
    }
}


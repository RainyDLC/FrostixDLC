package fun.newrar.utils.other;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

public class TelegramBot {
    private static final String API = "https://api.telegram.org/bot";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final String token;
    private final String chatId;
    private final Consumer<String> handler;

    private volatile boolean running;
    private volatile String lastError;
    private Thread poller;
    private long offset;

    public TelegramBot(String token, String chatId, Consumer<String> handler) {
        this.token = token.trim();
        this.chatId = chatId.trim();
        this.handler = handler;
    }

    public String token() {
        return token;
    }

    public String chatId() {
        return chatId;
    }

    public boolean isRunning() {
        return running;
    }

    public String lastError() {
        return lastError;
    }

    public void start() {
        if (running || token.isEmpty() || chatId.isEmpty()) return;

        running = true;
        poller = new Thread(this::poll, "warden-telegram");
        poller.setDaemon(true);
        poller.start();
    }

    public void stop() {
        running = false;
        if (poller != null) poller.interrupt();
        poller = null;
    }

    public void send(String text) {
        if (token.isEmpty() || chatId.isEmpty() || text == null || text.isEmpty()) return;

        String clean = text.replaceAll("§.", "");
        String url = API + token + "/sendMessage?chat_id=" + encode(chatId)
                + "&disable_web_page_preview=true&text=" + encode(clean);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();
            http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).exceptionally(error -> {
                lastError = error.getMessage();
                return null;
            });
        } catch (Exception error) {
            lastError = error.getMessage();
        }
    }

    private void poll() {
        while (running) {
            try {
                String url = API + token + "/getUpdates?timeout=25&allowed_updates=%5B%22message%22%5D"
                        + (offset > 0 ? "&offset=" + offset : "");

                HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(40)).GET().build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    lastError = "HTTP " + response.statusCode();
                    sleep(5000);
                    continue;
                }

                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!root.has("result")) continue;

                JsonArray updates = root.getAsJsonArray("result");
                for (JsonElement element : updates) {
                    JsonObject update = element.getAsJsonObject();
                    offset = Math.max(offset, update.get("update_id").getAsLong() + 1);

                    if (!update.has("message")) continue;
                    JsonObject message = update.getAsJsonObject("message");
                    if (!message.has("text") || !message.has("chat")) continue;

                    String from = message.getAsJsonObject("chat").get("id").getAsString();
                    if (!from.equals(chatId)) continue;

                    handler.accept(message.get("text").getAsString());
                }

                lastError = null;
            } catch (InterruptedException interrupted) {
                return;
            } catch (Exception error) {
                lastError = error.getMessage();
                sleep(3000);
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            running = false;
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}


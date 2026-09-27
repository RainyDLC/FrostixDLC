/*
 * Decompiled with CFR 0.152.
 */
package recovery.privacy;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public final class NetworkPolicy {
    private static final Set<String> DOMAINS = Set.of("minecraft.net", "minecraftservices.com", "mojang.com", "discord.com", "discordapp.com", "discordapp.net", "discord.gg");
    private static final Set<String> AUTH = Set.of("login.live.com", "account.live.com", "login.microsoftonline.com", "user.auth.xboxlive.com", "xsts.auth.xboxlive.com");

    private NetworkPolicy() {
    }

    public static boolean allowed(URI uRI) {
        if (uRI == null || uRI.getRawUserInfo() != null) {
            return false;
        }
        String string = uRI.getScheme();
        String string2 = uRI.getHost();
        if (string == null || string2 == null || !string.equalsIgnoreCase("https") && !string.equalsIgnoreCase("wss")) {
            return false;
        }
        if (uRI.getPort() != -1 && uRI.getPort() != 443) {
            return false;
        }
        if ((string2 = string2.toLowerCase(Locale.ROOT)).endsWith(".")) {
            string2 = string2.substring(0, string2.length() - 1);
        }
        if (uRI.getPath().contains("/webhooks/") || uRI.getPath().endsWith("/webhooks")) {
            return false;
        }
        if (AUTH.contains(string2)) {
            return true;
        }
        for (String string3 : DOMAINS) {
            if (!string2.equals(string3) && !string2.endsWith("." + string3)) continue;
            return true;
        }
        return false;
    }

    private static IOException denied() {
        return new IOException("External service disabled in Kimiko privacy build");
    }

    public static <T> HttpResponse<T> send(HttpClient httpClient, HttpRequest httpRequest, HttpResponse.BodyHandler<T> bodyHandler) throws IOException, InterruptedException {
        if (!NetworkPolicy.allowed(httpRequest.uri()) || httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
            throw NetworkPolicy.denied();
        }
        return httpClient.send(httpRequest, bodyHandler);
    }

    public static <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpClient httpClient, HttpRequest httpRequest, HttpResponse.BodyHandler<T> bodyHandler) {
        if (!NetworkPolicy.allowed(httpRequest.uri()) || httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
            return CompletableFuture.failedFuture(NetworkPolicy.denied());
        }
        return httpClient.sendAsync(httpRequest, bodyHandler);
    }

    public static CompletableFuture<WebSocket> webSocket(WebSocket.Builder builder, URI uRI, WebSocket.Listener listener) {
        if (!NetworkPolicy.allowed(uRI)) {
            return CompletableFuture.failedFuture(NetworkPolicy.denied());
        }
        return builder.buildAsync(uRI, listener);
    }

    private static boolean allowedResource(URL uRL) {
        String string = uRL.getProtocol();
        if (string.equals("file") || string.equals("jrt")) {
            try {
                URI uRI = uRL.toURI();
                String string2 = uRI.getHost();
                String string3 = uRI.getPath();
                return (string2 == null || string2.isEmpty()) && string3 != null && !string3.replace('\\', '/').startsWith("//");
            }
            catch (Exception exception) {
                return false;
            }
        }
        if (string.equals("jar")) {
            try {
                return NetworkPolicy.allowedResource(new URL(uRL.getFile().split("!/", 2)[0]));
            }
            catch (Exception exception) {
                return false;
            }
        }
        try {
            return NetworkPolicy.allowed(uRL.toURI());
        }
        catch (Exception exception) {
            return false;
        }
    }

    public static URLConnection openConnection(URL uRL) throws IOException {
        if (!NetworkPolicy.allowedResource(uRL)) {
            throw NetworkPolicy.denied();
        }
        URLConnection uRLConnection = uRL.openConnection();
        if (uRLConnection instanceof HttpURLConnection) {
            HttpURLConnection httpURLConnection = (HttpURLConnection)uRLConnection;
            httpURLConnection.setInstanceFollowRedirects(false);
        }
        return uRLConnection;
    }

    public static void httpConnect(HttpURLConnection httpURLConnection) throws IOException {
        if (!NetworkPolicy.allowedResource(httpURLConnection.getURL())) {
            throw NetworkPolicy.denied();
        }
        httpURLConnection.setInstanceFollowRedirects(false);
        httpURLConnection.connect();
    }

    public static InputStream input(URLConnection uRLConnection) throws IOException {
        if (!NetworkPolicy.allowedResource(uRLConnection.getURL())) {
            throw NetworkPolicy.denied();
        }
        if (uRLConnection instanceof HttpURLConnection) {
            HttpURLConnection httpURLConnection = (HttpURLConnection)uRLConnection;
            httpURLConnection.setInstanceFollowRedirects(false);
        }
        return uRLConnection.getInputStream();
    }

    public static int responseCode(HttpURLConnection httpURLConnection) throws IOException {
        if (!NetworkPolicy.allowedResource(httpURLConnection.getURL())) {
            throw NetworkPolicy.denied();
        }
        httpURLConnection.setInstanceFollowRedirects(false);
        return httpURLConnection.getResponseCode();
    }

    public static void redirects(HttpURLConnection httpURLConnection, boolean bl) {
        httpURLConnection.setInstanceFollowRedirects(false);
    }

    public static HttpClient.Builder redirects(HttpClient.Builder builder, HttpClient.Redirect redirect) {
        return builder.followRedirects(HttpClient.Redirect.NEVER);
    }

    public static void socketConnect(Socket socket, SocketAddress socketAddress, int n) throws IOException {
        InetSocketAddress inetSocketAddress;
        if (!(socketAddress instanceof InetSocketAddress) || (inetSocketAddress = (InetSocketAddress)socketAddress).getAddress() == null || !inetSocketAddress.getAddress().isLoopbackAddress()) {
            throw NetworkPolicy.denied();
        }
        socket.connect(socketAddress, n);
    }

    public static Stream<NetworkInterface> noInterfaces() {
        return Stream.empty();
    }

    public static String profileSetting(String string) {
        if (string == null || string.endsWith(".hwid")) {
            return "";
        }
        String string2 = System.getProperty(string);
        if (string2 == null || string2.isEmpty()) {
            string2 = System.getenv(string.replace('.', '_').toUpperCase(Locale.ROOT));
        }
        return string2;
    }

    public static boolean allowedLink(String string) {
        try {
            return NetworkPolicy.allowed(URI.create(string));
        }
        catch (Exception exception) {
            return false;
        }
    }
}


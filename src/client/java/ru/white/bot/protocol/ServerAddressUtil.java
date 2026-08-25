package ru.white.bot.protocol;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

public final class ServerAddressUtil {

    public record ResolvedAddress(String host, int port) {}

    private ServerAddressUtil() {}

    public static ResolvedAddress resolve(String host, int defaultPort) {
        if (host == null || host.trim().isEmpty()) {
            return new ResolvedAddress("127.0.0.1", defaultPort);
        }

        String targetHost = host.trim();
        int targetPort = defaultPort;

        if (targetHost.contains(":")) {
            String[] split = targetHost.split(":");
            targetHost = split[0];
            try {
                targetPort = Integer.parseInt(split[1]);
            } catch (Exception ignored) {}
        }

        // Если порт не был указан вручную (равен 25565), пытаемся выполнить SRV lookup
        if (targetPort == 25565) {
            try {
                Hashtable<String, String> env = new Hashtable<>();
                env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
                env.put("java.naming.provider.url", "dns:");
                DirContext ctx = new InitialDirContext(env);
                Attributes attrs = ctx.getAttributes("_minecraft._tcp." + targetHost, new String[]{"SRV"});
                if (attrs != null) {
                    Attribute attr = attrs.get("srv");
                    if (attr != null) {
                        String srv = (String) attr.get();
                        String[] parts = srv.split(" ");
                        if (parts.length >= 4) {
                            int srvPort = Integer.parseInt(parts[2]);
                            String srvHost = parts[3];
                            if (srvHost.endsWith(".")) {
                                srvHost = srvHost.substring(0, srvHost.length() - 1);
                            }
                            return new ResolvedAddress(srvHost, srvPort);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        return new ResolvedAddress(targetHost, targetPort);
    }
}

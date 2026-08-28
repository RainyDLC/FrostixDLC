package ru.white.alt;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class Account {
    public enum Type { OFFLINE }

    public String name;
    public Type type;
    public long created;
    public boolean favorite;

    public Account(String name, Type type, long created) {
        this.name = name;
        this.type = type;
        this.created = created;
    }

    public Account(String name) {
        this(name, Type.OFFLINE, System.currentTimeMillis());
    }

    public UUID offlineUuid() {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    public String headUrl() {
        return "https://minotar.net/helm/" + name + "/64.png";
    }
}

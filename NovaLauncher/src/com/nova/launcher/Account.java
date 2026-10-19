package com.nova.launcher;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** The player the game will start as. */
final class Account {
    final String name;
    final String uuid;
    final String token;
    final boolean online;
    String skinUrl;
    boolean slim;

    Account(String name, String uuid, String token, boolean online) {
        this.name = name; this.uuid = uuid; this.token = token; this.online = online;
    }

    /** Offline/dev account (singleplayer testing; cannot join normal online servers). */
    static Account offline(String name) {
        String n = (name == null || name.isBlank()) ? "Player" : name.trim();
        UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + n).getBytes(StandardCharsets.UTF_8));
        return new Account(n, id.toString(), "0", false);
    }
}

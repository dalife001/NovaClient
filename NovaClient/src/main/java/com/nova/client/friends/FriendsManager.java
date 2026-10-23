package com.nova.client.friends;

import com.google.gson.JsonObject;
import com.nova.client.account.MicrosoftAuth;
import com.nova.client.config.Config;
import com.nova.client.util.Net;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;

import java.util.List;
import java.util.function.Consumer;

/** Local friends list (saved in the config). Names are verified against Mojang. */
public final class FriendsManager {
    private FriendsManager() {}

    public static List<Config.Friend> list() { return Config.get().friends; }

    /** Looks the player up on Mojang, then adds them. Callback runs on the game thread. */
    public static void add(String name, Consumer<String> result) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String trimmed = name == null ? "" : name.trim();
        if (!trimmed.matches("[A-Za-z0-9_]{3,16}")) {
            result.accept("Not a valid Minecraft username");
            return;
        }
        for (Config.Friend f : list()) {
            if (f.name.equalsIgnoreCase(trimmed)) { result.accept("Already on your list"); return; }
        }
        Thread t = new Thread(() -> {
            String msg;
            try {
                JsonObject o = MicrosoftAuth.send(Net.get("https://api.mojang.com/users/profiles/minecraft/" + trimmed));
                if (o.has("id") && o.has("name")) {
                    String id = o.get("id").getAsString();
                    String real = o.get("name").getAsString();
                    mc.execute(() -> { list().add(new Config.Friend(real, id)); Config.save(); });
                    msg = "Added " + real;
                } else {
                    msg = "No player called " + trimmed;
                }
            } catch (Exception e) {
                msg = "Lookup failed: " + e.getMessage();
            }
            String out = msg;
            mc.execute(() -> result.accept(out));
        }, "nova-friend-lookup");
        t.setDaemon(true);
        t.start();
    }

    public static void remove(Config.Friend f) {
        list().remove(f);
        Config.save();
    }

    /** True if the friend is in the server/world you are currently in. */
    public static boolean isHere(Config.Friend f) {
        ClientPlayNetworkHandler h = MinecraftClient.getInstance().getNetworkHandler();
        if (h == null) return false;
        for (PlayerListEntry e : h.getPlayerList()) {
            //#if MC >= 2109
            String playerName = e.getProfile().name();
            //#else
            String playerName = e.getProfile().getName();
            //#endif
            if (playerName.equalsIgnoreCase(f.name)) return true;
        }
        return false;
    }
}

package com.nova.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Saved to .minecraft/config/novaclient.json */
public final class Config {
    /** Your own Azure app client ID (see README). */
    public String azureClientId = "";
    /** Microsoft refresh token, so you stay logged in. Treat this file like a password! */
    public String refreshToken = "";
    /** Custom background image (png/jpg). Blank = use the launcher's, or the built-in gradient. */
    public String backgroundPath = "";
    /** URL of latest.json for update notices. Blank = use the launcher's. */
    public String updateManifestUrl = "";
    public Map<String, Boolean> modules = new HashMap<>();
    /** HUD element positions: name -> [offsetX, offsetY, anchoredRight(0/1), anchoredBottom(0/1)] */
    public Map<String, List<Double>> hud = new HashMap<>();
    /** Module settings, keyed "Module/Setting". */
    public Map<String, Double> settings = new HashMap<>();
    public long playSeconds;
    public int launches;
    public long firstLaunch;
    public List<Friend> friends = new ArrayList<>();

    public static class Friend {
        public String name;
        public String uuid;
        public Friend() {}
        public Friend(String name, String uuid) { this.name = name; this.uuid = uuid; }
    }

    public String background() {
        if (backgroundPath != null && !backgroundPath.isBlank()) return backgroundPath;
        return System.getProperty("nova.background", "");
    }

    public String manifestUrl() {
        if (updateManifestUrl != null && !updateManifestUrl.isBlank()) return updateManifestUrl;
        return System.getProperty("nova.manifest", "");
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("novaclient.json");
    }

    public static synchronized Config get() {
        if (instance == null) load();
        return instance;
    }

    public static synchronized void load() {
        try {
            Path p = path();
            if (Files.exists(p)) instance = GSON.fromJson(Files.readString(p), Config.class);
        } catch (Exception ignored) {}
        if (instance == null) instance = new Config();
        if (instance.modules == null) instance.modules = new HashMap<>();
        if (instance.friends == null) instance.friends = new ArrayList<>();
        if (instance.hud == null) instance.hud = new HashMap<>();
        if (instance.settings == null) instance.settings = new HashMap<>();
    }

    public static synchronized void save() {
        try {
            Files.writeString(path(), GSON.toJson(get()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

package com.nova.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Saved in launcher.json inside the launcher folder. Contains a login token: keep it private. */
final class LauncherConfig {
    String azureClientId = DEFAULT_CLIENT_ID;
    /** The release pipeline writes the Microsoft app (client) ID here, so players never have to create one. */
    static final String DEFAULT_CLIENT_ID = "";
    String refreshToken = "";
    String offlineName = "Player";
    /** URL of latest.json (see README) that tells the launcher the newest Nova version. */
    String manifestUrl = DEFAULT_MANIFEST;
    /** The release pipeline writes this repo's latest.json address here at build time, so players need no setup. */
    static final String DEFAULT_MANIFEST = "";
    /** Optional: a locally built novaclient jar to use instead of downloading. */
    String localModJar = "";
    /** Optional: force a Minecraft version; blank = whatever the manifest says. */
    String mcVersion = "";
    String backgroundPath = "";
    String javaPath = "";
    int ramMb = 4096;
    /** Colour palette index (0 Nova, 1 Ocean, 2 Dusk, 3 Meadow, 4 Blossom). Shared with the game. */
    int theme = 0;
    /** Load the Nova Client mod. Off = plain Fabric (useful to check a version without Nova). */
    boolean useNova = true;
    /** Optional mods the player ticked, as comma separated Modrinth slugs: sodium, iris, freecam. */
    String optionalMods = "";
    /** True once the player has seen the "Choose your mods" window. */
    boolean optionalChosen = false;
    /** Skin PNG filenames saved in the launcher-managed skins folder; there is no item limit. */
    List<String> savedSkins = new ArrayList<>();
    /** Selected custom skin filename; blank means use the signed-in account's active skin. */
    String selectedSkin = "";
    /** Per-skin player model: true for slim arms, false for classic arms. */
    Map<String, Boolean> skinSlim = new LinkedHashMap<>();

    static Path file() { return Platform.home().resolve("launcher.json"); }

    static LauncherConfig load() {
        LauncherConfig c = new LauncherConfig();
        try {
            if (Files.exists(file())) {
                Map<String, Object> m = Json.o(Json.parse(Files.readString(file())));
                c.azureClientId = s(m, "azureClientId", c.azureClientId);
                if (c.azureClientId.isBlank()) c.azureClientId = DEFAULT_CLIENT_ID;
                c.refreshToken = s(m, "refreshToken", c.refreshToken);
                c.offlineName = s(m, "offlineName", c.offlineName);
                c.manifestUrl = s(m, "manifestUrl", c.manifestUrl);
                if (c.manifestUrl.isBlank()) c.manifestUrl = DEFAULT_MANIFEST;
                c.localModJar = s(m, "localModJar", c.localModJar);
                c.mcVersion = s(m, "mcVersion", c.mcVersion);
                c.backgroundPath = s(m, "backgroundPath", c.backgroundPath);
                c.javaPath = s(m, "javaPath", c.javaPath);
                long r = Json.num(m, "ramMb");
                if (r >= 1024) c.ramMb = (int) r;
                c.theme = (int) Math.max(0, Math.min(4, Json.num(m, "theme")));
                if (m.get("useNova") instanceof Boolean ub) c.useNova = ub;
                c.optionalMods = s(m, "optionalMods", c.optionalMods);
                if (m.get("optionalChosen") instanceof Boolean oc) c.optionalChosen = oc;
                c.selectedSkin = s(m, "selectedSkin", c.selectedSkin);
                Map<String, Object> models = Json.o(m.get("skinSlim"));
                if (models != null) {
                    c.skinSlim = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> entry : models.entrySet())
                        if (entry.getValue() instanceof Boolean slim) c.skinSlim.put(entry.getKey(), slim);
                }
                List<Object> skins = Json.a(m.get("savedSkins"));
                if (skins != null) {
                    c.savedSkins = new ArrayList<>();
                    for (Object skin : skins) if (skin instanceof String name && !name.isBlank()) c.savedSkins.add(name);
                }
            }
        } catch (Exception ignored) {}
        return c;
    }

    private static String s(Map<String, Object> m, String k, String def) {
        String v = Json.str(m, k);
        return v == null ? def : v;
    }

    void save() {
        try {
            Files.createDirectories(file().getParent());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("azureClientId", azureClientId);
            m.put("refreshToken", refreshToken);
            m.put("offlineName", offlineName);
            m.put("manifestUrl", manifestUrl);
            m.put("localModJar", localModJar);
            m.put("mcVersion", mcVersion);
            m.put("backgroundPath", backgroundPath);
            m.put("javaPath", javaPath);
            m.put("ramMb", (double) ramMb);
            m.put("theme", (double) theme);
            m.put("useNova", useNova);
            m.put("optionalMods", optionalMods);
            m.put("optionalChosen", optionalChosen);
            m.put("savedSkins", new ArrayList<>(savedSkins));
            m.put("selectedSkin", selectedSkin);
            m.put("skinSlim", new LinkedHashMap<>(skinSlim));
            Files.writeString(file(), Json.write(m));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

package com.nova.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Saved in launcher.json inside the launcher folder. Contains a login token: keep it private. */
final class LauncherConfig {
    String azureClientId = "";
    String refreshToken = "";
    String offlineName = "Player";
    /** URL of latest.json (see README) that tells the launcher the newest Nova version. */
    String manifestUrl = "";
    /** Optional: a locally built novaclient jar to use instead of downloading. */
    String localModJar = "";
    /** Optional: force a Minecraft version; blank = whatever the manifest says. */
    String mcVersion = "";
    String backgroundPath = "";
    String javaPath = "";
    int ramMb = 4096;

    static Path file() { return Platform.home().resolve("launcher.json"); }

    static LauncherConfig load() {
        LauncherConfig c = new LauncherConfig();
        try {
            if (Files.exists(file())) {
                Map<String, Object> m = Json.o(Json.parse(Files.readString(file())));
                c.azureClientId = s(m, "azureClientId", c.azureClientId);
                c.refreshToken = s(m, "refreshToken", c.refreshToken);
                c.offlineName = s(m, "offlineName", c.offlineName);
                c.manifestUrl = s(m, "manifestUrl", c.manifestUrl);
                c.localModJar = s(m, "localModJar", c.localModJar);
                c.mcVersion = s(m, "mcVersion", c.mcVersion);
                c.backgroundPath = s(m, "backgroundPath", c.backgroundPath);
                c.javaPath = s(m, "javaPath", c.javaPath);
                long r = Json.num(m, "ramMb");
                if (r >= 1024) c.ramMb = (int) r;
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
            Files.writeString(file(), Json.write(m));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

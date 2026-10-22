package com.nova.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Every Minecraft release that Fabric can run (Fabric starts at 1.14), newest first.
 * Mojang's manifest gives the order and the release type, Fabric's meta API says which versions it supports.
 * The result is cached so the picker still works offline.
 */
final class VersionList {
    private VersionList() {}

    static List<String> load(Path home) throws Exception {
        Path cache = home.resolve("version-list.json");
        try {
            Map<String, Object> manifest = Http.getJson(Installer.MOJANG_MANIFEST);
            List<Object> fabricGames = Json.a(Json.parse(Http.get(Installer.FABRIC_META + "/versions/game")));
            List<String> out = merge(manifest, fabricGames);
            if (out.isEmpty()) throw new java.io.IOException("Version list came back empty");
            try {
                Files.createDirectories(home);
                Files.writeString(cache, Json.write(new ArrayList<Object>(out)));
            } catch (Exception ignored) {}
            return out;
        } catch (Exception e) {
            if (Files.isRegularFile(cache)) {
                List<String> cached = new ArrayList<>();
                for (Object o : Json.a(Json.parse(Files.readString(cache)))) if (o instanceof String s) cached.add(s);
                if (!cached.isEmpty()) return cached;
            }
            throw e;
        }
    }

    /** Releases (no snapshots) that appear in both lists, in Mojang's order (newest first). */
    static List<String> merge(Map<String, Object> mojangManifest, List<Object> fabricGames) {
        Set<String> fabric = new HashSet<>();
        for (Object o : fabricGames) {
            Map<String, Object> g = Json.o(o);
            String v = Json.str(g, "version");
            if (v != null && Boolean.TRUE.equals(g.get("stable"))) fabric.add(v);
        }
        List<String> out = new ArrayList<>();
        for (Object o : Json.a(mojangManifest.get("versions"))) {
            Map<String, Object> m = Json.o(o);
            String id = Json.str(m, "id");
            if (id != null && "release".equals(m.get("type")) && fabric.contains(id)) out.add(id);
        }
        return out;
    }
}

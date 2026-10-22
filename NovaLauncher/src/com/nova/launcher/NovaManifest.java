package com.nova.launcher;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * latest.json as published by the release pipeline. One Nova jar per Minecraft version:
 * <pre>{ "version": "0.7.0", "minecraft": "1.21.4",
 *   "builds": { "1.21.4": { "url": "...", "sha256": "...", "tested": true }, "1.20.1": { ... } },
 *   "launcher": { "version": "1.0.0", "url": "..." } }</pre>
 * Old manifests (a single "mod" entry) still work: they become one build for their "minecraft" version.
 */
record NovaManifest(String version, String defaultMc, Map<String, Build> builds, String launcherVersion, String launcherUrl) {

    /** tested = a person has played this build; false = it compiled in CI but nobody has checked it in game. */
    record Build(String url, String sha256, boolean tested) {}

    static NovaManifest fetch(String url) throws Exception {
        return parse(Http.getJson(url));
    }

    static NovaManifest parse(Map<String, Object> m) {
        Map<String, Build> builds = new LinkedHashMap<>();
        Map<String, Object> raw = Json.o(m.get("builds"));
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            Map<String, Object> b = Json.o(e.getValue());
            String url = Json.str(b, "url");
            if (url == null) continue;
            builds.put(e.getKey(), new Build(url, Json.str(b, "sha256"), !Boolean.FALSE.equals(b.get("tested"))));
        }
        String mc = Json.str(m, "minecraft");
        Map<String, Object> mod = Json.o(m.get("mod"));
        if (builds.isEmpty() && mc != null && Json.str(mod, "url") != null)
            builds.put(mc, new Build(Json.str(mod, "url"), Json.str(mod, "sha256"), true));
        Map<String, Object> l = Json.o(m.get("launcher"));
        return new NovaManifest(Json.str(m, "version"), mc, builds, Json.str(l, "version"), Json.str(l, "url"));
    }
}

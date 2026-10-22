package com.nova.launcher;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Sodium, Iris and Freecam are not part of Nova Client. The player ticks the ones they want, and on Play the launcher
 * downloads the right build of each for the chosen Minecraft version from Modrinth (and removes the ones that were
 * switched off). The files it installed are remembered in mods/.nova-optional.json so it never touches mods you added yourself.
 */
final class OptionalMods {
    private OptionalMods() {}

    record Mod(String slug, String name, String blurb, List<String> requires) {}

    static final List<Mod> ALL = List.of(
            new Mod("sodium", "Sodium", "Much higher FPS: a faster renderer for Minecraft.", List.of()),
            new Mod("iris", "Iris Shaders", "Shader packs support. Needs Sodium (added automatically).", List.of("sodium")),
            new Mod("freecam", "Freecam", "Detach the camera and fly around without moving your player.", List.of()));

    record Found(String filename, String url, String sha1) {}

    static Mod bySlug(String slug) {
        for (Mod m : ALL) if (m.slug().equals(slug)) return m;
        return null;
    }

    /** The slugs saved in the launcher config ("sodium,iris"). */
    static Set<String> parse(String csv) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : csv.split(",")) {
            s = s.trim();
            if (bySlug(s) != null) out.add(s);
        }
        return out;
    }

    static String join(Collection<String> slugs) { return String.join(",", slugs); }

    /** Adds what the chosen mods depend on (Iris needs Sodium). */
    static Set<String> withRequirements(Set<String> chosen) {
        Set<String> out = new LinkedHashSet<>();
        for (Mod m : ALL) {
            if (!chosen.contains(m.slug())) continue;
            for (String r : m.requires()) out.add(r);
            out.add(m.slug());
        }
        // keep catalog order
        Set<String> ordered = new LinkedHashSet<>();
        for (Mod m : ALL) if (out.contains(m.slug())) ordered.add(m.slug());
        return ordered;
    }

    /** Newest release of a mod for this Minecraft version on Fabric, or null if there is none. */
    static Found find(String slug, String mc) throws Exception {
        String q = "https://api.modrinth.com/v2/project/" + slug + "/version?loaders="
                + URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8)
                + "&game_versions=" + URLEncoder.encode("[\"" + mc + "\"]", StandardCharsets.UTF_8);
        return pick(Json.a(Json.parse(Http.get(q))));
    }

    /** Picks the best version from a Modrinth version list (newest release first, else newest anything). Package-private for the self test. */
    static Found pick(List<Object> versions) {
        Map<String, Object> best = null;
        for (Object o : versions) {
            Map<String, Object> v = Json.o(o);
            if ("release".equals(v.get("version_type"))) { best = v; break; }
        }
        if (best == null && !versions.isEmpty()) best = Json.o(versions.get(0));
        if (best == null) return null;
        Map<String, Object> file = null;
        for (Object f : Json.a(best.get("files"))) {
            Map<String, Object> m = Json.o(f);
            String n = Json.str(m, "filename");
            if (n == null || !n.endsWith(".jar") || n.contains("-sources")) continue;
            if (Boolean.TRUE.equals(m.get("primary"))) { file = m; break; }
            if (file == null) file = m;
        }
        if (file == null) return null;
        return new Found(Json.str(file, "filename"), Json.str(file, "url"), Json.str(Json.o(file.get("hashes")), "sha1"));
    }

    private static Path stateFile(Path mods) { return mods.resolve(".nova-optional.json"); }

    private static Map<String, Object> loadState(Path mods) {
        try {
            if (Files.isRegularFile(stateFile(mods))) return Json.o(Json.parse(Files.readString(stateFile(mods))));
        } catch (Exception ignored) {}
        return new LinkedHashMap<>();
    }

    /**
     * Makes the mods folder match the player's choice for this Minecraft version.
     * Never throws for a single mod that cannot be had: it just says so and carries on, so the game still starts.
     */
    static void sync(LauncherConfig cfg, String mc, Path mods, Progress p) {
        Map<String, Object> state = loadState(mods);
        Set<String> want = withRequirements(parse(cfg.optionalMods));
        Map<String, Object> next = new LinkedHashMap<>();
        for (Mod m : ALL) {
            String have = Json.str(state, m.slug());
            if (!want.contains(m.slug())) {
                if (have != null) {
                    try { Files.deleteIfExists(mods.resolve(have)); Files.deleteIfExists(mods.resolve(have + ".disabled")); } catch (IOException ignored) {}
                    p.status(m.name() + " removed (switched off)");
                }
                continue;
            }
            try {
                p.status("Checking " + m.name() + " for Minecraft " + mc + "...");
                Found f = find(m.slug(), mc);
                if (f == null) {
                    if (have != null) { Files.deleteIfExists(mods.resolve(have)); }
                    p.status(m.name() + " has no build for Minecraft " + mc + " yet: starting without it");
                    continue;
                }
                Http.download(f.url(), mods.resolve(f.filename()), "SHA-1", f.sha1());
                if (have != null && !have.equals(f.filename())) Files.deleteIfExists(mods.resolve(have));
                next.put(m.slug(), f.filename());
                p.status(m.name() + " is ready (" + f.filename() + ")");
            } catch (Exception e) {
                if (have != null && Files.isRegularFile(mods.resolve(have))) {
                    next.put(m.slug(), have);
                    p.status("Could not update " + m.name() + " (" + e.getMessage() + "); using the installed copy");
                } else {
                    p.status("Could not get " + m.name() + " (" + e.getMessage() + "); starting without it");
                }
            }
        }
        // an optional mod whose dependency is missing would stop Fabric from starting: drop Iris when Sodium is not there
        if (next.containsKey("iris") && !next.containsKey("sodium")) {
            try { Files.deleteIfExists(mods.resolve((String) next.get("iris"))); } catch (IOException ignored) {}
            next.remove("iris");
            p.status("Iris needs Sodium, which is not available for Minecraft " + mc + ": starting without Iris");
        }
        try { Files.writeString(stateFile(mods), Json.write(next)); } catch (IOException ignored) {}
    }
}

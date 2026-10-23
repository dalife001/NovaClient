package com.nova.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Keeps the mods folder matched to the chosen Minecraft version: the Nova Client build for that version
 * (from your latest.json) and the newest Fabric API for it.
 */
final class NovaUpdater {
    private NovaUpdater() {}

    static final String DEFAULT_MC = "1.21.4";

    record Result(String mcVersion, String novaVersion, String latestLauncher, String launcherUrl, boolean novaLoaded, Path versionDir) {}

    /** Every Minecraft version gets its own game folder: its own mods, options, resource packs, worlds. */
    static Path versionDir(Path gameDir, String mc) {
        return gameDir.resolve("versions").resolve(mc.replaceAll("[^0-9A-Za-z._-]", "_"));
    }

    static Path modsDir(Path gameDir, String mc) { return versionDir(gameDir, mc).resolve("mods"); }

    /**
     * First time a version is played: it starts with your settings and your own mods from before, so nothing is lost.
     * Copied: options.txt, servers.dat, resource packs, shader packs and your own mods that fit this version.
     * Worlds are not copied (they can be big, and a world opened in a newer version cannot go back): they stay in the old folder.
     * Nova, Fabric API, Sodium, Iris and Freecam are not copied: the launcher installs the right builds itself.
     */
    static void adoptOldGame(Path gameDir, Path vdir, String mc) {
        try {
            Path mods = vdir.resolve("mods");
            Path earlier = gameDir.resolve("mods-by-version").resolve(mc);   // an earlier test build of the launcher used this
            if (Files.isDirectory(earlier)) copyTree(earlier, mods);
            else adoptOldMods(gameDir.resolve("mods"), mods, mc);
            for (String f : new String[]{"options.txt", "servers.dat"}) {
                Path src = gameDir.resolve(f);
                if (Files.isRegularFile(src)) Files.copy(src, vdir.resolve(f), StandardCopyOption.REPLACE_EXISTING);
            }
            for (String d : new String[]{"resourcepacks", "shaderpacks"}) copyTree(gameDir.resolve(d), vdir.resolve(d));
        } catch (IOException ignored) {}
    }

    private static void copyTree(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from)) return;
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path t = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(t);
                else { Files.createDirectories(t.getParent()); Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING); }
            }
        }
    }

    /**
     * Your own mods from the old shared folder that fit this version.
     * Nova, Fabric API, Sodium, Iris and Freecam are not copied: the launcher installs the right builds itself.
     */
    static void adoptOldMods(Path old, Path mods, String mc) {
        if (!Files.isDirectory(old)) return;
        try (Stream<Path> s = Files.list(old)) {
            for (Path f : (Iterable<Path>) s::iterator) {
                String n = f.getFileName().toString();
                String low = n.toLowerCase();
                if (!low.endsWith(".jar") && !low.endsWith(".jar.disabled")) continue;
                if (low.startsWith("novaclient-") || low.startsWith("fabric-api") || low.startsWith("sodium")
                        || low.startsWith("iris") || low.startsWith("freecam")) continue;
                JarInfo info = JarInfo.read(f);
                if (info == null || !info.supports(mc)) continue;
                try { Files.copy(f, mods.resolve(n), StandardCopyOption.REPLACE_EXISTING); } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {}
    }

    /** What the launcher plays when nothing is forced: the manifest's default, else the built-in mod's, else DEFAULT_MC. */
    static String defaultMc(NovaManifest man, Embedded emb) {
        if (man != null && man.defaultMc() != null) return man.defaultMc();
        if (emb != null) return emb.minecraft;
        return DEFAULT_MC;
    }

    /** File name a Nova jar gets inside the mods folder. The Minecraft version is part of it so builds never get mixed up. */
    static String jarName(String version, String mc) { return "novaclient-" + version + "-mc" + mc + ".jar"; }

    record Pick(String novaVersion, boolean loaded) {}

    static Result sync(LauncherConfig cfg, Path gameDir, Progress p) throws Exception {
        Path legacy = gameDir.resolve("mods");   // the old shared folder (only read, never written)
        Embedded emb = Embedded.load();

        NovaManifest man = null;
        if (!cfg.manifestUrl.isBlank()) {
            try {
                p.status("Checking for Nova Client updates...");
                man = NovaManifest.fetch(cfg.manifestUrl);
            } catch (Exception e) {
                p.status("Update check failed (" + e.getMessage() + "); using what is already installed");
            }
        }
        String mc = chooseMc(cfg, man, emb, legacy);

        Path vdir = versionDir(gameDir, mc);
        Path mods = vdir.resolve("mods");
        boolean fresh = !Files.isDirectory(vdir);
        Files.createDirectories(mods);
        if (fresh) adoptOldGame(gameDir, vdir, mc);

        Pick pick = installNova(cfg, man, emb, mc, mods, p);

        // Sodium / Iris / Freecam: whatever the player ticked, for this Minecraft version.
        OptionalMods.sync(cfg, mc, mods, p);

        // Fabric refuses to start if any mod needs another Minecraft version, so switch those off first.
        // Sodium / Iris / Freecam: exactly what the player ticked, for this Minecraft version
        OptionalMods.sync(cfg, mc, mods, p);
        for (String line : ModScan.disableIncompatible(mods, mc)) p.status(line);
        boolean loaded = pick.loaded() && ModScan.novaEnabled(mods);
        String novaVersion = pick.novaVersion() + (pick.loaded() && !loaded ? " (skipped: built for another Minecraft version)" : "");

        try {
            fabricApi(mc, mods, p);
        } catch (IOException e) {
            // Keep only a Fabric API that belongs to this Minecraft version; anything else would stop the game from starting.
            deleteNotContaining(mods, "fabric-api-", mc);
            if (loaded && !hasFile(mods, "fabric-api-")) throw e;
            p.status("Could not check Fabric API updates; using the installed one");
        }
        return new Result(mc, novaVersion, man == null ? null : man.launcherVersion(), man == null ? null : man.launcherUrl(), loaded, vdir);
    }

    /**
     * Which Minecraft version to play: the one you picked; else the version your own Nova jar was built for;
     * else what the update manifest / built-in mod says.
     */
    static String chooseMc(LauncherConfig cfg, NovaManifest man, Embedded emb, Path mods) {
        if (!cfg.mcVersion.isBlank()) return cfg.mcVersion;
        try {
            if (!cfg.localModJar.isBlank()) {
                JarInfo i = JarInfo.read(Path.of(cfg.localModJar));
                if (i != null && i.mcHint() != null) return i.mcHint();
            } else if (man == null && emb == null && Files.isDirectory(mods)) {
                try (Stream<Path> s = Files.list(mods)) {
                    for (Path f : (Iterable<Path>) s::iterator) {
                        String n = f.getFileName().toString();
                        if (!n.startsWith("novaclient-") || !n.endsWith(".jar")) continue;
                        JarInfo i = JarInfo.read(f);
                        if (i != null && i.mcHint() != null) return i.mcHint();
                    }
                }
            }
        } catch (Exception ignored) {}
        return defaultMc(man, emb);
    }

    /** Puts the right Nova jar for this Minecraft version into the mods folder (and removes every other one). */
    static Pick installNova(LauncherConfig cfg, NovaManifest man, Embedded emb, String mc, Path mods, Progress p) throws IOException {
        if (!cfg.localModJar.isBlank()) {
            // developer override: always use this exact jar
            Path src = Path.of(cfg.localModJar);
            if (!Files.isRegularFile(src)) throw new IOException("Local mod jar not found: " + src);
            Path dest = mods.resolve("novaclient-dev.jar");
            deleteOthers(mods, "novaclient-", dest.getFileName().toString());
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            p.status("Using your local Nova Client jar");
            return new Pick("dev (local jar)", true);
        }
        if (!cfg.useNova) {
            retire(mods);
            p.status("Nova Client is switched off in Settings: starting plain Fabric");
            return new Pick("switched off", false);
        }

        NovaManifest.Build b = man == null ? null : man.builds().get(mc);
        boolean embMatches = emb != null && mc.equals(emb.minecraft);
        if (b != null && man.version() != null && (!embMatches || Versions.cmp(man.version(), emb.version) > 0)) {
            String name = jarName(man.version(), mc);
            try {
                p.status("Getting Nova Client " + man.version() + " for Minecraft " + mc + "...");
                Http.download(b.url(), mods.resolve(name), "SHA-256", b.sha256());
                deleteOthers(mods, "novaclient-", name);
                return new Pick(man.version() + (b.tested() ? "" : " (experimental on " + mc + ")"), true);
            } catch (IOException | InterruptedException e) {
                p.status("Could not download Nova Client (" + e.getMessage() + ")");
            }
        }
        if (embMatches) {
            try (InputStream in = emb.jar()) { installJar(in, mods, emb.version + "-mc" + mc); }
            p.status("Nova Client " + emb.version + " is ready");
            return new Pick(emb.version + " (built in)", true);
        }
        String cached = cachedFor(mods, mc);
        if (cached != null) {
            deleteOthers(mods, "novaclient-", cached);
            p.status("Using the Nova Client already installed for " + mc);
            return new Pick("installed copy", true);
        }
        if (manualJar(mods)) {
            p.status("Using the Nova jar you put in the mods folder (not managed by the launcher)");
            return new Pick("your own jar", true);
        }
        deleteOthers(mods, "novaclient-", "");
        p.status("There is no Nova Client build for Minecraft " + mc + " yet: starting plain Fabric");
        return new Pick("none for " + mc, false);
    }

    /** True if the mods folder has a Nova jar the launcher did not create (for example one you built yourself). */
    static boolean manualJar(Path mods) throws IOException {
        try (Stream<Path> s = Files.list(mods)) {
            for (Path f : (Iterable<Path>) s::iterator) {
                String n = f.getFileName().toString();
                if (n.startsWith("novaclient-") && n.endsWith(".jar") && !n.contains("-mc")) return true;
            }
        }
        try (Stream<Path> s = Files.list(mods)) {          // switched back on: bring a retired jar back
            for (Path f : (Iterable<Path>) s::iterator) {
                String n = f.getFileName().toString();
                if (n.startsWith("novaclient-") && n.endsWith(".jar.disabled") && !n.contains("-mc")) {
                    Files.move(f, f.resolveSibling(n.substring(0, n.length() - ".disabled".length())), StandardCopyOption.REPLACE_EXISTING);
                    return true;
                }
            }
        }
        return false;
    }

    /** Nova switched off: launcher-managed jars are deleted, a jar you made yourself is only renamed so it is not lost. */
    static void retire(Path mods) throws IOException {
        try (Stream<Path> s = Files.list(mods)) {
            for (Path f : (Iterable<Path>) s::iterator) {
                String n = f.getFileName().toString();
                if (!n.startsWith("novaclient-") || !n.endsWith(".jar")) continue;
                if (n.contains("-mc")) Files.deleteIfExists(f);
                else Files.move(f, f.resolveSibling(n + ".disabled"), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** A Nova jar already in the mods folder that was built for this Minecraft version. */
    private static String cachedFor(Path mods, String mc) throws IOException {
        try (Stream<Path> s = Files.list(mods)) {
            return s.map(f -> f.getFileName().toString())
                    .filter(n -> n.startsWith("novaclient-") && n.endsWith("-mc" + mc + ".jar"))
                    .findFirst().orElse(null);
        }
    }

    /** Writes the built-in mod jar into the mods folder and removes older Nova jars. */
    static void installJar(InputStream in, Path mods, String version) throws IOException {
        String name = "novaclient-" + version + ".jar";
        Path dest = mods.resolve(name);
        byte[] data = in.readAllBytes();
        if (!Files.exists(dest) || !java.util.Arrays.equals(Files.readAllBytes(dest), data)) Files.write(dest, data);
        deleteOthers(mods, "novaclient-", name);
    }

    /** Newest release of Fabric API for this Minecraft version, via Modrinth. */
    private static void fabricApi(String mc, Path mods, Progress p) throws Exception {
        p.status("Checking Fabric API...");
        String q = "https://api.modrinth.com/v2/project/fabric-api/version?loaders="
                + URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8)
                + "&game_versions=" + URLEncoder.encode("[\"" + mc + "\"]", StandardCharsets.UTF_8);
        List<Object> versions = Json.a(Json.parse(Http.get(q)));
        Map<String, Object> best = null;
        for (Object o : versions) {
            Map<String, Object> v = Json.o(o);
            if ("release".equals(v.get("version_type"))) { best = v; break; }
        }
        if (best == null && !versions.isEmpty()) best = Json.o(versions.get(0));
        if (best == null) throw new IOException("No Fabric API build for Minecraft " + mc + " yet");
        Map<String, Object> file = null;
        for (Object f : Json.a(best.get("files"))) {
            Map<String, Object> m = Json.o(f);
            if (Boolean.TRUE.equals(m.get("primary"))) { file = m; break; }
            if (file == null) file = m;
        }
        if (file == null) throw new IOException("Fabric API listing had no file");
        String name = Json.str(file, "filename");
        Http.download(Json.str(file, "url"), mods.resolve(name), "SHA-1", Json.str(Json.o(file.get("hashes")), "sha1"));
        deleteOthers(mods, "fabric-api-", name);
    }

    private static boolean hasFile(Path dir, String prefix) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.anyMatch(f -> f.getFileName().toString().startsWith(prefix));
        }
    }

    static void deleteOthers(Path dir, String prefix, String keep) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            for (Path f : (Iterable<Path>) s::iterator) {
                String n = f.getFileName().toString();
                if (n.startsWith(prefix) && n.endsWith(".jar") && !n.equals(keep)) Files.deleteIfExists(f);
            }
        }
    }

    /** Deletes jars with this prefix whose name does not mention the Minecraft version. */
    static void deleteNotContaining(Path dir, String prefix, String mc) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            for (Path f : (Iterable<Path>) s::iterator) {
                String n = f.getFileName().toString();
                if (n.startsWith(prefix) && n.endsWith(".jar") && !n.contains(mc)) Files.deleteIfExists(f);
            }
        }
    }
}

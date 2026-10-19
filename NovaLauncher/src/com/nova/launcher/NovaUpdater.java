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
 * Keeps the mods folder up to date: the newest Nova Client (from your latest.json),
 * and the newest Fabric API for the Minecraft version Nova targets.
 */
final class NovaUpdater {
    private NovaUpdater() {}

    static final String DEFAULT_MC = "1.21.4";

    record Result(String mcVersion, String novaVersion, String latestLauncher, String launcherUrl) {}

    static Result sync(LauncherConfig cfg, Path gameDir, Progress p) throws Exception {
        Path mods = gameDir.resolve("mods");
        Files.createDirectories(mods);
        boolean forcedMc = !cfg.mcVersion.isBlank();
        String mc = forcedMc ? cfg.mcVersion : DEFAULT_MC;
        String novaVersion = installedNova(mods);
        String launcherLatest = null, launcherUrl = null;
        Embedded emb = Embedded.load();

        if (!cfg.localModJar.isBlank()) {
            // developer override: always use this exact jar
            Path src = Path.of(cfg.localModJar);
            if (!Files.isRegularFile(src)) throw new IOException("Local mod jar not found: " + src);
            Path dest = mods.resolve("novaclient-dev.jar");
            deleteOthers(mods, "novaclient-", dest.getFileName().toString());
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            novaVersion = "dev (local jar)";
            p.status("Using your local Nova Client jar");
        } else {
            boolean done = false;
            if (!cfg.manifestUrl.isBlank()) {
                try {
                    p.status("Checking for Nova Client updates...");
                    Map<String, Object> m = Http.getJson(cfg.manifestUrl);
                    Map<String, Object> l = Json.o(m.get("launcher"));
                    launcherLatest = Json.str(l, "version");
                    launcherUrl = Json.str(l, "url");
                    Map<String, Object> mod = Json.o(m.get("mod"));
                    String ver = Json.str(m, "version");
                    String url = Json.str(mod, "url");
                    // only download when the online version is newer than the one built into this app
                    if (ver != null && url != null && (emb == null || Versions.cmp(ver, emb.version) > 0)) {
                        String name = "novaclient-" + ver + ".jar";
                        p.status("Updating Nova Client to " + ver + "...");
                        Http.download(url, mods.resolve(name), "SHA-256", Json.str(mod, "sha256"));
                        deleteOthers(mods, "novaclient-", name);
                        novaVersion = ver;
                        if (!forcedMc && Json.str(m, "minecraft") != null) mc = Json.str(m, "minecraft");
                        done = true;
                    }
                } catch (IOException e) {
                    p.status("Update check failed (" + e.getMessage() + "); using the built-in version");
                }
            }
            if (!done) {
                if (emb != null) {
                    try (InputStream in = emb.jar()) { installJar(in, mods, emb.version); }
                    novaVersion = emb.version + " (built in)";
                    if (!forcedMc) mc = emb.minecraft;
                    p.status("Nova Client " + emb.version + " is ready");
                } else {
                    p.status("No built-in Nova Client; using whatever is in the mods folder");
                }
            }
        }

        try {
            fabricApi(mc, mods, p);
        } catch (IOException e) {
            if (!hasFile(mods, "fabric-api-")) throw e;
            p.status("Could not check Fabric API updates; using the installed one");
        }
        return new Result(mc, novaVersion, launcherLatest, launcherUrl);
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

    private static String installedNova(Path mods) throws IOException {
        try (Stream<Path> s = Files.list(mods)) {
            return s.map(f -> f.getFileName().toString())
                    .filter(n -> n.startsWith("novaclient-") && n.endsWith(".jar"))
                    .map(n -> n.substring("novaclient-".length(), n.length() - 4))
                    .findFirst().orElse("not installed");
        }
    }

    private static boolean hasFile(Path dir, String prefix) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.anyMatch(f -> f.getFileName().toString().startsWith(prefix));
        }
    }

    private static void deleteOthers(Path dir, String prefix, String keep) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            for (Path f : (Iterable<Path>) s::iterator) {
                String n = f.getFileName().toString();
                if (n.startsWith(prefix) && n.endsWith(".jar") && !n.equals(keep)) Files.deleteIfExists(f);
            }
        }
    }
}

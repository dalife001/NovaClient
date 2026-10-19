package com.nova.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/** Downloads Minecraft (from Mojang) and Fabric Loader, and works out how to start them. */
final class Installer {
    static final String MOJANG_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    static final String FABRIC_META = "https://meta.fabricmc.net/v2";
    static final String ASSET_HOST = "https://resources.download.minecraft.net/";

    record Plan(String mainClass, List<Path> classpath, List<String> jvm, List<String> game,
                String assetIndex, Path assetsDir, String versionId, Path natives, int javaMajor) {}

    private final Path root;
    private final Progress p;

    Installer(Path root, Progress p) { this.root = root; this.p = p; }

    Plan install(String mc) throws Exception {
        // ---- vanilla version json ----
        p.status("Reading Minecraft version list...");
        Map<String, Object> manifest = Http.getJson(MOJANG_MANIFEST);
        Map<String, Object> entry = null;
        for (Object o : Json.a(manifest.get("versions"))) {
            Map<String, Object> m = Json.o(o);
            if (mc.equals(m.get("id"))) { entry = m; break; }
        }
        if (entry == null) throw new IOException("Minecraft version " + mc + " not found");
        Path vdir = root.resolve("versions").resolve(mc);
        Path vjson = vdir.resolve(mc + ".json");
        Http.download(Json.str(entry, "url"), vjson, "SHA-1", Json.str(entry, "sha1"));
        Map<String, Object> vj = Json.o(Json.parse(Files.readString(vjson)));

        List<Http.Job> jobs = new ArrayList<>();

        // ---- client jar ----
        Map<String, Object> client = Json.o(Json.o(vj.get("downloads")).get("client"));
        Path clientJar = vdir.resolve(mc + ".jar");
        jobs.add(new Http.Job(Json.str(client, "url"), clientJar, "SHA-1", Json.str(client, "sha1"), Json.num(client, "size")));

        // ---- libraries (Fabric first so its versions win on duplicates) ----
        Path libs = root.resolve("libraries");
        Map<String, Path> classpath = new LinkedHashMap<>();

        Map<String, Object> fabric = fabricProfile(mc);
        for (Object o : Json.a(fabric.get("libraries"))) {
            Map<String, Object> lib = Json.o(o);
            String path = mavenPath(Json.str(lib, "name"));
            String base = Json.str(lib, "url");
            if (base == null) base = "https://maven.fabricmc.net/";
            if (!base.endsWith("/")) base += "/";
            Path dest = libs.resolve(path);
            jobs.add(new Http.Job(base + path, dest, "SHA-1", Json.str(lib, "sha1"), 0));
            classpath.putIfAbsent(dedupeKey(path), dest);
        }
        for (Object o : Json.a(vj.get("libraries"))) {
            Map<String, Object> lib = Json.o(o);
            if (!Rules.allowed(Json.a(lib.get("rules")))) continue;
            Map<String, Object> art = Json.o(Json.o(lib.get("downloads")).get("artifact"));
            String path = Json.str(art, "path");
            if (path == null) continue;
            Path dest = libs.resolve(path);
            jobs.add(new Http.Job(Json.str(art, "url"), dest, "SHA-1", Json.str(art, "sha1"), Json.num(art, "size")));
            classpath.putIfAbsent(dedupeKey(path), dest);
        }
        List<Path> cp = new ArrayList<>(classpath.values());
        cp.add(clientJar);

        // ---- assets ----
        Map<String, Object> ai = Json.o(vj.get("assetIndex"));
        String aiId = Json.str(ai, "id");
        Path assets = root.resolve("assets");
        Path indexFile = assets.resolve("indexes").resolve(aiId + ".json");
        Http.download(Json.str(ai, "url"), indexFile, "SHA-1", Json.str(ai, "sha1"));
        Map<String, Object> objects = Json.o(Json.o(Json.parse(Files.readString(indexFile))).get("objects"));
        for (Object o : objects.values()) {
            Map<String, Object> obj = Json.o(o);
            String h = Json.str(obj, "hash");
            String sub = h.substring(0, 2) + "/" + h;
            jobs.add(new Http.Job(ASSET_HOST + sub, assets.resolve("objects").resolve(h.substring(0, 2)).resolve(h), "SHA-1", h, Json.num(obj, "size")));
        }

        p.status("Checking game files...");
        Http.all(jobs, p, "Downloading game files");

        // ---- launch arguments ----
        Map<String, Object> vArgs = Json.o(vj.get("arguments"));
        Map<String, Object> fArgs = Json.o(fabric.get("arguments"));
        List<String> jvm = new ArrayList<>(Rules.resolveArgs(Json.a(vArgs.get("jvm"))));
        jvm.addAll(Rules.resolveArgs(Json.a(fArgs.get("jvm"))));
        List<String> game = new ArrayList<>(Rules.resolveArgs(Json.a(vArgs.get("game"))));
        game.addAll(Rules.resolveArgs(Json.a(fArgs.get("game"))));
        if (vArgs.isEmpty() && vj.get("minecraftArguments") instanceof String legacy) {
            game.addAll(Arrays.asList(legacy.split(" ")));
        }

        Path natives = vdir.resolve("natives");
        Files.createDirectories(natives);
        int java = (int) Json.num(Json.o(vj.get("javaVersion")), "majorVersion");
        String main = Json.str(fabric, "mainClass");
        if (main == null) main = Json.str(vj, "mainClass");

        p.status("Game files ready");
        return new Plan(main, cp, jvm, game, aiId, assets, mc, natives, java == 0 ? 21 : java);
    }

    Map<String, Object> fabricProfile(String mc) throws Exception {
        p.status("Reading Fabric version list...");
        List<Object> loaders = Json.a(Json.parse(Http.get(FABRIC_META + "/versions/loader/" + mc)));
        if (loaders.isEmpty()) throw new IOException("Fabric does not support Minecraft " + mc + " yet");
        String chosen = null;
        for (Object o : loaders) {
            Map<String, Object> l = Json.o(Json.o(o).get("loader"));
            if (Boolean.TRUE.equals(l.get("stable"))) { chosen = Json.str(l, "version"); break; }
        }
        if (chosen == null) chosen = Json.str(Json.o(Json.o(loaders.get(0)).get("loader")), "version");
        Path cache = root.resolve("versions").resolve(mc).resolve("fabric-" + chosen + ".json");
        String body;
        try {
            body = Http.get(FABRIC_META + "/versions/loader/" + mc + "/" + chosen + "/profile/json");
            Files.createDirectories(cache.getParent());
            Files.writeString(cache, body);
        } catch (IOException e) {
            if (!Files.exists(cache)) throw e;
            body = Files.readString(cache); // offline: reuse
        }
        return Json.o(Json.parse(body));
    }

    // ----- helpers (package-private for tests) -----
    static String mavenPath(String name) {
        String[] t = name.split(":");
        String ext = "jar";
        String ver = t[2];
        int at = ver.indexOf('@');
        if (at >= 0) { ext = ver.substring(at + 1); ver = ver.substring(0, at); }
        String cls = t.length > 3 ? "-" + t[3] : "";
        return t[0].replace('.', '/') + "/" + t[1] + "/" + ver + "/" + t[1] + "-" + ver + cls + "." + ext;
    }

    /** Same library at a different version => same key, so Fabric's copy replaces Mojang's. */
    static String dedupeKey(String path) {
        Path p = Path.of(path);
        Path verDir = p.getParent();
        if (verDir == null || verDir.getParent() == null) return path;
        String ver = verDir.getFileName().toString();
        return verDir.getParent().toString().replace('\\', '/') + "/" + p.getFileName().toString().replace(ver, "");
    }
}

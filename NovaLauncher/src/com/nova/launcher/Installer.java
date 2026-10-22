package com.nova.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Downloads Minecraft (from Mojang) and Fabric Loader, and works out how to start them. */
final class Installer {
    static final String MOJANG_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    static final String FABRIC_META = "https://meta.fabricmc.net/v2";
    static final String ASSET_HOST = "https://resources.download.minecraft.net/";

    record Plan(String mainClass, List<Path> classpath, List<String> jvm, List<String> game,
                String assetIndex, Path assetsDir, String versionId, Path natives, int javaMajor) {}

    /** An old-style native library: a jar whose contents (dll/so/dylib) must be unpacked into the natives folder. */
    record NativeJar(Path jar, List<String> exclude) {}

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
        List<NativeJar> nativeJars = new ArrayList<>();
        for (Object o : Json.a(vj.get("libraries"))) {
            Map<String, Object> lib = Json.o(o);
            if (!Rules.allowed(Json.a(lib.get("rules")))) continue;
            Map<String, Object> dl = Json.o(lib.get("downloads"));
            // Minecraft 1.14 - 1.18 style: natives come as a classifier jar that has to be unpacked
            String cls = nativeClassifier(lib);
            if (cls != null) {
                Map<String, Object> n = Json.o(Json.o(dl.get("classifiers")).get(cls));
                String npath = Json.str(n, "path");
                if (npath != null) {
                    Path ndest = libs.resolve(npath);
                    jobs.add(new Http.Job(Json.str(n, "url"), ndest, "SHA-1", Json.str(n, "sha1"), Json.num(n, "size")));
                    List<String> ex = new ArrayList<>();
                    for (Object x : Json.a(Json.o(lib.get("extract")).get("exclude"))) ex.add(String.valueOf(x));
                    nativeJars.add(new NativeJar(ndest, ex));
                }
            }
            Map<String, Object> art = Json.o(dl.get("artifact"));
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

        // ---- log4j config (older versions need Mojang's patched one) ----
        String logArg = null;
        Map<String, Object> logging = Json.o(Json.o(vj.get("logging")).get("client"));
        Map<String, Object> lf = Json.o(logging.get("file"));
        if (Json.str(lf, "url") != null && Json.str(lf, "id") != null && Json.str(logging, "argument") != null) {
            Path lp = assets.resolve("log_configs").resolve(Json.str(lf, "id"));
            jobs.add(new Http.Job(Json.str(lf, "url"), lp, "SHA-1", Json.str(lf, "sha1"), Json.num(lf, "size")));
            logArg = Json.str(logging, "argument").replace("${path}", lp.toString());
        }

        p.status("Checking game files...");
        Http.all(jobs, p, "Downloading game files");

        Path natives = vdir.resolve("natives");
        Files.createDirectories(natives);
        for (NativeJar nj : nativeJars) extractNatives(nj.jar(), natives, nj.exclude());

        // ---- launch arguments ----
        Map<String, Object> vArgs = Json.o(vj.get("arguments"));
        Map<String, Object> fArgs = Json.o(fabric.get("arguments"));
        List<String> jvm = new ArrayList<>(Rules.resolveArgs(Json.a(vArgs.get("jvm"))));
        jvm.addAll(Rules.resolveArgs(Json.a(fArgs.get("jvm"))));
        if (jvm.stream().noneMatch(a -> a.contains("java.library.path"))) jvm.add("-Djava.library.path=${natives_directory}");
        if (logArg != null) jvm.add(logArg);
        List<String> game = new ArrayList<>(Rules.resolveArgs(Json.a(vArgs.get("game"))));
        game.addAll(Rules.resolveArgs(Json.a(fArgs.get("game"))));
        if (vArgs.isEmpty() && vj.get("minecraftArguments") instanceof String legacy) {
            game.addAll(Arrays.asList(legacy.split(" ")));
        }

        int java = (int) Json.num(Json.o(vj.get("javaVersion")), "majorVersion");
        String main = Json.str(fabric, "mainClass");
        if (main == null) main = Json.str(vj, "mainClass");

        p.status("Game files ready");
        return new Plan(main, cp, jvm, game, aiId, assets, mc, natives, java == 0 ? 8 : java);
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

    /** Classifier name (like natives-windows) for this computer, or null if the library has no old-style natives. */
    static String nativeClassifier(Map<String, Object> lib) {
        Map<String, Object> nat = Json.o(lib.get("natives"));
        String cls = nat.isEmpty() ? null : Json.str(nat, Platform.os());
        return cls == null ? null : cls.replace("${arch}", Platform.is32bit() ? "32" : "64");
    }

    static boolean excluded(String entry, List<String> exclude) {
        for (String x : exclude) if (entry.startsWith(x)) return true;
        return false;
    }

    /** Unpacks the native libraries from a jar into the natives folder. */
    static void extractNatives(Path jar, Path dest, List<String> exclude) throws IOException {
        Path base = dest.toAbsolutePath().normalize();
        try (ZipFile z = new ZipFile(jar.toFile())) {
            for (var en = z.entries(); en.hasMoreElements(); ) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory() || excluded(e.getName(), exclude)) continue;
                Path out = base.resolve(e.getName()).normalize();
                if (!out.startsWith(base)) continue;                    // never write outside the natives folder
                if (Files.isRegularFile(out) && Files.size(out) == e.getSize()) continue;
                Files.createDirectories(out.getParent());
                try (InputStream in = z.getInputStream(e)) { Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING); }
            }
        }
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

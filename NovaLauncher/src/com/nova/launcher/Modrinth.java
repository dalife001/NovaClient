package com.nova.launcher;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Search and install from Modrinth (https://modrinth.com), the open site where creators publish mods, resource packs
 * and shaders. Everything is downloaded straight from Modrinth, checked against its SHA-512, and put in the folder of
 * the chosen Minecraft version. What was installed is remembered in a small file in each folder.
 */
final class Modrinth {
    private Modrinth() {}

    static final String API = "https://api.modrinth.com/v2";
    /** Fabric API is installed and updated by the launcher itself. */
    private static final String FABRIC_API_ID = "P7dR8mSH";

    enum Kind {
        MOD("mod", "Mods", "mods", ".jar"),
        PACK("resourcepack", "Resource packs", "resourcepacks", ".zip"),
        SHADER("shader", "Shaders", "shaderpacks", ".zip");

        final String type, label, folder, ext;
        Kind(String type, String label, String folder, String ext) { this.type = type; this.label = label; this.folder = folder; this.ext = ext; }
    }

    record Hit(String id, String slug, String title, String author, String description, long downloads, String iconUrl, int color, Kind kind) {}
    record Page(List<Hit> hits, int total) {}
    record Dep(String projectId, String type) {}
    record Version(String id, String projectId, String name, String filename, String url, String sha512, List<Dep> deps) {}

    // ------------------------------------------------------------------ search
    /** The address of a search. Package-private for the self test. */
    static String searchUrl(Kind kind, String query, String mc, String sort, int offset) {
        StringBuilder f = new StringBuilder("[[\"project_type:" + kind.type + "\"]");
        if (mc != null && !mc.isBlank()) f.append(",[\"versions:").append(mc).append("\"]");
        if (kind == Kind.MOD) f.append(",[\"categories:fabric\"]");
        f.append("]");
        return API + "/search?query=" + enc(query == null ? "" : query.trim()) + "&facets=" + enc(f.toString())
                + "&index=" + enc(sort == null ? "relevance" : sort) + "&limit=20&offset=" + offset;
    }

    static Page search(Kind kind, String query, String mc, String sort, int offset) throws Exception {
        return parseSearch(Json.o(Json.parse(Http.get(searchUrl(kind, query, mc, sort, offset)))), kind);
    }

    static Page parseSearch(Map<String, Object> body, Kind kind) {
        List<Hit> out = new ArrayList<>();
        for (Object o : Json.a(body.get("hits"))) {
            Map<String, Object> h = Json.o(o);
            String id = Json.str(h, "project_id");
            if (id == null || id.equals(FABRIC_API_ID)) continue;   // the launcher installs Fabric API itself
            out.add(new Hit(id, Json.str(h, "slug"), orEmpty(Json.str(h, "title")), orEmpty(Json.str(h, "author")),
                    orEmpty(Json.str(h, "description")), Json.num(h, "downloads"), Json.str(h, "icon_url"), h.get("color") instanceof Number c ? c.intValue() : 0, kind));
        }
        return new Page(out, (int) Json.num(body, "total_hits"));
    }

    // ------------------------------------------------------------------ versions and files
    /** The newest release of a project for this Minecraft version (else the newest of any kind), or null. */
    static Version findVersion(String projectId, Kind kind, String mc) throws Exception {
        StringBuilder q = new StringBuilder(API + "/project/" + enc(projectId) + "/version?game_versions=" + enc("[\"" + mc + "\"]"));
        if (kind == Kind.MOD) q.append("&loaders=").append(enc("[\"fabric\"]"));
        return pickVersion(Json.a(Json.parse(Http.get(q.toString()))), kind);
    }

    /** Picks the best version from a Modrinth version list. Package-private for the self test. */
    static Version pickVersion(List<Object> versions, Kind kind) {
        Map<String, Object> best = null;
        for (Object o : versions) {
            Map<String, Object> v = Json.o(o);
            if ("release".equals(v.get("version_type")) && file(v, kind) != null) { best = v; break; }
        }
        if (best == null) {
            for (Object o : versions) {
                Map<String, Object> v = Json.o(o);
                if (file(v, kind) != null) { best = v; break; }
            }
        }
        if (best == null) return null;
        Map<String, Object> f = file(best, kind);
        List<Dep> deps = new ArrayList<>();
        for (Object d : Json.a(best.get("dependencies"))) {
            Map<String, Object> m = Json.o(d);
            String pid = Json.str(m, "project_id");
            if (pid != null) deps.add(new Dep(pid, orEmpty(Json.str(m, "dependency_type"))));
        }
        return new Version(Json.str(best, "id"), Json.str(best, "project_id"), orEmpty(Json.str(best, "name")),
                Json.str(f, "filename"), Json.str(f, "url"), Json.str(Json.o(f.get("hashes")), "sha512"), deps);
    }

    private static Map<String, Object> file(Map<String, Object> version, Kind kind) {
        Map<String, Object> pick = null;
        for (Object o : Json.a(version.get("files"))) {
            Map<String, Object> f = Json.o(o);
            String n = Json.str(f, "filename");
            if (n == null || Json.str(f, "url") == null || !n.toLowerCase().endsWith(kind.ext) || n.contains("-sources") || n.contains("-javadoc")) continue;
            if (Boolean.TRUE.equals(f.get("primary"))) return f;
            if (pick == null) pick = f;
        }
        return pick;
    }

    // ------------------------------------------------------------------ install / remove
    /** What is installed from Modrinth in this folder: project id -> file name (and title). */
    static Map<String, Object> installed(Path versionDir, Kind kind) {
        Path s = versionDir.resolve(kind.folder).resolve(".nova-modrinth.json");
        try {
            if (Files.isRegularFile(s)) return Json.o(Json.parse(Files.readString(s)));
        } catch (Exception ignored) {}
        return new LinkedHashMap<>();
    }

    private static void saveInstalled(Path versionDir, Kind kind, Map<String, Object> state) throws IOException {
        Path dir = versionDir.resolve(kind.folder);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(".nova-modrinth.json"), Json.write(state));
    }

    /**
     * Installs a project (and, for mods, the other mods it needs) for this Minecraft version into the version's folder.
     * Returns the names of everything that was added. Throws when the project itself has no build for this version.
     */
    static List<String> install(Hit hit, String mc, Path versionDir, Progress p) throws Exception {
        List<String> added = new ArrayList<>();
        installOne(hit.id(), hit.title(), hit.kind(), mc, versionDir, p, added, new HashSet<>(), 0, true);
        return added;
    }

    private static void installOne(String projectId, String title, Kind kind, String mc, Path versionDir, Progress p,
                                   List<String> added, Set<String> seen, int depth, boolean required) throws Exception {
        if (!seen.add(projectId) || projectId.equals(FABRIC_API_ID) || depth > 4) return;
        Map<String, Object> state = installed(versionDir, kind);
        if (Json.o(state.get(projectId)).containsKey("file")
                && Files.isRegularFile(versionDir.resolve(kind.folder).resolve(Json.str(Json.o(state.get(projectId)), "file")))) return;
        p.status("Looking for " + title + " for Minecraft " + mc + "...");
        Version v = findVersion(projectId, kind, mc);
        if (v == null) {
            if (required) throw new IOException(title + " has no build for Minecraft " + mc + " yet");
            p.status(title + " (needed by another mod) has no build for Minecraft " + mc);
            return;
        }
        if (kind == Kind.MOD) {
            for (Dep d : v.deps()) {
                if ("required".equals(d.type())) installOne(d.projectId(), "a required mod", kind, mc, versionDir, p, added, seen, depth + 1, false);
            }
        }
        String filename = safeName(v.filename());
        Path dest = versionDir.resolve(kind.folder).resolve(filename);
        p.status("Downloading " + title + "...");
        Http.download(v.url(), dest, "SHA-512", v.sha512());
        // The Nova launcher runs each Minecraft version in its own game directory, while
        // the official launcher profile uses the shared game directory. Keep packs visible
        // to both launch paths; mods remain strictly version-specific.
        Path shared = sharedResourceDir(versionDir, kind);
        if (shared != null) {
            Path sharedFile = shared.resolve(filename);
            Files.createDirectories(shared);
            Files.copy(dest, sharedFile, StandardCopyOption.REPLACE_EXISTING);
        }
        state = installed(versionDir, kind);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("title", title);
        entry.put("file", dest.getFileName().toString());
        entry.put("version", v.name());
        state.put(projectId, entry);
        saveInstalled(versionDir, kind, state);
        added.add(title);
    }

    /** Removes a project that was installed from here. Returns false when it was not found. */
    static boolean remove(String projectId, Kind kind, Path versionDir) throws IOException {
        Map<String, Object> state = installed(versionDir, kind);
        Map<String, Object> entry = Json.o(state.get(projectId));
        String file = Json.str(entry, "file");
        if (file == null) return false;
        Path versionFile = versionDir.resolve(kind.folder).resolve(file);
        Path shared = sharedResourceDir(versionDir, kind);
        if (shared != null && Files.isRegularFile(versionFile) && Files.isRegularFile(shared.resolve(file))
                && Files.mismatch(versionFile, shared.resolve(file)) == -1) Files.deleteIfExists(shared.resolve(file));
        Files.deleteIfExists(versionFile);
        Files.deleteIfExists(versionDir.resolve(kind.folder).resolve(file + ".disabled"));
        state.remove(projectId);
        saveInstalled(versionDir, kind, state);
        return true;
    }

    /** Shared game-folder copy for packs, which are also consumed by the official launcher profile. */
    static Path sharedResourceDir(Path versionDir, Kind kind) {
        if (kind != Kind.PACK && kind != Kind.SHADER) return null;
        Path versions = versionDir.toAbsolutePath().normalize().getParent();
        if (versions == null || !"versions".equalsIgnoreCase(String.valueOf(versions.getFileName()))) return null;
        Path gameDir = versions.getParent();
        return gameDir == null ? null : gameDir.resolve(kind.folder);
    }

    /**
     * Modrinth often appends a thumbnail size and query parameters to WebP URLs. ImageIO cannot decode WebP,
     * so try the original upload extensions after normalizing the path while preserving its query string.
     */
    static List<String> iconCandidates(String url) {
        List<String> out = new ArrayList<>();
        if (url == null || url.isBlank()) return out;
        try {
            java.net.URI uri = java.net.URI.create(url);
            String path = uri.getRawPath();
            if (path == null || !path.toLowerCase(Locale.ROOT).endsWith(".webp")) { out.add(url); return out; }
            String base = path.replaceFirst("(?i)(_[0-9]+)?\\.webp$", "");
            String suffix = (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                    + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
            String prefix = url.substring(0, url.indexOf(path));
            for (String ext : new String[]{".png", ".jpg", ".jpeg", ".gif"}) out.add(prefix + base + ext + suffix);
            out.add(url);
            // Some projects only publish a WebP icon. Use a PNG-transcoding image proxy
            // as a final fallback because the launcher's standard Java runtime has no WebP reader.
            out.add("https://images.weserv.nl/?url=" + enc(uri.getHost() + path + suffix) + "&output=png");
        } catch (IllegalArgumentException e) { out.add(url); }
        return out;
    }

    static String pageUrl(Hit h) { return "https://modrinth.com/" + h.kind().type + "/" + (h.slug() != null ? h.slug() : h.id()); }

    /** A file name that cannot escape the folder. */
    static String safeName(String n) { return Path.of(n).getFileName().toString().replaceAll("[\\\\/:*?\"<>|]", "_"); }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private static String orEmpty(String s) { return s == null ? "" : s; }
}

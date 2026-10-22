package com.nova.launcher;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;

/** Run:  java -cp NovaLauncher.jar com.nova.launcher.SelfTest   (needs no internet) */
public final class SelfTest {
    static int fails;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) fails++;
    }

    public static void main(String[] a) throws Exception {
        // JSON
        Object o = Json.parse("{\"a\":[1,2.5,\"x\\n\\u0041\"],\"b\":{\"c\":true,\"d\":null},\"e\":\"q\"}");
        Map<String, Object> m = Json.o(o);
        check("json parse list", Json.a(m.get("a")).size() == 3);
        check("json string escapes", "x\nA".equals(Json.a(m.get("a")).get(2)));
        check("json nested bool", Boolean.TRUE.equals(Json.o(m.get("b")).get("c")));
        Object again = Json.parse(Json.write(m));
        check("json round trip", Json.write(m).equals(Json.write(again)));

        // versions
        check("version 0.10.0 > 0.9.9", Versions.cmp("0.10.0", "0.9.9") > 0);
        check("version v1.2 == 1.2.0", Versions.cmp("v1.2", "1.2.0") == 0);
        check("version 1.21.4 < 26.2", Versions.cmp("1.21.4", "26.2") < 0);

        // maven paths + dedupe
        check("maven path", Installer.mavenPath("net.fabricmc:fabric-loader:0.16.10")
                .equals("net/fabricmc/fabric-loader/0.16.10/fabric-loader-0.16.10.jar"));
        check("maven classifier", Installer.mavenPath("org.lwjgl:lwjgl:3.3.3:natives-windows")
                .equals("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar"));
        check("dedupe same lib diff version", Installer.dedupeKey("org/ow2/asm/asm/9.6/asm-9.6.jar")
                .equals(Installer.dedupeKey("org/ow2/asm/asm/9.7/asm-9.7.jar")));
        check("dedupe keeps natives separate", !Installer.dedupeKey("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar")
                .equals(Installer.dedupeKey("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar")));

        // rules
        String here = Platform.os();
        String other = here.equals("windows") ? "linux" : "windows";
        Map<String, Object> allowHere = Json.o(Json.parse("{\"action\":\"allow\",\"os\":{\"name\":\"" + here + "\"}}"));
        Map<String, Object> allowOther = Json.o(Json.parse("{\"action\":\"allow\",\"os\":{\"name\":\"" + other + "\"}}"));
        Map<String, Object> feat = Json.o(Json.parse("{\"action\":\"allow\",\"features\":{\"is_demo_user\":true}}"));
        check("rule allows this os", Rules.allowed(List.of(allowHere)));
        check("rule skips other os", !Rules.allowed(List.of(allowOther)));
        check("rule skips features", !Rules.allowed(List.of(feat)));
        check("empty rules allow", Rules.allowed(List.of()));

        List<Object> raw = Json.a(Json.parse("[\"--username\",\"${auth_player_name}\",{\"rules\":[{\"action\":\"allow\",\"features\":{\"is_demo_user\":true}}],\"value\":\"--demo\"},"
                + "{\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"" + here + "\"}}],\"value\":[\"-Dhere=1\",\"-Dtwo=2\"]}]"));
        List<String> res = Rules.resolveArgs(raw);
        check("args drop demo flag", !res.contains("--demo"));
        check("args keep os value list", res.contains("-Dhere=1") && res.contains("-Dtwo=2"));

        // command line
        Installer.Plan plan = new Installer.Plan("net.fabricmc.loader.impl.launch.knot.KnotClient",
                List.of(Path.of("/g/a.jar"), Path.of("/g/b.jar")),
                List.of("-Djava.library.path=${natives_directory}", "-cp", "${classpath}"),
                List.of("--username", "${auth_player_name}", "--uuid", "${auth_uuid}", "--accessToken", "${auth_access_token}"),
                "19", Path.of("/n/assets"), "1.21.4", Path.of("/n/versions/1.21.4/natives"), 21);
        List<String> cmd = GameLauncher.command(plan, Account.offline("Steve"), Path.of("/usr/bin/java"), Path.of("/n/instances/default"), 3072, List.of("-Dx=1"));
        String joined = String.join(" ", cmd);
        check("cmd has main class after jvm args", cmd.indexOf(plan.mainClass()) > cmd.indexOf("-cp"));
        check("cmd substitutes name", cmd.contains("Steve"));
        check("cmd substitutes classpath", joined.contains("/g/a.jar" + java.io.File.pathSeparator + "/g/b.jar"));
        check("cmd has no leftover placeholders", !joined.contains("${"));
        check("cmd sets memory", cmd.contains("-Xmx3072M"));

        // skin renderer: paint distinct colours into each skin region
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        fill(skin, 8, 8, 8, 8, 0xFFFF0000);      // head
        fill(skin, 20, 20, 8, 12, 0xFF00FF00);   // body
        fill(skin, 44, 20, 4, 12, 0xFF0000FF);   // right arm
        fill(skin, 36, 52, 4, 12, 0xFFFFFF00);   // left arm
        fill(skin, 4, 20, 4, 12, 0xFFFF00FF);    // right leg
        fill(skin, 20, 52, 4, 12, 0xFF00FFFF);   // left leg
        fill(skin, 40, 8, 8, 8, 0x80FFFFFF);     // hat: half transparent -> drawn
        int[] px = SkinRenderer.front(skin, false);
        check("skin head pixel (hat on top)", (px[2 * 16 + 8] & 0xFFFFFF) == 0xFFFFFF);
        check("skin body pixel", px[12 * 16 + 8] == 0xFF00FF00);
        check("skin right arm pixel", px[12 * 16 + 1] == 0xFF0000FF);
        check("skin left arm pixel", px[12 * 16 + 14] == 0xFFFFFF00);
        check("skin right leg pixel", px[26 * 16 + 5] == 0xFFFF00FF);
        check("skin left leg pixel", px[26 * 16 + 10] == 0xFF00FFFF);
        int[] slim = SkinRenderer.front(skin, true);
        check("slim arm is 3 wide (col 0 empty)", slim[12 * 16] == 0);

        // official launcher profile editing
        Path tmp = java.nio.file.Files.createTempDirectory("novatest");
        Path prof = tmp.resolve("launcher_profiles.json");
        java.nio.file.Files.writeString(prof, "{\"profiles\":{\"abc\":{\"name\":\"Other\",\"lastVersionId\":\"1.20\"}},\"version\":3,\"settings\":{\"x\":true}}");
        OfficialLauncher.addProfile(prof, "fabric-loader-0.16.10-1.21.4", "Nova Client", Path.of("/g/inst"), 4096, "Furnace");
        OfficialLauncher.addProfile(prof, "fabric-loader-0.16.10-1.21.4", "Nova Client", Path.of("/g/inst"), 4096, "Furnace");
        Map<String, Object> pr = Json.o(Json.parse(java.nio.file.Files.readString(prof)));
        Map<String, Object> plist = Json.o(pr.get("profiles"));
        check("profile: existing profile kept", plist.containsKey("abc") && Json.num(pr, "version") == 3);
        check("profile: nova added once", plist.size() == 2 && plist.containsKey("nova-client"));
        check("profile: points at fabric + game dir", "fabric-loader-0.16.10-1.21.4".equals(Json.str(Json.o(plist.get("nova-client")), "lastVersionId"))
                && Json.str(Json.o(plist.get("nova-client")), "gameDir").endsWith("inst"));
        check("profile: backup written", java.nio.file.Files.exists(tmp.resolve("launcher_profiles.json.nova-backup")));
        String ic = OfficialLauncher.icon();
        check("icon is a data URI or a built-in name", ic.startsWith("data:image/png;base64,") || ic.equals("Furnace"));

        // built-in mod install
        Path mods = tmp.resolve("mods");
        java.nio.file.Files.createDirectories(mods);
        java.nio.file.Files.writeString(mods.resolve("novaclient-0.1.0.jar"), "old");
        java.nio.file.Files.writeString(mods.resolve("fabric-api-1.jar"), "api");
        NovaUpdater.installJar(new java.io.ByteArrayInputStream("NEW".getBytes()), mods, "0.4.0");
        check("mod install: new jar written", "NEW".equals(java.nio.file.Files.readString(mods.resolve("novaclient-0.4.0.jar"))));
        check("mod install: old nova jar removed", !java.nio.file.Files.exists(mods.resolve("novaclient-0.1.0.jar")));
        check("mod install: fabric api untouched", java.nio.file.Files.exists(mods.resolve("fabric-api-1.jar")));

        // ---- multi-version support ----
        check("version suffix ignored (0.7.0-mc1.21.4 > 0.6.0-mc1.21.4)", Versions.cmp("0.7.0-mc1.21.4", "0.6.0-mc1.21.4") > 0);
        check("version suffix ignored (same base)", Versions.cmp("0.7.0-mc1.20.1", "0.7.0+build.9") == 0);

        Map<String, Object> mojang = Json.o(Json.parse("{\"versions\":[{\"id\":\"25w01a\",\"type\":\"snapshot\"},{\"id\":\"1.21.4\",\"type\":\"release\"},"
                + "{\"id\":\"1.14.4\",\"type\":\"release\"},{\"id\":\"1.13.2\",\"type\":\"release\"}]}"));
        List<Object> fab = Json.a(Json.parse("[{\"version\":\"1.21.4\",\"stable\":true},{\"version\":\"1.14.4\",\"stable\":true},"
                + "{\"version\":\"25w01a\",\"stable\":false}]"));
        List<String> vl = VersionList.merge(mojang, fab);
        check("version list: releases fabric supports, newest first", vl.equals(List.of("1.21.4", "1.14.4")));

        NovaManifest nm = NovaManifest.parse(Json.o(Json.parse("{\"version\":\"0.7.0\",\"minecraft\":\"1.21.4\",\"builds\":{"
                + "\"1.21.4\":{\"url\":\"u1\",\"sha256\":\"a\",\"tested\":true},\"1.20.1\":{\"url\":\"u2\",\"sha256\":\"b\",\"tested\":false}},"
                + "\"launcher\":{\"version\":\"1.0.0\",\"url\":\"lu\"}}")));
        check("manifest: two builds", nm.builds().size() == 2 && nm.builds().get("1.20.1").url().equals("u2"));
        check("manifest: tested flag", nm.builds().get("1.21.4").tested() && !nm.builds().get("1.20.1").tested());
        NovaManifest old = NovaManifest.parse(Json.o(Json.parse("{\"version\":\"0.6.0\",\"minecraft\":\"1.21.4\",\"mod\":{\"url\":\"ou\",\"sha256\":\"c\"}}")));
        check("manifest: old single-mod format still works", old.builds().size() == 1 && old.builds().get("1.21.4").url().equals("ou"));
        check("picker tags", VersionDialog.tagFor("1.21.4", nm.builds(), null).equals("Nova")
                && VersionDialog.tagFor("1.20.1", nm.builds(), null).contains("experimental")
                && VersionDialog.tagFor("1.14.4", nm.builds(), null).equals("Fabric only")
                && VersionDialog.tagFor("1.14.4", nm.builds(), "1.14.4").contains("built in"));
        check("auto version: manifest wins", NovaUpdater.defaultMc(nm, null).equals("1.21.4"));
        check("auto version: fallback", NovaUpdater.defaultMc(null, null).equals(NovaUpdater.DEFAULT_MC));

        // old-style natives (Minecraft 1.14 - 1.18)
        Map<String, Object> nlib = Json.o(Json.parse("{\"natives\":{\"windows\":\"natives-windows-${arch}\",\"linux\":\"natives-linux\",\"osx\":\"natives-macos\"}}"));
        String cls = Installer.nativeClassifier(nlib);
        check("natives classifier resolved for this OS", cls != null && cls.startsWith("natives-") && !cls.contains("${"));
        check("library without natives has no classifier", Installer.nativeClassifier(Json.o(Json.parse("{}"))) == null);
        Path nz = tmp.resolve("natives-test.jar");
        try (java.util.zip.ZipOutputStream zo = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(nz))) {
            for (String n : new String[]{"lwjgl.dll", "META-INF/MANIFEST.MF", "../evil.txt"}) { zo.putNextEntry(new java.util.zip.ZipEntry(n)); zo.write("x".getBytes()); zo.closeEntry(); }
        }
        Path ndir = tmp.resolve("natives-out");
        Installer.extractNatives(nz, ndir, List.of("META-INF/"));
        check("natives: library unpacked", java.nio.file.Files.exists(ndir.resolve("lwjgl.dll")));
        check("natives: excluded entries skipped", !java.nio.file.Files.exists(ndir.resolve("META-INF/MANIFEST.MF")));
        check("natives: path traversal blocked", !java.nio.file.Files.exists(tmp.resolve("evil.txt")));

        // picking the Nova jar per Minecraft version (local server, no internet needed)
        byte[] jarBytes = "NOVA-JAR-FOR-1.21.4".getBytes();
        String sha = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(jarBytes));
        com.sun.net.httpserver.HttpServer srv = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/nova.jar", ex -> { ex.sendResponseHeaders(200, jarBytes.length); ex.getResponseBody().write(jarBytes); ex.close(); });
        srv.start();
        String url = "http://127.0.0.1:" + srv.getAddress().getPort() + "/nova.jar";
        NovaManifest served = new NovaManifest("0.7.0", "1.21.4", Map.of("1.21.4", new NovaManifest.Build(url, sha, true),
                "1.20.1", new NovaManifest.Build(url, sha, false)), null, null);
        Path mods2 = tmp.resolve("mods2");
        java.nio.file.Files.createDirectories(mods2);
        java.nio.file.Files.writeString(mods2.resolve("novaclient-0.1.0-mc1.19.4.jar"), "stale");
        Progress quiet = new Progress() { public void status(String m) {} public void bar(int d, int t) {} };
        LauncherConfig lc = new LauncherConfig();
        NovaUpdater.Pick p1 = NovaUpdater.installNova(lc, served, null, "1.21.4", mods2, quiet);
        check("pick: build downloaded for chosen version", p1.loaded() && java.nio.file.Files.exists(mods2.resolve("novaclient-0.7.0-mc1.21.4.jar")));
        check("pick: jar of another version removed", !java.nio.file.Files.exists(mods2.resolve("novaclient-0.1.0-mc1.19.4.jar")));
        NovaUpdater.Pick p2 = NovaUpdater.installNova(lc, served, null, "1.14.4", mods2, quiet);
        check("pick: version without a build runs plain Fabric", !p2.loaded() && !java.nio.file.Files.exists(mods2.resolve("novaclient-0.7.0-mc1.21.4.jar")));
        NovaUpdater.Pick p3 = NovaUpdater.installNova(lc, served, null, "1.20.1", mods2, quiet);
        check("pick: experimental build is flagged", p3.loaded() && p3.novaVersion().contains("experimental"));
        srv.stop(0);
        NovaUpdater.Pick p4 = NovaUpdater.installNova(lc, served, null, "1.20.1", mods2, quiet);
        check("pick: works offline from the cached jar", p4.loaded() && java.nio.file.Files.exists(mods2.resolve("novaclient-0.7.0-mc1.20.1.jar")));
        lc.useNova = false;
        NovaUpdater.Pick p5 = NovaUpdater.installNova(lc, served, null, "1.20.1", mods2, quiet);
        check("pick: Nova switched off removes the jar", !p5.loaded() && !java.nio.file.Files.exists(mods2.resolve("novaclient-0.7.0-mc1.20.1.jar")));
        java.nio.file.Files.writeString(mods2.resolve("fabric-api-0.1+1.19.jar"), "x");
        java.nio.file.Files.writeString(mods2.resolve("fabric-api-0.2+1.20.1.jar"), "x");
        NovaUpdater.deleteNotContaining(mods2, "fabric-api-", "1.20.1");
        check("stale Fabric API removed, matching one kept", !java.nio.file.Files.exists(mods2.resolve("fabric-api-0.1+1.19.jar"))
                && java.nio.file.Files.exists(mods2.resolve("fabric-api-0.2+1.20.1.jar")));


        // a jar you built yourself must survive
        Path mods3 = tmp.resolve("mods3");
        java.nio.file.Files.createDirectories(mods3);
        java.nio.file.Files.writeString(mods3.resolve("novaclient-0.6.0.jar"), "mine");
        LauncherConfig lc3 = new LauncherConfig();
        NovaUpdater.Pick m1 = NovaUpdater.installNova(lc3, null, null, "1.21.4", mods3, quiet);
        check("own jar: kept and loaded when no manifest/built-in", m1.loaded() && java.nio.file.Files.exists(mods3.resolve("novaclient-0.6.0.jar")));
        lc3.useNova = false;
        NovaUpdater.Pick m2 = NovaUpdater.installNova(lc3, null, null, "1.21.4", mods3, quiet);
        check("own jar: switched off only renames it", !m2.loaded() && java.nio.file.Files.exists(mods3.resolve("novaclient-0.6.0.jar.disabled")));
        lc3.useNova = true;
        NovaUpdater.Pick m3 = NovaUpdater.installNova(lc3, null, null, "1.21.4", mods3, quiet);
        check("own jar: switched back on restores it", m3.loaded() && java.nio.file.Files.exists(mods3.resolve("novaclient-0.6.0.jar")));


        // ---- mod compatibility (the "Incompatible mods found" window) ----
        check("range: exact match", JarInfo.supports(List.of("1.21.4"), "1.21.4"));
        check("range: exact mismatch (Nova on 26.3)", !JarInfo.supports(List.of("1.21.4"), "26.3"));
        check("range: 1.21.x (Iris)", JarInfo.supports(List.of("1.21.x"), "1.21.4") && !JarInfo.supports(List.of("1.21.x"), "26.3"));
        check("range: tilde", JarInfo.supports(List.of("~1.21.4"), "1.21.4") && !JarInfo.supports(List.of("~1.21.4"), "1.22"));
        check("range: caret", JarInfo.supports(List.of("^1.21"), "1.21.9") && !JarInfo.supports(List.of("^1.21"), "2.0"));
        check("range: and / comparators", JarInfo.supports(List.of(">=1.20 <1.21.2"), "1.21.1") && !JarInfo.supports(List.of(">=1.20 <1.21.2"), "1.21.4"));
        check("range: or via || and via list", JarInfo.supports(List.of("1.20.1 || 1.21.4"), "1.21.4") && JarInfo.supports(List.of("1.19", "26.3"), "26.3"));
        check("range: star / empty / unknown version = ok", JarInfo.supports(List.of("*"), "26.3") && JarInfo.supports(List.of(), "26.3") && JarInfo.supports(List.of("1.21.4"), "25w01a"));
        check("range: garbage never disables", JarInfo.supports(List.of("~abc"), "1.21.4"));

        Path mods4 = tmp.resolve("mods4");
        java.nio.file.Files.createDirectories(mods4);
        fakeMod(mods4.resolve("novaclient-0.6.0.jar"), "novaclient", "Nova Client", "1.21.4");
        fakeMod(mods4.resolve("sodium.jar"), "sodium", "Sodium", "1.21.4");
        fakeMod(mods4.resolve("anyver.jar"), "anyver", "Any Version Mod", "*");
        JarInfo ji = JarInfo.read(mods4.resolve("novaclient-0.6.0.jar"));
        check("jar info: name + required Minecraft", ji != null && ji.name().equals("Nova Client") && "1.21.4".equals(ji.mcHint()));
        check("jar info: not a fabric mod gives null", JarInfo.read(nz) == null);

        // exactly your case: a 1.21.4 jar built by hand, Minecraft 26.3 picked
        LauncherConfig mine = new LauncherConfig();
        mine.mcVersion = "26.3";
        check("auto-pick: your pick always wins", NovaUpdater.chooseMc(mine, null, null, mods4).equals("26.3"));
        List<String> off = ModScan.disableIncompatible(mods4, "26.3");
        check("scan: Nova and Sodium switched off on 26.3", off.size() == 2
                && java.nio.file.Files.exists(mods4.resolve("novaclient-0.6.0.jar.disabled"))
                && java.nio.file.Files.exists(mods4.resolve("sodium.jar.disabled")));
        check("scan: mod for any version stays on", java.nio.file.Files.exists(mods4.resolve("anyver.jar")));
        check("scan: Nova no longer enabled", !ModScan.novaEnabled(mods4));

        Path mods5 = tmp.resolve("mods5");
        java.nio.file.Files.createDirectories(mods5);
        fakeMod(mods5.resolve("novaclient-0.6.0.jar"), "novaclient", "Nova Client", "1.21.4");
        check("scan: nothing switched off on the right version", ModScan.disableIncompatible(mods5, "1.21.4").isEmpty() && ModScan.novaEnabled(mods5));
        LauncherConfig auto = new LauncherConfig();
        check("auto-pick: follows the jar you built when nothing else says", NovaUpdater.chooseMc(auto, null, null, mods5).equals("1.21.4"));
        Path localJar = tmp.resolve("local-built.jar");
        fakeMod(localJar, "novaclient", "Nova Client", "1.21.4");
        auto.localModJar = localJar.toString();
        check("auto-pick: Local Nova jar decides the version", NovaUpdater.chooseMc(auto, null, null, tmp.resolve("empty")).equals("1.21.4"));

        // optional mods (Sodium / Iris / Freecam)
        check("optional: parse ignores unknown names", OptionalMods.parse("iris, bogus ,freecam").toString().equals("[iris, freecam]"));
        check("optional: iris pulls sodium in first", OptionalMods.withRequirements(OptionalMods.parse("iris")).toString().equals("[sodium, iris]"));
        check("optional: nothing ticked, nothing installed", OptionalMods.withRequirements(OptionalMods.parse("")).isEmpty());
        Object mr = Json.parse("[{\"version_type\":\"beta\",\"files\":[{\"filename\":\"a-beta.jar\",\"url\":\"u1\",\"primary\":true,\"hashes\":{\"sha1\":\"h1\"}}]},"
                + "{\"version_type\":\"release\",\"files\":[{\"filename\":\"a-src-sources.jar\",\"url\":\"x\"},{\"filename\":\"a-1.0.jar\",\"url\":\"u2\",\"primary\":true,\"hashes\":{\"sha1\":\"h2\"}}]}]");
        OptionalMods.Found pk = OptionalMods.pick(Json.a(mr));
        check("optional: prefers the newest release file", pk != null && pk.filename().equals("a-1.0.jar") && pk.url().equals("u2") && pk.sha1().equals("h2"));
        check("optional: no versions means null", OptionalMods.pick(new ArrayList<>()) == null);

        System.out.println("info: built-in mod = " + (Embedded.load() == null ? "none (dev build)" : Embedded.load().version));

        System.out.println(fails == 0 ? "\nALL TESTS PASSED" : "\n" + fails + " TEST(S) FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }

    private static void fakeMod(Path jar, String id, String name, String mc) throws Exception {
        try (java.util.zip.ZipOutputStream zo = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(jar))) {
            zo.putNextEntry(new java.util.zip.ZipEntry("fabric.mod.json"));
            zo.write(("{\"schemaVersion\":1,\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"version\":\"1.0\",\"depends\":{\"minecraft\":\"" + mc + "\"}}").getBytes());
            zo.closeEntry();
        }
    }

    private static void fill(BufferedImage i, int x, int y, int w, int h, int argb) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) i.setRGB(xx, yy, argb);
    }
}

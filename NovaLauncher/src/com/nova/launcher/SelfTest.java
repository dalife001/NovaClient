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
        System.out.println("info: built-in mod = " + (Embedded.load() == null ? "none (dev build)" : Embedded.load().version));

        System.out.println(fails == 0 ? "\nALL TESTS PASSED" : "\n" + fails + " TEST(S) FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }

    private static void fill(BufferedImage i, int x, int y, int w, int h, int argb) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) i.setRGB(xx, yy, argb);
    }
}

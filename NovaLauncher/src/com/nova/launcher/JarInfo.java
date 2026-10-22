package com.nova.launcher;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** What a Fabric mod jar says about itself (its fabric.mod.json), and whether it fits a Minecraft version. */
record JarInfo(String id, String name, String version, List<String> minecraft) {

    private static final Pattern PLAIN = Pattern.compile("\\d+(\\.\\d+)*");

    /** Reads a .jar or .jar.disabled. Returns null if it is not a readable Fabric mod. */
    static JarInfo read(Path file) {
        try (ZipFile z = new ZipFile(file.toFile())) {
            ZipEntry e = z.getEntry("fabric.mod.json");
            if (e == null) return null;
            Map<String, Object> m = Json.o(Json.parse(new String(z.getInputStream(e).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
            List<String> mc = new ArrayList<>();
            Object dep = Json.o(m.get("depends")).get("minecraft");
            if (dep instanceof String s) mc.add(s);
            else if (dep instanceof List<?> l) for (Object x : l) mc.add(String.valueOf(x));
            String id = Json.str(m, "id");
            return new JarInfo(id, Json.str(m, "name") != null ? Json.str(m, "name") : id, Json.str(m, "version"), mc);
        } catch (Exception ex) {
            return null;
        }
    }

    /** The one Minecraft version this jar was clearly built for (like 1.21.4), or null if it is a range we cannot name. */
    String mcHint() {
        if (minecraft.isEmpty()) return null;
        String t = minecraft.get(0).trim().split("[\\s|]+")[0].replaceFirst("^[~^>=<]+", "");
        return PLAIN.matcher(t).matches() ? t : null;
    }

    /** Human text for the Minecraft requirement. */
    String mcLabel() {
        return minecraft.isEmpty() ? "any" : String.join(" or ", minecraft).replace("||", "or");
    }

    boolean supports(String mc) { return supports(minecraft, mc); }

    /**
     * Fabric's version ranges, simplified: "1.21.4", "~1.21", "^1.21", ">=1.20 <1.21.2", "1.21.x", "*", with "||" or a list meaning "or".
     * Anything we cannot read counts as compatible, so we never switch off a mod by mistake.
     */
    static boolean supports(List<String> ranges, String mc) {
        if (ranges == null || ranges.isEmpty() || mc == null || !PLAIN.matcher(mc).matches()) return true;
        for (String r : ranges)
            for (String alt : r.split("\\|\\|")) if (all(alt.trim(), mc)) return true;
        return false;
    }

    private static boolean all(String alt, String mc) {
        if (alt.isEmpty() || alt.equals("*")) return true;
        for (String t : alt.split("\\s+")) if (!one(t, mc)) return false;
        return true;
    }

    private static boolean one(String t, String mc) {
        try {
            if (t.equals("*")) return true;
            if (t.startsWith(">=")) return Versions.cmp(mc, t.substring(2)) >= 0;
            if (t.startsWith("<=")) return Versions.cmp(mc, t.substring(2)) <= 0;
            if (t.startsWith(">")) return Versions.cmp(mc, t.substring(1)) > 0;
            if (t.startsWith("<")) return Versions.cmp(mc, t.substring(1)) < 0;
            if (t.startsWith("=")) return Versions.cmp(mc, t.substring(1)) == 0;
            if (t.startsWith("~") || t.startsWith("^")) {
                String v = t.substring(1);
                String[] p = v.split("\\.");
                int bump = t.startsWith("^") || p.length < 2 ? 0 : 1;      // ~1.21.4 -> <1.22, ^1.21 -> <2, ~1 -> <2
                StringBuilder up = new StringBuilder();
                for (int i = 0; i < bump; i++) up.append(p[i]).append('.');
                up.append(Integer.parseInt(p[bump]) + 1);
                return Versions.cmp(mc, v) >= 0 && Versions.cmp(mc, up.toString()) < 0;
            }
            if (t.matches("[0-9.]*[xX*]([.][xX*])*")) {                    // 1.21.x
                String[] want = t.split("\\.");
                String[] have = mc.split("\\.");
                for (int i = 0; i < want.length; i++) {
                    if (want[i].equalsIgnoreCase("x") || want[i].equals("*")) return true;
                    if (i >= have.length || !want[i].equals(have[i])) return false;
                }
                return true;
            }
            return Versions.cmp(mc, t) == 0;
        } catch (RuntimeException e) {
            return true;
        }
    }
}

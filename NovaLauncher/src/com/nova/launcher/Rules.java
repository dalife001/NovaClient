package com.nova.launcher;

import java.util.*;
import java.util.regex.Pattern;

/** Mojang's "rules" format (which libraries / arguments apply to this computer). */
final class Rules {
    private Rules() {}

    static boolean allowed(List<Object> rules) {
        if (rules == null || rules.isEmpty()) return true;
        boolean allowed = false;
        for (Object r : rules) {
            Map<String, Object> m = Json.o(r);
            boolean applies = true;
            if (m.get("os") != null) applies = osMatches(Json.o(m.get("os")));
            if (m.get("features") != null) applies = false; // we enable no optional features (demo, custom size...)
            if (applies) allowed = "allow".equals(m.get("action"));
        }
        return allowed;
    }

    static boolean osMatches(Map<String, Object> os) {
        String name = Json.str(os, "name");
        if (name != null) {
            String base = name, arch = null;
            int dash = name.indexOf('-');
            if (dash > 0) { base = name.substring(0, dash); arch = name.substring(dash + 1); }
            if (!base.equals(Platform.os())) return false;
            if (arch != null && !archMatches(arch)) return false;
        }
        String arch = Json.str(os, "arch");
        if (arch != null && !archMatches(arch)) return false;
        String ver = Json.str(os, "version");
        if (ver != null && !Pattern.compile(ver).matcher(System.getProperty("os.version", "")).find()) return false;
        return true;
    }

    private static boolean archMatches(String a) {
        switch (a) {
            case "arm64": return Platform.arm64();
            case "x86": return Platform.is32bit();
            case "x86_64": case "x64": return !Platform.arm64() && !Platform.is32bit();
            default: return true;
        }
    }

    static List<String> resolveArgs(List<Object> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (Object a : raw) {
            if (a instanceof String s) { out.add(s); continue; }
            Map<String, Object> m = Json.o(a);
            if (!allowed(Json.a(m.get("rules")))) continue;
            Object v = m.get("value");
            if (v instanceof String s) out.add(s);
            else if (v instanceof List<?> l) for (Object x : l) out.add(String.valueOf(x));
        }
        return out;
    }
}

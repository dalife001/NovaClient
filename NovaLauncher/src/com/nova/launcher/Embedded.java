package com.nova.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Bundled Nova client jars indexed by Minecraft version. */
final class Embedded {
    final String version;
    final String minecraft;
    private final Map<String, String> jars;
    private final Map<String, String> versions;

    private Embedded(String version, String minecraft, Map<String, String> jars, Map<String, String> versions) {
        this.version = version;
        this.minecraft = minecraft;
        this.jars = jars;
        this.versions = versions;
    }

    static Embedded load() {
        try (InputStream in = Embedded.class.getResourceAsStream("/embedded/embedded.properties")) {
            if (in == null) return null;
            Properties p = new Properties();
            p.load(in);
            String version = p.getProperty("version", "0").trim();
            String minecraft = p.getProperty("minecraft", NovaUpdater.DEFAULT_MC).trim();
            Map<String, String> jars = new LinkedHashMap<>();
            Map<String, String> versions = new LinkedHashMap<>();
            for (String key : p.stringPropertyNames()) {
                if (key.startsWith("jar.")) {
                    String mc = key.substring(4).trim();
                    String resource = p.getProperty(key, "").trim();
                    if (!mc.isEmpty() && !resource.isEmpty() && Embedded.class.getResource(resource) != null) {
                        jars.put(mc, resource);
                        versions.put(mc, p.getProperty("version." + mc, version).trim());
                    }
                }
            }
            if (jars.isEmpty() && Embedded.class.getResource("/embedded/novaclient.jar") != null) {
                jars.put(minecraft, "/embedded/novaclient.jar");
                versions.put(minecraft, version);
            }
            return jars.isEmpty() ? null : new Embedded(version, minecraft, jars, versions);
        } catch (IOException e) { return null; }
    }

    boolean supports(String mc) { return jars.containsKey(mc); }

    java.util.Set<String> minecraftVersions() { return java.util.Set.copyOf(jars.keySet()); }

    String version(String mc) { return versions.getOrDefault(mc, version); }

    InputStream jar() { return jar(minecraft); }

    InputStream jar(String mc) {
        String path = jars.get(mc);
        return path == null ? null : Embedded.class.getResourceAsStream(path);
    }
}

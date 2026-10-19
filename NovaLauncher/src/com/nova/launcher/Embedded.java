package com.nova.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** The Nova Client mod jar that build-everything packs inside this launcher (absent in a plain dev build). */
final class Embedded {
    final String version;
    final String minecraft;

    private Embedded(String version, String minecraft) { this.version = version; this.minecraft = minecraft; }

    static Embedded load() {
        try (InputStream in = Embedded.class.getResourceAsStream("/embedded/embedded.properties")) {
            if (in == null || Embedded.class.getResource("/embedded/novaclient.jar") == null) return null;
            Properties p = new Properties();
            p.load(in);
            return new Embedded(p.getProperty("version", "0").trim(), p.getProperty("minecraft", NovaUpdater.DEFAULT_MC).trim());
        } catch (IOException e) {
            return null;
        }
    }

    InputStream jar() { return Embedded.class.getResourceAsStream("/embedded/novaclient.jar"); }
}

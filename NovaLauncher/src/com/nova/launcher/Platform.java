package com.nova.launcher;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;

final class Platform {
    private Platform() {}

    static String os() {
        String n = System.getProperty("os.name", "").toLowerCase();
        if (n.contains("win")) return "windows";
        if (n.contains("mac") || n.contains("darwin")) return "osx";
        return "linux";
    }

    static boolean arm64() {
        String a = System.getProperty("os.arch", "").toLowerCase();
        return a.contains("aarch64") || a.contains("arm64");
    }

    static boolean is32bit() {
        String a = System.getProperty("os.arch", "").toLowerCase();
        return a.equals("x86") || a.equals("i386") || a.equals("i686");
    }

    static String exe() { return os().equals("windows") ? "java.exe" : "java"; }

    /** Where the launcher keeps everything (game files, config, runtimes). */
    static Path home() {
        String user = System.getProperty("user.home", ".");
        switch (os()) {
            case "windows": {
                String ad = System.getenv("APPDATA");
                return Path.of(ad != null ? ad : user, ".novaclient");
            }
            case "osx": return Path.of(user, "Library", "Application Support", "novaclient");
            default: return Path.of(user, ".novaclient");
        }
    }

    static void open(String url) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignored) {}
    }

    static void openFolder(Path p) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(p.toFile());
        } catch (Exception ignored) {}
    }
}

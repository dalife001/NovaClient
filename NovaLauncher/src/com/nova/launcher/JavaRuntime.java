package com.nova.launcher;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

/** Picks (or downloads) a Java runtime new enough for the Minecraft version. */
final class JavaRuntime {
    private JavaRuntime() {}

    static Path ensure(int major, Path root, String override, Progress p) throws Exception {
        if (override != null && !override.isBlank()) return Path.of(override);
        if (Runtime.version().feature() >= major) return Path.of(System.getProperty("java.home"), "bin", Platform.exe());

        Path dir = root.resolve("runtime").resolve(String.valueOf(major));
        Path found = find(dir);
        if (found != null) return found;

        p.status("Downloading Java " + major + "...");
        String os = Platform.os().equals("osx") ? "mac" : Platform.os();
        String arch = Platform.arm64() ? "aarch64" : "x64";
        String url = "https://api.adoptium.net/v3/binary/latest/" + major + "/ga/" + os + "/" + arch + "/jre/hotspot/normal/eclipse";
        Path archive = root.resolve("runtime").resolve("jre-" + major + (Platform.os().equals("windows") ? ".zip" : ".tar.gz"));
        Http.download(url, archive, null, null);

        Files.createDirectories(dir);
        p.status("Unpacking Java " + major + "...");
        Process pr = new ProcessBuilder("tar", "-xf", archive.toString(), "-C", dir.toString()).redirectErrorStream(true).start();
        pr.getInputStream().readAllBytes();
        if (pr.waitFor() != 0) throw new IOException("Could not unpack Java (is 'tar' available?)");
        Files.deleteIfExists(archive);
        found = find(dir);
        if (found == null) throw new IOException("Downloaded Java but could not find it");
        return found;
    }

    private static Path find(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return null;
        try (Stream<Path> s = Files.walk(dir, 7)) {
            return s.filter(f -> f.getFileName().toString().equals(Platform.exe()) && f.getParent().getFileName().toString().equals("bin"))
                    .findFirst().orElse(null);
        }
    }
}

package com.nova.launcher;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Looks at the mods folder before the game starts. */
final class ModScan {
    private ModScan() {}

    /**
     * Switches off (renames to .jar.disabled) every mod that says it needs a different Minecraft version.
     * Fabric would refuse to start the whole game because of one of them (the "Incompatible mods found" window).
     * Returns a line per mod that was switched off.
     */
    static List<String> disableIncompatible(Path mods, String mc) throws IOException {
        List<String> off = new ArrayList<>();
        if (!Files.isDirectory(mods)) return off;
        List<Path> jars = new ArrayList<>();
        try (Stream<Path> s = Files.list(mods)) { s.filter(f -> f.getFileName().toString().endsWith(".jar")).forEach(jars::add); }
        for (Path f : jars) {
            JarInfo info = JarInfo.read(f);
            if (info == null || info.supports(mc)) continue;
            Files.move(f, f.resolveSibling(f.getFileName() + ".disabled"), StandardCopyOption.REPLACE_EXISTING);
            off.add(info.name() + " switched off: it needs Minecraft " + info.mcLabel() + ", you are on " + mc);
        }
        return off;
    }

    /** Any Nova jar that is switched on right now. */
    static boolean novaEnabled(Path mods) throws IOException {
        try (Stream<Path> s = Files.list(mods)) {
            return s.anyMatch(f -> { String n = f.getFileName().toString(); return n.startsWith("novaclient-") && n.endsWith(".jar"); });
        }
    }
}

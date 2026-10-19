package com.nova.launcher;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Installs Nova Client into the official Minecraft Launcher as a "Nova Client" profile
 * (Fabric + Nova + Fabric API, using your Nova game folder). The official launcher then
 * handles your Microsoft login, so no Azure app is needed for that route.
 */
final class OfficialLauncher {
    static final String PROFILE_KEY = "nova-client";

    private OfficialLauncher() {}

    static Path minecraftDir() {
        String user = System.getProperty("user.home", ".");
        switch (Platform.os()) {
            case "windows": {
                String ad = System.getenv("APPDATA");
                return Path.of(ad != null ? ad : user, ".minecraft");
            }
            case "osx": return Path.of(user, "Library", "Application Support", "minecraft");
            default: return Path.of(user, ".minecraft");
        }
    }

    static String install(LauncherConfig cfg, Path home, Path gameDir, Progress p) throws Exception {
        Path mc = minecraftDir();
        Path profiles = mc.resolve("launcher_profiles.json");
        if (!Files.isRegularFile(profiles))
            throw new IOException("Open the official Minecraft Launcher once first (it creates its files), then try again.");

        NovaUpdater.Result r = NovaUpdater.sync(cfg, gameDir, p);
        p.status("Adding Fabric to the Minecraft Launcher...");
        Map<String, Object> fabric = new Installer(home, p).fabricProfile(r.mcVersion());
        String id = Json.str(fabric, "id");
        if (id == null) throw new IOException("Fabric did not return a version id");

        Path vdir = mc.resolve("versions").resolve(id);
        Files.createDirectories(vdir);
        Files.writeString(vdir.resolve(id + ".json"), Json.write(fabric));

        addProfile(profiles, id, "Nova Client", gameDir, cfg.ramMb, icon());
        return "Added the \"Nova Client\" profile (" + id + ") to the Minecraft Launcher.";
    }

    /** Adds/updates the Nova profile in launcher_profiles.json (keeps a one-time backup). */
    static void addProfile(Path profilesFile, String versionId, String name, Path gameDir, int ramMb, String icon) throws IOException {
        String text = Files.readString(profilesFile);
        Map<String, Object> root = Json.o(Json.parse(text));
        Path backup = profilesFile.resolveSibling(profilesFile.getFileName() + ".nova-backup");
        if (!Files.exists(backup)) Files.writeString(backup, text);

        Map<String, Object> profiles;
        if (root.get("profiles") instanceof Map) profiles = Json.o(root.get("profiles"));
        else { profiles = new LinkedHashMap<>(); root.put("profiles", profiles); }

        String now = Instant.now().toString();
        String created = now;
        if (profiles.get(PROFILE_KEY) instanceof Map) {
            String old = Json.str(Json.o(profiles.get(PROFILE_KEY)), "created");
            if (old != null) created = old;
        }
        Map<String, Object> prof = new LinkedHashMap<>();
        prof.put("name", name);
        prof.put("type", "custom");
        prof.put("created", created);
        prof.put("lastUsed", now);
        prof.put("icon", icon);
        prof.put("lastVersionId", versionId);
        prof.put("gameDir", gameDir.toString());
        prof.put("javaArgs", "-Xmx" + ramMb + "M");
        profiles.put(PROFILE_KEY, prof);
        Files.writeString(profilesFile, Json.write(root));
    }

    /** A small Nova icon (purple-to-cyan "N"). Falls back to a built-in icon name if drawing fails. */
    static String icon() {
        try {
            BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, new Color(0x8B5CF6), 64, 64, new Color(0x22D3EE)));
            g.fillRoundRect(2, 2, 60, 60, 16, 16);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 42));
            FontMetrics fm = g.getFontMetrics();
            g.drawString("N", (64 - fm.stringWidth("N")) / 2, (64 - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Throwable t) {
            return "Furnace";
        }
    }
}

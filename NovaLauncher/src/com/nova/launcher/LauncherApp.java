package com.nova.launcher;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** The Nova Launcher window (v2 design: sidebar, hero card, profile rows, status column). */
public final class LauncherApp {
    // ---- palette (the same night-sky palettes as the in-game client's Theme)
    static final Color BG = new Color(0x080816), SURFACE = new Color(0x15122B), SURFACE2 = new Color(0x221E42);
    static final Color BORDER = new Color(182, 156, 255, 46);
    static Color ACCENT = new Color(0xB69CFF), ACCENT_DARK = new Color(0x6C5BD0), CYAN = new Color(0xFFC2A8);   // CYAN = warm secondary
    static Color SKY_TOP = new Color(0x080818), SKY_BOT = new Color(0x352766);
    static Color AUR1 = new Color(0x7CF5D0), AUR2 = new Color(0xB69CFF), AUR3 = new Color(0xFF9EC7);
    static final String[] THEME_NAMES = {"Twilight", "Lagoon", "Dusk", "Meadow", "Blossom"};
    // accent, accent deep, secondary, sky top, sky bottom, aurora 1..3
    private static final int[][] THEMES = {
            {0xB69CFF, 0x6C5BD0, 0xFFC2A8, 0x080818, 0x352766, 0x7CF5D0, 0xB69CFF, 0xFF9EC7},
            {0x7CC8FF, 0x2F6FB5, 0x8FF0D4, 0x050F1E, 0x164766, 0x5FF2C8, 0x7CC8FF, 0xA99BFF},
            {0xFFA98A, 0xC4577A, 0xFFD58A, 0x110818, 0x6E2F4C, 0xFFB28A, 0xFF8FB1, 0xFFD58A},
            {0x9BE8B0, 0x3E8F6B, 0xE3F59A, 0x05120D, 0x1B5240, 0x8EF0B0, 0xD2F59A, 0x5FD6C8},
            {0xFF9EC4, 0xB6487C, 0xC9B4FF, 0x150818, 0x5E2A66, 0xFF9EC4, 0xC9B4FF, 0xFFC2A8}};
    static int themeIndex;

    /** Switches the palette of the whole launcher (takes effect on next repaint). */
    static void applyTheme(int i) {
        themeIndex = Math.max(0, Math.min(THEMES.length - 1, i));
        int[] t = THEMES[themeIndex];
        ACCENT = new Color(t[0]); ACCENT_DARK = new Color(t[1]); CYAN = new Color(t[2]);
        SKY_TOP = new Color(t[3]); SKY_BOT = new Color(t[4]);
        AUR1 = new Color(t[5]); AUR2 = new Color(t[6]); AUR3 = new Color(t[7]);
    }

    static Color mix(Color a, Color b, float t) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
    static final Color TEXT = new Color(0xF4F0FF), MUTED = new Color(0xB3AED0), DIM = new Color(0x77729A);
    static final Color GREEN = new Color(0x8EF0B0), RED = new Color(0xFF8FA3);
    static final Color INK = new Color(0x120C2A);   // dark text on pastel buttons
    // ------------------------------------------------------------------ fonts
    static Font font(int style, float size) {
        String[] want = {"Inter", "Segoe UI", "SF Pro Text", "Helvetica Neue", "Ubuntu", "DejaVu Sans"};
        for (String n : want) {
            Font f = new Font(n, style, 12);
            if (f.getFamily().equalsIgnoreCase(n)) return f.deriveFont(style, size);
        }
        return new Font(Font.SANS_SERIF, style, 12).deriveFont(style, size);
    }

    static void aa(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    // ---- shared look: glass panels + the crescent logo
    /** A frosted, tinted panel like the client's module cards. Paints into (0,0,w,h). */
    static void glass(Graphics2D g2, int w, int h, int arc, boolean strong, boolean dark) {
        Color a = dark ? new Color(6, 5, 16, 200) : alpha(mix(ACCENT_DARK, SKY_TOP, 0.62f), strong ? 170 : 125);
        Color b = dark ? new Color(6, 5, 16, 215) : alpha(mix(ACCENT_DARK, SKY_TOP, 0.82f), strong ? 140 : 95);
        g2.setPaint(new GradientPaint(0, 0, a, 0, h, b));
        g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
        g2.setColor(new Color(255, 255, 255, strong ? 18 : 10));
        g2.drawLine(arc / 2, 1, w - arc / 2, 1);
        g2.setColor(alpha(ACCENT, strong ? 125 : 64));
        g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
    }

    /** The Nova mark: a pixel crescent moon in a glass tile (same icon as the client's Singleplayer button). */
    static void paintLogo(Graphics2D g2, int x, int y, int s) {
        g2.setPaint(new GradientPaint(x, y, alpha(ACCENT_DARK, 205), x, y + s, alpha(mix(ACCENT_DARK, SKY_TOP, 0.6f), 235)));
        g2.fillRoundRect(x, y, s, s, s / 3, s / 3);
        g2.setColor(alpha(ACCENT, 150));
        g2.drawRoundRect(x, y, s - 1, s - 1, s / 3, s / 3);
        String[] m = {"...XXXX..", "..XXX....", ".XXX.....", ".XXX.....", ".XXX.....", ".XXX.....", "..XXX....", "...XXXX.."};
        int cell = Math.max(2, s / 11);
        int ox = x + (s - 9 * cell) / 2, oy = y + (s - 8 * cell) / 2;
        g2.setColor(mix(ACCENT, Color.WHITE, 0.65f));
        for (int r = 0; r < m.length; r++)
            for (int c = 0; c < m[r].length(); c++)
                if (m[r].charAt(c) == 'X') g2.fillRect(ox + c * cell, oy + r * cell, cell, cell);
    }

    static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a))); }


    private final LauncherConfig cfg = LauncherConfig.load();
    private final Path home = Platform.home();
    private final Path gameDir = home.resolve("instances").resolve("default");

    private JFrame frame;
    private Orbit orbit;
    private volatile Account account;
    private volatile boolean busy;
    private volatile NovaManifest manifest;     // latest.json, once fetched
    private volatile List<String> versionList;  // every Minecraft release Fabric supports

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        SwingUtilities.invokeLater(() -> new LauncherApp().show());
    }

    // ------------------------------------------------------------------ window
    private void show() {
        applyTheme(cfg.theme);
        frame = new JFrame("Nova Launcher");
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setBackground(BG);
        BufferedImage icon = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ig = icon.createGraphics(); aa(ig); paintLogo(ig, 2, 2, 60); ig.dispose();
        frame.setIconImage(icon);
        buildUi();
        frame.setContentPane(orbit);
        frame.pack();
        frame.setLocationRelativeTo(null);
        try { frame.setShape(new RoundRectangle2D.Double(0, 0, frame.getWidth(), frame.getHeight(), 30, 30)); } catch (Exception ignored) {}
        frame.setVisible(true);
        new Timer(33, e -> { if (frame.isShowing() && frame.getState() != Frame.ICONIFIED) orbit.repaint(); }).start();
        startup();
    }

    private void buildUi() {
        orbit = new Orbit();
        orbit.mc = displayMc();
        orbit.ramMb = cfg.ramMb;
        orbit.themeName = THEME_NAMES[cfg.theme];
        orbit.note = "Nova Client is built in \u00B7 installs on Play";
        orbit.onPlay = this::play;
        orbit.onLogin = this::toggleLogin;
        orbit.onSettings = this::openSettings;
        orbit.onTheme = this::nextTheme;
        orbit.onSkin = this::previewSkinFile;
        orbit.onVersion = this::openVersions;
        orbit.onMods = this::openMods;
        orbit.onOfficial = this::addToOfficial;
        orbit.onFolder = () -> {
            try { Files.createDirectories(gameDir); } catch (Exception ignored) {}
            Platform.openFolder(gameDir);
        };
        orbit.onMin = () -> { if (frame != null) frame.setState(Frame.ICONIFIED); };
        orbit.onClose = () -> System.exit(0);
        orbit.setSkin(null, false);
    }

    /** The Minecraft version shown (and played): the picked one, or whatever "automatic" resolves to. */
    private String displayMc() {
        return NovaUpdater.chooseMc(cfg, manifest, Embedded.load(), gameDir.resolve("mods"));
    }

    private void openMods() {
        try { Files.createDirectories(gameDir.resolve("mods")); } catch (Exception ignored) {}
        new ModsDialog(frame, gameDir.resolve("mods"), this::displayMc, this::chooseOptionalMods).setVisible(true);
        orbit.mc = displayMc();
    }

    private void openVersions() {
        if (busy) return;
        List<String> vl = versionList;
        if (vl != null) { showVersions(vl); return; }
        setStatus("Loading Minecraft versions...");
        async(() -> {
            try {
                List<String> l = VersionList.load(home);
                versionList = l;
                SwingUtilities.invokeLater(() -> { orbit.status = "Ready"; showVersions(l); });
            } catch (Exception e) {
                log("Could not load the version list: " + e.getMessage());
                setStatus("Could not load the version list (offline?)");
            }
        });
    }

    private void showVersions(List<String> vl) {
        NovaManifest m = manifest;
        Embedded emb = Embedded.load();
        Map<String, NovaManifest.Build> builds = m == null ? Map.of() : m.builds();
        String embMc = emb == null ? null : emb.minecraft;
        new VersionDialog(frame, vl, cfg.mcVersion, builds, embMc, picked -> {
            cfg.mcVersion = picked;
            cfg.save();
            orbit.mc = displayMc();
            String mc = orbit.mc;
            String tag = VersionDialog.tagFor(mc, builds, embMc);
            orbit.status = "Minecraft " + mc + (picked.isBlank() ? " (automatic)" : "");
            if (tag.equals("Fabric only")) orbit.note = "No Nova build for " + mc + " yet \u00B7 it will start as plain Fabric";
            else if (tag.contains("experimental")) orbit.note = "Nova for " + mc + " is experimental: some features may be missing";
            else orbit.note = "Nova Client is available for " + mc;
            frame.repaint();
        }).setVisible(true);
    }

    private void nextTheme() {
        cfg.theme = (cfg.theme + 1) % THEME_NAMES.length;
        applyTheme(cfg.theme);
        cfg.save();
        orbit.themeName = THEME_NAMES[cfg.theme];
        orbit.status = "Theme: " + THEME_NAMES[cfg.theme];
    }

    private void openSettings() {
        new SettingsDialog(frame, cfg, () -> {
            orbit.mc = displayMc();
            orbit.ramMb = cfg.ramMb;
            frame.repaint();
        }).setVisible(true);
    }

    /** Renders the launcher to a PNG without opening a window (used for docs/screenshots). */
    static Point previewMouse;
    static void preview(String file, boolean playing, boolean console) throws Exception { preview(file, playing, console, 0); }

    static void preview(String file, boolean playing, boolean console, int theme) throws Exception {
        applyTheme(theme);
        Orbit o = new Orbit();
        o.themeName = THEME_NAMES[theme];
        o.who = "Dalif"; o.loggedIn = true; o.loginText = "log out";
        o.note = "Nova Client 0.5.0 is built in \u00B7 installs on Play";
        o.ramMb = 16384;
        if (playing) { o.working = true; o.progress = 0.38f; o.status = "Downloading game files (1520/3980)"; }
        else o.status = "Ready";
        o.addLog("Nova Launcher 2.0.0 - data folder: C:\\Users\\dalif\\AppData\\Roaming\\.novaclient");
        o.addLog("Built-in Nova Client 0.5.0 (Minecraft 1.21.4)");
        o.addLog("Logged in as Dalif");
        o.addLog("Nova Client 0.5.0 is ready");
        if (console) o.toggleConsole();
        int w = 1180, h = 720;
        o.setSize(w, h);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        if (previewMouse != null) o.simulateMouse(previewMouse.x, previewMouse.y);
        for (int i = 0; i < 40; i++) {                // let the console slide in / hover states settle
            Graphics2D g = img.createGraphics();
            o.paint(g);
            g.dispose();
        }
        ImageIO.write(img, "png", new File(file));
    }

    private void startup() {
        log("Nova Launcher " + GameLauncher.VERSION + " - data folder: " + home);
        Embedded emb = Embedded.load();
        if (emb != null) {
            orbit.note = "Nova Client " + emb.version + " is built in \u00B7 installs on Play";
            log("Built-in Nova Client " + emb.version + " (Minecraft " + emb.minecraft + ")");
        }
        if (!cfg.refreshToken.isBlank() && !cfg.azureClientId.isBlank()) {
            async(() -> {
                try {
                    setStatus("Logging back in...");
                    Auth.Tokens t = Auth.refresh(cfg.azureClientId, cfg.refreshToken);
                    finishLogin(t);
                } catch (Exception e) {
                    log("Auto-login failed: " + e.getMessage());
                    setStatus("Ready");
                }
            });
        }
        async(() -> { try { versionList = VersionList.load(home); } catch (Exception e) { log("Version list not loaded: " + e.getMessage()); } });
        // Look for a newer launcher / Nova version (does not install anything yet)
        if (!cfg.manifestUrl.isBlank()) {
            async(() -> {
                try {
                    NovaManifest nm = NovaManifest.fetch(cfg.manifestUrl);
                    manifest = nm;
                    String ver = nm.version();
                    String lv = nm.launcherVersion(), lu = nm.launcherUrl();
                    SwingUtilities.invokeLater(() -> {
                        orbit.mc = displayMc();
                        if (lv != null && Versions.cmp(lv, GameLauncher.VERSION) > 0) {
                            orbit.note = "A new launcher (" + lv + ") is available - click to download";
                            orbit.noteUrl = lu;
                        } else if (ver != null) {
                            orbit.note = "Nova Client " + ver + " installs/updates when you press Play";
                        }
                    });
                } catch (Exception ignored) {}
            });
        }
    }

    // ------------------------------------------------------------------ login
    private void toggleLogin() {
        if (account != null && account.online) {
            account = null;
            cfg.refreshToken = "";
            cfg.save();
            orbit.loggedIn = false;
            orbit.who = "Not logged in";
            orbit.loginText = "log in with microsoft";
            orbit.setSkin(null, false);
            return;
        }
        if (cfg.azureClientId.isBlank()) {
            JOptionPane.showMessageDialog(frame, "Open Settings and paste your Azure app client ID first (see README).",
                    "Microsoft login", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        async(() -> {
            try {
                setStatus("Requesting login code...");
                Auth.DeviceCode dc = Auth.start(cfg.azureClientId);
                SwingUtilities.invokeLater(() -> {
                    Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new java.awt.datatransfer.StringSelection(dc.userCode()), null);
                    Platform.open(dc.verificationUri());
                    JOptionPane.showMessageDialog(frame, "Your code is  " + dc.userCode() + "  (copied to clipboard).\n"
                            + "A browser page was opened at " + dc.verificationUri() + "\nEnter the code there, then wait here.",
                            "Microsoft login", JOptionPane.INFORMATION_MESSAGE);
                });
                setStatus("Waiting for you to finish logging in...");
                finishLogin(Auth.poll(cfg.azureClientId, dc));
            } catch (Exception e) {
                log("Login failed: " + e.getMessage());
                setStatus("Login failed: " + e.getMessage());
            }
        });
    }

    private void finishLogin(Auth.Tokens t) throws Exception {
        if (!t.refresh().isEmpty()) { cfg.refreshToken = t.refresh(); cfg.save(); }
        Account a = Auth.minecraft(t.access());
        account = a;
        BufferedImage skin = null;
        if (a.skinUrl != null) {
            try { skin = ImageIO.read(new ByteArrayInputStream(Http.getBytes(a.skinUrl))); } catch (Exception ignored) {}
        }
        BufferedImage fs = skin;
        SwingUtilities.invokeLater(() -> {
            orbit.who = a.name;
            orbit.loggedIn = true;
            orbit.loginText = "log out";
            orbit.setSkin(fs, a.slim);
        });
        setStatus("Logged in as " + a.name);
        log("Logged in as " + a.name);
    }

    private void previewSkinFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose a skin PNG");
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            try {
                BufferedImage img = ImageIO.read(fc.getSelectedFile());
                if (img == null) throw new Exception("not an image");
                orbit.setSkin(img, false);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, "Could not read that file: " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ play
    private void chooseOptionalMods() {
        new OptionalModsDialog(frame, cfg, this::displayMc, () -> orbit.status = "Mods saved: they are installed when you press Play").setVisible(true);
    }

    private void play() {
        if (busy) return;
        if (!cfg.optionalChosen) {          // first launch: let the player pick Sodium / Iris / Freecam before anything is installed
            cfg.optionalChosen = true;
            cfg.save();
            chooseOptionalMods();
        }
        busy = true;
        SwingUtilities.invokeLater(() -> { orbit.working = true; orbit.progress = 0; });
        async(() -> {
            try {
                Progress prog = new Progress() {
                    public void status(String m) { setStatus(m); log(m); }
                    public void bar(int d, int t) { SwingUtilities.invokeLater(() -> { orbit.indeterminate = false; orbit.progress = t <= 0 ? 0 : (float) d / t; }); }
                };
                SwingUtilities.invokeLater(() -> orbit.indeterminate = true);

                NovaUpdater.Result res = NovaUpdater.sync(cfg, gameDir, prog);
                log("Nova Client: " + res.novaVersion() + "  |  Minecraft " + res.mcVersion() + (res.novaLoaded() ? "" : "  |  plain Fabric"));
                SwingUtilities.invokeLater(() -> orbit.mc = res.mcVersion());

                Installer.Plan plan = new Installer(home, prog).install(res.mcVersion());
                Path java = JavaRuntime.ensure(plan.javaMajor(), home, cfg.javaPath, prog);

                Account acct = account;
                if (acct != null && !cfg.refreshToken.isBlank() && !cfg.azureClientId.isBlank()) {
                    try { // fresh Minecraft token for this launch
                        setStatus("Refreshing login...");
                        Auth.Tokens t = Auth.refresh(cfg.azureClientId, cfg.refreshToken);
                        if (!t.refresh().isEmpty()) { cfg.refreshToken = t.refresh(); cfg.save(); }
                        acct = Auth.minecraft(t.access());
                        account = acct;
                    } catch (Exception e) { log("Could not refresh login: " + e.getMessage()); }
                }
                if (acct == null) {
                    acct = Account.offline(cfg.offlineName);
                    log("Not logged in: starting in offline mode as " + acct.name + " (singleplayer / offline servers only)");
                }

                List<String> extra = new ArrayList<>();
                extra.add("-Dnova.theme=" + cfg.theme);
                if (!cfg.manifestUrl.isBlank()) extra.add("-Dnova.manifest=" + cfg.manifestUrl);
                if (!cfg.backgroundPath.isBlank()) extra.add("-Dnova.background=" + cfg.backgroundPath);
                List<String> cmd = GameLauncher.command(plan, acct, java, gameDir, cfg.ramMb, extra);

                setStatus("Minecraft is running");
                SwingUtilities.invokeLater(() -> { orbit.indeterminate = false; orbit.progress = 1f; });
                Process pr = GameLauncher.start(cmd, gameDir);
                try (BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) log(line);
                }
                int code = pr.waitFor();
                log("Game closed (exit code " + code + ")");
                setStatus(code == 0 ? "Ready" : "Game exited with code " + code + " - see the console");
            } catch (Exception e) {
                log("ERROR: " + e);
                setStatus("Error: " + e.getMessage());
                SwingUtilities.invokeLater(() -> orbit.indeterminate = false);
            } finally {
                busy = false;
                SwingUtilities.invokeLater(() -> { orbit.working = false; orbit.indeterminate = false; orbit.progress = 0; });
            }
        });
    }

    // ------------------------------------------------------------------ official launcher
    private void addToOfficial() {
        if (busy) return;
        busy = true;
        SwingUtilities.invokeLater(() -> { orbit.working = true; orbit.indeterminate = true; });
        async(() -> {
            try {
                Progress prog = new Progress() {
                    public void status(String m) { setStatus(m); log(m); }
                    public void bar(int d, int t) {}
                };
                String msg = OfficialLauncher.install(cfg, home, gameDir, prog);
                log(msg);
                setStatus("Added to the Minecraft Launcher");
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame,
                        msg + "\n\nClose and reopen the Minecraft Launcher, pick the \"Nova Client\" profile and press Play.\n"
                                + "(The official launcher handles your Microsoft login.)", "Added", JOptionPane.INFORMATION_MESSAGE));
            } catch (Exception e) {
                log("ERROR: " + e);
                setStatus("Error: " + e.getMessage());
            } finally {
                busy = false;
                SwingUtilities.invokeLater(() -> { orbit.working = false; orbit.indeterminate = false; orbit.progress = 0; });
            }
        });
    }

    // ------------------------------------------------------------------ helpers
    private void async(Runnable r) {
        Thread t = new Thread(r, "nova-launcher-worker");
        t.setDaemon(true);
        t.start();
    }

    private void setStatus(String s) { SwingUtilities.invokeLater(() -> orbit.status = s); }

    private void log(String line) { SwingUtilities.invokeLater(() -> orbit.addLog(line)); }

    static JLabel label(String text, int size, boolean bold, Color c) {
        JLabel l = new JLabel(text);
        l.setFont(font(bold ? Font.BOLD : Font.PLAIN, size));
        l.setForeground(c);
        return l;
    }

    /** A pixel skin used before you log in, so the hero card never looks empty. */
    static BufferedImage defaultSkin() {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        int[][] r = {{8, 8, 8, 8, 0xFFD9A066}, {20, 20, 8, 12, 0xFF7C6BD6}, {44, 20, 4, 12, 0xFF8E7EE6}, {36, 52, 4, 12, 0xFF8E7EE6},
                {4, 20, 4, 12, 0xFF2A2550}, {20, 52, 4, 12, 0xFF2A2550}, {40, 8, 8, 4, 0xFF2B1B3D}, {8, 8, 8, 2, 0xFF2B1B3D},
                {20, 26, 8, 2, 0xFFFFC2A8}, {8, 10, 1, 4, 0xFF2B1B3D}, {15, 10, 1, 4, 0xFF2B1B3D}};
        for (int[] q : r) for (int y = q[1]; y < q[1] + q[3]; y++) for (int x = q[0]; x < q[0] + q[2]; x++) skin.setRGB(x, y, q[4]);
        skin.setRGB(10, 12, 0xFFFFFFFF); skin.setRGB(13, 12, 0xFFFFFFFF);
        skin.setRGB(9, 12, 0xFF3B1D8A); skin.setRGB(14, 12, 0xFF3B1D8A);
        for (int x = 10; x < 14; x++) skin.setRGB(x, 14, 0xFFB45309);
        return skin;
    }

    // ------------------------------------------------------------------ glyphs (vector icons)
    enum Glyph {
        HOME, TERMINAL, SHIRT, FOLDER, GEAR, PLAY, PALETTE, PLUS, BOX;

        void paint(Graphics2D g, double cx, double cy, double s, Color c) {
            g.setColor(c);
            g.setStroke(new BasicStroke((float) Math.max(1.6, s / 11), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double h = s / 2;
            switch (this) {
                case HOME -> {
                    Path2D p = new Path2D.Double();
                    p.moveTo(cx - h, cy); p.lineTo(cx, cy - h); p.lineTo(cx + h, cy);
                    g.draw(p);
                    Path2D b = new Path2D.Double();
                    b.moveTo(cx - h * 0.72, cy - h * 0.15); b.lineTo(cx - h * 0.72, cy + h * 0.85); b.lineTo(cx + h * 0.72, cy + h * 0.85); b.lineTo(cx + h * 0.72, cy - h * 0.15);
                    g.draw(b);
                    g.draw(new Rectangle2D.Double(cx - h * 0.22, cy + h * 0.2, h * 0.44, h * 0.65));
                }
                case TERMINAL -> {
                    g.draw(new RoundRectangle2D.Double(cx - h, cy - h * 0.8, s, s * 0.8, 6, 6));
                    g.draw(new Line2D.Double(cx - h * 0.55, cy - h * 0.35, cx - h * 0.15, cy));
                    g.draw(new Line2D.Double(cx - h * 0.15, cy, cx - h * 0.55, cy + h * 0.3 - h * 0.0));
                    g.draw(new Line2D.Double(cx + h * 0.05, cy + h * 0.3, cx + h * 0.5, cy + h * 0.3));
                }
                case SHIRT -> {
                    Path2D p = new Path2D.Double();
                    p.moveTo(cx - h * 0.45, cy - h); p.lineTo(cx - h, cy - h * 0.55); p.lineTo(cx - h * 0.7, cy - h * 0.05);
                    p.lineTo(cx - h * 0.5, cy - h * 0.2); p.lineTo(cx - h * 0.5, cy + h); p.lineTo(cx + h * 0.5, cy + h);
                    p.lineTo(cx + h * 0.5, cy - h * 0.2); p.lineTo(cx + h * 0.7, cy - h * 0.05); p.lineTo(cx + h, cy - h * 0.55);
                    p.lineTo(cx + h * 0.45, cy - h);
                    p.quadTo(cx, cy - h * 0.55, cx - h * 0.45, cy - h);
                    g.draw(p);
                }
                case FOLDER -> {
                    Path2D p = new Path2D.Double();
                    p.moveTo(cx - h, cy - h * 0.55); p.lineTo(cx - h * 0.35, cy - h * 0.55); p.lineTo(cx - h * 0.1, cy - h * 0.25);
                    p.lineTo(cx + h, cy - h * 0.25); p.lineTo(cx + h, cy + h * 0.7); p.lineTo(cx - h, cy + h * 0.7); p.closePath();
                    g.draw(p);
                }
                case GEAR -> {
                    g.draw(new Ellipse2D.Double(cx - h * 0.38, cy - h * 0.38, h * 0.76, h * 0.76));
                    for (int i = 0; i < 8; i++) {
                        double a = i * Math.PI / 4;
                        g.draw(new Line2D.Double(cx + Math.cos(a) * h * 0.62, cy + Math.sin(a) * h * 0.62, cx + Math.cos(a) * h * 0.92, cy + Math.sin(a) * h * 0.92));
                    }
                    g.draw(new Ellipse2D.Double(cx - h * 0.62, cy - h * 0.62, h * 1.24, h * 1.24));
                }
                case PALETTE -> {
                    g.draw(new Ellipse2D.Double(cx - h, cy - h, s, s));
                    Color[] cs = {ACCENT, CYAN, Color.WHITE};
                    double[][] pos = {{-0.4, -0.35}, {0.35, -0.3}, {0.0, 0.45}};
                    for (int i = 0; i < 3; i++) { g.setColor(cs[i]); g.fill(new Ellipse2D.Double(cx + pos[i][0] * h - 2.6, cy + pos[i][1] * h - 2.6, 5.2, 5.2)); }
                }
                case BOX -> {
                    Path2D top = new Path2D.Double();
                    top.moveTo(cx, cy - h); top.lineTo(cx + h * 0.87, cy - h * 0.5); top.lineTo(cx, cy); top.lineTo(cx - h * 0.87, cy - h * 0.5); top.closePath();
                    g.draw(top);
                    Path2D sides = new Path2D.Double();
                    sides.moveTo(cx - h * 0.87, cy - h * 0.5); sides.lineTo(cx - h * 0.87, cy + h * 0.5); sides.lineTo(cx, cy + h);
                    sides.lineTo(cx + h * 0.87, cy + h * 0.5); sides.lineTo(cx + h * 0.87, cy - h * 0.5);
                    g.draw(sides);
                    g.draw(new Line2D.Double(cx, cy, cx, cy + h));
                }
                case PLUS -> {
                    g.draw(new Line2D.Double(cx - h * 0.8, cy, cx + h * 0.8, cy));
                    g.draw(new Line2D.Double(cx, cy - h * 0.8, cx, cy + h * 0.8));
                }
                case PLAY -> {
                    Path2D p = new Path2D.Double();
                    p.moveTo(cx - h * 0.5, cy - h * 0.7); p.lineTo(cx + h * 0.7, cy); p.lineTo(cx - h * 0.5, cy + h * 0.7); p.closePath();
                    g.fill(p);
                }
            }
        }
    }

    // ------------------------------------------------------------------ components
    static final class NovaButton extends JButton {
        private final boolean primary; private boolean hover; Glyph glyph;
        NovaButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setOpaque(false);
            setForeground(Color.WHITE);
            setFont(font(Font.BOLD, 13f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(9, 16, 9, 16));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
        @Override public Dimension getPreferredSize() {
            if (glyph != null && getText().isEmpty()) return new Dimension(46, 46);
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(getText()) + 34, 36);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create(); aa(g2);
            int w = getWidth(), h = getHeight();
            if (!isEnabled()) g2.setColor(new Color(255, 255, 255, 18));
            else if (primary) g2.setPaint(new GradientPaint(0, 0, hover ? mix(ACCENT, Color.WHITE, 0.3f) : ACCENT, 0, h, mix(ACCENT, ACCENT_DARK, 0.45f)));
            else g2.setColor(alpha(mix(ACCENT_DARK, SKY_TOP, hover ? 0.4f : 0.6f), hover ? 210 : 160));
            g2.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);
            g2.setColor(primary ? new Color(255, 255, 255, 90) : alpha(ACCENT, hover && isEnabled() ? 200 : 75));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);
            if (glyph != null) glyph.paint(g2, w / 2.0, h / 2.0, 20, hover ? Color.WHITE : MUTED);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(!isEnabled() ? DIM : primary ? INK : TEXT);
            g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}

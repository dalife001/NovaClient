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
import java.util.Random;

/** The Nova Launcher window (v2 design: sidebar, hero card, profile rows, status column). */
public final class LauncherApp {
    // ---- palette
    static final Color BG = new Color(0x0A0A10), SURFACE = new Color(0x13131C), SURFACE2 = new Color(0x1B1B27);
    static final Color BORDER = new Color(255, 255, 255, 22);
    static Color ACCENT = new Color(0x8B5CF6), ACCENT_DARK = new Color(0x5B34B8), CYAN = new Color(0x22D3EE);
    static final String[] THEME_NAMES = {"Nova", "Ocean", "Sunset", "Emerald", "Rose"};
    private static final int[][] THEMES = {{0x8B5CF6, 0x5B34B8, 0x22D3EE}, {0x3B82F6, 0x1D4ED8, 0x2DD4BF}, {0xF97316, 0xC2410C, 0xF472B6},
            {0x10B981, 0x047857, 0xA3E635}, {0xF43F5E, 0x9F1239, 0xFBBF24}};
    static int themeIndex;

    /** Switches the accent palette of the whole launcher (takes effect on next repaint). */
    static void applyTheme(int i) {
        themeIndex = Math.max(0, Math.min(THEMES.length - 1, i));
        ACCENT = new Color(THEMES[themeIndex][0]); ACCENT_DARK = new Color(THEMES[themeIndex][1]); CYAN = new Color(THEMES[themeIndex][2]);
    }

    static Color mix(Color a, Color b, float t) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
    static final Color TEXT = Color.WHITE, MUTED = new Color(0x9CA3AF), DIM = new Color(0x6B7280);
    static final Color GREEN = new Color(0x34D399), GREEN_BTN = new Color(0x22C55E), GREEN_DARK = new Color(0x15803D), RED = new Color(0xF87171);

    private final LauncherConfig cfg = LauncherConfig.load();
    private final Path home = Platform.home();
    private final Path gameDir = home.resolve("instances").resolve("default");

    private JFrame frame;
    private JPanel root;
    private CardLayout pages;
    private JPanel pageHost;
    private Hero hero;
    private SkinPanel skinPanel;
    private PlayButton playButton;
    private NovaButton loginButton;
    private JLabel status, who, subtitle, hint, chipVersion, rowSub;
    private JTextArea updateNote;
    private NovaBar bar;
    private JTextArea log;
    private final List<JComponent> animated = new ArrayList<>();
    private final List<SideButton> sideButtons = new ArrayList<>();
    private SideButton paletteBtn;
    private volatile Account account;
    private volatile boolean busy;

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        SwingUtilities.invokeLater(() -> new LauncherApp().show());
    }

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
    }

    static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a))); }

    // ------------------------------------------------------------------ UI
    private void show() {
        frame = new JFrame("Nova Launcher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1180, 720);
        frame.setMinimumSize(new Dimension(1020, 640));
        frame.setLocationRelativeTo(null);
        buildUi();
        frame.setContentPane(root);
        frame.setVisible(true);
        new Timer(33, e -> { for (JComponent c : animated) if (c.isShowing()) c.repaint(); }).start();
        startup();
    }

    private String subtitleText(String mc) {
        return "Fabric " + mc + "  \u2022  Sodium + Iris  \u2022  Nova Client";
    }

    private void buildUi() {
        applyTheme(cfg.theme);
        String mc = cfg.mcVersion.isBlank() ? NovaUpdater.DEFAULT_MC : cfg.mcVersion;

        root = new JPanel(new BorderLayout());
        root.setBackground(BG);

        // ---------- sidebar
        JPanel side = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create(); aa(g2);
                g2.setColor(new Color(0x0D0D14)); g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(BORDER); g2.fillRect(getWidth() - 1, 0, 1, getHeight());
                // logo
                int s = 38, x = (getWidth() - s) / 2, y = 18;
                g2.setPaint(new GradientPaint(x, y, ACCENT, x + s, y + s, CYAN));
                g2.fillRoundRect(x, y, s, s, 12, 12);
                g2.setColor(Color.WHITE);
                g2.setFont(font(Font.BOLD, 22f));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("N", x + (s - fm.stringWidth("N")) / 2, y + (s - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        };
        side.setPreferredSize(new Dimension(72, 0));
        side.setLayout(null);
        SideButton home = new SideButton(Glyph.HOME, "Home", () -> showPage("home"));
        SideButton console = new SideButton(Glyph.TERMINAL, "Console", () -> showPage("console"));
        SideButton skin = new SideButton(Glyph.SHIRT, "Preview a skin file", this::previewSkinFile);
        SideButton folder = new SideButton(Glyph.FOLDER, "Game folder", () -> {
            try { Files.createDirectories(gameDir); } catch (Exception ignored) {}
            Platform.openFolder(gameDir);
        });
        SideButton settings = new SideButton(Glyph.GEAR, "Settings", this::openSettings);
        SideButton palette = new SideButton(Glyph.PALETTE, "Theme: " + THEME_NAMES[cfg.theme] + " (click to change)", this::nextTheme);
        paletteBtn = palette;
        SideButton[] top = {home, console, skin, folder};
        for (int i = 0; i < top.length; i++) { top[i].setBounds(10, 82 + i * 54, 52, 46); side.add(top[i]); sideButtons.add(top[i]); }
        home.selected = true;
        side.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                settings.setBounds(10, side.getHeight() - 112, 52, 46);
                palette.setBounds(10, side.getHeight() - 166, 52, 46);
            }
        });
        settings.setBounds(10, 500, 52, 46);
        side.add(settings);
        palette.setBounds(10, 446, 52, 46);
        side.add(palette);
        PlayButton quick = new PlayButton(true);
        quick.setBounds(14, 560, 44, 44);
        quick.addActionListener(e -> play());
        side.add(quick);
        side.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                quick.setBounds(14, side.getHeight() - 58, 44, 44);
            }
        });
        root.add(side, BorderLayout.WEST);

        // ---------- pages
        pages = new CardLayout();
        pageHost = new JPanel(pages);
        pageHost.setOpaque(false);
        pageHost.add(buildHome(mc), "home");
        pageHost.add(buildConsole(), "console");
        root.add(pageHost, BorderLayout.CENTER);
        animated.add(hero);
        animated.add(playButton);
        animated.add(quick);
        animated.add(bar);
    }

    private void nextTheme() {
        cfg.theme = (cfg.theme + 1) % THEME_NAMES.length;
        applyTheme(cfg.theme);
        cfg.save();
        paletteBtn.setToolTipText("Theme: " + THEME_NAMES[cfg.theme] + " (click to change)");
        status.setText("Theme: " + THEME_NAMES[cfg.theme]);
        frame.getContentPane().repaint();
    }

    private void showPage(String name) {
        pages.show(pageHost, name);
        for (int i = 0; i < sideButtons.size(); i++) sideButtons.get(i).selected = (i == 0 && name.equals("home")) || (i == 1 && name.equals("console"));
        for (SideButton b : sideButtons) b.repaint();
    }

    private JPanel buildHome(String mc) {
        JPanel page = new JPanel(new BorderLayout(0, 16));
        page.setOpaque(false);
        page.setBorder(new EmptyBorder(18, 22, 20, 22));

        // ----- top bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        tabs.setOpaque(false);
        tabs.add(new TabPill("NOVA", true));
        chipVersion = label("Minecraft " + mc, 13, true, MUTED);
        JPanel vchip = chip(chipVersion);
        tabs.add(vchip);
        topBar.add(tabs, BorderLayout.WEST);
        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        topRight.setOpaque(false);
        NovaButton off = new NovaButton("Add to Minecraft Launcher", false);
        off.addActionListener(e -> addToOfficial());
        topRight.add(off);
        topBar.add(topRight, BorderLayout.EAST);
        page.add(topBar, BorderLayout.NORTH);

        // ----- left column: hero + profiles
        JPanel left = new JPanel(new BorderLayout(0, 14));
        left.setOpaque(false);

        hero = new Hero();
        hero.setLayout(new BorderLayout());
        skinPanel = new SkinPanel();
        hero.add(skinPanel, BorderLayout.CENTER);

        JPanel nameWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 14));
        nameWrap.setOpaque(false);
        who = label("Not logged in", 15, true, TEXT);
        nameWrap.add(chip(who));
        hero.add(nameWrap, BorderLayout.NORTH);

        JPanel acctWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 16));
        acctWrap.setOpaque(false);
        JPanel acct = new RoundPanel(new Color(10, 10, 16, 215), BORDER, 18);
        acct.setLayout(new BoxLayout(acct, BoxLayout.X_AXIS));
        acct.setBorder(new EmptyBorder(10, 16, 10, 12));
        hint = label("Playing offline as \"" + cfg.offlineName + "\"", 13, false, MUTED);
        loginButton = new NovaButton("Log in with Microsoft", false);
        loginButton.addActionListener(e -> toggleLogin(hint));
        acct.add(hint); acct.add(Box.createHorizontalStrut(16)); acct.add(loginButton);
        acctWrap.add(acct);
        hero.add(acctWrap, BorderLayout.SOUTH);
        left.add(hero, BorderLayout.CENTER);

        JPanel profiles = new JPanel();
        profiles.setOpaque(false);
        profiles.setLayout(new BoxLayout(profiles, BoxLayout.Y_AXIS));
        JLabel ph = label("Last Played Profiles", 14, true, TEXT);
        ph.setAlignmentX(0);
        profiles.add(ph);
        profiles.add(Box.createVerticalStrut(10));
        subtitle = label(subtitleText(mc), 13, false, MUTED);
        playButton = new PlayButton(false);
        playButton.addActionListener(e -> play());
        NovaButton gear = new NovaButton("", false);
        gear.glyph = Glyph.GEAR;
        gear.addActionListener(e -> openSettings());
        ProfileRow r1 = new ProfileRow("Nova Client", subtitle, true, 78);
        r1.addAction(playButton); r1.addAction(gear);
        profiles.add(r1);
        profiles.add(Box.createVerticalStrut(10));
        rowSub = label("Adds a \"Nova Client\" profile to the official launcher", 12, false, MUTED);
        ProfileRow r2 = new ProfileRow("Official Launcher", rowSub, false, 62);
        NovaButton addOff = new NovaButton("Add", false);
        addOff.addActionListener(e -> addToOfficial());
        r2.addAction(addOff);
        profiles.add(r2);
        left.add(profiles, BorderLayout.SOUTH);
        page.add(left, BorderLayout.CENTER);

        // ----- right column
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setPreferredSize(new Dimension(330, 0));

        JLabel nh = label("\u2022 Latest News", 13, true, MUTED);
        nh.setAlignmentX(0);
        right.add(nh);
        right.add(Box.createVerticalStrut(8));
        Banner banner = new Banner();
        banner.setLayout(new BorderLayout());
        banner.setAlignmentX(0);
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        banner.setPreferredSize(new Dimension(330, 150));
        updateNote = new JTextArea("Nova Client is built in and installs when you press Play");
        updateNote.setEditable(false); updateNote.setOpaque(false); updateNote.setLineWrap(true); updateNote.setWrapStyleWord(true);
        updateNote.setFont(font(Font.BOLD, 15f)); updateNote.setForeground(TEXT);
        updateNote.setBorder(new EmptyBorder(0, 16, 14, 16));
        updateNote.setPreferredSize(new Dimension(300, 70));
        JLabel tag = label("CLIENT", 11, true, new Color(0xFFD9A8));
        tag.setBorder(new EmptyBorder(14, 16, 0, 0));
        banner.add(tag, BorderLayout.NORTH);
        banner.add(updateNote, BorderLayout.SOUTH);
        right.add(banner);
        right.add(Box.createVerticalStrut(16));

        JPanel info = new RoundPanel(SURFACE, BORDER, 16);
        info.setLayout(new GridLayout(0, 1, 0, 0));
        info.setBorder(new EmptyBorder(8, 18, 8, 18));
        info.setAlignmentX(0);
        info.setMaximumSize(new Dimension(Integer.MAX_VALUE, 168));
        info.setPreferredSize(new Dimension(330, 168));
        info.add(infoRow("Game", "Minecraft Java Edition"));
        info.add(infoRow("Loader", "Fabric"));
        info.add(infoRow("Performance", "Sodium + Iris"));
        info.add(infoRow("Memory", String.format("%.1f GB", cfg.ramMb / 1024.0)));
        right.add(info);
        right.add(Box.createVerticalStrut(16));

        JPanel st = new RoundPanel(SURFACE, BORDER, 16);
        st.setLayout(new BoxLayout(st, BoxLayout.Y_AXIS));
        st.setBorder(new EmptyBorder(16, 18, 16, 18));
        st.setAlignmentX(0);
        st.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        JLabel sh = label("STATUS", 11, true, DIM);
        sh.setAlignmentX(0);
        status = label("Ready", 14, true, TEXT);
        status.setAlignmentX(0);
        bar = new NovaBar();
        bar.setAlignmentX(0);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        bar.setPreferredSize(new Dimension(280, 8));
        st.add(sh); st.add(Box.createVerticalStrut(6)); st.add(status); st.add(Box.createVerticalStrut(12)); st.add(bar);
        right.add(st);
        right.add(Box.createVerticalStrut(16));

        JPanel keys = new RoundPanel(SURFACE, BORDER, 16);
        keys.setLayout(new GridLayout(0, 1, 0, 6));
        keys.setBorder(new EmptyBorder(14, 18, 14, 18));
        keys.setAlignmentX(0);
        keys.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        keys.setPreferredSize(new Dimension(330, 140));
        keys.add(label("IN-GAME", 11, true, DIM));
        keys.add(infoRow("Right Shift", "Module menu"));
        keys.add(infoRow("Hold C", "Zoom"));
        keys.add(infoRow("F6", "Freecam"));
        right.add(keys);
        right.add(Box.createVerticalGlue());
        page.add(right, BorderLayout.EAST);
        // gap between center and right column
        left.setBorder(new EmptyBorder(0, 0, 0, 20));
        return page;
    }

    private JPanel buildConsole() {
        JPanel page = new JPanel(new BorderLayout(0, 14));
        page.setOpaque(false);
        page.setBorder(new EmptyBorder(18, 22, 20, 22));
        JLabel h = label("Console", 20, true, TEXT);
        page.add(h, BorderLayout.NORTH);
        log = new JTextArea();
        log.setEditable(false);
        log.setOpaque(false);
        log.setForeground(mix(ACCENT, Color.WHITE, 0.6f));
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        log.setBorder(new EmptyBorder(12, 14, 12, 14));
        JScrollPane sp = new JScrollPane(log);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        JPanel box = new RoundPanel(new Color(0x0D0D14), BORDER, 16);
        box.setLayout(new BorderLayout());
        box.add(sp);
        page.add(box, BorderLayout.CENTER);
        return page;
    }

    private void openSettings() {
        new SettingsDialog(frame, cfg, () -> {
            String mc = cfg.mcVersion.isBlank() ? NovaUpdater.DEFAULT_MC : cfg.mcVersion;
            chipVersion.setText("Minecraft " + mc);
            subtitle.setText(subtitleText(mc));
            frame.repaint();
        }).setVisible(true);
    }

    private JPanel infoRow(String k, String v) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(label(k, 13, false, MUTED), BorderLayout.WEST);
        p.add(label(v, 13, true, TEXT), BorderLayout.EAST);
        return p;
    }

    private static JPanel chip(JLabel l) {
        JPanel p = new RoundPanel(new Color(10, 10, 16, 215), BORDER, 14);
        p.setLayout(new BorderLayout());
        p.setBorder(new EmptyBorder(6, 14, 6, 14));
        p.add(l);
        return p;
    }

    /** Renders the launcher UI to a PNG without opening a window (used for docs/screenshots). */
    static void preview(String file, boolean playing, boolean console) throws Exception { preview(file, playing, console, 0); }

    static void preview(String file, boolean playing, boolean console, int theme) throws Exception {
        LauncherApp app = new LauncherApp();
        app.cfg.theme = theme;
        app.buildUi();
        int w = 1180, h = 720;
        app.root.setSize(w, h);
        app.who.setText("Dalif");
        app.hint.setText("Signed in with Microsoft");
        app.loginButton.setText("Log out");
        app.updateNote.setText("Nova Client 0.5.0 is built in - installs automatically on Play");
        app.log.setText("Nova Launcher 2.0.0 - data folder: C:\\Users\\dalif\\AppData\\Roaming\\.novaclient\n"
                + "Built-in Nova Client 0.5.0 (Minecraft 1.21.4)\nLogged in as Dalif\nNova Client 0.5.0 is ready\n");
        if (playing) {
            app.playButton.setEnabled(false);
            app.bar.setValue(38);
            app.status.setText("Downloading game files (1520/3980)");
        } else {
            app.status.setText("Logged in as Dalif");
        }
        if (console) app.showPage("console");
        layoutAll(app.root);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        aa(g);
        app.root.paint(g);
        g.dispose();
        ImageIO.write(img, "png", new File(file));
    }

    private static void layoutAll(Container c) {
        c.invalidate();
        c.doLayout();
        for (Component k : c.getComponents()) if (k instanceof Container) layoutAll((Container) k);
    }

    private void startup() {
        log("Nova Launcher " + GameLauncher.VERSION + " - data folder: " + home);
        Embedded emb = Embedded.load();
        if (emb != null) {
            updateNote.setText("Nova Client " + emb.version + " is built in - it installs automatically when you press Play");
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
        // Look for a newer launcher / Nova version (does not install anything yet)
        if (!cfg.manifestUrl.isBlank()) {
            async(() -> {
                try {
                    var m = Http.getJson(cfg.manifestUrl);
                    String ver = Json.str(m, "version");
                    var l = Json.o(m.get("launcher"));
                    String lv = Json.str(l, "version"), lu = Json.str(l, "url");
                    SwingUtilities.invokeLater(() -> {
                        if (lv != null && Versions.cmp(lv, GameLauncher.VERSION) > 0) {
                            updateNote.setText("A new launcher (" + lv + ") is available - click here to download");
                            updateNote.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                            updateNote.addMouseListener(new MouseAdapter() {
                                public void mouseClicked(MouseEvent e) { if (lu != null) Platform.open(lu); }
                            });
                        } else if (ver != null) {
                            updateNote.setText("Nova Client " + ver + " will be installed/updated when you press Play");
                        }
                    });
                } catch (Exception ignored) {}
            });
        }
    }

    // ------------------------------------------------------------------ login
    private void toggleLogin(JLabel hint) {
        if (account != null && account.online) {
            account = null;
            cfg.refreshToken = "";
            cfg.save();
            who.setText("Not logged in");
            hint.setText("Playing offline as \"" + cfg.offlineName + "\"");
            skinPanel.setSkin(null);
            loginButton.setText("Log in with Microsoft");
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
            who.setText(a.name);
            hint.setText("Signed in with Microsoft");
            loginButton.setText("Log out");
            skinPanel.setSkin(fs, a.slim);
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
                skinPanel.setSkin(img, false);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, "Could not read that file: " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ play
    private void play() {
        if (busy) return;
        busy = true;
        playButton.setEnabled(false);
        async(() -> {
            try {
                Progress prog = new Progress() {
                    public void status(String m) { setStatus(m); log(m); }
                    public void bar(int d, int t) { SwingUtilities.invokeLater(() -> { bar.setIndeterminate(false); bar.setValue(t <= 0 ? 0 : d * 100 / t); }); }
                };
                SwingUtilities.invokeLater(() -> bar.setIndeterminate(true));

                NovaUpdater.Result res = NovaUpdater.sync(cfg, gameDir, prog);
                log("Nova Client: " + res.novaVersion() + "  |  Minecraft " + res.mcVersion());
                SwingUtilities.invokeLater(() -> { subtitle.setText(subtitleText(res.mcVersion())); chipVersion.setText("Minecraft " + res.mcVersion()); });

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
                SwingUtilities.invokeLater(() -> { bar.setIndeterminate(false); bar.setValue(100); });
                Process pr = GameLauncher.start(cmd, gameDir);
                try (BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) log(line);
                }
                int code = pr.waitFor();
                log("Game closed (exit code " + code + ")");
                setStatus(code == 0 ? "Ready" : "Game exited with code " + code + " - see the log");
            } catch (Exception e) {
                log("ERROR: " + e);
                setStatus("Error: " + e.getMessage());
                SwingUtilities.invokeLater(() -> bar.setIndeterminate(false));
            } finally {
                busy = false;
                SwingUtilities.invokeLater(() -> { playButton.setEnabled(true); bar.setValue(0); });
            }
        });
    }

    // ------------------------------------------------------------------ official launcher
    private void addToOfficial() {
        if (busy) return;
        busy = true;
        playButton.setEnabled(false);
        async(() -> {
            try {
                Progress prog = new Progress() {
                    public void status(String m) { setStatus(m); log(m); }
                    public void bar(int d, int t) {}
                };
                SwingUtilities.invokeLater(() -> bar.setIndeterminate(true));
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
                SwingUtilities.invokeLater(() -> { playButton.setEnabled(true); bar.setIndeterminate(false); bar.setValue(0); });
            }
        });
    }

    // ------------------------------------------------------------------ helpers
    private void async(Runnable r) {
        Thread t = new Thread(r, "nova-launcher-worker");
        t.setDaemon(true);
        t.start();
    }

    private void setStatus(String s) { SwingUtilities.invokeLater(() -> status.setText(s)); }

    private void log(String line) {
        SwingUtilities.invokeLater(() -> {
            log.append(line + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    static JLabel label(String text, int size, boolean bold, Color c) {
        JLabel l = new JLabel(text);
        l.setFont(font(bold ? Font.BOLD : Font.PLAIN, size));
        l.setForeground(c);
        return l;
    }

    /** A pixel skin used before you log in, so the hero card never looks empty. */
    static BufferedImage defaultSkin() {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        int[][] r = {{8, 8, 8, 8, 0xFFD9A066}, {20, 20, 8, 12, 0xFF6D28D9}, {44, 20, 4, 12, 0xFF7C3AED}, {36, 52, 4, 12, 0xFF7C3AED},
                {4, 20, 4, 12, 0xFF1E1B4B}, {20, 52, 4, 12, 0xFF1E1B4B}, {40, 8, 8, 4, 0xFF2B1B3D}, {8, 8, 8, 2, 0xFF2B1B3D},
                {20, 26, 8, 2, 0xFF22D3EE}, {8, 10, 1, 4, 0xFF2B1B3D}, {15, 10, 1, 4, 0xFF2B1B3D}};
        for (int[] q : r) for (int y = q[1]; y < q[1] + q[3]; y++) for (int x = q[0]; x < q[0] + q[2]; x++) skin.setRGB(x, y, q[4]);
        skin.setRGB(10, 12, 0xFFFFFFFF); skin.setRGB(13, 12, 0xFFFFFFFF);
        skin.setRGB(9, 12, 0xFF3B1D8A); skin.setRGB(14, 12, 0xFF3B1D8A);
        for (int x = 10; x < 14; x++) skin.setRGB(x, 14, 0xFFB45309);
        return skin;
    }

    // ------------------------------------------------------------------ glyphs (vector icons)
    enum Glyph {
        HOME, TERMINAL, SHIRT, FOLDER, GEAR, PLAY, PALETTE;

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
                case PLAY -> {
                    Path2D p = new Path2D.Double();
                    p.moveTo(cx - h * 0.5, cy - h * 0.7); p.lineTo(cx + h * 0.7, cy); p.lineTo(cx - h * 0.5, cy + h * 0.7); p.closePath();
                    g.fill(p);
                }
            }
        }
    }

    // ------------------------------------------------------------------ components
    static class RoundPanel extends JPanel {
        private final Color fill, border;
        private final int arc;
        RoundPanel(Color fill, Color border, int arc) { this.fill = fill; this.border = border; this.arc = arc; setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create(); aa(g2);
            g2.setColor(fill); g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            if (border != null) { g2.setColor(border); g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc); }
            g2.dispose();
        }
    }

    static final class TabPill extends JComponent {
        private final String text; private final boolean active;
        TabPill(String text, boolean active) { this.text = text; this.active = active; setPreferredSize(new Dimension(150, 40)); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create(); aa(g2);
            int w = getWidth(), h = getHeight();
            g2.setPaint(active ? new GradientPaint(0, 0, ACCENT, w, 0, CYAN) : new GradientPaint(0, 0, SURFACE, w, 0, SURFACE));
            g2.fillRoundRect(0, 0, w, h, 14, 14);
            g2.setColor(new Color(255, 255, 255, 40)); g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);
            g2.setFont(font(Font.BOLD, 17f));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(new Color(0, 0, 0, 90)); g2.drawString(text, (w - fm.stringWidth(text)) / 2 + 1, (h - fm.getHeight()) / 2 + fm.getAscent() + 1);
            g2.setColor(Color.WHITE); g2.drawString(text, (w - fm.stringWidth(text)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    static final class SideButton extends JComponent {
        private final Glyph glyph; boolean selected, hover;
        SideButton(Glyph glyph, String tip, Runnable action) {
            this.glyph = glyph; setToolTipText(tip);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                public void mouseClicked(MouseEvent e) { action.run(); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create(); aa(g2);
            int w = getWidth(), h = getHeight();
            if (selected || hover) { g2.setColor(selected ? alpha(ACCENT, 46) : new Color(255, 255, 255, 14)); g2.fillRoundRect(0, 0, w, h, 14, 14); }
            if (selected) { g2.setColor(ACCENT); g2.fillRoundRect(-3, h / 2 - 11, 5, 22, 5, 5); }
            glyph.paint(g2, w / 2.0, h / 2.0, 22, selected ? mix(ACCENT, Color.WHITE, 0.6f) : hover ? Color.WHITE : MUTED);
            g2.dispose();
        }
    }

    /** Dusk sky with twinkling stars and blocky terrain, in Nova colours. */
    static final class Hero extends JPanel {
        private static final int N = 90;
        private final float[] sx = new float[N], sy = new float[N], sp = new float[N];
        Hero() {
            setOpaque(false);
            Random r = new Random(7);
            for (int i = 0; i < N; i++) { sx[i] = r.nextFloat(); sy[i] = r.nextFloat() * 0.78f; sp[i] = r.nextFloat() * 6.28f; }
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create(); aa(g2);
            int w = getWidth(), h = getHeight(), arc = 26;
            Shape clip = new RoundRectangle2D.Double(0, 0, w, h, arc, arc);
            g2.setClip(clip);
            g2.setPaint(new LinearGradientPaint(0, 0, 0, h, new float[]{0f, 0.55f, 0.85f, 1f},
                    new Color[]{mix(ACCENT_DARK, Color.BLACK, 0.88f), mix(ACCENT_DARK, Color.BLACK, 0.45f), ACCENT, mix(CYAN, Color.WHITE, 0.25f)}));
            g2.fillRect(0, 0, w, h);
            double t = System.nanoTime() / 1e9;
            for (int i = 0; i < N; i++) {
                float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * 1.6 + sp[i]));
                g2.setColor(new Color(255, 255, 255, (int) (tw * 200)));
                int s = i % 7 == 0 ? 3 : 2;
                g2.fillRect((int) (sx[i] * w), (int) (sy[i] * h), s, s);
            }
            // moon glow
            g2.setPaint(new RadialGradientPaint(new Point2D.Double(w * 0.82, h * 0.2), h * 0.35f, new float[]{0f, 1f},
                    new Color[]{alpha(CYAN, 90), alpha(CYAN, 0)}));
            g2.fillRect(0, 0, w, h);
            g2.setColor(mix(CYAN, Color.WHITE, 0.8f)); g2.fillRect((int) (w * 0.82) - 14, (int) (h * 0.2) - 14, 28, 28);
            // blocky hills (two layers)
            blocks(g2, w, h, 0.72, 34, mix(ACCENT_DARK, Color.BLACK, 0.55f), 3);
            blocks(g2, w, h, 0.82, 26, mix(ACCENT_DARK, Color.BLACK, 0.8f), 11);
            // top shade so chips stay readable
            g2.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 120), 0, 90, new Color(0, 0, 0, 0)));
            g2.fillRect(0, 0, w, 90);
            g2.setClip(null);
            g2.setColor(BORDER); g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, arc, arc));
            g2.dispose();
        }
        private void blocks(Graphics2D g2, int w, int h, double base, int cell, Color c, long seed) {
            Random r = new Random(seed);
            g2.setColor(c);
            int y = (int) (h * base);
            for (int x = 0; x < w; x += cell) {
                y += (r.nextInt(3) - 1) * cell / 2;
                y = Math.max((int) (h * (base - 0.08)), Math.min((int) (h * (base + 0.06)), y));
                g2.fillRect(x, y, cell + 1, h - y);
            }
        }
    }

    static final class Banner extends JPanel {
        Banner() { setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create(); aa(g2);
            int w = getWidth(), h = getHeight();
            Shape clip = new RoundRectangle2D.Double(0, 0, w, h, 18, 18);
            g2.setClip(clip);
            g2.setPaint(new GradientPaint(0, 0, mix(ACCENT_DARK, Color.BLACK, 0.1f), w, h, mix(CYAN, Color.BLACK, 0.5f)));
            g2.fillRect(0, 0, w, h);
            g2.setFont(font(Font.BOLD, 76f));
            g2.setColor(new Color(255, 255, 255, 26));
            g2.drawString("NOVA", w - g2.getFontMetrics().stringWidth("NOVA") - 8, h / 2 + 6);
            g2.setPaint(new GradientPaint(0, h * 0.3f, new Color(0, 0, 0, 0), 0, h, new Color(0, 0, 0, 170)));
            g2.fillRect(0, 0, w, h);
            g2.setClip(null);
            g2.setColor(BORDER); g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 18, 18));
            g2.dispose();
        }
    }

    static final class ProfileRow extends JPanel {
        private final boolean featured; private final JPanel actions;
        ProfileRow(String title, JLabel sub, boolean featured, int height) {
            this.featured = featured;
            setOpaque(false);
            setLayout(new BorderLayout(14, 0));
            setBorder(new EmptyBorder(10, 14, 10, 14));
            setAlignmentX(0);
            setPreferredSize(new Dimension(100, height));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
            int ic = height - 22;
            JComponent icon = new JComponent() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create(); aa(g2);
                    g2.setPaint(new GradientPaint(0, 0, ACCENT, getWidth(), getHeight(), CYAN));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                    g2.setColor(Color.WHITE); g2.setFont(font(Font.BOLD, getHeight() * 0.5f));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString("N", (getWidth() - fm.stringWidth("N")) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                    g2.dispose();
                }
            };
            icon.setPreferredSize(new Dimension(ic, ic));
            JPanel iconWrap = new JPanel(new GridBagLayout()); iconWrap.setOpaque(false); iconWrap.add(icon);
            add(iconWrap, BorderLayout.WEST);
            JPanel text = new JPanel(new GridLayout(2, 1, 0, 2)); text.setOpaque(false);
            JLabel t = label(title, featured ? 19 : 15, true, TEXT);
            text.add(t); text.add(sub);
            JPanel tw = new JPanel(new GridBagLayout()); tw.setOpaque(false); tw.add(text, new GridBagConstraints() {{ anchor = WEST; weightx = 1; }});
            add(tw, BorderLayout.CENTER);
            actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)); actions.setOpaque(false);
            JPanel aw = new JPanel(new GridBagLayout()); aw.setOpaque(false); aw.add(actions);
            add(aw, BorderLayout.EAST);
        }
        void addAction(JComponent c) { actions.add(c); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create(); aa(g2);
            int w = getWidth(), h = getHeight();
            g2.setPaint(featured ? new GradientPaint(0, 0, mix(ACCENT_DARK, SURFACE, 0.82f), w, 0, SURFACE) : new GradientPaint(0, 0, SURFACE, w, 0, SURFACE));
            g2.fillRoundRect(0, 0, w - 1, h - 1, 20, 20);
            g2.setColor(featured ? alpha(ACCENT, 90) : BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 20, 20);
            g2.dispose();
        }
    }

    static final class PlayButton extends JButton {
        private final boolean compact; private boolean hover;
        PlayButton(boolean compact) {
            this.compact = compact;
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(compact ? new Dimension(44, 44) : new Dimension(150, 46));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create(); aa(g2);
            int w = getWidth(), h = getHeight();
            boolean on = isEnabled();
            double pulse = 0.5 + 0.5 * Math.sin(System.nanoTime() / 4e8);
            if (on) {
                g2.setColor(new Color(34, 197, 94, (int) (hover ? 70 : 28 + 22 * pulse)));
                g2.fillRoundRect(-2, -2, w + 3, h + 3, 18, 18);
            }
            g2.setPaint(on ? new GradientPaint(0, 0, hover ? new Color(0x4ADE80) : GREEN_BTN, 0, h, GREEN_DARK) : new GradientPaint(0, 0, new Color(0x3F3F52), 0, h, new Color(0x2A2A3A)));
            g2.fillRoundRect(0, 0, w - 1, h - 1, compact ? 14 : 16, compact ? 14 : 16);
            g2.setColor(new Color(255, 255, 255, on ? 70 : 25)); g2.drawRoundRect(0, 0, w - 1, h - 1, compact ? 14 : 16, compact ? 14 : 16);
            if (compact) {
                Glyph.PLAY.paint(g2, w / 2.0 + 1, h / 2.0, 18, Color.WHITE);
            } else {
                String s = on ? "Play" : "Launching...";
                g2.setFont(font(Font.BOLD, 17f));
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(s) + (on ? 28 : 0);
                int x = (w - tw) / 2;
                if (on) { Glyph.PLAY.paint(g2, x + 8, h / 2.0, 16, Color.WHITE); x += 28; }
                g2.setColor(Color.WHITE);
                g2.drawString(s, x, (h - fm.getHeight()) / 2 + fm.getAscent());
            }
            g2.dispose();
        }
    }

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
            if (!isEnabled()) g2.setColor(new Color(60, 60, 78));
            else if (primary) g2.setPaint(new GradientPaint(0, 0, hover ? new Color(0xA78BFA) : ACCENT, 0, h, ACCENT_DARK));
            else g2.setColor(hover ? new Color(0x2A2A3C) : SURFACE2);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);
            g2.setColor(hover && isEnabled() ? alpha(CYAN, 160) : new Color(255, 255, 255, 30));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);
            if (glyph != null) glyph.paint(g2, w / 2.0, h / 2.0, 20, hover ? Color.WHITE : MUTED);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Color.WHITE);
            g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    static final class NovaBar extends JComponent {
        private int value; private boolean indeterminate;
        void setValue(int v) { value = Math.max(0, Math.min(100, v)); repaint(); }
        void setIndeterminate(boolean b) { indeterminate = b; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create(); aa(g2);
            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(255, 255, 255, 22)); g2.fillRoundRect(0, 0, w, h, h, h);
            int bw, bx = 0;
            if (indeterminate) { bw = w / 3; bx = (int) ((System.nanoTime() / 6_000_000L) % (w + bw)) - bw; }
            else bw = w * value / 100;
            if (bw > 0) {
                g2.setClip(new RoundRectangle2D.Double(0, 0, w, h, h, h));
                g2.setPaint(new GradientPaint(bx, 0, ACCENT, bx + bw, 0, CYAN));
                g2.fillRoundRect(bx, 0, bw, h, h, h);
            }
            g2.dispose();
        }
    }

    /** Shows the player's skin on a glowing platform with a gentle float animation. */
    static final class SkinPanel extends JPanel {
        private BufferedImage rendered = SkinRenderer.toImage(SkinRenderer.front(defaultSkin(), false), 1);

        SkinPanel() { setOpaque(false); }
        void setSkin(BufferedImage skin) { setSkin(skin, false); }
        void setSkin(BufferedImage skin, boolean slim) {
            rendered = SkinRenderer.toImage(SkinRenderer.front(skin == null ? defaultSkin() : skin, slim), 1);
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create(); aa(g2);
            int w = getWidth(), h = getHeight();
            int scale = Math.max(2, Math.min((int) (w * 0.5) / SkinRenderer.W, (h - 70) / SkinRenderer.H));
            int dw = SkinRenderer.W * scale, dh = SkinRenderer.H * scale;
            double bob = Math.sin(System.nanoTime() / 6e8) * 4;
            int x = (w - dw) / 2, floor = h - 14;
            int top = (int) (floor - 12 - dh + bob);
            // platform glow + disc
            g2.setPaint(new RadialGradientPaint(new Point2D.Double(w / 2.0, floor), dw * 0.9f, new float[]{0f, 1f},
                    new Color[]{alpha(CYAN, 110), alpha(CYAN, 0)}));
            g2.fill(new Ellipse2D.Double(w / 2.0 - dw * 0.9, floor - dw * 0.22, dw * 1.8, dw * 0.44));
            g2.setColor(new Color(15, 8, 40, 235)); g2.fill(new Ellipse2D.Double(w / 2.0 - dw * 0.6, floor - dw * 0.12, dw * 1.2, dw * 0.24));
            g2.setColor(alpha(CYAN, 170)); g2.setStroke(new BasicStroke(2f));
            g2.draw(new Ellipse2D.Double(w / 2.0 - dw * 0.6, floor - dw * 0.12, dw * 1.2, dw * 0.24));
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.drawImage(rendered, x, top, dw, dh, null);
            g2.dispose();
        }
    }
}

package com.nova.launcher;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

/** The Nova Launcher window. */
public final class LauncherApp {
    static final Color BG1 = new Color(0x070714), BG2 = new Color(0x2A1250);
    static final Color ACCENT = new Color(0x8B5CF6), ACCENT_DARK = new Color(0x5B34B8), CYAN = new Color(0x22D3EE);
    static final Color TEXT = Color.WHITE, MUTED = new Color(0x9CA3AF), GREEN = new Color(0x34D399), RED = new Color(0xF87171);

    private final LauncherConfig cfg = LauncherConfig.load();
    private final Path home = Platform.home();
    private final Path gameDir = home.resolve("instances").resolve("default");

    private JFrame frame;
    private BgPanel bg;
    private SkinPanel skinPanel;
    private NovaButton playButton, loginButton;
    private JLabel status, who, updateNote, subtitle, hint;
    private JProgressBar bar;
    private JTextArea log;
    private volatile Account account;
    private volatile boolean busy;

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        SwingUtilities.invokeLater(() -> new LauncherApp().show());
    }

    // ------------------------------------------------------------------ UI
    private void show() {
        frame = new JFrame("Nova Launcher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(940, 580);
        frame.setMinimumSize(new Dimension(820, 520));
        frame.setLocationRelativeTo(null);

        buildUi();
        frame.setContentPane(bg);
        frame.setVisible(true);
        startup();
    }

    private void buildUi() {
        bg = new BgPanel();
        bg.setLayout(new BorderLayout(20, 0));
        bg.setBorder(new EmptyBorder(24, 24, 24, 24));
        bg.reloadImage(cfg.backgroundPath);

        // ----- left: skin + account
        JPanel left = card(new BorderLayout(0, 10));
        left.setPreferredSize(new Dimension(250, 0));
        skinPanel = new SkinPanel();
        left.add(skinPanel, BorderLayout.CENTER);
        JPanel acct = new JPanel();
        acct.setOpaque(false);
        acct.setLayout(new BoxLayout(acct, BoxLayout.Y_AXIS));
        who = label("Not logged in", 16, true, TEXT);
        hint = label("Playing offline as \"" + cfg.offlineName + "\"", 12, false, MUTED);
        who.setAlignmentX(Component.CENTER_ALIGNMENT);
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton = new NovaButton("Log in with Microsoft", false);
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton.setMaximumSize(new Dimension(210, 34));
        loginButton.addActionListener(e -> toggleLogin(hint));
        NovaButton previewBtn = new NovaButton("Preview a skin file", false);
        previewBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        previewBtn.setMaximumSize(new Dimension(210, 34));
        previewBtn.addActionListener(e -> previewSkinFile());
        acct.add(who); acct.add(Box.createVerticalStrut(2)); acct.add(hint);
        acct.add(Box.createVerticalStrut(10)); acct.add(loginButton);
        acct.add(Box.createVerticalStrut(6)); acct.add(previewBtn);
        left.add(acct, BorderLayout.SOUTH);
        bg.add(left, BorderLayout.WEST);

        // ----- right: title, play, progress, log
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

        JLabel title = label("NOVA CLIENT", 40, true, new Color(0xE9D5FF));
        subtitle = label("Minecraft " + (cfg.mcVersion.isBlank() ? NovaUpdater.DEFAULT_MC : cfg.mcVersion) + "  \u2022  Fabric  \u2022  Sodium + Iris", 14, false, CYAN);
        updateNote = label(" ", 13, false, GREEN);
        title.setAlignmentX(0); subtitle.setAlignmentX(0); updateNote.setAlignmentX(0);
        right.add(title); right.add(subtitle); right.add(Box.createVerticalStrut(6)); right.add(updateNote);
        right.add(Box.createVerticalGlue());

        playButton = new NovaButton("PLAY", true);
        playButton.setFont(playButton.getFont().deriveFont(Font.BOLD, 22f));
        playButton.setAlignmentX(0);
        playButton.setMaximumSize(new Dimension(340, 60));
        playButton.setPreferredSize(new Dimension(340, 60));
        playButton.addActionListener(e -> play());
        right.add(playButton);
        right.add(Box.createVerticalStrut(14));

        bar = new JProgressBar(0, 100);
        bar.setAlignmentX(0);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
        bar.setBorderPainted(false);
        bar.setForeground(ACCENT);
        bar.setBackground(new Color(255, 255, 255, 40));
        right.add(bar);
        status = label("Ready", 13, false, MUTED);
        status.setAlignmentX(0);
        right.add(Box.createVerticalStrut(6)); right.add(status);
        right.add(Box.createVerticalStrut(10));

        log = new JTextArea(7, 40);
        log.setEditable(false);
        log.setOpaque(true);
        log.setBackground(new Color(8, 8, 20));
        log.setForeground(new Color(0xC4B5FD));
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane sp = new JScrollPane(log);
        sp.setOpaque(true);
        sp.getViewport().setOpaque(true);
        sp.getViewport().setBackground(new Color(8, 8, 20));
        sp.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 40)));
        sp.setAlignmentX(0);
        sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
        right.add(sp);
        right.add(Box.createVerticalStrut(12));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(0);
        NovaButton settings = new NovaButton("Settings", false);
        settings.addActionListener(e -> new SettingsDialog(frame, cfg, () -> {
            bg.reloadImage(cfg.backgroundPath);
            subtitle.setText("Minecraft " + (cfg.mcVersion.isBlank() ? NovaUpdater.DEFAULT_MC : cfg.mcVersion) + "  \u2022  Fabric  \u2022  Sodium + Iris");
            bg.repaint();
        }).setVisible(true));
        NovaButton folder = new NovaButton("Game folder", false);
        folder.addActionListener(e -> { try { Files.createDirectories(gameDir); } catch (Exception ignored) {} Platform.openFolder(gameDir); });
        NovaButton official = new NovaButton("Add to Minecraft Launcher", false);
        official.addActionListener(e -> addToOfficial());
        row.add(settings); row.add(folder); row.add(official);
        right.add(row);
        bg.add(right, BorderLayout.CENTER);

    }

    /** Renders the launcher UI to a PNG without opening a window (used for docs/screenshots). */
    static void preview(String file, boolean playing) throws Exception {
        LauncherApp app = new LauncherApp();
        app.buildUi();
        int w = 940, h = 580;
        app.bg.setSize(w, h);
        layoutAll(app.bg);
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        int[][] r = {{8, 8, 8, 8, 0xFFC68642}, {20, 20, 8, 12, 0xFF3B82F6}, {44, 20, 4, 12, 0xFFC68642}, {36, 52, 4, 12, 0xFFC68642},
                {4, 20, 4, 12, 0xFF1E3A8A}, {20, 52, 4, 12, 0xFF1E3A8A}, {40, 8, 8, 4, 0xFF3A2410}, {8, 8, 8, 2, 0xFF3A2410}};
        for (int[] q : r) for (int y = q[1]; y < q[1] + q[3]; y++) for (int x = q[0]; x < q[0] + q[2]; x++) skin.setRGB(x, y, q[4]);
        skin.setRGB(10, 11, 0xFF111111); skin.setRGB(13, 11, 0xFF111111);
        app.skinPanel.setSkin(skin, false);
        app.who.setText("Dalif");
        app.hint.setText("Signed in with Microsoft");
        app.loginButton.setText("Log out");
        app.updateNote.setText("Nova Client 0.4.0 is built in - it installs automatically when you press Play");
        app.log.setText("Nova Launcher 1.0.0 - data folder: C:\\Users\\dalif\\AppData\\Roaming\\.novaclient\n"
                + "Built-in Nova Client 0.4.0 (Minecraft 1.21.4)\nLogged in as Dalif\nNova Client 0.4.0 is ready\n");
        if (playing) {
            app.playButton.setEnabled(false);
            app.bar.setValue(38);
            app.status.setText("Downloading game files (1520/3980)");
            app.log.append("Reading Minecraft version list...\nReading Fabric version list...\nChecking game files...\nDownloading game files (1520/3980)\n");
        } else {
            app.status.setText("Logged in as Dalif");
        }
        layoutAll(app.bg);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        app.bg.paint(g);
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
                            updateNote.setForeground(CYAN);
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
                SwingUtilities.invokeLater(() -> subtitle.setText("Minecraft " + res.mcVersion() + "  \u2022  Fabric  \u2022  Sodium + Iris"));

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
        l.setFont(l.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) size));
        l.setForeground(c);
        return l;
    }

    private static JPanel card(LayoutManager lm) {
        JPanel p = new JPanel(lm) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(10, 10, 28, 215));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
                g2.setColor(new Color(255, 255, 255, 40));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 22, 22);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(16, 16, 16, 16));
        return p;
    }

    // ------------------------------------------------------------------ components
    /** Window background: custom image (if set) or an animated-look gradient. */
    static final class BgPanel extends JPanel {
        private BufferedImage image;

        void reloadImage(String path) {
            image = null;
            try {
                if (path != null && !path.isBlank() && Files.isRegularFile(Path.of(path))) image = ImageIO.read(new File(path));
            } catch (Exception ignored) {}
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int w = getWidth(), h = getHeight();
            if (image != null) {
                double s = Math.max((double) w / image.getWidth(), (double) h / image.getHeight());
                int dw = (int) Math.ceil(image.getWidth() * s), dh = (int) Math.ceil(image.getHeight() * s);
                g2.drawImage(image, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
                g2.setColor(new Color(5, 5, 20, 150));
                g2.fillRect(0, 0, w, h);
            } else {
                g2.setPaint(new GradientPaint(0, 0, BG1, 0, h, BG2));
                g2.fillRect(0, 0, w, h);
                g2.setPaint(new RadialGradientPaint(new Point(w / 4, h / 3), Math.min(w, h) * 0.6f,
                        new float[]{0f, 1f}, new Color[]{new Color(139, 92, 246, 70), new Color(139, 92, 246, 0)}));
                g2.fillRect(0, 0, w, h);
                g2.setPaint(new RadialGradientPaint(new Point(w * 4 / 5, h * 4 / 5), Math.min(w, h) * 0.5f,
                        new float[]{0f, 1f}, new Color[]{new Color(34, 211, 238, 55), new Color(34, 211, 238, 0)}));
                g2.fillRect(0, 0, w, h);
            }
            g2.dispose();
        }
    }

    static final class NovaButton extends JButton {
        private final boolean primary;
        private boolean hover;

        NovaButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setForeground(Color.WHITE);
            setFont(getFont().deriveFont(Font.BOLD, 13f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(8, 16, 8, 16));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (!isEnabled()) g2.setColor(new Color(80, 80, 100));
            else if (primary) g2.setPaint(new GradientPaint(0, 0, hover ? new Color(0xA78BFA) : ACCENT, 0, h, ACCENT_DARK));
            else g2.setColor(hover ? new Color(0x2A2A52) : new Color(0x181830));
            g2.fillRoundRect(0, 0, w, h, 14, 14);
            g2.setColor(hover && isEnabled() ? CYAN : new Color(255, 255, 255, 50));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Color.WHITE);
            g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** Shows the player's skin, front view, scaled up with crisp pixels. */
    static final class SkinPanel extends JPanel {
        private BufferedImage rendered;

        SkinPanel() { setOpaque(false); }

        void setSkin(BufferedImage skin) { setSkin(skin, false); }

        void setSkin(BufferedImage skin, boolean slim) {
            rendered = skin == null ? null : SkinRenderer.toImage(SkinRenderer.front(skin, slim), 1);
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth(), h = getHeight();
            if (rendered == null) {
                g2.setColor(MUTED);
                g2.setFont(getFont().deriveFont(13f));
                String s = "Log in to see your skin";
                g2.drawString(s, (w - g2.getFontMetrics().stringWidth(s)) / 2, h / 2);
            } else {
                int scale = Math.max(1, Math.min(w / SkinRenderer.W, h / SkinRenderer.H));
                int dw = SkinRenderer.W * scale, dh = SkinRenderer.H * scale;
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.setColor(new Color(0, 0, 0, 60));
                g2.fillOval((w - dw) / 2, (h + dh) / 2 - 10, dw, 20); // soft floor shadow
                g2.drawImage(rendered, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
            }
            g2.dispose();
        }
    }
}

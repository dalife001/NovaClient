package com.nova.launcher;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Browse compatible mods, resource packs and shaders from Modrinth, inspect details, then install.
 * More results load automatically as you scroll.
 */
final class ModrinthDialog extends JDialog {
    private static final int PAGE = 20, ICON = 56;

    private final Path versionDir, cacheDir;
    private final Supplier<String> mc;
    private final JPanel rows = new JPanel();
    private final JScrollPane scroll;
    private final JTextField query = new JTextField();
    private final JLabel status = LauncherApp.label(" ", 12, false, LauncherApp.MUTED);
    private final LauncherApp.NovaButton[] tabs = new LauncherApp.NovaButton[Modrinth.Kind.values().length];
    private final LauncherApp.NovaButton popular = new LauncherApp.NovaButton("Popular", true);
    private final LauncherApp.NovaButton newest = new LauncherApp.NovaButton("New", false);
    private final Map<String, BufferedImage> icons = new ConcurrentHashMap<>();
    private final Map<String, Row> rowById = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(6, r -> { Thread t = new Thread(r, "nova-modrinth"); t.setDaemon(true); return t; });
    private final javax.swing.Timer debounce;

    private Modrinth.Kind kind = Modrinth.Kind.MOD;
    private String sort = "downloads";
    private int offset, total, generation;
    private boolean loading;

    ModrinthDialog(Window owner, Path versionDir, Supplier<String> mc) {
        super(owner, "Get mods, packs and shaders", ModalityType.MODELESS);
        this.versionDir = versionDir;
        this.mc = mc;
        Path home;
        try { home = versionDir.toAbsolutePath().getParent().getParent().getParent().getParent(); } catch (Exception e) { home = Path.of(System.getProperty("java.io.tmpdir")); }
        this.cacheDir = home.resolve("icon-cache");

        // ---- top: title, tabs, search
        JLabel title = LauncherApp.label("Get mods, packs and shaders", 22, true, LauncherApp.TEXT);
        JLabel sub = LauncherApp.label("For Minecraft " + mc.get() + ". Press Install: it is ready next time you play.", 13, false, LauncherApp.MUTED);

        JPanel tabRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tabRow.setOpaque(false);
        for (Modrinth.Kind k : Modrinth.Kind.values()) {
            LauncherApp.NovaButton b = new LauncherApp.NovaButton(k.label, k == kind);
            b.addActionListener(e -> switchKind(k));
            tabs[k.ordinal()] = b;
            tabRow.add(b);
        }
        popular.addActionListener(e -> setSort("downloads"));
        newest.addActionListener(e -> setSort("newest"));
        JPanel sortRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        sortRow.setOpaque(false);
        sortRow.add(popular);
        sortRow.add(newest);
        JPanel tabsAndSort = new JPanel(new BorderLayout());
        tabsAndSort.setOpaque(false);
        tabsAndSort.add(tabRow, BorderLayout.WEST);
        tabsAndSort.add(sortRow, BorderLayout.EAST);

        query.setFont(LauncherApp.font(Font.PLAIN, 15f));
        query.setBackground(LauncherApp.SURFACE);
        query.setForeground(LauncherApp.TEXT);
        query.setCaretColor(LauncherApp.TEXT);
        query.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LauncherApp.BORDER), new EmptyBorder(9, 12, 9, 12)));
        query.putClientProperty("JTextField.placeholderText", "Search Modrinth...");
        debounce = new javax.swing.Timer(350, e -> search(true));
        debounce.setRepeats(false);
        query.addActionListener(e -> { debounce.stop(); search(true); });
        LauncherApp.NovaButton searchButton = new LauncherApp.NovaButton("Search", true);
        searchButton.addActionListener(e -> { debounce.stop(); search(true); });
        query.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { debounce.restart(); }
            public void removeUpdate(DocumentEvent e) { debounce.restart(); }
            public void changedUpdate(DocumentEvent e) { debounce.restart(); }
        });

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        searchRow.add(query, BorderLayout.CENTER);
        searchRow.add(searchButton, BorderLayout.EAST);

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[]{title, sub, tabsAndSort, searchRow}) c.setAlignmentX(LEFT_ALIGNMENT);
        top.add(title);
        top.add(Box.createVerticalStrut(2));
        top.add(sub);
        top.add(Box.createVerticalStrut(14));
        top.add(tabsAndSort);
        top.add(Box.createVerticalStrut(10));
        top.add(searchRow);

        // ---- the results
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setBackground(LauncherApp.SURFACE);
        scroll = new JScrollPane(rows, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(LauncherApp.BORDER));
        scroll.getViewport().setBackground(LauncherApp.SURFACE);
        scroll.getVerticalScrollBar().setUnitIncrement(28);
        scroll.getVerticalScrollBar().addAdjustmentListener(e -> {
            JScrollBar sb = scroll.getVerticalScrollBar();
            if (!loading && offset < total && sb.getValue() + sb.getVisibleAmount() >= sb.getMaximum() - 240) search(false);
        });

        LauncherApp.NovaButton close = new LauncherApp.NovaButton("Done", false);
        close.addActionListener(e -> dispose());
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(status, BorderLayout.CENTER);
        south.add(close, BorderLayout.EAST);

        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBackground(LauncherApp.BG);
        p.setBorder(new EmptyBorder(22, 26, 18, 26));
        p.add(top, BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        p.add(south, BorderLayout.SOUTH);
        setContentPane(p);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setPreferredSize(new Dimension(900, 720));
        pack();
        setLocationRelativeTo(owner);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { pool.shutdownNow(); debounce.stop(); }
            @Override public void windowOpened(java.awt.event.WindowEvent e) { query.requestFocusInWindow(); }
        });
        search(true);
    }

    private void switchKind(Modrinth.Kind k) {
        if (k == kind) return;
        kind = k;
        for (Modrinth.Kind x : Modrinth.Kind.values()) tabs[x.ordinal()].setPrimary(x == kind);
        search(true);
    }

    private void setSort(String s) {
        if (s.equals(sort)) return;
        sort = s;
        popular.setPrimary(s.equals("downloads"));
        newest.setPrimary(s.equals("newest"));
        search(true);
    }

    // ------------------------------------------------------------------ searching
    private void search(boolean fresh) {
        if (fresh) {
            offset = 0; total = 0;
            rows.removeAll();
            rowById.clear();
            rows.revalidate();
            rows.repaint();
            scroll.getVerticalScrollBar().setValue(0);
        }
        int gen = ++generation;
        loading = true;
        Modrinth.Kind k = kind;
        String q = query.getText(), version = mc.get(), s = q.isBlank() ? sort : (sort.equals("newest") ? "newest" : "relevance");
        int off = offset;
        status.setText(fresh ? "Searching..." : "Loading more...");
        pool.submit(() -> {
            try {
                Modrinth.Page page = Modrinth.search(k, q, version, s, off);
                SwingUtilities.invokeLater(() -> {
                    if (gen != generation) return;
                    loading = false;
                    for (Modrinth.Hit h : page.hits()) {
                        Row r = new Row(h);
                        rowById.put(h.id(), r);
                        rows.add(r);
                        loadIcon(h);
                    }
                    offset += PAGE;
                    total = page.total();
                    rows.revalidate();
                    rows.repaint();
                    status.setText(rows.getComponentCount() == 0
                            ? "Nothing found for Minecraft " + version + ". Try another word, or another tab."
                            : total + " results");
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (gen != generation) return;
                    loading = false;
                    status.setText("Could not reach Modrinth. Check your internet connection. (" + e.getMessage() + ")");
                });
            }
        });
    }

    // ------------------------------------------------------------------ icons
    private void loadIcon(Modrinth.Hit h) {
        BufferedImage have = icons.get(h.id());
        if (have != null) return;
        pool.submit(() -> {
            BufferedImage img = fetchIcon(h);
            if (img == null) return;
            icons.put(h.id(), img);
            SwingUtilities.invokeLater(() -> { Row r = rowById.get(h.id()); if (r != null) r.repaint(); });
        });
    }

    private BufferedImage fetchIcon(Modrinth.Hit h) {
        Path cached = cacheDir.resolve(h.id() + ".png");
        try {
            if (Files.isRegularFile(cached)) { BufferedImage c = ImageIO.read(cached.toFile()); if (c != null) return c; }
        } catch (Exception ignored) {}
        for (String url : Modrinth.iconCandidates(h.iconUrl())) {
            try {
                BufferedImage raw = ImageIO.read(new ByteArrayInputStream(Http.getBytes(url)));
                if (raw == null) continue;
                BufferedImage small = new BufferedImage(ICON * 2, ICON * 2, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = small.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(raw.getScaledInstance(ICON * 2, ICON * 2, Image.SCALE_SMOOTH), 0, 0, null);
                g.dispose();
                try { Files.createDirectories(cacheDir); ImageIO.write(small, "png", cached.toFile()); } catch (Exception ignored) {}
                return small;
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ------------------------------------------------------------------ one result
    private final class Row extends JPanel {
        private final Modrinth.Hit hit;
        private final JLabel tag = LauncherApp.label("", 12, true, LauncherApp.GREEN);
        private final LauncherApp.NovaButton action = new LauncherApp.NovaButton("Install", true);
        private boolean working;

        Row(Modrinth.Hit h) {
            super(new BorderLayout(14, 0));
            this.hit = h;
            setBackground(LauncherApp.SURFACE);
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LauncherApp.BORDER), new EmptyBorder(11, 14, 11, 14)));
            setPreferredSize(new Dimension(100, 88));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));
            setAlignmentX(LEFT_ALIGNMENT);

            JComponent iconBox = new JComponent() {
                @Override protected void paintComponent(Graphics g0) { paintIcon((Graphics2D) g0, getWidth(), getHeight()); }
            };
            iconBox.setPreferredSize(new Dimension(ICON, ICON));

            JLabel name = LauncherApp.label(h.title(), 15, true, LauncherApp.TEXT);
            JLabel meta = LauncherApp.label("by " + h.author() + "  \u00B7  " + count(h.downloads()) + " downloads", 12, false, LauncherApp.MUTED);
            String d = h.description();
            JLabel desc = LauncherApp.label(d.length() > 120 ? d.substring(0, 117) + "..." : d, 12, false, LauncherApp.DIM);
            JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            nameRow.setOpaque(false);
            tag.setBorder(new EmptyBorder(0, 10, 0, 0));
            nameRow.add(name);
            nameRow.add(tag);
            JPanel text = new JPanel(new GridLayout(3, 1, 0, 1));
            text.setOpaque(false);
            text.add(nameRow);
            text.add(meta);
            text.add(desc);

            action.addActionListener(e -> toggle());
            LauncherApp.NovaButton details = new LauncherApp.NovaButton("Details", false);
            details.addActionListener(e -> showDetails(hit, this));
            JPanel east = new JPanel(new GridLayout(2, 1, 0, 5));
            east.setOpaque(false);
            east.add(details);
            east.add(action);
            setToolTipText("Inspect this project on Modrinth before installing.");

            java.awt.event.MouseAdapter openDetails = new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) { Platform.open(Modrinth.pageUrl(hit)); }
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); }
                @Override public void mouseExited(java.awt.event.MouseEvent e) { setCursor(Cursor.getDefaultCursor()); }
            };
            iconBox.addMouseListener(openDetails);
            name.addMouseListener(openDetails);
            meta.addMouseListener(openDetails);
            desc.addMouseListener(openDetails);
            add(iconBox, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            add(east, BorderLayout.EAST);
            showState();
        }

        private boolean installed() { return Modrinth.installed(versionDir, hit.kind()).containsKey(hit.id()); }

        private void showState() {
            boolean in = installed();
            tag.setText(in ? "\u2713 installed" : "");
            action.setText(working ? "Installing..." : in ? "Remove" : "Install");
            action.setPrimary(!in && !working);
            action.setEnabled(!working);
        }

        private void toggle() {
            if (working) return;
            if (installed()) {
                try {
                    Modrinth.remove(hit.id(), hit.kind(), versionDir);
                    status.setText(hit.title() + " removed");
                } catch (Exception e) {
                    status.setText("Could not remove " + hit.title() + ": " + e.getMessage());
                }
                showState();
                return;
            }
            working = true;
            showState();
            String version = mc.get();
            pool.submit(() -> {
                String result;
                try {
                    List<String> added = Modrinth.install(hit, version, versionDir, new Progress() {
                        public void status(String m) { SwingUtilities.invokeLater(() -> ModrinthDialog.this.status.setText(m)); }
                        public void bar(int d, int t) {}
                    });
                    int extra = Math.max(0, added.size() - 1);
                    result = hit.title() + " installed" + (extra > 0 ? " (plus " + extra + " mod" + (extra > 1 ? "s" : "") + " it needs)" : "") + ". It loads next time you press Play.";
                } catch (Exception e) {
                    result = "Could not install " + hit.title() + ": " + e.getMessage();
                }
                String msg = result;
                SwingUtilities.invokeLater(() -> {
                    working = false;
                    ModrinthDialog.this.status.setText(msg);
                    showState();
                });
            });
        }

        private void paintIcon(Graphics2D g, int w, int h) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            Shape clip = new RoundRectangle2D.Float(0, 0, w, h, 14, 14);
            g.setClip(clip);
            BufferedImage img = icons.get(hit.id());
            if (img != null) {
                g.drawImage(img, 0, 0, w, h, null);
            } else {
                // not loaded (yet): a tile in the project's own colour with its first letter, so a row never looks empty
                Color base = hit.color() != 0 ? new Color(hit.color() & 0xFFFFFF) : new Color(Math.abs(hit.title().hashCode()) % 0xFFFFFF);
                g.setPaint(new GradientPaint(0, 0, base.brighter(), w, h, base.darker().darker()));
                g.fillRect(0, 0, w, h);
                g.setColor(Color.WHITE);
                g.setFont(LauncherApp.font(Font.BOLD, 24f));
                String letter = hit.title().isEmpty() ? "?" : hit.title().substring(0, 1).toUpperCase(Locale.ROOT);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(letter, (w - fm.stringWidth(letter)) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
            }
        }
    }

    private void showDetails(Modrinth.Hit hit, Component parent) {
        JDialog details = new JDialog(this, hit.title(), Dialog.ModalityType.APPLICATION_MODAL);
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(LauncherApp.BG);
        panel.setBorder(new EmptyBorder(20, 22, 18, 22));

        JLabel title = LauncherApp.label(hit.title(), 20, true, LauncherApp.TEXT);
        JLabel meta = LauncherApp.label("by " + hit.author() + "  ?  " + count(hit.downloads()) + " downloads", 12, false, LauncherApp.MUTED);
        JTextArea description = new JTextArea(hit.description());
        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setOpaque(false);
        description.setForeground(LauncherApp.TEXT);
        description.setFont(LauncherApp.font(Font.PLAIN, 14));
        description.setCaretPosition(0);
        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 3));
        heading.setOpaque(false);
        heading.add(title);
        heading.add(meta);
        panel.add(heading, BorderLayout.NORTH);
        JScrollPane textScroll = new JScrollPane(description);
        textScroll.setBorder(BorderFactory.createLineBorder(LauncherApp.BORDER));
        textScroll.getViewport().setBackground(LauncherApp.SURFACE);
        panel.add(textScroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        LauncherApp.NovaButton web = new LauncherApp.NovaButton("Modrinth page", false);
        web.addActionListener(e -> Platform.open(Modrinth.pageUrl(hit)));
        LauncherApp.NovaButton install = new LauncherApp.NovaButton("Install", true);
        install.addActionListener(e -> { details.dispose(); Row row = rowById.get(hit.id()); if (row != null) row.toggle(); });
        LauncherApp.NovaButton close = new LauncherApp.NovaButton("Back", false);
        close.addActionListener(e -> details.dispose());
        actions.add(web); actions.add(close); actions.add(install);
        panel.add(actions, BorderLayout.SOUTH);
        details.setContentPane(panel);
        details.setSize(540, 360);
        details.setLocationRelativeTo(parent);
        details.setVisible(true);
    }

    static String count(long n) {
        if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format(Locale.ROOT, "%.1fK", n / 1_000.0);
        return Long.toString(n);
    }
}

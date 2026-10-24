package com.nova.launcher;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Browse Modrinth and install mods, resource packs and shaders for the chosen Minecraft version in one click. */
final class ModrinthDialog extends JDialog {
    private final Path versionDir;
    private final Supplier<String> mc;
    private final DefaultListModel<Modrinth.Hit> model = new DefaultListModel<>();
    private final JList<Modrinth.Hit> list = new JList<>(model);
    private final JTextField query = new JTextField();
    private final JComboBox<String> sort = new JComboBox<>(new String[]{"Most relevant", "Most downloaded", "Newest", "Recently updated"});
    private final JLabel status = LauncherApp.label(" ", 12, false, LauncherApp.MUTED);
    private final JLabel heading = LauncherApp.label("", 13, false, LauncherApp.MUTED);
    private final JProgressBar bar = new JProgressBar();
    private final LauncherApp.NovaButton install = new LauncherApp.NovaButton("Install", true);
    private final LauncherApp.NovaButton remove = new LauncherApp.NovaButton("Remove", false);
    private final LauncherApp.NovaButton more = new LauncherApp.NovaButton("Show more", false);
    private final LauncherApp.NovaButton[] tabs = new LauncherApp.NovaButton[Modrinth.Kind.values().length];
    private final Map<String, ImageIcon> icons = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> { Thread t = new Thread(r, "nova-modrinth"); t.setDaemon(true); return t; });

    private Modrinth.Kind kind = Modrinth.Kind.MOD;
    private int offset, total;
    private int generation;          // a newer search makes older answers stale
    private volatile boolean busy;

    ModrinthDialog(Window owner, Path versionDir, Supplier<String> mc) {
        super(owner, "Get mods, packs and shaders", ModalityType.MODELESS);
        this.versionDir = versionDir;
        this.mc = mc;

        JPanel tabRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tabRow.setOpaque(false);
        for (Modrinth.Kind k : Modrinth.Kind.values()) {
            LauncherApp.NovaButton b = new LauncherApp.NovaButton(k.label, k == kind);
            b.addActionListener(e -> switchKind(k));
            tabs[k.ordinal()] = b;
            tabRow.add(b);
        }

        query.setFont(LauncherApp.font(Font.PLAIN, 14f));
        query.setBackground(LauncherApp.SURFACE);
        query.setForeground(LauncherApp.TEXT);
        query.setCaretColor(LauncherApp.TEXT);
        query.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LauncherApp.BORDER), new EmptyBorder(7, 10, 7, 10)));
        query.addActionListener(e -> search(true));
        LauncherApp.NovaButton go = new LauncherApp.NovaButton("Search", false);
        go.addActionListener(e -> search(true));
        sort.addActionListener(e -> search(true));
        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        searchRow.add(query, BorderLayout.CENTER);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(sort);
        right.add(go);
        searchRow.add(right, BorderLayout.EAST);

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JLabel title = LauncherApp.label("Get mods, packs and shaders", 22, true, LauncherApp.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);
        heading.setAlignmentX(LEFT_ALIGNMENT);
        tabRow.setAlignmentX(LEFT_ALIGNMENT);
        searchRow.setAlignmentX(LEFT_ALIGNMENT);
        top.add(title);
        top.add(Box.createVerticalStrut(2));
        top.add(heading);
        top.add(Box.createVerticalStrut(12));
        top.add(tabRow);
        top.add(Box.createVerticalStrut(10));
        top.add(searchRow);

        list.setBackground(LauncherApp.SURFACE);
        list.setCellRenderer(new Cell());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(78);
        list.addListSelectionListener(e -> updateButtons());
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { if (e.getClickCount() == 2) installSelected(); }
        });
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createLineBorder(LauncherApp.BORDER));
        sp.getViewport().setBackground(LauncherApp.SURFACE);
        sp.getVerticalScrollBar().setUnitIncrement(24);

        install.addActionListener(e -> installSelected());
        remove.addActionListener(e -> removeSelected());
        more.addActionListener(e -> search(false));
        LauncherApp.NovaButton open = new LauncherApp.NovaButton("Open on Modrinth", false);
        open.addActionListener(e -> { Modrinth.Hit h = list.getSelectedValue(); if (h != null) Platform.open(Modrinth.pageUrl(h)); });
        LauncherApp.NovaButton folder = new LauncherApp.NovaButton("Open folder", false);
        folder.addActionListener(e -> {
            Path d = versionDir.resolve(kind.folder);
            try { java.nio.file.Files.createDirectories(d); } catch (Exception ignored) {}
            Platform.openFolder(d);
        });
        LauncherApp.NovaButton close = new LauncherApp.NovaButton("Close", false);
        close.addActionListener(e -> dispose());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(install); left.add(remove); left.add(open); left.add(more); left.add(folder);
        JPanel btns = new JPanel(new BorderLayout());
        btns.setOpaque(false);
        btns.add(left, BorderLayout.WEST);
        btns.add(close, BorderLayout.EAST);

        bar.setBorderPainted(false);
        bar.setForeground(LauncherApp.GREEN);
        bar.setBackground(LauncherApp.SURFACE);
        bar.setPreferredSize(new Dimension(100, 4));
        bar.setVisible(false);
        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setOpaque(false);
        JPanel st = new JPanel(new BorderLayout(0, 4));
        st.setOpaque(false);
        st.add(status, BorderLayout.NORTH);
        st.add(bar, BorderLayout.SOUTH);
        south.add(st, BorderLayout.NORTH);
        south.add(btns, BorderLayout.SOUTH);

        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBackground(LauncherApp.BG);
        p.setBorder(new EmptyBorder(22, 26, 20, 26));
        p.add(top, BorderLayout.NORTH);
        p.add(sp, BorderLayout.CENTER);
        p.add(south, BorderLayout.SOUTH);
        setContentPane(p);
        setPreferredSize(new Dimension(860, 680));
        pack();
        setLocationRelativeTo(owner);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { pool.shutdownNow(); }
        });
        refreshHeading();
        updateButtons();
        search(true);
    }

    private void refreshHeading() {
        heading.setText("Everything here is installed for Minecraft " + mc.get() + ". Browse, pick one, press Install.");
    }

    private void switchKind(Modrinth.Kind k) {
        if (k == kind) return;
        kind = k;
        for (Modrinth.Kind x : Modrinth.Kind.values()) tabs[x.ordinal()].setPrimary(x == kind);   // the open tab is the bright one
        search(true);
    }

    private String sortKey() {
        switch (sort.getSelectedIndex()) {
            case 1: return "downloads";
            case 2: return "newest";
            case 3: return "updated";
            default: return "relevance";
        }
    }

    // ------------------------------------------------------------------ searching
    private void search(boolean fresh) {
        if (fresh) { offset = 0; model.clear(); }
        int gen = ++generation;
        Modrinth.Kind k = kind;
        String q = query.getText(), version = mc.get(), s = sortKey();
        int off = offset;
        status.setText(fresh ? "Searching Modrinth..." : "Loading more...");
        pool.submit(() -> {
            try {
                Modrinth.Page page = Modrinth.search(k, q, version, s, off);
                SwingUtilities.invokeLater(() -> {
                    if (gen != generation) return;
                    for (Modrinth.Hit h : page.hits()) model.addElement(h);
                    offset += page.hits().size();
                    total = page.total();
                    status.setText(model.isEmpty() ? "Nothing found for Minecraft " + version + ". Try another word or another tab."
                            : model.size() + " of " + total + " shown");
                    updateButtons();
                    loadIcons(page.hits());
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (gen == generation) status.setText("Could not reach Modrinth: " + e.getMessage());
                });
            }
        });
    }

    private void loadIcons(List<Modrinth.Hit> hits) {
        for (Modrinth.Hit h : hits) {
            if (h.iconUrl() == null || icons.containsKey(h.id())) continue;
            pool.submit(() -> {
                try {
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(Http.getBytes(h.iconUrl())));
                    if (img == null) return;
                    ImageIcon ic = new ImageIcon(img.getScaledInstance(52, 52, Image.SCALE_SMOOTH));
                    icons.put(h.id(), ic);
                    SwingUtilities.invokeLater(list::repaint);
                } catch (Exception ignored) {}
            });
        }
    }

    // ------------------------------------------------------------------ install / remove
    private void updateButtons() {
        Modrinth.Hit h = list.getSelectedValue();
        boolean have = h != null && isInstalled(h);
        install.setEnabled(h != null && !busy && !have);
        install.setText(have ? "Installed" : "Install");
        remove.setEnabled(h != null && !busy && have);
        more.setEnabled(!busy && offset < total);
    }

    private boolean isInstalled(Modrinth.Hit h) { return Modrinth.installed(versionDir, h.kind()).containsKey(h.id()); }

    private void installSelected() {
        Modrinth.Hit h = list.getSelectedValue();
        if (h == null || busy || isInstalled(h)) return;
        busy = true;
        updateButtons();
        bar.setIndeterminate(true);
        bar.setVisible(true);
        String version = mc.get();
        pool.submit(() -> {
            String result;
            try {
                List<String> added = Modrinth.install(h, version, versionDir, new Progress() {
                    public void status(String m) { SwingUtilities.invokeLater(() -> status.setText(m)); }
                    public void bar(int d, int t) {}
                });
                result = added.isEmpty() ? h.title() + " is already installed"
                        : h.title() + " installed" + (added.size() > 1 ? " (with " + (added.size() - 1) + " required mod" + (added.size() > 2 ? "s" : "") + ")" : "")
                          + ". It loads next time you press Play.";
            } catch (Exception e) {
                result = "Could not install " + h.title() + ": " + e.getMessage();
            }
            String msg = result;
            SwingUtilities.invokeLater(() -> {
                busy = false;
                bar.setVisible(false);
                status.setText(msg);
                updateButtons();
                list.repaint();
            });
        });
    }

    private void removeSelected() {
        Modrinth.Hit h = list.getSelectedValue();
        if (h == null || busy) return;
        try {
            status.setText(Modrinth.remove(h.id(), h.kind(), versionDir) ? h.title() + " removed" : h.title() + " was not installed from here");
        } catch (Exception e) {
            status.setText("Could not remove " + h.title() + ": " + e.getMessage());
        }
        updateButtons();
        list.repaint();
    }

    // ------------------------------------------------------------------ one row of the list
    private final class Cell extends JPanel implements ListCellRenderer<Modrinth.Hit> {
        private final JLabel icon = new JLabel();
        private final JLabel title = LauncherApp.label("", 15, true, LauncherApp.TEXT);
        private final JLabel meta = LauncherApp.label("", 12, false, LauncherApp.MUTED);
        private final JLabel desc = LauncherApp.label("", 12, false, LauncherApp.DIM);
        private final JLabel tag = LauncherApp.label("", 12, true, LauncherApp.GREEN);

        Cell() {
            super(new BorderLayout(12, 0));
            setBorder(new EmptyBorder(10, 12, 10, 12));
            icon.setPreferredSize(new Dimension(52, 52));
            JPanel text = new JPanel(new GridLayout(3, 1, 0, 1));
            text.setOpaque(false);
            JPanel first = new JPanel(new BorderLayout(8, 0));
            first.setOpaque(false);
            first.add(title, BorderLayout.WEST);
            first.add(tag, BorderLayout.CENTER);
            text.add(first);
            text.add(meta);
            text.add(desc);
            add(icon, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Modrinth.Hit> l, Modrinth.Hit h, int i, boolean sel, boolean focus) {
            setBackground(sel ? LauncherApp.SURFACE2 : LauncherApp.SURFACE);
            icon.setIcon(icons.get(h.id()));
            title.setText(h.title());
            meta.setText("by " + h.author() + "  \u00B7  " + count(h.downloads()) + " downloads");
            String d = h.description();
            desc.setText(d.length() > 110 ? d.substring(0, 107) + "..." : d);
            tag.setText(isInstalled(h) ? "  installed" : "");
            return this;
        }
    }

    static String count(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return Long.toString(n);
    }
}

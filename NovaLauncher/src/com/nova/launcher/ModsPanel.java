package com.nova.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The mods of the chosen Minecraft version as a clean list: a switch on every mod, an x to remove it, and one big
 * button to get more. Nova's own parts (Nova Client, Fabric API) are listed separately because the launcher looks after them.
 * "Off" only renames the file to .jar.disabled, nothing is deleted.
 */
final class ModsPanel extends JPanel {
    private record Item(Path file, boolean on, JarInfo info) {
        String title() { return info != null && info.name() != null ? info.name() : base(); }
        String base() { String n = file.getFileName().toString(); return n.endsWith(".disabled") ? n.substring(0, n.length() - 9) : n; }
        /** Nova Client and Fabric API: installed and updated by the launcher, not by the player. */
        boolean managed() {
            String id = info == null ? "" : String.valueOf(info.id());
            String b = base().toLowerCase(Locale.ROOT);
            return id.equals("novaclient") || id.equals("fabric-api") || id.equals("fabric") || b.startsWith("novaclient-") || b.startsWith("fabric-api");
        }
    }

    private final Window owner;
    private final Path dir;
    private final Supplier<String> mc;
    private final JPanel list = new JPanel();
    private final JLabel sub = LauncherApp.label("", 13, false, LauncherApp.MUTED);

    ModsPanel(Window owner, Path dir, Supplier<String> mc) {
        super(new BorderLayout(0, 14));
        this.owner = owner;
        this.dir = dir;
        this.mc = mc;

        // ---- top: title and the one thing most people want
        JLabel title = LauncherApp.label("Mods", 24, true, LauncherApp.TEXT);
        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);
        titles.add(title);
        titles.add(sub);
        LauncherApp.NovaButton get = new LauncherApp.NovaButton("Get mods, packs and shaders", true);
        get.addActionListener(e -> {
            ModrinthDialog d = new ModrinthDialog(owner, dir.getParent(), mc);
            d.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosed(java.awt.event.WindowEvent we) { reload(); }   // show what was just installed
            });
            d.setVisible(true);
        });
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(titles, BorderLayout.WEST);
        JPanel getBox = new JPanel(new GridBagLayout());
        getBox.setOpaque(false);
        getBox.add(get);
        top.add(getBox, BorderLayout.EAST);

        // ---- the list
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(LauncherApp.SURFACE);
        JScrollPane sp = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createLineBorder(LauncherApp.BORDER));
        sp.getViewport().setBackground(LauncherApp.SURFACE);
        sp.getVerticalScrollBar().setUnitIncrement(24);
        sp.setPreferredSize(new Dimension(700, 400));

        // drop .jar files anywhere on the window to add them
        TransferHandler drop = new TransferHandler() {
            public boolean canImport(TransferSupport s) { return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor); }
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport s) {
                try { importFiles((List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor)); return true; }
                catch (Exception e) { return false; }
            }
        };
        list.setTransferHandler(drop);
        sp.setTransferHandler(drop);

        // ---- bottom: two quiet buttons and Done
        LauncherApp.NovaButton add = new LauncherApp.NovaButton("Add a file...", false);
        add.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setMultiSelectionEnabled(true);
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Fabric mods (*.jar)", "jar"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) importFiles(List.of(fc.getSelectedFiles()));
        });
        LauncherApp.NovaButton open = new LauncherApp.NovaButton("Open folder", false);
        open.addActionListener(e -> { try { Files.createDirectories(dir); } catch (IOException ignored) {} Platform.openFolder(dir); });
        LauncherApp.NovaButton close = new LauncherApp.NovaButton("Done", false);
        close.addActionListener(e -> { if (owner != null) owner.dispose(); });
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(add);
        left.add(open);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(left, BorderLayout.WEST);
        bottom.add(close, BorderLayout.EAST);

        setBackground(LauncherApp.BG);
        setBorder(new EmptyBorder(22, 26, 20, 26));
        add(top, BorderLayout.NORTH);
        add(sp, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setTransferHandler(drop);
        reload();
    }

    // ------------------------------------------------------------------ the list
    private void reload() {
        List<Item> managed = new ArrayList<>(), mine = new ArrayList<>();
        try (Stream<Path> s = Files.isDirectory(dir) ? Files.list(dir) : Stream.<Path>empty()) {
            s.filter(f -> { String n = f.getFileName().toString(); return n.endsWith(".jar") || n.endsWith(".jar.disabled"); })
                    .sorted(Comparator.comparing(f -> f.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(f -> {
                        boolean on = f.getFileName().toString().endsWith(".jar");
                        JarInfo info = JarInfo.read(f);
                        // switched-off jars built for other Minecraft versions are just clutter: hide them
                        if (!on && info != null && !info.supports(mc.get())) return;
                        Item it = new Item(f, on, info);
                        (it.managed() ? managed : mine).add(it);
                    });
        } catch (IOException ignored) {}

        list.removeAll();
        if (!managed.isEmpty()) {
            list.add(heading("Included with Nova"));
            for (Item it : managed) list.add(new Line(it, true));
        }
        list.add(heading("Your mods"));
        if (mine.isEmpty()) {
            list.add(empty());
        } else {
            for (Item it : mine) list.add(new Line(it, false));
        }
        list.add(Box.createVerticalGlue());
        sub.setText("Minecraft " + mc.get() + (mine.isEmpty() ? "" : "  \u00B7  " + mine.size() + " mod" + (mine.size() == 1 ? "" : "s")));
        list.revalidate();
        list.repaint();
    }

    private JComponent heading(String text) {
        JLabel l = LauncherApp.label(text.toUpperCase(Locale.ROOT), 11, true, LauncherApp.DIM);
        l.setBorder(new EmptyBorder(14, 16, 6, 16));
        l.setAlignmentX(LEFT_ALIGNMENT);
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(l, BorderLayout.WEST);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        return p;
    }

    private JComponent empty() {
        JPanel p = new JPanel(new GridLayout(2, 1, 0, 4));
        p.setOpaque(false);
        JLabel a = LauncherApp.label("Nothing here yet", 15, true, LauncherApp.MUTED);
        JLabel b = LauncherApp.label("Press \u201CGet mods, packs and shaders\u201D, or drop a .jar file on this window.", 12, false, LauncherApp.DIM);
        a.setHorizontalAlignment(SwingConstants.CENTER);
        b.setHorizontalAlignment(SwingConstants.CENTER);
        p.add(a);
        p.add(b);
        p.setBorder(new EmptyBorder(30, 16, 30, 16));
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        return p;
    }

    /** One mod: a tile, its name, one line about it, a switch and an x. */
    private final class Line extends JPanel {
        private final Item it;
        private final boolean locked;

        Line(Item it, boolean locked) {
            super(new BorderLayout(14, 0));
            this.it = it;
            this.locked = locked;
            setBackground(LauncherApp.SURFACE);
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LauncherApp.BORDER), new EmptyBorder(10, 16, 10, 16)));
            setPreferredSize(new Dimension(100, 62));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
            setAlignmentX(LEFT_ALIGNMENT);
            setTransferHandler(list.getTransferHandler());

            JComponent tile = new JComponent() {
                @Override protected void paintComponent(Graphics g0) { paintTile((Graphics2D) g0, getWidth(), getHeight()); }
            };
            tile.setPreferredSize(new Dimension(38, 38));

            boolean fits = it.info() == null || it.info().supports(mc.get());
            boolean dim = !it.on() && !locked;
            JLabel name = LauncherApp.label(it.title(), 14, true, dim ? LauncherApp.DIM : LauncherApp.TEXT);
            String line;
            Color lineColor = LauncherApp.MUTED;
            if (locked) line = "Looked after by Nova" + (it.info() != null && it.info().version() != null ? "  \u00B7  " + it.info().version() : "");
            else if (it.info() == null) line = "Not a Fabric mod: it may not load";
            else if (!fits) { line = "Made for Minecraft " + it.info().mcLabel() + ": it will be switched off for " + mc.get(); lineColor = LauncherApp.RED; }
            else line = it.info().version() != null ? "Version " + it.info().version() : "";
            JLabel info = LauncherApp.label(line, 12, false, dim ? LauncherApp.DIM : lineColor);
            JPanel text = new JPanel(new GridLayout(2, 1, 0, 1));
            text.setOpaque(false);
            text.add(name);
            text.add(info);

            JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
            east.setOpaque(false);
            if (locked) {
                east.add(LauncherApp.label("always on", 12, false, LauncherApp.DIM));
            } else {
                Switch sw = new Switch(it.on());
                sw.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { toggle(it, !it.on()); } });
                east.add(sw);
                JLabel x = LauncherApp.label("\u2715", 15, true, LauncherApp.DIM);
                x.setToolTipText("Remove " + it.title());
                x.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                x.setBorder(new EmptyBorder(0, 2, 0, 0));
                x.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) { removeMod(it); }
                    @Override public void mouseEntered(MouseEvent e) { x.setForeground(LauncherApp.RED); }
                    @Override public void mouseExited(MouseEvent e) { x.setForeground(LauncherApp.DIM); }
                });
                east.add(x);
            }
            JPanel eastBox = new JPanel(new GridBagLayout());
            eastBox.setOpaque(false);
            eastBox.add(east);

            add(tile, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            add(eastBox, BorderLayout.EAST);
        }

        private void paintTile(Graphics2D g, int w, int h) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setClip(new RoundRectangle2D.Float(0, 0, w, h, 11, 11));
            Color base = locked ? LauncherApp.ACCENT_DARK : new Color(Math.abs(it.title().hashCode()) % 0xFFFFFF);
            if (!it.on() && !locked) base = new Color(0x3A3658);
            g.setPaint(new GradientPaint(0, 0, base.brighter(), w, h, base.darker().darker()));
            g.fillRect(0, 0, w, h);
            g.setColor(Color.WHITE);
            g.setFont(LauncherApp.font(Font.BOLD, 17f));
            String letter = it.title().isEmpty() ? "?" : it.title().substring(0, 1).toUpperCase(Locale.ROOT);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(letter, (w - fm.stringWidth(letter)) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
        }
    }

    /** A small on/off switch. */
    private static final class Switch extends JComponent {
        private final boolean on;
        Switch(boolean on) {
            this.on = on;
            setPreferredSize(new Dimension(42, 24));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g.setColor(on ? LauncherApp.ACCENT_DARK : new Color(0x3A3658));
            g.fillRoundRect(0, 0, w, h, h, h);
            g.setColor(on ? Color.WHITE : new Color(0xB3AED0));
            int d = h - 6;
            g.fillOval(on ? w - d - 3 : 3, 3, d, d);
        }
    }

    // ------------------------------------------------------------------ actions
    private void toggle(Item w, boolean on) {
        if (on && w.info() != null && !w.info().supports(mc.get())) {
            int a = JOptionPane.showConfirmDialog(this, w.title() + " is made for Minecraft " + w.info().mcLabel() + ", but you are playing " + mc.get()
                    + ".\nIf you switch it on, the launcher switches it off again before the game starts\n(otherwise the game would not start at all). Switch it on anyway?",
                    "Wrong Minecraft version", JOptionPane.YES_NO_OPTION);
            if (a != JOptionPane.YES_OPTION) return;
        }
        try {
            Path to = on ? w.file().resolveSibling(w.base()) : w.file().resolveSibling(w.base() + ".disabled");
            Files.move(w.file(), to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not change " + w.base() + ": " + e.getMessage());
        }
        reload();
    }

    private void removeMod(Item w) {
        if (JOptionPane.showConfirmDialog(this, "Remove " + w.title() + "?", "Remove mod", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try { Files.deleteIfExists(w.file()); } catch (IOException e) { JOptionPane.showMessageDialog(this, "Could not remove it: " + e.getMessage()); }
        reload();
    }

    private void importFiles(List<File> files) {
        int n = 0;
        for (File f : files) {
            if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
            try { Files.createDirectories(dir); Files.copy(f.toPath(), dir.resolve(f.getName()), StandardCopyOption.REPLACE_EXISTING); n++; }
            catch (IOException e) { JOptionPane.showMessageDialog(this, "Could not add " + f.getName() + ": " + e.getMessage()); }
        }
        reload();
        if (n == 0 && !files.isEmpty()) JOptionPane.showMessageDialog(this, "Only .jar files can be added.");
    }
}

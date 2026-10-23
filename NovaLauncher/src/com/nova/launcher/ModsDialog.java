package com.nova.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Your mods folder as a list: switch each mod on or off, import jars (button or drag and drop), remove them.
 * "Off" just renames the file to .jar.disabled, nothing is deleted.
 */
final class ModsDialog extends JDialog {
    private record Row(Path file, boolean on, JarInfo info) {
        String title() { return info != null && info.name() != null ? info.name() : base(); }
        String base() { String n = file.getFileName().toString(); return n.endsWith(".disabled") ? n.substring(0, n.length() - 9) : n; }
    }

    private final Path dir;
    private final Supplier<String> mc;
    private final List<Row> rows = new ArrayList<>();
    private final AbstractTableModel model;
    private final JTable table;
    private final JLabel header = LauncherApp.label("", 12, false, LauncherApp.DIM);

    ModsDialog(JFrame owner, Path dir, Supplier<String> mc, Runnable chooseOptional) {
        super(owner, "Mods", true);
        this.dir = dir;
        this.mc = mc;

        model = new AbstractTableModel() {
            final String[] cols = {"On", "Mod", "Version", "Needs Minecraft", "Status"};
            public int getRowCount() { return rows.size(); }
            public int getColumnCount() { return cols.length; }
            public String getColumnName(int c) { return cols[c]; }
            public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
            public boolean isCellEditable(int r, int c) { return c == 0; }
            public Object getValueAt(int r, int c) {
                Row w = rows.get(r);
                return switch (c) {
                    case 0 -> w.on();
                    case 1 -> w.title();
                    case 2 -> w.info() == null || w.info().version() == null ? "" : w.info().version();
                    case 3 -> w.info() == null ? "?" : w.info().mcLabel();
                    default -> status(w);
                };
            }
            public void setValueAt(Object v, int r, int c) { if (c == 0) toggle(r, Boolean.TRUE.equals(v)); }
        };
        table = new JTable(model);
        table.setBackground(LauncherApp.SURFACE);
        table.setForeground(LauncherApp.TEXT);
        table.setFont(LauncherApp.font(Font.PLAIN, 13f));
        table.setRowHeight(30);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(LauncherApp.ACCENT_DARK);
        table.setSelectionForeground(Color.WHITE);
        table.getTableHeader().setBackground(LauncherApp.SURFACE2);
        table.getTableHeader().setForeground(LauncherApp.MUTED);
        table.getTableHeader().setFont(LauncherApp.font(Font.BOLD, 12f));
        int[] widths = {44, 230, 110, 150, 200};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        DefaultTableCellRenderer text = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                setBorder(new EmptyBorder(0, 10, 0, 10));
                Row w = rows.get(r);
                Color col = !w.on() ? LauncherApp.DIM : LauncherApp.TEXT;
                if (c == 4) col = status(w).startsWith("ok") ? LauncherApp.GREEN : status(w).startsWith("off") ? LauncherApp.DIM : LauncherApp.RED;
                setForeground(sel ? Color.WHITE : col);
                setBackground(sel ? LauncherApp.ACCENT_DARK : LauncherApp.SURFACE);
                return this;
            }
        };
        for (int i = 1; i < 5; i++) table.getColumnModel().getColumn(i).setCellRenderer(text);

        JScrollPane sp = new JScrollPane(table);
        sp.getViewport().setBackground(LauncherApp.SURFACE);
        sp.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 36), 1, true));
        sp.setPreferredSize(new Dimension(760, 360));

        // drag jar files onto the list to import them
        table.setTransferHandler(new TransferHandler() {
            public boolean canImport(TransferSupport s) { return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor); }
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport s) {
                try { importFiles((List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor)); return true; }
                catch (Exception e) { return false; }
            }
        });
        sp.setTransferHandler(table.getTransferHandler());

        LauncherApp.NovaButton add = new LauncherApp.NovaButton("Import mod...", true);
        add.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setMultiSelectionEnabled(true);
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Fabric mods (*.jar)", "jar"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) importFiles(List.of(fc.getSelectedFiles()));
        });
        LauncherApp.NovaButton remove = new LauncherApp.NovaButton("Remove", false);
        remove.addActionListener(e -> removeSelected());
        LauncherApp.NovaButton open = new LauncherApp.NovaButton("Open folder", false);
        open.addActionListener(e -> Platform.openFolder(dir));
        LauncherApp.NovaButton close = new LauncherApp.NovaButton("Close", false);
        close.addActionListener(e -> dispose());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        LauncherApp.NovaButton extras = new LauncherApp.NovaButton("Sodium / Iris / Freecam...", false);
        extras.addActionListener(e -> { chooseOptional.run(); });
        left.add(add); left.add(remove); left.add(open); left.add(extras);
        JPanel btns = new JPanel(new BorderLayout());
        btns.setOpaque(false);
        btns.add(left, BorderLayout.WEST);
        btns.add(close, BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setOpaque(false);
        top.add(LauncherApp.label("Mods", 22, true, LauncherApp.TEXT), BorderLayout.NORTH);
        top.add(header, BorderLayout.SOUTH);

        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBackground(LauncherApp.BG);
        p.setBorder(new EmptyBorder(22, 26, 22, 26));
        p.add(top, BorderLayout.NORTH);
        p.add(sp, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setOpaque(false);
        south.add(LauncherApp.label("Tick = on. Drop .jar files here to import. Nova Client and Fabric API are managed by the launcher (switch Nova off in Settings).", 12, false, LauncherApp.DIM), BorderLayout.NORTH);
        south.add(btns, BorderLayout.SOUTH);
        p.add(south, BorderLayout.SOUTH);

        setContentPane(p);
        reload();
        pack();
        setLocationRelativeTo(owner);
    }

    private String status(Row w) {
        if (!w.on()) return "off";
        if (w.info() == null) return "ok (not a Fabric mod?)";
        return w.info().supports(mc.get()) ? "ok" : "needs another Minecraft version";
    }

    private void reload() {
        rows.clear();
        int[] hidden = {0};
        try (Stream<Path> s = Files.isDirectory(dir) ? Files.list(dir) : Stream.<Path>empty()) {
            s.filter(f -> { String n = f.getFileName().toString(); return n.endsWith(".jar") || n.endsWith(".jar.disabled"); })
                    .sorted(Comparator.comparing(f -> f.getFileName().toString().toLowerCase()))
                    .forEach(f -> {
                        boolean on = f.getFileName().toString().endsWith(".jar");
                        JarInfo info = JarInfo.read(f);
                        // switched-off jars built for other Minecraft versions are just clutter: hide them
                        if (!on && info != null && !info.supports(mc.get())) { hidden[0]++; return; }
                        rows.add(new Row(f, on, info));
                    });
        } catch (IOException ignored) {}
        model.fireTableDataChanged();
        header.setText("Minecraft " + mc.get() + "  \u00B7  " + rows.size() + " mod" + (rows.size() == 1 ? "" : "s")
                + (hidden[0] > 0 ? " (" + hidden[0] + " for other versions hidden)" : "") + " in " + dir);
    }

    private void toggle(int r, boolean on) {
        Row w = rows.get(r);
        if (on && w.info() != null && !w.info().supports(mc.get())) {
            int a = JOptionPane.showConfirmDialog(this, w.title() + " needs Minecraft " + w.info().mcLabel() + " but you are on " + mc.get()
                    + ".\nIf you switch it on, the launcher will switch it off again before starting the game\n(otherwise Fabric would not start at all). Switch it on anyway?",
                    "Wrong Minecraft version", JOptionPane.YES_NO_OPTION);
            if (a != JOptionPane.YES_OPTION) { model.fireTableRowsUpdated(r, r); return; }
        }
        try {
            Path to = on ? w.file().resolveSibling(w.base()) : w.file().resolveSibling(w.base() + ".disabled");
            Files.move(w.file(), to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not change " + w.base() + ": " + e.getMessage());
        }
        reload();
    }

    private void importFiles(List<File> files) {
        int n = 0;
        for (File f : files) {
            if (!f.getName().toLowerCase().endsWith(".jar")) continue;
            try { Files.createDirectories(dir); Files.copy(f.toPath(), dir.resolve(f.getName()), StandardCopyOption.REPLACE_EXISTING); n++; }
            catch (IOException e) { JOptionPane.showMessageDialog(this, "Could not import " + f.getName() + ": " + e.getMessage()); }
        }
        reload();
        if (n == 0 && !files.isEmpty()) JOptionPane.showMessageDialog(this, "Only .jar files can be imported.");
    }

    private void removeSelected() {
        int r = table.getSelectedRow();
        if (r < 0) return;
        Row w = rows.get(r);
        if (JOptionPane.showConfirmDialog(this, "Delete " + w.base() + " from the mods folder?", "Remove mod", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try { Files.deleteIfExists(w.file()); } catch (IOException e) { JOptionPane.showMessageDialog(this, "Could not delete: " + e.getMessage()); }
        reload();
    }
}

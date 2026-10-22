package com.nova.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Pick the Minecraft version. Shows which ones have a Nova build and which run as plain Fabric. */
final class VersionDialog extends JDialog {
    private record Item(String id, String label, String tag) {}

    VersionDialog(JFrame owner, List<String> versions, String current, Map<String, NovaManifest.Build> builds,
                  String embeddedMc, Consumer<String> onPick) {
        super(owner, "Minecraft version", true);
        List<Item> all = new ArrayList<>();
        all.add(new Item("", "Automatic", "newest Nova build"));
        for (String v : versions) all.add(new Item(v, v, tagFor(v, builds, embeddedMc)));

        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBackground(LauncherApp.BG);
        p.setBorder(new EmptyBorder(22, 26, 22, 26));
        p.add(LauncherApp.label("Minecraft version", 22, true, LauncherApp.TEXT), BorderLayout.NORTH);

        JTextField search = new JTextField();
        search.setBackground(LauncherApp.SURFACE2);
        search.setForeground(LauncherApp.TEXT);
        search.setCaretColor(LauncherApp.TEXT);
        search.setFont(LauncherApp.font(Font.PLAIN, 13f));
        search.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 36), 1, true), new EmptyBorder(8, 10, 8, 10)));

        DefaultListModel<Item> model = new DefaultListModel<>();
        JList<Item> list = new JList<>(model);
        list.setBackground(LauncherApp.SURFACE);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(30);
        list.setCellRenderer((l, it, idx, sel, focus) -> {
            JPanel row = new JPanel(new BorderLayout());
            row.setBorder(new EmptyBorder(0, 12, 0, 12));
            row.setBackground(sel ? LauncherApp.ACCENT_DARK : LauncherApp.SURFACE);
            row.add(LauncherApp.label(it.label(), 13, true, LauncherApp.TEXT), BorderLayout.WEST);
            Color c = it.tag().startsWith("Nova") ? LauncherApp.GREEN : LauncherApp.MUTED;
            row.add(LauncherApp.label(it.tag(), 12, false, c), BorderLayout.EAST);
            return row;
        });

        Runnable refill = () -> {
            String q = search.getText().trim().toLowerCase();
            model.clear();
            for (Item it : all) if (q.isEmpty() || it.id().isEmpty() || it.id().toLowerCase().contains(q)) model.addElement(it);
            for (int i = 0; i < model.size(); i++) if (model.get(i).id().equals(current == null ? "" : current)) { list.setSelectedIndex(i); list.ensureIndexIsVisible(i); }
            if (list.getSelectedIndex() < 0 && model.size() > 0) list.setSelectedIndex(0);
        };
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { refill.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { refill.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refill.run(); }
        });
        refill.run();

        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 36), 1, true));
        sp.setPreferredSize(new Dimension(460, 340));

        JPanel mid = new JPanel(new BorderLayout(0, 10));
        mid.setOpaque(false);
        mid.add(search, BorderLayout.NORTH);
        mid.add(sp, BorderLayout.CENTER);
        mid.add(LauncherApp.label("Fabric runs every release from 1.14 on. \"Fabric only\" = no Nova build yet.", 12, false, LauncherApp.DIM), BorderLayout.SOUTH);
        p.add(mid, BorderLayout.CENTER);

        Runnable choose = () -> {
            Item it = list.getSelectedValue();
            if (it != null) { onPick.accept(it.id()); dispose(); }
        };
        LauncherApp.NovaButton cancel = new LauncherApp.NovaButton("Cancel", false);
        cancel.addActionListener(e -> dispose());
        LauncherApp.NovaButton ok = new LauncherApp.NovaButton("Use this version", true);
        ok.addActionListener(e -> choose.run());
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) choose.run(); }
        });
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btns.setOpaque(false);
        btns.add(cancel); btns.add(ok);
        p.add(btns, BorderLayout.SOUTH);

        setContentPane(p);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Label shown next to a version. */
    static String tagFor(String mc, Map<String, NovaManifest.Build> builds, String embeddedMc) {
        NovaManifest.Build b = builds.get(mc);
        if (b != null) return b.tested() ? "Nova" : "Nova (experimental)";
        if (mc.equals(embeddedMc)) return "Nova (built in)";
        return "Fabric only";
    }
}

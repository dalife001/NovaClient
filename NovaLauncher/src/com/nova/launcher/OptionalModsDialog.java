package com.nova.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** "Choose your mods": tick Sodium / Iris / Freecam; shows whether each one exists for the selected Minecraft version. */
final class OptionalModsDialog extends JDialog {
    OptionalModsDialog(JFrame owner, LauncherConfig cfg, Supplier<String> mc, Runnable onSaved) {
        super(owner, "Choose your mods", true);
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(LauncherApp.BG);
        p.setBorder(new EmptyBorder(22, 26, 22, 26));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.weightx = 1; c.anchor = GridBagConstraints.WEST;

        String version = mc.get();
        c.gridy = 0; c.insets = new Insets(0, 4, 4, 4);
        p.add(LauncherApp.label("Choose your mods", 22, true, LauncherApp.TEXT), c);
        c.gridy = 1; c.insets = new Insets(0, 4, 14, 4);
        p.add(LauncherApp.label("Nova Client works on its own. These are optional and are downloaded for Minecraft " + version + " when you press Play.", 12, false, LauncherApp.DIM), c);

        Set<String> chosen = OptionalMods.parse(cfg.optionalMods);
        Map<String, JCheckBox> boxes = new LinkedHashMap<>();
        Map<String, JLabel> avail = new LinkedHashMap<>();
        int row = 2;
        for (OptionalMods.Mod m : OptionalMods.ALL) {
            JCheckBox box = new JCheckBox(m.name(), chosen.contains(m.slug()));
            box.setOpaque(false);
            box.setForeground(LauncherApp.TEXT);
            box.setFont(LauncherApp.font(Font.BOLD, 15f));
            boxes.put(m.slug(), box);
            JLabel state = LauncherApp.label("checking...", 12, false, LauncherApp.DIM);
            avail.put(m.slug(), state);
            JPanel line = new JPanel(new BorderLayout(12, 0));
            line.setOpaque(false);
            line.add(box, BorderLayout.WEST);
            line.add(state, BorderLayout.EAST);
            c.gridy = row++; c.insets = new Insets(8, 4, 0, 4);
            p.add(line, c);
            c.gridy = row++; c.insets = new Insets(0, 28, 4, 4);
            p.add(LauncherApp.label(m.blurb(), 12, false, LauncherApp.MUTED), c);
        }
        // Iris pulls Sodium in with it
        JCheckBox iris = boxes.get("iris"), sodium = boxes.get("sodium");
        iris.addActionListener(e -> { if (iris.isSelected()) sodium.setSelected(true); });
        sodium.addActionListener(e -> { if (!sodium.isSelected()) iris.setSelected(false); });

        LauncherApp.NovaButton cancel = new LauncherApp.NovaButton("Cancel", false);
        cancel.addActionListener(e -> dispose());
        LauncherApp.NovaButton save = new LauncherApp.NovaButton("Save", true);
        save.addActionListener(e -> {
            java.util.List<String> on = new java.util.ArrayList<>();
            for (Map.Entry<String, JCheckBox> en : boxes.entrySet()) if (en.getValue().isSelected()) on.add(en.getKey());
            cfg.optionalMods = OptionalMods.join(on);
            cfg.optionalChosen = true;
            cfg.save();
            onSaved.run();
            dispose();
        });
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btns.setOpaque(false);
        btns.add(cancel); btns.add(save);
        c.gridy = row; c.insets = new Insets(18, 4, 0, 4);
        p.add(btns, c);

        setContentPane(p);
        pack();
        setMinimumSize(new Dimension(560, getHeight()));
        setLocationRelativeTo(owner);

        // look up availability in the background
        for (OptionalMods.Mod m : OptionalMods.ALL) {
            Thread t = new Thread(() -> {
                String text;
                Color col;
                try {
                    OptionalMods.Found f = OptionalMods.find(m.slug(), version);
                    text = f == null ? "not available for " + version : "available for " + version;
                    col = f == null ? LauncherApp.RED : LauncherApp.GREEN;
                } catch (Exception ex) {
                    text = "could not check (offline?)";
                    col = LauncherApp.DIM;
                }
                String tt = text; Color cc = col;
                SwingUtilities.invokeLater(() -> { JLabel l = avail.get(m.slug()); l.setText(tt); l.setForeground(cc); });
            }, "nova-mod-check");
            t.setDaemon(true);
            t.start();
        }
    }
}

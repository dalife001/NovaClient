package com.nova.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Dark settings window matching the launcher. */
final class SettingsDialog extends JDialog {
    SettingsDialog(JFrame owner, LauncherConfig cfg, Runnable onSaved) {
        super(owner, "Settings", true);
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(LauncherApp.BG);
        p.setBorder(new EmptyBorder(22, 26, 22, 26));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 4, 5, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = LauncherApp.label("Settings", 22, true, LauncherApp.TEXT);
        c.gridx = 0; c.gridy = 0; c.gridwidth = 3; c.insets = new Insets(0, 4, 14, 4);
        p.add(title, c);
        c.gridwidth = 1; c.insets = new Insets(5, 4, 5, 4);

        JTextField azure = field(cfg.azureClientId);
        JTextField offline = field(cfg.offlineName);
        JTextField manifest = field(cfg.manifestUrl);
        JTextField mcv = field(cfg.mcVersion);
        JTextField localJar = field(cfg.localModJar);
        JTextField bgImg = field(cfg.backgroundPath);
        JTextField java = field(cfg.javaPath);
        JCheckBox useNova = new JCheckBox("Load Nova Client (off = plain Fabric)", cfg.useNova);
        useNova.setOpaque(false);
        useNova.setForeground(LauncherApp.TEXT);
        useNova.setFont(LauncherApp.font(Font.PLAIN, 13f));
        JSlider ram = new JSlider(1024, 16384, Math.max(1024, Math.min(16384, cfg.ramMb)));
        ram.setOpaque(false);
        ram.setMajorTickSpacing(1024);
        ram.setSnapToTicks(true);
        JLabel ramLabel = LauncherApp.label(gb(ram.getValue()), 13, true, LauncherApp.TEXT);
        ram.addChangeListener(e -> { ram.setValue(Math.round(ram.getValue() / 512f) * 512); ramLabel.setText(gb(ram.getValue())); });

        int r = 1;
        r = row(p, c, r, "Azure client ID (Microsoft login)", azure, null);
        r = row(p, c, r, "Offline player name", offline, null);
        r = row(p, c, r, "Update manifest URL (latest.json)", manifest, null);
        r = row(p, c, r, "Minecraft version (blank = auto)", mcv, null);
        r = row(p, c, r, "Nova Client", useNova, null);
        r = row(p, c, r, "Local Nova jar (optional)", localJar, browse(localJar, "jar"));
        r = row(p, c, r, "Background image", bgImg, browse(bgImg, "png", "jpg", "jpeg"));
        r = row(p, c, r, "Java path (blank = auto)", java, null);
        r = row(p, c, r, "Memory", ram, ramLabel);

        LauncherApp.NovaButton cancel = new LauncherApp.NovaButton("Cancel", false);
        cancel.addActionListener(e -> dispose());
        LauncherApp.NovaButton save = new LauncherApp.NovaButton("Save", true);
        save.addActionListener(e -> {
            cfg.azureClientId = azure.getText().trim();
            cfg.offlineName = offline.getText().trim().isEmpty() ? "Player" : offline.getText().trim();
            cfg.manifestUrl = manifest.getText().trim();
            cfg.mcVersion = mcv.getText().trim();
            cfg.useNova = useNova.isSelected();
            cfg.localModJar = localJar.getText().trim();
            cfg.backgroundPath = bgImg.getText().trim();
            cfg.javaPath = java.getText().trim();
            cfg.ramMb = ram.getValue();
            cfg.save();
            onSaved.run();
            dispose();
        });
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btns.setOpaque(false);
        btns.add(cancel); btns.add(save);
        c.gridx = 0; c.gridy = r; c.gridwidth = 3; c.insets = new Insets(16, 4, 0, 4);
        p.add(btns, c);

        setContentPane(p);
        pack();
        setMinimumSize(new Dimension(640, getHeight()));
        setLocationRelativeTo(owner);
    }

    private static String gb(int mb) { return String.format("%.1f GB", mb / 1024.0); }

    private static JTextField field(String text) {
        JTextField f = new JTextField(text, 30);
        f.setBackground(LauncherApp.SURFACE2);
        f.setForeground(LauncherApp.TEXT);
        f.setCaretColor(LauncherApp.TEXT);
        f.setFont(LauncherApp.font(Font.PLAIN, 13f));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 36), 1, true), new EmptyBorder(8, 10, 8, 10)));
        return f;
    }

    private static int row(JPanel p, GridBagConstraints c, int r, String label, JComponent field, JComponent extra) {
        c.gridx = 0; c.gridy = r; c.weightx = 0; c.anchor = GridBagConstraints.WEST;
        p.add(LauncherApp.label(label, 13, false, LauncherApp.MUTED), c);
        c.gridx = 1; c.weightx = 1;
        p.add(field, c);
        c.gridx = 2; c.weightx = 0;
        if (extra != null) p.add(extra, c);
        return r + 1;
    }

    private JButton browse(JTextField target, String... ext) {
        LauncherApp.NovaButton b = new LauncherApp.NovaButton("Browse", false);
        b.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(String.join("/", ext), ext));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) target.setText(fc.getSelectedFile().getAbsolutePath());
        });
        return b;
    }
}

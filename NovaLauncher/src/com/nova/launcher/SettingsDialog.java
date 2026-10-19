package com.nova.launcher;

import javax.swing.*;
import java.awt.*;

final class SettingsDialog extends JDialog {
    SettingsDialog(JFrame owner, LauncherConfig cfg, Runnable onSaved) {
        super(owner, "Settings", true);
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        JTextField azure = new JTextField(cfg.azureClientId, 34);
        JTextField offline = new JTextField(cfg.offlineName, 34);
        JTextField manifest = new JTextField(cfg.manifestUrl, 34);
        JTextField mcv = new JTextField(cfg.mcVersion, 34);
        JTextField localJar = new JTextField(cfg.localModJar, 34);
        JTextField bgImg = new JTextField(cfg.backgroundPath, 34);
        JTextField java = new JTextField(cfg.javaPath, 34);
        JSpinner ram = new JSpinner(new SpinnerNumberModel(cfg.ramMb, 1024, 32768, 512));

        int r = 0;
        r = row(p, c, r, "Azure client ID (Microsoft login)", azure, null);
        r = row(p, c, r, "Offline player name", offline, null);
        r = row(p, c, r, "Update manifest URL (latest.json)", manifest, null);
        r = row(p, c, r, "Minecraft version (blank = auto)", mcv, null);
        r = row(p, c, r, "Local Nova jar (optional)", localJar, browse(localJar, "jar"));
        r = row(p, c, r, "Background image", bgImg, browse(bgImg, "png", "jpg", "jpeg"));
        r = row(p, c, r, "Java path (blank = auto)", java, null);
        r = row(p, c, r, "Memory (MB)", ram, null);

        JButton save = new JButton("Save");
        save.addActionListener(e -> {
            cfg.azureClientId = azure.getText().trim();
            cfg.offlineName = offline.getText().trim().isEmpty() ? "Player" : offline.getText().trim();
            cfg.manifestUrl = manifest.getText().trim();
            cfg.mcVersion = mcv.getText().trim();
            cfg.localModJar = localJar.getText().trim();
            cfg.backgroundPath = bgImg.getText().trim();
            cfg.javaPath = java.getText().trim();
            cfg.ramMb = (Integer) ram.getValue();
            cfg.save();
            onSaved.run();
            dispose();
        });
        c.gridx = 1; c.gridy = r; c.anchor = GridBagConstraints.EAST; c.fill = GridBagConstraints.NONE;
        p.add(save, c);

        setContentPane(p);
        pack();
        setLocationRelativeTo(owner);
    }

    private static int row(JPanel p, GridBagConstraints c, int r, String label, JComponent field, JButton extra) {
        c.gridx = 0; c.gridy = r; c.weightx = 0; c.anchor = GridBagConstraints.WEST;
        p.add(new JLabel(label), c);
        c.gridx = 1; c.weightx = 1;
        p.add(field, c);
        if (extra != null) { c.gridx = 2; c.weightx = 0; p.add(extra, c); }
        return r + 1;
    }

    private JButton browse(JTextField target, String... ext) {
        JButton b = new JButton("...");
        b.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(String.join("/", ext), ext));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) target.setText(fc.getSelectedFile().getAbsolutePath());
        });
        return b;
    }
}

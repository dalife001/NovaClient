package com.nova.launcher;

import javax.swing.*;
import java.awt.Window;
import java.nio.file.Path;
import java.util.function.Supplier;

/** The window around {@link ModrinthPanel}. */
final class ModrinthDialog extends JDialog {
    ModrinthDialog(Window owner, Path versionDir, Supplier<String> mc) {
        super(owner, "Get mods, packs and shaders", ModalityType.MODELESS);
        ModrinthPanel panel = new ModrinthPanel(this, versionDir, mc, true);
        setContentPane(panel);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setLocationRelativeTo(owner);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { panel.close(); }
            @Override public void windowOpened(java.awt.event.WindowEvent e) { panel.focusSearch(); }
        });
    }
}

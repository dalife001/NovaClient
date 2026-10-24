package com.nova.launcher;

import javax.swing.*;
import java.nio.file.Path;
import java.util.function.Supplier;

/** The window around {@link ModsPanel}. */
final class ModsDialog extends JDialog {
    ModsDialog(JFrame owner, Path dir, Supplier<String> mc, Runnable chooseOptional) {
        super(owner, "Mods", true);
        setContentPane(new ModsPanel(this, dir, mc));
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setLocationRelativeTo(owner);
    }
}

package com.nova.client.module;

import com.nova.client.gui.Ui;
import net.minecraft.client.gui.DrawContext;

/** Shared look for every HUD element. */
public final class Hud {
    private Hud() {}

    public static void panel(DrawContext c, int x, int y, int w, int h) {
        Ui.round(c, x, y, w, h, 0x99090916);
        c.fillGradient(x, y + 2, x + 2, y + h - 2, Ui.ACCENT, Ui.CYAN);
    }
}

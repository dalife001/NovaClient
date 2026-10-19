package com.nova.client.module;

import com.nova.client.gui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Shared look for every HUD element: dark glass panels with a Nova accent edge and optional title bar. */
public final class Hud {
    /** Height of the title bar on titled panels. */
    public static final int TITLE_H = 13;

    private Hud() {}

    /** Plain glass panel with an accent strip on the left and a faint outline. */
    public static void panel(DrawContext c, int x, int y, int w, int h) {
        Ui.round(c, x, y, w, h, 0xB00A0A16);
        Ui.outline(c, x + 1, y + 1, w - 2, h - 2, 0x22FFFFFF);
        c.fillGradient(x, y + 2, x + 2, y + h - 2, Ui.ACCENT, Ui.CYAN);
    }

    /** Panel with a gradient title bar on top (content starts at y + TITLE_H). */
    public static void titled(DrawContext c, MinecraftClient mc, int x, int y, int w, int h, String title) {
        Ui.round(c, x, y, w, h, 0xB00A0A16);
        Ui.outline(c, x + 1, y + 1, w - 2, h - 2, 0x22FFFFFF);
        Ui.hGradient(c, x + 2, y + 1, w - 4, TITLE_H - 2, Ui.a(Ui.ACCENT_DARK, 0.8f), Ui.a(Ui.CYAN, 0.55f));
        c.fill(x + 2, y + TITLE_H - 1, x + w - 2, y + TITLE_H, 0x33FFFFFF);
        c.drawTextWithShadow(mc.textRenderer, title, x + 6, y + 3, 0xFFF5F3FF);
    }

    /** Width a titled panel needs so its title always fits. */
    public static int titleWidth(MinecraftClient mc, String title) { return mc.textRenderer.getWidth(title) + 14; }
}

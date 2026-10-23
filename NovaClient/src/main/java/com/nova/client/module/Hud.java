package com.nova.client.module;

import com.nova.client.gui.Gfx;
import com.nova.client.gui.Anim;
import com.nova.client.gui.Ui;
import net.minecraft.client.MinecraftClient;

/** Shared look for every HUD element: soft frosted glass with a thin glowing line along the top edge. */
public final class Hud {
    /** Height of the title area on titled panels. */
    public static final int TITLE_H = 13;

    private Hud() {}

    private static void glass(Gfx c, int x, int y, int w, int h) {
        Ui.rrect(c, x, y, w, h, 4, 0x940C0A1E);
        Ui.rim(c, x, y, w, h, 4, Ui.glass(0.10f));
        // the signature: a hairline of light that flows from accent to the warm secondary colour
        Ui.hGradient(c, x + 5, y + 1, w - 10, 1, Ui.a(Ui.ACCENT, 0.85f), Ui.a(Ui.CYAN, 0.85f));
    }

    /** Plain glass panel. */
    public static void panel(Gfx c, int x, int y, int w, int h) {
        glass(c, x, y, w, h);
    }

    /** Panel with a small title on top (content starts at y + TITLE_H). */
    public static void titled(Gfx c, MinecraftClient mc, int x, int y, int w, int h, String title) {
        glass(c, x, y, w, h);
        c.drawTextWithShadow(mc.textRenderer, title, x + 6, y + 3, Anim.lerpColor(Ui.ACCENT, 0xFFFFFFFF, 0.55f));
        c.fill(x + 5, y + TITLE_H - 1, x + w - 5, y + TITLE_H, Ui.glass(0.10f));
    }

    /** Width a titled panel needs so its title always fits. */
    public static int titleWidth(MinecraftClient mc, String title) { return mc.textRenderer.getWidth(title) + 14; }
}

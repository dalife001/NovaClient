package com.nova.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** A button with a slow, soft hover animation. Frosted glass instead of hard borders. */
public final class AnimButton {
    /** ROW: quiet menu line with icon and subtitle. PILL: small rounded chip. */
    public enum Style { PRIMARY, SECONDARY, TILE, BIG, GHOST, ROW, PILL }

    public int x, y, w, h;
    public String label, sub = "";
    public Style style;
    public Runnable action;
    public boolean enabled = true;

    private int icon = -1;
    private float hover;

    /** 8x8 pixel icons: 0 mods, 1 friends, 2 skins, 3 textures, 4 shaders, 5 settings, 6 singleplayer (moon), 7 multiplayer. */
    private static final String[][] ICONS = {
            {"###.###.", "###.###.", "###.###.", "........", "###.###.", "###.###.", "###.###.", "........"},
            {"..####..", "..####..", "..####..", "........", ".######.", "########", "########", "########"},
            {".##..##.", "########", "########", ".######.", "..####..", "..####..", "..####..", "........"},
            {"##..##..", "##..##..", "..##..##", "..##..##", "##..##..", "##..##..", "..##..##", "..##..##"},
            {"...##...", "#..##..#", ".######.", ".######.", ".######.", ".######.", "#..##..#", "...##..."},
            {"########", "...##...", "...##...", "........", "########", ".....##.", ".....##.", "........"},
            {"..####..", ".###....", "###.....", "###.....", "###.....", ".###....", "..####..", "........"},
            {".##..##.", ".##..##.", "........", "####.###", "####.###", "####.###", "........", "........"}};

    public AnimButton icon(int id) { this.icon = id; return this; }
    private long last = System.nanoTime();

    public AnimButton(int x, int y, int w, int h, String label, Style style, Runnable action) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        this.label = label; this.style = style; this.action = action;
    }

    public AnimButton sub(String s) { this.sub = s; return this; }

    public boolean hit(double mx, double my) { return enabled && Ui.inside(mx, my, x, y, w, h); }

    public void draw(DrawContext c, TextRenderer tr, int mx, int my, float alpha, int yOff) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        hover = Anim.approach(hover, hit(mx, my) ? 1f : 0f, dt, 9f); // slower than before: calmer
        if (alpha <= 0.02f) return;
        int ry = y + yOff;
        float al = enabled ? alpha : alpha * 0.45f;

        switch (style) {
            case PRIMARY -> primary(c, tr, ry, al, false);
            case BIG -> primary(c, tr, ry, al, true);
            case SECONDARY -> {
                glassBase(c, ry, al, 5);
                Ui.centered(c, tr, label, x + w / 2, ry + (h - 8) / 2, Anim.lerpColor(0xFFE2DEF5, Ui.TEXT, hover), al);
            }
            case PILL -> {
                glassBase(c, ry, al, h / 2);
                Ui.centered(c, tr, label, x + w / 2, ry + (h - 8) / 2, Anim.lerpColor(0xFFCFCBE6, Ui.TEXT, hover), al);
            }
            case TILE -> {
                glassBase(c, ry, al, 6);
                drawIcon(c, x + (w - 16) / 2, ry + 5, al);
                Ui.centered(c, tr, label, x + w / 2, ry + h - 12, Anim.lerpColor(Ui.MUTED, Ui.TEXT, hover), al);
            }
            case GHOST -> {
                int tw = tr.getWidth(label);
                Ui.centered(c, tr, label, x + w / 2, ry + (h - 8) / 2, Anim.lerpColor(Ui.MUTED, Ui.TEXT, hover), al);
                int uw = (int) (tw * Anim.easeInOutSine(hover));
                if (uw > 1) Ui.hGradient(c, x + (w - uw) / 2, ry + (h + 8) / 2 + 2, uw, 1, Ui.a(Ui.ACCENT, al), Ui.a(Ui.CYAN, al));
            }
            case ROW -> row(c, tr, ry, al);
        }
    }

    private void drawIcon(DrawContext c, int bx, int by, float al) {
        if (icon < 0 || icon >= ICONS.length) return;
        int ic = Ui.a(Anim.lerpColor(Ui.MUTED, Theme.light(Ui.ACCENT), hover), al);
        String[] rows = ICONS[icon];
        for (int r = 0; r < 8; r++)
            for (int k = 0; k < 8; k++)
                if (rows[r].charAt(k) == '#') c.fill(bx + k * 2, by + r * 2, bx + k * 2 + 2, by + r * 2 + 2, ic);
    }

    /** Frosted base: faint white body, hairline rim that turns accent-coloured, soft halo on hover. */
    private void glassBase(DrawContext c, int ry, float al, int r) {
        float e = Anim.easeInOutSine(hover);
        if (e > 0.02f) Ui.rrect(c, x - 2, ry - 2, w + 4, h + 4, r + 2, Ui.a(Ui.ACCENT, 0.10f * e * al));
        Ui.rrect(c, x, ry, w, h, r, Ui.glass((0.07f + 0.07f * e) * al));
        Ui.rim(c, x, ry, w, h, r, Ui.a(Anim.lerpColor(0x33FFFFFF, Ui.ACCENT, e * 0.8f), al));
    }

    private void primary(DrawContext c, TextRenderer tr, int ry, float al, boolean big) {
        float e = Anim.easeInOutSine(hover);
        int r = big ? 7 : 5;
        Ui.rrect(c, x - 3, ry - 3, w + 6, h + 6, r + 3, Ui.a(Ui.ACCENT, 0.09f * e * al));
        Ui.rrect(c, x - 1, ry - 1, w + 2, h + 2, r + 1, Ui.a(Ui.ACCENT, 0.12f * e * al));
        Ui.rrect(c, x, ry, w, h, r, Ui.a(Anim.lerpColor(Ui.ACCENT_DARK, Ui.ACCENT, 0.25f + 0.30f * e), al));
        if (w > 2 * r + 2) c.fillGradient(x + r, ry + 1, x + w - r, ry + h / 2, Ui.glass(0.20f * al), 0x00FFFFFF);
        Ui.rim(c, x, ry, w, h, r, Ui.glass(0.22f * al));
        if (big) {
            Ui.big(c, tr, label, x + w / 2, ry + 8, 1.3f, Ui.TEXT, al);
            Ui.centered(c, tr, sub, x + w / 2, ry + h - 13, 0xFFEDE6FF, al * 0.85f);
        } else {
            Ui.centered(c, tr, label, x + w / 2, ry + (h - 8) / 2, Ui.TEXT, al);
        }
    }

    /** The main menu line: a quiet row that gently lights up and slides when you point at it. */
    private void row(DrawContext c, TextRenderer tr, int ry, float al) {
        float e = Anim.easeInOutSine(hover);
        if (e > 0.02f) {
            Ui.rrect(c, x - 4, ry - 1, w + 8, h + 2, 9, Ui.glass(0.07f * e * al));
            Ui.rim(c, x - 4, ry - 1, w + 8, h + 2, 9, Ui.a(Ui.ACCENT, 0.32f * e * al));
            Ui.rrect(c, x - 4, ry + 4, 2, h - 8, 1, Ui.a(Ui.ACCENT, 0.9f * e * al)); // soft marker
        }
        int slide = Math.round(e * 4f);
        int ix = x + 6 + slide;
        drawIcon(c, ix, ry + (h - 16) / 2, al);
        int tx = ix + 26;
        int col = Anim.lerpColor(0xFFD9D5EE, Ui.TEXT, e);
        if (sub.isEmpty()) {
            Ui.scaled(c, tr, label, tx, ry + (h - 10) / 2f, 1.2f, col, al);
        } else {
            Ui.scaled(c, tr, label, tx, ry + 3, 1.2f, col, al);
            Ui.text(c, tr, sub, tx, ry + h - 11, Anim.lerpColor(Ui.MUTED, Theme.light(Ui.ACCENT), e * 0.6f), al * 0.9f);
        }
        if (e > 0.05f) Ui.sparkle(c, x + w - 10 - Math.round((1f - e) * 6f), ry + h / 2, 2, Ui.a(Ui.CYAN, e * al));
    }
}

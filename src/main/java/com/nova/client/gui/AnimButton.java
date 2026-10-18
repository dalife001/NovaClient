package com.nova.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** A button with smooth hover animation (glow, lift, sweep). */
public final class AnimButton {
    public enum Style { PRIMARY, SECONDARY, TILE, BIG, GHOST }

    public int x, y, w, h;
    public String label, sub = "";
    public Style style;
    public Runnable action;
    public boolean enabled = true;

    private int icon = -1;
    private float hover;

    /** 8x8 pixel icons for tiles: 0 mods, 1 friends, 2 skins, 3 textures, 4 shaders, 5 settings. */
    private static final String[][] ICONS = {
            {"###.###.", "###.###.", "###.###.", "........", "###.###.", "###.###.", "###.###.", "........"},
            {"..####..", "..####..", "..####..", "........", ".######.", "########", "########", "########"},
            {".##..##.", "########", "########", ".######.", "..####..", "..####..", "..####..", "........"},
            {"##..##..", "##..##..", "..##..##", "..##..##", "##..##..", "##..##..", "..##..##", "..##..##"},
            {"...##...", "#..##..#", ".######.", ".######.", ".######.", ".######.", "#..##..#", "...##..."},
            {"########", "...##...", "...##...", "........", "########", ".....##.", ".....##.", "........"}};

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
        hover = Anim.approach(hover, hit(mx, my) ? 1f : 0f, dt, 16f);
        if (alpha <= 0.02f) return;
        float lift = (style == Style.TILE || style == Style.BIG) ? 2f : 1f;
        int ry = y + yOff - Math.round(hover * lift);
        float al = enabled ? alpha : alpha * 0.45f;

        switch (style) {
            case PRIMARY, BIG -> {
                Ui.round(c, x - 3, ry - 3, w + 6, h + 6, Ui.a(Ui.ACCENT, 0.20f * hover * al));
                Ui.round(c, x - 1, ry - 1, w + 2, h + 2, Ui.a(Anim.lerpColor(0x66FFFFFF, Ui.CYAN, hover), al));
                Ui.round(c, x, ry, w, h, Ui.a(Ui.ACCENT_DARK, al));
                c.fillGradient(x + 1, ry + 1, x + w - 1, ry + h - 1,
                        Ui.a(Anim.lerpColor(Ui.ACCENT, 0xFFA78BFA, hover), al), Ui.a(Ui.ACCENT_DARK, al));
                if (style == Style.BIG) {
                    Ui.big(c, tr, label, x + w / 2, ry + 8, 1.3f, Ui.TEXT, al);
                    Ui.centered(c, tr, sub, x + w / 2, ry + h - 13, 0xFFE9D5FF, al * 0.85f);
                } else {
                    Ui.centered(c, tr, label, x + w / 2, ry + (h - 8) / 2, Ui.TEXT, al);
                }
            }
            case SECONDARY -> {
                base(c, ry, al);
                Ui.centered(c, tr, label, x + w / 2, ry + (h - 8) / 2, Ui.TEXT, al);
            }
            case TILE -> {
                base(c, ry, al);
                int bx = x + (w - 16) / 2, by = ry + 5;
                if (icon >= 0) {
                    int ic = Ui.a(Anim.lerpColor(0xFFA78BFA, 0xFF67E8F9, hover), al);
                    String[] rows = ICONS[icon];
                    for (int r = 0; r < 8; r++)
                        for (int k = 0; k < 8; k++)
                            if (rows[r].charAt(k) == '#') c.fill(bx + k * 2, by + r * 2, bx + k * 2 + 2, by + r * 2 + 2, ic);
                } else {
                    Ui.round(c, bx, by, 16, 16, Ui.a(Anim.lerpColor(Ui.ACCENT_DARK, Ui.ACCENT, hover), al));
                    Ui.centered(c, tr, label.substring(0, 1), x + w / 2, by + 4, Ui.TEXT, al);
                }
                Ui.centered(c, tr, label, x + w / 2, ry + h - 12, Anim.lerpColor(Ui.MUTED, Ui.TEXT, hover), al);
            }
            case GHOST -> {
                int tw = tr.getWidth(label);
                Ui.centered(c, tr, label, x + w / 2, ry + (h - 8) / 2, Anim.lerpColor(Ui.MUTED, Ui.TEXT, hover), al);
                int uw = (int) (tw * hover);
                if (uw > 1) Ui.hGradient(c, x + (w - uw) / 2, ry + (h + 8) / 2 + 2, uw, 1, Ui.a(Ui.ACCENT, al), Ui.a(Ui.CYAN, al));
            }
        }
    }

    private void base(DrawContext c, int ry, float al) {
        Ui.round(c, x - 1, ry - 1, w + 2, h + 2, Ui.a(Anim.lerpColor(0x40FFFFFF, Ui.CYAN, hover), al));
        Ui.round(c, x, ry, w, h, Ui.a(Anim.lerpColor(0xFF181830, 0xFF24244C, hover), al));
        int sweep = (int) (w * hover);
        if (sweep > 3) Ui.hGradient(c, x + 1, ry + h - 2, sweep - 2, 1, Ui.a(Ui.ACCENT, al), Ui.a(Ui.CYAN, al));
    }
}

package com.nova.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Shared colours and drawing helpers so every screen feels the same: soft, rounded, calm.
 * ACCENT / ACCENT_DARK / CYAN keep their old names so older code keeps working;
 * CYAN is now the warm secondary colour of the current palette.
 */
public final class Ui {
    public static int ACCENT = 0xFFB69CFF;
    public static int ACCENT_DARK = 0xFF6C5BD0;
    public static int CYAN = 0xFFFFC2A8;
    public static int SKY_TOP = 0xFF080818;
    public static int SKY_BOT = 0xFF352766;
    public static int AUR1 = 0xFF7CF5D0;
    public static int AUR2 = 0xFFB69CFF;
    public static int AUR3 = 0xFFFF9EC7;

    public static final int PANEL = 0xDC120F2B;
    public static final int PANEL_LIGHT = 0xDC1E1A42;
    public static final int TEXT = 0xFFF4F1FF;
    public static final int MUTED = 0xFFA9A6C4;
    public static final int GREEN = 0xFF8EE6B8;
    public static final int RED = 0xFFFF9AA2;
    public static final int AMBER = 0xFFFFD58A;

    private Ui() {}

    // ---------- colour / text helpers ----------
    /** Multiplies a colour's alpha by `alpha` (0..1). */
    public static int a(int color, float alpha) {
        int ca = (int) ((color >>> 24) * Anim.clamp01(alpha));
        return (ca << 24) | (color & 0xFFFFFF);
    }

    /** White at the given opacity: the basis of every "glass" surface. */
    public static int glass(float alpha) { return ((int) (Anim.clamp01(alpha) * 255f) << 24) | 0xFFFFFF; }

    /** Draws text with an alpha; skips it when nearly invisible (Minecraft treats tiny alpha as opaque!). */
    public static void text(DrawContext c, TextRenderer tr, String s, int x, int y, int color, float alpha) {
        int col = a(color, alpha);
        if ((col >>> 24) < 12) return;
        c.drawTextWithShadow(tr, s, x, y, col);
    }

    public static void centered(DrawContext c, TextRenderer tr, String s, int cx, int y, int color, float alpha) {
        text(c, tr, s, cx - tr.getWidth(s) / 2, y, color, alpha);
    }

    /** Text drawn at a larger size around centre x. */
    public static void big(DrawContext c, TextRenderer tr, String s, int cx, int y, float scale, int color, float alpha) {
        c.getMatrices().push();
        c.getMatrices().translate(cx - tr.getWidth(s) * scale / 2f, y, 0);
        c.getMatrices().scale(scale, scale, 1f);
        text(c, tr, s, 0, 0, color, alpha);
        c.getMatrices().pop();
    }

    /** Left-aligned text at a larger size. */
    public static void scaled(DrawContext c, TextRenderer tr, String s, float x, float y, float scale, int color, float alpha) {
        c.getMatrices().push();
        c.getMatrices().translate(x, y, 0);
        c.getMatrices().scale(scale, scale, 1f);
        text(c, tr, s, 0, 0, color, alpha);
        c.getMatrices().pop();
    }

    /** Letters that slowly drift between the two palette colours, centred on cx. */
    public static void shimmer(DrawContext c, TextRenderer tr, String s, int cx, int y, float scale, float alpha) {
        int total = 0;
        for (int i = 0; i < s.length(); i++) total += tr.getWidth(String.valueOf(s.charAt(i)));
        flow(c, tr, s, cx - total * scale / 2f, y, scale, 0, alpha, false);
    }

    /**
     * Left-aligned, letter-spaced text whose colour flows slowly through the palette.
     * With `bob` each letter also floats up and down very gently.
     */
    public static void flow(DrawContext c, TextRenderer tr, String s, float x, float y, float scale, float spacing, float alpha, boolean bob) {
        float t = Anim.seconds();
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            float k = 0.5f + 0.5f * (float) Math.sin(t * 0.8f - i * 0.9f);
            float dy = bob ? (float) Math.sin(t * 0.9f + i * 0.8f) * scale * 0.35f : 0f;
            scaled(c, tr, ch, cx, y + dy, scale, Anim.lerpColor(Theme.light(ACCENT), Theme.light(CYAN), k), alpha);
            cx += (tr.getWidth(ch) + spacing) * scale;
        }
    }

    /** Width of letter-spaced text (matches flow()). */
    public static float flowWidth(TextRenderer tr, String s, float scale, float spacing) {
        float w = 0;
        for (int i = 0; i < s.length(); i++) w += (tr.getWidth(String.valueOf(s.charAt(i))) + spacing) * scale;
        return w;
    }

    // ---------- shapes ----------
    private static final int MAX_R = 12;
    private static final int[][] INSETS = new int[MAX_R + 1][];

    private static int[] insets(int r) {
        if (INSETS[r] == null) {
            int[] a = new int[r];
            for (int i = 0; i < r; i++) {
                double dy = r - i - 0.5;
                a[i] = r - (int) Math.round(Math.sqrt(r * (double) r - dy * dy));
            }
            INSETS[r] = a;
        }
        return INSETS[r];
    }

    private static int clampR(int r, int w, int h) { return Math.max(0, Math.min(Math.min(r, MAX_R), Math.min(w, h) / 2)); }

    /** Filled rectangle with truly round corners (radius r, softened edge). */
    public static void rrect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        r = clampR(r, w, h);
        if (r <= 1) { c.fill(x, y, x + w, y + h, color); return; }
        int[] ins = insets(r);
        int soft = (color & 0xFFFFFF) | (((color >>> 24) * 2 / 5) << 24);
        for (int i = 0; i < r; i++) {
            int in = ins[i];
            c.fill(x + in, y + i, x + w - in, y + i + 1, color);
            c.fill(x + in, y + h - 1 - i, x + w - in, y + h - i, color);
            if (r >= 4 && in > 0) { // one half-transparent pixel makes the curve look smooth
                c.fill(x + in - 1, y + i, x + in, y + i + 1, soft);
                c.fill(x + w - in, y + i, x + w - in + 1, y + i + 1, soft);
                c.fill(x + in - 1, y + h - 1 - i, x + in, y + h - i, soft);
                c.fill(x + w - in, y + h - 1 - i, x + w - in + 1, y + h - i, soft);
            }
        }
        c.fill(x, y + r, x + w, y + h - r, color);
    }

    /** A one pixel rounded outline. */
    public static void rim(DrawContext c, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        r = clampR(r, w, h);
        if (r <= 1) { outline(c, x, y, w, h, color); return; }
        int[] ins = insets(r);
        c.fill(x + ins[0], y, x + w - ins[0], y + 1, color);
        c.fill(x + ins[0], y + h - 1, x + w - ins[0], y + h, color);
        for (int i = 1; i < r; i++) {
            int in = ins[i], end = Math.max(in + 1, ins[i - 1]);
            c.fill(x + in, y + i, x + end, y + i + 1, color);
            c.fill(x + w - end, y + i, x + w - in, y + i + 1, color);
            c.fill(x + in, y + h - 1 - i, x + end, y + h - i, color);
            c.fill(x + w - end, y + h - 1 - i, x + w - in, y + h - i, color);
        }
        c.fill(x, y + r, x + 1, y + h - r, color);
        c.fill(x + w - 1, y + r, x + w, y + h - r, color);
    }

    /** Old name kept: now draws a properly rounded rectangle. */
    public static void round(DrawContext c, int x, int y, int w, int h, int color) {
        rrect(c, x, y, w, h, Math.min(4, Math.min(w, h) / 2), color);
    }

    /** Soft drop shadow under a rounded shape. */
    public static void shadow(DrawContext c, int x, int y, int w, int h, int r, float alpha) {
        rrect(c, x - 3, y, w + 6, h + 5, r + 3, a(0xFF000000, 0.07f * alpha));
        rrect(c, x - 1, y + 1, w + 2, h + 3, r + 1, a(0xFF000000, 0.10f * alpha));
    }

    /** The standard frosted surface: shadow, tinted body, light top edge, thin rim. */
    public static void panel(DrawContext c, int x, int y, int w, int h, int r, int fill, float alpha) {
        shadow(c, x, y, w, h, r, alpha);
        rrect(c, x, y, w, h, r, a(fill, alpha));
        int hh = Math.min(h / 3, 18);
        if (hh > 2 && w > 2 * r + 2) c.fillGradient(x + r, y + 1, x + w - r, y + 1 + hh, glass(0.07f * alpha), 0x00FFFFFF);
        rim(c, x, y, w, h, r, glass(0.13f * alpha));
    }

    public static void card(DrawContext c, int x, int y, int w, int h) { panel(c, x, y, w, h, 7, PANEL, 1f); }

    /** Soft tinted band at the top of a card, with a hairline under it (replaces the old hard coloured bar). */
    public static void headerBand(DrawContext c, int x, int y, int w) {
        c.fillGradient(x + 6, y + 1, x + w - 6, y + 26, a(ACCENT, 0.22f), a(ACCENT, 0f));
        c.fill(x + 10, y + 26, x + w - 10, y + 27, glass(0.10f));
    }

    /** Left-to-right gradient. */
    public static void hGradient(DrawContext c, int x, int y, int w, int h, int c1, int c2) {
        if (w <= 0) return;
        int step = 2;
        for (int i = 0; i < w; i += step) {
            c.fill(x + i, y, x + Math.min(i + step, w), y + h, Anim.lerpColor(c1, c2, (float) i / w));
        }
    }

    /** A rounded progress or slider fill: rounded track, rounded fill in the accent colours. */
    public static void bar(DrawContext c, int x, int y, int w, int h, float fraction, float alpha) {
        rrect(c, x, y, w, h, h / 2, glass(0.10f * alpha));
        int fw = (int) (w * Anim.clamp01(fraction));
        if (fw >= h) {
            rrect(c, x, y, fw, h, h / 2, a(ACCENT, alpha));
            if (fw > h + 4) hGradient(c, x + h / 2, y + h / 4, fw - h, Math.max(1, h / 2), a(ACCENT, 0.0f), a(CYAN, 0.45f * alpha));
        }
    }

    public static void outline(DrawContext c, int x, int y, int w, int h, int color) {
        c.fill(x, y, x + w, y + 1, color);
        c.fill(x, y + h - 1, x + w, y + h, color);
        c.fill(x, y, x + 1, y + h, color);
        c.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** A four-point twinkle. */
    public static void sparkle(DrawContext c, int cx, int cy, int size, int color) {
        if ((color >>> 24) < 4 || size < 1) return;
        c.fill(cx - size, cy, cx + size + 1, cy + 1, a(color, 0.55f));
        c.fill(cx, cy - size, cx + 1, cy + size + 1, a(color, 0.55f));
        c.fill(cx - 1, cy, cx + 2, cy + 1, color);
        c.fill(cx, cy - 1, cx + 1, cy + 2, color);
    }

    /** Soft round glow made of stacked translucent ellipses. */
    public static void glow(DrawContext c, int cx, int cy, int rx, int ry, int rgb, float intensity) {
        if (rx < 2 || ry < 2 || intensity <= 0f) return;
        int layers = 9;
        int per = Math.max(1, (int) (Anim.clamp01(intensity) * 255f / layers));
        int color = (per << 24) | (rgb & 0xFFFFFF);
        int step = Math.max(2, ry / 22);
        for (int i = 0; i < layers; i++) {
            float k = 1f - i / (float) layers;
            double rxx = rx * k, ryy = ry * k;
            for (int dy = (int) -ryy; dy < (int) ryy; dy += step) {
                double f = (dy + step / 2.0) / ryy;
                if (f * f >= 1) continue;
                int half = (int) (rxx * Math.sqrt(1 - f * f));
                c.fill(cx - half, cy + dy, cx + half, cy + dy + step, color);
            }
        }
    }

    // ---------- backgrounds ----------
    /** Mouse position relative to the window centre, -0.5..0.5 (for parallax). */
    public static float parallaxX() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow().getWidth() <= 0) return 0;
        return Math.max(-0.5f, Math.min(0.5f, (float) (mc.mouse.getX() / mc.getWindow().getWidth()) - 0.5f));
    }

    public static float parallaxY() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow().getHeight() <= 0) return 0;
        return Math.max(-0.5f, Math.min(0.5f, (float) (mc.mouse.getY() / mc.getWindow().getHeight()) - 0.5f));
    }

    /** The living night sky (or your own wallpaper with soft stars on top). */
    public static void background(DrawContext c, int w, int h) {
        float dx = parallaxX(), dy = parallaxY();
        if (BackgroundManager.draw(c, w, h, dx, dy)) {
            Cosmos.stars(c, w, h, dx, dy, 0.55f);
        } else {
            Cosmos.scene(c, w, h, dx, dy);
        }
        // gentle vignette keeps the eye in the middle
        c.fillGradient(0, 0, w, Math.max(8, h / 6), 0x40000008, 0x00000008);
        c.fillGradient(0, h - Math.max(8, h / 6), w, h, 0x00000008, 0x50000008);
    }
}

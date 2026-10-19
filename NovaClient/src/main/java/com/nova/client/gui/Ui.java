package com.nova.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.Random;

/** Shared colours and drawing helpers so every screen looks the same. */
public final class Ui {
    public static final int ACCENT = 0xFF8B5CF6;
    public static final int ACCENT_DARK = 0xFF5B34B8;
    public static final int CYAN = 0xFF22D3EE;
    public static final int PANEL = 0xE0121226;
    public static final int PANEL_LIGHT = 0xE01C1C3A;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int MUTED = 0xFF9CA3AF;
    public static final int GREEN = 0xFF34D399;
    public static final int RED = 0xFFF87171;
    public static final int AMBER = 0xFFFBBF24;

    private Ui() {}

    // ---------- colour / text helpers ----------
    /** Multiplies a colour's alpha by `alpha` (0..1). */
    public static int a(int color, float alpha) {
        int ca = (int) ((color >>> 24) * Anim.clamp01(alpha));
        return (ca << 24) | (color & 0xFFFFFF);
    }

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

    /** Letters that shimmer between lavender and cyan, centred on cx. */
    public static void shimmer(DrawContext c, TextRenderer tr, String s, int cx, int y, float scale, float alpha) {
        float t = Anim.seconds();
        int[] ws = new int[s.length()];
        int total = 0;
        for (int i = 0; i < ws.length; i++) { ws[i] = tr.getWidth(String.valueOf(s.charAt(i))); total += ws[i]; }
        c.getMatrices().push();
        c.getMatrices().translate(cx - total * scale / 2f, y, 0);
        c.getMatrices().scale(scale, scale, 1f);
        int x = 0;
        for (int i = 0; i < ws.length; i++) {
            float k = 0.5f + 0.5f * (float) Math.sin(t * 2.2f - i * 0.8f);
            text(c, tr, String.valueOf(s.charAt(i)), x, 0, Anim.lerpColor(0xFFE9D5FF, 0xFF67E8F9, k), alpha);
            x += ws[i];
        }
        c.getMatrices().pop();
    }

    // ---------- shapes ----------
    /** Rectangle with softly cut corners. */
    public static void round(DrawContext c, int x, int y, int w, int h, int color) {
        c.fill(x + 2, y, x + w - 2, y + h, color);
        c.fill(x, y + 2, x + w, y + h - 2, color);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
    }

    public static void card(DrawContext c, int x, int y, int w, int h) {
        round(c, x - 1, y - 1, w + 2, h + 2, 0x33FFFFFF);
        round(c, x, y, w, h, PANEL);
    }

    /** Left-to-right gradient. */
    public static void hGradient(DrawContext c, int x, int y, int w, int h, int c1, int c2) {
        if (w <= 0) return;
        int step = 2;
        for (int i = 0; i < w; i += step) {
            c.fill(x + i, y, x + Math.min(i + step, w), y + h, Anim.lerpColor(c1, c2, (float) i / w));
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

    // ---------- animated background ----------
    private static final int N = 70;
    private static final float[] PX = new float[N], PY = new float[N], PS = new float[N], PZ = new float[N], PP = new float[N];

    static {
        Random r = new Random(42);
        for (int i = 0; i < N; i++) {
            PX[i] = r.nextFloat(); PY[i] = r.nextFloat();
            PS[i] = 0.01f + r.nextFloat() * 0.03f;
            PZ[i] = r.nextFloat();
            PP[i] = r.nextFloat() * 6.283f;
        }
    }

    /** Gradient (or your image) + drifting glows + floating particles, with mouse parallax. */
    public static void background(DrawContext c, int w, int h) {
        float dx = 0, dy = 0;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow().getWidth() > 0 && mc.getWindow().getHeight() > 0) {
            dx = Math.max(-0.5f, Math.min(0.5f, (float) (mc.mouse.getX() / mc.getWindow().getWidth()) - 0.5f));
            dy = Math.max(-0.5f, Math.min(0.5f, (float) (mc.mouse.getY() / mc.getWindow().getHeight()) - 0.5f));
        }
        if (!BackgroundManager.draw(c, w, h, dx, dy)) {
            c.fillGradient(0, 0, w, h, 0xFF070714, 0xFF2A1250);
            double t = Anim.seconds();
            int base = Math.min(w, h);
            glow(c, (int) (w * 0.30 + Math.sin(t * 0.40) * w * 0.08 - dx * 40),
                    (int) (h * 0.30 + Math.cos(t * 0.30) * h * 0.08 - dy * 30), (int) (base * 0.50), 0x8B5CF6);
            glow(c, (int) (w * 0.82 + Math.cos(t * 0.35) * w * 0.06 - dx * 60),
                    (int) (h * 0.78 + Math.sin(t * 0.45) * h * 0.06 - dy * 40), (int) (base * 0.42), 0x22D3EE);
        }
        particles(c, w, h, dx, dy);
    }

    private static void particles(DrawContext c, int w, int h, float dx, float dy) {
        float t = Anim.seconds();
        for (int i = 0; i < N; i++) {
            float yy = ((PY[i] - t * PS[i]) % 1f + 1f) % 1f;
            int x = (int) (PX[i] * w + Math.sin(t * 0.3 + PP[i]) * 6 + dx * PZ[i] * 26);
            int y = (int) (yy * h + dy * PZ[i] * 16);
            int size = PZ[i] > 0.75f ? 2 : 1;
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * 1.6 + PP[i]));
            int alpha = (int) (tw * (70 + PZ[i] * 110));
            c.fill(x, y, x + size, y + size, (alpha << 24) | (i % 3 == 0 ? 0x67E8F9 : 0xE9D5FF));
        }
    }

    private static void glow(DrawContext c, int cx, int cy, int radius, int rgb) {
        int layers = 8;
        for (int i = 0; i < layers; i++) {
            int r = radius * (layers - i) / layers;
            int color = (6 << 24) | rgb;
            for (int dy = -r; dy < r; dy += 3) {
                int half = (int) Math.sqrt((double) r * r - (double) dy * dy);
                c.fill(cx - half, cy + dy, cx + half, cy + dy + 3, color);
            }
        }
    }
}

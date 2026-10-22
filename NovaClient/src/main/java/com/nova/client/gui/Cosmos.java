package com.nova.client.gui;


import java.util.Random;

/**
 * The calm night sky behind every Nova screen: slow aurora curtains, twinkling stars,
 * layered hills with mist, an occasional shooting star, and (on the hub) a ringed planet.
 * Everything moves slowly on purpose.
 */
public final class Cosmos {
    private static final int N = 120;
    private static final float[] SX = new float[N], SY = new float[N], SZ = new float[N], SP = new float[N];

    static {
        Random r = new Random(7);
        for (int i = 0; i < N; i++) {
            SX[i] = r.nextFloat();
            SY[i] = r.nextFloat() * 0.78f;
            SZ[i] = r.nextFloat();
            SP[i] = r.nextFloat() * 6.283f;
        }
    }

    private Cosmos() {}

    /** Full scene: sky, stars, aurora, shooting star, mist, hills. */
    public static void scene(Gfx c, int w, int h, float dx, float dy) {
        float t = Anim.seconds();
        int mid = Anim.lerpColor(Ui.SKY_TOP, Ui.SKY_BOT, 0.42f);
        int split = (int) (h * 0.62f);
        c.fillGradient(0, 0, w, split, Ui.SKY_TOP, mid);
        c.fillGradient(0, split, w, h, mid, Ui.SKY_BOT);
        stars(c, w, h, dx, dy, 1f);
        aurora(c, w, h, dx, t);
        shootingStar(c, w, h, t);
        // horizon mist
        c.fillGradient(0, (int) (h * 0.60f), w, (int) (h * 0.84f), Ui.a(Ui.ACCENT, 0f), Ui.a(Ui.ACCENT, 0.10f));
        hills(c, w, h, dx, t);
        fireflies(c, w, h, dx, t);
    }

    /** Twinkling stars in three depth layers; the biggest ones sparkle. */
    public static void stars(Gfx c, int w, int h, float dx, float dy, float strength) {
        float t = Anim.seconds();
        for (int i = 0; i < N; i++) {
            float z = SZ[i];
            int x = (int) (SX[i] * w - dx * (4 + z * 22));
            int y = (int) (SY[i] * h - dy * (2 + z * 12));
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * (0.7f + z * 0.8f) + SP[i]));
            int alpha = (int) (tw * (60 + z * 150) * strength);
            if (alpha < 6) continue;
            int rgb = (i % 4 == 0 ? Theme.light(Ui.CYAN) : 0xFFFFFF) & 0xFFFFFF;
            if (z > 0.93f) Ui.sparkle(c, x, y, 2 + (int) (z * 2), (Math.min(255, alpha + 30) << 24) | rgb);
            else c.fill(x, y, x + (z > 0.7f ? 2 : 1), y + (z > 0.7f ? 2 : 1), (alpha << 24) | rgb);
        }
    }

    /** Slow curtains of light. Three ribbons, each a row of soft vertical gradients. */
    private static void aurora(Gfx c, int w, int h, float dx, float t) {
        int[] cols = {Ui.AUR1, Ui.AUR2, Ui.AUR3};
        int step = 4;
        for (int k = 0; k < 3; k++) {
            int rgb = cols[k] & 0xFFFFFF;
            float baseY = h * (0.20f + 0.11f * k);
            float breathe = 0.60f + 0.40f * (float) Math.sin(t * 0.22f + k * 2.1f);
            for (int x = 0; x < w; x += step) {
                float u = x / (float) w;
                float wave = (float) (Math.sin(u * 3.1 + t * 0.12 + k * 1.7) * 0.55 + Math.sin(u * 7.3 - t * 0.20 + k) * 0.25);
                int yc = (int) (baseY + wave * h * 0.09f - dx * (8 + 8 * k));
                float rays = 0.55f + 0.45f * (float) Math.sin(u * 41 + t * 0.45 + k * 2.3);
                float env = Anim.smooth(Math.min(u, 1f - u) * 3.2f);
                int a = (int) (86 * rays * env * breathe);
                if (a < 2) continue;
                int up = (int) (h * (0.17f + 0.06f * Math.sin(u * 5 + t * 0.2 + k)));
                c.fillGradient(x, yc - up, x + step, yc, rgb, (a << 24) | rgb);
                c.fillGradient(x, yc, x + step, yc + up / 2, (a << 24) | rgb, rgb);
            }
        }
    }

    /** One slow streak every 17 seconds. */
    private static void shootingStar(Gfx c, int w, int h, float t) {
        float cyc = t % 17f;
        if (cyc > 1.1f) return;
        int seed = (int) (t / 17f);
        float sx = w * (0.15f + 0.6f * Anim.hash(seed * 3 + 1));
        float sy = h * (0.05f + 0.22f * Anim.hash(seed * 3 + 2));
        float hx = sx + cyc * w * 0.22f, hy = sy + cyc * h * 0.10f;
        float fade = 1f - Anim.smooth(cyc / 1.1f);
        for (int i = 0; i < 16; i++) {
            float k = (1f - i / 16f) * fade;
            int x = (int) (hx - i * 4), y = (int) (hy - i * 1.7f);
            c.fill(x, y, x + 2, y + 1, Ui.a(0xFFFFFFFF, k * 0.9f));
        }
    }

    /** Three layers of rolling hills, lighter and mistier the further away. */
    private static void hills(Gfx c, int w, int h, float dx, float t) {
        int far = Anim.lerpColor(Ui.SKY_BOT, Ui.ACCENT, 0.34f);
        int mid = Anim.lerpColor(Ui.SKY_BOT, Ui.SKY_TOP, 0.42f);
        int near = Anim.lerpColor(Ui.SKY_TOP, 0xFF000000, 0.55f);
        int[] top = {far, mid, near};
        int[] bottom = {Anim.lerpColor(far, Ui.SKY_TOP, 0.5f), Anim.lerpColor(mid, Ui.SKY_TOP, 0.6f), 0xFF03030A};
        for (int layer = 0; layer < 3; layer++) {
            float baseY = h * (0.77f + 0.065f * layer);
            float amp = h * (0.070f - 0.014f * layer);
            float shift = dx * (6 + layer * 16);
            for (int x = 0; x < w; x += 3) {
                double xx = x + shift;
                double y = baseY + amp * (Math.sin(xx * 0.011 + layer * 2.0) * 0.55
                        + Math.sin(xx * 0.027 + layer * 4.1) * 0.28 + Math.sin(xx * 0.0045 + layer) * 0.5);
                c.fillGradient(x, (int) y, x + 3, h, top[layer], bottom[layer]);
            }
        }
    }

    /** A few warm fireflies drifting over the hills. */
    private static void fireflies(Gfx c, int w, int h, float dx, float t) {
        for (int i = 0; i < 16; i++) {
            float ph = Anim.hash(i * 7 + 3) * 6.283f;
            float fx = (Anim.hash(i * 5 + 1) * w + (float) Math.sin(t * 0.21f + ph) * 18f - dx * 20f);
            float fy = h * (0.70f + 0.25f * Anim.hash(i * 11 + 2)) + (float) Math.cos(t * 0.17f + ph * 1.3f) * 9f;
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 0.8f + ph * 2f);
            float a = pulse * pulse;
            if (a < 0.08f) continue;
            int x = (int) fx, y = (int) fy;
            c.fill(x - 2, y - 2, x + 3, y + 3, Ui.a(Ui.CYAN, 0.10f * a));
            c.fill(x - 1, y - 1, x + 2, y + 2, Ui.a(Theme.light(Ui.CYAN), 0.35f * a));
            c.fill(x, y, x + 1, y + 1, Ui.a(0xFFFFFFFF, 0.9f * a));
        }
    }

    // ---------- the planet ----------
    /** A slowly turning banded planet with a ring and a small orbiting moon. */
    public static void planet(Gfx c, int cx, int cy, int r, float alpha) {
        if (alpha <= 0.02f || r < 8) return;
        float t = Anim.seconds();
        float breath = 0.75f + 0.25f * (float) Math.sin(t * 0.5f);
        Ui.glow(c, cx, cy, (int) (r * 2.7f), (int) (r * 2.7f), Ui.ACCENT, 0.30f * alpha * breath);
        Ui.glow(c, cx, cy, (int) (r * 1.5f), (int) (r * 1.5f), Theme.light(Ui.CYAN), 0.12f * alpha);

        // moon orbit
        float ang = t * 0.16f;
        float mx = (float) Math.cos(ang) * r * 2.5f, my = (float) Math.sin(ang) * r * 0.55f;
        boolean moonFront = Math.sin(ang) > 0;
        int mr = Math.max(3, r / 6);
        if (!moonFront) moon(c, (int) (cx + mx), (int) (cy + my - r * 0.35f), mr, alpha);

        ring(c, cx, cy, r, alpha, false);
        body(c, cx, cy, r, t, alpha);
        ring(c, cx, cy, r, alpha, true);

        if (moonFront) moon(c, (int) (cx + mx), (int) (cy + my - r * 0.35f), mr, alpha);
    }

    /** Lit sphere: each scanline is a few soft steps from the lit side to the shadow side. */
    private static void body(Gfx c, int cx, int cy, int r, float t, float alpha) {
        int lit = Anim.lerpColor(Ui.ACCENT, 0xFFFFFFFF, 0.45f);
        int dark = Anim.lerpColor(Ui.ACCENT_DARK, 0xFF05050F, 0.62f);
        // light comes from the upper left, a little towards the viewer
        final float lx = -0.55f, ly = -0.45f, lz = 0.70f;
        for (int dy = -r; dy < r; dy++) {
            double yy = dy + 0.5;
            int half = (int) Math.sqrt(r * (double) r - yy * yy);
            if (half < 1) continue;
            float ny = (float) (yy / r);
            float band = 0.5f + 0.5f * (float) Math.sin(dy * 0.42 + t * 0.06 + Math.sin(dy * 0.15) * 2);
            int segs = Math.max(3, Math.min(10, half / 3));
            for (int sI = 0; sI < segs; sI++) {
                int x0 = -half + (2 * half * sI) / segs, x1 = -half + (2 * half * (sI + 1)) / segs;
                float nx = ((x0 + x1) * 0.5f) / r;
                float nz2 = 1f - nx * nx - ny * ny;
                float nz = nz2 > 0 ? (float) Math.sqrt(nz2) : 0f;
                float l = Math.max(0f, nx * lx + ny * ly + nz * lz);
                float k = Anim.smooth(l * 1.25f);
                int col = Anim.lerpColor(dark, lit, k);
                col = Anim.lerpColor(col, Ui.CYAN, 0.26f * band * (0.35f + 0.65f * k));
                c.fill(cx + x0, cy + dy, cx + x1, cy + dy + 1, Ui.a(col, alpha));
            }
        }
        // a thin bright atmosphere on the lit edge
        for (int dy = -r + 2; dy < 0; dy += 2) {
            double yy = dy + 0.5;
            int half = (int) Math.sqrt(r * (double) r - yy * yy);
            c.fill(cx - half, cy + dy, cx - half + 1, cy + dy + 2, Ui.a(0xFFFFFFFF, 0.30f * alpha));
        }
    }

    private static void ring(Gfx c, int cx, int cy, int r, float alpha, boolean front) {
        float tilt = -0.32f;
        float cos = (float) Math.cos(tilt), sin = (float) Math.sin(tilt);
        float rx = r * 1.95f, ry = r * 0.38f;
        int pts = Math.max(60, (int) (6.283f * rx / 1.6f));
        for (int pass = 0; pass < 2; pass++) {
            float k = pass == 0 ? 1f : 1.13f;
            int col = pass == 0 ? Ui.a(Theme.light(Ui.ACCENT), 0.50f * alpha) : Ui.a(Ui.CYAN, 0.26f * alpha);
            for (int i = 0; i < pts; i++) {
                float a = i * 6.2831853f / pts;
                float sa = (float) Math.sin(a);
                if ((sa > 0) != front) continue;
                float ex = (float) Math.cos(a) * rx * k, ey = sa * ry * k;
                int px = cx + Math.round(ex * cos - ey * sin), py = cy + Math.round(ex * sin + ey * cos);
                c.fill(px, py, px + 2, py + 1, col);
            }
        }
    }

    private static void moon(Gfx c, int cx, int cy, int r, float alpha) {
        Ui.glow(c, cx, cy, r * 3, r * 3, 0xFFFFFF, 0.14f * alpha);
        for (int dy = -r; dy < r; dy++) {
            double yy = dy + 0.5;
            int half = (int) Math.sqrt(r * (double) r - yy * yy);
            c.fill(cx - half, cy + dy, cx + half, cy + dy + 1, Ui.a(0xFFEFEAFF, alpha));
            c.fill(cx + half / 3, cy + dy, cx + half, cy + dy + 1, Ui.a(0xFF8E88B8, 0.55f * alpha));
        }
    }
}

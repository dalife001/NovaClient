package com.nova.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.nova.launcher.LauncherApp.*;

/**
 * The whole launcher window, painted as one pixel-art galaxy.
 *
 *  - Saturn IS the Play button. Its ring is the progress bar.
 *  - Your skin stands on a little floating island; the nametag above it is your account.
 *  - A magnifying dock floats at the bottom (console, skin, folder, add to launcher, theme, settings).
 *  - No boxes, no sidebar: the sky is the interface.
 *
 * The scene is drawn at low resolution and scaled up with nearest-neighbour, like the client's menu.
 */
final class Orbit extends JComponent {
    private static final double TAU = Math.PI * 2;

    // ------------------------------------------------------------------ state (set by LauncherApp)
    String status = "Ready", who = "Not logged in", loginText = "log in with microsoft", mc = "1.21.4", note = "", noteUrl, themeName = "";
    int ramMb = 4096;
    boolean working, indeterminate, loggedIn;
    float progress;
    Runnable onPlay = () -> {}, onLogin = () -> {}, onSettings = () -> {}, onTheme = () -> {}, onSkin = () -> {}, onVersion = () -> {}, onMods = () -> {},
            onFolder = () -> {}, onOfficial = () -> {}, onMin = () -> {}, onClose = () -> {};

    private final List<String> lines = new ArrayList<>();
    private int scroll;
    private boolean consoleOpen;
    private float consoleT;

    // ------------------------------------------------------------------ scene
    private BufferedImage buf;
    private int[] px;
    private int SW, SH, cell = 4;
    private static final int STARS = 150;
    private final float[] sx = new float[STARS], sy = new float[STARS], sp = new float[STARS];
    private final int[] edge = new int[20];
    private int[] skinPx = SkinRenderer.front(defaultSkin(), false);
    private double planetX, planetY, planetR;      // in scene cells
    private int islandX, islandTop;                 // in scene cells
    private final long t0 = System.nanoTime();
    private long last = t0;

    // ------------------------------------------------------------------ interaction
    private record Hit(String id, Shape shape, Runnable act) {}
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Float> hov = new HashMap<>();
    private final float[] mag = new float[7];
    private Point mouse = new Point(-9999, -9999);
    private String hovered;
    private Point pressScreen, frameStart;
    private String pressedId;
    private boolean dragged;

    Orbit() {
        setOpaque(true);
        Random r = new Random(7);
        for (int i = 0; i < STARS; i++) { sx[i] = r.nextFloat(); sy[i] = r.nextFloat(); sp[i] = r.nextFloat() * 6.28f; }
        for (int i = 0; i < edge.length; i++) edge[i] = r.nextInt(3) - 1;

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) { mouse = e.getPoint(); updateHover(); }
            @Override public void mouseDragged(MouseEvent e) {
                mouse = e.getPoint();
                if (pressScreen != null && pressedId == null) {
                    Window w = SwingUtilities.getWindowAncestor(Orbit.this);
                    Point now = e.getLocationOnScreen();
                    if (w != null) w.setLocation(frameStart.x + now.x - pressScreen.x, frameStart.y + now.y - pressScreen.y);
                    dragged = true;
                }
                updateHover();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                mouse = e.getPoint();
                Hit h = hitAt(mouse);
                pressedId = h == null ? null : h.id();
                dragged = false;
                Window w = SwingUtilities.getWindowAncestor(Orbit.this);
                pressScreen = e.getLocationOnScreen();
                frameStart = w == null ? new Point() : w.getLocation();
            }
            @Override public void mouseReleased(MouseEvent e) {
                Hit h = hitAt(e.getPoint());
                if (!dragged && h != null && h.id().equals(pressedId) && h.act() != null) h.act().run();
                pressScreen = null; pressedId = null;
            }
            @Override public void mouseExited(MouseEvent e) { mouse = new Point(-9999, -9999); updateHover(); }
        });
        addMouseWheelListener(e -> {
            if (consoleOpen) { scroll = Math.max(0, Math.min(Math.max(0, lines.size() - 3), scroll - e.getWheelRotation() * 3)); }
        });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "esc");
        getActionMap().put("esc", new AbstractAction() { public void actionPerformed(ActionEvent e) { consoleOpen = false; } });
    }

    // ------------------------------------------------------------------ API
    void setSkin(java.awt.image.BufferedImage skin, boolean slim) {
        skinPx = SkinRenderer.front(skin == null ? defaultSkin() : skin, slim);
    }
    void addLog(String s) {
        lines.add(s);
        if (lines.size() > 800) lines.remove(0);
        if (scroll > 0) scroll++;
    }
    void simulateMouse(int x, int y) { mouse = new Point(x, y); }
    void toggleConsole() { consoleOpen = !consoleOpen; }
    boolean consoleOpen() { return consoleOpen; }

    private Hit hitAt(Point p) {
        for (int i = hits.size() - 1; i >= 0; i--) if (hits.get(i).shape().contains(p)) return hits.get(i);
        return null;
    }
    private void updateHover() {
        Hit h = hitAt(mouse);
        hovered = h == null ? null : h.id();
        setCursor(h != null && h.act() != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
    }
    private float hv(String id, float dt) {
        float cur = hov.getOrDefault(id, 0f), tgt = id.equals(hovered) ? 1f : 0f;
        cur += (tgt - cur) * Math.min(1f, dt * 10f);
        hov.put(id, cur);
        return cur;
    }

    // ------------------------------------------------------------------ colour helpers
    private static int rgb(Color c) { return c.getRGB() & 0xFFFFFF; }
    private static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t + 0.5f);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t + 0.5f);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t + 0.5f);
        return r << 16 | g << 8 | bl;
    }
    private void put(int x, int y, int c, float a) {
        if (x < 0 || y < 0 || x >= SW || y >= SH || a <= 0f) return;
        int i = y * SW + x;
        px[i] = a >= 1f ? c : lerp(px[i], c, a);
    }
    private void disc(double cx, double cy, double r, int c, float a) {
        for (int y = (int) Math.floor(cy - r); y <= (int) Math.ceil(cy + r); y++)
            for (int x = (int) Math.floor(cx - r); x <= (int) Math.ceil(cx + r); x++)
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) put(x, y, c, a);
    }
    private void rect(int x, int y, int w, int h, int c, float a) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) put(xx, yy, c, a);
    }

    // ------------------------------------------------------------------ scene
    private void ring(double cx, double cy, double r, boolean front, int acc, int sec, double t, float hover) {
        double tilt = -0.30, ct = Math.cos(tilt), st = Math.sin(tilt);
        int[] col = {lerp(acc, 0xFFFFFF, 0.78f), lerp(acc, 0xFFFFFF, 0.42f), -1, lerp(sec, acc, 0.35f)};
        for (double a = 0; a < TAU; a += 0.0022) {
            if ((Math.sin(a) > 0) != front) continue;
            double u = (((a - Math.PI) % TAU) + TAU) % TAU / TAU;     // 0..1 around the ring
            float lit = 1f, bright = 0f;
            if (working) {
                if (indeterminate) { double d = (((u - (t * 0.35) % 1.0) % 1.0) + 1.0) % 1.0; if (d < 0.18) { lit = 1f; bright = 1f; } else lit = 0.28f; }
                else if (u <= progress) { lit = 1f; bright = 1f; } else lit = 0.28f;
            } else {
                double d = (((u - (t * 0.07) % 1.0) % 1.0) + 1.0) % 1.0;
                bright = (float) Math.max(0, 1 - d / 0.09) * (0.30f + 0.55f * hover);
            }
            for (int k = 0; k < 4; k++) {
                if (col[k] < 0) continue;
                double s = 1.0 - k * 0.055;
                double X = Math.cos(a) * r * 2.15 * s, Y = Math.sin(a) * r * 0.58 * s;
                int c = bright > 0 ? lerp(col[k], 0xFFFFFF, bright * 0.85f) : col[k];
                put((int) Math.round(cx + X * ct - Y * st), (int) Math.round(cy + X * st + Y * ct), c, 0.92f * lit);
            }
        }
    }

    private void moon(double cx, double cy, double r, boolean front, double t, int acc) {
        double a = t * 0.22 + 0.6;
        if ((Math.sin(a) > 0) != front) return;
        double tilt = -0.30, X = Math.cos(a) * r * 2.9, Y = Math.sin(a) * r * 0.85;
        double mx = cx + X * Math.cos(tilt) - Y * Math.sin(tilt), my = cy + X * Math.sin(tilt) + Y * Math.cos(tilt);
        double mr = Math.max(2, r * 0.13);
        disc(mx, my, mr + 2, acc, 0.10f);
        disc(mx, my, mr, lerp(acc, 0xFFFFFF, 0.85f), 1f);
        disc(mx + mr * 0.35, my + mr * 0.3, mr * 0.7, lerp(acc, 0x000000, 0.25f), 0.45f);
    }

    private void hills(int k, int top, int deep, int acc) {
        int[] hc = {lerp(deep, top, 0.38f), lerp(deep, top, 0.66f), lerp(top, 0x000000, 0.45f)};
        double[] base = {0.80, 0.87, 0.94};
        for (int x = 0; x < SW; x++) {
            int y = (int) (SH * base[k] + Math.sin(x * 0.038 + k * 1.9) * SH * 0.04 + Math.sin(x * 0.015 + k * 4.1) * SH * 0.05);
            for (int yy = Math.max(0, y); yy < SH; yy++) px[yy * SW + x] = hc[k];
            if (k < 2) put(x, y, lerp(hc[k], acc, 0.30f), 1f);
        }
    }

    private void island(double t, int top, int deep, int acc) {
        int bob = (int) Math.round(Math.sin(t * 0.9) * 1.4);
        islandX = (int) (SW * 0.235);
        islandTop = (int) (SH * 0.70) + bob;
        int icx = islandX, ty = islandTop, hw = 27, depth = 24;
        int grass = lerp(rgb(AUR1), deep, 0.30f), grassHi = lerp(grass, 0xFFFFFF, 0.35f);
        int dirt = lerp(deep, 0xFFFFFF, 0.10f), rock = lerp(deep, acc, 0.12f);
        for (int k = 6; k >= 1; k--) disc(icx, ty + 6, hw * (0.8 + k * 0.22), rgb(AUR1), 0.012f);
        for (int dy = 0; dy <= depth; dy++) {
            double f = dy / (double) depth;
            int half = (int) Math.round(hw * (1 - Math.pow(f, 1.25))) + edge[dy % edge.length];
            for (int x = icx - half; x <= icx + half; x++) {
                int c = dy == 0 ? grassHi : dy <= 2 ? grass : dy <= 5 ? dirt : ((x * 3 + dy * 5) % 9 == 0 ? lerp(rock, dirt, 0.55f) : rock);
                if (dy > 5 && (dy % 4 == 0)) c = lerp(c, top, 0.18f);
                if (x > icx + half * 0.35) c = lerp(c, top, 0.30f);
                if (x == icx - half && dy > 2) c = lerp(c, 0xFFFFFF, 0.18f);
                put(x, ty + dy, c, 1f);
            }
        }
        // floating pebbles
        int[][] peb = {{-34, 26, 4}, {33, 31, 3}, {9, 44, 3}, {-16, 49, 2}};
        for (int i = 0; i < peb.length; i++) {
            int b = (int) Math.round(Math.sin(t * 0.9 + 1.3 * (i + 1)) * 1.4);
            rect(icx + peb[i][0], ty + peb[i][1] + b, peb[i][2], peb[i][2], rock, 1f);
            rect(icx + peb[i][0], ty + peb[i][1] + b, peb[i][2], 1, lerp(rock, acc, 0.35f), 1f);
        }
        // crystal
        int cx = icx + hw - 9;
        int cr = rgb(AUR3);
        rect(cx, ty - 5, 2, 5, lerp(cr, deep, 0.25f), 1f);
        rect(cx, ty - 7, 1, 2, lerp(cr, 0xFFFFFF, 0.5f), 1f);
        disc(cx + 1, ty - 3, 6, cr, 0.07f);
        // shadow + character (every skin pixel is 2x2 cells)
        for (int x = icx - 13; x <= icx + 13; x++) { put(x, ty, 0x000000, 0.30f); if (Math.abs(x - icx) < 10) put(x, ty + 1, 0x000000, 0.14f); }
        int ox = icx - 16, oy = ty - 64;
        for (int py = 0; py < 32; py++)
            for (int pxl = 0; pxl < 16; pxl++) {
                int a = skinPx[py * 16 + pxl];
                if ((a >>> 24) < 128) continue;
                int c = lerp(a & 0xFFFFFF, top, 0.10f * pxl / 16f);
                rect(ox + pxl * 2, oy + py * 2, 2, 2, c, 1f);
            }
    }

    private void render(double t, float playHover) {
        int top = rgb(SKY_TOP), bot = rgb(SKY_BOT), acc = rgb(ACCENT), deep = rgb(ACCENT_DARK), sec = rgb(CYAN);
        for (int y = 0; y < SH; y++) {
            float f = Math.min(1f, y / (SH * 0.86f));
            f = Math.round(f * 20) / 20f;
            int c = lerp(top, bot, f * f * (3 - 2 * f));
            for (int x = 0; x < SW; x++) px[y * SW + x] = c;
        }
        int[] ac = {rgb(AUR1), rgb(AUR2), rgb(AUR3)};
        for (int k = 0; k < 3; k++)
            for (int x = 0; x < SW; x++) {
                double c = SH * (0.22 + 0.07 * k) + Math.sin(x * 0.03 + t * 0.22 + k * 2.1) * SH * 0.06 + Math.sin(x * 0.011 - t * 0.1 + k) * SH * 0.05;
                double th = SH * (0.11 + 0.02 * k);
                float pulse = (float) (0.65 + 0.35 * Math.sin(x * 0.06 + t * 0.35 + k * 1.3));
                for (int y = (int) (c - th); y <= (int) (c + th); y++) {
                    double d = Math.abs(y - c) / th;
                    if (d >= 1) continue;
                    float a = (float) ((1 - d) * (1 - d)) * (k == 2 ? 0.12f : 0.21f) * pulse;
                    put(x, y, ac[k], Math.round(a * 40) / 40f);
                }
            }
        for (int i = 0; i < STARS; i++) {
            int x = (int) (sx[i] * SW), y = (int) (sy[i] * SH * 0.80);
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * 1.4 + sp[i]));
            tw = Math.round(tw * 5) / 5f;
            if (i % 9 == 0) {
                put(x, y, 0xFFFFFF, tw);
                float q = tw * 0.55f;
                put(x - 1, y, 0xFFFFFF, q); put(x + 1, y, 0xFFFFFF, q); put(x, y - 1, 0xFFFFFF, q); put(x, y + 1, 0xFFFFFF, q);
                if (i % 27 == 0) { put(x - 2, y, 0xFFFFFF, q * 0.5f); put(x + 2, y, 0xFFFFFF, q * 0.5f); put(x, y - 2, 0xFFFFFF, q * 0.5f); put(x, y + 2, 0xFFFFFF, q * 0.5f); }
            } else put(x, y, i % 5 == 0 ? lerp(0xFFFFFF, acc, 0.4f) : 0xFFFFFF, tw * 0.85f);
        }
        double ph = (t % 9) / 0.9;
        if (ph < 1) {
            double x0 = SW * 0.10 + ph * SW * 0.28, y0 = SH * 0.06 + ph * SH * 0.20;
            for (int i = 0; i < 11; i++) put((int) (x0 - i * 1.3), (int) (y0 - i * 0.6), 0xFFFFFF, (1f - i / 11f) * (1f - (float) ph) * 0.9f);
        }

        // ---- Saturn (the Play button)
        double r = planetR = SH * 0.19, cx = planetX = SW * 0.70, cy = planetY = SH * 0.43;
        float g = 1f + playHover;
        for (int k = 10; k >= 1; k--) disc(cx, cy, r * (1.25 + k * 0.17), acc, 0.012f * g);
        moon(cx, cy, r, false, t, acc);
        ring(cx, cy, r, false, acc, sec, t, playHover);
        int lit = lerp(acc, 0xFFFFFF, 0.55f + 0.18f * playHover), shade = lerp(deep, top, 0.35f);
        for (int y = (int) (cy - r); y <= (int) (cy + r); y++)
            for (int x = (int) (cx - r); x <= (int) (cx + r); x++) {
                double dx = (x - cx) / r, dy = (y - cy) / r, d2 = dx * dx + dy * dy;
                if (d2 > 1) continue;
                double l = -0.55 * dx - 0.5 * dy + 0.65 * Math.sqrt(1 - d2);
                l = Math.round(Math.max(0, Math.min(1, l)) * 5) / 5.0;
                int c = lerp(shade, lit, (float) l);
                if (((int) Math.floor((dy + 1) * 5.5)) % 2 == 0) c = lerp(c, sec, 0.16f);
                put(x, y, c, 1f);
            }
        ring(cx, cy, r, true, acc, sec, t, playHover);
        moon(cx, cy, r, true, t, acc);

        hills(0, top, deep, acc);
        island(t, top, deep, acc);
        hills(1, top, deep, acc);
        hills(2, top, deep, acc);
    }

    // ------------------------------------------------------------------ text helpers
    private static Font light(float size) {
        for (String n : new String[]{"Segoe UI Light", "Inter Light", "Helvetica Neue Light"}) {
            Font f = new Font(n, Font.PLAIN, 12);
            if (f.getFamily().equalsIgnoreCase(n)) return f.deriveFont(Font.PLAIN, size);
        }
        return font(Font.PLAIN, size);
    }
    private static int spacedW(FontMetrics fm, String s, int gap) {
        int w = -gap;
        for (char c : s.toCharArray()) w += fm.charWidth(c) + gap;
        return w;
    }
    private static void spaced(Graphics2D g, String s, int x, int y, int gap, Color c, boolean shadow) {
        FontMetrics fm = g.getFontMetrics();
        for (char ch : s.toCharArray()) {
            if (shadow) { g.setColor(new Color(0, 0, 0, 150)); g.drawString(String.valueOf(ch), x + 2, y + 2); }
            g.setColor(c); g.drawString(String.valueOf(ch), x, y);
            x += fm.charWidth(ch) + gap;
        }
    }
    private static void centered(Graphics2D g, String s, double cx, double y, Color c) {
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(0, 0, 0, 120)); g.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0 + 1), (float) y + 1);
        g.setColor(c); g.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0), (float) y);
    }
    private static String fit(FontMetrics fm, String s, int maxW) {
        if (fm.stringWidth(s) <= maxW) return s;
        while (s.length() > 1 && fm.stringWidth(s + "...") > maxW) s = s.substring(0, s.length() - 1);
        return s + "...";
    }
    private static void tipPill(Graphics2D g2, String s, double cx, double bottomY) {
        g2.setFont(font(Font.PLAIN, 12.5f));
        FontMetrics fm = g2.getFontMetrics();
        int w = fm.stringWidth(s) + 26, h = 28, x = (int) (cx - w / 2.0), y = (int) (bottomY - h);
        g2.translate(x, y);
        glass(g2, w, h, 14, false, true);
        g2.translate(-x, -y);
        g2.setColor(TEXT);
        g2.drawString(s, x + 13, y + (h - fm.getHeight()) / 2 + fm.getAscent());
    }

    // ------------------------------------------------------------------ paint
    @Override protected void paintComponent(Graphics g) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        double t = (now - t0) / 1e9;
        Graphics2D g2 = (Graphics2D) g.create();
        aa(g2);
        int w = getWidth(), h = getHeight();
        if (w < 16 || h < 16) { g2.dispose(); return; }
        hits.clear();

        // ---- scene
        cell = Math.max(3, Math.round(h / 170f));
        SW = w / cell + 1; SH = h / cell + 1;
        if (buf == null || buf.getWidth() != SW || buf.getHeight() != SH) { buf = new BufferedImage(SW, SH, BufferedImage.TYPE_INT_RGB); px = new int[SW * SH]; }
        float playHover = hv("play", dt);
        render(t, playHover);
        buf.setRGB(0, 0, SW, SH, px, 0, SW);
        Object oldInterp = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(buf, 0, 0, SW * cell, SH * cell, null);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);

        // readability shades
        g2.setPaint(new RadialGradientPaint(new Point2D.Double(0, 0), 560f, new float[]{0f, 1f}, new Color[]{new Color(0, 0, 0, 130), new Color(0, 0, 0, 0)}));
        g2.fillRect(0, 0, w, h);
        g2.setPaint(new GradientPaint(0, h - 170, new Color(0, 0, 0, 0), 0, h, new Color(0, 0, 0, 150)));
        g2.fillRect(0, h - 170, w, 170);

        double pcx = planetX * cell + cell / 2.0, pcy = planetY * cell + cell / 2.0, pr = planetR * cell;
        double iscx = islandX * cell + cell, headY = (islandTop - 64) * cell, feetY = islandTop * cell;

        // ---- wordmark (top-left)
        paintLogo(g2, 40, 32, 46);
        g2.setFont(light(42f));
        spaced(g2, "nova", 102, 70, 16, mix(ACCENT, Color.WHITE, 0.82f), true);
        g2.setFont(font(Font.PLAIN, 14f));
        g2.setColor(MUTED); g2.drawString("a quieter way to play", 104, 98);
        g2.setFont(font(Font.PLAIN, 12f));
        FontMetrics vfm = g2.getFontMetrics();
        String vtxt = "minecraft " + mc;
        int vw = vfm.stringWidth(vtxt);
        hits.add(new Hit("version", new Rectangle(98, 104, vw + 34, 22), onVersion));
        float vh = hv("version", dt);
        g2.setColor(mix(DIM, Color.WHITE, vh)); g2.drawString(vtxt, 104, 120);
        int tx0 = 104 + vw + 9;                                    // small caret: this is a picker
        g2.setColor(alpha(mix(ACCENT, Color.WHITE, vh), 150 + (int) (105 * vh)));
        g2.fillPolygon(new int[]{tx0, tx0 + 8, tx0 + 4}, new int[]{114, 114, 119}, 3);
        g2.setColor(DIM);
        g2.drawString("   \u00B7   fabric   \u00B7   " + String.format("%.1f", ramMb / 1024.0) + " gb", 104 + vw + 16, 120);

        // ---- window controls
        for (int i = 0; i < 2; i++) {
            String id = i == 0 ? "min" : "close";
            double cx = w - 72 + i * 32, cy = 34;
            Ellipse2D e = new Ellipse2D.Double(cx - 13, cy - 13, 26, 26);
            hits.add(new Hit(id, e, i == 0 ? onMin : onClose));
            float hh = hv(id, dt);
            g2.setColor(i == 1 ? alpha(mix(Color.WHITE, RED, hh), (int) (22 + 150 * hh)) : new Color(255, 255, 255, (int) (18 + 40 * hh)));
            g2.fill(e);
            g2.setColor(alpha(ACCENT, 70 + (int) (90 * hh)));
            g2.draw(e);
            g2.setColor(mix(MUTED, Color.WHITE, hh));
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (i == 0) g2.draw(new Line2D.Double(cx - 4.5, cy + 0.5, cx + 4.5, cy + 0.5));
            else { g2.draw(new Line2D.Double(cx - 4, cy - 4, cx + 4, cy + 4)); g2.draw(new Line2D.Double(cx - 4, cy + 4, cx + 4, cy - 4)); }
        }

        // ---- key hints (bottom-left)
        String[][] keys = {{"right shift", "module menu"}, {"hold c", "zoom"}, {"f6", "freecam"}};
        g2.setFont(font(Font.PLAIN, 12f));
        for (int i = 0; i < keys.length; i++) {
            int y = h - 96 + i * 20;
            g2.setColor(mix(ACCENT, Color.WHITE, 0.35f)); g2.drawString(keys[i][0], 44, y);
            g2.setColor(DIM); g2.drawString(keys[i][1], 44 + 86, y);
        }

        // ---- news note (bottom-right)
        if (note != null && !note.isBlank()) {
            g2.setFont(font(Font.PLAIN, 12f));
            FontMetrics fm = g2.getFontMetrics();
            String n = fit(fm, note, 350);
            int nw = fm.stringWidth(n), nx = w - 44 - nw, ny = h - 56;
            Rectangle nr = new Rectangle(nx - 16, ny - 16, nw + 20, 24);
            if (noteUrl != null) hits.add(new Hit("note", nr, () -> Platform.open(noteUrl)));
            float nh = noteUrl != null ? hv("note", dt) : 0f;
            g2.setColor(alpha(ACCENT, 200)); g2.fillRect(nx - 12, ny - 8, 4, 4);
            g2.setColor(mix(MUTED, Color.WHITE, nh)); g2.drawString(n, nx, ny);
        }

        // ---- the character: nametag + account link
        g2.setFont(font(Font.BOLD, 15f));
        FontMetrics nfm = g2.getFontMetrics();
        int tagW = nfm.stringWidth(who) + (loggedIn ? 52 : 36), tagH = 32;
        int tagX = (int) (iscx - tagW / 2.0), tagY = (int) (headY - 18 - tagH);
        g2.translate(tagX, tagY);
        glass(g2, tagW, tagH, 16, true, true);
        g2.translate(-tagX, -tagY);
        int tx = tagX + 18;
        if (loggedIn) { g2.setColor(GREEN); g2.fillRect(tagX + 15, tagY + 13, 6, 6); tx += 16; }
        g2.setColor(TEXT);
        g2.drawString(who, tx, tagY + (tagH - nfm.getHeight()) / 2 + nfm.getAscent());
        g2.setColor(alpha(ACCENT, 160));
        g2.fillRect((int) iscx - 3, tagY + tagH + 3, 6, 4); g2.fillRect((int) iscx - 1, tagY + tagH + 7, 2, 2);

        g2.setFont(font(Font.PLAIN, 12.5f));
        FontMetrics lfm = g2.getFontMetrics();
        int lw = lfm.stringWidth(loginText);
        int ly = (int) (feetY + 19 * cell + 28);
        Rectangle lr = new Rectangle((int) iscx - lw / 2 - 14, ly - 18, lw + 28, 28);
        hits.add(new Hit("login", lr, onLogin));
        float lh = hv("login", dt);
        g2.setColor(mix(MUTED, Color.WHITE, lh));
        g2.drawString(loginText, (int) iscx - lw / 2, ly);
        g2.setColor(alpha(mix(ACCENT, Color.WHITE, lh), (int) (40 + 160 * lh)));
        g2.fillRect((int) iscx - lw / 2, ly + 4, lw, 1);

        // ---- Saturn: label + status
        Ellipse2D planetShape = new Ellipse2D.Double(pcx - pr * 1.12, pcy - pr * 1.12, pr * 2.24, pr * 2.24);
        hits.add(new Hit("play", planetShape, working ? null : onPlay));
        String big = working ? (indeterminate ? "\u00B7".repeat(1 + (int) (t * 3) % 3) : Math.round(progress * 100) + "%") : "play";
        float fs = (working ? 34f : 38f) * (1f + 0.07f * playHover);
        g2.setFont(light(fs).deriveFont(Font.BOLD, fs));
        FontMetrics bfm = g2.getFontMetrics();
        int gap = working ? 4 : 10;
        int bw = spacedW(bfm, big, gap);
        g2.setColor(new Color(255, 255, 255, 60));
        spaced(g2, big, (int) (pcx - bw / 2.0) + 1, (int) (pcy + bfm.getAscent() * 0.36) + 2, gap, new Color(255, 255, 255, 70), false);
        spaced(g2, big, (int) (pcx - bw / 2.0), (int) (pcy + bfm.getAscent() * 0.36), gap, alpha(INK, 235), false);

        double sy2 = pcy + pr * 1.78;
        g2.setFont(font(Font.BOLD, 16f));
        centered(g2, fit(g2.getFontMetrics(), status, 440), pcx, sy2, TEXT);
        g2.setFont(font(Font.PLAIN, 13f));
        centered(g2, working ? "launching nova client" : "press the planet to launch", pcx, sy2 + 22, working ? mix(ACCENT, Color.WHITE, 0.5f) : DIM);

        // ---- console sheet
        consoleT += ((consoleOpen ? 1f : 0f) - consoleT) * Math.min(1f, dt * 9f);
        if (consoleT > 0.01f) paintConsole(g2, w, h, dt);

        // ---- dock
        paintDock(g2, w, h, dt);

        // ---- window edge
        g2.setColor(alpha(ACCENT, 60));
        g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 30, 30));
        g2.dispose();
        updateHover();
    }

    private void paintConsole(Graphics2D g2, int w, int h, float dt) {
        float e = 1f - (float) Math.pow(1f - consoleT, 3);
        g2.setColor(new Color(0, 0, 0, (int) (130 * e)));
        g2.fillRect(0, 0, w, h);
        int sx0 = 110, sw = w - 220, sh = h - 200, sy0 = (int) (80 + (1f - e) * h);
        Rectangle sheet = new Rectangle(sx0, sy0, sw, sh);
        hits.add(new Hit("sheet", sheet, null));
        g2.setColor(alpha(mix(SKY_TOP, Color.BLACK, 0.5f), (int) (225 * e)));
        g2.fillRoundRect(sx0, sy0, sw, sh, 26, 26);
        g2.translate(sx0, sy0);
        glass(g2, sw, sh, 26, true, true);
        g2.translate(-sx0, -sy0);
        g2.setFont(light(22f));
        spaced(g2, "console", sx0 + 28, sy0 + 44, 8, mix(ACCENT, Color.WHITE, 0.8f), false);
        // close
        Ellipse2D close = new Ellipse2D.Double(sx0 + sw - 50, sy0 + 20, 28, 28);
        hits.add(new Hit("closeConsole", close, () -> consoleOpen = false));
        float ch = hv("closeConsole", dt);
        g2.setColor(new Color(255, 255, 255, (int) (16 + 40 * ch))); g2.fill(close);
        g2.setColor(mix(MUTED, Color.WHITE, ch));
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double qx = sx0 + sw - 36, qy = sy0 + 34;
        g2.draw(new Line2D.Double(qx - 4, qy - 4, qx + 4, qy + 4)); g2.draw(new Line2D.Double(qx - 4, qy + 4, qx + 4, qy - 4));
        g2.setColor(alpha(ACCENT, 50)); g2.fillRect(sx0 + 28, sy0 + 62, sw - 56, 1);
        // lines
        Shape oldClip = g2.getClip();
        Rectangle body = new Rectangle(sx0 + 28, sy0 + 72, sw - 56, sh - 96);
        g2.clip(body);
        g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        FontMetrics fm = g2.getFontMetrics();
        int lh = 17, vis = body.height / lh;
        int end = Math.max(0, lines.size() - scroll), start = Math.max(0, end - vis);
        g2.setColor(mix(ACCENT, Color.WHITE, 0.62f));
        for (int i = start; i < end; i++) g2.drawString(lines.get(i), body.x, body.y + (i - start + 1) * lh - 4);
        g2.setClip(oldClip);
    }

    private void paintDock(Graphics2D g2, int w, int h, float dt) {
        String[] ids = {"console", "skin", "folder", "mods", "add", "theme", "settings"};
        String[] tips = {"Console", "Preview a skin file", "Game folder", "Mods", "Add to Minecraft Launcher", "Theme \u00B7 " + themeName, "Settings"};
        Glyph[] gl = {Glyph.TERMINAL, Glyph.SHIRT, Glyph.FOLDER, Glyph.BOX, Glyph.PLUS, Glyph.PALETTE, Glyph.GEAR};
        Runnable[] acts = {this::toggleConsole, onSkin, onFolder, onMods, onOfficial, onTheme, onSettings};
        double base = 42, gapPx = 12;
        boolean near = mouse.y > h - 132 && mouse.y < h && mouse.x > 0;
        // first pass: layout with the previous magnification so hit-testing stays stable
        double[] size = new double[7];
        double total = -gapPx;
        for (int i = 0; i < 7; i++) { size[i] = base * (1 + 0.40 * mag[i]); total += size[i] + gapPx; }
        double x0 = (w - total) / 2.0, bottom = h - 26;
        double[] cxs = new double[7];
        double x = x0;
        for (int i = 0; i < 7; i++) { cxs[i] = x + size[i] / 2; x += size[i] + gapPx; }
        for (int i = 0; i < 7; i++) {
            double target = near ? Math.exp(-Math.pow((mouse.x - cxs[i]) / 74.0, 2)) : 0;
            mag[i] += (target - mag[i]) * Math.min(1f, dt * 14f);
        }
        // capsule
        double capW = total + 40, capH = base + 26;
        int cx0 = (int) (x0 - 20), cy0 = (int) (bottom - capH + 11);
        g2.translate(cx0, cy0);
        glass(g2, (int) capW, (int) capH, (int) capH, true, true);
        g2.translate(-cx0, -cy0);
        hits.add(new Hit("dock", new Rectangle2D.Double(cx0, cy0, capW, capH), null));
        String tip = null; double tipX = 0, tipY = 0;
        for (int i = 0; i < 7; i++) {
            double s = size[i], ix = cxs[i] - s / 2, iy = bottom - s - 2;
            Ellipse2D circ = new Ellipse2D.Double(ix, iy, s, s);
            hits.add(new Hit(ids[i], circ, acts[i]));
            float hh = hv(ids[i], dt);
            boolean active = i == 0 && consoleOpen;
            g2.setColor(active ? alpha(ACCENT, 70) : new Color(255, 255, 255, (int) (12 + 34 * hh)));
            g2.fill(circ);
            g2.setColor(alpha(ACCENT, (int) (55 + 150 * hh)));
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(circ);
            gl[i].paint(g2, cxs[i], iy + s / 2, s * 0.46, mix(MUTED, Color.WHITE, Math.max(hh, active ? 1f : 0f)));
            if (active) { g2.setColor(ACCENT); g2.fillRect((int) cxs[i] - 2, (int) (iy + s + 3), 4, 3); }
            if (hh > 0.5f) { tip = tips[i]; tipX = cxs[i]; tipY = iy - 10; }
        }
        if (tip != null) tipPill(g2, tip, tipX, tipY);
    }

    @Override public Dimension getPreferredSize() { return new Dimension(1180, 720); }
}

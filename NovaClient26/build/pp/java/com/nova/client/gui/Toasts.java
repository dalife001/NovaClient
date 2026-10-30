package com.nova.client.gui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.gui.Font;

/** Little notifications that slide in from the right. Safe to call from any thread. */
public final class Toasts {
    private static final class Toast {
        final String title, msg;
        final int color;
        final long born;

        Toast(String title, String msg, int color, long born) { this.title = title; this.msg = msg; this.color = color; this.born = born; }
    }

    private static final List<Toast> LIST = new ArrayList<>();
    private static final long LIFE = 3400, IN = 450, OUT = 500;

    private Toasts() {}

    public static void push(String title, String msg, int color) {
        synchronized (LIST) {
            LIST.add(new Toast(title, msg, color, System.currentTimeMillis()));
            while (LIST.size() > 4) LIST.remove(0);
        }
    }

    public static void render(Gfx c, Font tr, int w, int h) {
        long now = System.currentTimeMillis();
        synchronized (LIST) {
            int y = 30;
            for (Iterator<Toast> it = LIST.iterator(); it.hasNext(); ) {
                Toast t = it.next();
                long age = now - t.born;
                if (age > LIFE) { it.remove(); continue; }
                float in = Anim.easeOutCubic(age / (float) IN);
                float fade = Anim.clamp01(age / 200f);
                float out = (age > LIFE - OUT ? 1f - (age - (LIFE - OUT)) / (float) OUT : 1f) * fade;
                int tw = Math.max(120, Math.max(tr.width(t.title), tr.width(t.msg)) + 28);
                int x = (w - tw) / 2;
                int yy = y - (int) ((1f - Math.min(1f, in)) * 26); // drops in from above
                Ui.panel(c, x, yy, tw, 28, 9, 0xF0100D26, out);
                Ui.rrect(c, x + 6, yy + 7, 3, 14, 1, Ui.a(t.color, out));
                Ui.text(c, tr, t.title, x + 15, yy + 5, Ui.TEXT, out);
                Ui.text(c, tr, t.msg, x + 15, yy + 16, Ui.MUTED, out);
                // a thin line slowly drains: how long the toast stays
                int bw = (int) ((tw - 20) * (1f - age / (float) LIFE));
                if (bw > 1) c.fill(x + 10, yy + 26, x + 10 + bw, yy + 27, Ui.a(t.color, out * 0.55f));
                y += 34;
            }
        }
    }
}

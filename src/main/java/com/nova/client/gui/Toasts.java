package com.nova.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Little notifications that slide in from the right. Safe to call from any thread. */
public final class Toasts {
    private record Toast(String title, String msg, int color, long born) {}

    private static final List<Toast> LIST = new ArrayList<>();
    private static final long LIFE = 3200, IN = 300, OUT = 400;

    private Toasts() {}

    public static void push(String title, String msg, int color) {
        synchronized (LIST) {
            LIST.add(new Toast(title, msg, color, System.currentTimeMillis()));
            while (LIST.size() > 4) LIST.remove(0);
        }
    }

    public static void render(DrawContext c, TextRenderer tr, int w, int h) {
        long now = System.currentTimeMillis();
        synchronized (LIST) {
            int y = 30;
            for (Iterator<Toast> it = LIST.iterator(); it.hasNext(); ) {
                Toast t = it.next();
                long age = now - t.born;
                if (age > LIFE) { it.remove(); continue; }
                float in = Anim.easeOutBack(age / (float) IN);
                float fade = Anim.clamp01(age / 200f);
                float out = (age > LIFE - OUT ? 1f - (age - (LIFE - OUT)) / (float) OUT : 1f) * fade;
                int tw = Math.max(120, Math.max(tr.getWidth(t.title), tr.getWidth(t.msg)) + 28);
                int x = (w - tw) / 2;
                int yy = y - (int) ((1f - Math.min(1f, in)) * 26); // drops in from above
                Ui.round(c, x - 1, yy - 1, tw + 2, 30, Ui.a(0x40FFFFFF, out));
                Ui.round(c, x, yy, tw, 28, Ui.a(0xF00B0B1C, out));
                c.fill(x + 2, yy + 3, x + 4, yy + 25, Ui.a(t.color, out));
                Ui.text(c, tr, t.title, x + 10, yy + 5, Ui.TEXT, out);
                Ui.text(c, tr, t.msg, x + 10, yy + 16, Ui.MUTED, out);
                // life bar
                int bw = (int) ((tw - 4) * (1f - age / (float) LIFE));
                c.fill(x + 2, yy + 26, x + 2 + Math.max(0, bw), yy + 27, Ui.a(t.color, out * 0.8f));
                y += 34;
            }
        }
    }
}

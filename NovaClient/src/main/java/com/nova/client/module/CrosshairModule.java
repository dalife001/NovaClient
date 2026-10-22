package com.nova.client.module;

import com.nova.client.compat.Mc;
import com.nova.client.gui.Gfx;
import com.nova.client.gui.Ui;
import net.minecraft.client.MinecraftClient;

/** A fully custom crosshair. Replaces the vanilla one while enabled. */
public class CrosshairModule extends Module {
    public final NumberSetting size = new NumberSetting("Size", 2, 20, 6, 1);
    public final NumberSetting gap = new NumberSetting("Gap", 0, 12, 3, 1);
    public final NumberSetting thickness = new NumberSetting("Thickness", 1, 4, 1, 1);
    public final NumberSetting color = new NumberSetting("Color", 0, 5, 1, 1);
    public final NumberSetting dot = new NumberSetting("Dot", 0, 1, 0, 1);

    public CrosshairModule() {
        super("Crosshair", "Custom crosshair. Color: 0 white, 1 theme, 2 accent 2, 3 green, 4 red, 5 yellow", false);
        settings.add(size); settings.add(gap); settings.add(thickness); settings.add(color); settings.add(dot);
    }

    @Override public String category() { return "Visual"; }

    private int argb() {
        switch ((int) color.get()) {
            case 0: return 0xFFFFFFFF;
            case 1: return Ui.ACCENT;
            case 2: return Ui.CYAN;
            case 3: return 0xFF34D399;
            case 4: return 0xFFF87171;
            default: return 0xFFFBBF24;
        }
    }

    @Override
    public void onRender(Gfx ctx, MinecraftClient mc) {
        if (mc.options.hudHidden || !Mc.firstPerson(mc)) return;
        int cx = mc.getWindow().getScaledWidth() / 2, cy = mc.getWindow().getScaledHeight() / 2;
        int s = (int) size.get(), g = (int) gap.get(), t = (int) thickness.get();
        int c = argb(), o = 0xB0000000;
        int lo = t / 2, hi = t - lo;
        // dark outline first, then the colour on top, so it reads on any background
        arms(ctx, cx, cy, s, g, lo, hi, 1, o);
        arms(ctx, cx, cy, s, g, lo, hi, 0, c);
        if (dot.get() > 0.5) {
            ctx.fill(cx - lo - 1, cy - lo - 1, cx + hi + 1, cy + hi + 1, o);
            ctx.fill(cx - lo, cy - lo, cx + hi, cy + hi, c);
        }
    }

    private static void arms(Gfx ctx, int cx, int cy, int s, int g, int lo, int hi, int grow, int color) {
        ctx.fill(cx - g - s - grow, cy - lo - grow, cx - g + grow, cy + hi + grow, color);   // left
        ctx.fill(cx + g - grow, cy - lo - grow, cx + g + s + grow, cy + hi + grow, color);   // right
        ctx.fill(cx - lo - grow, cy - g - s - grow, cx + hi + grow, cy - g + grow, color);   // top
        ctx.fill(cx - lo - grow, cy + g - grow, cx + hi + grow, cy + g + s + grow, color);   // bottom
    }
}

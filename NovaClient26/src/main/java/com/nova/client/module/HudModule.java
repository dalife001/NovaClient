package com.nova.client.module;

import com.nova.client.gui.Gfx;
import net.minecraft.client.Minecraft;

/**
 * A module that draws something on screen. Position and scale are editable in the HUD editor.
 * draw() paints at local (0,0); the base class moves/scales it.
 */
public abstract class HudModule extends Module {
    public final NumberSetting scale = new NumberSetting("Scale", 0.6, 2.0, 1.0, 0.05);
    public float offX, offY;
    public boolean right, bottom;
    private final float defX, defY;
    private final boolean defRight, defBottom;

    protected HudModule(String name, String desc, boolean def, float x, float y, boolean right, boolean bottom) {
        super(name, desc, def);
        settings.add(scale);
        this.offX = defX = x; this.offY = defY = y;
        this.right = defRight = right; this.bottom = defBottom = bottom;
    }

    @Override public String category() { return "HUD"; }

    protected abstract int contentW(Minecraft mc);
    protected abstract int contentH(Minecraft mc);
    protected abstract void draw(Gfx ctx, Minecraft mc);

    /** Screen rectangle {x, y, w, h} in scaled GUI pixels. */
    public int[] bounds(Minecraft mc) {
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        float s = (float) scale.get();
        int w = Math.round(contentW(mc) * s), h = Math.round(contentH(mc) * s);
        int x = right ? sw - (int) offX - w : (int) offX;
        int y = bottom ? sh - (int) offY - h : (int) offY;
        x = Math.max(0, Math.min(x, Math.max(0, sw - w)));
        y = Math.max(0, Math.min(y, Math.max(0, sh - h)));
        return new int[]{x, y, w, h};
    }

    public void renderAt(Gfx ctx, Minecraft mc, int x, int y) {
        float s = (float) scale.get();
        ctx.push();
        ctx.translate((float) (x), (float) (y));
        ctx.scale(s, s);
        draw(ctx, mc);
        ctx.pop();
    }

    @Override
    public void onRender(Gfx ctx, Minecraft mc) {
        int[] b = bounds(mc);
        renderAt(ctx, mc, b[0], b[1]);
    }

    /** Places the element at a screen position, remembering which edges it is closest to. */
    public void moveTo(Minecraft mc, int x, int y, int w, int h) {
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        right = x + w / 2 > sw / 2;
        bottom = y + h / 2 > sh / 2;
        offX = right ? sw - x - w : x;
        offY = bottom ? sh - y - h : y;
    }

    public void resetPosition() {
        offX = defX; offY = defY; right = defRight; bottom = defBottom;
        scale.set(1.0);
    }
}

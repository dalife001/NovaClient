package com.nova.client.module;

import com.nova.client.gui.Gfx;
import net.minecraft.client.Minecraft;

/** A one-line "LABEL value" pill. */
public abstract class SimpleHudModule extends HudModule {
    protected SimpleHudModule(String name, String desc, boolean def, float x, float y, boolean right, boolean bottom) {
        super(name, desc, def, x, y, right, bottom);
    }

    protected abstract String label();
    protected abstract String value(Minecraft mc);
    protected int valueColor(Minecraft mc) { return 0xFFFFFFFF; }

    @Override protected int contentW(Minecraft mc) {
        return mc.font.width(label()) + mc.font.width(value(mc)) + 19;
    }
    @Override protected int contentH(Minecraft mc) { return 16; }

    @Override
    protected void draw(Gfx ctx, Minecraft mc) {
        Hud.panel(ctx, 0, 0, contentW(mc), 16);
        ctx.drawTextWithShadow(mc.font, label(), 7, 4, 0xFF9CA3AF);
        ctx.drawTextWithShadow(mc.font, value(mc), 7 + mc.font.width(label()) + 5, 4, valueColor(mc));
    }
}

package com.nova.client.module;

import com.nova.client.gui.Gfx;
import net.minecraft.client.MinecraftClient;

/** A one-line "LABEL value" pill. */
public abstract class SimpleHudModule extends HudModule {
    protected SimpleHudModule(String name, String desc, boolean def, float x, float y, boolean right, boolean bottom) {
        super(name, desc, def, x, y, right, bottom);
    }

    protected abstract String label();
    protected abstract String value(MinecraftClient mc);
    protected int valueColor(MinecraftClient mc) { return 0xFFFFFFFF; }

    @Override protected int contentW(MinecraftClient mc) {
        return mc.textRenderer.getWidth(label()) + mc.textRenderer.getWidth(value(mc)) + 19;
    }
    @Override protected int contentH(MinecraftClient mc) { return 16; }

    @Override
    protected void draw(Gfx ctx, MinecraftClient mc) {
        Hud.panel(ctx, 0, 0, contentW(mc), 16);
        ctx.drawTextWithShadow(mc.textRenderer, label(), 7, 4, 0xFF9CA3AF);
        ctx.drawTextWithShadow(mc.textRenderer, value(mc), 7 + mc.textRenderer.getWidth(label()) + 5, 4, valueColor(mc));
    }
}

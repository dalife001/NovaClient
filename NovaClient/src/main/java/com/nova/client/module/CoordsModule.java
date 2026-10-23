package com.nova.client.module;

import com.nova.client.gui.Gfx;
import net.minecraft.client.Minecraft;

public class CoordsModule extends HudModule {
    public CoordsModule() { super("Coordinates", "Shows your XYZ position", true, 6, 106, false, false); }

    @Override protected int contentW(Minecraft mc) { return 100; }
    @Override protected int contentH(Minecraft mc) { return Hud.TITLE_H + 38; }

    @Override
    protected void draw(Gfx ctx, Minecraft mc) {
        Hud.titled(ctx, mc, 0, 0, 100, contentH(mc), "Coordinates");
        double[] v = mc.player == null ? new double[]{0, 0, 0}
                : new double[]{mc.player.getX(), mc.player.getY(), mc.player.getZ()};
        String[] n = {"X", "Y", "Z"};
        for (int i = 0; i < 3; i++) {
            ctx.drawTextWithShadow(mc.font, n[i], 7, Hud.TITLE_H + 4 + i * 11, 0xFF9CA3AF);
            ctx.drawTextWithShadow(mc.font, String.format("%.1f", v[i]), 20, Hud.TITLE_H + 4 + i * 11, 0xFFFFFFFF);
        }
    }
}

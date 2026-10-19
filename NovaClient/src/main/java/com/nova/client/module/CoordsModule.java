package com.nova.client.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class CoordsModule extends HudModule {
    public CoordsModule() { super("Coordinates", "Shows your XYZ position", true, 6, 106, false, false); }

    @Override protected int contentW(MinecraftClient mc) { return 96; }
    @Override protected int contentH(MinecraftClient mc) { return 42; }

    @Override
    protected void draw(DrawContext ctx, MinecraftClient mc) {
        Hud.panel(ctx, 0, 0, 96, 42);
        double[] v = mc.player == null ? new double[]{0, 0, 0}
                : new double[]{mc.player.getX(), mc.player.getY(), mc.player.getZ()};
        String[] n = {"X", "Y", "Z"};
        for (int i = 0; i < 3; i++) {
            ctx.drawTextWithShadow(mc.textRenderer, n[i], 7, 4 + i * 12, 0xFF9CA3AF);
            ctx.drawTextWithShadow(mc.textRenderer, String.format("%.1f", v[i]), 20, 4 + i * 12, 0xFFFFFFFF);
        }
    }
}

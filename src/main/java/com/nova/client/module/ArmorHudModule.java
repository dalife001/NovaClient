package com.nova.client.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/** Your equipped armor with remaining durability. */
public class ArmorHudModule extends HudModule {
    public ArmorHudModule() { super("Armor HUD", "Shows armor and durability", true, 6, 60, true, false); }

    private int count(MinecraftClient mc) {
        if (mc.player == null) return 0;
        int n = 0;
        for (int i = 0; i < 4; i++) if (!mc.player.getInventory().getArmorStack(i).isEmpty()) n++;
        return n;
    }

    @Override protected int contentW(MinecraftClient mc) { return 62; }
    @Override protected int contentH(MinecraftClient mc) { return Math.max(1, count(mc)) * 20 + 2; }

    @Override
    protected void draw(DrawContext ctx, MinecraftClient mc) {
        int h = contentH(mc);
        Hud.panel(ctx, 0, 0, 62, h);
        if (count(mc) == 0) {
            ctx.drawTextWithShadow(mc.textRenderer, "No armor", 7, 6, 0xFF9CA3AF);
            return;
        }
        int y = 2;
        for (int slot = 3; slot >= 0; slot--) { // helmet -> boots
            ItemStack s = mc.player.getInventory().getArmorStack(slot);
            if (s.isEmpty()) continue;
            ctx.drawItem(s, 5, y + 1);
            ctx.drawStackOverlay(mc.textRenderer, s, 5, y + 1);
            if (s.getMaxDamage() > 0) {
                int left = s.getMaxDamage() - s.getDamage();
                double pct = (double) left / s.getMaxDamage();
                int color = pct > 0.5 ? 0xFF34D399 : pct > 0.2 ? 0xFFFBBF24 : 0xFFF87171;
                ctx.drawTextWithShadow(mc.textRenderer, String.valueOf(left), 26, y + 6, color);
            }
            y += 20;
        }
    }
}

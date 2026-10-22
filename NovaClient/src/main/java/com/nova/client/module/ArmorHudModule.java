package com.nova.client.module;

import com.nova.client.compat.Mc;
import com.nova.client.gui.Gfx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

/** Your equipped armor with remaining durability as a percentage. */
public class ArmorHudModule extends HudModule {
    private static final String TITLE = "Armor Status";

    public ArmorHudModule() { super("Armor HUD", "Shows armor and durability", true, 6, 60, true, false); }

    private int count(MinecraftClient mc) {
        if (mc.player == null) return 0;
        int n = 0;
        for (int i = 0; i < 4; i++) if (!Mc.armor(mc.player, i).isEmpty()) n++;
        return n;
    }

    @Override protected int contentW(MinecraftClient mc) { return Math.max(Hud.titleWidth(mc, TITLE), 74); }
    @Override protected int contentH(MinecraftClient mc) { return Hud.TITLE_H + Math.max(1, count(mc)) * 18 + 3; }

    @Override
    protected void draw(Gfx ctx, MinecraftClient mc) {
        int w = contentW(mc);
        Hud.titled(ctx, mc, 0, 0, w, contentH(mc), TITLE);
        if (count(mc) == 0) {
            ctx.drawTextWithShadow(mc.textRenderer, "No armor", 7, Hud.TITLE_H + 4, 0xFF9CA3AF);
            return;
        }
        int y = Hud.TITLE_H + 2;
        for (int slot = 3; slot >= 0; slot--) { // helmet -> boots
            ItemStack s = Mc.armor(mc.player, slot);
            if (s.isEmpty()) continue;
            ctx.drawItem(s, 4, y);
            if (s.getMaxDamage() > 0) {
                int left = s.getMaxDamage() - s.getDamage();
                double pct = (double) left / s.getMaxDamage();
                int color = pct > 0.5 ? 0xFF34D399 : pct > 0.2 ? 0xFFFBBF24 : 0xFFF87171;
                String t = Math.round(pct * 100) + "%";
                ctx.drawTextWithShadow(mc.textRenderer, t, w - 6 - mc.textRenderer.getWidth(t), y + 5, color);
            }
            y += 18;
        }
    }
}

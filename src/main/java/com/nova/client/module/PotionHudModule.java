package com.nova.client.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Active potion effects with time left. */
public class PotionHudModule extends HudModule {
    public PotionHudModule() { super("Potion Status", "Shows active effects", true, 6, 6, true, false); }

    private List<String> lines(MinecraftClient mc) {
        List<String> out = new ArrayList<>();
        if (mc.player == null) return out;
        Collection<StatusEffectInstance> effects = mc.player.getStatusEffects();
        for (StatusEffectInstance e : effects) {
            String name = e.getEffectType().value().getName().getString();
            String lvl = e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "";
            String time = e.isInfinite() ? "\u221E" : String.format("%d:%02d", e.getDuration() / 1200, (e.getDuration() / 20) % 60);
            out.add(name + lvl + "  " + time);
        }
        return out;
    }

    @Override protected int contentW(MinecraftClient mc) {
        int w = mc.textRenderer.getWidth("No effects");
        for (String l : lines(mc)) w = Math.max(w, mc.textRenderer.getWidth(l));
        return w + 16;
    }
    @Override protected int contentH(MinecraftClient mc) { return Math.max(1, lines(mc).size()) * 12 + 6; }

    @Override
    protected void draw(DrawContext ctx, MinecraftClient mc) {
        Hud.panel(ctx, 0, 0, contentW(mc), contentH(mc));
        List<String> l = lines(mc);
        if (l.isEmpty()) { ctx.drawTextWithShadow(mc.textRenderer, "No effects", 8, 4, 0xFF9CA3AF); return; }
        for (int i = 0; i < l.size(); i++) ctx.drawTextWithShadow(mc.textRenderer, l.get(i), 8, 4 + i * 12, 0xFFFFFFFF);
    }
}

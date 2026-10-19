package com.nova.client.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Active potion effects with time left. */
public class PotionHudModule extends HudModule {
    private static final String TITLE = "Potion Status";

    public PotionHudModule() { super("Potion Status", "Shows active effects", true, 6, 6, true, false); }

    /** Each entry is {name, time}. */
    private List<String[]> lines(MinecraftClient mc) {
        List<String[]> out = new ArrayList<>();
        if (mc.player == null) return out;
        Collection<StatusEffectInstance> effects = mc.player.getStatusEffects();
        for (StatusEffectInstance e : effects) {
            String name = e.getEffectType().value().getName().getString();
            String lvl = e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "";
            String time = e.isInfinite() ? "\u221E" : String.format("%d:%02d", e.getDuration() / 1200, (e.getDuration() / 20) % 60);
            out.add(new String[]{name + lvl, time});
        }
        return out;
    }

    @Override protected int contentW(MinecraftClient mc) {
        int w = Math.max(Hud.titleWidth(mc, TITLE), mc.textRenderer.getWidth("No effects") + 14);
        for (String[] l : lines(mc)) w = Math.max(w, mc.textRenderer.getWidth(l[0]) + mc.textRenderer.getWidth(l[1]) + 26);
        return w;
    }
    @Override protected int contentH(MinecraftClient mc) { return Hud.TITLE_H + Math.max(1, lines(mc).size()) * 11 + 5; }

    @Override
    protected void draw(DrawContext ctx, MinecraftClient mc) {
        int w = contentW(mc);
        Hud.titled(ctx, mc, 0, 0, w, contentH(mc), TITLE);
        List<String[]> l = lines(mc);
        if (l.isEmpty()) { ctx.drawTextWithShadow(mc.textRenderer, "No effects", 7, Hud.TITLE_H + 4, 0xFF9CA3AF); return; }
        for (int i = 0; i < l.size(); i++) {
            int y = Hud.TITLE_H + 4 + i * 11;
            ctx.drawTextWithShadow(mc.textRenderer, l.get(i)[0], 7, y, 0xFFFFFFFF);
            ctx.drawTextWithShadow(mc.textRenderer, l.get(i)[1], w - 7 - mc.textRenderer.getWidth(l.get(i)[1]), y, com.nova.client.gui.Theme.light(com.nova.client.gui.Ui.CYAN));
        }
    }
}

package com.nova.client.module;

import com.nova.client.compat.Mc;
import com.nova.client.gui.Gfx;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;

/** Active potion effects with time left. */
public class PotionHudModule extends HudModule {
    private static final String TITLE = "Potion Status";

    public PotionHudModule() { super("Potion Status", "Shows active effects", true, 6, 6, true, false); }

    /** Each entry is {name, time}. */
    private List<String[]> lines(Minecraft mc) {
        List<String[]> out = new ArrayList<>();
        if (mc.player == null) return out;
        Collection<MobEffectInstance> effects = mc.player.getActiveEffects();
        for (MobEffectInstance e : effects) {
            String name = Mc.effectName(e);
            String lvl = e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "";
            String time = e.getDuration() < 0 ? "\u221E" : String.format("%d:%02d", e.getDuration() / 1200, (e.getDuration() / 20) % 60);
            out.add(new String[]{name + lvl, time});
        }
        return out;
    }

    @Override protected int contentW(Minecraft mc) {
        int w = Math.max(Hud.titleWidth(mc, TITLE), mc.font.width("No effects") + 14);
        for (String[] l : lines(mc)) w = Math.max(w, mc.font.width(l[0]) + mc.font.width(l[1]) + 26);
        return w;
    }
    @Override protected int contentH(Minecraft mc) { return Hud.TITLE_H + Math.max(1, lines(mc).size()) * 11 + 5; }

    @Override
    protected void draw(Gfx ctx, Minecraft mc) {
        int w = contentW(mc);
        Hud.titled(ctx, mc, 0, 0, w, contentH(mc), TITLE);
        List<String[]> l = lines(mc);
        if (l.isEmpty()) { ctx.drawTextWithShadow(mc.font, "No effects", 7, Hud.TITLE_H + 4, 0xFF9CA3AF); return; }
        for (int i = 0; i < l.size(); i++) {
            int y = Hud.TITLE_H + 4 + i * 11;
            ctx.drawTextWithShadow(mc.font, l.get(i)[0], 7, y, 0xFFFFFFFF);
            ctx.drawTextWithShadow(mc.font, l.get(i)[1], w - 7 - mc.font.width(l.get(i)[1]), y, com.nova.client.gui.Theme.light(com.nova.client.gui.Ui.CYAN));
        }
    }
}

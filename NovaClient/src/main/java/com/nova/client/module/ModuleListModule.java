package com.nova.client.module;

import com.nova.client.NovaClient;
import com.nova.client.gui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** Lists the features you have switched on, right-aligned with an accent bar. */
public class ModuleListModule extends HudModule {
    public ModuleListModule() { super("Module List", "Shows which features are on", false, 6, 160, true, false); }

    private List<String> names() {
        List<String> out = new ArrayList<>();
        if (NovaClient.modules == null) return out;
        for (Module m : NovaClient.modules.all()) {
            if (m == this || m instanceof HudModule || m instanceof ThemeModule || !m.isEnabled()) continue;
            out.add(m.name);
        }
        return out;
    }

    @Override protected int contentW(MinecraftClient mc) {
        int w = 40;
        for (String n : names()) w = Math.max(w, mc.textRenderer.getWidth(n) + 14);
        return w;
    }
    @Override protected int contentH(MinecraftClient mc) { return Math.max(1, names().size()) * 12; }

    @Override
    protected void draw(DrawContext ctx, MinecraftClient mc) {
        int w = contentW(mc);
        List<String> n = names();
        if (n.isEmpty()) { ctx.drawTextWithShadow(mc.textRenderer, "nothing on", w - 6 - mc.textRenderer.getWidth("nothing on"), 2, 0xFF6B7280); return; }
        for (int i = 0; i < n.size(); i++) {
            int tw = mc.textRenderer.getWidth(n.get(i));
            int y = i * 12;
            Ui.rrect(ctx, w - tw - 12, y, tw + 12, 11, 3, 0x940C0A1E);
            ctx.fillGradient(w - 2, y + 2, w, y + 9, Ui.ACCENT, Ui.CYAN);
            ctx.drawTextWithShadow(mc.textRenderer, n.get(i), w - tw - 7, y + 2, 0xFFFFFFFF);
        }
    }
}

package com.nova.client.module;

import com.nova.client.gui.Gfx;
import com.nova.client.NovaClient;
import com.nova.client.gui.Ui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

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

    @Override protected int contentW(Minecraft mc) {
        int w = 40;
        for (String n : names()) w = Math.max(w, mc.font.width(n) + 14);
        return w;
    }
    @Override protected int contentH(Minecraft mc) { return Math.max(1, names().size()) * 12; }

    @Override
    protected void draw(Gfx ctx, Minecraft mc) {
        int w = contentW(mc);
        List<String> n = names();
        if (n.isEmpty()) { ctx.drawTextWithShadow(mc.font, "nothing on", w - 6 - mc.font.width("nothing on"), 2, 0xFF6B7280); return; }
        for (int i = 0; i < n.size(); i++) {
            int tw = mc.font.width(n.get(i));
            int y = i * 12;
            Ui.rrect(ctx, w - tw - 12, y, tw + 12, 11, 3, 0x940C0A1E);
            ctx.fillGradient(w - 2, y + 2, w, y + 9, Ui.ACCENT, Ui.CYAN);
            ctx.drawTextWithShadow(mc.font, n.get(i), w - tw - 7, y + 2, 0xFFFFFFFF);
        }
    }
}

package com.nova.client.module;

import com.nova.client.gui.Theme;

/** Picks the colour palette of the whole client (also used by the launcher via -Dnova.theme). */
public class ThemeModule extends Module {
    public final NumberSetting palette = new NumberSetting("Palette", 0, Theme.NAMES.length - 1, 0, 1);

    public ThemeModule() {
        super("Theme", "Palette: 0 Twilight, 1 Lagoon, 2 Dusk, 3 Meadow, 4 Blossom", true);
        settings.add(palette);
        try { palette.set(Integer.parseInt(System.getProperty("nova.theme", "0"))); } catch (NumberFormatException ignored) {}
    }

    @Override public String category() { return "Visual"; }
    @Override public boolean persistent() { return false; }

    @Override public void onTick(net.minecraft.client.Minecraft mc) { Theme.apply((int) palette.get()); }
    @Override protected void onDisable() { Theme.apply(0); }
}

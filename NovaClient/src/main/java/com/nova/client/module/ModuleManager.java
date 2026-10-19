package com.nova.client.module;

import com.nova.client.config.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    public final FreecamModule freecam = new FreecamModule();
    public final ZoomModule zoom = new ZoomModule();
    public final FullbrightModule fullbright = new FullbrightModule();
    public final LowFireModule lowFire = new LowFireModule();
    public final CrosshairModule crosshair = new CrosshairModule();
    public final ThemeModule theme = new ThemeModule();

    public ModuleManager() {
        modules.add(new FpsModule());
        modules.add(new PingModule());
        modules.add(new CpsModule());
        modules.add(new CoordsModule());
        modules.add(new DirectionModule());
        modules.add(new ClockModule());
        modules.add(new KeystrokesModule());
        modules.add(new ArmorHudModule());
        modules.add(new PotionHudModule());
        modules.add(new SpeedModule());
        modules.add(new MemoryModule());
        modules.add(new ComboModule());
        modules.add(new ModuleListModule());
        modules.add(new ToggleSprintModule());
        modules.add(zoom);
        modules.add(freecam);
        modules.add(fullbright);
        modules.add(crosshair);
        modules.add(lowFire);
        modules.add(theme);

        Config cfg = Config.get();
        for (Module m : modules) {
            Boolean saved = cfg.modules.get(m.name);
            if (saved != null && m.persistent()) m.restore(saved);
            for (NumberSetting s : m.settings) {
                Double v = cfg.settings.get(m.name + "/" + s.name);
                if (v != null) s.set(v);
            }
            if (m instanceof HudModule h) {
                List<Double> p = cfg.hud.get(m.name);
                if (p != null && p.size() == 4) {
                    h.offX = p.get(0).floatValue();
                    h.offY = p.get(1).floatValue();
                    h.right = p.get(2) > 0.5;
                    h.bottom = p.get(3) > 0.5;
                }
            }
        }
        com.nova.client.gui.Theme.apply((int) theme.palette.get());
    }

    public List<Module> all() { return Collections.unmodifiableList(modules); }

    public int enabledCount() {
        int n = 0;
        for (Module m : modules) if (m.isEnabled()) n++;
        return n;
    }

    public void saveStates() {
        Config cfg = Config.get();
        for (Module m : modules) {
            if (m.persistent()) cfg.modules.put(m.name, m.isEnabled());
            for (NumberSetting s : m.settings) cfg.settings.put(m.name + "/" + s.name, s.get());
            if (m instanceof HudModule h) {
                cfg.hud.put(m.name, List.of((double) h.offX, (double) h.offY, h.right ? 1.0 : 0.0, h.bottom ? 1.0 : 0.0));
            }
        }
        Config.save();
    }

    public void onTick(MinecraftClient mc) {
        for (Module m : modules) if (m.isEnabled()) m.onTick(mc);
    }

    public void onRender(DrawContext ctx, MinecraftClient mc) {
        for (Module m : modules) if (m.isEnabled()) m.onRender(ctx, mc);
    }
}

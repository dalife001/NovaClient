package com.nova.client.module;

import com.nova.client.mixin.SimpleOptionAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

/** Maximum brightness everywhere: no more dark caves. Puts your old brightness back when turned off. */
public class FullbrightModule extends Module {
    private static final double LEVEL = 15.0;
    private Double saved;

    public FullbrightModule() { super("Fullbright", "See clearly in caves and at night", false); }

    @Override public String category() { return "Visual"; }

    private static double gamma(Minecraft mc) {
        return mc.options.gamma().get();
    }

    @SuppressWarnings("unchecked")
    private void set(Minecraft mc, double v) {
        OptionInstance<Double> gamma = mc.options.gamma();
        ((SimpleOptionAccessor<Double>) (Object) gamma).novaSetRawValue(v);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.options == null) return;
        double now = gamma(mc);
        if (saved == null && now <= 1.0) saved = now;
        if (now < LEVEL) set(mc, LEVEL);
    }

    @Override protected void onDisable() { release(); }

    /** Restores the brightness the player had before. Also called when the game closes. */
    public void release() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        if (gamma(mc) > 1.0) set(mc, saved != null ? saved : 1.0);
        saved = null;
    }
}

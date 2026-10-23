package com.nova.client.module;

import net.minecraft.client.MinecraftClient;
//#if MC >= 1900
import com.nova.client.mixin.SimpleOptionAccessor;
import net.minecraft.client.option.SimpleOption;
//#endif

/** Maximum brightness everywhere: no more dark caves. Puts your old brightness back when turned off. */
public class FullbrightModule extends Module {
    private static final double LEVEL = 15.0;
    private Double saved;

    public FullbrightModule() { super("Fullbright", "See clearly in caves and at night", false); }

    @Override public String category() { return "Visual"; }

    private static double gamma(MinecraftClient mc) {
        //#if MC >= 1900
        return mc.options.getGamma().getValue();
        //#else
        return mc.options.gamma;
        //#endif
    }

    @SuppressWarnings("unchecked")
    private void set(MinecraftClient mc, double v) {
        //#if MC >= 1900
        SimpleOption<Double> gamma = mc.options.getGamma();
        ((SimpleOptionAccessor<Double>) (Object) gamma).novaSetRawValue(v);
        //#else
        mc.options.gamma = v;
        //#endif
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.options == null) return;
        double now = gamma(mc);
        if (saved == null && now <= 1.0) saved = now;
        if (now < LEVEL) set(mc, LEVEL);
    }

    @Override protected void onDisable() { release(); }

    /** Restores the brightness the player had before. Also called when the game closes. */
    public void release() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        if (gamma(mc) > 1.0) set(mc, saved != null ? saved : 1.0);
        saved = null;
    }
}

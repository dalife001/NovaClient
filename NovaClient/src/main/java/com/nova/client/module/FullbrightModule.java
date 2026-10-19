package com.nova.client.module;

import com.nova.client.mixin.SimpleOptionAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;

/** Maximum brightness everywhere: no more dark caves. Puts your old brightness back when turned off. */
public class FullbrightModule extends Module {
    private static final double LEVEL = 15.0;
    private Double saved;

    public FullbrightModule() { super("Fullbright", "See clearly in caves and at night", false); }

    @Override public String category() { return "Visual"; }

    @SuppressWarnings("unchecked")
    private void set(MinecraftClient mc, double v) {
        SimpleOption<Double> gamma = mc.options.getGamma();
        ((SimpleOptionAccessor<Double>) (Object) gamma).novaSetRawValue(v);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.options == null) return;
        double now = mc.options.getGamma().getValue();
        if (saved == null && now <= 1.0) saved = now;
        if (now < LEVEL) set(mc, LEVEL);
    }

    @Override protected void onDisable() { release(); }

    /** Restores the brightness the player had before. Also called when the game closes. */
    public void release() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        if (mc.options.getGamma().getValue() > 1.0) set(mc, saved != null ? saved : 1.0);
        saved = null;
    }
}

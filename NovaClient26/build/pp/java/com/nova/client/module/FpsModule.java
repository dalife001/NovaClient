package com.nova.client.module;

import com.nova.client.compat.Mc;
import net.minecraft.client.Minecraft;

public class FpsModule extends SimpleHudModule {
    public FpsModule() { super("FPS", "Shows your frame rate", true, 6, 6, false, false); }
    @Override protected String label() { return "FPS"; }
    @Override protected String value(Minecraft mc) { return String.valueOf(Mc.fps(mc)); }
    @Override protected int valueColor(Minecraft mc) {
        int f = Mc.fps(mc);
        return f >= 60 ? 0xFF34D399 : f >= 30 ? 0xFFFBBF24 : 0xFFF87171;
    }
}

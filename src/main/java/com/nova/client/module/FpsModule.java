package com.nova.client.module;

import net.minecraft.client.MinecraftClient;

public class FpsModule extends SimpleHudModule {
    public FpsModule() { super("FPS", "Shows your frame rate", true, 6, 6, false, false); }
    @Override protected String label() { return "FPS"; }
    @Override protected String value(MinecraftClient mc) { return String.valueOf(mc.getCurrentFps()); }
    @Override protected int valueColor(MinecraftClient mc) {
        int f = mc.getCurrentFps();
        return f >= 60 ? 0xFF34D399 : f >= 30 ? 0xFFFBBF24 : 0xFFF87171;
    }
}

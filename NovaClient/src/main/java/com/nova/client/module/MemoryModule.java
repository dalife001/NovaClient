package com.nova.client.module;

import net.minecraft.client.MinecraftClient;

/** Game memory use. */
public class MemoryModule extends SimpleHudModule {
    public MemoryModule() { super("Memory", "RAM used by the game", false, 6, 190, false, false); }
    @Override protected String label() { return "RAM"; }
    @Override protected String value(MinecraftClient mc) {
        Runtime r = Runtime.getRuntime();
        long used = (r.totalMemory() - r.freeMemory()) >> 20, max = r.maxMemory() >> 20;
        return used + " / " + max + " MB";
    }
    @Override protected int valueColor(MinecraftClient mc) {
        Runtime r = Runtime.getRuntime();
        double f = (double) (r.totalMemory() - r.freeMemory()) / r.maxMemory();
        return f < 0.7 ? 0xFF34D399 : f < 0.9 ? 0xFFFBBF24 : 0xFFF87171;
    }
}

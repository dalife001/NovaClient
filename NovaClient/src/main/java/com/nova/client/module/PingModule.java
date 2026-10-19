package com.nova.client.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

public class PingModule extends SimpleHudModule {
    public PingModule() { super("Ping", "Shows your latency to the server", true, 6, 26, false, false); }

    private int ping(MinecraftClient mc) {
        if (mc.getNetworkHandler() == null || mc.player == null) return -1;
        PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return e == null ? -1 : e.getLatency();
    }

    @Override protected String label() { return "PING"; }
    @Override protected String value(MinecraftClient mc) {
        int p = ping(mc);
        return p < 0 ? "--" : p + " ms";
    }
    @Override protected int valueColor(MinecraftClient mc) {
        int p = ping(mc);
        return p < 0 ? 0xFF9CA3AF : p < 80 ? 0xFF34D399 : p < 160 ? 0xFFFBBF24 : 0xFFF87171;
    }
}

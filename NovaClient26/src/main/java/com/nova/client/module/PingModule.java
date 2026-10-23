package com.nova.client.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

public class PingModule extends SimpleHudModule {
    public PingModule() { super("Ping", "Shows your latency to the server", true, 6, 26, false, false); }

    private int ping(Minecraft mc) {
        if (mc.getConnection() == null || mc.player == null) return -1;
        PlayerInfo e = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return e == null ? -1 : e.getLatency();
    }

    @Override protected String label() { return "PING"; }
    @Override protected String value(Minecraft mc) {
        int p = ping(mc);
        return p < 0 ? "--" : p + " ms";
    }
    @Override protected int valueColor(Minecraft mc) {
        int p = ping(mc);
        return p < 0 ? 0xFF9CA3AF : p < 80 ? 0xFF34D399 : p < 160 ? 0xFFFBBF24 : 0xFFF87171;
    }
}

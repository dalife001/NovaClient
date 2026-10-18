package com.nova.client.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

public class DirectionModule extends SimpleHudModule {
    private static final String[] DIRS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    public DirectionModule() { super("Direction", "Compass direction you are facing", true, 6, 86, false, false); }
    @Override protected String label() { return "FACING"; }
    @Override protected String value(MinecraftClient mc) {
        if (mc.player == null) return "-";
        float yaw = ((MathHelper.wrapDegrees(mc.player.getYaw()) % 360) + 360) % 360;
        int idx = Math.round(yaw / 45f) % 8;
        return DIRS[idx] + " (" + Math.round(yaw) + "\u00B0)";
    }
}

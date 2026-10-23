package com.nova.client.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/** Horizontal speed in blocks per second. */
public class SpeedModule extends SimpleHudModule {
    public SpeedModule() { super("Speed", "Your speed in blocks per second", false, 6, 170, false, false); }
    @Override protected String label() { return "SPEED"; }
    @Override protected String value(MinecraftClient mc) {
        if (mc.player == null) return "0.0 b/s";
        Vec3d v = mc.player.getVelocity();
        return String.format("%.1f b/s", Math.sqrt(v.x * v.x + v.z * v.z) * 20.0);
    }
    @Override protected int valueColor(MinecraftClient mc) { return com.nova.client.gui.Theme.light(com.nova.client.gui.Ui.CYAN); }
}

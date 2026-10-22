package com.nova.client.module;

import com.nova.client.compat.Mc;
import net.minecraft.client.MinecraftClient;

public class ToggleSprintModule extends Module {
    public ToggleSprintModule() { super("Auto Sprint", "Sprints whenever you walk forward", false); }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null) return;
        if (Mc.forward(mc.options).isPressed()
                && !mc.player.isSneaking()
                && !mc.player.horizontalCollision
                && mc.player.getHungerManager().getFoodLevel() > 6) {
            mc.player.setSprinting(true);
        }
    }
}

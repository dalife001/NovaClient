package com.nova.client.module;

import com.nova.client.compat.Mc;
import net.minecraft.client.Minecraft;

public class ToggleSprintModule extends Module {
    public ToggleSprintModule() { super("Auto Sprint", "Sprints whenever you walk forward", false); }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) return;
        if (Mc.forward(mc.options).isDown()
                && !mc.player.isShiftKeyDown()
                && !mc.player.horizontalCollision
                && mc.player.getFoodData().getFoodLevel() > 6) {
            mc.player.setSprinting(true);
        }
    }
}

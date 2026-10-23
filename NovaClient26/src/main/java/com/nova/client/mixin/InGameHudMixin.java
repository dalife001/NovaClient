package com.nova.client.mixin;

import com.nova.client.NovaClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class InGameHudMixin {
    /** Hides the vanilla crosshair while the Nova crosshair is on. The method's arguments changed in 1.20, 1.20.5 and 1.21. */
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void nova$crosshair(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (NovaClient.modules != null && NovaClient.modules.crosshair.isEnabled()) ci.cancel();
    }
}

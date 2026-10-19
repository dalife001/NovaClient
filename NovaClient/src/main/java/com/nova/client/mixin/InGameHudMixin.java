package com.nova.client.mixin;

import com.nova.client.NovaClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    /** Hides the vanilla crosshair while the Nova crosshair is on. */
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void nova$crosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (NovaClient.modules != null && NovaClient.modules.crosshair.isEnabled()) ci.cancel();
    }
}

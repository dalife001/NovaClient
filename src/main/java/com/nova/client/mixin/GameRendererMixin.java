package com.nova.client.mixin;

import com.nova.client.NovaClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** Narrows the field of view while the zoom key is held. */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void nova$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (NovaClient.modules != null && NovaClient.modules.zoom.isZooming()) {
            cir.setReturnValue(cir.getReturnValue() * NovaClient.modules.zoom.factor());
        }
    }
}

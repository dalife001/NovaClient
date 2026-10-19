package com.nova.client.mixin;

import com.nova.client.NovaClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
    /** Moves the first-person fire overlay down so it stops covering the screen. */
    @Inject(method = "renderFireOverlay", at = @At("HEAD"))
    private static void nova$lowFire(MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (NovaClient.modules != null && NovaClient.modules.lowFire.isEnabled()) {
            matrices.translate(0.0f, -0.3f * (float) NovaClient.modules.lowFire.drop.get(), 0.0f);
        }
    }
}

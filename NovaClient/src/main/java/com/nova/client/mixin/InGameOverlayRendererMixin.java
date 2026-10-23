package com.nova.client.mixin;

import com.nova.client.NovaClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#if MC >= 2104
import net.minecraft.client.render.VertexConsumerProvider;
//#else
import net.minecraft.client.MinecraftClient;
//#endif
//#if MC >= 2109
import net.minecraft.client.texture.Sprite;
//#endif

@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
    /** Moves the first-person fire overlay down so it stops covering the screen. The method's arguments changed in 1.21.4 and 1.21.9. */
    //#if MC >= 2109
    @Inject(method = "renderFireOverlay", at = @At("HEAD"))
    private static void nova$lowFire(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite, CallbackInfo ci) {
    //#elseif MC >= 2104
    @Inject(method = "renderFireOverlay", at = @At("HEAD"))
    private static void nova$lowFire(MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
    //#else
    @Inject(method = "renderFireOverlay", at = @At("HEAD"))
    private static void nova$lowFire(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
    //#endif
        if (NovaClient.modules != null && NovaClient.modules.lowFire.isEnabled()) {
            matrices.translate(0.0f, -0.3f * (float) NovaClient.modules.lowFire.drop.get(), 0.0f);
        }
    }
}

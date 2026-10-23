package com.nova.client.mixin;

//#if MC < 26200
import com.mojang.blaze3d.vertex.PoseStack;
import com.nova.client.NovaClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class InGameOverlayRendererMixin {
    /** Moves the first-person fire overlay down so it stops covering the screen. */
    @Inject(method = "renderFire", at = @At("HEAD"))
    private static void nova$lowFire(PoseStack matrices, MultiBufferSource vertexConsumers, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (NovaClient.modules != null && NovaClient.modules.lowFire.isEnabled()) {
            matrices.translate(0.0f, -0.3f * (float) NovaClient.modules.lowFire.drop.get(), 0.0f);
        }
    }
}
//#else
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;

/** The fire overlay was rebuilt in 26.2: Low Fire is not ported yet, so this mixin does nothing there. */
@Mixin(ScreenEffectRenderer.class)
public abstract class InGameOverlayRendererMixin {
}
//#endif

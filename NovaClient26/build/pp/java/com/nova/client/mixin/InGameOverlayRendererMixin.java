package com.nova.client.mixin;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;

/** The fire overlay was rebuilt in 26.2: Low Fire is not ported yet, so this mixin does nothing there. */
@Mixin(ScreenEffectRenderer.class)
public abstract class InGameOverlayRendererMixin {
}

package com.nova.client.mixin;

import com.nova.client.NovaClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    /** While freecam is on, mouse movement turns the camera instead of the player. */
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void nova$look(double dx, double dy, CallbackInfo ci) {
        if (NovaClient.modules == null || !NovaClient.modules.freecam.isEnabled()) return;
        if ((Object) this == MinecraftClient.getInstance().player) {
            NovaClient.modules.freecam.turn(dx, dy);
            ci.cancel();
        }
    }
}

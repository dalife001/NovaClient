package com.nova.client.mixin;

import com.nova.client.NovaClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    /** Your real player stands still while the freecam flies. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void nova$freezePlayer(CallbackInfo ci) {
        if (NovaClient.modules != null && NovaClient.modules.freecam.isEnabled()) {
            this.playerInput = PlayerInput.DEFAULT;
            this.movementForward = 0;
            this.movementSideways = 0;
        }
    }
}

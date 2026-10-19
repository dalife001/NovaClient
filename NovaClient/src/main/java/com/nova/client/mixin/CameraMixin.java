package com.nova.client.mixin;

import com.nova.client.NovaClient;
import com.nova.client.module.FreecamModule;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow private boolean thirdPerson;

    @Inject(method = "update", at = @At("TAIL"))
    private void nova$freecam(BlockView area, Entity focused, boolean tp, boolean inverse,
                              float tickDelta, CallbackInfo ci) {
        if (NovaClient.modules == null) return;
        FreecamModule fc = NovaClient.modules.freecam;
        if (!fc.isEnabled()) return;
        Vec3d p = fc.cameraPos(tickDelta);
        setRotation(fc.yaw, fc.pitch);
        setPos(p.x, p.y, p.z);
        this.thirdPerson = true; // makes your own player model visible
    }
}

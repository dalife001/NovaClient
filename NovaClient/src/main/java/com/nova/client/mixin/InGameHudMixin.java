package com.nova.client.mixin;

import com.nova.client.NovaClient;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#if MC >= 2000
import net.minecraft.client.gui.DrawContext;
//#else
import net.minecraft.client.util.math.MatrixStack;
//#endif
//#if MC >= 2100
import net.minecraft.client.render.RenderTickCounter;
//#endif

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    /** Hides the vanilla crosshair while the Nova crosshair is on. The method's arguments changed in 1.20, 1.20.5 and 1.21. */
    //#if MC >= 2100
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void nova$crosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
    //#elseif MC >= 2005
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void nova$crosshair(DrawContext context, float tickDelta, CallbackInfo ci) {
    //#elseif MC >= 2000
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void nova$crosshair(DrawContext context, CallbackInfo ci) {
    //#else
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void nova$crosshair(MatrixStack matrices, CallbackInfo ci) {
    //#endif
        if (NovaClient.modules != null && NovaClient.modules.crosshair.isEnabled()) ci.cancel();
    }
}

package com.nova.client.module;

import com.nova.client.NovaClient;
import com.nova.client.compat.Mc;
import net.minecraft.client.Minecraft;

/** Hold the zoom key (C by default) to zoom in with smooth camera. */
public class ZoomModule extends Module {
    private boolean wasZooming;
    private boolean previousSmooth;

    public final NumberSetting level = new NumberSetting("Zoom level", 1.5, 10, 4, 0.5);

    public ZoomModule() {
        super("Zoom", "Hold C to zoom in", true);
        settings.add(level);
    }

    @Override public String category() { return "Camera"; }

    public boolean isZooming() {
        Minecraft mc = Minecraft.getInstance();
        return isEnabled() && NovaClient.zoomKey != null && NovaClient.zoomKey.isDown() && Mc.screen(mc) == null;
    }

    /** Multiplier applied to the field of view. */
    public double factor() { return 1.0 / level.get(); }

    @Override
    public void onTick(Minecraft mc) {
        boolean z = isZooming();
        if (z && !wasZooming) {
            previousSmooth = mc.options.smoothCamera;
            mc.options.smoothCamera = true;
        } else if (!z && wasZooming) {
            mc.options.smoothCamera = previousSmooth;
        }
        wasZooming = z;
    }

    @Override
    protected void onDisable() {
        if (wasZooming) {
            Minecraft.getInstance().options.smoothCamera = previousSmooth;
            wasZooming = false;
        }
    }
}

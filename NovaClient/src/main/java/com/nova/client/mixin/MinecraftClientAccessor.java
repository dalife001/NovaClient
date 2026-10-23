package com.nova.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
//#if MC >= 2002
import net.minecraft.client.session.Session;
//#else
import net.minecraft.client.util.Session;
//#endif

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Mutable
    @Accessor("session")
    void novaSetSession(Session session);

    //#if MC < 1903
    /** Before 1.19.3 there is no getCurrentFps(); the debug text starts with the frame rate ("123 fps ..."). */
    @Accessor("fpsDebugString")
    String novaFpsString();
    //#endif
}

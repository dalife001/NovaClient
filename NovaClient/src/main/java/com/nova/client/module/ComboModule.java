package com.nova.client.module;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;

/** Counts your hits in a row. Resets when you get hit or stop attacking for 3 seconds. */
public class ComboModule extends SimpleHudModule {
    private static final long TIMEOUT_MS = 3000;
    private int combo;
    private long lastHit;

    public ComboModule() {
        super("Combo", "Hits in a row", false, 6, 210, false, false);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            //#if MC >= 2109
            boolean clientSide = world.isClient();
            //#else
            boolean clientSide = world.isClient;
            //#endif
            if (clientSide && entity instanceof LivingEntity) {
                long now = System.currentTimeMillis();
                if (now - lastHit > TIMEOUT_MS) combo = 0;
                combo++;
                lastHit = now;
            }
            return ActionResult.PASS;
        });
    }

    private int current() { return System.currentTimeMillis() - lastHit > TIMEOUT_MS ? 0 : combo; }

    @Override public void onTick(MinecraftClient mc) {
        if (mc.player != null && mc.player.hurtTime > 0) combo = 0;
    }

    @Override protected String label() { return "COMBO"; }
    @Override protected String value(MinecraftClient mc) { return current() + (current() == 1 ? " hit" : " hits"); }
    @Override protected int valueColor(MinecraftClient mc) { int c = current(); return c >= 5 ? 0xFFF87171 : c >= 3 ? 0xFFFBBF24 : 0xFFFFFFFF; }
}

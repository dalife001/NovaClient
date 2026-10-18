package com.nova.client.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Detaches the camera from your player so you can fly around and look.
 * Purely client-side: your real position never changes.
 * Controls: WASD to fly, Space = up, Sneak = down, Sprint = fast.
 */
public class FreecamModule extends Module {
    private Vec3d pos = Vec3d.ZERO;
    private Vec3d prev = Vec3d.ZERO;
    public float yaw, pitch;

    public final NumberSetting speed = new NumberSetting("Fly speed", 0.1, 2.0, 0.45, 0.05);

    public FreecamModule() {
        super("Freecam", "Detach the camera and fly around (F6)", false);
        settings.add(speed);
    }

    @Override public String category() { return "Camera"; }

    @Override public boolean persistent() { return false; }

    @Override
    protected void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) { setEnabled(false); return; }
        pos = prev = mc.gameRenderer.getCamera().getPos();
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
    }

    public Vec3d cameraPos(float tickDelta) { return prev.lerp(pos, tickDelta); }

    public void turn(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch = MathHelper.clamp(pitch + (float) dy * 0.15f, -90f, 90f);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null) { setEnabled(false); return; }
        if (mc.currentScreen != null) { prev = pos; return; }

        GameOptions o = mc.options;
        double yr = Math.toRadians(yaw);
        Vec3d look = Vec3d.fromPolar(pitch, yaw);
        Vec3d right = new Vec3d(-Math.cos(yr), 0, -Math.sin(yr));

        Vec3d dir = Vec3d.ZERO;
        if (o.forwardKey.isPressed()) dir = dir.add(look);
        if (o.backKey.isPressed())    dir = dir.subtract(look);
        if (o.rightKey.isPressed())   dir = dir.add(right);
        if (o.leftKey.isPressed())    dir = dir.subtract(right);
        if (o.jumpKey.isPressed())    dir = dir.add(0, 1, 0);
        if (o.sneakKey.isPressed())   dir = dir.add(0, -1, 0);

        prev = pos;
        if (dir.lengthSquared() > 0) {
            double sp = speed.get() * (o.sprintKey.isPressed() ? 2.6 : 1.0);
            pos = pos.add(dir.normalize().multiply(sp));
        }
    }

    @Override
    public void onRender(DrawContext ctx, MinecraftClient mc) {
        String t = "FREECAM";
        int cx = mc.getWindow().getScaledWidth() / 2, w = mc.textRenderer.getWidth(t) + 16;
        com.nova.client.gui.Ui.round(ctx, cx - w / 2, 8, w, 16, 0x99090916);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, t, cx, 12, 0xFF22D3EE);
    }
}

package com.nova.client.module;

import com.nova.client.gui.Anim;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.GameOptions;

import static com.nova.client.gui.Ui.round;

/** WASD, mouse buttons and space. Keys glow smoothly when pressed. */
public class KeystrokesModule extends HudModule {
    private final float[] a = new float[7];
    private long last = System.nanoTime();

    public KeystrokesModule() { super("Keystrokes", "WASD, mouse and jump display", true, 6, 6, false, true); }

    @Override protected int contentW(MinecraftClient mc) { return 58; }
    @Override protected int contentH(MinecraftClient mc) { return 68; }

    @Override
    protected void draw(DrawContext ctx, MinecraftClient mc) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        GameOptions o = mc.options;
        boolean[] p = {o.forwardKey.isPressed(), o.leftKey.isPressed(), o.backKey.isPressed(), o.rightKey.isPressed(),
                o.attackKey.isPressed(), o.useKey.isPressed(), o.jumpKey.isPressed()};
        for (int i = 0; i < a.length; i++) a[i] = Anim.approach(a[i], p[i] ? 1f : 0f, dt, 22f);

        key(ctx, mc, "W", 20, 0, 18, 18, a[0]);
        key(ctx, mc, "A", 0, 20, 18, 18, a[1]);
        key(ctx, mc, "S", 20, 20, 18, 18, a[2]);
        key(ctx, mc, "D", 40, 20, 18, 18, a[3]);
        key(ctx, mc, "LMB", 0, 40, 28, 16, a[4]);
        key(ctx, mc, "RMB", 30, 40, 28, 16, a[5]);
        key(ctx, mc, "", 0, 58, 58, 10, a[6]);
    }

    private void key(DrawContext ctx, MinecraftClient mc, String label, int x, int y, int w, int h, float t) {
        round(ctx, x, y, w, h, Anim.lerpColor(0x8A0C0A1E, com.nova.client.gui.Ui.a(com.nova.client.gui.Ui.ACCENT, 0.92f), t));
        if (label.isEmpty()) {
            ctx.fill(x + 14, y + 4, x + w - 14, y + 6, Anim.lerpColor(0xFFA9A6C4, 0xFF1A1433, t));
            return;
        }
        ctx.drawCenteredTextWithShadow(mc.textRenderer, label, x + w / 2, y + (h - 8) / 2,
                Anim.lerpColor(0xFFFFFFFF, 0xFF1A1433, t));
    }
}

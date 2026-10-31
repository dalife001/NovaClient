package com.nova.client.module;

import com.nova.client.gui.Gfx;
import net.minecraft.client.Minecraft;

/** Iris-free Christmas ambience: a light HUD snow pass with optional festive edge lights. */
public final class ChristmasModule extends Module {
    public final NumberSetting density = new NumberSetting("Snow amount", 0, 100, 24, 1);
    public final NumberSetting speed = new NumberSetting("Snow speed", 0, 100, 36, 1);
    public final NumberSetting size = new NumberSetting("Flake size", 1, 100, 18, 1);
    public final NumberSetting glow = new NumberSetting("Glow", 0, 100, 8, 1);
    public final NumberSetting quality = new NumberSetting("Quality", 1, 3, 1, 1);
    public final NumberSetting lights = new NumberSetting("Festive lights", 0, 100, 28, 1);

    private static final int MAX_FLAKES = 220;
    private static final int[] COLORS = {0xFFF6FFFF, 0xFFD9F2FF, 0xFFFFF0D6};

    public ChristmasModule() {
        super("Christmas", "Iris-free snowfall, soft glow and festive edge lights", false);
        settings.add(density);
        settings.add(speed);
        settings.add(size);
        settings.add(glow);
        settings.add(quality);
        settings.add(lights);
    }

    @Override public String category() { return "Visual"; }

    @Override
    public void onRender(Gfx ctx, Minecraft mc) {
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        if (width < 2 || height < 2) return;

        float time = System.nanoTime() / 1_000_000_000f;
        int q = (int) quality.get();
        float densityScale = q == 1 ? 0.45f : q == 2 ? 0.75f : 1f;
        int count = Math.min(MAX_FLAKES, Math.round((float) density.get() * densityScale));
        float motion = (float) speed.get() / 100f;
        float flakeScale = 0.55f + (float) size.get() / 100f * 2.2f;
        float glowAmount = (float) glow.get() / 100f;

        for (int i = 0; i < count; i++) {
            float seedX = unit(i * 0x9E3779B9 + 0x51ED270B);
            float seedY = unit(i * 0x85EBCA6B + 0xC2B2AE35);
            float drift = (float) Math.sin(time * (0.32f + seedX * 0.7f) + seedY * 19f);
            float fall = (time * (0.035f + motion * 0.38f) * (0.45f + seedX * 0.9f) + seedY) % 1f;
            int x = clamp((int) ((seedX + drift * (0.012f + motion * 0.028f) + 1f) % 1f * width), 1, width - 1);
            int y = (int) (fall * (height + 12)) - 6;
            if (y < 0 || y >= height) continue;

            int radius = Math.max(1, Math.round(flakeScale * (0.65f + seedX * 0.65f)));
            int color = COLORS[i % COLORS.length];
            int alpha = 125 + (i % 4) * 28;
            if (glowAmount > 0f && q >= 2 && radius >= 2) {
                int halo = Math.round(22f * glowAmount * (q == 2 ? 0.55f : 1f));
                if (halo > 0) ctx.fill(x - radius - 1, y - radius - 1, x + radius + 2, y + radius + 2, (halo << 24) | (color & 0xFFFFFF));
            }
            int core = (alpha << 24) | (color & 0xFFFFFF);
            ctx.fill(x - radius, y, x + radius + 1, y + 1, core);
            if (q >= 2 && radius >= 2) ctx.fill(x, y - radius, x + 1, y + radius + 1, core);
        }

        drawLights(ctx, width, height, time, q);
    }

    private void drawLights(Gfx ctx, int width, int height, float time, int q) {
        float amount = (float) lights.get() / 100f;
        int bulbs = Math.round(amount * (q == 1 ? 10 : q == 2 ? 18 : 28));
        for (int i = 0; i < bulbs; i++) {
            float xUnit = (i + 0.5f) / Math.max(1, bulbs);
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * (0.8f + (i % 3) * 0.17f) + i * 1.71f);
            int x = (int) (xUnit * width);
            int y = 7 + (i % 3) * 3;
            int[] palette = {0xFFFF5468, 0xFF67E59A, 0xFFFFD76A, 0xFF78D9FF};
            int color = palette[i % palette.length];
            int alpha = 70 + (int) (pulse * 130f);
            if (q >= 3 && glow.get() > 0) {
                int halo = (int) (alpha * (float) glow.get() / 100f * 0.20f);
                if (halo > 0) ctx.fill(x - 3, y - 3, x + 4, y + 4, (halo << 24) | (color & 0xFFFFFF));
            }
            ctx.fill(x - 1, y - 1, x + 2, y + 2, (alpha << 24) | (color & 0xFFFFFF));
        }
    }

    private static float unit(int value) {
        int h = value ^ (value >>> 16);
        h *= 0x7FEB352D;
        h ^= h >>> 15;
        h *= 0x846CA68B;
        h ^= h >>> 16;
        return (h & 0x7FFFFFFF) / (float) Integer.MAX_VALUE;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

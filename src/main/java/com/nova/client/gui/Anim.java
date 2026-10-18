package com.nova.client.gui;

/** Small animation toolkit: easing, smoothing, colour blending. */
public final class Anim {
    private static final long START = System.nanoTime();

    private Anim() {}

    public static float seconds() { return (System.nanoTime() - START) / 1_000_000_000f; }

    public static float clamp01(float v) { return v < 0 ? 0 : Math.min(v, 1); }

    public static float easeOutCubic(float t) { float u = 1 - clamp01(t); return 1 - u * u * u; }

    public static float easeOutBack(float t) {
        t = clamp01(t) - 1;
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * t * t * t + c1 * t * t;
    }

    /** Framerate-independent smoothing: moves `cur` toward `target`. */
    public static float approach(float cur, float target, float dt, float speed) {
        return cur + (target - cur) * (1f - (float) Math.exp(-speed * dt));
    }

    /** Eased 0..1 progress for an element that starts after `delay` seconds and takes `dur`. */
    public static float stagger(float t, float delay, float dur) { return easeOutCubic((t - delay) / dur); }

    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    public static int lerpColor(int a, int b, float t) {
        t = clamp01(t);
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}

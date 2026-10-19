package com.nova.client.gui;

/** Accent palettes for the whole client. Everything reads Ui.ACCENT / Ui.ACCENT_DARK / Ui.CYAN, so switching is instant. */
public final class Theme {
    public static final String[] NAMES = {"Nova", "Ocean", "Sunset", "Emerald", "Rose"};
    // accent, accent dark, secondary
    private static final int[][] P = {
            {0xFF8B5CF6, 0xFF5B34B8, 0xFF22D3EE},
            {0xFF3B82F6, 0xFF1D4ED8, 0xFF2DD4BF},
            {0xFFF97316, 0xFFC2410C, 0xFFF472B6},
            {0xFF10B981, 0xFF047857, 0xFFA3E635},
            {0xFFF43F5E, 0xFF9F1239, 0xFFFBBF24},
    };
    private static int current = -1;

    private Theme() {}

    public static int index() { return Math.max(0, current); }
    public static String name() { return NAMES[index()]; }

    public static void apply(int i) {
        i = Math.max(0, Math.min(P.length - 1, i));
        if (i == current) return;
        current = i;
        Ui.ACCENT = P[i][0];
        Ui.ACCENT_DARK = P[i][1];
        Ui.CYAN = P[i][2];
    }

    /** A pale tint of a colour (used for shimmering text). */
    public static int light(int color) { return Anim.lerpColor(color, 0xFFFFFFFF, 0.6f); }
}

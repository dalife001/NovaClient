package com.nova.client.gui;

/**
 * Calm palettes for the whole client. Each one is a small night-sky: an accent, a deeper accent,
 * a warm secondary, two sky colours and three aurora colours.
 * Everything reads the fields on Ui, so switching is instant.
 *
 * The order matches the launcher's themes (0..4), so -Dnova.theme still lines up.
 */
public final class Theme {
    public static final String[] NAMES = {"Twilight", "Lagoon", "Dusk", "Meadow", "Blossom"};

    // accent, accent deep, secondary, sky top, sky bottom, aurora 1, aurora 2, aurora 3
    private static final int[][] P = {
            // Twilight: lavender night, peach glow
            {0xFFB69CFF, 0xFF6C5BD0, 0xFFFFC2A8, 0xFF080818, 0xFF352766, 0xFF7CF5D0, 0xFFB69CFF, 0xFFFF9EC7},
            // Lagoon: deep water blue, mint
            {0xFF7CC8FF, 0xFF2F6FB5, 0xFF8FF0D4, 0xFF050F1E, 0xFF164766, 0xFF5FF2C8, 0xFF7CC8FF, 0xFFA99BFF},
            // Dusk: warm rose sunset
            {0xFFFFA98A, 0xFFC4577A, 0xFFFFD58A, 0xFF110818, 0xFF6E2F4C, 0xFFFFB28A, 0xFFFF8FB1, 0xFFFFD58A},
            // Meadow: forest night, soft green
            {0xFF9BE8B0, 0xFF3E8F6B, 0xFFE3F59A, 0xFF05120D, 0xFF1B5240, 0xFF8EF0B0, 0xFFD2F59A, 0xFF5FD6C8},
            // Blossom: pink dream
            {0xFFFF9EC4, 0xFFB6487C, 0xFFC9B4FF, 0xFF150818, 0xFF5E2A66, 0xFFFF9EC4, 0xFFC9B4FF, 0xFFFFC2A8},
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
        Ui.SKY_TOP = P[i][3];
        Ui.SKY_BOT = P[i][4];
        Ui.AUR1 = P[i][5];
        Ui.AUR2 = P[i][6];
        Ui.AUR3 = P[i][7];
    }

    /** A pale tint of a colour (used for shimmering text). */
    public static int light(int color) { return Anim.lerpColor(color, 0xFFFFFFFF, 0.55f); }
}

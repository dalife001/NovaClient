package com.nova.client.gui;

import java.awt.image.BufferedImage;

/** Draws the front of a Minecraft skin (like the in-game preview), 16x32 pixels. */
public final class SkinPreview {
    public static final int W = 16, H = 32;

    private SkinPreview() {}

    /** Returns W*H ARGB pixels. Works for 64x64 and legacy 64x32 skins. */
    public static int[] front(BufferedImage skin, boolean slim) {
        int[] out = new int[W * H];
        boolean legacy = skin.getHeight() < 64;
        // head, body, arms, legs: base layer first, then the overlay layer
        part(out, skin, 8, 8, 8, 8, 4, 0, false, true);          // head
        part(out, skin, 20, 20, 8, 12, 4, 8, false, true);       // body
        int aw = slim ? 3 : 4;
        part(out, skin, 44, 20, aw, 12, slim ? 1 : 0, 8, false, true);              // right arm (viewer's left)
        if (legacy) part(out, skin, 44, 20, aw, 12, 12, 8, true, true);             // left arm mirrored
        else part(out, skin, 36, 52, aw, 12, 12, 8, false, true);                   // left arm
        part(out, skin, 4, 20, 4, 12, 4, 20, false, true);       // right leg
        if (legacy) part(out, skin, 4, 20, 4, 12, 8, 20, true, true);               // left leg mirrored
        else part(out, skin, 20, 52, 4, 12, 8, 20, false, true);                    // left leg

        if (!legacy) {
            part(out, skin, 40, 8, 8, 8, 4, 0, false, false);    // hat
            part(out, skin, 20, 36, 8, 12, 4, 8, false, false);  // jacket
            part(out, skin, 44, 36, aw, 12, slim ? 1 : 0, 8, false, false);   // right sleeve
            part(out, skin, 52, 52, aw, 12, 12, 8, false, false);              // left sleeve
            part(out, skin, 4, 36, 4, 12, 4, 20, false, false);  // right pant leg
            part(out, skin, 4, 52, 4, 12, 8, 20, false, false);  // left pant leg
        } else {
            part(out, skin, 40, 8, 8, 8, 4, 0, false, false);    // hat exists on legacy skins too
        }
        return out;
    }

    private static void part(int[] out, BufferedImage s, int sx, int sy, int w, int h, int dx, int dy,
                             boolean mirror, boolean opaque) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = sx + (mirror ? w - 1 - x : x), py = sy + y;
                if (px >= s.getWidth() || py >= s.getHeight()) continue;
                int argb = s.getRGB(px, py);
                int a = argb >>> 24;
                if (opaque) argb |= 0xFF000000;
                else if (a < 128) continue;
                int ox = dx + x, oy = dy + y;
                if (ox >= 0 && ox < W && oy >= 0 && oy < H) out[oy * W + ox] = argb;
            }
        }
    }

    public static BufferedImage toImage(int[] px, int scale) {
        BufferedImage img = new BufferedImage(W * scale, H * scale, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H * scale; y++)
            for (int x = 0; x < W * scale; x++)
                img.setRGB(x, y, px[(y / scale) * W + (x / scale)]);
        return img;
    }
}

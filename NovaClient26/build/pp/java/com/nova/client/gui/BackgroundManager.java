package com.nova.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.nova.client.config.Config;
import com.nova.client.util.S;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Draws the user's custom background image (png or jpg) behind every Nova screen.
 * Needs the 1.20+ drawing API; on older versions the built-in night sky is always used.
 */
public final class BackgroundManager {
    private static Identifier id() {
        return Identifier.fromNamespaceAndPath("novaclient", "custom_background");
    }

    private static final Identifier ID = id();
    private static String loaded = null;
    private static boolean ok;
    private static int iw, ih;

    private BackgroundManager() {}

    /** @return true if an image was drawn (so the caller skips the gradient). */
    public static boolean draw(Gfx ctx, int w, int h, float parX, float parY) {
        if (!Gfx.CAN_DRAW_TEXTURE) return false;
        String path = Config.get().background();
        if (S.blank(path)) return false;
        if (!path.equals(loaded)) load(path);
        if (!ok) return false;

        // slow "breathing" zoom + mouse parallax
        double zoom = 1.06 + 0.02 * Math.sin(Anim.seconds() * 0.15);
        double s = Math.max((double) w / iw, (double) h / ih) * zoom;
        int dw = (int) Math.ceil(iw * s), dh = (int) Math.ceil(ih * s);
        int ox = (w - dw) / 2 - (int) (parX * 18), oy = (h - dh) / 2 - (int) (parY * 12);
        ctx.drawTexture(ID, ox, oy, dw, dh);
        ctx.fill(0, 0, w, h, 0x95050514); // darken so text stays readable
        return true;
    }

    /** Forces a reload next frame (after the path changed). */
    public static void reset() { loaded = null; }

    private static void load(String path) {
        loaded = path;
        ok = false;
        try {
            Path p = Paths.get(path.replace("\"", ""));
            if (!Files.isRegularFile(p)) return;
            BufferedImage img = ImageIO.read(p.toFile());
            if (img == null) return;
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(img, "png", png); // NativeImage only reads PNG, so convert jpg etc.
            NativeImage ni = NativeImage.read(new ByteArrayInputStream(png.toByteArray()));
            iw = ni.getWidth();
            ih = ni.getHeight();
            Minecraft.getInstance().getTextureManager().register(ID, new DynamicTexture(() -> "novaclient_background", ni));
            ok = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

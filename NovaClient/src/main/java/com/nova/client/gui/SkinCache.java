package com.nova.client.gui;

import com.google.gson.JsonObject;
import com.nova.client.compat.Mc;
import com.nova.client.util.Net;
import com.nova.client.util.S;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

/** Downloads the logged-in player's skin once and draws their head / body from it. */
public final class SkinCache {
    private static volatile int[] pixels;
    private static volatile String forName = "";
    private static volatile boolean loading;
    private static long retryAt;

    private SkinCache() {}

    /** Call every frame: starts a background load when the account changed. */
    public static void ensure() {
        Minecraft mc = Minecraft.getInstance();
        String name = mc.getUser().getName();
        if (name.equals(forName) || loading || System.currentTimeMillis() < retryAt) return;
        if (!Mc.online(mc)) { // offline names have no real skin
            forName = name;
            pixels = null;
            return;
        }
        loading = true;
        Thread t = new Thread(() -> {
            try {
                JsonObject id = S.parseObject(get("https://api.mojang.com/users/profiles/minecraft/" + name));
                JsonObject prof = S.parseObject(get("https://sessionserver.mojang.com/session/minecraft/profile/" + id.get("id").getAsString()));
                String b64 = prof.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
                JsonObject skin = S.parseObject(new String(Base64.getDecoder().decode(b64), java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject("textures").getAsJsonObject("SKIN");
                boolean slim = skin.has("metadata") && skin.getAsJsonObject("metadata").has("model");
                Net.Response r = Net.get(skin.get("url").getAsString());
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(r.bytes));
                pixels = SkinPreview.front(img, slim);
                forName = name;
            } catch (Exception e) {
                retryAt = System.currentTimeMillis() + 60_000; // try again in a minute
            } finally {
                loading = false;
            }
        }, "nova-skin-cache");
        t.setDaemon(true);
        t.start();
    }

    private static String get(String url) throws Exception {
        Net.Response r = Net.get(url);
        if (r.status != 200) throw new IllegalStateException("HTTP " + r.status);
        return r.body();
    }

    public static boolean hasSkin() { return pixels != null; }

    /** The face (with hat layer) as a size x size square, or a coloured initial if there is no skin. */
    public static void drawHead(Gfx c, int x, int y, int size, float alpha) {
        int[] p = pixels;
        if (p == null) {
            Ui.round(c, x, y, size, size, Ui.a(Ui.ACCENT_DARK, alpha));
            String n = Minecraft.getInstance().getUser().getName();
            Ui.centered(c, Minecraft.getInstance().font, n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase(),
                    x + size / 2, y + (size - 8) / 2, Ui.TEXT, alpha);
            return;
        }
        float s = size / 8f;
        for (int py = 0; py < 8; py++) {
            for (int px = 0; px < 8; px++) {
                int col = p[py * SkinPreview.W + 4 + px];
                if ((col >>> 24) == 0) continue;
                c.fill(x + Math.round(px * s), y + Math.round(py * s), x + Math.round((px + 1) * s), y + Math.round((py + 1) * s),
                        Ui.a(col | 0xFF000000, alpha));
            }
        }
    }

    /** Full body, front view. Returns false if there is no skin to draw. */
    public static boolean drawBody(Gfx c, int x, int y, int scale, float alpha) {
        int[] p = pixels;
        if (p == null) return false;
        for (int py = 0; py < SkinPreview.H; py++) {
            for (int px = 0; px < SkinPreview.W; px++) {
                int col = p[py * SkinPreview.W + px];
                if ((col >>> 24) == 0) continue;
                c.fill(x + px * scale, y + py * scale, x + (px + 1) * scale, y + (py + 1) * scale, Ui.a(col | 0xFF000000, alpha));
            }
        }
        return true;
    }
}

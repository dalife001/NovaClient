package com.nova.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.session.Session;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
        MinecraftClient mc = MinecraftClient.getInstance();
        String name = mc.getSession().getUsername();
        if (name.equals(forName) || loading || System.currentTimeMillis() < retryAt) return;
        if (mc.getSession().getAccountType() != Session.AccountType.MSA) { // offline names have no real skin
            forName = name;
            pixels = null;
            return;
        }
        loading = true;
        Thread t = new Thread(() -> {
            try {
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NORMAL).build();
                JsonObject id = JsonParser.parseString(get(http, "https://api.mojang.com/users/profiles/minecraft/" + name))
                        .getAsJsonObject();
                JsonObject prof = JsonParser.parseString(get(http,
                        "https://sessionserver.mojang.com/session/minecraft/profile/" + id.get("id").getAsString()))
                        .getAsJsonObject();
                String b64 = prof.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
                JsonObject skin = JsonParser.parseString(new String(Base64.getDecoder().decode(b64)))
                        .getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN");
                boolean slim = skin.has("metadata") && skin.getAsJsonObject("metadata").has("model");
                HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(skin.get("url").getAsString())).build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(r.body()));
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

    private static String get(HttpClient http, String url) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode());
        return r.body();
    }

    public static boolean hasSkin() { return pixels != null; }

    /** The face (with hat layer) as a size x size square, or a coloured initial if there is no skin. */
    public static void drawHead(DrawContext c, int x, int y, int size, float alpha) {
        int[] p = pixels;
        if (p == null) {
            Ui.round(c, x, y, size, size, Ui.a(Ui.ACCENT_DARK, alpha));
            String n = MinecraftClient.getInstance().getSession().getUsername();
            Ui.centered(c, MinecraftClient.getInstance().textRenderer, n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase(),
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
    public static boolean drawBody(DrawContext c, int x, int y, int scale, float alpha) {
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

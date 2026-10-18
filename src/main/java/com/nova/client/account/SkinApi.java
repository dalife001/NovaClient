package com.nova.client.account;

import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Uploads a skin to your Minecraft profile via the official Minecraft Services API. */
public final class SkinApi {
    private SkinApi() {}

    public static String upload(Path file, boolean slim) throws Exception {
        String token = AccountManager.mcToken;
        if (token == null) throw new IllegalStateException("Log in with Microsoft first");
        if (!Files.isRegularFile(file)) throw new IllegalArgumentException("File not found: " + file);

        byte[] data = Files.readAllBytes(file);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
        if (img == null) throw new IllegalArgumentException("That is not a valid PNG image");
        boolean ok = img.getWidth() == 64 && (img.getHeight() == 64 || img.getHeight() == 32);
        if (!ok) throw new IllegalArgumentException("Skin must be 64x64 (or 64x32) pixels, got "
                + img.getWidth() + "x" + img.getHeight());

        String boundary = "----NovaClient" + UUID.randomUUID();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n"
                + (slim ? "slim" : "classic") + "\r\n");
        write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n");
        out.write(data);
        write(out, "\r\n--" + boundary + "--\r\n");

        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile/skins"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();
        JsonObject res = MicrosoftAuth.send(req);
        int status = res.get("_status").getAsInt();
        if (status == 200) return "Skin updated! Rejoin a world/server to see it.";
        String msg = res.has("errorMessage") ? res.get("errorMessage").getAsString() : "HTTP " + status;
        throw new IllegalStateException(msg);
    }

    private static void write(ByteArrayOutputStream o, String s) throws java.io.IOException {
        o.write(s.getBytes(StandardCharsets.UTF_8));
    }
}

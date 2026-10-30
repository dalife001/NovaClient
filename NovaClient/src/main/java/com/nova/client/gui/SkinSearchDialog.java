package com.nova.client.gui;

import com.google.gson.JsonObject;
import com.nova.client.compat.Mc;
import com.nova.client.util.Net;
import com.nova.client.util.S;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.BiConsumer;

/** Looks up a Minecraft username and returns its current public skin. */
public final class SkinSearchDialog extends NovaScreen {
    private final BiConsumer<BufferedImage, Boolean> onSkinFound;
    private TextFieldWidget username;
    private volatile String status = "Enter a Minecraft Java username.";
    private volatile int statusColor = Ui.MUTED;
    private volatile boolean searching;

    public SkinSearchDialog(Screen parent, BiConsumer<BufferedImage, Boolean> onSkinFound) {
        super("Find a skin", parent);
        this.onSkinFound = onSkinFound;
    }

    private int px() { return width / 2 - 155; }
    private int py() { return Math.max(20, height / 2 - 90); }

    @Override
    protected void build() {
        username = new TextFieldWidget(textRenderer, px() + 18, py() + 56, 274, 20, Mc.text("Minecraft username"));
        username.setMaxLength(16);
        hint(username, "PlayerName");
        addField(username);
        button(px() + 18, py() + 88, 132, 22, "Find skin", true, this::search);
        button(px() + 160, py() + 88, 132, 22, "Cancel", false, this::nClose);
    }

    private void search() {
        if (searching) return;
        String name = username.getText().trim();
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            status = "Enter a valid Minecraft username.";
            statusColor = Ui.RED;
            return;
        }
        searching = true;
        status = "Looking up " + name + "...";
        statusColor = Ui.MUTED;
        Thread worker = new Thread(() -> {
            try {
                String escaped = URLEncoder.encode(name, "UTF-8");
                JsonObject profile = S.parseObject(get("https://api.mojang.com/users/profiles/minecraft/" + escaped));
                if (!profile.has("id")) throw new IllegalStateException("No Minecraft profile found for " + name + ".");
                String id = profile.get("id").getAsString();
                JsonObject session = S.parseObject(get("https://sessionserver.mojang.com/session/minecraft/profile/" + id));
                String encoded = session.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
                JsonObject textures = S.parseObject(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8));
                JsonObject skin = textures.getAsJsonObject("textures").getAsJsonObject("SKIN");
                String skinUrl = skin.get("url").getAsString();
                boolean slim = skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")
                        && "slim".equalsIgnoreCase(skin.getAsJsonObject("metadata").get("model").getAsString());
                Net.Response imageResponse = Net.get(skinUrl);
                if (imageResponse.status != 200) throw new IllegalStateException("Skin image request failed (HTTP " + imageResponse.status + ").");
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageResponse.bytes));
                if (image == null || image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32))
                    throw new IllegalStateException("The profile returned an unsupported skin image.");
                client.execute(() -> {
                    onSkinFound.accept(image, slim);
                    status = "Found " + name + "'s skin. Preview it, then choose Upload skin to apply it.";
                    statusColor = Ui.GREEN;
                    searching = false;
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Skin lookup failed." : e.getMessage();
                client.execute(() -> {
                    status = message;
                    statusColor = Ui.RED;
                    searching = false;
                });
            }
        }, "nova-skin-search");
        worker.setDaemon(true);
        worker.start();
    }

    private static String get(String url) throws Exception {
        Net.Response response = Net.get(url);
        if (response.status != 200) throw new IllegalStateException("Profile lookup failed (HTTP " + response.status + ").");
        return response.body();
    }

    @Override
    protected void drawBack(Gfx ctx, int mx, int my, float delta) {
        Ui.card(ctx, px(), py(), 310, 138);
        Ui.headerBand(ctx, px(), py(), 310);
    }

    @Override
    protected void drawFront(Gfx ctx, int mx, int my, float delta) {
        ctx.drawCenteredTextWithShadow(textRenderer, "Find a Minecraft skin", width / 2, py() + 10, Ui.TEXT);
        ctx.drawTextWithShadow(textRenderer, "Searches the public Java profile; sign in to apply it.", px() + 18, py() + 38, Ui.MUTED);
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(status, 274), px() + 18, py() + 120, statusColor);
    }
}

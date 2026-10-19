package com.nova.client.gui;

import com.nova.client.NovaClient;
import com.nova.client.UpdateChecker;
import com.nova.client.config.Config;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class SettingsScreen extends NovaScreen {
    private static final int W = 380;
    private String bgText = Config.get().backgroundPath == null ? "" : Config.get().backgroundPath;
    private String urlText = Config.get().updateManifestUrl == null ? "" : Config.get().updateManifestUrl;
    private String status = "";

    public SettingsScreen(Screen parent) { super("Settings", parent); }

    private int px() { return width / 2 - W / 2; }
    private int py() { return Math.max(14, height / 2 - 115); }

    @Override
    protected void build() {
        int x = px() + 20, w = W - 40;

        TextFieldWidget bg = new TextFieldWidget(textRenderer, x, py() + 52, w, 20, Text.literal("Background"));
        bg.setMaxLength(512);
        bg.setPlaceholder(Text.literal("C:\\path\\to\\wallpaper.png (png or jpg)"));
        bg.setText(bgText);
        bg.setChangedListener(s -> bgText = s);
        addDrawableChild(bg);

        int half = (w - 5) / 2;
        button(x, py() + 76, half, 20, "Apply background", true, () -> {
            Config.get().backgroundPath = bgText.trim().replace("\"", "");
            Config.save();
            BackgroundManager.reset();
            status = "Background saved";
        });
        button(x + half + 5, py() + 76, half, 20, "Remove background", false, () -> {
            bgText = "";
            Config.get().backgroundPath = "";
            Config.save();
            BackgroundManager.reset();
            clearAndInit();
            status = "Background removed";
        });

        TextFieldWidget url = new TextFieldWidget(textRenderer, x, py() + 126, w, 20, Text.literal("Update URL"));
        url.setMaxLength(512);
        url.setPlaceholder(Text.literal("https://.../latest.json (the launcher sets this for you)"));
        url.setText(urlText);
        url.setChangedListener(s -> urlText = s);
        addDrawableChild(url);

        button(x, py() + 150, half, 20, "Save + check updates", true, () -> {
            Config.get().updateManifestUrl = urlText.trim();
            Config.save();
            UpdateChecker.check();
            status = "Checking for updates...";
        });
        button(x + half + 5, py() + 150, half, 20, "Open mods folder", false, () ->
                net.minecraft.util.Util.getOperatingSystem().open(
                        net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("mods").toFile()));

        button(width / 2 - 50, py() + 196, 100, 22, "Back", false, this::close);
    }

    @Override
    protected void drawBack(DrawContext ctx, int mx, int my, float delta) {
        Ui.card(ctx, px(), py(), W, 182);
        Ui.headerBand(ctx, px(), py(), W);
    }

    @Override
    protected void drawFront(DrawContext ctx, int mx, int my, float delta) {
        int cx = width / 2;
        ctx.drawCenteredTextWithShadow(textRenderer, "Settings", cx, py() + 9, Ui.TEXT);
        ctx.drawTextWithShadow(textRenderer, "Custom background image", px() + 20, py() + 38, Ui.MUTED);
        ctx.drawTextWithShadow(textRenderer, "Update source", px() + 20, py() + 112, Ui.MUTED);
        String line = UpdateChecker.available()
                ? "Update available: " + UpdateChecker.latest + " (you have " + NovaClient.VERSION + ")"
                : "Version " + NovaClient.VERSION + " - " + UpdateChecker.note;
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(line, W - 40), px() + 20, py() + 176 - 4,
                UpdateChecker.available() ? Ui.GREEN : Ui.MUTED);
        if (!status.isEmpty()) ctx.drawCenteredTextWithShadow(textRenderer, status, cx, py() + 224, Ui.GREEN);
    }
}

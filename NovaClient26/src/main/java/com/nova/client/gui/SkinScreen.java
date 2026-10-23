package com.nova.client.gui;

import com.nova.client.compat.Mc;
import com.nova.client.account.AccountManager;
import com.nova.client.account.SkinApi;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

public class SkinScreen extends NovaScreen {
    private static final int W = 450;
    private String pathText = "";
    private boolean slim;
    private String status = "";
    private int statusColor = Ui.MUTED;
    private BufferedImage loaded;
    private String loadedPath = "";
    private int[] preview;

    public SkinScreen(Screen parent) { super("Skins", parent); }

    private int px() { return width / 2 - W / 2; }
    private int py() { return Math.max(20, height / 2 - 110); }

    @Override
    protected void build() {
        int x = px() + 20, lw = 270;
        EditBox field = new EditBox(font, x, py() + 62, lw, 20, Mc.text("Skin file"));
        field.setMaxLength(512);
        hint(field, "C:\\path\\to\\skin.png");
        field.setValue(pathText);
        field.setResponder(s -> pathText = s);
        addField(field);

        int half = (lw - 5) / 2;
        button(x, py() + 90, half, 22, slim ? "Model: Slim" : "Model: Classic", false, () -> {
            slim = !slim;
            preview = loaded == null ? null : SkinPreview.front(loaded, slim);
            rebuild();
        });
        button(x + half + 5, py() + 90, half, 22, "Upload skin", true, this::upload);
        button(width / 2 - 50, py() + 205, 100, 22, "Back", false, this::nClose);
    }

    private void refreshPreview() {
        String p = pathText.trim().replace("\"", "");
        if (p.equals(loadedPath)) return;
        loadedPath = p;
        loaded = null;
        preview = null;
        try {
            Path f = java.nio.file.Paths.get(p);
            if (p.isEmpty() || !Files.isRegularFile(f)) return;
            BufferedImage img = ImageIO.read(f.toFile());
            if (img == null || img.getWidth() != 64 || (img.getHeight() != 64 && img.getHeight() != 32)) return;
            loaded = img;
            preview = SkinPreview.front(img, slim);
        } catch (Exception ignored) {}
    }

    private void upload() {
        if (!AccountManager.isLoggedIn()) { set("Log in with Microsoft first (Account)", Ui.RED); return; }
        String p = pathText.trim().replace("\"", "");
        if (p.isEmpty()) { set("Enter the path to a 64x64 PNG", Ui.RED); return; }
        set("Uploading...", Ui.MUTED);
        boolean s = slim;
        Thread t = new Thread(() -> {
            try {
                String msg = SkinApi.upload(java.nio.file.Paths.get(p), s);
                minecraft.execute(() -> set(msg, Ui.GREEN));
            } catch (Exception e) {
                minecraft.execute(() -> set(String.valueOf(e.getMessage()), Ui.RED));
            }
        }, "nova-skin-upload");
        t.setDaemon(true);
        t.start();
    }

    private void set(String msg, int color) { status = msg; statusColor = color; }

    @Override
    protected void drawBack(Gfx ctx, int mx, int my, float delta) {
        Ui.card(ctx, px(), py(), W, 190);
        Ui.headerBand(ctx, px(), py(), W);
        // preview well
        Ui.round(ctx, px() + W - 140, py() + 36, 120, 142, 0x30FFFFFF);
    }

    @Override
    protected void drawFront(Gfx ctx, int mx, int my, float delta) {
        refreshPreview();
        int x = px() + 20;
        ctx.drawCenteredTextWithShadow(font, "Change your skin", width / 2, py() + 9, Ui.TEXT);
        ctx.drawTextWithShadow(font, "Pick a 64x64 PNG on your computer", x, py() + 38, Ui.MUTED);
        ctx.drawTextWithShadow(font, "Skin file path", x, py() + 51, Ui.MUTED);
        ctx.drawTextWithShadow(font, font.plainSubstrByWidth(status, 270), x, py() + 122, statusColor);
        ctx.drawTextWithShadow(font, "Needs a Microsoft login (Account).", x, py() + 162, Ui.MUTED);

        // live preview
        int s = 4, ox = px() + W - 140 + (120 - SkinPreview.W * s) / 2, oy = py() + 36 + (142 - SkinPreview.H * s) / 2;
        if (preview == null) {
            ctx.drawCenteredTextWithShadow(font, "Preview", px() + W - 80, py() + 100, Ui.MUTED);
        } else {
            for (int yy = 0; yy < SkinPreview.H; yy++)
                for (int xx = 0; xx < SkinPreview.W; xx++) {
                    int c = preview[yy * SkinPreview.W + xx];
                    if ((c >>> 24) == 0) continue;
                    ctx.fill(ox + xx * s, oy + yy * s, ox + (xx + 1) * s, oy + (yy + 1) * s, c | 0xFF000000);
                }
        }
    }
}

package com.nova.client.gui;

import com.nova.client.account.AccountManager;
import com.nova.client.account.SkinApi;
import com.nova.client.compat.Mc;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Preview a local or public Minecraft skin and apply it to the signed-in account. */
public class SkinScreen extends NovaScreen {
    private static final int W = 450, H = 218;
    private String pathText = "";
    private boolean slim;
    private String status = "Choose a local PNG or find a Minecraft username.";
    private int statusColor = Ui.MUTED;
    private BufferedImage loaded;
    private String loadedPath = "";
    private int[] preview;
    private volatile boolean uploading;

    public SkinScreen(Screen parent) { super("Skins", parent); }

    private int px() { return width / 2 - W / 2; }
    private int py() { return Math.max(12, height / 2 - H / 2); }

    @Override
    protected void build() {
        int x = px() + 20, leftW = 270;
        TextFieldWidget fileField = new TextFieldWidget(textRenderer, x, py() + 58, leftW, 20, Mc.text("Skin file"));
        fileField.setMaxLength(512);
        hint(fileField, "C:\\path\\to\\skin.png");
        fileField.setText(pathText);
        fileField.setChangedListener(value -> {
            pathText = value;
            loaded = null;
            loadedPath = "";
            refreshPreview();
        });
        addField(fileField);

        button(x, py() + 88, leftW, 22, "Find skin by Minecraft username", true,
                () -> Mc.setScreen(client, new SkinSearchDialog(this, this::useSearchedSkin)));
        int half = (leftW - 6) / 2;
        button(x, py() + 118, half, 22, slim ? "Model: Slim" : "Model: Classic", false, () -> {
            slim = !slim;
            if (loaded != null) preview = SkinPreview.front(loaded, slim);
            rebuild();
        });
        button(x + half + 6, py() + 118, half, 22, uploading ? "Uploading..." : "Upload skin", true, this::upload);
        button(width / 2 - 50, py() + H - 29, 100, 22, "Back", false, this::nClose);
    }

    private void useSearchedSkin(BufferedImage skin, Boolean isSlim) {
        pathText = "";
        loaded = skin;
        loadedPath = "";
        slim = Boolean.TRUE.equals(isSlim);
        preview = SkinPreview.front(skin, slim);
        status = "Skin loaded. Sign in, then Upload skin to apply it to your account.";
        statusColor = Ui.GREEN;
        rebuild();
    }

    private void refreshPreview() {
        String p = pathText.trim().replace("\"", "");
        if (p.equals(loadedPath)) return;
        loadedPath = p;
        loaded = null;
        preview = null;
        if (p.isEmpty()) return;
        try {
            Path file = Paths.get(p);
            if (!Files.isRegularFile(file)) return;
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null || image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) return;
            loaded = image;
            preview = SkinPreview.front(image, slim);
        } catch (Exception ignored) {}
    }

    private void upload() {
        if (uploading) return;
        if (!AccountManager.isLoggedIn()) {
            status = "Log in with Microsoft first (Account).";
            statusColor = Ui.RED;
            return;
        }
        refreshPreview();
        BufferedImage skin = loaded;
        if (skin == null) {
            status = "Choose a valid 64x64 or 64x32 skin PNG first.";
            statusColor = Ui.RED;
            return;
        }
        String p = pathText.trim().replace("\"", "");
        boolean uploadSlim = slim;
        uploading = true;
        status = "Uploading skin to your Minecraft account...";
        statusColor = Ui.MUTED;
        rebuild();
        Thread worker = new Thread(() -> {
            try {
                String message;
                if (!p.isEmpty() && Paths.get(p).toFile().isFile()) {
                    message = SkinApi.upload(Paths.get(p), uploadSlim);
                } else {
                    Path temp = Files.createTempFile("nova-skin-", ".png");
                    try {
                        ImageIO.write(skin, "png", temp.toFile());
                        message = SkinApi.upload(temp, uploadSlim);
                    } finally {
                        Files.deleteIfExists(temp);
                    }
                }
                String result = message;
                client.execute(() -> {
                    status = result;
                    statusColor = Ui.GREEN;
                    uploading = false;
                    rebuild();
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Skin upload failed." : e.getMessage();
                client.execute(() -> {
                    status = message;
                    statusColor = Ui.RED;
                    uploading = false;
                    rebuild();
                });
            }
        }, "nova-skin-upload");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    protected void drawBack(Gfx ctx, int mx, int my, float delta) {
        Ui.card(ctx, px(), py(), W, H);
        Ui.headerBand(ctx, px(), py(), W);
        Ui.round(ctx, px() + W - 140, py() + 36, 120, 142, 0x30FFFFFF);
    }

    @Override
    protected void drawFront(Gfx ctx, int mx, int my, float delta) {
        refreshPreview();
        int x = px() + 20;
        ctx.drawCenteredTextWithShadow(textRenderer, "Your skins", width / 2, py() + 9, Ui.TEXT);
        ctx.drawTextWithShadow(textRenderer, "Local PNG or public Java username", x, py() + 38, Ui.MUTED);
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(status, 270), x, py() + 152, statusColor);
        ctx.drawTextWithShadow(textRenderer, "Applying a skin requires Microsoft sign-in.", x, py() + 174, Ui.MUTED);

        int scale = 4;
        int ox = px() + W - 140 + (120 - SkinPreview.W * scale) / 2;
        int oy = py() + 36 + (142 - SkinPreview.H * scale) / 2;
        if (preview == null) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Preview", px() + W - 80, py() + 100, Ui.MUTED);
        } else {
            for (int yy = 0; yy < SkinPreview.H; yy++) {
                for (int xx = 0; xx < SkinPreview.W; xx++) {
                    int color = preview[yy * SkinPreview.W + xx];
                    if ((color >>> 24) != 0)
                        ctx.fill(ox + xx * scale, oy + yy * scale, ox + (xx + 1) * scale, oy + (yy + 1) * scale, color | 0xFF000000);
                }
            }
        }
    }
}

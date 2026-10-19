package com.nova.client.gui;

import com.nova.client.account.AccountManager;
import com.nova.client.config.Config;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Util;

import java.net.URI;

public class AccountScreen extends NovaScreen {
    private static final int W = 300;

    public AccountScreen(Screen parent) { super("Account", parent); }

    private int px() { return width / 2 - W / 2; }
    private int py() { return Math.max(20, height / 2 - 110); }

    @Override
    protected void build() {
        int x = px() + 20, w = W - 40, y = py() + 118;
        if (AccountManager.isLoggedIn()) {
            button(x, y, w, 22, "Log out", false, () -> { AccountManager.logout(); clearAndInit(); });
        } else {
            button(x, y, w, 22, AccountManager.isBusy() ? "Waiting for login..." : "Log in with Microsoft", true, () -> {
                AccountManager.startLogin();
            });
        }
        int half = (w - 5) / 2;
        button(x, y + 27, half, 22, "Copy code", false, () -> {
            if (AccountManager.userCode != null) client.keyboard.setClipboard(AccountManager.userCode);
        });
        button(x + half + 5, y + 27, half, 22, "Open login page", false, () -> {
            String u = AccountManager.verifyUri != null ? AccountManager.verifyUri : "https://www.microsoft.com/link";
            Util.getOperatingSystem().open(URI.create(u));
        });
        button(width / 2 - 50, py() + 215, 100, 22, "Back", false, this::close);
    }

    @Override
    protected void drawBack(DrawContext ctx, int mx, int my, float delta) {
        Ui.card(ctx, px(), py(), W, 200);
        ctx.fillGradient(px(), py(), px() + W, py() + 26, Ui.ACCENT, Ui.ACCENT_DARK);
    }

    @Override
    protected void drawFront(DrawContext ctx, int mx, int my, float delta) {
        int cx = width / 2;
        ctx.drawCenteredTextWithShadow(textRenderer, "Account", cx, py() + 9, Ui.TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, "Playing as", cx, py() + 38, Ui.MUTED);
        ctx.drawCenteredTextWithShadow(textRenderer, client.getSession().getUsername(), cx, py() + 50, Ui.TEXT);

        String code = AccountManager.userCode;
        if (code != null) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(cx, py() + 68, 0);
            ctx.getMatrices().scale(2.5f, 2.5f, 1f);
            ctx.drawCenteredTextWithShadow(textRenderer, code, 0, 0, Ui.CYAN);
            ctx.getMatrices().pop();
        }
        String status = AccountManager.status;
        int color = status.startsWith("Login failed") || status.startsWith("Put your") ? Ui.RED
                : AccountManager.isLoggedIn() ? Ui.GREEN : Ui.MUTED;
        ctx.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(status, W - 20), cx, py() + 98, color);

        if (Config.get().azureClientId == null || Config.get().azureClientId.isBlank()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Set azureClientId in config/novaclient.json", cx, py() + 178, Ui.MUTED);
        }
    }

    @Override
    public void tick() {
        // status text changes from the login thread; rebuild buttons when login finishes
        if (AccountManager.isLoggedIn() != lastLoggedIn || AccountManager.isBusy() != lastBusy) {
            lastLoggedIn = AccountManager.isLoggedIn();
            lastBusy = AccountManager.isBusy();
            clearAndInit();
        }
    }

    private boolean lastLoggedIn = AccountManager.isLoggedIn();
    private boolean lastBusy = AccountManager.isBusy();
}

package com.nova.client.gui;

import com.nova.client.util.S;
import com.nova.client.account.AccountManager;
import com.nova.client.compat.Mc;
import com.nova.client.config.Config;
import java.net.URI;
import net.minecraft.client.gui.screens.Screen;

public class AccountScreen extends NovaScreen {
    private static final int W = 300;

    public AccountScreen(Screen parent) { super("Account", parent); }

    private int px() { return width / 2 - W / 2; }
    private int py() { return Math.max(20, height / 2 - 110); }

    @Override
    protected void build() {
        int x = px() + 20, w = W - 40, y = py() + 118;
        if (AccountManager.isLoggedIn()) {
            button(x, y, w, 22, "Log out", false, () -> { AccountManager.logout(); rebuild(); });
        } else {
            button(x, y, w, 22, AccountManager.isBusy() ? "Waiting for login..." : "Log in with Microsoft", true, () -> {
                AccountManager.startLogin();
            });
        }
        int half = (w - 5) / 2;
        button(x, y + 27, half, 22, "Copy code", false, () -> {
            if (AccountManager.userCode != null) Mc.clipboard(minecraft, AccountManager.userCode);
        });
        button(x + half + 5, y + 27, half, 22, "Open login page", false, () -> {
            String u = AccountManager.verifyUri != null ? AccountManager.verifyUri : "https://www.microsoft.com/link";
            Mc.openUri(URI.create(u));
        });
        button(width / 2 - 50, py() + 215, 100, 22, "Back", false, this::nClose);
    }

    @Override
    protected void drawBack(Gfx ctx, int mx, int my, float delta) {
        Ui.card(ctx, px(), py(), W, 200);
        Ui.headerBand(ctx, px(), py(), W);
    }

    @Override
    protected void drawFront(Gfx ctx, int mx, int my, float delta) {
        int cx = width / 2;
        ctx.drawCenteredTextWithShadow(font, "Account", cx, py() + 9, Ui.TEXT);
        ctx.drawCenteredTextWithShadow(font, "Playing as", cx, py() + 38, Ui.MUTED);
        ctx.drawCenteredTextWithShadow(font, minecraft.getUser().getName(), cx, py() + 50, Ui.TEXT);

        String code = AccountManager.userCode;
        if (code != null) {
            ctx.push();
            ctx.translate((float) (cx), (float) (py() + 68));
            ctx.scale(2.5f, 2.5f);
            ctx.drawCenteredTextWithShadow(font, code, 0, 0, Ui.CYAN);
            ctx.pop();
        }
        String status = AccountManager.status;
        int color = status.startsWith("Login failed") || status.startsWith("Put your") ? Ui.RED
                : AccountManager.isLoggedIn() ? Ui.GREEN : Ui.MUTED;
        ctx.drawCenteredTextWithShadow(font, font.plainSubstrByWidth(status, W - 20), cx, py() + 98, color);

        if (S.blank(Config.get().azureClientId)) {
            ctx.drawCenteredTextWithShadow(font, "Set azureClientId in config/novaclient.json", cx, py() + 178, Ui.MUTED);
        }
    }

    @Override
    public void tick() {
        // status text changes from the login thread; rebuild buttons when login finishes
        if (AccountManager.isLoggedIn() != lastLoggedIn || AccountManager.isBusy() != lastBusy) {
            lastLoggedIn = AccountManager.isLoggedIn();
            lastBusy = AccountManager.isBusy();
            rebuild();
        }
    }

    private boolean lastLoggedIn = AccountManager.isLoggedIn();
    private boolean lastBusy = AccountManager.isBusy();
}

package com.nova.client.account;

import com.nova.client.config.Config;
import com.nova.client.gui.Toasts;
import com.nova.client.gui.Ui;
import com.nova.client.mixin.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.util.Optional;

/** Holds the current login state and swaps the game's Session after login. */
public final class AccountManager {
    public static volatile String status = "Using the account from your launcher";
    public static volatile String userCode;
    public static volatile String verifyUri;
    public static volatile String mcToken;

    private static Session original;
    private static volatile boolean busy;

    private AccountManager() {}

    public static boolean isBusy() { return busy; }
    public static boolean isLoggedIn() { return mcToken != null; }

    public static void startLogin() {
        if (busy) return;
        String cid = Config.get().azureClientId;
        if (cid == null || cid.isBlank()) {
            status = "Put your Azure client ID in config/novaclient.json (see README)";
            return;
        }
        busy = true;
        status = "Requesting login code...";
        run(() -> {
            MicrosoftAuth.DeviceCode dc = MicrosoftAuth.requestDeviceCode(cid);
            userCode = dc.userCode();
            verifyUri = dc.verificationUri();
            status = "Open the page and enter the code";
            finish(cid, MicrosoftAuth.pollForTokens(cid, dc));
        });
    }

    /** Called at startup: silently log back in with the saved refresh token. */
    public static void tryAutoLogin() {
        Config cfg = Config.get();
        if (busy || cfg.refreshToken == null || cfg.refreshToken.isBlank()
                || cfg.azureClientId == null || cfg.azureClientId.isBlank()) return;
        busy = true;
        status = "Logging back in...";
        run(() -> finish(cfg.azureClientId, MicrosoftAuth.refresh(cfg.azureClientId, cfg.refreshToken)));
    }

    private interface Task { void run() throws Exception; }

    private static void run(Task t) {
        Thread th = new Thread(() -> {
            try {
                t.run();
            } catch (Exception e) {
                status = "Login failed: " + e.getMessage();
                Toasts.push("Login failed", String.valueOf(e.getMessage()), Ui.RED);
            } finally {
                busy = false;
                userCode = null;
            }
        }, "nova-login");
        th.setDaemon(true);
        th.start();
    }

    private static void finish(String cid, MicrosoftAuth.MsTokens tok) throws Exception {
        if (!tok.refreshToken().isEmpty()) {
            Config.get().refreshToken = tok.refreshToken();
            Config.save();
        }
        MicrosoftAuth.McProfile p = MicrosoftAuth.loginMinecraft(tok.accessToken());
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (original == null) original = mc.getSession();
            ((MinecraftClientAccessor) mc).novaSetSession(new Session(
                    p.name(), p.uuid(), p.mcToken(), Optional.empty(), Optional.empty(), Session.AccountType.MSA));
        });
        mcToken = p.mcToken();
        status = "Logged in as " + p.name();
        Toasts.push("Logged in", p.name(), Ui.GREEN);
    }

    public static void logout() {
        Config.get().refreshToken = "";
        Config.save();
        mcToken = null;
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (original != null) ((MinecraftClientAccessor) mc).novaSetSession(original);
        });
        status = "Logged out (back to your launcher account)";
    }
}

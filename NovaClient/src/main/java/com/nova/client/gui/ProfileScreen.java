package com.nova.client.gui;

import com.nova.client.NovaClient;
import com.nova.client.PlayStats;
import com.nova.client.config.Config;
import com.nova.client.friends.FriendsManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.session.Session;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Your player card: skin, level, playtime and quick links. */
public class ProfileScreen extends NovaScreen {
    private int px, py, pw, ph;

    public ProfileScreen(Screen parent) { super("Profile", parent); }

    @Override
    protected void build() {
        pw = Math.min(width - 24, 540);
        ph = Math.min(height - 20, 290);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int rx = px + 172, rw = pw - 172 - 16;
        int bw = (rw - 12) / 3;
        int by = py + ph - 34;
        button(rx, by, bw, 20, "Skins", true, () -> client.setScreen(new SkinScreen(this)));
        button(rx + bw + 6, by, bw, 20, "Account", false, () -> client.setScreen(new AccountScreen(this)));
        button(rx + 2 * (bw + 6), by, bw, 20, "Friends", false, () -> client.setScreen(new FriendsScreen(this)));
        button(px + 12, py + ph - 26, 60, 16, "< Back", AnimButton.Style.GHOST, this::close);
    }

    @Override
    protected void drawBack(DrawContext ctx, int mx, int my, float delta) {
        float e = Anim.stagger(age(), 0f, 0.4f);
        int dy = (int) ((1f - e) * 12);
        Ui.round(ctx, px - 1, py - 1 + dy, pw + 2, ph + 2, Ui.a(0x40FFFFFF, e));
        Ui.round(ctx, px, py + dy, pw, ph, Ui.a(0xE80B0B1E, e));
        // left showcase column
        Ui.round(ctx, px + 8, py + 8 + dy, 154, ph - 16, Ui.a(0x66141430, e));
        ctx.fillGradient(px + 8, py + 10 + dy, px + 162, py + ph / 2 + dy, Ui.a(Ui.a(Ui.ACCENT, 0.13f), e), 0x00000000);
    }

    @Override
    protected void drawFront(DrawContext ctx, int mx, int my, float delta) {
        SkinCache.ensure();
        float t = age();
        String name = client.getSession().getUsername();
        boolean online = client.getSession().getAccountType() == Session.AccountType.MSA;

        // ---- left: figure on a glowing platform ----
        float eF = Anim.stagger(t, 0.1f, 0.6f);
        int scale = Math.max(2, Math.min(5, (ph - 100) / 32));
        int fw = SkinPreview.W * scale, fh = SkinPreview.H * scale;
        int fx = px + 8 + (154 - fw) / 2;
        int fy = py + 22 + (int) (Math.sin(t * 2.0) * 1.5) + (int) ((1f - eF) * 16);
        float glow = 0.5f + 0.5f * (float) Math.sin(t * 1.5);
        int platY = py + 22 + fh + 1;
        for (int i = 0; i < 4; i++) {
            int w = fw + 40 - i * 8;
            Ui.round(ctx, px + 8 + (154 - w) / 2, platY + i * 2, w, 3,
                    Ui.a(Anim.lerpColor(Ui.ACCENT, Ui.CYAN, i / 3f), (0.18f + 0.1f * glow) * eF));
        }
        if (!SkinCache.drawBody(ctx, fx, fy, scale, eF)) {
            int s = Math.min(64, fw);
            SkinCache.drawHead(ctx, px + 8 + (154 - s) / 2, py + 40, s, eF);
            Ui.centered(ctx, textRenderer, online ? "Loading skin..." : "Offline mode", px + 85, py + 40 + s + 8, Ui.MUTED, eF);
        }
        Ui.centered(ctx, textRenderer, name, px + 85, py + ph - 52, Ui.TEXT, eF);

        // ---- right: name + level ----
        int rx = px + 172, rw = pw - 172 - 16;
        float e1 = Anim.stagger(t, 0.15f, 0.5f);
        Ui.big(ctx, textRenderer, name, rx + textRenderer.getWidth(name), py + 16 - (int) ((1f - e1) * 8), 2f, Ui.TEXT, e1);
        // (big() centres; the shift above keeps it left aligned)

        String badge = online ? "Microsoft account" : "Offline mode";
        int bw = textRenderer.getWidth(badge) + 12;
        Ui.round(ctx, rx, py + 38, bw, 12, Ui.a(online ? 0xFF0B3B2E : 0xFF3B2F0B, e1));
        Ui.text(ctx, textRenderer, badge, rx + 6, py + 40, online ? Ui.GREEN : Ui.AMBER, e1);
        long since = Config.get().firstLaunch;
        if (since > 0) {
            String m = "Member since " + LocalDate.ofInstant(Instant.ofEpochMilli(since), ZoneId.systemDefault());
            Ui.text(ctx, textRenderer, m, rx + bw + 8, py + 40, Ui.MUTED, e1);
        }

        // level bar
        float e2 = Anim.stagger(t, 0.25f, 0.5f);
        int ly = py + 60;
        Ui.text(ctx, textRenderer, "LEVEL " + PlayStats.level(), rx, ly, Ui.ACCENT, e2);
        String next = PlayStats.minutesToNext() + " min to next level";
        Ui.text(ctx, textRenderer, next, rx + rw - textRenderer.getWidth(next), ly, Ui.MUTED, e2);
        Ui.round(ctx, rx, ly + 12, rw, 8, Ui.a(0xFF1A1A33, e2));
        float fill = PlayStats.progress() * Anim.easeOutCubic((t - 0.5f) / 0.9f); // bar fills in
        int fwid = Math.max(0, (int) ((rw - 2) * fill));
        Ui.hGradient(ctx, rx + 1, ly + 13, fwid, 6, Ui.a(Ui.ACCENT, e2), Ui.a(Ui.CYAN, e2));

        // stat cards
        Config cfg = Config.get();
        String[][] stats = {
                {"PLAYTIME", PlayStats.format(cfg.playSeconds)},
                {"SESSIONS", String.valueOf(Math.max(1, cfg.launches))},
                {"FRIENDS", String.valueOf(FriendsManager.list().size())},
                {"MODULES ON", NovaClient.modules.enabledCount() + " / " + NovaClient.modules.all().size()}};
        int cw = (rw - 8) / 2, ch = 44, sy = py + 92;
        for (int i = 0; i < 4; i++) {
            float e = Anim.stagger(t, 0.35f + i * 0.07f, 0.45f);
            int x = rx + (i % 2) * (cw + 8), y = sy + (i / 2) * (ch + 8) + (int) ((1f - e) * 10);
            boolean hov = Ui.inside(mx, my, x, y, cw, ch);
            Ui.round(ctx, x - 1, y - 1, cw + 2, ch + 2, Ui.a(hov ? Ui.a(Ui.CYAN, 0.67f) : 0x33FFFFFF, e));
            Ui.round(ctx, x, y, cw, ch, Ui.a(0xFF141430, e));
            ctx.fillGradient(x, y + 3, x + 2, y + ch - 3, Ui.a(Ui.ACCENT, e), Ui.a(Ui.CYAN, e));
            Ui.text(ctx, textRenderer, stats[i][0], x + 10, y + 8, Ui.MUTED, e);
            Ui.big(ctx, textRenderer, stats[i][1], x + 10 + textRenderer.getWidth(stats[i][1]) * 3 / 4, y + 22, 1.5f, Ui.TEXT, e);
        }
    }
}

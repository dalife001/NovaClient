package com.nova.client.gui;

import com.nova.client.NovaClient;
import com.nova.client.PlayStats;
import com.nova.client.UpdateChecker;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.resource.ResourcePackManager;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** The Nova hub: animated logo, big play buttons, tiles, quick-join servers and your profile. */
public class NovaTitleScreen extends NovaScreen {
    private static final String[] TIPS = {
            "Press F6 in-game to fly around with Freecam",
            "Hold C to zoom in",
            "Right Shift opens the module menu",
            "Drag your HUD anywhere in the HUD Editor",
            "Scroll over a HUD element in the editor to resize it",
            "Set your own wallpaper in Settings",
            "Add friends and see who is in your game",
            "Change your skin without leaving the game"
    };

    private final List<AnimButton> hub = new ArrayList<>();
    private final List<Float> delays = new ArrayList<>();
    private float pillHover;
    private long pillLast = System.nanoTime();
    private int pillX, pillY, pillW, pillH;

    public NovaTitleScreen() { super(NovaClient.NAME, null); }

    private AnimButton add(AnimButton b, float delay) { hub.add(b); delays.add(delay); return b; }

    @Override
    protected void build() {
        hub.clear();
        delays.clear();
        int cx = width / 2;
        int rowY = (int) (height * 0.45f);

        int bigW = Math.min(150, (width - 48) / 2);
        add(new AnimButton(cx - bigW - 4, rowY, bigW, 40, "Singleplayer", AnimButton.Style.BIG,
                () -> client.setScreen(new SelectWorldScreen(this))).sub("Your worlds"), 0.30f);
        add(new AnimButton(cx + 4, rowY, bigW, 40, "Multiplayer", AnimButton.Style.BIG,
                () -> client.setScreen(new MultiplayerScreen(this))).sub("Servers"), 0.38f);

        String[] names = {"Mods", "Friends", "Skins", "Textures", "Shaders", "Settings"};
        Runnable[] acts = {
                () -> client.setScreen(new ClickGuiScreen(this)),
                () -> client.setScreen(new FriendsScreen(this)),
                () -> client.setScreen(new SkinScreen(this)),
                this::openPacks,
                this::openShaders,
                () -> client.setScreen(new SettingsScreen(this))};
        int tileW = Math.max(40, Math.min(64, (width - 48 - 5 * 6) / 6));
        int total = 6 * tileW + 5 * 6;
        int tx = cx - total / 2, ty = rowY + 50;
        for (int i = 0; i < 6; i++) {
            add(new AnimButton(tx + i * (tileW + 6), ty, tileW, 38, names[i], AnimButton.Style.TILE, acts[i]).icon(i), 0.48f + i * 0.05f);
        }

        // Quick join: your saved servers
        try {
            ServerList sl = new ServerList(client);
            sl.loadFile();
            int n = Math.min(3, sl.size());
            if (n > 0) {
                int chipW = Math.min(118, (width - 48 - 2 * 6) / 3);
                int cTotal = n * chipW + (n - 1) * 6;
                int sx = cx - cTotal / 2, sy = ty + 50;
                for (int i = 0; i < n; i++) {
                    ServerInfo info = sl.get(i);
                    String label = textRenderer.trimToWidth(info.name, chipW - 14);
                    add(new AnimButton(sx + i * (chipW + 6), sy, chipW, 18, label, AnimButton.Style.SECONDARY, () ->
                            ConnectScreen.connect(this, client, ServerAddress.parse(info.address), info, false, null)), 0.85f + i * 0.05f);
                }
            }
        } catch (Throwable ignored) {}

        add(new AnimButton(width - 112, height - 22, 50, 16, "Options", AnimButton.Style.GHOST,
                () -> client.setScreen(new OptionsScreen(this, client.options))), 1.0f);
        add(new AnimButton(width - 58, height - 22, 44, 16, "Quit", AnimButton.Style.GHOST, () -> client.scheduleStop()), 1.0f);
    }

    private void openPacks() {
        client.setScreen(new PackScreen(client.getResourcePackManager(), this::applyPacks,
                client.getResourcePackDir(), Text.translatable("resourcePack.title")));
    }

    private void applyPacks(ResourcePackManager manager) {
        client.options.refreshResourcePacks(manager);
        client.setScreen(this);
    }

    /** Opens Iris's shader pack screen (looked up by name so the menu never breaks if Iris changes). */
    private void openShaders() {
        try {
            Class<?> c = Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen");
            client.setScreen((Screen) c.getConstructor(Screen.class).newInstance(this));
        } catch (Throwable t) {
            Toasts.push("Shaders", "Iris is not loaded", Ui.RED);
        }
    }

    @Override
    protected void drawFront(DrawContext ctx, int mx, int my, float delta) {
        float t = age();
        int cx = width / 2;
        SkinCache.ensure();

        // ---- logo ----
        float e0 = Anim.stagger(t, 0f, 0.7f);
        int logoY = (int) (height * 0.13f);
        Ui.shimmer(ctx, textRenderer, "NOVA", cx, logoY - (int) ((1f - e0) * 14), 5f, e0);
        float pulse = 0.6f + 0.4f * (float) Math.sin(t * 2.0);
        int lw = (int) (130 * Anim.stagger(t, 0.15f, 0.6f));
        Ui.hGradient(ctx, cx - lw / 2, logoY + 49, lw, 1, Ui.a(Ui.ACCENT, pulse), Ui.a(Ui.CYAN, pulse));
        float e1 = Anim.stagger(t, 0.2f, 0.6f);
        Ui.big(ctx, textRenderer, "C  L  I  E  N  T", cx, logoY + 56, 1.1f, Ui.CYAN, e1);

        // ---- buttons ----
        for (int i = 0; i < hub.size(); i++) {
            float e = Anim.stagger(t, delays.get(i), 0.45f);
            hub.get(i).draw(ctx, textRenderer, mx, my, e, (int) ((1f - e) * 14));
        }

        // ---- version chip (top left) ----
        float ev = Anim.stagger(t, 0.1f, 0.5f);
        String ver = "v" + NovaClient.VERSION;
        int vw = textRenderer.getWidth(ver) + 14;
        Ui.round(ctx, 10, 10, vw, 16, Ui.a(0xCC0B0B1C, ev));
        Ui.text(ctx, textRenderer, ver, 17, 14, Ui.MUTED, ev);
        if (UpdateChecker.available()) {
            String up = "Update " + UpdateChecker.latest + " ready";
            int uw = textRenderer.getWidth(up) + 14;
            float pu = 0.65f + 0.35f * (float) Math.sin(t * 4);
            Ui.round(ctx, 10 + vw + 6, 10, uw, 16, Ui.a(0xCC0B3B2E, ev));
            Ui.round(ctx, 10 + vw + 5, 9, uw + 2, 18, Ui.a(Ui.GREEN, 0.25f * pu * ev));
            Ui.text(ctx, textRenderer, up, 17 + vw + 6, 14, Ui.GREEN, ev);
        }

        // ---- profile pill (top right) ----
        drawPill(ctx, mx, my, Anim.stagger(t, 0.15f, 0.5f));

        // ---- rotating tips ----
        float phase = t % 5f;
        float ta = Math.min(1f, Math.min(phase / 0.5f, (5f - phase) / 0.5f)) * Anim.stagger(t, 1.0f, 0.5f);
        Ui.centered(ctx, textRenderer, TIPS[(int) (t / 5f) % TIPS.length], cx, height - 20, Ui.MUTED, ta);
    }

    private void drawPill(DrawContext ctx, int mx, int my, float e) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - pillLast) / 1e9f);
        pillLast = now;
        String name = client.getSession().getUsername();
        pillW = Math.max(96, 34 + textRenderer.getWidth(name) + 10);
        pillH = 26;
        pillX = width - pillW - 10;
        pillY = 10;
        pillHover = Anim.approach(pillHover, Ui.inside(mx, my, pillX, pillY, pillW, pillH) ? 1f : 0f, dt, 16f);

        Ui.round(ctx, pillX - 1, pillY - 1, pillW + 2, pillH + 2, Ui.a(Anim.lerpColor(0x40FFFFFF, Ui.CYAN, pillHover), e));
        Ui.round(ctx, pillX, pillY, pillW, pillH, Ui.a(Anim.lerpColor(0xE00B0B1C, 0xE01C1C3A, pillHover), e));
        SkinCache.drawHead(ctx, pillX + 3, pillY + 3, 20, e);
        Ui.text(ctx, textRenderer, name, pillX + 28, pillY + 4, Ui.TEXT, e);
        Ui.text(ctx, textRenderer, "Level " + PlayStats.level(), pillX + 28, pillY + 14, Ui.MUTED, e);
        int bw = (int) ((pillW - 34) * PlayStats.progress());
        Ui.hGradient(ctx, pillX + 28, pillY + 23, bw, 1, Ui.a(Ui.ACCENT, e), Ui.a(Ui.CYAN, e));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (Ui.inside(mx, my, pillX, pillY, pillW, pillH)) {
                click();
                client.setScreen(new ProfileScreen(this));
                return true;
            }
            for (AnimButton b : hub) {
                if (b.hit(mx, my)) {
                    click();
                    b.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}

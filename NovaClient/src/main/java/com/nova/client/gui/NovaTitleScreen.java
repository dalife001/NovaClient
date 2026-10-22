package com.nova.client.gui;

import com.nova.client.compat.Mc;
import com.nova.client.NovaClient;
import com.nova.client.PlayStats;
import com.nova.client.UpdateChecker;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The Nova hub. A quiet left column (logo, two big doors, a few small pills) in front of a slow night sky
 * with a ringed planet. Nothing flashes; everything eases.
 */
public class NovaTitleScreen extends NovaScreen {
    private static final String[] TIPS = {
            "Breathe in. Pick a world.",
            "F6 lets you drift around with Freecam",
            "Hold C to zoom in on something far away",
            "Right Shift opens the module menu",
            "Arrange your HUD so it stays out of your way",
            "Scroll over a HUD element in the editor to resize it",
            "Put your own wallpaper behind everything in Settings",
            "Take a moment. The stars are not going anywhere."
    };

    private final List<AnimButton> hub = new ArrayList<>();
    private final List<Float> delays = new ArrayList<>();
    private float pillHover;
    private long pillLast = System.nanoTime();
    private int pillX, pillY, pillW, pillH;

    // layout
    private int left, colW, logoY, planetX, planetY, planetR;
    private float logoScale;
    private boolean compact;
    private int chipLabelY = -1;

    public NovaTitleScreen() { super(NovaClient.NAME, null); }

    private AnimButton add(AnimButton b, float delay) { hub.add(b); delays.add(delay); return b; }

    @Override
    protected void build() {
        hub.clear();
        delays.clear();
        chipLabelY = -1;

        compact = height < 240;
        left = Math.max(22, (int) (width * 0.09f));
        colW = Math.max(150, Math.min(214, width - left * 2));
        logoScale = compact ? 3f : 4f;
        logoY = Math.max(14, (int) (height * (compact ? 0.07f : 0.11f)));

        int rowH = compact ? 26 : 30;
        int y = logoY + (int) (8 * logoScale) + (compact ? 22 : 38);

        add(new AnimButton(left, y, colW, rowH, "Singleplayer", AnimButton.Style.ROW,
                () -> Mc.setScreen(client, new SelectWorldScreen(this))).sub("Your worlds").icon(6), 0.35f);
        y += rowH + 2;
        add(new AnimButton(left, y, colW, rowH, "Multiplayer", AnimButton.Style.ROW,
                () -> Mc.setScreen(client, new MultiplayerScreen(this))).sub("Servers and friends").icon(7), 0.43f);
        y += rowH + (compact ? 8 : 12);

        String[] names = {"Mods", "Friends", "Skins", "Textures", "Shaders", "Settings"};
        Runnable[] acts = {
                () -> Mc.setScreen(client, new ClickGuiScreen(this)),
                () -> Mc.setScreen(client, new FriendsScreen(this)),
                () -> Mc.setScreen(client, new SkinScreen(this)),
                this::openPacks,
                this::openShaders,
                () -> Mc.setScreen(client, new SettingsScreen(this))};
        int gap = 6, pw = (colW - 2 * gap) / 3, ph = 18;
        for (int i = 0; i < 6; i++) {
            int px = left + (i % 3) * (pw + gap), py = y + (i / 3) * (ph + 5);
            add(new AnimButton(px, py, pw, ph, names[i], AnimButton.Style.PILL, acts[i]), 0.55f + i * 0.05f);
        }
        y += 2 * ph + 5 + (compact ? 8 : 14);

        // Jump back in: your saved servers
        try {
            ServerList sl = new ServerList(client);
            sl.loadFile();
            int n = Math.min(2, sl.size());
            if (n > 0 && y + 28 < height - 24) {
                chipLabelY = y;
                int chipW = (colW - gap * (n - 1)) / n;
                for (int i = 0; i < n; i++) {
                    ServerInfo info = sl.get(i);
                    String label = textRenderer.trimToWidth(info.name, chipW - 14);
                    add(new AnimButton(left + i * (chipW + gap), y + 12, chipW, 16, label, AnimButton.Style.PILL, () ->
                            Mc.connect(this, client, info)), 0.9f + i * 0.05f);
                }
            }
        } catch (Throwable ignored) {}

        add(new AnimButton(width - 112, height - 22, 50, 16, "Options", AnimButton.Style.GHOST,
                () -> Mc.setScreen(client, new OptionsScreen(this, client.options))), 1.0f);
        add(new AnimButton(width - 58, height - 22, 44, 16, "Quit", AnimButton.Style.GHOST, () -> client.scheduleStop()), 1.0f);

        // the planet sits in the free space on the right
        int freeLeft = left + colW + 20;
        int free = width - freeLeft;
        planetR = Math.max(22, Math.min(64, (int) (Math.min(free, height) * 0.22f)));
        planetX = free >= 130 ? freeLeft + free / 2 : (int) (width * 0.80f);
        planetY = (int) (height * 0.44f);
    }

    private void openPacks() { Mc.openPacks(client, this); }

    /** Opens Iris's shader pack screen (looked up by name so the menu never breaks if Iris changes). */
    private void openShaders() {
        try {
            Class<?> c = Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen");
            Mc.setScreen(client, (Screen) c.getConstructor(Screen.class).newInstance(this));
        } catch (Throwable t) {
            Toasts.push("Shaders", "Iris is not loaded", Ui.RED);
        }
    }

    private String greeting() {
        int h = LocalTime.now().getHour();
        String g = h < 5 ? "Still up" : h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
        return g + ", " + client.getSession().getUsername();
    }

    /** The planet lives in the background layer so it is always behind the menu. */
    @Override
    protected void drawBack(Gfx ctx, int mx, int my, float delta) {
        float t = age();
        float e = Anim.stagger(t, 0.2f, 1.6f);
        float bob = (float) Math.sin(Anim.seconds() * 0.35f) * 3f;
        int px = planetX - (int) (Ui.parallaxX() * 14);
        int py = planetY + (int) bob - (int) ((1f - e) * 18) - (int) (Ui.parallaxY() * 8);
        Cosmos.planet(ctx, px, py, planetR, e);
    }

    @Override
    protected void drawFront(Gfx ctx, int mx, int my, float delta) {
        float t = age();
        SkinCache.ensure();

        // ---- logo ----
        float e0 = Anim.stagger(t, 0f, 1.0f);
        float lx = left + 1;
        float ly = logoY - (1f - e0) * 10f;
        Ui.flow(ctx, textRenderer, "nova", lx, ly, logoScale, 3f, e0, true);
        float lw = Ui.flowWidth(textRenderer, "nova", logoScale, 3f);
        Ui.sparkle(ctx, (int) (lx + lw + 2), (int) (logoY + 2 + Math.sin(Anim.seconds() * 1.1) * 1.5),
                1 + (int) (1.6f * Anim.breathe(4f, 0f)), Ui.a(Theme.light(Ui.CYAN), e0));
        float e1 = Anim.stagger(t, 0.25f, 0.9f);
        int tagY = logoY + (int) (8 * logoScale) + 5;
        Ui.text(ctx, textRenderer, "a quieter way to play", left + 2, tagY, Ui.MUTED, e1);
        if (!compact) Ui.text(ctx, textRenderer, greeting(), left + 2, tagY + 13, Theme.light(Ui.ACCENT), e1 * 0.85f);

        // ---- menu ----
        for (int i = 0; i < hub.size(); i++) {
            float e = Anim.stagger(t, delays.get(i), 0.7f);
            hub.get(i).draw(ctx, textRenderer, mx, my, e, (int) ((1f - e) * 8));
        }
        if (chipLabelY >= 0) Ui.text(ctx, textRenderer, "Jump back in", left + 2, chipLabelY, Ui.MUTED, Anim.stagger(t, 0.85f, 0.6f));

        // ---- version chip (top left) ----
        float ev = Anim.stagger(t, 0.1f, 0.7f);
        String ver = "v" + NovaClient.VERSION;
        int vw = textRenderer.getWidth(ver) + 14;
        Ui.rrect(ctx, 10, 10, vw, 16, 8, Ui.glass(0.07f * ev));
        Ui.rim(ctx, 10, 10, vw, 16, 8, Ui.glass(0.12f * ev));
        Ui.text(ctx, textRenderer, ver, 17, 14, Ui.MUTED, ev);
        if (UpdateChecker.available()) {
            String up = "Update " + UpdateChecker.latest + " ready";
            int uw = textRenderer.getWidth(up) + 14;
            float pu = 0.7f + 0.3f * Anim.breathe(3f, 0f);
            Ui.rrect(ctx, 10 + vw + 5, 9, uw + 2, 18, 9, Ui.a(Ui.GREEN, 0.16f * pu * ev));
            Ui.rrect(ctx, 10 + vw + 6, 10, uw, 16, 8, Ui.a(0xFF0F2F27, 0.85f * ev));
            Ui.text(ctx, textRenderer, up, 17 + vw + 6, 14, Ui.GREEN, ev);
        }

        // ---- profile pill (top right) ----
        drawPill(ctx, mx, my, Anim.stagger(t, 0.15f, 0.7f));

        // ---- rotating tips ----
        float phase = t % 7f;
        float ta = Math.min(1f, Math.min(phase / 0.9f, (7f - phase) / 0.9f)) * Anim.stagger(t, 1.0f, 0.6f);
        Ui.text(ctx, textRenderer, TIPS[(int) (t / 7f) % TIPS.length], left + 2, height - 20, Ui.MUTED, ta * 0.9f);
    }

    private void drawPill(Gfx ctx, int mx, int my, float e) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - pillLast) / 1e9f);
        pillLast = now;
        String name = client.getSession().getUsername();
        pillW = Math.max(96, 34 + textRenderer.getWidth(name) + 10);
        pillH = 26;
        pillX = width - pillW - 10;
        pillY = 10;
        pillHover = Anim.approach(pillHover, Ui.inside(mx, my, pillX, pillY, pillW, pillH) ? 1f : 0f, dt, 9f);
        float h = Anim.easeInOutSine(pillHover);

        if (h > 0.02f) Ui.rrect(ctx, pillX - 2, pillY - 2, pillW + 4, pillH + 4, 11, Ui.a(Ui.ACCENT, 0.12f * h * e));
        Ui.rrect(ctx, pillX, pillY, pillW, pillH, 9, Ui.glass((0.07f + 0.06f * h) * e));
        Ui.rim(ctx, pillX, pillY, pillW, pillH, 9, Ui.a(Anim.lerpColor(0x33FFFFFF, Ui.ACCENT, h * 0.8f), e));
        SkinCache.drawHead(ctx, pillX + 3, pillY + 3, 20, e);
        Ui.text(ctx, textRenderer, name, pillX + 28, pillY + 4, Ui.TEXT, e);
        Ui.text(ctx, textRenderer, "Level " + PlayStats.level(), pillX + 28, pillY + 14, Ui.MUTED, e);
        Ui.bar(ctx, pillX + 28, pillY + 22, pillW - 36, 2, PlayStats.progress(), e);
    }

    @Override
    protected boolean nClick(double mx, double my, int button) {
        if (button == 0) {
            if (Ui.inside(mx, my, pillX, pillY, pillW, pillH)) {
                click();
                Mc.setScreen(client, new ProfileScreen(this));
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
        return super.nClick(mx, my, button);
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}

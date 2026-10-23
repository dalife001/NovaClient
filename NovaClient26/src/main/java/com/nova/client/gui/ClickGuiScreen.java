package com.nova.client.gui;

import com.nova.client.compat.Mc;
import com.nova.client.NovaClient;
import com.nova.client.module.HudModule;
import com.nova.client.module.Module;
import com.nova.client.module.NumberSetting;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/** The module menu: search, tabs, animated cards and a settings drawer. Opens with Right Shift. */
public class ClickGuiScreen extends NovaScreen {
    private static final String[] TABS = {"All", "HUD", "Utility", "Camera", "Visual"};
    private static final int CH = 46, GAP = 8, DW = 160;

    private int tab;
    private String search = "";
    private EditBox field;
    private float scroll, scrollTarget, tabX = -1, tabW = 20, drawer;
    private Module selected;
    private final Map<Module, Float> on = new HashMap<>(), hov = new HashMap<>();
    private NumberSetting dragging;
    private int dragX, dragW;
    private AnimButton editorBtn, toggleBtn, resetBtn;
    private long last = System.nanoTime();
    private float dt;
    private int px, py, pw, ph, gx, gy, gw, gh, cols, cw;

    public ClickGuiScreen(Screen parent) { super("Modules", parent); }

    @Override
    protected void build() {
        field = new EditBox(font, 0, 0, 120, 16, Mc.text("Search"));
        hint(field, "Search modules");
        field.setValue(search);
        field.setResponder(s -> { search = s; scrollTarget = 0; });
        addField(field);

        editorBtn = button(0, 0, 86, 16, "HUD Editor", AnimButton.Style.SECONDARY, () -> {
            if (minecraft.player != null) Mc.setScreen(minecraft, new HudEditorScreen(this));
        });
        editorBtn.enabled = minecraft.player != null;

        toggleBtn = new AnimButton(0, 0, DW - 20, 20, "", AnimButton.Style.SECONDARY, () -> { if (selected != null) toggle(selected); });
        resetBtn = new AnimButton(0, 0, DW - 20, 18, "Reset position", AnimButton.Style.SECONDARY, () -> {
            if (selected instanceof HudModule) { ((HudModule) selected).resetPosition(); NovaClient.modules.saveStates(); }
        });
    }

    private void toggle(Module m) {
        m.toggle();
        NovaClient.modules.saveStates();
        Toasts.push(m.name, m.isEnabled() ? "Enabled" : "Disabled", m.isEnabled() ? Ui.GREEN : Ui.MUTED);
    }

    private List<Module> filtered() {
        List<Module> out = new ArrayList<>();
        String q = search.trim().toLowerCase();
        for (Module m : NovaClient.modules.all()) {
            if (tab != 0 && !m.category().equals(TABS[tab])) continue;
            if (!q.isEmpty() && !m.name.toLowerCase().contains(q) && !m.description.toLowerCase().contains(q)) continue;
            out.add(m);
        }
        return out;
    }

    private void layout() {
        pw = Math.min(width - 24, 600);
        ph = Math.min(height - 24, 330);
        px = (width - pw) / 2;
        int slide = Math.round((1f - Anim.easeOutCubic(age() / 0.35f)) * 14);
        py = (height - ph) / 2 + slide;
        gx = px + 14;
        gy = py + 62;
        gh = py + ph - 14 - gy;
        gw = pw - 28 - (selected != null ? DW + 10 : 0);
        cols = Math.max(1, (gw + GAP) / (104 + GAP));
        cw = (gw - (cols - 1) * GAP) / cols;
    }

    private int maxScroll() {
        int rows = (filtered().size() + cols - 1) / cols;
        return Math.max(0, rows * (CH + GAP) - GAP - gh);
    }

    private int tabX(int i) {
        int x = px + 14;
        for (int k = 0; k < i; k++) x += font.width(TABS[k]) + 22;
        return x;
    }

    @Override
    protected void nBackground(Gfx ctx, int mx, int my, float delta) {
        if (parent != null) Ui.background(ctx, width, height);
        else ctx.fillGradient(0, 0, width, height, 0xB0070716, 0xD0120E2C); // see-through when in-game
        drawBack(ctx, mx, my, delta);
    }

    @Override
    protected void drawBack(Gfx ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        layout();
        scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll()));
        scroll = Anim.approach(scroll, scrollTarget, dt, 14f);
        drawer = Anim.approach(drawer, selected != null ? 1f : 0f, dt, 12f);

        Ui.panel(ctx, px, py, pw, ph, 9, 0xF00E0C24, 1f);
        ctx.fillGradient(px + 9, py + 1, px + pw - 9, py + 32, Ui.a(Ui.ACCENT, 0.16f), Ui.a(Ui.ACCENT, 0f));

        editorBtn.x = px + pw - 12 - editorBtn.w;
        editorBtn.y = py + 7;
        place(field, editorBtn.x - 8 - 120, py + 7);
    }

    @Override
    protected void drawFront(Gfx ctx, int mx, int my, float delta) {
        Ui.text(ctx, font, "Modules", px + 24, py + 11, Ui.TEXT, 1f);
        Ui.sparkle(ctx, px + 16, py + 15, 2, Ui.a(Theme.light(Ui.CYAN), 0.6f + 0.4f * Anim.breathe(4f, 0f)));

        // ---- tabs: a soft pill glides under the active one ----
        for (int i = 0; i < TABS.length; i++) {
            int x = tabX(i), w = font.width(TABS[i]);
            if (i == tab) {
                if (tabX < 0) { tabX = x; tabW = w; }
                tabX = Anim.approach(tabX, x, dt, 12f);
                tabW = Anim.approach(tabW, w, dt, 12f);
            }
        }
        if (tabX >= 0) {
            Ui.rrect(ctx, (int) tabX - 7, py + 35, (int) tabW + 14, 17, 8, Ui.a(Ui.ACCENT, 0.20f));
            Ui.rim(ctx, (int) tabX - 7, py + 35, (int) tabW + 14, 17, 8, Ui.a(Ui.ACCENT, 0.45f));
        }
        for (int i = 0; i < TABS.length; i++) {
            int x = tabX(i), w = font.width(TABS[i]);
            boolean active = i == tab;
            boolean h = Ui.inside(mx, my, x - 6, py + 36, w + 12, 18);
            Ui.text(ctx, font, TABS[i], x, py + 40, active ? Ui.TEXT : h ? 0xFFD8D4EE : Ui.MUTED, 1f);
        }
        ctx.fill(px + 14, py + 56, px + pw - 14, py + 57, Ui.glass(0.07f));

        // ---- cards ----
        List<Module> list = filtered();
        ctx.scissorOn(gx - 2, gy, gx + gw + 2, gy + gh);
        if (list.isEmpty()) Ui.centered(ctx, font, "No modules found", gx + gw / 2, gy + 30, Ui.MUTED, 1f);
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            int cx = gx + (i % cols) * (cw + GAP);
            int cy = gy + (i / cols) * (CH + GAP) - (int) scroll;
            if (cy + CH < gy || cy > gy + gh) continue;

            boolean inGrid = Ui.inside(mx, my, gx, gy, gw, gh);
            float hv = Anim.approach(hov.getOrDefault(m, 0f), inGrid && Ui.inside(mx, my, cx, cy, cw, CH) ? 1f : 0f, dt, 14f);
            hov.put(m, hv);
            float onv = Anim.approach(on.getOrDefault(m, m.isEnabled() ? 1f : 0f), m.isEnabled() ? 1f : 0f, dt, 12f);
            on.put(m, onv);

            float e = Anim.stagger(age(), 0.05f + i * 0.03f, 0.4f);
            int y = cy + (int) ((1f - e) * 10) - Math.round(hv);
            boolean sel = m == selected;
            Ui.rrect(ctx, cx, y, cw, CH, 7, Ui.a(Anim.lerpColor(Ui.glass(0.05f), Ui.a(Ui.ACCENT, 0.16f), onv), e));
            if (hv > 0.02f) Ui.rrect(ctx, cx, y, cw, CH, 7, Ui.glass(0.05f * hv * e));
            int rimc = Anim.lerpColor(Anim.lerpColor(Ui.glass(0.10f), Ui.a(Ui.ACCENT, 0.55f), onv), Ui.a(Ui.CYAN, 0.8f), Math.max(hv * 0.5f, sel ? 0.8f : 0f));
            Ui.rim(ctx, cx, y, cw, CH, 7, Ui.a(rimc, e));
            if (onv > 0.02f) Ui.rrect(ctx, cx + 4, y + 9, 2, CH - 18, 1, Ui.a(Ui.ACCENT, onv * e));

            Ui.text(ctx, font, m.name, cx + 12, y + 8, Ui.TEXT, e);
            Ui.text(ctx, font, font.plainSubstrByWidth(m.description, cw - 22), cx + 12, y + 20, Ui.MUTED, e);

            // soft pill switch
            int sx = cx + cw - 36, sy = y + CH - 15;
            Ui.rrect(ctx, sx, sy, 26, 10, 5, Ui.a(Anim.lerpColor(0xFF3B3858, Ui.ACCENT, onv), e));
            int kx = (int) Anim.lerp(sx + 1, sx + 17, onv);
            Ui.rrect(ctx, kx, sy + 1, 8, 8, 4, Ui.a(0xFFFFFFFF, e));

            if (!m.settings.isEmpty()) {
                boolean gh2 = Ui.inside(mx, my, cx + cw - 18, y + 4, 16, 16);
                int dc = Ui.a(gh2 || sel ? Ui.CYAN : Ui.MUTED, e);
                for (int d = 0; d < 3; d++) ctx.fill(cx + cw - 12, y + 7 + d * 4, cx + cw - 10, y + 9 + d * 4, dc);
            }
        }
        ctx.scissorOff();

        int ms = maxScroll();
        if (ms > 0) {
            int bh = Math.max(16, gh * gh / (gh + ms));
            int by = gy + (int) ((gh - bh) * (scroll / ms));
            Ui.rrect(ctx, gx + gw + 2, by, 3, bh, 1, Ui.glass(0.28f));
        }

        drawDrawer(ctx, mx, my);
    }

    private void drawDrawer(Gfx ctx, int mx, int my) {
        if (selected == null || drawer < 0.03f) return;
        float a = drawer;
        int dx = px + pw - 14 - DW + (int) ((1f - a) * 24), dy = gy;
        Module m = selected;

        Ui.rrect(ctx, dx, dy, DW, gh, 8, Ui.a(0xFF15122F, a));
        Ui.rim(ctx, dx, dy, DW, gh, 8, Ui.glass(0.14f * a));
        Ui.text(ctx, font, m.name, dx + 10, dy + 10, Ui.TEXT, a);
        Ui.text(ctx, font, m.category(), dx + DW - 10 - font.width(m.category()), dy + 10, Ui.ACCENT, a);

        toggleBtn.x = dx + 10;
        toggleBtn.y = dy + 26;
        toggleBtn.label = m.isEnabled() ? "Enabled" : "Disabled";
        toggleBtn.draw(ctx, font, mx, my, a, 0);
        if (m.isEnabled()) Ui.rrect(ctx, toggleBtn.x + 8, toggleBtn.y + 17, toggleBtn.w - 16, 1, 0, Ui.a(Ui.GREEN, a * 0.8f));

        int sy = dy + 58;
        for (NumberSetting s : m.settings) {
            String val = s.step >= 1 ? String.valueOf((int) s.get()) : String.format("%.2f", s.get());
            Ui.text(ctx, font, s.name, dx + 10, sy, Ui.MUTED, a);
            Ui.text(ctx, font, val, dx + DW - 10 - font.width(val), sy, Ui.TEXT, a);
            int tx = dx + 10, tw = DW - 20, ty = sy + 14;
            int fw = (int) (tw * s.fraction());
            Ui.bar(ctx, tx, ty, tw, 6, (float) s.fraction(), a);
            Ui.rrect(ctx, tx + fw - 4, ty - 1, 8, 8, 4, Ui.a(0xFFFFFFFF, a));
            sy += 34;
        }
        if (m instanceof HudModule) {
            resetBtn.x = dx + 10;
            resetBtn.y = dy + gh - 28;
            resetBtn.draw(ctx, font, mx, my, a, 0);
            Ui.text(ctx, font, "Drag it in the HUD Editor", dx + 10, dy + gh - 42, Ui.MUTED, a * 0.8f);
        }
    }

    // ---------------- input ----------------
    @Override
    protected boolean nClick(double mx, double my, int button) {
        if (button == 0) {
            layout();
            // tabs
            for (int i = 0; i < TABS.length; i++) {
                if (Ui.inside(mx, my, tabX(i) - 6, py + 36, font.width(TABS[i]) + 12, 18)) {
                    tab = i; scrollTarget = 0; click();
                    return true;
                }
            }
            // drawer controls
            if (selected != null && drawer > 0.5f) {
                if (toggleBtn.hit(mx, my)) { click(); toggleBtn.action.run(); return true; }
                if (selected instanceof HudModule && resetBtn.hit(mx, my)) { click(); resetBtn.action.run(); return true; }
                int dx = px + pw - 14 - DW, sy = gy + 58;
                for (NumberSetting s : selected.settings) {
                    if (Ui.inside(mx, my, dx + 6, sy + 10, DW - 12, 16)) {
                        dragging = s; dragX = dx + 10; dragW = DW - 20;
                        s.setFraction((mx - dragX) / dragW);
                        return true;
                    }
                    sy += 34;
                }
            }
            // cards
            if (Ui.inside(mx, my, gx, gy, gw, gh)) {
                List<Module> list = filtered();
                for (int i = 0; i < list.size(); i++) {
                    int cx = gx + (i % cols) * (cw + GAP);
                    int cy = gy + (i / cols) * (CH + GAP) - (int) scroll;
                    if (!Ui.inside(mx, my, cx, cy, cw, CH)) continue;
                    Module m = list.get(i);
                    click();
                    if (!m.settings.isEmpty() && Ui.inside(mx, my, cx + cw - 18, cy + 4, 16, 16)) {
                        selected = (selected == m) ? null : m; // gear: open/close settings
                    } else {
                        toggle(m);
                    }
                    return true;
                }
            }
        }
        return super.nClick(mx, my, button);
    }

    @Override
    protected boolean nDrag(double mx, double my, int button, double dX, double dY) {
        if (dragging != null) {
            dragging.setFraction((mx - dragX) / dragW);
            return true;
        }
        return super.nDrag(mx, my, button, dX, dY);
    }

    @Override
    protected boolean nRelease(double mx, double my, int button) {
        if (dragging != null) {
            dragging = null;
            NovaClient.modules.saveStates();
            return true;
        }
        return super.nRelease(mx, my, button);
    }

    @Override
    protected boolean nScroll(double mx, double my, double h, double v) {
        if (Ui.inside(mx, my, gx, gy, gw, gh)) {
            scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - (float) v * 30f));
            return true;
        }
        return super.nScroll(mx, my, h, v);
    }

    @Override
    protected boolean nKey(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_RSHIFT && !field.isFocused()) { nClose(); return true; }
        return super.nKey(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean pausesGame() { return false; }
}

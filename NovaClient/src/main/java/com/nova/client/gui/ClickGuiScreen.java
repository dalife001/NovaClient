package com.nova.client.gui;

import com.nova.client.NovaClient;
import com.nova.client.module.HudModule;
import com.nova.client.module.Module;
import com.nova.client.module.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The module menu: search, tabs, animated cards and a settings drawer. Opens with Right Shift. */
public class ClickGuiScreen extends NovaScreen {
    private static final String[] TABS = {"All", "HUD", "Utility", "Camera", "Visual"};
    private static final int CH = 46, GAP = 8, DW = 160;

    private int tab;
    private String search = "";
    private TextFieldWidget field;
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
        field = new TextFieldWidget(textRenderer, 0, 0, 120, 16, Text.literal("Search"));
        field.setPlaceholder(Text.literal("Search modules"));
        field.setText(search);
        field.setChangedListener(s -> { search = s; scrollTarget = 0; });
        addDrawableChild(field);

        editorBtn = button(0, 0, 86, 16, "HUD Editor", AnimButton.Style.SECONDARY, () -> {
            if (client.player != null) client.setScreen(new HudEditorScreen(this));
        });
        editorBtn.enabled = client.player != null;

        toggleBtn = new AnimButton(0, 0, DW - 20, 20, "", AnimButton.Style.SECONDARY, () -> { if (selected != null) toggle(selected); });
        resetBtn = new AnimButton(0, 0, DW - 20, 18, "Reset position", AnimButton.Style.SECONDARY, () -> {
            if (selected instanceof HudModule h) { h.resetPosition(); NovaClient.modules.saveStates(); }
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
        for (int k = 0; k < i; k++) x += textRenderer.getWidth(TABS[k]) + 22;
        return x;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        if (parent != null) Ui.background(ctx, width, height);
        else ctx.fill(0, 0, width, height, 0xB0000010); // see-through when in-game
        drawBack(ctx, mx, my, delta);
    }

    @Override
    protected void drawBack(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        layout();
        scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll()));
        scroll = Anim.approach(scroll, scrollTarget, dt, 14f);
        drawer = Anim.approach(drawer, selected != null ? 1f : 0f, dt, 12f);

        Ui.round(ctx, px - 1, py - 1, pw + 2, ph + 2, 0x44FFFFFF);
        Ui.round(ctx, px, py, pw, ph, 0xF00B0B1E);
        ctx.fillGradient(px + 1, py + 1, px + pw - 1, py + 30, Ui.ACCENT, Ui.ACCENT_DARK);

        editorBtn.x = px + pw - 12 - editorBtn.w;
        editorBtn.y = py + 7;
        field.setPosition(editorBtn.x - 8 - 120, py + 7);
    }

    @Override
    protected void drawFront(DrawContext ctx, int mx, int my, float delta) {
        Ui.text(ctx, textRenderer, "Modules", px + 14, py + 11, Ui.TEXT, 1f);

        // ---- tabs ----
        for (int i = 0; i < TABS.length; i++) {
            int x = tabX(i), w = textRenderer.getWidth(TABS[i]);
            boolean active = i == tab;
            boolean h = Ui.inside(mx, my, x - 6, py + 36, w + 12, 18);
            Ui.text(ctx, textRenderer, TABS[i], x, py + 40, active ? Ui.TEXT : h ? 0xFFD1D5DB : Ui.MUTED, 1f);
            if (active) {
                if (tabX < 0) { tabX = x; tabW = w; }
                tabX = Anim.approach(tabX, x, dt, 16f);
                tabW = Anim.approach(tabW, w, dt, 16f);
            }
        }
        if (tabX >= 0) Ui.hGradient(ctx, (int) tabX, py + 52, (int) tabW, 2, Ui.ACCENT, Ui.CYAN);
        ctx.fill(px + 14, py + 54, px + pw - 14, py + 55, 0x22FFFFFF);

        // ---- cards ----
        List<Module> list = filtered();
        ctx.enableScissor(gx - 2, gy, gx + gw + 2, gy + gh);
        if (list.isEmpty()) Ui.centered(ctx, textRenderer, "No modules found", gx + gw / 2, gy + 30, Ui.MUTED, 1f);
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
            int border = Anim.lerpColor(Anim.lerpColor(0x30FFFFFF, Ui.a(Ui.ACCENT, 0.67f), onv), Ui.CYAN, Math.max(hv * 0.6f, sel ? 0.9f : 0f));
            Ui.round(ctx, cx - 1, y - 1, cw + 2, CH + 2, Ui.a(border, e));
            Ui.round(ctx, cx, y, cw, CH, Ui.a(Anim.lerpColor(0xFF141430, 0xFF1E1E44, hv), e));
            if (onv > 0.02f) ctx.fillGradient(cx, y + 3, cx + 2, y + CH - 3, Ui.a(Ui.ACCENT, onv * e), Ui.a(Ui.CYAN, onv * e));

            Ui.text(ctx, textRenderer, m.name, cx + 10, y + 8, Ui.TEXT, e);
            Ui.text(ctx, textRenderer, textRenderer.trimToWidth(m.description, cw - 20), cx + 10, y + 20, Ui.MUTED, e);

            int sx = cx + cw - 36, sy = y + CH - 15;
            Ui.round(ctx, sx, sy, 26, 10, Ui.a(Anim.lerpColor(0xFF4B4B63, Ui.GREEN, onv), e));
            int kx = (int) Anim.lerp(sx + 1, sx + 17, onv);
            ctx.fill(kx, sy + 1, kx + 8, sy + 9, Ui.a(0xFFFFFFFF, e));

            if (!m.settings.isEmpty()) {
                boolean gh2 = Ui.inside(mx, my, cx + cw - 18, y + 4, 16, 16);
                int dc = Ui.a(gh2 || sel ? Ui.CYAN : Ui.MUTED, e);
                for (int d = 0; d < 3; d++) ctx.fill(cx + cw - 11, y + 7 + d * 4, cx + cw - 9, y + 9 + d * 4, dc);
            }
        }
        ctx.disableScissor();

        int ms = maxScroll();
        if (ms > 0) {
            int bh = Math.max(16, gh * gh / (gh + ms));
            int by = gy + (int) ((gh - bh) * (scroll / ms));
            Ui.round(ctx, gx + gw + 2, by, 3, bh, 0x66FFFFFF);
        }

        drawDrawer(ctx, mx, my);
    }

    private void drawDrawer(DrawContext ctx, int mx, int my) {
        if (selected == null || drawer < 0.03f) return;
        float a = drawer;
        int dx = px + pw - 14 - DW + (int) ((1f - a) * 24), dy = gy;
        Module m = selected;

        Ui.round(ctx, dx - 1, dy - 1, DW + 2, gh + 2, Ui.a(0x44FFFFFF, a));
        Ui.round(ctx, dx, dy, DW, gh, Ui.a(0xFF10102A, a));
        Ui.text(ctx, textRenderer, m.name, dx + 10, dy + 10, Ui.TEXT, a);
        Ui.text(ctx, textRenderer, m.category(), dx + DW - 10 - textRenderer.getWidth(m.category()), dy + 10, Ui.ACCENT, a);

        toggleBtn.x = dx + 10;
        toggleBtn.y = dy + 26;
        toggleBtn.label = m.isEnabled() ? "Enabled" : "Disabled";
        toggleBtn.draw(ctx, textRenderer, mx, my, a, 0);
        if (m.isEnabled()) Ui.hGradient(ctx, toggleBtn.x + 1, toggleBtn.y + 18, toggleBtn.w - 2, 1, Ui.a(Ui.GREEN, a), Ui.a(Ui.CYAN, a));

        int sy = dy + 58;
        for (NumberSetting s : m.settings) {
            String val = s.step >= 1 ? String.valueOf((int) s.get()) : String.format("%.2f", s.get());
            Ui.text(ctx, textRenderer, s.name, dx + 10, sy, Ui.MUTED, a);
            Ui.text(ctx, textRenderer, val, dx + DW - 10 - textRenderer.getWidth(val), sy, Ui.TEXT, a);
            int tx = dx + 10, tw = DW - 20, ty = sy + 14;
            Ui.round(ctx, tx, ty, tw, 6, Ui.a(0xFF222244, a));
            int fw = (int) (tw * s.fraction());
            Ui.hGradient(ctx, tx, ty, Math.max(3, fw), 6, Ui.a(Ui.ACCENT, a), Ui.a(Ui.CYAN, a));
            Ui.round(ctx, tx + fw - 3, ty - 2, 6, 10, Ui.a(0xFFFFFFFF, a));
            sy += 34;
        }
        if (m instanceof HudModule) {
            resetBtn.x = dx + 10;
            resetBtn.y = dy + gh - 28;
            resetBtn.draw(ctx, textRenderer, mx, my, a, 0);
            Ui.text(ctx, textRenderer, "Drag it in the HUD Editor", dx + 10, dy + gh - 42, Ui.MUTED, a * 0.8f);
        }
    }

    // ---------------- input ----------------
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            layout();
            // tabs
            for (int i = 0; i < TABS.length; i++) {
                if (Ui.inside(mx, my, tabX(i) - 6, py + 36, textRenderer.getWidth(TABS[i]) + 12, 18)) {
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
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dX, double dY) {
        if (dragging != null) {
            dragging.setFraction((mx - dragX) / dragW);
            return true;
        }
        return super.mouseDragged(mx, my, button, dX, dY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null) {
            dragging = null;
            NovaClient.modules.saveStates();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (Ui.inside(mx, my, gx, gy, gw, gh)) {
            scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - (float) v * 30f));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT && !field.isFocused()) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }
}

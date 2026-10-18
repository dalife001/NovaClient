package com.nova.client.gui;

import com.nova.client.NovaClient;
import com.nova.client.module.HudModule;
import com.nova.client.module.Module;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;

/** Drag HUD elements where you want them. Scroll over one to resize. Edges and the centre snap. */
public class HudEditorScreen extends NovaScreen {
    private HudModule drag;
    private int gx, gy;

    public HudEditorScreen(Screen parent) { super("HUD Editor", parent); }

    private List<HudModule> huds() {
        List<HudModule> out = new ArrayList<>();
        for (Module m : NovaClient.modules.all()) if (m instanceof HudModule h && h.isEnabled()) out.add(h);
        return out;
    }

    @Override
    protected void build() {
        button(width / 2 - 92, height - 30, 88, 18, "Done", true, this::close);
        button(width / 2 + 4, height - 30, 88, 18, "Reset all", false, () -> {
            for (Module m : NovaClient.modules.all()) if (m instanceof HudModule h) h.resetPosition();
            NovaClient.modules.saveStates();
            Toasts.push("HUD Editor", "Positions reset", Ui.CYAN);
        });
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0xB0050510);
        for (int x = 10; x < width; x += 20)
            for (int y = 10; y < height; y += 20) ctx.fill(x, y, x + 1, y + 1, 0x22FFFFFF);
    }

    @Override
    protected void drawFront(DrawContext ctx, int mx, int my, float delta) {
        Ui.centered(ctx, textRenderer, "HUD Editor", width / 2, 14, Ui.TEXT, 1f);
        Ui.centered(ctx, textRenderer, "Drag to move  \u2022  Scroll to resize  \u2022  Turn elements on in the module menu",
                width / 2, 28, Ui.MUTED, 1f);

        if (drag != null) { // centre guides while dragging
            ctx.fill(width / 2, 0, width / 2 + 1, height, 0x4422D3EE);
            ctx.fill(0, height / 2, width, height / 2 + 1, 0x4422D3EE);
        }
        for (HudModule m : huds()) {
            int[] b = m.bounds(client);
            m.renderAt(ctx, client, b[0], b[1]);
            boolean hover = Ui.inside(mx, my, b[0], b[1], b[2], b[3]);
            int c = m == drag ? Ui.CYAN : hover ? Ui.ACCENT : 0x66FFFFFF;
            Ui.outline(ctx, b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, c);
            if (hover || m == drag) Ui.text(ctx, textRenderer, m.name, b[0], b[1] - 12, Ui.TEXT, 1f);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            List<HudModule> l = huds();
            for (int i = l.size() - 1; i >= 0; i--) {
                int[] b = l.get(i).bounds(client);
                if (Ui.inside(mx, my, b[0], b[1], b[2], b[3])) {
                    drag = l.get(i);
                    gx = (int) mx - b[0];
                    gy = (int) my - b[1];
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dX, double dY) {
        if (drag != null) {
            int[] b = drag.bounds(client);
            int x = (int) mx - gx, y = (int) my - gy;
            int sw = width, sh = height, snap = 6;
            if (Math.abs(x) < snap) x = 0;
            if (Math.abs(x + b[2] - sw) < snap) x = sw - b[2];
            if (Math.abs(x + b[2] / 2 - sw / 2) < snap) x = sw / 2 - b[2] / 2;
            if (Math.abs(y) < snap) y = 0;
            if (Math.abs(y + b[3] - sh) < snap) y = sh - b[3];
            if (Math.abs(y + b[3] / 2 - sh / 2) < snap) y = sh / 2 - b[3] / 2;
            drag.moveTo(client, x, y, b[2], b[3]);
            return true;
        }
        return super.mouseDragged(mx, my, button, dX, dY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (drag != null) {
            drag = null;
            NovaClient.modules.saveStates();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        for (HudModule m : huds()) {
            int[] b = m.bounds(client);
            if (Ui.inside(mx, my, b[0], b[1], b[2], b[3])) {
                m.scale.set(m.scale.get() + Math.signum(v) * 0.05);
                NovaClient.modules.saveStates();
                return true;
            }
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public void close() {
        NovaClient.modules.saveStates();
        super.close();
    }

    @Override
    public boolean shouldPause() { return false; }
}

package com.nova.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.nova.client.compat.Mc;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Base class: animated background, animated buttons, fade-in, toasts.
 *
 * Minecraft changed the Screen methods several times (drawing in 1.20, scrolling in 1.20.2, mouse and key events in 1.21.9,
 * close/onClose in 1.18). This class is the only place that knows; the Nova screens override the n... hooks instead:
 * nBackground, nRender, nClick, nDrag, nRelease, nScroll, nKey, nClose, pausesGame.
 */
public abstract class NovaScreen extends Screen {
    protected final Screen parent;
    protected final List<AnimButton> buttons = new ArrayList<AnimButton>();
    private final long opened = System.nanoTime();

    protected NovaScreen(String title, Screen parent) {
        super(Mc.text(title));
        this.parent = parent;
    }

    /** Seconds since this screen opened. */
    protected float age() { return (System.nanoTime() - opened) / 1_000_000_000f; }

    @Override
    protected void init() {
        buttons.clear();
        build();
    }

    /** Throws away the widgets and builds them again (Screen.clearAndInit only exists in newer versions). */
    protected void rebuild() {
        rebuildWidgets();
    }

    /** Create buttons / text fields here. */
    protected abstract void build();
    /** Cards and panels, drawn underneath vanilla widgets like text fields. */
    protected void drawBack(Gfx ctx, int mx, int my, float delta) {}
    /** Text and extras, drawn on top. */
    protected void drawFront(Gfx ctx, int mx, int my, float delta) {}

    protected AnimButton button(int x, int y, int w, int h, String label, boolean primary, Runnable action) {
        AnimButton b = new AnimButton(x, y, w, h, label, primary ? AnimButton.Style.PRIMARY : AnimButton.Style.SECONDARY, action);
        buttons.add(b);
        return b;
    }

    protected AnimButton button(int x, int y, int w, int h, String label, AnimButton.Style style, Runnable action) {
        AnimButton b = new AnimButton(x, y, w, h, label, style, action);
        buttons.add(b);
        return b;
    }

    /** Adds a vanilla text box (1.16.5 calls this addButton, later versions addDrawableChild). */
    protected void addField(EditBox f) {
        addRenderableWidget(f);
    }

    /** Moves a text box (the x / y fields became setters in 1.19.3). */
    protected static void place(EditBox f, int x, int y) {
        f.setX(x);
        f.setY(y);
    }

    /** Grey hint text while the box is empty (the widget only supports this from 1.19.3). */
    protected static void hint(EditBox f, String s) {
        f.setHint(Mc.text(s));
    }

    protected void click() {
        Mc.clickSound(minecraft);
    }

    // ------------------------------------------------------------------ hooks for the Nova screens
    protected void nBackground(Gfx ctx, int mx, int my, float delta) {
        Ui.background(ctx, width, height);
        drawBack(ctx, mx, my, delta);
    }

    protected void nRender(Gfx ctx, int mx, int my, float delta) {
        drawFront(ctx, mx, my, delta);
        for (AnimButton b : buttons) b.draw(ctx, font, mx, my, 1f, 0);
        Toasts.render(ctx, font, width, height);
        float f = 1f - Anim.smooth(age() / 0.55f); // slow fade in from deep night blue
        if (f > 0.02f) ctx.fill(0, 0, width, height, (((int) (f * 255f)) << 24) | 0x070716);
    }

    protected boolean nClick(double mx, double my, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            for (AnimButton b : buttons) {
                if (b.hit(mx, my)) {
                    click();
                    b.action.run();
                    return true;
                }
            }
        }
        return vClick(mx, my, button);
    }

    protected boolean nDrag(double mx, double my, int button, double dx, double dy) { return vDrag(mx, my, button, dx, dy); }

    protected boolean nRelease(double mx, double my, int button) { return vRelease(mx, my, button); }

    protected boolean nScroll(double mx, double my, double h, double v) { return vScroll(mx, my, h, v); }

    protected boolean nKey(int keyCode, int scanCode, int modifiers) { return vKey(keyCode, scanCode, modifiers); }

    protected void nClose() { Mc.setScreen(minecraft, parent); }

    protected boolean pausesGame() { return true; }

    // ------------------------------------------------------------------ bridge to the real Screen methods
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        nBackground(new Gfx(ctx), mx, my, delta);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        super.extractRenderState(ctx, mx, my, delta); // calls extractBackground, then the vanilla widgets
        nRender(new Gfx(ctx), mx, my, delta);
    }

    @Override
    public void onClose() { nClose(); }

    @Override
    public boolean isPauseScreen() { return pausesGame(); }

    private static int clickLog = 0;
    private MouseButtonEvent lastClick;
    private boolean lastDouble;
    private KeyEvent lastKey;

    @Override
    public boolean mouseClicked(MouseButtonEvent c, boolean doubled) {
        lastClick = c; lastDouble = doubled;
        if (clickLog++ < 12) {
            System.out.println("[Nova] click x=" + c.x() + " y=" + c.y() + " button=" + c.button()
                    + " screen=" + width + "x" + height + " nova-buttons=" + buttons.size() + " in " + getClass().getSimpleName());
        }
        return nClick(c.x(), c.y(), c.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent c, double dx, double dy) {
        lastClick = c;
        return nDrag(c.x(), c.y(), c.button(), dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent c) {
        lastClick = c;
        return nRelease(c.x(), c.y(), c.button());
    }

    @Override
    public boolean keyPressed(KeyEvent k) {
        lastKey = k;
        return nKey(k.input(), 0, 0);
    }

    protected boolean vClick(double mx, double my, int button) { return lastClick != null && super.mouseClicked(lastClick, lastDouble); }

    protected boolean vDrag(double mx, double my, int button, double dx, double dy) { return lastClick != null && super.mouseDragged(lastClick, dx, dy); }

    protected boolean vRelease(double mx, double my, int button) { return lastClick != null && super.mouseReleased(lastClick); }

    protected boolean vKey(int keyCode, int scanCode, int modifiers) { return lastKey != null && super.keyPressed(lastKey); }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) { return nScroll(mx, my, h, v); }

    protected boolean vScroll(double mx, double my, double h, double v) { return super.mouseScrolled(mx, my, h, v); }
}

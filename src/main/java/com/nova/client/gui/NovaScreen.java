package com.nova.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Base class: animated background, animated buttons, fade-in, toasts. */
public abstract class NovaScreen extends Screen {
    protected final Screen parent;
    protected final List<AnimButton> buttons = new ArrayList<>();
    private final long opened = System.nanoTime();

    protected NovaScreen(String title, Screen parent) {
        super(Text.literal(title));
        this.parent = parent;
    }

    /** Seconds since this screen opened. */
    protected float age() { return (System.nanoTime() - opened) / 1_000_000_000f; }

    @Override
    protected void init() {
        buttons.clear();
        build();
    }

    /** Create buttons / text fields here. */
    protected abstract void build();
    /** Cards and panels, drawn underneath vanilla widgets like text fields. */
    protected void drawBack(DrawContext ctx, int mx, int my, float delta) {}
    /** Text and extras, drawn on top. */
    protected void drawFront(DrawContext ctx, int mx, int my, float delta) {}

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

    protected void click() {
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        Ui.background(ctx, width, height);
        drawBack(ctx, mx, my, delta);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta); // background + vanilla widgets
        drawFront(ctx, mx, my, delta);
        for (AnimButton b : buttons) b.draw(ctx, textRenderer, mx, my, 1f, 0);
        Toasts.render(ctx, textRenderer, width, height);
        float f = 1f - Anim.clamp01(age() / 0.22f); // quick fade in from black
        if (f > 0.02f) ctx.fill(0, 0, width, height, ((int) (f * 255f)) << 24);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (AnimButton b : buttons) {
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
    public void close() { client.setScreen(parent); }
}

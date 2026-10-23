package com.nova.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The one drawing surface every Nova screen and HUD element paints on.
 *
 * Minecraft changed how the GUI is drawn several times: up to 1.19.4 everything goes through a MatrixStack and
 * DrawableHelper, from 1.20 through DrawContext, and from 1.21.6 the matrix stack is 2D. This class hides all of that,
 * so the rest of the client only ever calls fill / fillGradient / text / push / translate / scale.
 */
public final class Gfx {
    private final GuiGraphics dc;

    public Gfx(GuiGraphics dc) { this.dc = dc; }

    // ---------- shapes ----------
    public void fill(int x1, int y1, int x2, int y2, int color) {
        dc.fill(x1, y1, x2, y2, color);
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        dc.fillGradient(x1, y1, x2, y2, top, bottom);
    }

    // ---------- text ----------
    public void drawTextWithShadow(Font tr, String s, int x, int y, int color) {
        dc.drawString(tr, s, x, y, color);
    }

    public void drawCenteredTextWithShadow(Font tr, String s, int cx, int y, int color) {
        dc.drawCenteredString(tr, s, cx, y, color);
    }

    // ---------- transforms ----------
    public void push() {
        dc.pose().pushMatrix();
    }

    public void pop() {
        dc.pose().popMatrix();
    }

    public void translate(float x, float y) {
        dc.pose().translate(x, y);
    }

    public void scale(float x, float y) {
        dc.pose().scale(x, y);
    }

    // ---------- clipping ----------
    public void scissorOn(int x1, int y1, int x2, int y2) {
        dc.enableScissor(x1, y1, x2, y2);
    }

    public void scissorOff() {
        dc.disableScissor();
    }

    // ---------- items and pictures ----------
    public void drawItem(ItemStack stack, int x, int y) {
        dc.renderItem(stack, x, y);
    }

    /** Draws a whole texture stretched to w x h. Only available from 1.20 (older versions keep the built-in sky). */
    public static final boolean CAN_DRAW_TEXTURE = MC_CAN_TEXTURE();

    private static boolean MC_CAN_TEXTURE() {
        return true;
    }

    public void drawTexture(Identifier id, int x, int y, int w, int h) {
        dc.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, w, h);
    }
}

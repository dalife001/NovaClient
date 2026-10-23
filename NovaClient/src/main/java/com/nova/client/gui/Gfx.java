package com.nova.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
//#if MC >= 2000
import net.minecraft.client.gui.DrawContext;
//#else
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.opengl.GL11;
//#endif

/**
 * The one drawing surface every Nova screen and HUD element paints on.
 *
 * Minecraft changed how the GUI is drawn several times: up to 1.19.4 everything goes through a MatrixStack and
 * DrawableHelper, from 1.20 through DrawContext, and from 1.21.6 the matrix stack is 2D. This class hides all of that,
 * so the rest of the client only ever calls fill / fillGradient / text / push / translate / scale.
 */
//#if MC >= 2000
public final class Gfx {
//#else
public final class Gfx extends DrawableHelper {
//#endif
    //#if MC >= 2000
    private final DrawContext dc;

    public Gfx(DrawContext dc) { this.dc = dc; }
    //#else
    private final MatrixStack ms;
    // The item renderer of these versions ignores our MatrixStack, so we remember the transform ourselves.
    private float tx, ty, sc = 1f;
    private final float[] saved = new float[3 * 32];
    private int sp;

    public Gfx(MatrixStack ms) { this.ms = ms; }
    //#endif

    // ---------- shapes ----------
    public void fill(int x1, int y1, int x2, int y2, int color) {
        //#if MC >= 2000
        dc.fill(x1, y1, x2, y2, color);
        //#else
        fill(ms, x1, y1, x2, y2, color);
        //#endif
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        //#if MC >= 2000
        dc.fillGradient(x1, y1, x2, y2, top, bottom);
        //#else
        fillGradient(ms, x1, y1, x2, y2, top, bottom);
        //#endif
    }

    // ---------- text ----------
    public void drawTextWithShadow(TextRenderer tr, String s, int x, int y, int color) {
        //#if MC >= 2000
        dc.drawTextWithShadow(tr, s, x, y, color);
        //#else
        tr.drawWithShadow(ms, s, (float) x, (float) y, color);
        //#endif
    }

    public void drawCenteredTextWithShadow(TextRenderer tr, String s, int cx, int y, int color) {
        //#if MC >= 2000
        dc.drawCenteredTextWithShadow(tr, s, cx, y, color);
        //#else
        tr.drawWithShadow(ms, s, cx - tr.getWidth(s) / 2f, (float) y, color);
        //#endif
    }

    // ---------- transforms ----------
    public void push() {
        //#if MC >= 2106
        dc.getMatrices().pushMatrix();
        //#elseif MC >= 2000
        dc.getMatrices().push();
        //#else
        ms.push();
        if (sp + 3 <= saved.length) { saved[sp++] = tx; saved[sp++] = ty; saved[sp++] = sc; }
        //#endif
    }

    public void pop() {
        //#if MC >= 2106
        dc.getMatrices().popMatrix();
        //#elseif MC >= 2000
        dc.getMatrices().pop();
        //#else
        ms.pop();
        if (sp >= 3) { sc = saved[--sp]; ty = saved[--sp]; tx = saved[--sp]; }
        //#endif
    }

    public void translate(float x, float y) {
        //#if MC >= 2106
        dc.getMatrices().translate(x, y);
        //#elseif MC >= 2000
        dc.getMatrices().translate(x, y, 0f);
        //#else
        ms.translate((double) x, (double) y, 0.0);
        tx += x * sc;
        ty += y * sc;
        //#endif
    }

    public void scale(float x, float y) {
        //#if MC >= 2106
        dc.getMatrices().scale(x, y);
        //#elseif MC >= 2000
        dc.getMatrices().scale(x, y, 1f);
        //#else
        ms.scale(x, y, 1f);
        sc *= x;
        //#endif
    }

    // ---------- clipping ----------
    public void scissorOn(int x1, int y1, int x2, int y2) {
        //#if MC >= 2000
        dc.enableScissor(x1, y1, x2, y2);
        //#else
        net.minecraft.client.util.Window w = MinecraftClient.getInstance().getWindow();
        double f = w.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x1 * f), (int) (w.getFramebufferHeight() - y2 * f), Math.max(0, (int) ((x2 - x1) * f)), Math.max(0, (int) ((y2 - y1) * f)));
        //#endif
    }

    public void scissorOff() {
        //#if MC >= 2000
        dc.disableScissor();
        //#else
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        //#endif
    }

    // ---------- items and pictures ----------
    public void drawItem(ItemStack stack, int x, int y) {
        //#if MC >= 2000
        dc.drawItem(stack, x, y);
        //#elseif MC >= 1904
        MinecraftClient.getInstance().getItemRenderer().renderGuiItemIcon(ms, stack, Math.round(tx + x * sc), Math.round(ty + y * sc));
        //#else
        MinecraftClient.getInstance().getItemRenderer().renderGuiItemIcon(stack, Math.round(tx + x * sc), Math.round(ty + y * sc));
        //#endif
    }

    /** Draws a whole texture stretched to w x h. Only available from 1.20 (older versions keep the built-in sky). */
    public static final boolean CAN_DRAW_TEXTURE = MC_CAN_TEXTURE();

    private static boolean MC_CAN_TEXTURE() {
        //#if MC >= 2000
        return true;
        //#else
        return false;
        //#endif
    }

    public void drawTexture(Identifier id, int x, int y, int w, int h) {
        //#if MC >= 2106
        dc.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, w, h);
        //#elseif MC >= 2102
        dc.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, id, x, y, 0f, 0f, w, h, w, h);
        //#elseif MC >= 2000
        dc.drawTexture(id, x, y, 0f, 0f, w, h, w, h);
        //#endif
    }
}

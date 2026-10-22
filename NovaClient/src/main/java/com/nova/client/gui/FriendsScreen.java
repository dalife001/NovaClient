package com.nova.client.gui;

import com.nova.client.compat.Mc;
import com.nova.client.config.Config;
import com.nova.client.friends.FriendsManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;

import java.util.List;

public class FriendsScreen extends NovaScreen {
    private static final int W = 320, ROW = 22, VISIBLE = 6;
    private int scroll;
    private String input = "";
    private String status = "";
    private int statusColor = Ui.MUTED;

    public FriendsScreen(Screen parent) { super("Friends", parent); }

    private int px() { return width / 2 - W / 2; }
    private int py() { return Math.max(14, height / 2 - 125); }

    @Override
    protected void build() {
        int x = px() + 16, w = W - 32;
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, py() + 34, w - 70, 20, Mc.text("Username"));
        field.setMaxLength(16);
        hint(field, "Add friend by username");
        field.setText(input);
        field.setChangedListener(s -> input = s);
        addField(field);

        button(x + w - 65, py() + 34, 65, 20, "Add", true, () -> {
            String name = input;
            status = "Looking up " + name + "...";
            statusColor = Ui.MUTED;
            FriendsManager.add(name, msg -> {
                status = msg;
                statusColor = msg.startsWith("Added") ? Ui.GREEN : Ui.RED;
                if (msg.startsWith("Added")) input = "";
                rebuild();
            });
        });

        List<Config.Friend> list = FriendsManager.list();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - VISIBLE)));
        for (int i = 0; i < VISIBLE && scroll + i < list.size(); i++) {
            Config.Friend f = list.get(scroll + i);
            int ry = py() + 82 + i * ROW;
            button(px() + W - 40, ry + 1, 24, 18, "x", false, () -> {
                FriendsManager.remove(f);
                rebuild();
            });
        }
        button(width / 2 - 50, py() + 82 + VISIBLE * ROW + 30, 100, 22, "Back", false, this::nClose);
    }

    @Override
    protected void drawBack(Gfx ctx, int mx, int my, float delta) {
        Ui.card(ctx, px(), py(), W, 82 + VISIBLE * ROW + 22);
    }

    @Override
    protected void drawFront(Gfx ctx, int mx, int my, float delta) {
        ctx.drawTextWithShadow(textRenderer, "Friends", px() + 16, py() + 12, Ui.ACCENT);
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(status, W - 100), px() + 70, py() + 12, statusColor);

        List<Config.Friend> list = FriendsManager.list();
        if (list.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No friends yet. Add someone above!", width / 2, py() + 110, Ui.MUTED);
        }
        for (int i = 0; i < VISIBLE && scroll + i < list.size(); i++) {
            Config.Friend f = list.get(scroll + i);
            int ry = py() + 82 + i * ROW;
            Ui.rrect(ctx, px() + 12, ry, W - 24, ROW - 2, 5, Ui.glass(0.07f));
            boolean here = FriendsManager.isHere(f);
            ctx.fill(px() + 20, ry + 7, px() + 26, ry + 13, here ? Ui.GREEN : 0xFF6E6A8E);
            ctx.drawTextWithShadow(textRenderer, f.name, px() + 34, ry + 6, Ui.TEXT);
            ctx.drawTextWithShadow(textRenderer, here ? "in your game" : "offline / elsewhere",
                    px() + 130, ry + 6, here ? Ui.GREEN : Ui.MUTED);
        }
        if (list.size() > VISIBLE) {
            ctx.drawCenteredTextWithShadow(textRenderer, "scroll for more (" + list.size() + " total)",
                    width / 2, py() + 82 + VISIBLE * ROW + 4, Ui.MUTED);
        }
    }

    @Override
    protected boolean nScroll(double mx, double my, double horizontal, double vertical) {
        scroll -= (int) Math.signum(vertical);
        rebuild();
        return true;
    }
}

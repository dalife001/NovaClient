package com.nova.client.gui;

import com.nova.client.NovaClient;
import com.nova.client.compat.Mc;
import com.nova.client.module.ChristmasModule;
import com.nova.client.module.NumberSetting;
import net.minecraft.client.gui.screen.Screen;

/** Dedicated Christmas atmosphere controls. */
public final class ChristmasSettingsScreen extends NovaScreen {
    private static final int W = 430, ROW = 25;
    private final ChristmasModule effect;
    private final NumberSetting[] settings;
    private final String[] labels = {"Snow amount", "Snow speed", "Flake size", "Glow", "Quality", "Festive lights", "Sky mode", "Aurora", "Day snow haze"};

    public ChristmasSettingsScreen(Screen parent) {
        super("Christmas mode", parent);
        effect = NovaClient.modules.christmas;
        settings = new NumberSetting[]{effect.density, effect.speed, effect.size, effect.glow, effect.quality, effect.lights, effect.mode, effect.aurora, effect.snowVeil};
    }

    private int x() { return (width - W) / 2; }
    private int y() { return Math.max(8, (height - 340) / 2); }

    @Override protected void build() {
        int left = x() + W - 92;
        for (int i = 0; i < settings.length; i++) {
            final NumberSetting setting = settings[i];
            int rowY = y() + 57 + i * ROW;
            button(left, rowY, 28, 19, "-", false, () -> adjust(setting, -1));
            button(left + 58, rowY, 28, 19, "+", true, () -> adjust(setting, 1));
        }
        button(width / 2 - 48, y() + 294, 96, 22, "Back", false, this::nClose);
    }

    private void adjust(NumberSetting setting, int direction) {
        setting.set(setting.get() + setting.step * direction);
        NovaClient.modules.saveStates();
    }

    private String value(NumberSetting setting) {
        if (setting == effect.mode) {
            int mode = (int) setting.get();
            return mode == 1 ? "Day" : mode == 2 ? "Night" : "Auto";
        }
        if (setting == effect.quality) {
            int quality = (int) setting.get();
            return quality == 2 ? "Medium" : quality == 3 ? "High" : "Low";
        }
        return setting.step >= 1 ? Integer.toString((int) setting.get()) : String.format("%.1f", setting.get());
    }

    @Override protected void drawBack(Gfx ctx, int mx, int my, float delta) {
        Ui.card(ctx, x(), y(), W, 326);
        Ui.headerBand(ctx, x(), y(), W);
    }

    @Override protected void drawFront(Gfx ctx, int mx, int my, float delta) {
        int left = x() + 20;
        Ui.text(ctx, textRenderer, "Christmas mode", left, y() + 12, Ui.TEXT, 1f);
        Ui.text(ctx, textRenderer, "Iris-free snowy day / aurora night atmosphere", left, y() + 33, Ui.MUTED, 1f);
        for (int i = 0; i < settings.length; i++) {
            int rowY = y() + 62 + i * ROW;
            NumberSetting setting = settings[i];
            Ui.text(ctx, textRenderer, labels[i], left, rowY + 5, Ui.TEXT, 1f);
            int trackX = x() + 158, trackW = 126;
            Ui.bar(ctx, trackX, rowY + 7, trackW, 6, (float) setting.fraction(), 0.9f);
            Ui.text(ctx, textRenderer, value(setting), x() + 290, rowY + 5, Ui.CYAN, 1f);
        }
        Ui.text(ctx, textRenderer, "Low quality uses fewer screen-space layers.", left, y() + 290, Ui.MUTED, 1f);
    }

    @Override protected boolean pausesGame() { return false; }
}

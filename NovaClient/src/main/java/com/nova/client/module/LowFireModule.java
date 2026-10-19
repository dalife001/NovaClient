package com.nova.client.module;

/** Lowers the fire overlay so you can still see while burning. */
public class LowFireModule extends Module {
    public final NumberSetting drop = new NumberSetting("Drop", 0.5, 2.0, 1.0, 0.1);

    public LowFireModule() {
        super("Low Fire", "Fire overlay no longer blocks your view", false);
        settings.add(drop);
    }

    @Override public String category() { return "Visual"; }
}

package com.nova.client.module;

import com.nova.client.gui.Gfx;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

public abstract class Module {
    public final String name;
    public final String description;
    public final List<NumberSetting> settings = new ArrayList<>();
    private boolean enabled;

    protected Module(String name, String description, boolean enabledByDefault) {
        this.name = name;
        this.description = description;
        this.enabled = enabledByDefault;
    }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean value) {
        if (value == enabled) return;
        enabled = value;
        if (value) onEnable(); else onDisable();
    }

    public void toggle() { setEnabled(!enabled); }

    /** Tab the module appears under in the menu: "HUD", "Utility" or "Camera". */
    public String category() { return "Utility"; }

    /** Whether the on/off state is saved between launches. */
    public boolean persistent() { return true; }

    protected void onEnable() {}
    protected void onDisable() {}
    public void onTick(Minecraft mc) {}
    public void onRender(Gfx ctx, Minecraft mc) {}

    /** Used only to load the saved state without firing hooks. */
    void restore(boolean value) { this.enabled = value; }
}

package com.nova.client;

import com.nova.client.account.AccountManager;
import com.nova.client.config.Config;
import com.nova.client.gui.ClickGuiScreen;
import com.nova.client.gui.HudEditorScreen;
import com.nova.client.gui.Toasts;
import com.nova.client.gui.Ui;
import com.nova.client.gui.NovaTitleScreen;
import com.nova.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class NovaClient implements ClientModInitializer {
    public static final String NAME = "Nova Client";
    public static String VERSION = "dev";
    private static boolean updateToasted;
    public static ModuleManager modules;

    private static KeyBinding openGuiKey;
    private static KeyBinding freecamKey;
    public static KeyBinding zoomKey;

    @Override
    public void onInitializeClient() {
        FabricLoader.getInstance().getModContainer("novaclient")
                .ifPresent(c -> VERSION = c.getMetadata().getVersion().getFriendlyString());
        Config.load();
        PlayStats.onStart();
        modules = new ModuleManager();

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.novaclient.open_gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.novaclient"));
        freecamKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.novaclient.freecam", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F6, "category.novaclient"));

        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.novaclient.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, "category.novaclient"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.currentScreen instanceof TitleScreen) {
                client.setScreen(new NovaTitleScreen());
            }
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new ClickGuiScreen(null));
            }
            while (freecamKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null) {
                    modules.freecam.toggle();
                    Toasts.push("Freecam", modules.freecam.isEnabled() ? "On - WASD to fly" : "Off",
                            modules.freecam.isEnabled() ? Ui.CYAN : Ui.MUTED);
                }
            }
            PlayStats.tick(client);
            if (!updateToasted && UpdateChecker.available()) {
                updateToasted = true;
                Toasts.push("Update available", "Nova Client " + UpdateChecker.latest, Ui.CYAN);
            }
            // leaving the world turns freecam off
            if (client.player == null && modules.freecam.isEnabled()) modules.freecam.setEnabled(false);
            modules.onTick(client);
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && !mc.options.hudHidden && !(mc.currentScreen instanceof HudEditorScreen)) {
                modules.onRender(context, mc);
            }
            if (mc.currentScreen == null) {
                Toasts.render(context, mc.textRenderer, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
            }
        });

        AccountManager.tryAutoLogin();
        UpdateChecker.checkOnce();
    }
}

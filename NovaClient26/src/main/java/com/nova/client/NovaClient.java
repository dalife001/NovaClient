package com.nova.client;

import com.nova.client.compat.Mc;
import com.nova.client.account.AccountManager;
import com.nova.client.config.Config;
import com.nova.client.gui.ClickGuiScreen;
import com.nova.client.gui.HudEditorScreen;
import com.nova.client.gui.Gfx;
import com.nova.client.gui.Toasts;
import com.nova.client.gui.Ui;
import com.nova.client.gui.NovaTitleScreen;
import com.nova.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;

public class NovaClient implements ClientModInitializer {
    public static final String NAME = "Nova Client";
    public static String VERSION = "dev";
    private static boolean updateToasted;
    public static ModuleManager modules;

    private static KeyMapping openGuiKey;
    public static KeyMapping zoomKey;

    @Override
    public void onInitializeClient() {
        FabricLoader.getInstance().getModContainer("novaclient")
                .ifPresent(c -> VERSION = c.getMetadata().getVersion().getFriendlyString());
        Config.load();
        PlayStats.onStart();
        modules = new ModuleManager();

        openGuiKey = KeyMappingHelper.registerKeyMapping(Mc.newKey("key.novaclient.open_gui", InputConstants.KEY_RSHIFT));
        zoomKey = KeyMappingHelper.registerKeyMapping(Mc.newKey("key.novaclient.zoom", InputConstants.KEY_C));

        // put the player's normal brightness back when the game closes
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> modules.fullbright.release());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (Mc.screen(client) instanceof TitleScreen) {
                Mc.setScreen(client, new NovaTitleScreen());
            }
            while (openGuiKey.consumeClick()) {
                if (Mc.screen(client) == null) Mc.setScreen(client, new ClickGuiScreen(null));
            }
            PlayStats.tick(client);
            if (!updateToasted && UpdateChecker.available()) {
                updateToasted = true;
                Toasts.push("Update available", "Nova Client " + UpdateChecker.latest, Ui.CYAN);
            }
            modules.onTick(client);
        });

        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("novaclient", "hud"),
                (context, tickCounter) -> renderHud(new Gfx(context)));

        AccountManager.tryAutoLogin();
        UpdateChecker.checkOnce();
    }

    private static void renderHud(Gfx context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !Mc.hideGui(mc) && !(Mc.screen(mc) instanceof HudEditorScreen)) {
            modules.onRender(context, mc);
        }
        if (Mc.screen(mc) == null) {
            Toasts.render(context, mc.font, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }
    }
}

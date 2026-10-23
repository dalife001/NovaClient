package com.nova.client.compat;

import com.nova.client.mixin.MinecraftClientAccessor;
import java.net.URI;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Everything in Nova that has to ask Minecraft something whose name or shape changed between 1.16.5 and 1.21.x.
 * The rest of the client calls these methods and never has to care which Minecraft it runs on.
 */
public final class Mc {
    private Mc() {}

    // ---------------------------------------------------------------- text / screens
    public static Component text(String s) {
        return Component.literal(s);
    }

    public static void setScreen(Minecraft c, Screen s) {
        c.setScreen(s);
    }

    /** Opens the vanilla resource pack screen; on versions where that is awkward it opens the options screen instead. */
    public static void openPacks(Minecraft c, Screen parent) {
        c.setScreen(new net.minecraft.client.gui.screens.packs.PackSelectionScreen(c.getResourcePackRepository(),
                manager -> { c.options.updateResourcePacks(manager); c.setScreen(parent); },
                c.getResourcePackDirectory(), Component.translatable("resourcePack.title")));
    }

    /** The vanilla options screen. Its constructor grew an extra flag in 26.x, so it is looked up instead of called directly. */
    public static Screen optionsScreen(Screen parent, Minecraft c) {
        try {
            for (java.lang.reflect.Constructor<?> k : net.minecraft.client.gui.screens.options.OptionsScreen.class.getConstructors()) {
                Class<?>[] p = k.getParameterTypes();
                if (p.length < 2 || p[0] != Screen.class || p[1] != Options.class) continue;
                Object[] args = new Object[p.length];
                args[0] = parent;
                args[1] = c.options;
                for (int i = 2; i < p.length; i++) args[i] = p[i] == boolean.class ? Boolean.TRUE : null;
                return (Screen) k.newInstance(args);
            }
        } catch (Throwable ignored) {}
        return parent;
    }

    public static void connect(Screen parent, Minecraft c, ServerData info) {
        ServerAddress addr = ServerAddress.parseString(info.ip);
        ConnectScreen.startConnecting(parent, c, addr, info, false, null);
    }

    public static void openUri(URI uri) {
        net.minecraft.util.Util.getPlatform().openUri(uri);
    }

    /** The soft "click" every vanilla button makes (the method was renamed from master to ui in 1.21.11). */
    public static void clickSound(Minecraft c) {
        c.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    public static void clipboard(Minecraft c, String s) { c.keyboardHandler.setClipboard(s); }

    // ---------------------------------------------------------------- game state
    public static int fps(Minecraft c) {
        return c.getFps();
    }

    public static boolean firstPerson(Minecraft c) {
        return c.options.getCameraType().isFirstPerson();
    }

    public static float yaw(Entity e) {
        return e.getYRot();
    }

    /** index 0 = boots ... 3 = helmet */
    public static ItemStack armor(LivingEntity e, int i) {
        EquipmentSlot[] slots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
        return e.getItemBySlot(slots[i]);
    }

    public static String effectName(MobEffectInstance e) {
        return e.getEffect().value().getDisplayName().getString();
    }

    // ---------------------------------------------------------------- key bindings (renamed in 1.18.2)
    public static KeyMapping forward(Options o) {
        return o.keyUp;
    }

    public static KeyMapping back(Options o) {
        return o.keyDown;
    }

    public static KeyMapping left(Options o) {
        return o.keyLeft;
    }

    public static KeyMapping right(Options o) {
        return o.keyRight;
    }

    public static KeyMapping jump(Options o) {
        return o.keyJump;
    }

    public static KeyMapping attack(Options o) {
        return o.keyAttack;
    }

    public static KeyMapping use(Options o) {
        return o.keyUse;
    }

    private static KeyMapping.Category NOVA_CATEGORY;

    /** A new key binding in Nova's own category. */
    public static KeyMapping newKey(String id, int glfwKey) {
        // Category.create throws if the same id is registered twice, so every key shares one category.
        if (NOVA_CATEGORY == null) NOVA_CATEGORY = KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("novaclient", "main"));
        return new KeyMapping(id, glfwKey, NOVA_CATEGORY);
    }

    // ---------------------------------------------------------------- account
    /** True when the game is running with a real (Microsoft) account rather than an offline name. */
    public static boolean online(Minecraft c) {
        String t = c.getUser().getAccessToken();
        return t != null && t.length() > 16;
    }

    public static Object currentSession(Minecraft c) { return c.getUser(); }

    public static void setSession(Minecraft c, Object session) {
        ((MinecraftClientAccessor) c).novaSetSession((User) session);
    }

    public static Object newSession(String name, UUID uuid, String token) {
        return new User(name, uuid, token, java.util.Optional.empty(), java.util.Optional.empty());
    }
}

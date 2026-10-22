package com.nova.client.compat;

import com.nova.client.mixin.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
//#if MC >= 2002
import net.minecraft.client.session.Session;
//#else
import net.minecraft.client.util.Session;
//#endif
//#if MC >= 2003
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
//#else
import net.minecraft.client.gui.screen.ConnectScreen;
//#endif
//#if MC < 1900
import net.minecraft.text.LiteralText;
//#endif

import java.net.URI;
import java.util.UUID;

/**
 * Everything in Nova that has to ask Minecraft something whose name or shape changed between 1.16.5 and 1.21.x.
 * The rest of the client calls these methods and never has to care which Minecraft it runs on.
 */
public final class Mc {
    private Mc() {}

    // ---------------------------------------------------------------- text / screens
    public static Text text(String s) {
        //#if MC >= 1900
        return Text.literal(s);
        //#else
        return new LiteralText(s);
        //#endif
    }

    public static void setScreen(MinecraftClient c, Screen s) {
        //#if MC >= 1700
        c.setScreen(s);
        //#else
        c.openScreen(s);
        //#endif
    }

    /** Opens the vanilla resource pack screen; on versions where that is awkward it opens the options screen instead. */
    public static void openPacks(MinecraftClient c, Screen parent) {
        //#if MC >= 1904
        c.setScreen(new net.minecraft.client.gui.screen.pack.PackScreen(c.getResourcePackManager(),
                manager -> { c.options.refreshResourcePacks(manager); c.setScreen(parent); },
                c.getResourcePackDir(), Text.translatable("resourcePack.title")));
        //#else
        setScreen(c, new net.minecraft.client.gui.screen.option.OptionsScreen(parent, c.options));
        //#endif
    }

    public static void connect(Screen parent, MinecraftClient c, ServerInfo info) {
        ServerAddress addr = ServerAddress.parse(info.address);
        //#if MC >= 2005
        ConnectScreen.connect(parent, c, addr, info, false, null);
        //#elseif MC >= 2000
        ConnectScreen.connect(parent, c, addr, info, false);
        //#elseif MC >= 1700
        ConnectScreen.connect(parent, c, addr, info);
        //#else
        c.openScreen(new ConnectScreen(parent, c, addr.getAddress(), addr.getPort()));
        //#endif
    }

    public static void openUri(URI uri) {
        net.minecraft.util.Util.getOperatingSystem().open(uri);
    }

    /** The soft "click" every vanilla button makes (the method was renamed from master to ui in 1.21.11). */
    public static void clickSound(MinecraftClient c) {
        //#if MC >= 2111
        c.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.ui(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0f));
        //#else
        c.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0f));
        //#endif
    }

    public static void clipboard(MinecraftClient c, String s) { c.keyboard.setClipboard(s); }

    // ---------------------------------------------------------------- game state
    public static int fps(MinecraftClient c) {
        //#if MC >= 1903
        return c.getCurrentFps();
        //#else
        String d = ((MinecraftClientAccessor) c).novaFpsString(); // starts with "123 fps"
        int n = 0;
        for (int i = 0; d != null && i < d.length() && Character.isDigit(d.charAt(i)); i++) n = n * 10 + (d.charAt(i) - '0');
        return n;
        //#endif
    }

    public static boolean firstPerson(MinecraftClient c) {
        return c.options.getPerspective().isFirstPerson();
    }

    public static float yaw(Entity e) {
        //#if MC >= 1700
        return e.getYaw();
        //#else
        return e.yaw;
        //#endif
    }

    /** index 0 = boots ... 3 = helmet */
    public static ItemStack armor(LivingEntity e, int i) {
        EquipmentSlot[] slots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
        return e.getEquippedStack(slots[i]);
    }

    public static String effectName(StatusEffectInstance e) {
        //#if MC >= 2005
        return e.getEffectType().value().getName().getString();
        //#else
        return e.getEffectType().getName().getString();
        //#endif
    }

    // ---------------------------------------------------------------- key bindings (renamed in 1.18.2)
    public static KeyBinding forward(GameOptions o) {
        //#if MC >= 1802
        return o.forwardKey;
        //#else
        return o.keyForward;
        //#endif
    }

    public static KeyBinding back(GameOptions o) {
        //#if MC >= 1802
        return o.backKey;
        //#else
        return o.keyBack;
        //#endif
    }

    public static KeyBinding left(GameOptions o) {
        //#if MC >= 1802
        return o.leftKey;
        //#else
        return o.keyLeft;
        //#endif
    }

    public static KeyBinding right(GameOptions o) {
        //#if MC >= 1802
        return o.rightKey;
        //#else
        return o.keyRight;
        //#endif
    }

    public static KeyBinding jump(GameOptions o) {
        //#if MC >= 1802
        return o.jumpKey;
        //#else
        return o.keyJump;
        //#endif
    }

    public static KeyBinding attack(GameOptions o) {
        //#if MC >= 1802
        return o.attackKey;
        //#else
        return o.keyAttack;
        //#endif
    }

    public static KeyBinding use(GameOptions o) {
        //#if MC >= 1802
        return o.useKey;
        //#else
        return o.keyUse;
        //#endif
    }

    /** A new key binding in Nova's own category. */
    public static KeyBinding newKey(String id, int glfwKey) {
        //#if MC >= 2109
        return new KeyBinding(id, glfwKey, KeyBinding.Category.create(net.minecraft.util.Identifier.of("novaclient", "main")));
        //#else
        return new KeyBinding(id, InputUtil.Type.KEYSYM, glfwKey, "category.novaclient");
        //#endif
    }

    // ---------------------------------------------------------------- account
    /** True when the game is running with a real (Microsoft) account rather than an offline name. */
    public static boolean online(MinecraftClient c) {
        String t = c.getSession().getAccessToken();
        return t != null && t.length() > 16;
    }

    public static Object currentSession(MinecraftClient c) { return c.getSession(); }

    public static void setSession(MinecraftClient c, Object session) {
        ((MinecraftClientAccessor) c).novaSetSession((Session) session);
    }

    public static Object newSession(String name, UUID uuid, String token) {
        //#if MC >= 2109
        return new Session(name, uuid, token, java.util.Optional.empty(), java.util.Optional.empty());
        //#elseif MC >= 2002
        return new Session(name, uuid, token, java.util.Optional.empty(), java.util.Optional.empty(), Session.AccountType.MSA);
        //#elseif MC >= 1800
        return new Session(name, uuid.toString().replace("-", ""), token, java.util.Optional.empty(), java.util.Optional.empty(), Session.AccountType.MSA);
        //#else
        return new Session(name, uuid.toString().replace("-", ""), token, "msa");
        //#endif
    }
}

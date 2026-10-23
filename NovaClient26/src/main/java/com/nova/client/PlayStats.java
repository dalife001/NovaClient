package com.nova.client;

import com.nova.client.config.Config;
import net.minecraft.client.Minecraft;

/** Tracks time played and turns it into a fun "Nova level". */
public final class PlayStats {
    private static int ticks, saveTimer;

    private PlayStats() {}

    public static void onStart() {
        Config c = Config.get();
        c.launches++;
        if (c.firstLaunch == 0) c.firstLaunch = System.currentTimeMillis();
        Config.save();
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null) return;
        if (++ticks >= 20) {
            ticks = 0;
            Config.get().playSeconds++;
            if (++saveTimer >= 60) { saveTimer = 0; Config.save(); }
        }
    }

    public static long minutes() { return Config.get().playSeconds / 60; }

    /** Level N needs 5*(N-1)^2 minutes. */
    public static int level() { return 1 + (int) Math.sqrt(minutes() / 5.0); }

    /** 0..1 progress towards the next level. */
    public static float progress() {
        int l = level();
        double lo = 5.0 * (l - 1) * (l - 1), hi = 5.0 * l * l;
        return (float) Math.max(0, Math.min(1, (Config.get().playSeconds / 60.0 - lo) / (hi - lo)));
    }

    public static long minutesToNext() {
        int l = level();
        return Math.max(0, (long) Math.ceil(5.0 * l * l - Config.get().playSeconds / 60.0));
    }

    public static String format(long seconds) {
        long h = seconds / 3600, m = (seconds / 60) % 60;
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }
}

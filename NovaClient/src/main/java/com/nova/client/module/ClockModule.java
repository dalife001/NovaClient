package com.nova.client.module;

import net.minecraft.client.MinecraftClient;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ClockModule extends SimpleHudModule {
    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("HH:mm");

    public ClockModule() { super("Clock", "Shows the real time", false, 6, 66, false, false); }
    @Override protected String label() { return "TIME"; }
    @Override protected String value(MinecraftClient mc) { return LocalTime.now().format(F); }
}

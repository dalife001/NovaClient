package com.nova.client.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Java 8 replacements for newer String / Files / Gson helpers. */
public final class S {
    private S() {}

    public static boolean blank(String s) { return s == null || s.trim().isEmpty(); }

    public static String read(Path p) throws IOException { return new String(Files.readAllBytes(p), StandardCharsets.UTF_8); }

    public static void write(Path p, String s) throws IOException { Files.write(p, s.getBytes(StandardCharsets.UTF_8)); }

    /** Works on every Gson version Minecraft ships (JsonParser.parseString only exists from Gson 2.8.6). */
    @SuppressWarnings("deprecation")
    public static com.google.gson.JsonElement parse(String json) { return new JsonParser().parse(json); }

    public static JsonObject parseObject(String json) { return parse(json).getAsJsonObject(); }
}

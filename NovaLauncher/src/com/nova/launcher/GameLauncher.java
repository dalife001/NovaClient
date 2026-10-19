package com.nova.launcher;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/** Builds the java command line and starts Minecraft. */
final class GameLauncher {
    static final String VERSION = "1.0.0";

    private GameLauncher() {}

    static List<String> command(Installer.Plan plan, Account acct, Path java, Path gameDir,
                                int ramMb, List<String> extraJvm) {
        String sep = File.pathSeparator;
        Map<String, String> v = new HashMap<>();
        v.put("auth_player_name", acct.name);
        v.put("auth_uuid", acct.uuid);
        v.put("auth_access_token", acct.token);
        v.put("auth_xuid", "0");
        v.put("clientid", "0");
        v.put("user_type", acct.online ? "msa" : "legacy");
        v.put("user_properties", "{}");
        v.put("version_name", plan.versionId());
        v.put("version_type", "release");
        v.put("game_directory", gameDir.toString());
        v.put("assets_root", plan.assetsDir().toString());
        v.put("assets_index_name", plan.assetIndex());
        v.put("natives_directory", plan.natives().toString());
        v.put("library_directory", plan.natives().getParent().getParent().getParent().resolve("libraries").toString());
        v.put("classpath_separator", sep);
        v.put("launcher_name", "NovaLauncher");
        v.put("launcher_version", VERSION);
        v.put("classpath", plan.classpath().stream().map(Path::toString).collect(Collectors.joining(sep)));

        List<String> cmd = new ArrayList<>();
        cmd.add(java.toString());
        cmd.add("-Xmx" + ramMb + "M");
        cmd.add("-Xms512M");
        cmd.addAll(extraJvm);
        for (String a : plan.jvm()) cmd.add(sub(a, v));
        // Safety net: if the version's own arguments had no classpath, add one.
        if (!cmd.contains("-cp") && !cmd.contains("-classpath")) { cmd.add("-cp"); cmd.add(v.get("classpath")); }
        cmd.add(plan.mainClass());
        for (String a : plan.game()) cmd.add(sub(a, v));
        return cmd;
    }

    static String sub(String s, Map<String, String> vars) {
        for (Map.Entry<String, String> e : vars.entrySet()) s = s.replace("${" + e.getKey() + "}", e.getValue());
        return s;
    }

    static Process start(List<String> cmd, Path gameDir) throws Exception {
        Files.createDirectories(gameDir);
        return new ProcessBuilder(cmd).directory(gameDir.toFile()).redirectErrorStream(true).start();
    }
}

package com.nova.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nova.client.config.Config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Looks at latest.json and remembers the newest Nova Client version. The Nova Launcher installs it. */
public final class UpdateChecker {
    public static volatile String latest;
    public static volatile String note = "";
    private static boolean started;

    private UpdateChecker() {}

    public static boolean available() {
        return latest != null && compare(latest, NovaClient.VERSION) > 0;
    }

    public static synchronized void checkOnce() {
        if (started) return;
        started = true;
        check();
    }

    public static void check() {
        String url = Config.get().manifestUrl();
        if (url.isBlank()) { note = "No update URL set"; return; }
        note = "Checking...";
        Thread t = new Thread(() -> {
            try {
                HttpClient c = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(10)).build();
                HttpResponse<String> r = c.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).build(),
                        HttpResponse.BodyHandlers.ofString());
                JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
                latest = o.get("version").getAsString();
                note = available() ? "Update available: " + latest : "You are up to date";
            } catch (Exception e) {
                note = "Update check failed";
            }
        }, "nova-update-check");
        t.setDaemon(true);
        t.start();
    }

    static int compare(String a, String b) {
        int[] x = parts(a), y = parts(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int[] parts(String v) {
        String[] t = v.replaceFirst("^[^0-9]*", "").split("[^0-9]+");
        int[] r = new int[t.length];
        for (int i = 0; i < t.length; i++) {
            try { r[i] = Integer.parseInt(t[i]); } catch (NumberFormatException e) { r[i] = 0; }
        }
        return r;
    }
}

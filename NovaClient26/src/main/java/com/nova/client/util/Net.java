package com.nova.client.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Tiny HTTP helper built on HttpURLConnection, so the client needs nothing newer than Java 8
 * (java.net.http only exists from Java 11, and Minecraft 1.16 runs on Java 8).
 */
public final class Net {
    private Net() {}

    public static final class Response {
        public final int status;
        public final byte[] bytes;

        Response(int status, byte[] bytes) { this.status = status; this.bytes = bytes; }

        public String body() { return new String(bytes, StandardCharsets.UTF_8); }
    }

    public static Response request(String method, String url, Map<String, String> headers, byte[] body, int timeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "NovaClient");
        if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
        if (body != null) {
            c.setDoOutput(true);
            try (OutputStream o = c.getOutputStream()) { o.write(body); }
        }
        int status = c.getResponseCode();
        InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
        byte[] data = in == null ? new byte[0] : readAll(in);
        c.disconnect();
        return new Response(status, data);
    }

    public static Response get(String url) throws IOException { return request("GET", url, null, null, 15000); }

    public static Response getAuth(String url, String bearer) throws IOException {
        java.util.Map<String, String> h = new java.util.HashMap<String, String>();
        h.put("Authorization", "Bearer " + bearer);
        return request("GET", url, h, null, 15000);
    }

    public static Response postForm(String url, Map<String, String> fields) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        java.util.Map<String, String> h = new java.util.HashMap<String, String>();
        h.put("Content-Type", "application/x-www-form-urlencoded");
        return request("POST", url, h, sb.toString().getBytes(StandardCharsets.UTF_8), 15000);
    }

    public static Response postJson(String url, String json) throws IOException {
        java.util.Map<String, String> h = new java.util.HashMap<String, String>();
        h.put("Content-Type", "application/json");
        h.put("Accept", "application/json");
        return request("POST", url, h, json.getBytes(StandardCharsets.UTF_8), 15000);
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}

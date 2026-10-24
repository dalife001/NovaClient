package com.nova.launcher;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/** Microsoft -> Xbox -> XSTS -> Minecraft login with the device-code flow (no password typed here). */
final class Auth {
    private Auth() {}

    private static final String SCOPE = "XboxLive.signin offline_access";
    private static final String TENANT = "https://login.microsoftonline.com/consumers/oauth2/v2.0";

    record DeviceCode(String deviceCode, String userCode, String verificationUri, int interval, int expiresIn) {}
    record Tokens(String access, String refresh) {}

    static DeviceCode start(String clientId) throws Exception {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("client_id", clientId);
        f.put("scope", SCOPE);
        Map<String, Object> o = Http.postForm(TENANT + "/devicecode", f);
        if (!o.containsKey("device_code")) throw new IOException(err(o));
        return new DeviceCode((String) o.get("device_code"), (String) o.get("user_code"),
                (String) o.get("verification_uri"), (int) Json.num(o, "interval"), (int) Json.num(o, "expires_in"));
    }

    static Tokens poll(String clientId, DeviceCode dc) throws Exception {
        long deadline = System.currentTimeMillis() + dc.expiresIn() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(Math.max(dc.interval(), 2) * 1000L);
            Map<String, String> f = new LinkedHashMap<>();
            f.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            f.put("client_id", clientId);
            f.put("device_code", dc.deviceCode());
            Map<String, Object> o = Http.postForm(TENANT + "/token", f);
            if (o.containsKey("access_token")) return tokens(o);
            String e = Json.str(o, "error");
            if (e == null || (!e.equals("authorization_pending") && !e.equals("slow_down"))) throw new IOException(err(o));
        }
        throw new IOException("Login code expired");
    }

    /** The page that opens with the code already filled in, so nothing has to be typed. */
    static String codeLink(DeviceCode dc) {
        String u = dc.verificationUri();
        return u + (u.contains("?") ? "&" : "?") + "otc=" + URLEncoder.encode(dc.userCode(), StandardCharsets.UTF_8);
    }

    /**
     * Sign in through the browser: the player logs in on Microsoft's own page and is sent back to a tiny page on this
     * computer (PKCE, so no secret is stored and no code is typed). Needs "http://localhost" as a redirect address on the app.
     */
    static final class BrowserLogin implements AutoCloseable {
        private final String clientId, verifier, state;
        private final ServerSocket server;
        private final String redirect;
        private volatile boolean cancelled;

        BrowserLogin(String clientId) throws IOException {
            this.clientId = clientId;
            this.verifier = random(48);
            this.state = random(18);
            this.server = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
            this.redirect = "http://localhost:" + server.getLocalPort();
        }

        String url() {
            return TENANT + "/authorize?client_id=" + enc(clientId) + "&response_type=code&redirect_uri=" + enc(redirect)
                    + "&response_mode=query&scope=" + enc(SCOPE) + "&state=" + enc(state)
                    + "&code_challenge=" + challenge(verifier) + "&code_challenge_method=S256&prompt=select_account";
        }

        /** Waits for the browser to come back, then trades the answer for tokens. */
        Tokens await(int seconds) throws Exception {
            long end = System.currentTimeMillis() + seconds * 1000L;
            server.setSoTimeout(500);
            while (!cancelled && System.currentTimeMillis() < end) {
                Socket s;
                try {
                    s = server.accept();
                } catch (SocketTimeoutException e) {
                    continue;
                } catch (IOException e) {
                    if (cancelled) break;
                    throw e;
                }
                try (s) {
                    s.setSoTimeout(3000);
                    byte[] buf = new byte[8192];
                    int n = Math.max(0, s.getInputStream().read(buf));
                    String head = new String(buf, 0, n, StandardCharsets.ISO_8859_1);
                    Map<String, String> q = parseQuery(head.lines().findFirst().orElse(""));
                    if (!q.containsKey("code") && !q.containsKey("error")) { reply(s, 404, "Not found", false); continue; }
                    if (!state.equals(q.get("state"))) { reply(s, 400, "This sign-in link is not from the current attempt.", false); continue; }
                    if (q.containsKey("error")) {
                        reply(s, 200, "Sign-in was cancelled or refused. You can close this tab.", false);
                        String d = q.get("error_description");
                        throw new IOException(d != null ? d : q.get("error"));
                    }
                    Map<String, String> f = new LinkedHashMap<>();
                    f.put("grant_type", "authorization_code");
                    f.put("client_id", clientId);
                    f.put("code", q.get("code"));
                    f.put("redirect_uri", redirect);
                    f.put("code_verifier", verifier);
                    f.put("scope", SCOPE);
                    Map<String, Object> o = Http.postForm(TENANT + "/token", f);
                    if (!o.containsKey("access_token")) {
                        reply(s, 200, "Something went wrong. You can close this tab and try again in Nova.", false);
                        throw new IOException(err(o));
                    }
                    reply(s, 200, "You are signed in. You can close this tab and go back to Nova.", true);
                    return tokens(o);
                }
            }
            if (cancelled) throw new IOException("Sign-in cancelled");
            throw new IOException("Sign-in timed out");
        }

        void cancel() { cancelled = true; close(); }

        @Override public void close() { try { server.close(); } catch (IOException ignored) {} }
    }

    /** The query of a request line like "GET /?code=abc&state=xyz HTTP/1.1", as a map. */
    static Map<String, String> parseQuery(String requestLine) {
        Map<String, String> m = new LinkedHashMap<>();
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) return m;
        int i = parts[1].indexOf('?');
        if (i < 0) return m;
        for (String kv : parts[1].substring(i + 1).split("&")) {
            int e = kv.indexOf('=');
            if (e <= 0) continue;
            m.put(URLDecoder.decode(kv.substring(0, e), StandardCharsets.UTF_8), URLDecoder.decode(kv.substring(e + 1), StandardCharsets.UTF_8));
        }
        return m;
    }

    static String challenge(String verifier) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String random(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static void reply(Socket s, int code, String message, boolean good) throws IOException {
        String html = "<!doctype html><meta charset=utf-8><title>Nova</title><body style=\"margin:0;height:100vh;display:flex;"
                + "align-items:center;justify-content:center;background:#080816;color:#f4f0ff;font:18px system-ui,sans-serif\">"
                + "<div style=\"text-align:center\"><div style=\"font-size:44px;letter-spacing:.3em;color:" + (good ? "#8ef0b0" : "#ff8fa3") + "\">nova</div>"
                + "<p>" + message + "</p></div>";
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " Error") + "\r\nContent-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
        s.getOutputStream().write(head.getBytes(StandardCharsets.ISO_8859_1));
        s.getOutputStream().write(body);
        s.getOutputStream().flush();
    }

    /** A message a player can act on instead of a raw server answer. */
    static String friendly(String message) {
        String m = message == null ? "" : message;
        if (m.contains("Invalid app registration") || m.contains("approved by Mojang"))
            return "Microsoft accepted your login, but Minecraft's servers refused this launcher. Its Microsoft app has not been approved by Mojang yet.";
        if (m.contains("does not own Minecraft"))
            return "This Microsoft account does not own Minecraft: Java Edition.";
        if (m.contains("no Xbox profile") || m.contains("child account"))
            return "This account has no Xbox profile yet (or it is a child account). Sign in once at xbox.com, or ask an adult to add it to a family.";
        if (m.contains("AADSTS700016") || m.contains("unauthorized_client") || m.contains("invalid_client"))
            return "The Microsoft app ID in this launcher is not valid. Check it in Settings.";
        if (m.contains("redirect_uri") || m.contains("AADSTS50011"))
            return "The Microsoft app is missing the 'http://localhost' redirect address. Use the code sign-in instead, or add it in the Azure portal.";
        if (m.contains("timed out") || m.contains("expired")) return "The sign-in took too long. Try again.";
        if (m.contains("cancelled")) return "Sign-in cancelled.";
        return m;
    }

    static Tokens refresh(String clientId, String refreshToken) throws Exception {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("grant_type", "refresh_token");
        f.put("client_id", clientId);
        f.put("refresh_token", refreshToken);
        f.put("scope", SCOPE);
        Map<String, Object> o = Http.postForm(TENANT + "/token", f);
        if (!o.containsKey("access_token")) throw new IOException(err(o));
        return tokens(o);
    }

    private static Tokens tokens(Map<String, Object> o) {
        String r = Json.str(o, "refresh_token");
        return new Tokens(Json.str(o, "access_token"), r == null ? "" : r);
    }

    static Account minecraft(String msAccess) throws Exception {
        Map<String, Object> xblProps = new LinkedHashMap<>();
        xblProps.put("AuthMethod", "RPS");
        xblProps.put("SiteName", "user.auth.xboxlive.com");
        xblProps.put("RpsTicket", "d=" + msAccess);
        Map<String, Object> xblBody = new LinkedHashMap<>();
        xblBody.put("Properties", xblProps);
        xblBody.put("RelyingParty", "http://auth.xboxlive.com");
        xblBody.put("TokenType", "JWT");
        Map<String, Object> xbl = Http.postJson("https://user.auth.xboxlive.com/user/authenticate", xblBody);
        if (!xbl.containsKey("Token")) throw new IOException("Xbox Live login failed");
        String uhs = Json.str(Json.o(Json.a(Json.o(xbl.get("DisplayClaims")).get("xui")).get(0)), "uhs");

        Map<String, Object> xstsProps = new LinkedHashMap<>();
        xstsProps.put("SandboxId", "RETAIL");
        xstsProps.put("UserTokens", List.of(xbl.get("Token")));
        Map<String, Object> xstsBody = new LinkedHashMap<>();
        xstsBody.put("Properties", xstsProps);
        xstsBody.put("RelyingParty", "rp://api.minecraftservices.com/");
        xstsBody.put("TokenType", "JWT");
        Map<String, Object> xsts = Http.postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsBody);
        if (!xsts.containsKey("Token")) throw new IOException("Xbox security token refused (no Xbox profile, or a child account?)");

        Map<String, Object> mcBody = new LinkedHashMap<>();
        mcBody.put("identityToken", "XBL3.0 x=" + uhs + ";" + xsts.get("Token"));
        Map<String, Object> mc = Http.postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcBody);
        if (!mc.containsKey("access_token"))
            throw new IOException("Minecraft login refused: " + err(mc) + " (new Azure apps must be approved by Mojang, see README)");
        String token = (String) mc.get("access_token");

        Map<String, Object> prof = Http.getAuth("https://api.minecraftservices.com/minecraft/profile", token);
        if (!prof.containsKey("id")) throw new IOException("This account does not own Minecraft: Java Edition");
        String id = (String) prof.get("id");
        String uuid = id.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5");
        Account a = new Account((String) prof.get("name"), uuid, token, true);
        for (Object s : Json.a(prof.get("skins"))) {
            Map<String, Object> sk = Json.o(s);
            if ("ACTIVE".equals(sk.get("state"))) {
                a.skinUrl = Json.str(sk, "url");
                a.slim = "SLIM".equalsIgnoreCase(Json.str(sk, "variant"));
            }
        }
        return a;
    }

    private static String err(Map<String, Object> o) {
        for (String k : new String[]{"error_description", "errorMessage", "error"}) {
            String v = Json.str(o, k);
            if (v != null) return v;
        }
        return "HTTP " + Json.num(o, "_status");
    }
}

package com.nova.launcher;

import java.io.IOException;
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

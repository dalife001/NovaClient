package com.nova.client.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Microsoft -> Xbox Live -> XSTS -> Minecraft login using the OAuth "device code" flow.
 * You never type your password into Nova Client: you log in on microsoft.com.
 */
public final class MicrosoftAuth {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String SCOPE = "XboxLive.signin offline_access";
    private static final String TENANT = "https://login.microsoftonline.com/consumers/oauth2/v2.0";

    public record DeviceCode(String deviceCode, String userCode, String verificationUri, int interval, int expiresIn) {}
    public record MsTokens(String accessToken, String refreshToken) {}
    public record McProfile(String name, UUID uuid, String mcToken) {}

    // ----- step 1: device code -----
    public static DeviceCode requestDeviceCode(String clientId) throws Exception {
        JsonObject o = send(form(TENANT + "/devicecode", Map.of("client_id", clientId, "scope", SCOPE)));
        if (!o.has("device_code")) throw new IOException(err(o));
        return new DeviceCode(o.get("device_code").getAsString(), o.get("user_code").getAsString(),
                o.get("verification_uri").getAsString(), o.get("interval").getAsInt(), o.get("expires_in").getAsInt());
    }

    // ----- step 2: wait for the user to approve -----
    public static MsTokens pollForTokens(String clientId, DeviceCode dc) throws Exception {
        long deadline = System.currentTimeMillis() + dc.expiresIn() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(Math.max(dc.interval(), 2) * 1000L);
            Map<String, String> f = new LinkedHashMap<>();
            f.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            f.put("client_id", clientId);
            f.put("device_code", dc.deviceCode());
            JsonObject o = send(form(TENANT + "/token", f));
            if (o.has("access_token")) return tokens(o);
            String e = o.has("error") ? o.get("error").getAsString() : "";
            if (!e.equals("authorization_pending") && !e.equals("slow_down")) throw new IOException(err(o));
        }
        throw new IOException("Login code expired");
    }

    public static MsTokens refresh(String clientId, String refreshToken) throws Exception {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("grant_type", "refresh_token");
        f.put("client_id", clientId);
        f.put("refresh_token", refreshToken);
        f.put("scope", SCOPE);
        JsonObject o = send(form(TENANT + "/token", f));
        if (!o.has("access_token")) throw new IOException(err(o));
        return tokens(o);
    }

    private static MsTokens tokens(JsonObject o) {
        return new MsTokens(o.get("access_token").getAsString(),
                o.has("refresh_token") ? o.get("refresh_token").getAsString() : "");
    }

    // ----- steps 3-6: Xbox, XSTS, Minecraft -----
    public static McProfile loginMinecraft(String msAccessToken) throws Exception {
        JsonObject xblProps = new JsonObject();
        xblProps.addProperty("AuthMethod", "RPS");
        xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProps.addProperty("RpsTicket", "d=" + msAccessToken);
        JsonObject xblBody = new JsonObject();
        xblBody.add("Properties", xblProps);
        xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblBody.addProperty("TokenType", "JWT");
        JsonObject xbl = send(json("https://user.auth.xboxlive.com/user/authenticate", xblBody, null));
        if (!xbl.has("Token")) throw new IOException("Xbox Live login failed");
        String xblToken = xbl.get("Token").getAsString();
        String uhs = xbl.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0)
                .getAsJsonObject().get("uhs").getAsString();

        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);
        JsonObject xstsProps = new JsonObject();
        xstsProps.addProperty("SandboxId", "RETAIL");
        xstsProps.add("UserTokens", userTokens);
        JsonObject xstsBody = new JsonObject();
        xstsBody.add("Properties", xstsProps);
        xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsBody.addProperty("TokenType", "JWT");
        JsonObject xsts = send(json("https://xsts.auth.xboxlive.com/xsts/authorize", xstsBody, null));
        if (!xsts.has("Token")) throw new IOException("XSTS failed (does this account have an Xbox profile / is it a child account?)");

        JsonObject mcBody = new JsonObject();
        mcBody.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xsts.get("Token").getAsString());
        JsonObject mc = send(json("https://api.minecraftservices.com/authentication/login_with_xbox", mcBody, null));
        if (!mc.has("access_token")) {
            throw new IOException("Minecraft login refused: " + err(mc)
                    + " (new Azure apps must be approved by Mojang, see README)");
        }
        String mcToken = mc.get("access_token").getAsString();

        HttpRequest prof = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                .header("Authorization", "Bearer " + mcToken).GET().build();
        JsonObject p = send(prof);
        if (!p.has("id")) throw new IOException("This account does not own Minecraft: Java Edition");
        UUID uuid = UUID.fromString(p.get("id").getAsString().replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
        return new McProfile(p.get("name").getAsString(), uuid, mcToken);
    }

    // ----- http helpers -----
    public static JsonObject send(HttpRequest req) throws IOException, InterruptedException {
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject o = new JsonObject();
        String body = r.body();
        if (body != null && !body.isBlank() && body.trim().startsWith("{")) {
            o = JsonParser.parseString(body).getAsJsonObject();
        }
        o.addProperty("_status", r.statusCode());
        return o;
    }

    private static HttpRequest form(String url, Map<String, String> fields) {
        String body = fields.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private static HttpRequest json(String url, JsonObject body, String bearer) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json").header("Accept", "application/json");
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        return b.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
    }

    private static String err(JsonObject o) {
        for (String k : new String[]{"error_description", "errorMessage", "error"}) {
            if (o.has(k)) return o.get(k).getAsString();
        }
        return "HTTP " + (o.has("_status") ? o.get("_status").getAsInt() : -1);
    }
}

package com.nova.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

final class Http {
    private Http() {}

    static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20)).build();

    record Job(String url, Path dest, String algo, String hash, long size) {}

    private static HttpRequest.Builder req(String url) {
        return HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "NovaLauncher/1.0").timeout(Duration.ofSeconds(90));
    }

    static String get(String url) throws IOException, InterruptedException {
        HttpResponse<String> r = CLIENT.send(req(url).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() / 100 != 2) throw new IOException("HTTP " + r.statusCode() + " for " + url);
        return r.body();
    }

    static byte[] getBytes(String url) throws IOException, InterruptedException {
        HttpResponse<byte[]> r = CLIENT.send(req(url).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (r.statusCode() / 100 != 2) throw new IOException("HTTP " + r.statusCode() + " for " + url);
        return r.body();
    }

    static Map<String, Object> getJson(String url) throws IOException, InterruptedException {
        return Json.o(Json.parse(get(url)));
    }

    /** Sends a request and parses a JSON object body (adds "_status"). Never throws on HTTP errors. */
    static Map<String, Object> send(HttpRequest r) throws IOException, InterruptedException {
        HttpResponse<String> resp = CLIENT.send(r, HttpResponse.BodyHandlers.ofString());
        String body = resp.body() == null ? "" : resp.body().trim();
        Map<String, Object> m = new LinkedHashMap<>();
        if (body.startsWith("{")) m = Json.o(Json.parse(body));
        m.put("_status", (double) resp.statusCode());
        return m;
    }

    static Map<String, Object> postForm(String url, Map<String, String> f) throws IOException, InterruptedException {
        String body = f.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return send(req(url).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    static Map<String, Object> postJson(String url, Object body) throws IOException, InterruptedException {
        return send(req(url).header("Content-Type", "application/json").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body))).build());
    }

    static Map<String, Object> getAuth(String url, String bearer) throws IOException, InterruptedException {
        return send(req(url).header("Authorization", "Bearer " + bearer).GET().build());
    }

    // ---------- downloads ----------
    static String hash(Path p, String algo) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            MessageDigest md = MessageDigest.getInstance(algo);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    static boolean valid(Path p, String algo, String hash, long size) throws IOException {
        if (!Files.isRegularFile(p)) return false;
        if (size > 0) return Files.size(p) == size;
        if (hash == null || algo == null) return true;
        return hash(p, algo).equalsIgnoreCase(hash);
    }

    static void download(String url, Path dest, String algo, String hash) throws IOException, InterruptedException {
        download(new Job(url, dest, algo, hash, 0));
    }

    static void download(Job j) throws IOException, InterruptedException {
        if (valid(j.dest, j.algo, j.hash, j.size)) return;
        Files.createDirectories(j.dest.getParent());
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            Path part = j.dest.resolveSibling(j.dest.getFileName() + ".part");
            try {
                HttpResponse<InputStream> r = CLIENT.send(req(j.url).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                if (r.statusCode() / 100 != 2) throw new IOException("HTTP " + r.statusCode() + " for " + j.url);
                try (InputStream in = r.body()) { Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING); }
                if (j.hash != null && j.algo != null && !hash(part, j.algo).equalsIgnoreCase(j.hash))
                    throw new IOException("Checksum mismatch for " + j.dest.getFileName());
                Files.move(part, j.dest, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                last = e;
                Files.deleteIfExists(part);
            }
        }
        throw last;
    }

    /** Downloads many files in parallel, reporting progress. */
    static void all(List<Job> jobs, Progress p, String label) throws Exception {
        if (jobs.isEmpty()) return;
        ExecutorService ex = Executors.newFixedThreadPool(12);
        AtomicInteger done = new AtomicInteger();
        int total = jobs.size();
        List<Future<Void>> fs = new ArrayList<>();
        try {
            for (Job j : jobs) {
                fs.add(ex.submit(() -> {
                    download(j);
                    int n = done.incrementAndGet();
                    if (n % 25 == 0 || n == total) { p.status(label + " (" + n + "/" + total + ")"); p.bar(n, total); }
                    return null;
                }));
            }
            for (Future<Void> f : fs) {
                try { f.get(); } catch (ExecutionException e) { throw new IOException(e.getCause().getMessage(), e.getCause()); }
            }
        } finally {
            ex.shutdownNow();
        }
    }
}

package com.example.desktop;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

public final class PortalClient {
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    public PortalClient() {
        this.baseUrl = System.getenv().getOrDefault("PORTAL_URL", "https://remote-javafx-portal.onrender.com").replaceAll("/$", "");
    }

    public String baseUrl() { return baseUrl; }

    public boolean health() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/public/client-health"))
            .timeout(Duration.ofSeconds(120))
            .GET()
            .build();
        HttpResponse<String> res = sendWithRetry(req, 3, 2500);
        return res.statusCode() == 200;
    }

    public String electricalNews() throws Exception {
        return publicGet("/api/public/news/electrical");
    }

    public String bootstrap(String username, String password) throws Exception {
        return authorizedGet("/api/client/bootstrap", username, password);
    }

    public String profile(String username, String password) throws Exception {
        return authorizedGet("/api/profile", username, password);
    }

    public String updateProfile(String username, String password, String jsonBody) throws Exception {
        return authorizedJson("/api/profile", "PUT", username, password, jsonBody);
    }

    public String changePassword(String username, String password, String jsonBody) throws Exception {
        return authorizedJson("/api/profile/password", "PUT", username, password, jsonBody);
    }

    private String publicGet(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(150))
            .header("Cache-Control", "no-cache")
            .GET()
            .build();
        return bodyOrThrow(sendWithRetry(req, 3, 3000));
    }

    private String authorizedGet(String path, String username, String password) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(150))
            .header("Authorization", basic(username, password))
            .GET()
            .build();
        return bodyOrThrow(sendWithRetry(req, 3, 3000));
    }

    private String authorizedJson(String path, String method, String username, String password, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(150))
            .header("Authorization", basic(username, password))
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(jsonBody == null ? "{}" : jsonBody, StandardCharsets.UTF_8))
            .build();
        return bodyOrThrow(sendWithRetry(req, 3, 3000));
    }

    private static String basic(String username, String password) {
        String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    private static String bodyOrThrow(HttpResponse<String> res) {
        if (res.statusCode() == 401 || res.statusCode() == 403) {
            throw new SecurityException("Invalid username/password or account disabled");
        }
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            String detail = res.body() == null || res.body().isBlank() ? "" : ": " + res.body();
            throw new IllegalStateException("Portal returned HTTP " + res.statusCode() + detail);
        }
        return res.body();
    }

    private HttpResponse<String> sendWithRetry(HttpRequest req, int attempts, long delayMs) throws Exception {
        Exception last = null;
        for (int i = 1; i <= attempts; i++) {
            try {
                return http.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (java.net.http.HttpTimeoutException | java.net.ConnectException e) {
                last = e;
                if (i < attempts) Thread.sleep(delayMs);
            }
        }
        if (last != null) throw last;
        throw new IllegalStateException("Request failed");
    }
}

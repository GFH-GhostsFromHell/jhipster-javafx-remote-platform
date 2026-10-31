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

    public String bootstrap(String username, String password) throws Exception {
        String basic = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/client/bootstrap"))
            .timeout(Duration.ofSeconds(150))
            .header("Authorization", "Basic " + basic)
            .GET()
            .build();
        HttpResponse<String> res = sendWithRetry(req, 3, 3000);
        if (res.statusCode() == 401 || res.statusCode() == 403) {
            throw new SecurityException("Invalid username/password or account disabled");
        }
        if (res.statusCode() != 200) {
            throw new IllegalStateException("Portal returned HTTP " + res.statusCode());
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

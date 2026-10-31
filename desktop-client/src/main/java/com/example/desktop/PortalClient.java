package com.example.desktop;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

public final class PortalClient {
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public PortalClient() {
        this.baseUrl = System.getenv().getOrDefault("PORTAL_URL", "https://remote-javafx-portal.onrender.com").replaceAll("/$", "");
    }

    public String baseUrl() { return baseUrl; }

    public boolean health() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/public/client-health")).timeout(Duration.ofSeconds(20)).GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
    }

    public String bootstrap(String username, String password) throws Exception {
        String basic = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/client/bootstrap"))
            .timeout(Duration.ofSeconds(30)).header("Authorization", "Basic " + basic).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 401 || res.statusCode() == 403) throw new SecurityException("Invalid username/password or account disabled");
        if (res.statusCode() != 200) throw new IllegalStateException("Portal returned HTTP " + res.statusCode());
        return res.body();
    }
}

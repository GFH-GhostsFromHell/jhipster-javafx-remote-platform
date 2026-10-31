package com.example.agent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class AgentApplication {
    private static final String BASE_URL = resolveBaseUrl();
    private static final String DEVICE_ID = System.getProperty("device.id", UUID.randomUUID().toString());
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public void start() {
        scheduler.scheduleAtFixedRate(this::heartbeat, 0, 30, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::pollCommands, 5, 15, TimeUnit.SECONDS);
    }

    private void heartbeat() {
        try {
            String json = "{\"deviceId\":\"" + DEVICE_ID + "\",\"timestamp\":\"" + Instant.now() + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/agent/heartbeat"))
                .header("Content-Type", "application/json")
                .header("X-Device-Id", DEVICE_ID)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(8))
                .build();
            http.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }

    private void pollCommands() {
    }

    private static String resolveBaseUrl() {
        String value = System.getProperty("portal.url");
        if (value == null || value.isBlank()) value = System.getenv("PORTAL_URL");
        if (value == null || value.isBlank()) value = "http://localhost:8080";
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    public static void main(String[] args) {
        AgentApplication app = new AgentApplication();
        app.start();
        Runtime.getRuntime().addShutdownHook(new Thread(app.scheduler::shutdownNow));
    }
}

package com.example.remoteportal.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/public/releases")
public class ReleaseResource {
    private static final String RELEASES_API = "https://api.github.com/repos/GFH-GhostsFromHell/jhipster-javafx-remote-platform/releases?per_page=40";
    private static final Duration RELEASE_LIST_TTL = Duration.ofMinutes(3);
    private static final Duration RESOLVED_RELEASE_TTL = Duration.ofSeconds(45);

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, CachedRelease> cache = new ConcurrentHashMap<>();
    private final Object releaseListLock = new Object();
    private volatile CachedReleaseList releaseListCache;

    @GetMapping("/android")
    public ResponseEntity<?> android(@RequestParam(defaultValue = "stable") String channel) {
        return release("android", channel, "android-v", "android-dev-v", "ElectricalWorkbench-Android.apk");
    }

    @GetMapping("/desktop")
    public ResponseEntity<?> desktop(@RequestParam(defaultValue = "stable") String channel) {
        return release("desktop", channel, "client-v", "client-dev-v", "RemoteJavaFXClient-Setup.msi");
    }

    private ResponseEntity<?> release(String platform, String channel, String stablePrefix, String devPrefix, String assetName) {
        String normalized = normalizeChannel(channel);
        try {
            return ResponseEntity.ok(findRelease(platform, normalized, stablePrefix, devPrefix, assetName));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "Unable to resolve the current " + platform + " release",
                "channel", normalized,
                "detail", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
            ));
        }
    }

    private Map<String, Object> findRelease(
        String platform,
        String channel,
        String stablePrefix,
        String devPrefix,
        String assetName
    ) throws Exception {
        String cacheKey = platform + ":" + channel;
        CachedRelease cached = cache.get(cacheKey);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) return cached.payload();

        JsonNode releases = releaseList();
        boolean dev = "development".equals(channel);
        String prefix = dev ? devPrefix : stablePrefix;
        JsonNode selected = null;
        for (JsonNode release : releases) {
            if (release.path("draft").asBoolean(false)) continue;
            String tag = release.path("tag_name").asText("");
            boolean prerelease = release.path("prerelease").asBoolean(false);
            if (!tag.startsWith(prefix)) continue;
            if (!dev && prerelease) continue;
            selected = release;
            break;
        }
        if (selected == null) throw new IOException("No " + channel + " " + platform + " release is available");

        String tag = selected.path("tag_name").asText();
        String version = tag.substring(prefix.length());
        String downloadUrl = null;
        String sha256 = null;
        long size = 0L;
        for (JsonNode asset : selected.path("assets")) {
            if (!assetName.equals(asset.path("name").asText())) continue;
            downloadUrl = asset.path("browser_download_url").asText(null);
            sha256 = asset.path("digest").asText(null);
            size = asset.path("size").asLong(0L);
            break;
        }
        if (downloadUrl == null || downloadUrl.isBlank()) throw new IOException("Release does not contain " + assetName);
        if (sha256 != null && sha256.startsWith("sha256:")) sha256 = sha256.substring(7);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("platform", platform);
        payload.put("channel", channel);
        payload.put("version", version);
        payload.put("tag", tag);
        payload.put("downloadUrl", downloadUrl);
        payload.put("releaseUrl", selected.path("html_url").asText(""));
        payload.put("sha256", sha256 == null ? "" : sha256);
        payload.put("sizeBytes", size);
        payload.put("publishedAt", selected.path("published_at").asText(""));
        payload.put("assetName", assetName);
        payload.put("checkedAt", now.toString());
        Map<String, Object> immutable = Map.copyOf(payload);
        cache.put(cacheKey, new CachedRelease(immutable, now.plus(RESOLVED_RELEASE_TTL)));
        return immutable;
    }

    private JsonNode releaseList() throws Exception {
        Instant now = Instant.now();
        CachedReleaseList cached = releaseListCache;
        if (cached != null && cached.expiresAt().isAfter(now)) return cached.releases();

        synchronized (releaseListLock) {
            cached = releaseListCache;
            now = Instant.now();
            if (cached != null && cached.expiresAt().isAfter(now)) return cached.releases();

            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES_API))
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "ElectricalWorkbench-Portal")
                    .GET()
                    .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IOException("GitHub releases returned HTTP " + response.statusCode());
                }
                JsonNode releases = json.readTree(response.body());
                if (!releases.isArray()) throw new IOException("GitHub releases returned an invalid response");
                releaseListCache = new CachedReleaseList(releases, now.plus(RELEASE_LIST_TTL));
                return releases;
            } catch (Exception ex) {
                // Release metadata changes slowly. If GitHub is temporarily unavailable or rate-limited,
                // serving the last known-good list is safer than breaking every installed updater.
                if (cached != null && cached.releases() != null && cached.releases().isArray()) {
                    releaseListCache = new CachedReleaseList(cached.releases(), now.plus(Duration.ofMinutes(1)));
                    return cached.releases();
                }
                throw ex;
            }
        }
    }

    private static String normalizeChannel(String channel) {
        String c = channel == null ? "stable" : channel.trim().toLowerCase();
        return switch (c) {
            case "dev", "development", "beta" -> "development";
            default -> "stable";
        };
    }

    private record CachedRelease(Map<String, Object> payload, Instant expiresAt) {}
    private record CachedReleaseList(JsonNode releases, Instant expiresAt) {}
}

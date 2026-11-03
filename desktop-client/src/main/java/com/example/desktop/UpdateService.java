package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

public final class UpdateService {
    private static final String DEFAULT_PORTAL = "https://remote-javafx-portal.onrender.com";
    private static final String GITHUB_RELEASES_API = "https://api.github.com/repos/GFH-GhostsFromHell/jhipster-javafx-remote-platform/releases?per_page=40";
    private static final String DESKTOP_ASSET = "RemoteJavaFXClient-Setup.msi";

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private final ObjectMapper json = new ObjectMapper();

    public record UpdateInfo(String currentVersion, String latestVersion, String downloadUrl, String sha256, String channel) {
        public boolean updateAvailable() { return compareVersions(latestVersion, currentVersion) > 0; }
    }

    public String currentVersion() {
        return System.getProperty("app.version", "0.1.0");
    }

    public String updateChannel() {
        return normalizeChannel(System.getProperty("app.updateChannel", "stable"));
    }

    public UpdateInfo check() throws Exception {
        String channel = updateChannel();
        try {
            UpdateInfo info = checkViaPortal(channel);
            log("Update check via portal succeeded: installed=" + info.currentVersion() + ", latest=" + info.latestVersion() + ", channel=" + channel);
            return info;
        } catch (Exception portalError) {
            log("Portal update check failed: " + message(portalError));
            try {
                UpdateInfo info = checkViaGitHub(channel);
                log("GitHub fallback succeeded: installed=" + info.currentVersion() + ", latest=" + info.latestVersion() + ", channel=" + channel);
                return info;
            } catch (Exception githubError) {
                log("GitHub fallback failed: " + message(githubError));
                throw new IOException(
                    "Update check failed. Portal: " + message(portalError) + " | GitHub fallback: " + message(githubError),
                    githubError
                );
            }
        }
    }

    private UpdateInfo checkViaPortal(String channel) throws Exception {
        String portal = System.getProperty("app.portalBaseUrl", DEFAULT_PORTAL).replaceAll("/+$", "");
        URI endpoint = URI.create(portal + "/api/public/releases/desktop?channel=" + channel);

        HttpRequest request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("User-Agent", "RemoteJavaFXClient/" + currentVersion())
            .GET()
            .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            String detail = "Update service returned HTTP " + response.statusCode();
            try {
                JsonNode body = json.readTree(response.body());
                String serverDetail = body.path("detail").asText("");
                String serverError = body.path("error").asText("");
                if (!serverDetail.isBlank()) detail += ": " + serverDetail;
                else if (!serverError.isBlank()) detail += ": " + serverError;
            } catch (Exception ignored) { }
            throw new IOException(detail);
        }

        JsonNode release = json.readTree(response.body());
        return toUpdateInfo(release, channel, "portal");
    }

    private UpdateInfo checkViaGitHub(String channel) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(GITHUB_RELEASES_API))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "RemoteJavaFXClient/" + currentVersion())
            .GET()
            .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("GitHub releases returned HTTP " + response.statusCode());

        JsonNode releases = json.readTree(response.body());
        if (!releases.isArray()) throw new IOException("GitHub releases returned an invalid response");
        boolean dev = "development".equals(channel);
        String prefix = dev ? "client-dev-v" : "client-v";
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
        if (selected == null) throw new IOException("No " + channel + " desktop release is available on GitHub");

        String tag = selected.path("tag_name").asText("");
        String latest = tag.substring(prefix.length());
        String url = "";
        String digest = "";
        for (JsonNode asset : selected.path("assets")) {
            if (!DESKTOP_ASSET.equals(asset.path("name").asText(""))) continue;
            url = asset.path("browser_download_url").asText("");
            digest = asset.path("digest").asText("");
            break;
        }
        if (url.isBlank()) throw new IOException("GitHub release does not contain " + DESKTOP_ASSET);
        if (digest.startsWith("sha256:")) digest = digest.substring(7);
        return new UpdateInfo(currentVersion(), latest, url, digest, channel);
    }

    private UpdateInfo toUpdateInfo(JsonNode release, String requestedChannel, String source) throws IOException {
        String latest = release.path("version").asText("");
        String url = release.path("downloadUrl").asText("");
        String digest = release.path("sha256").asText("");
        String resolvedChannel = release.path("channel").asText(requestedChannel);
        if (latest.isBlank() || url.isBlank()) {
            throw new IOException("The " + source + " update response was incomplete");
        }
        if (digest.startsWith("sha256:")) digest = digest.substring(7);
        return new UpdateInfo(currentVersion(), latest, url, digest, resolvedChannel);
    }

    public Path downloadAndVerify(UpdateInfo info) throws Exception {
        Path target = Files.createTempFile("RemoteJavaFXClient-", ".msi");
        log("Downloading update " + info.latestVersion() + " from " + info.downloadUrl());
        HttpRequest request = HttpRequest.newBuilder(URI.create(info.downloadUrl()))
            .timeout(Duration.ofMinutes(5))
            .header("User-Agent", "RemoteJavaFXClient/" + currentVersion())
            .GET().build();
        HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Files.deleteIfExists(target);
            throw new IOException("Installer download returned HTTP " + response.statusCode());
        }
        if (info.sha256() != null && !info.sha256().isBlank()) {
            String actual = sha256(target);
            if (!actual.equalsIgnoreCase(info.sha256())) {
                Files.deleteIfExists(target);
                throw new SecurityException("Downloaded installer failed SHA-256 verification");
            }
        }
        log("Downloaded and verified update " + info.latestVersion() + " to " + target);
        return target;
    }

    public void launchInstallerAndRelaunch(Path msi) throws Exception {
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath == null || appPath.isBlank()) appPath = ProcessHandle.current().info().command().orElse("");
        Path script = Files.createTempFile("RemoteJavaFXClient-update-", ".ps1");
        String escapedMsi = ps(msi.toAbsolutePath().toString());
        String escapedApp = ps(appPath);
        String body = "$ErrorActionPreference='Stop'\r\n" +
            "Start-Sleep -Seconds 2\r\n" +
            "$p=Start-Process msiexec.exe -Verb RunAs -ArgumentList @('/i','\"" + escapedMsi + "\"','/passive','/norestart') -Wait -PassThru\r\n" +
            "if($p.ExitCode -eq 0 -or $p.ExitCode -eq 3010){ if('" + escapedApp + "'){ Start-Process '" + escapedApp + "' } }\r\n" +
            "Remove-Item -LiteralPath $MyInvocation.MyCommand.Path -Force\r\n";
        Files.writeString(script, body, StandardCharsets.UTF_8);
        log("Launching elevated MSI installer via " + script);
        new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString()).start();
    }

    private static String normalizeChannel(String value) {
        if (value == null) return "stable";
        String c = value.trim().toLowerCase();
        return c.equals("development") || c.equals("dev") || c.equals("beta") ? "development" : "stable";
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) md.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private static void log(String text) {
        try {
            Path path = Path.of(System.getProperty("user.home", "."), "RemoteJavaFXClient-update.log");
            Files.writeString(
                path,
                Instant.now() + "  " + text + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (Exception ignored) { }
    }

    private static String message(Throwable t) {
        if (t == null) return "unknown error";
        String value = t.getMessage();
        return value == null || value.isBlank() ? t.getClass().getSimpleName() : value;
    }

    private static String ps(String s) { return s == null ? "" : s.replace("'", "''"); }

    static int compareVersions(String a, String b) {
        String[] aa = a.split("\\.");
        String[] bb = b.split("\\.");
        int n = Math.max(aa.length, bb.length);
        for (int i = 0; i < n; i++) {
            int av = i < aa.length ? number(aa[i]) : 0;
            int bv = i < bb.length ? number(bb[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private static int number(String value) {
        String digits = value.replaceAll("[^0-9].*$", "");
        try { return digits.isBlank() ? 0 : Integer.parseInt(digits); }
        catch (NumberFormatException e) { return 0; }
    }
}

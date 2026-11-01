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
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

public final class UpdateService {
    private static final String RELEASES_API = "https://api.github.com/repos/GFH-GhostsFromHell/jhipster-javafx-remote-platform/releases?per_page=30";
    private static final String MSI_NAME = "RemoteJavaFXClient-Setup.msi";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final ObjectMapper json = new ObjectMapper();

    public record UpdateInfo(String currentVersion, String latestVersion, String downloadUrl, String sha256, String channel) {
        public boolean updateAvailable() { return compareVersions(latestVersion, currentVersion) > 0; }
    }

    public String currentVersion() {
        return System.getProperty("app.version", "0.1.0");
    }

    public String updateChannel() {
        return System.getProperty("app.updateChannel", "stable").trim().toLowerCase();
    }

    public UpdateInfo check() throws Exception {
        String channel = updateChannel();
        boolean dev = channel.equals("development") || channel.equals("dev") || channel.equals("beta");
        String prefix = dev ? "client-dev-v" : "client-v";

        HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES_API))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "RemoteJavaFXClient")
            .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Update check returned HTTP " + response.statusCode());

        JsonNode selected = null;
        for (JsonNode release : json.readTree(response.body())) {
            if (release.path("draft").asBoolean(false)) continue;
            String tag = release.path("tag_name").asText("");
            boolean prerelease = release.path("prerelease").asBoolean(false);
            if (!tag.startsWith(prefix)) continue;
            if (dev != prerelease) continue;
            selected = release;
            break;
        }
        if (selected == null) throw new IOException("No " + channel + " release is available");

        String tag = selected.path("tag_name").asText("");
        String latest = tag.substring(prefix.length());
        String url = null;
        String digest = null;
        for (JsonNode asset : selected.path("assets")) {
            if (MSI_NAME.equals(asset.path("name").asText())) {
                url = asset.path("browser_download_url").asText(null);
                digest = asset.path("digest").asText(null);
                break;
            }
        }
        if (latest.isBlank() || url == null) throw new IOException("Selected release does not contain the Windows installer");
        if (digest != null && digest.startsWith("sha256:")) digest = digest.substring(7);
        return new UpdateInfo(currentVersion(), latest, url, digest, dev ? "development" : "stable");
    }

    public Path downloadAndVerify(UpdateInfo info) throws Exception {
        Path target = Files.createTempFile("RemoteJavaFXClient-", ".msi");
        HttpRequest request = HttpRequest.newBuilder(URI.create(info.downloadUrl()))
            .timeout(Duration.ofMinutes(5))
            .header("User-Agent", "RemoteJavaFXClient")
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
        new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString()).start();
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

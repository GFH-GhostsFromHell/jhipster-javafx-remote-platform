package com.gfh.industrial;

import java.net.URI;
import java.util.Map;

/**
 * Public, non-sensitive release metadata. The portal never supplies code to execute.
 * A positive release requires all publisher-controlled metadata and a GitHub Releases URL.
 * The desktop must independently verify the Authenticode signature and SHA-256 before installation.
 */
public final class DesktopReleaseInfo {
    private static final String TRUSTED_BASE =
        "https://github.com/GFH-GhostsFromHell/jhipster-javafx-remote-platform/releases/download/";

    private DesktopReleaseInfo() {}

    public static Map<String,Object> current() {
        String version = System.getenv().getOrDefault("STUDIO_DESKTOP_RELEASE_VERSION", "").trim();
        String url = System.getenv().getOrDefault("STUDIO_DESKTOP_RELEASE_URL", "").trim();
        String sha = System.getenv().getOrDefault("STUDIO_DESKTOP_RELEASE_SHA256", "").trim();
        String publisher = System.getenv().getOrDefault("STUDIO_DESKTOP_RELEASE_PUBLISHER", "").trim();
        boolean enabled = Boolean.parseBoolean(System.getenv().getOrDefault(
            "STUDIO_DESKTOP_RELEASE_PUBLISHED","false"));
        boolean published=enabled && version.matches("[0-9]{1,5}\\.[0-9]{1,5}\\.[0-9]{1,8}")
            && sha.matches("[a-fA-F0-9]{64}") && !publisher.isBlank() && publisher.length()<=120
            && validDownloadUrl(url);
        return Map.of(
            "version", published ? version : "0.1.0",
            "channel", published ? "stable" : "development",
            "downloadUrl", published ? url : "",
            "sha256", published ? sha.toLowerCase(java.util.Locale.ROOT) : "",
            "publisher", published ? publisher : "",
            "signed", published,
            "released", published,
            "message", published ? "Publisher-announced signed MSI; verify signature and hash before use" :
                "No signed industrial desktop software release has been published"
        );
    }

    static boolean validDownloadUrl(String url) {
        if (url == null || !url.startsWith(TRUSTED_BASE) || url.length()>500) return false;
        try {
            URI parsed=URI.create(url);
            return "https".equals(parsed.getScheme()) && "github.com".equals(parsed.getHost()) &&
                parsed.getUserInfo()==null && parsed.getFragment()==null &&
                parsed.getRawQuery()==null && parsed.getPort()==-1 &&
                parsed.getPath().endsWith(".msi") &&
                !parsed.getPath().contains("..") && !parsed.getPath().contains("%");
        }catch(IllegalArgumentException error){return false;}
    }
}

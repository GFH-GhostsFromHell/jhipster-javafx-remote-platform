package com.gfh.industrial;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DesktopReleaseInfoTest {
    @Test void onlyTrustedGithubReleasePathsAreAdvertised() {
        assertTrue(DesktopReleaseInfo.validDownloadUrl(
            "https://github.com/GFH-GhostsFromHell/jhipster-javafx-remote-platform/releases/download/v1/app.msi"));
        assertFalse(DesktopReleaseInfo.validDownloadUrl(
            "http://github.com/GFH-GhostsFromHell/jhipster-javafx-remote-platform/releases/download/v1/app.msi"));
        assertFalse(DesktopReleaseInfo.validDownloadUrl(
            "https://evil.test/GFH-GhostsFromHell/jhipster-javafx-remote-platform/releases/download/v1/app.msi"));
        assertFalse(DesktopReleaseInfo.validDownloadUrl(
            "https://github.com/GFH-GhostsFromHell/jhipster-javafx-remote-platform/releases/download/v1/../../app.msi"));
        assertFalse(DesktopReleaseInfo.validDownloadUrl(
            "https://github.com/GFH-GhostsFromHell/jhipster-javafx-remote-platform/releases/download/v1/app.msi#fragment"));
        assertFalse(DesktopReleaseInfo.validDownloadUrl(
            "https://github.com/GFH-GhostsFromHell/jhipster-javafx-remote-platform/releases/download/v1/app.exe"));
    }

    @Test void noReleaseByDefaultUnlessExplicitlyProvisioned() {
        var manifest=DesktopReleaseInfo.current();
        assertTrue(manifest.containsKey("released"));
        assertTrue(manifest.containsKey("signed"));
        assertTrue(manifest.containsKey("version"));
        assertTrue(manifest.containsKey("downloadUrl"));
        assertFalse(manifest.get("downloadUrl").toString().contains("token"));
    }
}

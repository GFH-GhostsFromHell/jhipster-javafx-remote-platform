package com.gfh.industrial.hmi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DesktopSoftwareUpdatesTest {
    private final ObjectMapper json=new ObjectMapper();

    @Test void comparesFullThreePartVersionsNumerically() {
        assertTrue(DesktopSoftwareUpdates.newerThan("0.1.110","0.1.99"));
        assertTrue(DesktopSoftwareUpdates.newerThan("2.0.0","1.999.999"));
        assertFalse(DesktopSoftwareUpdates.newerThan("0.1.10","0.1.10"));
        assertFalse(DesktopSoftwareUpdates.newerThan("0.1.9","0.1.10"));
        assertFalse(DesktopSoftwareUpdates.newerThan("testing","0.1.0"));
        assertFalse(DesktopSoftwareUpdates.newerThan("0.1.3","testing"));
    }

    @Test void rejectsUnpublishedUnsignedAndOtherHosts() throws Exception {
        var unsigned=json.readTree("""
            {"released":true,"signed":false,"version":"0.1.200","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","publisher":"Acme","downloadUrl":"https://github.com/GFH-GhostsFromHell/jhipster-javafx-remote-platform/releases/download/v1/app.msi"}
            """);
        assertNull(DesktopSoftwareUpdates.safeRelease(unsigned));
        var untrusted=json.readTree("""
            {"released":true,"signed":true,"version":"0.1.200","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","publisher":"Acme","downloadUrl":"https://attacker.example/files/app.msi"}
            """);
        assertNull(DesktopSoftwareUpdates.safeRelease(untrusted));
        var valid=json.readTree("""
            {"released":true,"signed":true,"version":"0.1.200","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","publisher":"Industrial Control Studio","downloadUrl":"https://github.com/GFH-GhostsFromHell/jhipster-javafx-remote-platform/releases/download/v0.1.200/IndustrialControlStudioHMI-Setup.msi"}
            """);
        assertNotNull(DesktopSoftwareUpdates.safeRelease(valid));
        assertEquals("0.1.200",DesktopSoftwareUpdates.safeRelease(valid).version());
    }
}

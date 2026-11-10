package com.gfh.industrial;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class WorkstationSetupPackageResourceTest {
    @Test void allowsLocalhostAndHttpsButNeverRemoteHttpOrEmbeddedCredentials() {
        assertEquals("http://127.0.0.1:8090", WorkstationSetupPackageResource.validateBaseUrl("http://127.0.0.1:8090/"));
        assertEquals("https://gateway.example.com",WorkstationSetupPackageResource.validateBaseUrl("https://gateway.example.com"));
        for(String rejected: new String[]{"http://plc.example.com:8080","https://username:pass@gateway.example.com",
                "https://gateway.example.com/path","ftp://gateway.example.com","https://gateway.example.com?token=abc"}) {
            assertThrows(IllegalArgumentException.class,()->WorkstationSetupPackageResource.validateBaseUrl(rejected));
        }
    }
    @Test void bundleContainsOnlySettingsAndInstructionsNotSecrets() throws Exception {
        byte[] zip=WorkstationSetupPackageResource.buildZip(new ObjectMapper(),
            Map.of("format","industrial-studio-pc-v1","serverUrl","https://gateway.example.com",
                "workstationId","controlpc","viewerUsername","studio-viewer","assignedScreen","overview"));
        Map<String,String> entries=new HashMap<>();
        try (ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(zip),StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while((entry=in.getNextEntry())!=null) entries.put(entry.getName(),
                new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
        assertEquals(java.util.Set.of("workstation.json","README.txt","Install-Industrial-HMI.ps1"),entries.keySet());
        String config=entries.get("workstation.json");
        assertTrue(config.contains("controlpc"));
        assertFalse(config.contains("password"));
        assertFalse(config.contains("token"));
        assertFalse(config.contains("secret"));
        assertTrue(entries.get("README.txt").contains("standard IndustrialControlStudioHMI Windows MSI"));
    }
}

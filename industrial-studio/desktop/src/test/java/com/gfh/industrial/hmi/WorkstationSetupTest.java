package com.gfh.industrial.hmi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class WorkstationSetupTest {
    @TempDir Path tmp;
    private static final String VALID="""
        {"format":"industrial-studio-pc-v1","serverUrl":"https://ot.example.org/",
         "workstationId":"operatorpc","workstationName":"Operator Console",
         "viewerUsername":"studio-viewer","assignedScreen":"overview"}
        """;
    @Test void parsesSettingsOnly() throws Exception {
        var profile=WorkstationSetup.parse(VALID.getBytes(StandardCharsets.UTF_8));
        assertEquals("operatorpc",profile.workstationId());
        assertEquals("https://ot.example.org",profile.serverUrl());
    }
    @Test void rejectsRemoteHttpAndSecrets() {
        String insecure=VALID.replace("https://ot.example.org/","http://plc.example.org/");
        assertThrows(Exception.class,()->WorkstationSetup.parse(insecure.getBytes(StandardCharsets.UTF_8)));
        String secret=VALID.replace("\"assignedScreen\":\"overview\"",
            "\"assignedScreen\":\"overview\",\"deviceSecret\":\"never embed\"");
        assertThrows(Exception.class,()->WorkstationSetup.parse(secret.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void safelyImportsOnlyNamedJsonEntry() throws Exception {
        Path zip=tmp.resolve("pc.zip"), target=tmp.resolve("config").resolve("workstation.json");
        try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(zip))){
            out.putNextEntry(new ZipEntry("../other.properties"));out.write("ignored".getBytes(StandardCharsets.UTF_8));out.closeEntry();
            out.putNextEntry(new ZipEntry("workstation.json"));out.write(VALID.getBytes(StandardCharsets.UTF_8));out.closeEntry();
        }
        var profile=WorkstationSetup.importZip(zip,target);
        assertEquals("operatorpc",profile.workstationId());
        assertTrue(Files.exists(target));
        assertFalse(Files.exists(tmp.resolve("other.properties")));
    }
}

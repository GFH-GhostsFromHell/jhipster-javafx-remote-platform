package com.gfh.industrial.hmi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SecureWorkstationStoreTest {
    @TempDir Path tmp;

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsRoundTripProtectsSecretAtRest() throws Exception {
        var session=new SecureWorkstationStore.Session("https://example.com","pump-room-1",
            "Pump Room","overview","B".repeat(43));
        Path file=tmp.resolve("secure.json");
        SecureWorkstationStore.save(session,file);
        assertFalse(Files.readString(file).contains("B".repeat(43)));
        var recovered=SecureWorkstationStore.load(file);
        assertEquals(session.deviceSecret(),recovered.deviceSecret());
        assertEquals(session.workstationId(),recovered.workstationId());
    }
}

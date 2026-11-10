package com.gfh.industrial.hmi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AppliedConfigurationStoreTest {
    @TempDir Path folder;
    @Test void retainsAppliedRevisionForOneWorkstationAfterRestart() throws Exception {
        Path file=folder.resolve("applied.json");
        assertEquals("",AppliedConfigurationStore.load(file,"https://example.com","pc1"));
        AppliedConfigurationStore.save(file,"https://example.com","pc1","overview:2:4:1");
        assertEquals("overview:2:4:1",AppliedConfigurationStore.load(file,"https://example.com","pc1"));
        assertEquals("",AppliedConfigurationStore.load(file,"https://example.com","pc2"));
        assertEquals("",AppliedConfigurationStore.load(file,"https://different.example.com","pc1"));
        assertFalse(Files.readString(file).contains("deviceSecret"));
    }
    @Test void rejectsUnsafeRevisionMetadata() {
        Path file=folder.resolve("applied.json");
        assertThrows(Exception.class,()->AppliedConfigurationStore.save(file,"http://insecure.example.com","pc1","overview:2:1:0"));
        assertThrows(Exception.class,()->AppliedConfigurationStore.save(file,"https://example.com","pc1","../application.jar"));
    }
}

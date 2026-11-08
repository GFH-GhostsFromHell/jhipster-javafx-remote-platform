package com.example.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalWorkspaceStoreSafetyTest {
    @TempDir Path temp;

    @Test void savingWorksheetsPreservesOtherReportsAndCreatesRecoverableBackup() throws Exception {
        ObjectMapper json=new ObjectMapper();
        LocalWorkspaceStore store=new LocalWorkspaceStore(json,"archive-test",temp);
        ObjectNode original=json.createObjectNode();
        original.put("kind","otherReport");
        original.put("title","Previously saved equipment design");
        store.append("designReports",original);
        assertEquals(1,store.records("designReports").size());
        ObjectNode circuit=json.createObjectNode();
        circuit.put("kind","circuitWorksheet");
        circuit.put("project","Mine");
        circuit.put("title","Feeder");
        circuit.put("revision",1);
        store.append("designReports",circuit);
        assertEquals(2,store.records("designReports").size());
        Path backup=store.file().resolveSibling(store.file().getFileName()+".bak");
        assertTrue(Files.exists(backup),"Old contents must be backed up");
        assertTrue(Files.readString(backup).contains("Previously saved equipment design"));
        LocalWorkspaceStore again=new LocalWorkspaceStore(json,"archive-test",temp);
        assertEquals(2,again.records("designReports").size(),"saved data must survive a reopen");
        assertTrue(again.records("designReports").get(1).path("_syncId").isTextual());
    }

    @Test void corruptedStoreIsNeverSilentlyReplacedWithEmptyData() throws Exception {
        LocalWorkspaceStore store=new LocalWorkspaceStore(new ObjectMapper(),"archive-test",temp);
        Files.createDirectories(store.file().getParent());
        Files.writeString(store.file(),"{broken JSON");
        assertThrows(IllegalStateException.class,store::load);
        assertThrows(IllegalStateException.class,
            ()->store.append("designReports",new ObjectMapper().createObjectNode()));
        assertEquals("{broken JSON",Files.readString(store.file()),
            "Corrupted original must remain untouched for recovery");
    }

    @Test void separateAccountStoragePreventsAccidentalCrossAccountVisibility() {
        ObjectMapper mapper=new ObjectMapper();
        LocalWorkspaceStore a=new LocalWorkspaceStore(mapper,"site-A",temp);
        LocalWorkspaceStore b=new LocalWorkspaceStore(mapper,"site-B",temp);
        ObjectNode record=mapper.createObjectNode();record.put("project","Confidential site");
        a.append("designReports",record);
        assertEquals(1,a.records("designReports").size());
        assertEquals(0,b.records("designReports").size());
        assertNotEquals(a.file(),b.file());
    }
}

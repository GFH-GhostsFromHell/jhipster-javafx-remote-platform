package com.gfh.devicesecurity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.crypto.AEADBadTagException;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SelectedFileCryptoTest {
    @TempDir Path working;

    @Test void roundTripDoesNotModifyOrRemoveOriginal() throws Exception {
        Path file = working.resolve("meeting-notes.txt");
        byte[] original = "User-owned test data with Unicode: café".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(file, original);
        byte[] key = SelectedFileCrypto.createKey();
        String offlineRecovery = SelectedFileCrypto.recoveryText(key);
        Path encrypted = SelectedFileCrypto.encrypt(file, key, (n, total) -> { });
        assertTrue(Files.exists(file));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertNotEquals(new String(original), Files.readString(encrypted));
        Path restored = SelectedFileCrypto.decrypt(encrypted,
            SelectedFileCrypto.readRecoveryText(offlineRecovery), (n, total) -> { });
        assertArrayEquals(original, Files.readAllBytes(restored));
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test void refuseOverwriteAndWrongKeyDoesNotProduceRestoredFile() throws Exception {
        Path file = working.resolve("design-data");
        Files.writeString(file, "Important original");
        byte[] first = SelectedFileCrypto.createKey();
        Path encrypted = SelectedFileCrypto.encrypt(file, first, null);
        assertThrows(FileAlreadyExistsException.class,
            () -> SelectedFileCrypto.encrypt(file, first, null));
        byte[] wrong = SelectedFileCrypto.createKey();
        assertThrows(Exception.class, () -> SelectedFileCrypto.decrypt(encrypted, wrong, null));
        assertFalse(Files.exists(working.resolve("design-data.restored")));
        assertEquals("Important original", Files.readString(file));
        assertTrue(Files.exists(encrypted));
    }

    @Test void rejectDirectory() throws Exception {
        assertThrows(IllegalArgumentException.class,
            () -> SelectedFileCrypto.encrypt(working, SelectedFileCrypto.createKey(), null));
    }

    @Test void tamperedCiphertextCannotBeRestored() throws Exception {
        Path file = working.resolve("sample");
        Files.writeString(file, "Hello");
        byte[] key = SelectedFileCrypto.createKey();
        Path encrypted = SelectedFileCrypto.encrypt(file, key, null);
        byte[] bytes = Files.readAllBytes(encrypted);
        bytes[bytes.length - 1] ^= 0x01;
        Files.write(encrypted, bytes);
        assertThrows(Exception.class, () -> SelectedFileCrypto.decrypt(encrypted, key, null));
        assertFalse(Files.exists(working.resolve("sample.restored")));
        assertEquals("Hello", Files.readString(file));
    }
}

package com.gfh.devicesecurity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DiskEncryptionStatusTest {
    @Test void fullyProtectedDrive() {
        assertEquals("ENCRYPTED", DiskEncryptionStatus.classify(
            "Conversion Status: Fully Encrypted\nProtection Status: Protection On"));
    }
    @Test void fullyDecryptedDrive() {
        assertEquals("NOT_ENCRYPTED", DiskEncryptionStatus.classify(
            "Conversion Status: Fully Decrypted\nProtection Status: Protection Off"));
    }
    @Test void encryptionInProgress() {
        assertEquals("IN_PROGRESS", DiskEncryptionStatus.classify(
            "Conversion Status: Encryption in Progress"));
    }
    @Test void suspendedProtectionIsNotReportedAsProtected() {
        assertEquals("UNKNOWN", DiskEncryptionStatus.classify(
            "Conversion Status: Fully Encrypted\nProtection Status: Protection Off"));
    }
    @Test void unknownIsFailClosed() {
        assertEquals("UNKNOWN", DiskEncryptionStatus.classify("BitLocker information unavailable"));
    }
}

package com.gfh.devicesecurity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupportMessageCryptoTest {
    @Test void encryptedChatRoundTrip() throws Exception {
        char[] password = "a strong shared passphrase for test".toCharArray();
        String message = "We need a recovery review. 🔒";
        String sealed = SupportMessageCrypto.seal(message, password);
        assertFalse(sealed.contains("We need"));
        assertTrue(sealed.startsWith("v1."));
        assertEquals(message, SupportMessageCrypto.open(sealed, password));
    }

    @Test void wrongPassphraseCannotReadChat() throws Exception {
        String sealed = SupportMessageCrypto.seal("Secret test", "very strong password one".toCharArray());
        assertThrows(Exception.class,
            () -> SupportMessageCrypto.open(sealed, "very strong password two".toCharArray()));
    }

    @Test void rejectsWeakPassword() {
        assertThrows(IllegalArgumentException.class,
            () -> SupportMessageCrypto.seal("hello", "short".toCharArray()));
    }
}

package com.gfh.industrial.hmi;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PairingCodeTest {
    private static final String TOKEN="A".repeat(43);

    @Test void readsOnePasteSetupCode() {
        var code=PairingCode.parse("ICS1|https://industrial-control-studio-demo.onrender.com|pump-room-1|"+TOKEN);
        assertEquals("pump-room-1",code.workstationId());
        assertEquals("https://industrial-control-studio-demo.onrender.com",code.serverUrl());
        assertEquals(TOKEN,code.pairingSecret());
    }
    @Test void rejectsUnsafeAndIncompleteCodes() {
        assertThrows(IllegalArgumentException.class,()->PairingCode.parse("invalid"));
        assertThrows(IllegalArgumentException.class,()->PairingCode.parse("ICS1|http://example.com|pump-room-1|"+TOKEN));
        assertThrows(IllegalArgumentException.class,()->PairingCode.parse("ICS1|https://example.com|pump-room-1|short"));
        assertThrows(IllegalArgumentException.class,()->PairingCode.parse("ICS1|https://user:pass@example.com|pump-room-1|"+TOKEN));
        assertThrows(IllegalArgumentException.class,()->PairingCode.parse("ICS1|https://example.com/evil|pump-room-1|"+TOKEN));
    }
}

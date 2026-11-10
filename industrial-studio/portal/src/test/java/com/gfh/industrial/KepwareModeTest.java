package com.gfh.industrial;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KepwareModeTest {
    @Test void simulationDoesNotConnect() {
        var source=new KepwareOpcClient(null,null,"SIMULATION","","","","");
        assertFalse(source.enabled());
        assertEquals("SIMULATION",source.mode());
        assertDoesNotThrow(source::refresh);
    }
    @Test void rejectInsecureOrMissingKepwareProvisioning() {
        assertThrows(IllegalStateException.class,
            ()->new KepwareOpcClient(null,null,"KEPWARE","opc.tcp://public.example:49320","","",""));
        assertThrows(IllegalStateException.class,
            ()->new KepwareOpcClient(null,null,"WANT_TO_WRITE","","","",""));
    }
}

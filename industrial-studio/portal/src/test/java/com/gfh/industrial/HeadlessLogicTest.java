package com.gfh.industrial;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class HeadlessLogicTest {
    @Test void dryRunOnlyAndClampsOutput(){
        var rules=HeadlessLogic.compile(
            "# no PLC writes\nSIM_WRITE desiredSpeed = CLAMP(tankLevel * 2 + 5, 0, 100)",
            Set.of("tankLevel"));
        var values=HeadlessLogic.preview(rules,Map.of("tankLevel",70d));
        assertEquals(1,values.size());
        assertEquals(100d,values.getFirst().value());
        assertEquals("DRY_RUN_ONLY",values.getFirst().mode());
        assertEquals("SIMULATED_WRITE_NOT_SENT",values.getFirst().quality());
    }
    @Test void unknownInputsArbitraryCodeAndDuplicateOutputsAreRejected(){
        assertThrows(IllegalArgumentException.class,
            ()->HeadlessLogic.compile("System.exit(0);",Set.of("tankLevel")));
        assertThrows(IllegalArgumentException.class,
            ()->HeadlessLogic.compile("SIM_WRITE out = CLAMP(other * 1 + 0, 0, 100)",Set.of("tankLevel")));
        assertThrows(IllegalArgumentException.class,
            ()->HeadlessLogic.compile("SIM_WRITE out = CLAMP(tankLevel * 1 + 0, 0, 100)\n"+
                "SIM_WRITE out = CLAMP(tankLevel * 1 + 0, 0, 100)",Set.of("tankLevel")));
        assertThrows(IllegalArgumentException.class,
            ()->HeadlessLogic.compile("SIM_WRITE tankLevel = CLAMP(tankLevel * 1 + 0, 0, 100)",
                Set.of("tankLevel")));
    }
    @Test void missingInputNeverCreatesWriteCandidate(){
        var rules=HeadlessLogic.compile(
            "SIM_WRITE desiredSpeed = CLAMP(tankLevel * 1 + 0, 0, 100)",Set.of("tankLevel"));
        assertTrue(HeadlessLogic.preview(rules,Map.of()).isEmpty());
    }
}

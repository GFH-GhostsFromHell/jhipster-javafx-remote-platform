package com.gfh.industrial;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class ShowcaseApiTest {
    @Test void publicShowcaseIsAlwaysSimulatedAndReadOnly() {
        var sample=ShowcaseApi.sampleAt(1_700_000_000_000L);
        assertEquals("SIMULATION",sample.mode());
        assertTrue(sample.readOnly());
        assertFalse(sample.realPlcConnected());
        assertEquals(3,sample.panels().size());
        assertEquals(15,sample.panels().stream().mapToInt(p->p.points().size()).sum());
        var ids=new HashSet<String>();
        for(var panel:sample.panels()){
            assertTrue(ids.add(panel.id()));
            assertFalse(panel.name().isBlank());
            for(var metric:panel.points()){
                assertEquals("SIMULATED",metric.quality());
                assertTrue(Double.isFinite(metric.value()));
                assertTrue(metric.min()<metric.max());
                assertTrue(metric.value()>=metric.min());
                assertTrue(metric.value()<=metric.max());
                assertTrue(metric.state().equals("NORMAL") ||
                    metric.state().equals("SIMULATED_LIMIT"));
            }
        }
    }

    @Test void multipleSamplesChangeOverTimeWithoutChangingDataSource() {
        var a=ShowcaseApi.sampleAt(1_700_000_000_000L);
        var b=ShowcaseApi.sampleAt(1_700_000_020_000L);
        var speedA=a.panels().getFirst().points().get(1);
        var speedB=b.panels().getFirst().points().get(1);
        assertNotEquals(speedA.value(),speedB.value());
        assertEquals("SIMULATED",speedB.quality());
        assertFalse(b.realPlcConnected());
    }
}

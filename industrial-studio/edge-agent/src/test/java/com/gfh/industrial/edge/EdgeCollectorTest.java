package com.gfh.industrial.edge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EdgeCollectorTest {
    private final ObjectMapper json=new ObjectMapper();

    @Test void rejectsUnsecuredRemoteServers() {
        assertThrows(IllegalArgumentException.class,
            ()->EdgeCollector.validBase("http://minesite.example.com"));
        assertThrows(IllegalArgumentException.class,
            ()->EdgeCollector.validBase("https://host.example.com/path"));
        assertThrows(IllegalArgumentException.class,
            ()->EdgeCollector.validBase("https://user:password@host.example.com"));
        assertEquals("https://example.com",EdgeCollector.validBase("https://example.com/"));
        assertEquals("http://127.0.0.1:8090",
            EdgeCollector.validBase("http://127.0.0.1:8090"));
    }
    @Test void deterministicDemoSamplesContainFiniteValues() throws Exception {
        var tags=json.readTree("""
          [{"id":"tankLevel"},{"id":"pumpSpeed"}]
          """);
        var values=EdgeCollector.simulation(tags,123456L);
        assertEquals(2,values.size());
        assertTrue(values.values().stream().allMatch(Double::isFinite));
    }
    @Test void repeatedTagIdRejected() throws Exception {
        var tags=json.readTree("""
          [{"id":"tankLevel"},{"id":"tankLevel"}]
          """);
        assertThrows(IllegalArgumentException.class,()->EdgeCollector.simulation(tags,12345L));
    }
}

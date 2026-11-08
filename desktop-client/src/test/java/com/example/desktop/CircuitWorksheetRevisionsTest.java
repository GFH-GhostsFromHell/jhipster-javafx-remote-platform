package com.example.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CircuitWorksheetRevisionsTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static ObjectNode record(String type,String project,String title,int rev) {
        ObjectNode n=JSON.createObjectNode();
        n.put("kind",type);n.put("project",project);n.put("title",title);n.put("revision",rev);
        return n;
    }

    @Test void emptyProjectStartsFirstRevision() {
        assertEquals(1,CircuitWorksheetRevisions.next(JSON.createArrayNode(),"Mine","Feeder"));
    }
    @Test void projectAndTitleScopeRevisionNumbers() {
        ArrayNode data=JSON.createArrayNode();
        data.add(record("circuitWorksheet","Mine","Feeder",1));
        data.add(record("circuitWorksheet","Mine","Feeder",3));
        data.add(record("circuitWorksheet","Mine","Lights",9));
        data.add(record("circuitWorksheet","Shop","Feeder",11));
        data.add(record("transformerDesign","Mine","Feeder",50));
        assertEquals(4,CircuitWorksheetRevisions.next(data,"Mine","Feeder"));
        assertEquals(10,CircuitWorksheetRevisions.next(data,"Mine","Lights"));
        assertEquals(12,CircuitWorksheetRevisions.next(data,"Shop","Feeder"));
        assertEquals(1,CircuitWorksheetRevisions.next(data,"Mine","Motor"));
        assertEquals(1,CircuitWorksheetRevisions.next(data,"Other","Feeder"));
    }
    @Test void missingIdentityCannotBeSaved() {
        assertThrows(IllegalArgumentException.class,
            ()->CircuitWorksheetRevisions.next(JSON.createArrayNode(),"","Feeder"));
        assertThrows(IllegalArgumentException.class,
            ()->CircuitWorksheetRevisions.next(JSON.createArrayNode(),"Mine"," "));
    }
}

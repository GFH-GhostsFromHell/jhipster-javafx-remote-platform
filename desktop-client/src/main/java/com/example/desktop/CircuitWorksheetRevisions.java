package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/** Pure revision policy for append-only project circuit worksheets. */
final class CircuitWorksheetRevisions {
    private CircuitWorksheetRevisions() {}
    static int next(ArrayNode records,String project,String title) {
        if(project==null||project.isBlank()||title==null||title.isBlank())
            throw new IllegalArgumentException("Project and worksheet title are required.");
        int n=1;
        for(JsonNode row:records) {
            if(!"circuitWorksheet".equals(row.path("kind").asText()))continue;
            if(project.equals(row.path("project").asText()) &&
               title.equals(row.path("title").asText()))
                n=Math.max(n,row.path("revision").asInt(0)+1);
        }
        return n;
    }
}

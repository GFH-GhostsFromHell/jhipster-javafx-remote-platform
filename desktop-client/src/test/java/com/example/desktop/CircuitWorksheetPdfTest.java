package com.example.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CircuitWorksheetPdfTest {
    private final ObjectMapper json=new ObjectMapper();

    private ObjectNode example(){
        ObjectNode r=json.createObjectNode();
        r.put("kind","circuitWorksheet");
        r.put("id","unit-123");
        r.put("_syncId","unit-123");
        r.put("project","Hospital renovation");
        r.put("title","Feeder-A");
        r.put("revision",3);
        r.put("saved","2026-11-08T14:30:00Z");
        r.put("codeEdition","CEC_2024");
        r.put("jurisdiction","Ontario, Canada");
        r.put("referenceTables","Table 2; Tables 5A and 5C (user supplied)");
        ObjectNode inputs=r.putObject("inputs");
        inputs.put("loadA",40);
        inputs.put("designMultiplier",1.25);
        inputs.put("voltageV",600);
        inputs.put("phases",3);
        inputs.put("oneWayLengthM",40);
        inputs.put("conductorDescription","Copper conductor — example");
        inputs.put("tableReference","Table 2");
        inputs.put("terminalC",75);
        inputs.put("insulationC",90);
        inputs.put("terminalColumnAmpacityA",85);
        inputs.put("ambientFactor",1);
        inputs.put("groupingFactor",1);
        inputs.put("circularMils",66360);
        inputs.put("kOhmCircularMilPerFoot",12.9);
        inputs.put("maxVoltageDropPercent",3);
        inputs.put("selectedOcpdA",50);
        ObjectNode results=r.putObject("results");
        results.put("codeComplianceVerified",false);
        results.put("designCurrentA",50);
        results.put("adjustedAmpacityA",85);
        results.put("conductorMarginA",35);
        results.put("voltageDropV",1.76746);
        results.put("voltageDropPercent",0.294576);
        results.put("allowableDropV",18);
        results.put("minimumCircularMilsForDrop",6516.03);
        results.put("maximumOneWayLengthM",407.365);
        results.put("ampacityArithmeticFits",true);
        results.put("voltageDropArithmeticFits",true);
        results.put("protectionScreenFits",true);
        r.putObject("assumptions").put("notes","Confirm protective device at actual terminals.");
        r.put("result","Original saved calculation \n 40 A x 1.25 = 50 A\nNOT CODE VERIFIED");
        return r;
    }

    @Test void generatedPdfIsReadableAndContainsTraceabilityAndSafetyWarnings() throws Exception {
        byte[] bytes=CircuitWorksheetPdf.render(example(),"Example client","North panel","Field technician");
        assertTrue(bytes.length>2500);
        assertEquals("%PDF-",new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII));
        try(PDDocument doc=Loader.loadPDF(bytes)){
            assertTrue(doc.getNumberOfPages()>=2);
            String content=new PDFTextStripper().getText(doc);
            for(String phrase:new String[]{
                "Hospital renovation","Feeder-A","Revision","unit-123","2026-11-08",
                "Example client","North panel","Field technician","NOT CODE VERIFIED",
                "OESC_ON_2024","AUTHORIZED_TABLE_VERIFICATION_REQUIRED",
                "Termination temperature","Adjusted",
                "Minimum circular mils for VD","Reviewer","Signature"
            })assertTrue(content.contains(phrase),"Missing PDF content: "+phrase);
        }
    }

    @Test void longCalculationSplitsIntoMultiplePagesWithoutLosingConclusion() throws Exception {
        ObjectNode r=example();
        StringBuilder report=new StringBuilder();
        for(int i=0;i<160;i++)report.append("Worksheet calculation step ").append(i).append("\n");
        report.append("FINAL REVIEW REQUIRED");
        r.put("result",report.toString());
        try(PDDocument doc=Loader.loadPDF(CircuitWorksheetPdf.render(r,"","",""))){
            assertTrue(doc.getNumberOfPages()>=4);
            String text=new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Worksheet calculation step 0"));
            assertTrue(text.contains("Worksheet calculation step 159"));
            assertTrue(text.contains("FINAL REVIEW REQUIRED"));
        }
    }

    @Test void unsavedOrFalselyCertifiedRecordsAreRejected() throws Exception {
        ObjectNode wrongKind=example();
        wrongKind.put("kind","otherReport");
        assertThrows(IllegalArgumentException.class,()->CircuitWorksheetPdf.render(wrongKind,"","",""));
        ObjectNode falseApproval=example();
        falseApproval.with("results").put("codeComplianceVerified",true);
        assertThrows(IllegalArgumentException.class,()->CircuitWorksheetPdf.render(falseApproval,"","",""));
    }
}

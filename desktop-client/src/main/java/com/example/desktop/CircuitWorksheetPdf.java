package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Professional, offline PDF report for a SAVED circuit worksheet revision.
 * This is a planning document, never an approval/engineer seal.
 * PDFBox standard fonts use safe ASCII transliteration to avoid bad glyphs.
 */
final class CircuitWorksheetPdf {
    private CircuitWorksheetPdf() { }

    static byte[] render(JsonNode record, String client, String site, String reviewer) throws IOException {
        if(record==null || !"circuitWorksheet".equals(record.path("kind").asText())
            || !record.path("inputs").isObject() || !record.path("results").isObject()
            || record.path("project").asText().isBlank()
            || record.path("title").asText().isBlank())
            throw new IllegalArgumentException("Select a saved circuitWorksheet revision with complete inputs and results.");
        if(record.path("results").path("codeComplianceVerified").asBoolean(true))
            throw new IllegalArgumentException("A report may not claim Code compliance without a verified engineering approval process.");

        JsonNode in=record.path("inputs"),out=record.path("results"),assumptions=record.path("assumptions");
        List<String> lines=new ArrayList<>();
        lines.add("ELECTRICAL WORKBENCH  |  CIRCUIT CALCULATION RECORD");
        lines.add("PRELIMINARY - NOT CODE VERIFIED - NOT FOR INSTALLATION APPROVAL");
        lines.add("");
        lines.add("PROJECT & REVISION");
        add(lines,"Project",record.path("project"));
        add(lines,"Worksheet",record.path("title"));
        add(lines,"Revision",record.path("revision"));
        add(lines,"Saved UTC",record.path("saved"));
        add(lines,"Record ID",record.path("_syncId").asText(record.path("id").asText("")));
        lines.add("Export generated UTC: "+Instant.now());
        lines.add("Client / employer: "+blank(client));
        lines.add("Site / installation: "+blank(site));
        lines.add("");
        lines.add("CODE REFERENCE & VERIFICATION");
        lines.add("Edition: "+record.path("codeEdition").asText("CEC_2024"));
        lines.add("Jurisdiction: "+record.path("jurisdiction").asText("Ontario, Canada"));
        lines.add("Data status: AUTHORIZED_TABLE_VERIFICATION_REQUIRED");
        lines.add("Reference profile: OESC_ON_2024 / CSA C22.1:24");
        lines.add("Entered tables: "+record.path("referenceTables").asText(
            in.path("tableReference").asText("User supplied")+", Tables 5A / 5C"));
        lines.add("Table values verified: NO    Terminal markings verified: NO");
        lines.add("Code compliance verified: NO");
        lines.add("");
        lines.add("CIRCUIT AND CONDUCTOR INPUTS");
        add(lines,"Actual load (A)",in.path("loadA"));
        add(lines,"General design multiplier",in.path("designMultiplier"));
        add(lines,"System voltage (V)",in.path("voltageV"));
        add(lines,"Phases",in.path("phases"));
        add(lines,"One-way length (m)",in.path("oneWayLengthM"));
        add(lines,"Selected conductor",in.path("conductorDescription"));
        add(lines,"Ampacity table",in.path("tableReference"));
        add(lines,"Termination temperature (C)",in.path("terminalC"));
        add(lines,"Insulation rating (C)",in.path("insulationC"));
        add(lines,"User-entered table ampacity (A)",in.path("terminalColumnAmpacityA"));
        add(lines,"Ambient correction factor",in.path("ambientFactor"));
        add(lines,"Grouping correction factor",in.path("groupingFactor"));
        add(lines,"Conductor circular mils",in.path("circularMils"));
        add(lines,"Resistance K factor",in.path("kOhmCircularMilPerFoot"));
        add(lines,"User-selected VD target (%)",in.path("maxVoltageDropPercent"));
        add(lines,"Entered protection device (A)",in.path("selectedOcpdA"));
        lines.add("");
        lines.add("ARITHMETIC RESULTS (NOT CODE APPROVAL)");
        add(lines,"Required design current (A)",out.path("designCurrentA"));
        add(lines,"Corrected conductor ampacity (A)",out.path("adjustedAmpacityA"));
        add(lines,"Conductor margin (A)",out.path("conductorMarginA"));
        add(lines,"Calculated voltage drop (V)",out.path("voltageDropV"));
        add(lines,"Calculated voltage drop (%)",out.path("voltageDropPercent"));
        add(lines,"Entered allowable drop (V)",out.path("allowableDropV"));
        add(lines,"Minimum circular mils for VD",out.path("minimumCircularMilsForDrop"));
        add(lines,"Maximum one-way length (m)",out.path("maximumOneWayLengthM"));
        add(lines,"Ampacity arithmetic",out.path("ampacityArithmeticFits").asBoolean()?"WITHIN ENTERED BOUNDS":"REVIEW / FAIL");
        add(lines,"Voltage-drop arithmetic",out.path("voltageDropArithmeticFits").asBoolean()?"WITHIN ENTERED TARGET":"REVIEW / FAIL");
        add(lines,"Conservative OCPD screen",out.path("protectionScreenFits").asBoolean()?"WITHIN INPUT BOUNDS":"REVIEW");
        lines.add("");
        lines.add("FIELD NOTES AND ASSUMPTIONS");
        lines.add("Notes: "+assumptions.path("notes").asText(""));
        lines.add("K-factor method: resistive approximation; does not model AC");
        lines.add("reactance, full Table D3 impedance or upstream feeder voltage drop.");
        lines.add("Special circuit rules verified: NO");
        lines.add("");
        lines.add("ORIGINAL SAVED WORKED CALCULATION");
        String savedText=record.path("result").asText("");
        if(savedText.isBlank())lines.add("No worked calculation text saved.");
        else for(String t:savedText.replace("\r","").split("\n"))lines.add(t);
        lines.add("");
        lines.add("REVIEW / SIGN-OFF (NOT AUTOMATICALLY AUTHORIZED)");
        lines.add("Reviewed by: "+blank(reviewer));
        lines.add("Verification date: ______________________");
        lines.add("Signature: ___________________________");
        lines.add("Installation authorization: NOT PROVIDED BY THIS REPORT");
        lines.add("");
        lines.add("WARNING: Verify current OESC/CEC tables, equipment markings, load");
        lines.add("classification, neutral count, wiring method, conductor sizing,");
        lines.add("overcurrent device, bonding and upstream voltage drop independently.");

        PDType1Font regular=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font bold=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(PDDocument doc=new PDDocument()) {
            PDPage page=null;
            PDPageContentStream stream=null;
            float y=0;
            int pageNumber=0;
            final float margin=46,top=742,bottom=58;
            try {
                for(String raw:lines){
                    for(String wrapped:wrap(ascii(raw),92)){
                        if(stream==null||y<bottom){
                            if(stream!=null){stream.close();stream=null;}
                            page=new PDPage(PDRectangle.LETTER);
                            doc.addPage(page);pageNumber++;
                            stream=new PDPageContentStream(doc,page);
                            printLine(stream,bold,9,margin,760,
                                "ELECTRICAL WORKBENCH | "+ascii(record.path("project").asText(""))+
                                " | Page "+pageNumber);
                            stream.setStrokingColor(120f/255f,150f/255f,170f/255f);
                            stream.moveTo(margin,751);stream.lineTo(565,751);stream.stroke();
                            y=top;
                        }
                        boolean heading=isHeading(wrapped);
                        printLine(stream,heading?bold:regular,heading?10:9.1f,margin,y,wrapped);
                        y-=heading?15:12;
                    }
                }
            } finally {
                if(stream!=null)stream.close();
            }
            doc.save(output);
        }
        return output.toByteArray();
    }

    private static void add(List<String> lines,String name,JsonNode value){
        lines.add(name+": "+(value.isMissingNode()||value.isNull()?"Not supplied":value.asText()));
    }
    private static void add(List<String> lines,String name,String value){lines.add(name+": "+value);}
    private static String blank(String s){return s==null||s.isBlank()?"____________________":s.trim();}
    private static void printLine(PDPageContentStream stream,PDType1Font font,float size,
                                  float x,float y,String s) throws IOException {
        stream.beginText();stream.setFont(font,size);stream.newLineAtOffset(x,y);
        stream.showText(ascii(s));stream.endText();
    }
    private static boolean isHeading(String s){
        return !s.isBlank()&&s.length()<83&&s.matches(".*[A-Z].*")&&
            s.equals(s.toUpperCase(Locale.ROOT))&&!s.contains(": ");
    }
    private static List<String> wrap(String s,int maxChars){
        List<String> out=new ArrayList<>();
        if(s.isEmpty()){out.add("");return out;}
        while(s.length()>maxChars){
            int cut=s.lastIndexOf(' ',maxChars);
            if(cut<20)cut=maxChars;
            out.add(s.substring(0,cut));
            s=s.substring(cut).stripLeading();
        }
        out.add(s);return out;
    }
    private static String ascii(String s){
        if(s==null)return "";
        return s.replace("°"," deg ").replace("φ","phase").replace("√","sqrt")
            .replace("×","x").replace("→","->").replace("≤","<=")
            .replace("≥",">=").replace("—","-").replace("–","-")
            .replace("²","^2").replace("•","-")
            .replaceAll("[^\\x20-\\x7E]","?");
    }
}

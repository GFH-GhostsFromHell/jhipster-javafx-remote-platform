package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Direct PDF export for local Electrical Workbench project/calculation records. */
public final class PdfReportCenterPane extends BorderPane {
    private final ObjectMapper json;
    private final LocalWorkspaceStore store;
    private static final String INPUT="-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 9;";
    private static final String PRIMARY="-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-padding:9 13;";
    private static final String SECONDARY="-fx-background-color:#f8fafc;-fx-text-fill:#0f4c81;-fx-border-color:#c7d9e8;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 11;-fx-font-weight:bold;";
    private static final String CARD="-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#dfe7ef;-fx-border-radius:14;-fx-padding:16;";

    public PdfReportCenterPane(ObjectMapper json,String username){
        this.json=json;this.store=new LocalWorkspaceStore(json,username);
        VBox page=new VBox(13);page.setPadding(new Insets(2));
        Label title=new Label("Calculation Report Center");title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label sub=new Label("Create a direct PDF from saved circuit designs, panel schedules, transformer/motor designs, estimates and project records. Reports are generated locally on this computer.");sub.setWrapText(true);sub.setStyle("-fx-text-fill:#64748b;");page.getChildren().addAll(title,sub);

        TextField project=field("Electrical Workbench Project"),client=field(""),site=field("");TextArea notes=new TextArea();notes.setPrefRowCount(3);notes.setPromptText("Report notes / assumptions / scope");
        CheckBox designs=check("Circuit / design reports",true),panels=check("Panel schedules",true),transformers=check("Transformer designs",true),motors=check("Motor designs",true),estimates=check("Estimates",true),recent=check("Recent calculations",false),projects=check("Saved project records",false);
        Spinner<Integer> maxRecords=new Spinner<>(1,100,15);maxRecords.setEditable(true);
        VBox meta=card("1. Report scope","Choose which locally saved calculation categories to include. The newest records are included first.");meta.getChildren().addAll(row("Project / report title",project),row("Client / employer",client),row("Site / location",site),row("Maximum records per category",maxRecords),new FlowPane(12,8,designs,panels,transformers,motors,estimates,recent,projects),notes);

        TextArea preview=new TextArea("Select Preview report to assemble the saved calculation records.");preview.setEditable(false);preview.setWrapText(true);preview.setPrefRowCount(24);preview.setStyle("-fx-font-family:Consolas;-fx-font-size:12px;");Label status=new Label();status.setWrapText(true);status.setStyle("-fx-text-fill:#475569;-fx-font-weight:bold;");Button previewBtn=primary("Preview report"),pdfBtn=secondary("Export PDF"),copy=secondary("Copy preview");
        final String[] current={""};
        Runnable assemble=()->{String text=assembleReport(project.getText(),client.getText(),site.getText(),notes.getText(),maxRecords.getValue(),designs.isSelected(),panels.isSelected(),transformers.isSelected(),motors.isSelected(),estimates.isSelected(),recent.isSelected(),projects.isSelected());current[0]=text;preview.setText(text);status.setText("Report assembled from local workspace records.");};
        previewBtn.setOnAction(e->assemble.run());copy.setOnAction(e->{javafx.scene.input.ClipboardContent c=new javafx.scene.input.ClipboardContent();c.putString(preview.getText());javafx.scene.input.Clipboard.getSystemClipboard().setContent(c);status.setText("Preview copied.");});
        pdfBtn.setOnAction(e->{try{if(current[0].isBlank())assemble.run();FileChooser fc=new FileChooser();fc.setTitle("Export Electrical Workbench PDF");fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF document","*.pdf"));fc.setInitialFileName(safe(project.getText())+"-calculation-report.pdf");File file=fc.showSaveDialog(getScene().getWindow());if(file==null)return;writePdf(file,current[0],project.getText());status.setText("PDF exported: "+file.getAbsolutePath());}catch(Exception ex){status.setText("PDF export failed: "+message(ex));}});
        VBox output=card("2. Preview and export","PDF output includes page headers, calculation text and a verification disclaimer. The report is a calculation aid, not an engineering seal or inspection approval.");output.getChildren().addAll(new HBox(8,previewBtn,pdfBtn,copy),status,preview);
        page.getChildren().addAll(meta,output);ScrollPane scroll=new ScrollPane(page);scroll.setFitToWidth(true);scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");setCenter(scroll);
    }

    private String assembleReport(String title,String client,String site,String notes,int max,boolean designs,boolean panels,boolean transformers,boolean motors,boolean estimates,boolean recent,boolean projects){
        StringBuilder b=new StringBuilder();b.append("ELECTRICAL WORKBENCH CALCULATION REPORT\n");b.append("=======================================\n");b.append("Project: ").append(title).append('\n');if(!client.isBlank())b.append("Client / employer: ").append(client).append('\n');if(!site.isBlank())b.append("Site / location: ").append(site).append('\n');b.append("Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n\n");if(!notes.isBlank())b.append("REPORT NOTES\n------------\n").append(notes).append("\n\n");
        if(designs)appendCollection(b,"CIRCUIT / DESIGN REPORTS","designReports",max);
        if(panels)appendCollection(b,"PANEL SCHEDULES","panelSchedules",max);
        if(transformers)appendCollection(b,"TRANSFORMER DESIGNS","transformerDesigns",max);
        if(motors)appendCollection(b,"MOTOR DESIGNS","motorDesigns",max);
        if(estimates)appendCollection(b,"ESTIMATES","estimates",max);
        if(recent)appendCollection(b,"RECENT CALCULATIONS","recentCalculations",max);
        if(projects)appendCollection(b,"SAVED PROJECT RECORDS","projects",max);
        b.append("VERIFICATION NOTICE\n-------------------\nThis report is a calculation and planning aid. Verify final conductor, overcurrent, bonding, grounding, raceway, demand, equipment and protection selections against the Code edition and provincial amendments adopted by the authority having jurisdiction, equipment markings, manufacturer instructions and installation-specific conditions.\n");
        return b.toString();
    }

    private void appendCollection(StringBuilder b,String heading,String collection,int max){ArrayNode arr=store.records(collection);if(arr.isEmpty())return;b.append(heading).append("\n").append("-".repeat(Math.min(heading.length(),70))).append("\n");int first=Math.max(0,arr.size()-max);int n=1;for(int i=arr.size()-1;i>=first;i--){JsonNode x=arr.get(i);b.append("\n#").append(n++).append("\n");String saved=x.path("saved").asText(x.path("created").asText(x.path("date").asText("")));if(!saved.isBlank())b.append("Saved: ").append(saved).append('\n');String name=x.path("title").asText(x.path("name").asText(""));if(!name.isBlank())b.append("Title: ").append(name).append('\n');String result=x.path("result").asText(x.path("journal").asText(x.path("notes").asText("")));if(result.isBlank())result=compactJson(x);b.append(result).append("\n");}b.append("\n");}
    private String compactJson(JsonNode n){try{return json.writerWithDefaultPrettyPrinter().writeValueAsString(n);}catch(Exception e){return n.toString();}}

    private void writePdf(File file,String raw,String reportTitle)throws Exception{
        String text=ascii(raw);PDType1Font normal=new PDType1Font(Standard14Fonts.FontName.HELVETICA);PDType1Font bold=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);try(PDDocument doc=new PDDocument()){List<String> lines=wrap(text,96);float margin=48,top=PDRectangle.LETTER.getHeight()-52,bottom=52,lineHeight=13;PDPage page=null;PDPageContentStream cs=null;float y=0;int pageNo=0;try{for(String line:lines){if(page==null||y<bottom){if(cs!=null){cs.endText();cs.close();}page=new PDPage(PDRectangle.LETTER);doc.addPage(page);pageNo++;cs=new PDPageContentStream(doc,page);cs.beginText();cs.setFont(bold,10);cs.newLineAtOffset(margin,PDRectangle.LETTER.getHeight()-30);cs.showText(safePdf(reportTitle)+"  |  Electrical Workbench  |  Page "+pageNo);cs.endText();cs.beginText();cs.setFont(normal,9.5f);cs.newLineAtOffset(margin,top);y=top;}boolean heading=isHeading(line);cs.setFont(heading?bold:normal,heading?10.5f:9.5f);cs.showText(safePdf(line));cs.newLineAtOffset(0,-lineHeight);y-=lineHeight;}if(cs!=null){cs.endText();cs.close();cs=null;}}finally{if(cs!=null){try{cs.endText();}catch(Exception ignored){}try{cs.close();}catch(Exception ignored){}}}doc.save(file);}}
    private static boolean isHeading(String s){return !s.isBlank()&&s.length()<75&&s.equals(s.toUpperCase(Locale.ROOT))&&s.matches(".*[A-Z].*");}
    private static List<String> wrap(String raw,int width){List<String> out=new ArrayList<>();for(String source:raw.replace("\r","").split("\n",-1)){if(source.isEmpty()){out.add("");continue;}String line=source;while(line.length()>width){int cut=line.lastIndexOf(' ',width);if(cut<20)cut=width;out.add(line.substring(0,cut));line=line.substring(cut).stripLeading();}out.add(line);}return out;}
    private static String ascii(String s){return s.replace("φ","phase").replace("Φ","phase").replace("→","->").replace("←","<-").replace("×","x").replace("√","sqrt").replace("≤","<=").replace("≥",">=").replace("•","-").replace("✓","PASS").replace("—","-").replace("–","-").replace("²","^2").replace("³","^3").replace("°"," deg ");}
    private static String safePdf(String s){StringBuilder b=new StringBuilder();for(char c:s.toCharArray())b.append(c>=32&&c<=255?c:'?');return b.toString();}
    private VBox card(String title,String desc){VBox b=new VBox(10);b.setStyle(CARD);Label h=new Label(title);h.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");Label d=new Label(desc);d.setWrapText(true);d.setStyle("-fx-text-fill:#64748b;");b.getChildren().addAll(h,d);return b;}
    private HBox row(String label,Control c){Label l=new Label(label);l.setMinWidth(230);l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;");HBox h=new HBox(10,l,c);h.setAlignment(Pos.CENTER_LEFT);HBox.setHgrow(c,Priority.ALWAYS);c.setMaxWidth(Double.MAX_VALUE);return h;}
    private static TextField field(String v){TextField f=new TextField(v);f.setStyle(INPUT);return f;}
    private static CheckBox check(String text,boolean selected){CheckBox c=new CheckBox(text);c.setSelected(selected);return c;}
    private Button primary(String s){Button b=new Button(s);b.setStyle(PRIMARY);return b;}
    private Button secondary(String s){Button b=new Button(s);b.setStyle(SECONDARY);return b;}
    private static String safe(String s){String x=s==null?"project":s.trim().replaceAll("[^A-Za-z0-9._-]+","-");return x.isBlank()?"project":x;}
    private static String message(Exception e){return e.getMessage()==null?e.toString():e.getMessage();}
}

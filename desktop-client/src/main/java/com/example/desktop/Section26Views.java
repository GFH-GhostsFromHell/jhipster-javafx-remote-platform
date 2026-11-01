package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.concurrent.CompletableFuture;

public final class Section26Views {
  private static final ObjectMapper JSON=new ObjectMapper();
  private Section26Views(){}

  public static Node create(PortalClient p,String u,String pw){
    BorderPane root=new BorderPane();
    VBox header=new VBox(5);header.setPadding(new Insets(18,22,10,22));
    Label h=new Label("Section 26 — Installation of Electrical Equipment");h.setStyle("-fx-font-size:21px;-fx-font-weight:bold;-fx-text-fill:#14213d;");
    Label s=new Label("Worksheet mode follows the order used in the uploaded CEC practice sheets: enter the facts from the question, then work through current → protection → conductor requirements → secondary side / disconnect as applicable.");s.setWrapText(true);s.setStyle("-fx-text-fill:#64748b;");header.getChildren().addAll(h,s);
    TabPane tabs=new TabPane();tabs.getTabs().addAll(tab("Dry-Type / 600 V class",dry(p,u,pw)),tab("Medium-Voltage Transformer",mv(p,u,pw)),tab("High-Voltage Transformer",hv(p,u,pw)),tab("Capacitor Bank",capacitor(p,u,pw)));
    root.setTop(header);root.setCenter(tabs);return root;
  }

  private static Node dry(PortalClient p,String u,String pw){
    TextField kva=tf("60"),pv=tf("480"),sv=tf("208");ComboBox<Integer> ph=phases(3);TextField conductor=tf("TW75 copper"),raceway=tf("EMT / rigid steel");
    VBox in=new VBox(12,
      card("Question facts",grid("Transformer rating (kVA)",kva,"Primary voltage (V)",pv,"Secondary line voltage (V)",sv,"Phases",ph,"Conductor description",conductor,"Raceway / wiring method",raceway),"Enter the same information supplied at the top of the worksheet. Conductor/raceway descriptions are retained for your check; full automatic table selection is added only when the validated CEC dataset supports it."),
      info("Calculation order","1. Primary current\n2. Primary maximum and alternate OCPD\n3. Primary conductor ampacity — both methods\n4. Primary raceway / bonding check\n5. Secondary current\n6. Secondary conductor ampacity — both methods\n7. Secondary raceway / bonding check\n8. Secondary OCPD for splitter application"));
    return worksheet(in,()->p.dryTransformerWorksheet(u,pw,"{\"kva\":"+n(kva)+",\"primaryVoltage\":"+n(pv)+",\"secondaryVoltage\":"+n(sv)+",\"phases\":"+ph.getValue()+"}"),r->
      "DRY-TYPE TRANSFORMER WORKSHEET\n\n"+
      "1) PRIMARY CURRENT\n   I = kVA × 1000 ÷ (√3 × V) = "+a(r,"primaryCurrentA")+"\n\n"+
      "2) PRIMARY OVERCURRENT PROTECTION\n   125% value = "+a(r,"primary125A")+"\n   Maximum common size = "+i(r,"primaryMaximumCommonOcpdA")+" A\n   Alternate common size = "+i(r,"primaryAlternateCommonOcpdA")+" A\n\n"+
      "3) PRIMARY CONDUCTOR MINIMUM AMPACITY\n   Based on maximum primary OCPD = "+a(r,"primaryConductorAmpacityMaxMethodA")+"\n   Based on alternate 125% method = "+a(r,"primaryConductorAmpacityAlternateMethodA")+"\n   Select conductor from the applicable ampacity table for: "+conductor.getText()+"\n\n"+
      "4) PRIMARY RACEWAY / BONDING\n   Use conductor selected above with "+raceway.getText()+"; check Table 6/9 fill and Table 16 bonding as applicable.\n\n"+
      "5) SECONDARY CURRENT\n   Secondary current = "+a(r,"secondaryCurrentA")+"\n   Voltage ratio = "+r.path("ratio").asDouble()+":1\n\n"+
      "6) SECONDARY CONDUCTOR MINIMUM AMPACITY\n   Maximum-primary-fuse method = "+a(r,"secondaryConductorAmpacityMaxMethodA")+"\n   Alternate 125% method = "+a(r,"secondaryConductorAmpacityAlternateMethodA")+"\n\n"+
      "7) SECONDARY RACEWAY / FIELD BONDING\n   Size after selecting the actual conductor; check Table 9 and Rule 10-614 / Table 16 where applicable.\n\n"+
      "8) SECONDARY OCPD — SPLITTER APPLICATION\n   125% value = "+a(r,"secondary125A")+"\n   Common size not exceeding result = "+i(r,"secondaryCommonOcpdA")+" A\n\n"+refs(r));
  }

  private static Node mv(PortalClient p,String u,String pw){return hvLike(p,u,pw,"MEDIUM-VOLTAGE TRANSFORMER WORKSHEET","2.5","13.8","7.2","FUSE","1.50","Aluminum","Outdoor ventilated cable tray","5-15 kV");}
  private static Node hv(PortalClient p,String u,String pw){return hvLike(p,u,pw,"HIGH-VOLTAGE TRANSFORMER WORKSHEET","10","44","27.6","BREAKER","3.00","Copper","Single buried conduit","25-46 kV");}

  private static Node hvLike(PortalClient p,String u,String pw,String title,String mva0,String pv0,String sv0,String prot0,String factor0,String mat0,String wiring0,String class0){
    TextField mva=tf(mva0),pv=tf(pv0),sv=tf(sv0),factor=tf(factor0);ComboBox<String> prot=combo(prot0,"BREAKER","FUSE"),mat=combo(mat0,"Copper","Aluminum"),wiring=combo(wiring0,"Single buried conduit","Outdoor ventilated cable tray","Other"),vc=combo(class0,"5-15 kV","15-25 kV","25-46 kV","Other");
    VBox in=new VBox(12,
      card("Question facts",grid("Transformer rating (MVA)",mva,"Primary voltage (kV)",pv,"Secondary voltage (kV)",sv,"Primary protection type",prot,"Protection multiplier",factor,"Conductor material",mat,"Wiring method",wiring,"Cable voltage class",vc),"These are question facts. The medium-voltage preset mirrors the 13.8/7.2 kV aluminum-tray example; the high-voltage preset mirrors the 44/27.6 kV copper-buried example."),
      info("Calculation order","1. Primary current\n2. Maximum primary breaker/fuse\n3. 125% primary conductor ampacity\n4. Apply 26-256(4) OCPD-derived minimum\n5. Select applicable D17 cable table\n6. Secondary current\n7. 125% secondary conductor ampacity\n8. Compare with OCPD × voltage-ratio minimum\n9. Select applicable D17 conductor size"));
    return worksheet(in,()->p.hvTransformerWorksheet(u,pw,"{\"mva\":"+n(mva)+",\"primaryKv\":"+n(pv)+",\"secondaryKv\":"+n(sv)+",\"protection\":\""+prot.getValue()+"\",\"protectionFactor\":"+n(factor)+",\"material\":\""+mat.getValue()+"\",\"wiringMethod\":\""+wiring.getValue()+"\",\"voltageClass\":\""+vc.getValue()+"\"}"),r->
      title+"\n\n"+
      "1) PRIMARY CURRENT\n   "+a(r,"primaryCurrentA")+"\n\n"+
      "2) MAXIMUM PRIMARY "+prot.getValue()+"\n   Calculated = "+a(r,"primaryProtectionCalculatedA")+"\n   Common size used = "+i(r,"primaryProtectionCommonA")+" A\n\n"+
      "3) PRIMARY CONDUCTOR — 125% METHOD\n   125% ampacity = "+a(r,"primary125AmpacityA")+"\n\n"+
      "4) APPLY OCPD-DERIVED MINIMUM\n   Required primary ampacity after comparison = "+a(r,"primaryRequiredAmpacityAfterOcpdCheckA")+"\n\n"+
      "5) D17 TABLE\n   "+r.path("tableFamilyHint").asText()+"\n\n"+
      "6) SECONDARY CURRENT\n   "+a(r,"secondaryCurrentA")+"\n\n"+
      "7) SECONDARY — 125% METHOD\n   "+a(r,"secondary125AmpacityA")+"\n\n"+
      "8) SECONDARY — PRIMARY OCPD × VOLTAGE RATIO\n   Minimum from ratio check = "+a(r,"secondaryOcpdRatioMinimumA")+"\n\n"+
      "9) FINAL SECONDARY REQUIRED AMPACITY\n   Use greater value = "+a(r,"secondaryRequiredAmpacityA")+"\n   Then select conductor from the applicable D17 table.\n\n"+refs(r));
  }

  private static Node capacitor(PortalClient p,String u,String pw){
    TextField kvar=tf("25"),v=tf("600"),desc=tf("T90 copper in EMT");ComboBox<Integer> ph=phases(3);
    VBox in=new VBox(12,card("Question facts",grid("Capacitor bank rating (kVAR)",kvar,"Voltage (V)",v,"Phases",ph,"Conductor / wiring method",desc),"This follows the uploaded capacitor worksheet directly: the capacitor bank kVAR is already known."),info("Calculation order","a. FLA\nb. 135% conductor ampacity\nc. Select minimum conductor\nd. 250% maximum OCPD and Table 13 common size\ne. 135% minimum non-fused disconnect\nf. Check discharge / motor-controller exceptions separately when asked"));
    return worksheet(in,()->p.capacitorWorksheet(u,pw,"{\"kvar\":"+n(kvar)+",\"voltage\":"+n(v)+",\"phases\":"+ph.getValue()+"}"),r->
      "CAPACITOR BANK WORKSHEET\n\n"+
      "a) FULL-LOAD CURRENT\n   FLA = "+a(r,"flaA")+"\n\n"+
      "b) CALCULATED CONDUCTOR AMPACITY\n   FLA × 135% = "+a(r,"conductorAmpacityA")+"\n\n"+
      "c) MINIMUM CONDUCTOR\n   Select from the applicable ampacity table for "+desc.getText()+" using at least "+a(r,"conductorAmpacityA")+".\n\n"+
      "d) MAXIMUM OVERCURRENT DEVICE\n   FLA × 250% = "+a(r,"ocpdCalculatedA")+"\n   Common size not exceeding result = "+i(r,"maximumCommonOcpdA")+" A\n\n"+
      "e) MINIMUM NON-FUSED DISCONNECT\n   FLA × 135% = "+a(r,"disconnectCalculatedA")+"\n   Minimum common disconnect rating = "+i(r,"minimumCommonNonFusedDisconnectA")+" A\n\n"+refs(r));
  }

  private static Node worksheet(VBox inputs,Call call,Format format){inputs.setPadding(new Insets(14));ScrollPane left=new ScrollPane(inputs);left.setFitToWidth(true);left.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);Label h=new Label("Worked solution");h.setStyle("-fx-font-size:18px;-fx-font-weight:bold;");Button go=new Button("Calculate worksheet");go.setMaxWidth(Double.MAX_VALUE);go.setStyle("-fx-background-color:#1d4ed8;-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:11 16;");Label status=new Label("Enter the facts from the question, then calculate.");status.setWrapText(true);TextArea out=new TextArea();out.setEditable(false);out.setWrapText(true);out.setStyle("-fx-font-family:Consolas;-fx-font-size:13px;");out.setPrefRowCount(28);go.setOnAction(e->{go.setDisable(true);status.setText("Calculating…");CompletableFuture.supplyAsync(()->{try{return call.get();}catch(Exception ex){throw new RuntimeException(ex);}}).whenComplete((body,err)->Platform.runLater(()->{go.setDisable(false);if(err!=null){Throwable c=err.getCause()==null?err:err.getCause();status.setText("Calculation failed: "+c.getMessage());return;}try{out.setText(format.apply(JSON.readTree(body)));status.setText("Worksheet calculated.");}catch(Exception ex){status.setText("Could not read result: "+ex.getMessage());}}));});VBox right=new VBox(10,h,go,status,out);right.setPadding(new Insets(18));SplitPane sp=new SplitPane(left,right);sp.setDividerPositions(.48);return sp;}
  private static VBox card(String title,GridPane grid,String note){Label h=new Label(title);h.setStyle("-fx-font-size:16px;-fx-font-weight:bold;");Label n=new Label(note);n.setWrapText(true);n.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");VBox v=new VBox(8,h,grid,n);v.setPadding(new Insets(14));v.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-background-radius:10;-fx-border-radius:10;");return v;}
  private static VBox info(String title,String text){Label h=new Label(title);h.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#1d4ed8;");Label n=new Label(text);n.setWrapText(true);VBox v=new VBox(7,h,n);v.setPadding(new Insets(14));v.setStyle("-fx-background-color:#eff6ff;-fx-border-color:#bfdbfe;-fx-background-radius:10;-fx-border-radius:10;");return v;}
  private static GridPane grid(Object... p){GridPane g=new GridPane();g.setHgap(12);g.setVgap(8);ColumnConstraints a=new ColumnConstraints();a.setMinWidth(235);ColumnConstraints b=new ColumnConstraints();b.setHgrow(Priority.ALWAYS);g.getColumnConstraints().addAll(a,b);for(int x=0;x<p.length;x+=2){g.add(new Label(String.valueOf(p[x])),0,x/2);g.add((Node)p[x+1],1,x/2);}return g;}
  private static TextField tf(String s){TextField t=new TextField(s);t.setMaxWidth(280);return t;} private static ComboBox<Integer> phases(int d){ComboBox<Integer> c=new ComboBox<>();c.getItems().addAll(1,3);c.setValue(d);return c;} @SafeVarargs private static <T> ComboBox<T> combo(T d,T... values){ComboBox<T> c=new ComboBox<>();c.getItems().addAll(values);c.setValue(d);return c;} private static Tab tab(String t,Node n){Tab x=new Tab(t,n);x.setClosable(false);return x;} private static double n(TextField f){return Double.parseDouble(f.getText().trim());} private static String a(JsonNode r,String k){return String.format("%.1f A",r.path(k).asDouble());} private static int i(JsonNode r,String k){return r.path(k).asInt();} private static String refs(JsonNode r){return "CEC reference: "+r.path("reference").asText()+"\n\nCheck: "+r.path("note").asText();} private interface Call{String get() throws Exception;} private interface Format{String apply(JsonNode r) throws Exception;}
}

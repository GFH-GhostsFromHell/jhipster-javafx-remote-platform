package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.concurrent.CompletableFuture;

public final class Section28Views {
  private static final ObjectMapper JSON=new ObjectMapper();
  private Section28Views(){}

  public static Node create(PortalClient p,String u,String pw){
    BorderPane root=new BorderPane();
    VBox head=new VBox(5);head.setPadding(new Insets(18,22,10,22));
    Label h=new Label("Section 28 — Motors & Generators");h.setStyle("-fx-font-size:21px;-fx-font-weight:bold;-fx-text-fill:#14213d;");
    Label s=new Label("Enter the motor facts first, then apply the conductor, OCPD, overload and disconnect rules in worksheet order.");s.setWrapText(true);s.setStyle("-fx-text-fill:#64748b;");head.getChildren().addAll(h,s);
    TabPane tabs=new TabPane();tabs.getTabs().addAll(tab("Single Motor",singleMotor(p,u,pw)),tab("Multiple Motors",multiMotor(p,u,pw)),tab("Refrigeration / Compressor",refrigeration(p,u,pw)));
    root.setTop(head);root.setCenter(tabs);return root;
  }

  private static Node singleMotor(PortalClient p,String u,String pw){
    TextField flc=tf("28"),duty=tf("1.25"),ocpd=tf("2.50"),disc=tf("1.15");ComboBox<String> sf=combo("Not marked / below 1.15","Not marked / below 1.15","1.15 or greater");
    VBox in=new VBox(12,
      card("1. Motor current from the question / CEC table",grid("Motor FLC / FLA (A)",flc),"Use the applicable CEC motor-current table or question-provided current before applying conductor/OCPD rules."),
      card("2. Duty / conductor rule",grid("Conductor multiplier",duty),"Continuous duty is commonly 125%. For non-continuous duty, enter the applicable Table 27 multiplier from the question."),
      card("3. Branch short-circuit / ground-fault protection",grid("Table 29 / applicable OCPD multiplier",ocpd),"Enter the multiplier for the selected fuse/breaker and starting method."),
      card("4. Overload and disconnect",grid("Motor service factor condition",sf,"Disconnect current multiplier",disc),"The worksheet examples use 115% overload where the service-factor condition is not met and 125% where it is met."));
    return worksheet(in,()->{double of=sf.getValue().startsWith("1.15")?1.25:1.15;return p.motorCalculator(u,pw,"{\"flc\":"+n(flc)+",\"conductorFactor\":"+n(duty)+",\"overloadFactor\":"+of+",\"ocpdFactor\":"+n(ocpd)+",\"disconnectFactor\":"+n(disc)+",\"edition\":\"CEC_2024\"}");},r->
      "SINGLE MOTOR WORKSHEET\n\n1) MOTOR FLC / FLA\n   "+a(r,"motorFLC_A")+"\n\n2) MINIMUM SUPPLY CONDUCTOR AMPACITY\n   FLC × duty multiplier = "+a(r,"requiredConductorAmpacityA")+"\n   Starter conductor lookup = "+r.path("starterConductor").path("size").asText()+"\n\n3) OVERLOAD PROTECTION\n   Maximum worksheet setting = "+a(r,"overloadSettingA")+"\n\n4) BRANCH OCPD\n   Calculated = "+a(r,"branchOcpdCalculatedA")+"\n   Common size = "+r.path("branchOcpdStandardA").asInt()+" A\n\n5) DISCONNECT\n   Minimum current rating = "+a(r,"disconnectMinimumA")+"\n\n"+refs(r));
  }

  private static Node multiMotor(PortalClient p,String u,String pw){
    TextField flcs=tf("42,27,20,10"),cf=tf("1.25"),of=tf("2.50"),start=tf("0");
    VBox in=new VBox(12,
      card("1. Motors supplied from splitter / feeder",grid("Motor currents, comma separated (A)",flcs),"Example from your worksheet: 42, 27, 20, 10 A."),
      card("2. Feeder conductor",grid("Largest-motor conductor multiplier",cf),"Continuous-duty worksheet questions use 125% of the largest motor plus the remaining motors."),
      card("3. Feeder OCPD",grid("Largest-motor branch OCPD multiplier",of,"Extra simultaneous-start allowance (decimal)",start),"Enter 0 when none start simultaneously. If the question permits a larger device for simultaneous starting, enter the explicit allowance required by that scenario."));
    return worksheet(in,()->p.multiMotorCalculator(u,pw,"{\"flcs\":"+array(flcs.getText())+",\"largestConductorFactor\":"+n(cf)+",\"largestBranchOcpdFactor\":"+n(of)+",\"simultaneousStartExtra\":"+n(start)+",\"edition\":\"CEC_2024\"}"),r->
      "MULTIPLE MOTOR FEEDER WORKSHEET\n\n1) LARGEST MOTOR\n   "+a(r,"largestMotorFLC_A")+"\n   Sum of remaining motors = "+a(r,"otherMotorsFLC_A")+"\n\n2) FEEDER CONDUCTOR AMPACITY\n   Required = "+a(r,"feederAmpacityA")+"\n   Starter conductor = "+r.path("starterFeederConductor").path("size").asText()+"\n\n3) FEEDER OCPD\n   Calculated = "+a(r,"feederOcpdCalculatedA")+"\n   Common size = "+r.path("feederOcpdStandardA").asInt()+" A\n\n4) SIMULTANEOUS START CHECK\n   Added starting allowance = "+a(r,"simultaneousStartExtraA")+"\n\n5) MOTOR TAP CONDUCTORS\n   Use the Section 28 tap rules after the feeder size is known; the 2 m and 5 m questions in your worksheet are separate checks.\n\n"+refs(r));
  }

  private static Node refrigeration(PortalClient p,String u,String pw){
    TextField rla=tf("24.2"),lra=tf("145.2"),desc=tf("T90 copper in EMT, 75°C terminations");
    VBox in=new VBox(12,
      card("Question facts",grid("Compressor RLA (A)",rla,"Compressor LRA (A; enter 0 to use 6 × RLA)",lra,"Conductor / installation",desc),"This follows the uploaded Hermetic Refrigerant Motor Exercise sequence."),
      info("Calculation order","1. RLA\n2. LRA\n3. Minimum conductor = RLA × 125%\n4. Normal maximum OCPD = LRA × 50%\n5. If not starting, alternate maximum = LRA × 65%\n6. Relay overload = RLA × 140%\n7. Fuse overload = RLA × 125%\n8. Disconnect / compressor-fan combination is checked separately when required"));
    return worksheet(in,()->p.refrigerationWorksheet(u,pw,"{\"rla\":"+n(rla)+",\"lra\":"+n(lra)+"}"),r->
      "HERMETIC REFRIGERANT MOTOR WORKSHEET\n\n1) RLA\n   "+a(r,"rlaA")+"\n\n2) LRA\n   "+a(r,"lraA")+(r.path("lraWasDerived").asBoolean()?" (derived as 6 × RLA)":" (entered from question/nameplate)")+"\n\n3) MINIMUM CONDUCTOR AMPACITY\n   RLA × 125% = "+a(r,"conductorAmpacityA")+"\n   Select conductor for "+desc.getText()+".\n\n4) MAX OCPD — NORMAL\n   LRA × 50% = "+a(r,"normalOcpdCalculatedA")+"\n   Common size = "+r.path("normalCommonOcpdA").asInt()+" A\n\n5) MAX OCPD — IF NOT STARTING\n   LRA × 65% = "+a(r,"notStartingOcpdCalculatedA")+"\n   Common size = "+r.path("notStartingCommonOcpdA").asInt()+" A\n\n6) MAX RELAY OVERLOAD\n   RLA × 140% = "+a(r,"relayOverloadA")+"\n\n7) MAX FUSE OVERLOAD\n   RLA × 125% = "+a(r,"fuseOverloadA")+"\n\n"+refs(r));
  }

  private static Node worksheet(VBox inputs,Call call,Format format){inputs.setPadding(new Insets(14));ScrollPane left=new ScrollPane(inputs);left.setFitToWidth(true);Label h=new Label("Worked solution");h.setStyle("-fx-font-size:18px;-fx-font-weight:bold;");Button go=new Button("Calculate worksheet");go.setMaxWidth(Double.MAX_VALUE);go.setStyle("-fx-background-color:#1d4ed8;-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:11 16;");Label status=new Label("Enter the question facts, then calculate.");TextArea out=new TextArea();out.setEditable(false);out.setWrapText(true);out.setStyle("-fx-font-family:Consolas;-fx-font-size:13px;");go.setOnAction(e->{go.setDisable(true);status.setText("Calculating…");CompletableFuture.supplyAsync(()->{try{return call.get();}catch(Exception ex){throw new RuntimeException(ex);}}).whenComplete((body,err)->Platform.runLater(()->{go.setDisable(false);if(err!=null){Throwable c=err.getCause()==null?err:err.getCause();status.setText("Calculation failed: "+c.getMessage());return;}try{out.setText(format.apply(JSON.readTree(body)));status.setText("Worksheet calculated.");}catch(Exception ex){status.setText("Could not read result: "+ex.getMessage());}}));});VBox right=new VBox(10,h,go,status,out);right.setPadding(new Insets(18));SplitPane sp=new SplitPane(left,right);sp.setDividerPositions(.48);return sp;}
  private static VBox card(String title,GridPane grid,String note){Label h=new Label(title);h.setStyle("-fx-font-size:16px;-fx-font-weight:bold;");Label n=new Label(note);n.setWrapText(true);n.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");VBox v=new VBox(8,h,grid,n);v.setPadding(new Insets(14));v.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-background-radius:10;-fx-border-radius:10;");return v;}
  private static VBox info(String title,String text){Label h=new Label(title);h.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#1d4ed8;");Label n=new Label(text);n.setWrapText(true);VBox v=new VBox(7,h,n);v.setPadding(new Insets(14));v.setStyle("-fx-background-color:#eff6ff;-fx-border-color:#bfdbfe;-fx-background-radius:10;-fx-border-radius:10;");return v;}
  private static GridPane grid(Object... p){GridPane g=new GridPane();g.setHgap(12);g.setVgap(8);ColumnConstraints a=new ColumnConstraints();a.setMinWidth(250);ColumnConstraints b=new ColumnConstraints();b.setHgrow(Priority.ALWAYS);g.getColumnConstraints().addAll(a,b);for(int x=0;x<p.length;x+=2){g.add(new Label(String.valueOf(p[x])),0,x/2);g.add((Node)p[x+1],1,x/2);}return g;}
  private static TextField tf(String s){TextField t=new TextField(s);t.setMaxWidth(280);return t;} @SafeVarargs private static <T> ComboBox<T> combo(T d,T... values){ComboBox<T> c=new ComboBox<>();c.getItems().addAll(values);c.setValue(d);return c;} private static Tab tab(String t,Node n){Tab x=new Tab(t,n);x.setClosable(false);return x;} private static double n(TextField f){return Double.parseDouble(f.getText().trim());} private static String array(String s){String[] parts=s.split(",");StringBuilder b=new StringBuilder("[");for(int i=0;i<parts.length;i++){if(i>0)b.append(',');b.append(Double.parseDouble(parts[i].trim()));}return b.append(']').toString();} private static String a(JsonNode r,String k){return String.format("%.1f A",r.path(k).asDouble());} private static String refs(JsonNode r){return "CEC reference: "+r.path("reference").asText()+"\n\nCheck: "+r.path("note").asText();} private interface Call{String get() throws Exception;} private interface Format{String apply(JsonNode r) throws Exception;}
}

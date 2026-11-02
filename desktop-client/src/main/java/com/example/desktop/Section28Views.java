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
    Label s=new Label("Motor type, starting method and protective-device choices now drive the common motor factors automatically. Motor taps from splitters/feeders have their own Rule 28-110 / 28-106 worksheet.");s.setWrapText(true);s.setStyle("-fx-text-fill:#64748b;");head.getChildren().addAll(h,s);
    TabPane tabs=new TabPane();tabs.getTabs().addAll(
      tab("AC Motors",acMotor(p,u,pw)),
      tab("DC Motors",dcMotor(p,u,pw)),
      tab("Multiple Motors",multiMotor(p,u,pw)),
      tab("Motor Taps / Splitter",motorTap()),
      tab("Refrigeration / Compressor",refrigeration(p,u,pw))
    );
    root.setTop(head);root.setCenter(tabs);return root;
  }

  private static Node acMotor(PortalClient p,String u,String pw){
    TextField flc=tf("28"),voltage=tf("208"),duty=tf("1.25"),disc=tf("1.15");
    ComboBox<Integer> phases=combo(3,1,3);
    ComboBox<String> type=combo("Squirrel-cage / synchronous","Single-phase — all types","Squirrel-cage / synchronous","Wound-rotor induction");
    ComboBox<String> start=combo("Full voltage / resistor / reactor","Full voltage / resistor / reactor","Autotransformer / star-delta","Other / verify Table 29");
    ComboBox<String> protection=combo("Time-delay fuse","Time-delay fuse","Non-time-delay fuse","Inverse-time circuit breaker");
    ComboBox<String> sf=combo("SF 1.15+ or temp rise 40°C or less","SF 1.15+ or temp rise 40°C or less","Other motor");
    VBox in=new VBox(12,
      card("1. AC motor question facts",grid("AC motor type",type,"Phases",phases,"Voltage (V)",voltage,"Motor FLC / FLA (A)",flc,"Starting method",start),"Use the applicable CEC motor-current table or the value supplied in the question before applying conductor and protection rules."),
      card("2. Supply conductor",grid("Duty / conductor multiplier",duty),"Continuous-duty questions use 125%. For non-continuous duty, enter the applicable Table 27 multiplier."),
      card("3. Branch protection",grid("Protective device",protection),"The Table 29 percentage is now selected automatically from the motor type, starting method, protective device and FLC where the 30 A threshold applies."),
      card("4. Overload and disconnect",grid("Overload condition",sf,"Disconnect current multiplier",disc),"The overload worksheet uses 125% for the marked service-factor/temperature-rise condition and 115% for other motors."),
      info("AC motor worksheet order","1. Identify motor type / phases / voltage\n2. Determine FLC/FLA\n3. Apply the conductor-duty multiplier\n4. Choose the protective-device type — Table 29 factor is automatic\n5. Set overload protection\n6. Size the disconnect\n7. Check controller/starter requirements separately"));
    return worksheet(in,()->{
      double fla=n(flc),of=sf.getValue().startsWith("SF")?1.25:1.15,ocpd=motorOcpdFactor(type.getValue(),start.getValue(),protection.getValue(),phases.getValue(),fla);
      return p.motorCalculator(u,pw,"{\"flc\":"+fla+",\"conductorFactor\":"+n(duty)+",\"overloadFactor\":"+of+",\"ocpdFactor\":"+ocpd+",\"disconnectFactor\":"+n(disc)+",\"edition\":\"CEC_2024\"}");
    },r->
      "AC MOTOR WORKSHEET\n"+
      "Motor type: "+type.getValue()+"\n"+
      "Supply: "+voltage.getText()+" V, "+phases.getValue()+" phase\n"+
      "Starting method: "+start.getValue()+"\n"+
      "Protection: "+protection.getValue()+"\n"+
      "Table 29 factor: "+String.format("%.0f%%",motorOcpdFactor(type.getValue(),start.getValue(),protection.getValue(),phases.getValue(),n(flc))*100)+"\n\n"+
      "1) MOTOR FLC / FLA\n   "+a(r,"motorFLC_A")+"\n\n"+
      "2) MINIMUM SUPPLY CONDUCTOR AMPACITY\n   FLC × duty multiplier = "+a(r,"requiredConductorAmpacityA")+"\n   Starter conductor lookup = "+r.path("starterConductor").path("size").asText()+"\n\n"+
      "3) OVERLOAD PROTECTION\n   Maximum worksheet setting = "+a(r,"overloadSettingA")+"\n\n"+
      "4) BRANCH OCPD\n   Calculated maximum = "+a(r,"branchOcpdCalculatedA")+"\n   Backend common-size worksheet = "+r.path("branchOcpdStandardA").asInt()+" A\n   For a Table 29 maximum, verify the selected standard rating does not exceed the permitted calculated maximum unless a specific CEC exception applies.\n\n"+
      "5) DISCONNECT\n   Minimum current rating = "+a(r,"disconnectMinimumA")+"\n\n"+refs(r));
  }

  private static double motorOcpdFactor(String type,String start,String protection,int phases,double flc){
    if(type.startsWith("Wound"))return 1.50;
    if(phases==1||type.startsWith("Single"))return protectionFactor(protection,1.75,3.00,2.50);
    if(start.startsWith("Autotransformer")){
      if(protection.startsWith("Time-delay"))return 1.75;
      if(protection.startsWith("Non-time"))return flc<=30?2.50:2.00;
      return 2.00;
    }
    return protectionFactor(protection,1.75,3.00,2.50);
  }

  private static double protectionFactor(String protection,double td,double ntd,double breaker){
    if(protection.startsWith("Time-delay"))return td;
    if(protection.startsWith("Non-time"))return ntd;
    return breaker;
  }

  private static Node dcMotor(PortalClient p,String u,String pw){
    TextField flc=tf("72"),voltage=tf("250"),duty=tf("1.25"),ocpd=tf("1.50"),disc=tf("1.15");
    ComboBox<String> type=combo("Shunt","Shunt","Series","Compound — cumulative","Compound — differential","Permanent-magnet DC","Separately excited");
    ComboBox<String> supply=combo("DC supply","DC supply","Rectifier-fed","Drive / DC controller");
    ComboBox<String> sf=combo("Not marked / below 1.15","Not marked / below 1.15","1.15 or greater");
    VBox in=new VBox(12,
      card("1. DC motor question facts",grid("DC motor type",type,"Supply / controller type",supply,"Armature / supply voltage (V DC)",voltage,"Motor FLC / FLA (A)",flc),"Use the DC motor current value supplied by the question or the applicable CEC table/nameplate rule. Do not use an AC three-phase current formula for a DC motor."),
      card("2. Supply conductor",grid("Duty / conductor multiplier",duty),"Apply the conductor multiplier required for the motor duty in the question."),
      card("3. DC branch protection",grid("Applicable DC OCPD multiplier",ocpd),"Use the Section 28 rule/table percentage that applies to the DC motor and protective device. This is separate from the AC motor starting-method choices."),
      card("4. Overload and disconnect",grid("Motor service-factor condition",sf,"Disconnect current multiplier",disc),"Keep overload and disconnect sizing separate from short-circuit/ground-fault protection."),
      info("DC motor worksheet order","1. Identify DC motor type and supply/controller\n2. Determine FLC/FLA\n3. Size the supply/armature conductors\n4. Determine branch OCPD\n5. Determine overload protection\n6. Size the disconnect/controller\n7. For shunt/compound motors, field-circuit questions are handled separately from the armature circuit"));
    return worksheet(in,()->{double of=sf.getValue().startsWith("1.15")?1.25:1.15;return p.motorCalculator(u,pw,"{\"flc\":"+n(flc)+",\"conductorFactor\":"+n(duty)+",\"overloadFactor\":"+of+",\"ocpdFactor\":"+n(ocpd)+",\"disconnectFactor\":"+n(disc)+",\"edition\":\"CEC_2024\"}");},r->
      "DC MOTOR WORKSHEET\n"+
      "Motor type: "+type.getValue()+"\n"+
      "Supply/controller: "+supply.getValue()+"\n"+
      "Voltage: "+voltage.getText()+" V DC\n\n"+
      "1) MOTOR FLC / FLA\n   "+a(r,"motorFLC_A")+"\n\n"+
      "2) MINIMUM SUPPLY / ARMATURE CONDUCTOR AMPACITY\n   FLC × duty multiplier = "+a(r,"requiredConductorAmpacityA")+"\n   Starter conductor lookup = "+r.path("starterConductor").path("size").asText()+"\n\n"+
      "3) OVERLOAD PROTECTION\n   Maximum worksheet setting = "+a(r,"overloadSettingA")+"\n\n"+
      "4) DC BRANCH OCPD\n   Calculated = "+a(r,"branchOcpdCalculatedA")+"\n   Common size = "+r.path("branchOcpdStandardA").asInt()+" A\n\n"+
      "5) DISCONNECT / CONTROLLER\n   Minimum current rating = "+a(r,"disconnectMinimumA")+"\n\n"+
      "NOTE: Field-circuit resistance/speed-control calculations for shunt and compound motors will be kept in a separate DC theory/control worksheet rather than mixed into branch-circuit sizing.\n\n"+refs(r));
  }

  private static Node multiMotor(PortalClient p,String u,String pw){
    TextField flcs=tf("42,27,20,10"),cf=tf("1.25"),of=tf("2.50"),start=tf("0");
    ComboBox<String> system=combo("AC motors","AC motors","DC motors","Mixed / verify individually");
    VBox in=new VBox(12,
      card("1. Motors supplied from splitter / feeder",grid("Motor group",system,"Motor currents, comma separated (A)",flcs),"Example from your worksheet: 42, 27, 20, 10 A. If motors are mixed AC/DC, verify each branch rule before using the feeder total."),
      card("2. Feeder conductor",grid("Largest-motor conductor multiplier",cf),"Continuous-duty worksheet questions use 125% of the largest motor plus the remaining motors."),
      card("3. Feeder OCPD",grid("Largest-motor branch OCPD multiplier",of,"Extra simultaneous-start allowance (decimal)",start),"Enter 0 when none start simultaneously. If the question permits a larger device for simultaneous starting, enter the explicit allowance required by that scenario."));
    return worksheet(in,()->p.multiMotorCalculator(u,pw,"{\"flcs\":"+array(flcs.getText())+",\"largestConductorFactor\":"+n(cf)+",\"largestBranchOcpdFactor\":"+n(of)+",\"simultaneousStartExtra\":"+n(start)+",\"edition\":\"CEC_2024\"}"),r->
      "MULTIPLE MOTOR FEEDER WORKSHEET\nMotor group: "+system.getValue()+"\n\n1) LARGEST MOTOR\n   "+a(r,"largestMotorFLC_A")+"\n   Sum of remaining motors = "+a(r,"otherMotorsFLC_A")+"\n\n2) FEEDER CONDUCTOR AMPACITY\n   Required = "+a(r,"feederAmpacityA")+"\n   Starter conductor = "+r.path("starterFeederConductor").path("size").asText()+"\n\n3) FEEDER OCPD\n   Calculated = "+a(r,"feederOcpdCalculatedA")+"\n   Common size = "+r.path("feederOcpdStandardA").asInt()+" A\n\n4) SIMULTANEOUS START CHECK\n   Added starting allowance = "+a(r,"simultaneousStartExtraA")+"\n\n5) MOTOR TAP CONDUCTORS\n   Open the Motor Taps / Splitter tab for the Rule 28-110 or Rule 28-106 tap check.\n\n"+refs(r));
  }

  private static Node motorTap(){
    TextField source=tf("200"),flc=tf("40"),tap=tf("85"),length=tf("5"),custom=tf("1.25");
    ComboBox<String> mode=combo("Feeder / splitter to individual motor branch OCPD","Feeder / splitter to individual motor branch OCPD","One branch OCPD to individual motor tap");
    ComboBox<String> installation=combo("7.5 m or less — mechanically protected","3 m or less — enclosed in metal","7.5 m or less — mechanically protected","Longer / no short-tap exception");
    ComboBox<String> duty=combo("Continuous duty — 125%","Continuous duty — 125%","Custom / Table 27 factor");
    VBox inputs=new VBox(12,
      card("1. Tap arrangement",grid("Arrangement",mode,"Feeder / source conductor ampacity (A)",source,"Motor FLC / FLA (A)",flc),"Use feeder ampacity for a splitter/feeder tap. For a group branch-circuit tap, enter the ampacity of the branch-circuit conductors ahead of the tap."),
      card("2. Tap conductor",grid("Selected tap conductor ampacity (A)",tap,"Tap length (m)",length,"Installation condition",installation),"The short-tap exception depends on both length and installation/protection."),
      card("3. Motor duty",grid("Duty",duty,"Custom/Table 27 factor",custom),"Continuous-duty motor conductor basis is 125% of motor FLC. Use the custom factor only where the question calls for a Table 27 non-continuous-duty value."),
      info("Rule paths","Feeder / splitter → individual branch OCPD: Rule 28-110(2).\n• ≤3 m enclosed in metal: motor branch-conductor calculation may be used.\n• ≤7.5 m mechanically protected: motor calculation, but not less than 1/3 feeder ampacity.\n• Otherwise: tap ampacity at least feeder ampacity.\n\nOne branch OCPD → individual motor: Rule 28-106(3).\n• ≤7.5 m: motor conductor calculation, but not less than 1/3 of the branch-circuit conductor ampacity.\n• Over 7.5 m: use at least the branch-circuit conductor ampacity."));

    inputs.setPadding(new Insets(14));ScrollPane left=new ScrollPane(inputs);left.setFitToWidth(true);
    Label h=new Label("Worked motor-tap result");h.setStyle("-fx-font-size:18px;-fx-font-weight:bold;");
    Button go=new Button("Calculate motor tap");go.setMaxWidth(Double.MAX_VALUE);go.setStyle("-fx-background-color:#1d4ed8;-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:11 16;");
    TextArea out=new TextArea();out.setEditable(false);out.setWrapText(true);out.setStyle("-fx-font-family:Consolas;-fx-font-size:13px;");
    Label status=new Label("Enter the splitter / tap facts, then calculate.");
    go.setOnAction(e->{try{
      double src=n(source),mf=n(flc),selected=n(tap),len=n(length),factor=duty.getValue().startsWith("Custom")?n(custom):1.25;
      if(src<=0||mf<=0||selected<=0||len<0||factor<=0)throw new IllegalArgumentException("Values must be positive and length cannot be negative.");
      double motorReq=mf*factor,third=src/3.0,required;boolean valid=true;String condition,ref;
      if(mode.getValue().startsWith("Feeder")){
        ref="CEC 2024 Rule 28-110(2)";
        if(installation.getValue().startsWith("3 m")){valid=len<=3;required=motorReq;condition="≤3 m enclosed in metal — motor branch-conductor calculation.";}
        else if(installation.getValue().startsWith("7.5 m")){valid=len<=7.5;required=Math.max(motorReq,third);condition="≤7.5 m mechanically protected — larger of motor requirement or 1/3 feeder ampacity.";}
        else{required=src;condition="No short-tap exception — tap ampacity must be at least feeder ampacity.";}
      }else{
        ref="CEC 2024 Rule 28-106(3)";
        if(len<=7.5){required=Math.max(motorReq,third);condition="Individual motor tap ≤7.5 m — larger of motor requirement or 1/3 source branch-conductor ampacity.";}
        else{required=src;condition="Tap exceeds 7.5 m exception — use at least source branch-conductor ampacity.";}
        if(installation.getValue().startsWith("3 m")&&len>3)valid=false;
        if(installation.getValue().startsWith("7.5 m")&&len>7.5)valid=false;
      }
      boolean pass=valid&&selected>=required;
      out.setText("MOTOR TAP / SPLITTER WORKSHEET\n\n"+
        "Arrangement: "+mode.getValue()+"\n"+
        "Tap length: "+String.format("%.2f m",len)+"\n"+
        "Installation: "+installation.getValue()+"\n\n"+
        "Motor conductor basis = "+String.format("%.2f A × %.0f%% = %.2f A",mf,factor*100,motorReq)+"\n"+
        "One-third source conductor ampacity = "+String.format("%.2f ÷ 3 = %.2f A",src,third)+"\n\n"+
        condition+"\n\n"+
        "MINIMUM REQUIRED TAP AMPACITY = "+String.format("%.2f A",required)+"\n"+
        "Starter conductor = "+starterConductor(required)+"\n"+
        "Selected tap ampacity = "+String.format("%.2f A",selected)+"\n"+
        "Length / installation = "+(valid?"PASS":"FAIL")+"\n"+
        "RESULT = "+(pass?"PASS":"FAIL")+"\n\n"+
        "Reference: "+ref+". Verify conductor temperature/derating, mechanical protection, enclosure/termination conditions and the downstream motor protection arrangement.");
      status.setText("Motor tap calculated.");
    }catch(Exception ex){status.setText("Check inputs: "+ex.getMessage());}});
    VBox right=new VBox(10,h,go,status,out);right.setPadding(new Insets(18));VBox.setVgrow(out,Priority.ALWAYS);
    SplitPane sp=new SplitPane(left,right);sp.setDividerPositions(.48);return sp;
  }

  private static String starterConductor(double amps){
    if(amps<=20)return "14 AWG Cu (20 A starter lookup)";
    if(amps<=25)return "12 AWG Cu (25 A starter lookup)";
    if(amps<=35)return "10 AWG Cu (35 A starter lookup)";
    if(amps<=50)return "8 AWG Cu (50 A starter lookup)";
    if(amps<=65)return "6 AWG Cu (65 A starter lookup)";
    if(amps<=85)return "4 AWG Cu (85 A starter lookup)";
    if(amps<=100)return "3 AWG Cu (100 A starter lookup)";
    if(amps<=115)return "2 AWG Cu (115 A starter lookup)";
    if(amps<=130)return "1 AWG Cu (130 A starter lookup)";
    if(amps<=150)return "1/0 AWG Cu (150 A starter lookup)";
    if(amps<=175)return "2/0 AWG Cu (175 A starter lookup)";
    if(amps<=200)return "3/0 AWG Cu (200 A starter lookup)";
    if(amps<=230)return "4/0 AWG Cu (230 A starter lookup)";
    return "Above starter conductor lookup range";
  }

  private static Node refrigeration(PortalClient p,String u,String pw){
    TextField rla=tf("24.2"),lra=tf("145.2"),desc=tf("T90 copper in EMT, 75°C terminations");
    VBox in=new VBox(12,
      card("Question facts",grid("Compressor RLA (A)",rla,"Compressor LRA (A; enter 0 to use 6 × RLA)",lra,"Conductor / installation",desc),"This follows the Hermetic Refrigerant Motor Exercise sequence."),
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

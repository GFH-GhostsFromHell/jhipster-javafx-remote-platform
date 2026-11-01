package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.concurrent.CompletableFuture;

public final class ServiceCalculatorViews {
  private static final ObjectMapper JSON=new ObjectMapper();
  private ServiceCalculatorViews(){}

  public static Node create(PortalClient p,String u,String pw){
    BorderPane root=new BorderPane();
    VBox header=new VBox(5);
    header.setPadding(new Insets(18,22,10,22));
    Label h=new Label("CEC 2024 Service Calculations");h.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#14213d;");
    Label s=new Label("Pick the occupancy first. Each worksheet is organized the same way you would solve a C of Q question: building information → connected loads → demand rules → final service current.");
    s.setWrapText(true);s.setStyle("-fx-text-fill:#64748b;");
    header.getChildren().addAll(h,s);

    TabPane tabs=new TabPane();
    tabs.getTabs().addAll(
      tab("Residential",residential(p,u,pw)),
      tab("Apartment / Multi-suite",apartment(p,u,pw)),
      tab("School",school(p,u,pw)),
      tab("Hospital",hospital(p,u,pw)),
      tab("Other Occupancy",other(p,u,pw))
    );
    root.setTop(header);root.setCenter(tabs);return root;
  }

  private static Tab tab(String title,Node content){Tab t=new Tab(title,content);t.setClosable(false);return t;}

  private static Node residential(PortalClient p,String u,String pw){
    TextField ground=tf("150"),upper=tf("100"),basement=tf("100"),v=tf("240");
    TextField range=tf("12"),other=tf("8.5"),full=tf("0"),ev=tf("0");
    TextField roomHeat=tf("25"),central=tf("0"),ac=tf("7");CheckBox interlock=check("Heating and A/C are interlocked — use the larger load",true);

    VBox inputs=new VBox(12,
      ruleCard("Residential dwelling — CEC 2024 Rule 8-200","Use this for one dwelling service. Small normal household loads under 1500 W are normally part of the basic dwelling load; enter larger/special loads separately."),
      section("1. Building area",grid(
        "Ground-floor living area (m²)",ground,
        "Upper-floor living area (m²)",upper,
        "Qualifying basement area (m²)",basement,
        "Service voltage",v),
        "Basement area is counted at 75% by the dwelling-area method."),
      section("2. Cooking and special loads",grid(
        "Single range rating (kW)",range,
        "Other loads over 1500 W — total (kW)",other,
        "100% loads — tankless/pool/spa etc. (kW)",full,
        "EVSE load (kW)",ev),
        "Keep ordinary small household loads out of these boxes if they are already covered by the basic dwelling load."),
      section("3. Heating and air conditioning",grid(
        "Room/baseboard heat connected (kW)",roomHeat,
        "Central electric heat at 100% (kW)",central,
        "Air conditioning / heat-pump load (kW)",ac),null),
      interlock
    );

    String body="{\"groundArea\":"+n(ground)+",\"upperArea\":"+n(upper)+",\"basementArea\":"+n(basement)+",\"rangeKw\":"+n(range)+",\"roomHeatKw\":"+n(roomHeat)+",\"centralHeatKw\":"+n(central)+",\"acKw\":"+n(ac)+",\"interlocked\":"+interlock.isSelected()+",\"otherOver1500Kw\":"+n(other)+",\"dedicated100Kw\":"+n(full)+",\"evseKw\":"+n(ev)+",\"voltage\":"+n(v)+"}";
    return worksheet(inputs,()->p.residentialServiceCalculator(u,pw,body),r->
      "STEP 1 — CEC living area\n"+
      "  Area used = "+d(r,"livingAreaM2")+" m²\n\n"+
      "STEP 2 — Basic dwelling load\n"+
      "  Basic load = "+w(r,"basicLoadW")+"\n\n"+
      "STEP 3 — Range\n"+
      "  Range demand = "+w(r,"rangeDemandW")+"\n\n"+
      "STEP 4 — Heating / A/C\n"+
      "  Heating demand = "+w(r,"heatingDemandW")+"\n"+
      "  A/C load = "+w(r,"airConditioningW")+"\n"+
      "  HVAC load actually carried = "+w(r,"hvacDemandW")+"\n\n"+
      "STEP 5 — Other large/special loads\n"+
      "  Other-load demand = "+w(r,"otherLoadsDemandW")+"\n\n"+
      "STEP 6 — Compare with Rule 8-200 minimum\n"+
      "  Calculated load before minimum = "+w(r,"calculatedBeforeMinimumW")+"\n"+
      "  Minimum rule load = "+w(r,"minimumRuleLoadW")+"\n"+
      "  Use the GREATER value.\n\n"+
      finalBlock(r)+"\n\n"+refs(r));
  }

  private static Node apartment(PortalClient p,String u,String pw){
    TextField suites=tf("12"),area=tf("90"),v=tf("240");ComboBox<Integer> ph=phases(1);
    TextField range=tf("12"),other=tf("0"),heat=tf("0"),ac=tf("0");CheckBox interlock=check("Suite heating and A/C are interlocked",true);
    TextField house=tf("18"),houseDf=tf("0.75"),houseCont=tf("1.25");

    VBox inputs=new VBox(12,
      ruleCard("Apartment / multi-suite building — CEC 2024 Rule 8-202","Calculate ONE suite first, apply the suite diversity to the building, then add house/common loads separately. The individual 60 A suite-feeder minimum is a separate check; it is not substituted into the building diversity calculation."),
      section("1. Building setup",grid(
        "Number of suites",suites,
        "Area of each suite (m²)",area,
        "Service voltage",v,
        "Service phases",ph),null),
      section("2. Load inside each suite",grid(
        "Range rating per suite (kW)",range,
        "Other suite load per suite (kW)",other,
        "Electric heat per suite (kW)",heat,
        "A/C per suite (kW)",ac),null),
      interlock,
      section("3. House / common-area loads",grid(
        "House/common connected load (kW)",house,
        "House-load demand factor",houseDf,
        "Continuous-load multiplier",houseCont),
        "Examples: hallway/common lighting, shared laundry and other building loads. These are kept separate from loads inside each suite." )
    );

    String body="{\"suites\":"+(int)n(suites)+",\"suiteArea\":"+n(area)+",\"rangeKw\":"+n(range)+",\"suiteOtherKw\":"+n(other)+",\"suiteHeatKw\":"+n(heat)+",\"suiteAcKw\":"+n(ac)+",\"suiteInterlocked\":"+interlock.isSelected()+",\"houseLoadKw\":"+n(house)+",\"houseDemandFactor\":"+n(houseDf)+",\"houseContinuousFactor\":"+n(houseCont)+",\"voltage\":"+n(v)+",\"phases\":"+ph.getValue()+"}";
    return worksheet(inputs,()->p.apartmentServiceCalculator(u,pw,body),r->
      "STEP 1 — Calculate one suite\n"+
      "  Basic suite load = "+w(r,"basicLoadPerSuiteW")+"\n"+
      "  Range demand = "+w(r,"rangeDemandPerSuiteW")+"\n"+
      "  Suite HVAC demand = "+w(r,"suiteHvacDemandW")+"\n"+
      "  Calculated load per suite = "+w(r,"calculatedLoadPerSuiteW")+"\n\n"+
      "STEP 2 — Apply apartment diversity\n"+
      "  First suite @ 100%: "+i(r,"firstSuite100Percent")+" suite\n"+
      "  Next suites @ 65%: "+i(r,"nextSuites65Percent")+"\n"+
      "  Next suites @ 40%: "+i(r,"nextSuites40Percent")+"\n"+
      "  Remaining suites @ 25%: "+i(r,"remainingSuites25Percent")+"\n"+
      "  Suite demand after diversity = "+w(r,"suiteDemandAfterDiversityW")+"\n\n"+
      "STEP 3 — Add house/common loads separately\n"+
      "  House connected load = "+w(r,"houseConnectedLoadW")+"\n"+
      "  House demand used = "+w(r,"houseDemandW")+"\n\n"+
      "STEP 4 — Building service\n"+
      finalBlock(r)+"\n\n"+
      "IMPORTANT: The 60 A minimum for an individual apartment feeder is checked separately from the building service diversity calculation.\n\n"+refs(r));
  }

  private static Node school(PortalClient p,String u,String pw){
    TextField area=tf("900"),classroom=tf("550"),v=tf("240");ComboBox<Integer> ph=phases(1);
    TextField other=tf("40"),cord=tf("0"),ev=tf("0"),heat=tf("0"),ac=tf("0");CheckBox interlock=check("Heating and A/C are interlocked",true);

    VBox inputs=new VBox(12,
      ruleCard("School — CEC 2024 Rule 8-204","The school worksheet separates classroom area from the rest of the building, then applies the school demand treatment. This matches the 900 m² / 550 m² classroom style questions you practiced."),
      section("1. Building area",grid(
        "Total school area (m²)",area,
        "Classroom area (m²)",classroom,
        "Service voltage",v,
        "Service phases",ph),
        "Classroom area uses 50 W/m²; the remaining area uses 10 W/m² in this worksheet."),
      section("2. Other connected loads",grid(
        "Other permanently connected load (kW)",other,
        "High-power cord/receptacle load (kW)",cord,
        "EVSE load (kW)",ev),null),
      section("3. Heating and A/C",grid(
        "Electric space heat connected (kW)",heat,
        "Air conditioning (kW)",ac),null),
      interlock
    );

    String body="{\"totalArea\":"+n(area)+",\"classroomArea\":"+n(classroom)+",\"otherKw\":"+n(other)+",\"heatKw\":"+n(heat)+",\"acKw\":"+n(ac)+",\"interlocked\":"+interlock.isSelected()+",\"evseKw\":"+n(ev)+",\"highPowerCordKw\":"+n(cord)+",\"voltage\":"+n(v)+",\"phases\":"+ph.getValue()+"}";
    return worksheet(inputs,()->p.schoolServiceCalculator(u,pw,body),r->
      "STEP 1 — Area load\n"+
      "  Classroom portion = "+w(r,"classroomLoadW")+"\n"+
      "  Remaining-area portion = "+w(r,"remainingAreaLoadW")+"\n"+
      "  Basic connected area load = "+w(r,"basicLoadW")+"\n\n"+
      "STEP 2 — HVAC\n"+
      "  Heating demand = "+w(r,"heatingDemandW")+"\n"+
      "  A/C load = "+w(r,"airConditioningW")+"\n\n"+
      "STEP 3 — Apply school demand rule\n"+
      "  Rule 8-204 demand treatment is applied by the backend, including the 900 m² split where applicable.\n\n"+
      "STEP 4 — Service\n"+
      finalBlock(r)+"\n\n"+refs(r));
  }

  private static Node hospital(PortalClient p,String u,String pw){
    TextField area=tf("1300"),hi=tf("275"),v=tf("240");ComboBox<Integer> ph=phases(1);
    TextField other=tf("85"),cord=tf("0"),ev=tf("0"),heat=tf("110"),ac=tf("0");CheckBox interlock=check("Heating and A/C are interlocked",true);

    VBox inputs=new VBox(12,
      ruleCard("Hospital — CEC 2024 Rule 8-206","Hospital calculations use total floor area plus a separate high-intensity area allowance, then apply the hospital demand treatment. The defaults mirror the 1300 m² / 275 m² example you practiced."),
      section("1. Hospital area",grid(
        "Total hospital area (m²)",area,
        "High-intensity area (m²)",hi,
        "Service voltage",v,
        "Service phases",ph),
        "The worksheet adds 20 W/m² for total area plus an additional 100 W/m² for high-intensity area."),
      section("2. Other connected loads",grid(
        "Other permanently connected loads (kW)",other,
        "High-power cord/receptacle load (kW)",cord,
        "EVSE load (kW)",ev),null),
      section("3. Heating and A/C",grid(
        "Electric space heat connected (kW)",heat,
        "Air conditioning (kW)",ac),null),
      interlock
    );

    String body="{\"totalArea\":"+n(area)+",\"highIntensityArea\":"+n(hi)+",\"otherKw\":"+n(other)+",\"heatKw\":"+n(heat)+",\"acKw\":"+n(ac)+",\"interlocked\":"+interlock.isSelected()+",\"evseKw\":"+n(ev)+",\"highPowerCordKw\":"+n(cord)+",\"voltage\":"+n(v)+",\"phases\":"+ph.getValue()+"}";
    return worksheet(inputs,()->p.hospitalServiceCalculator(u,pw,body),r->
      "STEP 1 — Hospital area load\n"+
      "  Total-area load (20 W/m²) = "+w(r,"basicAreaLoadW")+"\n"+
      "  High-intensity addition (100 W/m²) = "+w(r,"highIntensityAdditionalW")+"\n\n"+
      "STEP 2 — HVAC\n"+
      "  Heating demand = "+w(r,"heatingDemandW")+"\n"+
      "  A/C load = "+w(r,"airConditioningW")+"\n\n"+
      "STEP 3 — Apply hospital demand rule\n"+
      "  Rule 8-206 demand treatment is applied by the backend, including the 900 m² split where applicable.\n\n"+
      "STEP 4 — Service\n"+
      finalBlock(r)+"\n\n"+refs(r));
  }

  private static Node other(PortalClient p,String u,String pw){
    ComboBox<String> preset=new ComboBox<>();preset.getItems().addAll("Hotel / motel","Commercial / retail","Industrial / shop","Custom");preset.setValue("Hotel / motel");
    TextField name=tf("Hotel / motel"),area=tf("775"),wpm=tf("20"),df=tf("0.80"),v=tf("208");ComboBox<Integer> ph=phases(3);
    TextField special=tf("40"),motor=tf("0"),ev=tf("0"),heat=tf("75"),ac=tf("50");CheckBox interlock=check("Heating and A/C are interlocked",true);
    preset.setOnAction(e->name.setText(preset.getValue()));

    VBox inputs=new VBox(12,
      ruleCard("Other occupancy — CEC 2024 Rule 8-210 / Table 14","Use this when the occupancy has a Table 14 area load rather than one of the dedicated dwelling, apartment, school or hospital methods. The app does not guess the Table 14 value—enter the value that matches the exact occupancy."),
      section("1. Occupancy and basic area load",grid(
        "Quick occupancy label",preset,
        "Occupancy description",name,
        "Floor area (m²)",area,
        "Table 14 basic load (W/m²)",wpm,
        "Table demand factor",df,
        "Service voltage",v,
        "Service phases",ph),null),
      section("2. Loads added outside the area load",grid(
        "Special/permanently connected load (kW)",special,
        "Motor / other special load (kW)",motor,
        "EVSE load (kW)",ev),null),
      section("3. Heating and A/C",grid(
        "Electric heat connected (kW)",heat,
        "Air conditioning (kW)",ac),null),
      interlock
    );

    Call call=()->p.otherServiceCalculator(u,pw,"{\"occupancy\":"+JSON.writeValueAsString(name.getText())+",\"area\":"+n(area)+",\"wattsPerM2\":"+n(wpm)+",\"tableDemand\":"+n(df)+",\"specialKw\":"+n(special)+",\"heatKw\":"+n(heat)+",\"acKw\":"+n(ac)+",\"interlocked\":"+interlock.isSelected()+",\"motorKw\":"+n(motor)+",\"evseKw\":"+n(ev)+",\"voltage\":"+n(v)+",\"phases\":"+ph.getValue()+"}");
    return worksheet(inputs,call,r->
      "STEP 1 — Occupancy area load\n"+
      "  Occupancy = "+t(r,"occupancy")+"\n"+
      "  Demanded Table 14 area load = "+w(r,"basicTable14LoadW")+"\n\n"+
      "STEP 2 — Add special loads\n"+
      "  Special / motor / EV loads = "+w(r,"specialAndMotorLoadsW")+"\n\n"+
      "STEP 3 — HVAC\n"+
      "  Heating demand = "+w(r,"heatingDemandW")+"\n"+
      "  A/C load = "+w(r,"airConditioningW")+"\n\n"+
      "STEP 4 — Service\n"+
      finalBlock(r)+"\n\n"+refs(r));
  }

  private static Node worksheet(VBox inputs,Call call,Format fmt){
    inputs.setPadding(new Insets(14));
    ScrollPane left=new ScrollPane(inputs);left.setFitToWidth(true);left.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

    Label resultsTitle=new Label("Calculation breakdown");resultsTitle.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#14213d;");
    Label hint=new Label("The result is shown in the same order you would write the solution on paper.");hint.setWrapText(true);hint.setStyle("-fx-text-fill:#64748b;");
    Button go=new Button("Calculate service");go.setMaxWidth(Double.MAX_VALUE);go.setStyle("-fx-background-color:#1d4ed8;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;-fx-padding:11 16;");
    Label status=new Label("Enter or change the loads, then calculate.");status.setWrapText(true);status.setStyle("-fx-text-fill:#64748b;");
    TextArea out=new TextArea();out.setEditable(false);out.setWrapText(true);out.setPrefRowCount(24);out.setStyle("-fx-font-family:Consolas;-fx-font-size:13px;");
    go.setOnAction(e->{go.setDisable(true);status.setText("Calculating…");CompletableFuture.supplyAsync(()->{try{return call.get();}catch(Exception ex){throw new RuntimeException(ex);}}).whenComplete((body,err)->Platform.runLater(()->{go.setDisable(false);if(err!=null){Throwable c=err.getCause()==null?err:err.getCause();status.setText("Calculation failed: "+c.getMessage());return;}try{out.setText(fmt.apply(JSON.readTree(body)));status.setText("CEC 2024 worksheet calculated.");}catch(Exception ex){status.setText("Could not read result: "+ex.getMessage());}}));});
    VBox right=new VBox(10,resultsTitle,hint,go,status,out);right.setPadding(new Insets(18));
    SplitPane split=new SplitPane(left,right);split.setDividerPositions(0.53);return split;
  }

  private static VBox ruleCard(String title,String text){Label h=new Label(title);h.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:#1d4ed8;");Label d=new Label(text);d.setWrapText(true);d.setStyle("-fx-text-fill:#475569;");VBox b=new VBox(6,h,d);b.setPadding(new Insets(14));b.setStyle("-fx-background-color:#eff6ff;-fx-border-color:#bfdbfe;-fx-background-radius:10;-fx-border-radius:10;");return b;}
  private static VBox section(String title,GridPane fields,String note){Label h=new Label(title);h.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#14213d;");VBox b=new VBox(8,h,fields);if(note!=null&&!note.isBlank()){Label n=new Label(note);n.setWrapText(true);n.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");b.getChildren().add(n);}b.setPadding(new Insets(14));b.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-background-radius:10;-fx-border-radius:10;");return b;}
  private static GridPane grid(Object... pairs){GridPane g=new GridPane();g.setHgap(12);g.setVgap(8);ColumnConstraints a=new ColumnConstraints();a.setMinWidth(245);ColumnConstraints b=new ColumnConstraints();b.setHgrow(Priority.ALWAYS);g.getColumnConstraints().addAll(a,b);for(int x=0;x<pairs.length;x+=2){g.add(new Label(String.valueOf(pairs[x])),0,x/2);g.add((Node)pairs[x+1],1,x/2);}return g;}
  private static TextField tf(String value){TextField t=new TextField(value);t.setMaxWidth(240);return t;}
  private static ComboBox<Integer> phases(int value){ComboBox<Integer> c=new ComboBox<>();c.getItems().addAll(1,3);c.setValue(value);c.setMaxWidth(240);return c;}
  private static CheckBox check(String text,boolean selected){CheckBox c=new CheckBox(text);c.setSelected(selected);c.setWrapText(true);return c;}
  private static double n(TextField f){return Double.parseDouble(f.getText().trim());}
  private static double d(JsonNode r,String k){return r.path(k).asDouble();}
  private static int i(JsonNode r,String k){return r.path(k).asInt();}
  private static String t(JsonNode r,String k){return r.path(k).asText();}
  private static String w(JsonNode r,String k){return String.format("%.0f W",d(r,k));}
  private static String finalBlock(JsonNode r){return "FINAL CALCULATED DEMAND = "+String.format("%.0f W",d(r,"calculatedDemandW"))+"\nSERVICE CURRENT = "+String.format("%.1f A",d(r,"serviceCurrentA"))+"\nNEXT STANDARD SERVICE SIZE = "+i(r,"suggestedStandardServiceA")+" A";}
  private static String refs(JsonNode r){return "CEC reference: "+t(r,"reference")+"\n\nCheck: "+t(r,"note");}
  private interface Call{String get() throws Exception;} private interface Format{String apply(JsonNode r) throws Exception;}
}

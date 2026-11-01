package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.concurrent.CompletableFuture;

public final class Section42Views {
  private static final ObjectMapper JSON=new ObjectMapper();
  private Section42Views(){}

  public static Node create(PortalClient p,String u,String pw){
    BorderPane root=new BorderPane();VBox head=new VBox(5);head.setPadding(new Insets(18,22,10,22));
    Label h=new Label("Section 42 — Electric Welders");h.setStyle("-fx-font-size:21px;-fx-font-weight:bold;-fx-text-fill:#14213d;");
    Label s=new Label("Worksheet mode starts from kVA, voltage and duty cycle, then works through conductor loading and the two OCPD limits used in your practice sheets.");s.setWrapText(true);s.setStyle("-fx-text-fill:#64748b;");head.getChildren().addAll(h,s);
    TabPane tabs=new TabPane();tabs.getTabs().addAll(tab("Single Welder",single(p,u,pw)),tab("Multiple Welder Feeder",groupInfo()));root.setTop(head);root.setCenter(tabs);return root;
  }

  private static Node single(PortalClient p,String u,String pw){
    TextField kva=tf("30"),v=tf("600"),duty=tf("0.55"),condAmp=tf("35"),desc=tf("TECK90 copper / 75°C ampacity");ComboBox<String> type=combo("TRANSFORMER_ARC","TRANSFORMER_ARC","RESISTANCE");ComboBox<String> preset=combo("30% → 0.55","20% → 0.45","30% → 0.55","40% → 0.63","50% → 0.71","60% → 0.78","70% → 0.84","Custom");preset.setOnAction(e->{String x=preset.getValue();if(x.startsWith("20"))duty.setText("0.45");else if(x.startsWith("30"))duty.setText("0.55");else if(x.startsWith("40"))duty.setText("0.63");else if(x.startsWith("50"))duty.setText("0.71");else if(x.startsWith("60"))duty.setText("0.78");else if(x.startsWith("70"))duty.setText("0.84");});
    VBox in=new VBox(12,
      card("Question facts",grid("Welder type",type,"Rating (kVA)",kva,"Voltage (V)",v,"Duty-cycle preset",preset,"Duty-cycle multiplier",duty,"Selected conductor ampacity (A)",condAmp,"Conductor description",desc),"The selected conductor ampacity is entered after choosing the actual conductor from Table 2/4 or the applicable cable table. This lets the app reproduce the Subrule 2 check from your worksheet without pretending every installation uses the same conductor table."),
      info("Calculation order","1. Input current = kVA × 1000 ÷ V\n2. Apply duty-cycle factor to find conductor load\n3. Select conductor from applicable ampacity table\n4. OCPD Subrule 1 limit from welder input current\n5. OCPD Subrule 2 limit from selected conductor ampacity\n6. Use the controlling result; then consider nuisance-tripping allowance where the applicable rule permits it"));
    return worksheet(in,()->p.welderWorksheet(u,pw,"{\"kva\":"+n(kva)+",\"voltage\":"+n(v)+",\"dutyFactor\":"+n(duty)+",\"type\":\""+type.getValue()+"\",\"selectedConductorAmpacity\":"+n(condAmp)+"}"),r->
      "WELDER WORKSHEET\n\n1) INPUT CURRENT\n   kVA × 1000 ÷ V = "+a(r,"inputCurrentA")+"\n\n2) DUTY-CYCLE-ADJUSTED CONDUCTOR LOAD\n   Input current × duty factor = "+a(r,"dutyAdjustedConductorLoadA")+"\n   Select conductor for "+desc.getText()+".\n\n3) OCPD — SUBRULE 1\n   Multiplier = "+r.path("ocpdMultiplier").asDouble()+" ×\n   Calculated = "+a(r,"subrule1CalculatedA")+"\n   Common size = "+r.path("subrule1CommonA").asInt()+" A\n\n4) OCPD — SUBRULE 2\n   Selected conductor ampacity = "+a(r,"selectedConductorAmpacityA")+"\n   Calculated = "+a(r,"subrule2CalculatedA")+"\n   Common size = "+r.path("subrule2CommonA").asInt()+" A\n\n5) STARTING OUTCOME\n   Controlling common size = "+r.path("startingOutcomeA").asInt()+" A\n\n"+refs(r));
  }

  private static Node groupInfo(){VBox v=new VBox(12);v.setPadding(new Insets(18));Label h=new Label("Multiple-welder feeder worksheet");h.setStyle("-fx-font-size:18px;-fx-font-weight:bold;");Label t=new Label("The uploaded multi-welder problem uses a different duty factor for each welder, then applies group demand to the ordered loads before sizing the feeder. The next dataset pass will make this a row-based worksheet so each welder can have its own current and duty cycle rather than forcing one factor onto the whole group.");t.setWrapText(true);t.setStyle("-fx-text-fill:#475569;");v.getChildren().addAll(h,t);return v;}

  private static Node worksheet(VBox inputs,Call call,Format format){inputs.setPadding(new Insets(14));ScrollPane left=new ScrollPane(inputs);left.setFitToWidth(true);Label h=new Label("Worked solution");h.setStyle("-fx-font-size:18px;-fx-font-weight:bold;");Button go=new Button("Calculate worksheet");go.setMaxWidth(Double.MAX_VALUE);go.setStyle("-fx-background-color:#1d4ed8;-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:11 16;");Label status=new Label("Enter the question facts, then calculate.");TextArea out=new TextArea();out.setEditable(false);out.setWrapText(true);out.setStyle("-fx-font-family:Consolas;-fx-font-size:13px;");go.setOnAction(e->{go.setDisable(true);status.setText("Calculating…");CompletableFuture.supplyAsync(()->{try{return call.get();}catch(Exception ex){throw new RuntimeException(ex);}}).whenComplete((body,err)->Platform.runLater(()->{go.setDisable(false);if(err!=null){Throwable c=err.getCause()==null?err:err.getCause();status.setText("Calculation failed: "+c.getMessage());return;}try{out.setText(format.apply(JSON.readTree(body)));status.setText("Worksheet calculated.");}catch(Exception ex){status.setText("Could not read result: "+ex.getMessage());}}));});VBox right=new VBox(10,h,go,status,out);right.setPadding(new Insets(18));SplitPane sp=new SplitPane(left,right);sp.setDividerPositions(.48);return sp;}
  private static VBox card(String title,GridPane grid,String note){Label h=new Label(title);h.setStyle("-fx-font-size:16px;-fx-font-weight:bold;");Label n=new Label(note);n.setWrapText(true);n.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");VBox v=new VBox(8,h,grid,n);v.setPadding(new Insets(14));v.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-background-radius:10;-fx-border-radius:10;");return v;}
  private static VBox info(String title,String text){Label h=new Label(title);h.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#1d4ed8;");Label n=new Label(text);n.setWrapText(true);VBox v=new VBox(7,h,n);v.setPadding(new Insets(14));v.setStyle("-fx-background-color:#eff6ff;-fx-border-color:#bfdbfe;-fx-background-radius:10;-fx-border-radius:10;");return v;}
  private static GridPane grid(Object... p){GridPane g=new GridPane();g.setHgap(12);g.setVgap(8);ColumnConstraints a=new ColumnConstraints();a.setMinWidth(250);ColumnConstraints b=new ColumnConstraints();b.setHgrow(Priority.ALWAYS);g.getColumnConstraints().addAll(a,b);for(int x=0;x<p.length;x+=2){g.add(new Label(String.valueOf(p[x])),0,x/2);g.add((Node)p[x+1],1,x/2);}return g;}
  private static TextField tf(String s){TextField t=new TextField(s);t.setMaxWidth(280);return t;} @SafeVarargs private static <T> ComboBox<T> combo(T d,T... values){ComboBox<T> c=new ComboBox<>();c.getItems().addAll(values);c.setValue(d);return c;} private static Tab tab(String t,Node n){Tab x=new Tab(t,n);x.setClosable(false);return x;} private static double n(TextField f){return Double.parseDouble(f.getText().trim());} private static String a(JsonNode r,String k){return String.format("%.1f A",r.path(k).asDouble());} private static String refs(JsonNode r){return "CEC reference: "+r.path("reference").asText()+"\n\nCheck: "+r.path("note").asText();} private interface Call{String get() throws Exception;} private interface Format{String apply(JsonNode r) throws Exception;}
}

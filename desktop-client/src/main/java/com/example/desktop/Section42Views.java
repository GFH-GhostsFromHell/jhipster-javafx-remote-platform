package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class Section42Views {
  private static final ObjectMapper JSON=new ObjectMapper();
  private static final int[] STD={15,20,25,30,35,40,45,50,60,70,80,90,100,110,125,150,175,200,225,250,300,350,400,450,500,600,700,800,1000,1200};
  private static final LinkedHashMap<String,Double> CU75=new LinkedHashMap<>();
  static{CU75.put("14 AWG",20d);CU75.put("12 AWG",25d);CU75.put("10 AWG",35d);CU75.put("8 AWG",50d);CU75.put("6 AWG",65d);CU75.put("4 AWG",85d);CU75.put("3 AWG",100d);CU75.put("2 AWG",115d);CU75.put("1 AWG",130d);CU75.put("1/0 AWG",150d);CU75.put("2/0 AWG",175d);CU75.put("3/0 AWG",200d);CU75.put("4/0 AWG",230d);}
  private Section42Views(){}

  public static Node create(PortalClient p,String u,String pw){
    BorderPane root=new BorderPane();VBox head=new VBox(5);head.setPadding(new Insets(18,22,10,22));
    Label h=new Label("Section 42 — Electric Welders");h.setStyle("-fx-font-size:21px;-fx-font-weight:bold;-fx-text-fill:#14213d;");
    Label s=new Label("Single-welder and multiple-welder feeder worksheets now follow the calculation order used in your practice material.");s.setWrapText(true);s.setStyle("-fx-text-fill:#64748b;");head.getChildren().addAll(h,s);
    TabPane tabs=new TabPane();tabs.getTabs().addAll(tab("Single Welder",single(p,u,pw)),tab("Multiple Welder Feeder",group()));root.setTop(head);root.setCenter(tabs);return root;
  }

  private static Node single(PortalClient p,String u,String pw){
    TextField kva=tf("30"),v=tf("600"),duty=tf("0.55"),condAmp=tf("35"),desc=tf("TECK90 copper / 75°C ampacity");ComboBox<String> type=combo("TRANSFORMER_ARC","TRANSFORMER_ARC","RESISTANCE");ComboBox<String> preset=combo("30% → 0.55","20% → 0.45","30% → 0.55","40% → 0.63","50% → 0.71","60% → 0.78","70% → 0.84","Custom");preset.setOnAction(e->{String x=preset.getValue();if(x.startsWith("20"))duty.setText("0.45");else if(x.startsWith("30"))duty.setText("0.55");else if(x.startsWith("40"))duty.setText("0.63");else if(x.startsWith("50"))duty.setText("0.71");else if(x.startsWith("60"))duty.setText("0.78");else if(x.startsWith("70"))duty.setText("0.84");});
    VBox in=new VBox(12,
      card("Question facts",grid("Welder type",type,"Rating (kVA)",kva,"Voltage (V)",v,"Duty-cycle preset",preset,"Duty-cycle multiplier",duty,"Selected conductor ampacity (A)",condAmp,"Conductor description",desc),"The selected conductor ampacity is entered after choosing the actual conductor from Table 2/4 or the applicable cable table."),
      info("Calculation order","1. Input current = kVA × 1000 ÷ V\n2. Apply duty-cycle factor to find conductor load\n3. Select conductor from applicable ampacity table\n4. OCPD Subrule 1 limit from welder input current\n5. OCPD Subrule 2 limit from selected conductor ampacity\n6. Use the controlling result and nuisance-tripping provision where permitted"));
    return worksheet(in,()->p.welderWorksheet(u,pw,"{\"kva\":"+n(kva)+",\"voltage\":"+n(v)+",\"dutyFactor\":"+n(duty)+",\"type\":\""+type.getValue()+"\",\"selectedConductorAmpacity\":"+n(condAmp)+"}"),r->
      "WELDER WORKSHEET\n\n1) INPUT CURRENT\n   kVA × 1000 ÷ V = "+a(r,"inputCurrentA")+"\n\n2) DUTY-CYCLE-ADJUSTED CONDUCTOR LOAD\n   Input current × duty factor = "+a(r,"dutyAdjustedConductorLoadA")+"\n   Select conductor for "+desc.getText()+".\n\n3) OCPD — SUBRULE 1\n   Multiplier = "+r.path("ocpdMultiplier").asDouble()+" ×\n   Calculated = "+a(r,"subrule1CalculatedA")+"\n   Common size = "+r.path("subrule1CommonA").asInt()+" A\n\n4) OCPD — SUBRULE 2\n   Selected conductor ampacity = "+a(r,"selectedConductorAmpacityA")+"\n   Calculated = "+a(r,"subrule2CalculatedA")+"\n   Common size = "+r.path("subrule2CommonA").asInt()+" A\n\n5) STARTING OUTCOME\n   Controlling common size = "+r.path("startingOutcomeA").asInt()+" A\n\n"+refs(r));
  }

  private static Node group(){
    VBox rowsBox=new VBox(6);List<WelderRow> rows=new ArrayList<>();
    String[] currents={"25","63","33","25","42"};String[] duties={"0.55","0.45","0.55","0.63","0.45"};String[] conductorAmps={"20","35","20","20","20"};
    GridPane header=new GridPane();header.setHgap(8);header.add(new Label("Welder"),0,0);header.add(new Label("Input A"),1,0);header.add(new Label("Duty factor"),2,0);header.add(new Label("Branch conductor ampacity A"),3,0);rowsBox.getChildren().add(header);
    for(int i=0;i<5;i++){WelderRow r=new WelderRow(i+1,currents[i],duties[i],conductorAmps[i]);rows.add(r);rowsBox.getChildren().add(r.node);}
    TextField ocpdFactor=tf("2.0");TextArea out=new TextArea();out.setEditable(false);out.setWrapText(true);out.setStyle("-fx-font-family:Consolas;-fx-font-size:13px;");Button calc=new Button("Calculate multiple-welder feeder");calc.setMaxWidth(Double.MAX_VALUE);calc.setStyle("-fx-background-color:#1d4ed8;-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:11 16;");
    calc.setOnAction(e->{try{
      double factor=n(ocpdFactor);if(factor<=0)throw new IllegalArgumentException("OCPD multiplier must be greater than zero");
      List<Weld> data=new ArrayList<>();for(WelderRow r:rows){double input=n(r.input),duty=n(r.duty),ca=n(r.conductorAmp);if(input<=0||duty<=0||ca<=0)throw new IllegalArgumentException("all five welder rows require positive current, duty factor and conductor ampacity");data.add(new Weld(r.number,input,duty,ca,input*duty));}
      List<Weld> ordered=new ArrayList<>(data);ordered.sort((x,y)->Double.compare(y.adjusted,x.adjusted));double feeder=0;double[] demand={1,1,.85,.70,.60};for(int i=0;i<ordered.size();i++)feeder+=ordered.get(i).adjusted*demand[i];
      Weld largest=data.get(0);double largestCalc=largest.input*factor;for(Weld w:data){double x=w.input*factor;if(x>largestCalc){largest=w;largestCalc=x;}}
      double feederOcpdCalc=largestCalc;for(Weld w:data)if(w.number!=largest.number)feederOcpdCalc+=w.adjusted;int feederOcpd=nextDown(feederOcpdCalc);
      StringBuilder b=new StringBuilder("MULTIPLE TRANSFORMER-ARC WELDER FEEDER\n\n1) DUTY-CYCLE-ADJUST EACH WELDER\n");for(Weld w:data)b.append(String.format("   Welder %d: %.2f A × %.2f = %.2f A\n",w.number,w.input,w.duty,w.adjusted));
      b.append("\n2) ORDER ADJUSTED LOADS FROM LARGEST TO SMALLEST\n");for(int i=0;i<ordered.size();i++)b.append(String.format("   %.2f A × %.0f%% = %.2f A\n",ordered.get(i).adjusted,demand[i]*100,ordered.get(i).adjusted*demand[i]));
      b.append(String.format("\n   FEEDER CONDUCTOR LOAD = %.2f A\n",feeder));b.append("   Starter Cu 75°C conductor = ").append(conductorFor(feeder)).append("\n");
      b.append("\n3) INDIVIDUAL WELDER OCPD CHECKS\n");for(Weld w:data){double s1=w.input*factor,s2=w.conductorAmp*factor;b.append(String.format("   Welder %d: input method %.2f A → %d A down; conductor method %.2f A → %d A up\n",w.number,s1,nextDown(s1),s2,nextUp(s2)));}
      b.append(String.format("\n4) FEEDER OCPD\n   Largest individual calculated protection = Welder %d: %.2f A\n",largest.number,largestCalc));b.append("   Add duty-adjusted currents of the other welders:\n");for(Weld w:data)if(w.number!=largest.number)b.append(String.format("     + %.2f A\n",w.adjusted));b.append(String.format("   Calculated feeder OCPD = %.2f A\n   Maximum common size = %d A\n",feederOcpdCalc,feederOcpd));
      b.append("\nCEC worksheet reference: Rule 42-006(2) and the supplied multiple-welder exercise. The default values reproduce the practice answer: 81 A feeder load and 175 A maximum feeder circuit breaker.\n\nThe 100% / 100% / 85% / 70% / 60% sequence is taken from that supplied five-welder worksheet. Use the applicable CEC welder demand rule/table for a different arrangement.");out.setText(b.toString());
    }catch(Exception ex){out.setText("Check inputs: "+ex.getMessage());}});
    VBox left=new VBox(12,card("Five-welder feeder worksheet",rowsBox,"Each welder has its own input current, duty factor and selected branch-conductor ampacity. The defaults are the exact practice values from your answer sheet."),card("Protection",grid("Individual welder OCPD multiplier",ocpdFactor),"The supplied transformer-arc worksheet uses 200% for the individual and feeder protection calculations."),calc);left.setPadding(new Insets(16));SplitPane sp=new SplitPane(scroll(left),out);sp.setDividerPositions(.56);return sp;
  }

  private static final class WelderRow{final int number;final TextField input,duty,conductorAmp;final GridPane node=new GridPane();WelderRow(int number,String i,String d,String c){this.number=number;input=tf(i);duty=tf(d);conductorAmp=tf(c);node.setHgap(8);node.add(new Label("#"+number),0,0);node.add(input,1,0);node.add(duty,2,0);node.add(conductorAmp,3,0);}}
  private record Weld(int number,double input,double duty,double conductorAmp,double adjusted){}
  private static int nextDown(double a){int last=STD[0];for(int x:STD){if(x>a)break;last=x;}return last;}private static int nextUp(double a){for(int x:STD)if(x>=a)return x;return(int)Math.ceil(a);}private static String conductorFor(double a){for(Map.Entry<String,Double> e:CU75.entrySet())if(e.getValue()>=a)return e.getKey()+" ("+e.getValue().intValue()+" A)";return "Above starter Cu 75°C lookup — verify Table 2";}
  private static Node worksheet(VBox inputs,Call call,Format format){inputs.setPadding(new Insets(14));ScrollPane left=new ScrollPane(inputs);left.setFitToWidth(true);Label h=new Label("Worked solution");h.setStyle("-fx-font-size:18px;-fx-font-weight:bold;");Button go=new Button("Calculate worksheet");go.setMaxWidth(Double.MAX_VALUE);go.setStyle("-fx-background-color:#1d4ed8;-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:11 16;");Label status=new Label("Enter the question facts, then calculate.");TextArea out=new TextArea();out.setEditable(false);out.setWrapText(true);out.setStyle("-fx-font-family:Consolas;-fx-font-size:13px;");go.setOnAction(e->{go.setDisable(true);status.setText("Calculating…");CompletableFuture.supplyAsync(()->{try{return call.get();}catch(Exception ex){throw new RuntimeException(ex);}}).whenComplete((body,err)->Platform.runLater(()->{go.setDisable(false);if(err!=null){Throwable c=err.getCause()==null?err:err.getCause();status.setText("Calculation failed: "+c.getMessage());return;}try{out.setText(format.apply(JSON.readTree(body)));status.setText("Worksheet calculated.");}catch(Exception ex){status.setText("Could not read result: "+ex.getMessage());}}));});VBox right=new VBox(10,h,go,status,out);right.setPadding(new Insets(18));SplitPane sp=new SplitPane(left,right);sp.setDividerPositions(.48);return sp;}
  private static VBox card(String title,Node body,String note){Label h=new Label(title);h.setStyle("-fx-font-size:16px;-fx-font-weight:bold;");Label n=new Label(note);n.setWrapText(true);n.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");VBox v=new VBox(8,h,body,n);v.setPadding(new Insets(14));v.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-background-radius:10;-fx-border-radius:10;");return v;}private static VBox info(String title,String text){Label h=new Label(title);h.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#1d4ed8;");Label n=new Label(text);n.setWrapText(true);VBox v=new VBox(7,h,n);v.setPadding(new Insets(14));v.setStyle("-fx-background-color:#eff6ff;-fx-border-color:#bfdbfe;-fx-background-radius:10;-fx-border-radius:10;");return v;}
  private static GridPane grid(Object... p){GridPane g=new GridPane();g.setHgap(12);g.setVgap(8);ColumnConstraints a=new ColumnConstraints();a.setMinWidth(250);ColumnConstraints b=new ColumnConstraints();b.setHgrow(Priority.ALWAYS);g.getColumnConstraints().addAll(a,b);for(int x=0;x<p.length;x+=2){g.add(new Label(String.valueOf(p[x])),0,x/2);g.add((Node)p[x+1],1,x/2);}return g;}
  private static ScrollPane scroll(Node n){ScrollPane s=new ScrollPane(n);s.setFitToWidth(true);return s;}private static TextField tf(String s){TextField t=new TextField(s);t.setMaxWidth(220);return t;} @SafeVarargs private static <T> ComboBox<T> combo(T d,T... values){ComboBox<T> c=new ComboBox<>();c.getItems().addAll(values);c.setValue(d);return c;} private static Tab tab(String t,Node n){Tab x=new Tab(t,n);x.setClosable(false);return x;} private static double n(TextField f){return Double.parseDouble(f.getText().trim());} private static String a(JsonNode r,String k){return String.format("%.1f A",r.path(k).asDouble());} private static String refs(JsonNode r){return "CEC reference: "+r.path("reference").asText()+"\n\nCheck: "+r.path("note").asText();} private interface Call{String get() throws Exception;} private interface Format{String apply(JsonNode r) throws Exception;}
}

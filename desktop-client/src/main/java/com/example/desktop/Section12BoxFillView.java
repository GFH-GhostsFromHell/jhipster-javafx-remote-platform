package com.example.desktop;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;

public final class Section12BoxFillView {
  private static final LinkedHashMap<String,Double> ALLOWANCE = new LinkedHashMap<>();
  static {
    ALLOWANCE.put("14 AWG",24.6);
    ALLOWANCE.put("12 AWG",28.7);
    ALLOWANCE.put("10 AWG",36.9);
    ALLOWANCE.put("8 AWG",45.1);
    ALLOWANCE.put("6 AWG",73.7);
  }
  private Section12BoxFillView(){}

  public static TitledPane create(){
    TextField capacity=tf("245"),devices=tf("0"),connectors=tf("0"),studs=tf("0"),deep=tf("0");
    List<Row> rows=new ArrayList<>();
    rows.add(new Row(size("14 AWG"),tf("6")));
    rows.add(new Row(size("12 AWG"),tf("0")));
    rows.add(new Row(size("10 AWG"),tf("0")));
    rows.add(new Row(size("8 AWG"),tf("0")));

    ComboBox<String> deviceSize=autoSize(),connectorSize=autoSize();
    TextArea out=new TextArea();out.setEditable(false);out.setWrapText(true);out.setPrefRowCount(19);out.setStyle("-fx-font-family:Consolas;-fx-font-size:13px;");
    Button calc=new Button("Calculate CEC box fill");calc.setMaxWidth(Double.MAX_VALUE);calc.setStyle("-fx-background-color:#1d4ed8;-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:10 14;");

    GridPane groups=new GridPane();groups.setHgap(10);groups.setVgap(7);
    Label hs=new Label("Conductor size / Table 22 allowance");hs.setStyle("-fx-font-weight:bold;-fx-text-fill:#64748b;");
    Label hq=new Label("Quantity");hq.setStyle("-fx-font-weight:bold;-fx-text-fill:#64748b;");
    groups.add(hs,0,0);groups.add(hq,1,0);
    for(int i=0;i<rows.size();i++){groups.add(rows.get(i).size,0,i+1);groups.add(rows.get(i).qty,1,i+1);}

    calc.setOnAction(e->{try{
      double box=positive(n(capacity),"Box volume");
      double conductors=0;int conductorCount=0;StringBuilder detail=new StringBuilder();
      for(Row r:rows){int q=whole(r.qty,"Conductor quantity");if(q==0)continue;double each=ALLOWANCE.get(r.size.getValue()),vol=q*each;conductors+=vol;conductorCount+=q;detail.append(q).append(" × ").append(r.size.getValue()).append(" @ ").append(f(each)).append(" mL = ").append(f(vol)).append(" mL\n");}
      String largest=largest(rows);String devSize=resolve(deviceSize.getValue(),largest),connSize=resolve(connectorSize.getValue(),largest);
      int dev=whole(devices,"Device count"),caps=whole(connectors,"Connector count"),stud=whole(studs,"Stud/hickey count");double extra=nonNegative(n(deep),"Deep-device volume");
      int pairs=caps/2;double deviceVol=dev*2*ALLOWANCE.get(devSize),connectorVol=pairs*ALLOWANCE.get(connSize),studVol=stud*ALLOWANCE.get(devSize);
      double required=conductors+deviceVol+connectorVol+studVol+extra,remaining=box-required;boolean pass=remaining>=-1e-9;
      out.setText("CEC 2024 BOX FILL — RULE 12-3034 / TABLE 22\n\n"+
        "INSULATED CONDUCTORS\n"+(detail.length()==0?"No insulated conductors entered\n":detail)+
        "Conductor subtotal = "+f(conductors)+" mL\n\n"+
        "ADJUSTMENTS\n"+
        "Flush devices: "+dev+" × 2 × "+devSize+" allowance ("+f(ALLOWANCE.get(devSize))+" mL) = "+f(deviceVol)+" mL\n"+
        "Fixture studs / hickeys: "+stud+" × "+devSize+" allowance = "+f(studVol)+" mL\n"+
        "Insulating-cap connectors: "+caps+" connector(s) = "+pairs+" pair allowance(s) × "+connSize+" = "+f(connectorVol)+" mL\n"+
        "Additional deep-device volume = "+f(extra)+" mL\n\n"+
        "TOTAL REQUIRED = "+f(required)+" mL\n"+
        "BOX VOLUME = "+f(box)+" mL\n"+
        "REMAINING = "+f(remaining)+" mL\n"+
        "RESULT: "+(pass?"PASS — volume is sufficient":"FAIL — box volume is insufficient")+"\n\n"+
        "CEC counting reminders\n"+
        "• Insulated conductors entering/leaving or passing through count.\n"+
        "• Conductors entirely inside the box do not count.\n"+
        "• One allowance is added for each fixture stud/hickey.\n"+
        "• One allowance is added for every pair of capped conductor connectors.\n"+
        "• Each flush-mounted single-strap device uses two conductor allowances.\n"+
        "• Locknuts, bushings, box connectors and clamps are disregarded by Rule 12-3034(5).\n"+
        "• Bare bonding conductors are not entered; insulated bonding conductors are entered as insulated conductors.\n\n"+
        "References: CEC 2024 Rule 12-3034, Table 22 and Table 23.");
    }catch(Exception ex){out.setText("Check inputs: "+ex.getMessage());}});

    VBox left=new VBox(12,
      card("1. Box volume",grid("Box usable / marked volume (mL)",capacity),"Use the marked/internal box volume. Table 23 may be used for listed standard box dimensions."),
      card("2. Insulated conductors",groups,"Choose the conductor size; the CEC 2024 Table 22 mL allowance is built into the dropdown, so no separate lookup is needed."),
      card("3. Devices / fittings",grid("Flush-mounted single-strap devices",devices,"Fixture studs / hickeys",studs,"Insulating-cap connectors",connectors,"Additional deep-device volume (mL)",deep,"Device/stud conductor-size allowance",deviceSize,"Capped-connector conductor-size allowance",connectorSize),"Auto uses the largest insulated conductor entered. If a device or connector involves a different conductor size, select that size explicitly."),
      calc,
      info("How to use this calculator","1. Enter the box volume in mL.\n2. Add each conductor size and quantity.\n3. Add devices, capped connectors and fixture studs/hickeys.\n4. Calculate and confirm required volume does not exceed box volume.\n\nCEC 2024 reference: Rule 12-3034; Table 22 conductor-space allowances; Table 23 standard-box conductor limits.\n\nTable 22 allowances built in: #14 = 24.6 mL, #12 = 28.7 mL, #10 = 36.9 mL, #8 = 45.1 mL, #6 = 73.7 mL."));
    left.setPadding(new Insets(14));
    ScrollPane leftScroll=new ScrollPane(left);leftScroll.setFitToWidth(true);leftScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    VBox right=new VBox(8,new Label("Worked box-fill result"),out);right.setPadding(new Insets(14));VBox.setVgrow(out,Priority.ALWAYS);
    SplitPane split=new SplitPane(leftScroll,right);split.setDividerPositions(.5);
    TitledPane pane=new TitledPane("Box fill — Table 22 conductor-size dropdown",split);pane.setCollapsible(true);return pane;
  }

  private static ComboBox<String> size(String selected){ComboBox<String> c=new ComboBox<>();for(var e:ALLOWANCE.entrySet())c.getItems().add(e.getKey()+" — "+f(e.getValue())+" mL");c.setValue(selected+" — "+f(ALLOWANCE.get(selected))+" mL");return c;}
  private static ComboBox<String> autoSize(){ComboBox<String> c=new ComboBox<>();c.getItems().add("Auto — largest entered conductor");for(var e:ALLOWANCE.entrySet())c.getItems().add(e.getKey()+" — "+f(e.getValue())+" mL");c.setValue("Auto — largest entered conductor");return c;}
  private static String key(String display){for(String k:ALLOWANCE.keySet())if(display.startsWith(k))return k;throw new IllegalArgumentException("Unknown conductor size");}
  private static String resolve(String display,String largest){return display.startsWith("Auto")?largest:key(display);}
  private static String largest(List<Row> rows){String best="14 AWG";double max=0;for(Row r:rows){int q=whole(r.qty,"Conductor quantity");if(q>0){String k=key(r.size.getValue());double a=ALLOWANCE.get(k);if(a>max){max=a;best=k;}}}return best;}
  private static int whole(TextField f,String label){double d=nonNegative(n(f),label);if(Math.rint(d)!=d)throw new IllegalArgumentException(label+" must be a whole number");return (int)d;}
  private static double n(TextField f){return Double.parseDouble(f.getText().trim());}
  private static double positive(double v,String label){if(!Double.isFinite(v)||v<=0)throw new IllegalArgumentException(label+" must be greater than zero");return v;}
  private static double nonNegative(double v,String label){if(!Double.isFinite(v)||v<0)throw new IllegalArgumentException(label+" cannot be negative");return v;}
  private static String f(double d){return String.format("%.1f",d);}
  private static TextField tf(String s){TextField t=new TextField(s);t.setMaxWidth(230);return t;}
  private static GridPane grid(Object... p){GridPane g=new GridPane();g.setHgap(10);g.setVgap(8);ColumnConstraints a=new ColumnConstraints();a.setMinWidth(250);ColumnConstraints b=new ColumnConstraints();b.setHgrow(Priority.ALWAYS);g.getColumnConstraints().addAll(a,b);for(int i=0;i<p.length;i+=2){g.add(new Label(String.valueOf(p[i])),0,i/2);g.add((Node)p[i+1],1,i/2);}return g;}
  private static VBox card(String title,Node body,String note){Label h=new Label(title);h.setStyle("-fx-font-size:15px;-fx-font-weight:bold;");Label n=new Label(note);n.setWrapText(true);n.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");VBox v=new VBox(8,h,body,n);v.setPadding(new Insets(12));v.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-background-radius:9;-fx-border-radius:9;");return v;}
  private static VBox info(String title,String text){Label h=new Label(title);h.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#1d4ed8;");Label n=new Label(text);n.setWrapText(true);n.setStyle("-fx-text-fill:#334155;-fx-font-size:12px;");VBox v=new VBox(6,h,n);v.setPadding(new Insets(12));v.setStyle("-fx-background-color:#eff6ff;-fx-border-color:#bfdbfe;-fx-background-radius:9;-fx-border-radius:9;");return v;}
  private record Row(ComboBox<String> size,TextField qty){}
}

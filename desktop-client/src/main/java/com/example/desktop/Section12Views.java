package com.example.desktop;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class Section12Views {
  private Section12Views(){}

  public static Node create(TitledPane conduitFill,TitledPane boxFill){
    BorderPane root=new BorderPane();
    VBox head=new VBox(5);head.setPadding(new Insets(18,22,10,22));
    Label h=new Label("Section 12 — Wiring Methods");h.setStyle("-fx-font-size:21px;-fx-font-weight:bold;-fx-text-fill:#14213d;");
    Label s=new Label("Conduit fill, Table D3 voltage-drop math, PVC thermal expansion, field-bending math, and support/strap quick references.");s.setWrapText(true);s.setStyle("-fx-text-fill:#64748b;");head.getChildren().addAll(h,s);
    TabPane tabs=new TabPane();
    tabs.getTabs().addAll(
      tab("Fill Calculators",fill(conduitFill,boxFill)),
      tab("Voltage Drop / D3",voltageDrop()),
      tab("PVC Expansion",pvcExpansion()),
      tab("Conduit Bending",bending()),
      tab("Supports & Straps",supports())
    );
    root.setTop(head);root.setCenter(tabs);return root;
  }

  private static Node fill(TitledPane c,TitledPane b){Accordion a=new Accordion();if(c!=null)a.getPanes().add(c);if(b!=null)a.getPanes().add(b);if(!a.getPanes().isEmpty())a.setExpandedPane(a.getPanes().get(0));VBox v=new VBox(12,a);v.setPadding(new Insets(18));return v;}

  private static Node voltageDrop(){
    TextField voltage=tf("240"),current=tf("40"),length=tf("30"),k=tf("2.54"),limit=tf("3");
    ComboBox<String> circuit=new ComboBox<>();
    circuit.getItems().addAll("1-phase / DC — f = 2.00","3-phase line-to-line — f = 1.73");
    circuit.setValue("1-phase / DC — f = 2.00");
    ComboBox<Integer> temp=new ComboBox<>();temp.getItems().addAll(60,75,90);temp.setValue(75);
    CheckBox extraLow=new CheckBox("Apply 0.86 extra-low-voltage copper multiplier (only when Table D3 Note 4 applies)");
    extraLow.setWrapText(true);
    TextArea out=new TextArea();out.setEditable(false);out.setWrapText(true);out.setStyle("-fx-font-family:Consolas;-fx-font-size:13px;");
    Button calc=new Button("Calculate Table D3 voltage drop / length / K");
    calc.setOnAction(e->{try{
      double v=positive(n(voltage),"Source voltage"),amps=positive(n(current),"Load current"),metres=positive(n(length),"One-way length"),tableK=positive(n(k),"Table D3 K"),pct=positive(n(limit),"Allowed voltage drop");
      double f=factor(circuit.getValue());double temperatureMultiplier=switch(temp.getValue()){case 60->.95;case 90->1.05;default->1.0;};double note4=extraLow.isSelected()?.86:1.0;double correction=temperatureMultiplier*note4;double effectiveK=tableK*correction;
      double vd=effectiveK*f*amps*metres/1000.0;double vdPct=vd/v*100.0;double allowedV=v*pct/100.0;double maxLength=allowedV*1000.0/(amps*effectiveK*f);double maxTableK=allowedV*1000.0/(amps*metres*f*correction);boolean pass=vd<=allowedV+1e-9;
      out.setText("CEC 2024 TABLE D3 METHOD\n\n"+
        "Entered Table D3 K: "+f(tableK)+" Ω/km\n"+
        "Circuit factor f: "+f(f)+"\n"+
        "Temperature multiplier: "+f(temperatureMultiplier)+"\n"+
        "Extra-low-voltage multiplier: "+f(note4)+"\n"+
        "Effective K used: "+f(effectiveK)+" Ω/km\n\n"+
        "1) VOLTAGE DROP\n"+
        "VD = K × f × I × L ÷ 1000\n"+
        "VD = "+f(vd)+" V\n"+
        "VD% = "+f(vdPct)+"%\n"+
        "Allowed at "+f(pct)+"% = "+f(allowedV)+" V\n"+
        "Result: "+(pass?"PASS":"EXCEEDS SELECTED LIMIT")+"\n\n"+
        "2) MAXIMUM ONE-WAY CONDUCTOR LENGTH\n"+
        "L ≤ VD × 1000 ÷ (I × K × f)\n"+
        "Maximum length = "+f(maxLength)+" m\n\n"+
        "3) MAXIMUM TABLE K FACTOR\n"+
        "K ≤ VD × 1000 ÷ (I × L × f), adjusted back to the Table D3 base K\n"+
        "Maximum Table D3 K = "+f(maxTableK)+" Ω/km\n\n"+
        "Use a Table D3 conductor/installation entry whose K is at or below the calculated maximum K. Enter the K value from your licensed CEC table; this calculator does not reproduce the table.\n\n"+
        "References: CEC 2024 Rule 8-102 and Appendix D, Table D3 notes. Table D3 uses K in ohms per circuit kilometre. 60°C and 90°C multipliers are 0.95 and 1.05. The 0.86 multiplier is only for the specific extra-low-voltage copper condition in Note 4.");
    }catch(Exception ex){out.setText("Check inputs: "+ex.getMessage());}});
    VBox left=new VBox(12,
      card("Table D3 inputs",grid("Source / connected-load voltage (V)",voltage,"Load current (A)",current,"One-way conductor length (m)",length,"Table D3 K (Ω/km)",k,"Circuit / voltage-drop factor",circuit,"Conductor operating temperature (°C)",temp,"Allowed voltage drop (%)",limit),"The 2024 Table D3 method uses VD = K × f × I × L / 1000. Use the K for the conductor material, size, installation method and power factor that matches the question."),
      extraLow,
      calc,
      info("Exam workflow","Known conductor: enter K and calculate VD.\nKnown conductor + allowed drop: read the maximum one-way length.\nUnknown conductor: use the maximum K result, then select a Table D3 K that does not exceed it."));
    left.setPadding(new Insets(16));SplitPane sp=new SplitPane(scroll(left),out);sp.setDividerPositions(.53);return sp;
  }

  private static Node pvcExpansion(){
    TextField run=tf("20"),minTemp=tf("-40"),maxTemp=tf("30"),coefficient=tf("0.0520"),jointTravel=tf("100");
    CheckBox grouted=new CheckBox("Conduit is grouted in concrete");
    TextArea out=new TextArea();out.setEditable(false);out.setWrapText(true);out.setStyle("-fx-font-family:Consolas;-fx-font-size:13px;");
    Button calc=new Button("Calculate PVC expansion-joint count");calc.setOnAction(e->{try{
      double metres=positive(n(run),"Run length"),tMin=n(minTemp),tMax=n(maxTemp),coef=positive(n(coefficient),"Expansion coefficient"),travel=positive(n(jointTravel),"Expansion-joint travel");if(tMax<tMin)throw new IllegalArgumentException("Maximum temperature must be at least the minimum temperature");
      double deltaT=tMax-tMin;double movement=metres*deltaT*coef;boolean trigger=!grouted.isSelected()&&movement>45.0;int count=trigger?Math.max(1,(int)Math.ceil(movement/travel)):0;double perJoint=count>0?movement/count:0;
      out.setText("RIGID PVC THERMAL EXPANSION\n\n"+
        "Temperature change ΔT = "+f(deltaT)+" °C\n"+
        "Expansion coefficient = "+f(coef)+" mm/m/°C\n"+
        "Movement = length × ΔT × coefficient\n"+
        "Movement = "+f(movement)+" mm\n\n"+
        "CEC 45 mm trigger: "+(grouted.isSelected()?"GROUTED-IN-CONCRETE EXCEPTION SELECTED":movement>45?"EXCEEDED":"NOT EXCEEDED")+"\n"+
        "Entered joint travel capacity = "+f(travel)+" mm per joint\n"+
        "Calculated minimum joint count = "+count+"\n"+
        (count>0?"Movement allocated per joint ≈ "+f(perJoint)+" mm\n":"")+
        "\nCEC 2024 Rule 12-1118 requires at least one expansion joint in a rigid PVC conduit run, unless grouted in concrete, when expected expansion exceeds 45 mm. The actual number, placement and preset position depend on the listed expansion joint's travel and manufacturer instructions.\n\nAppendix B coefficient for PVC conduit: 0.0520 mm per metre per °C. Some manufacturers publish a slightly different product-specific coefficient; use their value when required.");
    }catch(Exception ex){out.setText("Check inputs: "+ex.getMessage());}});
    VBox left=new VBox(12,
      card("Rigid PVC expansion inputs",grid("Conduit run length (m)",run,"Minimum expected temperature (°C)",minTemp,"Maximum expected temperature (°C)",maxTemp,"Expansion coefficient (mm/m/°C)",coefficient,"Joint travel capacity (mm)",jointTravel),"Use the maximum probable conduit temperature range during and after installation. For outdoor sun exposure, the conduit temperature can exceed ambient temperature; follow the product manufacturer's guidance."),
      grouted,
      calc,
      info("How joint count is estimated","Total movement = L × ΔT × coefficient. If the CEC 45 mm trigger is exceeded, this calculator divides total movement by the entered travel capacity and rounds up. Final joint quantity and placement must still follow the listed fitting manufacturer's instructions."));
    left.setPadding(new Insets(16));SplitPane sp=new SplitPane(scroll(left),out);sp.setDividerPositions(.53);return sp;
  }

  private static Node bending(){
    TextField rise=tf("150"),vertical=tf("150"),horizontal=tf("100"),stub=tf("500"),takeup=tf("130");
    ComboBox<Double> angle=new ComboBox<>();angle.getItems().addAll(10d,22.5,30d,45d,60d);angle.setValue(30d);
    TextArea out=new TextArea();out.setEditable(false);out.setWrapText(true);out.setStyle("-fx-font-family:Consolas;-fx-font-size:13px;");
    Button calc=new Button("Calculate bends");calc.setOnAction(e->{try{
      double a=Math.toRadians(angle.getValue());double r=n(rise);double mult=1/Math.sin(a);double spacing=r*mult;double shrink=r*Math.tan(a/2);
      double vr=n(vertical),hr=n(horizontal),trueOffset=Math.hypot(vr,hr);double rollSpacing=trueOffset*mult;double rollShrink=trueOffset*Math.tan(a/2);double stubMark=n(stub)-n(takeup);
      out.setText("OFFSET BEND\nMultiplier = "+f(mult)+"\nDistance between bend marks = "+f(spacing)+" mm\nCalculated shrink = "+f(shrink)+" mm\n\nROLLING OFFSET\nTrue offset = √(vertical² + horizontal²) = "+f(trueOffset)+" mm\nDistance between marks = "+f(rollSpacing)+" mm\nCalculated shrink = "+f(rollShrink)+" mm\n\n90° STUB-UP\nMark from end = desired stub − bender take-up\n= "+f(stubMark)+" mm\n\nFIELD NOTES\n• Offset multiplier = 1/sin(angle).\n• Shrink = offset × tan(angle/2).\n• Use the take-up marked for the actual bender/conduit size.\n• Minimum bend radius still has to comply with CEC Rule 12-924 and the raceway-specific rules.");
    }catch(Exception ex){out.setText("Check inputs: "+ex.getMessage());}});
    VBox left=new VBox(12,card("Two-bend offset",grid("Offset/rise (mm)",rise,"Bend angle",angle),"Enter the obstruction height. The calculator gives exact trig multiplier, bend spacing and shrink."),card("Rolling offset",grid("Vertical offset (mm)",vertical,"Horizontal offset (mm)",horizontal),"The true offset is the hypotenuse before the bend-angle multiplier is applied."),card("90° stub-up",grid("Desired stub length (mm)",stub,"Bender take-up (mm)",takeup),"Take-up varies by bender and conduit size, so enter the value stamped on the bender."),calc);left.setPadding(new Insets(16));
    SplitPane sp=new SplitPane(scroll(left),out);sp.setDividerPositions(.5);return sp;
  }

  private static Node supports(){
    ComboBox<String> type=new ComboBox<>();type.getItems().addAll("Rigid metal conduit","EMT","Rigid PVC conduit","Flexible metal conduit","Liquid-tight flexible conduit","ENT");type.setValue("EMT");
    ComboBox<Integer> size=new ComboBox<>();size.getItems().addAll(16,21,27,35,41,53,63,78,91,103,129,155);size.setValue(21);
    TextField run=tf("10");TextArea out=new TextArea();out.setEditable(false);out.setWrapText(true);out.setStyle("-fx-font-family:Consolas;-fx-font-size:13px;");
    Button calc=new Button("Show support rule / estimate");calc.setOnAction(e->{try{Rule r=rule(type.getValue(),size.getValue());double L=n(run);int estimate=estimate(L,r.spacing,r.nearEnd);out.setText("RACEWAY: "+type.getValue()+"\nTRADE SIZE: "+size.getValue()+"\n\nMaximum support spacing: "+f(r.spacing)+" m\nNear box/fitting requirement: "+(r.nearEnd>0?"within "+f(r.nearEnd)+" m":"no separate near-box distance in this quick lookup")+"\nRule reference: "+r.ref+"\n\nPlanning estimate for a "+f(L)+" m straight run between boxes: about "+estimate+" support point(s).\n\nThis count is a layout aid only. Couplings, changes of direction, grouped raceways, equipment, expansion requirements and field conditions can require additional supports.\n\n"+r.note); }catch(Exception ex){out.setText("Check inputs: "+ex.getMessage());}});
    VBox left=new VBox(12,card("Support / strap lookup",grid("Raceway type",type,"Trade size",size,"Straight run length between boxes (m)",run),"The calculator returns the maximum spacing and any near-box/fitting requirement used by the quick-reference dataset."),calc,info("Quick reminders","EMT: secure within 1 m of each outlet box, junction box, cabinet or fitting; spacing then follows Rule 12-1010.\nFlexible metal conduit: generally 1.5 m spacing and within 300 mm on each side of boxes/fittings, subject to listed exceptions.\nLiquid-tight flexible conduit: within 300 mm of boxes/fittings and max 1.5 m spacing.\nENT: within 1 m of boxes/couplings/fittings and max 1 m spacing.\nRigid PVC spacing varies by trade size and must allow for expansion/contraction."));left.setPadding(new Insets(16));
    SplitPane sp=new SplitPane(scroll(left),out);sp.setDividerPositions(.52);return sp;
  }

  private static Rule rule(String t,int s){
    return switch(t){
      case "Rigid metal conduit" -> new Rule(s<=21?1.5:s<=35?2.0:3.0,0,"CEC 2024 Rule 12-1010","For grouped mixed sizes, use the support spacing required for the smallest conduit in the group.");
      case "EMT" -> new Rule(s<=21?1.5:s<=35?2.0:3.0,1.0,"CEC 2024 Rules 12-1406 and 12-1010","EMT must be installed as a complete system and secured within 1 m of boxes, cabinets and fittings.");
      case "Rigid PVC conduit" -> new Rule(s<=27?.75:s<=41?1.2:s<=53?1.5:s<=78?1.8:s<=129?2.1:2.5,0,"CEC 2024 Rule 12-1114","Except where sufficiently encased/embedded, support PVC to permit linear movement for expansion and contraction.");
      case "Flexible metal conduit" -> new Rule(1.5,.3,"CEC 2024 Rule 12-1010(3)","Exceptions include fished installations and short terminal lengths where flexibility is necessary.");
      case "Liquid-tight flexible conduit" -> new Rule(1.5,.3,"CEC 2024 Rule 12-1308","Concealed runs may have an exception where it is impracticable to provide supports.");
      case "ENT" -> new Rule(1.0,1.0,"CEC 2024 Rule 12-1504","ENT must also be protected where subject to mechanical damage.");
      default -> throw new IllegalArgumentException("Unsupported raceway type");
    };
  }
  private static double factor(String circuit){return circuit!=null&&circuit.startsWith("3-phase")?1.73:2.0;}
  private static double positive(double value,String label){if(!Double.isFinite(value)||value<=0)throw new IllegalArgumentException(label+" must be greater than zero");return value;}
  private static int estimate(double L,double spacing,double near){if(L<=0)return 0;if(near<=0)return Math.max(1,(int)Math.ceil(L/spacing));if(L<=2*near)return 1;return 2+Math.max(0,(int)Math.ceil((L-2*near)/spacing)-1);}
  private record Rule(double spacing,double nearEnd,String ref,String note){}
  private static VBox card(String title,GridPane g,String note){Label h=new Label(title);h.setStyle("-fx-font-size:16px;-fx-font-weight:bold;");Label n=new Label(note);n.setWrapText(true);n.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");VBox v=new VBox(8,h,g,n);v.setPadding(new Insets(14));v.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-background-radius:10;-fx-border-radius:10;");return v;}
  private static VBox info(String t,String x){Label h=new Label(t);h.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#1d4ed8;");Label n=new Label(x);n.setWrapText(true);VBox v=new VBox(7,h,n);v.setPadding(new Insets(14));v.setStyle("-fx-background-color:#eff6ff;-fx-border-color:#bfdbfe;-fx-background-radius:10;-fx-border-radius:10;");return v;}
  private static GridPane grid(Object... p){GridPane g=new GridPane();g.setHgap(12);g.setVgap(8);for(int i=0;i<p.length;i+=2){g.add(new Label(String.valueOf(p[i])),0,i/2);g.add((Node)p[i+1],1,i/2);}return g;}
  private static ScrollPane scroll(Node n){ScrollPane s=new ScrollPane(n);s.setFitToWidth(true);return s;}private static Tab tab(String t,Node n){Tab x=new Tab(t,n);x.setClosable(false);return x;}private static TextField tf(String s){TextField t=new TextField(s);t.setMaxWidth(260);return t;}private static double n(TextField f){return Double.parseDouble(f.getText().trim());}private static String f(double d){return String.format("%.2f",d);}
}

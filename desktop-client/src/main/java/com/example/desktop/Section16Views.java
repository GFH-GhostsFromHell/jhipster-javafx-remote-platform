package com.example.desktop;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;

public final class Section16Views {
  private Section16Views(){}

  public static Node create(){
    return createEmbedded("Section 16 provides the low-voltage / Class 1 and Class 2 circuit rules used by many control, signalling and auxiliary circuits.");
  }

  public static Node createEmbedded(String context){
    BorderPane root=new BorderPane();
    VBox head=new VBox(5);head.setPadding(new Insets(18,22,10,22));
    Label h=new Label("Section 16 — Class 1 & Class 2 Circuits");h.setStyle("-fx-font-size:21px;-fx-font-weight:bold;-fx-text-fill:#14213d;");
    Label s=new Label(context);s.setWrapText(true);s.setStyle("-fx-text-fill:#64748b;");head.getChildren().addAll(h,s);
    TabPane tabs=new TabPane();tabs.getTabs().addAll(tab("Class 2 Power Circuit",class2()),tab("Class 1 / Listed Equipment",class1()));
    root.setTop(head);root.setCenter(tabs);return root;
  }

  private static Node class2(){
    TextField va=tf("100"),volts=tf("24"),load=tf("4.0"),fuseSizes=tf("0.5,1,1.5,2,2.5,3,4,5"),cableAmp=tf("3.5");
    ComboBox<String> cable=combo("3C #20 AWG LVT — 3.5 A (practice)","3C #20 AWG LVT — 3.5 A (practice)","Custom / enter Table 57 ampacity");
    cable.setOnAction(e->{if(cable.getValue().startsWith("3C #20"))cableAmp.setText("3.5");});
    TextArea out=out();Button calc=button("Calculate Class 2 circuit");
    calc.setOnAction(e->{try{
      double sourceVa=n(va),v=n(volts),actual=n(load),amp=n(cableAmp);if(sourceVa<=0||v<=0||actual<0||amp<=0)throw new IllegalArgumentException("values must be positive");
      double max=sourceVa/v;List<Double> sizes=parse(fuseSizes.getText());Double selected=null;for(double x:sizes)if(x<=max&&(selected==null||x>selected))selected=x;
      boolean cablePass=actual<=amp;
      String selectedText=selected==null?"No listed size entered is low enough":String.format("%.2f A",selected);
      out.setText("CLASS 2 LOW-ENERGY POWER CIRCUIT\n\n"+
        "1) SOURCE-LIMITED CIRCUIT CURRENT\n"+
        String.format("   %.2f VA ÷ %.2f V = %.2f A\n",sourceVa,v,max)+
        "   Practice reference: Rule 16-200(1)(b).\n\n"+
        "2) MAXIMUM OCPD / FUSE WORKSHEET\n"+
        "   Maximum calculated current = "+String.format("%.2f A",max)+"\n"+
        "   Largest available fuse/OCPD entered that does not exceed it = "+selectedText+"\n"+
        "   The fuse-size list above is USER-EDITABLE and is not a reproduced CEC table.\n\n"+
        "3) CABLE AMPACITY CHECK\n"+
        "   Circuit load = "+String.format("%.2f A",actual)+"\n"+
        "   Selected cable ampacity = "+String.format("%.2f A",amp)+"\n"+
        "   Result = "+(cablePass?"PASS":"FAIL — cable ampacity is too small")+"\n\n"+
        "Practice reference: Rule 16-210(6) and Table 57. The validated practice example in your material gives 3C #20 AWG LVT cable an allowable ampacity of 3.5 A.\n\n"+
        "Use the actual Table 57 entry / listed cable data for any other cable construction.");
    }catch(Exception ex){out.setText("Check inputs: "+ex.getMessage());}});
    VBox left=new VBox(12,
      card("Source / fuse sizing",grid("Power-source rating (VA)",va,"Circuit voltage (V)",volts,"Available fuse/OCPD sizes (A)",fuseSizes),"This reproduces the practice pattern: 100 VA ÷ 24 V = 4.17 A, so the selected overcurrent device must not exceed the Class 2 source limit."),
      card("Conductor check",grid("Cable preset",cable,"Cable ampacity from Table 57 / listing (A)",cableAmp,"Actual circuit load (A)",load),"For cable sizes other than the validated practice preset, enter the ampacity from the applicable Table 57 row or listed cable data."),calc);
    left.setPadding(new Insets(16));SplitPane sp=new SplitPane(scroll(left),out);sp.setDividerPositions(.52);return sp;
  }

  private static Node class1(){
    TextField va=tf("120"),volts=tf("24"),load=tf("5"),cond=tf("7"),proposed=tf("5"),maxDevice=tf("5");
    TextArea out=out();Button calc=button("Check Class 1 circuit");
    calc.setOnAction(e->{try{
      double sourceVa=n(va),v=n(volts),actual=n(load),amp=n(cond),device=n(proposed),limit=n(maxDevice);if(sourceVa<=0||v<=0||actual<0||amp<=0||device<=0||limit<=0)throw new IllegalArgumentException("values must be positive");
      double sourceCurrent=sourceVa/v;boolean conductorPass=amp>=actual;boolean devicePass=device<=limit;boolean loadWithinSource=actual<=sourceCurrent;
      out.setText("CLASS 1 / LISTED LOW-VOLTAGE CIRCUIT CHECK\n\n"+
        String.format("Source current equivalent: %.2f VA ÷ %.2f V = %.2f A\n\n",sourceVa,v,sourceCurrent)+
        "Load current = "+String.format("%.2f A",actual)+"\n"+
        "Conductor ampacity entered = "+String.format("%.2f A",amp)+" → "+(conductorPass?"PASS":"FAIL")+"\n"+
        "Proposed fuse/OCPD = "+String.format("%.2f A",device)+"\n"+
        "Maximum permitted by equipment / applicable rule entered = "+String.format("%.2f A",limit)+" → "+(devicePass?"PASS":"FAIL")+"\n"+
        "Load within source rating = "+(loadWithinSource?"PASS":"FAIL")+"\n\n"+
        "IMPORTANT: unlike the Class 2 100 VA / 24 V practice example, the material available here does not support one universal Class 1 fuse formula for every application. Enter the maximum permitted OCPD from the actual listed source/equipment and applicable Section 16 rule, then use this worksheet as the conductor/fuse compliance check.");
    }catch(Exception ex){out.setText("Check inputs: "+ex.getMessage());}});
    VBox left=new VBox(12,
      card("Class 1 circuit facts",grid("Power source / transformer VA",va,"Circuit voltage (V)",volts,"Actual load current (A)",load,"Selected conductor ampacity (A)",cond),"Use actual equipment/listing and the applicable Section 16 Class 1 rule to establish the circuit limits."),
      card("Protection check",grid("Proposed fuse/OCPD (A)",proposed,"Maximum OCPD from equipment / applicable rule (A)",maxDevice),"The app does not invent a universal Class 1 fuse multiplier. This field keeps the calculation tied to the specific listed equipment or Code rule."),calc);
    left.setPadding(new Insets(16));SplitPane sp=new SplitPane(scroll(left),out);sp.setDividerPositions(.52);return sp;
  }

  private static List<Double> parse(String s){List<Double> out=new ArrayList<>();for(String p:s.split(",")){String x=p.trim();if(!x.isEmpty())out.add(Double.parseDouble(x));}if(out.isEmpty())throw new IllegalArgumentException("enter at least one available fuse size");Collections.sort(out);return out;}
  private static VBox card(String title,GridPane grid,String note){Label h=new Label(title);h.setStyle("-fx-font-size:16px;-fx-font-weight:bold;");Label n=new Label(note);n.setWrapText(true);n.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");VBox v=new VBox(8,h,grid,n);v.setPadding(new Insets(14));v.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-background-radius:10;-fx-border-radius:10;");return v;}
  private static GridPane grid(Object... p){GridPane g=new GridPane();g.setHgap(12);g.setVgap(8);ColumnConstraints a=new ColumnConstraints();a.setMinWidth(260);ColumnConstraints b=new ColumnConstraints();b.setHgrow(Priority.ALWAYS);g.getColumnConstraints().addAll(a,b);for(int i=0;i<p.length;i+=2){g.add(new Label(String.valueOf(p[i])),0,i/2);g.add((Node)p[i+1],1,i/2);}return g;}
  private static TextField tf(String s){TextField t=new TextField(s);t.setMaxWidth(300);return t;}private static TextArea out(){TextArea t=new TextArea();t.setEditable(false);t.setWrapText(true);t.setStyle("-fx-font-family:Consolas;-fx-font-size:13px;");return t;}private static Button button(String s){Button b=new Button(s);b.setMaxWidth(Double.MAX_VALUE);b.setStyle("-fx-background-color:#1d4ed8;-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:11 16;");return b;}private static ScrollPane scroll(Node n){ScrollPane s=new ScrollPane(n);s.setFitToWidth(true);return s;}private static Tab tab(String t,Node n){Tab x=new Tab(t,n);x.setClosable(false);return x;}private static double n(TextField f){return Double.parseDouble(f.getText().trim());}@SafeVarargs private static <T> ComboBox<T> combo(T d,T... values){ComboBox<T> c=new ComboBox<>();c.getItems().addAll(values);c.setValue(d);return c;}
}

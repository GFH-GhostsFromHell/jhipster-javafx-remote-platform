package com.example.desktop;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class Section4Views {
  private Section4Views() {}

  public static Node create() {
    BorderPane root = new BorderPane();
    VBox head = new VBox(5);
    head.setPadding(new Insets(18,22,10,22));
    Label h = new Label("Section 4 — Conductors / Busbars");
    h.setStyle("-fx-font-size:21px;-fx-font-weight:bold;-fx-text-fill:#14213d;");
    Label s = new Label("Worksheet-based bare-busbar sizing, reverse sizing, parallel strap count, and editable current-density calculations.");
    s.setWrapText(true); s.setStyle("-fx-text-fill:#64748b;");
    head.getChildren().addAll(h,s);

    TabPane tabs = new TabPane();
    tabs.getTabs().addAll(
      tab("Dimensions → Ampacity", dimensionsToAmpacity()),
      tab("Current → Bar Size", currentToSize()),
      tab("Parallel Straps / Bars", parallelStraps()),
      tab("Current Density", currentDensity())
    );
    root.setTop(head); root.setCenter(tabs); return root;
  }

  private static Node dimensionsToAmpacity() {
    ComboBox<String> material = material();
    TextField width=tf("50"), thickness=tf("6");
    TextArea out=output();
    Button calc=new Button("Calculate busbar ampacity");
    calc.setOnAction(e->{try{
      double w=positive(n(width),"Width"),t=positive(n(thickness),"Thickness");
      double area=w*t,k=rating(material.getValue()),raw=area/645.0*k;
      boolean practiceApplies=t<=6.35;
      double permitted=practiceApplies?Math.min(raw,1200.0):raw;
      out.setText("BUSBAR — DIMENSIONS TO AMPACITY\n\n"+
        "Material: "+material.getValue()+"\n"+
        "Area = width × thickness\n"+
        "Area = "+f(w)+" × "+f(t)+" = "+f(area)+" mm²\n"+
        "Area in in² = "+f(area/645.0)+"\n\n"+
        "Worksheet method:\nI = (Area ÷ 645) × "+f(k)+"\n"+
        "Calculated current = "+f(raw)+" A\n"+
        (practiceApplies?"Rule 4-036 practice cap = 1200 A\nPermitted worksheet value = "+f(permitted)+" A\n":"Thickness is above 6.35 mm, so do not apply the flat-bare-busbar Rule 4-036 practice shortcut without checking the actual CEC requirements.\n")+
        "\nPractice reference: copper = 1000 A/in²; aluminum = 700 A/in²; 1 in² ≈ 645 mm².");
    }catch(Exception ex){out.setText("Check inputs: "+ex.getMessage());}});
    return split(card("Known busbar dimensions",grid("Material",material,"Width (mm)",width,"Thickness (mm)",thickness),"Use this when the bar dimensions are known and you need the worksheet ampacity."),calc,out,
      "CEC 2024 Rule 4-036 practice method: flat bare busbars up to 6.35 mm thick use the 1000 A/in² copper or 700 A/in² aluminum worksheet method, with the practice-bank maximum of 1200 A. Verify the actual Code and equipment design before field use.");
  }

  private static Node currentToSize() {
    ComboBox<String> material=material();
    TextField current=tf("1200"), thickness=tf("6"), width=tf("100");
    TextArea out=output();
    Button calc=new Button("Calculate required area / width / thickness");
    calc.setOnAction(e->{try{
      double amps=positive(n(current),"Target current"),t=positive(n(thickness),"Known thickness"),w=positive(n(width),"Known width"),k=rating(material.getValue());
      double area=amps*645.0/k,requiredWidth=area/t,requiredThickness=area/w;
      out.setText("BUSBAR — CURRENT TO REQUIRED SIZE\n\n"+
        "Target current = "+f(amps)+" A\n"+
        "Material factor = "+f(k)+" A/in²\n\n"+
        "1) REQUIRED CROSS-SECTION\n"+
        "A = I × 645 ÷ K\n"+
        "A = "+f(area)+" mm²\n\n"+
        "2) REQUIRED WIDTH WITH "+f(t)+" mm THICKNESS\n"+
        "W = A ÷ T = "+f(requiredWidth)+" mm\n\n"+
        "3) REQUIRED THICKNESS WITH "+f(w)+" mm WIDTH\n"+
        "T = A ÷ W = "+f(requiredThickness)+" mm\n\n"+
        (amps>1200?"NOTE: target current exceeds the 1200 A single-bar practice limit; use parallel bars/straps or a separately engineered arrangement and verify Rule 4-036.\n":"")+
        "Example from your practice method: 1200 A copper → 774 mm²; at 6 mm thick, required width ≈ 129 mm.");
    }catch(Exception ex){out.setText("Check inputs: "+ex.getMessage());}});
    return split(card("Reverse-size a busbar",grid("Material",material,"Target current (A)",current,"Known thickness for width calculation (mm)",thickness,"Known width for thickness calculation (mm)",width),"This runs all three reverse calculations at once: required area, required width, and required thickness."),calc,out,
      "Formula set: copper A = I × 645 / 1000; aluminum A = I × 645 / 700; W = A / T; T = A / W.");
  }

  private static Node parallelStraps() {
    ComboBox<String> material=material();
    TextField total=tf("3000"), width=tf("100"), thickness=tf("6");
    TextArea out=output();
    Button calc=new Button("Calculate number of parallel bars / straps");
    calc.setOnAction(e->{try{
      double amps=positive(n(total),"Total current"),w=positive(n(width),"Bar width"),t=positive(n(thickness),"Bar thickness"),k=rating(material.getValue());
      double area=w*t,rawPer=area/645.0*k;
      if(t>6.35) throw new IllegalArgumentException("This worksheet strap method is limited to the flat-bar practice case at or below 6.35 mm thickness");
      double per=Math.min(rawPer,1200.0);
      int count=(int)Math.ceil(amps/per);
      double actualTotal=count*per,loadEach=amps/count;
      out.setText("PARALLEL BUSBAR / STRAP COUNT\n\n"+
        "One bar area = "+f(w)+" × "+f(t)+" = "+f(area)+" mm²\n"+
        "Raw one-bar worksheet ampacity = "+f(rawPer)+" A\n"+
        "One-bar value used after 1200 A practice cap = "+f(per)+" A\n\n"+
        "Number of bars N = total current ÷ one-bar current, rounded UP\n"+
        "N = "+f(amps)+" ÷ "+f(per)+" = "+f(amps/per)+" → "+count+" bars / straps\n\n"+
        "Nominal combined worksheet capacity = "+f(actualTotal)+" A\n"+
        "Equal-share current at target load = "+f(loadEach)+" A per bar\n"+
        "Total metal cross-section = "+f(area*count)+" mm²\n\n"+
        "This is the arithmetic strap-count method. Real parallel-bus arrangements also require appropriate spacing, connections, current sharing, enclosure/temperature design and applicable equipment standards.");
    }catch(Exception ex){out.setText("Check inputs: "+ex.getMessage());}});
    return split(card("Parallel bars / straps",grid("Material",material,"Total required current (A)",total,"Each bar width (mm)",width,"Each bar thickness (mm)",thickness),"Calculates the worksheet capacity of one strap/bar, then rounds the required quantity up to the next whole bar."),calc,out,
      "Number of straps formula: N = I(total) ÷ I(one strap), rounded up. Each strap's worksheet current comes from area / 645 × 1000 for copper or ×700 for aluminum.");
  }

  private static Node currentDensity() {
    TextField current=tf("1200"), density=tf("1.2"), thickness=tf("10");
    TextArea out=output();
    Button calc=new Button("Calculate by current density");
    calc.setOnAction(e->{try{
      double amps=positive(n(current),"Current"),j=positive(n(density),"Current density"),t=positive(n(thickness),"Thickness");
      double area=amps/j,w=area/t;
      out.setText("EDITABLE CURRENT-DENSITY METHOD\n\n"+
        "J = I ÷ A\nA = I ÷ J\n\n"+
        "A = "+f(amps)+" ÷ "+f(j)+" = "+f(area)+" mm²\n"+
        "With "+f(t)+" mm thickness:\nW = A ÷ T = "+f(w)+" mm\n\n"+
        "Example: 1200 A ÷ 1.2 A/mm² = 1000 mm².\n\n"+
        "The current-density value is user-entered. This tab is an engineering/study calculation and is NOT a substitute for the CEC Rule 4-036 busbar method, manufacturer data, thermal-rise analysis, or equipment certification.");
    }catch(Exception ex){out.setText("Check inputs: "+ex.getMessage());}});
    return split(card("Current-density sizing",grid("Target current I (A)",current,"Selected current density J (A/mm²)",density,"Selected thickness (mm)",thickness),"Use this only when the question or engineering design explicitly gives an allowable current density."),calc,out,
      "Generic formula: J = I/A and A = I/J. Keep this separate from the 1000/700 A/in² CEC practice shortcut.");
  }

  private static ComboBox<String> material(){ComboBox<String> b=new ComboBox<>();b.getItems().addAll("Copper","Aluminum");b.setValue("Copper");return b;}
  private static double rating(String material){return "Aluminum".equals(material)?700.0:1000.0;}
  private static TextArea output(){TextArea a=new TextArea();a.setEditable(false);a.setWrapText(true);a.setStyle("-fx-font-family:Consolas;-fx-font-size:13px;");return a;}
  private static Node split(VBox input,Button calc,TextArea out,String note){VBox left=new VBox(12,input,calc,info("Method note",note));left.setPadding(new Insets(16));SplitPane sp=new SplitPane(scroll(left),out);sp.setDividerPositions(.52);return sp;}
  private static VBox card(String title,GridPane g,String note){Label h=new Label(title);h.setStyle("-fx-font-size:16px;-fx-font-weight:bold;");Label n=new Label(note);n.setWrapText(true);n.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");VBox v=new VBox(8,h,g,n);v.setPadding(new Insets(14));v.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-background-radius:10;-fx-border-radius:10;");return v;}
  private static VBox info(String t,String x){Label h=new Label(t);h.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#1d4ed8;");Label n=new Label(x);n.setWrapText(true);VBox v=new VBox(7,h,n);v.setPadding(new Insets(14));v.setStyle("-fx-background-color:#eff6ff;-fx-border-color:#bfdbfe;-fx-background-radius:10;-fx-border-radius:10;");return v;}
  private static GridPane grid(Object... p){GridPane g=new GridPane();g.setHgap(12);g.setVgap(8);for(int i=0;i<p.length;i+=2){g.add(new Label(String.valueOf(p[i])),0,i/2);g.add((Node)p[i+1],1,i/2);}return g;}
  private static ScrollPane scroll(Node n){ScrollPane s=new ScrollPane(n);s.setFitToWidth(true);return s;}
  private static Tab tab(String t,Node n){Tab x=new Tab(t,n);x.setClosable(false);return x;}
  private static TextField tf(String s){TextField t=new TextField(s);t.setMaxWidth(260);return t;}
  private static double n(TextField f){return Double.parseDouble(f.getText().trim());}
  private static double positive(double value,String label){if(!Double.isFinite(value)||value<=0)throw new IllegalArgumentException(label+" must be greater than zero");return value;}
  private static String f(double d){return String.format("%.2f",d);}
}

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
    Label s=new Label("Conduit fill, box fill, field-bending math, and support/strap quick references.");s.setWrapText(true);s.setStyle("-fx-text-fill:#64748b;");head.getChildren().addAll(h,s);
    TabPane tabs=new TabPane();
    tabs.getTabs().addAll(tab("Fill Calculators",fill(conduitFill,boxFill)),tab("Conduit Bending",bending()),tab("Supports & Straps",supports()));
    root.setTop(head);root.setCenter(tabs);return root;
  }

  private static Node fill(TitledPane c,TitledPane b){Accordion a=new Accordion();if(c!=null)a.getPanes().add(c);if(b!=null)a.getPanes().add(b);if(!a.getPanes().isEmpty())a.setExpandedPane(a.getPanes().get(0));VBox v=new VBox(12,a);v.setPadding(new Insets(18));return v;}

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
  private static int estimate(double L,double spacing,double near){if(L<=0)return 0;if(near<=0)return Math.max(1,(int)Math.ceil(L/spacing));if(L<=2*near)return 1;return 2+Math.max(0,(int)Math.ceil((L-2*near)/spacing)-1);}
  private record Rule(double spacing,double nearEnd,String ref,String note){}
  private static VBox card(String title,GridPane g,String note){Label h=new Label(title);h.setStyle("-fx-font-size:16px;-fx-font-weight:bold;");Label n=new Label(note);n.setWrapText(true);n.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");VBox v=new VBox(8,h,g,n);v.setPadding(new Insets(14));v.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-background-radius:10;-fx-border-radius:10;");return v;}
  private static VBox info(String t,String x){Label h=new Label(t);h.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#1d4ed8;");Label n=new Label(x);n.setWrapText(true);VBox v=new VBox(7,h,n);v.setPadding(new Insets(14));v.setStyle("-fx-background-color:#eff6ff;-fx-border-color:#bfdbfe;-fx-background-radius:10;-fx-border-radius:10;");return v;}
  private static GridPane grid(Object... p){GridPane g=new GridPane();g.setHgap(12);g.setVgap(8);for(int i=0;i<p.length;i+=2){g.add(new Label(String.valueOf(p[i])),0,i/2);g.add((Node)p[i+1],1,i/2);}return g;}
  private static ScrollPane scroll(Node n){ScrollPane s=new ScrollPane(n);s.setFitToWidth(true);return s;}private static Tab tab(String t,Node n){Tab x=new Tab(t,n);x.setClosable(false);return x;}private static TextField tf(String s){TextField t=new TextField(s);t.setMaxWidth(260);return t;}private static double n(TextField f){return Double.parseDouble(f.getText().trim());}private static String f(double d){return String.format("%.2f",d);}
}

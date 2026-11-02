package com.example.desktop;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.function.Supplier;

public final class AdvancedCalculationViews {
  private AdvancedCalculationViews(){}

  public static Node create(){
    BorderPane root=new BorderPane();
    VBox head=new VBox(5);head.setPadding(new Insets(18,22,10,22));
    Label h=new Label("Advanced CEC Calculation Pack");h.setStyle("-fx-font-size:21px;-fx-font-weight:bold;-fx-text-fill:#14213d;");
    Label s=new Label("Grounding/bonding, fire pumps, neutral sizing, advanced service demand, motor feeders, transformer fault/protection, HVAC, fire alarm power, and conductor derating.");s.setWrapText(true);s.setStyle("-fx-text-fill:#64748b;");head.getChildren().addAll(h,s);
    TabPane tabs=new TabPane();
    tabs.getTabs().addAll(tab("Grounding / Bonding",grounding()),tab("Fire Pump",firePump()),tab("Neutral",neutral()),tab("Services",services()),tab("Motor Feeder",motor()),tab("Transformer",transformer()),tab("Heating / HVAC",hvac()),tab("Fire Alarm",fireAlarm()),tab("Derating",derating()));
    root.setTop(head);root.setCenter(tabs);return root;
  }

  private static Node grounding(){
    TextField d=tf("16"),len=tf("3"),pl=tf("0.6"),pw=tf("0.6"),ring=tf("6"),req=tf("21.2"),sel=tf("21.2"),count=tf("1"),sec=tf("180"),fac=tf("0.125");
    return sheet("Grounding & bonding",grid("Rod diameter (mm)",d,"Rod length (m)",len,"Plate length (m)",pl,"Plate width (m)",pw,"Electrode ring length (m)",ring,"Required minimum from licensed CEC table (mm²)",req,"Selected conductor area (mm²)",sel,"Parallel conductors",count,"Transformer secondary current (A)",sec,"Bonding factor (study input)",fac),()->{
      double dm=n(d)/1000,L=n(len),plate=n(pl)*n(pw),rod=Math.PI*dm*L,total=n(sel)*Math.max(1,Math.floor(n(count))),need=n(req),study=n(sec)*n(fac);
      return "ELECTRODE GEOMETRY\nRod surface area = π × d × L = "+f(rod)+" m²\nPlate one-face area = "+f(plate)+" m²\nPlate both-face area = "+f(2*plate)+" m²\nRing length entered = "+f(n(ring))+" m\n\nTABLE-SIZE VERIFICATION\nRequired minimum entered = "+f(need)+" mm²\nSelected total metal area = "+f(total)+" mm² → "+pass(total,need)+"\n\nTransformer-secondary study value = "+f(study)+" A equivalent\n\nFinal conductor size must come from the applicable licensed CEC Section 10 table/rule; the app verifies the arithmetic without reproducing the table.";
    });
  }

  private static Node firePump(){
    TextField flc=tf("124"),cf=tf("1.25"),lra=tf("720"),pf=tf("1.00"),ca=tf("160"),oc=tf("720");
    return sheet("Fire-pump feeder worksheet",grid("Motor FLC (A)",flc,"Conductor factor",cf,"Locked-rotor current (A)",lra,"Protection / instantaneous factor",pf,"Selected conductor ampacity (A)",ca,"Selected protection setting (A)",oc),()->{
      double cond=n(flc)*n(cf),prot=n(lra)*n(pf);
      return "CONDUCTOR STUDY REQUIREMENT = "+f(cond)+" A\nSelected conductor = "+f(n(ca))+" A → "+pass(n(ca),cond)+"\n\nProtection / instantaneous study value = "+f(prot)+" A\nSelected setting = "+f(n(oc))+" A\n\nSection 32 fire-pump protection is special. Keep the factor tied to the applicable CEC rule or supplied exam data rather than ordinary motor rules.";
    });
  }

  private static Node neutral(){
    ComboBox<String> sys=new ComboBox<>();sys.getItems().addAll("120/240 split-phase","3-phase 4-wire");sys.setValue("3-phase 4-wire");
    TextField a=tf("80"),b=tf("55"),c=tf("30"),df=tf("1.00"),sel=tf("60");
    return sheet("Neutral sizing",grid("System",sys,"Line A / L1 current (A)",a,"Line B / L2 current (A)",b,"Line C current (A)",c,"Neutral demand factor",df,"Selected neutral ampacity (A)",sel),()->{
      double av=n(a),bv=n(b),cv=n(c);double raw=sys.getValue().startsWith("120/240")?Math.abs(av-bv):Math.sqrt(av*av+bv*bv+cv*cv-av*bv-bv*cv-cv*av);double need=raw*n(df);
      return "Raw neutral current = "+f(raw)+" A\nDemand-adjusted neutral = "+f(need)+" A\nSelected neutral ampacity = "+f(n(sel))+" A → "+pass(n(sel),need)+"\n\n3φ formula: √(A²+B²+C²−AB−BC−CA). Apply only the neutral demand reduction permitted by the applicable CEC rule; nonlinear/harmonic loads can require different treatment.";
    });
  }

  private static Node services(){
    TextField[] loads={tf("100000"),tf("0"),tf("0"),tf("0"),tf("0"),tf("0")};TextField[] demands={tf("100"),tf("0"),tf("0"),tf("0"),tf("0"),tf("0")};
    GridPane g=new GridPane();g.setHgap(12);g.setVgap(8);for(int i=0;i<6;i++){g.add(new Label("Component "+(i+1)+" load (VA)"),0,i);g.add(loads[i],1,i);g.add(new Label("Demand (%)"),2,i);g.add(demands[i],3,i);}TextField v=tf("600"),sel=tf("400");ComboBox<String> ph=new ComboBox<>();ph.getItems().addAll("3","1");ph.setValue("3");ComboBox<String> occ=new ComboBox<>();occ.getItems().addAll("Apartment / multi-suite","Hotel / motel","School","Hospital","Industrial / other");occ.setValue("Apartment / multi-suite");int r=6;g.add(new Label("Occupancy"),0,r);g.add(occ,1,r);g.add(new Label("System voltage (V)"),2,r);g.add(v,3,r);g.add(new Label("Phases"),0,r+1);g.add(ph,1,r+1);g.add(new Label("Selected service ampacity (A)"),2,r+1);g.add(sel,3,r+1);
    return sheet("Advanced service demand",g,()->{double total=0;StringBuilder sb=new StringBuilder(occ.getValue().toUpperCase()+"\n\n");for(int i=0;i<6;i++){double x=n(loads[i])*n(demands[i])/100;total+=x;sb.append("Component ").append(i+1).append(": ").append(f(x)).append(" VA\n");}double amps="3".equals(ph.getValue())?total/(Math.sqrt(3)*n(v)):total/n(v);sb.append("\nTotal demand = ").append(f(total)).append(" VA\nService current = ").append(f(amps)).append(" A\nSelected ampacity = ").append(f(n(sel))).append(" A → ").append(pass(n(sel),amps)).append("\n\nPopulate the demand percentages from the applicable Section 8 rule or exam data; the app intentionally does not reproduce demand tables.");return sb.toString();});
  }

  private static Node motor(){
    TextField largest=tf("65"),others=tf("92"),non=tf("20"),cf=tf("1.25"),boc=tf("175"),start=tf("0"),sel=tf("200"),oc=tf("300");
    return sheet("Advanced motor feeder",grid("Largest motor FLC (A)",largest,"Other motor FLC total (A)",others,"Other non-motor load (A)",non,"Largest-motor conductor factor",cf,"Largest branch OCPD (A)",boc,"Simultaneous-start extra current (A)",start,"Selected feeder ampacity (A)",sel,"Selected feeder OCPD (A)",oc),()->{double cond=n(largest)*n(cf)+n(others)+n(non)+n(start),feed=n(boc)+n(others)+n(non)+n(start);return "FEEDER CONDUCTOR = largest×factor + other motors + other loads + start extra\n= "+f(cond)+" A\nSelected ampacity = "+f(n(sel))+" A → "+pass(n(sel),cond)+"\n\nFEEDER OCPD ARITHMETIC = largest branch OCPD + remaining currents\n= "+f(feed)+" A\nSelected feeder OCPD = "+f(n(oc))+" A\n\nVerify the applicable Rule 28-204 method and permitted branch-device allowances.";});
  }

  private static Node transformer(){
    TextField kva=tf("75"),v=tf("208"),z=tf("3"),pf=tf("1.25"),oc=tf("300"),unit=tf("75"),air=tf("10000");ComboBox<String> ph=new ComboBox<>();ph.getItems().addAll("3","1");ph.setValue("3");
    return sheet("Transformer fault & protection",grid("Transformer kVA",kva,"Secondary voltage (V)",v,"Phases",ph,"Impedance Z (%)",z,"Protection factor",pf,"Selected OCPD / fuse (A)",oc,"Single-phase unit kVA for 3-unit bank",unit,"Selected interrupting rating (A)",air),()->{double fla=n(kva)*1000/("3".equals(ph.getValue())?Math.sqrt(3)*n(v):n(v)),isc=fla*100/n(z),prot=fla*n(pf),bank=n(unit)*3;return "Secondary FLA = "+f(fla)+" A\nApprox. terminal fault current = FLA×100/Z% = "+f(isc)+" A ("+f(isc/1000)+" kA)\nSelected AIR = "+f(n(air))+" A → "+pass(n(air),isc)+"\n\nProtection study value = "+f(prot)+" A\nSelected OCPD/fuse = "+f(n(oc))+" A\n\n3-unit bank total = "+f(bank)+" kVA\n\nAvailable fault current is approximate and assumes an infinite primary source. Use Section 26 for actual transformer protection limits.";});
  }

  private static Node hvac(){
    TextField watts=tf("18000"),v=tf("208"),cf=tf("1.25"),sel=tf("70"),oc=tf("70"),rla=tf("24"),mca=tf("30"),mocp=tf("50"),rsel=tf("35"),roc=tf("50");ComboBox<String> ph=new ComboBox<>();ph.getItems().addAll("3","1");ph.setValue("3");
    return sheet("Heating / HVAC",grid("Heater power (W)",watts,"Heater voltage (V)",v,"Phases",ph,"Continuous-load factor",cf,"Selected heater conductor ampacity (A)",sel,"Selected heater OCPD (A)",oc,"Refrigeration RLA (A)",rla,"Nameplate MCA (A)",mca,"Nameplate MOCP (A)",mocp,"Selected refrigeration conductor (A)",rsel,"Selected refrigeration OCPD (A)",roc),()->{double i=n(watts)/("3".equals(ph.getValue())?Math.sqrt(3)*n(v):n(v)),need=i*n(cf);return "HEATING\nCurrent = "+f(i)+" A\nFactored load = "+f(need)+" A\nSelected conductor = "+f(n(sel))+" A → "+pass(n(sel),need)+"\nSelected OCPD = "+f(n(oc))+" A\n\nREFRIGERATION NAMEPLATE\nSelected conductor "+f(n(rsel))+" A vs MCA "+f(n(mca))+" A → "+pass(n(rsel),n(mca))+"\nSelected OCPD "+f(n(roc))+" A vs MOCP "+f(n(mocp))+" A → "+(n(roc)<=n(mocp)+1e-9?"PASS":"FAIL")+"\n\nVerify Section 62, Section 28 and Rule 14-104 as applicable.";});
  }

  private static Node fireAlarm(){
    TextField v=tf("24"),r=tf("3900"),si=tf("0.18"),sh=tf("24"),ai=tf("1.2"),am=tf("30"),bf=tf("1.20"),nac=tf("2"),dev=tf("0.075"),vdI=tf("1.2"),vdL=tf("100"),vdK=tf("8"),vdPct=tf("10");
    return sheet("Fire-alarm power",grid("Panel voltage (V)",v,"EOL resistance (Ω)",r,"Standby current (A)",si,"Standby hours",sh,"Alarm current (A)",ai,"Alarm minutes",am,"Battery margin factor",bf,"NAC available current (A)",nac,"Current per device (A)",dev,"VD load current (A)",vdI,"VD one-way length (m)",vdL,"Conductor K / resistance (Ω/km)",vdK,"Allowed VD (%)",vdPct),()->{double eol=n(v)/n(r),ah=(n(si)*n(sh)+n(ai)*n(am)/60)*n(bf);int devices=(int)Math.floor(n(nac)/n(dev));double drop=2*n(vdK)*n(vdI)*n(vdL)/1000,pct=drop/n(v)*100,allow=n(v)*n(vdPct)/100,maxL=allow*1000/(2*n(vdK)*n(vdI));return "EOL current = "+f(eol)+" A ("+f(eol*1000)+" mA)\nBattery requirement = "+f(ah)+" Ah\nNAC device capacity = "+devices+" devices\n\nDC VOLTAGE DROP\nVD = "+f(drop)+" V ("+f(pct)+"%)\nAllowed = "+f(allow)+" V\nMaximum one-way length = "+f(maxL)+" m\nResult = "+(drop<=allow+1e-9?"PASS":"EXCEEDS LIMIT")+"\n\nConfirm required battery durations/margins from the applicable fire-alarm standard and project specification.";});
  }

  private static Node derating(){
    TextField base=tf("100"),amb=tf("0.82"),grp=tf("0.80"),term=tf("85"),load=tf("60"),cf=tf("1.25"),oc=tf("70");
    return sheet("Conductor derating & termination limits",grid("Base table ampacity (A)",base,"Ambient correction factor",amb,"Grouping factor",grp,"Terminal-temperature ampacity cap (A)",term,"Calculated load (A)",load,"Continuous-load factor",cf,"Selected OCPD (A)",oc),()->{double corrected=n(base)*n(amb)*n(grp),allow=Math.min(corrected,n(term)),need=n(load)*n(cf);return "Corrected insulation ampacity = "+f(corrected)+" A\nTerminal-temperature cap = "+f(n(term))+" A\nAllowable ampacity = "+f(allow)+" A\n\nFactored load = "+f(need)+" A\nConductor result = "+pass(allow,need)+"\nSelected OCPD = "+f(n(oc))+" A\n\nEnter the 60/75/90°C ampacity and correction values from the licensed CEC tables. Rule 14-104 / equipment terminal limitations can control the final result.";});
  }

  private static Node sheet(String title,GridPane fields,Supplier<String> calculation){TextArea out=new TextArea();out.setEditable(false);out.setWrapText(true);out.setStyle("-fx-font-family:Consolas;-fx-font-size:13px;");Button calc=new Button("Calculate");calc.setOnAction(e->{try{out.setText(calculation.get());}catch(Exception ex){out.setText("Check inputs: "+ex.getMessage());}});VBox left=new VBox(12,card(title,fields),calc,info("Code-use note","This worksheet shows the arithmetic and assumptions. Where a CEC table value is required, enter it from your licensed 2024 Code rather than relying on an embedded copy."));left.setPadding(new Insets(16));SplitPane sp=new SplitPane(scroll(left),out);sp.setDividerPositions(.53);return sp;}
  private static VBox card(String title,GridPane g){Label h=new Label(title);h.setStyle("-fx-font-size:16px;-fx-font-weight:bold;");VBox v=new VBox(8,h,g);v.setPadding(new Insets(14));v.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-background-radius:10;-fx-border-radius:10;");return v;}
  private static VBox info(String t,String x){Label h=new Label(t);h.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#1d4ed8;");Label n=new Label(x);n.setWrapText(true);VBox v=new VBox(7,h,n);v.setPadding(new Insets(14));v.setStyle("-fx-background-color:#eff6ff;-fx-border-color:#bfdbfe;-fx-background-radius:10;-fx-border-radius:10;");return v;}
  private static GridPane grid(Object... p){GridPane g=new GridPane();g.setHgap(12);g.setVgap(8);for(int i=0;i<p.length;i+=2){g.add(new Label(String.valueOf(p[i])),0,i/2);g.add((Node)p[i+1],1,i/2);}return g;}
  private static ScrollPane scroll(Node n){ScrollPane s=new ScrollPane(n);s.setFitToWidth(true);return s;}
  private static Tab tab(String t,Node n){Tab x=new Tab(t,n);x.setClosable(false);return x;}
  private static TextField tf(String s){TextField t=new TextField(s);t.setMaxWidth(260);return t;}
  private static double n(TextField f){return Double.parseDouble(f.getText().trim());}
  private static String f(double d){return String.format("%.2f",d);}
  private static String pass(double selected,double required){return selected+1e-9>=required?"PASS":"FAIL";}
}

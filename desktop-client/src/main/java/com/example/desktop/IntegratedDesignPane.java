package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Integrated design workflow that ties conductor sizing, derating, voltage drop,
 * bonding, raceway sizing, panel schedules, transformers, motors, estimating and
 * project reporting into one local-first JavaFX surface.
 */
public final class IntegratedDesignPane extends BorderPane {
    private final ObjectMapper json;
    private final LocalWorkspaceStore store;
    private final String username;
    private final TabPane tabs = new TabPane();
    private final TextArea journal = new TextArea();
    private final ListView<String> recent = new ListView<>();
    private final TextField projectName = field("Untitled project");
    private final TextField projectClient = field("");
    private final TextField projectSite = field("");
    private final TextArea projectNotes = new TextArea();

    private static final String INPUT = "-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 9;";
    private static final String PRIMARY = "-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-padding:9 13;";
    private static final String SECONDARY = "-fx-background-color:#f8fafc;-fx-text-fill:#0f4c81;-fx-border-color:#c7d9e8;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 11;-fx-font-weight:bold;";
    private static final String CARD = "-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#dfe7ef;-fx-border-radius:14;-fx-padding:16;";

    private record Wire(double a60, double a75, double a90, double rOhmKm, double fillArea) {}
    private static final LinkedHashMap<String,Wire> CU = new LinkedHashMap<>();
    private static final LinkedHashMap<String,Wire> AL = new LinkedHashMap<>();
    private static final LinkedHashMap<Integer,String> BOND_CU = new LinkedHashMap<>();
    private static final int[] STANDARD_OCPD = {15,20,25,30,35,40,45,50,60,70,80,90,100,110,125,150,175,200,225,250,300,350,400,450,500,600,700,800,1000,1200,1600,2000,2500,3000,4000,5000,6000};
    private static final LinkedHashMap<String,Double> EMT = new LinkedHashMap<>();
    private static final LinkedHashMap<String,Double> PVC = new LinkedHashMap<>();

    static {
        add(CU,"14 AWG",15,20,25,8.28,8.87); add(CU,"12 AWG",20,25,30,5.21,11.60); add(CU,"10 AWG",30,35,40,3.28,15.70);
        add(CU,"8 AWG",40,50,55,2.06,28.20); add(CU,"6 AWG",55,65,75,1.30,38.00); add(CU,"4 AWG",70,85,95,0.815,52.50);
        add(CU,"3 AWG",85,100,115,0.646,62.00); add(CU,"2 AWG",95,115,130,0.513,73.90); add(CU,"1 AWG",110,130,145,0.406,99.10);
        add(CU,"1/0 AWG",125,150,170,0.322,119); add(CU,"2/0 AWG",145,175,195,0.255,142); add(CU,"3/0 AWG",165,200,225,0.202,171);
        add(CU,"4/0 AWG",195,230,260,0.160,207); add(CU,"250 kcmil",215,255,290,0.135,252); add(CU,"300 kcmil",240,285,320,0.113,293);
        add(CU,"350 kcmil",260,310,350,0.0967,332); add(CU,"400 kcmil",280,335,380,0.0847,373); add(CU,"500 kcmil",320,380,430,0.0676,460);
        add(CU,"600 kcmil",350,420,475,0.0564,540); add(CU,"750 kcmil",400,475,535,0.0451,650); add(CU,"1000 kcmil",455,545,615,0.0338,840);

        add(AL,"12 AWG",15,20,25,8.54,13); add(AL,"10 AWG",25,30,35,5.37,18); add(AL,"8 AWG",35,40,45,3.38,31); add(AL,"6 AWG",40,50,55,2.13,42);
        add(AL,"4 AWG",55,65,75,1.34,58); add(AL,"3 AWG",65,75,85,1.06,69); add(AL,"2 AWG",75,90,100,0.84,82); add(AL,"1 AWG",85,100,115,0.665,110);
        add(AL,"1/0 AWG",100,120,135,0.528,132); add(AL,"2/0 AWG",115,135,150,0.418,158); add(AL,"3/0 AWG",130,155,175,0.332,190); add(AL,"4/0 AWG",150,180,205,0.263,230);
        add(AL,"250 kcmil",170,205,230,0.222,280); add(AL,"300 kcmil",195,230,260,0.186,325); add(AL,"350 kcmil",210,250,280,0.159,370); add(AL,"400 kcmil",225,270,305,0.139,415);
        add(AL,"500 kcmil",260,310,350,0.111,510); add(AL,"600 kcmil",285,340,385,0.0927,600); add(AL,"750 kcmil",320,385,435,0.0742,720); add(AL,"1000 kcmil",375,445,500,0.0556,920);

        bond(20,"14 AWG"); bond(30,"12 AWG"); bond(60,"10 AWG"); bond(100,"8 AWG"); bond(200,"6 AWG"); bond(300,"4 AWG"); bond(400,"3 AWG");
        bond(500,"2 AWG"); bond(600,"1 AWG"); bond(800,"1/0 AWG"); bond(1000,"2/0 AWG"); bond(1200,"3/0 AWG"); bond(1600,"4/0 AWG");
        bond(2000,"250 kcmil"); bond(2500,"350 kcmil"); bond(3000,"400 kcmil"); bond(4000,"500 kcmil"); bond(5000,"700 kcmil"); bond(6000,"800 kcmil");

        race(EMT,"16 (1/2 in)",186); race(EMT,"21 (3/4 in)",330); race(EMT,"27 (1 in)",539); race(EMT,"35 (1-1/4 in)",940); race(EMT,"41 (1-1/2 in)",1288); race(EMT,"53 (2 in)",2132); race(EMT,"63 (2-1/2 in)",3783); race(EMT,"78 (3 in)",5701); race(EMT,"91 (3-1/2 in)",7451); race(EMT,"103 (4 in)",9503);
        race(PVC,"16 (1/2 in)",167); race(PVC,"21 (3/4 in)",307); race(PVC,"27 (1 in)",507); race(PVC,"35 (1-1/4 in)",792); race(PVC,"41 (1-1/2 in)",1140); race(PVC,"53 (2 in)",2027); race(PVC,"63 (2-1/2 in)",2951); race(PVC,"78 (3 in)",4560); race(PVC,"91 (3-1/2 in)",6138); race(PVC,"103 (4 in)",7870); race(PVC,"129 (5 in)",12439); race(PVC,"155 (6 in)",17613); race(PVC,"200 (8 in)",31225);
    }

    private static void add(Map<String,Wire> map,String size,double a60,double a75,double a90,double r,double area){map.put(size,new Wire(a60,a75,a90,r,area));}
    private static void bond(int ocpd,String size){BOND_CU.put(ocpd,size);}
    private static void race(Map<String,Double> map,String size,double area){map.put(size,area);}

    public IntegratedDesignPane(ObjectMapper json, String username) {
        this.json = json;
        this.username = username == null ? "user" : username;
        this.store = new LocalWorkspaceStore(json, this.username);
        VBox head = new VBox(7);
        Label title = new Label("Integrated Electrical Design Workbench");
        title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label sub = new Label("One workflow for conductor selection, derating, voltage drop, OCPD/bonding, raceway sizing, panels, transformers, motors, estimates and calculation reports.");
        sub.setWrapText(true); sub.setStyle("-fx-text-fill:#64748b;");
        ComboBox<String> jump = new ComboBox<>();
        jump.getItems().addAll("Circuit Designer","Panel Schedule","Transformer → Panel","Motor Circuit","Estimate","Project / Report");
        jump.setPromptText("Jump to tool…"); jump.setPrefWidth(210);
        CheckBox dark = new CheckBox("Dark workspace");
        Button favorite = secondary("★ Favorite current tool");
        HBox tools = new HBox(9,new Label("Find tool:"),jump,dark,favorite); tools.setAlignment(Pos.CENTER_LEFT);
        head.getChildren().addAll(title,sub,tools); head.setPadding(new Insets(0,0,10,0));
        setTop(head);

        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
            new Tab("Circuit Designer", scroll(circuitDesigner())),
            new Tab("Panel Schedule", scroll(panelSchedule())),
            new Tab("Transformer → Panel", scroll(transformerDesigner())),
            new Tab("Motor Circuit", scroll(motorDesigner())),
            new Tab("Estimate", scroll(estimatePane())),
            new Tab("Project / Report", scroll(projectPane()))
        );
        setCenter(tabs);
        jump.setOnAction(e->{String v=jump.getValue();if(v!=null)for(Tab t:tabs.getTabs())if(t.getText().equals(v)){tabs.getSelectionModel().select(t);break;}});
        dark.setOnAction(e->{boolean on=dark.isSelected();setStyle(on?"-fx-background-color:#0b1220;":"-fx-background-color:transparent;");tabs.setStyle(on?"-fx-base:#111827;-fx-background:#0b1220;-fx-control-inner-background:#111827;-fx-text-background-color:#e5e7eb;":"");});
        favorite.setOnAction(e->{Tab t=tabs.getSelectionModel().getSelectedItem();if(t==null)return;ObjectNode n=json.createObjectNode();n.put("tool",t.getText());n.put("saved",LocalDateTime.now().toString());store.append("calculatorFavorites",n);favorite.setText("★ Saved: "+t.getText());});
    }

    private Node circuitDesigner() {
        VBox page = page("Design This Circuit", "Enter the load and installation once. The worksheet selects a conductor, applies CEC ambient/grouping correction, checks termination limits and voltage drop, suggests OCPD/bonding, and finds a minimum listed raceway using the visible preset dataset.");
        ChoiceBox<String> preset=choice("600 V 3φ industrial","600 V 3φ industrial","208 V 3φ commercial","347 V lighting","120/240 V 1φ dwelling","Custom");
        ChoiceBox<String> loadMode=choice("Current (A)","Current (A)","kVA","kW");
        TextField load=field("40"), voltage=field("600"), pf=field("0.90"), length=field("60"), vdLimit=field("3"), insulatedCount=field("4"), ccc=field("3");
        ChoiceBox<String> phase=choice("3φ","1φ","3φ");
        ChoiceBox<String> lengthUnit=choice("m","m","ft");
        CheckBox continuous=new CheckBox("Treat load as continuous / design at 125%");
        ChoiceBox<String> material=choice("Copper","Copper","Aluminum");
        ChoiceBox<Integer> insulation=choice(90,60,75,90), termination=choice(75,60,75,90), ambient=choice(30,30,35,40,45,50,55,60,65,70,75,80);
        ChoiceBox<String> raceway=choice("EMT","EMT","Rigid PVC");
        CheckBox includeBond=new CheckBox("Include bonding conductor in raceway fill"); includeBond.setSelected(true);
        TextArea out=output(); Label status=status();

        Runnable applyPreset=()->{
            switch(preset.getValue()){
                case "600 V 3φ industrial"->{voltage.setText("600");phase.setValue("3φ");material.setValue("Copper");insulation.setValue(90);termination.setValue(75);ambient.setValue(30);ccc.setText("3");insulatedCount.setText("4");}
                case "208 V 3φ commercial"->{voltage.setText("208");phase.setValue("3φ");material.setValue("Copper");insulation.setValue(90);termination.setValue(75);ambient.setValue(30);ccc.setText("4");insulatedCount.setText("4");}
                case "347 V lighting"->{voltage.setText("347");phase.setValue("1φ");material.setValue("Copper");insulation.setValue(90);termination.setValue(75);ambient.setValue(30);ccc.setText("2");insulatedCount.setText("2");}
                case "120/240 V 1φ dwelling"->{voltage.setText("240");phase.setValue("1φ");material.setValue("Copper");insulation.setValue(90);termination.setValue(75);ambient.setValue(30);ccc.setText("3");insulatedCount.setText("3");}
                default -> {}
            }
        }; preset.setOnAction(e->applyPreset.run());

        Button calc=primary("Design circuit"); Button reset=secondary("Reset"); Button save=secondary("Save to project");
        final String[] latest={""};
        calc.setOnAction(e->{
            try {
                double v=positive(voltage,"Voltage"), x=positive(load,"Load"), powerFactor=positive(pf,"Power factor");
                double amps=switch(loadMode.getValue()){case "kVA"->x*1000/(phase.getValue().equals("3φ")?Math.sqrt(3)*v:v);case "kW"->x*1000/(phase.getValue().equals("3φ")?Math.sqrt(3)*v*powerFactor:v*powerFactor);default->x;};
                double design=continuous.isSelected()?amps*1.25:amps;
                int nccc=positiveInt(ccc,"Current-carrying conductor count"), nIns=positiveInt(insulatedCount,"Insulated conductor count");
                double tf=ambientFactor(ambient.getValue(),insulation.getValue()), gf=groupFactor(nccc), metres=positive(length,"Length")*(lengthUnit.getValue().equals("ft")?0.3048:1.0), limit=positive(vdLimit,"Voltage-drop limit");
                Map<String,Wire> table=material.getValue().equals("Copper")?CU:AL;
                List<Option> passing=new ArrayList<>();
                for(Map.Entry<String,Wire> en:table.entrySet()){
                    Wire w=en.getValue(); double base=amp(w,insulation.getValue()), adjusted=base*tf*gf;
                    int termRating=Math.min(termination.getValue(),insulation.getValue()); double termAmp=amp(w,termRating); double allowable=Math.min(adjusted,termAmp);
                    double vd=(phase.getValue().equals("3φ")?Math.sqrt(3):2)*metres*amps*w.rOhmKm()/1000.0; double pct=vd/v*100.0;
                    if(allowable+1e-9>=design && pct<=limit+1e-9) passing.add(new Option(en.getKey(),w,allowable,vd,pct));
                }
                if(passing.isEmpty()) throw new IllegalArgumentException("No conductor in the built-in range passes both adjusted ampacity and voltage-drop limits. Increase the dataset/raceway arrangement or revise the design.");
                Option pick=passing.get(0); int ocpd=nextOcpd(design); int smallCap=smallConductorCap(material.getValue(),pick.size()); if(smallCap>0) ocpd=Math.min(ocpd,smallCap);
                if(ocpd<design) ocpd=nextOcpd(design);
                String bond=bondSize(ocpd); double occupied=pick.w().fillArea()*nIns;
                if(includeBond.isSelected()) occupied+=bondArea(bond);
                int physicalCount=nIns+(includeBond.isSelected()?1:0); double fillPct=physicalCount==1?53:physicalCount==2?31:40;
                String minRaceway=minRaceway(raceway.getValue(),occupied,fillPct);
                StringBuilder compare=new StringBuilder(); int shown=0;
                for(Option o:passing){if(shown++>=3)break;compare.append(String.format(Locale.CANADA,"  %-10s allowable %.1f A | VD %.2f V (%.2f%%)%n",o.size(),o.allowable(),o.vd(),o.vdPct()));}
                String text=String.format(Locale.CANADA,
                    "INTEGRATED CIRCUIT DESIGN%n%nLoad current: %.2f A%nDesign current: %.2f A%s%nSystem: %.0f V %s%nConductor: %s %s%nInsulation / termination: %d°C / %d°C%nAmbient factor: %.2f%nGrouping factor (%d conductors): %.2f%nFinal allowable ampacity: %.2f A%nVoltage drop: %.2f V (%.2f%%)%nSuggested standard OCPD screening size: %d A%nMinimum Cu bonding conductor from Table 16 screening: %s%nRaceway: %s → minimum listed size %s%nRaceway occupied area used: %.2f mm² at %.0f%% fill limit%n%nNEXT PASSING OPTIONS%n%s%nTRACEABILITY / CHECKS%n• Rule 4-004; Tables %s, 5A and 5C — conductor ampacity and corrections.%n• Rule 4-006 — equipment/termination temperature limitations.%n• Rule 10-616 / Table 16 — field-installed bonding conductor sizing.%n• Section 12 / Table 9-series workflow — raceway dimensions and fill.%n• Rule 14-104 — overcurrent protection and small-conductor limitations.%n• Appendix D / Table D3 workflow — voltage-drop verification.%n%nAssumptions: table presets are CEC 2024-oriented screening data. Verify the adopted Ontario amendments, actual conductor construction/dimensions, neutral treatment, equipment markings and installation-specific exceptions before construction.",
                    amps,design,continuous.isSelected()?" (125% continuous-load design)":"",v,phase.getValue(),pick.size(),material.getValue(),insulation.getValue(),termination.getValue(),tf,nccc,gf,pick.allowable(),pick.vd(),pick.vdPct(),ocpd,bond,raceway.getValue(),minRaceway,occupied,fillPct,compare,material.getValue().equals("Copper")?"2":"4");
                latest[0]=text; out.setText(text); status.setText("PASS — a conductor/raceway combination meets the entered ampacity and voltage-drop limits.");
                addJournal("Circuit Designer",text); record("recentCalculations","Circuit Designer",text);
            } catch(Exception ex){status.setText("Check inputs");out.setText(message(ex));}
        });
        save.setOnAction(e->{if(latest[0].isBlank())return;record("designReports","Circuit Design",latest[0]);status.setText("Saved to local design records.");});
        reset.setOnAction(e->{preset.setValue("600 V 3φ industrial");loadMode.setValue("Current (A)");load.setText("40");pf.setText("0.90");length.setText("60");lengthUnit.setValue("m");vdLimit.setText("3");continuous.setSelected(false);raceway.setValue("EMT");includeBond.setSelected(true);applyPreset.run();out.setText("Enter a circuit and select Design circuit.");status.setText("");});

        VBox inputs=card("1. Load and installation","Presets are editable. Current-carrying conductor count is separate from the physical conductor count so neutrals/bonds are not blindly treated the same.");
        inputs.getChildren().add(grid(row("Preset",preset),row("Load input",loadMode),row("Load value",load),row("System voltage (V)",voltage),row("Phase",phase),row("Power factor",pf),row("Conductor material",material),row("Insulation rating",insulation),row("Equipment termination rating",termination),row("Ambient °C",ambient),row("Current-carrying conductors",ccc),row("Insulated conductors in raceway",insulatedCount),row("One-way length",new HBox(7,length,lengthUnit)),row("Maximum VD (%)",vdLimit),row("Raceway",raceway),continuous,includeBond));
        VBox result=card("2. Coordinated result","The result shows the selected conductor plus two larger passing options so you can compare practical alternatives."); result.getChildren().addAll(new HBox(8,calc,save,reset),status,out);
        page.getChildren().addAll(inputs,result); return page;
    }

    private Node panelSchedule() {
        VBox page=page("Panel Schedule & Phase Balancing","Build a 120/208 V, 347/600 V or 120/240 V schedule, total VA by phase, calculate phase current and automatically balance single-pole loads.");
        ChoiceBox<String> system=choice("120/208 V 3φ 4W","120/208 V 3φ 4W","347/600 V 3φ 4W","120/240 V 1φ 3W");
        VBox rowBox=new VBox(6); List<PanelRow> rows=new ArrayList<>();
        Runnable add=()->{PanelRow r=new PanelRow(rows,rowBox);rows.add(r);rowBox.getChildren().add(r.node);};
        for(int i=0;i<8;i++)add.run();
        Button addBtn=secondary("+ Add circuit");addBtn.setOnAction(e->add.run()); Button clear=secondary("Reset schedule");clear.setOnAction(e->{rows.clear();rowBox.getChildren().clear();for(int i=0;i<8;i++)add.run();});
        Button balance=secondary("Auto-balance 1-pole loads");
        balance.setOnAction(e->{double[] ph={0,0,0};List<PanelRow> sorted=new ArrayList<>(rows);sorted.sort((a,b)->Double.compare(num(b.va),num(a.va)));for(PanelRow r:sorted){if(!r.poles.getValue().equals("1"))continue;int idx=ph[0]<=ph[1]&&ph[0]<=ph[2]?0:ph[1]<=ph[2]?1:2;r.phase.setValue(idx==0?"A":idx==1?"B":"C");ph[idx]+=num(r.va);}});
        TextArea out=output();Label status=status();Button calc=primary("Calculate panel");Button save=secondary("Save schedule");Button csv=secondary("Export CSV");final String[] latest={""};
        calc.setOnAction(e->{try{double[] p={0,0,0};double total=0;StringBuilder detail=new StringBuilder();for(PanelRow r:rows){double va=num(r.va);if(va<=0)continue;total+=va;String ph=r.phase.getValue();if(ph.equals("ABC")){p[0]+=va/3;p[1]+=va/3;p[2]+=va/3;}else if(ph.length()==2){p[ph.charAt(0)-'A']+=va/2;p[ph.charAt(1)-'A']+=va/2;}else p[ph.charAt(0)-'A']+=va;detail.append(String.format(Locale.CANADA,"  Ckt %-4s %-24s %8.0f VA  %-3s  %sP  %s A%n",r.circuit.getText(),r.desc.getText(),va,ph,r.poles.getValue(),r.breaker.getText()));}double ln=system.getValue().startsWith("347")?347:120;if(system.getValue().startsWith("120/240"))p[2]=0;double a=p[0]/ln,b=p[1]/ln,c=p[2]/ln;double max=Math.max(a,Math.max(b,c)),min=system.getValue().startsWith("120/240")?Math.min(a,b):Math.min(a,Math.min(b,c)),avg=system.getValue().startsWith("120/240")?(a+b)/2:(a+b+c)/3;double imbalance=avg>0?(max-min)/avg*100:0;String text=String.format(Locale.CANADA,"PANEL SCHEDULE SUMMARY%nSystem: %s%nTotal connected load: %.0f VA%nPhase A: %.0f VA / %.2f A%nPhase B: %.0f VA / %.2f A%nPhase C: %.0f VA / %.2f A%nLoad spread: %.1f%%%n%nCIRCUITS%n%s%nChecks:%n• Review demand factors separately from connected load.%n• Confirm multi-pole loads are assigned across the correct phases.%n• Review neutral loading for nonlinear loads rather than assuming cancellation.%n• Verify panel/breaker ratings, continuous loads and applicable CEC Section 8/14 requirements.",system.getValue(),total,p[0],a,p[1],b,p[2],c,imbalance,detail);latest[0]=text;out.setText(text);status.setText(imbalance<=15?"Panel calculated — phase spread is reasonably balanced.":"Panel calculated — consider rebalancing single-pole loads.");addJournal("Panel Schedule",text);record("recentCalculations","Panel Schedule",text);}catch(Exception ex){out.setText(message(ex));status.setText("Check schedule");}});
        save.setOnAction(e->{if(!latest[0].isBlank())record("panelSchedules","Panel Schedule",latest[0]);status.setText("Panel schedule saved locally.");});
        csv.setOnAction(e->{try{FileChooser fc=new FileChooser();fc.setTitle("Export panel schedule CSV");fc.setInitialFileName(safe(projectName.getText())+"-panel.csv");File f=fc.showSaveDialog(getScene().getWindow());if(f==null)return;StringBuilder b=new StringBuilder("Circuit,Description,VA,Phase,Poles,BreakerA\n");for(PanelRow r:rows)b.append(csv(r.circuit.getText())).append(',').append(csv(r.desc.getText())).append(',').append(csv(r.va.getText())).append(',').append(csv(r.phase.getValue())).append(',').append(csv(r.poles.getValue())).append(',').append(csv(r.breaker.getText())).append('\n');Files.writeString(f.toPath(),b.toString(),StandardCharsets.UTF_8);status.setText("CSV exported: "+f.getName());}catch(Exception ex){status.setText(message(ex));}});
        VBox controls=card("Schedule setup","Use A/B/C for single-pole, AB/BC/CA for two-pole and ABC for three-pole loads.");controls.getChildren().addAll(row("System",system),rowBox,new HBox(8,addBtn,balance,clear),new HBox(8,calc,save,csv),status,out);page.getChildren().add(controls);return page;
    }

    private Node transformerDesigner() {
        VBox page=page("Transformer → Panel Workflow","Coordinate transformer current, impedance/fault current, primary and secondary OCPD targets, panel loading and secondary bonding in one worksheet.");
        TextField kva=field("75"),pv=field("600"),sv=field("208"),z=field("3"),pPct=field("125"),sPct=field("125"),panelKva=field("50");ChoiceBox<String> phase=choice("3φ","1φ","3φ");TextArea out=output();Label status=status();Button calc=primary("Calculate transformer system");Button save=secondary("Save design");final String[] latest={""};
        calc.setOnAction(e->{try{double k=positive(kva,"kVA"),p=positive(pv,"Primary voltage"),s=positive(sv,"Secondary voltage"),zz=positive(z,"Impedance"),loadKva=positive(panelKva,"Panel load");double factor=phase.getValue().equals("3φ")?Math.sqrt(3):1;double ip=k*1000/(factor*p),is=k*1000/(factor*s),fault=is/(zz/100),pTarget=ip*positive(pPct,"Primary OCPD percent")/100,sTarget=is*positive(sPct,"Secondary OCPD percent")/100;int pO=nextOcpd(pTarget),sO=nextOcpd(sTarget);double panelI=loadKva*1000/(factor*s),headroom=(k-loadKva)/k*100;String bond=bondSize(sO);String text=String.format(Locale.CANADA,"TRANSFORMER → PANEL DESIGN%n%nTransformer: %.1f kVA, %s, %.0f V → %.0f V%nPrimary FLA: %.2f A%nSecondary FLA: %.2f A%nImpedance: %.2f%%%nApprox. secondary terminal fault current: %.0f A%n%nPrimary OCPD target (%.0f%%): %.2f A → next standard %d A%nSecondary OCPD target (%.0f%%): %.2f A → next standard %d A%nSecondary bonding screening size (Cu Table 16): %s%n%nPanel load: %.1f kVA = %.2f A%nTransformer kVA headroom: %.1f%%%n%nTRACEABILITY / CHECKS%n• Section 26 transformer installation/OCPD requirements; verify the exact permitted primary/secondary protection arrangement.%n• Rule 10-616 / Table 16 for field-installed bonding conductor screening.%n• Fault current shown is transformer-terminal approximation only; include source and conductor impedance for an equipment SCCR/AIC study.%n• Verify panel main/bus rating, conductor ampacity, demand, grounding/bonding and manufacturer data.",k,phase.getValue(),p,s,ip,is,zz,fault,positive(pPct,"Primary percent"),pTarget,pO,positive(sPct,"Secondary percent"),sTarget,sO,bond,loadKva,panelI,headroom);latest[0]=text;out.setText(text);status.setText(loadKva<=k?"PASS — entered panel load is within transformer kVA.":"WARNING — entered panel load exceeds transformer kVA.");addJournal("Transformer → Panel",text);record("recentCalculations","Transformer → Panel",text);}catch(Exception ex){out.setText(message(ex));status.setText("Check inputs");}});
        save.setOnAction(e->{if(!latest[0].isBlank())record("transformerDesigns","Transformer → Panel",latest[0]);status.setText("Transformer design saved locally.");});
        VBox c=card("Transformer and panel","Protection percentages are deliberately editable because the permitted value depends on transformer/protection details.");c.getChildren().addAll(grid(row("Transformer kVA",kva),row("Phase",phase),row("Primary V",pv),row("Secondary V",sv),row("Impedance (%)",z),row("Primary OCPD multiplier (%)",pPct),row("Secondary OCPD multiplier (%)",sPct),row("Calculated panel load (kVA)",panelKva)),new HBox(8,calc,save),status,out);page.getChildren().add(c);return page;
    }

    private Node motorDesigner() {
        VBox page=page("Motor Circuit Designer","Coordinate motor current, branch conductors, ambient/grouping correction, voltage drop, overload, branch short-circuit protection and disconnect screening.");
        TextField hp=field("20"),voltage=field("600"),eff=field("0.90"),pf=field("0.85"),flc=field(""),condPct=field("125"),fusePct=field("175"),breakerPct=field("250"),olPct=field("115"),discPct=field("115"),length=field("50"),ccc=field("3");ChoiceBox<String> phase=choice("3φ","1φ","3φ"),unit=choice("m","m","ft");ChoiceBox<Integer> insulation=choice(90,75,90),termination=choice(75,60,75,90),ambient=choice(30,30,35,40,45,50,55,60);TextArea out=output();Label status=status();Button calc=primary("Design motor circuit");Button save=secondary("Save motor design");final String[] latest={""};
        calc.setOnAction(e->{try{double h=positive(hp,"Horsepower"),v=positive(voltage,"Voltage"),eta=positive(eff,"Efficiency"),powerFactor=positive(pf,"Power factor"),metres=positive(length,"Length")*(unit.getValue().equals("ft")?0.3048:1);double motorA=flc.getText().isBlank()?h*746/(phase.getValue().equals("3φ")?Math.sqrt(3)*v*eta*powerFactor:v*eta*powerFactor):positive(flc,"Motor FLC");double target=motorA*positive(condPct,"Conductor percent")/100;double tf=ambientFactor(ambient.getValue(),insulation.getValue()),gf=groupFactor(positiveInt(ccc,"CCC"));Option pick=null;for(Map.Entry<String,Wire> en:CU.entrySet()){Wire w=en.getValue();double allowed=Math.min(amp(w,insulation.getValue())*tf*gf,amp(w,Math.min(insulation.getValue(),termination.getValue())));double vd=(phase.getValue().equals("3φ")?Math.sqrt(3):2)*metres*motorA*w.rOhmKm()/1000;double pct=vd/v*100;if(allowed>=target&&pct<=3){pick=new Option(en.getKey(),w,allowed,vd,pct);break;}}if(pick==null)throw new IllegalArgumentException("No copper conductor in the built-in range passes the entered motor conductor target and 3% branch voltage-drop screen.");int fuse=nextOcpd(motorA*positive(fusePct,"Fuse percent")/100),breaker=nextOcpd(motorA*positive(breakerPct,"Breaker percent")/100);double overload=motorA*positive(olPct,"Overload percent")/100,disconnect=motorA*positive(discPct,"Disconnect percent")/100;String text=String.format(Locale.CANADA,"MOTOR CIRCUIT DESIGN%n%nMotor: %.1f hp, %.0f V %s%nCalculated/entered FLC: %.2f A%nConductor design target (%.0f%%): %.2f A%nSelected Cu conductor: %s%nAdjusted allowable ampacity: %.2f A%nVoltage drop at motor FLC: %.2f V (%.2f%%)%nAmbient factor: %.2f | grouping factor: %.2f%n%nFuse screening target (%.0f%%): %d A standard%nBreaker screening target (%.0f%%): %d A standard%nOverload setting target (%.0f%%): %.2f A%nDisconnect current target (%.0f%%): %.2f A%n%nTRACEABILITY / CHECKS%n• CEC Section 28 / Table 44 workflow — use the applicable Code motor current rather than calculated nameplate current where required.%n• Verify Rules for branch conductors, overloads, branch short-circuit/ground-fault protection, disconnects, multi-motor feeders and VFD applications.%n• Tables 5A/5C and Rule 4-006 still apply to conductor ampacity/termination limits.%n• Manufacturer nameplate and listed drive/starter instructions govern where applicable.%n%nProtection percentages are editable screening inputs; the app does not assume one multiplier is correct for every motor/protection type.",h,v,phase.getValue(),motorA,positive(condPct,"Conductor percent"),target,pick.size(),pick.allowable(),pick.vd(),pick.vdPct(),tf,gf,positive(fusePct,"Fuse percent"),fuse,positive(breakerPct,"Breaker percent"),breaker,positive(olPct,"OL percent"),overload,positive(discPct,"Disc percent"),disconnect);latest[0]=text;out.setText(text);status.setText("Motor workflow calculated. Verify the applicable Section 28 multipliers for the selected equipment.");addJournal("Motor Circuit",text);record("recentCalculations","Motor Circuit",text);}catch(Exception ex){out.setText(message(ex));status.setText("Check inputs");}});
        save.setOnAction(e->{if(!latest[0].isBlank())record("motorDesigns","Motor Circuit",latest[0]);status.setText("Motor design saved locally.");});
        VBox c=card("Motor and protection","Leave FLC blank to estimate current from hp/efficiency/PF; enter the CEC Table 44 current when doing a final Code selection.");c.getChildren().addAll(grid(row("Horsepower",hp),row("Voltage",voltage),row("Phase",phase),row("Efficiency",eff),row("Power factor",pf),row("CEC/nameplate FLC override (A)",flc),row("Conductor target (%)",condPct),row("Fuse multiplier (%)",fusePct),row("Breaker multiplier (%)",breakerPct),row("Overload (%)",olPct),row("Disconnect (%)",discPct),row("Insulation °C",insulation),row("Termination °C",termination),row("Ambient °C",ambient),row("Current-carrying conductors",ccc),row("One-way length",new HBox(7,length,unit))),new HBox(8,calc,save),status,out);page.getChildren().add(c);return page;
    }

    private Node estimatePane() {
        VBox page=page("Field Estimate / Material Takeoff","Build material and labour totals, then apply markup and tax. This complements the existing Projects → Material Takeoff list with pricing and labour.");
        VBox rowBox=new VBox(6);List<EstimateRow> rows=new ArrayList<>();Runnable add=()->{EstimateRow r=new EstimateRow(rows,rowBox);rows.add(r);rowBox.getChildren().add(r.node);};for(int i=0;i<5;i++)add.run();rows.get(0).desc.setText("EMT / cable / raceway");rows.get(0).qty.setText("30");rows.get(0).cost.setText("4.50");rows.get(0).hours.setText("1.5");rows.get(1).desc.setText("Devices / boxes / fittings");rows.get(1).qty.setText("10");rows.get(1).cost.setText("8.00");rows.get(1).hours.setText("2");
        TextField labourRate=field("75"),markup=field("20"),tax=field("13");TextArea out=output();Label status=status();Button calc=primary("Calculate estimate"),save=secondary("Save estimate"),addBtn=secondary("+ Add item"),clear=secondary("Reset");addBtn.setOnAction(e->add.run());clear.setOnAction(e->{rows.clear();rowBox.getChildren().clear();for(int i=0;i<5;i++)add.run();});final String[] latest={""};
        calc.setOnAction(e->{try{double mat=0,hours=0;StringBuilder detail=new StringBuilder();for(EstimateRow r:rows){if(r.desc.getText().isBlank())continue;double q=num(r.qty),c=num(r.cost),h=num(r.hours);mat+=q*c;hours+=h;detail.append(String.format(Locale.CANADA,"  %-28s %8.2f × $%8.2f = $%10.2f | labour %.2f h%n",r.desc.getText(),q,c,q*c,h));}double lr=positive(labourRate,"Labour rate"),lab=hours*lr,subtotal=mat+lab,mu=subtotal*positive(markup,"Markup")/100,preTax=subtotal+mu,t=preTax*positive(tax,"Tax")/100,total=preTax+t;String text=String.format(Locale.CANADA,"FIELD ESTIMATE%n%n%s%nMaterial: $%.2f%nLabour: %.2f h × $%.2f = $%.2f%nSubtotal: $%.2f%nMarkup: $%.2f%nTax: $%.2f%nESTIMATED TOTAL: $%.2f%n%nNotes: pricing is user-entered; confirm supplier pricing, labour burden, permits, travel, rentals, contingency and contractual tax treatment.",detail,mat,hours,lr,lab,subtotal,mu,t,total);latest[0]=text;out.setText(text);status.setText("Estimate calculated.");addJournal("Estimate",text);record("recentCalculations","Estimate",text);}catch(Exception ex){out.setText(message(ex));status.setText("Check estimate");}});
        save.setOnAction(e->{if(!latest[0].isBlank())record("estimates","Estimate",latest[0]);status.setText("Estimate saved locally.");});
        VBox c=card("Estimate rows","Quantity × unit material cost plus labour hours. Add as many rows as needed.");c.getChildren().addAll(rowBox,new HBox(8,addBtn,clear),grid(row("Labour rate ($/h)",labourRate),row("Markup (%)",markup),row("Tax (%)",tax)),new HBox(8,calc,save),status,out);page.getChildren().add(c);return page;
    }

    private Node projectPane() {
        VBox page=page("Project / Report","Calculations performed in this integrated workbench are accumulated into a project journal. Save the project locally or export a clean HTML calculation report that can be printed to PDF from a browser.");
        projectNotes.setPrefRowCount(3);projectNotes.setWrapText(true);journal.setEditable(true);journal.setWrapText(false);journal.setPrefRowCount(20);journal.setStyle("-fx-font-family:Consolas;-fx-font-size:12px;");recent.setPrefHeight(150);
        Button saveProject=primary("Save project snapshot"),loadLatest=secondary("Load latest project"),export=secondary("Export HTML report"),clear=secondary("Clear journal");Label status=status();
        saveProject.setOnAction(e->{ObjectNode n=json.createObjectNode();n.put("saved",LocalDateTime.now().toString());n.put("name",projectName.getText());n.put("client",projectClient.getText());n.put("site",projectSite.getText());n.put("notes",projectNotes.getText());n.put("journal",journal.getText());store.append("projects",n);status.setText("Project snapshot saved locally.");});
        loadLatest.setOnAction(e->{try{ArrayNode arr=store.records("projects");if(arr.isEmpty()){status.setText("No saved project records.");return;}JsonNode n=arr.get(arr.size()-1);projectName.setText(n.path("name").asText("Untitled project"));projectClient.setText(n.path("client").asText(""));projectSite.setText(n.path("site").asText(n.path("location").asText("")));projectNotes.setText(n.path("notes").asText(""));journal.setText(n.path("journal").asText(""));status.setText("Loaded latest project snapshot.");}catch(Exception ex){status.setText(message(ex));}});
        export.setOnAction(e->{try{FileChooser fc=new FileChooser();fc.setTitle("Export calculation report");fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML report","*.html"));fc.setInitialFileName(safe(projectName.getText())+"-calculation-report.html");File f=fc.showSaveDialog(getScene().getWindow());if(f==null)return;String html="<!doctype html><html><head><meta charset='utf-8'><title>"+esc(projectName.getText())+"</title><style>body{font-family:Arial,sans-serif;max-width:980px;margin:40px auto;color:#172033}h1{color:#0e7490}pre{white-space:pre-wrap;background:#f6f8fa;padding:18px;border:1px solid #dfe7ef;border-radius:8px}.meta{color:#475569}.warn{font-size:12px;color:#64748b}</style></head><body><h1>Electrical Workbench Calculation Report</h1><p class='meta'><b>Project:</b> "+esc(projectName.getText())+"<br><b>Client:</b> "+esc(projectClient.getText())+"<br><b>Site:</b> "+esc(projectSite.getText())+"<br><b>Generated:</b> "+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))+"</p><h2>Notes</h2><p>"+esc(projectNotes.getText()).replace("\n","<br>")+"</p><h2>Calculation journal</h2><pre>"+esc(journal.getText())+"</pre><p class='warn'>CEC-oriented calculation aid. Final installation must be verified against the Code edition and provincial amendments adopted by the authority having jurisdiction, equipment markings and manufacturer instructions.</p></body></html>";Files.writeString(f.toPath(),html,StandardCharsets.UTF_8);ObjectNode n=json.createObjectNode();n.put("saved",LocalDateTime.now().toString());n.put("name",projectName.getText());n.put("path",f.getAbsolutePath());store.append("designReports",n);status.setText("Report exported: "+f.getName());}catch(Exception ex){status.setText(message(ex));}});
        clear.setOnAction(e->journal.clear());
        VBox meta=card("Project identity","This metadata is used in the exported calculation report.");meta.getChildren().addAll(grid(row("Project",projectName),row("Client / employer",projectClient),row("Site / location",projectSite),row("Project notes",projectNotes)),new HBox(8,saveProject,loadLatest,export,clear),status);
        VBox rec=card("Recent calculations","Every integrated calculation is added here and to the report journal.");rec.getChildren().add(recent);
        VBox log=card("Calculation journal","Edit or remove notes before exporting the report if needed.");log.getChildren().add(journal);
        page.getChildren().addAll(meta,rec,log);return page;
    }

    private void addJournal(String title,String text){String stamp=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));String block="\n\n============================================================\n"+title+" — "+stamp+"\n============================================================\n"+text+"\n";journal.appendText(block);recent.getItems().add(0,stamp+" · "+title);while(recent.getItems().size()>12)recent.getItems().remove(recent.getItems().size()-1);}
    private void record(String collection,String title,String text){ObjectNode n=json.createObjectNode();n.put("saved",LocalDateTime.now().toString());n.put("project",projectName.getText());n.put("title",title);n.put("result",text);store.append(collection,n);}

    private final class PanelRow {final HBox node=new HBox(6);final TextField circuit=field(""),desc=field(""),va=field("0"),breaker=field("15");final ChoiceBox<String> phase=choice("A","A","B","C","AB","BC","CA","ABC"),poles=choice("1","1","2","3");PanelRow(List<PanelRow> list,VBox parent){circuit.setText(String.valueOf(list.size()+1));circuit.setPrefWidth(55);desc.setPrefWidth(210);va.setPrefWidth(90);phase.setPrefWidth(70);poles.setPrefWidth(60);breaker.setPrefWidth(70);Button x=secondary("×");x.setOnAction(e->{parent.getChildren().remove(node);list.remove(this);});node.setAlignment(Pos.CENTER_LEFT);node.getChildren().addAll(new Label("Ckt"),circuit,desc,new Label("VA"),va,phase,new Label("P"),poles,new Label("A"),breaker,x);}}
    private final class EstimateRow {final HBox node=new HBox(6);final TextField desc=field(""),qty=field("1"),cost=field("0"),hours=field("0");EstimateRow(List<EstimateRow> list,VBox parent){desc.setPrefWidth(260);qty.setPrefWidth(80);cost.setPrefWidth(90);hours.setPrefWidth(80);Button x=secondary("×");x.setOnAction(e->{parent.getChildren().remove(node);list.remove(this);});node.setAlignment(Pos.CENTER_LEFT);node.getChildren().addAll(desc,new Label("Qty"),qty,new Label("$/unit"),cost,new Label("Lab h"),hours,x);}}
    private record Option(String size,Wire w,double allowable,double vd,double vdPct) {}

    private static double ambientFactor(int amb,int rating){if(amb<=30)return 1.0;Map<Integer,double[]> m=Map.of(35,new double[]{.91,.94,.96},40,new double[]{.82,.88,.91},45,new double[]{.71,.82,.87},50,new double[]{.58,.75,.82},55,new double[]{.41,.67,.76},60,new double[]{Double.NaN,.58,.71},65,new double[]{Double.NaN,.47,.65},70,new double[]{Double.NaN,.33,.58},75,new double[]{Double.NaN,Double.NaN,.50},80,new double[]{Double.NaN,Double.NaN,.41});double[] a=m.get(amb);if(a==null)throw new IllegalArgumentException("Unsupported ambient temperature.");double f=a[rating==60?0:rating==75?1:2];if(!Double.isFinite(f))throw new IllegalArgumentException("Selected insulation rating is not suitable for that ambient temperature.");return f;}
    private static double groupFactor(int n){return n<=3?1:n<=6?.80:n<=24?.70:n<=42?.60:.50;}
    private static double amp(Wire w,int temp){return temp<=60?w.a60():temp<=75?w.a75():w.a90();}
    private static int nextOcpd(double target){for(int n:STANDARD_OCPD)if(n+1e-9>=target)return n;return STANDARD_OCPD[STANDARD_OCPD.length-1];}
    private static int smallConductorCap(String material,String size){if(material.equals("Copper")){if(size.equals("14 AWG"))return 15;if(size.equals("12 AWG"))return 20;if(size.equals("10 AWG"))return 30;}else{if(size.equals("12 AWG"))return 15;if(size.equals("10 AWG"))return 25;}return 0;}
    private static String bondSize(int ocpd){for(Map.Entry<Integer,String> e:BOND_CU.entrySet())if(ocpd<=e.getKey())return e.getValue();return "> Table 16 range";}
    private static double bondArea(String size){Wire w=CU.get(size);return w==null?0:w.fillArea();}
    private static String minRaceway(String type,double occupied,double pct){Map<String,Double> m=type.equals("EMT")?EMT:PVC;for(Map.Entry<String,Double> e:m.entrySet())if(occupied<=e.getValue()*pct/100.0)return e.getKey();return "larger than listed presets";}

    private VBox page(String title,String desc){VBox p=new VBox(13);Label h=new Label(title);h.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");Label d=new Label(desc);d.setWrapText(true);d.setStyle("-fx-text-fill:#64748b;");p.getChildren().addAll(h,d);return p;}
    private VBox card(String title,String desc){VBox b=new VBox(10);b.setStyle(CARD);Label h=new Label(title);h.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");Label d=new Label(desc);d.setWrapText(true);d.setStyle("-fx-text-fill:#64748b;");b.getChildren().addAll(h,d);return b;}
    private GridPane grid(Node... nodes){GridPane g=new GridPane();g.setHgap(10);g.setVgap(8);int i=0;for(Node n:nodes){g.add(n,0,i++);GridPane.setHgrow(n,Priority.ALWAYS);}return g;}
    private HBox row(String label,Node control){Label l=new Label(label);l.setMinWidth(245);l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;");HBox h=new HBox(10,l,control);h.setAlignment(Pos.CENTER_LEFT);HBox.setHgrow(control,Priority.ALWAYS);if(control instanceof Region r)r.setMaxWidth(Double.MAX_VALUE);return h;}
    private static TextField field(String v){TextField f=new TextField(v);f.setStyle(INPUT);return f;}
    @SafeVarargs private final <T> ChoiceBox<T> choice(T selected,T... values){ChoiceBox<T> c=new ChoiceBox<>();c.getItems().addAll(values);c.setValue(selected);return c;}
    private Button primary(String t){Button b=new Button(t);b.setStyle(PRIMARY);return b;}
    private Button secondary(String t){Button b=new Button(t);b.setStyle(SECONDARY);return b;}
    private TextArea output(){TextArea a=new TextArea("Enter values, then calculate.");a.setEditable(false);a.setWrapText(true);a.setPrefRowCount(18);a.setStyle("-fx-font-family:Consolas;-fx-font-size:12px;-fx-control-inner-background:#0f172a;-fx-text-fill:#e2e8f0;");return a;}
    private Label status(){Label l=new Label();l.setWrapText(true);l.setStyle("-fx-text-fill:#475569;-fx-font-size:12px;-fx-font-weight:bold;");return l;}
    private ScrollPane scroll(Node n){ScrollPane s=new ScrollPane(n);s.setFitToWidth(true);s.setStyle("-fx-background:transparent;-fx-background-color:transparent;");return s;}
    private static double positive(TextField f,String name){try{double v=Double.parseDouble(f.getText().trim());if(!Double.isFinite(v)||v<=0)throw new Exception();return v;}catch(Exception e){throw new IllegalArgumentException(name+" must be greater than zero.");}}
    private static int positiveInt(TextField f,String name){try{int v=Integer.parseInt(f.getText().trim());if(v<=0)throw new Exception();return v;}catch(Exception e){throw new IllegalArgumentException(name+" must be a whole number greater than zero.");}}
    private static double num(TextField f){return num(f.getText());}
    private static double num(String s){try{return s==null||s.isBlank()?0:Double.parseDouble(s.trim());}catch(Exception e){return 0;}}
    private static String message(Exception e){return e.getMessage()==null?e.toString():e.getMessage();}
    private static String safe(String s){String x=s==null?"project":s.trim().replaceAll("[^A-Za-z0-9._-]+","-");return x.isBlank()?"project":x;}
    private static String csv(String s){String x=s==null?"":s;return "\""+x.replace("\"","\"\"")+"\"";}
    private static String esc(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
}

package com.example.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDateTime;
import java.util.*;

/** Load-driven transformer sizing and standard-size selector. */
public final class TransformerLoadSizingPane extends BorderPane {
    private static final String INPUT="-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 9;";
    private static final String PRIMARY="-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-padding:9 13;";
    private static final String SECONDARY="-fx-background-color:#f8fafc;-fx-text-fill:#0f4c81;-fx-border-color:#c7d9e8;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 11;-fx-font-weight:bold;";
    private static final String CARD="-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#dfe7ef;-fx-border-radius:14;-fx-padding:16;";

    private static final double[] SINGLE_PHASE_SIZES = {0.5,0.75,1,1.5,2,3,5,7.5,10,15,25,37.5,50,75,100,167,250,333,500};
    private static final double[] THREE_PHASE_SIZES = {3,6,9,15,30,45,75,112.5,150,225,300,500,750,1000,1500,2000,2500,3000};

    // These choices identify the source of an independently calculated service load.
    // The transformer selector does not replace CEC Section 8 service calculations.
    private static final String[] SERVICE_TYPES = {
        "Service — calculated demand",
        "Service — residential",
        "Service — apartments / multi-unit",
        "Service — commercial / ICI",
        "Service — school",
        "Service — hospital",
        "Service — hotel / motel",
        "Service — industrial"
    };
    private static boolean isCalculatedService(String type) {
        return type != null && type.startsWith("Service — ");
    }

    private final ObjectMapper json;
    private final LocalWorkspaceStore store;
    private final VBox loadRows = new VBox(7);
    private final List<LoadRow> rows = new ArrayList<>();
    private final ChoiceBox<String> phase = choiceStatic("3φ","1φ","3φ");
    private final TextField primaryVoltage = field("600");
    private final TextField secondaryVoltage = field("208");
    private final TextField sparePercent = field("20");
    private final TextArea output = new TextArea();
    private final Label status = new Label();
    private final Label totalBadge = new Label("Calculated load: 0.00 kVA");
    private final Label selectedBadge = new Label("Recommended transformer: —");

    public TransformerLoadSizingPane(ObjectMapper json, String username) {
        this.json = json;
        this.store = new LocalWorkspaceStore(json, username);

        VBox page = new VBox(13);
        page.setPadding(new Insets(2));
        Label title = new Label("Transformer Size Selector — Based on Connected Loads");
        title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label sub = new Label("Enter the loads the transformer will actually supply, including optional pre-calculated service demand loads. Equipment rows use their entered demand/continuous factors; calculated service rows are treated as final demand (100% with no additional continuous multiplier). Design spare capacity is then applied before selecting a common transformer size.");
        sub.setWrapText(true); sub.setStyle("-fx-text-fill:#64748b;");
        page.getChildren().addAll(title,sub);

        VBox setup = card("1. Transformer system","The primary voltage is used to calculate primary full-load current. Loads entered in amperes are converted using the selected secondary voltage and phase.");
        setup.getChildren().add(grid(
            row("Transformer phase",phase),
            row("Primary voltage (V)",primaryVoltage),
            row("Secondary voltage (V)",secondaryVoltage),
            row("Design spare / future capacity (%)",sparePercent)
        ));
        page.getChildren().add(setup);

        Button add = secondary("+ Add load");
        Button addService = secondary("+ Add service demand");
        Button example = secondary("Load example");
        Button clear = secondary("Clear loads");
        add.setOnAction(e -> addRow("New load","General","1","10","kVA","0.90","100",false));
        addService.setOnAction(e -> addRow("Calculated service load","Service — calculated demand","1","100","kVA","1.00","100",false));
        example.setOnAction(e -> loadExample());
        clear.setOnAction(e -> { rows.clear(); loadRows.getChildren().clear(); addRow("Lighting","Lighting","1","12","kW","0.95","100",true); });

        VBox loads = card("2. Loads","Select any regular load or a calculated service category in Type. A service row expects the completed service demand, entered as kVA, VA, kW/W (with PF), or calculated demand amperes at the transformer secondary voltage. Service rows are not demand-reduced or multiplied by 125% again. Do not duplicate loads already covered by a service total.");
        HBox header = new HBox(6,
            header("Description",180),header("Type",200),header("Qty",55),header("Value",70),header("Unit",70),header("PF",60),header("Demand %",80),header("Continuous",85),header("",38));
        header.setAlignment(Pos.CENTER_LEFT);
        loads.getChildren().addAll(header,loadRows,new HBox(8,add,addService,example,clear));
        page.getChildren().add(loads);

        output.setEditable(false); output.setWrapText(true); output.setPrefRowCount(24);
        output.setStyle("-fx-font-family:Consolas;-fx-font-size:12px;-fx-control-inner-background:#0f172a;-fx-text-fill:#e2e8f0;");
        status.setWrapText(true); status.setStyle("-fx-text-fill:#475569;-fx-font-weight:bold;");
        totalBadge.setStyle("-fx-background-color:#f1f5f9;-fx-background-radius:10;-fx-padding:8 12;-fx-font-weight:bold;-fx-text-fill:#334155;");
        selectedBadge.setStyle("-fx-background-color:#ecfeff;-fx-background-radius:10;-fx-padding:8 12;-fx-font-weight:bold;-fx-text-fill:#155e75;");
        Button calculate = primary("Select transformer size");
        Button save = secondary("Save transformer design");
        Button copy = secondary("Copy result");
        final String[] latest = {""};

        calculate.setOnAction(e -> {
            try {
                Result r = calculate();
                latest[0] = r.text();
                output.setText(r.text());
                totalBadge.setText(String.format(Locale.CANADA,"Calculated load: %.2f kVA",r.adjustedLoadKva()));
                selectedBadge.setText(r.selectedKva() > 0
                    ? String.format(Locale.CANADA,"Recommended transformer: %s kVA",fmtKva(r.selectedKva()))
                    : "Recommended transformer: custom / engineered above standard list");
                status.setText(r.selectedKva() > 0
                    ? "Transformer selected from the common standard-size list. Verify exact manufacturer ratings and the applicable CEC installation/protection rules before construction."
                    : "Calculated design load exceeds the built-in common transformer list. Use an engineered/custom arrangement or multiple transformers as appropriate.");
            } catch (Exception ex) {
                output.setText(message(ex));
                status.setText("Check the transformer/load inputs.");
            }
        });
        save.setOnAction(e -> {
            if (latest[0].isBlank()) return;
            ObjectNode n=json.createObjectNode();
            n.put("saved", LocalDateTime.now().toString());
            n.put("title","Load-Based Transformer Sizing");
            n.put("result",latest[0]);
            store.append("transformerDesigns",n);
            status.setText("Transformer sizing result saved to local transformer design records.");
        });
        copy.setOnAction(e -> {
            if(latest[0].isBlank()) return;
            javafx.scene.input.ClipboardContent c=new javafx.scene.input.ClipboardContent();
            c.putString(latest[0]); javafx.scene.input.Clipboard.getSystemClipboard().setContent(c);
            status.setText("Transformer sizing result copied.");
        });

        VBox result = card("3. Recommended transformer","The minimum standard size is selected from the design kVA after demand, continuous-load factors and spare capacity. The next larger standard size is also shown for comparison.");
        result.getChildren().addAll(new HBox(9,totalBadge,selectedBadge),new HBox(8,calculate,save,copy),status,output);
        page.getChildren().add(result);

        addRow("Lighting","Lighting","1","12","kW","0.95","100",true);
        addRow("Receptacles","Receptacles","1","8","kVA","1.00","100",false);
        addRow("Mechanical equipment","Motor / HVAC","1","18","kVA","0.90","100",false);

        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true); scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        setCenter(scroll);
    }

    private Result calculate() {
        double pv=positive(primaryVoltage,"Primary voltage");
        double sv=positive(secondaryVoltage,"Secondary voltage");
        double spare=nonNegative(sparePercent,"Spare capacity");
        if(spare>200) throw new IllegalArgumentException("Spare capacity must be 200% or less.");
        boolean three=phase.getValue().equals("3φ");
        if(rows.isEmpty()) throw new IllegalArgumentException("Add at least one load.");

        double connected=0, demanded=0, adjusted=0, serviceDemand=0;
        int serviceRows=0, equipmentRows=0;
        StringBuilder detail=new StringBuilder();
        int used=0;
        for(LoadRow row:rows){
            if(row.description.getText().isBlank() && row.value.getText().isBlank()) continue;
            double qty=positive(row.quantity,"Quantity");
            double value=positive(row.value,"Load value");
            double pf=positive(row.powerFactor,"Power factor");
            if(pf>1) throw new IllegalArgumentException("Power factor must be 1.00 or less for "+name(row)+".");
            double demand=nonNegative(row.demand,"Demand percent");
            if(demand>100) throw new IllegalArgumentException("Demand percent must be 100% or less for "+name(row)+".");
            double baseKva=toKva(value,row.unit.getValue(),qty,pf,sv,three);
            boolean calculatedService=isCalculatedService(row.type.getValue());
            double demandKva=calculatedService?baseKva:baseKva*demand/100.0;
            double designKva=calculatedService?demandKva:demandKva*(row.continuous.isSelected()?1.25:1.0);
            if(calculatedService){serviceDemand+=demandKva;serviceRows++;}else equipmentRows++;
            connected+=baseKva; demanded+=demandKva; adjusted+=designKva; used++;
            detail.append(String.format(Locale.CANADA,
                "  %-24s %-13s Qty %-5s %8.2f %-4s PF %.2f | base %8.2f kVA | demand %6.1f%% = %8.2f kVA | design %8.2f kVA%s%n",
                trim(row.description.getText(),24),trim(row.type.getValue(),13),fmt(qty),value,row.unit.getValue(),pf,baseKva,calculatedService?100.0:demand,demandKva,designKva,
                calculatedService?" (calculated service demand; no additional demand or continuous factor)":row.continuous.isSelected()?" (125% continuous)":""));
        }
        if(used==0) throw new IllegalArgumentException("Enter at least one usable load row.");

        double withSpare=adjusted*(1+spare/100.0);
        double[] sizes=three?THREE_PHASE_SIZES:SINGLE_PHASE_SIZES;
        double selected=nextSize(withSpare,sizes);
        double next=selected>0?nextAfter(selected,sizes):0;
        double factor=three?Math.sqrt(3):1;
        double actualSecondaryCurrent=adjusted*1000/(factor*sv);
        double designSecondaryCurrent=withSpare*1000/(factor*sv);
        double selectedPrimaryFla=selected>0?selected*1000/(factor*pv):0;
        double selectedSecondaryFla=selected>0?selected*1000/(factor*sv):0;
        double actualLoading=selected>0?adjusted/selected*100:0;
        double reserveLoading=selected>0?withSpare/selected*100:0;
        double headroom=selected>0?selected-adjusted:0;
        double nextSecondary=next>0?next*1000/(factor*sv):0;

        String selection = selected>0
            ? String.format(Locale.CANADA,
                "RECOMMENDED STANDARD SIZE: %s kVA%nSelected transformer primary FLA: %.2f A at %.0f V%nSelected transformer secondary FLA: %.2f A at %.0f V%nCalculated adjusted load current: %.2f A secondary%nDesign current including %.1f%% spare: %.2f A secondary%nActual adjusted-load utilization: %.1f%%%nUtilization including requested spare allowance: %.1f%%%nHeadroom above adjusted load: %.2f kVA%n%s",
                fmtKva(selected),selectedPrimaryFla,pv,selectedSecondaryFla,sv,actualSecondaryCurrent,spare,designSecondaryCurrent,actualLoading,reserveLoading,headroom,
                next>0?String.format(Locale.CANADA,"Next larger common size: %s kVA (secondary FLA %.2f A)",fmtKva(next),nextSecondary):"Selected size is the largest common size in the built-in list.")
            : String.format(Locale.CANADA,
                "REQUIRED DESIGN SIZE: %.2f kVA%nThis exceeds the largest built-in common %s transformer size of %s kVA.%nUse a manufacturer-specific/custom size, parallel/alternate arrangement, or divide the loads as appropriate.",
                withSpare,three?"three-phase":"single-phase",fmtKva(sizes[sizes.length-1]));

        String serviceChecks=serviceRows==0?"":String.format(Locale.CANADA,
            "SERVICE DEMAND INPUTS%n• %d calculated service row(s) total %.2f kVA before spare capacity.%n• These rows must already reflect the applicable CEC service demand calculation; the selector does not calculate service demand factors.%n%s%n",
            serviceRows,serviceDemand,equipmentRows>0
                ?"• You have both service and equipment rows. Ensure equipment included in a service calculation is not counted twice."
                :"• Verify the calculated service demand includes all loads served by this transformer.");
        String text=String.format(Locale.CANADA,
            "LOAD-BASED TRANSFORMER SIZE SELECTOR%n%nSYSTEM%nPhase: %s%nPrimary: %.0f V%nSecondary: %.0f V%nRequested spare/future capacity: %.1f%%%n%nLOAD BREAKDOWN%n%s%nTOTALS%nConnected load before demand: %.2f kVA%nAfter row demand factors: %.2f kVA%nAfter continuous-load design factors: %.2f kVA%nRequired design kVA including spare: %.2f kVA%n%n%s%n%nSTANDARD SIZE BASIS%n%s%n%n%sIMPORTANT CHECKS%n• kW/W inputs are converted to kVA using the entered power factor.%n• Ampere inputs must represent calculated demand current at the selected transformer secondary voltage and phase (not the service breaker rating).%n• For regular equipment rows, demand and continuous factors are user-entered; apply only the relevant CEC requirements.%n• Calculated service rows bypass additional demand and continuous factors; complete the applicable CEC Section 8 service calculation separately.%n• Verify transformer primary/secondary overcurrent protection, conductor ampacity, grounding/bonding, ventilation, location, impedance/SCCR requirements and manufacturer data separately.%n• Standard-size lists are common selector values for planning; final available ratings depend on manufacturer/product family.%n",
            phase.getValue(),pv,sv,spare,detail,connected,demanded,adjusted,withSpare,selection,standardList(sizes),serviceChecks);

        return new Result(connected,demanded,adjusted,withSpare,selected,next,text);
    }

    private double toKva(double value,String unit,double qty,double pf,double sv,boolean three){
        return switch(unit){
            case "VA" -> value*qty/1000.0;
            case "kVA" -> value*qty;
            case "W" -> value*qty/(1000.0*pf);
            case "kW" -> value*qty/pf;
            case "A" -> (three?Math.sqrt(3):1)*sv*value*qty/1000.0;
            default -> throw new IllegalArgumentException("Unsupported load unit: "+unit);
        };
    }

    private void loadExample(){
        rows.clear(); loadRows.getChildren().clear();
        phase.setValue("3φ"); primaryVoltage.setText("600"); secondaryVoltage.setText("208"); sparePercent.setText("20");
        addRow("Lighting","Lighting","1","18","kW","0.95","100",true);
        addRow("General receptacles","Receptacles","1","12","kVA","1.00","80",false);
        addRow("AHU / fan motors","Motor / HVAC","2","7.5","kW","0.88","100",false);
        addRow("Controls / misc.","General","1","3","kVA","1.00","100",false);
    }

    private void addRow(String description,String type,String qty,String value,String unit,String pf,String demand,boolean continuous){
        LoadRow row=new LoadRow(description,type,qty,value,unit,pf,demand,continuous);
        rows.add(row); loadRows.getChildren().add(row.node);
    }

    private final class LoadRow {
        final HBox node=new HBox(6);
        final TextField description=field(""),quantity=field("1"),value=field("0"),powerFactor=field("0.90"),demand=field("100");
        final ChoiceBox<String> type=choiceStatic("General",
            "General","Lighting","Receptacles","Motor / HVAC","Heating","EV charging","Controls","Other",
            SERVICE_TYPES[0],SERVICE_TYPES[1],SERVICE_TYPES[2],SERVICE_TYPES[3],
            SERVICE_TYPES[4],SERVICE_TYPES[5],SERVICE_TYPES[6],SERVICE_TYPES[7]);
        final ChoiceBox<String> unit=choiceStatic("kVA","A","VA","kVA","W","kW");
        final CheckBox continuous=new CheckBox();
        LoadRow(String d,String t,String q,String v,String u,String pf,String dem,boolean cont){
            description.setText(d); type.setValue(t); quantity.setText(q); value.setText(v); unit.setValue(u); powerFactor.setText(pf); demand.setText(dem); continuous.setSelected(cont);
            description.setPrefWidth(180);type.setPrefWidth(200);quantity.setPrefWidth(55);value.setPrefWidth(70);unit.setPrefWidth(70);powerFactor.setPrefWidth(60);demand.setPrefWidth(80);continuous.setPrefWidth(85);
            Button remove=secondary("×"); remove.setMinWidth(38); remove.setOnAction(e->{rows.remove(this);loadRows.getChildren().remove(node);});
            type.setTooltip(new Tooltip("Calculated service types assume a completed service demand calculation; demand factor and 125% continuous factor are not reapplied."));
            type.getSelectionModel().selectedItemProperty().addListener((obs,oldValue,newValue)->updateServiceMode());
            updateServiceMode();
            node.setAlignment(Pos.CENTER_LEFT); node.getChildren().addAll(description,type,quantity,value,unit,powerFactor,demand,continuous,remove);
        }
        private void updateServiceMode() {
            boolean service=isCalculatedService(type.getValue());
            if(service) {
                demand.setText("100");
                continuous.setSelected(false);
                if(unit.getValue().equals("kVA")||unit.getValue().equals("VA")||unit.getValue().equals("A")) {
                    powerFactor.setText("1.00");
                }
            }
            demand.setDisable(service);
            continuous.setDisable(service);
            String guidance=service
                ?"Enter CEC-calculated service demand. Demand stays 100% and Continuous is disabled to prevent double adjustment."
                :"Use demand/continuous factors only when appropriate for this equipment load.";
            demand.setTooltip(new Tooltip(guidance));
            continuous.setTooltip(new Tooltip(guidance));
        }
    }

    private record Result(double connectedKva,double demandedKva,double adjustedLoadKva,double requiredKva,double selectedKva,double nextKva,String text){}

    private static double nextSize(double required,double[] sizes){for(double s:sizes)if(s+1e-9>=required)return s;return -1;}
    private static double nextAfter(double selected,double[] sizes){for(double s:sizes)if(s>selected+1e-9)return s;return -1;}
    private static String standardList(double[] sizes){StringBuilder b=new StringBuilder();for(int i=0;i<sizes.length;i++){if(i>0)b.append(", ");b.append(fmtKva(sizes[i]));}return b.append(" kVA").toString();}
    private static String fmtKva(double v){return Math.abs(v-Math.rint(v))<1e-9?String.format(Locale.CANADA,"%.0f",v):String.format(Locale.CANADA,"%.2f",v).replaceAll("0+$","").replaceAll("\\.$","");}
    private static String fmt(double v){return Math.abs(v-Math.rint(v))<1e-9?String.format(Locale.CANADA,"%.0f",v):String.format(Locale.CANADA,"%.2f",v);}
    private static String name(LoadRow r){return r.description.getText().isBlank()?"load":r.description.getText().trim();}
    private static String trim(String s,int max){if(s==null)return "";String x=s.trim();return x.length()<=max?x:x.substring(0,Math.max(0,max-1))+"…";}
    private static String message(Exception e){return e.getMessage()==null?e.toString():e.getMessage();}
    private static double positive(TextField f,String name){try{double v=Double.parseDouble(f.getText().trim());if(!Double.isFinite(v)||v<=0)throw new Exception();return v;}catch(Exception e){throw new IllegalArgumentException(name+" must be greater than zero.");}}
    private static double nonNegative(TextField f,String name){try{double v=Double.parseDouble(f.getText().trim());if(!Double.isFinite(v)||v<0)throw new Exception();return v;}catch(Exception e){throw new IllegalArgumentException(name+" must be zero or greater.");}}
    private static Label header(String text,double width){Label l=new Label(text);l.setMinWidth(width);l.setPrefWidth(width);l.setStyle("-fx-font-weight:bold;-fx-text-fill:#475569;");return l;}
    private VBox card(String title,String desc){VBox b=new VBox(10);b.setStyle(CARD);Label h=new Label(title);h.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");Label d=new Label(desc);d.setWrapText(true);d.setStyle("-fx-text-fill:#64748b;");b.getChildren().addAll(h,d);return b;}
    private GridPane grid(javafx.scene.Node... nodes){GridPane g=new GridPane();g.setHgap(10);g.setVgap(8);int i=0;for(javafx.scene.Node n:nodes){g.add(n,0,i++);GridPane.setHgrow(n,Priority.ALWAYS);}return g;}
    private HBox row(String label,Control c){Label l=new Label(label);l.setMinWidth(260);l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;");HBox h=new HBox(10,l,c);h.setAlignment(Pos.CENTER_LEFT);HBox.setHgrow(c,Priority.ALWAYS);c.setMaxWidth(Double.MAX_VALUE);return h;}
    private static TextField field(String value){TextField f=new TextField(value);f.setStyle(INPUT);return f;}
    @SafeVarargs private static <T> ChoiceBox<T> choiceStatic(T selected,T... values){ChoiceBox<T> c=new ChoiceBox<>();c.getItems().addAll(values);c.setValue(selected);return c;}
    private static Button primary(String text){Button b=new Button(text);b.setStyle(PRIMARY);return b;}
    private static Button secondary(String text){Button b=new Button(text);b.setStyle(SECONDARY);return b;}
}

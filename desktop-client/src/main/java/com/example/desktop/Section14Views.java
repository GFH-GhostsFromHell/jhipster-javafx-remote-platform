package com.example.desktop;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Section14Views {
    private Section14Views() {}

    public static Node create(TitledPane legacyTap) {
        VBox root = new VBox(12);
        root.setPadding(new Insets(22));
        root.setStyle("-fx-background-color:#f8fafc;");

        Label title = new Label("Section 14 — Protection & Tap Conductors");
        title.setStyle("-fx-font-size:21px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label lead = new Label("CEC 2024 tap worksheets for general feeder taps, transformer taps, motor taps and welder supply taps. Enter conductor ampacity, not just breaker rating, where the rule compares conductor sizes.");
        lead.setWrapText(true);
        lead.setStyle("-fx-text-fill:#64748b;");

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
            tab("General Tap", generalTap()),
            tab("Transformer Tap", transformerTap()),
            tab("Motor Tap", motorTap()),
            tab("Welder Tap", welderTap())
        );
        if (legacyTap != null) {
            VBox legacy = new VBox(10, legacyTap);
            legacy.setPadding(new Insets(8));
            tabs.getTabs().add(tab("Legacy Ratio Check", legacy));
        }

        VBox.setVgrow(tabs, Priority.ALWAYS);
        root.getChildren().addAll(title, lead, new Separator(), tabs);
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background:#f8fafc;-fx-background-color:#f8fafc;");
        return scroll;
    }

    private static Tab tab(String title, Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private static Node generalTap() {
        GridPane form = form();
        ComboBox<String> mode = new ComboBox<>();
        mode.getItems().addAll("7.5 m one-third tap — Rule 14-100(1)(c)", "3 m equipment tap — Rule 14-100(1)(b)");
        mode.getSelectionModel().selectFirst();
        TextField feeder = tf("285"), selected = tf("100"), length = tf("7.5"), downstream = tf("100"), load = tf("80"), equipment = tf("100");
        CheckBox mechanical = cb("Mechanically protected for 7.5 m tap", true);
        CheckBox enclosed = cb("3 m tap is enclosed in the required wiring method", true);
        CheckBox noExtend = cb("3 m tap does not extend beyond the supplied equipment", true);
        row(form, 0, "Tap rule mode", mode);
        row(form, 1, "Larger conductor ampacity (A)", feeder);
        row(form, 2, "Selected tap ampacity (A)", selected);
        row(form, 3, "Tap length (m)", length);
        row(form, 4, "Downstream single OCPD (A)", downstream);
        row(form, 5, "Computed load for 3 m mode (A)", load);
        row(form, 6, "Supplied equipment rating for 3 m mode (A)", equipment);
        form.add(mechanical, 0, 7, 2, 1); form.add(enclosed, 0, 8, 2, 1); form.add(noExtend, 0, 9, 2, 1);
        return worksheet(form, "Calculate general tap", () -> {
            double feederA = n(feeder), selectedA = n(selected), len = n(length);
            if (mode.getSelectionModel().getSelectedIndex() == 1) {
                double loadA = n(load), equipmentA = n(equipment), req = Math.max(loadA, equipmentA);
                boolean pass = len <= 3 && enclosed.isSelected() && noExtend.isSelected() && selectedA >= req;
                return lines(
                    "GENERAL 3 m EQUIPMENT TAP — RULE 14-100(1)(b)", "",
                    "Computed load: " + f(loadA) + " A", "Equipment rating: " + f(equipmentA) + " A",
                    "Minimum tap ampacity: " + f(req) + " A", "Starter conductor: " + starter(req), "",
                    "Length check: " + passFail(len <= 3), "Enclosure check: " + passFail(enclosed.isSelected()),
                    "Does not extend beyond equipment: " + passFail(noExtend.isSelected()),
                    "Selected tap ampacity: " + f(selectedA) + " A — " + passFail(selectedA >= req), "",
                    "OVERALL: " + passFail(pass), "", "The 3 m exception is load/equipment-rating based; it is not automatically a one-third calculation."
                );
            }
            double down = n(downstream); boolean reduced = len <= 7.5 && mechanical.isSelected();
            double req = reduced ? feederA / 3.0 : feederA;
            boolean pass = selectedA >= req && (!reduced || down <= selectedA);
            return lines(
                "GENERAL 7.5 m ONE-THIRD TAP — RULE 14-100(1)(c)", "",
                "Larger conductor ampacity: " + f(feederA) + " A", "One-third floor: " + f(feederA / 3.0) + " A",
                "Required tap ampacity: " + f(req) + " A", "Starter conductor: " + starter(req), "",
                "Length: " + f(len) + " m", "Mechanical protection: " + yesNo(mechanical.isSelected()),
                "Reduced-tap exception: " + (reduced ? "AVAILABLE" : "NOT AVAILABLE — full feeder ampacity required"),
                "Selected tap ampacity: " + f(selectedA) + " A — " + passFail(selectedA >= req),
                "Downstream OCPD: " + f(down) + " A" + (reduced ? " — " + passFail(down <= selectedA) : ""), "",
                "OVERALL: " + passFail(pass)
            );
        });
    }

    private static Node transformerTap() {
        GridPane form = form();
        TextField feeder = tf("300"), kva = tf("75"), primaryV = tf("600"), secondaryV = tf("208");
        ComboBox<Integer> phases = new ComboBox<>(); phases.getItems().addAll(3, 1); phases.getSelectionModel().selectFirst();
        TextField primaryDemand = tf("0"), secondaryDemand = tf("0"), primarySelected = tf("100"), secondarySelected = tf("350"), primaryLength = tf("3"), secondaryLength = tf("4"), downstream = tf("350");
        CheckBox mechanical = cb("Primary and secondary tap conductors are protected from mechanical damage", true);
        row(form,0,"Larger feeder conductor ampacity (A)",feeder); row(form,1,"Transformer kVA",kva); row(form,2,"Primary voltage (V)",primaryV); row(form,3,"Secondary voltage (V)",secondaryV); row(form,4,"Phases",phases);
        row(form,5,"Primary demand override A (0 = normal 125%)",primaryDemand); row(form,6,"Secondary demand override A (0 = normal 125%)",secondaryDemand);
        row(form,7,"Selected primary tap ampacity (A)",primarySelected); row(form,8,"Selected secondary ampacity (A)",secondarySelected); row(form,9,"Primary tap length (m)",primaryLength); row(form,10,"Secondary length (m)",secondaryLength); row(form,11,"Secondary terminating OCPD (A)",downstream); form.add(mechanical,0,12,2,1);
        return worksheet(form, "Calculate transformer tap", () -> {
            double rating = n(kva), pv = n(primaryV), sv = n(secondaryV), feederA = n(feeder); int ph = phases.getValue();
            double ip = current(rating,pv,ph), is = current(rating,sv,ph), ratio = pv/sv;
            double pd = n(primaryDemand), sd = n(secondaryDemand), pLoad = pd > 0 ? pd : ip*1.25, sLoad = sd > 0 ? sd : is*1.25;
            double pSel = n(primarySelected), sSel = n(secondarySelected), pReq = Math.max(feederA/3.0,pLoad), sRatio = pSel*ratio, sReq = Math.max(sRatio,sLoad), total = n(primaryLength)+n(secondaryLength), down = n(downstream);
            boolean pass = pSel>=pReq && sSel>=sReq && total<=7.5 && mechanical.isSelected() && down<=sSel;
            return lines(
                "TRANSFORMER TAP — RULE 14-100(1)(f) + RULE 26-256", "",
                "Primary rated current: " + f(ip) + " A", "Secondary rated current: " + f(is) + " A", "Voltage ratio Vp/Vs: " + f(ratio), "",
                "PRIMARY", "Normal 125% basis: " + f(ip*1.25) + " A", "Demand override: " + (pd>0?f(pd)+" A":"not used"), "One-third feeder floor: " + f(feederA/3.0) + " A", "Required primary ampacity: " + f(pReq) + " A", "Selected primary: " + f(pSel) + " A — " + passFail(pSel>=pReq), "Starter: " + starter(pReq), "",
                "SECONDARY", "Normal 125% basis: " + f(is*1.25) + " A", "Demand override: " + (sd>0?f(sd)+" A":"not used"), "Selected primary x voltage ratio: " + f(sRatio) + " A", "Required secondary ampacity: " + f(sReq) + " A", "Selected secondary: " + f(sSel) + " A — " + passFail(sSel>=sReq), "Starter: " + starter(sReq), "",
                "Combined primary + secondary length: " + f(total) + " m — " + passFail(total<=7.5), "Mechanical protection: " + passFail(mechanical.isSelected()), "Secondary terminating OCPD: " + f(down) + " A — " + passFail(down<=sSel), "", "OVERALL: " + passFail(pass), "",
                "Leave demand overrides at 0 for the normal 125% transformer-conductor basis. Use demand-load overrides only where Rule 26-256 permits demand sizing and the conductor protection complies with Rules 14-100 and 14-104."
            );
        });
    }

    private static Node motorTap() {
        GridPane form = form();
        ComboBox<String> mode = new ComboBox<>(); mode.getItems().addAll("Feeder tap to motor branch OCPD — Rule 28-110(2)","Group branch tap to individual motor — Rule 28-106(3)"); mode.getSelectionModel().selectFirst();
        TextField base = tf("200"), selected = tf("85"), flcs = tf("40"), length = tf("7.5");
        CheckBox metal = cb("Tap is 3 m or less and enclosed in metal", false), mechanical = cb("Tap is suitably protected from mechanical damage", true);
        row(form,0,"Motor tap mode",mode); row(form,1,"Feeder / branch conductor ampacity (A)",base); row(form,2,"Selected tap conductor ampacity (A)",selected); row(form,3,"Motor FLC(s), comma-separated (A)",flcs); row(form,4,"Tap length (m)",length); form.add(metal,0,5,2,1);form.add(mechanical,0,6,2,1);
        return worksheet(form,"Calculate motor tap",()->{
            List<Double> motors = csv(flcs.getText()); double largest=motors.stream().mapToDouble(Double::doubleValue).max().orElseThrow(), sum=motors.stream().mapToDouble(Double::doubleValue).sum(), motorBasis=largest*1.25+(sum-largest), source=n(base), sel=n(selected), len=n(length), required; String route;
            if(mode.getSelectionModel().getSelectedIndex()==1){double single=motors.get(0)*1.25;if(len<=7.5){required=Math.max(single,source/3.0);route="Rule 28-106(3), tap 7.5 m or less";}else{required=source;route="Over 7.5 m — full branch-conductor ampacity";}}
            else if(len<=3&&metal.isSelected()){required=motorBasis;route="Rule 28-110(2)(a), 3 m or less in metal";}
            else if(len<=7.5&&mechanical.isSelected()){required=Math.max(motorBasis,source/3.0);route="Rule 28-110(2)(b), 7.5 m or less / one-third / mechanical protection";}
            else{required=source;route="Reduced feeder-tap exception unavailable";}
            boolean pass=sel>=required;
            return lines("MOTOR TAP — "+route,"","Motor FLC(s): "+motors,"Largest motor FLC: "+f(largest)+" A","Continuous-duty motor load basis: "+f(motorBasis)+" A","Source conductor ampacity: "+f(source)+" A","One-third source value: "+f(source/3.0)+" A","","Required tap ampacity: "+f(required)+" A","Starter conductor: "+starter(required),"Selected tap ampacity: "+f(sel)+" A — "+passFail(pass),"Tap length: "+f(len)+" m","","OVERALL: "+passFail(pass),"","For non-continuous-duty motors use the applicable Table 27 calculation instead of the 125% continuous-duty basis. Branch OCPD is a separate Section 28 calculation.");
        });
    }

    private static Node welderTap() {
        GridPane form = form();
        ComboBox<String> type = new ComboBox<>(); type.getItems().addAll("Transformer / inverter — Table 42A factor","Motor-generator — Table 42B factor + Section 28","Resistance manual non-automatic — 50%","Resistance seam / automatic — 70%","Resistance known actual current — Table 42C factor"); type.getSelectionModel().selectFirst();
        TextField rated=tf("100"),actual=tf("100"),duty=tf("0.63"),feeder=tf("200"),selected=tf("85"),length=tf("7.5"),downstream=tf("80"); CheckBox mechanical=cb("Tap is suitably protected from mechanical damage",true);
        row(form,0,"Welder type",type);row(form,1,"Rated primary current (A)",rated);row(form,2,"Actual primary current — known-current mode (A)",actual);row(form,3,"Duty-cycle / table factor",duty);row(form,4,"Feeder conductor ampacity (A)",feeder);row(form,5,"Selected tap conductor ampacity (A)",selected);row(form,6,"Tap length (m)",length);row(form,7,"Downstream welder OCPD (A)",downstream);form.add(mechanical,0,8,2,1);
        return worksheet(form,"Calculate welder tap",()->{
            double r=n(rated),a=n(actual),factor=n(duty),feed=n(feeder),sel=n(selected),len=n(length),down=n(downstream),load;String label;Double protectionFactor;
            switch(type.getSelectionModel().getSelectedIndex()){
                case 1 -> {load=r*factor;label="Motor-generator: rated primary current x entered Table 42B factor; Section 28 also applies";protectionFactor=null;}
                case 2 -> {load=r*0.50;label="Manual non-automatic resistance welder: 50% rated primary current";protectionFactor=3.0;}
                case 3 -> {load=r*0.70;label="Seam / automatically fed resistance welder: 70% rated primary current";protectionFactor=3.0;}
                case 4 -> {load=a*factor;label="Known actual primary current x entered Table 42C factor";protectionFactor=3.0;}
                default -> {load=r*factor;label="Transformer / inverter: rated primary current x entered Table 42A factor";protectionFactor=2.0;}
            }
            boolean reduced=len<=7.5&&mechanical.isSelected();double required=reduced?Math.max(load,feed/3.0):feed;boolean tapOk=sel>=required,terminalOk=!reduced||down<=sel;Double sectionMax=protectionFactor==null?null:Math.min(r*protectionFactor,sel*protectionFactor);boolean sectionOk=sectionMax==null||down<=sectionMax,pass=tapOk&&terminalOk&&sectionOk;
            return lines("WELDER SUPPLY TAP — SECTION 42 + GENERAL TAP RULE","",label,"Welder supply-conductor load: "+f(load)+" A","Feeder conductor ampacity: "+f(feed)+" A","One-third feeder value: "+f(feed/3.0)+" A","","Reduced general tap: "+(reduced?"AVAILABLE":"NOT AVAILABLE — full feeder ampacity required"),"Required tap ampacity: "+f(required)+" A","Starter conductor: "+starter(required),"Selected tap ampacity: "+f(sel)+" A — "+passFail(tapOk),"Downstream OCPD: "+f(down)+" A"+(reduced?" — "+passFail(terminalOk):""),sectionMax==null?"Motor-generator welder: verify Section 28 / Motor Tap conditions.":"Section 42 worksheet OCPD ceiling before permitted next-size exceptions: "+f(sectionMax)+" A — "+passFail(sectionOk),"","OVERALL: "+passFail(pass),"","Section 42 does not provide a separate reduced-size tap exception. Calculate the welder supply conductor first, then satisfy the applicable Section 14 tap conditions.");
        });
    }

    private interface Calculation { String run() throws Exception; }

    private static Node worksheet(Node form, String buttonText, Calculation calculation) {
        TextArea output = new TextArea("Enter the question facts, then calculate.");
        output.setEditable(false); output.setWrapText(true); output.setPrefRowCount(21);
        output.setStyle("-fx-control-inner-background:#0f172a;-fx-text-fill:#e2e8f0;-fx-font-family:monospace;-fx-font-size:12px;");
        Button button = new Button(buttonText); button.setDefaultButton(true); button.setStyle("-fx-background-color:#1d4ed8;-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:9 14;");
        button.setOnAction(e -> { try { output.setText(calculation.run()); } catch (Exception ex) { output.setText("Check inputs: " + ex.getMessage()); } });
        VBox left = new VBox(12, form, button); HBox layout = new HBox(16, left, output); HBox.setHgrow(output, Priority.ALWAYS); left.setMinWidth(430); output.setMinWidth(420); layout.setPadding(new Insets(12));
        return layout;
    }

    private static GridPane form() { GridPane g=new GridPane();g.setHgap(12);g.setVgap(9);ColumnConstraints a=new ColumnConstraints();a.setMinWidth(230);ColumnConstraints b=new ColumnConstraints();b.setHgrow(Priority.ALWAYS);g.getColumnConstraints().addAll(a,b);return g; }
    private static void row(GridPane g,int r,String label,Node field){Label l=new Label(label);l.setWrapText(true);l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;");g.add(l,0,r);g.add(field,1,r);if(field instanceof Region region)region.setMaxWidth(Double.MAX_VALUE);}
    private static TextField tf(String value){TextField t=new TextField(value);t.setPrefColumnCount(14);return t;}
    private static CheckBox cb(String text,boolean selected){CheckBox c=new CheckBox(text);c.setSelected(selected);c.setWrapText(true);return c;}
    private static double n(TextField f){double v=Double.parseDouble(f.getText().trim());if(!Double.isFinite(v)||v<0)throw new IllegalArgumentException("Enter a valid non-negative value");return v;}
    private static List<Double> csv(String text){List<Double> out=new ArrayList<>();for(String part:text.split(",")){double v=Double.parseDouble(part.trim());if(v<=0)throw new IllegalArgumentException("Motor FLC values must be greater than zero");out.add(v);}if(out.isEmpty())throw new IllegalArgumentException("Enter at least one motor FLC");return out;}
    private static double current(double kva,double volts,int phases){if(kva<=0||volts<=0)throw new IllegalArgumentException("kVA and voltage must be greater than zero");return kva*1000.0/(volts*(phases==3?Math.sqrt(3):1));}
    private static String f(double v){return String.format("%.2f",v);}
    private static String passFail(boolean pass){return pass?"PASS":"FAIL";}
    private static String yesNo(boolean yes){return yes?"YES":"NO";}
    private static String lines(String... values){return String.join(System.lineSeparator(), Arrays.asList(values));}
    private static String starter(double a){double[] amps={20,25,35,50,65,85,100,115,130,150,175,200,230};String[] sizes={"#14","#12","#10","#8","#6","#4","#3","#2","#1","1/0","2/0","3/0","4/0"};for(int i=0;i<amps.length;i++)if(a<=amps[i])return sizes[i]+" Cu (75 C starter lookup)";return "Use the validated CEC ampacity table for this value";}
}

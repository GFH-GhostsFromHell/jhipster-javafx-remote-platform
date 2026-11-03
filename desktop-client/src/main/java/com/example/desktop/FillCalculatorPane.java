package com.example.desktop;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

import java.util.*;

/**
 * CEC-oriented conduit-fill and box-fill workspace.
 *
 * The calculator intentionally keeps the source dimensions visible/editable. Presets are a convenience;
 * the user can replace any raceway area or conductor area with the value from the adopted Code edition,
 * manufacturer data, or a field measurement where permitted.
 */
public final class FillCalculatorPane extends BorderPane {
    private static final String INPUT = "-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 9;";
    private static final String PRIMARY = "-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-padding:9 13;";
    private static final String SECONDARY = "-fx-background-color:#f8fafc;-fx-text-fill:#0f4c81;-fx-border-color:#c7d9e8;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 11;-fx-font-weight:bold;";
    private static final String CARD = "-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#dfe7ef;-fx-border-radius:14;-fx-padding:16;";

    /** CEC 2024 Table 6A-style RW90XLPE 600 V unjacketed stranded conductor areas, mm². */
    private static final LinkedHashMap<String, Double> RW90_AREA = new LinkedHashMap<>();
    /** CEC 2024 Table 22 usable box space required per insulated conductor, mL. */
    private static final LinkedHashMap<String, Double> BOX_ALLOWANCE = new LinkedHashMap<>();
    /** Common box capacities used as quick presets. The marked box/assembly volume always governs in the field. */
    private static final LinkedHashMap<String, Double> BOX_PRESETS = new LinkedHashMap<>();
    /** Raceway internal cross-sectional areas, mm², by raceway and metric trade size. */
    private static final LinkedHashMap<String, LinkedHashMap<String, Double>> RACEWAY_AREA = new LinkedHashMap<>();

    static {
        RW90_AREA.put("14 AWG", 8.87); RW90_AREA.put("12 AWG", 11.60); RW90_AREA.put("10 AWG", 15.70);
        RW90_AREA.put("8 AWG", 28.20); RW90_AREA.put("6 AWG", 38.00); RW90_AREA.put("4 AWG", 52.50);
        RW90_AREA.put("3 AWG", 62.00); RW90_AREA.put("2 AWG", 73.90); RW90_AREA.put("1 AWG", 99.10);
        RW90_AREA.put("1/0 AWG", 119.0); RW90_AREA.put("2/0 AWG", 142.0); RW90_AREA.put("3/0 AWG", 171.0);
        RW90_AREA.put("4/0 AWG", 207.0); RW90_AREA.put("250 kcmil", 252.0); RW90_AREA.put("300 kcmil", 293.0);
        RW90_AREA.put("350 kcmil", 332.0); RW90_AREA.put("400 kcmil", 373.0); RW90_AREA.put("450 kcmil", 413.0);

        BOX_ALLOWANCE.put("14 AWG", 24.6); BOX_ALLOWANCE.put("12 AWG", 28.7); BOX_ALLOWANCE.put("10 AWG", 36.9);
        BOX_ALLOWANCE.put("8 AWG", 45.1); BOX_ALLOWANCE.put("6 AWG", 73.7);

        BOX_PRESETS.put("Custom / marked volume", 0d);
        BOX_PRESETS.put("Device 3 × 2 × 1-1/2 — 131 mL", 131d);
        BOX_PRESETS.put("Device 3 × 2 × 2 — 163 mL", 163d);
        BOX_PRESETS.put("Device 3 × 2 × 2-1/2 — 204 mL", 204d);
        BOX_PRESETS.put("Device 3 × 2 × 3 — 245 mL", 245d);
        BOX_PRESETS.put("Square 4 × 1-1/2 — 344 mL", 344d);
        BOX_PRESETS.put("Square 4 × 2-1/8 — 491 mL", 491d);
        BOX_PRESETS.put("Square 4-11/16 × 2-1/8 — 688 mL", 688d);

        addRaceway("Rigid metal conduit", new double[]{202,354,573,985,1336,2199,3139,4839,6458,8311,13039,18811,Double.NaN});
        addRaceway("Flexible metal conduit", new double[]{198,334,507,792,1140,2027,3167,4560,6207,8107,Double.NaN,Double.NaN,Double.NaN});
        addRaceway("Rigid PVC conduit", new double[]{167,307,507,792,1140,2027,2951,4560,6138,7870,12439,17613,31225});
        addRaceway("Metallic liquid-tight flexible conduit", new double[]{196,341,549,965,1257,2068,3116,4776,6207,8107,Double.NaN,Double.NaN,Double.NaN});
        addRaceway("Non-metallic liquid-tight flexible conduit", new double[]{188,328,527,937,1257,2098,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN});
        addRaceway("EMT", new double[]{186,330,539,940,1288,2132,3783,5701,7451,9503,Double.NaN,Double.NaN,Double.NaN});
        addRaceway("ENT", new double[]{167,304,506,894,1230,2057,2990,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN});
        addRaceway("HDPE Schedule 40", new double[]{169,307,507,898,1233,2057,2935,4553,Double.NaN,7895,12451,18027,31303});
        addRaceway("HDPE Schedule 80", new double[]{128,246,418,760,1054,1796,2549,4002,Double.NaN,7023,11169,16029,Double.NaN});
    }

    private static void addRaceway(String name, double[] values) {
        String[] sizes = {"16 (1/2 in)","21 (3/4 in)","27 (1 in)","35 (1-1/4 in)","41 (1-1/2 in)","53 (2 in)","63 (2-1/2 in)","78 (3 in)","91 (3-1/2 in)","103 (4 in)","129 (5 in)","155 (6 in)","200 (8 in)"};
        LinkedHashMap<String,Double> map = new LinkedHashMap<>();
        for (int i=0;i<sizes.length && i<values.length;i++) if (Double.isFinite(values[i])) map.put(sizes[i], values[i]);
        RACEWAY_AREA.put(name, map);
    }

    public FillCalculatorPane() {
        VBox heading = new VBox(5);
        heading.setPadding(new Insets(0,0,12,0));
        Label title = new Label("Conduit Fill & Box Fill — Advanced");
        title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label sub = new Label("Field-friendly CEC worksheets with dropdowns, mixed conductor sizes, editable source data, pass/fail checks and worked results.");
        sub.setWrapText(true); sub.setStyle("-fx-text-fill:#64748b;");
        heading.getChildren().addAll(title,sub);
        setTop(heading);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(new Tab("Conduit Fill", scroll(buildConduitFill())), new Tab("Box Fill", scroll(buildBoxFill())));
        setCenter(tabs);
    }

    private Node buildConduitFill() {
        VBox page = new VBox(13);

        ComboBox<String> racewayType = combo(RACEWAY_AREA.keySet());
        racewayType.setValue("EMT");
        ComboBox<String> racewaySize = combo(List.of());
        TextField racewayArea = field("");
        ChoiceBox<String> covering = choice("Non-lead-sheathed conductors / cables", "Non-lead-sheathed conductors / cables", "Lead-sheathed multi-conductor cable");
        ChoiceBox<String> limitMode = choice("Automatic CEC fill limit", "Automatic CEC fill limit", "Manual fill limit");
        TextField manualLimit = field("40");
        ComboBox<String> spareWireSize = combo(RW90_AREA.keySet()); spareWireSize.setValue("12 AWG");
        TextField spareWireArea = field(fmt(RW90_AREA.get("12 AWG")));

        Runnable syncRaceway = () -> {
            LinkedHashMap<String,Double> map = RACEWAY_AREA.get(racewayType.getValue());
            String old = racewaySize.getValue();
            racewaySize.getItems().setAll(map.keySet());
            if (old != null && map.containsKey(old)) racewaySize.setValue(old);
            else if (!map.isEmpty()) racewaySize.setValue(map.keySet().iterator().next());
            Double a = map.get(racewaySize.getValue());
            racewayArea.setText(a == null ? "" : fmt(a));
        };
        racewayType.setOnAction(e -> syncRaceway.run());
        racewaySize.setOnAction(e -> {
            Double a = RACEWAY_AREA.getOrDefault(racewayType.getValue(),new LinkedHashMap<>()).get(racewaySize.getValue());
            if (a != null) racewayArea.setText(fmt(a));
        });
        spareWireSize.setOnAction(e -> spareWireArea.setText(fmt(RW90_AREA.get(spareWireSize.getValue()))));
        syncRaceway.run();

        VBox setup = card("1. Raceway", "Pick a raceway and trade size. The 100% internal area is filled automatically and remains editable for manufacturer data or a verified Code-table value.");
        setup.getChildren().add(grid(
            row("Raceway type", racewayType), row("Metric trade size", racewaySize), row("Internal area at 100% (mm²)", racewayArea),
            row("Conductor/cable covering", covering), row("Fill-limit method", limitMode), row("Manual limit (%)", manualLimit)
        ));

        VBox conductorRows = new VBox(7);
        List<ConduitRow> rows = new ArrayList<>();
        Runnable addRow = () -> {
            ConduitRow r = new ConduitRow(rows, conductorRows);
            rows.add(r); conductorRows.getChildren().add(r.node);
        };
        addRow.run(); addRow.run();
        rows.get(0).size.setValue("12 AWG"); rows.get(0).count.setText("3"); rows.get(0).syncPreset();
        rows.get(1).size.setValue("10 AWG"); rows.get(1).count.setText("1"); rows.get(1).syncPreset();

        Button add = secondary("+ Add conductor group"); add.setOnAction(e -> addRow.run());
        Button clear = secondary("Clear groups"); clear.setOnAction(e -> { conductorRows.getChildren().clear(); rows.clear(); addRow.run(); });
        VBox conductors = card("2. Conductors", "Add each conductor size as its own row. RW90 600 V unjacketed stranded presets auto-fill the area; overwrite the area for T90, TW, jacketed cable, solid conductors or manufacturer-specific dimensions.");
        conductors.getChildren().addAll(conductorRows, new HBox(8,add,clear));

        TextArea result = resultArea();
        Label status = statusLabel();
        Button calculate = primary("Calculate fill / find minimum raceway");
        Button copy = secondary("Copy result"); copy.setOnAction(e -> copy(result.getText()));
        Button reset = secondary("Reset example");
        reset.setOnAction(e -> {
            racewayType.setValue("EMT"); syncRaceway.run(); covering.setValue("Non-lead-sheathed conductors / cables"); limitMode.setValue("Automatic CEC fill limit"); manualLimit.setText("40");
            conductorRows.getChildren().clear(); rows.clear(); addRow.run(); addRow.run(); rows.get(0).size.setValue("12 AWG"); rows.get(0).count.setText("3"); rows.get(0).syncPreset(); rows.get(1).size.setValue("10 AWG"); rows.get(1).count.setText("1"); rows.get(1).syncPreset();
            result.setText("Enter the installation, then calculate."); status.setText("");
        });

        calculate.setOnAction(e -> {
            try {
                double selectedArea = positive(racewayArea,"Raceway internal area");
                int totalCount = 0; double used = 0;
                StringBuilder detail = new StringBuilder();
                for (ConduitRow r : rows) {
                    int n = nonNegativeInt(r.count,"Conductor quantity");
                    if (n == 0) continue;
                    double a = positive(r.area,"Conductor area");
                    totalCount += n; used += n*a;
                    detail.append(String.format(Locale.CANADA,"  %d × %s @ %.2f mm² = %.2f mm²%n",n,r.size.getValue(),a,n*a));
                }
                if (totalCount < 1) throw new IllegalArgumentException("Add at least one conductor.");
                if (totalCount > 200) throw new IllegalArgumentException("CEC Rule 12-910 limits a conduit/tubing run to 200 conductors.");
                double limit = limitMode.getValue().startsWith("Manual") ? positive(manualLimit,"Manual fill limit") : autoFillLimit(totalCount, covering.getValue().startsWith("Lead"));
                double allowed = selectedArea * limit/100.0;
                double actualPct = used/selectedArea*100.0;
                double remaining = allowed-used;
                boolean pass = used <= allowed + 1e-9;

                String minimum = smallestRaceway(racewayType.getValue(), used, limit);
                double extraArea = positive(spareWireArea,"Additional-conductor area");
                int additional = Math.max(0,(int)Math.floor(Math.max(0,remaining)/extraArea));

                String text = String.format(Locale.CANADA,
                    "CONDUIT FILL — %s%n%nRaceway: %s · %s%nInternal area: %.2f mm²%nTotal conductors/cables: %d%nApplied fill limit: %.1f%%%nAllowed occupied area: %.2f mm²%nActual occupied area: %.2f mm²%nActual fill: %.2f%%%nRemaining legal fill area: %.2f mm²%nStatus: %s%n%nCONDUCTOR GROUPS%n%s%nSMALLEST LISTED %s SIZE THAT PASSES%n%s%n%nSPARE-CAPACITY CHECK%nAt the selected raceway size, up to %d additional %s conductor(s) at %.2f mm² each fit before the fill limit is exceeded.%n%nNotes:%n• Preset conductor areas are for the RW90/R90XLPE-style 600 V unjacketed stranded dataset shown in the app; edit the area for other constructions.%n• Verify the final raceway/conductor dimensions against the adopted CEC edition, provincial amendments and manufacturer data.%n• Fill compliance does not check ampacity derating, voltage drop, pulling tension or bend limits.",
                    pass?"PASS":"FAIL",racewayType.getValue(),racewaySize.getValue(),selectedArea,totalCount,limit,allowed,used,actualPct,remaining,pass?"WITHIN FILL LIMIT":"OVER FILL LIMIT",detail,
                    racewayType.getValue(),minimum,additional,spareWireSize.getValue(),extraArea);
                result.setText(text); status.setText(pass?"Pass — selected raceway is within the fill limit.":"Fail — increase raceway size or reduce conductor area/count.");
            } catch (Exception ex) { status.setText("Check inputs"); result.setText(message(ex)); }
        });

        VBox results = card("3. Result & spare capacity", "Shows pass/fail, actual fill, remaining legal area, smallest listed raceway and how many additional conductors of a selected size can fit.");
        HBox spare = new HBox(9, new Label("Additional conductor:"),spareWireSize,new Label("Area mm²:"),spareWireArea); spare.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(spareWireSize,Priority.ALWAYS);
        results.getChildren().addAll(spare,new HBox(8,calculate,copy,reset),status,result);

        page.getChildren().addAll(info("CEC 2024 workflow", "Automatic fill limit: non-lead-sheathed = 53% for 1, 31% for 2, 40% for 3 or more. Lead-sheathed cable uses its own count-based limits. Maximum 200 conductors. Source dimensions remain editable so the calculator can handle other Tables 6/9 constructions."), setup, conductors, results);
        return page;
    }

    private Node buildBoxFill() {
        VBox page = new VBox(13);
        ChoiceBox<String> boxPreset = choice(BOX_PRESETS.keySet().iterator().next(), BOX_PRESETS.keySet().toArray(String[]::new));
        TextField boxVolume = field("204");
        boxPreset.setValue("Device 3 × 2 × 2-1/2 — 204 mL");
        boxPreset.setOnAction(e -> { Double v=BOX_PRESETS.get(boxPreset.getValue()); if(v!=null&&v>0) boxVolume.setText(fmt(v)); });

        VBox box = card("1. Box / assembly volume", "Choose a common preset or enter the marked usable volume. For ganged boxes, extension rings or raised covers, use the marked total assembled volume where applicable.");
        box.getChildren().add(grid(row("Box preset",boxPreset),row("Usable/marked volume (mL)",boxVolume)));

        VBox conductorRows = new VBox(7); List<BoxConductorRow> conductors = new ArrayList<>();
        Runnable addConductor = () -> { BoxConductorRow r=new BoxConductorRow(conductors,conductorRows);conductors.add(r);conductorRows.getChildren().add(r.node); };
        addConductor.run(); conductors.get(0).size.setValue("12 AWG"); conductors.get(0).count.setText("4"); conductors.get(0).syncPreset();
        Button addCond = secondary("+ Add conductor size"); addCond.setOnAction(e->addConductor.run());
        VBox ccard = card("2. Insulated conductors", "Each insulated conductor running through the box or entering/leaving and terminating/splicing is entered by size. Conductors that originate and terminate entirely inside the box (typical pigtails) are not entered.");
        ccard.getChildren().addAll(conductorRows,addCond);

        VBox deviceRows = new VBox(7); List<BoxDeviceRow> devices = new ArrayList<>();
        Runnable addDevice = () -> { BoxDeviceRow r=new BoxDeviceRow(devices,deviceRows);devices.add(r);deviceRows.getChildren().add(r.node); };
        addDevice.run(); devices.get(0).size.setValue("12 AWG"); devices.get(0).count.setText("1");
        Button addDev=secondary("+ Add device group");addDev.setOnAction(e->addDevice.run());
        VBox dcard = card("3. Flush devices", "Each flush-mounted device is treated as two conductor allowances based on the largest conductor connected to that device. Group devices by connected conductor size.");
        dcard.getChildren().addAll(deviceRows,addDev);

        TextField connectors=field("3"); ChoiceBox<String> connectorSize=choice("12 AWG", BOX_ALLOWANCE.keySet().toArray(String[]::new));
        TextField studs=field("0"); ChoiceBox<String> studSize=choice("12 AWG", BOX_ALLOWANCE.keySet().toArray(String[]::new));
        TextField deepDevices=field("0"), deepDepth=field("0");
        VBox extras=card("4. Connectors, studs & deep devices", "Connector deduction is one allowance for each pair of insulating-cap conductor connectors, based on the largest conductor included with each pair. Fixture stud/hickey deductions use the selected conductor size. Deep-device displacement is available for devices deeper than 2.54 cm from strap to back.");
        extras.getChildren().add(grid(row("Insulating-cap wire connectors (count)",connectors),row("Largest conductor with connector pairs",connectorSize),row("Fixture studs / hickeys",studs),row("Allowance size for studs/hickeys",studSize),row("Deep devices >2.54 cm (count)",deepDevices),row("Device depth, strap to back (cm)",deepDepth)));

        TextArea result=resultArea();Label status=statusLabel();Button calc=primary("Calculate box fill");Button copy=secondary("Copy result");copy.setOnAction(e->copy(result.getText()));Button reset=secondary("Reset example");
        reset.setOnAction(e->{boxPreset.setValue("Device 3 × 2 × 2-1/2 — 204 mL");boxVolume.setText("204");conductorRows.getChildren().clear();conductors.clear();addConductor.run();conductors.get(0).size.setValue("12 AWG");conductors.get(0).count.setText("4");conductors.get(0).syncPreset();deviceRows.getChildren().clear();devices.clear();addDevice.run();devices.get(0).size.setValue("12 AWG");devices.get(0).count.setText("1");connectors.setText("3");connectorSize.setValue("12 AWG");studs.setText("0");studSize.setValue("12 AWG");deepDevices.setText("0");deepDepth.setText("0");result.setText("Enter the box contents, then calculate.");status.setText("");});

        calc.setOnAction(e->{
            try{
                double capacity=positive(boxVolume,"Box volume"), conductorVol=0,deviceVol=0;int insulated=0,deviceCount=0;
                StringBuilder cdetail=new StringBuilder(),ddetail=new StringBuilder();
                for(BoxConductorRow r:conductors){int n=nonNegativeInt(r.count,"Conductor quantity");if(n==0)continue;double a=positive(r.allowance,"Conductor allowance");conductorVol+=n*a;insulated+=n;cdetail.append(String.format(Locale.CANADA,"  %d × %s @ %.1f mL = %.1f mL%n",n,r.size.getValue(),a,n*a));}
                for(BoxDeviceRow r:devices){int n=nonNegativeInt(r.count,"Device quantity");if(n==0)continue;Double a=BOX_ALLOWANCE.get(r.size.getValue());if(a==null)throw new IllegalArgumentException("Select a conductor size for every device group.");double v=n*2*a;deviceVol+=v;deviceCount+=n;ddetail.append(String.format(Locale.CANADA,"  %d × device(s) on %s = %d allowances × %.1f mL = %.1f mL%n",n,r.size.getValue(),n*2,a,v));}
                int connectorCount=nonNegativeInt(connectors,"Connector count");int connectorPairs=connectorCount/2;double connectorVol=connectorPairs*BOX_ALLOWANCE.get(connectorSize.getValue());
                int studCount=nonNegativeInt(studs,"Stud/hickey count");double studVol=studCount*BOX_ALLOWANCE.get(studSize.getValue());
                int deepCount=nonNegativeInt(deepDevices,"Deep-device count");double depth=nonNegative(deepDepth,"Deep-device depth");double deepDisplacement=deepCount==0?0:deepCount*32.0*depth;
                double required=conductorVol+deviceVol+connectorVol+studVol+deepDisplacement,remaining=capacity-required,util=required/capacity*100;boolean pass=required<=capacity+1e-9;
                String recommended=smallestBox(required);
                String text=String.format(Locale.CANADA,
                    "BOX FILL — %s%n%nBox/assembly: %s%nUsable volume: %.1f mL%nRequired volume: %.1f mL%nRemaining volume: %.1f mL%nVolume used: %.1f%%%nStatus: %s%n%nINSULATED CONDUCTORS (%d)%n%sSubtotal: %.1f mL%n%nFLUSH DEVICES (%d)%n%sSubtotal: %.1f mL%n%nOTHER DEDUCTIONS%n  Wire connectors: %d connector(s) → %d pair allowance(s) at %s = %.1f mL%n  Fixture studs/hickeys: %d at %s = %.1f mL%n  Deep-device displacement: %d × 32 cm³/cm × %.2f cm = %.1f mL%n%nSMALLEST LISTED PRESET BY VOLUME%n%s%n%nCEC counting reminders:%n• Add each insulated conductor that passes through or enters/leaves and terminates/splices.%n• Do not add a pigtail that starts and ends entirely inside the box.%n• Bare bonding conductors are not entered as insulated conductors; an insulated bonding conductor is entered normally.%n• Locknuts, bushings, box connectors and clamps do not consume a separate Table-22 volume allowance in this volume worksheet.%n• Verify the actual marked box/assembly volume and adopted CEC requirements before installation.",
                    pass?"PASS":"FAIL",boxPreset.getValue(),capacity,required,remaining,util,pass?"WITHIN USABLE VOLUME":"BOX TOO SMALL",insulated,cdetail,conductorVol,deviceCount,ddetail,deviceVol,connectorCount,connectorPairs,connectorSize.getValue(),connectorVol,studCount,studSize.getValue(),studVol,deepCount,depth,deepDisplacement,recommended);
                result.setText(text);status.setText(pass?"Pass — box has sufficient calculated usable volume.":"Fail — select a larger box/assembly or reduce contents.");
            }catch(Exception ex){status.setText("Check inputs");result.setText(message(ex));}
        });

        VBox results=card("5. Result", "Breaks the fill down by conductor size and item type, shows remaining mL and suggests the smallest listed preset volume that meets the calculation.");
        results.getChildren().addAll(new HBox(8,calc,copy,reset),status,result);

        page.getChildren().addAll(info("Canadian box-fill method", "CEC Rule 12-3034 uses insulated-conductor counting plus the Table 22 volume allowance for mixed sizes/non-standard boxes. The calculator keeps the allowance visible per conductor row so a future Code revision can be handled without redesigning the workflow."),box,ccard,dcard,extras,results);
        return page;
    }

    private final class ConduitRow {
        final HBox node = new HBox(8); final ComboBox<String> size=comboWithCustom(RW90_AREA.keySet()); final TextField count=field("1"), area=field("");
        ConduitRow(List<ConduitRow> rows,VBox owner){size.setValue("12 AWG");syncPreset();size.setOnAction(e->syncPreset());Button remove=secondary("Remove");remove.setOnAction(e->{rows.remove(this);owner.getChildren().remove(node);});node.setAlignment(Pos.CENTER_LEFT);Label q=new Label("Qty");Label a=new Label("Area mm²");node.getChildren().addAll(size,q,count,a,area,remove);HBox.setHgrow(size,Priority.ALWAYS);size.setPrefWidth(190);count.setPrefWidth(70);area.setPrefWidth(100);}
        void syncPreset(){Double v=RW90_AREA.get(size.getValue());if(v!=null)area.setText(fmt(v));else if("Custom area".equals(size.getValue()))area.clear();}
    }

    private final class BoxConductorRow {
        final HBox node=new HBox(8);final ChoiceBox<String> size=choice("12 AWG",BOX_ALLOWANCE.keySet().toArray(String[]::new));final TextField count=field("1"),allowance=field("28.7");
        BoxConductorRow(List<BoxConductorRow> rows,VBox owner){size.setOnAction(e->syncPreset());Button remove=secondary("Remove");remove.setOnAction(e->{rows.remove(this);owner.getChildren().remove(node);});node.setAlignment(Pos.CENTER_LEFT);node.getChildren().addAll(size,new Label("Qty"),count,new Label("mL each"),allowance,remove);HBox.setHgrow(size,Priority.ALWAYS);size.setPrefWidth(150);count.setPrefWidth(70);allowance.setPrefWidth(90);}
        void syncPreset(){Double v=BOX_ALLOWANCE.get(size.getValue());if(v!=null)allowance.setText(fmt(v));}
    }

    private final class BoxDeviceRow {
        final HBox node=new HBox(8);final ChoiceBox<String> size=choice("12 AWG",BOX_ALLOWANCE.keySet().toArray(String[]::new));final TextField count=field("1");
        BoxDeviceRow(List<BoxDeviceRow> rows,VBox owner){Button remove=secondary("Remove");remove.setOnAction(e->{rows.remove(this);owner.getChildren().remove(node);});node.setAlignment(Pos.CENTER_LEFT);node.getChildren().addAll(new Label("Largest connected conductor"),size,new Label("Devices"),count,remove);HBox.setHgrow(size,Priority.ALWAYS);count.setPrefWidth(70);}
    }

    private double autoFillLimit(int count, boolean lead) {
        if (!lead) return count==1?53:count==2?31:40;
        if (count==1) return 55; if(count==2)return 30;if(count==3)return 40;if(count==4)return 38;return 35;
    }

    private String smallestRaceway(String type,double used,double limit) {
        LinkedHashMap<String,Double> map=RACEWAY_AREA.get(type);if(map==null||map.isEmpty())return "No preset data for this raceway type.";
        for(var e:map.entrySet())if(e.getValue()*limit/100.0>=used-1e-9)return e.getKey()+String.format(Locale.CANADA," (%.0f mm² internal; %.1f mm² allowed at %.1f%%)",e.getValue(),e.getValue()*limit/100.0,limit);
        return "Above the largest preset available — enter a larger verified raceway area manually.";
    }

    private String smallestBox(double required){
        return BOX_PRESETS.entrySet().stream().filter(e->e.getValue()>0&&e.getValue()>=required-1e-9).min(Comparator.comparingDouble(Map.Entry::getValue)).map(e->e.getKey()+String.format(Locale.CANADA," (%.0f mL)",e.getValue())).orElse("Above the largest listed preset — use the marked volume of a larger box/assembly.");
    }

    private VBox card(String title,String desc){VBox b=new VBox(10);b.setPadding(new Insets(15));b.setStyle(CARD);Label h=new Label(title);h.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");Label d=new Label(desc);d.setWrapText(true);d.setStyle("-fx-text-fill:#64748b;");b.getChildren().addAll(h,d);return b;}
    private Node info(String title,String text){VBox b=new VBox(5);b.setPadding(new Insets(11));b.setStyle("-fx-background-color:#ecfeff;-fx-border-color:#a5f3fc;-fx-border-radius:10;-fx-background-radius:10;");Label h=new Label(title);h.setStyle("-fx-font-weight:bold;-fx-text-fill:#155e75;");Label d=new Label(text);d.setWrapText(true);d.setStyle("-fx-text-fill:#155e75;");b.getChildren().addAll(h,d);return b;}
    private GridPane grid(Node... rows){GridPane g=new GridPane();g.setVgap(8);int i=0;for(Node n:rows){g.add(n,0,i++);GridPane.setHgrow(n,Priority.ALWAYS);}return g;}
    private HBox row(String label,Node control){Label l=new Label(label);l.setMinWidth(250);l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;");HBox h=new HBox(10,l,control);h.setAlignment(Pos.CENTER_LEFT);HBox.setHgrow(control,Priority.ALWAYS);if(control instanceof Region r)r.setMaxWidth(Double.MAX_VALUE);return h;}
    private ScrollPane scroll(Node n){ScrollPane s=new ScrollPane(n);s.setFitToWidth(true);s.setStyle("-fx-background:transparent;-fx-background-color:transparent;");return s;}
    private TextField field(String value){TextField f=new TextField(value);f.setStyle(INPUT);return f;}
    private <T> ChoiceBox<T> choice(T selected,@SuppressWarnings("unchecked") T... items){ChoiceBox<T> c=new ChoiceBox<>();c.getItems().addAll(items);c.setValue(selected);c.setMaxWidth(Double.MAX_VALUE);return c;}
    private <T> ComboBox<T> combo(Collection<T> items){ComboBox<T> c=new ComboBox<>();c.getItems().addAll(items);c.setMaxWidth(Double.MAX_VALUE);return c;}
    private ComboBox<String> comboWithCustom(Collection<String> items){ComboBox<String> c=combo(items);c.getItems().add("Custom area");return c;}
    private Button primary(String text){Button b=new Button(text);b.setStyle(PRIMARY);return b;}
    private Button secondary(String text){Button b=new Button(text);b.setStyle(SECONDARY);return b;}
    private TextArea resultArea(){TextArea a=new TextArea("Enter the installation, then calculate.");a.setEditable(false);a.setWrapText(false);a.setPrefRowCount(22);a.setStyle("-fx-font-family:Consolas;-fx-font-size:12px;-fx-control-inner-background:#0f172a;-fx-text-fill:#e2e8f0;");return a;}
    private Label statusLabel(){Label l=new Label();l.setWrapText(true);l.setStyle("-fx-text-fill:#475569;-fx-font-weight:bold;");return l;}
    private void copy(String text){ClipboardContent c=new ClipboardContent();c.putString(text==null?"":text);Clipboard.getSystemClipboard().setContent(c);}
    private double positive(TextField f,String name){double v=parse(f,name);if(v<=0)throw new IllegalArgumentException(name+" must be greater than zero.");return v;}
    private double nonNegative(TextField f,String name){double v=parse(f,name);if(v<0)throw new IllegalArgumentException(name+" cannot be negative.");return v;}
    private int nonNegativeInt(TextField f,String name){double v=nonNegative(f,name);int n=(int)Math.round(v);if(Math.abs(v-n)>1e-9)throw new IllegalArgumentException(name+" must be a whole number.");return n;}
    private double parse(TextField f,String name){try{String s=f.getText().trim();if(s.isEmpty())throw new NumberFormatException();double v=Double.parseDouble(s);if(!Double.isFinite(v))throw new NumberFormatException();return v;}catch(Exception ex){throw new IllegalArgumentException(name+" is blank or invalid.");}}
    private static String fmt(Double v){if(v==null)return "";return Math.abs(v-Math.rint(v))<1e-9?String.format(Locale.CANADA,"%.0f",v):String.format(Locale.CANADA,"%.2f",v);}
    private static String message(Exception ex){return ex.getMessage()==null?ex.toString():ex.getMessage();}
}

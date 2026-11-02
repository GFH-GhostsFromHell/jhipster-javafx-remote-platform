package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Full native JavaFX electrician workspace organized by CEC sections.
 * The cloud remains an API/data engine only; no core calculator redirects to web UI.
 */
public final class SectionedCalculatorPane extends BorderPane {
    private final PortalClient portal;
    private final ObjectMapper json;
    private final String username;
    private final String password;
    private final StackPane content = new StackPane();

    private static final String INPUT = "-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-border-radius:9;-fx-background-radius:9;-fx-padding:9 10;";
    private static final String PRIMARY = "-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;-fx-padding:10 14;";
    private static final String CARD = "-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#dfe7ef;-fx-border-radius:14;-fx-padding:16;";
    private static final String NAV = "-fx-background-color:transparent;-fx-text-fill:#334155;-fx-font-weight:bold;-fx-alignment:center-left;-fx-padding:10 12;-fx-background-radius:9;";

    private static final Map<String, Double> CIRCULAR_MILS = new LinkedHashMap<>();
    static {
        CIRCULAR_MILS.put("14 AWG", 4110d); CIRCULAR_MILS.put("12 AWG", 6530d); CIRCULAR_MILS.put("10 AWG", 10380d);
        CIRCULAR_MILS.put("8 AWG", 16510d); CIRCULAR_MILS.put("6 AWG", 26240d); CIRCULAR_MILS.put("4 AWG", 41740d);
        CIRCULAR_MILS.put("3 AWG", 52620d); CIRCULAR_MILS.put("2 AWG", 66360d); CIRCULAR_MILS.put("1 AWG", 83690d);
        CIRCULAR_MILS.put("1/0 AWG", 105600d); CIRCULAR_MILS.put("2/0 AWG", 133100d); CIRCULAR_MILS.put("3/0 AWG", 167800d);
        CIRCULAR_MILS.put("4/0 AWG", 211600d); CIRCULAR_MILS.put("250 kcmil", 250000d); CIRCULAR_MILS.put("300 kcmil", 300000d);
        CIRCULAR_MILS.put("350 kcmil", 350000d); CIRCULAR_MILS.put("400 kcmil", 400000d); CIRCULAR_MILS.put("500 kcmil", 500000d);
        CIRCULAR_MILS.put("600 kcmil", 600000d); CIRCULAR_MILS.put("750 kcmil", 750000d); CIRCULAR_MILS.put("1000 kcmil", 1000000d);
    }

    public SectionedCalculatorPane(PortalClient portal, ObjectMapper json, String username, String password) {
        this.portal = portal;
        this.json = json;
        this.username = username;
        this.password = password;

        VBox heading = new VBox(4);
        heading.setPadding(new Insets(0, 0, 14, 0));
        Label title = new Label("CEC 2024 Electrical Workbench");
        title.setStyle("-fx-font-size:25px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label sub = new Label("Full native JavaFX workspace organized by Code section. Calculators stay inside the desktop app.");
        sub.setWrapText(true); sub.setStyle("-fx-text-fill:#64748b;");
        heading.getChildren().addAll(title, sub);
        setTop(heading);

        VBox rail = new VBox(5);
        rail.setPadding(new Insets(12));
        rail.setPrefWidth(235);
        rail.setStyle("-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#dfe7ef;-fx-border-radius:14;");
        Label railTitle = new Label("CEC SECTIONS");
        railTitle.setStyle("-fx-text-fill:#0e7490;-fx-font-size:10px;-fx-font-weight:bold;-fx-padding:0 5 7 5;");
        rail.getChildren().add(railTitle);

        addSectionButton(rail, "Section 2 — General Rules", this::section2);
        addSectionButton(rail, "Section 4 — Conductors / Busbars", this::section4);
        addSectionButton(rail, "Section 8 — Service & Demand", this::section8);
        addSectionButton(rail, "Section 12 — Wiring Methods", this::section12);
        addSectionButton(rail, "Section 14 — Protection / Taps", this::section14);
        addSectionButton(rail, "Section 16 — Class 1 & Class 2", this::section16);
        addSectionButton(rail, "Section 24 — Patient Care", this::section24);
        addSectionButton(rail, "Section 26 — Transformers / Capacitors", this::section26);
        addSectionButton(rail, "Section 28 — Motors / Refrigeration", this::section28);
        addSectionButton(rail, "Section 32 — Fire Alarm / Fire Pumps", this::section32);
        addSectionButton(rail, "Section 42 — Welders", this::section42);
        addSectionButton(rail, "ADV — Advanced Calculations", AdvancedCalculationViews::create);

        ScrollPane railScroll = new ScrollPane(rail);
        railScroll.setFitToWidth(true);
        railScroll.setPrefWidth(255);
        railScroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        setLeft(railScroll);
        BorderPane.setMargin(railScroll, new Insets(0, 14, 0, 0));

        content.setAlignment(Pos.TOP_LEFT);
        setCenter(content);
        show(section2());
    }

    private void addSectionButton(VBox rail, String label, Supplier<Node> supplier) {
        Button b = new Button(label); b.setMaxWidth(Double.MAX_VALUE); b.setStyle(NAV);
        b.setOnAction(e -> show(supplier.get()));
        rail.getChildren().add(b);
    }

    private void show(Node node) { content.getChildren().setAll(node); }

    private Node section2() {
        VBox page = sectionHeader("Section 2 — General Rules", "Quick exam/field reminders; summaries only, not reproduced Code text.");
        TilePane refs = new TilePane(); refs.setHgap(10); refs.setVgap(10); refs.setPrefColumns(2);
        refs.getChildren().addAll(
            refCard("Approvals / markings", "Check approval, equipment markings, ratings and intended use before applying any calculator."),
            refCard("Working space / access", "Confirm the working-space, access and disconnecting requirements for the actual equipment and location."),
            refCard("Environmental suitability", "Dry, damp, wet, corrosive and hazardous locations affect wiring method, fittings and enclosure selection."),
            refCard("Code hierarchy", "General sections apply broadly; supplementary sections can add to or amend the general rules."),
            refCard("Units / definitions", "Confirm line vs phase voltage, AC/DC, ampacity and whether the problem gives FLC, FLA, RLA or nameplate MCA/MOCP."),
            refCard("Verification", "Use calculator results as a worksheet, then verify the final design against CEC 2024 and equipment instructions.")
        );
        page.getChildren().add(refs);
        return scroll(page);
    }

    private Node section4() {
        return sectionTabs("Section 4 — Conductors / Busbars", "Busbar sizing, reverse sizing, parallel straps/bars and current density.",
            tab("Dimensions → Ampacity", busbarDimensions()), tab("Current → Bar Size", busbarSize()),
            tab("Parallel Straps / Bars", busbarParallel()), tab("Current Density", busbarDensity()));
    }

    private Node busbarDimensions() {
        ChoiceBox<String> material = choice("Copper", "Copper", "Aluminum");
        TextField width = field("50"), thick = field("6");
        return localForm("Busbar dimensions → worksheet ampacity", "Rule 4-036 study method used in the earlier workspace: area = width × thickness; copper 1000 A/in², aluminum 700 A/in²; 645 mm² ≈ 1 in².",
            "Calculate busbar ampacity", () -> {
                double k = material.getValue().equals("Aluminum") ? 700 : 1000;
                double area = number(width) * number(thick);
                double raw = area / 645.0 * k;
                double used = number(thick) <= 6.35 ? Math.min(raw, 1200) : raw;
                return String.format("Material: %s%nArea: %.2f mm²%nWorksheet ampacity: %.1f A%n%s", material.getValue(), area, used,
                    number(thick) <= 6.35 && raw > 1200 ? "1200 A practice cap applied to the thin-bar shortcut." : "No thin-bar cap changed the result.");
            }, row("Material", material), row("Width (mm)", width), row("Thickness (mm)", thick));
    }

    private Node busbarSize() {
        ChoiceBox<String> material = choice("Copper", "Copper", "Aluminum");
        TextField amps = field("1200"), thick = field("6"), width = field("100");
        return localForm("Current → required busbar size", "Calculates required cross-sectional area, then required width for the entered thickness and required thickness for the entered width.",
            "Calculate required size", () -> {
                double k = material.getValue().equals("Aluminum") ? 700 : 1000;
                double area = number(amps) / k * 645.0;
                return String.format("Required area: %.2f mm²%nWidth at %.2f mm thickness: %.2f mm%nThickness at %.2f mm width: %.2f mm",
                    area, number(thick), area / number(thick), number(width), area / number(width));
            }, row("Material", material), row("Target current (A)", amps), row("Known thickness (mm)", thick), row("Known width (mm)", width));
    }

    private Node busbarParallel() {
        ChoiceBox<String> material = choice("Copper", "Copper", "Aluminum");
        TextField total = field("3000"), width = field("100"), thick = field("6");
        return localForm("Parallel straps / bars", "Calculates the worksheet ampacity of one bar and rounds the required number of parallel bars up.",
            "Calculate number of straps / bars", () -> {
                double k = material.getValue().equals("Aluminum") ? 700 : 1000;
                double raw = number(width) * number(thick) / 645.0 * k;
                double one = number(thick) <= 6.35 ? Math.min(raw, 1200) : raw;
                int count = (int)Math.ceil(number(total) / one);
                return String.format("One-bar worksheet ampacity: %.1f A%nRequired total: %.1f A%nMinimum parallel bars/straps: %d%nCombined worksheet ampacity: %.1f A",
                    one, number(total), count, count * one);
            }, row("Material", material), row("Total required current (A)", total), row("Each bar width (mm)", width), row("Each bar thickness (mm)", thick));
    }

    private Node busbarDensity() {
        TextField amps = field("1200"), density = field("1.2"), thick = field("10");
        return localForm("Editable current-density method", "Generic study/engineering method: J = I/A and A = I/J. Kept separate from the CEC busbar worksheet shortcut.",
            "Calculate by current density", () -> {
                double area = number(amps) / number(density);
                return String.format("Required area: %.2f mm²%nRequired width at %.2f mm thickness: %.2f mm%nCurrent density used: %.3f A/mm²",
                    area, number(thick), area / number(thick), number(density));
            }, row("Target current (A)", amps), row("Current density (A/mm²)", density), row("Selected thickness (mm)", thick));
    }

    private Node section8() {
        return sectionTabs("Section 8 — Circuit Loading & Demand", "Occupancy-specific service calculations restored from the earlier workspace.",
            tab("Residential", residential()), tab("Apartment", apartment()), tab("School", school()), tab("Hospital", hospital()), tab("Other / Hotel", otherService()));
    }

    private Node residential() {
        TextField ground=field("150"), upper=field("100"), basement=field("100"), range=field("12"), roomHeat=field("25"), centralHeat=field("0"), ac=field("7"), other=field("8.5"), full=field("0"), ev=field("0"), voltage=field("240");
        CheckBox interlocked = new CheckBox("Heating and A/C interlocked"); interlocked.setSelected(true);
        return apiForm("Residential dwelling — Rule 8-200", "Dwelling service worksheet including living area, range, heating/A-C, other loads and minimum service load.",
            "/service/residential", "Calculate residential", () -> payload(
                "groundArea",number(ground),"upperArea",number(upper),"basementArea",number(basement),"rangeKw",number(range),
                "roomHeatKw",number(roomHeat),"centralHeatKw",number(centralHeat),"acKw",number(ac),"interlocked",interlocked.isSelected(),
                "otherOver1500Kw",number(other),"dedicated100Kw",number(full),"evseKw",number(ev),"voltage",number(voltage)),
            row("Ground floor area (m²)",ground),row("Upper floor area (m²)",upper),row("Qualifying basement area (m²)",basement),row("Range (kW)",range),
            row("Room/baseboard heat (kW)",roomHeat),row("Central electric heat (kW)",centralHeat),row("A/C or heat pump (kW)",ac),row("Other loads >1500 W total (kW)",other),
            row("100% special loads (kW)",full),row("EVSE (kW)",ev),row("Service voltage",voltage),row("HVAC",interlocked));
    }

    private Node apartment() {
        TextField suites=field("12"), area=field("90"), range=field("12"), other=field("0"), heat=field("0"), ac=field("0"), house=field("18"), df=field("0.75"), cf=field("1.25"), voltage=field("240");
        ChoiceBox<Integer> phases=phaseChoice(1); CheckBox interlocked=new CheckBox("Suite heat/A-C interlocked"); interlocked.setSelected(true);
        return apiForm("Apartment / multi-suite — Rule 8-202", "Multi-suite service worksheet with suite diversity and separate house/common load treatment.",
            "/service/apartment", "Calculate apartment service", () -> payload(
                "suites",integer(suites),"suiteArea",number(area),"rangeKw",number(range),"suiteOtherKw",number(other),"suiteHeatKw",number(heat),"suiteAcKw",number(ac),
                "suiteInterlocked",interlocked.isSelected(),"houseLoadKw",number(house),"houseDemandFactor",number(df),"houseContinuousFactor",number(cf),"voltage",number(voltage),"phases",phases.getValue()),
            row("Suites",suites),row("Suite area each (m²)",area),row("Range per suite (kW)",range),row("Other suite load each (kW)",other),row("Heat per suite (kW)",heat),row("A/C per suite (kW)",ac),
            row("House/common load (kW)",house),row("House demand factor",df),row("Continuous multiplier",cf),row("Service voltage",voltage),row("Phases",phases),row("HVAC",interlocked));
    }

    private Node school() {
        TextField area=field("900"), classroom=field("550"), other=field("40"), heat=field("0"), ac=field("0"), ev=field("0"), cord=field("0"), voltage=field("600");
        ChoiceBox<Integer> phases=phaseChoice(3); CheckBox interlocked=new CheckBox("Heat/A-C interlocked"); interlocked.setSelected(true);
        return apiForm("School — Rule 8-204", "School service calculation including classroom area, other loads, heating/A-C, EVSE and high-power cord loads.",
            "/service/school", "Calculate school service", () -> payload(
                "totalArea",number(area),"classroomArea",number(classroom),"otherKw",number(other),"heatKw",number(heat),"acKw",number(ac),"interlocked",interlocked.isSelected(),
                "evseKw",number(ev),"highPowerCordKw",number(cord),"voltage",number(voltage),"phases",phases.getValue()),
            row("Total area (m²)",area),row("Classroom area (m²)",classroom),row("Other connected load (kW)",other),row("Electric heat (kW)",heat),row("A/C (kW)",ac),row("EVSE (kW)",ev),
            row("High-power cord load (kW)",cord),row("Voltage",voltage),row("Phases",phases),row("HVAC",interlocked));
    }

    private Node hospital() {
        TextField area=field("1300"), high=field("275"), other=field("85"), heat=field("110"), ac=field("0"), ev=field("0"), cord=field("0"), voltage=field("600");
        ChoiceBox<Integer> phases=phaseChoice(3); CheckBox interlocked=new CheckBox("Heat/A-C interlocked"); interlocked.setSelected(true);
        return apiForm("Hospital — Rule 8-206", "Hospital service worksheet with high-intensity area and occupancy-specific demand treatment.",
            "/service/hospital", "Calculate hospital service", () -> payload(
                "totalArea",number(area),"highIntensityArea",number(high),"otherKw",number(other),"heatKw",number(heat),"acKw",number(ac),"interlocked",interlocked.isSelected(),
                "evseKw",number(ev),"highPowerCordKw",number(cord),"voltage",number(voltage),"phases",phases.getValue()),
            row("Total hospital area (m²)",area),row("High-intensity area (m²)",high),row("Other connected loads (kW)",other),row("Electric heat (kW)",heat),row("A/C (kW)",ac),row("EVSE (kW)",ev),
            row("High-power cord load (kW)",cord),row("Voltage",voltage),row("Phases",phases),row("HVAC",interlocked));
    }

    private Node otherService() {
        TextField occupancy=field("Hotel / commercial"), area=field("775"), wpm=field("20"), df=field("0.8"), special=field("40"), heat=field("75"), ac=field("50"), motor=field("0"), ev=field("0"), voltage=field("208");
        ChoiceBox<Integer> phases=phaseChoice(3); CheckBox interlocked=new CheckBox("Heat/A-C interlocked"); interlocked.setSelected(true);
        return apiForm("Other occupancy — Rule 8-210 / Table 14", "Use for hotel/commercial/other occupancies by entering the applicable W/m² and demand factor.",
            "/service/other", "Calculate other occupancy", () -> payload(
                "occupancy",occupancy.getText(),"area",number(area),"wattsPerM2",number(wpm),"tableDemand",number(df),"specialKw",number(special),"heatKw",number(heat),"acKw",number(ac),
                "interlocked",interlocked.isSelected(),"motorKw",number(motor),"evseKw",number(ev),"voltage",number(voltage),"phases",phases.getValue()),
            row("Occupancy",occupancy),row("Area (m²)",area),row("Table 14 W/m²",wpm),row("Table demand factor",df),row("Special load (kW)",special),row("Heat (kW)",heat),row("A/C (kW)",ac),
            row("Motor / other special (kW)",motor),row("EVSE (kW)",ev),row("Voltage",voltage),row("Phases",phases),row("HVAC",interlocked));
    }

    private Node section12() {
        return sectionTabs("Section 12 — Wiring Methods", "Conduit fill, box fill, bending, supports, voltage drop and PVC expansion.",
            tab("Conduit Fill", conduitFill()), tab("Box Fill", boxFill()), tab("Conduit Bending", bending()), tab("Supports & Straps", supports()),
            tab("Voltage Drop / D3", voltageDrop()), tab("PVC Expansion", pvcExpansion()));
    }

    private Node conduitFill() {
        TextField area=field("530"), pct=field("40"), cond=field("35,21"), counts=field("6,4");
        return apiForm("Conduit fill", "Mixed conductor groups and fill percentage using the areas/counts you enter.", "/conduit-fill", "Calculate conduit fill",
            () -> payload("racewayArea",number(area),"conductorAreas",csvD(cond.getText()),"counts",csvI(counts.getText()),"maxFillPercent",number(pct),"edition","CEC_2024"),
            row("Raceway usable/internal area (mm²)",area),row("Maximum fill (%)",pct),row("Conductor areas mm² (comma-separated)",cond),row("Matching counts",counts));
    }

    private Node boxFill() {
        TextField vol=field("40"), allow=field("2.25"), conductors=field("8"), devices=field("2"), bonds=field("1");
        return apiForm("Box fill", "Conductors, devices and bonding/clamp equivalents.", "/box-fill", "Calculate box fill",
            () -> payload("boxVolume",number(vol),"conductorAllowance",number(allow),"insulatedConductors",integer(conductors),"deviceEquivalents",integer(devices),"bondClampEquivalents",integer(bonds),"edition","CEC_2024"),
            row("Box volume",vol),row("Allowance per conductor",allow),row("Insulated conductors",conductors),row("Device equivalents",devices),row("Bond/clamp equivalents",bonds));
    }

    private Node bending() {
        TextField rise=field("150"), angle=field("30"), vertical=field("150"), horizontal=field("100"), stub=field("500"), takeup=field("130");
        return localForm("Conduit bending", "Offset, rolling-offset and 90° stub calculations using the same geometry as the earlier workspace.", "Calculate bends", () -> {
            double deg=number(angle), rad=Math.toRadians(deg), mult=1/Math.sin(rad), r=number(rise), spacing=r*mult, shrink=r*Math.tan(rad/2);
            double trueOffset=Math.hypot(number(vertical),number(horizontal));
            double rollSpacing=trueOffset*mult, rollShrink=trueOffset*Math.tan(rad/2), stubMark=number(stub)-number(takeup);
            return String.format("OFFSET%nMultiplier: %.3f%nMark spacing: %.1f mm%nShrink: %.1f mm%n%nROLLING OFFSET%nTrue offset: %.1f mm%nMark spacing: %.1f mm%nShrink: %.1f mm%n%n90° STUB%nMark from end: %.1f mm",
                mult,spacing,shrink,trueOffset,rollSpacing,rollShrink,stubMark);
        }, row("Offset/rise (mm)",rise),row("Bend angle (degrees)",angle),row("Rolling vertical (mm)",vertical),row("Rolling horizontal (mm)",horizontal),row("Desired 90° stub (mm)",stub),row("Bender take-up (mm)",takeup));
    }

    private Node supports() {
        ChoiceBox<String> type=choice("NMD90 / non-metallic sheathed cable","NMD90 / non-metallic sheathed cable","BX / AC90 armoured cable","Rigid metal conduit","EMT","Rigid PVC conduit","Flexible metal conduit","Liquid-tight flexible conduit","ENT");
        TextField size=field("21"), run=field("10");
        return localForm("Supports & straps", "Restores the wiring-method support/strap worksheet from the earlier app.", "Calculate supports / straps", () -> {
            double[] r=supportRule(type.getValue(),integer(size)); double spacing=r[0], near=r[1], L=number(run);
            int count=near<=0?Math.max(1,(int)Math.ceil(L/spacing)):(L<=2*near?1:2+Math.max(0,(int)Math.ceil((L-2*near)/spacing)-1));
            return String.format("Wiring method: %s%nTrade size: %d mm%nMaximum support spacing: %.2f m%nNear box/fitting: %s%nMinimum straight-run supports/straps: %d%n%nVerify the listed CEC rule and actual installation details.",
                type.getValue(),integer(size),spacing,near>0?String.format("within %.2f m",near):"see raceway-specific rule",count);
        }, row("Wiring method",type),row("Trade size (mm)",size),row("Straight run between boxes (m)",run));
    }

    private double[] supportRule(String t,int z){
        if(t.startsWith("NMD90"))return new double[]{1.5,.3}; if(t.startsWith("BX"))return new double[]{1.5,.3};
        if(t.equals("Rigid metal conduit"))return new double[]{z<=21?1.5:z<=35?2:3,0};
        if(t.equals("EMT"))return new double[]{z<=21?1.5:z<=35?2:3,1};
        if(t.equals("Rigid PVC conduit"))return new double[]{z<=27?.75:z<=41?1.2:z<=53?1.5:z<=78?1.8:z<=129?2.1:2.5,0};
        if(t.equals("Flexible metal conduit")||t.equals("Liquid-tight flexible conduit"))return new double[]{1.5,.3};
        return new double[]{1,1};
    }

    private Node voltageDrop() {
        ChoiceBox<Integer> phases=phaseChoice(1); TextField voltage=field("240"), current=field("40"), length=field("30");
        ChoiceBox<String> unit=choice("m","m","ft"); ComboBox<String> size=new ComboBox<>(); size.getItems().addAll(CIRCULAR_MILS.keySet()); size.setValue("6 AWG"); size.setMaxWidth(Double.MAX_VALUE);
        TextField cmil=field(String.valueOf(CIRCULAR_MILS.get("6 AWG").intValue())); size.setOnAction(e->cmil.setText(String.valueOf(CIRCULAR_MILS.get(size.getValue()).intValue())));
        ChoiceBox<String> material=choice("Copper","Copper","Aluminum","Custom K"); TextField k=field("12.9"), allowed=field("3.0");
        material.setOnAction(e->{if(material.getValue().equals("Copper"))k.setText("12.9");else if(material.getValue().equals("Aluminum"))k.setText("21.2");});
        TextArea result=resultArea(); Label status=statusLabel(); Button drop=button("Voltage drop"), max=button("Maximum length"), solve=button("Solve K factor");
        drop.setOnAction(e->local(status,result,()->{double ft=toFeet(number(length),unit.getValue()),f=phases.getValue()==3?Math.sqrt(3):2,vd=f*number(k)*number(current)*ft/number(cmil);return String.format("Voltage drop: %.2f V%nVoltage drop: %.2f%%%nLoad voltage: %.2f V%nFormula: VD = %s × K × I × L ÷ CM%n%nVerify final conductor selection against CEC 2024 Table D3 and its notes.",vd,vd/number(voltage)*100,number(voltage)-vd,phases.getValue()==3?"√3":"2");}));
        max.setOnAction(e->local(status,result,()->{double av=number(voltage)*number(allowed)/100,f=phases.getValue()==3?Math.sqrt(3):2,ft=av*number(cmil)/(f*number(k)*number(current));return String.format("Maximum one-way length: %.1f ft%nMaximum one-way length: %.1f m%nAllowed drop: %.2f V (%.2f%%)",ft,ft/3.280839895,av,number(allowed));}));
        solve.setOnAction(e->local(status,result,()->{double ft=toFeet(number(length),unit.getValue()),av=number(voltage)*number(allowed)/100,f=phases.getValue()==3?Math.sqrt(3):2,kk=av*number(cmil)/(f*number(current)*ft);return String.format("Solved K factor: %.4f ohm-cmil/ft%nUsing %.2f%% drop over %.1f %s.",kk,number(allowed),number(length),unit.getValue());}));
        VBox box=card("Voltage drop / conductor length / K factor", "Includes the Table D3-style workflow plus engineering K-factor calculations.");
        box.getChildren().addAll(grid(row("Phases",phases),row("System voltage",voltage),row("Current (A)",current),row("One-way length",length),row("Length unit",unit),row("Conductor size",size),row("Circular mils",cmil),row("Material",material),row("K factor",k),row("Allowed / observed drop (%)",allowed)),new HBox(8,drop,max,solve),status,result);
        return box;
    }

    private Node pvcExpansion() {
        TextField run=field("30"), min=field("-20"), max=field("40"), coeff=field("0.052"), travel=field("100");
        return localForm("PVC expansion", "Calculates thermal movement and an expansion-joint count estimate from the coefficient and fitting travel you enter.", "Calculate expansion and joint count", () -> {
            double dt=Math.abs(number(max)-number(min)), movement=number(run)*dt*number(coeff); int joints=(int)Math.ceil(movement/number(travel));
            return String.format("Temperature change: %.1f °C%nEstimated PVC movement: %.1f mm%nExpansion-joint travel entered: %.1f mm%nEstimated minimum fittings for movement capacity: %d%n%nFormula: movement = run × ΔT × coefficient.%nVerify manufacturer data and the applicable CEC installation rule.",dt,movement,number(travel),Math.max(1,joints));
        }, row("PVC run length (m)",run),row("Minimum temperature (°C)",min),row("Maximum temperature (°C)",max),row("Expansion coefficient (mm/m/°C)",coeff),row("Movement capacity per fitting (mm)",travel));
    }

    private Node section14() {
        TextField source=field("225"), amp=field("100"), ratio=field("0.333333"), length=field("3"), down=field("100");
        return sectionTabs("Section 14 — Protection & Control", "Tap/splitter checks restored from the earlier workspace.", tab("Tap / Splitter",
            apiForm("Tap / splitter worksheet", "Checks selected tap ampacity ratio and downstream OCPD values; verify exact length/installation conditions.", "/tap", "Calculate tap check",
                () -> payload("sourceOcpd",number(source),"tapAmpacity",number(amp),"minimumRatio",number(ratio),"lengthM",number(length),"downstreamOcpd",number(down),"edition","CEC_2024"),
                row("Source OCPD (A)",source),row("Tap conductor ampacity (A)",amp),row("Minimum ratio",ratio),row("Tap length (m)",length),row("Downstream OCPD (A)",down))));
    }

    private Node section16() {
        return sectionTabs("Section 16 — Class 1 & Class 2 Circuits", "Low-voltage source current, fuse/OCPD and cable-ampacity checks.", tab("Low-Voltage Circuit", lowVoltage("Class 2")));
    }

    private Node lowVoltage(String defaultClass) {
        ChoiceBox<String> cls=choice(defaultClass,"Class 1","Class 2"); TextField va=field("100"), voltage=field("24"), max=field("4"), fuse=field("4"), cable=field("3.5"), desc=field("3C #20 AWG LVT (practice entry: 3.5 A)");
        return localForm("Class 1 / Class 2 low-voltage circuit", "Source current, maximum OCPD, selected fuse and cable-ampacity worksheet.", "Calculate low-voltage circuit", () -> {
            double amps=number(va)/number(voltage); return String.format("%s LOW-VOLTAGE CIRCUIT%nSource current: %.2f A%nMaximum OCPD entered: %.2f A%nSelected fuse/OCPD: %.2f A → %s%nCable ampacity: %.2f A → %s%nCable: %s%n%nVerify Rule 16-200/Table 57 and any Section 24/32 or equipment requirements.",
                cls.getValue().toUpperCase(),amps,number(max),number(fuse),number(fuse)<=number(max)?"PASS":"FAIL",number(cable),number(cable)>=amps?"PASS vs source current":"CHECK",desc.getText());
        }, row("Circuit class",cls),row("Source rating (VA)",va),row("Circuit voltage (V)",voltage),row("Maximum permitted OCPD (A)",max),row("Selected fuse/OCPD (A)",fuse),row("Cable ampacity (A)",cable),row("Cable description",desc));
    }

    private Node section24() {
        TextField len=field("2.1"), wid=field("0.9");
        Node planning=localForm("Patient care environment planning", "Planning-footprint helper from the earlier workspace.", "Calculate planning footprint", () ->
            String.format("Bed/table: %.2f m × %.2f m%nPlanning footprint: %.2f m × %.2f m%nVertical extent: 2.3 m above floor%n%nVerify the exact Section 24 patient-care environment definition and requirements.",number(len),number(wid),number(len)+3,number(wid)+3),
            row("Bed/table length (m)",len),row("Bed/table width (m)",wid));
        VBox refs=card("Patient-care quick reference", "Use Section 24 together with listed medical equipment instructions.");
        refs.getChildren().addAll(new Label("• Basic care — grounded distribution and patient-care branch-circuit requirements."),new Label("• Intermediate / critical — additional bonding/receptacle requirements may apply."),new Label("• Isolated systems — isolation source, monitoring, identification, separation and bonding."));
        VBox combined=new VBox(12,planning,refs,lowVoltage("Class 2"));
        return sectionTabs("Section 24 — Patient Care Areas", "Environment planning, quick reference and low-voltage checks.", tab("Environment",combined));
    }

    private Node section26() {
        return sectionTabs("Section 26 — Installation of Electrical Equipment", "Transformers and capacitor-bank worksheets.",
            tab("Transformer", transformer()),tab("Dry Transformer", dryTransformer()),tab("Medium Voltage", hvTransformer(false)),tab("High Voltage", hvTransformer(true)),tab("Capacitor Bank", capacitorWorksheet()),tab("PF Correction", pfCorrection()));
    }

    private Node transformer() {
        TextField kva=field("75"), pv=field("600"), sv=field("208"), design=field("1.25"), po=field("1.25"), so=field("1.25"); ChoiceBox<Integer> ph=phaseChoice(3);
        return apiForm("Transformer sizing", "Primary/secondary current, design ampacity and primary/secondary OCPD worksheet.", "/transformer", "Calculate transformer",
            ()->payload("kva",number(kva),"primaryVoltage",number(pv),"secondaryVoltage",number(sv),"phases",ph.getValue(),"designFactor",number(design),"primaryOcpdFactor",number(po),"secondaryOcpdFactor",number(so),"edition","CEC_2024"),
            row("Transformer kVA",kva),row("Primary voltage",pv),row("Secondary voltage",sv),row("Phases",ph),row("Conductor design factor",design),row("Primary OCPD factor",po),row("Secondary OCPD factor",so));
    }

    private Node dryTransformer() {
        TextField kva=field("52"), pv=field("600"), sv=field("480"); ChoiceBox<Integer> ph=phaseChoice(3);
        return apiForm("Dry-type / 600 V-class transformer worksheet", "Restores the detailed dry-transformer worksheet path.", "/transformer-dry-worksheet", "Calculate dry transformer",
            ()->payload("kva",number(kva),"primaryVoltage",number(pv),"secondaryVoltage",number(sv),"phases",ph.getValue()),row("Transformer kVA",kva),row("Primary voltage (V)",pv),row("Secondary voltage (V)",sv),row("Phases",ph));
    }

    private Node hvTransformer(boolean high) {
        TextField mva=field(high?"10":"2.5"), pk=field(high?"44":"13.8"), sk=field(high?"27.6":"7.2"), factor=field("3");
        ChoiceBox<String> protection=choice("BREAKER","BREAKER","FUSE"), material=choice(high?"COPPER":"ALUMINUM","COPPER","ALUMINUM"), wiring=choice(high?"BURIED_CONDUIT":"OUTDOOR_VENTILATED_TRAY","BURIED_CONDUIT","OUTDOOR_VENTILATED_TRAY"), vclass=choice(high?"25-46_KV":"5-15_KV","5-15_KV","25-46_KV");
        return apiForm((high?"High":"Medium")+"-voltage transformer worksheet", "Current, protection and conductor/raceway worksheet for the selected voltage class.", "/transformer-hv-worksheet", "Calculate transformer",
            ()->payload("mva",number(mva),"primaryKv",number(pk),"secondaryKv",number(sk),"protection",protection.getValue(),"protectionFactor",number(factor),"material",material.getValue(),"wiringMethod",wiring.getValue(),"voltageClass",vclass.getValue()),
            row("Rating (MVA)",mva),row("Primary (kV)",pk),row("Secondary (kV)",sk),row("Protection",protection),row("Protection multiplier",factor),row("Material",material),row("Wiring method",wiring),row("Voltage class",vclass));
    }

    private Node capacitorWorksheet() {
        TextField kvar=field("25"), voltage=field("600"); ChoiceBox<Integer> ph=phaseChoice(3);
        return apiForm("Capacitor bank worksheet", "kVAR → current → conductor/OCPD worksheet.", "/capacitor-worksheet", "Calculate capacitor worksheet", ()->payload("kvar",number(kvar),"voltage",number(voltage),"phases",ph.getValue()),row("Capacitor kVAR",kvar),row("Voltage (V)",voltage),row("Phases",ph));
    }

    private Node pfCorrection() {
        TextField kw=field("100"), old=field("0.75"), target=field("0.95"), voltage=field("600"), cf=field("1.35"), of=field("1.65"); ChoiceBox<Integer> ph=phaseChoice(3);
        return apiForm("Power-factor correction", "Required kVAR, capacitor current, conductor and OCPD worksheet.", "/capacitor-bank", "Calculate PF correction",
            ()->payload("kw",number(kw),"existingPf",number(old),"targetPf",number(target),"voltage",number(voltage),"phases",ph.getValue(),"conductorFactor",number(cf),"ocpdFactor",number(of),"edition","CEC_2024"),
            row("Load (kW)",kw),row("Existing PF",old),row("Target PF",target),row("Voltage",voltage),row("Phases",ph),row("Conductor factor",cf),row("OCPD factor",of));
    }

    private Node section28() {
        return sectionTabs("Section 28 — Motors & Generators", "AC motors, DC motors, multiple motors and refrigeration/compressor worksheets.",
            tab("AC Motor", motor(false)),tab("DC Motor", motor(true)),tab("Multiple Motors", multiMotor()),tab("Refrigeration Worksheet", refrigerationWorksheet()),tab("Nameplate MCA/MOCP", refrigerationDetailed()));
    }

    private Node motor(boolean dc) {
        ChoiceBox<String> type=choice("Shunt","Shunt","Series","Cumulative compound","Differential compound","Permanent-magnet","Separately excited");
        TextField flc=field(dc?"40":"34"), cf=field("1.25"), of=field(dc?"1.5":"1.75"), ol=field(dc?"1.15":"1.25"), dis=field("1.15");
        Node form=apiForm((dc?"DC":"AC")+" motor branch worksheet", "Conductor, branch OCPD, overload and disconnect worksheet.", "/motor", "Calculate motor",
            ()->payload("flc",number(flc),"conductorFactor",number(cf),"overloadFactor",number(ol),"ocpdFactor",number(of),"disconnectFactor",number(dis),"edition","CEC_2024"),
            row("Motor FLC/FLA (A)",flc),row("Conductor factor",cf),row("OCPD factor",of),row("Overload factor",ol),row("Disconnect factor",dis));
        if(!dc)return form; VBox box=new VBox(10,new Label("DC motor type: "),type,form); return box;
    }

    private Node multiMotor() {
        TextField flcs=field("42,27,20,10"), cf=field("1.25"), of=field("1.75"), extra=field("0");
        return apiForm("Multiple motors", "Feeder ampacity and feeder OCPD worksheet including simultaneous-start allowance.", "/multi-motor", "Calculate multi-motor feeder",
            ()->payload("flcs",csvD(flcs.getText()),"largestConductorFactor",number(cf),"largestBranchOcpdFactor",number(of),"simultaneousStartExtra",number(extra),"edition","CEC_2024"),
            row("Motor FLCs (A, comma-separated)",flcs),row("Largest motor conductor factor",cf),row("Largest branch OCPD factor",of),row("Simultaneous-start extra",extra));
    }

    private Node refrigerationWorksheet() {
        TextField rla=field("20"), lra=field("0");
        return apiForm("Refrigeration / hermetic compressor worksheet", "Worksheet path using RLA and LRA; enter 0 LRA to use the backend worksheet default.", "/refrigeration-worksheet", "Calculate refrigeration worksheet",
            ()->payload("rla",number(rla),"lra",number(lra)<=0?null:number(lra)),row("RLA (A)",rla),row("LRA (A; 0 = worksheet default)",lra));
    }

    private Node refrigerationDetailed() {
        TextField rla=field("28"), mca=field(""), mocp=field(""), cf=field("1.25"), of=field("1.75"), ol=field("1.15");
        return apiForm("Refrigeration / compressor nameplate worksheet", "Uses entered MCA/MOCP where provided; otherwise worksheet factors are applied.", "/refrigeration", "Calculate refrigeration circuit",
            ()->payload("rla",number(rla),"mca",optionalNumber(mca),"mocp",optionalNumber(mocp),"conductorFactor",number(cf),"ocpdFactor",number(of),"overloadFactor",number(ol),"edition","CEC_2024"),
            row("RLA (A)",rla),row("Nameplate MCA (optional)",mca),row("Nameplate MOCP (optional)",mocp),row("Conductor factor",cf),row("OCPD factor",of),row("Overload factor",ol));
    }

    private Node section32() {
        return sectionTabs("Section 32 — Fire Alarm / Fire Pumps", "Fire-alarm low-voltage checks, end-of-line current and fire-pump ampacity.",
            tab("Fire Alarm / LV", fireAlarm()),tab("EOL Current", eol()),tab("Fire Pump", firePump()));
    }

    private Node fireAlarm() {
        VBox box=card("Fire alarm quick reference", "Use the listed FACP/power-supply ratings and manufacturer instructions together with Section 32.");
        box.getChildren().addAll(new Label("• Dedicated fire-alarm wiring and power-supply requirements apply."),new Label("• NAC/SLC/auxiliary output fusing and conductor limits follow listed equipment ratings."),new Label("• Use the low-voltage worksheet only where the ancillary circuit is actually Class 1/Class 2."),lowVoltage("Class 2"));
        return box;
    }

    private Node eol() {
        TextField voltage=field("24"), resistance=field("3900");
        return localForm("End-of-line current", "Simple fire-alarm EOL current calculator.", "Calculate EOL current", () -> {
            double amps=number(voltage)/number(resistance); return String.format("EOL current: %.6f A%nEOL current: %.3f mA%nFormula: I = V / R",amps,amps*1000);
        },row("Circuit voltage (V)",voltage),row("EOL resistance (Ω)",resistance));
    }

    private Node firePump() {
        TextField fire=field("100"), jockey=field("0"), aux=field("0");
        return localForm("Fire pump conductor ampacity", "Practice worksheet: fire-pump FLC at 125% plus jockey/auxiliary loads entered.", "Calculate fire-pump ampacity", () -> {
            double required=number(fire)*1.25+number(jockey)*1.25+number(aux); return String.format("Fire pump contribution: %.2f A%nJockey contribution: %.2f A%nAuxiliary contribution: %.2f A%nRequired worksheet ampacity: %.2f A%n%nVerify the special fire-pump rules and the applicable ampacity table before final selection.",number(fire)*1.25,number(jockey)*1.25,number(aux),required);
        },row("Fire pump FLC (A)",fire),row("Jockey pump FLC (A)",jockey),row("Auxiliary load (A)",aux));
    }

    private Node section42() {
        return sectionTabs("Section 42 — Electric Welders", "Single and multiple-welder worksheets.", tab("Single Welder", singleWelder()),tab("Multiple Welder Feeder", multipleWelders()));
    }

    private Node singleWelder() {
        ChoiceBox<String> type=choice("TRANSFORMER_ARC","TRANSFORMER_ARC","RESISTANCE"); TextField kva=field("30"), voltage=field("600"), duty=field("0.55"), amp=field("35");
        return apiForm("Single welder worksheet", "Rating, duty-cycle and selected-conductor worksheet.", "/welder-worksheet", "Calculate single welder",
            ()->payload("kva",number(kva),"voltage",number(voltage),"dutyFactor",number(duty),"type",type.getValue(),"selectedConductorAmpacity",number(amp)),
            row("Welder type",type),row("Rating (kVA)",kva),row("Voltage (V)",voltage),row("Duty-cycle multiplier",duty),row("Selected conductor ampacity (A)",amp));
    }

    private Node multipleWelders() {
        TextField currents=field("50,40,30,20,15"), duties=field("0.70,0.70,0.60,0.55,0.50"), branch=field("35,30,25,20,15");
        return localForm("Multiple welder feeder — worksheet method", "Two largest duty-adjusted loads at 100%, then 85%, 70% and 60% for later units.", "Calculate multiple-welder feeder", () -> {
            List<Double> i=csvD(currents.getText()), d=csvD(duties.getText()), b=csvD(branch.getText()); if(i.size()!=d.size())throw new IllegalArgumentException("Current and duty lists must have the same count");
            List<Double> adj=new ArrayList<>(); for(int x=0;x<i.size();x++)adj.add(i.get(x)*d.get(x)); adj.sort(Comparator.reverseOrder());
            double total=0; for(int x=0;x<adj.size();x++){double f=x<2?1.0:x==2?.85:x==3?.70:.60; total+=adj.get(x)*f;}
            return String.format("Welders: %d%nDuty-adjusted loads (descending): %s%nFeeder worksheet load: %.2f A%nBranch ampacities entered: %s%n%nVerify the exact Section 42 duty-cycle/group-demand table factors for the actual welder ratings.",i.size(),adj,total,b);
        },row("Input currents (A, comma-separated)",currents),row("Duty factors (comma-separated)",duties),row("Branch conductor ampacities (A)",branch));
    }

    private Node sectionTabs(String title,String lead,Tab... tabs){VBox page=sectionHeader(title,lead);TabPane tp=new TabPane();tp.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);tp.getTabs().addAll(tabs);VBox.setVgrow(tp,Priority.ALWAYS);page.getChildren().add(tp);return page;}
    private VBox sectionHeader(String title,String lead){VBox page=new VBox(12);Label h=new Label(title);h.setStyle("-fx-font-size:23px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");Label l=new Label(lead);l.setWrapText(true);l.setStyle("-fx-text-fill:#64748b;");page.getChildren().addAll(h,l);return page;}
    private Tab tab(String title,Node n){Tab t=new Tab(title);t.setContent(scroll(n));return t;}
    private ScrollPane scroll(Node n){ScrollPane s=new ScrollPane(n);s.setFitToWidth(true);s.setStyle("-fx-background:transparent;-fx-background-color:transparent;");return s;}
    private Node refCard(String title,String text){VBox b=card(title,text);b.setPrefWidth(330);return b;}

    private Node apiForm(String title,String desc,String endpoint,String buttonText,Supplier<Map<String,Object>> body,Node... rows){TextArea out=resultArea();Label status=statusLabel();Button calc=button(buttonText);calc.setOnAction(e->{try{runApi(calc,status,out,endpoint,body.get());}catch(Exception ex){status.setText("Check inputs: "+ex.getMessage());}});return form(title,desc,grid(rows),calc,status,out);}
    private Node localForm(String title,String desc,String buttonText,Calculation c,Node... rows){TextArea out=resultArea();Label status=statusLabel();Button calc=button(buttonText);calc.setOnAction(e->local(status,out,c));return form(title,desc,grid(rows),calc,status,out);}
    private Node form(String title,String desc,Node fields,Node controls,Label status,TextArea result){VBox box=card(title,desc);box.getChildren().addAll(fields,controls,status,result);return box;}
    private VBox card(String title,String desc){VBox b=new VBox(11);b.setPadding(new Insets(16));b.setStyle(CARD);Label h=new Label(title);h.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");Label d=new Label(desc);d.setWrapText(true);d.setStyle("-fx-text-fill:#64748b;");b.getChildren().addAll(h,d);return b;}
    private GridPane grid(Node... rows){GridPane g=new GridPane();g.setHgap(12);g.setVgap(9);int r=0;for(Node n:rows){g.add(n,0,r++);GridPane.setHgrow(n,Priority.ALWAYS);}return g;}
    private HBox row(String label,Node control){Label l=new Label(label);l.setMinWidth(235);l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;");HBox h=new HBox(12,l,control);h.setAlignment(Pos.CENTER_LEFT);HBox.setHgrow(control,Priority.ALWAYS);if(control instanceof Region rr)rr.setMaxWidth(Double.MAX_VALUE);return h;}
    private TextField field(String value){TextField f=new TextField(value);f.setStyle(INPUT);return f;}
    private <T> ChoiceBox<T> choice(T selected,@SuppressWarnings("unchecked") T... items){ChoiceBox<T> c=new ChoiceBox<>();c.getItems().addAll(items);c.setValue(selected);c.setMaxWidth(Double.MAX_VALUE);return c;}
    private ChoiceBox<Integer> phaseChoice(int selected){ChoiceBox<Integer> c=choice(selected,1,3);return c;}
    private Button button(String text){Button b=new Button(text);b.setStyle(PRIMARY);return b;}
    private TextArea resultArea(){TextArea a=new TextArea("Enter the question facts, then calculate.");a.setEditable(false);a.setWrapText(false);a.setPrefRowCount(13);a.setStyle("-fx-font-family:Consolas;-fx-font-size:12px;-fx-control-inner-background:#0f172a;-fx-text-fill:#e2e8f0;");return a;}
    private Label statusLabel(){Label l=new Label();l.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");return l;}

    private void runApi(Button button,Label status,TextArea result,String endpoint,Map<String,Object> payload){button.setDisable(true);status.setText("Calculating…");CompletableFuture.supplyAsync(()->{try{return portal.calculate(endpoint,username,password,json.writeValueAsString(payload));}catch(Exception ex){throw new RuntimeException(ex);}}).whenComplete((body,err)->Platform.runLater(()->{button.setDisable(false);if(err!=null){Throwable c=unwrap(err);status.setText("Calculation failed");result.setText(message(c));return;}try{JsonNode node=json.readTree(body);result.setText(json.writerWithDefaultPrettyPrinter().writeValueAsString(node));status.setText("Calculated");}catch(Exception ex){result.setText(body);status.setText("Calculated");}}));}
    private void local(Label status,TextArea result,Calculation c){try{result.setText(c.run());status.setText("Calculated locally in JavaFX");}catch(Exception ex){status.setText("Check inputs");result.setText(ex.getMessage()==null?ex.toString():ex.getMessage());}}

    private Map<String,Object> payload(Object... kv){LinkedHashMap<String,Object> m=new LinkedHashMap<>();for(int i=0;i<kv.length;i+=2)m.put(String.valueOf(kv[i]),kv[i+1]);return m;}
    private double number(TextField f){String s=f.getText().trim();if(s.isEmpty())throw new IllegalArgumentException("A required value is blank");double v=Double.parseDouble(s);if(!Double.isFinite(v))throw new IllegalArgumentException("Invalid number");return v;}
    private Double optionalNumber(TextField f){String s=f.getText().trim();return s.isEmpty()?null:Double.parseDouble(s);}
    private int integer(TextField f){return (int)Math.round(number(f));}
    private List<Double> csvD(String s){List<Double> out=new ArrayList<>();for(String p:s.split(",")){String q=p.trim();if(!q.isEmpty())out.add(Double.parseDouble(q));}return out;}
    private List<Integer> csvI(String s){List<Integer> out=new ArrayList<>();for(String p:s.split(",")){String q=p.trim();if(!q.isEmpty())out.add(Integer.parseInt(q));}return out;}
    private double toFeet(double length,String unit){return unit.equals("m")?length*3.280839895:length;}
    private Throwable unwrap(Throwable t){Throwable x=t;while(x.getCause()!=null&&x.getCause()!=x)x=x.getCause();return x;}
    private String message(Throwable t){return t.getMessage()==null?t.toString():t.getMessage();}
    @FunctionalInterface private interface Calculation{String run();}
}

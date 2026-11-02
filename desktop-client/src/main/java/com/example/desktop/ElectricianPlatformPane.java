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

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Native JavaFX platform features beyond the core calculators: Learn, Field,
 * Projects and Career. Most features persist locally so they continue to work
 * without site connectivity.
 */
public final class ElectricianPlatformPane extends BorderPane {
    private final ObjectMapper json;
    private final String username;
    private final LocalWorkspaceStore store;
    private final TabPane mainTabs = new TabPane();

    private static final String INPUT = "-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-border-radius:9;-fx-background-radius:9;-fx-padding:9 10;";
    private static final String PRIMARY = "-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;-fx-padding:10 14;";
    private static final String CARD = "-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#dfe7ef;-fx-border-radius:14;-fx-padding:16;";

    public ElectricianPlatformPane(ObjectMapper json, String username, String initialArea) {
        this.json = json;
        this.username = username == null ? "user" : username;
        this.store = new LocalWorkspaceStore(json, this.username);

        VBox heading = new VBox(5);
        Label title = new Label("Electrician Platform");
        title.setStyle("-fx-font-size:25px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label sub = new Label("Study, field work, project records and career tools. Local records are stored offline on this computer.");
        sub.setWrapText(true); sub.setStyle("-fx-text-fill:#64748b;");
        Label offline = new Label("● OFFLINE-READY LOCAL WORKSPACE");
        offline.setStyle("-fx-text-fill:#15803d;-fx-font-size:10px;-fx-font-weight:bold;");
        heading.getChildren().addAll(title, sub, offline);
        heading.setPadding(new Insets(0,0,12,0));
        setTop(heading);

        mainTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        mainTabs.getTabs().addAll(
            tab("Learn", learnPane()),
            tab("Field", fieldPane()),
            tab("Projects", projectsPane()),
            tab("Career", careerPane())
        );
        setCenter(mainTabs);
        select(initialArea);
    }

    private void select(String area) {
        if (area == null) return;
        for (Tab t : mainTabs.getTabs()) if (t.getText().equalsIgnoreCase(area)) { mainTabs.getSelectionModel().select(t); break; }
    }

    // ------------------------- LEARN -------------------------
    private Node learnPane() {
        TabPane tabs = nestedTabs();
        tabs.getTabs().addAll(
            tab("Daily 25 Quiz", dailyQuiz()),
            tab("CEC Navigator", cecNavigator()),
            tab("Formula Library", formulaLibrary()),
            tab("Control Trainer", controlTrainer()),
            tab("Province / Code", provinceSelector())
        );
        return tabs;
    }

    private Node dailyQuiz() {
        VBox root = page("Red Seal / C of Q Daily 25", "Five questions from each A–E task category. The same user gets a stable set for the calendar day.");
        List<PracticeQuestionBank.Question> questions = PracticeQuestionBank.daily25(LocalDate.now(), username);
        final int[] index = {0}; final int[] score = {0}; final boolean[] answered = {false};

        Label progress = new Label(); progress.setStyle("-fx-font-weight:bold;-fx-text-fill:#0e7490;");
        Label category = new Label(); category.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#64748b;");
        Label question = new Label(); question.setWrapText(true); question.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        VBox answers = new VBox(8); ToggleGroup group = new ToggleGroup();
        Label feedback = new Label(); feedback.setWrapText(true); feedback.setStyle("-fx-text-fill:#334155;");
        Button action = button("Check answer");
        Label scoreLabel = new Label("Score: 0 / 0"); scoreLabel.setStyle("-fx-font-weight:bold;");

        Runnable render = () -> {
            if (index[0] >= questions.size()) {
                progress.setText("Daily quiz complete"); category.setText("25 questions • 5 per task category");
                question.setText("Final score: " + score[0] + " / " + questions.size()); answers.getChildren().clear();
                feedback.setText("Your result is saved locally. Revisit the explanations and use the CEC Navigator for weak topics.");
                action.setDisable(true);
                ObjectNode rec = json.createObjectNode(); rec.put("date", LocalDate.now().toString()); rec.put("score", score[0]); rec.put("total", questions.size()); store.append("quizHistory", rec);
                return;
            }
            answered[0] = false; group.getToggles().clear(); answers.getChildren().clear(); feedback.setText(""); action.setText("Check answer");
            PracticeQuestionBank.Question q = questions.get(index[0]);
            progress.setText("Question " + (index[0] + 1) + " of " + questions.size()); category.setText(q.category()); question.setText(q.question());
            for (int i=0;i<q.options().size();i++) { RadioButton rb = new RadioButton((char)('A'+i) + ". " + q.options().get(i)); rb.setToggleGroup(group); rb.setUserData(i); rb.setWrapText(true); answers.getChildren().add(rb); }
        };
        action.setOnAction(e -> {
            if (index[0] >= questions.size()) return;
            PracticeQuestionBank.Question q = questions.get(index[0]);
            if (!answered[0]) {
                Toggle selected = group.getSelectedToggle(); if (selected == null) { feedback.setText("Select an answer first."); return; }
                int choice = (Integer)selected.getUserData(); boolean correct = choice == q.correctIndex(); if (correct) score[0]++;
                scoreLabel.setText("Score: " + score[0] + " / " + (index[0]+1));
                feedback.setText((correct ? "Correct. " : "Incorrect. Correct answer: " + (char)('A'+q.correctIndex()) + ". ") + q.explanation() + "\nReference: " + q.reference());
                answered[0] = true; action.setText(index[0] == questions.size()-1 ? "Finish quiz" : "Next question");
            } else { index[0]++; render.run(); }
        });
        render.run();
        VBox card = card("Today's quiz", "Questions are selected by task category; this starter desktop bank can be expanded with the full cloud test bank later.");
        card.getChildren().addAll(progress, category, question, answers, feedback, new HBox(12, action, scoreLabel));
        root.getChildren().add(card); return scroll(root);
    }

    private Node cecNavigator() {
        VBox root = page("CEC Rule Navigator", "Search original topic summaries and rule/table references without reproducing the copyrighted Code text.");
        TextField search = field(""); search.setPromptText("Try: motor feeder, hospital, tap, bonding, voltage drop, fire alarm...");
        VBox results = new VBox(8);
        List<RuleEntry> entries = List.of(
            r("Voltage drop / conductor length","Appendix D / Table D3 study workflow","Voltage drop, maximum run length and conductor-selection checks."),
            r("Residential service","Rule 8-200 study workflow","Dwelling demand including basic load, range, heating/A-C and special loads."),
            r("Apartment service","Rule 8-202 study workflow","Multi-suite demand and common/house load calculation."),
            r("School service","Rule 8-204 study workflow","School occupancy demand by area and connected loads."),
            r("Hospital service","Rule 8-206 study workflow","Hospital occupancy demand including high-intensity areas."),
            r("Other occupancy / hotel","Rule 8-210 / Table 14 study workflow","Area load plus occupancy demand factor and special loads."),
            r("Grounding and bonding","Section 10","Service grounding, bonding jumpers, electrodes and equipment bonding."),
            r("Raceway fill","Section 12 / Table 9 series","Raceway dimensions, conductor fill and wiring-method installation."),
            r("Tap conductors","Section 14","Tap ampacity, length and downstream overcurrent protection conditions."),
            r("Class 1 / Class 2","Section 16","Low-voltage source, OCPD and conductor requirements."),
            r("Patient care","Section 24","Patient-care environments, bonding, receptacles and isolated systems."),
            r("Transformers","Section 26","Transformer currents, conductors, OCPD, grounding and capacitor equipment."),
            r("Motors / feeders","Section 28","Motor conductors, overloads, branch OCPD, disconnects and multi-motor feeders."),
            r("Fire alarm / fire pump","Section 32","Fire-alarm wiring/power and special fire-pump requirements."),
            r("Welders","Section 42","Duty-cycle and group-demand treatment for welder conductors/feeders."),
            r("Emergency systems","Section 46","Emergency power, transfer and emergency lighting supply requirements."),
            r("Electric heating","Section 62","Space heating, trace heating and heating load demand treatment."),
            r("Communications / fibre","Section 60/86 study topics","Separation, cable suitability and communications wiring considerations.")
        );
        Runnable render = () -> { results.getChildren().clear(); String q=search.getText().trim().toLowerCase(); for(RuleEntry x:entries) if(q.isEmpty()||(x.topic()+" "+x.reference()+" "+x.summary()).toLowerCase().contains(q)) results.getChildren().add(refCard(x.topic(),x.reference()+"\n"+x.summary())); };
        search.textProperty().addListener((o,a,b)->render.run()); render.run();
        root.getChildren().addAll(search, results); return scroll(root);
    }

    private Node formulaLibrary() {
        VBox root=page("Formula Library","Fast trade formulas with rearranged forms and Canadian electrical examples.");
        String text = """
OHM'S LAW
V = I × R     I = V / R     R = V / I

POWER
DC / 1φ resistive: P = V × I
1φ AC: P = V × I × PF
3φ AC: P = √3 × V(L-L) × I × PF
I(3φ) = P / (√3 × V × PF)

TRANSFORMERS
1φ: I = VA / V
3φ: I = VA / (√3 × V)
Turns/voltage ratio: Vp/Vs = Np/Ns = Is/Ip (ideal)
Approx fault current at transformer terminals: I_fault ≈ I_FLA / Z(pu)

VOLTAGE DROP — K FACTOR METHOD
1φ: VD = 2 × K × I × L / CM
3φ: VD = √3 × K × I × L / CM
Maximum length: L = VD × CM / (factor × K × I)
K = VD × CM / (factor × I × L)

POWER FACTOR CORRECTION
kVAR = kW × [tan(cos⁻¹ PF_old) − tan(cos⁻¹ PF_new)]

CT RATIO
Primary current = Secondary current × (CT primary rating / CT secondary rating)

4–20 mA SCALING
Signal = 4 mA + (process fraction × 16 mA)
Process fraction = (signal − 4) / 16

HYDRAULICS
Force = Pressure × Area

CONDUIT BENDING
Offset mark spacing = offset × angle multiplier
Rolling offset = √(vertical² + horizontal²)

PVC THERMAL MOVEMENT
Movement = run length × ΔT × expansion coefficient
""";
        TextArea area=new TextArea(text); area.setEditable(false); area.setWrapText(false); area.setPrefRowCount(28); area.setStyle("-fx-font-family:Consolas;-fx-font-size:13px;");
        root.getChildren().add(area); return root;
    }

    private Node controlTrainer() {
        VBox root=page("Electrical Diagram / Control Trainer","Interactive study scenarios for motor controls, VFDs, CTs and fire-alarm circuits.");
        ChoiceBox<String> scenario=choice("3-wire starter","3-wire starter","Reversing starter","Wye-delta starter","VFD 4–20 mA","CT measurement","Fire alarm Class A/B");
        TextArea lesson=new TextArea(); lesson.setEditable(false); lesson.setWrapText(true); lesson.setPrefRowCount(18);
        Runnable show=()->lesson.setText(switch(scenario.getValue()){
            case "3-wire starter" -> "L1 ─ STOP(NC) ─ OL(NC) ─┬─ START(NO) ─┬─ M coil ─ L2\n                         └─ M AUX(NO) ─┘\n\nPress START: M coil energizes and the auxiliary contact seals around START. Press STOP or trip OL: the coil de-energizes and the motor stops.";
            case "Reversing starter" -> "Forward and reverse contactors swap two motor phases. Electrical and mechanical interlocking prevent both contactors from closing together. Troubleshoot by checking stop/overload path, interlocks, coils and phase sequence.";
            case "Wye-delta starter" -> "Start in WYE to reduce winding voltage to about 57.7% of line voltage, then transition to DELTA for normal running. Interlocking/timing must prevent wye and delta contactors from overlapping improperly.";
            case "VFD 4–20 mA" -> "For 0–1800 rpm mapped to 4–20 mA: 1200 rpm = 4 + (1200/1800 × 16) ≈ 14.67 mA. Use the configured reference span, not loaded motor slip speed.";
            case "CT measurement" -> "For a 400:5 CT, ratio = 80:1. A measured 2.5 A secondary corresponds to 200 A primary. Never open-circuit a current-transformer secondary while energized.";
            default -> "Class A circuits return to the source/control equipment and can maintain operation beyond a single open depending on topology. Class B circuits terminate in the field with supervision. Always follow the listed fire-alarm equipment instructions and applicable Code requirements.";
        });
        scenario.setOnAction(e->show.run()); show.run(); root.getChildren().addAll(scenario,lesson); return root;
    }

    private Node provinceSelector() {
        VBox root=page("Province / Code Profile","CEC-based calculations can remain common while provincial adoption, amendments and inspection guidance are layered on top.");
        ChoiceBox<String> province=choice("Ontario","Ontario","Alberta","British Columbia","Saskatchewan","Manitoba","Quebec","New Brunswick","Nova Scotia","Prince Edward Island","Newfoundland and Labrador","Yukon","Northwest Territories","Nunavut");
        Label info=new Label(); info.setWrapText(true);
        Runnable update=()->info.setText(province.getValue().equals("Ontario") ? "Ontario profile: CEC/OESC-focused workspace with ESA news integration. Always verify the currently adopted Ontario amendments and bulletins." : province.getValue()+" profile: core CEC tools are available. Province-specific amendments/inspection bulletins should be added as a separate verified dataset before relying on local rule differences.");
        province.setOnAction(e->update.run()); update.run(); root.getChildren().addAll(row("Province / territory",province),cardText("Code profile",info)); return root;
    }

    // ------------------------- FIELD -------------------------
    private Node fieldPane() {
        TabPane tabs=nestedTabs(); tabs.getTabs().addAll(
            tab("Troubleshoot", troubleshooting()), tab("Nameplate Capture", nameplateCapture()), tab("Commissioning", commissioning()),
            tab("Inspection", inspection()), tab("Blueprint Helper", blueprintHelper()), tab("Fault Current", faultCurrent()), tab("Ground/Bond Wizard", groundingWizard())
        ); return tabs;
    }

    private Node troubleshooting() {
        VBox root=page("Troubleshooting Assistant","Guided checks that organize a diagnostic process; they do not replace safe isolation, testing procedures or equipment instructions.");
        ChoiceBox<String> symptom=choice("Motor will not start","Motor will not start","Breaker trips immediately","Contactor chatters","GFCI nuisance trips","VFD fault / won't run","Transformer secondary low","4–20 mA reading wrong","Fire alarm open trouble");
        TextArea steps=new TextArea(); steps.setEditable(false); steps.setWrapText(true); steps.setPrefRowCount(18);
        Runnable update=()->steps.setText(troubleSteps(symptom.getValue())); symptom.setOnAction(e->update.run()); update.run(); root.getChildren().addAll(symptom,steps); return root;
    }

    private String troubleSteps(String s){return switch(s){
        case "Motor will not start" -> "1. Establish safe work condition / follow required energized-work procedure.\n2. Confirm source voltage and disconnect/fuses/breaker.\n3. Check overload state and stop/E-stop/interlock chain.\n4. Verify control transformer/control voltage.\n5. Test starter coil command and auxiliary/interlock contacts.\n6. Check motor leads/windings and mechanical load.\n7. For VFD-fed motors, check drive enable, reference, faults and output conditions.";
        case "Breaker trips immediately" -> "1. Do not repeatedly reclose into a fault.\n2. Isolate load and inspect for phase-phase/phase-ground faults.\n3. Verify breaker type/rating and available fault duty.\n4. Check conductor insulation and equipment faults.\n5. For motors/transformers, distinguish inrush from a true fault using the applicable protection rules.";
        case "Contactor chatters" -> "Check control voltage under load, loose control wiring, failing coil, dirty armature/shading ring (AC contactor), unstable PLC/VFD output, seal-in contact and stop/overload chain.";
        case "GFCI nuisance trips" -> "Check actual leakage first: moisture, damaged cords, neutral-ground connections downstream, shared/miswired neutrals, filters/VFDs and aggregate leakage. Do not bypass GFCI protection.";
        case "VFD fault / won't run" -> "Record exact fault code. Check line voltage, DC bus, motor cable/motor insulation, acceleration/deceleration settings, overload/current limit, enable/interlocks, local/remote mode and speed reference. Use the drive manual for fault-specific actions.";
        case "Transformer secondary low" -> "Check primary line voltage and tap position, verify phase/connection, measure secondary unloaded and loaded, inspect loading/voltage drop, confirm transformer rating/frequency and look for single-phasing or poor terminations.";
        case "4–20 mA reading wrong" -> "Confirm loop power, polarity, series wiring, input type/configuration, common/reference, scaling endpoints and whether the transmitter is sourcing or sinking. Measure loop current in series, then compare process fraction to 4–20 mA.";
        default -> "Identify the zone/circuit and exact trouble indication. Check panel documentation and listed equipment instructions. Inspect field wiring for opens/shorts/grounds, EOL device/location, isolators/modules and power supplies. Restore and test the circuit according to applicable fire-alarm procedures.";
    };}

    private Node nameplateCapture() {
        VBox root=page("Equipment Nameplate Capture","Attach a nameplate photo and store the values with the equipment record. Automatic OCR/vision can be connected later without changing the record format.");
        TextField photo=field(""), equipment=field("Motor"), maker=field(""), model=field(""), voltage=field("575"), phases=field("3"), rating=field("60 hp"), current=field(""), notes=field("");
        Button choose=button("Choose photo"); choose.setOnAction(e->{FileChooser fc=new FileChooser();fc.setTitle("Choose nameplate photo");File f=fc.showOpenDialog(getScene()==null?null:getScene().getWindow());if(f!=null)photo.setText(f.getAbsolutePath());});
        Label status=status(); Button save=button("Save equipment record"); save.setOnAction(e->{ObjectNode n=json.createObjectNode();n.put("date",LocalDate.now().toString());n.put("equipment",equipment.getText());n.put("manufacturer",maker.getText());n.put("model",model.getText());n.put("voltage",voltage.getText());n.put("phases",phases.getText());n.put("rating",rating.getText());n.put("current",current.getText());n.put("photo",photo.getText());n.put("notes",notes.getText());store.append("nameplates",n);status.setText("Saved locally.");});
        root.getChildren().add(cardForm("Nameplate / equipment record",row("Photo path",photo),choose,row("Equipment type",equipment),row("Manufacturer",maker),row("Model",model),row("Voltage",voltage),row("Phases",phases),row("Rating",rating),row("FLA/RLA/current",current),row("Notes",notes),save,status)); return scroll(root);
    }

    private Node commissioning() {
        VBox root=page("Commissioning & Test Sheet","Record test results for a project/equipment item and keep them offline with the project workspace.");
        TextField project=field(""), equipment=field(""), voltage=field(""), current=field(""), insulation=field(""), continuity=field("PASS"), rotation=field("ABC"), tester=field(username), notes=field("");
        Label status=status(); Button save=button("Save test sheet"); save.setOnAction(e->{ObjectNode n=json.createObjectNode();n.put("date",LocalDate.now().toString());n.put("project",project.getText());n.put("equipment",equipment.getText());n.put("voltage",voltage.getText());n.put("current",current.getText());n.put("insulationResistance",insulation.getText());n.put("continuity",continuity.getText());n.put("phaseRotation",rotation.getText());n.put("tester",tester.getText());n.put("notes",notes.getText());store.append("commissioning",n);status.setText("Commissioning record saved.");});
        root.getChildren().add(cardForm("Electrical test record",row("Project",project),row("Equipment / circuit",equipment),row("Measured voltage",voltage),row("Measured current",current),row("Insulation resistance",insulation),row("Continuity / bonding",continuity),row("Phase rotation",rotation),row("Tester",tester),row("Notes",notes),save,status)); return scroll(root);
    }

    private Node inspection() {
        VBox root=page("Inspection / Deficiency Checklist","General field checklist. The applicable inspection authority, Code and project specifications remain authoritative.");
        CheckBox labels=new CheckBox("Equipment/circuits labelled"), bonds=new CheckBox("Bonding/grounding visually checked"), torque=new CheckBox("Terminations/torque documentation checked"), covers=new CheckBox("Covers/barriers installed"), clearance=new CheckBox("Working space/access checked"), test=new CheckBox("Required testing completed");
        TextField project=field(""), location=field(""), deficiencies=field(""); Label status=status(); Button save=button("Save inspection");
        save.setOnAction(e->{ObjectNode n=json.createObjectNode();n.put("date",LocalDate.now().toString());n.put("project",project.getText());n.put("location",location.getText());n.put("labels",labels.isSelected());n.put("bonding",bonds.isSelected());n.put("torque",torque.isSelected());n.put("covers",covers.isSelected());n.put("clearance",clearance.isSelected());n.put("testing",test.isSelected());n.put("deficiencies",deficiencies.getText());store.append("inspections",n);status.setText("Inspection saved.");});
        VBox checks=new VBox(8,labels,bonds,torque,covers,clearance,test); root.getChildren().add(cardForm("Field inspection",row("Project",project),row("Location",location),checks,row("Deficiencies / notes",deficiencies),save,status)); return scroll(root);
    }

    private Node blueprintHelper() {
        VBox root=page("Drawing / Blueprint Helper","Scale measurements and quickly estimate actual cable/raceway lengths from drawings.");
        TextField drawing=field("125"), scaleDrawing=field("10"), scaleActual=field("1000"), allowance=field("10"); TextArea out=output(); Button calc=button("Calculate actual length");
        calc.setOnAction(e->{try{double actual=num(drawing)*num(scaleActual)/num(scaleDrawing), with=actual*(1+num(allowance)/100);out.setText(String.format("Scaled actual length: %.2f mm%nWith %.1f%% routing/waste allowance: %.2f mm%n= %.2f m",actual,num(allowance),with,with/1000));}catch(Exception ex){out.setText(ex.getMessage());}});
        root.getChildren().add(cardForm("Scale calculator",row("Measured drawing length (mm)",drawing),row("Scale drawing units",scaleDrawing),row("Scale actual units",scaleActual),row("Routing/waste allowance (%)",allowance),calc,out)); return root;
    }

    private Node faultCurrent() {
        VBox root=page("Transformer Fault-Current Estimate","Approximate transformer-terminal symmetrical current for study/planning. It is not a protection-coordination or arc-flash study.");
        TextField kva=field("75"), voltage=field("208"), impedance=field("3"), upstream=field("0"); ChoiceBox<Integer> phases=choice(3,1,3); TextArea out=output(); Button calc=button("Estimate fault current");
        calc.setOnAction(e->{try{double fla=phases.getValue()==3?num(kva)*1000/(Math.sqrt(3)*num(voltage)):num(kva)*1000/num(voltage);double z=num(impedance)/100.0;double transformer=fla/z;double result=transformer;if(num(upstream)>0)result=1.0/(1.0/transformer+1.0/num(upstream));out.setText(String.format("Transformer FLA: %.1f A%nTransformer-limited terminal fault current: %.0f A%nCombined estimate (if upstream source entered): %.0f A%n%nDo not use this simple estimate as a substitute for a stamped short-circuit/coordination or arc-flash study.",fla,transformer,result));}catch(Exception ex){out.setText(ex.getMessage());}});
        root.getChildren().add(cardForm("Available fault-current worksheet",row("Transformer kVA",kva),row("Secondary voltage",voltage),row("Phases",phases),row("Transformer impedance (%)",impedance),row("Upstream available fault current A (0 = ignore)",upstream),calc,out)); return root;
    }

    private Node groundingWizard() {
        VBox root=page("Grounding & Bonding Wizard","Choose the installation element to identify the calculation/reference path. Exact conductor sizing remains tied to the applicable CEC tables/rules and installation facts.");
        ChoiceBox<String> item=choice("Service grounding conductor","Service grounding conductor","Equipment bonding conductor","Transformer secondary bonding jumper","Grounding electrode conductor","Ground rod / plate","Grounding electrode ring","HV station grounding grid"); Label answer=new Label(); answer.setWrapText(true);
        Runnable update=()->answer.setText(switch(item.getValue()){
            case "Equipment bonding conductor" -> "Start with Section 10 equipment bonding requirements and the applicable bonding-conductor sizing table. Identify the circuit/feeder OCPD and conductor material before sizing.";
            case "Transformer secondary bonding jumper" -> "Use the separately derived system / transformer secondary bonding provisions in Section 10. Determine derived conductor size, transformer arrangement and connection point before selecting the jumper.";
            case "Ground rod / plate" -> "Use the electrode installation requirements for the selected electrode type, including dimensions, burial/location and connection method.";
            case "Grounding electrode ring" -> "Confirm ring-electrode construction, conductor material/size and burial conditions under Section 10.";
            case "HV station grounding grid" -> "High-voltage station grounding is a specialized design task. Use the applicable CEC high-voltage grounding provisions and engineering criteria; touch/step potential may require engineering analysis.";
            default -> "Identify service/derived-system rating, electrode type, conductor material and the applicable Section 10 sizing rule/table. Use the Workbench grounding references and verify the final size against CEC 2024.";
        }); item.setOnAction(e->update.run()); update.run(); root.getChildren().addAll(item,cardText("Sizing path",answer)); return root;
    }

    // ------------------------- PROJECTS -------------------------
    private Node projectsPane() {
        TabPane tabs=nestedTabs(); tabs.getTabs().addAll(tab("Project Log",projectLog()),tab("Material Takeoff",materialTakeoff()),tab("Saved Records",savedRecords()),tab("Cable Planner",cablePlanner())); return tabs;
    }

    private Node projectLog() {
        VBox root=page("Project Workspace","Keep project, panel, equipment and field notes together locally.");
        TextField name=field(""), client=field(""), location=field(""), system=field("347/600 V 3φ 4W"), notes=field(""); Label status=status(); Button save=button("Save project");
        save.setOnAction(e->{ObjectNode n=json.createObjectNode();n.put("created",LocalDate.now().toString());n.put("name",name.getText());n.put("client",client.getText());n.put("location",location.getText());n.put("system",system.getText());n.put("notes",notes.getText());store.append("projects",n);status.setText("Project saved.");});
        root.getChildren().add(cardForm("New project",row("Project name",name),row("Client / employer",client),row("Location",location),row("Electrical system",system),row("Notes",notes),save,status)); return root;
    }

    private Node materialTakeoff() {
        VBox root=page("Material Takeoff / Job Planner","Enter one item per line as: description, quantity, unit cost. Quantity and cost are optional for a pure material list.");
        TextArea lines=new TextArea("12/2 NMD90,100,1.25\n4 in square boxes,12,4.50\nDuplex receptacles,10,3.25"); lines.setPrefRowCount(12); TextArea out=output(); Button calc=button("Calculate takeoff"); Button save=button("Save takeoff"); final double[] total={0};
        calc.setOnAction(e->{StringBuilder b=new StringBuilder();double sum=0;int count=0;for(String line:lines.getText().split("\\R")){if(line.isBlank())continue;String[] p=line.split(",");String desc=p[0].trim();double qty=p.length>1?parse(p[1],1):1,cost=p.length>2?parse(p[2],0):0;double ext=qty*cost;sum+=ext;count++;b.append(String.format("%-30s %8.2f × %8.2f = %10.2f%n",desc,qty,cost,ext));}total[0]=sum;b.append(String.format("%nItems: %d%nEstimated material total: $%.2f",count,sum));out.setText(b.toString());});
        save.setOnAction(e->{ObjectNode n=json.createObjectNode();n.put("date",LocalDate.now().toString());n.put("lines",lines.getText());n.put("estimatedTotal",total[0]);store.append("materials",n);out.appendText("\nSaved locally.");});
        root.getChildren().add(cardForm("Takeoff",lines,new HBox(8,calc,save),out)); return root;
    }

    private Node savedRecords() {
        VBox root=page("Saved Local Records","Review the offline data file used by Projects, Field and Career features.");
        ChoiceBox<String> collection=choice("projects","projects","nameplates","commissioning","inspections","hours","credentials","quizHistory","materials"); TextArea out=output(); out.setPrefRowCount(28); Button refresh=button("Refresh");
        Runnable load=()->{try{out.setText(json.writerWithDefaultPrettyPrinter().writeValueAsString(store.records(collection.getValue()))+"\n\nStored at: "+store.file());}catch(Exception ex){out.setText(ex.getMessage());}}; collection.setOnAction(e->load.run());refresh.setOnAction(e->load.run());load.run();root.getChildren().addAll(new HBox(8,collection,refresh),out);return root;
    }

    private Node cablePlanner() {
        VBox root=page("Cable & Raceway Planner","Combine load current, voltage drop and installation notes into a single project worksheet. Final ampacity/raceway selection must be verified in the Workbench.");
        TextField load=field("100"), voltage=field("600"), length=field("100"), k=field("12.9"), cmil=field("105600"), circuits=field("1"), notes=field("RW90 Cu in raceway"); ChoiceBox<Integer> phases=choice(3,1,3); TextArea out=output(); Button calc=button("Build cable worksheet");
        calc.setOnAction(e->{try{double ft=num(length)*3.280839895,f=phases.getValue()==3?Math.sqrt(3):2,vd=f*num(k)*num(load)*ft/num(cmil);out.setText(String.format("Load current: %.1f A%nSystem: %d-phase %.0f V%nOne-way run: %.1f m%nVoltage drop estimate: %.2f V (%.2f%%)%nParallel circuits/runs entered: %s%nInstallation note: %s%n%nNext: verify ampacity, temperature/terminal limitations, grouping/ambient correction, raceway fill, bonding and CEC Table D3/notes in the Workbench.",num(load),phases.getValue(),num(voltage),num(length),vd,vd/num(voltage)*100,circuits.getText(),notes.getText()));}catch(Exception ex){out.setText(ex.getMessage());}});
        root.getChildren().add(cardForm("Cable planning worksheet",row("Load current (A)",load),row("Voltage",voltage),row("Phases",phases),row("One-way length (m)",length),row("K factor",k),row("Conductor circular mils",cmil),row("Parallel runs",circuits),row("Installation notes",notes),calc,out)); return root;
    }

    // ------------------------- CAREER -------------------------
    private Node careerPane() {
        TabPane tabs=nestedTabs(); tabs.getTabs().addAll(tab("Hours / Skills",hoursTracker()),tab("Credential Wallet",credentials()),tab("Wage Calculator",wageCalculator()),tab("Job Market",jobMarket())); return tabs;
    }

    private Node hoursTracker() {
        VBox root=page("Apprenticeship Hours & Competency Tracker","Personal tracking aid only—the official apprenticeship logbook/sponsor records remain authoritative.");
        TextField date=field(LocalDate.now().toString()), employer=field(""), project=field(""), hours=field("8"), notes=field(""); ChoiceBox<String> category=choice("B — Generating, Distribution and Service Systems","A — Common Occupational Skills","B — Generating, Distribution and Service Systems","C — Wiring Systems","D — Motors and Control Systems","E — Signalling and Communication Systems");
        Label status=status(); Label total=new Label(); total.setStyle("-fx-font-weight:bold;"); Runnable refresh=()->{double sum=0;for(JsonNode n:store.records("hours"))sum+=n.path("hours").asDouble();total.setText(String.format("Locally tracked hours: %.1f",sum));};
        Button save=button("Add hours");save.setOnAction(e->{try{ObjectNode n=json.createObjectNode();n.put("date",date.getText());n.put("employer",employer.getText());n.put("project",project.getText());n.put("category",category.getValue());n.put("hours",num(hours));n.put("notes",notes.getText());store.append("hours",n);status.setText("Hours saved locally.");refresh.run();}catch(Exception ex){status.setText(ex.getMessage());}}); refresh.run();
        root.getChildren().add(cardForm("Work entry",row("Date",date),row("Employer / sponsor",employer),row("Project",project),row("Task category",category),row("Hours",hours),row("Skills / notes",notes),save,status,total)); return root;
    }

    private Node credentials() {
        VBox root=page("Credential Wallet","Track training/certification expiry dates locally and see upcoming renewals.");
        TextField name=field("Working at Heights"), issuer=field(""), expiry=field(LocalDate.now().plusYears(3).toString()), notes=field(""); Label status=status(); TextArea list=output();
        Runnable refresh=()->{StringBuilder b=new StringBuilder();LocalDate today=LocalDate.now();for(JsonNode n:store.records("credentials")){String exp=n.path("expiry").asText("");String remaining="";try{long days=ChronoUnit.DAYS.between(today,LocalDate.parse(exp));remaining=days<0?"EXPIRED "+(-days)+" days ago":days+" days remaining";}catch(Exception ignored){}b.append(n.path("name").asText()).append(" — ").append(exp).append(" — ").append(remaining).append("\n");}list.setText(b.length()==0?"No credentials saved yet.":b.toString());};
        Button save=button("Save credential");save.setOnAction(e->{ObjectNode n=json.createObjectNode();n.put("name",name.getText());n.put("issuer",issuer.getText());n.put("expiry",expiry.getText());n.put("notes",notes.getText());store.append("credentials",n);status.setText("Credential saved.");refresh.run();}); refresh.run();
        root.getChildren().add(cardForm("Credential",row("Certification / training",name),row("Issuer",issuer),row("Expiry YYYY-MM-DD",expiry),row("Notes",notes),save,status),cardForm("Renewal status",list)); return scroll(root);
    }

    private Node wageCalculator() {
        VBox root=page("Canadian Electrician Wage Calculator","Estimate gross weekly/annual earnings from an hourly rate and overtime assumptions.");
        TextField rate=field("35"), regular=field("40"), overtime=field("5"), multiplier=field("1.5"), weeks=field("50"); TextArea out=output(); Button calc=button("Calculate earnings");
        calc.setOnAction(e->{try{double weekly=num(rate)*num(regular)+num(rate)*num(multiplier)*num(overtime),annual=weekly*num(weeks);out.setText(String.format("Regular weekly: $%.2f%nOvertime weekly: $%.2f%nGross weekly: $%.2f%nGross annual (%s weeks): $%.2f%n%nThis is a gross-pay estimate before taxes, dues, pension, benefits, travel or allowances.",num(rate)*num(regular),num(rate)*num(multiplier)*num(overtime),weekly,weeks.getText(),annual));}catch(Exception ex){out.setText(ex.getMessage());}});
        root.getChildren().add(cardForm("Gross earnings estimator",row("Hourly rate",rate),row("Regular hours/week",regular),row("Overtime hours/week",overtime),row("OT multiplier",multiplier),row("Working weeks/year",weeks),calc,out)); return root;
    }

    private Node jobMarket() {
        VBox root=page("Job & Career Dashboard","Use the local tools for planning; open official/current external sources only when you explicitly choose to.");
        Button jobBank=button("Open Canada Job Bank — electricians");jobBank.setOnAction(e->openUrl("https://www.jobbank.gc.ca/marketreport/summary-occupation/20677/ca"));
        Button redSeal=button("Open Red Seal Construction Electrician");redSeal.setOnAction(e->openUrl("https://www.red-seal.ca/eng/trades/const-electric.shtml"));
        Button sto=button("Open Skilled Trades Ontario");sto.setOnAction(e->openUrl("https://www.skilledtradesontario.ca/"));
        root.getChildren().add(cardForm("Official resources",new Label("These buttons intentionally open your normal browser; the rest of the platform remains native JavaFX."),new HBox(8,jobBank,redSeal,sto))); return root;
    }

    // ------------------------- UI HELPERS -------------------------
    private TabPane nestedTabs(){TabPane t=new TabPane();t.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);return t;}
    private Tab tab(String title,Node node){Tab t=new Tab(title);t.setContent(node);return t;}
    private VBox page(String title,String desc){VBox v=new VBox(12);v.setPadding(new Insets(16));Label h=new Label(title);h.setStyle("-fx-font-size:23px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");Label d=new Label(desc);d.setWrapText(true);d.setStyle("-fx-text-fill:#64748b;");v.getChildren().addAll(h,d);return v;}
    private VBox card(String title,String desc){VBox v=new VBox(10);v.setPadding(new Insets(16));v.setStyle(CARD);Label h=new Label(title);h.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");Label d=new Label(desc);d.setWrapText(true);d.setStyle("-fx-text-fill:#64748b;");v.getChildren().addAll(h,d);return v;}
    private VBox cardForm(String title,Node... nodes){VBox v=card(title,"");v.getChildren().addAll(nodes);return v;}
    private VBox cardText(String title,Label body){VBox v=card(title,"");v.getChildren().add(body);return v;}
    private Node refCard(String title,String text){VBox v=card(title,text);return v;}
    private HBox row(String label,Node control){Label l=new Label(label);l.setMinWidth(205);l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;");HBox h=new HBox(10,l,control);h.setAlignment(Pos.CENTER_LEFT);if(control instanceof Region r){HBox.setHgrow(r,Priority.ALWAYS);r.setMaxWidth(Double.MAX_VALUE);}return h;}
    private TextField field(String text){TextField f=new TextField(text);f.setStyle(INPUT);return f;}
    @SafeVarargs private final <T> ChoiceBox<T> choice(T selected,T... items){ChoiceBox<T> c=new ChoiceBox<>();c.getItems().addAll(items);c.setValue(selected);c.setMaxWidth(Double.MAX_VALUE);return c;}
    private Button button(String text){Button b=new Button(text);b.setStyle(PRIMARY);return b;}
    private Label status(){Label l=new Label();l.setWrapText(true);l.setStyle("-fx-text-fill:#64748b;");return l;}
    private TextArea output(){TextArea a=new TextArea();a.setEditable(false);a.setWrapText(true);a.setPrefRowCount(12);a.setStyle("-fx-font-family:Consolas;-fx-font-size:12px;");return a;}
    private ScrollPane scroll(Node n){ScrollPane s=new ScrollPane(n);s.setFitToWidth(true);s.setStyle("-fx-background:transparent;-fx-background-color:transparent;");return s;}
    private double num(TextField f){double v=Double.parseDouble(f.getText().trim());if(!Double.isFinite(v))throw new IllegalArgumentException("Invalid number");return v;}
    private double parse(String s,double fallback){try{return Double.parseDouble(s.trim());}catch(Exception e){return fallback;}}
    private void openUrl(String url){try{if(Desktop.isDesktopSupported())Desktop.getDesktop().browse(URI.create(url));}catch(Exception ignored){}}
    private RuleEntry r(String a,String b,String c){return new RuleEntry(a,b,c);}
    private record RuleEntry(String topic,String reference,String summary){}
}

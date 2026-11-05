package com.example.desktop;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Self-contained offline industrial troubleshooting checklists. OEM/site procedures control. */
public final class TroubleshootingKnowledgePane extends BorderPane {
    private record Guide(String category, String title, String decision, List<String> checks) {
        @Override public String toString() { return category+" · "+title; }
    }
    private static Guide g(String cat,String title,String decision,String... checks) {
        return new Guide(cat,title,decision,Arrays.asList(checks));
    }
    private static final List<String> COMMON=List.of(
        "SAFETY FIRST: Obtain site authorization, identify every energy source, isolate/LOTO, and prove absence of voltage with a properly rated tester.",
        "Interview operator: precise symptoms, operating mode, alarm timestamps, recent modifications and attempts to reset.",
        "Read the OEM schematic, control sequence, interlock and protection documentation before testing.",
        "Divide the circuit into source, protection, control power, command, I/O, output and mechanical load.",
        "Determine the expected values and test systematically by a qualified method; compare with known-good reference values.",
        "Correct the confirmed cause without defeating an interlock, protection, safety relay or grounding.",
        "Return to service through approved testing; log measurements, parts, settings, alarms and remaining work."
    );
    private static final List<Guide> GUIDES=List.of(
        g("Motors","Three-phase motor no start","No coil command → trace controls; contactor closed but no torque → power path, motor or load.","Check phase supply, fuse/breaker and starter event status.","Trace E-stop, STOP, overload NC, permissives and coil input.","After isolation inspect motor winding, cable, coupling and jam by OEM-approved procedures."),
        g("Motors","Contactor chattering","Low coil voltage → control supply; stable coil voltage → contactor mechanism.","Compare coil rating and measured control supply under approved conditions.","Inspect terminals, stop chain and seal-in auxiliary contact.","Look for PLC output cycling or intermittent permissive."),
        g("Motors","Single phasing / overload","Phase absent upstream → feeder/protection; only load-side absent → contactor/cable.","Log overload or phase-loss trip elements.","Investigate phase voltage/current balance by approved test procedure.","Inspect fuses, contactor poles and motor cable after isolation."),
        g("Controls","START STOP seal-in circuit","Motor only runs while START held → failed seal-in contact or PLC latch.","Review three-wire START NO, STOP NC and seal-in auxiliary.","Confirm E-stop/overload interlocks in series with command.","Check coil holding voltage and PLC latch/unlatch conditions."),
        g("Controls","Forward / reverse interlock","Both directions commanded → remove from service; never bypass interlocks.","Review mechanical and NC electrical cross-interlocks.","Trace direction command, selector and coil circuit.","Confirm stop before reverse and safe motor rotation."),
        g("Controls","ON-delay vs OFF-delay timer","TON delays ON; TOF holds output after signal OFF.","Draw input/output state vs elapsed time.","Verify timer type, timebase and NO/NC terminals.","Check effects of wrong replacement on motor or ventilation sequence."),
        g("PLC","PLC input ON output OFF","Rung false → permissive; rung true, field output off → module/supply/wiring.","Find the first false ladder contact in the path to output.","Verify controller mode, safety system and permissive status.","Check output module state and field device after logic verified."),
        g("PLC","4-20 mA loop incorrect","Correct loop current but wrong process display → engineering scaling.","Review loop supply, AI configuration and transmitter span.","Compare local process reading to actual mA signal.","Check (mA − 4)/16 × span + minimum and shield/EMI."),
        g("VFD","Overcurrent / acceleration trip","Trips on startup → output/motor; trips under load → torque/ramp.","Record when the fault occurs in the drive trend.","Check motor parameters, acceleration and mechanical load.","Inspect motor/cable only after isolation and approved drive disconnection."),
        g("VFD","DC-bus overvoltage","Trip during deceleration often indicates regeneration/inadequate braking.","Review deceleration ramp and input voltage alarms.","Identify overhauling load and braking hardware design.","HV DC bus requires OEM discharge and voltage verification."),
        g("VFD","Wrong direction","Correct command but wrong motor direction → approved output phasing.","Verify drive direction command and digital input map.","Confirm safe jog conditions and guarding.","Correct output phase with isolation and manufacturer instructions."),
        g("Mining BEV","Battery-electric vehicle will not enable","HV isolation/precharge alarms require authorized OEM/site HV specialist; do not bypass.","Secure machine and capture BMS, traction and thermal fault codes.","Review permitted low-voltage control and supervisory diagnostics.","Escalate HV contactor, precharge and isolation tests to qualified staff."),
        g("Mining","Underground sump pump fault","No start command → level/PLC; overcurrent → pump or motor.","Read sump level, floats and pump sequence.","Check starter/VFD and overload alarms.","Inspect impeller, discharge, cable/motor after site isolation."),
        g("Mining","Conveyor / crusher E-stop trip","Never jumper pull-cord or safety interlock.","Capture alarm sequence, pull-cord and belt drift status.","Inspect zero-speed, belt alignment and blockage switches.","Verify drive and safe restart sequence after correction."),
        g("Mining","Underground ventilation fan interlock","Ventilation permissives are life-safety circuits; notify mine control.","Review airflow, damper, gas and E-stop inputs.","Verify starter/VFD and command only after safety conditions assessed.","Restore and verify ventilation with approved site procedure."),
        g("Protection","Feeder breaker / protection trip","Do not repeatedly close into an unknown fault.","Record relay target/element, SOE and affected loads.","Read one-line and setting/coordination information.","Investigate downstream phase/ground/overload fault with switching authorization."),
        g("Protection","SEL/GE relay or RTU SCADA lost","Local protection healthy, remote data absent → comms/power/network.","Confirm relay local health and protected function.","Inspect network link, switch, supply and permitted logs.","Check DNP/RTU channel, data point mapping and clock under change control."),
        g("Distribution","Transformer overheating","Protective operation or dielectric warning → no re-energization pending investigation.","Review kVA loading, voltage and temperature trends.","Inspect cooling, enclosure and harmonic/neutral loading.","Refer winding/oil/insulation tests to qualified personnel."),
        g("Distribution","Excessive loaded voltage drop","Good open-circuit voltage but low under load → high resistance or feeder sizing.","Compare source and load-end measurements at matched load.","Review conductor material, size, length and load current.","Inspect joints, contacts and phase imbalance under appropriate isolation."),
        g("Life safety","Fire alarm ground/open trouble","Never leave life-safety circuits disabled without formal impairment control.","Find exact panel trouble and affected zone.","Verify EOL/supervision, device wiring and configuration by listed procedures.","Restore complete operation and document functional acceptance."),
        g("HVAC","Furnace / makeup-air lockout","Never jumper flame/pressure/gas interlocks; qualified combustion service required.","Map call, fan, airflow proof, purge, ignition and flame prove.","Read lockout stage, BAS enable and safety limit status.","Escalate burner/gas train testing to licensed/authorized trade.")
    );
    private final ListView<Guide> list=new ListView<>();
    private final TextField search=new TextField();
    private final VBox checkboxes=new VBox(7);
    private final Label title=new Label("Choose a fault");
    private final Label decision=new Label("");
    private final TextField asset=new TextField();
    private final TextArea notes=new TextArea();
    private final Label status=new Label("Choose a procedure to start.");
    private Guide selected;

    public TroubleshootingKnowledgePane() {
        VBox root=new VBox(11);
        root.setPadding(new Insets(15));
        Label heading=new Label("Industrial Troubleshooting · native offline knowledge base");
        heading.setStyle("-fx-font-size:21px;-fx-font-weight:bold;");
        Label intro=new Label("General systematic Epiroc-style diagnostics for electricians, controls and mining maintenance. OEM/site instructions and required qualified authorization always supersede these checklists.");
        intro.setWrapText(true);
        Label safety=new Label("SAFETY GATE: Hazard assessment, LOTO, zero-energy verification and qualified HV/battery procedures. Never bypass an E-stop, safety relay, overload or isolation monitor.");
        safety.setWrapText(true);safety.setStyle("-fx-background-color:#fff3df;-fx-text-fill:#8b4300;-fx-padding:10;");
        search.setPromptText("Search: VFD, motor, relay, PLC, mining...");
        search.textProperty().addListener((o,oldText,newText)->filter(newText));
        VBox left=new VBox(9,new Label("Find a troubleshooting method"),search,list);
        VBox.setVgrow(list,Priority.ALWAYS);left.setPrefWidth(320);left.setMinWidth(220);
        title.setStyle("-fx-font-size:18px;-fx-font-weight:bold;");
        decision.setWrapText(true);
        VBox content=new VBox(10,title,new Label("Decision point"),decision,new Separator(),new Label("Diagnostic steps"),checkboxes,
            new Label("Asset / work order"),asset,new Label("Readings, symptoms, repairs, verification"),notes);
        notes.setPrefRowCount(4);notes.setWrapText(true);asset.setPromptText("e.g. Borden sump P-1");
        Button copy=new Button("Copy procedure");
        copy.setOnAction(e->copy());
        Button save=new Button("Save troubleshooting case locally");
        save.setStyle("-fx-background-color:#0e7490;-fx-text-fill:white;");
        save.setOnAction(e->save());
        status.setWrapText(true);
        content.getChildren().addAll(new HBox(10,copy,save),status);
        ScrollPane right=new ScrollPane(content);right.setFitToWidth(true);right.setPadding(new Insets(12));
        SplitPane panes=new SplitPane(left,right);panes.setDividerPositions(.33);
        VBox.setVgrow(panes,Priority.ALWAYS);
        root.getChildren().addAll(heading,intro,safety,panes);
        setCenter(root);
        filter("");
        list.getSelectionModel().selectedItemProperty().addListener((o,a,b)->select(b));
        list.getSelectionModel().selectFirst();
    }
    private void filter(String query) {
        String q=query.toLowerCase(Locale.ROOT).trim();
        list.setItems(FXCollections.observableArrayList(GUIDES.stream()
            .filter(g->(g.category()+" "+g.title()+" "+g.decision()).toLowerCase(Locale.ROOT).contains(q))
            .toList()));
    }
    private void select(Guide g) {
        if(g==null)return;
        selected=g;title.setText(g.title());decision.setText(g.decision());
        checkboxes.getChildren().clear();
        List<String> steps=new ArrayList<>(COMMON.subList(0,4));
        steps.addAll(g.checks());steps.addAll(COMMON.subList(4,COMMON.size()));
        for(int i=0;i<steps.size();i++){
            CheckBox c=new CheckBox((i+1)+". "+steps.get(i));c.setWrapText(true);
            checkboxes.getChildren().add(c);
        }
        asset.clear();notes.clear();status.setText("Safety-first checklist loaded. Follow OEM procedures.");
    }
    private String report() {
        if(selected==null)return "No procedure selected.";
        StringBuilder b=new StringBuilder(selected.category()+" · "+selected.title()+"\nDecision: "+selected.decision()+"\n");
        for(var n:checkboxes.getChildren())if(n instanceof CheckBox c)b.append(c.isSelected()?"[X] ":"[ ] ").append(c.getText()).append("\n");
        b.append("Asset: ").append(asset.getText()).append("\nNotes: ").append(notes.getText()).append("\n");
        return b.toString();
    }
    private void copy() {
        ClipboardContent c=new ClipboardContent();c.putString(report());Clipboard.getSystemClipboard().setContent(c);
        status.setText("Procedure and notes copied.");
    }
    private void save() {
        try {
            Path folder=Path.of(System.getProperty("user.home"),".electrical-workbench");
            Files.createDirectories(folder);
            Path file=folder.resolve("troubleshooting-cases.txt");
            String data="\n--- "+LocalDateTime.now()+" ---\n"+report().replace("\u0000","")+"\n";
            Files.writeString(file,data,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            status.setText("Saved locally: "+file);
        }catch(Exception e){status.setText("Save failed: "+e.getMessage());}
    }
}

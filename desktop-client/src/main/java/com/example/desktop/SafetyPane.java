package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Native JavaFX electrical-safety workspace.
 *
 * This module is intentionally a planning/recordkeeping aid. It does not authorize energized work,
 * replace an employer hazard assessment, or reproduce proprietary CSA Z462 tables. Where CSA shock
 * approach boundaries or arc-flash incident energy are required, the user enters values from the
 * equipment label, study, employer procedure, or other authorized source.
 */
public final class SafetyPane extends BorderPane {
    private final ObjectMapper json;
    private final String username;
    private final LocalWorkspaceStore store;

    private static final String CARD = "-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#dfe7ef;-fx-border-radius:14;-fx-padding:16;";
    private static final String INPUT = "-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-border-radius:9;-fx-background-radius:9;-fx-padding:9 10;";
    private static final String PRIMARY = "-fx-background-color:#b45309;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;-fx-padding:10 14;";

    public SafetyPane(ObjectMapper json, String username) {
        this.json = json;
        this.username = username == null ? "default" : username;
        this.store = new LocalWorkspaceStore(json, this.username);

        VBox head = new VBox(4);
        Label title = new Label("Electrical Safety");
        title.setStyle("-fx-font-size:25px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label sub = new Label("Thunderstorm stand-down tracking, PPE and boundary checks, unsafe-condition records, rubber-glove tracking and electrical safety-equipment maintenance. Core records work offline.");
        sub.setWrapText(true); sub.setStyle("-fx-text-fill:#64748b;");
        head.getChildren().addAll(title, sub);
        setTop(head);
        BorderPane.setMargin(head, new Insets(0,0,14,0));

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
            tab("Safety Dashboard", dashboard()),
            tab("Thunderstorm / Lightning", new ThunderstormSafetyPane(json, username)),
            tab("PPE / Arc Flash", ppePlanner()),
            tab("Approach Distance", approachPlanner()),
            tab("Unsafe Conditions", unsafeConditions()),
            tab("Rubber Gloves", gloveTracker()),
            tab("Equipment Maintenance", maintenanceTracker())
        );
        setCenter(tabs);
    }

    private Node dashboard() {
        VBox root = new VBox(12);
        Label summary = new Label(); summary.setWrapText(true); summary.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        TextArea detail = output();
        Runnable refresh = () -> {
            int openHazards = 0, overdueGloves = 0, overdueEquipment = 0;
            LocalDate today = LocalDate.now();
            StringBuilder b = new StringBuilder();
            for (JsonNode n : store.records("unsafeConditions")) {
                if (!"RESOLVED".equalsIgnoreCase(n.path("status").asText())) openHazards++;
            }
            for (JsonNode n : store.records("rubberGloves")) {
                LocalDate due = parseDate(n.path("nextTestDue").asText(""));
                if (due != null && due.isBefore(today)) overdueGloves++;
            }
            for (JsonNode n : store.records("safetyMaintenance")) {
                LocalDate due = parseDate(n.path("nextDue").asText(""));
                if (due != null && due.isBefore(today)) overdueEquipment++;
            }
            summary.setText("Open unsafe-condition records: " + openHazards + "   •   Overdue glove tests: " + overdueGloves + "   •   Overdue equipment maintenance: " + overdueEquipment);
            b.append("SAFETY RECORDS\n\n");
            b.append("PPE / arc-flash assessments saved: ").append(store.records("safetyAssessments").size()).append('\n');
            b.append("Unsafe-condition records: ").append(store.records("unsafeConditions").size()).append('\n');
            b.append("Rubber glove records: ").append(store.records("rubberGloves").size()).append('\n');
            b.append("Safety-equipment records: ").append(store.records("safetyMaintenance").size()).append('\n');
            b.append("\nUse these records to support—not replace—your employer's electrical safety program, energized-work process, hazard/risk assessment, and manufacturer instructions.");
            detail.setText(b.toString());
        };
        Button reload = button("Refresh dashboard"); reload.setOnAction(e -> refresh.run()); refresh.run();
        root.getChildren().addAll(card("Current safety status", "Local summary for this user.", summary, reload, detail));
        return scroll(root);
    }

    private Node ppePlanner() {
        VBox root = new VBox(12);
        TextField task = field("Diagnostic testing / voltage verification");
        TextField equipment = field("");
        TextField voltage = field("600");
        TextField incident = field("8");
        TextField garment = field("12");
        TextField face = field("12");
        TextField gloveMax = field("1000");
        TextField source = field("Arc-flash label / employer study");
        TextArea result = output(); Label status = status();
        Button calc = button("Check entered PPE ratings");
        calc.setOnAction(e -> {
            try {
                double ie = num(incident), g = num(garment), f = num(face), sysV = num(voltage), gloveV = num(gloveMax);
                if (ie <= 0 || g <= 0 || f <= 0 || sysV <= 0 || gloveV <= 0) throw new IllegalArgumentException("Values must be greater than zero.");
                boolean garmentOk = g >= ie, faceOk = f >= ie, gloveVoltageOk = gloveV >= sysV;
                result.setText(String.format(Locale.CANADA,
                    "ENTERED HAZARD DATA\nSystem voltage: %.0f V\nIncident energy at working distance: %.2f cal/cm²\nSource: %s\n\nARC-RATING CHECK\nMinimum arc rating to compare against entered incident energy: %.2f cal/cm²\nGarment: %.2f → %s\nFace/head arc rating entered: %.2f → %s\n\nSHOCK-PROTECTION RATING CHECK\nGlove maximum-use voltage entered: %.0f V\nSystem voltage: %.0f V → %s\n\nIMPORTANT\nThis is a rating comparison only. It does not determine whether energized work is justified, whether an arc-flash hazard exists for the task, the complete PPE ensemble, shock approach boundaries, glove class, leather protector requirements, or equipment condition. Use the employer's hazard assessment, current CSA Z462 program, equipment label/study and manufacturer instructions.",
                    sysV, ie, source.getText(), ie, g, garmentOk ? "MEETS entered incident-energy rating" : "INSUFFICIENT", f, faceOk ? "MEETS entered incident-energy rating" : "INSUFFICIENT", gloveV, sysV, gloveVoltageOk ? "RATING NOT BELOW system voltage" : "INSUFFICIENT RATING"));
                status.setText((garmentOk && faceOk && gloveVoltageOk) ? "Entered ratings pass this limited comparison." : "One or more entered ratings are insufficient.");
            } catch (Exception ex) { result.setText(ex.getMessage()); status.setText("Check inputs."); }
        });
        Button save = button("Save assessment record");
        save.setOnAction(e -> {
            try {
                ObjectNode n = json.createObjectNode(); n.put("date", LocalDate.now().toString()); n.put("task", task.getText()); n.put("equipment", equipment.getText());
                n.put("voltage", num(voltage)); n.put("incidentEnergyCalCm2", num(incident)); n.put("garmentArcRating", num(garment)); n.put("faceArcRating", num(face)); n.put("gloveMaxUseVoltage", num(gloveMax)); n.put("source", source.getText());
                store.append("safetyAssessments", n); status.setText("Safety assessment record saved locally.");
            } catch (Exception ex) { status.setText(ex.getMessage()); }
        });
        root.getChildren().addAll(card("PPE / arc-flash rating check", "Enter incident energy and boundary information from an authorized arc-flash label, engineering study or employer procedure. The app compares ratings; it does not generate incident energy from scratch.",
            grid(row("Task", task), row("Equipment / location", equipment), row("Nominal system voltage (V)", voltage), row("Incident energy (cal/cm²)", incident), row("Garment arc rating (cal/cm²)", garment), row("Face/head arc rating (cal/cm²)", face), row("Glove maximum-use voltage (V)", gloveMax), row("Hazard-data source", source)),
            new HBox(8, calc, save), status, result));
        return scroll(root);
    }

    private Node approachPlanner() {
        VBox root = new VBox(12);

        TextField voltage = field("13800"); TextArea ontarioOut = output();
        Button ontario = button("Calculate Ontario overhead minimum");
        ontario.setOnAction(e -> {
            try {
                double v = num(voltage);
                String r;
                if (v < 750) r = "The O. Reg. 213/91 s.188 overhead-conductor table starts at 750 V. Use the applicable employer/site procedure and electrical-safety assessment for lower-voltage proximity work.";
                else if (v <= 150000) r = "Minimum distance: 3.0 m";
                else if (v <= 250000) r = "Minimum distance: 4.5 m";
                else r = "Minimum distance: 6.0 m";
                ontarioOut.setText(String.format(Locale.CANADA, "Nominal phase-to-phase voltage: %.0f V\n%s\n\nOntario construction reference: O. Reg. 213/91, s.188, for energized overhead electrical conductors unless the conditions in s.189 are satisfied. This is not a CSA Z462 shock-boundary lookup.", v, r));
            } catch (Exception ex) { ontarioOut.setText(ex.getMessage()); }
        });

        TextField limited = field("1.0"), restricted = field("0.3"), arc = field("1.5"), planned = field("2.0"), boundarySource = field("Employer shock/arc-flash assessment");
        TextArea boundaryOut = output(); Button check = button("Check planned working distance");
        check.setOnAction(e -> {
            try {
                double l=num(limited), r=num(restricted), a=num(arc), p=num(planned);
                String shock = p < r ? "INSIDE entered restricted approach boundary" : p < l ? "INSIDE entered limited approach boundary" : "OUTSIDE entered limited/restricted boundaries";
                String arcState = p < a ? "INSIDE entered arc-flash boundary" : "OUTSIDE entered arc-flash boundary";
                boundaryOut.setText(String.format(Locale.CANADA, "Planned closest distance: %.3f m\nShock status: %s\nArc-flash status: %s\n\nBoundary source: %s\n\nThe app intentionally requires authorized boundary values to be entered instead of reproducing proprietary CSA Z462 tables. Crossing a boundary may require qualified-person controls, shock/arc PPE, energized-work procedures or other controls defined by the employer's program.", p, shock, arcState, boundarySource.getText()));
            } catch(Exception ex) { boundaryOut.setText(ex.getMessage()); }
        });

        root.getChildren().addAll(
            card("Ontario construction — energized overhead conductors", "Public Ontario minimum-distance table for construction projects.", grid(row("Nominal phase-to-phase voltage (V)", voltage)), ontario, ontarioOut),
            card("Authorized shock / arc-flash boundary checker", "Enter limited, restricted and arc-flash boundaries from the current employer assessment, label or authorized study, then compare the planned closest approach.", grid(row("Limited approach boundary (m)", limited), row("Restricted approach boundary (m)", restricted), row("Arc-flash boundary (m)", arc), row("Planned closest distance (m)", planned), row("Boundary source", boundarySource)), check, boundaryOut)
        );
        return scroll(root);
    }

    private Node unsafeConditions() {
        VBox root = new VBox(12);
        TextField date = field(LocalDate.now().toString()), project = field(""), location = field(""), description = field("");
        ChoiceBox<String> category = choice("Electrical hazard", "Electrical hazard", "Damaged equipment", "Improper guarding", "Housekeeping", "LOTO / isolation", "PPE", "Overhead line", "Grounding / bonding", "Other");
        ChoiceBox<String> severity = choice("High", "Low", "Medium", "High", "Critical");
        ChoiceBox<String> recordStatus = choice("OPEN", "OPEN", "CONTROLLED", "RESOLVED");
        TextField immediate = field(""), photo = field(""); photo.setEditable(false);
        Label status = status(); TextArea records = output();
        Button attach = button("Attach unsafe-condition photo");
        attach.setOnAction(e -> {
            FileChooser fc = new FileChooser(); fc.setTitle("Select safety photo");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png", "*.webp"));
            File selected = fc.showOpenDialog(attach.getScene().getWindow());
            if (selected != null) {
                try { photo.setText(copySafetyPhoto(selected.toPath()).toString()); status.setText("Photo copied into the local safety workspace."); }
                catch (Exception ex) { status.setText("Could not attach photo: " + ex.getMessage()); }
            }
        });
        Runnable refresh = () -> {
            StringBuilder b=new StringBuilder();
            for(JsonNode n:store.records("unsafeConditions")) b.append(n.path("date").asText()).append(" | ").append(n.path("severity").asText()).append(" | ").append(n.path("status").asText()).append(" | ").append(n.path("project").asText()).append(" | ").append(n.path("description").asText()).append(n.path("photoPath").asText("").isBlank()?"":" | photo saved").append('\n');
            records.setText(b.length()==0?"No unsafe-condition records saved yet.":b.toString());
        };
        Button save = button("Save unsafe-condition record");
        save.setOnAction(e -> {
            ObjectNode n=json.createObjectNode();n.put("date",date.getText());n.put("project",project.getText());n.put("location",location.getText());n.put("category",category.getValue());n.put("severity",severity.getValue());n.put("description",description.getText());n.put("immediateAction",immediate.getText());n.put("status",recordStatus.getValue());n.put("photoPath",photo.getText());
            store.append("unsafeConditions",n);status.setText("Unsafe-condition record saved locally.");refresh.run();
        }); refresh.run();
        root.getChildren().addAll(card("Unsafe-condition / near-miss record", "Save observations and optional photos locally. Android can later use the same record model with direct camera capture and cloud sync.", grid(row("Date",date),row("Project / site",project),row("Specific location",location),row("Category",category),row("Severity",severity),row("Description",description),row("Immediate control / action",immediate),row("Status",recordStatus),row("Photo",photo)),new HBox(8,attach,save),status),card("Saved records", "Local safety history for this user.", records));
        return scroll(root);
    }

    private Node gloveTracker() {
        VBox root = new VBox(12);
        TextField asset=field("GLV-001"), manufacturer=field(""), gloveClass=field(""), maxVoltage=field(""), lastTest=field(LocalDate.now().toString()), notes=field("");
        CheckBox inService=new CheckBox("Currently in service");inService.setSelected(true);
        CheckBox ontarioInterval=new CheckBox("Apply Ontario construction >5 kV dielectric-test interval");ontarioInterval.setSelected(true);
        Label status=status();TextArea list=output();
        Runnable refresh=()->{StringBuilder b=new StringBuilder();LocalDate today=LocalDate.now();for(JsonNode n:store.records("rubberGloves")){String due=n.path("nextTestDue").asText("");LocalDate d=parseDate(due);String state=d==null?"manual/unspecified":d.isBefore(today)?"OVERDUE by "+ChronoUnit.DAYS.between(d,today)+" days":ChronoUnit.DAYS.between(today,d)+" days remaining";b.append(n.path("assetId").asText()).append(" | class ").append(n.path("gloveClass").asText()).append(" | next test ").append(due).append(" | ").append(state).append('\n');}list.setText(b.length()==0?"No glove records saved yet.":b.toString());};
        Button save=button("Save / add glove record");save.setOnAction(e->{try{
            LocalDate last=LocalDate.parse(lastTest.getText().trim());String next="";double v=maxVoltage.getText().isBlank()?0:Double.parseDouble(maxVoltage.getText().trim());
            if(ontarioInterval.isSelected() && v>5000) next=last.plusMonths(inService.isSelected()?3:6).toString();
            ObjectNode n=json.createObjectNode();n.put("assetId",asset.getText());n.put("manufacturer",manufacturer.getText());n.put("gloveClass",gloveClass.getText());n.put("maxUseVoltage",v);n.put("inService",inService.isSelected());n.put("lastDielectricTest",last.toString());n.put("nextTestDue",next);n.put("notes",notes.getText());n.put("preUseInspectionReminder","Air test and visually inspect immediately before each use");store.append("rubberGloves",n);status.setText(next.isBlank()?"Glove saved. Set the next dielectric-test date according to your employer/manufacturer program.":"Glove saved. Next Ontario construction >5 kV test date: "+next);refresh.run();
        }catch(Exception ex){status.setText("Check date/voltage: "+ex.getMessage());}});refresh.run();
        root.getChildren().addAll(card("Rubber insulating glove record", "Ontario construction rules require air testing and visual inspection immediately before each use. For gloves rated above 5,000 V AC, the Ontario construction regulation specifies periodic certification intervals; the tracker can calculate those dates.",grid(row("Asset ID",asset),row("Manufacturer",manufacturer),row("Glove class / marking",gloveClass),row("Maximum-use voltage from glove (V)",maxVoltage),row("Last dielectric test YYYY-MM-DD",lastTest),row("Service status",inService),row("Ontario >5 kV interval",ontarioInterval),row("Notes",notes)),save,status),card("Glove test status", "Always perform the required pre-use air test and visual inspection regardless of periodic certification date.",list));
        return scroll(root);
    }

    private Node maintenanceTracker() {
        VBox root=new VBox(12);
        ChoiceBox<String> type=choice("Voltage detector / meter","Voltage detector / meter","Arc-flash suit","Arc-rated face shield / hood","Insulated hand tools","Hot stick / live-line tool","Grounding set","Insulating mat / blanket","Rescue hook","LOTO kit","Test leads","Other");
        TextField asset=field(""), manufacturer=field(""), model=field(""), last=field(LocalDate.now().toString()), interval=field("180"), notes=field("");
        ChoiceBox<String> condition=choice("SERVICEABLE","SERVICEABLE","INSPECT / REPAIR","OUT OF SERVICE");Label status=status();TextArea list=output();
        Runnable refresh=()->{StringBuilder b=new StringBuilder();LocalDate today=LocalDate.now();for(JsonNode n:store.records("safetyMaintenance")){LocalDate d=parseDate(n.path("nextDue").asText(""));String state=d==null?"":d.isBefore(today)?"OVERDUE":"due in "+ChronoUnit.DAYS.between(today,d)+" days";b.append(n.path("assetId").asText()).append(" | ").append(n.path("type").asText()).append(" | ").append(n.path("condition").asText()).append(" | next ").append(n.path("nextDue").asText()).append(" | ").append(state).append('\n');}list.setText(b.length()==0?"No safety-equipment maintenance records saved yet.":b.toString());};
        Button save=button("Save maintenance record");save.setOnAction(e->{try{LocalDate ld=LocalDate.parse(last.getText().trim());int days=(int)Math.round(num(interval));LocalDate next=ld.plusDays(days);ObjectNode n=json.createObjectNode();n.put("type",type.getValue());n.put("assetId",asset.getText());n.put("manufacturer",manufacturer.getText());n.put("model",model.getText());n.put("condition",condition.getValue());n.put("lastMaintenance",ld.toString());n.put("intervalDays",days);n.put("nextDue",next.toString());n.put("notes",notes.getText());store.append("safetyMaintenance",n);status.setText("Maintenance record saved. Next due: "+next);refresh.run();}catch(Exception ex){status.setText("Check inputs: "+ex.getMessage());}});refresh.run();
        root.getChildren().addAll(card("Electrical safety equipment maintenance", "Track inspection/test/maintenance dates for meters, live-line tools, arc-flash PPE, insulated tools, grounding equipment, mats/blankets and other safety assets. Use manufacturer and employer intervals where they are more stringent.",grid(row("Equipment type",type),row("Asset ID / serial",asset),row("Manufacturer",manufacturer),row("Model",model),row("Condition",condition),row("Last inspection/test YYYY-MM-DD",last),row("Interval (days)",interval),row("Notes",notes)),save,status),card("Maintenance status", "This is a recordkeeping aid; equipment-specific standards and manufacturer requirements still govern testing and retirement criteria.",list));
        return scroll(root);
    }

    private Path copySafetyPhoto(Path source) throws IOException {
        String safe = username.replaceAll("[^A-Za-z0-9._-]", "_");
        Path dir = Path.of(System.getProperty("user.home"), ".electrical-workbench", "safety-media", safe);
        Files.createDirectories(dir);
        String name = source.getFileName().toString();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ".jpg";
        Path target = dir.resolve(LocalDate.now() + "-" + System.currentTimeMillis() + ext.toLowerCase(Locale.ROOT));
        return Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private Tab tab(String title, Node content){Tab t=new Tab(title);t.setContent(content);return t;}
    private ScrollPane scroll(Node n){ScrollPane s=new ScrollPane(n);s.setFitToWidth(true);s.setStyle("-fx-background:transparent;-fx-background-color:transparent;");return s;}
    private VBox card(String title,String desc,Node... content){VBox b=new VBox(10);b.setStyle(CARD);Label h=new Label(title);h.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");Label d=new Label(desc);d.setWrapText(true);d.setStyle("-fx-text-fill:#64748b;");b.getChildren().addAll(h,d);b.getChildren().addAll(content);return b;}
    private GridPane grid(Node... rows){GridPane g=new GridPane();g.setHgap(12);g.setVgap(9);int r=0;for(Node n:rows){g.add(n,0,r++);GridPane.setHgrow(n,Priority.ALWAYS);}return g;}
    private HBox row(String label,Node control){Label l=new Label(label);l.setMinWidth(245);l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;");HBox h=new HBox(12,l,control);h.setAlignment(Pos.CENTER_LEFT);HBox.setHgrow(control,Priority.ALWAYS);if(control instanceof Region rr)rr.setMaxWidth(Double.MAX_VALUE);return h;}
    private TextField field(String value){TextField f=new TextField(value);f.setStyle(INPUT);return f;}
    private ChoiceBox<String> choice(String selected,String... items){ChoiceBox<String> c=new ChoiceBox<>();c.getItems().addAll(items);c.setValue(selected);c.setMaxWidth(Double.MAX_VALUE);return c;}
    private Button button(String text){Button b=new Button(text);b.setStyle(PRIMARY);return b;}
    private Label status(){Label l=new Label();l.setWrapText(true);l.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");return l;}
    private TextArea output(){TextArea a=new TextArea();a.setEditable(false);a.setWrapText(true);a.setPrefRowCount(10);a.setStyle("-fx-font-family:Consolas;-fx-font-size:12px;-fx-control-inner-background:#0f172a;-fx-text-fill:#e2e8f0;");return a;}
    private double num(TextField f){String s=f.getText().trim();if(s.isEmpty())throw new IllegalArgumentException("A required value is blank.");double v=Double.parseDouble(s);if(!Double.isFinite(v))throw new IllegalArgumentException("Invalid number.");return v;}
    private LocalDate parseDate(String s){try{return s==null||s.isBlank()?null:LocalDate.parse(s);}catch(Exception ex){return null;}}
}

package com.example.desktop;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

import java.util.Locale;

/** Offline, deliberately conservative one-circuit field worksheet on Windows. */
public final class CircuitReviewPane extends BorderPane {
    private static final String CARD =
        "-fx-background-color:#fff;-fx-border-color:#dbe5ed;-fx-border-radius:12;-fx-background-radius:12;-fx-padding:15;";
    private static final String LABEL =
        "-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#20384a;";
    private static final String WARNING =
        "-fx-font-size:13px;-fx-text-fill:#9a4c12;";

    private final TextField load = field("40");
    private final TextField voltage = field("600");
    private final ChoiceBox<Integer> phases = ints(3, 1, 3);
    private final TextField length = field("40");
    private final TextField device = field("50");
    private final ChoiceBox<Double> multiplier = multipliers();
    private final TextField conductor = field("Selected conductor");
    private final ChoiceBox<String> table = tables();
    private final ChoiceBox<Integer> terminal = ints(75, 60, 75, 90);
    private final ChoiceBox<Integer> insulation = ints(90, 60, 75, 90);
    private final TextField base = field("85");
    private final TextField ambient = field("1.00");
    private final TextField grouping = field("1.00");
    private final TextField circularMils = field("66360");
    private final TextField kFactor = field("12.9");
    private final TextField vdTarget = field("3");
    private final TextArea report = new TextArea("Replace the sample inputs with verified site and conductor data.");
    private final Label status = new Label("Not code verified · no conductor or device has been approved.");

    public CircuitReviewPane() {
        setStyle("-fx-background-color:#f4f7fa;");
        VBox page = new VBox(13);
        page.setPadding(new Insets(16));
        page.setMaxWidth(860);
        Label title = new Label("Circuit + Conductor Review");
        title.setStyle("-fx-font-size:23px;-fx-font-weight:900;-fx-text-fill:#163c51;");
        Label sub = new Label(
            "One offline circuit worksheet for corrected ampacity, approximate voltage drop, maximum run, " +
            "and a conservative general-purpose device screen. No embedded code-table approval.");
        sub.setWrapText(true);
        sub.setStyle("-fx-text-fill:#587187;");

        GridPane setup = grid();
        setup.addRow(0,row("Actual load current (A)",load));
        setup.addRow(1,row("System voltage (V)",voltage));
        setup.addRow(2,row("Phase / circuit",phases));
        setup.addRow(3,row("One-way length (m)",length));
        setup.addRow(4,row("Selected breaker or fuse (A)",device));
        setup.addRow(5,row("General load sizing multiplier",multiplier));

        GridPane wire = grid();
        wire.addRow(0,row("Conductor / size label",conductor));
        wire.addRow(1,row("CEC ampacity reference",table));
        wire.addRow(2,row("Verified termination temp (°C)",terminal));
        wire.addRow(3,row("Insulation temp (°C)",insulation));
        wire.addRow(4,row("Ampacity from termination column (A)",base));
        wire.addRow(5,row("Verified ambient factor (Table 5A)",ambient));
        wire.addRow(6,row("Verified grouping factor (Table 5C)",grouping));

        GridPane voltageDrop = grid();
        voltageDrop.addRow(0,row("Selected conductor area (circular mils)",circularMils));
        voltageDrop.addRow(1,row("Conductor K factor (ohm·cmil/ft)",kFactor));
        voltageDrop.addRow(2,row("Entered voltage drop limit (%)",vdTarget));
        TitledPane vdOptions = new TitledPane("Voltage-drop inputs (confirm actual wire area and K)", voltageDrop);
        vdOptions.setExpanded(false);
        vdOptions.setCollapsible(true);

        Label caution = new Label(
            "Use the ampacity from the actual equipment TERMINATION-temperature column. " +
            "A 90°C wire insulation rating does not automatically authorize 90°C ampacity.");
        caution.setWrapText(true);
        caution.setStyle(WARNING);

        Button calculate = new Button("Review circuit");
        calculate.setMaxWidth(Double.MAX_VALUE);
        calculate.setMinHeight(46);
        calculate.setStyle("-fx-background-color:#0e7490;-fx-text-fill:white;-fx-background-radius:9;-fx-font-weight:800;");
        calculate.setOnAction(event -> calculate());

        Button copy = new Button("Copy worked result");
        copy.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(report.getText());
            Clipboard.getSystemClipboard().setContent(content);
        });
        copy.setMinHeight(40);

        report.setEditable(false);
        report.setWrapText(true);
        report.setPrefRowCount(19);
        report.setStyle("-fx-control-inner-background:#f8fbfd;-fx-font-family:monospace;");
        status.setWrapText(true);
        status.setStyle("-fx-text-fill:#854e0d;-fx-font-weight:bold;");

        Label footer = new Label(
            "NOT CODE VERIFIED. Confirm licensed CEC 2024 and Ontario amendments, conductor material/size, " +
            "termination marking, installation method, correction factor applicability, neutral counting, " +
            "protection requirements and upstream VD. The K-factor calculation is a resistive approximation; " +
            "motor, HVAC, and transformer circuits need a specific rule review.");
        footer.setWrapText(true);
        footer.setStyle(WARNING);

        page.getChildren().addAll(title,sub,
            card("1 · Circuit conditions",setup),
            card("2 · Selected conductor and installation",caution,wire,vdOptions),
            calculate,card("3 · Worked calculation",status,report,copy),
            footer);
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        setCenter(scroll);
    }

    private void calculate() {
        try {
            UnifiedCircuitMath.Input input = new UnifiedCircuitMath.Input(
                number(load),multiplier.getValue(),number(voltage),phases.getValue(),
                number(length),number(circularMils),number(kFactor),
                number(base),terminal.getValue(),insulation.getValue(),
                number(ambient),number(grouping),number(device),number(vdTarget));
            UnifiedCircuitMath.Result r = UnifiedCircuitMath.calculate(input);
            StringBuilder b = new StringBuilder();
            b.append(conductor.getText()).append(" · ").append(table.getValue())
             .append(" / ").append(terminal.getValue()).append("°C terminations\n\n");
            b.append("1 · LOAD / AMPACITY\n")
             .append(String.format(Locale.CANADA,"Required current = %.2f × %.2f = %.2f A%n",
                 input.loadA(),input.designMultiplier(),r.designCurrentA()))
             .append(String.format(Locale.CANADA,"Corrected ampacity = %.2f × %.2f × %.2f = %.2f A%n",
                 input.terminalColumnAmpacityA(),input.ambientFactor(),input.groupingFactor(),
                 r.adjustedAmpacityA()))
             .append(String.format(Locale.CANADA,"Ampacity margin = %.2f A%n",r.conductorMarginA()))
             .append("Ampacity screen: ").append(r.ampacityArithmeticFits()?"WITHIN INPUT LIMIT":"FAIL")
             .append("\n\n2 · VOLTAGE DROP\n")
             .append(String.format(Locale.CANADA,"Estimated drop = %.2f V (%.2f%%)%n",
                 r.voltageDropV(),r.voltageDropPercent()))
             .append(String.format(Locale.CANADA,"Chosen target = %.2f V (%.2f%%)%n",
                 r.allowableDropV(),input.maxVoltageDropPercent()))
             .append("VD screen: ").append(r.voltageDropArithmeticFits()?"WITHIN ENTERED TARGET":"EXCEEDS TARGET")
             .append(String.format(Locale.CANADA,"%nRequired circular-mil area for VD target = %.2f cmil%n",
                 r.minimumCircularMilsForDrop()))
             .append(String.format(Locale.CANADA,"Maximum one-way length at selected area = %.2f m%n",
                 r.maximumOneWayLengthM()))
             .append("\n3 · GENERAL-CIRCUIT OCPD SCREEN\n")
             .append(String.format(Locale.CANADA,"Entered device = %.2f A%n",input.selectedOcpdA()))
             .append(r.protectionScreenFits()?"Conservative arithmetic bounds satisfied":"REVIEW PROTECTION")
             .append("\n\nNOT CODE VERIFIED. No conductor or breaker is approved by this worksheet. ")
             .append("Check actual CEC/OESC tables, equipment and site installation.");
            report.setText(b.toString());
            status.setText(r.ampacityArithmeticFits()&&r.voltageDropArithmeticFits()
                    &&r.protectionScreenFits()
                ?"Arithmetic checks fit — NOT CODE VERIFIED"
                :"One or more arithmetic checks need review — NOT CODE VERIFIED");
        } catch (Exception ex) {
            report.setText("Check inputs: "+(ex.getMessage()==null?"Invalid numeric values":ex.getMessage()));
            status.setText("Cannot calculate · review entered values");
        }
    }

    private static double number(TextField field) {
        return Double.parseDouble(field.getText().trim());
    }
    private static TextField field(String value) {
        TextField f=new TextField(value);
        f.setMinHeight(39);
        f.setMaxWidth(Double.MAX_VALUE);
        return f;
    }
    private static ChoiceBox<Integer> ints(int selected,Integer... values) {
        ChoiceBox<Integer> box=new ChoiceBox<>();
        box.getItems().addAll(values);box.setValue(selected);
        return box;
    }
    private static ChoiceBox<Double> multipliers() {
        ChoiceBox<Double> box=new ChoiceBox<>();
        box.getItems().addAll(1.0,1.25);box.setValue(1.25);
        return box;
    }
    private static ChoiceBox<String> tables() {
        ChoiceBox<String> box=new ChoiceBox<>();
        box.getItems().addAll("Table 1","Table 2","Table 3","Table 4");box.setValue("Table 2");
        return box;
    }
    private static GridPane grid() {
        GridPane g=new GridPane();
        g.setHgap(12);g.setVgap(10);
        return g;
    }
    private static HBox row(String title,Node control) {
        Label label=new Label(title);label.setWrapText(true);label.setMinWidth(255);
        label.setPrefWidth(280);label.setStyle(LABEL);
        HBox line=new HBox(11,label,control);
        HBox.setHgrow(control,Priority.ALWAYS);
        if(control instanceof Region region)region.setMaxWidth(Double.MAX_VALUE);
        return line;
    }
    private static VBox card(String title,Node... children) {
        Label heading=new Label(title);
        heading.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:#0e506d;");
        VBox box=new VBox(11);box.setStyle(CARD);
        box.getChildren().add(heading);box.getChildren().addAll(children);
        return box;
    }
}

package com.example.desktop;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** CEC-oriented ampacity correction worksheet for ambient temperature and current-carrying conductor count. */
public final class AmpacityDeratingPane extends BorderPane {
    private static final String INPUT = "-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 9;";
    private static final String PRIMARY = "-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-padding:9 13;";
    private static final String SECONDARY = "-fx-background-color:#f8fafc;-fx-text-fill:#0f4c81;-fx-border-color:#c7d9e8;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 11;-fx-font-weight:bold;";
    private static final String CARD = "-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#dfe7ef;-fx-border-radius:14;-fx-padding:16;";

    private static final Map<Integer, double[]> AMBIENT = new LinkedHashMap<>();
    static {
        // Columns: 60 C, 75 C, 90 C insulation. NaN means that insulation rating is not valid at that ambient.
        AMBIENT.put(30, new double[]{1.00,1.00,1.00});
        AMBIENT.put(35, new double[]{0.91,0.94,0.96});
        AMBIENT.put(40, new double[]{0.82,0.88,0.91});
        AMBIENT.put(45, new double[]{0.71,0.82,0.87});
        AMBIENT.put(50, new double[]{0.58,0.75,0.82});
        AMBIENT.put(55, new double[]{0.41,0.67,0.76});
        AMBIENT.put(60, new double[]{Double.NaN,0.58,0.71});
        AMBIENT.put(65, new double[]{Double.NaN,0.47,0.65});
        AMBIENT.put(70, new double[]{Double.NaN,0.33,0.58});
        AMBIENT.put(75, new double[]{Double.NaN,Double.NaN,0.50});
        AMBIENT.put(80, new double[]{Double.NaN,Double.NaN,0.41});
    }

    public AmpacityDeratingPane() {
        VBox page = new VBox(13);
        page.setPadding(new Insets(2));

        Label title = new Label("Ampacity / Derating — CEC 2024");
        title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label subtitle = new Label("Rule 4-006: use ampacity AND relevant correction factors from the selected equipment TERMINATION-temperature column, not automatically the wire's higher insulation rating.");
        subtitle.setWrapText(true); subtitle.setStyle("-fx-text-fill:#64748b;");
        page.getChildren().addAll(title,subtitle);

        TextField baseAmpacity = field("55");
        ChoiceBox<Integer> insulation = choice(90,60,75,90);
        ChoiceBox<Integer> termination = choice(60,60,75,90);
        ChoiceBox<String> ambient = choice("50 °C","≤30 °C","35 °C","40 °C","45 °C","50 °C","55 °C","60 °C","65 °C","70 °C","75 °C","80 °C");
        TextField ccc = field("6");
        TextField load = field("40");
        TextField terminalLimit = field("");

        VBox inputs = card("1. Installation conditions", "Select the equipment's marked termination temperature (or applicable unmarked-equipment default). Enter the CEC Table 1–4 ampacity from THAT 60°C/75°C/90°C column. Verify the conductor insulation can support it.");
        inputs.getChildren().add(grid(
            row("Table ampacity at TERMINATION column (A)",baseAmpacity),
            row("Verified termination temperature (°C)",termination),
            row("Conductor insulation rating (°C)",insulation),
            row("Ambient temperature",ambient),
            row("Current-carrying conductors",ccc),
            row("Actual/design load (A, optional)",load),
            row("Additional equipment current ceiling (A, optional)",terminalLimit)
        ));

        Label countHelp = new Label("Count current-carrying conductors for the ampacity adjustment—not simply every wire physically in the raceway. Bonding conductors are not load conductors; neutral treatment depends on the circuit/loading arrangement. Enter the verified current-carrying count here.");
        countHelp.setWrapText(true);
        countHelp.setStyle("-fx-background-color:#f8fafc;-fx-border-color:#dbeafe;-fx-border-radius:9;-fx-background-radius:9;-fx-padding:10;-fx-text-fill:#334155;");

        TextArea result = new TextArea("Enter the installation, then calculate.");
        result.setEditable(false); result.setWrapText(true); result.setPrefRowCount(18);
        result.setStyle("-fx-font-family:Consolas;-fx-font-size:12px;-fx-control-inner-background:#0f172a;-fx-text-fill:#e2e8f0;");
        Label status = new Label(); status.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");

        Button calculate = primary("Calculate adjusted ampacity");
        Button copy = secondary("Copy result");
        Button reset = secondary("Reset example");

        calculate.setOnAction(e -> {
            try {
                double base = positive(baseAmpacity,"Starting ampacity");
                int rating = insulation.getValue();
                int terminationRating = termination.getValue();
                if(rating<terminationRating)
                    throw new IllegalArgumentException("Conductor insulation rating cannot be lower than the selected equipment termination temperature.");
                int amb = parseAmbient(ambient.getValue());
                int n = positiveInt(ccc,"Current-carrying conductor count");
                // CEC Rule 4-006 requires applicable corrections to the
                // selected TERMINATION temperature column, not the wire's
                // higher 90 °C insulation column.
                double tempFactor = ambientFactor(amb,terminationRating);
                double groupFactor = groupingFactor(n);
                double corrected = base * tempFactor * groupFactor;
                Double term = optionalPositive(terminalLimit);
                double finalAllowable = term == null ? corrected : Math.min(corrected,term);
                Double designLoad = optionalPositive(load);
                boolean pass = designLoad == null || designLoad <= finalAllowable + 1e-9;

                String loadLine = designLoad == null ? "Load check: not entered" : String.format(Locale.CANADA,"Load check: %.2f A ≤ %.2f A → %s",designLoad,finalAllowable,pass?"PASS":"FAIL");
                String terminalLine = term == null ? "Additional equipment current ceiling: not entered" : String.format(Locale.CANADA,"Additional equipment current ceiling: %.2f A; arithmetic comparison uses the lesser value.",term);

                result.setText(String.format(Locale.CANADA,
                    "RULE 4-006 CONDUCTOR AMPACITY REVIEW%n"
                    +"Status: %s%n%n"
                    +"Base ampacity (selected TERMINATION column): %.2f A%n"
                    +"Equipment termination column: %d °C%n"
                    +"Conductor insulation rating: %d °C%n"
                    +"Ambient temperature: %s%n"
                    +"Ambient factor for TERMINATION column: %.2f%n"
                    +"Current-carrying conductors: %d%n"
                    +"Grouping factor: %.2f%n"
                    +"Combined correction factor: %.4f%n"
                    +"Corrected ampacity: %.2f A%n"
                    +"%s%n"
                    +"Arithmetic comparison available: %.2f A%n"
                    +"%s%n%n"
                    +"Calculation: %.2f × %.2f × %.2f = %.2f A%n%n"
                    +"IMPORTANT: This is NOT a code-compliant conductor selection. Confirm Table 1/2/3/4 size and installation method; equipment marking or Rule 4-006 default, Table 5A/5C factors, current-carrying neutral count, cable/raceway conditions, OCPD and voltage drop. A 90°C-insulated conductor on a 60°C termination uses the 60°C ampacity/correction basis, NOT 90°C.",
                    pass?"MEETS ENTERED LOAD ARITHMETICALLY — NOT FIELD VERIFIED":"FAIL — ENTERED LOAD EXCEEDS ADJUSTED AMPACITY",
                    base,terminationRating,rating,ambient.getValue(),tempFactor,n,groupFactor,
                    tempFactor*groupFactor,corrected,terminalLine,finalAllowable,loadLine,
                    base,tempFactor,groupFactor,corrected));
                status.setText(pass?"Arithmetic check complete. Verify the actual terminal and CEC installation rules before use.":"Entered load exceeds the selected termination-column ampacity after corrections.");
            } catch (Exception ex) {
                status.setText("Check inputs");
                result.setText(ex.getMessage()==null?ex.toString():ex.getMessage());
            }
        });

        copy.setOnAction(e -> { ClipboardContent c = new ClipboardContent(); c.putString(result.getText()); Clipboard.getSystemClipboard().setContent(c); });
        reset.setOnAction(e -> { baseAmpacity.setText("55"); insulation.setValue(90); termination.setValue(60); ambient.setValue("50 °C"); ccc.setText("6"); load.setText("40"); terminalLimit.clear(); result.setText("Enter the installation, then calculate."); status.setText(""); });

        VBox factors = card("2. Automatic correction factors", "The worksheet selects the ambient factor for the TERMINATION temperature and multiplies it by the number-of-current-carrying-conductors factor. Check Rule 4-004/4-006 applicability and actual code tables.");
        factors.getChildren().add(countHelp);

        VBox outputs = card("3. Adjusted ampacity", "Compare entered load against the adjusted termination-column ampacity. Arithmetic adequacy does NOT establish CEC approval or actual equipment/termination suitability.");
        outputs.getChildren().addAll(new HBox(8,calculate,copy,reset),status,result);

        page.getChildren().addAll(inputs,factors,outputs);
        ScrollPane scroll = new ScrollPane(page); scroll.setFitToWidth(true); scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        setCenter(scroll);
    }

    private static double ambientFactor(int ambient,int rating) {
        double[] factors = AMBIENT.get(ambient);
        if (factors == null) throw new IllegalArgumentException("Select a supported ambient-temperature row.");
        int col = rating==60?0:rating==75?1:rating==90?2:-1;
        if (col < 0) throw new IllegalArgumentException("Unsupported insulation rating.");
        double factor = factors[col];
        if (!Double.isFinite(factor)) throw new IllegalArgumentException("The selected insulation rating has no usable correction factor at that ambient temperature. Select conductors/equipment suitable for the temperature.");
        return factor;
    }

    private static double groupingFactor(int count) {
        if (count <= 3) return 1.00;
        if (count <= 6) return 0.80;
        if (count <= 24) return 0.70;
        if (count <= 42) return 0.60;
        return 0.50;
    }

    private static int parseAmbient(String label) {
        if (label.startsWith("≤30")) return 30;
        return Integer.parseInt(label.replace("°C","").trim());
    }

    private VBox card(String title,String desc) { VBox b=new VBox(10); b.setPadding(new Insets(16)); b.setStyle(CARD); Label h=new Label(title); h.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#0f172a;"); Label d=new Label(desc); d.setWrapText(true); d.setStyle("-fx-text-fill:#64748b;"); b.getChildren().addAll(h,d); return b; }
    private GridPane grid(Node... rows) { GridPane g=new GridPane(); g.setHgap(12); g.setVgap(9); int r=0; for(Node n:rows){g.add(n,0,r++);GridPane.setHgrow(n,Priority.ALWAYS);} return g; }
    private HBox row(String label,Node control) { Label l=new Label(label); l.setMinWidth(260); l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;"); HBox h=new HBox(12,l,control); h.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(control,Priority.ALWAYS); if(control instanceof Region rr) rr.setMaxWidth(Double.MAX_VALUE); return h; }
    private TextField field(String value) { TextField f=new TextField(value); f.setStyle(INPUT); return f; }
    @SafeVarargs private final <T> ChoiceBox<T> choice(T selected,T... values) { ChoiceBox<T> c=new ChoiceBox<>(); c.getItems().addAll(values); c.setValue(selected); c.setMaxWidth(Double.MAX_VALUE); return c; }
    private Button primary(String text) { Button b=new Button(text); b.setStyle(PRIMARY); return b; }
    private Button secondary(String text) { Button b=new Button(text); b.setStyle(SECONDARY); return b; }
    private static double positive(TextField f,String name) { double v=Double.parseDouble(f.getText().trim()); if(!Double.isFinite(v)||v<=0) throw new IllegalArgumentException(name+" must be greater than zero."); return v; }
    private static int positiveInt(TextField f,String name) { int v=Integer.parseInt(f.getText().trim()); if(v<=0) throw new IllegalArgumentException(name+" must be at least 1."); return v; }
    private static Double optionalPositive(TextField f) { String s=f.getText().trim(); if(s.isEmpty()) return null; double v=Double.parseDouble(s); if(!Double.isFinite(v)||v<=0) throw new IllegalArgumentException("Optional ampacity/load values must be greater than zero when entered."); return v; }
}

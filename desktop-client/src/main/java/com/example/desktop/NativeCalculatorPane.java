package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Native JavaFX calculator workspace. The server remains the calculation/data engine,
 * but no calculator page is rendered in a browser or WebView.
 */
public final class NativeCalculatorPane extends BorderPane {
    private final PortalClient portal;
    private final ObjectMapper json;
    private final String username;
    private final String password;

    private static final String INPUT = "-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-border-radius:9;-fx-background-radius:9;-fx-padding:9 10;";
    private static final String PRIMARY = "-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;-fx-padding:10 14;";
    private static final String CARD = "-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#dfe7ef;-fx-border-radius:14;-fx-padding:16;";

    private static final Map<String, Double> CIRCULAR_MILS = new LinkedHashMap<>();
    static {
        CIRCULAR_MILS.put("14 AWG", 4110d);
        CIRCULAR_MILS.put("12 AWG", 6530d);
        CIRCULAR_MILS.put("10 AWG", 10380d);
        CIRCULAR_MILS.put("8 AWG", 16510d);
        CIRCULAR_MILS.put("6 AWG", 26240d);
        CIRCULAR_MILS.put("4 AWG", 41740d);
        CIRCULAR_MILS.put("3 AWG", 52620d);
        CIRCULAR_MILS.put("2 AWG", 66360d);
        CIRCULAR_MILS.put("1 AWG", 83690d);
        CIRCULAR_MILS.put("1/0 AWG", 105600d);
        CIRCULAR_MILS.put("2/0 AWG", 133100d);
        CIRCULAR_MILS.put("3/0 AWG", 167800d);
        CIRCULAR_MILS.put("4/0 AWG", 211600d);
        CIRCULAR_MILS.put("250 kcmil", 250000d);
        CIRCULAR_MILS.put("300 kcmil", 300000d);
        CIRCULAR_MILS.put("350 kcmil", 350000d);
        CIRCULAR_MILS.put("400 kcmil", 400000d);
        CIRCULAR_MILS.put("500 kcmil", 500000d);
        CIRCULAR_MILS.put("600 kcmil", 600000d);
        CIRCULAR_MILS.put("750 kcmil", 750000d);
        CIRCULAR_MILS.put("1000 kcmil", 1000000d);
    }

    public NativeCalculatorPane(PortalClient portal, ObjectMapper json, String username, String password) {
        this.portal = portal;
        this.json = json;
        this.username = username;
        this.password = password;

        VBox heading = new VBox(4);
        heading.setPadding(new Insets(0, 0, 14, 0));
        Label title = new Label("Electrical Calculators");
        title.setStyle("-fx-font-size:25px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label sub = new Label("Native JavaFX forms. Cloud APIs provide calculation results without redirecting you to the web app.");
        sub.setWrapText(true);
        sub.setStyle("-fx-text-fill:#64748b;");
        heading.getChildren().addAll(title, sub);
        setTop(heading);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
            tab("Transformer", transformerPane()),
            tab("Motor", motorPane()),
            tab("Capacitor Bank", capacitorPane()),
            tab("Refrigeration", refrigerationPane()),
            tab("Welder", welderPane()),
            tab("Tap / Splitter", tapPane()),
            tab("Voltage Drop", voltageDropPane()),
            tab("PVC Expansion", pvcExpansionPane()),
            tab("Conduit Bending", bendingPane())
        );
        setCenter(tabs);
    }

    private Tab tab(String title, Node content) {
        Tab t = new Tab(title);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        t.setContent(scroll);
        return t;
    }

    private Node transformerPane() {
        TextField kva = field("75");
        TextField pv = field("600");
        TextField sv = field("208");
        ChoiceBox<Integer> phases = phaseChoice();
        TextField design = field("1.25");
        TextField pocpd = field("1.25");
        TextField socpd = field("1.25");
        TextArea result = resultArea();
        Label status = statusLabel();
        Button calc = button("Calculate transformer");
        calc.setOnAction(e -> runApi(calc, status, result, "/transformer", Map.of(
            "kva", number(kva), "primaryVoltage", number(pv), "secondaryVoltage", number(sv), "phases", phases.getValue(),
            "designFactor", number(design), "primaryOcpdFactor", number(pocpd), "secondaryOcpdFactor", number(socpd), "edition", "CEC_2024"
        )));
        return form("Transformer sizing", "Calculates primary/secondary current, design ampacity and primary/secondary OCPD worksheet values.",
            grid(row("Transformer kVA", kva), row("Primary voltage", pv), row("Secondary voltage", sv), row("Phases", phases),
                row("Conductor design factor", design), row("Primary OCPD factor", pocpd), row("Secondary OCPD factor", socpd)), calc, status, result);
    }

    private Node motorPane() {
        TextField flc = field("100");
        TextField conductor = field("1.25");
        TextField overload = field("1.15");
        TextField ocpd = field("1.75");
        TextField disconnect = field("1.15");
        TextArea result = resultArea(); Label status = statusLabel(); Button calc = button("Calculate motor");
        calc.setOnAction(e -> runApi(calc, status, result, "/motor", Map.of(
            "flc", number(flc), "conductorFactor", number(conductor), "overloadFactor", number(overload),
            "ocpdFactor", number(ocpd), "disconnectFactor", number(disconnect), "edition", "CEC_2024"
        )));
        return form("Motor circuit sizing", "Native Section 28 worksheet for conductor ampacity, overload, disconnect and branch OCPD.",
            grid(row("Motor FLC (A)", flc), row("Conductor factor", conductor), row("Overload factor", overload),
                row("Branch OCPD factor", ocpd), row("Disconnect factor", disconnect)), calc, status, result);
    }

    private Node capacitorPane() {
        TextField kw = field("75"); TextField existing = field("0.80"); TextField target = field("0.95"); TextField voltage = field("600");
        ChoiceBox<Integer> phases = phaseChoice(); TextField conductor = field("1.35"); TextField ocpd = field("1.65");
        TextArea result = resultArea(); Label status = statusLabel(); Button calc = button("Calculate capacitor bank");
        calc.setOnAction(e -> runApi(calc, status, result, "/capacitor-bank", Map.of(
            "kw", number(kw), "existingPf", number(existing), "targetPf", number(target), "voltage", number(voltage),
            "phases", phases.getValue(), "conductorFactor", number(conductor), "ocpdFactor", number(ocpd), "edition", "CEC_2024"
        )));
        return form("Capacitor bank / power-factor correction", "Calculates required kvar, capacitor current, conductor load and OCPD worksheet values.",
            grid(row("Load (kW)", kw), row("Existing PF", existing), row("Target PF", target), row("Voltage", voltage), row("Phases", phases),
                row("Conductor factor", conductor), row("OCPD factor", ocpd)), calc, status, result);
    }

    private Node refrigerationPane() {
        TextField rla = field("28"); TextField mca = field(""); TextField mocp = field("");
        TextField cf = field("1.25"); TextField of = field("1.75"); TextField overload = field("1.15");
        TextArea result = resultArea(); Label status = statusLabel(); Button calc = button("Calculate refrigeration circuit");
        calc.setOnAction(e -> {
            Map<String,Object> payload = new LinkedHashMap<>();
            payload.put("rla", number(rla));
            payload.put("mca", optionalNumber(mca));
            payload.put("mocp", optionalNumber(mocp));
            payload.put("conductorFactor", number(cf)); payload.put("ocpdFactor", number(of)); payload.put("overloadFactor", number(overload)); payload.put("edition", "CEC_2024");
            runApi(calc, status, result, "/refrigeration", payload);
        });
        return form("Refrigeration / compressor circuit", "If nameplate MCA or MOCP is entered it is used; otherwise the worksheet factors are applied.",
            grid(row("RLA (A)", rla), row("Nameplate MCA (optional)", mca), row("Nameplate MOCP (optional)", mocp),
                row("Conductor factor", cf), row("OCPD factor", of), row("Overload factor", overload)), calc, status, result);
    }

    private Node welderPane() {
        TextField input = field("50"); TextField duty = field("0.70"); TextField cf = field("1.0"); TextField ocpd = field("2.0");
        TextField units = field("1"); TextField group = field("1.0");
        TextArea result = resultArea(); Label status = statusLabel(); Button calc = button("Calculate welder circuit");
        calc.setOnAction(e -> runApi(calc, status, result, "/welder", Map.of(
            "ratedInputA", number(input), "dutyCycleFactor", number(duty), "conductorFactor", number(cf), "ocpdFactor", number(ocpd),
            "units", integer(units), "groupDemandFactor", number(group), "edition", "CEC_2024"
        )));
        return form("Welder sizing", "Uses the duty-cycle and group-demand factors you enter, so CEC table factors can be applied without copying tables into the client.",
            grid(row("Rated input current (A)", input), row("Duty-cycle factor", duty), row("Conductor factor", cf),
                row("OCPD factor", ocpd), row("Number of welders", units), row("Group demand factor", group)), calc, status, result);
    }

    private Node tapPane() {
        TextField source = field("225"); TextField amp = field("100"); TextField ratio = field("0.333333"); TextField length = field("3"); TextField down = field("100");
        TextArea result = resultArea(); Label status = statusLabel(); Button calc = button("Check tap worksheet");
        calc.setOnAction(e -> runApi(calc, status, result, "/tap", Map.of(
            "sourceOcpd", number(source), "tapAmpacity", number(amp), "minimumRatio", number(ratio), "lengthM", number(length),
            "downstreamOcpd", number(down), "edition", "CEC_2024"
        )));
        return form("Tap / splitter worksheet", "Checks the selected ampacity ratio and downstream OCPD values. Verify the exact CEC tap length and installation conditions.",
            grid(row("Source OCPD (A)", source), row("Tap ampacity (A)", amp), row("Minimum ratio", ratio),
                row("Tap length (m)", length), row("Downstream OCPD (A)", down)), calc, status, result);
    }

    private Node voltageDropPane() {
        ChoiceBox<Integer> phases = phaseChoice();
        TextField voltage = field("600"); TextField current = field("100"); TextField length = field("100");
        ChoiceBox<String> lengthUnit = new ChoiceBox<>(); lengthUnit.getItems().addAll("m", "ft"); lengthUnit.setValue("m");
        ComboBox<String> size = new ComboBox<>(); size.getItems().addAll(CIRCULAR_MILS.keySet()); size.setValue("1/0 AWG"); size.setMaxWidth(Double.MAX_VALUE);
        TextField cmil = field(String.valueOf(CIRCULAR_MILS.get("1/0 AWG").intValue()));
        size.setOnAction(e -> cmil.setText(String.valueOf(CIRCULAR_MILS.get(size.getValue()).intValue())));
        ChoiceBox<String> material = new ChoiceBox<>(); material.getItems().addAll("Copper", "Aluminum", "Custom K"); material.setValue("Copper");
        TextField k = field("12.9");
        material.setOnAction(e -> { if ("Copper".equals(material.getValue())) k.setText("12.9"); else if ("Aluminum".equals(material.getValue())) k.setText("21.2"); });
        TextField allowed = field("3.0");
        TextArea result = resultArea();
        Button drop = button("Voltage drop"); Button maxLength = button("Maximum length"); Button solveK = button("Solve K factor");
        Label status = statusLabel();

        drop.setOnAction(e -> local(status, result, () -> {
            double lenFt = toFeet(number(length), lengthUnit.getValue());
            double factor = phases.getValue() == 3 ? Math.sqrt(3) : 2.0;
            double vd = factor * number(k) * number(current) * lenFt / number(cmil);
            double pct = vd / number(voltage) * 100.0;
            return String.format("Voltage drop: %.2f V%nVoltage drop: %.2f %%%nLoad voltage: %.2f V%n%nFormula: VD = %s × K × I × L ÷ CM%nVerify final conductor selection against CEC 2024 Table D3 and its notes where applicable.",
                vd, pct, number(voltage) - vd, phases.getValue() == 3 ? "√3" : "2");
        }));
        maxLength.setOnAction(e -> local(status, result, () -> {
            double allowedV = number(voltage) * number(allowed) / 100.0;
            double factor = phases.getValue() == 3 ? Math.sqrt(3) : 2.0;
            double ft = allowedV * number(cmil) / (factor * number(k) * number(current));
            return String.format("Maximum one-way length: %.1f ft%nMaximum one-way length: %.1f m%nAllowed drop: %.2f V (%.2f%%)%n%nThis is the K-factor engineering formula; verify CEC Table D3/notes for the final code calculation.", ft, ft / 3.280839895, allowedV, number(allowed));
        }));
        solveK.setOnAction(e -> local(status, result, () -> {
            double lenFt = toFeet(number(length), lengthUnit.getValue());
            double observedV = number(voltage) * number(allowed) / 100.0;
            double factor = phases.getValue() == 3 ? Math.sqrt(3) : 2.0;
            double solved = observedV * number(cmil) / (factor * number(current) * lenFt);
            return String.format("Solved K factor: %.4f ohm-cmil/ft%nUsing %.2f%% drop over %.1f %s.%n%nTypical K values vary with material and temperature; use the value required by your design method.", solved, number(allowed), number(length), lengthUnit.getValue());
        }));

        HBox buttons = new HBox(8, drop, maxLength, solveK); buttons.setAlignment(Pos.CENTER_LEFT);
        VBox box = card("Voltage drop / conductor length / K factor", "Native field calculator. Wire-size circular-mil values are provided as standard AWG geometry; CEC Table D3 remains the final code reference when the question calls for it.");
        box.getChildren().addAll(grid(row("System voltage", voltage), row("Current (A)", current), row("Phases", phases),
            row("One-way length", length), row("Length unit", lengthUnit), row("Conductor size", size), row("Circular mils", cmil),
            row("Material", material), row("K factor", k), row("Allowed / observed drop (%)", allowed)), buttons, status, result);
        return box;
    }

    private Node pvcExpansionPane() {
        TextField run = field("30"); TextField tMin = field("-20"); TextField tMax = field("40"); TextField coeff = field("0.052"); TextField travel = field("100");
        TextArea result = resultArea(); Label status = statusLabel(); Button calc = button("Calculate expansion and joint count");
        calc.setOnAction(e -> local(status, result, () -> {
            double delta = Math.abs(number(tMax) - number(tMin));
            double movement = number(run) * delta * number(coeff);
            int joints = (int)Math.ceil(movement / number(travel));
            return String.format("Temperature change: %.1f °C%nEstimated PVC movement: %.1f mm%nExpansion-joint travel entered: %.1f mm%nEstimated minimum fittings for movement capacity: %d%n%nFormula: movement = run length × ΔT × coefficient.%nUse the PVC raceway manufacturer's expansion coefficient/travel rating and verify the applicable CEC installation rule before final design.",
                delta, movement, number(travel), Math.max(1, joints));
        }));
        return form("PVC expansion", "Calculates thermal movement and a fitting-count estimate from the coefficient and movement capacity you enter.",
            grid(row("PVC run length (m)", run), row("Minimum temperature (°C)", tMin), row("Maximum temperature (°C)", tMax),
                row("Expansion coefficient (mm/m/°C)", coeff), row("Movement capacity per fitting (mm)", travel)), calc, status, result);
    }

    private Node bendingPane() {
        VBox page = new VBox(16);
        page.setPadding(new Insets(2));

        TextField offset = field("150"); TextField angle = field("30"); TextArea offsetResult = resultArea(); offsetResult.setPrefRowCount(5); Label offsetStatus = statusLabel(); Button offsetCalc = button("Calculate offset");
        offsetCalc.setOnAction(e -> local(offsetStatus, offsetResult, () -> {
            double a = Math.toRadians(number(angle)); if (a <= 0 || a >= Math.PI/2) throw new IllegalArgumentException("Angle must be between 0° and 90°.");
            double distance = number(offset) / Math.sin(a); double shrink = number(offset) * Math.tan(a / 2.0);
            return String.format("Distance between bend marks: %.2f%nEstimated shrink: %.2f%nMultiplier: %.4f%n%nFormulas: spacing = offset ÷ sin(angle); shrink = offset × tan(angle/2).", distance, shrink, 1.0/Math.sin(a));
        }));
        VBox offsetCard = card("Two-bend offset", "Use the same units for offset and returned spacing/shrink.");
        offsetCard.getChildren().addAll(grid(row("Offset height", offset), row("Bend angle (degrees)", angle)), offsetCalc, offsetStatus, offsetResult);

        TextField saddleH = field("100"); TextField sideAngle = field("22.5"); TextArea saddleResult = resultArea(); saddleResult.setPrefRowCount(5); Label saddleStatus = statusLabel(); Button saddleCalc = button("Calculate 3-point saddle");
        saddleCalc.setOnAction(e -> local(saddleStatus, saddleResult, () -> {
            double a = Math.toRadians(number(sideAngle)); if (a <= 0 || a >= Math.PI/4) throw new IllegalArgumentException("Side angle should be greater than 0° and less than 45°.");
            double each = number(saddleH) / Math.sin(a); double center = number(sideAngle) * 2.0;
            return String.format("Outside mark from center: %.2f each side%nOutside-to-outside spacing: %.2f%nCenter bend angle: %.1f°%nSide bends: %.1f° each", each, each*2, center, number(sideAngle));
        }));
        VBox saddleCard = card("3-point saddle", "Center bend is twice the entered side-bend angle.");
        saddleCard.getChildren().addAll(grid(row("Obstruction height", saddleH), row("Side bend angle (degrees)", sideAngle)), saddleCalc, saddleStatus, saddleResult);

        TextField stub = field("500"); TextField takeup = field("150"); TextArea stubResult = resultArea(); stubResult.setPrefRowCount(4); Label stubStatus = statusLabel(); Button stubCalc = button("Calculate 90° stub mark");
        stubCalc.setOnAction(e -> local(stubStatus, stubResult, () -> String.format("Mark from end: %.2f%n%nFormula: desired stub − bender take-up.", number(stub) - number(takeup))));
        VBox stubCard = card("90° stub", "Enter the take-up value for the actual bender and conduit size.");
        stubCard.getChildren().addAll(grid(row("Desired stub", stub), row("Bender take-up", takeup)), stubCalc, stubStatus, stubResult);

        page.getChildren().addAll(offsetCard, saddleCard, stubCard);
        return page;
    }

    private VBox form(String title, String description, GridPane inputs, Button calc, Label status, TextArea result) {
        VBox box = card(title, description);
        box.getChildren().addAll(inputs, calc, status, result);
        return box;
    }

    private VBox card(String title, String description) {
        VBox box = new VBox(11);
        box.setPadding(new Insets(18));
        box.setStyle(CARD);
        Label t = new Label(title); t.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label d = new Label(description); d.setWrapText(true); d.setStyle("-fx-text-fill:#64748b;");
        box.getChildren().addAll(t, d);
        return box;
    }

    private GridPane grid(Node[]... rows) {
        GridPane g = new GridPane(); g.setHgap(12); g.setVgap(10); g.setMaxWidth(760);
        ColumnConstraints label = new ColumnConstraints(); label.setMinWidth(210); label.setPrefWidth(250);
        ColumnConstraints input = new ColumnConstraints(); input.setHgrow(Priority.ALWAYS); input.setFillWidth(true);
        g.getColumnConstraints().addAll(label, input);
        int r = 0;
        for (Node[] pair : rows) { g.add(pair[0],0,r); g.add(pair[1],1,r); GridPane.setHgrow(pair[1], Priority.ALWAYS); r++; }
        return g;
    }

    private Node[] row(String label, Node field) {
        Label l = new Label(label); l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;");
        if (field instanceof Control c) c.setMaxWidth(Double.MAX_VALUE);
        return new Node[]{l, field};
    }

    private TextField field(String value) { TextField f = new TextField(value); f.setStyle(INPUT); return f; }
    private ChoiceBox<Integer> phaseChoice() { ChoiceBox<Integer> p = new ChoiceBox<>(); p.getItems().addAll(1,3); p.setValue(3); return p; }
    private Button button(String text) { Button b = new Button(text); b.setStyle(PRIMARY); return b; }
    private Label statusLabel() { Label l = new Label(); l.setWrapText(true); l.setStyle("-fx-text-fill:#475569;-fx-font-size:12px;"); return l; }
    private TextArea resultArea() { TextArea a = new TextArea(); a.setEditable(false); a.setWrapText(true); a.setPrefRowCount(12); a.setStyle("-fx-font-family:'Consolas';-fx-font-size:12px;"); return a; }

    private void runApi(Button button, Label status, TextArea result, String endpoint, Map<String,Object> payload) {
        try {
            String body = json.writeValueAsString(payload);
            button.setDisable(true); status.setText("Calculating…"); result.clear();
            CompletableFuture.supplyAsync(() -> {
                try { return portal.calculate(endpoint, username, password, body); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            }).whenComplete((response, err) -> Platform.runLater(() -> {
                button.setDisable(false);
                if (err != null) {
                    Throwable c = unwrap(err); status.setText("Calculation failed: " + message(c)); return;
                }
                try {
                    JsonNode node = json.readTree(response);
                    result.setText(prettyResult(node)); status.setText("Calculated using the cloud engine; UI remains native JavaFX.");
                } catch (Exception ex) { status.setText("Could not read calculation result: " + ex.getMessage()); }
            }));
        } catch (Exception ex) { status.setText(ex.getMessage()); }
    }

    private void local(Label status, TextArea result, Calculation calculation) {
        try { result.setText(calculation.run()); status.setText("Calculated locally in the JavaFX desktop app."); }
        catch (Exception ex) { status.setText("Check the entered values: " + ex.getMessage()); }
    }

    private String prettyResult(JsonNode node) {
        StringBuilder out = new StringBuilder();
        node.fields().forEachRemaining(e -> {
            String key = pretty(e.getKey()); JsonNode v = e.getValue();
            if (v.isObject()) {
                out.append(key).append(":\n");
                v.fields().forEachRemaining(x -> out.append("  ").append(pretty(x.getKey())).append(": ").append(value(x.getValue())).append('\n'));
            } else out.append(key).append(": ").append(value(v)).append('\n');
        });
        return out.toString().trim();
    }

    private static String value(JsonNode n) { return n == null || n.isNull() ? "—" : n.isTextual() ? n.asText() : n.toString(); }
    private static String pretty(String s) { return s.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_',' ').replaceAll("\\s+"," ").trim(); }
    private static Throwable unwrap(Throwable t) { Throwable c=t; while(c.getCause()!=null && c!=c.getCause()) c=c.getCause(); return c; }
    private static String message(Throwable t) { return t.getMessage()==null?t.getClass().getSimpleName():t.getMessage(); }
    private static double number(TextField f) { try { return Double.parseDouble(f.getText().trim()); } catch(Exception ex) { throw new IllegalArgumentException("Enter a valid number in all required fields."); } }
    private static Double optionalNumber(TextField f) { String s=f.getText().trim(); if(s.isEmpty()) return null; try{return Double.parseDouble(s);}catch(Exception ex){throw new IllegalArgumentException("Optional values must be numeric when entered.");} }
    private static int integer(TextField f) { try { return Integer.parseInt(f.getText().trim()); } catch(Exception ex) { throw new IllegalArgumentException("Enter a whole number where required."); } }
    private static double toFeet(double length, String unit) { return "m".equalsIgnoreCase(unit) ? length * 3.280839895 : length; }

    @FunctionalInterface private interface Calculation { String run(); }
}

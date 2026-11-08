package com.example.desktop;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

/** Native Windows field-first CT, VT, relay translation and instrumentation review. */
public final class IndustrialFieldPane extends BorderPane {
    private final ChoiceBox<IndustrialFieldMath.Model> type=new ChoiceBox<>();
    private final VBox measurementFields=new VBox(10);
    private final Map<String,TextField> entries=new LinkedHashMap<>();
    private final TextArea report=new TextArea();
    private final Label status=new Label("Examples only; verify actual nameplates and measurements.");

    public IndustrialFieldPane(){
        setStyle("-fx-background-color:#f1f6f9;");
        VBox page=new VBox(13);
        page.setMaxWidth(820);
        page.setPadding(new Insets(17));
        Label title=new Label("Industrial Field Worksheets");
        title.setStyle("-fx-font-size:24px;-fx-font-weight:800;-fx-text-fill:#123d51;");
        Label description=new Label("CT/PT current and voltage, three-phase power, specified relay pickup conversion, ideal transformer fault estimate, and 4-20mA scaling.");
        description.setWrapText(true);
        Label safety=new Label("NOT CODE VERIFIED. These arithmetic calculations are not approved relay settings, fault-current/arc-flash studies or electrical installation approvals.");
        safety.setWrapText(true);
        safety.setStyle("-fx-text-fill:#844200;-fx-font-weight:bold;");
        type.getItems().addAll(IndustrialFieldMath.MODELS);
        type.setValue(IndustrialFieldMath.MODELS.getFirst());
        type.setMaxWidth(Double.MAX_VALUE);
        type.setMinHeight(42);
        type.valueProperty().addListener((obs,a,b)->rebuild());
        status.setWrapText(true);
        status.setStyle("-fx-text-fill:#844200;");
        report.setEditable(false);
        report.setWrapText(true);
        report.setPrefRowCount(15);
        report.setStyle("-fx-font-family:monospace;-fx-control-inner-background:#eef7fc;");
        Button calculate=new Button("Calculate field worksheet");
        calculate.setMinHeight(46);
        calculate.setMaxWidth(Double.MAX_VALUE);
        calculate.setStyle("-fx-background-color:#13718a;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;");
        calculate.setOnAction(e->calculate());
        Button reset=new Button("Restore example inputs");
        reset.setMinHeight(42);
        reset.setOnAction(e->rebuild());

        page.getChildren().addAll(title,description,
            card("1 · Worksheet type",type),
            card("2 · Enter actual field measurements",
                new Label("Shown values are examples; check equipment ratings."),measurementFields),
            new HBox(10,calculate,reset),
            card("3 · Results",status,report),safety);
        HBox.setHgrow(calculate,Priority.ALWAYS);
        HBox.setHgrow(reset,Priority.ALWAYS);
        calculate.setMaxWidth(Double.MAX_VALUE);
        reset.setMaxWidth(Double.MAX_VALUE);
        rebuild();
        ScrollPane scroller=new ScrollPane(page);
        scroller.setFitToWidth(true);
        scroller.setStyle("-fx-background:transparent;");
        setCenter(scroller);
    }
    private static VBox card(String heading,javafx.scene.Node...nodes){
        Label title=new Label(heading);
        title.setStyle("-fx-font-size:17px;-fx-font-weight:750;-fx-text-fill:#17556c;");
        VBox v=new VBox(10);
        v.setPadding(new Insets(14));
        v.setStyle("-fx-background-color:white;-fx-background-radius:12;-fx-border-color:#d0e0e8;-fx-border-radius:12;");
        v.getChildren().add(title);
        v.getChildren().addAll(nodes);
        return v;
    }
    private void rebuild(){
        entries.clear();
        measurementFields.getChildren().clear();
        IndustrialFieldMath.Model chosen=type.getValue();
        if(chosen==null)return;
        for(IndustrialFieldMath.Field f:chosen.fields()){
            TextField input=new TextField(f.example());
            input.setMinHeight(43);
            input.setMaxWidth(Double.MAX_VALUE);
            input.setPromptText("Enter actual measurement");
            Label label=new Label(f.label());
            label.setWrapText(true);
            label.setStyle("-fx-font-size:13px;-fx-font-weight:bold;");
            VBox row=new VBox(5,label,input);
            measurementFields.getChildren().add(row);
            entries.put(f.id(),input);
        }
        report.setText("No calculation performed.");
        status.setText("Example inputs shown; verify before calculating.");
    }
    private void calculate(){
        try{
            IndustrialFieldMath.Model chosen=type.getValue();
            if(chosen==null)throw new IllegalArgumentException("Choose a worksheet.");
            Map<String,Double> values=new LinkedHashMap<>();
            for(var e:entries.entrySet()){
                if(e.getValue().getText().isBlank())
                    throw new IllegalArgumentException("Enter "+e.getKey()+".");
                values.put(e.getKey(),Double.parseDouble(e.getValue().getText().trim()));
            }
            IndustrialFieldMath.Result result=IndustrialFieldMath.calculate(chosen.id(),values);
            StringBuilder b=new StringBuilder(chosen.title()).append("\n\n");
            for(var e:result.values().entrySet())
                b.append(String.format(Locale.CANADA,"%s: %.4f%n",e.getKey(),e.getValue()));
            b.append("\nMETHOD LIMITATIONS\n").append(result.caution())
             .append("\n\nReference: OESC_ON_2024 / numeric dataset not verified")
             .append("\nNOT CODE VERIFIED. No relay setting, fault-current or wiring approval.");
            report.setText(b.toString());
            status.setText("Arithmetic completed; code and equipment details require field verification.");
        }catch(Exception ex){
            status.setText("Cannot calculate: correct the entered values.");
            report.setText(ex.getMessage()==null?"Invalid numeric measurement.":ex.getMessage());
        }
    }
}

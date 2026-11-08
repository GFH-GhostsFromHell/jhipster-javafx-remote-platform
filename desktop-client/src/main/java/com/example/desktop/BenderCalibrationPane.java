package com.example.desktop;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.Locale;
import java.util.prefs.Preferences;

/** Persistent offline measurements for one physical shoe/conduit pairing. */
final class BenderCalibrationPane extends ScrollPane {
    private static final Preferences PREFS = Preferences.userNodeForPackage(BenderCalibrationPane.class).node("bender-calibration");
    private final TextField profile = field(PREFS.get("profile",""));
    private final TextField model = field(PREFS.get("model",""));
    private final TextField material = field(PREFS.get("material","EMT"));
    private final TextField size = field(PREFS.get("size","1/2"));
    private final ChoiceBox<String> units = new ChoiceBox<>();
    private final TextField mark = field(PREFS.get("mark",""));
    private final TextField stub = field(PREFS.get("stub",""));
    private final TextField stock = field(PREFS.get("stock",""));
    private final TextField legA = field(PREFS.get("legA",""));
    private final TextField legB = field(PREFS.get("legB",""));
    private final TextField minimumMarkGap = field(PREFS.get("minMarkGap",""));
    private final Label result = new Label();

    BenderCalibrationPane() {
        units.getItems().addAll("Inches","Millimetres");
        units.setValue(PREFS.get("units","Inches"));
        VBox page = new VBox(14);
        page.setPadding(new Insets(16));
        Label title = new Label("Bender Calibration · your actual shoe");
        title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;");
        Label explanation = info("Test the same bender, conduit material and trade size on scrap. Deduct and bend gain are separate measurements; neither is assumed. This profile is stored locally and synchronized to your signed-in account when online.");
        GridPane identity = grid();
        identity.addRow(0, new Label("Profile name"),profile);
        identity.addRow(1, new Label("Make / model"),model);
        identity.addRow(2, new Label("Conduit material"),material);
        identity.addRow(3, new Label("Trade size (e.g. 3/4)"),size);
        identity.addRow(4, new Label("Measurement units"),units);
        GridPane deduct = grid();
        deduct.addRow(0,new Label("Test 90° arrow mark from square end"),mark);
        deduct.addRow(1,new Label("Actual finished stub height"),stub);
        GridPane gain = grid();
        gain.addRow(0,new Label("Original straight test stock"),stock);
        gain.addRow(1,new Label("First finished leg"),legA);
        gain.addRow(2,new Label("Second finished leg"),legB);
        GridPane clearance = grid();
        clearance.addRow(0,new Label("Verified minimum shoe mark spacing (optional)"),minimumMarkGap);
        Button calculate = new Button("Calculate calibration");
        calculate.setOnAction(e -> calculate(false));
        Button save = new Button("Save calibration for this bender");
        save.setStyle("-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;");
        save.setOnAction(e -> calculate(true));
        result.setWrapText(true);
        result.setStyle("-fx-font-weight:bold;-fx-text-fill:#0f4c81;");
        page.getChildren().addAll(title,explanation,new Separator(),
            heading("1 · Tool identification"),identity,heading("2 · Effective stub deduct"),info("Deduct = actual finished stub height − original ARROW mark distance. Both must use the same reference end and dimension convention."),deduct,
            heading("3 · 90° bend gain"),info("Gain = finished leg A + finished leg B − original straight test stock. Use the same back-of-bend datums as your cut-length worksheet."),gain,
            heading("4 · Verified shoe clearance"),info("Optional. Enter the minimum spacing between two shop marks only if checked against this exact bender model, material and trade size. This does NOT establish minimum straight between bend arcs or full head clearance."),clearance,
            new HBox(10,calculate,save),result,
            info("Load this measured profile in the Single Bend or Gain/Cut Length tabs using their Use saved calibration button. Always verify the correct shoe and conduit size, springback, radius and manufacturing instructions."));
        setContent(page);setFitToWidth(true);
        calculate(false);
    }

    static String profileDescription() {return PREFS.get("profile","Saved bender")+" · "+PREFS.get("size","")+" "+PREFS.get("material","");}
    static String sizeMaterial() {return PREFS.get("size","")+" "+PREFS.get("material","");}
    static Double value(String key, String targetUnits) {
        String raw=PREFS.get(key,"");
        if(raw.isBlank())return null;
        try {
            double n=Double.parseDouble(raw);
            if(!Double.isFinite(n)||n<0)return null;
            String savedUnits=PREFS.get("units","Inches");
            return savedUnits.equals(targetUnits)?n:(targetUnits.equals("Millimetres")?n*25.4:n/25.4);
        }catch(Exception e){return null;}
    }

    private void calculate(boolean save) {
        try{
            boolean hasDeduct=!mark.getText().isBlank()||!stub.getText().isBlank();
            boolean hasGain=!stock.getText().isBlank()||!legA.getText().isBlank()||!legB.getText().isBlank();
            if(save&&(profile.getText().isBlank()||model.getText().isBlank()||material.getText().isBlank()||size.getText().isBlank()||!hasDeduct&&!hasGain))
                throw new IllegalArgumentException("Name/model/size/material and at least one complete test required.");
            Double deduct=null, bendGain=null;
            if(hasDeduct){
                double arrow=nonnegative(mark),height=positive(stub);
                if(height<arrow)throw new IllegalArgumentException("Stub height must be at least arrow mark distance.");
                deduct=height-arrow;
            }
            if(hasGain){
                double original=positive(stock),a=positive(legA),b=positive(legB);
                if(a+b<original)throw new IllegalArgumentException("Measured bend gain is negative; check the measurement datums.");
                bendGain=a+b-original;
            }
            Double minimumGap=null;
            if(!minimumMarkGap.getText().isBlank()){
                double gap=positive(minimumMarkGap);
                double maxStock="Inches".equals(units.getValue())?120:3048;
                if(gap>maxStock)throw new IllegalArgumentException("Verified shoe mark spacing must not exceed 10 ft stock.");
                minimumGap=gap;
            }
            String u=units.getValue();
            String message=(deduct==null?"Deduct: not measured":"Measured deduct: "+fmt(deduct)+" "+u)+"\n"+
                (bendGain==null?"90° gain: not measured":"Measured 90° gain: "+fmt(bendGain)+" "+u)+"\n"+
                (minimumGap==null?"Shoe mark gap: NOT VERIFIED":"Verified mark gap: "+fmt(minimumGap)+" "+u);
            if(save){
                PREFS.put("profile",profile.getText().trim());PREFS.put("model",model.getText().trim());
                PREFS.put("material",material.getText().trim());PREFS.put("size",size.getText().trim());PREFS.put("units",u);
                PREFS.put("mark",mark.getText().trim());PREFS.put("stub",stub.getText().trim());
                PREFS.put("stock",stock.getText().trim());PREFS.put("legA",legA.getText().trim());PREFS.put("legB",legB.getText().trim());
                PREFS.put("deduct",deduct==null?"":deduct.toString());PREFS.put("gain",bendGain==null?"":bendGain.toString());
                PREFS.put("minMarkGap",minimumGap==null?"":minimumGap.toString());
                DesktopAccountSync.scheduleCurrent();
                message+="\nSaved locally for "+profile.getText().trim()+". Sync will run when online.";
            }
            result.setText(message);
        }catch(Exception e){result.setText("Check calibration: "+e.getMessage());}
    }
    private static double nonnegative(TextField f) {
        double v=Double.parseDouble(f.getText().trim());
        if(!Double.isFinite(v)||v<0)throw new IllegalArgumentException("Measurements must be finite and nonnegative.");
        return v;
    }
    private static double positive(TextField f) {
        double v=nonnegative(f);if(v==0)throw new IllegalArgumentException("Finished lengths must be positive.");return v;
    }
    private static String fmt(double v){return String.format(Locale.ROOT,"%.3f",v);}
    private static TextField field(String s){TextField f=new TextField(s);f.setMaxWidth(250);return f;}
    private static GridPane grid(){GridPane g=new GridPane();g.setHgap(12);g.setVgap(9);return g;}
    private static Label heading(String text){Label l=new Label(text);l.setStyle("-fx-font-size:17px;-fx-font-weight:bold;");return l;}
    private static Label info(String text){Label l=new Label(text);l.setWrapText(true);return l;}
}

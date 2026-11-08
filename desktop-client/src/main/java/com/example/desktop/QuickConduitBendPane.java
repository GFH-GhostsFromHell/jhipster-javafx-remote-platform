package com.example.desktop;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fast, offline field layout for common hand-bender operations.
 * Manufacturer-dependent shoe references must be verified against the actual tool.
 */
final class QuickConduitBendPane extends BorderPane {
    private final ChoiceBox<String> kind = new ChoiceBox<>(FXCollections.observableArrayList(
            "Two-bend offset", "90° stub-up", "Rolling offset", "Back-to-back 90s"));
    private final ChoiceBox<String> units = new ChoiceBox<>(FXCollections.observableArrayList("Millimetres","Inches"));
    private final ChoiceBox<Double> angle = new ChoiceBox<>(FXCollections.observableArrayList(10d,15d,22.5d,30d,45d,60d));
    private static final double STOCK_MM = 3048.0;
    private final TextField rise = field("150"), datum = field("600"), deduct = field(""), roll = field("100"), span = field("300");
    private final TextField minShoeMarkGap = field("");
    private final VBox riseBox=labeled("Offset rise / stub height",rise),datumBox=labeled("Reference from tape end",datum),
            deductBox=labeled("Actual bender take-up / deduct",deduct),rollBox=labeled("Horizontal roll",roll),
            spanBox=labeled("Back-of-first-90 to second 90",span),angleBox=labeled("Bend angle",angle);
    private final Label headline = new Label("Enter the measurements for your bend");
    private final Label instructions = new Label();
    private final Label notice = new Label();
    private final Label stockNotice = new Label("10 ft EMT stock · 3,048 mm / 120 in");
    private final BendMarkStripPane markStrip = new BendMarkStripPane();
    private String clipboard = "";

    QuickConduitBendPane() {
        kind.setValue("Two-bend offset");
        units.setValue("Millimetres");
        angle.setValue(30d);
        VBox content=new VBox(12);
        content.setPadding(new Insets(15));
        content.setStyle("-fx-background-color:#edf5f9;");
        Label title=new Label("Quick Bend · get your marks in seconds");
        title.setStyle("-fx-font-size:21px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label intro=note("Pick a bend and type the dimensions shown. Results update automatically. Verified tool-specific references still matter.");
        FlowPane selectors=new FlowPane(10,10,labeled("Bend type",kind),labeled("Units",units));
        selectors.setAlignment(Pos.CENTER_LEFT);
        kind.setPrefWidth(225);
        FlowPane measurements=new FlowPane(12,10,riseBox,datumBox,angleBox,deductBox,rollBox,spanBox);
        measurements.setAlignment(Pos.CENTER_LEFT);
        VBox shoeGapBox=labeled("Verified minimum shoe mark spacing (optional)",minShoeMarkGap);
        Label clearanceNote=note("Tool clearance varies by shoe. Leave spacing blank if unknown; the result will say NOT VERIFIED.");
        stockNotice.setWrapText(true);
        stockNotice.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#166534;");
        Button load=primary("Use saved shoe measurements");
        load.setOnAction(e -> {
            Double calibrated=BenderCalibrationPane.value("deduct",units.getValue());
            Double minimum=BenderCalibrationPane.value("minMarkGap",units.getValue());
            if((calibrated==null||calibrated<=0)&&(minimum==null||minimum<=0)){
                notice.setText("No measured deduct or minimum shoe gap saved. Calibrate this exact tool first.");
                return;
            }
            if(calibrated!=null&&calibrated>0)deduct.setText(String.format(Locale.ROOT,"%.3f",calibrated));
            if(minimum!=null&&minimum>0)minShoeMarkGap.setText(String.format(Locale.ROOT,"%.3f",minimum));
            notice.setText("Loaded "+BenderCalibrationPane.profileDescription()+". Confirm this is the exact shoe before using its measurements.");
        });
        Button copy=primary("Copy bend marks");
        copy.setOnAction(e -> {
            if(clipboard.isBlank())return;
            ClipboardContent data=new ClipboardContent();
            data.putString(clipboard);
            Clipboard.getSystemClipboard().setContent(data);
        });
        headline.setWrapText(true);
        headline.setStyle("-fx-font-size:clamp(18px,2vw,23px);-fx-font-weight:bold;-fx-text-fill:#0e7490;");
        instructions.setWrapText(true);
        instructions.setStyle("-fx-font-size:14px;-fx-text-fill:#1e293b;");
        notice.setWrapText(true);
        notice.setStyle("-fx-text-fill:#9a3412;-fx-font-size:13px;");
        VBox results=new VBox(10,stockNotice,headline,instructions,copy,notice);
        results.setPadding(new Insets(15));
        results.setStyle("-fx-background-color:white;-fx-border-color:#b8dae5;-fx-border-radius:12;-fx-background-radius:12;");
        content.getChildren().addAll(title,intro,selectors,measurements,shoeGapBox,clearanceNote,load,results,
                note("The tape-zero end is LEFT. For offsets, the near-bend-critical geometric reference is used. Check whether your actual obstruction measurement and bender method match that convention."),
                markStrip,note("FIELD SAFETY · Confirm the stamped ARROW/STAR/NOTCH, manufacturer instructions, actual take-up, springback and minimum bend radius. Layout marks do not verify tool access or final fit."));

        ScrollPane scroll=new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:transparent;");
        setCenter(scroll);

        kind.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{showInputs();calculate();});
        units.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{
            if(a==null||b==null||a.equals(b))return;
            double factor="Inches".equals(b)?1.0/25.4:25.4;
            for(TextField f:List.of(rise,datum,deduct,roll,span,minShoeMarkGap)) {
                if(f.getText().isBlank())continue;
                try{f.setText(String.format(Locale.ROOT,"%.3f",Double.parseDouble(f.getText())*factor));}
                catch(Exception ignored){}
            }
            calculate();
        });
        angle.getSelectionModel().selectedItemProperty().addListener((o,a,b)->calculate());
        for(TextField f:List.of(rise,datum,deduct,roll,span,minShoeMarkGap))
            f.textProperty().addListener((o,a,b)->calculate());
        showInputs();
        calculate();
    }

    private void showInputs() {
        String type=kind.getValue();
        boolean isOffset="Two-bend offset".equals(type)||"Rolling offset".equals(type);
        show(datumBox,isOffset);
        show(angleBox,isOffset);
        show(rollBox,"Rolling offset".equals(type));
        show(deductBox,!isOffset);
        show(spanBox,"Back-to-back 90s".equals(type));
        riseBox.getChildren().get(0).setVisible(true);
        ((Label)riseBox.getChildren().get(0)).setText(isOffset?"Offset rise":"Desired first 90° stub height");
    }
    private static void show(VBox box,boolean visible){box.setVisible(visible);box.setManaged(visible);}
    private void calculate() {
        String type=kind.getValue();
        List<BendMarkStripPane.Mark> marks=new ArrayList<>();
        String unit="Inches".equals(units.getValue())?"in":"mm";
        try {
            double h=positive(rise,"rise or stub height");
            StringBuilder steps=new StringBuilder();
            double a=angle.getValue()==null?30:angle.getValue();
            double reference=0;
            if(type.equals("Two-bend offset")||type.equals("Rolling offset")) {
                reference=nonNegative(datum,"obstacle reference");
                double lateral=type.equals("Rolling offset")?positive(roll,"horizontal roll"):0;
                double trueRise=Math.hypot(h,lateral);
                double separation=trueRise/Math.sin(Math.toRadians(a));
                double shrink=trueRise*Math.tan(Math.toRadians(a/2));
                double markA=reference+shrink,markB=markA+separation;
                headline.setText("A = "+fmt(markA,unit)+"   ·   B = "+fmt(markB,unit));
                marks.add(new BendMarkStripPane.Mark(markA,1,"A","ARROW","HOOK ← toward datum","First "+a+"° offset bend"));
                marks.add(new BendMarkStripPane.Mark(markB,2,"B","ARROW","HOOK ← toward datum","Return "+a+"° to parallel"));
                steps.append("1. Tape from the LEFT datum and mark A, then B.\n");
                steps.append("2. Align the bender ARROW at A and bend ").append(a).append("°.\n");
                steps.append("3. Flip / reposition as the actual shoe requires; keep the two bends in the same plane. Align ARROW at B and return ").append(a).append("° to parallel.\n");
                if(lateral>0)steps.append("Roll the bending plane about ").append(String.format(Locale.ROOT,"%.1f",Math.toDegrees(Math.atan2(lateral,h)))).append("° from vertical.\n");
                steps.append("Spacing A→B: ").append(fmt(separation,unit)).append(". Calculated geometric shrink: ").append(fmt(shrink,unit)).append(".");
                notice.setText("Use the NEAR bend critical + shrink convention. Check the actual obstacle reference and manufacturer's offset method before marking.");
            } else {
                double take=positive(deduct,"actual bender deduct");
                double markA=h-take;
                if(markA<=0)throw new IllegalArgumentException("Stub height must exceed the measured deduct so the ARROW mark is beyond tape zero.");
                if(h>stockLimit(unit))throw new IllegalArgumentException("CALCULATION NOT POSSIBLE: stub height exceeds a 10 ft EMT stick.");
                headline.setText("Mark A = "+fmt(markA,unit)+" from the short end");
                marks.add(new BendMarkStripPane.Mark(markA,1,"A","ARROW","HOOK ← toward short end","Make first 90° stub"));
                steps.append("1. Tape from the end that becomes the short stub. Mark A at ").append(fmt(markA,unit)).append(".\n");
                steps.append("2. Align the actual shoe ARROW at A, with hook facing toward the stub end. Bend 90° and check the result.\n");
                if("Back-to-back 90s".equals(type)) {
                    double between=positive(span,"back-to-back distance");
                    if(between>stockLimit(unit))throw new IllegalArgumentException("CALCULATION NOT POSSIBLE: second relative 90 reference exceeds one 10 ft EMT stick.");
                    steps.append("3. From the BACK of the completed first 90°, measure ").append(fmt(between,unit)).append(" for the second STAR / back-of-90 reference.\n");
                    steps.append("4. Point the hook toward the remaining free end, align STAR (not ARROW) as directed by your shoe, bend 90° and verify spacing.");
                    marks.add(new BendMarkStripPane.Mark(between,2,"B · relative","STAR / BACK OF 90","HOOK → toward free end","Measure from back of first bend",true));
                    headline.setText("A = "+fmt(markA,unit)+"   ·   B = "+fmt(between,unit)+" from BACK of first 90°");
                }
                notice.setText("The actual stamped / calibrated deduct is REQUIRED. Back-to-back mark B is relative to the completed first bend, not straight-pipe zero.");
            }
            validateStockAndShoeSpacing(marks,unit);
            stockNotice.setText("10 ft STOCK CHECK · Plotted marks within "+fmt(stockLimit(unit),unit)+
                ". "+(minShoeMarkGap.getText().isBlank()
                ? "Shoe mark spacing NOT VERIFIED — enter the actual bender minimum."
                : "Same-datum marks checked against your measured minimum.")+
                " Remaining straight material and shoe-head access require field verification.");
            instructions.setText(steps.toString());
            clipboard=type+"\n"+headline.getText()+"\n"+steps+"\n"+notice.getText();
            double longest=marks.stream().filter(m->!m.relativeReference()).mapToDouble(BendMarkStripPane.Mark::position).max().orElse(200);
            markStrip.setPlan(type,marks,stockLimit(unit),units.getValue());
        }catch(RuntimeException ex) {
            stockNotice.setText("CALCULATION NOT POSSIBLE / CHECK INPUTS");
            headline.setText("Check the stock or bend spacing");
            instructions.setText(ex.getMessage());
            notice.setText("No physical mark is provided until the required inputs are valid. Do not guess your bender deduct.");
            clipboard="";
            markStrip.setPlan("Waiting for valid field measurements",List.of(),0,units.getValue());
        }
    }
    private static double stockLimit(String unit){return "mm".equals(unit)?STOCK_MM:120.0;}
    private void validateStockAndShoeSpacing(List<BendMarkStripPane.Mark> marks,String unit) {
        double stock=stockLimit(unit);
        for(BendMarkStripPane.Mark mark:marks) {
            if(!Double.isFinite(mark.position())||mark.position()<0||mark.position()>stock+1e-6)
                throw new IllegalArgumentException("CALCULATION NOT POSSIBLE on one 10 ft EMT: mark "+mark.name()+
                    " at "+fmt(mark.position(),unit)+" falls outside "+fmt(stock,unit)+" stock.");
        }
        String entry=minShoeMarkGap.getText().trim();
        double minimum=0;
        if(!entry.isEmpty()){
            try{minimum=Double.parseDouble(entry);}catch(NumberFormatException ex){throw new IllegalArgumentException("Enter a numeric shoe minimum or leave it blank.");}
            if(!Double.isFinite(minimum)||minimum<0||minimum>stock)
                throw new IllegalArgumentException("Verified shoe mark gap must be within the 10 ft stock.");
        }
        List<BendMarkStripPane.Mark> sameDatum=marks.stream().filter(m->!m.relativeReference())
            .sorted(java.util.Comparator.comparingDouble(BendMarkStripPane.Mark::position)).toList();
        for(int index=1;index<sameDatum.size();index++) {
            double distance=sameDatum.get(index).position()-sameDatum.get(index-1).position();
            if(distance<=1e-6)
                throw new IllegalArgumentException("BENDS TOO CLOSE: shop marks overlap. Increase the rise or separation.");
            if(minimum>0&&distance+1e-6<minimum)
                throw new IllegalArgumentException("BENDS TOO CLOSE: "+fmt(distance,unit)+
                    " between marks "+sameDatum.get(index-1).name()+" and "+sameDatum.get(index).name()+
                    ", less than your verified shoe minimum of "+fmt(minimum,unit)+".");
        }
    }
    private static String fmt(double x,String unit) {
        return String.format(Locale.ROOT, "mm".equals(unit)?"%.0f mm":"%.3f in",x);
    }
    private static double nonNegative(TextField f,String name){
        try{
            double n=Double.parseDouble(f.getText());
            if(Double.isFinite(n)&&n>=0)return n;
        }catch(Exception ignored){}
        throw new IllegalArgumentException("Enter a valid "+name+" (0 or greater).");
    }
    private static double positive(TextField f,String name){
        double n=nonNegative(f,name);
        if(n>0)return n;
        throw new IllegalArgumentException("Enter your "+name+" greater than zero.");
    }
    private static TextField field(String value){
        TextField f=new TextField(value);f.setPrefWidth(160);f.setMinHeight(40);return f;
    }
    private static VBox labeled(String text,Control input){
        Label title=new Label(text);title.setWrapText(true);
        title.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#334155;");
        VBox box=new VBox(5,title,input);box.setMaxWidth(190);return box;
    }
    private static Button primary(String name){
        Button b=new Button(name);
        b.setStyle("-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:10 12;");
        return b;
    }
    private static Label note(String text) {
        Label l=new Label(text);l.setWrapText(true);l.setStyle("-fx-text-fill:#475569;-fx-font-size:12px;");
        return l;
    }
}

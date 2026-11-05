package com.example.desktop;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import java.util.List;
import java.util.Locale;

/**
 * Original jobsite layout tools using geometric relationships and measured shoe calibration.
 * Gain is never silently assumed from conduit size: it must be measured or supplied.
 */
final class ConduitFabricationToolsPane extends ScrollPane {
    private final ChoiceBox<String> units = new ChoiceBox<>(FXCollections.observableArrayList("Inches", "Millimetres"));
    private final ChoiceBox<String> cutType = new ChoiceBox<>(FXCollections.observableArrayList("Single 90° (two finished legs)", "Back-to-back 90s (two stubs plus span)"));
    private final TextField firstLeg = field("18"), middleSpan = field("30"), lastLeg = field("24"), shoeGain = field("");
    private final TextField scrapStock = field("36"), scrapLegA = field("15"), scrapLegB = field("24");
    private final TextArea cutResult = output();
    private final ChoiceBox<Double> rackAngle = new ChoiceBox<>(FXCollections.observableArrayList(5d, 10d, 15d, 22.5d, 30d, 45d, 60d));
    private final ChoiceBox<String> rackDirection = new ChoiceBox<>(FXCollections.observableArrayList("Mark positions increase along pipe", "Mark positions decrease along pipe"));
    private final TextField rackSpacing = field("2"), rackStart = field("24"), rackCount = field("4");
    private final TextArea rackResult = output();

    ConduitFabricationToolsPane() {
        units.setValue("Inches");
        cutType.setValue("Single 90° (two finished legs)");
        rackAngle.setValue(30d);
        rackDirection.setValue("Mark positions increase along pipe");
        VBox page = new VBox(13);
        page.setPadding(new Insets(14));
        Label title = new Label("Field fabrication · gain, precut length and conduit racks");
        title.setStyle("-fx-font-size:21px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        page.getChildren().addAll(title, note("These are layout aids, not assumed bender specifications. Use the actual conduit type, shoe and measured bend gain. Mark from a common datum and test-fit before cutting a production run."));

        VBox cut = card("90° bend gain and straight stock cut length");
        GridPane c = new GridPane();
        c.setHgap(12); c.setVgap(10);
        c.add(label("Units"), 0, 0); c.add(units, 1, 0);
        c.add(label("Finished bend arrangement"), 0, 1); c.add(cutType, 1, 1);
        c.add(label("First finished stub / leg"), 0, 2); c.add(firstLeg, 1, 2);
        Label spanLabel = label("Back-to-back distance between bend backs");
        c.add(spanLabel, 0, 3); c.add(middleSpan, 1, 3);
        c.add(label("Second finished stub / leg"), 0, 4); c.add(lastLeg, 1, 4);
        c.add(label("Measured gain PER 90° bend"), 0, 5); c.add(shoeGain, 1, 5);
        Button calculateCut = primary("Calculate straight cut length");
        calculateCut.setOnAction(e -> computeCut());
        Button copyCut = new Button("Copy cut list");copyCut.setOnAction(e -> copy(cutResult.getText()));
        cut.getChildren().addAll(note("Measure each finished straight dimension using the same back-of-bend convention you used when calibrating gain. Do NOT confuse gain with arrow take-up/deduct."), c, new HBox(9, calculateCut, copyCut), cutResult);

        VBox calibrate = card("Measure gain from a scrap 90° bend");
        GridPane test = new GridPane();test.setHgap(12);test.setVgap(10);
        test.add(label("Original straight scrap stock length"), 0, 0);test.add(scrapStock, 1, 0);
        test.add(label("Finished first leg measurement"), 0, 1);test.add(scrapLegA, 1, 1);
        test.add(label("Finished second leg measurement"), 0, 2);test.add(scrapLegB, 1, 2);
        Button measureGain = primary("Calculate and use measured gain");
        measureGain.setOnAction(e -> {
            try {
                double stock = positive(scrapStock, "Original scrap stock");
                double leg1 = positive(scrapLegA, "First finished leg");
                double leg2 = positive(scrapLegB, "Second finished leg");
                double gain = leg1 + leg2 - stock;
                if(gain < 0)throw new IllegalArgumentException("Measured gain was negative. Check the two leg datum points and stock length.");
                shoeGain.setText(String.format(Locale.ROOT, "%.4f", gain));
                computeCut();
            } catch(Exception ex) {cutResult.setText("Check calibration: " + ex.getMessage());}
        });
        calibrate.getChildren().addAll(note("Bend scrap using the same conduit size, material and bender that will be used on the job. Measure both finished legs using a consistent back-of-bend method. Gain = leg A + leg B − original straight stock."),test,measureGain);

        VBox rack=card("Parallel conduit rack · matching offset mark locations");
        GridPane r=new GridPane();r.setHgap(12);r.setVgap(10);
        r.add(label("Angle for EACH offset bend"),0,0);r.add(rackAngle,1,0);
        r.add(label("Centre-to-centre spacing of adjacent runs"),0,1);r.add(rackSpacing,1,1);
        r.add(label("Number of conduits (2–20)"),0,2);r.add(rackCount,1,2);
        r.add(label("First conduit mark from datum"),0,3);r.add(rackStart,1,3);
        r.add(label("Stagger direction"),0,4);r.add(rackDirection,1,4);
        Button calcRack=primary("Calculate each conduit mark");
        calcRack.setOnAction(e->computeRack());
        Button copyRack=new Button("Copy rack marks");copyRack.setOnAction(e->copy(rackResult.getText()));
        rack.getChildren().addAll(note("For a parallel offset bank, the adjacent bend-centre stagger = centre-to-centre spacing × tan(bend angle ÷ 2). This is not the offset shrink allowance. Apply the same shift to both marks on each next pipe."),r,new HBox(9,calcRack,copyRack),rackResult);

        page.getChildren().addAll(cut,calibrate,rack,
            note("IMPORTANT: The calculated cut length assumes equal gain at both 90° bends. Different shoes/radii require separate calibration. Check available straight lead, minimum bend radius, conduit diameter, interference, code bend sum, and finished dimensions."));
        cutType.setOnAction(e -> {
            boolean doubleBend = cutType.getValue().startsWith("Back-to-back");
            middleSpan.setDisable(!doubleBend);
            spanLabel.setDisable(!doubleBend);
            computeCut();
        });
        cutType.setValue("Back-to-back 90s (two stubs plus span)");
        cutType.setValue("Single 90° (two finished legs)");
        units.setOnAction(e -> {
            double factor = "Millimetres".equals(units.getValue()) ? 25.4 : 1d/25.4;
            for(TextField f: List.of(firstLeg,middleSpan,lastLeg,shoeGain,scrapStock,scrapLegA,scrapLegB,rackSpacing,rackStart)){
                if(f.getText().isBlank())continue;
                try{f.setText(String.format(Locale.ROOT,"%.4f",Double.parseDouble(f.getText().trim())*factor));}
                catch(NumberFormatException ignored){}
            }
            computeCut();computeRack();
        });
        computeCut();computeRack();
        setContent(page);setFitToWidth(true);
        setStyle("-fx-background:transparent;-fx-background-color:transparent;");
    }

    private void computeCut(){
        try{
            double a=positive(firstLeg,"First finished leg"), b=positive(lastLeg,"Second finished leg");
            if(shoeGain.getText().isBlank()){
                cutResult.setText("Enter the actual measured gain per 90° or use the scrap calibration above. No universal gain value has been assumed.");
                return;
            }
            double gain=nonnegative(shoeGain,"Measured gain");
            boolean two=cutType.getValue().startsWith("Back-to-back");
            double span=two?positive(middleSpan,"Back-to-back distance"):0;
            double cut=a+b+span-(two?2:1)*gain;
            if(cut<=0)throw new IllegalArgumentException("Gain exceeds the available finished dimensions.");
            StringBuilder out=new StringBuilder();
            out.append(two?"TWO 90° BENDS":"ONE 90° BEND").append("\n")
               .append("Finished dimension sum: ").append(format(a+b+span)).append("\n")
               .append("Gain allowance: ").append(format((two?2:1)*gain)).append("\n")
               .append("STRAIGHT STOCK TO CUT: ").append(format(cut)).append("\n\n")
               .append("Formula: sum of measured finished straight dimensions − (number of 90° bends × measured gain).\n")
               .append("Confirm the chosen back-of-bend reference, correct tool calibration, and stub minimum before cutting.");
            cutResult.setText(out.toString());
        }catch(Exception ex){cutResult.setText("Check cut-length inputs: "+ex.getMessage());}
    }

    private void computeRack(){
        try{
            double spacing=positive(rackSpacing,"Centre-to-centre spacing"),start=nonnegative(rackStart,"First mark");
            double a=rackAngle.getValue();
            double countValue=positive(rackCount,"Conduit count");
            int count=(int)countValue;
            if(count!=countValue||count<2||count>20)throw new IllegalArgumentException("Conduit count must be a whole number from 2 to 20.");
            double each=spacing*Math.tan(Math.toRadians(a/2));
            boolean increase=rackDirection.getValue().startsWith("Mark positions increase");
            StringBuilder out=new StringBuilder();
            out.append("Offset angle: ").append(a).append("° per bend\n")
               .append("Adjacent rack centreline spacing: ").append(format(spacing)).append("\n")
               .append("Stagger between neighbouring pipes: ").append(format(each)).append("\n")
               .append("First-to-last stagger: ").append(format(each*(count-1))).append("\n\n");
            for(int i=0;i<count;i++){
                double mark=start+(increase?1:-1)*each*i;
                out.append("Pipe ").append(i+1).append(": ")
                   .append(mark<0?"BEFORE DATUM — change layout":"first bend mark "+format(mark))
                   .append("\n");
            }
            out.append("\nMove BOTH offset marks together on each adjacent pipe; retain the same individual bend spacing and angle. This assumes the bank's centreline geometry is valid for the selected conduit sizes.");
            rackResult.setText(out.toString());
        }catch(Exception ex){rackResult.setText("Check rack layout inputs: "+ex.getMessage());}
    }

    private String format(double value){
        if("Millimetres".equals(units.getValue()))
            return String.format(Locale.CANADA,"%.1f mm",value);
        long n=Math.round(Math.abs(value)*16),whole=n/16,part=n%16;
        if(part==0)return (value<0?"−":"")+whole+" in";
        int x=(int)part,y=16;
        while(y!=0){int temp=x%y;x=y;y=temp;}
        return (value<0?"−":"")+(whole==0?"":whole+" ")+(part/x)+"/"+(16/x)+" in";
    }
    private static double positive(TextField f,String name){
        double v=nonnegative(f,name);if(v==0)throw new IllegalArgumentException(name+" must be greater than zero.");return v;
    }
    private static double nonnegative(TextField f,String name){
        try{
            double value=Double.parseDouble(f.getText().trim());
            if(Double.isFinite(value)&&value>=0)return value;
        }catch(NumberFormatException ignored){}
        throw new IllegalArgumentException(name+" must be a finite number zero or greater.");
    }
    private static TextField field(String value){
        TextField f=new TextField(value);f.setMaxWidth(145);
        f.setStyle("-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-padding:8 10;");
        return f;
    }
    private static TextArea output(){
        TextArea a=new TextArea();a.setEditable(false);a.setWrapText(true);a.setPrefRowCount(10);
        a.setStyle("-fx-font-size:12px;");
        return a;
    }
    private static Label label(String content){
        Label l=new Label(content);l.setWrapText(true);l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;");return l;
    }
    private static Label note(String content){
        Label l=new Label(content);l.setWrapText(true);l.setStyle("-fx-font-size:12px;-fx-text-fill:#475569;");return l;
    }
    private static VBox card(String title){
        VBox v=new VBox(9);v.setPadding(new Insets(13));
        v.setStyle("-fx-background-color:white;-fx-border-color:#dbe5ef;-fx-border-radius:12;-fx-background-radius:12;");
        Label h=new Label(title);h.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        v.getChildren().add(h);return v;
    }
    private static Button primary(String text){
        Button b=new Button(text);b.setStyle("-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:9 12;");return b;
    }
    private static void copy(String content){
        ClipboardContent clip=new ClipboardContent();clip.putString(content);Clipboard.getSystemClipboard().setContent(clip);
    }
}

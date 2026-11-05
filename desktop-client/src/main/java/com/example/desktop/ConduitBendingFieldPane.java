package com.example.desktop;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;

/** Local-first native field bending guide, with measurements and marking sequence. */
public final class ConduitBendingFieldPane extends BorderPane {
    private static final String[] TYPES = {"Two-bend offset", "Rolling offset", "90° stub-up", "Back-to-back 90s", "Three-point saddle", "Four-point saddle", "Kicked 90°", "Segmented 90°"};
    private final ChoiceBox<String> type = new ChoiceBox<>(FXCollections.observableArrayList(TYPES));
    private final ChoiceBox<String> units = new ChoiceBox<>(FXCollections.observableArrayList("Millimetres","Inches"));
    private final ChoiceBox<Double> angle = new ChoiceBox<>(FXCollections.observableArrayList(10d,15d,22.5d,30d,45d,60d));
    private final ChoiceBox<String> bender = new ChoiceBox<>(FXCollections.observableArrayList("1/2 EMT", "3/4 EMT", "1 EMT", "1 1/4 EMT", "Custom"));
    private final ChoiceBox<String> obstacleReference = new ChoiceBox<>(FXCollections.observableArrayList("Near bend critical", "Far bend critical"));
    private final ChoiceBox<String> tapeMarks = new ChoiceBox<>(FXCollections.observableArrayList("Exact", "1 mm", "5 mm"));
    private final TextField rise = field("150"), roll = field("100"), distance = field("600"), stub = field("500");
    private final TextField takeup = field("127"), width = field("200"), center = field("600"), correction = field("0");
    private final TextField segments = field("6");
    private final Map<String,HBox> rows = new LinkedHashMap<>();
    private final TextArea answer = new TextArea();
    private final BendMarkStripPane visual = new BendMarkStripPane();
    private final TextArea help = new TextArea();

    public ConduitBendingFieldPane() {
        type.setValue(TYPES[0]); units.setValue("Millimetres"); angle.setValue(30d); bender.setValue("1/2 EMT");
        obstacleReference.setValue("Near bend critical"); tapeMarks.setValue("1 mm");
        VBox page = new VBox(12); page.setPadding(new Insets(14));
        Label title = new Label("Conduit bender · field layout & instructions");
        title.setStyle("-fx-font-size:21px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label intro = note("Select the bend you are making, enter your measurements, and follow the numbered marking and bending steps. Calculations are local and work without a connection.");
        HBox selectors = new HBox(10,label("Bend type"),type,label("Units"),units);
        selectors.setAlignment(Pos.CENTER_LEFT); type.setPrefWidth(240);
        HBox settings = new HBox(10,label("Hand bender"),bender,label("Angle"),angle);
        settings.setAlignment(Pos.CENTER_LEFT);
        HBox fieldSettings = new HBox(10,label("Offset reference"),obstacleReference,label("Tape mark precision"),tapeMarks);
        fieldSettings.setAlignment(Pos.CENTER_LEFT);
        VBox inputs = new VBox(9);
        put(inputs,"rise","Rise / obstacle height",rise);
        put(inputs,"roll","Horizontal roll",roll);
        put(inputs,"distance","Distance from datum to obstacle reference",distance);
        put(inputs,"stub","Desired 90° stub height",stub);
        put(inputs,"takeup","Bender take-up / deduct",takeup);
        put(inputs,"width","Obstacle width / back-to-back separation",width);
        put(inputs,"center","Obstruction centre from reference end",center);
        put(inputs,"correction","Star/back-of-90 mark correction",correction);
        put(inputs,"segments","Number of segments",segments);
        Button calculate = new Button("Calculate & show bending steps");
        calculate.setStyle("-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:11 14;");
        Button copy = new Button("Copy field layout");
        copy.setOnAction(e -> { javafx.scene.input.ClipboardContent cb = new javafx.scene.input.ClipboardContent();cb.putString(answer.getText());javafx.scene.input.Clipboard.getSystemClipboard().setContent(cb); });
        calculate.setOnAction(e->calculate());
        answer.setEditable(false);answer.setWrapText(true);answer.setPrefRowCount(18);
        help.setEditable(false);help.setWrapText(true);help.setPrefRowCount(9);
        help.setStyle("-fx-font-size:12px;-fx-control-inner-background:#eff8fc;-fx-text-fill:#0f4c81;");
        TitledPane bendHelp=new TitledPane("Help · exact marking, shoe reference, bender orientation & order",help);
        bendHelp.setExpanded(true);
        bendHelp.setCollapsible(true);
        page.getChildren().addAll(title,intro,selectors,settings,fieldSettings,inputs,new HBox(10,calculate,copy),visual,bendHelp,answer,
            note("Hand-bender arrows, star/back-of-90 marks, saddle notches, deduct, minimum stub and springback vary by manufacturer, conduit size and type. Field shrink charts are rounded; this calculator shows geometric values. Measure conduit on its centreline, keep bends in the intended plane, and check a test piece before cutting production runs."));
        ScrollPane scroll = new ScrollPane(page);scroll.setFitToWidth(true);scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        TabPane screens=new TabPane();
        screens.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        screens.getTabs().addAll(
            new Tab("Single Bend & Field Guide",scroll),
            new Tab("Advanced · Multiple Bends on One Pipe",new AdvancedConduitBendPane()),
            new Tab("Multiplier Chart & Quick Sheet",new ConduitBendReferencePane())
        );
        setCenter(screens);
        type.setOnAction(e->{showFields();calculate();});
        angle.setOnAction(e->calculate());
        obstacleReference.setOnAction(e->calculate());
        tapeMarks.setOnAction(e->calculate());
        bender.setOnAction(e->{
            double in = switch(bender.getValue()){case "1/2 EMT" -> 5;case "3/4 EMT" -> 6;case "1 EMT" -> 8;case "1 1/4 EMT" -> 11;default -> -1;};
            if(in>0)takeup.setText(String.format(Locale.ROOT,"%.3f", units.getValue().equals("Inches") ? in : in*25.4));
            calculate();
        });
        units.setOnAction(e->{
            boolean inch=units.getValue().equals("Inches");double factor=inch?1/25.4:25.4;
            for(TextField t:List.of(rise,roll,distance,stub,takeup,width,center,correction)){
                try{t.setText(String.format(Locale.ROOT,"%.3f",Double.parseDouble(t.getText())*factor));}catch(Exception ignored){}
            }
            if(inch){ tapeMarks.getItems().setAll("Exact","1/16 in","1/8 in","1/4 in");tapeMarks.setValue("1/16 in"); }
            else { tapeMarks.getItems().setAll("Exact","1 mm","5 mm");tapeMarks.setValue("1 mm"); }
            showFields();calculate();
        });
        showFields();calculate();
    }

    private void put(VBox box,String key,String prompt,TextField field) {
        HBox row = new HBox(10,label(prompt),field);row.setAlignment(Pos.CENTER_LEFT);field.setMaxWidth(150);
        rows.put(key,row);box.getChildren().add(row);
    }

    private void showFields() {
        String t=type.getValue();
        Set<String> active=new HashSet<>();
        switch(t) {
            case "Two-bend offset" -> Collections.addAll(active,"rise","distance");
            case "Rolling offset" -> Collections.addAll(active,"rise","roll","distance");
            case "90° stub-up" -> Collections.addAll(active,"stub","takeup");
            case "Back-to-back 90s" -> Collections.addAll(active,"stub","takeup","width","correction");
            case "Three-point saddle" -> Collections.addAll(active,"rise","center");
            case "Four-point saddle" -> Collections.addAll(active,"rise","width","distance");
            case "Kicked 90°" -> Collections.addAll(active,"stub","takeup","rise");
            case "Segmented 90°" -> Collections.addAll(active,"rise","segments");
        }
        for(Map.Entry<String,HBox> e:rows.entrySet()){boolean show=active.contains(e.getKey());e.getValue().setVisible(show);e.getValue().setManaged(show);}
        bender.setDisable(!List.of("90° stub-up","Back-to-back 90s","Kicked 90°").contains(t));
        boolean isOffset = t.equals("Two-bend offset") || t.equals("Rolling offset");
        obstacleReference.setDisable(!isOffset);
        angle.setDisable(List.of("90° stub-up","Back-to-back 90s","Segmented 90°").contains(t));
    }

    private void calculate() {
        try {
            List<BendMarkStripPane.Mark> visualMarks=new ArrayList<>();
            double a=angle.getValue(), rad=Math.toRadians(a), mult=1/Math.sin(rad), shrinkFactor=Math.tan(rad/2);
            String t=type.getValue();StringBuilder s=new StringBuilder();
            s.append("FIELD BEND: ").append(t).append("\n\n");
            if(t.equals("Two-bend offset")||t.equals("Rolling offset")){
                double vertical=positive(rise,"rise"), lateral=t.equals("Rolling offset")?nonNegative(roll,"horizontal roll"):0;
                double h=Math.hypot(vertical,lateral), spacing=h*mult,shrink=h*shrinkFactor,ref=nonNegative(distance,"reference distance");
                // The critical-bend selection controls which mark is anchored to the obstacle reference.
                // These are geometric estimates; compare with the shoe maker's field shrink convention.
                boolean farCritical = "Far bend critical".equals(obstacleReference.getValue());
                double first=(farCritical ? ref+shrink-spacing : ref+shrink),second=first+spacing;
                if(first<0)throw new IllegalArgumentException("The near mark is before the reference end. Increase the reference distance or change layout.");
                visualMarks.add(new BendMarkStripPane.Mark(first,1,"A","ARROW","HOOK ← away from B","+ "+a+"° first offset bend"));
                visualMarks.add(new BendMarkStripPane.Mark(second,2,"B","ARROW","Flip/rotate; same plane","− "+a+"° return to parallel"));
                s.append("Offset datum reference: ").append(obstacleReference.getValue()).append("\n")
                 .append("True offset: ").append(measure(h)).append("\n")
                 .append("Bend angle at BOTH marks: ").append(a).append("°\n")
                 .append("Multiplier: ").append(dec(mult)).append(" (1 ÷ sin angle)\n")
                 .append("Mark spacing: ").append(measure(spacing)).append(" (true offset × multiplier)\n")
                 .append("Exact geometric shrink: ").append(measure(shrink)).append(" (offset × tan half-angle)\n")
                 .append("A / first mark from datum: ").append(measure(first)).append("\n")
                 .append("B / far mark from datum: ").append(measure(second)).append("\n");
                if(lateral>0)s.append("Rotate bending plane away from vertical by: ").append(dec(Math.toDegrees(Math.atan2(lateral,vertical)))).append("°\n");
                s.append("\nFIELD STEPS\n1. Mark the datum end and measure ").append(measure(first)).append(" to mark A.\n")
                 .append("2. Measure ").append(measure(spacing)).append(" farther to mark B; keep both marks visible.\n")
                 .append("3. With the hook facing away from the second mark as in the Klein offset method, align ARROW to A and bend ").append(a).append("° with steady foot pressure.\n")
                 .append("4. Flip/position the bender and conduit for the return bend, turning the conduit 180° in the bender cradle as your shoe instructions require; keep both bends coplanar. Align ARROW with B and bend ").append(a).append("° back to parallel.\n")
                 .append("5. For a rolling offset, turn the bend plane to the calculated orientation before bending. Check rise AND horizontal roll in place.\n")
                 .append("6. Check parallel ends and clearance. FAR bend critical: B = obstacle reference + shrink and A is one spacing earlier. NEAR bend critical: A = obstacle reference + shrink and B is one spacing later. Verify the reference against your manufacturer method.\n");
            } else if(t.equals("90° stub-up")||t.equals("Back-to-back 90s")||t.equals("Kicked 90°")){
                double h=positive(stub,"stub height"), tk=positive(takeup,"bender deduct"),mark=h-tk;
                if(mark<0)throw new IllegalArgumentException("Stub is shorter than deduct; use a suitable bender/method.");
                visualMarks.add(new BendMarkStripPane.Mark(mark,1,"A","ARROW","HOOK ← toward short end","Make first 90° stub-up"));
                s.append("Stub height: ").append(measure(h)).append("\n")
                 .append("Take-up/deduct (verify stamped shoe): ").append(measure(tk)).append("\n")
                 .append("First 90° ARROW mark: ").append(measure(mark)).append(" from reference end.\n");
                s.append("\nFIELD STEPS\n1. Square the reference end. Mark ").append(measure(mark)).append(" for the 90°.\n")
                 .append("2. Point the conduit toward the shoe hook, line up the ARROW with the mark, and apply firm foot pressure while bending to 90°.\n")
                 .append("3. Remove and measure actual stub height; allow for springback and adjust against the installed bender.\n");
                if(t.equals("Back-to-back 90s")){
                    double d=positive(width,"back-to-back dimension"),c=signed(correction,"star correction");
                     if(d+c<=0)throw new IllegalArgumentException("Star/back-of-90 reference must be after the first bend.");
                    visualMarks.add(new BendMarkStripPane.Mark(d+c,2,"B","STAR / BACK OF 90","Hook toward second free end","2nd 90°; reference from BACK of first bend",true));
                    s.append("\nBack-to-back desired reference distance: ").append(measure(d)).append("\n")
                     .append("Second bend STAR/back-of-90 reference: ").append(measure(d+c)).append(" from the back of the FIRST bend, subject to shoe calibration.\n")
                     .append("4. Measure from the BACK of the first 90° to the required back-to-back reference.\n")
                     .append("5. Reverse/position the conduit as your bender requires; align its STAR/back-of-90 mark, not the stub arrow deduct, for the second 90°.\n")
                     .append("6. Bend 90° in the same plane; check the final leg-to-leg dimension.\n");
                } else if(t.equals("Kicked 90°")){
                    double k=positive(rise,"kick rise");
                    s.append("Second kick mark is field-selected; there is no universal absolute kick mark from rise alone.\n");
                    s.append("\nKick angle: ").append(a).append("°\n")
                     .append("Kick diagonal travel: ").append(measure(k*mult)).append("\n")
                     .append("Kick projected run: ").append(measure(k/Math.tan(rad))).append("\n")
                     .append("4. Choose the actual kick location on the horizontal leg; mark it according to your shoe's bend reference.\n")
                     .append("5. Orient the kick away from the 90° in the required plane and bend ").append(a).append("°; verify the rise.\n")
                     .append("NOTE: Kick location depends on the chosen leg reference and bender radius; this worksheet does not invent an absolute second mark.\n");
                }
            } else if(t.equals("Three-point saddle")){
                double h=positive(rise,"saddle height"), c=positive(center,"obstruction centre"), side=a/2;
                double spacing=h/Math.sin(Math.toRadians(side)),shift=h*Math.tan(Math.toRadians(side/2));
                double B=c+shift,A=B-spacing,C=B+spacing;
                if(A<0)throw new IllegalArgumentException("Saddle near mark is before the datum. Use a longer lead or shorter/steeper saddle.");
                visualMarks.add(new BendMarkStripPane.Mark(B,1,"B · centre","SADDLE / CENTRE","Saddle notch; shoe-specific","Bend "+a+"° centre FIRST"));
                visualMarks.add(new BendMarkStripPane.Mark(A,2,"A · outer","ARROW","Flip/rotate for outside return","Bend "+side+"° outside return"));
                visualMarks.add(new BendMarkStripPane.Mark(C,3,"C · outer","ARROW","Keep return in same plane","Bend "+side+"° outside return"));
                s.append("Centre bend: ").append(a).append("° · side bends: ").append(side).append("°\n")
                 .append("Spacing each side of centre: ").append(measure(spacing)).append("\n")
                 .append("Geometric centre shift: ").append(measure(shift)).append("\n")
                 .append("A (first outside): ").append(measure(A)).append("\nB (centre): ").append(measure(B))
                 .append("\nC (second outside): ").append(measure(C)).append("\n")
                 .append("\nFIELD STEPS\n1. Measure from the reference end to the obstruction centre and add the calculated centre shift. Mark B.\n")
                 .append("2. Measure ").append(measure(spacing)).append(" either side for A and C.\n")
                 .append("3. Align B with the bender's saddle/centre notch (if equipped) and bend ").append(a).append("°.\n")
                 .append("4. Turn the conduit 180° on its length and bend A and C ").append(side).append("° in the opposite direction, in the SAME plane.\n")
                 .append("5. Test-fit over the obstacle. Manufacturer saddle tables may give slightly different marks due to shoe radius.\n");
            } else if(t.equals("Four-point saddle")){
                double h=positive(rise,"saddle height"), w=positive(width,"obstacle width"),d=positive(distance,"distance to near face");
                double spacing=h*mult,sh=h*shrinkFactor;
                double B=d+sh,A=B-spacing,C=B+w,D=C+spacing;
                if(A<0)throw new IllegalArgumentException("First mark is before reference end; increase clearance to near face.");
                visualMarks.add(new BendMarkStripPane.Mark(A,1,"A","ARROW","HOOK ← toward datum","Bend "+a+"° rise"));
                visualMarks.add(new BendMarkStripPane.Mark(B,2,"B","ARROW","Flip/rotate; hold plane","Return "+a+"° to level"));
                visualMarks.add(new BendMarkStripPane.Mark(C,3,"C","ARROW","Reverse offset direction","Bend "+a+"° back toward datum plane"));
                visualMarks.add(new BendMarkStripPane.Mark(D,4,"D","ARROW","Flip/rotate; hold plane","Return "+a+"° to original run"));
                s.append("Each offset bend: ").append(a).append("°\nSide spacing: ").append(measure(spacing)).append("\nShrink per offset: ")
                 .append(measure(sh)).append("\nTotal shrink: ").append(measure(2*sh)).append("\n")
                 .append("Marks A/B/C/D: ").append(measure(A)).append(" / ").append(measure(B)).append(" / ")
                 .append(measure(C)).append(" / ").append(measure(D)).append("\n")
                 .append("\nFIELD STEPS\n1. Measure A, B, C and D from the SAME reference end and mark all four points.\n")
                 .append("2. Make equal-angle bends at A and B to rise over the obstacle, reversing the conduit 180° between bends.\n")
                 .append("3. Make equal-angle bends at C and D in the opposite offset direction to return to the starting plane.\n")
                 .append("4. Keep the two offsets parallel and verify the obstacle width allows for the bender shoe radius.\n");
            } else {
                double r=positive(rise,"centreline radius");int n=(int)nonNegative(segments,"segments");
                if(n<3||n>12)throw new IllegalArgumentException("Use 3 to 12 segments.");
                double each=90d/n, arc=Math.PI*r/2, spacing=arc/n;
                for(int i=1;i<=n;i++)
                    visualMarks.add(new BendMarkStripPane.Mark(i*spacing,i,"M"+i,"SHOE CENTRE","Same bend plane","Form "+dec(each)+"° segment "+i));
                s.append("Total 90° arc: ").append(measure(arc)).append("\n")
                 .append("Bends: ").append(n).append(" × ").append(dec(each)).append("°\n")
                 .append("Equal arc/mark spacing (approx.): ").append(measure(spacing)).append("\n")
                 .append("\nFIELD STEPS\n1. Set out equal spacings along the centreline tangent/arc layout, accounting for the shoe start point.\n")
                 .append("2. Bend each marked station by ").append(dec(each)).append("°; keep all bends in the same plane.\n")
                 .append("3. Check resulting radius and overall 90°. Springback and shoe geometry require a test piece.\n");
            }
            s.append("\nTAPE MARK NOTE\n• Dimensions above are displayed at ").append(tapeMarks.getValue()).append(" precision; trigonometric calculations retain full precision. Confirm the actual bender shoe reference and round only FINAL marking dimensions.\n")
              .append("\nCHECK BEFORE INSTALLATION\n• Confirm conduit type, actual take-up, mark convention, minimum bending radius and manufacturer instructions.\n")
             .append("• Support the conduit firmly and inspect for flattening/kinks. Verify the total bend allowance and pull-point requirements.\n")
             .append("• All measurements use ").append(units.getValue().toLowerCase(Locale.ROOT)).append(" and a consistent reference end.");
            answer.setText(s.toString());
            help.setText(ConduitBendReferencePane.guide(t));
            visual.setPlan("Bend layout · "+t,visualMarks,0,units.getValue(),tapeMarks.getValue());
        }catch(Exception ex){
            answer.setText("Check inputs: "+ex.getMessage());
            help.setText(ConduitBendReferencePane.guide(type.getValue()));
            visual.setPlan("Check inputs",List.of(),0,units.getValue(),tapeMarks.getValue());
        }
    }

    private String measure(double x){
        String precision=tapeMarks.getValue();
        if(!"Inches".equals(units.getValue())){
            if("Exact".equals(precision))return String.format(Locale.CANADA,"%.3f mm",x);
            double increment="5 mm".equals(precision)?5d:1d;
            return String.format(Locale.CANADA,"%.0f mm",Math.round(x/increment)*increment);
        }
        if("Exact".equals(precision))return String.format(Locale.CANADA,"%.3f in",x);
        int denominator="1/4 in".equals(precision)?4:"1/8 in".equals(precision)?8:16;
        long ticks=Math.round(Math.abs(x)*denominator),whole=ticks/denominator,part=ticks%denominator;
        String amount;
        if(part==0)amount=Long.toString(whole);
        else{
            int gcd=gcd((int)part,denominator);
            amount=(whole==0?"":whole+" ")+(part/gcd)+"/"+(denominator/gcd);
        }
        return (x<0?"-":"")+amount+" in";
    }
    private static int gcd(int a,int b){while(b!=0){int t=a%b;a=b;b=t;}return a==0?1:a;}
    private static double signed(TextField t,String label){
        try{double x=Double.parseDouble(t.getText().trim());if(Double.isFinite(x))return x;}catch(Exception ignored){}
        throw new IllegalArgumentException(label+" must be a finite number.");
    }
    private static String dec(double x){return String.format(Locale.CANADA,"%.3f",x);}
    private static double positive(TextField t,String label){double x=nonNegative(t,label);if(x==0)throw new IllegalArgumentException("Enter "+label+" greater than zero.");return x;}
    private static double nonNegative(TextField t,String label){
        try{double x=Double.parseDouble(t.getText().trim());if(Double.isFinite(x)&&x>=0)return x;}catch(Exception ignored){}
        throw new IllegalArgumentException(label+" must be zero or greater.");
    }
    private static TextField field(String s){TextField f=new TextField(s);f.setStyle("-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-padding:8 10;");return f;}
    private static Label label(String s){Label l=new Label(s);l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;");return l;}
    private static Label note(String s){Label l=new Label(s);l.setWrapText(true);l.setStyle("-fx-text-fill:#475569;");return l;}
}

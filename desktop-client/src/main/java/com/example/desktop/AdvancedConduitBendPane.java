package com.example.desktop;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Multiple bend operations on one straight conduit, with a consistent datum.
 * Marks are nominal layout references; machine/hand-shoe interference and
 * bend allowance on a developed pipe must still be verified on a test piece.
 */
public final class AdvancedConduitBendPane extends BorderPane {
    private static final String INPUT="-fx-background-color:#fff;-fx-border-color:#cbd5e1;-fx-background-radius:6;-fx-border-radius:6;-fx-padding:6 8;";
    private static final String[] KINDS={
        "Two-bend offset", "Rolling offset", "90° arrow mark", "Single kick",
        "Three-point saddle", "Four-point saddle"
    };
    private final ChoiceBox<String> kind=new ChoiceBox<>(FXCollections.observableArrayList(KINDS));
    private final ChoiceBox<Double> angle=new ChoiceBox<>(FXCollections.observableArrayList(10d,15d,22.5d,30d,45d,60d));
    private final ChoiceBox<String> direction=new ChoiceBox<>(FXCollections.observableArrayList("Up","Down","Left","Right"));
    private final ChoiceBox<String> units=new ChoiceBox<>(FXCollections.observableArrayList("Millimetres","Inches"));
    private final TextField stock=field("2400"), clearance=field("150");
    private final TextField position=field("450"),height=field("150"),roll=field("100"),obstacleWidth=field("250");
    private final ListView<Operation> operations=new ListView<>();
    private final ObservableList<Operation> entries=FXCollections.observableArrayList();
    private final BendMarkStripPane marks=new BendMarkStripPane();
    private final TextArea instructions=new TextArea();
    private final Label warning=new Label();
    private String clipboardText="";
    private record Operation(String kind, String direction, double position, double height, double roll, double width, double angle) {
        @Override public String toString(){
            return kind+"  ·  mark/reference "+number(position)+"  ·  "+direction+
                (kind.contains("90°")?"": "  ·  "+number(height)+" height")+
                "  ·  "+(kind.contains("90°")?90:angle)+"°";
        }
    }
    private record Proposed(double position,String name,String reference,String hook,String description){}
    private record Station( double position,String name,String reference,String hook,String description,boolean relative){}

    AdvancedConduitBendPane() {
        setPadding(new Insets(12));
        setStyle("-fx-background-color:#eef3f8;");
        kind.setValue(KINDS[0]);angle.setValue(30d);direction.setValue("Up");units.setValue("Millimetres");
        VBox page=new VBox(13);page.setPadding(new Insets(3));
        Label title=new Label("Advanced multi-bend layout · one conduit");
        title.setStyle("-fx-font-size:21px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label lead=note("Build a sequence of 2-bend offsets, 90s, kicks, saddles and rolling offsets on the SAME pipe. Every nominal mark uses the left datum end. Reorder or edit elements, and review the numbered bending sequence before forming.");
        VBox config=card("1  ·  Conduit stock and operation editor");
        GridPane grid=new GridPane();grid.setHgap(14);grid.setVgap(8);
        grid.add(labeled("Units",units),0,0);grid.add(labeled("Straight stock length",stock),1,0);
        grid.add(labeled("Minimum available shoe spacing (manual)",clearance),2,0);
        grid.add(labeled("Bend / operation type",kind),0,1);grid.add(labeled("Direction / plane",direction),1,1);
        grid.add(labeled("Bend angle (degrees)",angle),2,1);
        grid.add(labeled("First arrow mark / centre from datum",position),0,2);
        grid.add(labeled("Offset height / saddle rise",height),1,2);
        grid.add(labeled("Roll (rolling offset only)",roll),2,2);
        grid.add(labeled("Obstacle width (four-point only)",obstacleWidth),0,3);
        config.getChildren().addAll(note("All measurements below use the selected unit. A 90° entry is an ARROW MARK position, not a finished stub height. Use the single-bend guide to find the arrow mark (stub minus manufacturer deduct)."),grid);
        Button add=primary("+ Add bend operation"),update=secondary("Update selected"),remove=secondary("Remove selected");
        Button reset=secondary("Clear all"),example=secondary("Load multi-bend example");
        add.setOnAction(e->addOperation());
        update.setOnAction(e->updateOperation());
        remove.setOnAction(e->{Operation selected=operations.getSelectionModel().getSelectedItem();if(selected!=null){entries.remove(selected);calculate();}});
        reset.setOnAction(e->{entries.clear();calculate();});
        example.setOnAction(e->loadExample());
        config.getChildren().add(new HBox(8,add,update,remove,reset,example));
        VBox list=card("2  ·  Bend operations on this conduit");
        operations.setItems(entries);operations.setPrefHeight(135);
        operations.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{if(b!=null)edit(b);});
        list.getChildren().addAll(operations,note("Select an operation to edit it. The plan shows the suggested per-operation sequence (a three-point saddle bends its centre first)."));
        VBox results=card("3  ·  Numbered pipe marks and field bending order");
        Button copy=secondary("Copy all marks & steps");
        copy.setOnAction(e->{ClipboardContent c=new ClipboardContent();c.putString(clipboardText);Clipboard.getSystemClipboard().setContent(c);});
        warning.setWrapText(true);warning.setStyle("-fx-text-fill:#92400e;-fx-font-size:12px;-fx-font-weight:bold;");
        instructions.setEditable(false);instructions.setWrapText(true);instructions.setPrefRowCount(14);
        results.getChildren().addAll(marks,warning,instructions,copy);
        page.getChildren().addAll(title,lead,config,list,results,
            note("SAFETY: A multi-bend schedule is a marking and sequencing AID, not a 3D fabrication simulation. Combined offsets can change developed pipe length and downstream field dimensions. Shoe clearances, radius, springback, spacing, rotation and installation fit must be verified with manufacturer marks and a sample."));
        ScrollPane scroll=new ScrollPane(page);scroll.setFitToWidth(true);scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        setCenter(scroll);

        kind.setOnAction(e->{showInputHelp();});showInputHelp();
        units.setOnAction(e->{
            double factor="Inches".equals(units.getValue())?1/25.4:25.4;
            for(TextField field:List.of(stock,clearance,position,height,roll,obstacleWidth))try{
                field.setText(String.format(Locale.ROOT,"%.3f",Double.parseDouble(field.getText())*factor));
            }catch(Exception ignored){}
            List<Operation> converted=new ArrayList<>();
            for(Operation op:entries)converted.add(new Operation(op.kind(),op.direction(),op.position()*factor,
                op.height()*factor,op.roll()*factor,op.width()*factor,op.angle()));
            entries.setAll(converted);calculate();
        });
        loadExample();
    }
    private void showInputHelp() {
        String selected=kind.getValue();
        roll.setDisable(!"Rolling offset".equals(selected));
        obstacleWidth.setDisable(!"Four-point saddle".equals(selected));
        height.setDisable("90° arrow mark".equals(selected)||"Single kick".equals(selected));
        angle.setDisable("90° arrow mark".equals(selected));
        if("Three-point saddle".equals(selected))angle.setValue(45d);
        if("Four-point saddle".equals(selected))angle.setValue(30d);
    }
    private void edit(Operation op){
        kind.setValue(op.kind());direction.setValue(op.direction());
        position.setText(number(op.position()));height.setText(number(op.height()));
        roll.setText(number(op.roll()));obstacleWidth.setText(number(op.width()));
        angle.setValue(op.angle());
    }
    private Operation read(){
        double p=nonnegative(position,"mark position");
        double h=nonnegative(height,"rise / height");
        double r=nonnegative(roll,"rolling dimension");
        double w=nonnegative(obstacleWidth,"obstacle width");
        String k=kind.getValue();
        if(k==null)throw new IllegalArgumentException("Select a bend type");
        if(p<=0)throw new IllegalArgumentException("Mark / centre position must be greater than zero.");
        if(!"90° arrow mark".equals(k)&&!"Single kick".equals(k)&&h<=0)
            throw new IllegalArgumentException("This operation requires positive rise / height.");
        if("Rolling offset".equals(k)&&r<=0)throw new IllegalArgumentException("A rolling offset requires positive lateral roll.");
        if("Four-point saddle".equals(k)&&w<=0)throw new IllegalArgumentException("Four-point saddle needs a positive obstacle width.");
        return new Operation(k,direction.getValue(),p,h,r,w,angle.getValue());
    }
    private void addOperation(){
        try{if(entries.size()>=12)throw new IllegalArgumentException("Maximum 12 operations per conduit.");entries.add(read());calculate();}
        catch(Exception ex){showError(ex);}
    }
    private void updateOperation(){
        Operation selected=operations.getSelectionModel().getSelectedItem();
        if(selected==null){showError(new IllegalArgumentException("Select an existing operation to update."));return;}
        try{int i=entries.indexOf(selected);entries.set(i,read());calculate();}
        catch(Exception ex){showError(ex);}
    }
    private void loadExample(){
        boolean inches="Inches".equals(units.getValue());
        entries.setAll(
            new Operation("Two-bend offset","Up",inches?15:381,inches?4:101.6,0,0,30),
            new Operation("90° arrow mark","Up",inches?66:1676.4,0,0,0,30)
        );
        stock.setText(inches?"96":"2438.4");
        clearance.setText(inches?"6":"152.4");
        calculate();
    }
    private void calculate(){
        try{
            if(entries.isEmpty()){
                marks.setPlan("No bend operations",List.of(),nonnegative(stock,"stock length"),units.getValue());
                warning.setText("Add at least two bend operations to plan a combined layout.");
                instructions.setText("");clipboardText="";return;
            }
            double length=positive(stock,"stock length"),minimum=nonnegative(clearance,"minimum shoe spacing");
            List<Operation> ordered=new ArrayList<>(entries);
            ordered.sort(Comparator.comparingDouble(Operation::position));
            List<BendMarkStripPane.Mark> display=new ArrayList<>();
            List<String> issues=new ArrayList<>();
            StringBuilder text=new StringBuilder("MULTI-BEND CONDUIT FIELD LAYOUT\n");
            text.append("Reference: LEFT / DATUM, dimensions in ").append(units.getValue()).append('\n');
            text.append("Straight stock: ").append(fmt(length)).append("; manual min. shoe spacing: ").append(fmt(minimum)).append("\n\n");
            int seq=0, group=0, totalAngular=0;
            for(Operation op:ordered){
                group++;
                double rad=Math.toRadians(op.angle()),spacing=op.height()/Math.sin(rad);
                String groupName="G"+group+" · "+op.kind();
                String dir=op.direction();
                List<Proposed> proposal=new ArrayList<>();
                switch(op.kind()){
                    case "Two-bend offset" -> {
                        proposal.add(new Proposed(op.position(),"A","ARROW","HOOK ← away from next mark","+ "+op.angle()+"° toward "+dir));
                        proposal.add(new Proposed(op.position()+spacing,"B","ARROW","Flip shoe/pipe; preserve plane","− "+op.angle()+"°; return parallel"));
                    }
                    case "Rolling offset" -> {
                        double trueOffset=Math.hypot(op.height(),op.roll());
                        double rollPlane=Math.toDegrees(Math.atan2(op.roll(),op.height()));
                        proposal.add(new Proposed(op.position(),"A","ARROW","HOOK ←; plane "+String.format(Locale.CANADA,"%.1f",rollPlane)+"° from vertical","First "+op.angle()+"° to "+dir));
                        proposal.add(new Proposed(op.position()+trueOffset/Math.sin(rad),"B","ARROW","Flip/rotate 180°; preserve roll plane","Return "+op.angle()+"° to parallel"));
                    }
                    case "90° arrow mark" ->
                        proposal.add(new Proposed(op.position(),"A","ARROW","Hook toward stub/free end","90° toward "+dir+"; verify deduct"));
                    case "Single kick" ->
                        proposal.add(new Proposed(op.position(),"A","ARROW*","Hook/orientation by exact shoe","Single "+op.angle()+"° kick to "+dir));
                    case "Three-point saddle" -> {
                        double side=op.angle()/2.0, flank=op.height()/Math.sin(Math.toRadians(side));
                        proposal.add(new Proposed(op.position(),"B","SADDLE CENTRE","Notch / centre-of-bend reference","Centre "+op.angle()+"° toward "+dir));
                        proposal.add(new Proposed(op.position()-flank,"A","ARROW","Flip for return bend; stay in plane","Outside "+side+"° opposite "+dir));
                        proposal.add(new Proposed(op.position()+flank,"C","ARROW","Keep returns in same plane","Outside "+side+"° opposite "+dir));
                    }
                    case "Four-point saddle" -> {
                        double a=op.position(),b=a+spacing,c=b+op.width(),d=c+spacing;
                        proposal.add(new Proposed(a,"A","ARROW","HOOK ← toward datum","First "+op.angle()+"° toward "+dir));
                        proposal.add(new Proposed(b,"B","ARROW","Flip/rotate; preserve plane","Return "+op.angle()+"°"));
                        proposal.add(new Proposed(c,"C","ARROW","Opposite offset; stay in plane","Bend "+op.angle()+"° away from "+dir));
                        proposal.add(new Proposed(d,"D","ARROW","Flip/rotate; preserve plane","Return "+op.angle()+"°"));
                    }
                    default -> throw new IllegalArgumentException("Unsupported operation "+op.kind());
                }
                text.append(groupName).append("  [").append(dir).append("]\n");
                for(Proposed mark:proposal){
                    seq++;
                    display.add(new BendMarkStripPane.Mark(mark.position(),seq,group+"/"+mark.name(),
                        mark.reference(),mark.hook(),mark.description()));
                    text.append("  BEND ").append(seq).append("  mark ").append(mark.name()).append(" at ")
                        .append(fmt(mark.position())).append(" from datum")
                        .append(" | ").append(mark.reference()).append(" | ").append(mark.hook())
                        .append(" | ").append(mark.description()).append("\n");
                    if(mark.position()<0)issues.add("Group "+group+" has a mark before the datum (negative position).");
                    if(mark.position()>length)issues.add("Group "+group+" has a mark beyond available stock length.");
                    totalAngular+=(int)Math.round(op.kind().contains("90°")?90:
                        op.kind().equals("Three-point saddle")&&mark.name().equals("B")?op.angle():
                        op.kind().equals("Three-point saddle")?op.angle()/2:op.angle());
                }
                if(op.kind().equals("Two-bend offset")||op.kind().equals("Rolling offset"))
                    text.append("  Offset multiplier 1/sin(").append(number(op.angle())).append("°) = ")
                        .append(number(1/Math.sin(rad))).append(" (exact geometric). Shrink/clearance depends on layout convention.\n");
                text.append("  NOTE: Recheck the actual shoe's bending centre, take-up/deduct and how completed upstream bends affect the downstream datum.\n\n");
            }
            List<BendMarkStripPane.Mark> positionOrder=new ArrayList<>(display);
            positionOrder.sort(Comparator.comparingDouble(BendMarkStripPane.Mark::position));
            for(int i=1;i<positionOrder.size();i++){
                double gap=positionOrder.get(i).position()-positionOrder.get(i-1).position();
                if(gap<1e-6)issues.add("Two or more marks share the same position ("+fmt(positionOrder.get(i).position())+").");
                else if(gap<minimum)issues.add("Gap of "+fmt(gap)+" between "+
                    positionOrder.get(i-1).name()+" and "+positionOrder.get(i).name()+
                    " is below your entered minimum shoe spacing ("+fmt(minimum)+").");
            }
            if(totalAngular>360)issues.add("Sum of individual bend angles exceeds 360°. Verify pull-point/bend limits for your installation.");
            if(ordered.size()>1)issues.add("Multiple operations are combined. Confirm developed length, cumulative shrink and physical shoe access with a field test; no automatic 3D collision model is supplied.");
            marks.setPlan("Multiple operations · "+display.size()+" numbered bends",display,length,units.getValue());
            warning.setText(issues.isEmpty()?"No range / spacing flags detected. Verify actual shoe clearances and springback.":
                "CHECK LAYOUT: "+issues.size()+" note(s) — "+String.join("  •  ",issues));
            text.append("IMPORTANT: Sequence numbers show a proposed forming order, not a guaranteed tool-access order. Reorder on the bench if the formed conduit blocks your shoe. Do not twist completed offsets out of plane.\n\n");
            text.append("MANUAL VALIDATION\n");
            for(String issue:issues)text.append("• ").append(issue).append('\n');
            instructions.setText(text.toString());clipboardText=text.toString();
        } catch(Exception ex){
            warning.setText("Check inputs: "+ex.getMessage());
            instructions.setText("");clipboardText="";
            marks.setPlan("Invalid layout",List.of(),0,units.getValue());
        }
    }
    private static TextField field(String v){TextField f=new TextField(v);f.setPrefWidth(180);f.setStyle(INPUT);return f;}
    private static String number(double v){return String.format(Locale.CANADA,"%.3f",v).replaceAll("0+$","").replaceAll("\\.$","");}
    private String fmt(double v){return number(v)+("Inches".equals(units.getValue())?" in":" mm");}
    private static double nonnegative(TextField f,String name){
        try{double d=Double.parseDouble(f.getText());if(Double.isFinite(d)&&d>=0)return d;}catch(Exception ignored){}
        throw new IllegalArgumentException(name+" must be zero or greater.");
    }
    private static double positive(TextField f,String name){
        double d=nonnegative(f,name);if(d<=0)throw new IllegalArgumentException(name+" must be greater than zero.");return d;
    }
    private static void showError(Exception ex){
        Alert a=new Alert(Alert.AlertType.WARNING,ex.getMessage(),ButtonType.OK);
        a.setHeaderText("Cannot add or update bend");a.showAndWait();
    }
    private static Label note(String s){Label l=new Label(s);l.setWrapText(true);l.setStyle("-fx-text-fill:#64748b;");return l;}
    private static VBox labeled(String title,Control input){VBox v=new VBox(4);Label l=new Label(title);l.setStyle("-fx-font-weight:bold;-fx-font-size:11px;-fx-text-fill:#334155;");v.getChildren().addAll(l,input);return v;}
    private static VBox card(String title){VBox v=new VBox(10);v.setPadding(new Insets(14));v.setStyle("-fx-background-color:white;-fx-border-color:#dbe5ef;-fx-border-radius:12;-fx-background-radius:12;");
        Label label=new Label(title);label.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");v.getChildren().add(label);return v;}
    private static Button primary(String text){Button b=new Button(text);b.setStyle("-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:8 11;");return b;}
    private static Button secondary(String text){Button b=new Button(text);b.setStyle("-fx-background-color:#eff6fa;-fx-text-fill:#155e75;-fx-border-color:#c8d9e5;-fx-padding:8 11;");return b;}
}

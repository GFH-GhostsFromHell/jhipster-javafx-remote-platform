package com.example.desktop;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Offline JavaFX 3D centreline preview for multi-bend conduit.
 *
 * Mirrors the web Bend Instruction Lab's ideal-radius, successive-local-plane
 * geometry; it is a visual planning tool, NOT a calibrated marking or shoe
 * clearance calculation. All model dimensions are millimetres.
 */
public final class Conduit3DPreviewPane extends BorderPane {
    private static final double DEG = Math.PI / 180.0;
    private static final Color[] BEND_COLOURS = {
        Color.web("#fb7185"), Color.web("#5eead4"), Color.web("#fbbf24"),
        Color.web("#c4b5fd"), Color.web("#86efac")
    };

    private record Vector(double x, double y, double z) {
        Vector add(Vector other) { return new Vector(x + other.x, y + other.y, z + other.z); }
        Vector times(double factor) { return new Vector(x * factor, y * factor, z * factor); }
        Vector cross(Vector other) {
            return new Vector(y * other.z - z * other.y,
                    z * other.x - x * other.z, x * other.y - y * other.x);
        }
        Vector unit() {
            double size = Math.sqrt(x*x + y*y + z*z);
            if (size < 1e-9 || !Double.isFinite(size)) throw new IllegalArgumentException("Bend orientation is undefined.");
            return times(1.0 / size);
        }
        boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    }
    private record Point(Vector position, int bend) {}
    private record ScreenPoint(double x, double y) {}
    private record Shape(List<Point> points, List<Integer> bendEnds) {}

    private static final class Operation {
        double before, angle, plane;
        Operation(double before, double angle, double plane) {
            this.before = before; this.angle = angle; this.plane = plane;
        }
    }

    private final ComboBox<String> preset = new ComboBox<>();
    private final TextField radiusField = new TextField("120");
    private final TextField tailField = new TextField("350");
    private final TextField stockField = new TextField("3048");
    private final TextField shoeStraightField = new TextField("");
    private final VBox operations = new VBox(8);
    private final Canvas canvas = new Canvas(960, 470);
    private final Label status = new Label();
    private final Label geometry = new Label();
    private final Label frame = new Label("Final shape");
    private final List<Operation> bends = new ArrayList<>();
    private final Button previous = new Button("Previous bend");
    private final Button next = new Button("Next bend");

    private boolean partial = false;
    private int step = 0;
    private double yaw = -.7, pitch = .38, zoom = 1;
    private double previousX, previousY;
    private Shape shape;

    public Conduit3DPreviewPane() {
        VBox page = new VBox(13);
        page.setPadding(new Insets(16));
        page.setStyle("-fx-background-color:#edf4f9;");

        Label heading = new Label("3D conduit bending · interactive preview");
        heading.setStyle("-fx-font-size:21px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label intro = note("Offline, rotatable 3D centreline. Drag with your mouse to rotate, use the scroll wheel or zoom buttons, and step through bends. This is idealized geometry—not production tape marks, clearance, shoe deduct or developed cut length.");
        page.getChildren().addAll(heading, intro);

        preset.getItems().addAll("Kicked 90 + second 90", "90 then kick", "Kick then 90",
                "Single 90", "Two-bend offset", "Rolling offset", "Back-to-back 90s",
                "Three-point saddle", "Four-point saddle", "Segmented 90", "Custom");
        preset.setValue("Kicked 90 + second 90");
        preset.setPrefWidth(230);
        radiusField.setPrefColumnCount(7);
        tailField.setPrefColumnCount(7);
        stockField.setPrefColumnCount(7);
        shoeStraightField.setPrefColumnCount(7);
        Button restore = action("Load selected layout", true);
        restore.setOnAction(e -> loadPreset());
        FlowPane setup = new FlowPane(10, 10,
                labeled("Layout preset", preset),
                labeled("Centreline radius (mm)", radiusField),
                labeled("Final straight / tail (mm)", tailField),
                labeled("Straight stock ≤ 10 ft (mm)", stockField),
                labeled("Required clear straight between arcs (mm, optional)", shoeStraightField),
                restore);
        setup.setAlignment(Pos.CENTER_LEFT);
        page.getChildren().addAll(card("Preview dimensions", setup));

        Button iso = action("Isometric", false);
        Button front = action("Front", false);
        Button side = action("Side", false);
        Button top = action("Top", false);
        Button zoomIn = action("Zoom +", false);
        Button zoomOut = action("Zoom −", false);
        Button reset = action("Reset view", false);
        iso.setOnAction(e -> view(-.7, .38));
        front.setOnAction(e -> view(0, 0));
        side.setOnAction(e -> view(-Math.PI / 2, 0));
        top.setOnAction(e -> view(0, 1.56));
        zoomIn.setOnAction(e -> {zoom=Math.min(5, zoom*1.2); draw();});
        zoomOut.setOnAction(e -> {zoom=Math.max(.2, zoom/1.2); draw();});
        reset.setOnAction(e -> {zoom=1; view(-.7,.38);});
        FlowPane cameras = new FlowPane(8, 8, iso, front, side, top, zoomIn, zoomOut, reset);
        cameras.setAlignment(Pos.CENTER_LEFT);

        StackPane viewer = new StackPane(canvas);
        viewer.setStyle("-fx-background-color:#102b44;-fx-border-color:#496f87;-fx-border-radius:10;-fx-background-radius:10;");
        viewer.setMinHeight(360);
        viewer.setPrefHeight(480);
        canvas.widthProperty().addListener((o,a,b) -> draw());
        canvas.heightProperty().addListener((o,a,b) -> draw());
        viewer.widthProperty().addListener((o,a,b) -> canvas.setWidth(Math.max(350,b.doubleValue()-16)));
        viewer.heightProperty().addListener((o,a,b) -> canvas.setHeight(Math.max(330,b.doubleValue()-12)));
        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> { previousX=e.getX(); previousY=e.getY(); });
        canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            yaw += (e.getX()-previousX)*.007;
            pitch = Math.max(-1.54, Math.min(1.54, pitch+(e.getY()-previousY)*.007));
            previousX=e.getX(); previousY=e.getY(); draw();
        });
        canvas.setOnScroll(e -> {zoom=Math.max(.2,Math.min(5,zoom*(e.getDeltaY()>0?1.1:.9)));draw();e.consume();});
        Label hint = note("Drag to rotate · mouse wheel to zoom · colour bands identify individual bends");
        frame.setStyle("-fx-background-color:#dff6f6;-fx-text-fill:#0f5168;-fx-background-radius:10;-fx-padding:6 10;");
        Button finished = action("Show finished conduit", true);
        Button through = action("Through selected bend", false);
        finished.setOnAction(e -> {partial=false;frame.setText("Final shape");draw();});
        through.setOnAction(e -> {partial=true;frame.setText("Through bend "+(step+1));draw();});
        previous.setOnAction(e -> {step=Math.max(0,step-1);partial=true;frame.setText("Through bend "+(step+1));draw();});
        next.setOnAction(e -> {step=Math.min(bends.size()-1,step+1);partial=true;frame.setText("Through bend "+(step+1));draw();});
        FlowPane stages = new FlowPane(8, 8, finished, through, previous, next, frame);
        stages.setAlignment(Pos.CENTER_LEFT);
        status.setWrapText(true);
        status.setStyle("-fx-text-fill:#be123c;-fx-font-weight:bold;");
        geometry.setWrapText(true);
        geometry.setStyle("-fx-text-fill:#334155;");
        page.getChildren().addAll(card("Rotate the finished conduit", cameras, viewer, hint, stages, status, geometry));

        Button addBend = action("+ Add bend", true);
        addBend.setOnAction(e -> {
            if(bends.size()>=12) { status.setText("Maximum 12 bends in one preview."); return; }
            bends.add(new Operation(300,30,0)); renderRows(); draw();
        });
        page.getChildren().addAll(card("Edit the individual 3D bends", note("Each lead is the straight length BEFORE that bend; angles and plane rotations act relative to the preceding bend. Change a value to immediately redraw. These are visual planning entries—not calibrated field marks."), operations, addBend));
        page.getChildren().add(note("FIELD CHECK · Confirm exact tool/shoe marks, springback, minimum stub, cumulative shrink, conduit size, bend radius, and the permitted total angle between pull points. Test complex layouts on scrap before installation."));

        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setPannable(false);
        scroll.setStyle("-fx-background-color:transparent;");
        setCenter(scroll);
        radiusField.textProperty().addListener((o,a,b) -> draw());
        tailField.textProperty().addListener((o,a,b) -> draw());
        stockField.textProperty().addListener((o,a,b) -> draw());
        shoeStraightField.textProperty().addListener((o,a,b) -> draw());
        loadPreset();
    }

    private void loadPreset() {
        bends.clear();
        switch (preset.getValue()) {
            case "Kicked 90 + second 90" -> {add(404,90,0);add(350,30,90);add(360,90,-90);}
            case "90 then kick" -> {add(404,90,0);add(350,30,90);}
            case "Kick then 90" -> {add(350,30,90);add(404,90,0);}
            case "Single 90" -> add(404,90,0);
            case "Two-bend offset" -> {add(400,30,0);add(380,-30,0);}
            case "Rolling offset" -> {add(400,30,35);add(380,-30,0);}
            case "Back-to-back 90s" -> {add(390,90,0);add(460,90,0);}
            case "Three-point saddle" -> {add(320,22.5,0);add(170,-45,0);add(170,22.5,0);}
            case "Four-point saddle" -> {add(250,30,0);add(300,-30,0);add(350,-30,0);add(300,30,0);}
            case "Segmented 90" -> {for(int i=0;i<6;i++)add(i==0?200:140,15,0);}
            default -> {add(404,90,0);add(350,30,90);add(360,90,-90);}
        }
        step=0;partial=false;zoom=1;yaw=-.7;pitch=.38;
        frame.setText("Final shape");
        renderRows();
        draw();
    }
    private void add(double before,double angle,double plane) {bends.add(new Operation(before,angle,plane));}

    private void renderRows() {
        operations.getChildren().clear();
        for (int i=0;i<bends.size();i++) {
            Operation op=bends.get(i);
            Label number=new Label("B"+(i+1));
            number.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#0e7490;");
            TextField lead=new TextField(format(op.before));
            TextField angle=new TextField(format(op.angle));
            TextField plane=new TextField(format(op.plane));
            lead.setPrefWidth(105);angle.setPrefWidth(93);plane.setPrefWidth(93);
            lead.textProperty().addListener((o,a,b) -> {op.before=parseUnverified(b);draw();});
            angle.textProperty().addListener((o,a,b) -> {op.angle=parseUnverified(b);draw();});
            plane.textProperty().addListener((o,a,b) -> {op.plane=parseUnverified(b);draw();});
            Button remove=action("Remove",false);
            remove.setOnAction(e -> {if(bends.size()>1){bends.remove(op);step=Math.min(step,bends.size()-1);renderRows();draw();}});
            FlowPane row=new FlowPane(8,8,number,labeled("Lead (mm)",lead),labeled("Angle (°)",angle),labeled("Plane (°)",plane),remove);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-border-color:#dce8f0;-fx-border-radius:7;-fx-padding:8;-fx-background-color:#f9fcfe;");
            operations.getChildren().add(row);
        }
    }
    private static double parseUnverified(String value) {
        try{return value==null||value.isBlank()?Double.NaN:Double.parseDouble(value.trim());}
        catch(NumberFormatException ex){return Double.NaN;}
    }
    private static double readNonNegative(TextField text, String name, boolean positive) {
        double n=parseUnverified(text.getText());
        if(!Double.isFinite(n)||n<0||(positive&&n==0)||n>100000)
            throw new IllegalArgumentException(name+" must be "+(positive?"greater than zero":"zero or greater")+" (up to 100,000 mm).");
        return n;
    }

    private Shape calculate() {
        double radius=readNonNegative(radiusField,"Centreline radius",true);
        double tail=readNonNegative(tailField,"Final straight length",false);
        double stock=readNonNegative(stockField,"Straight stock length",true);
        if(stock>3048+1e-6)
            throw new IllegalArgumentException("CALCULATION NOT POSSIBLE: one EMT stick is 10 ft / 3,048 mm.");
        Double clearance=null;
        if(!shoeStraightField.getText().trim().isEmpty()){
            clearance=readNonNegative(shoeStraightField,"Required minimum straight between bend arcs",false);
        }
        double developed=tail;
        Vector position=new Vector(0,0,0),tangent=new Vector(1,0,0),normal=new Vector(0,0,1);
        List<Point> points=new ArrayList<>(); List<Integer> ends=new ArrayList<>();
        points.add(new Point(position,0));
        for (int i=0;i<bends.size();i++) {
            Operation bend=bends.get(i);
            if(!Double.isFinite(bend.before)||bend.before<0||bend.before>100000
                ||!Double.isFinite(bend.angle)||Math.abs(bend.angle)>180||Math.abs(bend.angle)<1e-8
                ||!Double.isFinite(bend.plane)||Math.abs(bend.plane)>360)
                throw new IllegalArgumentException("B"+(i+1)+": lead 0–100,000 mm, nonzero angle ±180°, plane ±360°.");
            if(i>0&&bend.before<=1e-6)
                throw new IllegalArgumentException("BENDS TOO CLOSE: B"+(i+1)+
                    " has no straight length between bend arcs. Verify shoe clearance.");
            if(i>0&&clearance!=null&&bend.before+1e-6<clearance)
                throw new IllegalArgumentException("BENDS TOO CLOSE: B"+(i+1)+" has "+
                    format(bend.before)+" mm between arcs; your shoe requires "+format(clearance)+" mm.");
            developed += bend.before + radius * Math.abs(bend.angle) * DEG;
            if(!Double.isFinite(developed)||developed>stock+1e-6)
                throw new IllegalArgumentException("CALCULATION NOT POSSIBLE on one 10 ft EMT: developed centreline needs "+
                    format(developed)+" mm; stock is "+format(stock)+" mm.");
            int straightCount=Math.max(1,Math.min(300,(int)Math.ceil(bend.before/24)));
            Vector change=tangent.times(bend.before/straightCount);
            for(int k=0;k<straightCount;k++) {
                position=position.add(change);points.add(new Point(position,i));
            }
            Vector binormal=tangent.cross(normal).unit();
            double phi=bend.plane*DEG;
            Vector bendNormal=normal.times(Math.cos(phi)).add(binormal.times(Math.sin(phi))).unit();
            double totalAngle=bend.angle*DEG;
            int count=Math.max(2,(int)Math.ceil(Math.abs(bend.angle)/2.5));
            Vector entry=position;
            for(int j=1;j<=count;j++) {
                double theta=totalAngle*j/count;
                Vector arc=tangent.times(radius*Math.sin(theta))
                        .add(bendNormal.times(radius*(1-Math.cos(theta))));
                position=entry.add(arc);
                points.add(new Point(position,i+1));
            }
            Vector entryTangent=tangent;
            tangent=entryTangent.times(Math.cos(totalAngle))
                    .add(bendNormal.times(Math.sin(totalAngle))).unit();
            // Transport the local bend-plane normal into the next bend.
            normal=bendNormal.times(Math.cos(totalAngle))
                    .add(entryTangent.times(-Math.sin(totalAngle))).unit();
            ends.add(points.size()-1);
        }
        if(developed>stock+1e-6)
            throw new IllegalArgumentException("CALCULATION NOT POSSIBLE on one 10 ft EMT: developed centreline needs "+
                format(developed)+" mm; stock is "+format(stock)+" mm.");
        int tailCount=Math.max(1,Math.min(300,(int)Math.ceil(tail/24)));
        Vector delta=tangent.times(tail/tailCount);
        for(int k=0;k<tailCount;k++){position=position.add(delta);points.add(new Point(position,bends.size()+1));}
        if(points.stream().anyMatch(p -> !p.position.finite()))
            throw new IllegalArgumentException("3D geometry is not finite. Reset the preview.");
        return new Shape(points,ends);
    }

    private void view(double nextYaw,double nextPitch) {yaw=nextYaw;pitch=nextPitch;draw();}
    private ScreenPoint camera(Vector v) {
        double cy=Math.cos(yaw),sy=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);
        double x=v.x*cy-v.y*sy, y=v.x*sy+v.y*cy;
        return new ScreenPoint(x,v.z*cp-y*sp);
    }
    private void draw() {
        GraphicsContext g=canvas.getGraphicsContext2D();
        double w=canvas.getWidth(),h=canvas.getHeight();
        g.setFill(new LinearGradient(0,0,1,1,true,CycleMethod.NO_CYCLE,
                new Stop(0,Color.web("#071e30")),new Stop(1,Color.web("#1b4966"))));
        g.fillRect(0,0,w,h);
        g.setFill(Color.web("#d1e7f3"));
        g.setFont(Font.font("System",14));
        g.fillText("3D CENTRELINE · GEOMETRY ONLY · NOT SHOP MARKS",15,25);
        if(bends.isEmpty()) return;
        try {
            shape=calculate();
            int end=partial?shape.bendEnds.get(Math.min(step,shape.bendEnds.size()-1)):shape.points.size()-1;
            List<ScreenPoint> projected=new ArrayList<>(end+1);
            for(int i=0;i<=end;i++)projected.add(camera(shape.points.get(i).position));
            double minx=projected.stream().mapToDouble(p->p.x).min().orElse(0),
                    maxx=projected.stream().mapToDouble(p->p.x).max().orElse(1),
                    miny=projected.stream().mapToDouble(p->p.y).min().orElse(0),
                    maxy=projected.stream().mapToDouble(p->p.y).max().orElse(1);
            if(!Double.isFinite(minx+maxx+miny+maxy))throw new IllegalArgumentException("Invalid 3D projection.");
            double scale=Math.min((w-125)/Math.max(1,maxx-minx),(h-115)/Math.max(1,maxy-miny))*zoom;
            double midx=(minx+maxx)/2,midy=(miny+maxy)/2;
            List<ScreenPoint> screen=new ArrayList<>(projected.size());
            for(ScreenPoint p:projected)screen.add(new ScreenPoint(w/2+(p.x-midx)*scale,h/2-(p.y-midy)*scale));
            if(screen.size()>1) {
                stroke(g,screen,0,end,21,Color.web("#031421"));
                stroke(g,screen,0,end,16,Color.web("#64899f"));
                stroke(g,screen,0,end,11,Color.web("#b0d9e6"));
                stroke(g,screen,0,end,3,Color.web("#e4fcff"));
            }
            for(int i=0;i<shape.bendEnds.size();i++){
                int point=shape.bendEnds.get(i);
                if(point>end)break;
                int start=point;
                while(start>0&&shape.points.get(start).bend==i+1)start--;
                stroke(g,screen,start,point,7,BEND_COLOURS[i%BEND_COLOURS.length]);
                ScreenPoint p=screen.get(point);
                g.setFill(Color.web("#ffefc3"));g.fillOval(p.x-5,p.y-5,10,10);
                g.setFill(Color.WHITE);g.setFont(Font.font("System",16));
                g.fillText("B"+(i+1),p.x+10,p.y-11);
            }
            ScreenPoint start=screen.get(0),finish=screen.get(screen.size()-1);
            g.setFill(Color.web("#9cf5ff"));g.setFont(Font.font("System",15));
            g.fillText("START",start.x+7,start.y-14);
            g.fillText(partial?"IN PROGRESS":"FINISH",finish.x+7,finish.y+23);
            status.setText("");
            double developed=readNonNegative(tailField,"Final straight length",false);
            for(Operation op:bends)developed+=op.before+readNonNegative(radiusField,"Radius",true)*Math.abs(op.angle)*DEG;
            geometry.setText(String.format(Locale.ROOT,
                    "%d bends · estimated developed centreline %.1f / %.1f mm stock · %d points · %s. %s Actual shoe radius, gain and access require field verification.",
                    bends.size(),developed,readNonNegative(stockField,"Stock",true),screen.size(),
                    partial?"through bend "+(step+1):"finished conduit",
                    shoeStraightField.getText().isBlank()?"Shoe clearance NOT VERIFIED.":"Shoe gap checked."));
        } catch (RuntimeException error) {
            shape=null;
            status.setText("3D preview: "+error.getMessage());
            geometry.setText("Check the dimension entries or load a sample layout.");
            g.setFill(Color.web("#e2f3ff"));g.setFont(Font.font("System",20));
            g.fillText("Preview unavailable — check dimensions",20,76);
        }
        previous.setDisable(!partial||step<=0);
        next.setDisable(!partial||step>=bends.size()-1);
    }
    private static void stroke(GraphicsContext g,List<ScreenPoint> points,int first,int last,double width,Color colour) {
        if(last<=first)return;
        g.setStroke(colour);g.setLineWidth(width);
        g.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        g.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        g.beginPath();
        g.moveTo(points.get(first).x,points.get(first).y);
        for(int i=first+1;i<=last;i++)g.lineTo(points.get(i).x,points.get(i).y);
        g.stroke();
    }
    private static String format(double value){return String.format(Locale.ROOT,"%.1f",value);}
    private static Button action(String text,boolean primary) {
        Button button=new Button(text);
        button.setStyle(primary
                ?"-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:10 12;"
                :"-fx-background-color:#d9eaf2;-fx-text-fill:#0b4662;-fx-font-weight:bold;-fx-padding:10 12;");
        return button;
    }
    private static Label note(String text){
        Label label=new Label(text);label.setWrapText(true);
        label.setStyle("-fx-text-fill:#475569;-fx-font-size:13px;");
        return label;
    }
    private static VBox labeled(String text,Control control){
        Label label=new Label(text);
        label.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#334155;");
        VBox box=new VBox(4,label,control);box.setAlignment(Pos.CENTER_LEFT);return box;
    }
    private static VBox card(String heading,javafx.scene.Node... children){
        Label title=new Label(heading);
        title.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        VBox card=new VBox(12);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-background-color:white;-fx-border-color:#cbdce9;-fx-border-radius:12;-fx-background-radius:12;");
        card.getChildren().add(title);card.getChildren().addAll(children);
        return card;
    }
}

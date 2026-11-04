package com.example.desktop;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.stage.FileChooser;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Preview-only manual electronic drawing takeoff.
 * Measurements are based on an explicitly calibrated PDF page scale, never on guessed dimensions.
 * Sends counted/measured quantities to the estimate; does not promise automatic symbol detection.
 */
public final class EstimatingPdfTakeoffPane extends BorderPane {
    @FunctionalInterface public interface TakeoffSink {
        void add(String system,String phase,String drawing,String description,double quantity,String unit);
    }
    private static final String FIELD="-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-background-radius:7;-fx-border-radius:7;-fx-padding:7;";
    private final TakeoffSink sink;
    private PDDocument pdf;
    private PDFRenderer renderer;
    private File currentFile;
    private int pageIndex=0;
    private double zoom=1;
    private WritableImage pageImage;
    private final Map<Integer,Double> metresPerPixel=new HashMap<>();
    private final ObservableList<Measure> measurements=FXCollections.observableArrayList();
    private final List<Point2D> pending=new ArrayList<>();
    private final Pane drawingCanvas=new Pane();
    private final ImageView imageView=new ImageView();
    private final ScrollPane planScroll=new ScrollPane(drawingCanvas);
    private final Label pageLabel=new Label("No drawing loaded"),scaleLabel=new Label("Scale: not calibrated"),statusLabel=new Label("Choose a tool, then click on a loaded drawing.");
    private final TextField item=new TextField(),phase=new TextField(),drawingRevision=new TextField();
    private final ComboBox<String> system=new ComboBox<>();
    private final RadioButton calibrate=new RadioButton("Calibrate"),count=new RadioButton("Count"),length=new RadioButton("Measure length"),area=new RadioButton("Measure area");
    private final TableView<Measure> table=new TableView<>(measurements);
    private final TextField knownLength=new TextField("10");
    private final Slider zoomSlider=new Slider(.5,2.5,1);

    public EstimatingPdfTakeoffPane(TakeoffSink sink){
        this.sink=sink;
        setPadding(new Insets(12));setStyle("-fx-background-color:#f1f5f9;");
        setTop(header());
        planScroll.setFitToHeight(false);planScroll.setFitToWidth(false);
        planScroll.setPannable(true);planScroll.setStyle("-fx-background-color:#e2e8f0;");
        drawingCanvas.getChildren().add(imageView);
        drawingCanvas.setOnMouseClicked(e->clicked(e.getX()/zoom,e.getY()/zoom,e.getClickCount()));
        VBox plan=new VBox(7,planScroll,statusLabel);
        VBox.setVgrow(planScroll,Priority.ALWAYS);
        statusLabel.setWrapText(true);statusLabel.setStyle("-fx-text-fill:#475569;-fx-font-size:11px;");
        VBox right=sidePanel();right.setPrefWidth(340);
        SplitPane body=new SplitPane(plan,right);body.setDividerPositions(.71);
        setCenter(body);
        setPrefHeight(670);
        pageLabel.setStyle("-fx-font-weight:bold;-fx-text-fill:#0f4c81;");
        scaleLabel.setStyle("-fx-font-weight:bold;-fx-text-fill:#92400e;");
        zoomSlider.valueProperty().addListener((o,a,b)->{zoom=b.doubleValue();redraw();});
        selectMode(count);
    }
    private Node header(){
        VBox h=new VBox(9);h.setPadding(new Insets(11));
        h.setStyle("-fx-background-color:white;-fx-border-color:#dbe5ef;-fx-border-radius:10;-fx-background-radius:10;");
        Label title=new Label("PDF Plan Takeoff — Manual Measurement");
        title.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label help=new Label("Load a local PDF. Calibrate each drawing sheet using a known dimension before measuring lengths/areas. Count devices by clicking each symbol. Nothing is uploaded to the server.");
        help.setWrapText(true);help.setStyle("-fx-text-fill:#64748b;-fx-font-size:11px;");
        Button open=action("Open PDF",true);open.setOnAction(e->openFile());
        Button previous=action("◀ Page",false);previous.setOnAction(e->{if(pageIndex>0){pageIndex--;renderPage();}});
        Button next=action("Page ▶",false);next.setOnAction(e->{if(pdf!=null&&pageIndex+1<pdf.getNumberOfPages()){pageIndex++;renderPage();}});
        HBox pages=new HBox(9,open,previous,pageLabel,next,scaleLabel);pages.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup tools=new ToggleGroup();for(RadioButton rb:List.of(calibrate,count,length,area)){rb.setToggleGroup(tools);rb.setStyle("-fx-font-size:11px;");}
        knownLength.setPrefWidth(65);knownLength.setStyle(FIELD);
        HBox controls=new HBox(12,calibrate,knownLength,new Label("known metres"),count,length,area,new Label("Zoom"),zoomSlider);
        controls.setAlignment(Pos.CENTER_LEFT);HBox.setHgrow(zoomSlider,Priority.ALWAYS);
        h.getChildren().addAll(title,help,pages,controls);
        return h;
    }
    private VBox sidePanel(){
        VBox side=new VBox(10);side.setPadding(new Insets(10));
        side.setStyle("-fx-background-color:white;-fx-border-color:#dbe5ef;-fx-border-radius:10;-fx-background-radius:10;");
        Label title=new Label("Measured quantities");title.setStyle("-fx-font-size:15px;-fx-font-weight:bold;");
        item.setPromptText("Item / assembly description");item.setText("Electrical device / run");item.setStyle(FIELD);
        phase.setPromptText("Floor, room, or phase");phase.setText("General");phase.setStyle(FIELD);
        drawingRevision.setPromptText("Revision / addendum");drawingRevision.setStyle(FIELD);
        system.getItems().addAll("01 Service / distribution","02 Feeders","03 Branch wiring","04 Lighting / controls",
            "05 Devices","06 Mechanical power","07 Fire alarm / low voltage","08 Grounding / bonding","09 Supports / misc.");
        system.setValue("03 Branch wiring");system.setEditable(true);system.setMaxWidth(Double.MAX_VALUE);
        TableColumn<Measure,String> description=new TableColumn<>("Item");
        description.setCellValueFactory(c->new SimpleStringProperty(c.getValue().description));description.setPrefWidth(155);
        TableColumn<Measure,String> qty=new TableColumn<>("Quantity");
        qty.setCellValueFactory(c->new SimpleStringProperty(String.format(Locale.CANADA,"%.2f %s",c.getValue().quantity,c.getValue().unit)));qty.setPrefWidth(95);
        table.getColumns().addAll(description,qty);table.setPrefHeight(260);
        Button undo=action("Undo last",false);undo.setOnAction(e->{if(!pending.isEmpty())pending.remove(pending.size()-1);else if(!measurements.isEmpty())measurements.remove(measurements.size()-1);redraw();updateStatus();});
        Button clear=action("Clear measurements",false);clear.setOnAction(e->{
            if(!measurements.isEmpty()||!pending.isEmpty()){
                Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Discard uncommitted drawing marks?",ButtonType.YES,ButtonType.NO);
                if(a.showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;
            }
            pending.clear();measurements.clear();redraw();updateStatus();
        });
        Button send=action("Send quantities to estimate",true);send.setWrapText(true);
        send.setOnAction(e->commit());
        Label note=new Label("Only quantities are sent to the estimate. Check labour/material unit pricing, CEC requirements, scale, drawing revision and takeoff completeness before bidding.");
        note.setWrapText(true);note.setStyle("-fx-font-size:10px;-fx-text-fill:#64748b;");
        side.getChildren().addAll(title,labeled("System",system),labeled("Description",item),labeled("Area / phase",phase),
            labeled("Drawing revision",drawingRevision),table,new HBox(7,undo,clear),send,note);
        VBox.setVgrow(table,Priority.ALWAYS);
        return side;
    }
    private static VBox labeled(String title,Node input){Label l=new Label(title);l.setStyle("-fx-font-weight:bold;-fx-font-size:11px;");return new VBox(4,l,input);}
    private static Button action(String label,boolean primary){
        Button b=new Button(label);
        b.setStyle(primary?"-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:7;-fx-padding:7 10;":
            "-fx-background-color:#eff6fa;-fx-text-fill:#155e75;-fx-border-color:#bfdae9;-fx-background-radius:7;-fx-border-radius:7;-fx-padding:7 10;");
        return b;
    }
    private void selectMode(RadioButton mode){mode.setSelected(true);pending.clear();redraw();}
    private void openFile(){
        FileChooser chooser=new FileChooser();chooser.setTitle("Open construction drawing PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF drawings","*.pdf"));
        File selected=chooser.showOpenDialog(getScene()==null?null:getScene().getWindow());
        if(selected==null)return;
        try{
            PDDocument doc=Loader.loadPDF(selected);
            if(doc.getNumberOfPages()==0){doc.close();throw new IllegalArgumentException("PDF has no pages");}
            if(pdf!=null)pdf.close();
            pdf=doc;renderer=new PDFRenderer(doc);currentFile=selected;pageIndex=0;
            measurements.clear();pending.clear();metresPerPixel.clear();renderPage();
        }catch(Exception ex){failure("Cannot open PDF",ex);}
    }
    private void renderPage(){
        if(pdf==null)return;
        try{
            BufferedImage buffered=renderer.renderImageWithDPI(pageIndex,90);
            int w=buffered.getWidth(),h=buffered.getHeight();
            if((long)w*h>26000000L)throw new IllegalArgumentException("This page is too large for preview rendering");
            int[] rgb=new int[w*h];buffered.getRGB(0,0,w,h,rgb,0,w);
            WritableImage fx=new WritableImage(w,h);
            fx.getPixelWriter().setPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),rgb,0,w);
            pageImage=fx;
            pageLabel.setText("Page "+(pageIndex+1)+" of "+pdf.getNumberOfPages());
            pending.clear();redraw();updateStatus();
        }catch(Exception ex){failure("Could not render PDF page",ex);}
    }
    private void redraw(){
        drawingCanvas.getChildren().clear();
        if(pageImage==null){drawingCanvas.setPrefSize(600,400);return;}
        imageView.setImage(pageImage);imageView.setFitWidth(pageImage.getWidth()*zoom);imageView.setFitHeight(pageImage.getHeight()*zoom);
        drawingCanvas.setPrefSize(pageImage.getWidth()*zoom,pageImage.getHeight()*zoom);
        drawingCanvas.getChildren().add(imageView);
        for(Measure m:measurements)if(m.page==pageIndex)drawMeasure(m);
        if(!pending.isEmpty()){
            for(Point2D p:pending)drawPoint(p,Color.DEEPSKYBLUE,5.0);
            for(int i=1;i<pending.size();i++)drawSegment(pending.get(i-1),pending.get(i),Color.DEEPSKYBLUE);
        }
        Double scale=metresPerPixel.get(pageIndex);
        scaleLabel.setText(scale==null?"Scale: NOT CALIBRATED":String.format(Locale.CANADA,"Scale: 1 image px = %.5f m",scale));
    }
    private void drawMeasure(Measure m){
        if(m.points.size()==1){drawPoint(m.points.get(0),Color.DARKORANGE,5);return;}
        if(m.method.equals("area")){
            Polygon shape=new Polygon();
            for(Point2D p:m.points)shape.getPoints().addAll(p.getX()*zoom,p.getY()*zoom);
            shape.setFill(Color.rgb(14,116,144,.17));shape.setStroke(Color.DARKCYAN);shape.setStrokeWidth(2);
            shape.setMouseTransparent(true);drawingCanvas.getChildren().add(shape);
        }
        for(int i=1;i<m.points.size();i++)drawSegment(m.points.get(i-1),m.points.get(i),Color.DARKORANGE);
        for(Point2D p:m.points)drawPoint(p,Color.DARKORANGE,4);
    }
    private void drawPoint(Point2D p,Color color,double radius){
        Circle c=new Circle(p.getX()*zoom,p.getY()*zoom,radius,color);c.setStroke(Color.WHITE);c.setStrokeWidth(1);
        c.setMouseTransparent(true);drawingCanvas.getChildren().add(c);
    }
    private void drawSegment(Point2D a,Point2D b,Color color){
        Line line=new Line(a.getX()*zoom,a.getY()*zoom,b.getX()*zoom,b.getY()*zoom);
        line.setStroke(color);line.setStrokeWidth(2);line.setMouseTransparent(true);drawingCanvas.getChildren().add(line);
    }
    private void clicked(double x,double y,int clickCount){
        if(pdf==null||pageImage==null)return;
        if(x<0||y<0||x>pageImage.getWidth()||y>pageImage.getHeight())return;
        Point2D point=new Point2D(x,y);
        if(calibrate.isSelected()){
            pending.add(point);
            if(pending.size()==2){
                double pixels=pending.get(0).distance(pending.get(1));double metres=numeric(knownLength.getText());
                if(pixels<2||metres<=0){message("Scale calibration","Enter a known drawing dimension in metres greater than zero.");}
                else{
                    metresPerPixel.put(pageIndex,metres/pixels);
                    statusLabel.setText("Scale calibrated on page "+(pageIndex+1)+". Recalibrate each page with a different drawing scale.");
                }
                pending.clear();
            }
        }else if(count.isSelected()){
            measurements.add(new Measure(pageIndex,"count",label(),systemText(),phaseText(),1,"ea",List.of(point)));
        }else if(length.isSelected()){
            if(!requiresScale())return;
            pending.add(point);
            if(pending.size()==2){
                double metres=pending.get(0).distance(pending.get(1))*metresPerPixel.get(pageIndex);
                measurements.add(new Measure(pageIndex,"length",label(),systemText(),phaseText(),metres,"m",List.copyOf(pending)));pending.clear();
            }
        }else if(area.isSelected()){
            if(!requiresScale())return;
            // Polygon points are added with single clicks; double-click closes a polygon after at least 3 points.
            if(clickCount>1&&pending.size()>=2){
                if(pending.get(pending.size()-1).distance(point)>2)pending.add(point);
                if(pending.size()>=3){
                    double squarePixels=0;
                    for(int i=0;i<pending.size();i++){
                        Point2D a=pending.get(i),b=pending.get((i+1)%pending.size());
                        squarePixels+=a.getX()*b.getY()-b.getX()*a.getY();
                    }
                    double factor=metresPerPixel.get(pageIndex);
                    measurements.add(new Measure(pageIndex,"area",label(),systemText(),phaseText(),Math.abs(squarePixels)/2*factor*factor,"m²",List.copyOf(pending)));
                }
                pending.clear();
            }else if(clickCount==1)pending.add(point);
        }
        if(measurements.size()>15000){measurements.remove(measurements.size()-1);message("Takeoff limit","This preview supports up to 15,000 individual marks. Commit quantities to the estimate before adding more.");}
        redraw();updateStatus();
    }
    private boolean requiresScale(){
        if(metresPerPixel.containsKey(pageIndex))return true;
        message("Calibrate scale first","Choose Calibrate, enter a known distance in metres, and click its two endpoints on this PDF page.");
        return false;
    }
    private String label(){return item.getText().isBlank()?"Unspecified item":item.getText().trim();}
    private String systemText(){return system.getValue()==null||system.getValue().isBlank()?"Uncategorized":system.getValue();}
    private String phaseText(){return phase.getText().isBlank()?"General":phase.getText().trim();}
    private static double numeric(String text){try{return Double.parseDouble(text.trim());}catch(Exception e){return 0;}}
    private void updateStatus(){
        Map<String,Double> totals=new LinkedHashMap<>();
        for(Measure m:measurements)totals.merge(m.method+" ("+m.unit+")",m.quantity,Double::sum);
        StringBuilder summary=new StringBuilder(measurements.size()+" uncommitted drawing marks");
        totals.forEach((key,value)->summary.append(String.format(Locale.CANADA," | %s: %.2f",key,value)));
        statusLabel.setText(summary.toString());
    }
    private void commit(){
        if(measurements.isEmpty()){message("No measurements","Count or measure items before sending them to the estimate.");return;}
        if(!pending.isEmpty()){message("Unfinished measurement","Complete or undo the unfinished calibration/measurement first.");return;}
        record Key(String system,String phase,String drawing,String description,String unit){}
        Map<Key,Double> grouped=new LinkedHashMap<>();
        for(Measure m:measurements){
            String reference=(currentFile==null?"Drawing":currentFile.getName())+" p."+ (m.page+1)+
                (drawingRevision.getText().isBlank()?"":" rev "+drawingRevision.getText().trim());
            grouped.merge(new Key(m.system,m.phase,reference,m.description,m.unit),m.quantity,Double::sum);
        }
        for(Map.Entry<Key,Double> e:grouped.entrySet()){
            Key k=e.getKey();sink.add(k.system(),k.phase(),k.drawing(),k.description(),e.getValue(),k.unit());
        }
        measurements.clear();pending.clear();redraw();updateStatus();
        message("Takeoff quantities transferred","Transferred "+grouped.size()+" measured takeoff groups to the estimate. Check material/unit prices, labour hours, scale, and scope before quoting.");
    }
    private static void message(String title,String text){
        Alert a=new Alert(Alert.AlertType.INFORMATION,text,ButtonType.OK);a.setTitle(title);a.setHeaderText(null);a.showAndWait();
    }
    private static void failure(String title,Exception ex){
        Alert a=new Alert(Alert.AlertType.ERROR,ex.getMessage()==null?ex.toString():ex.getMessage(),ButtonType.OK);
        a.setTitle(title);a.setHeaderText(null);a.showAndWait();
    }
    private record Measure(int page,String method,String description,String system,String phase,double quantity,String unit,List<Point2D> points){}
}

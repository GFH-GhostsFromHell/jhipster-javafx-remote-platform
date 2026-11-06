package com.example.desktop;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Schematic of the unbent conduit. Marks are measured from one named datum end.
 * Geometry is schematic at tight spacing; exact dimensions are in the table.
 */
final class BendMarkStripPane extends VBox {
    record Mark(double position, int sequence, String name, String shoeSymbol,
                String hook, String action, boolean relativeReference) {
        Mark(double position, int sequence, String name, String shoeSymbol, String hook, String action) {
            this(position,sequence,name,shoeSymbol,hook,action,false);
        }
    }
    private final Canvas canvas = new Canvas(980,226);
    private final Label caption = new Label();
    private final Label advisory = new Label();
    private final TableView<Mark> table = new TableView<>();
    private List<Mark> current = List.of();
    private String unit = "mm";
    private String tapePrecision = "Exact";
    private double stockLength = 0;

    BendMarkStripPane() {
        setSpacing(7);
        setPadding(new Insets(10));
        setStyle("-fx-background-color:#fff;-fx-background-radius:12;-fx-border-color:#d9e5ef;-fx-border-radius:12;");
        caption.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        advisory.setWrapText(true);
        advisory.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().addAll(
            col("#",m->Integer.toString(m.sequence()),50),
            col("Mark",Mark::name,80),
            col("From datum",m->format(m.position()),115),
            col("Shoe mark",Mark::shoeSymbol,100),
            col("HOOK FACING ← / →",Mark::hook,230),
            col("Bend action",Mark::action,300)
        );
        table.setPrefHeight(195); table.setFixedCellSize(31);
        getChildren().addAll(caption,canvas,advisory,table);
        setPlan("No marks to display",List.of(),0,"Millimetres");
    }

    private TableColumn<Mark,String> col(String title,java.util.function.Function<Mark,String> fn,double width) {
        TableColumn<Mark,String> c=new TableColumn<>(title);
        c.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(fn.apply(d.getValue())));
        c.setPrefWidth(width);
        return c;
    }
    void setPlan(String title,List<Mark> marks,double stockLength,String units) {
        setPlan(title,marks,stockLength,units,"Exact");
    }
    void setPlan(String title,List<Mark> marks,double stockLength,String units,String precision) {
        this.current=new ArrayList<>(marks);
        this.tapePrecision=precision;
        this.unit="Inches".equals(units)?"in":"mm";
        this.stockLength=stockLength;
        caption.setText(title+"  •  All marks measured from LEFT / DATUM unless noted");
        table.setItems(FXCollections.observableArrayList(marks));
        table.setPrefHeight(Math.min(320, 38+31*Math.max(3,marks.size())));
        boolean relative=marks.stream().anyMatch(Mark::relativeReference);
        advisory.setText(relative
            ?"Diagram is schematic. A mark labelled RELATIVE is measured from an existing bend, not from the same straight-pipe datum. Confirm manufacturer reference geometry."
            :"LEFT is the original tape-zero end; RIGHT is the far end. Every mark shows a hook-facing arrow or ? VERIFY. Bender flips and 180° conduit rotation are separate steps; check your actual shoe.");
        draw();
    }
    private String format(double value) {
        if("Exact".equals(tapePrecision))
            return String.format(Locale.CANADA,"%.2f %s",value,unit);
        if("mm".equals(unit)){
            double increment="5 mm".equals(tapePrecision)?5d:1d;
            return String.format(Locale.CANADA,"%.0f mm",Math.round(value/increment)*increment);
        }
        int denominator="1/4 in".equals(tapePrecision)?4:"1/8 in".equals(tapePrecision)?8:16;
        long ticks=Math.round(Math.abs(value)*denominator),whole=ticks/denominator,part=ticks%denominator;
        String fraction;
        if(part==0)fraction=Long.toString(whole);
        else{
            int a=(int)part,b=denominator;
            while(b!=0){int tmp=a%b;a=b;b=tmp;}
            fraction=(whole==0?"":whole+" ")+(part/a)+"/"+(denominator/a);
        }
        return (value<0?"-":"")+fraction+" in";
    }
    private void draw() {
        GraphicsContext g=canvas.getGraphicsContext2D();
        double w=canvas.getWidth(),h=canvas.getHeight();
        g.setFill(Color.web("#f7fafc"));g.fillRoundRect(0,0,w,h,12,12);
        g.setFont(Font.font("Segoe UI",12));
        g.setFill(Color.web("#475569"));
        g.fillText("STRAIGHT PIPE · before bending · face/roll plane marked on conduit",18,22);
        double left=58,right=w-42,y=128,span=right-left;
        double max=current.stream().filter(m->!m.relativeReference()).mapToDouble(Mark::position).max().orElse(100);
        if(stockLength>0)max=Math.max(max,stockLength);
        max=Math.max(1,max*1.06);
        g.setLineWidth(13);g.setStroke(Color.web("#8fa8b8"));g.strokeLine(left,y,right,y);
        g.setLineWidth(2);g.setStroke(Color.web("#c8d9e5"));g.strokeLine(left,y-4,right,y-4);
        g.setLineWidth(1.3);g.setStroke(Color.web("#64748b"));
        g.strokeLine(left,y-23,left,y+25);g.strokeLine(right,y-15,right,y+15);
        g.setFill(Color.web("#0f172a"));g.fillText("DATUM",left-26,y+49);
        g.setFill(Color.web("#334155"));g.fillText("+"+format(max),right-80,y+49);

        List<Mark> ordered=new ArrayList<>(current);
        ordered.sort(Comparator.comparingDouble(Mark::position));
        for(int i=0;i<ordered.size();i++){
            Mark mark=ordered.get(i);
            double position=Math.max(0,mark.position());
            double x=left+span*Math.min(1,position/max);
            if(mark.relativeReference())x=Math.min(right-20,Math.max(left+80,x));
            boolean upper=i%2==0;
            double tick=upper?66:188;
            g.setStroke(Color.web(mark.relativeReference()?"#a16207":"#0e7490"));
            g.setLineWidth(2.3);g.strokeLine(x,y-11,x,tick);
            g.setFill(Color.web(mark.relativeReference()?"#92400e":"#0e7490"));
            g.fillOval(x-14,tick-14,28,28);
            g.setFill(Color.WHITE);g.setFont(Font.font("Segoe UI Semibold",13));
            String num=Integer.toString(mark.sequence());
            g.fillText(num,x-(num.length()>1?8:4),tick+5);
            g.setFont(Font.font("Segoe UI",11));
            g.setFill(Color.web("#0f172a"));
            String subtitle=mark.name()+" · "+(mark.relativeReference()?"RELATIVE":format(mark.position()));
            double labelX=Math.max(10,Math.min(w-240,x-48));
            g.fillText(subtitle,labelX,upper?43:215);
            // Show a compact shoe/hook-facing arrow just above each mark.
            String hook=mark.hook();
            String direction=hook.contains("←")?"← HOOK":hook.contains("→")?"HOOK →":"? VERIFY";
            boolean unknown=!(hook.contains("←")||hook.contains("→"));
            double px=Math.max(5,Math.min(w-116,x-47)),py=upper?75:151;
            g.setFill(Color.web(unknown?"#92400e":"#075985"));
            g.fillRoundRect(px,py,104,24,8,8);
            g.setFill(Color.WHITE);g.setFont(Font.font("Segoe UI Semibold",12));
            g.fillText(direction,px+10,py+17);
        }
        if(current.isEmpty()){
            g.setFill(Color.web("#475569"));g.setFont(Font.font("Segoe UI",14));
            g.fillText("Enter a bend layout and choose Calculate to show numbered marks.",150,86);
        }
    }
}

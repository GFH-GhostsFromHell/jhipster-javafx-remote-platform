package com.example.desktop;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

import java.util.Locale;

/** Field quick reference with manufacturer-sourced rounded multiplier and take-up values. */
final class ConduitBendReferencePane extends ScrollPane {
    private static final String KLEIN="https://data.kleintools.com/sites/all/product_assets/documents/instructions/klein/ConduitBenderGuide.pdf";
    private static final String GREENLEE="https://www.greenlee.com/us/en/844a";
    ConduitBendReferencePane(){
        VBox page=new VBox(13);page.setPadding(new Insets(14));
        Label title=new Label("Conduit Bending · Quick Reference");
        title.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        page.getChildren().addAll(title,notice("Field rounded ratios; actual shoe marks, bending radius, deduct and springback vary by make and conduit size."));
        GridPane chart=new GridPane();chart.setHgap(0);chart.setVgap(0);
        addCells(chart,0,"Angle","Multiplier","Shrink per 1 in rise","When used");
        Object[][] data={
            {10,"6.0","1/16 in (0.063)","Long, shallow offset"},
            {15,"3.86","1/8 in (0.125)","Gentle offset"},
            {22.5,"2.6","3/16 in (0.188)","Common saddle outside bends"},
            {30,"2.0","1/4 in (0.250)","Common field offset"},
            {45,"1.4","3/8 in (0.375)","Compact offset"},
            {60,"1.2","1/2 in (0.500)","Steep offset; greater deformation risk"}
        };
        for(int i=0;i<data.length;i++)
            addCells(chart,i+1,number(((Number)data[i][0]).doubleValue())+"°",(String)data[i][1],(String)data[i][2],(String)data[i][3]);
        page.getChildren().add(card("Offset multiplier and field shrink chart",chart,notice(
            "Chart values are rounded manufacturer field constants, not exact trig ratios. Use: spacing = true offset × multiplier, shrink allowance = rise × listed ratio. Mark placement depends on whether the near or far obstacle reference is fixed.")));
        GridPane deduct=new GridPane();deduct.setHgap(0);deduct.setVgap(0);
        addCells(deduct,0,"Hand bender","Typical 90° deduct","Verify");
        addCells(deduct,1,"1/2 in EMT","5 in / 127 mm","Check shoe stamp");
        addCells(deduct,2,"3/4 in EMT","6 in / 152.4 mm","Check shoe stamp");
        addCells(deduct,3,"1 in EMT","8 in / 203.2 mm","Check shoe stamp");
        addCells(deduct,4,"1 1/4 in EMT","11 in / 279.4 mm","Check shoe stamp");
        page.getChildren().add(card("Common 90° stub-up deduct / take-up",deduct,
            notice("90° ARROW mark = finished stub-up height − actual deduct. These are examples from a Klein guide, not universal presets for every head.")));
        page.getChildren().add(card("Three-bend saddle · field marks versus geometry",
            notice("For a 45° centre bend (22.5° side bends), traditional centre-notch / outside-arrow field layouts commonly use a 2.5 × rise spacing factor. The mathematical bend-centre spacing factor is approximately 2.613 × rise. These use DIFFERENT reference marks."),
            notice("For a 30° centre bend (15° outside), a traditional field factor is approximately 3.8, while geometric centre-to-centre spacing is approximately 3.864. Other angles require shoe calibration; do not use centre distances directly as arrow positions.")));
        TextArea sheet=new TextArea(
            "FIELD BENDING QUICK SHEET\n\n"+
            "REFERENCE END: mark a fixed datum at the conduit end; measure and label every mark before bending.\n\n"+
            "90° STUB-UP: arrow mark = requested stub height − head deduct. Hook faces the short end being bent up.\n\n"+
            "BACK-TO-BACK 90s: first 90 uses ARROW; second uses STAR/back-of-90 from the back of the first finished bend. Keep both bends coplanar.\n\n"+
            "TWO-BEND OFFSET: two equal angles, equal and opposite. Mark spacing = true offset × multiplier. For a far obstruction reference, add the chart's shrink allowance when the method calls for it.\n\n"+
            "ROLLING OFFSET: true offset = √(vertical rise² + sideways roll²). Use true offset for spacing. Plane rotation from vertical = atan(roll/rise).\n\n"+
            "THREE-POINT SADDLE: choose a MARKING METHOD before measuring side bends. Traditional field multipliers (45° centre = 2.5; 30° centre = 3.8) use a centre notch and outside arrow. Pure geometric centre-to-centre spacing differs and requires shoe calibration.\n\n"+
            "FOUR-POINT SADDLE: two consecutive two-bend offsets in opposite directions. Mark and check all four bend locations and shoe clearance.\n\n"+
            "KICKED 90: establish 90 stub using arrow and deduct; kick is measured from its own selected bend reference. Do not assume a universal second mark from kick rise alone.\n\n"+
            "SEGMENTED 90: divide 90° into small bends. Confirm spacing and tangent marks with shoe radius; test the resulting centreline radius.\n\n"+
            "GAIN / PRECUT: measure gain from one trial 90°: first finished leg + second finished leg − original straight stock. Cut stock = finished dimensions added − (number of 90° bends × measured gain). Gain is NOT the arrow deduct.\n\n"+
            "PARALLEL OFFSETS: each next conduit shift = centre-to-centre spacing × tan(half the bend angle). Shift both bend marks equally on each adjacent pipe.\n\n"+
            "COMMON MARKS:\n"+
            "ARROW → stub-ups and many offset/outer saddle bends.\n"+
            "STAR ★ back-of-90 for back-to-back and some reverse methods.\n"+
            "CENTRE / SADDLE NOTCH → centre of a three-point saddle where provided.\n"+
            "DEGREE SCALE → check desired bend angle after springback.\n\n"+
            "JOB-SITE CHECKS: wear PPE, correct shoe size, no flattened/kinked conduit, adequate straight lead and stub minimum, verify pull-point/bend limits and manufacturer radius. Confirm the direction of the bend before stepping on the bender.\n"
        );sheet.setEditable(false);sheet.setWrapText(true);sheet.setPrefRowCount(20);
        page.getChildren().add(card("Bending quick-reference sheet",sheet));
        Button copy=new Button("Copy quick-reference sheet");copy.setOnAction(e->{ClipboardContent content=new ClipboardContent();content.putString(sheet.getText());Clipboard.getSystemClipboard().setContent(content);});
        Label source=notice("Source references: Klein Tools Conduit Bender Guide and Greenlee Site-Rite hand-bender instructions. Copy official reference links below to open the original diagrams and verify specific symbols.");
        Button klein=linkButton("Copy Klein bending guide URL",KLEIN),greenlee=linkButton("Copy Greenlee hand-bender URL",GREENLEE);
        page.getChildren().addAll(copy,source,new HBox(8,klein,greenlee));
        setContent(page);setFitToWidth(true);
        setStyle("-fx-background:transparent;-fx-background-color:transparent;");
    }
    static String guide(String type) {
        if(type==null)return "";
        return switch(type) {
            case "Two-bend offset" ->
                "USE: move conduit up/down around an obstruction and return parallel.\n"+
                "MARK: measure both marks from one reference end; mark spacing = rise × multiplier. Apply near/far shrink convention correctly.\n"+
                "BEND ORDER: 1) A first bend using ARROW; hook typically points away from mark B in Klein's documented method. "+
                "2) Flip bender/conduit for return bend; keep plane aligned and line ARROW on B. 3) Confirm parallel legs and clearance.\n"+
                "WATCH: 180° roll/flip is used for the return setup; do not accidentally twist the offset into an unintended rolling offset.";
            case "Rolling offset" ->
                "USE: shift conduit in TWO directions while keeping the final run parallel.\n"+
                "MARK: true offset = √(rise² + roll²); mark spacing = true offset / sin(angle). Determine bending plane = atan(roll/rise).\n"+
                "BEND ORDER: 1) Mark A and B. 2) Mark the desired plane orientation on the pipe. 3) Bend A with ARROW. "+
                "4) Flip for B return bend while staying in the rotated plane. 5) Check both lateral and vertical clearance.\n"+
                "WATCH: use the true offset for spacing, not only the vertical rise.";
            case "90° stub-up" ->
                "USE: turn a conduit up/down 90° from a wall or floor.\n"+
                "MARK: ARROW position = desired finished stub height − actual stamped deduct/take-up.\n"+
                "BEND ORDER: 1) Confirm shoe's marked deduct. 2) Measure and mark. 3) Hook faces short free end. "+
                "4) Line ARROW on mark, apply firm foot pressure and bend to 90°. 5) Check finished height.\n"+
                "WATCH: deduct values are head- and size-specific.";
            case "Back-to-back 90s" ->
                "USE: make a U shape between parallel surfaces.\n"+
                "MARK: first ARROW is found using stub deduct; second STAR/back-of-bend is measured from BACK of completed first 90°, not from the datum end.\n"+
                "BEND ORDER: 1) First 90 by ARROW. 2) Measure finished back-of-bend to second reference. "+
                "3) Hook faces free end opposite the original bend. 4) Align STAR and make second 90 in same plane.\n"+
                "WATCH: short U's may require an alternate manufacturer-approved arrow method; verify shoe clearance.";
            case "Three-point saddle" ->
                "USE: pass over a narrow pipe or round obstruction.\n"+
                "MARK: A and C are equal distances either side of centre B; centre position may require manufacturer saddle-shrink adjustment.\n"+
                "BEND ORDER: 1) Bend centre B with SADDLE / CENTRE mark. "+
                "2) Flip pipe/bender appropriately and bend A with ARROW to half centre angle. 3) Bend C to same return angle.\n"+
                "WATCH: outside bends must oppose centre and remain in same plane; saddle notch availability varies.";
            case "Four-point saddle" ->
                "USE: bridge a wide flat obstruction with a straight top section.\n"+
                "MARK: A–B and C–D are offset pairs; A–B and C–D mark spacing = height / sin(angle). Middle B–C spacing includes obstacle width and the appropriate bend-centre allowances.\n"+
                "BEND ORDER: A then B (first offset), C then D (return offset), changing direction between the pairs.\n"+
                "WATCH: include clearance and shoe radius; four marks may not fit near short obstacle edges.";
            case "Kicked 90°" ->
                "USE: 90° with an additional angular kick in its straight leg.\n"+
                "MARK: first 90 uses ARROW and actual shoe deduct; choose second kick mark on the leg only after measuring installation geometry.\n"+
                "BEND ORDER: 1) Form and check 90°. 2) Measure actual kick location. 3) Orient in required plane and form kick with manufacturer reference.\n"+
                "WATCH: computed kick diagonal is not the absolute second shoe mark.";
            case "Segmented 90°" ->
                "USE: approximate a broad smooth 90° using multiple small bends.\n"+
                "MARK: approximate equal arc stations; individual target angle = 90°/segment count.\n"+
                "BEND ORDER: mark all stations, form equal small bends one at a time, keep same plane, verify total 90° and radius.\n"+
                "WATCH: actual shoe centreline geometry and springback make a trial piece essential.";
            default -> "";
        };
    }
    private static String number(double v){return Math.abs(v-Math.rint(v))<1e-9?String.format(Locale.CANADA,"%.0f",v):String.format(Locale.CANADA,"%.1f",v);}
    private static void addCells(GridPane grid,int row,String...values){
        for(int i=0;i<values.length;i++){
            Label l=new Label(values[i]);l.setWrapText(true);l.setMinWidth(i==0?125:i==1?118:i==2?165:200);
            l.setPadding(new Insets(9));l.setStyle(row==0
                ?"-fx-background-color:#e0f2fe;-fx-border-color:#cbd5e1;-fx-font-weight:bold;-fx-text-fill:#0f4c81;"
                :"-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-text-fill:#334155;");
            grid.add(l,i,row);
        }
    }
    private static VBox card(String title,javafx.scene.Node...items){
        VBox box=new VBox(9);box.setPadding(new Insets(13));
        box.setStyle("-fx-background-color:white;-fx-border-color:#dbe5ef;-fx-border-radius:12;-fx-background-radius:12;");
        Label heading=new Label(title);heading.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        box.getChildren().add(heading);box.getChildren().addAll(items);return box;
    }
    private static Label notice(String msg){Label l=new Label(msg);l.setWrapText(true);l.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");return l;}
    private static Button linkButton(String text,String link){
        Button b=new Button(text);b.setOnAction(e->{
            ClipboardContent content=new ClipboardContent();content.putString(link);Clipboard.getSystemClipboard().setContent(content);b.setText("Link copied");
        });return b;
    }
}

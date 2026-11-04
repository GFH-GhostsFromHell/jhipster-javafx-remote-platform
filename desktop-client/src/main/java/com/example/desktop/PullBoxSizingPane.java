package com.example.desktop;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;

/** Native, local-first CEC 2024 Rule 12-3036 pull-box dimension worksheet. */
public final class PullBoxSizingPane extends BorderPane {
    private static final String[] MODES = {"Straight pull", "Angle pull", "U pull", "Mixed straight + angle/U", "Back-wall entry to cover", "Junction / splice review"};
    private static final String[] WALLS = {"Left", "Right", "Top", "Bottom", "Back"};
    private static final Integer[] TRADES = {16, 21, 27, 35, 41, 53, 63, 78, 91, 103, 129, 155};
    private record Entry(String wall, int trade) {
        @Override public String toString() { return wall + " wall  ·  " + trade + " mm trade size"; }
    }
    private final ObservableList<Entry> entries = FXCollections.observableArrayList();
    private final ChoiceBox<String> mode = new ChoiceBox<>(FXCollections.observableArrayList(MODES));
    private final ChoiceBox<String> wall = new ChoiceBox<>(FXCollections.observableArrayList(WALLS));
    private final ChoiceBox<Integer> trade = new ChoiceBox<>(FXCollections.observableArrayList(TRADES));
    private final ChoiceBox<String> display = new ChoiceBox<>(FXCollections.observableArrayList("Millimetres", "Inches"));
    private final TextField conductorOd = new TextField("20");
    private final ListView<Entry> list = new ListView<>(entries);
    private final TextArea result = new TextArea();

    public PullBoxSizingPane() {
        mode.setValue(MODES[0]); wall.setValue("Left"); trade.setValue(53); display.setValue("Millimetres");
        VBox page = new VBox(12); page.setPadding(new Insets(14));
        Label title = new Label("Pull-box & junction-box dimensions");
        title.setStyle("-fx-font-size:21px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label intro = note("CEC 2024 Rule 12-3036 · For raceways/cables containing insulated No. 4 AWG or larger conductors. Enter nominal Canadian trade sizes, not the measured outside diameter of the conduit.");
        HBox options = new HBox(10, label("Pull layout"), mode, label("Display"), display);
        options.setAlignment(Pos.CENTER_LEFT); mode.setPrefWidth(265);
        HBox add = new HBox(9, label("Entry wall"), wall, label("Conduit trade size"), trade);
        add.setAlignment(Pos.CENTER_LEFT);
        Button addButton = new Button("Add entry"); Button remove = new Button("Remove selected");
        addButton.setOnAction(e -> entries.add(new Entry(wall.getValue(), trade.getValue())));
        remove.setOnAction(e -> { Entry selected = list.getSelectionModel().getSelectedItem(); if (selected != null) entries.remove(selected); });
        add.getChildren().addAll(addButton, remove);
        list.setPrefHeight(132);
        HBox odRow = new HBox(10, label("Largest insulated conductor outside diameter (mm)"), conductorOd);
        odRow.setAlignment(Pos.CENTER_LEFT); conductorOd.setMaxWidth(120);
        Button calculate = new Button("Calculate minimum dimensions");
        calculate.setStyle("-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:10 15;");
        Button copy = new Button("Copy results");
        copy.setOnAction(e -> { javafx.scene.input.ClipboardContent c = new javafx.scene.input.ClipboardContent(); c.putString(result.getText()); javafx.scene.input.Clipboard.getSystemClipboard().setContent(c); });
        calculate.setOnAction(e -> calculate());
        display.setOnAction(e -> calculate());
        mode.setOnAction(e -> preset());
        HBox buttons = new HBox(10, calculate, copy);
        result.setEditable(false); result.setWrapText(true); result.setPrefRowCount(18);
        page.getChildren().addAll(title, intro, options, note("Select a layout, add every raceway entry by wall, then calculate. Duplicate entries represent separate conduits on the same wall."), add, list, odRow, buttons, result,
            note("Important: Straight-pull length and angle/U wall clearances are independent tests. For a pull from the back wall toward a removable front cover, enter the actual insulated conductor OD from the cable specification. Check physical space for locknuts, bushings, splices, wire bending radius and cable-pulling access. Always verify with the applicable OESC/CEC edition and AHJ."));
        ScrollPane scroll = new ScrollPane(page); scroll.setFitToWidth(true); scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        setCenter(scroll); preset();
    }

    private void preset() {
        entries.clear();
        switch (mode.getValue()) {
            case "Straight pull" -> { entries.add(new Entry("Left",53)); entries.add(new Entry("Right",53)); }
            case "Angle pull" -> { entries.add(new Entry("Left",53)); entries.add(new Entry("Bottom",53)); }
            case "U pull" -> { entries.add(new Entry("Bottom",53)); entries.add(new Entry("Bottom",53)); }
            case "Mixed straight + angle/U" -> { entries.add(new Entry("Left",53)); entries.add(new Entry("Right",53)); entries.add(new Entry("Bottom",35)); }
            case "Back-wall entry to cover" -> entries.add(new Entry("Back",53));
            default -> { entries.add(new Entry("Left",35)); entries.add(new Entry("Top",35)); }
        }
        calculate();
    }

    private void calculate() {
        try {
            Map<String,List<Integer>> grouped = new LinkedHashMap<>();
            for (String w : WALLS) grouped.put(w, new ArrayList<>());
            for (Entry e : entries) grouped.get(e.wall()).add(e.trade());
            if (entries.isEmpty()) throw new IllegalArgumentException("Add at least one raceway entry.");
            boolean straight = mode.getValue().equals(MODES[0]) || mode.getValue().equals(MODES[3]);
            boolean turning = mode.getValue().equals(MODES[1]) || mode.getValue().equals(MODES[2]) ||
                    mode.getValue().equals(MODES[3]) || mode.getValue().equals(MODES[5]);
            boolean rear = mode.getValue().equals(MODES[4]) || !grouped.get("Back").isEmpty();
            double width = Double.NaN, height = Double.NaN, depth = Double.NaN;
            StringBuilder detail = new StringBuilder();
            if (straight) {
                if (!grouped.get("Left").isEmpty() && !grouped.get("Right").isEmpty()) {
                    int largest = maxBoth(grouped.get("Left"), grouped.get("Right"));
                    width = Math.max(nz(width), 8d * largest);
                    detail.append("Left ↔ right straight pull: 8 × ").append(largest).append(" = ").append(format(8d*largest)).append("\n");
                }
                if (!grouped.get("Top").isEmpty() && !grouped.get("Bottom").isEmpty()) {
                    int largest = maxBoth(grouped.get("Top"), grouped.get("Bottom"));
                    height = Math.max(nz(height), 8d * largest);
                    detail.append("Top ↔ bottom straight pull: 8 × ").append(largest).append(" = ").append(format(8d*largest)).append("\n");
                }
                if (Double.isNaN(width) && Double.isNaN(height)) {
                    detail.append("Straight pull needs matching entries on opposing walls. Add both sides.\n");
                }
            }
            if (turning) {
                for (String w : List.of("Left","Right","Top","Bottom")) {
                    List<Integer> sizes = grouped.get(w);
                    if (sizes.isEmpty()) continue;
                    int largest = Collections.max(sizes);
                    int others = sizes.stream().mapToInt(Integer::intValue).sum() - largest;
                    double minimum = 6d*largest + others;
                    if (w.equals("Left") || w.equals("Right")) width = Math.max(nz(width),minimum);
                    else height = Math.max(nz(height),minimum);
                    detail.append(w).append(" wall: 6 × ").append(largest).append(" + ").append(others)
                          .append(" (other entries on this wall) = ").append(format(minimum)).append("\n");
                }
                int greatest = 0;
                for (String w : List.of("Left","Right","Top","Bottom")) {
                    List<Integer> sizes = grouped.get(w);
                    if (sizes.size() >= 2) greatest = Math.max(greatest,Collections.max(sizes));
                }
                if (greatest > 0) detail.append("Check pairs carrying the SAME insulated conductor: nearest-edge separation is at least 6 × the larger entry trade size (up to ")
                    .append(format(6d*greatest)).append(" for these same-wall entries).\n");
                else detail.append("Check nearest-edge separation of each entry/exit pair carrying the same conductor: 6 × larger trade size.\n");
            }
            if (rear) {
                List<Integer> back = grouped.get("Back");
                if (back.isEmpty()) throw new IllegalArgumentException("Add a Back-wall entry to calculate wall-to-cover depth.");
                double od = Double.parseDouble(conductorOd.getText().trim());
                if (!Double.isFinite(od) || od <= 0) throw new IllegalArgumentException("Enter the actual insulated conductor OD in mm.");
                int largest = Collections.max(back);
                depth = largest + 6d*od;
                detail.append("Back wall → removable cover: ").append(largest).append(" + 6 × ").append(od)
                      .append(" = ").append(format(depth)).append("\n");
            }
            StringBuilder out = new StringBuilder();
            out.append("CEC 2024 · Rule 12-3036(2)\n")
               .append("Minimum inside dimension, left ↔ right: ").append(format(width)).append("\n")
               .append("Minimum inside dimension, top ↔ bottom: ").append(format(height)).append("\n")
               .append("Minimum inside depth, back → removable cover: ").append(format(depth)).append("\n\n")
               .append("WORKED SIZING\n").append(detail).append("\n");
            if (mode.getValue().equals(MODES[5])) out.append("Splice/junction review: Rule 12-3036 dimensions do not alone establish adequate usable space, bending room or connector clearances. Verify Rule 12-3034 and applicable equipment requirements.\n\n");
            out.append("A dimension shown as 'not set' is NOT zero. This rule does not determine it from the selected geometry; physical fit, entry separation, manufacturer specifications and installation conditions can govern.\n")
               .append("Trade sizes in mm are CEC nominal values. Use actual insulation OD only in the back-wall-to-cover calculation.");
            result.setText(out.toString());
        } catch(Exception ex) { result.setText("Check inputs: " + ex.getMessage()); }
    }

    private static int maxBoth(List<Integer> a,List<Integer> b) { return Math.max(Collections.max(a),Collections.max(b)); }
    private static double nz(double x) { return Double.isNaN(x) ? 0 : x; }
    private String format(double mm) { if (Double.isNaN(mm)) return "not set"; return display.getValue().equals("Inches") ?
        String.format(Locale.CANADA, "%.2f in (%.0f mm)", mm/25.4,mm) :
        String.format(Locale.CANADA, "%.0f mm (%.2f in)",mm,mm/25.4); }
    private static Label label(String s) { Label l=new Label(s);l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;");return l; }
    private static Label note(String s) { Label l=new Label(s);l.setWrapText(true);l.setStyle("-fx-text-fill:#475569;");return l; }
}

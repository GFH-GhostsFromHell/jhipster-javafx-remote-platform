package com.example.desktop;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.util.converter.DoubleStringConverter;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Functional preview of a contractor-grade electrical estimating workspace.
 * It is intentionally local/in-memory while the enterprise module architecture is evaluated.
 */
public final class EstimatingWorkspaceDraftPane extends BorderPane {
    private static final String CARD = "-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#dbe5ef;-fx-border-radius:14;";
    private static final String INPUT = "-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:7 9;";
    private static final String PRIMARY = "-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-padding:8 12;";
    private static final String GHOST = "-fx-background-color:#eef6fa;-fx-text-fill:#0f4c81;-fx-border-color:#c7d9e8;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 11;-fx-font-weight:bold;";
    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(Locale.CANADA);

    private final ObservableList<EstimateLine> lines = FXCollections.observableArrayList();
    private final ObservableList<QuoteLine> quotes = FXCollections.observableArrayList();
    private final ObservableList<IndirectLine> indirects = FXCollections.observableArrayList();

    private final TextField projectName = field("Project name");
    private final TextField clientName = field("Client / GC");
    private final TextField estimatorName = field("Estimator");
    private final TextField bidNumber = field("Bid / estimate number");
    private final TextField location = field("Project location");
    private final TextField dueDate = field("Bid due date / time");

    private final TextField labourRate = numberField("78.00");
    private final TextField contingencyPct = numberField("3.00");
    private final TextField overheadPct = numberField("8.00");
    private final TextField profitPct = numberField("10.00");
    private final TextField taxPct = numberField("13.00");

    private final Label materialTotal = valueLabel();
    private final Label labourHoursTotal = valueLabel();
    private final Label labourTotal = valueLabel();
    private final Label quoteTotal = valueLabel();
    private final Label indirectTotal = valueLabel();
    private final Label preTaxTotal = valueLabel();
    private final Label taxTotal = valueLabel();
    private final Label finalBidTotal = valueLabel();
    private final Label reviewProgress = new Label();

    private final TextArea inclusions = area("Describe included scope, systems, drawing/specification basis and major assumptions.");
    private final TextArea exclusions = area("List exclusions, by-others items, owner-supplied equipment and scope gaps.");
    private final TextArea clarifications = area("Record RFIs, addenda, tender clarifications and unresolved assumptions.");

    public EstimatingWorkspaceDraftPane() {
        setStyle("-fx-background-color:#eef3f8;");
        seedElectricalStructure();
        seedIndirects();

        VBox shell = new VBox(12);
        shell.setPadding(new Insets(2));
        shell.getChildren().addAll(header(), summaryStrip(), workflowTabs());
        VBox.setVgrow(shell.getChildren().get(2), Priority.ALWAYS);
        setCenter(shell);
        recalc();
    }

    private Node header() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(16));
        box.setStyle(CARD);

        Label badge = new Label("ESTIMATING MODULE PREVIEW");
        badge.setStyle("-fx-background-color:#cffafe;-fx-text-fill:#155e75;-fx-background-radius:999;-fx-padding:4 8;-fx-font-size:10px;-fx-font-weight:bold;");
        Label title = new Label("Advanced Electrical Estimating");
        title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label note = new Label("Working estimate flow: scope the tender, build the takeoff, apply labour/productivity, level supplier/subcontractor quotes, add indirects, then close and review the bid.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");

        GridPane grid = new GridPane();
        grid.setHgap(9); grid.setVgap(8);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setPercentWidth(33.33);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setPercentWidth(33.33);
        ColumnConstraints c3 = new ColumnConstraints(); c3.setPercentWidth(33.33);
        grid.getColumnConstraints().addAll(c1,c2,c3);
        grid.add(labeled("Project", projectName),0,0);
        grid.add(labeled("Client / GC", clientName),1,0);
        grid.add(labeled("Estimator", estimatorName),2,0);
        grid.add(labeled("Bid / estimate #", bidNumber),0,1);
        grid.add(labeled("Location", location),1,1);
        grid.add(labeled("Bid due", dueDate),2,1);

        box.getChildren().addAll(badge,title,note,grid);
        return box;
    }

    private Node summaryStrip() {
        HBox row = new HBox(10,
            stat("Material", materialTotal),
            stat("Adj. labour hours", labourHoursTotal),
            stat("Labour", labourTotal),
            stat("Selected quotes", quoteTotal),
            stat("Indirects", indirectTotal),
            stat("Final bid", finalBidTotal)
        );
        for (Node node : row.getChildren()) HBox.setHgrow(node, Priority.ALWAYS);
        return row;
    }

    private Node workflowTabs() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
            new Tab("1 · Scope", scopePane()),
            new Tab("2 · Takeoff & Pricing", takeoffPane()),
            new Tab("3 · Quotes", quotesPane()),
            new Tab("4 · Indirects & Markups", indirectsPane()),
            new Tab("5 · Bid Review", reviewPane())
        );
        VBox.setVgrow(tabs, Priority.ALWAYS);
        return tabs;
    }

    private Node scopePane() {
        VBox page = new VBox(12);
        page.setPadding(new Insets(14));
        Label title = sectionTitle("Scope / tender review");
        Label note = note("Capture the bid basis before pricing. This is where drawing/specification review, addenda, site conditions, inclusions, exclusions and clarifications live.");
        HBox cards = new HBox(12,
            textCard("Included scope", inclusions),
            textCard("Exclusions / by others", exclusions),
            textCard("Clarifications / RFIs / addenda", clarifications)
        );
        for (Node n : cards.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        VBox checklist = checklistCard("Tender readiness",
            "Bid documents received and revision set confirmed",
            "Specifications and electrical drawings reviewed",
            "Addenda logged and incorporated",
            "Site visit / existing conditions considered",
            "Major scope gaps and by-others items identified",
            "Bid form, bonding, insurance and submission requirements identified");
        page.getChildren().addAll(title,note,cards,checklist);
        ScrollPane scroll = new ScrollPane(page); scroll.setFitToWidth(true); return scroll;
    }

    private Node takeoffPane() {
        VBox page = new VBox(10); page.setPadding(new Insets(14));
        Label title = sectionTitle("Electrical takeoff, material and labour");
        Label note = note("Use one line per measured item, assembly or equipment package. Material and labour extend automatically; productivity factor > 1.00 increases labour hours.");

        TableView<EstimateLine> table = new TableView<>(lines);
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(390);

        TableColumn<EstimateLine,String> system = textCol("System / division", 150, l -> l.system);
        TableColumn<EstimateLine,String> desc = textCol("Description", 240, l -> l.description);
        TableColumn<EstimateLine,Number> qty = doubleCol("Qty", 72, l -> l.quantity, l -> recalc());
        TableColumn<EstimateLine,String> unit = textCol("Unit", 64, l -> l.unit);
        TableColumn<EstimateLine,Number> matUnit = doubleCol("Mat. $/unit", 92, l -> l.materialUnit, l -> recalc());
        TableColumn<EstimateLine,Number> labUnit = doubleCol("Lab h/unit", 86, l -> l.labourUnitHours, l -> recalc());
        TableColumn<EstimateLine,Number> factor = doubleCol("Prod. factor", 86, l -> l.productivityFactor, l -> recalc());
        TableColumn<EstimateLine,String> matExt = readOnlyCol("Material ext.", 100, l -> MONEY.format(l.materialExtended()));
        TableColumn<EstimateLine,String> hrsExt = readOnlyCol("Adj. hours", 86, l -> String.format("%.2f", l.adjustedHours()));
        table.getColumns().addAll(system,desc,qty,unit,matUnit,labUnit,factor,matExt,hrsExt);

        Button add = new Button("+ Add line"); add.setStyle(PRIMARY);
        add.setOnAction(e -> { lines.add(new EstimateLine("Branch wiring","New takeoff item",1,"ea",0,0,1)); recalc(); });
        Button duplicate = new Button("Duplicate selected"); duplicate.setStyle(GHOST);
        duplicate.setOnAction(e -> {
            EstimateLine x = table.getSelectionModel().getSelectedItem();
            if (x != null) { lines.add(x.copy()); recalc(); }
        });
        Button remove = new Button("Remove selected"); remove.setStyle(GHOST);
        remove.setOnAction(e -> { EstimateLine x=table.getSelectionModel().getSelectedItem(); if(x!=null){lines.remove(x);recalc();} });
        Button seed = new Button("Reset electrical structure"); seed.setStyle(GHOST);
        seed.setOnAction(e -> { lines.clear(); seedElectricalStructure(); recalc(); });
        HBox actions = new HBox(8, add, duplicate, remove, seed);

        HBox settings = new HBox(12,
            compactField("Loaded labour $/h", labourRate),
            infoBox("Labour model", "Adjusted hours = quantity × labour hours/unit × productivity factor."),
            infoBox("Pricing model", "Material unit prices are entered before taxes; supplier/subcontractor quotes are handled separately."));
        HBox.setHgrow(settings.getChildren().get(1), Priority.ALWAYS);
        HBox.setHgrow(settings.getChildren().get(2), Priority.ALWAYS);
        labourRate.textProperty().addListener((o,a,b)->recalc());

        page.getChildren().addAll(title,note,actions,table,settings);
        return page;
    }

    private Node quotesPane() {
        VBox page = new VBox(10); page.setPadding(new Insets(14));
        Label title = sectionTitle("Supplier / subcontractor quote leveling");
        Label note = note("Capture competing quotes, identify scope coverage, and explicitly choose which quotation is carried into the bid.");

        TableView<QuoteLine> table = new TableView<>(quotes); table.setEditable(true); table.setPrefHeight(360);
        TableColumn<QuoteLine,Boolean> include = new TableColumn<>("Carry");
        include.setCellValueFactory(c -> c.getValue().include);
        include.setCellFactory(CheckBoxTableCell.forTableColumn(include)); include.setEditable(true); include.setPrefWidth(62);
        TableColumn<QuoteLine,String> vendor = quoteTextCol("Vendor / subcontractor", 180, q -> q.vendor);
        TableColumn<QuoteLine,String> scope = quoteTextCol("Quoted scope", 260, q -> q.scope);
        TableColumn<QuoteLine,Number> amount = quoteDoubleCol("Amount", 110, q -> q.amount);
        TableColumn<QuoteLine,String> status = quoteTextCol("Status / qualifications", 220, q -> q.status);
        table.getColumns().addAll(include,vendor,scope,amount,status);
        for (QuoteLine q : quotes) q.include.addListener((o,a,b)->recalc());

        Button add = new Button("+ Add quote"); add.setStyle(PRIMARY);
        add.setOnAction(e -> { QuoteLine q=new QuoteLine(false,"New vendor","Quoted scope",0,"Review qualifications"); q.include.addListener((o,a,b)->recalc()); quotes.add(q); recalc(); });
        Button remove = new Button("Remove selected"); remove.setStyle(GHOST);
        remove.setOnAction(e -> { QuoteLine q=table.getSelectionModel().getSelectedItem(); if(q!=null){quotes.remove(q);recalc();} });
        page.getChildren().addAll(title,note,new HBox(8,add,remove),table,
            checklistCard("Quote leveling checks",
                "Scope matches drawings/specifications and estimate breakdown",
                "Exclusions and qualifications reviewed",
                "Freight, taxes, lead times and escalation understood",
                "Alternates and substitutions identified",
                "Duplicate scope between self-perform and quoted work removed"));
        return page;
    }

    private Node indirectsPane() {
        VBox page = new VBox(10); page.setPadding(new Insets(14));
        Label title = sectionTitle("Indirect costs, risk and commercial markups");
        Label note = note("Separate direct electrical work from project-specific indirect costs and company-level markups so the bid can be reviewed transparently.");

        TableView<IndirectLine> table = new TableView<>(indirects); table.setEditable(true); table.setPrefHeight(300);
        table.getColumns().addAll(
            indirectTextCol("Type", 150, x -> x.type),
            indirectTextCol("Description", 300, x -> x.description),
            indirectDoubleCol("Amount", 120, x -> x.amount)
        );
        Button add = new Button("+ Add indirect"); add.setStyle(PRIMARY);
        add.setOnAction(e -> { indirects.add(new IndirectLine("General conditions","New indirect cost",0)); recalc(); });
        Button remove = new Button("Remove selected"); remove.setStyle(GHOST);
        remove.setOnAction(e -> { IndirectLine x=table.getSelectionModel().getSelectedItem(); if(x!=null){indirects.remove(x);recalc();} });

        GridPane markups = new GridPane(); markups.setHgap(12); markups.setVgap(8);
        markups.add(compactField("Contingency %", contingencyPct),0,0);
        markups.add(compactField("Overhead %", overheadPct),1,0);
        markups.add(compactField("Profit %", profitPct),2,0);
        markups.add(compactField("Tax %", taxPct),3,0);
        contingencyPct.textProperty().addListener((o,a,b)->recalc());
        overheadPct.textProperty().addListener((o,a,b)->recalc());
        profitPct.textProperty().addListener((o,a,b)->recalc());
        taxPct.textProperty().addListener((o,a,b)->recalc());

        HBox totals = new HBox(10,
            totalCard("Pre-tax bid", preTaxTotal),
            totalCard("Tax", taxTotal),
            totalCard("Final bid", finalBidTotal));
        for (Node n : totals.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        page.getChildren().addAll(title,note,new HBox(8,add,remove),table,markups,totals);
        return page;
    }

    private Node reviewPane() {
        VBox page = new VBox(12); page.setPadding(new Insets(14));
        Label title = sectionTitle("Bid closing / review");
        Label note = note("Final review should confirm quantities, quote coverage, labour assumptions, commercial requirements, exclusions and submission documents before the bid leaves the company.");

        VBox checks = new VBox(7); checks.setPadding(new Insets(14)); checks.setStyle(CARD);
        String[] items = {
            "Drawing/spec revision and all addenda confirmed",
            "Quantity takeoff double-checked",
            "Material pricing and supplier quotes reviewed",
            "Labour rate, productivity and staffing assumptions reviewed",
            "Subcontractor scope leveled and selected quotes confirmed",
            "General conditions / indirect costs complete",
            "Bonding, insurance, permits, freight and escalation addressed",
            "Inclusions, exclusions and clarifications reviewed",
            "Overhead, profit and taxes verified",
            "Bid form / proposal / alternates complete and authorized"
        };
        for (String item : items) {
            CheckBox cb = new CheckBox(item);
            cb.setWrapText(true); cb.setStyle("-fx-text-fill:#334155;");
            cb.selectedProperty().addListener((o,a,b)->updateReviewProgress(checks));
            checks.getChildren().add(cb);
        }
        reviewProgress.setStyle("-fx-font-weight:bold;-fx-text-fill:#0e7490;");
        updateReviewProgress(checks);

        VBox commercial = new VBox(7,
            pair("Material", materialTotal), pair("Adjusted labour hours", labourHoursTotal), pair("Labour", labourTotal),
            pair("Selected quotes", quoteTotal), pair("Indirects", indirectTotal), new Separator(), pair("Pre-tax bid", preTaxTotal),
            pair("Tax", taxTotal), pair("FINAL BID", finalBidTotal));
        commercial.setPadding(new Insets(16)); commercial.setStyle(CARD);

        Button copy = new Button("Copy bid summary"); copy.setStyle(PRIMARY);
        copy.setOnAction(e -> copySummary());
        HBox body = new HBox(12, new VBox(8, reviewProgress, checks, copy), commercial);
        HBox.setHgrow(body.getChildren().get(0), Priority.ALWAYS);
        commercial.setPrefWidth(330);
        page.getChildren().addAll(title,note,body);
        return page;
    }

    private void copySummary() {
        String text = "ELECTRICAL ESTIMATE SUMMARY\n" +
            "Project: " + projectName.getText() + "\n" +
            "Client / GC: " + clientName.getText() + "\n" +
            "Estimate #: " + bidNumber.getText() + "\n" +
            "Estimator: " + estimatorName.getText() + "\n\n" +
            "Material: " + materialTotal.getText() + "\n" +
            "Adjusted labour hours: " + labourHoursTotal.getText() + "\n" +
            "Labour: " + labourTotal.getText() + "\n" +
            "Selected quotes: " + quoteTotal.getText() + "\n" +
            "Indirects: " + indirectTotal.getText() + "\n" +
            "Pre-tax bid: " + preTaxTotal.getText() + "\n" +
            "Tax: " + taxTotal.getText() + "\n" +
            "FINAL BID: " + finalBidTotal.getText() + "\n\n" +
            "Inclusions:\n" + inclusions.getText() + "\n\nExclusions:\n" + exclusions.getText() + "\n\nClarifications:\n" + clarifications.getText();
        ClipboardContent content = new ClipboardContent(); content.putString(text); Clipboard.getSystemClipboard().setContent(content);
    }

    private void recalc() {
        double rate = parse(labourRate.getText());
        double mat = lines.stream().mapToDouble(EstimateLine::materialExtended).sum();
        double hours = lines.stream().mapToDouble(EstimateLine::adjustedHours).sum();
        double labour = hours * rate;
        double q = quotes.stream().filter(x -> x.include.get()).mapToDouble(x -> x.amount.get()).sum();
        double ind = indirects.stream().mapToDouble(x -> x.amount.get()).sum();
        double base = mat + labour + q + ind;
        double contingency = base * parse(contingencyPct.getText()) / 100.0;
        double overhead = (base + contingency) * parse(overheadPct.getText()) / 100.0;
        double profit = (base + contingency + overhead) * parse(profitPct.getText()) / 100.0;
        double preTax = base + contingency + overhead + profit;
        double tax = preTax * parse(taxPct.getText()) / 100.0;
        double total = preTax + tax;

        materialTotal.setText(MONEY.format(mat));
        labourHoursTotal.setText(String.format("%.1f h", hours));
        labourTotal.setText(MONEY.format(labour));
        quoteTotal.setText(MONEY.format(q));
        indirectTotal.setText(MONEY.format(ind));
        preTaxTotal.setText(MONEY.format(preTax));
        taxTotal.setText(MONEY.format(tax));
        finalBidTotal.setText(MONEY.format(total));
    }

    private void seedElectricalStructure() {
        lines.addAll(
            new EstimateLine("01 Service / distribution","Service, switchgear, main distribution",1,"lot",0,0,1),
            new EstimateLine("02 Feeders","Feeders, conduit / cable, supports and terminations",1,"lot",0,0,1),
            new EstimateLine("03 Branch wiring","Branch circuits, raceway, conductors and boxes",1,"lot",0,0,1),
            new EstimateLine("04 Lighting / controls","Fixtures, controls, emergency lighting and lighting branch wiring",1,"lot",0,0,1),
            new EstimateLine("05 Devices","Receptacles, switches, disconnects and specialty outlets",1,"lot",0,0,1),
            new EstimateLine("06 Mechanical power","Motors, VFDs, HVAC and process equipment connections",1,"lot",0,0,1),
            new EstimateLine("07 Fire alarm / low voltage","Fire alarm, communications and specialty systems",1,"lot",0,0,1),
            new EstimateLine("08 Grounding / bonding","Grounding, bonding, electrodes and equipotential work",1,"lot",0,0,1),
            new EstimateLine("09 Supports / misc.","Supports, sleeves, firestopping, labels and miscellaneous material",1,"lot",0,0,1)
        );
    }

    private void seedIndirects() {
        indirects.addAll(
            new IndirectLine("General conditions","Supervision / project management",0),
            new IndirectLine("General conditions","Mobilization, temporary facilities and small tools",0),
            new IndirectLine("Commercial","Permits, bonding and insurance",0),
            new IndirectLine("Commercial","Freight, escalation or special handling",0)
        );
    }

    private static VBox checklistCard(String title, String... items) {
        VBox box = new VBox(7); box.setPadding(new Insets(14)); box.setStyle(CARD);
        Label h = new Label(title); h.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#0f172a;"); box.getChildren().add(h);
        for (String item : items) { CheckBox cb = new CheckBox(item); cb.setWrapText(true); cb.setStyle("-fx-text-fill:#475569;"); box.getChildren().add(cb); }
        return box;
    }

    private static VBox textCard(String title, TextArea area) {
        VBox box = new VBox(7); box.setPadding(new Insets(14)); box.setStyle(CARD);
        Label h = new Label(title); h.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        VBox.setVgrow(area, Priority.ALWAYS); box.getChildren().addAll(h,area); return box;
    }

    private static VBox stat(String title, Label value) {
        VBox box = new VBox(4); box.setPadding(new Insets(12)); box.setMaxWidth(Double.MAX_VALUE); box.setStyle(CARD);
        Label h = new Label(title.toUpperCase()); h.setStyle("-fx-text-fill:#64748b;-fx-font-size:9px;-fx-font-weight:bold;");
        box.getChildren().addAll(h,value); return box;
    }

    private static VBox totalCard(String title, Label value) { return stat(title,value); }

    private static HBox pair(String title, Label value) {
        Label k = new Label(title); k.setStyle("-fx-text-fill:#475569;"); Region push = new Region(); HBox.setHgrow(push,Priority.ALWAYS);
        return new HBox(8,k,push,value);
    }

    private static VBox compactField(String title, TextField field) { return labeled(title,field); }

    private static VBox infoBox(String title, String text) {
        VBox box = new VBox(3); box.setPadding(new Insets(10)); box.setStyle("-fx-background-color:#f8fafc;-fx-background-radius:9;-fx-border-color:#e2e8f0;-fx-border-radius:9;");
        Label h=new Label(title); h.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;"); Label n=note(text); box.getChildren().addAll(h,n); return box;
    }

    private static VBox labeled(String title, Node field) {
        Label l=new Label(title); l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;-fx-font-size:11px;");
        VBox box=new VBox(4,l,field); VBox.setVgrow(field,Priority.NEVER); return box;
    }

    private static Label sectionTitle(String text) { Label l=new Label(text); l.setStyle("-fx-font-size:19px;-fx-font-weight:bold;-fx-text-fill:#0f172a;"); return l; }
    private static Label note(String text) { Label l=new Label(text); l.setWrapText(true); l.setStyle("-fx-text-fill:#64748b;-fx-font-size:11px;"); return l; }
    private static Label valueLabel() { Label l=new Label("—"); l.setStyle("-fx-text-fill:#0e7490;-fx-font-size:17px;-fx-font-weight:bold;"); return l; }
    private static TextField field(String prompt) { TextField f=new TextField(); f.setPromptText(prompt); f.setStyle(INPUT); return f; }
    private static TextField numberField(String value) { TextField f=field(""); f.setText(value); f.setPrefWidth(105); return f; }
    private static TextArea area(String prompt) { TextArea a=new TextArea(); a.setPromptText(prompt); a.setWrapText(true); a.setPrefRowCount(7); a.setStyle(INPUT); return a; }
    private static double parse(String text) { try { return Math.max(0,Double.parseDouble(text == null || text.isBlank() ? "0" : text.replace(",","").trim())); } catch(Exception ex){ return 0; } }

    private void updateReviewProgress(VBox container) {
        long total=container.getChildren().stream().filter(n->n instanceof CheckBox).count();
        long done=container.getChildren().stream().filter(n->n instanceof CheckBox cb && cb.isSelected()).count();
        reviewProgress.setText("Bid review: " + done + " / " + total + " checks complete");
    }

    private static TableColumn<EstimateLine,String> textCol(String title,double width,java.util.function.Function<EstimateLine,StringProperty> prop) {
        TableColumn<EstimateLine,String> c=new TableColumn<>(title); c.setCellValueFactory(x->prop.apply(x.getValue())); c.setCellFactory(TextFieldTableCell.forTableColumn()); c.setPrefWidth(width); return c;
    }
    private static TableColumn<EstimateLine,Number> doubleCol(String title,double width,java.util.function.Function<EstimateLine,DoubleProperty> prop,java.util.function.Consumer<EstimateLine> changed) {
        TableColumn<EstimateLine,Number> c=new TableColumn<>(title); c.setCellValueFactory(x->prop.apply(x.getValue())); c.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter())); c.setPrefWidth(width);
        c.setOnEditCommit(e->{prop.apply(e.getRowValue()).set(e.getNewValue().doubleValue());changed.accept(e.getRowValue());}); return c;
    }
    private static TableColumn<EstimateLine,String> readOnlyCol(String title,double width,java.util.function.Function<EstimateLine,String> text) {
        TableColumn<EstimateLine,String> c=new TableColumn<>(title); c.setCellValueFactory(x->new SimpleStringProperty(text.apply(x.getValue()))); c.setPrefWidth(width); return c;
    }
    private static TableColumn<QuoteLine,String> quoteTextCol(String title,double width,java.util.function.Function<QuoteLine,StringProperty> prop) {
        TableColumn<QuoteLine,String> c=new TableColumn<>(title); c.setCellValueFactory(x->prop.apply(x.getValue())); c.setCellFactory(TextFieldTableCell.forTableColumn()); c.setPrefWidth(width); return c;
    }
    private TableColumn<QuoteLine,Number> quoteDoubleCol(String title,double width,java.util.function.Function<QuoteLine,DoubleProperty> prop) {
        TableColumn<QuoteLine,Number> c=new TableColumn<>(title); c.setCellValueFactory(x->prop.apply(x.getValue())); c.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter())); c.setPrefWidth(width); c.setOnEditCommit(e->{prop.apply(e.getRowValue()).set(e.getNewValue().doubleValue());recalc();}); return c;
    }
    private static TableColumn<IndirectLine,String> indirectTextCol(String title,double width,java.util.function.Function<IndirectLine,StringProperty> prop) {
        TableColumn<IndirectLine,String> c=new TableColumn<>(title); c.setCellValueFactory(x->prop.apply(x.getValue())); c.setCellFactory(TextFieldTableCell.forTableColumn()); c.setPrefWidth(width); return c;
    }
    private TableColumn<IndirectLine,Number> indirectDoubleCol(String title,double width,java.util.function.Function<IndirectLine,DoubleProperty> prop) {
        TableColumn<IndirectLine,Number> c=new TableColumn<>(title); c.setCellValueFactory(x->prop.apply(x.getValue())); c.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter())); c.setPrefWidth(width); c.setOnEditCommit(e->{prop.apply(e.getRowValue()).set(e.getNewValue().doubleValue());recalc();}); return c;
    }

    private static final class EstimateLine {
        final StringProperty system=new SimpleStringProperty(); final StringProperty description=new SimpleStringProperty(); final DoubleProperty quantity=new SimpleDoubleProperty(); final StringProperty unit=new SimpleStringProperty(); final DoubleProperty materialUnit=new SimpleDoubleProperty(); final DoubleProperty labourUnitHours=new SimpleDoubleProperty(); final DoubleProperty productivityFactor=new SimpleDoubleProperty();
        EstimateLine(String s,String d,double q,String u,double m,double h,double f){system.set(s);description.set(d);quantity.set(q);unit.set(u);materialUnit.set(m);labourUnitHours.set(h);productivityFactor.set(f);}
        double materialExtended(){return Math.max(0,quantity.get())*Math.max(0,materialUnit.get());}
        double adjustedHours(){return Math.max(0,quantity.get())*Math.max(0,labourUnitHours.get())*Math.max(0,productivityFactor.get());}
        EstimateLine copy(){return new EstimateLine(system.get(),description.get(),quantity.get(),unit.get(),materialUnit.get(),labourUnitHours.get(),productivityFactor.get());}
    }
    private static final class QuoteLine {
        final BooleanProperty include=new SimpleBooleanProperty(); final StringProperty vendor=new SimpleStringProperty(); final StringProperty scope=new SimpleStringProperty(); final DoubleProperty amount=new SimpleDoubleProperty(); final StringProperty status=new SimpleStringProperty();
        QuoteLine(boolean i,String v,String s,double a,String st){include.set(i);vendor.set(v);scope.set(s);amount.set(a);status.set(st);}
    }
    private static final class IndirectLine {
        final StringProperty type=new SimpleStringProperty(); final StringProperty description=new SimpleStringProperty(); final DoubleProperty amount=new SimpleDoubleProperty();
        IndirectLine(String t,String d,double a){type.set(t);description.set(d);amount.set(a);}
    }
}

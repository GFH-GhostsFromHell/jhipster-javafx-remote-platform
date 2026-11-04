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
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.util.converter.DoubleStringConverter;
import javafx.stage.FileChooser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.Function;

/** Functional local preview of the optional Advanced Electrical Estimating module. */
public final class EstimatingWorkspaceDraftPane extends BorderPane {
    private static final String CARD = "-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#dbe5ef;-fx-border-radius:14;";
    private static final String INPUT = "-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:7 9;";
    private static final String PRIMARY = "-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-padding:8 12;";
    private static final String GHOST = "-fx-background-color:#eef6fa;-fx-text-fill:#0f4c81;-fx-border-color:#c7d9e8;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 11;-fx-font-weight:bold;";
    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(Locale.CANADA);

    private final ObservableList<EstimateLine> lines = FXCollections.observableArrayList();
    private final ObservableList<QuoteLine> quotes = FXCollections.observableArrayList();
    private final ObservableList<IndirectLine> indirects = FXCollections.observableArrayList();
    private final ObservableList<ChangeLine> changes = FXCollections.observableArrayList();
    private final ObservableList<AssemblyKit> kits = FXCollections.observableArrayList();
    private final ObjectMapper mapper = new ObjectMapper();
    private final LocalWorkspaceStore workspace;
    private String projectId = UUID.randomUUID().toString();
    private final TextField revision = numberField("R0");
    private final TextArea breakdownArea = new TextArea();
    private final TextArea validationArea = new TextArea();
    private final Label validationStatus = new Label();
    private final Label alternateTotal = valueLabel();
    private final Label approvedChangesTotal = valueLabel();
    private final List<CheckBox> reviewChecks = new ArrayList<>();
    private TableView<EstimateLine> takeoffTable;

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
    private final Label equipmentTotal = valueLabel();
    private final ComboBox<String> profitBasis = new ComboBox<>(FXCollections.observableArrayList("Markup on cost","Target gross margin"));
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

    public EstimatingWorkspaceDraftPane() { this("default"); }

    public EstimatingWorkspaceDraftPane(String username) {
        workspace = new LocalWorkspaceStore(mapper, "preview-estimating-"+username);
        profitBasis.setValue("Markup on cost");
        profitBasis.valueProperty().addListener((o,a,b)->recalc());
        setStyle("-fx-background-color:#eef3f8;");
        seedKits();
        loadUserKits();
        seedElectricalStructure();
        seedIndirects();

        VBox shell = new VBox(12, header(), summaryStrip(), workflowTabs());
        shell.setPadding(new Insets(2));
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
        Label note = note("Working estimate flow: scope the tender, build the takeoff, apply labour/productivity, level supplier/subcontractor quotes, add indirects, then close and review the bid.");

        GridPane grid = new GridPane();
        grid.setHgap(9); grid.setVgap(8);
        for (int i=0;i<3;i++) { ColumnConstraints c=new ColumnConstraints(); c.setPercentWidth(33.33); grid.getColumnConstraints().add(c); }
        grid.add(labeled("Project",projectName),0,0); grid.add(labeled("Client / GC",clientName),1,0); grid.add(labeled("Estimator",estimatorName),2,0);
        grid.add(labeled("Bid / estimate #",bidNumber),0,1); grid.add(labeled("Location",location),1,1); grid.add(labeled("Bid due",dueDate),2,1);
        grid.add(labeled("Revision (R0, R1...)",revision),0,2);
        Button save=button("Save revision",true); save.setOnAction(e->saveRevision());
        Button open=button("Open saved",false); open.setOnAction(e->openRevision());
        Button fresh=button("New estimate",false); fresh.setOnAction(e->newEstimate());
        Button csv=button("Export cost CSV",false); csv.setOnAction(e->exportCsv());
        Button importCsv=button("Import takeoff CSV",false); importCsv.setOnAction(e->importCsv());
        Button proposal=button("Export bid proposal",false); proposal.setOnAction(e->exportProposal());
        HBox actions=new HBox(8,save,open,fresh,csv,importCsv,proposal); actions.setStyle("-fx-padding:6 0 0 0;");
        box.getChildren().addAll(badge,title,note,grid,actions);
        return box;
    }

    private Node summaryStrip() {
        HBox row = new HBox(10,
            stat("Material",materialTotal), stat("Equipment",equipmentTotal), stat("Adj. labour hours",labourHoursTotal), stat("Labour",labourTotal),
            stat("Selected quotes",quoteTotal), stat("Indirects",indirectTotal), stat("Total incl. tax",finalBidTotal));
        for(Node n:row.getChildren()) HBox.setHgrow(n,Priority.ALWAYS);
        return row;
    }

    private Node workflowTabs() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
            new Tab("1 · Scope",scopePane()), new Tab("PDF Plan Takeoff",new EstimatingPdfTakeoffPane((sys,ph,dr,desc,qty,unit)->{
                EstimateLine x=new EstimateLine(sys,desc,qty,unit,0,0,1);x.phase.set(ph);x.drawing.set(dr);lines.add(x);recalc();
            })), new Tab("2 · Takeoff & Pricing",takeoffPane()), new Tab("3 · Quotes",quotesPane()),
            new Tab("4 · Indirects & Markups",indirectsPane()), new Tab("5 · Bid Review",reviewPane()),
            new Tab("6 · Assemblies",assembliesPane()), new Tab("7 · Cost Breakdown",breakdownPane()), new Tab("8 · Alternates / Changes",changesPane()));
        return tabs;
    }

    private Node scopePane() {
        VBox page=new VBox(12); page.setPadding(new Insets(14));
        HBox cards=new HBox(12,textCard("Included scope",inclusions),textCard("Exclusions / by others",exclusions),textCard("Clarifications / RFIs / addenda",clarifications));
        for(Node n:cards.getChildren()) HBox.setHgrow(n,Priority.ALWAYS);
        page.getChildren().addAll(sectionTitle("Scope / tender review"),note("Capture the bid basis before pricing: revisions, specifications, addenda, site conditions, inclusions, exclusions and clarifications."),cards,
            checklistCard("Tender readiness",
                "Bid documents received and revision set confirmed",
                "Specifications and electrical drawings reviewed",
                "Addenda logged and incorporated",
                "Site visit / existing conditions considered",
                "Major scope gaps and by-others items identified",
                "Bid form, bonding, insurance and submission requirements identified"));
        ScrollPane scroll=new ScrollPane(page); scroll.setFitToWidth(true); return scroll;
    }

    private Node takeoffPane() {
        VBox page=new VBox(10); page.setPadding(new Insets(14));
        TableView<EstimateLine> table=new TableView<>(lines); table.setEditable(true); table.setPrefHeight(390); table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE); takeoffTable=table;
        TableColumn<EstimateLine,String> system=textCol("System / division",150,x->x.system);
        TableColumn<EstimateLine,String> phase=textCol("Phase / area",115,x->x.phase);
        TableColumn<EstimateLine,String> drawing=textCol("Drawing / source",120,x->x.drawing);
        TableColumn<EstimateLine,String> desc=textCol("Description",240,x->x.description);
        TableColumn<EstimateLine,String> supplier=textCol("Price source / vendor",148,x->x.priceSource);
        TableColumn<EstimateLine,String> priced=textCol("Price date",105,x->x.priceDate);
        TableColumn<EstimateLine,Double> qty=doubleCol("Qty",72,x->x.quantity);
        TableColumn<EstimateLine,String> unit=textCol("Unit",64,x->x.unit);
        TableColumn<EstimateLine,Double> matUnit=doubleCol("Mat. $/unit",92,x->x.materialUnit);
        TableColumn<EstimateLine,Double> equipment=doubleCol("Equipment $/unit",110,x->x.equipmentUnit);
        TableColumn<EstimateLine,Double> labUnit=doubleCol("Lab h/unit",86,x->x.labourUnitHours);
        TableColumn<EstimateLine,Double> factor=doubleCol("Prod. factor",86,x->x.productivityFactor);
        TableColumn<EstimateLine,String> matExt=readOnlyCol("Material ext.",100,x->MONEY.format(x.materialExtended()));
        TableColumn<EstimateLine,String> equipExt=readOnlyCol("Equip. ext.",100,x->MONEY.format(x.equipmentExtended()));
        TableColumn<EstimateLine,String> hrsExt=readOnlyCol("Adj. hours",86,x->String.format("%.2f",x.adjustedHours()));
        table.getColumns().addAll(system,phase,drawing,desc,qty,unit,matUnit,equipment,labUnit,factor,supplier,priced,matExt,equipExt,hrsExt);

        Button add=button("+ Add line",true); add.setOnAction(e->{lines.add(new EstimateLine("Branch wiring","New takeoff item",1,"ea",0,0,1));recalc();});
        Button duplicate=button("Duplicate selected",false); duplicate.setOnAction(e->{EstimateLine x=table.getSelectionModel().getSelectedItem();if(x!=null){lines.add(x.copy());recalc();}});
        Button remove=button("Remove selected",false); remove.setOnAction(e->{EstimateLine x=table.getSelectionModel().getSelectedItem();if(x!=null){lines.remove(x);recalc();}});
        Button reset=button("Reset electrical structure",false); reset.setOnAction(e->{lines.clear();seedElectricalStructure();recalc();});
        Button saveKit=button("Save selection as assembly",false); saveKit.setOnAction(e->saveSelectedKit());
        Button priceTemplate=button("Export vendor price request",false);priceTemplate.setOnAction(e->exportPriceRequest());
        Button updatePrices=button("Apply vendor price CSV",false);updatePrices.setOnAction(e->applyPriceBook());
        HBox actions=new HBox(8,add,duplicate,remove,saveKit,priceTemplate,updatePrices,reset);

        labourRate.textProperty().addListener((o,a,b)->recalc());
        HBox settings=new HBox(12, compactField("Loaded labour $/h",labourRate),
            infoBox("Labour model","Adjusted hours = quantity × labour hours/unit × productivity factor."),
            infoBox("Pricing model","Material prices are entered before taxes; supplier/subcontractor quotes are leveled separately."));
        HBox.setHgrow(settings.getChildren().get(1),Priority.ALWAYS); HBox.setHgrow(settings.getChildren().get(2),Priority.ALWAYS);
        page.getChildren().addAll(sectionTitle("Electrical takeoff, material and labour"),
            note("Use one line per measured item, assembly or equipment package. Material and labour extend automatically; productivity factor > 1.00 increases labour hours."),actions,table,settings);
        return page;
    }

    private Node quotesPane() {
        VBox page=new VBox(10); page.setPadding(new Insets(14));
        TableView<QuoteLine> table=new TableView<>(quotes); table.setEditable(true); table.setPrefHeight(350);
        TableColumn<QuoteLine,Boolean> include=new TableColumn<>("Carry"); include.setCellValueFactory(c->c.getValue().include); include.setCellFactory(CheckBoxTableCell.forTableColumn(include)); include.setEditable(true); include.setPrefWidth(62);
        TableColumn<QuoteLine,String> vendor=quoteTextCol("Vendor / subcontractor",180,x->x.vendor);
        TableColumn<QuoteLine,String> group=quoteTextCol("Comparison group",130,x->x.group);
        TableColumn<QuoteLine,String> scope=quoteTextCol("Quoted scope",250,x->x.scope);
        TableColumn<QuoteLine,Double> amount=quoteDoubleCol("Amount",110,x->x.amount);
        TableColumn<QuoteLine,String> status=quoteTextCol("Status / qualifications",220,x->x.status);
        table.getColumns().addAll(include,vendor,group,scope,amount,status);

        Button add=button("+ Add quote",true); add.setOnAction(e->{QuoteLine q=new QuoteLine(false,"New vendor","Quoted scope",0,"Review qualifications");wireQuote(q);quotes.add(q);recalc();});
        Button remove=button("Remove selected",false); remove.setOnAction(e->{QuoteLine q=table.getSelectionModel().getSelectedItem();if(q!=null){quotes.remove(q);recalc();}});
        page.getChildren().addAll(sectionTitle("Supplier / subcontractor quote leveling"),
            note("Capture competing quotes, identify scope coverage, and explicitly choose which quotation is carried into the bid."),new HBox(8,add,remove),table,
            checklistCard("Quote leveling checks",
                "Scope matches drawings/specifications and estimate breakdown",
                "Exclusions and qualifications reviewed",
                "Freight, taxes, lead times and escalation understood",
                "Alternates and substitutions identified",
                "Duplicate scope between self-perform and quoted work removed"));
        return page;
    }

    private Node indirectsPane() {
        VBox page=new VBox(10); page.setPadding(new Insets(14));
        TableView<IndirectLine> table=new TableView<>(indirects); table.setEditable(true); table.setPrefHeight(290);
        TableColumn<IndirectLine,String> type=indirectTextCol("Type",150,x->x.type);
        TableColumn<IndirectLine,String> desc=indirectTextCol("Description",300,x->x.description);
        TableColumn<IndirectLine,Double> amount=indirectDoubleCol("Amount",120,x->x.amount);
        table.getColumns().addAll(type,desc,amount);
        Button add=button("+ Add indirect",true); add.setOnAction(e->{indirects.add(new IndirectLine("General conditions","New indirect cost",0));recalc();});
        Button remove=button("Remove selected",false); remove.setOnAction(e->{IndirectLine x=table.getSelectionModel().getSelectedItem();if(x!=null){indirects.remove(x);recalc();}});

        GridPane markups=new GridPane(); markups.setHgap(12); markups.setVgap(8);
        markups.add(compactField("Contingency %",contingencyPct),0,0); markups.add(compactField("Overhead %",overheadPct),1,0); markups.add(compactField("Profit rate %",profitPct),2,0); markups.add(labeled("Profit method",profitBasis),3,0); markups.add(compactField("Tax %",taxPct),4,0);
        contingencyPct.textProperty().addListener((o,a,b)->recalc()); overheadPct.textProperty().addListener((o,a,b)->recalc()); profitPct.textProperty().addListener((o,a,b)->recalc()); taxPct.textProperty().addListener((o,a,b)->recalc());
        HBox totals=new HBox(10,stat("Pre-tax bid",preTaxTotal),stat("Tax",taxTotal),stat("Final bid",finalBidTotal)); for(Node n:totals.getChildren()) HBox.setHgrow(n,Priority.ALWAYS);
        page.getChildren().addAll(sectionTitle("Indirect costs, risk and commercial markups"),
            note("Profit markup and target gross margin differ. Choose a method explicitly. Confirm whether the tender requests a price before tax."),
            note("Separate direct electrical work from project-specific indirect costs and company-level markups so the bid can be reviewed transparently."),new HBox(8,add,remove),table,markups,totals);
        return page;
    }

    private Node reviewPane() {
        VBox page=new VBox(12); page.setPadding(new Insets(14));
        VBox checks=new VBox(7); checks.setPadding(new Insets(14)); checks.setStyle(CARD);
        String[] items={
            "Drawing/spec revision and all addenda confirmed","Quantity takeoff double-checked","Material pricing and supplier quotes reviewed",
            "Labour rate, productivity and staffing assumptions reviewed","Subcontractor scope leveled and selected quotes confirmed","General conditions / indirect costs complete",
            "Bonding, insurance, permits, freight and escalation addressed","Inclusions, exclusions and clarifications reviewed","Overhead, profit and taxes verified","Bid form / proposal / alternates complete and authorized"};
        for(String item:items){CheckBox cb=new CheckBox(item);cb.setWrapText(true);cb.selectedProperty().addListener((o,a,b)->{updateReviewProgress(checks);updateValidation();});checks.getChildren().add(cb);reviewChecks.add(cb);}
        reviewProgress.setStyle("-fx-font-weight:bold;-fx-text-fill:#0e7490;"); updateReviewProgress(checks);
        VBox commercial=new VBox(7,pair("Material",materialTotal),pair("Equipment",equipmentTotal),pair("Adjusted labour hours",labourHoursTotal),pair("Labour",labourTotal),pair("Selected quotes",quoteTotal),pair("Indirects",indirectTotal),new Separator(),pair("Pre-tax bid",preTaxTotal),pair("Tax",taxTotal),pair("TOTAL INCLUDING EST. TAX",finalBidTotal));
        commercial.setPadding(new Insets(16)); commercial.setStyle(CARD); commercial.setPrefWidth(330);
        Button copy=button("Copy bid summary",true); copy.setOnAction(e->copySummary());
        VBox left=new VBox(8,reviewProgress,checks,copy); HBox body=new HBox(12,left,commercial); HBox.setHgrow(left,Priority.ALWAYS);
        validationArea.setEditable(false);validationArea.setPrefRowCount(8);validationArea.setWrapText(true);validationArea.setStyle(INPUT);
        validationStatus.setStyle("-fx-font-weight:bold;-fx-text-fill:#92400e;");
        page.getChildren().addAll(sectionTitle("Bid closing / review"),note("Confirm quantities, quote coverage, labour assumptions, commercial requirements, exclusions and submission documents before the bid leaves the company."),body,sectionTitle("Automated estimate audit"),validationStatus,validationArea);
        return page;
    }

    private void wireQuote(QuoteLine q){q.include.addListener((o,a,b)->recalc());}

    private void copySummary(){
        String text="ELECTRICAL ESTIMATE SUMMARY\nProject: "+projectName.getText()+"\nClient / GC: "+clientName.getText()+"\nEstimate #: "+bidNumber.getText()+"\nEstimator: "+estimatorName.getText()+
            "\n\nMaterial: "+materialTotal.getText()+"\nEquipment: "+equipmentTotal.getText()+"\nAdjusted labour hours: "+labourHoursTotal.getText()+"\nLabour: "+labourTotal.getText()+"\nSelected quotes: "+quoteTotal.getText()+"\nIndirects: "+indirectTotal.getText()+
            "\nPre-tax bid: "+preTaxTotal.getText()+"\nTax: "+taxTotal.getText()+"\nTOTAL INCLUDING EST. TAX: "+finalBidTotal.getText()+"\n\nInclusions:\n"+inclusions.getText()+"\n\nExclusions:\n"+exclusions.getText()+"\n\nClarifications:\n"+clarifications.getText();
        ClipboardContent content=new ClipboardContent(); content.putString(text); Clipboard.getSystemClipboard().setContent(content);
    }

    private void recalc(){
        double rate=parse(labourRate.getText());
        double mat=lines.stream().mapToDouble(EstimateLine::materialExtended).sum();
        double hours=lines.stream().mapToDouble(EstimateLine::adjustedHours).sum();
        double labour=hours*rate;
        double equipment=lines.stream().mapToDouble(EstimateLine::equipmentExtended).sum();
        double q=quotes.stream().filter(x->x.include.get()).mapToDouble(x->x.amount.get()).sum();
        double ind=indirects.stream().mapToDouble(x->x.amount.get()).sum();
        double base=mat+equipment+labour+q+ind;
        double contingency=base*parse(contingencyPct.getText())/100.0;
        double overhead=(base+contingency)*parse(overheadPct.getText())/100.0;
        double profitBase=base+contingency+overhead;
        double p=parse(profitPct.getText())/100.0;
        double profit="Target gross margin".equals(profitBasis.getValue())
            ? profitBase*(Math.min(.99,p)/(1.0-Math.min(.99,p)))
            : profitBase*p;
        double preTax=base+contingency+overhead+profit;
        double tax=preTax*parse(taxPct.getText())/100.0;
        materialTotal.setText(MONEY.format(mat)); equipmentTotal.setText(MONEY.format(equipment)); labourHoursTotal.setText(String.format("%.1f h",hours)); labourTotal.setText(MONEY.format(labour)); quoteTotal.setText(MONEY.format(q)); indirectTotal.setText(MONEY.format(ind)); preTaxTotal.setText(MONEY.format(preTax)); taxTotal.setText(MONEY.format(tax)); finalBidTotal.setText(MONEY.format(preTax+tax));
        alternateTotal.setText(MONEY.format(changes.stream().filter(c->c.kind.get().equalsIgnoreCase("Alternate")).mapToDouble(c->c.selling.get()).sum()));
        approvedChangesTotal.setText(MONEY.format(changes.stream().filter(c->c.kind.get().equalsIgnoreCase("Change Order")&&c.status.get().equalsIgnoreCase("Approved")).mapToDouble(c->c.selling.get()).sum()));
        refreshBreakdown();
        updateValidation();
        if(takeoffTable!=null)takeoffTable.refresh();
    }

    private void seedElectricalStructure(){
        lines.addAll(
            new EstimateLine("01 Service / distribution","Service, switchgear, main distribution",0,"lot",0,0,1),
            new EstimateLine("02 Feeders","Feeders, conduit / cable, supports and terminations",0,"lot",0,0,1),
            new EstimateLine("03 Branch wiring","Branch circuits, raceway, conductors and boxes",0,"lot",0,0,1),
            new EstimateLine("04 Lighting / controls","Fixtures, controls, emergency lighting and branch wiring",0,"lot",0,0,1),
            new EstimateLine("05 Devices","Receptacles, switches, disconnects and specialty outlets",0,"lot",0,0,1),
            new EstimateLine("06 Mechanical power","Motors, VFDs, HVAC and process equipment connections",0,"lot",0,0,1),
            new EstimateLine("07 Fire alarm / low voltage","Fire alarm, communications and specialty systems",0,"lot",0,0,1),
            new EstimateLine("08 Grounding / bonding","Grounding, bonding, electrodes and equipotential work",0,"lot",0,0,1),
            new EstimateLine("09 Supports / misc.","Supports, sleeves, firestopping, labels and miscellaneous material",0,"lot",0,0,1));
    }

    private void seedIndirects(){
        indirects.addAll(new IndirectLine("General conditions","Supervision / project management",0),new IndirectLine("General conditions","Mobilization, temporary facilities and small tools",0),new IndirectLine("Commercial","Permits, bonding and insurance",0),new IndirectLine("Commercial","Freight, escalation or special handling",0));
    }

    private TableColumn<EstimateLine,Double> doubleCol(String title,double width,Function<EstimateLine,DoubleProperty> prop){
        TableColumn<EstimateLine,Double> c=new TableColumn<>(title); c.setCellValueFactory(x->prop.apply(x.getValue()).asObject()); c.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter())); c.setPrefWidth(width); c.setOnEditCommit(e->{prop.apply(e.getRowValue()).set(e.getNewValue());recalc();}); return c;
    }
    private TableColumn<EstimateLine,String> textCol(String title,double width,Function<EstimateLine,StringProperty> prop){TableColumn<EstimateLine,String> c=new TableColumn<>(title);c.setCellValueFactory(x->prop.apply(x.getValue()));c.setCellFactory(TextFieldTableCell.forTableColumn());c.setOnEditCommit(e->{prop.apply(e.getRowValue()).set(e.getNewValue());recalc();});c.setPrefWidth(width);return c;}
    private static TableColumn<EstimateLine,String> readOnlyCol(String title,double width,Function<EstimateLine,String> prop){TableColumn<EstimateLine,String> c=new TableColumn<>(title);c.setCellValueFactory(x->new SimpleStringProperty(prop.apply(x.getValue())));c.setPrefWidth(width);return c;}
    private TableColumn<QuoteLine,String> quoteTextCol(String title,double width,Function<QuoteLine,StringProperty> prop){TableColumn<QuoteLine,String> c=new TableColumn<>(title);c.setCellValueFactory(x->prop.apply(x.getValue()));c.setCellFactory(TextFieldTableCell.forTableColumn());c.setOnEditCommit(e->{prop.apply(e.getRowValue()).set(e.getNewValue());recalc();});c.setPrefWidth(width);return c;}
    private TableColumn<QuoteLine,Double> quoteDoubleCol(String title,double width,Function<QuoteLine,DoubleProperty> prop){TableColumn<QuoteLine,Double> c=new TableColumn<>(title);c.setCellValueFactory(x->prop.apply(x.getValue()).asObject());c.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));c.setPrefWidth(width);c.setOnEditCommit(e->{prop.apply(e.getRowValue()).set(e.getNewValue());recalc();});return c;}
    private TableColumn<IndirectLine,String> indirectTextCol(String title,double width,Function<IndirectLine,StringProperty> prop){TableColumn<IndirectLine,String> c=new TableColumn<>(title);c.setCellValueFactory(x->prop.apply(x.getValue()));c.setCellFactory(TextFieldTableCell.forTableColumn());c.setOnEditCommit(e->{prop.apply(e.getRowValue()).set(e.getNewValue());recalc();});c.setPrefWidth(width);return c;}
    private TableColumn<IndirectLine,Double> indirectDoubleCol(String title,double width,Function<IndirectLine,DoubleProperty> prop){TableColumn<IndirectLine,Double> c=new TableColumn<>(title);c.setCellValueFactory(x->prop.apply(x.getValue()).asObject());c.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));c.setPrefWidth(width);c.setOnEditCommit(e->{prop.apply(e.getRowValue()).set(e.getNewValue());recalc();});return c;}

    private static VBox checklistCard(String title,String...items){VBox box=new VBox(7);box.setPadding(new Insets(14));box.setStyle(CARD);Label h=new Label(title);h.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");box.getChildren().add(h);for(String item:items){CheckBox cb=new CheckBox(item);cb.setWrapText(true);box.getChildren().add(cb);}return box;}
    private static VBox textCard(String title,TextArea area){VBox box=new VBox(7);box.setPadding(new Insets(14));box.setStyle(CARD);Label h=new Label(title);h.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");box.getChildren().addAll(h,area);return box;}
    private static VBox stat(String title,Label value){VBox box=new VBox(4);box.setPadding(new Insets(12));box.setMaxWidth(Double.MAX_VALUE);box.setStyle(CARD);Label h=new Label(title.toUpperCase());h.setStyle("-fx-text-fill:#64748b;-fx-font-size:9px;-fx-font-weight:bold;");box.getChildren().addAll(h,value);return box;}
    private static HBox pair(String title,Label value){Label k=new Label(title);Region push=new Region();HBox.setHgrow(push,Priority.ALWAYS);return new HBox(8,k,push,value);}
    private static VBox compactField(String title,TextField field){return labeled(title,field);}
    private static VBox infoBox(String title,String text){VBox box=new VBox(3);box.setPadding(new Insets(10));box.setStyle("-fx-background-color:#f8fafc;-fx-background-radius:9;-fx-border-color:#e2e8f0;-fx-border-radius:9;");Label h=new Label(title);h.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;");box.getChildren().addAll(h,note(text));return box;}
    private static VBox labeled(String title,Node field){Label l=new Label(title);l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;-fx-font-size:11px;");return new VBox(4,l,field);}
    private static Label sectionTitle(String text){Label l=new Label(text);l.setStyle("-fx-font-size:19px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");return l;}
    private static Label note(String text){Label l=new Label(text);l.setWrapText(true);l.setStyle("-fx-text-fill:#64748b;-fx-font-size:11px;");return l;}
    private static Label valueLabel(){Label l=new Label("—");l.setStyle("-fx-text-fill:#0e7490;-fx-font-size:17px;-fx-font-weight:bold;");return l;}
    private static Button button(String text,boolean primary){Button b=new Button(text);b.setStyle(primary?PRIMARY:GHOST);return b;}
    private static TextField field(String prompt){TextField f=new TextField();f.setPromptText(prompt);f.setStyle(INPUT);return f;}
    private static TextField numberField(String value){TextField f=field("");f.setText(value);f.setPrefWidth(105);return f;}
    private static TextArea area(String prompt){TextArea a=new TextArea();a.setPromptText(prompt);a.setWrapText(true);a.setPrefRowCount(7);a.setStyle(INPUT);return a;}
    private static double parse(String text){try{return Math.max(0,Double.parseDouble(text==null||text.isBlank()?"0":text.replace(",","").trim()));}catch(Exception ex){return 0;}}
    private void updateReviewProgress(VBox container){long total=container.getChildren().stream().filter(n->n instanceof CheckBox).count();long done=container.getChildren().stream().filter(n->n instanceof CheckBox cb&&cb.isSelected()).count();reviewProgress.setText("Bid review: "+done+" / "+total+" checks complete");}


    // Preview estimates are local, per account, versioned snapshots. No production data or billing is changed.
    private void saveRevision() {
        if(projectName.getText().isBlank() || bidNumber.getText().isBlank()){
            if(!confirm("Incomplete bid header","Project name or estimate number is blank. Save this draft anyway?")) return;
        }
        ObjectNode root=snapshot();
        root.put("savedAt",Instant.now().toString());
        root.put("snapshotId",UUID.randomUUID().toString());
        try {
            workspace.append("enterpriseEstimates",root);
            message("Estimate saved","Saved revision "+revision.getText()+" for "+projectName.getText()+".\nThis is a local, per-account preview snapshot and is not synchronized to the company server.");
        } catch(Exception ex){ error("Save failed",ex); }
    }

    private ObjectNode snapshot(){
        ObjectNode root=mapper.createObjectNode();
        root.put("projectId",projectId);root.put("project",projectName.getText());root.put("client",clientName.getText());
        root.put("estimator",estimatorName.getText());root.put("bidNumber",bidNumber.getText());root.put("location",location.getText());
        root.put("dueDate",dueDate.getText());root.put("revision",revision.getText());
        root.put("inclusions",inclusions.getText());root.put("exclusions",exclusions.getText());root.put("clarifications",clarifications.getText());
        root.put("labourRate",labourRate.getText());root.put("contingency",contingencyPct.getText());
        root.put("overhead",overheadPct.getText());root.put("profit",profitPct.getText());root.put("tax",taxPct.getText());root.put("profitBasis",profitBasis.getValue());
        root.put("version",2);
        ArrayNode a=root.putArray("lines");for(EstimateLine x:lines)a.add(lineJson(x));
        ArrayNode q=root.putArray("quotes");
        for(QuoteLine x:quotes){ObjectNode o=q.addObject();o.put("include",x.include.get());o.put("vendor",x.vendor.get());o.put("group",x.group.get());o.put("scope",x.scope.get());o.put("amount",x.amount.get());o.put("status",x.status.get());}
        ArrayNode i=root.putArray("indirects");
        for(IndirectLine x:indirects){ObjectNode o=i.addObject();o.put("type",x.type.get());o.put("description",x.description.get());o.put("amount",x.amount.get());}
        ArrayNode c=root.putArray("changes");
        for(ChangeLine x:changes){ObjectNode o=c.addObject();o.put("kind",x.kind.get());o.put("description",x.description.get());o.put("selling",x.selling.get());o.put("status",x.status.get());}
        ArrayNode checks=root.putArray("bidChecks");for(CheckBox cb:reviewChecks) checks.add(cb.isSelected());
        return root;
    }

    private static ObjectNode lineJson(EstimateLine x){
        ObjectNode o=new ObjectMapper().createObjectNode();
        o.put("system",x.system.get());o.put("phase",x.phase.get());o.put("drawing",x.drawing.get());
        o.put("description",x.description.get());o.put("quantity",x.quantity.get());o.put("unit",x.unit.get());
        o.put("priceSource",x.priceSource.get());o.put("priceDate",x.priceDate.get());
        o.put("materialUnit",x.materialUnit.get());o.put("equipmentUnit",x.equipmentUnit.get());o.put("labourUnitHours",x.labourUnitHours.get());o.put("productivityFactor",x.productivityFactor.get());
        return o;
    }
    private static EstimateLine parseLine(JsonNode n){
        EstimateLine x=new EstimateLine(n.path("system").asText("Uncategorized"),n.path("description").asText(""),
            Math.max(0,n.path("quantity").asDouble()),n.path("unit").asText("ea"),
            Math.max(0,n.path("materialUnit").asDouble()),Math.max(0,n.path("labourUnitHours").asDouble()),
            Math.max(0,n.path("productivityFactor").asDouble(1)));
        x.phase.set(n.path("phase").asText("General"));x.drawing.set(n.path("drawing").asText(""));
        x.priceSource.set(n.path("priceSource").asText(""));x.priceDate.set(n.path("priceDate").asText(""));
        x.equipmentUnit.set(Math.max(0,n.path("equipmentUnit").asDouble()));
        return x;
    }

    private void openRevision(){
        ArrayNode saved=workspace.records("enterpriseEstimates");
        if(saved.isEmpty()){message("No saved estimates","No preview revisions are saved for this account.");return;}
        Map<String,JsonNode> options=new LinkedHashMap<>();
        for(int i=saved.size()-1;i>=0;i--){
            JsonNode n=saved.get(i);
            String title=n.path("savedAt").asText("")+" | "+n.path("project").asText("Untitled")+
                " | "+n.path("bidNumber").asText("")+" / "+n.path("revision").asText("R0");
            options.put(String.format("%04d · %s",i+1,title),n);
        }
        ChoiceDialog<String> dialog=new ChoiceDialog<>(options.keySet().iterator().next(),options.keySet());
        dialog.setTitle("Saved estimating revisions");dialog.setHeaderText("Open a local estimate snapshot");
        dialog.setContentText("Revision:");
        dialog.showAndWait().ifPresent(choice->{
            if(!confirm("Load saved revision","Replace the current unsaved edits with this saved revision?"))return;
            loadSnapshot(options.get(choice));message("Revision opened","Loaded "+choice);
        });
    }

    private void loadSnapshot(JsonNode n){
        projectId=n.path("projectId").asText(UUID.randomUUID().toString());
        projectName.setText(n.path("project").asText(""));clientName.setText(n.path("client").asText(""));
        estimatorName.setText(n.path("estimator").asText(""));bidNumber.setText(n.path("bidNumber").asText(""));
        location.setText(n.path("location").asText(""));dueDate.setText(n.path("dueDate").asText(""));
        revision.setText(n.path("revision").asText("R0"));
        inclusions.setText(n.path("inclusions").asText(""));exclusions.setText(n.path("exclusions").asText(""));
        clarifications.setText(n.path("clarifications").asText(""));
        labourRate.setText(n.path("labourRate").asText("78"));
        contingencyPct.setText(n.path("contingency").asText("3"));overheadPct.setText(n.path("overhead").asText("8"));
        profitPct.setText(n.path("profit").asText("10"));taxPct.setText(n.path("tax").asText("13"));
        profitBasis.setValue(n.path("profitBasis").asText("Markup on cost"));
        lines.clear();for(JsonNode x:n.path("lines"))lines.add(parseLine(x));
        quotes.clear();
        for(JsonNode x:n.path("quotes")){
            QuoteLine q=new QuoteLine(x.path("include").asBoolean(),x.path("vendor").asText(""),
                x.path("scope").asText(""),Math.max(0,x.path("amount").asDouble()),x.path("status").asText(""));
            q.group.set(x.path("group").asText("General"));wireQuote(q);quotes.add(q);
        }
        indirects.clear();for(JsonNode x:n.path("indirects"))indirects.add(new IndirectLine(
            x.path("type").asText(""),x.path("description").asText(""),Math.max(0,x.path("amount").asDouble())));
        changes.clear();for(JsonNode x:n.path("changes"))changes.add(new ChangeLine(
            x.path("kind").asText("Alternate"),x.path("description").asText(""),
            Math.max(0,x.path("selling").asDouble()),x.path("status").asText("Proposed")));
        JsonNode checks=n.path("bidChecks");
        for(int i=0;i<reviewChecks.size();i++)reviewChecks.get(i).setSelected(checks.isArray()&&i<checks.size()&&checks.get(i).asBoolean());
        recalc();
    }

    private void newEstimate(){
        if(!confirm("New estimate","Start a new estimate? Save the current revision first if you want to retain it."))return;
        projectId=UUID.randomUUID().toString();
        projectName.clear();clientName.clear();estimatorName.clear();bidNumber.clear();location.clear();dueDate.clear();
        revision.setText("R0");inclusions.clear();exclusions.clear();clarifications.clear();
        labourRate.setText("78.00");contingencyPct.setText("3.00");overheadPct.setText("8.00");profitPct.setText("10.00");taxPct.setText("13.00");profitBasis.setValue("Markup on cost");
        lines.clear();seedElectricalStructure();quotes.clear();indirects.clear();seedIndirects();changes.clear();
        for(CheckBox cb:reviewChecks)cb.setSelected(false);recalc();
    }

    private static boolean confirm(String title,String text){
        Alert dialog=new Alert(Alert.AlertType.CONFIRMATION,text,ButtonType.YES,ButtonType.NO);
        dialog.setTitle(title);dialog.setHeaderText(null);
        return dialog.showAndWait().orElse(ButtonType.NO)==ButtonType.YES;
    }
    private static void message(String title,String text){Alert a=new Alert(Alert.AlertType.INFORMATION,text,ButtonType.OK);a.setTitle(title);a.setHeaderText(null);a.showAndWait();}
    private static void error(String title,Exception ex){Alert a=new Alert(Alert.AlertType.ERROR,ex.getMessage()==null?ex.toString():ex.getMessage(),ButtonType.OK);a.setTitle(title);a.setHeaderText(null);a.showAndWait();}

    private Path chooseOutput(String suggestedName,String extension){
        FileChooser chooser=new FileChooser();chooser.setTitle("Save "+extension.toUpperCase(Locale.ROOT)+" file");
        chooser.setInitialFileName(suggestedName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(extension.toUpperCase(Locale.ROOT)+" file","*."+extension));
        java.io.File file=chooser.showSaveDialog(getScene()==null?null:getScene().getWindow());
        return file==null?null:file.toPath();
    }
    private static String csv(String input){
        String val=input==null?"":input;
        return "\""+val.replace("\"","\"\"")+"\"";
    }
    private static String csvRow(String...values){
        return java.util.Arrays.stream(values).map(EstimatingWorkspaceDraftPane::csv).collect(java.util.stream.Collectors.joining(","))+"\r\n";
    }

    /**
     * Produce an exact-match price request so suppliers can return verified job prices.
     * No proprietary pricing catalogue or assumed labour unit is bundled in the application.
     */
    private void exportPriceRequest(){
        Path file=chooseOutput("vendor-price-request-"+safeFile(bidNumber.getText())+".csv","csv");if(file==null)return;
        StringBuilder out=new StringBuilder(csvRow("description","unit","unit_price","vendor","price_date"));
        java.util.Set<String> seen=new LinkedHashSet<>();
        for(EstimateLine x:lines){
            String key=(x.description.get()+"|"+x.unit.get()).trim().toLowerCase(Locale.ROOT);
            if(x.description.get().isBlank()||!seen.add(key))continue;
            out.append(csvRow(x.description.get(),x.unit.get(),"","",""));
        }
        try{Files.writeString(file,"\uFEFF"+out,StandardCharsets.UTF_8);message("Price request saved","Send the CSV to your supplier and have them fill unit_price, vendor and price_date. Import the returned file using Apply vendor price CSV.");}
        catch(Exception ex){error("Price template export failed",ex);}
    }
    private void applyPriceBook(){
        FileChooser chooser=new FileChooser();chooser.setTitle("Apply supplier material price CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files","*.csv"));
        java.io.File file=chooser.showOpenDialog(getScene()==null?null:getScene().getWindow());if(file==null)return;
        try{
            List<List<String>> rows=parseCsv(Files.readString(file.toPath(),StandardCharsets.UTF_8).replace("\uFEFF",""));
            if(rows.isEmpty())throw new IllegalArgumentException("Price book CSV is empty");
            Map<String,Integer> headers=new LinkedHashMap<>();
            for(int i=0;i<rows.get(0).size();i++)headers.put(rows.get(0).get(i).toLowerCase(Locale.ROOT).trim().replace(' ','_'),i);
            if(!headers.containsKey("description")||!headers.containsKey("unit_price"))
                throw new IllegalArgumentException("CSV needs description and unit_price columns. Use Export vendor price request to create the template.");
            Map<String,List<PriceRecord>> byKey=new LinkedHashMap<>();
            for(int i=1;i<rows.size();i++){
                List<String> row=rows.get(i);
                String desc=get(row,headers,"description").trim();
                String unit=get(row,headers,"unit").trim();
                String raw=get(row,headers,"unit_price").trim();
                if(desc.isBlank()||raw.isBlank())continue;
                double value=parse(raw);if(value<=0)continue;
                String key=(desc+"|"+unit).toLowerCase(Locale.ROOT);
                byKey.computeIfAbsent(key,k->new ArrayList<>()).add(new PriceRecord(value,
                    get(row,headers,"vendor"),get(row,headers,"price_date")));
            }
            Map<EstimateLine,PriceRecord> pending=new LinkedHashMap<>();
            int ambiguous=0;
            for(EstimateLine x:lines){
                String key=(x.description.get().trim()+"|"+x.unit.get().trim()).toLowerCase(Locale.ROOT);
                List<PriceRecord> found=byKey.get(key);
                if(found==null)continue;
                if(found.size()!=1){ambiguous++;continue;} // Never silently pick a vendor when quotes conflict
                pending.put(x,found.get(0));
            }
            if(pending.isEmpty()){message("No prices applied","No exact item+unit matches found. "+ambiguous+" takeoff rows had multiple conflicting vendor prices.");return;}
            long overwritten=pending.keySet().stream().filter(x->x.materialUnit.get()>0).count();
            if(!confirm("Apply supplier prices","Apply "+pending.size()+" exact-match unit prices to the takeoff? "+
                overwritten+" rows already have prices and will be overwritten. "+ambiguous+" ambiguous rows are excluded."))return;
            for(Map.Entry<EstimateLine,PriceRecord> e:pending.entrySet()){
                EstimateLine x=e.getKey();PriceRecord p=e.getValue();x.materialUnit.set(p.value());
                x.priceSource.set(p.vendor().isBlank()?file.getName():p.vendor());
                x.priceDate.set(p.date());
            }
            recalc();message("Material pricing updated","Applied "+pending.size()+" verified-price entries. "+
                ambiguous+" rows with competing prices were skipped. Confirm vendor qualifications, quote validity, taxes and exclusions.");
        }catch(Exception ex){error("Price book import failed",ex);}
    }
    private record PriceRecord(double value,String vendor,String date){}

    private void exportCsv(){
        Path output=chooseOutput("estimate-"+safeFile(bidNumber.getText())+"-"+safeFile(revision.getText())+".csv","csv");
        if(output==null)return;
        StringBuilder csv=new StringBuilder();
        csv.append(csvRow("system","phase","drawing","description","quantity","unit","material_unit","equipment_unit","labour_hours_unit","productivity_factor","price_source","price_date","material_extended","equipment_extended","adjusted_hours","extended_labour"));
        double rate=parse(labourRate.getText());
        for(EstimateLine x:lines)csv.append(csvRow(x.system.get(),x.phase.get(),x.drawing.get(),x.description.get(),
            Double.toString(x.quantity.get()),x.unit.get(),Double.toString(x.materialUnit.get()),Double.toString(x.equipmentUnit.get()),
            Double.toString(x.labourUnitHours.get()),Double.toString(x.productivityFactor.get()),x.priceSource.get(),x.priceDate.get(),
            Double.toString(x.materialExtended()),Double.toString(x.equipmentExtended()),Double.toString(x.adjustedHours()),Double.toString(x.adjustedHours()*rate)));
        csv.append("\r\n").append(csvRow("BID RECAP","Amount (CAD)"));
        csv.append(csvRow("Material",materialTotal.getText())).append(csvRow("Equipment",equipmentTotal.getText())).append(csvRow("Labour",labourTotal.getText()));
        csv.append(csvRow("Selected supplier/subcontractor quotes",quoteTotal.getText())).append(csvRow("Indirects",indirectTotal.getText()));
        csv.append(csvRow("Bid before tax",preTaxTotal.getText())).append(csvRow("Tax (assumed)",taxTotal.getText()));
        csv.append(csvRow("Bid including tax",finalBidTotal.getText()));
        csv.append("\r\n").append(csvRow("SUPPLIER QUOTES","Vendor","Comparison group","Scope","Amount","Carry","Qualifications"));
        for(QuoteLine q:quotes)csv.append(csvRow("",q.vendor.get(),q.group.get(),q.scope.get(),Double.toString(q.amount.get()),Boolean.toString(q.include.get()),q.status.get()));
        csv.append("\r\n").append(csvRow("INDIRECT COSTS","Type","Description","Amount"));
        for(IndirectLine i:indirects)csv.append(csvRow("",i.type.get(),i.description.get(),Double.toString(i.amount.get())));
        csv.append("\r\n").append(csvRow("ALTERNATES / CHANGE ORDERS","Type","Description","Selling price before tax","Status"));
        for(ChangeLine c:changes)csv.append(csvRow("",c.kind.get(),c.description.get(),Double.toString(c.selling.get()),c.status.get()));
        try{Files.writeString(output,"\uFEFF"+csv,StandardCharsets.UTF_8);message("CSV exported","Saved detailed takeoff, cost recap and quotations to:\n"+output);}
        catch(Exception ex){error("Export failed",ex);}
    }
    private static String safeFile(String text){String s=text==null?"":text.replaceAll("[^A-Za-z0-9._-]+","-");return s.isBlank()?"draft":s;}

    private void importCsv(){
        FileChooser chooser=new FileChooser();chooser.setTitle("Import detailed takeoff CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files","*.csv"));
        java.io.File file=chooser.showOpenDialog(getScene()==null?null:getScene().getWindow());
        if(file==null)return;
        try{
            String data=Files.readString(file.toPath(),StandardCharsets.UTF_8).replace("\uFEFF","");
            List<List<String>> records=parseCsv(data);
            if(records.isEmpty())throw new IllegalArgumentException("CSV is empty");
            Map<String,Integer> headers=new LinkedHashMap<>();
            List<String> first=records.get(0);
            for(int j=0;j<first.size();j++)headers.put(first.get(j).toLowerCase(Locale.ROOT).trim().replace(' ','_'),j);
            if(!headers.containsKey("description")||!headers.containsKey("quantity"))
                throw new IllegalArgumentException("CSV needs description and quantity headers. Export a template to see the format.");
            int count=0;
            for(int i=1;i<records.size()&&count<10000;i++){
                List<String> row=records.get(i);if(get(row,headers,"description").isBlank())continue;
                EstimateLine x=new EstimateLine(getOr(row,headers,"system","Uncategorized"),
                    get(row,headers,"description"),parse(get(row,headers,"quantity")),
                    getOr(row,headers,"unit","ea"),parse(get(row,headers,"material_unit")),
                    parse(get(row,headers,"labour_hours_unit")),parse(getOr(row,headers,"productivity_factor","1")));
                x.phase.set(getOr(row,headers,"phase","General"));x.drawing.set(get(row,headers,"drawing"));
                x.priceSource.set(get(row,headers,"price_source"));x.priceDate.set(get(row,headers,"price_date"));
                x.equipmentUnit.set(parse(get(row,headers,"equipment_unit")));
                lines.add(x);count++;
            }
            recalc();message("Takeoff imported","Added "+count+" takeoff lines. Imported prices and labour hours are user supplied; review every line before bidding.");
        }catch(Exception ex){error("CSV import failed",ex);}
    }
    private static String get(List<String> row,Map<String,Integer> head,String name){Integer i=head.get(name);return i==null||i>=row.size()?"":row.get(i);}
    private static String getOr(List<String> row,Map<String,Integer> head,String name,String fallback){String s=get(row,head,name);return s.isBlank()?fallback:s;}
    private static List<List<String>> parseCsv(String s){
        List<List<String>> out=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quoted=false;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(c=='"'){if(quoted&&i+1<s.length()&&s.charAt(i+1)=='"'){field.append('"');i++;}else quoted=!quoted;}
            else if(c==','&&!quoted){row.add(field.toString());field.setLength(0);}
            else if((c=='\n'||c=='\r')&&!quoted){if(c=='\r'&&i+1<s.length()&&s.charAt(i+1)=='\n')i++;row.add(field.toString());field.setLength(0);if(row.stream().anyMatch(v->!v.isBlank()))out.add(row);row=new ArrayList<>();}
            else field.append(c);
        }
        if(quoted)throw new IllegalArgumentException("Unclosed CSV quoted field");
        if(field.length()>0||!row.isEmpty()){row.add(field.toString());out.add(row);}
        return out;
    }

    private void exportProposal(){
        Path file=chooseOutput("bid-proposal-"+safeFile(bidNumber.getText())+".html","html");if(file==null)return;
        String title=html(projectName.getText()),client=html(clientName.getText());
        StringBuilder h=new StringBuilder("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>Electrical Bid Proposal</title>");
        h.append("<style>body{max-width:850px;margin:44px auto;font:15px/1.55 Arial,sans-serif;color:#1e293b}h1,h2{color:#0f4c81}header{border-bottom:3px solid #0e7490;padding-bottom:16px}dl{display:grid;grid-template-columns:180px 1fr;gap:5px}dt{font-weight:bold}.total{padding:18px;border:2px solid #0e7490;font-size:20px;font-weight:bold}.small{font-size:12px;color:#64748b}section{margin-top:24px}pre{white-space:pre-wrap;font:inherit}</style></head><body>");
        h.append("<header><h1>Electrical Construction Bid Proposal</h1><p class=\"small\">Draft for client review — subject to written acceptance and tender requirements</p></header>");
        h.append("<dl><dt>Project</dt><dd>").append(title).append("</dd><dt>Client / GC</dt><dd>").append(client)
            .append("</dd><dt>Estimate</dt><dd>").append(html(bidNumber.getText())).append(" / ").append(html(revision.getText()))
            .append("</dd><dt>Project location</dt><dd>").append(html(location.getText()))
            .append("</dd><dt>Bid due</dt><dd>").append(html(dueDate.getText())).append("</dd></dl>");
        h.append("<section><h2>Scope included</h2><pre>").append(html(inclusions.getText())).append("</pre></section>");
        h.append("<section><h2>Exclusions</h2><pre>").append(html(exclusions.getText())).append("</pre></section>");
        h.append("<section><h2>Clarifications / assumptions</h2><pre>").append(html(clarifications.getText())).append("</pre></section>");
        h.append("<section><h2>Commercial offer</h2><p><strong>Proposed contract amount before sales tax: ")
            .append(html(preTaxTotal.getText())).append("</strong></p><p>Estimated sales tax (")
            .append(html(taxPct.getText())).append("%): ").append(html(taxTotal.getText()))
            .append("</p><p class=\"total\">Estimated total including sales tax: ").append(html(finalBidTotal.getText())).append("</p>");
        h.append("<p class=\"small\">Tax treatment and whether the tender requires a pre-tax price must be verified. Alternates and change orders are not part of the base bid unless expressly incorporated by agreement.</p></section>");
        if(!changes.isEmpty()){
            h.append("<section><h2>Separately priced alternates / changes</h2><ul>");
            for(ChangeLine c:changes)h.append("<li>").append(html(c.kind.get())).append(": ").append(html(c.description.get())).append(" — ")
                .append(MONEY.format(c.selling.get())).append(" before tax (").append(html(c.status.get())).append(")</li>");
            h.append("</ul></section>");
        }
        h.append("<section><p>Prepared by: ").append(html(estimatorName.getText())).append("</p><p>Accepted by: _____________________ &nbsp;&nbsp; Date: ______________</p></section></body></html>");
        try{Files.writeString(file,h,StandardCharsets.UTF_8);message("Proposal exported","Created a client-facing HTML proposal:\n"+file+"\nOpen in your browser and print to PDF if needed.");}
        catch(Exception ex){error("Proposal export failed",ex);}
    }
    private static String html(String s){return (s==null?"":s).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}

    private void refreshBreakdown(){
        Map<String,double[]> totals=new LinkedHashMap<>();double rate=parse(labourRate.getText());
        for(EstimateLine x:lines){
            String key=x.phase.get()+" | "+x.system.get();
            double[] a=totals.computeIfAbsent(key,k->new double[5]);
            a[0]+=x.materialExtended();a[1]+=x.equipmentExtended();a[2]+=x.adjustedHours();a[3]+=x.adjustedHours()*rate;
            a[4]+=x.materialExtended()+x.equipmentExtended()+x.adjustedHours()*rate;
        }
        StringBuilder b=new StringBuilder(String.format("%-39s %13s %13s %12s %13s %14s%n","PHASE | SYSTEM","MATERIAL $","EQUIPMENT $","LABOUR HRS","LABOUR $","DIRECT $"));
        b.append("─".repeat(112)).append('\n');
        for(Map.Entry<String,double[]> e:totals.entrySet()){
            double[] a=e.getValue();String key=e.getKey();if(key.length()>39)key=key.substring(0,36)+"...";
            b.append(String.format(Locale.CANADA,"%-39s %13.2f %13.2f %12.2f %13.2f %14.2f%n",key,a[0],a[1],a[2],a[3],a[4]));
        }
        breakdownArea.setText(b.toString());
    }
    private Node breakdownPane(){
        VBox pane=new VBox(12);pane.setPadding(new Insets(14));
        breakdownArea.setEditable(false);breakdownArea.setStyle("-fx-font-family:Consolas,monospace;-fx-font-size:12px;");
        breakdownArea.setPrefRowCount(25);
        pane.getChildren().addAll(sectionTitle("Phase / area / electrical system cost breakdown"),
            note("Live audit report for direct material and adjusted labour. Supplier quotes, indirects, risk allowance, overhead, profit and tax are shown separately in the bid recap."),breakdownArea,
            infoBox("Tender accuracy","Use drawing references and phase/area tags on each takeoff line to reconcile drawing counts, field measurements and the cost breakdown."));
        VBox.setVgrow(breakdownArea,Priority.ALWAYS);
        return pane;
    }

    private void updateValidation(){
        List<String> warnings=new ArrayList<>();
        if(projectName.getText().isBlank())warnings.add("Project name is missing");
        if(clientName.getText().isBlank())warnings.add("Client / GC is missing");
        if(bidNumber.getText().isBlank())warnings.add("Estimate number is missing");
        if(dueDate.getText().isBlank())warnings.add("Bid due date / time has not been entered");
        if(inclusions.getText().isBlank())warnings.add("Included scope is undocumented");
        if(exclusions.getText().isBlank())warnings.add("Exclusions / by-others not reviewed or documented");
        long unpriced=lines.stream().filter(x->x.quantity.get()>0&&x.materialUnit.get()<=0&&x.labourUnitHours.get()<=0).count();
        if(unpriced>0)warnings.add(unpriced+" takeoff line(s) have positive quantity but no material or labour pricing");
        long invalid=lines.stream().filter(x->x.quantity.get()>0&&x.productivityFactor.get()<=0).count();
        if(invalid>0)warnings.add(invalid+" takeoff line(s) have zero productivity factor");
        if(lines.stream().mapToDouble(x->x.materialExtended()+x.equipmentExtended()+x.adjustedHours()).sum()==0&&quotes.stream().noneMatch(q->q.include.get()))
            warnings.add("Bid has no priced direct electrical work or selected supplier quotes");
        Map<String,Integer> carries=new LinkedHashMap<>();
        for(QuoteLine q:quotes)if(q.include.get()){
            String g=q.group.get()==null?"":q.group.get().trim().toLowerCase(Locale.ROOT);
            if(!g.isBlank()&&!g.equals("general"))carries.merge(g,1,Integer::sum);
            if(q.vendor.get().isBlank())warnings.add("A carried quote has no supplier / subcontractor name");
        }
        carries.forEach((g,n)->{if(n>1)warnings.add(n+" carried quotes in group '"+g+"' — check for duplicate scope");});
        if(parse(labourRate.getText())<=0&&lines.stream().anyMatch(x->x.adjustedHours()>0))warnings.add("Loaded labour rate is zero");
        if("Target gross margin".equals(profitBasis.getValue())&&parse(profitPct.getText())>=100)warnings.add("Target gross margin must be less than 100%");
        long unknownSource=lines.stream().filter(x->x.quantity.get()>0&&x.materialUnit.get()>0&&x.priceSource.get().isBlank()).count();
        if(unknownSource>0)warnings.add(unknownSource+" priced material lines have no supplier / price source (verify quotes)");
        long checked=reviewChecks.stream().filter(CheckBox::isSelected).count();
        if(checked<reviewChecks.size())warnings.add("Bid-close checklist incomplete: "+checked+"/"+reviewChecks.size()+" checked");
        validationStatus.setText(warnings.isEmpty()?"No automated warnings — final professional review still required":
            warnings.size()+" items require manual review before submission");
        validationArea.setText(warnings.isEmpty()?"Automated audit passed. Verify tender documents, measurements, prices, taxes and authority to submit.":
            String.join("\n",warnings.stream().map(w->"• "+w).toList()));
    }


    // Starter kits are deliberately unpriced: labour units and vendor rates must be verified for each contractor and tender.
    private void seedKits(){
        kits.add(kit("Duplex receptacle device set",new EstimateLine("05 Devices","Duplex receptacle (specified grade)",1,"ea",0,0,1),
            new EstimateLine("05 Devices","Device box (specified type)",1,"ea",0,0,1),
            new EstimateLine("05 Devices","Faceplate (specified finish)",1,"ea",0,0,1)));
        kits.add(kit("GFCI receptacle device set",new EstimateLine("05 Devices","GFCI receptacle (specified rating)",1,"ea",0,0,1),
            new EstimateLine("05 Devices","GFCI device box",1,"ea",0,0,1),
            new EstimateLine("05 Devices","GFCI faceplate",1,"ea",0,0,1)));
        kits.add(kit("Single-pole wall switch set",new EstimateLine("05 Devices","Single-pole wall switch",1,"ea",0,0,1),
            new EstimateLine("05 Devices","Switch box",1,"ea",0,0,1),
            new EstimateLine("05 Devices","Wall plate",1,"ea",0,0,1)));
        kits.add(kit("LED luminaire point",new EstimateLine("04 Lighting / controls","Specified LED fixture",1,"ea",0,0,1),
            new EstimateLine("04 Lighting / controls","Fixture mounting / support allowance",1,"lot",0,0,1)));
        kits.add(kit("Manual EMT raceway length",new EstimateLine("03 Branch wiring","EMT conduit as specified",1,"m",0,0,1)));
        kits.add(kit("Manual conductor/cable length",new EstimateLine("02 Feeders","Cable or conductors per specification",1,"m",0,0,1)));
        kits.add(kit("Fire alarm detector point",new EstimateLine("07 Fire alarm / low voltage","Specified fire alarm detector",1,"ea",0,0,1),
            new EstimateLine("07 Fire alarm / low voltage","Compatible base / backbox",1,"ea",0,0,1)));
        kits.add(kit("Mechanical equipment disconnect",new EstimateLine("06 Mechanical power","Disconnect switch as specified",1,"ea",0,0,1),
            new EstimateLine("06 Mechanical power","Disconnect mounting / enclosure allowance",1,"lot",0,0,1)));
    }
    private static AssemblyKit kit(String name,EstimateLine...parts){return new AssemblyKit("starter-"+name,name,java.util.Arrays.asList(parts),true);}

    private void loadUserKits(){
        try{
            for(JsonNode node:workspace.records("estimatingCatalog")){
                List<EstimateLine> parts=new ArrayList<>();
                for(JsonNode part:node.path("parts"))parts.add(parseLine(part));
                if(!parts.isEmpty())kits.add(new AssemblyKit(node.path("id").asText(UUID.randomUUID().toString()),
                    node.path("name").asText("Custom assembly"),parts,false));
            }
        }catch(Exception ex){System.err.println("Could not load local estimating assembly library: "+ex.getMessage());}
    }
    private void saveSelectedKit(){
        if(takeoffTable==null||takeoffTable.getSelectionModel().getSelectedItems().isEmpty()){
            message("Select assembly components","Select one or more rows in the takeoff. Use Ctrl-click to select multiple parts.");return;
        }
        TextInputDialog dialog=new TextInputDialog("Custom electrical assembly");
        dialog.setTitle("Create reusable electrical assembly");
        dialog.setHeaderText("Save selected takeoff rows as an assembly");
        dialog.setContentText("Assembly name:");
        dialog.showAndWait().ifPresent(name->{
            if(name.isBlank())return;
            List<EstimateLine> parts=new ArrayList<>();for(EstimateLine x:takeoffTable.getSelectionModel().getSelectedItems())parts.add(x.copy());
            AssemblyKit k=new AssemblyKit(UUID.randomUUID().toString(),name.trim(),parts,false);
            ObjectNode o=mapper.createObjectNode();o.put("id",k.id);o.put("name",k.name);
            ArrayNode a=o.putArray("parts");for(EstimateLine x:k.parts)a.add(lineJson(x));
            try{workspace.append("estimatingCatalog",o);kits.add(k);message("Assembly saved",name+" was saved locally with "+parts.size()+" component rows.");}
            catch(Exception ex){error("Save assembly failed",ex);}
        });
    }
    private Node assembliesPane(){
        VBox page=new VBox(12);page.setPadding(new Insets(14));
        Label warning=note("Starter kits are unpriced examples, not certified assemblies. Enter supplier unit costs and verified labour hours in the takeoff. Confirm every component, quantity, fitting and code requirement against drawings/specs.");
        ListView<AssemblyKit> list=new ListView<>(kits);list.setPrefWidth(345);list.setPrefHeight(310);
        TextArea details=new TextArea();details.setEditable(false);details.setPrefWidth(540);
        details.setStyle("-fx-font-family:Consolas,monospace;-fx-font-size:12px;");details.setWrapText(true);
        list.getSelectionModel().selectedItemProperty().addListener((obs,a,k)->{
            if(k==null){details.clear();return;}
            StringBuilder b=new StringBuilder(k.name+"\n\nComponents per assembly:\n\n");
            for(EstimateLine part:k.parts)b.append("• ").append(part.description.get()).append(" — ")
                .append(part.quantity.get()).append(" ").append(part.unit.get())
                .append(" | Material/unit $").append(String.format(Locale.CANADA,"%.2f",part.materialUnit.get()))
                .append(" | Labour h/unit ").append(String.format(Locale.CANADA,"%.3f",part.labourUnitHours.get())).append('\n');
            b.append("\nInsert into takeoff, then review quantities, supplier prices and labour factors.");
            details.setText(b.toString());
        });
        HBox listing=new HBox(12,list,details);HBox.setHgrow(details,Priority.ALWAYS);
        TextField multiplier=numberField("1"),phase=field("Floor / phase"),drawing=field("Drawing revision / source");
        Button insert=button("Insert selected assembly into takeoff",true);
        insert.setOnAction(e->{
            AssemblyKit selected=list.getSelectionModel().getSelectedItem();if(selected==null){message("Select an assembly","Choose an assembly first.");return;}
            double qty=parse(multiplier.getText());if(qty<=0){message("Invalid quantity","Assembly count must be greater than zero.");return;}
            for(EstimateLine part:selected.parts){
                EstimateLine row=part.copy();row.quantity.set(row.quantity.get()*qty);
                if(!phase.getText().isBlank())row.phase.set(phase.getText());
                row.drawing.set((drawing.getText().isBlank()?"Assembly "+selected.name:drawing.getText()));
                lines.add(row);
            }
            recalc();message("Assembly added","Inserted "+selected.parts.size()+" component rows. Review labour, material, takeoff conditions and fittings.");
        });
        Button remove=button("Delete selected custom assembly",false);
        remove.setOnAction(e->{
            AssemblyKit k=list.getSelectionModel().getSelectedItem();
            if(k==null||k.starter){message("Starter assembly","Only user-created assemblies can be removed.");return;}
            if(!confirm("Delete custom assembly","Remove "+k.name+" from your local library? Existing takeoff lines will be unaffected."))return;
            try{
                ArrayNode records=workspace.records("estimatingCatalog"), next=mapper.createArrayNode();
                for(JsonNode n:records)if(!k.id.equals(n.path("id").asText()))next.add(n);
                workspace.replace("estimatingCatalog",next);kits.remove(k);
            }catch(Exception ex){error("Delete failed",ex);}
        });
        HBox form=new HBox(10,labeled("Assembly count",multiplier),labeled("Phase / area",phase),labeled("Drawing / source",drawing));
        page.getChildren().addAll(sectionTitle("Reusable electrical assemblies / item kits"),warning,listing,form,new HBox(8,insert,remove),
            infoBox("Create your company library","In the Takeoff tab, select one or more rows and click 'Save selection as assembly'. Their components, unit prices and labour units become a reusable local kit for future bids."));
        return page;
    }

    private Node changesPane(){
        VBox page=new VBox(12);page.setPadding(new Insets(14));
        TableView<ChangeLine> table=new TableView<>(changes);table.setEditable(true);table.setPrefHeight(315);
        TableColumn<ChangeLine,String> kind=new TableColumn<>("Record type");
        kind.setCellValueFactory(c->c.getValue().kind);
        kind.setCellFactory(TextFieldTableCell.forTableColumn());
        kind.setOnEditCommit(e->{e.getRowValue().kind.set(e.getNewValue());recalc();});kind.setPrefWidth(125);
        TableColumn<ChangeLine,String> desc=new TableColumn<>("Alternate / change description");
        desc.setCellValueFactory(c->c.getValue().description);desc.setCellFactory(TextFieldTableCell.forTableColumn());
        desc.setOnEditCommit(e->{e.getRowValue().description.set(e.getNewValue());recalc();});desc.setPrefWidth(360);
        TableColumn<ChangeLine,Double> value=new TableColumn<>("Selling $ before tax");
        value.setCellValueFactory(c->c.getValue().selling.asObject());value.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        value.setOnEditCommit(e->{e.getRowValue().selling.set(Math.max(0,e.getNewValue()));recalc();});value.setPrefWidth(155);
        TableColumn<ChangeLine,String> status=new TableColumn<>("Status");
        status.setCellValueFactory(c->c.getValue().status);status.setCellFactory(TextFieldTableCell.forTableColumn());
        status.setOnEditCommit(e->{e.getRowValue().status.set(e.getNewValue());recalc();});status.setPrefWidth(150);
        table.getColumns().addAll(kind,desc,value,status);
        Button addAlt=button("+ Alternate",true);addAlt.setOnAction(e->{changes.add(new ChangeLine("Alternate","Optional scope / alternate",0,"Proposed"));recalc();});
        Button addCo=button("+ Change Order",false);addCo.setOnAction(e->{changes.add(new ChangeLine("Change Order","Additional / revised scope",0,"Proposed"));recalc();});
        Button del=button("Remove selected",false);del.setOnAction(e->{ChangeLine x=table.getSelectionModel().getSelectedItem();if(x!=null){changes.remove(x);recalc();}});
        HBox totals=new HBox(10,stat("Alternates (separate)",alternateTotal),stat("Approved COs (separate)",approvedChangesTotal));
        page.getChildren().addAll(sectionTitle("Alternates and change orders — tracked separately"),
            note("These records are NOT automatically added to the original base bid. Prices are selling amounts before tax. A change order marked Approved is reported separately, not retroactively added to the original tender."),
            new HBox(8,addAlt,addCo,del),table,totals,
            infoBox("Commercial control","Use status Proposed, Submitted, Approved or Rejected. Verify scope, schedule, approval authority and executed documentation before treating a change as contract value."));
        return page;
    }
    private static final class ChangeLine{
        final StringProperty kind=new SimpleStringProperty();
        final StringProperty description=new SimpleStringProperty();
        final DoubleProperty selling=new SimpleDoubleProperty();
        final StringProperty status=new SimpleStringProperty();
        ChangeLine(String k,String d,double s,String st){kind.set(k);description.set(d);selling.set(s);status.set(st);}
    }
    private static final class AssemblyKit{
        final String id,name;final List<EstimateLine> parts;final boolean starter;
        AssemblyKit(String id,String name,List<EstimateLine> parts,boolean starter){this.id=id;this.name=name;this.parts=parts;this.starter=starter;}
        @Override public String toString(){return name+" ("+parts.size()+" parts)"+(starter?" · starter":" · saved");}
    }

    private static final class EstimateLine {
        final StringProperty system=new SimpleStringProperty(); final StringProperty phase=new SimpleStringProperty("General"); final StringProperty drawing=new SimpleStringProperty(""); final StringProperty priceSource=new SimpleStringProperty(""); final StringProperty priceDate=new SimpleStringProperty(""); final StringProperty description=new SimpleStringProperty(); final DoubleProperty quantity=new SimpleDoubleProperty(); final StringProperty unit=new SimpleStringProperty(); final DoubleProperty materialUnit=new SimpleDoubleProperty(); final DoubleProperty equipmentUnit=new SimpleDoubleProperty(); final DoubleProperty labourUnitHours=new SimpleDoubleProperty(); final DoubleProperty productivityFactor=new SimpleDoubleProperty();
        EstimateLine(String s,String d,double q,String u,double m,double h,double f){system.set(s);description.set(d);quantity.set(q);unit.set(u);materialUnit.set(m);labourUnitHours.set(h);productivityFactor.set(f);}
        double materialExtended(){return Math.max(0,quantity.get())*Math.max(0,materialUnit.get());}
        double equipmentExtended(){return Math.max(0,quantity.get())*Math.max(0,equipmentUnit.get());}
        double adjustedHours(){return Math.max(0,quantity.get())*Math.max(0,labourUnitHours.get())*Math.max(0,productivityFactor.get());}
        EstimateLine copy(){EstimateLine x=new EstimateLine(system.get(),description.get(),quantity.get(),unit.get(),materialUnit.get(),labourUnitHours.get(),productivityFactor.get());x.phase.set(phase.get());x.drawing.set(drawing.get());x.priceSource.set(priceSource.get());x.priceDate.set(priceDate.get());x.equipmentUnit.set(equipmentUnit.get());return x;}
    }
    private static final class QuoteLine {
        final BooleanProperty include=new SimpleBooleanProperty(); final StringProperty vendor=new SimpleStringProperty(); final StringProperty group=new SimpleStringProperty("General"); final StringProperty scope=new SimpleStringProperty(); final DoubleProperty amount=new SimpleDoubleProperty(); final StringProperty status=new SimpleStringProperty();
        QuoteLine(boolean i,String v,String s,double a,String st){include.set(i);vendor.set(v);scope.set(s);amount.set(a);status.set(st);}
    }
    private static final class IndirectLine {
        final StringProperty type=new SimpleStringProperty(); final StringProperty description=new SimpleStringProperty(); final DoubleProperty amount=new SimpleDoubleProperty();
        IndirectLine(String t,String d,double a){type.set(t);description.set(d);amount.set(a);}
    }
}

package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/** Searchable launcher, reusable field presets, favorites and unit/electrical conversions. */
public final class WorkbenchToolsPane extends BorderPane {
    private static final String INPUT="-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 9;";
    private static final String PRIMARY="-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-padding:9 13;";
    private static final String SECONDARY="-fx-background-color:#f8fafc;-fx-text-fill:#0f4c81;-fx-border-color:#c7d9e8;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 11;-fx-font-weight:bold;";
    private static final String CARD="-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#dfe7ef;-fx-border-radius:14;-fx-padding:16;";

    private final ObjectMapper json;
    private final LocalWorkspaceStore store;
    private final Consumer<String> openTool;
    private final ListView<Tool> toolResults = new ListView<>();
    private final ListView<String> favorites = new ListView<>();
    private final ListView<Preset> presets = new ListView<>();

    private record Tool(String tab,String name,String description) {
        @Override public String toString(){ return name+" — "+description; }
    }
    private record Preset(String name,String voltage,String phase,String material,String insulation,String ambient,String raceway,String notes) {
        @Override public String toString(){ return name+" · "+voltage+" V · "+phase+" · "+material+" · "+raceway; }
    }

    private final List<Tool> tools = List.of(
        new Tool("Integrated Design","Circuit Designer","conductor sizing, ampacity, derating, voltage drop, OCPD, bonding and raceway"),
        new Tool("Integrated Design","Panel Schedule","phase balancing, VA totals and panel current"),
        new Tool("Integrated Design","Transformer → Panel","transformer FLA, impedance fault current, OCPD and panel loading"),
        new Tool("Integrated Design","Motor Circuit","motor conductors, voltage drop, overload, fuse/breaker and disconnect screening"),
        new Tool("Integrated Design","Estimate","materials, labour, markup and tax"),
        new Tool("Raceway + Derating","Raceway + Derating","physical conduit fill plus current-carrying-conductor/ambient ampacity checks"),
        new Tool("Conduit & Box Fill","Conduit Fill","mixed conductor groups and automatic raceway fill limits"),
        new Tool("Conduit & Box Fill","Box Fill","mixed conductor sizes, devices, connectors and usable box volume"),
        new Tool("Ampacity / Derating","Ampacity Derating","Table 5A ambient and Table 5C current-carrying-conductor factors"),
        new Tool("Exam Simulator","Exam Simulator","timed 100, 50 and 25 question blueprint-balanced practice"),
        new Tool("Guided Worksheets","Guided Worksheets","worked C of Q-style calculations and specialty worksheets"),
        new Tool("Report Center","Report Center","assemble saved work and export a local PDF calculation report"),
        new Tool("CEC Sections / Advanced","CEC Sections","direct section-based calculator workspace")
    );

    public WorkbenchToolsPane(ObjectMapper json,String username,Consumer<String> openTool){
        this.json=json; this.store=new LocalWorkspaceStore(json,username); this.openTool=openTool==null?s->{}:openTool;
        VBox page=new VBox(13); page.setPadding(new Insets(2));
        Label title=new Label("Workbench Tools & Presets"); title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label sub=new Label("Search the calculator workspace, reopen favorites, save reusable field defaults, and perform common electrical/trade unit conversions without leaving the desktop app."); sub.setWrapText(true); sub.setStyle("-fx-text-fill:#64748b;");
        page.getChildren().addAll(title,sub);

        page.getChildren().add(searchCard());
        page.getChildren().add(presetCard());
        page.getChildren().add(conversionCard());
        ScrollPane scroll=new ScrollPane(page); scroll.setFitToWidth(true); scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;"); setCenter(scroll);
    }

    private VBox searchCard(){
        TextField search=field(""); search.setPromptText("Search: motor, transformer, conduit, box fill, voltage drop, exam, report...");
        toolResults.setPrefHeight(190); favorites.setPrefHeight(120);
        Button open=primary("Open selected tool"),openFavorite=secondary("Open favorite"),refresh=secondary("Refresh favorites");
        Runnable render=()->{String q=search.getText().trim().toLowerCase(Locale.ROOT);toolResults.getItems().clear();for(Tool t:tools)if(q.isEmpty()||(t.name()+" "+t.description()+" "+t.tab()).toLowerCase(Locale.ROOT).contains(q))toolResults.getItems().add(t);if(!toolResults.getItems().isEmpty()&&toolResults.getSelectionModel().getSelectedItem()==null)toolResults.getSelectionModel().select(0);};
        Runnable fav=()->{favorites.getItems().clear();ArrayNode arr=store.records("calculatorFavorites");LinkedHashSet<String> seen=new LinkedHashSet<>();for(int i=arr.size()-1;i>=0;i--){String t=arr.get(i).path("tool").asText("");if(!t.isBlank())seen.add(t);}favorites.getItems().addAll(seen);};
        search.textProperty().addListener((o,a,b)->render.run()); render.run(); fav.run();
        open.setOnAction(e->{Tool t=toolResults.getSelectionModel().getSelectedItem();if(t!=null)openTool.accept(t.tab());});
        toolResults.setOnMouseClicked(e->{if(e.getClickCount()==2){Tool t=toolResults.getSelectionModel().getSelectedItem();if(t!=null)openTool.accept(t.tab());}});
        openFavorite.setOnAction(e->{String name=favorites.getSelectionModel().getSelectedItem();if(name==null)return;Tool exact=tools.stream().filter(t->t.name().equals(name)||t.tab().equals(name)).findFirst().orElse(null);if(exact!=null)openTool.accept(exact.tab());else openTool.accept(name);});
        refresh.setOnAction(e->fav.run());
        VBox c=card("1. Search & favorites","Searches the major native Workbench tools. Double-click a result or select it and choose Open. Favorites saved from Integrated Design appear below.");
        c.getChildren().addAll(search,toolResults,new HBox(8,open),new Label("Favorites"),favorites,new HBox(8,openFavorite,refresh));return c;
    }

    private VBox presetCard(){
        TextField name=field("600 V industrial feeder"),voltage=field("600"),insulation=field("90"),ambient=field("30");
        ChoiceBox<String> phase=choice("3φ","1φ","3φ"),material=choice("Copper","Copper","Aluminum"),raceway=choice("EMT","EMT","Rigid PVC","TECK90 / cable","Custom");
        TextArea notes=new TextArea("75°C terminations; verify CCC count and voltage-drop length for each run."); notes.setPrefRowCount(3); notes.setWrapText(true);
        Button save=primary("Save preset"),load=secondary("Load selected"),delete=secondary("Delete selected"),copy=secondary("Copy preset"); Label status=new Label(); status.setWrapText(true);
        presets.setPrefHeight(150);
        Runnable refresh=()->{presets.getItems().clear();ArrayNode arr=store.records("customPresets");for(int i=arr.size()-1;i>=0;i--){JsonNode n=arr.get(i);presets.getItems().add(new Preset(n.path("name").asText("Preset"),n.path("voltage").asText(""),n.path("phase").asText(""),n.path("material").asText(""),n.path("insulation").asText(""),n.path("ambient").asText(""),n.path("raceway").asText(""),n.path("notes").asText("")));}};refresh.run();
        save.setOnAction(e->{ObjectNode n=json.createObjectNode();n.put("saved",LocalDateTime.now().toString());n.put("name",name.getText().trim());n.put("voltage",voltage.getText().trim());n.put("phase",phase.getValue());n.put("material",material.getValue());n.put("insulation",insulation.getText().trim());n.put("ambient",ambient.getText().trim());n.put("raceway",raceway.getValue());n.put("notes",notes.getText());store.append("customPresets",n);refresh.run();status.setText("Preset saved locally.");});
        load.setOnAction(e->{Preset p=presets.getSelectionModel().getSelectedItem();if(p==null)return;name.setText(p.name());voltage.setText(p.voltage());phase.setValue(p.phase());material.setValue(p.material());insulation.setText(p.insulation());ambient.setText(p.ambient());raceway.setValue(p.raceway());notes.setText(p.notes());status.setText("Preset loaded. Use it as a reusable installation reference while entering the calculator.");});
        delete.setOnAction(e->{int selected=presets.getSelectionModel().getSelectedIndex();if(selected<0)return;ArrayNode arr=store.records("customPresets");int actual=arr.size()-1-selected;if(actual>=0&&actual<arr.size())arr.remove(actual);store.replace("customPresets",arr);refresh.run();status.setText("Preset deleted.");});
        copy.setOnAction(e->{Preset p=presets.getSelectionModel().getSelectedItem();if(p==null)return;String text=String.format(Locale.CANADA,"%s%nVoltage: %s V%nPhase: %s%nMaterial: %s%nInsulation: %s°C%nAmbient: %s°C%nRaceway: %s%nNotes: %s",p.name(),p.voltage(),p.phase(),p.material(),p.insulation(),p.ambient(),p.raceway(),p.notes());javafx.scene.input.ClipboardContent cc=new javafx.scene.input.ClipboardContent();cc.putString(text);javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);status.setText("Preset copied.");});
        VBox c=card("2. Reusable field presets","Save common installation defaults such as 600 V industrial, mine, commercial or residential starting conditions. Presets are local per user and remain editable before use.");
        GridPane g=grid(row("Preset name",name),row("Voltage",voltage),row("Phase",phase),row("Material",material),row("Insulation °C",insulation),row("Ambient °C",ambient),row("Raceway / wiring method",raceway),row("Notes",notes));
        c.getChildren().addAll(g,new HBox(8,save,load,delete,copy),status,presets); return c;
    }

    private VBox conversionCard(){
        ChoiceBox<String> type=choice("Length: m → ft",
            "Length: m → ft","Length: ft → m","Temperature: °C → °F","Temperature: °F → °C",
            "Conductor area: mm² → kcmil","Conductor area: kcmil → mm²","Power: kW → hp","Power: hp → kW",
            "Pressure: psi → kPa","Pressure: kPa → psi","Torque: N·m → lb-ft","Torque: lb-ft → N·m");
        TextField value=field("10"); Label result=new Label("32.8084 ft"); result.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#0e7490;");
        Button convert=primary("Convert");
        Runnable doConvert=()->{try{double x=Double.parseDouble(value.getText().trim());String t=type.getValue();double y;String unit;if(t.equals("Length: m → ft")){y=x*3.280839895;unit="ft";}else if(t.equals("Length: ft → m")){y=x/3.280839895;unit="m";}else if(t.equals("Temperature: °C → °F")){y=x*9/5+32;unit="°F";}else if(t.equals("Temperature: °F → °C")){y=(x-32)*5/9;unit="°C";}else if(t.equals("Conductor area: mm² → kcmil")){y=x/0.506707479;unit="kcmil";}else if(t.equals("Conductor area: kcmil → mm²")){y=x*0.506707479;unit="mm²";}else if(t.equals("Power: kW → hp")){y=x/0.746;unit="hp";}else if(t.equals("Power: hp → kW")){y=x*0.746;unit="kW";}else if(t.equals("Pressure: psi → kPa")){y=x*6.894757293;unit="kPa";}else if(t.equals("Pressure: kPa → psi")){y=x/6.894757293;unit="psi";}else if(t.equals("Torque: N·m → lb-ft")){y=x*0.737562149;unit="lb-ft";}else{y=x/0.737562149;unit="N·m";}result.setText(String.format(Locale.CANADA,"%.6g %s",y,unit));}catch(Exception ex){result.setText("Enter a valid number");}};
        convert.setOnAction(e->doConvert.run()); type.setOnAction(e->doConvert.run()); value.setOnAction(e->doConvert.run()); doConvert.run();

        TextField kva=field("75"),voltage=field("600"),pf=field("0.90"),kw=field("50"); ChoiceBox<String> phase=choice("3φ","1φ","3φ");
        Label kvaCurrent=new Label(),kwCurrent=new Label(); kvaCurrent.setStyle("-fx-font-weight:bold;-fx-text-fill:#0e7490;"); kwCurrent.setStyle("-fx-font-weight:bold;-fx-text-fill:#0e7490;");
        Button electrical=secondary("Calculate electrical conversions");
        electrical.setOnAction(e->{try{double v=positive(voltage,"Voltage"),k=positive(kva,"kVA"),p=positive(pf,"Power factor"),w=positive(kw,"kW"),factor=phase.getValue().equals("3φ")?Math.sqrt(3):1;kvaCurrent.setText(String.format(Locale.CANADA,"%.2f A from %.2f kVA",k*1000/(factor*v),k));kwCurrent.setText(String.format(Locale.CANADA,"%.2f A from %.2f kW at PF %.3f",w*1000/(factor*v*p),w,p));}catch(Exception ex){kvaCurrent.setText(ex.getMessage());kwCurrent.setText("");}});
        electrical.fire();
        VBox c=card("3. Unit & electrical conversions","Common field conversions plus quick single-/three-phase current from kVA or kW. These conversions do not replace conductor/OCPD sizing rules.");
        c.getChildren().addAll(row("Conversion",type),row("Value",value),new HBox(10,convert,result),new Separator(),grid(row("System voltage",voltage),row("Phase",phase),row("kVA",kva),row("kW",kw),row("Power factor",pf)),electrical,kvaCurrent,kwCurrent); return c;
    }

    private VBox card(String title,String desc){VBox b=new VBox(10);b.setStyle(CARD);Label h=new Label(title);h.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");Label d=new Label(desc);d.setWrapText(true);d.setStyle("-fx-text-fill:#64748b;");b.getChildren().addAll(h,d);return b;}
    private GridPane grid(javafx.scene.Node... n){GridPane g=new GridPane();g.setHgap(10);g.setVgap(8);int i=0;for(javafx.scene.Node x:n){g.add(x,0,i++);GridPane.setHgrow(x,Priority.ALWAYS);}return g;}
    private HBox row(String label,Control c){Label l=new Label(label);l.setMinWidth(230);l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;");HBox h=new HBox(10,l,c);h.setAlignment(Pos.CENTER_LEFT);HBox.setHgrow(c,Priority.ALWAYS);c.setMaxWidth(Double.MAX_VALUE);return h;}
    private static TextField field(String s){TextField f=new TextField(s);f.setStyle(INPUT);return f;}
    @SafeVarargs private final <T> ChoiceBox<T> choice(T selected,T... values){ChoiceBox<T> c=new ChoiceBox<>();c.getItems().addAll(values);c.setValue(selected);c.setMaxWidth(Double.MAX_VALUE);return c;}
    private static Button primary(String s){Button b=new Button(s);b.setStyle(PRIMARY);return b;}
    private static Button secondary(String s){Button b=new Button(s);b.setStyle(SECONDARY);return b;}
    private static double positive(TextField f,String name){try{double v=Double.parseDouble(f.getText().trim());if(!Double.isFinite(v)||v<=0)throw new Exception();return v;}catch(Exception e){throw new IllegalArgumentException(name+" must be greater than zero.");}}
}

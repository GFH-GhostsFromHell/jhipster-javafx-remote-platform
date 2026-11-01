package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class FeatureViews {
    private static final ObjectMapper JSON = new ObjectMapper();
    private FeatureViews() {}

    public static Node create(String featureKey, JsonNode bootstrap, PortalClient portal, Stage stage, String username, String password) {
        return switch (featureKey) {
            case "ELECTRICAL_CALCULATORS" -> calculators(portal, username, password);
            case "RESIDENTIAL_REFERENCE" -> residentialReference();
            case "DASHBOARD" -> dashboard(bootstrap, portal);
            case "REPORTS" -> reports(bootstrap, stage);
            case "PLC_CONNECT" -> plcConnect();
            case "REMOTE_DIAGNOSTICS" -> diagnostics(portal);
            case "ADVANCED_ANALYTICS" -> analytics(bootstrap);
            case "ENGINEERING_MODE" -> engineering(bootstrap);
            default -> placeholder(featureKey);
        };
    }

    private static Node calculators(PortalClient portal, String username, String password) {
        VBox box = page("Electrical Calculators", "CEC-oriented calculator engine. Code-edition data is kept on the backend so it can be updated centrally.");

        Label edition = new Label("Code edition: CEC 2024 · backend data revision starter-1");
        edition.setStyle("-fx-font-weight:bold;-fx-text-fill:#1d4ed8;");

        TitledPane transformerPane = new TitledPane();
        transformerPane.setText("Transformer current & starter conductor sizing");
        transformerPane.setExpanded(true);

        GridPane form = new GridPane(); form.setHgap(10); form.setVgap(10); form.setPadding(new Insets(12));
        TextField kva = new TextField("75");
        TextField primary = new TextField("600");
        TextField secondary = new TextField("208");
        ComboBox<Integer> phases = new ComboBox<>(); phases.getItems().addAll(1,3); phases.setValue(3);
        TextField factor = new TextField("1.25");
        form.addRow(0,new Label("Transformer kVA"),kva);
        form.addRow(1,new Label("Primary voltage"),primary);
        form.addRow(2,new Label("Secondary voltage"),secondary);
        form.addRow(3,new Label("Phases"),phases);
        form.addRow(4,new Label("Design multiplier"),factor);

        Button calculate = new Button("Calculate transformer");
        calculate.setStyle("-fx-background-color:#1d4ed8;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;-fx-padding:10 16;");
        TextArea result = new TextArea(); result.setEditable(false); result.setPrefRowCount(14); result.setWrapText(true);
        Label status = new Label(); status.setWrapText(true);

        calculate.setOnAction(e -> {
            try {
                double k = Double.parseDouble(kva.getText().trim());
                double p = Double.parseDouble(primary.getText().trim());
                double s = Double.parseDouble(secondary.getText().trim());
                double f = Double.parseDouble(factor.getText().trim());
                int ph = phases.getValue();
                String body = "{\"kva\":"+k+",\"primaryVoltage\":"+p+",\"secondaryVoltage\":"+s+",\"phases\":"+ph+",\"designFactor\":"+f+",\"edition\":\"CEC_2024\"}";
                calculate.setDisable(true); status.setText("Calculating on portal…");
                CompletableFuture.supplyAsync(() -> {
                    try { return portal.transformerCalculator(username,password,body); }
                    catch (Exception ex) { throw new RuntimeException(ex); }
                }).whenComplete((json,err) -> Platform.runLater(() -> {
                    calculate.setDisable(false);
                    if (err != null) {
                        Throwable c=err.getCause()==null?err:err.getCause();
                        status.setText("Calculation failed: "+c.getMessage()); return;
                    }
                    try {
                        JsonNode r=JSON.readTree(json);
                        String text = "Formula: " + r.path("formula").asText() + "\n"
                            + "Primary current: " + r.path("primaryCurrentA").asDouble() + " A\n"
                            + "Secondary current: " + r.path("secondaryCurrentA").asDouble() + " A\n\n"
                            + "With design multiplier " + r.path("designFactor").asDouble() + ":\n"
                            + "Required primary ampacity: " + r.path("requiredPrimaryAmpacityA").asDouble() + " A\n"
                            + "Starter primary conductor: " + r.path("primaryConductorStarter").path("size").asText() + " (" + r.path("primaryConductorStarter").path("ampacityA").asDouble() + " A)\n"
                            + "Required secondary ampacity: " + r.path("requiredSecondaryAmpacityA").asDouble() + " A\n"
                            + "Starter secondary conductor: " + r.path("secondaryConductorStarter").path("size").asText() + " (" + r.path("secondaryConductorStarter").path("ampacityA").asDouble() + " A)\n\n"
                            + "Reference: " + r.path("reference").asText() + "\n\n"
                            + r.path("note").asText();
                        result.setText(text); status.setText("Calculated using backend CEC_2024 dataset.");
                    } catch(Exception ex) { status.setText("Could not read calculator response: "+ex.getMessage()); }
                }));
            } catch (Exception ex) { status.setText("Enter valid numeric values."); }
        });
        VBox transformerContent = new VBox(10, form, calculate, status, result); transformerContent.setPadding(new Insets(6));
        transformerPane.setContent(transformerContent);

        Label catalogTitle = new Label("Calculator roadmap / tier access");
        catalogTitle.setStyle("-fx-font-size:16px;-fx-font-weight:bold;");
        VBox catalog = new VBox(7, new Label("Loading calculator catalog…"));
        CompletableFuture.supplyAsync(() -> {
            try { return portal.calculatorCatalog(username,password); }
            catch(Exception ex) { return null; }
        }).thenAccept(body -> Platform.runLater(() -> {
            catalog.getChildren().clear();
            if(body==null){catalog.getChildren().add(new Label("Could not load tier catalog."));return;}
            try {
                JsonNode root=JSON.readTree(body);
                for(JsonNode c:root.path("calculators")){
                    boolean unlocked=c.path("unlocked").asBoolean(false);
                    Label row=new Label((unlocked?"✓  ":"🔒  ")+c.path("name").asText()+" — "+c.path("description").asText()+"  ["+c.path("minimumTier").asText()+"]");
                    row.setWrapText(true); row.setStyle(unlocked?"-fx-text-fill:#166534;":"-fx-text-fill:#64748b;");
                    catalog.getChildren().add(row);
                }
            }catch(Exception ex){catalog.getChildren().add(new Label("Could not parse calculator catalog."));}
        }));

        box.getChildren().addAll(edition, transformerPane, catalogTitle, catalog);
        return box;
    }

    private static Node residentialReference() {
        VBox box = page("Residential CEC Reference", "A plain-language residential reference hub. It will cite CEC rule/table numbers and use our own explanations rather than reproducing the code book.");
        String[] groups = {
            "Services & panels — service calculations, feeders, grounding/bonding, masts and clearances",
            "Receptacles — room spacing, hallways, kitchens, bathrooms, outdoors, GFCI/AFCI and tamper-resistant",
            "Lighting — required outlets, switches, stairways, exterior lighting and 3-way/4-way control",
            "Special equipment — ranges, dryers, heating, HVAC, EV charging, pools/hot tubs and garages",
            "Wiring methods — NMD90, attic wiring, conduit, support, box sizing, ampacity and voltage drop"
        };
        for(String g:groups){Label l=new Label("•  "+g);l.setWrapText(true);l.setStyle("-fx-font-size:14px;-fx-padding:7 0;");box.getChildren().add(l);}
        Label note=new Label("Starter reference shell: detailed CEC 2024 rule links, diagrams and residential calculators will be populated next."); note.setWrapText(true); note.setStyle("-fx-text-fill:#64748b;"); box.getChildren().add(note);
        return box;
    }

    private static Node dashboard(JsonNode root, PortalClient portal) {
        VBox box = page("Dashboard", "Live account and cloud overview.");
        JsonNode user = root.path("user");
        GridPane cards = new GridPane(); cards.setHgap(14); cards.setVgap(14);
        cards.add(card("User", user.path("displayName").asText(user.path("username").asText("User"))), 0, 0);
        cards.add(card("Subscription", user.path("subscriptionTier").asText("TIER_1").replace('_', ' ')), 1, 0);
        int enabled = 0; Iterator<Map.Entry<String, JsonNode>> it = root.path("features").fields(); while (it.hasNext()) if (it.next().getValue().asBoolean(false)) enabled++;
        cards.add(card("Enabled features", String.valueOf(enabled)), 0, 1); cards.add(card("Portal", portal.baseUrl()), 1, 1); box.getChildren().add(cards); return box;
    }

    private static Node reports(JsonNode root, Stage stage) {
        VBox box = page("Reports", "Export your current account, entitlement and configuration snapshot.");
        TextArea preview = new TextArea(root.toPrettyString()); preview.setEditable(false); preview.setPrefRowCount(18);
        Button export = new Button("Export JSON report");
        export.setOnAction(e -> { FileChooser fc = new FileChooser(); fc.setInitialFileName("remote-javafx-report.json"); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files", "*.json")); File f = fc.showSaveDialog(stage); if (f != null) { try { Files.writeString(f.toPath(), root.toPrettyString()); new Alert(Alert.AlertType.INFORMATION, "Report exported to:\n" + f.getAbsolutePath()).showAndWait(); } catch (Exception ex) { new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait(); } } });
        box.getChildren().addAll(export, preview); return box;
    }

    private static Node plcConnect() {
        VBox box = page("PLC Connect", "Test TCP reachability to a PLC, HMI, gateway or industrial device before adding protocol-specific drivers.");
        TextField host = new TextField(); host.setPromptText("Device IP or hostname, e.g. 192.168.1.10"); TextField port = new TextField("502"); Label status = new Label(); Button test = new Button("Test connection");
        test.setOnAction(e -> { test.setDisable(true); status.setText("Connecting…"); CompletableFuture.supplyAsync(() -> { long start = System.nanoTime(); try (Socket socket = new Socket()) { socket.connect(new InetSocketAddress(host.getText().trim(), Integer.parseInt(port.getText().trim())), 3000); long ms = Duration.ofNanos(System.nanoTime() - start).toMillis(); return "Connected successfully in " + ms + " ms"; } catch (Exception ex) { return "Connection failed: " + ex.getMessage(); } }).thenAccept(msg -> Platform.runLater(() -> { status.setText(msg); test.setDisable(false); })); });
        GridPane form = new GridPane(); form.setHgap(10); form.setVgap(10); form.add(new Label("Host"),0,0); form.add(host,1,0); form.add(new Label("Port"),0,1); form.add(port,1,1); box.getChildren().addAll(form, test, status); return box;
    }

    private static Node diagnostics(PortalClient portal) {
        VBox box = page("Remote Diagnostics", "Check cloud availability and response latency from this computer."); Label result = new Label("Not tested yet."); result.setWrapText(true); Button run = new Button("Run diagnostics");
        run.setOnAction(e -> { run.setDisable(true); result.setText("Running cloud health check…"); CompletableFuture.supplyAsync(() -> { Instant start = Instant.now(); try { boolean ok = portal.health(); long ms = Duration.between(start, Instant.now()).toMillis(); return "Cloud health: " + (ok ? "OK" : "FAILED") + "\nLatency: " + ms + " ms\nPortal: " + portal.baseUrl(); } catch (Exception ex) { return "Diagnostic failed: " + ex.getMessage(); } }).thenAccept(msg -> Platform.runLater(() -> { result.setText(msg); run.setDisable(false); })); }); box.getChildren().addAll(run, result); return box;
    }

    private static Node analytics(JsonNode root) {
        VBox box = page("Advanced Analytics", "A simple entitlement/configuration summary."); int enabled = 0, disabled = 0; Iterator<Map.Entry<String, JsonNode>> it = root.path("features").fields(); while (it.hasNext()) { if (it.next().getValue().asBoolean(false)) enabled++; else disabled++; } int configCount = root.path("configuration").isObject() ? root.path("configuration").size() : 0; HBox cards = new HBox(14, card("Enabled", String.valueOf(enabled)), card("Disabled", String.valueOf(disabled)), card("Config values", String.valueOf(configCount))); box.getChildren().add(cards); return box;
    }

    private static Node engineering(JsonNode root) { VBox box = page("Engineering Mode", "Inspect the effective configuration returned by the cloud portal."); TextArea raw = new TextArea(root.path("configuration").toPrettyString()); raw.setEditable(false); raw.setPrefRowCount(20); box.getChildren().add(raw); return box; }
    private static Node placeholder(String key) { return page(pretty(key), "This module is enabled but does not have a dedicated screen yet."); }
    private static VBox page(String title, String subtitle) { VBox box = new VBox(14); box.setPadding(new Insets(22)); Label h = new Label(title); h.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#14213d;"); Label s = new Label(subtitle); s.setWrapText(true); s.setStyle("-fx-text-fill:#64748b;"); box.getChildren().addAll(h, s, new Separator()); return box; }
    private static VBox card(String title, String value) { VBox c = new VBox(6); c.setPadding(new Insets(16)); c.setPrefWidth(260); c.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:12;-fx-background-radius:12;"); Label t = new Label(title); t.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;"); Label v = new Label(value); v.setWrapText(true); v.setStyle("-fx-text-fill:#14213d;-fx-font-size:18px;-fx-font-weight:bold;"); c.getChildren().addAll(t, v); return c; }
    private static String pretty(String key) { String[] parts = key.toLowerCase().split("_"); StringBuilder out = new StringBuilder(); for (String p : parts) if (!p.isBlank()) out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' '); return out.toString().trim(); }
}

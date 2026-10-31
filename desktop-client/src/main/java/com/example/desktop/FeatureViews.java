package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
    private FeatureViews() {}

    public static Node create(String featureKey, JsonNode bootstrap, PortalClient portal, Stage stage) {
        return switch (featureKey) {
            case "DASHBOARD" -> dashboard(bootstrap, portal);
            case "REPORTS" -> reports(bootstrap, stage);
            case "PLC_CONNECT" -> plcConnect();
            case "REMOTE_DIAGNOSTICS" -> diagnostics(portal);
            case "ADVANCED_ANALYTICS" -> analytics(bootstrap);
            case "ENGINEERING_MODE" -> engineering(bootstrap);
            default -> placeholder(featureKey);
        };
    }

    private static Node dashboard(JsonNode root, PortalClient portal) {
        VBox box = page("Dashboard", "Live account and cloud overview.");
        JsonNode user = root.path("user");
        GridPane cards = new GridPane();
        cards.setHgap(14); cards.setVgap(14);
        cards.add(card("User", user.path("displayName").asText(user.path("username").asText("User"))), 0, 0);
        cards.add(card("Subscription", user.path("subscriptionTier").asText("TIER_1").replace('_', ' ')), 1, 0);
        int enabled = 0; Iterator<Map.Entry<String, JsonNode>> it = root.path("features").fields();
        while (it.hasNext()) if (it.next().getValue().asBoolean(false)) enabled++;
        cards.add(card("Enabled features", String.valueOf(enabled)), 0, 1);
        cards.add(card("Portal", portal.baseUrl()), 1, 1);
        box.getChildren().add(cards);
        return box;
    }

    private static Node reports(JsonNode root, Stage stage) {
        VBox box = page("Reports", "Export your current account, entitlement and configuration snapshot.");
        TextArea preview = new TextArea(root.toPrettyString());
        preview.setEditable(false); preview.setPrefRowCount(18);
        Button export = new Button("Export JSON report");
        export.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setInitialFileName("remote-javafx-report.json");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files", "*.json"));
            File f = fc.showSaveDialog(stage);
            if (f != null) {
                try {
                    Files.writeString(f.toPath(), root.toPrettyString());
                    new Alert(Alert.AlertType.INFORMATION, "Report exported to:\n" + f.getAbsolutePath()).showAndWait();
                } catch (Exception ex) {
                    new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                }
            }
        });
        box.getChildren().addAll(export, preview);
        return box;
    }

    private static Node plcConnect() {
        VBox box = page("PLC Connect", "Test TCP reachability to a PLC, HMI, gateway or industrial device before adding protocol-specific drivers.");
        TextField host = new TextField(); host.setPromptText("Device IP or hostname, e.g. 192.168.1.10");
        TextField port = new TextField("502");
        Label status = new Label();
        Button test = new Button("Test connection");
        test.setOnAction(e -> {
            test.setDisable(true); status.setText("Connecting…");
            CompletableFuture.supplyAsync(() -> {
                long start = System.nanoTime();
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host.getText().trim(), Integer.parseInt(port.getText().trim())), 3000);
                    long ms = Duration.ofNanos(System.nanoTime() - start).toMillis();
                    return "Connected successfully in " + ms + " ms";
                } catch (Exception ex) {
                    return "Connection failed: " + ex.getMessage();
                }
            }).thenAccept(msg -> Platform.runLater(() -> { status.setText(msg); test.setDisable(false); }));
        });
        GridPane form = new GridPane(); form.setHgap(10); form.setVgap(10);
        form.add(new Label("Host"),0,0); form.add(host,1,0); form.add(new Label("Port"),0,1); form.add(port,1,1);
        box.getChildren().addAll(form, test, status);
        return box;
    }

    private static Node diagnostics(PortalClient portal) {
        VBox box = page("Remote Diagnostics", "Check cloud availability and response latency from this computer.");
        Label result = new Label("Not tested yet."); result.setWrapText(true);
        Button run = new Button("Run diagnostics");
        run.setOnAction(e -> {
            run.setDisable(true); result.setText("Running cloud health check…");
            CompletableFuture.supplyAsync(() -> {
                Instant start = Instant.now();
                try {
                    boolean ok = portal.health();
                    long ms = Duration.between(start, Instant.now()).toMillis();
                    return "Cloud health: " + (ok ? "OK" : "FAILED") + "\nLatency: " + ms + " ms\nPortal: " + portal.baseUrl();
                } catch (Exception ex) {
                    return "Diagnostic failed: " + ex.getMessage();
                }
            }).thenAccept(msg -> Platform.runLater(() -> { result.setText(msg); run.setDisable(false); }));
        });
        box.getChildren().addAll(run, result);
        return box;
    }

    private static Node analytics(JsonNode root) {
        VBox box = page("Advanced Analytics", "A simple entitlement/configuration summary. This can later grow into real usage and device analytics.");
        int enabled = 0, disabled = 0;
        Iterator<Map.Entry<String, JsonNode>> it = root.path("features").fields();
        while (it.hasNext()) { if (it.next().getValue().asBoolean(false)) enabled++; else disabled++; }
        int configCount = root.path("configuration").isObject() ? root.path("configuration").size() : 0;
        HBox cards = new HBox(14, card("Enabled", String.valueOf(enabled)), card("Disabled", String.valueOf(disabled)), card("Config values", String.valueOf(configCount)));
        box.getChildren().add(cards);
        return box;
    }

    private static Node engineering(JsonNode root) {
        VBox box = page("Engineering Mode", "Inspect the effective configuration returned by the cloud portal.");
        TextArea raw = new TextArea(root.path("configuration").toPrettyString());
        raw.setEditable(false); raw.setPrefRowCount(20);
        box.getChildren().add(raw);
        return box;
    }

    private static Node placeholder(String key) {
        VBox box = page(pretty(key), "This module is enabled but does not have a dedicated screen yet.");
        return box;
    }

    private static VBox page(String title, String subtitle) {
        VBox box = new VBox(14); box.setPadding(new Insets(22));
        Label h = new Label(title); h.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#14213d;");
        Label s = new Label(subtitle); s.setWrapText(true); s.setStyle("-fx-text-fill:#64748b;");
        box.getChildren().addAll(h, s, new Separator());
        return box;
    }

    private static VBox card(String title, String value) {
        VBox c = new VBox(6); c.setPadding(new Insets(16)); c.setPrefWidth(260);
        c.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:12;-fx-background-radius:12;");
        Label t = new Label(title); t.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");
        Label v = new Label(value); v.setWrapText(true); v.setStyle("-fx-text-fill:#14213d;-fx-font-size:18px;-fx-font-weight:bold;");
        c.getChildren().addAll(t, v); return c;
    }

    private static String pretty(String key) {
        String[] parts = key.toLowerCase().split("_"); StringBuilder out = new StringBuilder();
        for (String p : parts) if (!p.isBlank()) out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        return out.toString().trim();
    }
}

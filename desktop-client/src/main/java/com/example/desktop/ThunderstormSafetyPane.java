package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.util.Duration;

import java.awt.Desktop;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Native JavaFX thunderstorm / lightning safety aid for electrical and substation work. */
public final class ThunderstormSafetyPane extends BorderPane {
    private static final String CARD = "-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#dfe7ef;-fx-border-radius:14;-fx-padding:16;";
    private static final String INPUT = "-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-border-radius:9;-fx-background-radius:9;-fx-padding:9 10;";
    private static final String PRIMARY = "-fx-background-color:#b45309;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;-fx-padding:10 14;";
    private static final String DANGER = "-fx-background-color:#b91c1c;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;-fx-padding:10 14;";
    private static final String ECCC_LIGHTNING_MAP = "https://weather.gc.ca/?layers=lightning";
    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    private final ObjectMapper json;
    private final LocalWorkspaceStore store;
    private Instant clearAfter;
    private final Timeline ticker;

    public ThunderstormSafetyPane(ObjectMapper json, String username) {
        this.json = json;
        this.store = new LocalWorkspaceStore(json, username);

        VBox root = new VBox(12);
        root.setPadding(new Insets(2));

        Label title = new Label("Thunderstorm / Lightning Safety");
        title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label lead = new Label("Live ECCC lightning context, Hydro One public 30–30 guidance, IHSA Electrical Utility Safety Rules Rule 108, and a local stand-down record for substation / utility work.");
        lead.setWrapText(true); lead.setStyle("-fx-text-fill:#64748b;");
        root.getChildren().addAll(title, lead);

        TextField site = field(""); site.setPromptText("Substation / project name");
        TextField latitude = field(""); latitude.setPromptText("e.g. 44.25");
        TextField longitude = field(""); longitude.setPromptText("e.g. -79.50");
        TextField procedure = field("Employer / site lightning procedure");
        TextField notes = field("");

        Label standDownState = new Label("No stand-down timer is active.");
        standDownState.setWrapText(true);
        standDownState.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:#166534;-fx-background-color:#dcfce7;-fx-background-radius:10;-fx-padding:12;");

        Runnable updateTimer = () -> {
            if (clearAfter == null) return;
            long seconds = Instant.now().until(clearAfter, ChronoUnit.SECONDS);
            if (seconds > 0) {
                long min = seconds / 60, sec = seconds % 60;
                standDownState.setText(String.format(Locale.CANADA, "STAND DOWN — public 30-minute shelter window: %02d:%02d remaining. A new thunder/lightning observation resets the clock.", min, sec));
                standDownState.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:white;-fx-background-color:#b91c1c;-fx-background-radius:10;-fx-padding:12;");
            } else {
                standDownState.setText("30 minutes have elapsed since the last recorded thunder/lightning event. DO NOT automatically restart work. Resume only when Rule 108, the employer/site procedure and the responsible supervisor/work authority permit it and hazards are controlled.");
                standDownState.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#92400e;-fx-background-color:#fef3c7;-fx-background-radius:10;-fx-padding:12;");
            }
        };
        ticker = new Timeline(new KeyFrame(Duration.ZERO, e -> updateTimer.run()), new KeyFrame(Duration.seconds(1), e -> updateTimer.run()));
        ticker.setCycleCount(Timeline.INDEFINITE);
        ticker.play();

        TextArea history = output();
        Runnable refreshHistory = () -> {
            StringBuilder b = new StringBuilder();
            for (JsonNode n : store.records("stormStandDowns")) {
                b.append(n.path("timestamp").asText()).append(" | ")
                    .append(n.path("eventType").asText()).append(" | ")
                    .append(n.path("site").asText()).append(" | ")
                    .append(n.path("notes").asText()).append('\n');
            }
            history.setText(b.length() == 0 ? "No thunderstorm stand-down events saved yet." : b.toString());
        };

        java.util.function.Consumer<String> startStandDown = eventType -> {
            Instant now = Instant.now();
            clearAfter = now.plus(30, ChronoUnit.MINUTES);
            ObjectNode n = json.createObjectNode();
            n.put("timestamp", LOCAL_TIME.format(now));
            n.put("instant", now.toString());
            n.put("eventType", eventType);
            n.put("site", site.getText());
            n.put("latitude", latitude.getText());
            n.put("longitude", longitude.getText());
            n.put("clearAfter", clearAfter.toString());
            n.put("procedureReference", procedure.getText());
            n.put("notes", notes.getText());
            store.append("stormStandDowns", n);
            updateTimer.run(); refreshHistory.run();
        };

        Button thunder = new Button("🔊 Thunder heard — START / RESET STAND-DOWN"); thunder.setStyle(DANGER); thunder.setOnAction(e -> startStandDown.accept("THUNDER_HEARD"));
        Button lightning = new Button("⚡ Lightning seen — START / RESET STAND-DOWN"); lightning.setStyle(DANGER); lightning.setOnAction(e -> startStandDown.accept("LIGHTNING_SEEN"));
        Button supervisor = button("Supervisor / site stand-down"); supervisor.setOnAction(e -> startStandDown.accept("SUPERVISOR_SITE_STAND_DOWN"));
        HBox triggers = new HBox(8, thunder, lightning, supervisor); triggers.setAlignment(Pos.CENTER_LEFT); triggers.setFillHeight(true);

        Button recordRestart = button("Record restart authorization / clearance");
        recordRestart.setOnAction(e -> {
            Instant now = Instant.now();
            ObjectNode n = json.createObjectNode();
            n.put("timestamp", LOCAL_TIME.format(now)); n.put("instant", now.toString()); n.put("eventType", "RESTART_AUTHORIZATION_RECORDED");
            n.put("site", site.getText()); n.put("latitude", latitude.getText()); n.put("longitude", longitude.getText());
            n.put("procedureReference", procedure.getText()); n.put("notes", notes.getText());
            store.append("stormStandDowns", n); refreshHistory.run();
            standDownState.setText("Restart/clearance was recorded. This record is not app authorization: the responsible supervisor/work authority and current site procedure control whether work may resume.");
            standDownState.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#075985;-fx-background-color:#e0f2fe;-fx-background-radius:10;-fx-padding:12;");
        });

        root.getChildren().add(card("Substation / site stand-down control",
            "IHSA EUSR Rule 108 requires work to be suspended when weather makes the job unduly hazardous and work on/near apparatus suspended where lightning may cause injury when deemed unsafe by the on-site supervisor/worker. Hydro One's public 30–30 guidance is used here as a conservative shelter timer; a timer expiry never overrides the employer/site procedure.",
            grid(row("Site / substation", site), row("Latitude", latitude), row("Longitude", longitude), row("Site procedure / reference", procedure), row("Observation / clearance notes", notes)),
            standDownState, triggers, recordRestart));

        ImageView lightningView = new ImageView();
        lightningView.setFitWidth(820); lightningView.setFitHeight(430); lightningView.setPreserveRatio(false); lightningView.setSmooth(true);
        Label mapStatus = new Label("Enter site latitude/longitude and refresh. The image is ECCC MSC GeoMet lightning density for the most recent operational layer available.");
        mapStatus.setWrapText(true); mapStatus.setStyle("-fx-text-fill:#64748b;");
        StackPane imageStack = new StackPane(lightningView);
        imageStack.setMinHeight(430); imageStack.setPrefHeight(430); imageStack.setMaxWidth(Region.USE_COMPUTED_SIZE);
        Line crossH = new Line(-18,0,18,0); crossH.setStroke(Color.WHITE); crossH.setStrokeWidth(2.0);
        Line crossV = new Line(0,-18,0,18); crossV.setStroke(Color.WHITE); crossV.setStrokeWidth(2.0);
        Label siteMark = new Label("SITE"); siteMark.setStyle("-fx-background-color:rgba(15,23,42,.8);-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:3 6;-fx-background-radius:6;");
        VBox marker = new VBox(2, new StackPane(crossH,crossV), siteMark); marker.setAlignment(Pos.CENTER);
        imageStack.getChildren().add(marker);

        Button refreshMap = button("Refresh ECCC lightning snapshot");
        refreshMap.setOnAction(e -> {
            try {
                double lat = parseCoordinate(latitude, -90, 90, "latitude");
                double lon = parseCoordinate(longitude, -180, 180, "longitude");
                String url = wmsUrl(lat, lon);
                mapStatus.setText("Requesting ECCC GeoMet lightning-density snapshot…");
                Image img = new Image(url, true);
                img.errorProperty().addListener((obs, old, failed) -> {
                    if (failed) Platform.runLater(() -> mapStatus.setText("The ECCC lightning snapshot could not be loaded. Use the official live Lightning Map and site procedure; do not interpret an unavailable image as clear weather."));
                });
                img.progressProperty().addListener((obs, old, progress) -> {
                    if (progress.doubleValue() >= 1.0 && !img.isError()) Platform.runLater(() -> mapStatus.setText("ECCC GeoMet lightning-density snapshot requested at " + LOCAL_TIME.format(Instant.now()) + ". Site coordinates are the centre crosshair. Density data are supplemental and do not authorize work."));
                });
                lightningView.setImage(img);
            } catch (Exception ex) { mapStatus.setText(ex.getMessage()); }
        });
        Button officialMap = button("Open official ECCC live Lightning Map");
        officialMap.setOnAction(e -> openExternal(ECCC_LIGHTNING_MAP, mapStatus));
        HBox mapButtons = new HBox(8, refreshMap, officialMap); mapButtons.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().add(card("Live Canadian lightning tracker",
            "The native snapshot uses Environment and Climate Change Canada's MSC GeoMet Lightning_2.5km_Density WMS layer. ECCC's official Lightning Map is the primary interactive public view. Lightning displays may not show every in-cloud/cloud-to-cloud discharge; hearing thunder or seeing lightning remains a warning even if the map looks clear.",
            mapButtons, mapStatus, imageStack));

        TextField seconds = field("30");
        TextArea distanceOut = output(); distanceOut.setPrefRowCount(6);
        Button calcDistance = button("Estimate lightning distance");
        calcDistance.setOnAction(e -> {
            try {
                double s = Double.parseDouble(seconds.getText().trim());
                if (s < 0 || !Double.isFinite(s)) throw new IllegalArgumentException("Seconds must be zero or greater.");
                double km = s / 3.0;
                String trigger = s < 30 ? "UNDER 30 SECONDS — Hydro One public 30–30 guidance says seek shelter." : "30 seconds or more — this estimate does NOT authorize electrical/substation work.";
                distanceOut.setText(String.format(Locale.CANADA, "Flash-to-thunder interval: %.1f s%nApproximate lightning distance: %.1f km%n%s%n%nApproximation: seconds ÷ 3 ≈ kilometres. Always follow EUSR Rule 108 and the current employer/site lightning procedure.", s, km, trigger));
            } catch (Exception ex) { distanceOut.setText(ex.getMessage()); }
        });
        root.getChildren().add(card("Flash-to-thunder / 30–30 check",
            "A field estimate only. Do not use distance estimation to override a stand-down, detector alarm, EUSR Rule 108 or site procedure.",
            grid(row("Seconds from flash to thunder", seconds)), calcDistance, distanceOut));

        TextArea rules = output(); rules.setPrefRowCount(13);
        rules.setText("IHSA ELECTRICAL UTILITY SAFETY RULES — RULE 108 (summary)\n" +
            "• Suspend work immediately when weather conditions make the job unduly hazardous.\n" +
            "• Suspend work on or near apparatus where a lightning strike may cause injury whenever the on-site supervisor/worker deems conditions unsafe.\n\n" +
            "HYDRO ONE PUBLIC 30–30 STORM GUIDANCE\n" +
            "• Lightning followed by thunder in under 30 seconds: seek shelter.\n" +
            "• Remain sheltered for at least 30 minutes after the last thunder.\n\n" +
            "SUBSTATION / UTILITY APPLICATION\n" +
            "• Current Hydro One/employer procedures, lightning detector alarms, work-protection instructions and supervisor direction may be stricter and take precedence.\n" +
            "• The app does not claim an internal Hydro One numeric detector-radius rule that is not publicly verified.\n" +
            "• Timer expiry is never automatic permission to resume work.");
        root.getChildren().add(card("Stand-down rules and references", "Public rules/guidance embedded as a practical field summary.", rules));

        refreshHistory.run();
        root.getChildren().add(card("Stand-down event history", "Saved locally per user for safety documentation. Entries record observations and clearances; they do not constitute work authorization.", history));

        ScrollPane scroll = new ScrollPane(root); scroll.setFitToWidth(true); scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        setCenter(scroll);
    }

    private String wmsUrl(double lat, double lon) {
        double latSpan = 1.0, lonSpan = 1.5;
        double minLat = Math.max(-90, lat - latSpan), maxLat = Math.min(90, lat + latSpan);
        double minLon = Math.max(-180, lon - lonSpan), maxLon = Math.min(180, lon + lonSpan);
        return String.format(Locale.ROOT,
            "https://geo.weather.gc.ca/geomet?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=Lightning_2.5km_Density&STYLES=Lightning&CRS=EPSG:4326&BBOX=%.6f,%.6f,%.6f,%.6f&WIDTH=900&HEIGHT=480&FORMAT=image/png&TRANSPARENT=FALSE",
            minLat, minLon, maxLat, maxLon);
    }

    private static double parseCoordinate(TextField f, double min, double max, String name) {
        String s = f.getText().trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Enter the site " + name + ".");
        double v = Double.parseDouble(s);
        if (!Double.isFinite(v) || v < min || v > max) throw new IllegalArgumentException("Invalid " + name + ".");
        return v;
    }

    private static void openExternal(String url, Label status) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) throw new IllegalStateException("Browser launch is not supported on this system.");
            Desktop.getDesktop().browse(URI.create(url));
            status.setText("Opened the official ECCC Lightning Map in your browser.");
        } catch (Exception ex) { status.setText("Could not open the official map: " + ex.getMessage()); }
    }

    private VBox card(String title, String desc, Node... nodes) {
        VBox box = new VBox(10); box.setPadding(new Insets(16)); box.setStyle(CARD);
        Label h = new Label(title); h.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label d = new Label(desc); d.setWrapText(true); d.setStyle("-fx-text-fill:#64748b;");
        box.getChildren().addAll(h,d); box.getChildren().addAll(nodes); return box;
    }
    private GridPane grid(Node... rows) { GridPane g=new GridPane();g.setHgap(12);g.setVgap(8);int i=0;for(Node n:rows){g.add(n,0,i++);GridPane.setHgrow(n,Priority.ALWAYS);}return g; }
    private HBox row(String label, Node control) { Label l=new Label(label);l.setMinWidth(210);l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;");HBox h=new HBox(10,l,control);h.setAlignment(Pos.CENTER_LEFT);HBox.setHgrow(control,Priority.ALWAYS);if(control instanceof Region r)r.setMaxWidth(Double.MAX_VALUE);return h; }
    private TextField field(String value) { TextField f=new TextField(value);f.setStyle(INPUT);return f; }
    private Button button(String text) { Button b=new Button(text);b.setStyle(PRIMARY);return b; }
    private TextArea output() { TextArea a=new TextArea();a.setEditable(false);a.setWrapText(true);a.setPrefRowCount(9);a.setStyle("-fx-font-family:Consolas;-fx-font-size:12px;-fx-control-inner-background:#0f172a;-fx-text-fill:#e2e8f0;");return a; }
}

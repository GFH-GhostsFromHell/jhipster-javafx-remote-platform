package com.gfh.industrial.hmi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import java.io.File;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.*;

public final class IndustrialHmiApp extends Application {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread t = new Thread(task, "industrial-hmi-poll"); t.setDaemon(true); return t;
    });
    private TextField server, user, workstationId;
    private PasswordField workstationSecret;
    private volatile String assignedWorkstation = "", assignedSecret = "";
    private PasswordField password;
    private Label status, quality, setupSummary;
    private TilePane tiles;
    private volatile String endpoint = "", authorization = "";
    private volatile boolean active;
    private volatile long lastGood;
    private volatile long connectionGeneration;

    @Override public void start(Stage stage) {
        server = new TextField("http://127.0.0.1:8090");
        user = new TextField("studio-viewer");
        password = new PasswordField();
        password.setPromptText("Viewer password (never saved)");
        workstationId = new TextField();
        workstationId.setPromptText("e.g. control-room-1");
        workstationSecret = new PasswordField();
        workstationSecret.setPromptText("One-time enrollment secret from web portal");
        Button connect = new Button("Connect");
        connect.setOnAction(e -> connect());
        Button disconnect = new Button("Disconnect");
        disconnect.setOnAction(e -> disconnect());
        Button checkUpdates = new Button("Check updates");
        checkUpdates.setOnAction(e -> checkUpdates());
        Button importSetup = new Button("Import PC setup ZIP");
        importSetup.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select this PC's setup ZIP from the web configurator");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HMI setup packages (*.zip)", "*.zip"));
            File selected = chooser.showOpenDialog(stage);
            if (selected == null) return;
            try {
                WorkstationSetup.Settings settings = WorkstationSetup.importZip(
                    selected.toPath(),WorkstationSetup.configPath());
                applySetup(settings);
                status.setText("PC settings imported. Enter separately issued viewer and workstation credentials.");
            } catch (Exception ex) {
                status.setText("Setup import rejected: " + ex.getMessage());
            }
        });
        VBox fields = new VBox(8, new Label("Middleware URL"), server,
                new Label("Viewer username"), user, new Label("Viewer password"), password,
                new Label("Registered workstation ID"), workstationId,
                new Label("Workstation enrollment secret"), workstationSecret,
                new HBox(10, connect, disconnect, checkUpdates, importSetup));
        fields.setMaxWidth(480);
        status = new Label("Disconnected");
        setupSummary = new Label("No PC setup profile imported · Select Import PC setup ZIP or configure manually");
        setupSummary.setWrapText(true);
        quality = new Label("SIMULATED VALUES ONLY — NO PLC CONNECTION");
        quality.setTextFill(Color.ORANGE);
        tiles = new TilePane(12,12);
        tiles.setPrefColumns(3);
        ScrollPane scroll = new ScrollPane(tiles);
        scroll.setFitToWidth(true);
        VBox root = new VBox(14, new Label("Industrial Control Studio — JavaFX HMI"), fields, setupSummary, status, quality, scroll);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color:#12253a; -fx-font-size:14px");
        VBox.setVgrow(scroll,Priority.ALWAYS);
        stage.setTitle("Industrial Control Studio · Read-only HMI Simulator");
        Scene scene = new Scene(root,1000,700);
        scene.getStylesheets().add(getClass().getResource("/hmi.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
        try {
            WorkstationSetup.Settings setup=WorkstationSetup.load();
            if(setup!=null)applySetup(setup);
        }catch(Exception ex){
            status.setText("Invalid saved PC setup: "+ex.getMessage()+". Re-import a trusted setup ZIP.");
        }
        worker.scheduleWithFixedDelay(this::poll,0,3,TimeUnit.SECONDS);
    }

    private void applySetup(WorkstationSetup.Settings setup) {
        server.setText(setup.serverUrl());
        workstationId.setText(setup.workstationId());
        user.setText(setup.viewerUsername());
        setupSummary.setText("Assigned PC: " + setup.workstationName() + " (" + setup.workstationId() +
            ") · Current portal screen: " + setup.assignedScreen() +
            " (server assignment takes precedence)");
    }

    private void connect() {
        String url = server.getText().trim().replaceAll("/+$","");
        if (!url.matches("https?://[^\\s]+") || user.getText().isBlank() || password.getText().isBlank()
            || !workstationId.getText().trim().matches("[A-Za-z][A-Za-z0-9_-]{1,47}")
            || workstationSecret.getText().length() < 20) {
            status.setText("Enter server, viewer credentials and a registered PC ID/enrollment secret");
            return;
        }
        if (!url.matches("https://.+|http://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?")) {
            status.setText("Remote hosts require HTTPS (HTTP is loopback-only)");
            return;
        }
        endpoint=url;
        authorization="Basic "+Base64.getEncoder().encodeToString(
            (user.getText()+":"+password.getText()).getBytes(StandardCharsets.UTF_8));
        assignedWorkstation = workstationId.getText().trim();
        assignedSecret = workstationSecret.getText();
        connectionGeneration++;
        active=true;
        status.setText("Connecting...");
        password.clear();
        workstationSecret.clear();
    }

    private void disconnect() {
        active=false;connectionGeneration++;authorization="";endpoint="";lastGood=0;
        assignedWorkstation="";assignedSecret="";
        tiles.getChildren().clear();password.clear();workstationSecret.clear();status.setText("Disconnected");
    }

    private void poll() {
        if (!active) return;
        final long generation = connectionGeneration;
        final String localUrl = endpoint, token = authorization;
        final String id = assignedWorkstation, secret = assignedSecret;
        try {
            // Layout and readings are both scoped to the authenticated workstation.
            // A PC name by itself is not sufficient to retrieve another station's screen.
            JsonNode screen = getForWorkstation(localUrl + "/api/studio/client-screen", token, id, secret);
            JsonNode snapshot = getForWorkstation(localUrl + "/api/studio/client-live", token, id, secret);
            if (!("SIMULATION".equals(snapshot.path("mode").asText()) ||
                "KEPWARE".equals(snapshot.path("mode").asText())) ||
                !id.equals(screen.path("workstationId").asText()) ||
                !id.equals(snapshot.path("workstationId").asText()) ||
                !screen.path("screenId").asText().equals(snapshot.path("screenId").asText()) ||
                screen.path("revision").asLong(-1) != snapshot.path("screenRevision").asLong(-2)) {
                throw new IllegalStateException("Stale, inconsistent or untrusted screen data");
            }
            long sampled = System.currentTimeMillis();
            Platform.runLater(() -> {
                if (!active || generation != connectionGeneration) return;
                lastGood = sampled;
                render(snapshot, screen);
            });
        } catch (Exception e) {
            Platform.runLater(() -> {
                if (!active || generation != connectionGeneration) return;
                status.setText("DATA UNAVAILABLE / STALE: " + e.getMessage());
                quality.setText("No reliable data · Last sample " +
                    (lastGood == 0 ? "never" : ((System.currentTimeMillis()-lastGood)/1000)+" seconds ago"));
                tiles.getChildren().clear();
            });
        }
    }

    private JsonNode getForWorkstation(String url, String basic, String id, String secret) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(7))
            .header("Authorization", basic)
            .header("X-Studio-Device-Id", id)
            .header("X-Studio-Device-Token", secret)
            .header("Cache-Control", "no-store")
            .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
            throw new IllegalStateException("Workstation authentication / screen unavailable (HTTP "+response.statusCode()+")");
        return mapper.readTree(response.body());
    }

    private void render(JsonNode packet, JsonNode screen) {
        status.setText("Connected · PC " + screen.path("workstationName").asText() +
            " · screen " + screen.path("screenId").asText() +
            " rev " + screen.path("revision").asText() +
            " · " + packet.path("serverTime").asText());
        quality.setText("READ ONLY · " + ("KEPWARE".equals(packet.path("mode").asText()) ?
            (packet.path("connected").asBoolean() ? "Kepware OPC UA connected" : "Kepware OPC UA disconnected") :
            "ALL VALUES SIMULATED"));
        HmiScreenRenderer.render(tiles, screen.path("layout"), packet.path("samples"));
    }

    private void checkUpdates() {
        if(!active){status.setText("Connect before checking releases");return;}
        final String localUrl=endpoint, token=authorization;
        final long generation=connectionGeneration;
        worker.execute(()->{
            String message;
            try{
                HttpRequest req=HttpRequest.newBuilder(URI.create(localUrl+"/api/studio/update-manifest"))
                    .header("Authorization",token).timeout(Duration.ofSeconds(7)).GET().build();
                HttpResponse<String> response=http.send(req,HttpResponse.BodyHandlers.ofString());
                if(response.statusCode()!=200)throw new IllegalStateException("HTTP "+response.statusCode());
                JsonNode manifest=mapper.readTree(response.body());
                message=manifest.path("released").asBoolean()?
                    "Release available: "+manifest.path("version").asText() :
                    "No signed installer published (development "+manifest.path("version").asText()+")";
            }catch(Exception e){message="Update check failed: "+e.getMessage();}
            final String result=message;
            Platform.runLater(()->{if(active&&generation==connectionGeneration)status.setText(result);});
        });
    }
    @Override public void stop(){active=false;worker.shutdownNow();}
    public static void main(String[] args){launch(args);}
}

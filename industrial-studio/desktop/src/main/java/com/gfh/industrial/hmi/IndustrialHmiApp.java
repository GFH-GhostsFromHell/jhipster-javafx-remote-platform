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
    private TextField server, user;
    private PasswordField password;
    private Label status, quality;
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
        Button connect = new Button("Connect");
        connect.setOnAction(e -> connect());
        Button disconnect = new Button("Disconnect");
        disconnect.setOnAction(e -> disconnect());
        Button checkUpdates = new Button("Check updates");
        checkUpdates.setOnAction(e -> checkUpdates());
        VBox fields = new VBox(8, new Label("Middleware URL"), server, new Label("Username"), user,
                new Label("Password"), password, new HBox(10, connect, disconnect, checkUpdates));
        fields.setMaxWidth(480);
        status = new Label("Disconnected");
        quality = new Label("SIMULATED VALUES ONLY — NO PLC CONNECTION");
        quality.setTextFill(Color.ORANGE);
        tiles = new TilePane(12,12);
        tiles.setPrefColumns(3);
        ScrollPane scroll = new ScrollPane(tiles);
        scroll.setFitToWidth(true);
        VBox root = new VBox(14, new Label("Industrial Control Studio — JavaFX HMI"), fields, status, quality, scroll);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color:#12253a; -fx-font-size:14px");
        VBox.setVgrow(scroll,Priority.ALWAYS);
        stage.setTitle("Industrial Control Studio · Read-only HMI Simulator");
        stage.setScene(new Scene(root,1000,700));
        stage.show();
        worker.scheduleWithFixedDelay(this::poll,0,3,TimeUnit.SECONDS);
    }

    private void connect() {
        String url = server.getText().trim().replaceAll("/+$","");
        if (!url.matches("https?://[^\\s]+") || user.getText().isBlank() || password.getText().isBlank()) {
            status.setText("Enter a valid URL and credentials");
            return;
        }
        if (!url.matches("https://.+|http://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?")) {
            status.setText("Remote hosts require HTTPS (HTTP is loopback-only)");
            return;
        }
        endpoint=url;
        authorization="Basic "+Base64.getEncoder().encodeToString(
            (user.getText()+":"+password.getText()).getBytes(StandardCharsets.UTF_8));
        connectionGeneration++;
        active=true;
        status.setText("Connecting...");
        password.clear();
    }

    private void disconnect() {
        active=false;connectionGeneration++;authorization="";endpoint="";lastGood=0;
        tiles.getChildren().clear();password.clear();status.setText("Disconnected");
    }

    private void poll() {
        if (!active)return;
        final long generation=connectionGeneration;
        final String localUrl=endpoint, token=authorization;
        try{
            HttpRequest req=HttpRequest.newBuilder(URI.create(localUrl+"/api/studio/live"))
                .header("Authorization",token).timeout(Duration.ofSeconds(7)).GET().build();
            HttpResponse<String> response=http.send(req,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)throw new IllegalStateException("HTTP "+response.statusCode());
            JsonNode snapshot=mapper.readTree(response.body());
            if(!"SIMULATION".equals(snapshot.path("mode").asText()))
                throw new IllegalStateException("Unknown data quality: refusing display");
            long sampled=System.currentTimeMillis();
            Platform.runLater(()->{
                if(!active||generation!=connectionGeneration)return;
                lastGood=sampled;render(snapshot);
            });
        } catch(Exception e) {
            Platform.runLater(()->{
                if(!active||generation!=connectionGeneration)return;
                status.setText("DATA UNAVAILABLE / STALE: "+e.getMessage());
                quality.setText("No reliable data • Last sample "+
                    (lastGood==0?"never":((System.currentTimeMillis()-lastGood)/1000)+" seconds ago"));
                tiles.getChildren().clear();
            });
        }
    }

    private void render(JsonNode packet) {
        status.setText("Connected · config revision "+packet.path("configurationRevision").asText()+
            " · "+packet.path("serverTime").asText());
        quality.setText("READ ONLY · ALL VALUES SIMULATED");
        tiles.getChildren().clear();
        for(JsonNode t:packet.path("samples")){
            Label name=new Label(t.path("label").asText());
            Label value=new Label(t.path("value").asText()+" "+t.path("unit").asText());
            value.setStyle("-fx-font-size:27px; -fx-text-fill:#8edbff; -fx-font-weight:bold");
            Label info=new Label("Quality: "+t.path("quality").asText()+
                (t.path("alarm").asBoolean()?" · SIMULATED LIMIT":""));
            info.setStyle("-fx-text-fill:#ffd49b");
            VBox card=new VBox(10,name,value,info);
            card.setPrefSize(265,130);card.setPadding(new Insets(14));
            card.setStyle("-fx-background-color:#1e4161; -fx-background-radius:9");
            tiles.getChildren().add(card);
        }
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

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
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.*;

/**
 * Read-only industrial HMI prototype with self-service, code-based workstation registration.
 * The only normal setup step is pasting a short-lived code from the admin web portal.
 * Legacy manual enrollment stays under Advanced.
 */
public final class IndustrialHmiApp extends Application {
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper=new ObjectMapper();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(task->{
        Thread t=new Thread(task,"industrial-hmi-worker");t.setDaemon(true);return t;
    });

    private TextField server,user,workstationId;
    private PasswordField password,workstationSecret;
    private TextArea pairingCode;
    private Button pairButton;
    private Label status,quality,setupSummary,pairingHelp;
    private VBox pairPanel;
    private TitledPane advanced;
    private TilePane tiles;
    private volatile String endpoint="",authorization="";
    private volatile String assignedWorkstation="",assignedSecret="";
    private volatile boolean active;
    private volatile long lastGood,connectionGeneration;

    @Override public void start(Stage stage) {
        pairingCode=new TextArea();
        pairingCode.setPromptText("Paste the full ICS1|... setup code from the web portal");
        pairingCode.setWrapText(true);
        pairingCode.setPrefRowCount(2);
        pairingCode.setMaxHeight(88);
        pairButton=new Button("Connect this PC");
        pairButton.setDefaultButton(true);
        pairButton.setOnAction(e->pairNewDevice());
        pairingHelp=new Label("In the web portal, open HMI screens & PCs → Register & get setup code (or Pair / reconnect PC). Copy the code into this box.");
        pairingHelp.setWrapText(true);
        Label setupTitle=new Label("Set up this PC");
        setupTitle.setStyle("-fx-font-size:22px;-fx-font-weight:bold");
        Hyperlink webPortal=new Hyperlink("Open the web configurator to get a setup code");
        webPortal.setOnAction(e->getHostServices().showDocument("https://industrial-control-studio-demo.onrender.com"));
        pairPanel=new VBox(9,setupTitle,pairingHelp,webPortal,pairingCode,pairButton);
        pairPanel.setPadding(new Insets(15));
        pairPanel.setStyle("-fx-background-color:#1a3952;-fx-background-radius:10;");
        pairPanel.setMaxWidth(720);

        server=new TextField("https://industrial-control-studio-demo.onrender.com");
        user=new TextField("studio-viewer");
        password=new PasswordField();
        password.setPromptText("Viewer password (advanced legacy setup)");
        workstationId=new TextField();
        workstationId.setPromptText("e.g. control-room-1");
        workstationSecret=new PasswordField();
        workstationSecret.setPromptText("Workstation secret (advanced legacy setup)");

        Button manualConnect=new Button("Connect with existing credentials");
        manualConnect.setOnAction(e->connectManual());
        Button importSetup=new Button("Import old PC setup ZIP");
        importSetup.setOnAction(e->{
            FileChooser chooser=new FileChooser();
            chooser.setTitle("Choose a configuration-only workstation setup ZIP");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP setup packages","*.zip"));
            File file=chooser.showOpenDialog(stage);
            if(file==null)return;
            try{
                var settings=WorkstationSetup.importZip(file.toPath(),WorkstationSetup.configPath());
                server.setText(settings.serverUrl());
                user.setText(settings.viewerUsername());
                workstationId.setText(settings.workstationId());
                setupSummary.setText("Imported "+settings.workstationName()+" · "+settings.assignedScreen()+
                    ". Old ZIP setup still requires a viewer password and workstation secret; for the easier setup use a new pairing code.");
                status.setText("Setup ZIP imported. Ask the administrator for a new pairing code, or enter legacy credentials below.");
            }catch(Exception ex){status.setText("ZIP import rejected: "+ex.getMessage());}
        });
        VBox advancedFields=new VBox(8,
            new Label("Middleware / web portal URL"),server,
            new Label("Viewer username"),user,
            new Label("Viewer password"),password,
            new Label("Workstation ID"),workstationId,
            new Label("Workstation secret"),workstationSecret,
            new HBox(9,manualConnect,importSetup));
        advancedFields.setPadding(new Insets(10));
        advanced=new TitledPane("Advanced: manual connection or older setup ZIP",advancedFields);
        advanced.setExpanded(false);
        advanced.setMaxWidth(720);

        Button forget=new Button("Disconnect / forget this PC");
        forget.setOnAction(e->forgetThisPc());
        Button updates=new Button("Check software updates");
        updates.setOnAction(e->checkUpdates());

        status=new Label("Not connected · Paste a setup code above to get started");
        status.setWrapText(true);
        setupSummary=new Label("This PC has not been registered yet.");
        setupSummary.setWrapText(true);
        quality=new Label("READ ONLY · DO NOT OPERATE EQUIPMENT FROM DEMO VALUES");
        quality.setTextFill(Color.ORANGE);
        quality.setWrapText(true);
        tiles=new TilePane(12,12);
        tiles.setPrefColumns(3);
        ScrollPane scroll=new ScrollPane(tiles);
        scroll.setFitToWidth(true);

        VBox root=new VBox(14,
            new Label("Industrial Control Studio — JavaFX HMI"),
            pairPanel,advanced,setupSummary,new HBox(10,forget,updates),status,quality,scroll);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color:#12253a;-fx-font-size:14px");
        VBox.setVgrow(scroll,Priority.ALWAYS);
        stage.setTitle("Industrial Control Studio · Workstation HMI");
        Scene scene=new Scene(root,1080,740);
        scene.getStylesheets().add(getClass().getResource("/hmi.css").toExternalForm());
        stage.setScene(scene);
        stage.show();

        try{
            SecureWorkstationStore.Session saved=SecureWorkstationStore.load();
            if(saved!=null){
                activate(saved.serverUrl(),saved.workstationId(),saved.deviceSecret(),"");
                setupSummary.setText("Registered PC: "+saved.workstationName()+" ("+
                    saved.workstationId()+") · Assigned screen: "+saved.screenId());
                status.setText("Reconnecting this PC automatically...");
            }else{
                // Load the older settings-only ZIP metadata without requiring the user to import it again.
                WorkstationSetup.Settings old=WorkstationSetup.load();
                if(old!=null){
                    server.setText(old.serverUrl());
                    user.setText(old.viewerUsername());
                    workstationId.setText(old.workstationId());
                    setupSummary.setText("Old PC profile detected for "+old.workstationName()+
                        ". To connect easily, ask the portal administrator for a new setup code.");
                }
            }
        }catch(Exception ex){
            status.setText("Could not restore this PC's saved enrollment. Ask for a new setup code: "+ex.getMessage());
        }
        worker.scheduleWithFixedDelay(this::poll,0,3,TimeUnit.SECONDS);
    }

    private void setPaired(boolean paired) {
        pairPanel.setVisible(!paired);
        pairPanel.setManaged(!paired);
        if(paired){advanced.setExpanded(false);pairingCode.clear();}
    }

    private void activate(String url,String id,String secret,String basic) {
        // UI thread only. Keep identity and access secret separate from display metadata.
        connectionGeneration++;
        endpoint=url;
        assignedWorkstation=id;
        assignedSecret=secret;
        authorization=basic;
        active=true;
        lastGood=0;
        setPaired(true);
    }

    private void pairNewDevice() {
        final PairingCode setup;
        try{
            setup=PairingCode.parse(pairingCode.getText());
        }catch(IllegalArgumentException e){
            status.setText(e.getMessage());return;
        }
        pairButton.setDisable(true);
        status.setText("Registering "+setup.workstationId()+" securely with the web portal...");
        worker.execute(()->{
            try{
                String body=mapper.writeValueAsString(java.util.Map.of(
                    "workstationId",setup.workstationId(),"pairingCode",setup.pairingSecret()));
                HttpRequest request=HttpRequest.newBuilder(URI.create(setup.serverUrl()+"/api/studio/pair"))
                    .header("Content-Type","application/json")
                    .header("Accept","application/json")
                    .header("Cache-Control","no-store")
                    .timeout(Duration.ofSeconds(75))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
                HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
                if(response.statusCode()!=200){
                    String message=response.statusCode()==403?
                        "Setup code was invalid, already used or expired. Click Pair / reconnect PC in the portal for a new code.":
                        response.statusCode()==404?
                        "This server has not deployed the new registration feature yet. Ask the administrator to update the server.":
                        "Registration failed (HTTP "+response.statusCode()+"). Check the portal and try again.";
                    throw new IllegalStateException(message);
                }
                JsonNode result=mapper.readTree(response.body());
                String id=result.path("workstationId").asText("");
                String issued=result.path("deviceSecret").asText("");
                String canonical=PairingCode.validServer(result.path("serverUrl").asText(""));
                String pcName=result.path("workstationName").asText(id);
                String screen=result.path("screenId").asText("");
                if(!id.equals(setup.workstationId()) || !canonical.equals(setup.serverUrl()) ||
                    !issued.matches("[A-Za-z0-9_-]{43}")) {
                    throw new IllegalStateException("Server returned an invalid workstation identity");
                }
                String persistenceNote="";
                try{
                    if(SecureWorkstationStore.supported()){
                        SecureWorkstationStore.save(new SecureWorkstationStore.Session(
                            canonical,id,pcName,screen,issued));
                        persistenceNote=" · Securely remembered on this Windows account";
                    }else{
                        persistenceNote=" · This OS cannot securely save the enrollment; pair again after restarting";
                    }
                }catch(Exception saveError){
                    persistenceNote=" · Connected for this session; Windows credential storage failed. Pair again after restarting.";
                }
                final String note=persistenceNote;
                Platform.runLater(()->{
                    activate(canonical,id,issued,"");
                    server.setText(canonical);
                    workstationId.setText(id);
                    setupSummary.setText("Registered PC: "+pcName+" ("+id+") · Screen: "+screen);
                    status.setText("Registration successful"+note+". Loading the assigned HMI screen...");
                    pairButton.setDisable(false);
                });
            }catch(Exception e){
                Platform.runLater(()->{
                    pairButton.setDisable(false);
                    status.setText("Could not register PC: "+e.getMessage());
                    pairingHelp.setText("Keep the setup code private. If it expired, open the web portal and choose Pair / reconnect PC.");
                });
            }
        });
    }

    private void connectManual() {
        final String url;
        try{url=PairingCode.validServer(server.getText());}
        catch(IllegalArgumentException e){status.setText(e.getMessage());return;}
        if(user.getText().isBlank() || password.getText().isBlank() ||
            !workstationId.getText().trim().matches("[A-Za-z][A-Za-z0-9_-]{1,47}") ||
            !workstationSecret.getText().matches("[A-Za-z0-9_-]{43}")){
            status.setText("Advanced setup needs the viewer username/password and the PC ID/secret. The simpler method is to paste one setup code above.");
            return;
        }
        String basic="Basic "+Base64.getEncoder().encodeToString(
            (user.getText()+":"+password.getText()).getBytes(StandardCharsets.UTF_8));
        activate(url,workstationId.getText().trim(),workstationSecret.getText(),basic);
        password.clear();workstationSecret.clear();
        status.setText("Connecting with older manual credentials...");
    }

    private void forgetThisPc() {
        connectionGeneration++;
        active=false;endpoint="";authorization="";
        assignedWorkstation="";assignedSecret="";lastGood=0;
        tiles.getChildren().clear();
        password.clear();workstationSecret.clear();
        setPaired(false);
        try{SecureWorkstationStore.forget();
            status.setText("Local PC registration cleared. Paste a new setup code to connect.");
        }catch(Exception ex){
            status.setText("Disconnected, but saved Windows registration could not be removed: "+ex.getMessage());
        }
        setupSummary.setText("Not connected. Ask an administrator for a new PC setup code.");
    }

    private void poll() {
        if(!active)return;
        final long generation=connectionGeneration;
        final String url=endpoint, basic=authorization;
        final String id=assignedWorkstation, secret=assignedSecret;
        try{
            JsonNode screen=getForWorkstation(url+"/api/studio/client-screen",basic,id,secret);
            JsonNode readings=getForWorkstation(url+"/api/studio/client-live",basic,id,secret);
            if(!("SIMULATION".equals(readings.path("mode").asText()) ||
                  "KEPWARE".equals(readings.path("mode").asText())) ||
                !id.equals(screen.path("workstationId").asText()) ||
                !id.equals(readings.path("workstationId").asText()) ||
                !screen.path("screenId").asText().equals(readings.path("screenId").asText()) ||
                screen.path("revision").asLong(-1)!=readings.path("screenRevision").asLong(-2)){
                throw new IllegalStateException("Screen data was inconsistent; retrying");
            }
            long sampled=System.currentTimeMillis();
            Platform.runLater(()->{
                if(!active || generation!=connectionGeneration)return;
                lastGood=sampled;
                render(readings,screen);
            });
        }catch(Exception ex){
            Platform.runLater(()->{
                if(!active || generation!=connectionGeneration)return;
                String error=ex.getMessage()==null?"Unknown network error":ex.getMessage();
                status.setText("DATA UNAVAILABLE: "+error);
                quality.setText("Readings cleared because the connection is stale or unauthorized");
                tiles.getChildren().clear();
                if(error.contains("403") || error.contains("401")){
                    active=false;
                    setPaired(false);
                    pairingHelp.setText("This PC no longer has access. In the web portal, choose Pair / reconnect PC and paste the new setup code here.");
                    status.setText("This PC was disabled, re-registered or has invalid credentials. Please pair it again.");
                }
            });
        }
    }

    private JsonNode getForWorkstation(String url,String basic,String id,String secret) throws Exception {
        HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("X-Studio-Device-Id",id)
            .header("X-Studio-Device-Token",secret)
            .header("Cache-Control","no-store")
            .GET();
        if(!basic.isBlank())builder.header("Authorization",basic);
        HttpResponse<String> response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)
            throw new IllegalStateException("HTTP "+response.statusCode()+
                (response.statusCode()==403?" · this PC needs pairing":""));
        return mapper.readTree(response.body());
    }

    private void render(JsonNode packet,JsonNode screen) {
        status.setText("Connected · "+screen.path("workstationName").asText()+
            " · screen "+screen.path("screenId").asText()+
            " rev "+screen.path("revision").asText()+
            " · "+packet.path("serverTime").asText());
        quality.setText("READ ONLY · "+("KEPWARE".equals(packet.path("mode").asText()) ?
            (packet.path("connected").asBoolean()?"Kepware OPC UA connected":"Kepware OPC UA disconnected") :
            "SIMULATED VALUES ONLY"));
        HmiScreenRenderer.render(tiles,screen.path("layout"),packet.path("samples"));
    }

    private void checkUpdates() {
        status.setText("Development installers are published as unsigned GitHub Actions artifacts. There is no signed automatic update channel yet.");
    }

    @Override public void stop(){
        active=false;
        worker.shutdownNow();
        authorization="";assignedSecret="";
    }
    public static void main(String[] args){launch(args);}
}

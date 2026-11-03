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
    private FeatureViews() {}

    public static Node create(String featureKey, JsonNode bootstrap, PortalClient portal, Stage stage, String username, String password) {
        return switch (featureKey) {
            case "ASK_CHATGPT" -> AskChatGptView.create(username);
            case "ELECTRICAL_CALCULATORS" -> CalculatorHub.create(portal,username,password);
            case "RESIDENTIAL_REFERENCE" -> residentialReference();
            case "DASHBOARD" -> dashboard(bootstrap,portal,username,password);
            case "REPORTS" -> reports(bootstrap,stage);
            case "PLC_CONNECT" -> plcConnect();
            case "REMOTE_DIAGNOSTICS" -> diagnostics(portal);
            case "ADVANCED_ANALYTICS" -> analytics(bootstrap);
            case "ENGINEERING_MODE" -> engineering(bootstrap);
            case "ENTERPRISE_TEAM_MANAGEMENT" -> new EnterpriseTeamPane(portal,username,password);
            default -> page(pretty(featureKey),"This module is enabled but does not have a dedicated screen yet.");
        };
    }

    private static Node residentialReference(){VBox b=page("Residential CEC Reference","Plain-language residential reference hub with rule/table citations and original explanations.");String[] groups={"Services & panels — demand, feeders, grounding/bonding, masts and clearances","Receptacles — room spacing, kitchens, bathrooms, outdoors, GFCI/AFCI and tamper-resistant","Lighting — outlets, switches, stairways, exterior lighting and 3-way/4-way control","Special equipment — ranges, dryers, heating, HVAC, EV charging, pools/hot tubs and garages","Wiring methods — NMD90, attic wiring, raceways, support, box sizing, ampacity and voltage drop"};for(String g:groups){Label l=new Label("•  "+g);l.setWrapText(true);l.setStyle("-fx-font-size:14px;-fx-padding:7 0;");b.getChildren().add(l);}return b;}

    private static Node dashboard(JsonNode root,PortalClient portal,String username,String password){
        VBox b=page("Dashboard","Live account and cloud overview.");
        JsonNode u=root.path("user");
        GridPane g=new GridPane();g.setHgap(14);g.setVgap(14);
        g.add(card("User",u.path("displayName").asText(u.path("username").asText("User"))),0,0);
        String rawTier=u.path("subscriptionTier").asText("TIER_1");
        g.add(card("Subscription","TIER_4".equals(rawTier)?"Business / Enterprise":rawTier.replace('_',' ')),1,0);
        int enabled=0;Iterator<Map.Entry<String,JsonNode>> it=root.path("features").fields();while(it.hasNext())if(it.next().getValue().asBoolean(false))enabled++;
        g.add(card("Enabled features",String.valueOf(enabled)),0,1);g.add(card("Portal",portal.baseUrl()),1,1);
        Label managed=new Label();managed.setWrapText(true);managed.setVisible(false);managed.setManaged(false);managed.setStyle("-fx-background-color:#ecfeff;-fx-border-color:#a5f3fc;-fx-border-radius:10;-fx-background-radius:10;-fx-padding:10 12;-fx-text-fill:#155e75;-fx-font-weight:bold;");
        b.getChildren().addAll(g,managed);
        CompletableFuture.supplyAsync(()->{try{return portal.enterpriseMembership(username,password);}catch(Exception ex){return null;}}).thenAccept(body->Platform.runLater(()->{
            if(body==null)return;
            try{
                JsonNode membership=new ObjectMapper().readTree(body);
                if(!membership.path("managed").asBoolean(false))return;
                String business=membership.path("businessName").asText(membership.path("ownerUsername").asText("Business account"));
                String tier=membership.path("assignedTier").asText("TIER_1").replace('_',' ');
                String role=membership.path("roleLabel").asText("");
                managed.setText("🏢 Business-managed account · "+business+" · "+tier+(role.isBlank()?"":" · "+role));
                managed.setVisible(true);managed.setManaged(true);
            }catch(Exception ignored){}
        }));
        return b;
    }

    private static Node reports(JsonNode root,Stage stage){VBox b=page("Reports","Export your current account, entitlement and configuration snapshot.");TextArea preview=new TextArea(root.toPrettyString());preview.setEditable(false);preview.setPrefRowCount(18);Button export=new Button("Export JSON report");export.setOnAction(e->{FileChooser fc=new FileChooser();fc.setInitialFileName("remote-javafx-report.json");fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files","*.json"));File f=fc.showSaveDialog(stage);if(f!=null)try{Files.writeString(f.toPath(),root.toPrettyString());new Alert(Alert.AlertType.INFORMATION,"Report exported to:\n"+f.getAbsolutePath()).showAndWait();}catch(Exception ex){new Alert(Alert.AlertType.ERROR,ex.getMessage()).showAndWait();}});b.getChildren().addAll(export,preview);return b;}

    private static Node plcConnect(){VBox b=page("PLC Connect","Test TCP reachability to a PLC, HMI, gateway or industrial device.");TextField host=new TextField();host.setPromptText("Device IP or hostname");TextField port=new TextField("502");Label status=new Label();Button test=new Button("Test connection");test.setOnAction(e->{test.setDisable(true);status.setText("Connecting…");CompletableFuture.supplyAsync(()->{long start=System.nanoTime();try(Socket s=new Socket()){s.connect(new InetSocketAddress(host.getText().trim(),Integer.parseInt(port.getText().trim())),3000);return "Connected successfully in "+Duration.ofNanos(System.nanoTime()-start).toMillis()+" ms";}catch(Exception ex){return "Connection failed: "+ex.getMessage();}}).thenAccept(msg->Platform.runLater(()->{status.setText(msg);test.setDisable(false);}));});GridPane f=new GridPane();f.setHgap(10);f.setVgap(10);f.add(new Label("Host"),0,0);f.add(host,1,0);f.add(new Label("Port"),0,1);f.add(port,1,1);b.getChildren().addAll(f,test,status);return b;}

    private static Node diagnostics(PortalClient portal){VBox b=page("Remote Diagnostics","Check cloud availability and response latency from this computer.");Label result=new Label("Not tested yet.");result.setWrapText(true);Button run=new Button("Run diagnostics");run.setOnAction(e->{run.setDisable(true);result.setText("Running cloud health check…");CompletableFuture.supplyAsync(()->{Instant start=Instant.now();try{boolean ok=portal.health();return "Cloud health: "+(ok?"OK":"FAILED")+"\nLatency: "+Duration.between(start,Instant.now()).toMillis()+" ms\nPortal: "+portal.baseUrl();}catch(Exception ex){return "Diagnostic failed: "+ex.getMessage();}}).thenAccept(msg->Platform.runLater(()->{result.setText(msg);run.setDisable(false);}));});b.getChildren().addAll(run,result);return b;}

    private static Node analytics(JsonNode root){VBox b=page("Advanced Analytics","Entitlement/configuration summary.");int enabled=0,disabled=0;Iterator<Map.Entry<String,JsonNode>> it=root.path("features").fields();while(it.hasNext()){if(it.next().getValue().asBoolean(false))enabled++;else disabled++;}int cc=root.path("configuration").isObject()?root.path("configuration").size():0;b.getChildren().add(new HBox(14,card("Enabled",String.valueOf(enabled)),card("Disabled",String.valueOf(disabled)),card("Config values",String.valueOf(cc))));return b;}
    private static Node engineering(JsonNode root){VBox b=page("Engineering Mode","Inspect the effective configuration returned by the cloud portal.");TextArea raw=new TextArea(root.path("configuration").toPrettyString());raw.setEditable(false);raw.setPrefRowCount(20);b.getChildren().add(raw);return b;}
    private static VBox page(String title,String subtitle){VBox b=new VBox(14);b.setPadding(new Insets(22));Label h=new Label(title);h.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#14213d;");Label s=new Label(subtitle);s.setWrapText(true);s.setStyle("-fx-text-fill:#64748b;");b.getChildren().addAll(h,s,new Separator());return b;}
    private static VBox card(String title,String value){VBox c=new VBox(6);c.setPadding(new Insets(16));c.setPrefWidth(260);c.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:12;-fx-background-radius:12;");Label t=new Label(title);t.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");Label v=new Label(value);v.setWrapText(true);v.setStyle("-fx-text-fill:#14213d;-fx-font-size:18px;-fx-font-weight:bold;");c.getChildren().addAll(t,v);return c;}
    private static String pretty(String key){if("ASK_CHATGPT".equals(key))return"Ask ChatGPT";if("ENTERPRISE_TEAM_MANAGEMENT".equals(key))return"Business Team";String[] p=key.toLowerCase().split("_");StringBuilder o=new StringBuilder();for(String x:p)if(!x.isBlank())o.append(Character.toUpperCase(x.charAt(0))).append(x.substring(1)).append(' ');return o.toString().trim();}
}

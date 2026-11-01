package com.example.desktop;

import com.example.desktop.ui.CloudStatusControl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class RemoteDesktopApp extends Application {
    private final PortalClient portal = new PortalClient();
    private final ObjectMapper json = new ObjectMapper();
    private final CloudStatusControl cloudStatus = new CloudStatusControl();
    private final UpdateService updater = new UpdateService();
    private Stage stage;
    private String currentUsername;
    private String currentPassword;
    private JsonNode currentBootstrap;

    private static final String BG = "-fx-background-color: linear-gradient(to bottom right,#0f172a,#17365d 58%,#2b6cb0);";
    private static final String CARD = "-fx-background-color:white;-fx-background-radius:22;-fx-effect:dropshadow(gaussian,rgba(0,0,0,.24),34,0,0,12);";
    private static final String INPUT = "-fx-background-color:#ffffff;-fx-border-color:#cbd5e1;-fx-border-radius:10;-fx-background-radius:10;-fx-padding:12 13;-fx-font-size:14px;";
    private static final String PRIMARY = "-fx-background-color:#1d4ed8;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:10;-fx-padding:12 18;-fx-font-size:14px;";

    @Override public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Remote JavaFX Client");
        stage.setMinWidth(820);
        stage.setMinHeight(560);
        showLogin();
        stage.show();
        checkCloud();
    }

    private void checkCloud() {
        cloudStatus.setState(CloudConnectionState.CONNECTING, "Checking " + portal.baseUrl());
        CompletableFuture.runAsync(() -> {
            try {
                boolean ok = portal.health();
                cloudStatus.setState(ok ? CloudConnectionState.ONLINE : CloudConnectionState.OFFLINE,
                    ok ? "Connected to " + portal.baseUrl() : "Cloud service is waking or unavailable");
            } catch (Exception e) {
                cloudStatus.setState(CloudConnectionState.OFFLINE, e.getMessage());
            }
        });
    }

    private void showLogin() {
        BorderPane root = new BorderPane(); root.setStyle(BG);
        VBox hero = new VBox(18); hero.setPadding(new Insets(58, 42, 58, 58)); hero.setAlignment(Pos.CENTER_LEFT);
        Label pill = new Label("REMOTE JAVAFX PLATFORM");
        pill.setStyle("-fx-text-fill:#dbeafe;-fx-border-color:rgba(255,255,255,.25);-fx-border-radius:20;-fx-background-color:rgba(255,255,255,.08);-fx-background-radius:20;-fx-padding:7 12;-fx-font-weight:bold;-fx-font-size:11px;");
        Label heroTitle = new Label("Your desktop tools,\nconnected to the cloud.");
        heroTitle.setStyle("-fx-text-fill:white;-fx-font-size:34px;-fx-font-weight:bold;"); heroTitle.setWrapText(true);
        Label heroText = new Label("Sign in to load your profile, subscription tier, enabled features and managed configuration.");
        heroText.setMaxWidth(390); heroText.setWrapText(true); heroText.setStyle("-fx-text-fill:#dbeafe;-fx-font-size:15px;-fx-line-spacing:4px;");
        HBox cloudBox = new HBox(cloudStatus); cloudBox.setAlignment(Pos.CENTER_LEFT); cloudBox.setStyle("-fx-background-color:rgba(255,255,255,.10);-fx-background-radius:12;-fx-padding:7 10;");
        hero.getChildren().addAll(pill, heroTitle, heroText, cloudBox);

        VBox card = new VBox(13); card.setPadding(new Insets(38)); card.setMaxWidth(390); card.setStyle(CARD);
        Label title = new Label("Welcome back"); title.setStyle("-fx-font-size:27px;-fx-font-weight:bold;-fx-text-fill:#14213d;");
        Label subtitle = new Label("Use the same account as your web portal."); subtitle.setStyle("-fx-text-fill:#64748b;-fx-font-size:13px;");
        TextField username = new TextField(); username.setPromptText("Enter your username"); username.setStyle(INPUT);
        PasswordField password = new PasswordField(); password.setPromptText("Enter your password"); password.setStyle(INPUT);
        Label message = new Label(); message.setWrapText(true); message.setStyle("-fx-text-fill:#b91c1c;-fx-font-size:12px;");
        Button login = new Button("Sign in to desktop client"); login.setMaxWidth(Double.MAX_VALUE); login.setDefaultButton(true); login.setStyle(PRIMARY);

        login.setOnAction(e -> {
            login.setDisable(true); login.setText("Connecting to cloud…"); message.setText("");
            String u = username.getText().trim(); String p = password.getText();
            CompletableFuture.supplyAsync(() -> { try { return portal.bootstrap(u, p); } catch (Exception ex) { throw new RuntimeException(ex); } })
                .whenComplete((body, err) -> Platform.runLater(() -> {
                    login.setDisable(false); login.setText("Sign in to desktop client");
                    if (err != null) { cloudStatus.setState(CloudConnectionState.DEGRADED, "Sign-in request failed"); Throwable c=err.getCause()==null?err:err.getCause(); message.setText(c.getMessage()==null?"Could not sign in.":c.getMessage()); return; }
                    try { currentUsername=u; currentPassword=p; currentBootstrap=json.readTree(body); cloudStatus.setState(CloudConnectionState.AUTHENTICATED, "Authenticated as "+u); showMain(currentBootstrap); }
                    catch (Exception parse) { message.setText("The cloud returned an invalid response."); }
                }));
        });

        card.getChildren().addAll(title, subtitle, spacer(4), fieldLabel("Username"), username, fieldLabel("Password"), password, spacer(4), login, message);
        StackPane cardWrap = new StackPane(card); cardWrap.setPadding(new Insets(42,58,42,32)); cardWrap.setAlignment(Pos.CENTER);
        GridPane layout = new GridPane(); ColumnConstraints left=new ColumnConstraints(); left.setPercentWidth(53); ColumnConstraints right=new ColumnConstraints(); right.setPercentWidth(47); layout.getColumnConstraints().addAll(left,right); layout.add(hero,0,0); layout.add(cardWrap,1,0); GridPane.setVgrow(hero,Priority.ALWAYS); GridPane.setVgrow(cardWrap,Priority.ALWAYS);
        root.setCenter(layout); stage.setScene(new Scene(root,960,620));
    }

    private void showMain(JsonNode root) {
        JsonNode account = root.path("user");
        String username = account.path("username").asText("user");
        String name = account.path("displayName").asText(username);
        String email = account.path("email").asText("");
        String tier = account.path("subscriptionTier").asText("TIER_1").replace('_',' ');
        String avatarData = account.path("avatarData").isNull()?null:account.path("avatarData").asText(null);

        BorderPane page = new BorderPane(); page.setStyle("-fx-background-color:#f4f7fb;");
        HBox top = new HBox(12); top.setAlignment(Pos.CENTER_LEFT); top.setPadding(new Insets(14,22,14,22)); top.setStyle("-fx-background-color:linear-gradient(to right,#0f172a,#17365d);");
        Label brand = new Label("Remote JavaFX Client"); brand.setStyle("-fx-text-fill:white;-fx-font-size:18px;-fx-font-weight:bold;");
        Label version = new Label("v" + updater.currentVersion()); version.setStyle("-fx-text-fill:#bfdbfe;-fx-font-size:11px;");
        Region push = new Region(); HBox.setHgrow(push,Priority.ALWAYS);
        Button update = new Button("Checking updates…");
        update.setVisible(false); update.setManaged(false);
        update.setStyle("-fx-background-color:#16a34a;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;-fx-padding:8 12;");
        Button logout = new Button("Sign out"); logout.setStyle("-fx-background-color:rgba(255,255,255,.12);-fx-text-fill:white;-fx-border-color:rgba(255,255,255,.25);-fx-border-radius:9;-fx-background-radius:9;-fx-padding:8 12;");
        logout.setOnAction(e->{currentUsername=null;currentPassword=null;currentBootstrap=null;showLogin();checkCloud();});
        HBox cloudBox = new HBox(cloudStatus); cloudBox.setStyle("-fx-background-color:rgba(255,255,255,.10);-fx-background-radius:10;-fx-padding:5 8;");
        top.getChildren().addAll(brand,version,push,update,cloudBox,logout); page.setTop(top);
        checkForUpdates(update);

        VBox content = new VBox(18); content.setPadding(new Insets(26));
        HBox profile = new HBox(18); profile.setAlignment(Pos.CENTER_LEFT); profile.setPadding(new Insets(22)); profile.setStyle("-fx-background-color:white;-fx-background-radius:17;-fx-border-color:#e7edf5;-fx-border-radius:17;");
        VBox identity = new VBox(5); Label welcome=new Label("Welcome, "+name); welcome.setStyle("-fx-font-size:24px;-fx-font-weight:bold;-fx-text-fill:#14213d;"); Label handle=new Label("@"+username+(email.isBlank()?"":"  ·  "+email)); handle.setStyle("-fx-text-fill:#64748b;"); Label badge=new Label(tier); badge.setStyle("-fx-background-color:#e8f0ff;-fx-text-fill:#1d4ed8;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:6 10;"); Button editProfile=new Button("Edit profile"); editProfile.setStyle(PRIMARY); editProfile.setOnAction(e->showProfileDialog()); identity.getChildren().addAll(welcome,handle,badge,spacer(5),editProfile); profile.getChildren().addAll(avatarNode(name,avatarData,86),identity);

        Label featureTitle = new Label("Your enabled features"); featureTitle.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#14213d;");
        TilePane featuresPane = new TilePane(); featuresPane.setHgap(12); featuresPane.setVgap(12); featuresPane.setPrefColumns(2);
        Iterator<Map.Entry<String, JsonNode>> it=root.path("features").fields();
        while(it.hasNext()){
            Map.Entry<String,JsonNode> f=it.next();
            if(f.getValue().asBoolean(false)){
                String key=f.getKey();
                Button b=new Button("✓  "+pretty(key));
                b.setPrefWidth(320); b.setAlignment(Pos.CENTER_LEFT);
                b.setStyle("-fx-background-color:white;-fx-text-fill:#14213d;-fx-font-weight:bold;-fx-background-radius:13;-fx-border-color:#e2e8f0;-fx-border-radius:13;-fx-padding:16 18;");
                b.setOnAction(e->showFeature(key));
                featuresPane.getChildren().add(b);
            }
        }
        if(featuresPane.getChildren().isEmpty()){Label none=new Label("No features are enabled for this account."); none.setStyle("-fx-text-fill:#64748b;"); featuresPane.getChildren().add(none);}
        content.getChildren().addAll(profile,featureTitle,featuresPane);
        ScrollPane scroll=new ScrollPane(content); scroll.setFitToWidth(true); scroll.setStyle("-fx-background:#f4f7fb;-fx-background-color:#f4f7fb;"); page.setCenter(scroll); stage.setScene(new Scene(page,960,650));
    }

    private void showFeature(String key) {
        BorderPane page = new BorderPane(); page.setStyle("-fx-background-color:#f4f7fb;");
        HBox top = new HBox(12); top.setAlignment(Pos.CENTER_LEFT); top.setPadding(new Insets(14,22,14,22)); top.setStyle("-fx-background-color:linear-gradient(to right,#0f172a,#17365d);");
        Button back = new Button("← Back"); back.setStyle("-fx-background-color:rgba(255,255,255,.12);-fx-text-fill:white;-fx-border-color:rgba(255,255,255,.25);-fx-border-radius:9;-fx-background-radius:9;-fx-padding:8 12;");
        back.setOnAction(e -> showMain(currentBootstrap));
        Label title = new Label(pretty(key)); title.setStyle("-fx-text-fill:white;-fx-font-size:18px;-fx-font-weight:bold;");
        Region push = new Region(); HBox.setHgrow(push, Priority.ALWAYS);
        HBox cloudBox = new HBox(cloudStatus); cloudBox.setStyle("-fx-background-color:rgba(255,255,255,.10);-fx-background-radius:10;-fx-padding:5 8;");
        top.getChildren().addAll(back,title,push,cloudBox); page.setTop(top);
        Node feature = FeatureViews.create(key,currentBootstrap,portal,stage,currentUsername,currentPassword);
        ScrollPane scroll = new ScrollPane(feature); scroll.setFitToWidth(true); scroll.setStyle("-fx-background:#f4f7fb;-fx-background-color:#f4f7fb;"); page.setCenter(scroll);
        stage.setScene(new Scene(page,960,650));
    }

    private void checkForUpdates(Button button) {
        CompletableFuture.supplyAsync(() -> { try { return updater.check(); } catch (Exception e) { return null; } }).thenAccept(info -> Platform.runLater(() -> {
            if (info == null || !info.updateAvailable()) return;
            button.setText("Update available · v" + info.latestVersion() + " — Update app"); button.setVisible(true); button.setManaged(true); button.setOnAction(e -> installUpdate(info, button));
        }));
    }

    private void installUpdate(UpdateService.UpdateInfo info, Button button) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION); confirm.initOwner(stage); confirm.setTitle("Update Remote JavaFX Client"); confirm.setHeaderText("Update to version " + info.latestVersion() + "?"); confirm.setContentText("The installer will download, verify its SHA-256 checksum, close this app, install the update, and relaunch the client. Windows may ask for permission.");
        Optional<ButtonType> result = confirm.showAndWait(); if (result.isEmpty() || result.get() != ButtonType.OK) return;
        button.setDisable(true); button.setText("Downloading update…");
        CompletableFuture.supplyAsync(() -> { try { return updater.downloadAndVerify(info); } catch (Exception ex) { throw new RuntimeException(ex); } }).whenComplete((msi, err) -> Platform.runLater(() -> {
            if (err != null) { button.setDisable(false); button.setText("Update available · v" + info.latestVersion() + " — Update app"); Throwable c = err.getCause()==null?err:err.getCause(); new Alert(Alert.AlertType.ERROR, "Update failed: " + (c.getMessage()==null?c.toString():c.getMessage())).showAndWait(); return; }
            try { button.setText("Installing update…"); updater.launchInstallerAndRelaunch(msi); Platform.exit(); }
            catch (Exception ex) { button.setDisable(false); button.setText("Update available · v" + info.latestVersion() + " — Update app"); new Alert(Alert.AlertType.ERROR, "Could not launch updater: " + ex.getMessage()).showAndWait(); }
        }));
    }

    private void showProfileDialog() {
        JsonNode account=currentBootstrap.path("user"); Dialog<Void> d=new Dialog<>(); d.setTitle("Edit profile"); d.initOwner(stage); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        VBox box=new VBox(12); box.setPadding(new Insets(12)); box.setPrefWidth(470);
        TextField name=new TextField(account.path("displayName").asText("")); name.setStyle(INPUT); TextField email=new TextField(account.path("email").asText("")); email.setStyle(INPUT); Label photoName=new Label("Current profile picture");
        final String[] avatarData={account.path("avatarData").isNull()?null:account.path("avatarData").asText(null)}; StackPane avatarWrap=new StackPane(avatarNode(name.getText(),avatarData[0],96)); Button choose=new Button("Choose picture"); Button remove=new Button("Remove picture");
        choose.setOnAction(e->{FileChooser fc=new FileChooser(); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images","*.png","*.jpg","*.jpeg","*.webp")); var f=fc.showOpenDialog(stage); if(f!=null){try{byte[] bytes=Files.readAllBytes(f.toPath()); if(bytes.length>1_500_000) throw new IllegalArgumentException("Image is too large. Choose an image under 1.5 MB."); String type=Files.probeContentType(f.toPath()); if(type==null) type="image/jpeg"; avatarData[0]="data:"+type+";base64,"+Base64.getEncoder().encodeToString(bytes); avatarWrap.getChildren().setAll(avatarNode(name.getText(),avatarData[0],96)); photoName.setText(f.getName());}catch(Exception ex){new Alert(Alert.AlertType.ERROR,ex.getMessage()).showAndWait();}}});
        remove.setOnAction(e->{avatarData[0]=null; avatarWrap.getChildren().setAll(avatarNode(name.getText(),null,96)); photoName.setText("No profile picture");}); HBox photoButtons=new HBox(8,choose,remove);
        Button save=new Button("Save profile"); save.setStyle(PRIMARY); Label status=new Label();
        save.setOnAction(e->{save.setDisable(true); Map<String,Object> payload=Map.of("displayName",name.getText(),"email",email.getText(),"avatarData",avatarData[0]==null?"":avatarData[0]); CompletableFuture.supplyAsync(()->{try{return portal.updateProfile(currentUsername,currentPassword,json.writeValueAsString(payload));}catch(Exception ex){throw new RuntimeException(ex);}}).whenComplete((body,err)->Platform.runLater(()->{save.setDisable(false); if(err!=null){Throwable c=err.getCause()==null?err:err.getCause();status.setText(c.getMessage());return;} try{JsonNode updated=json.readTree(body); ((com.fasterxml.jackson.databind.node.ObjectNode)currentBootstrap).set("user",updated); showMain(currentBootstrap); status.setText("Profile saved.");}catch(Exception ex){status.setText("Could not refresh profile.");}}));});
        PasswordField current=new PasswordField(); current.setPromptText("Current password"); current.setStyle(INPUT); PasswordField next=new PasswordField(); next.setPromptText("New password (8+ characters)"); next.setStyle(INPUT); Button change=new Button("Change password"); Label passStatus=new Label();
        change.setOnAction(e->{Map<String,String> payload=Map.of("currentPassword",current.getText(),"newPassword",next.getText()); change.setDisable(true); CompletableFuture.runAsync(()->{try{portal.changePassword(currentUsername,currentPassword,json.writeValueAsString(payload));}catch(Exception ex){throw new RuntimeException(ex);}}).whenComplete((v,err)->Platform.runLater(()->{change.setDisable(false); if(err!=null){Throwable c=err.getCause()==null?err:err.getCause();passStatus.setText(c.getMessage());return;} currentPassword=next.getText(); current.clear(); next.clear(); passStatus.setText("Password changed.");}));});
        box.getChildren().addAll(new Label("Profile picture"),avatarWrap,photoName,photoButtons,new Separator(),fieldLabel("Display name"),name,fieldLabel("Email"),email,save,status,new Separator(),new Label("Change password"),current,next,change,passStatus); ScrollPane pane=new ScrollPane(box); pane.setFitToWidth(true); d.getDialogPane().setContent(pane); d.showAndWait();
    }

    private static Node avatarNode(String name,String dataUrl,double size){StackPane holder=new StackPane(); holder.setMinSize(size,size); holder.setMaxSize(size,size); Circle bg=new Circle(size/2,Color.web("#2563eb")); Label initials=new Label(initials(name)); initials.setStyle("-fx-text-fill:white;-fx-font-size:"+(size*.31)+"px;-fx-font-weight:bold;"); holder.getChildren().addAll(bg,initials); Image image=decodeImage(dataUrl); if(image!=null){ImageView iv=new ImageView(image); iv.setFitWidth(size); iv.setFitHeight(size); iv.setPreserveRatio(false); iv.setClip(new Circle(size/2,size/2,size/2)); holder.getChildren().add(iv);} return holder;}
    private static Image decodeImage(String dataUrl){try{if(dataUrl==null||dataUrl.isBlank())return null;int comma=dataUrl.indexOf(',');if(comma<0)return null;return new Image(new ByteArrayInputStream(Base64.getDecoder().decode(dataUrl.substring(comma+1).getBytes(StandardCharsets.US_ASCII))));}catch(Exception e){return null;}}
    private static String initials(String name){if(name==null||name.isBlank())return"?";String[] parts=name.trim().split("\\s+");StringBuilder s=new StringBuilder();for(int i=0;i<Math.min(2,parts.length);i++)if(!parts[i].isBlank())s.append(Character.toUpperCase(parts[i].charAt(0)));return s.isEmpty()?"?":s.toString();}
    private static Label fieldLabel(String text){Label l=new Label(text);l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;-fx-font-size:12px;");return l;}
    private static Region spacer(double h){Region r=new Region();r.setMinHeight(h);return r;}
    private static String pretty(String key){String[] parts=key.toLowerCase().split("_");StringBuilder out=new StringBuilder();for(String p:parts)if(!p.isBlank())out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');return out.toString().trim();}
    public static void main(String[] args){launch(args);}
}

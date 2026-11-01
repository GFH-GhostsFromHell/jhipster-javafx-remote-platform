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

    private static final String BG = "-fx-background-color:linear-gradient(to bottom right,#050b14 0%,#0b2239 56%,#0e7490 125%);";
    private static final String CARD = "-fx-background-color:rgba(255,255,255,.98);-fx-background-radius:24;-fx-border-color:rgba(148,163,184,.30);-fx-border-radius:24;-fx-effect:dropshadow(gaussian,rgba(0,0,0,.30),42,0,0,14);";
    private static final String INPUT = "-fx-background-color:#ffffff;-fx-border-color:#c7d3e0;-fx-border-radius:11;-fx-background-radius:11;-fx-padding:12 14;-fx-font-size:14px;-fx-text-fill:#0f172a;-fx-prompt-text-fill:#94a3b8;";
    private static final String PRIMARY = "-fx-background-color:linear-gradient(to right,#1d4ed8,#0e7490);-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:11;-fx-padding:12 18;-fx-font-size:14px;-fx-cursor:hand;";
    private static final String GHOST = "-fx-background-color:#edf6fb;-fx-text-fill:#0f4c81;-fx-border-color:#c7d9e8;-fx-border-radius:10;-fx-background-radius:10;-fx-padding:9 13;-fx-font-weight:bold;-fx-cursor:hand;";
    private static final String TOPBAR = "-fx-background-color:linear-gradient(to right,#07111f,#0b2743 62%,#0e4f66);-fx-border-color:transparent transparent #155e75 transparent;-fx-border-width:0 0 1 0;";

    @Override public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Electrical Workbench — CEC 2024");
        stage.setMinWidth(920);
        stage.setMinHeight(620);
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
        BorderPane root = new BorderPane();
        root.setStyle(BG);

        VBox hero = new VBox(18);
        hero.setPadding(new Insets(62, 48, 62, 64));
        hero.setAlignment(Pos.CENTER_LEFT);

        HBox brandRow = new HBox(10);
        brandRow.setAlignment(Pos.CENTER_LEFT);
        Label bolt = new Label("⚡");
        bolt.setStyle("-fx-text-fill:#67e8f9;-fx-font-size:25px;");
        VBox brandCopy = new VBox(0);
        Label product = new Label("ELECTRICAL WORKBENCH");
        product.setStyle("-fx-text-fill:white;-fx-font-size:14px;-fx-font-weight:bold;");
        Label edition = new Label("CEC 2024  •  Desktop + Cloud");
        edition.setStyle("-fx-text-fill:#7dd3fc;-fx-font-size:10px;-fx-font-weight:bold;");
        brandCopy.getChildren().addAll(product, edition);
        brandRow.getChildren().addAll(bolt, brandCopy);

        Label heroTitle = new Label("Professional electrical\ncalculators, one clean workspace.");
        heroTitle.setStyle("-fx-text-fill:white;-fx-font-size:36px;-fx-font-weight:bold;-fx-line-spacing:-2px;");
        heroTitle.setWrapText(true);

        Label heroText = new Label("Service calculations, transformers, motors, wiring methods, fire alarm, patient care, welders and field-reference tools — organized by CEC section.");
        heroText.setMaxWidth(470);
        heroText.setWrapText(true);
        heroText.setStyle("-fx-text-fill:#cbd5e1;-fx-font-size:14px;-fx-line-spacing:4px;");

        HBox chips = new HBox(8);
        chips.getChildren().addAll(chip("CEC 2024"),chip("Worksheet flow"),chip("Desktop + Web"));

        HBox cloudBox = new HBox(cloudStatus);
        cloudBox.setAlignment(Pos.CENTER_LEFT);
        cloudBox.setMaxWidth(470);
        cloudBox.setStyle("-fx-background-color:rgba(255,255,255,.08);-fx-border-color:rgba(103,232,249,.18);-fx-border-radius:12;-fx-background-radius:12;-fx-padding:8 11;");

        Region line = new Region();
        line.setPrefHeight(2);
        line.setMaxWidth(160);
        line.setStyle("-fx-background-color:linear-gradient(to right,#22d3ee,#3b82f6,transparent);");
        hero.getChildren().addAll(brandRow, line, heroTitle, heroText, chips, spacer(8), cloudBox);

        VBox card = new VBox(13);
        card.setPadding(new Insets(40));
        card.setMaxWidth(410);
        card.setStyle(CARD);
        Label overline = new Label("SECURE ACCOUNT ACCESS");
        overline.setStyle("-fx-text-fill:#0e7490;-fx-font-size:10px;-fx-font-weight:bold;");
        Label title = new Label("Welcome back");
        title.setStyle("-fx-font-size:28px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label subtitle = new Label("Use the same account as the web portal.");
        subtitle.setStyle("-fx-text-fill:#64748b;-fx-font-size:13px;");
        TextField username = new TextField();
        username.setPromptText("Enter your username");
        username.setStyle(INPUT);
        PasswordField password = new PasswordField();
        password.setPromptText("Enter your password");
        password.setStyle(INPUT);
        Label message = new Label();
        message.setWrapText(true);
        message.setStyle("-fx-text-fill:#b91c1c;-fx-font-size:12px;");
        Button login = new Button("Sign in to Electrical Workbench");
        login.setMaxWidth(Double.MAX_VALUE);
        login.setDefaultButton(true);
        login.setStyle(PRIMARY);
        Label secure = new Label("Cloud-authenticated • account features load automatically");
        secure.setWrapText(true);
        secure.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:11px;");

        login.setOnAction(e -> {
            login.setDisable(true); login.setText("Connecting securely…"); message.setText("");
            String u = username.getText().trim(); String p = password.getText();
            CompletableFuture.supplyAsync(() -> { try { return portal.bootstrap(u, p); } catch (Exception ex) { throw new RuntimeException(ex); } })
                .whenComplete((body, err) -> Platform.runLater(() -> {
                    login.setDisable(false); login.setText("Sign in to Electrical Workbench");
                    if (err != null) { cloudStatus.setState(CloudConnectionState.DEGRADED, "Sign-in request failed"); Throwable c=err.getCause()==null?err:err.getCause(); message.setText(c.getMessage()==null?"Could not sign in.":c.getMessage()); return; }
                    try { currentUsername=u; currentPassword=p; currentBootstrap=json.readTree(body); cloudStatus.setState(CloudConnectionState.AUTHENTICATED, "Authenticated as "+u); showMain(currentBootstrap); }
                    catch (Exception parse) { message.setText("The cloud returned an invalid response."); }
                }));
        });

        card.getChildren().addAll(overline,title,subtitle,spacer(5),fieldLabel("Username"),username,fieldLabel("Password"),password,spacer(5),login,secure,message);
        StackPane cardWrap = new StackPane(card);
        cardWrap.setPadding(new Insets(48,64,48,34));
        cardWrap.setAlignment(Pos.CENTER);

        GridPane layout = new GridPane();
        ColumnConstraints left=new ColumnConstraints(); left.setPercentWidth(56);
        ColumnConstraints right=new ColumnConstraints(); right.setPercentWidth(44);
        layout.getColumnConstraints().addAll(left,right);
        layout.add(hero,0,0); layout.add(cardWrap,1,0);
        GridPane.setVgrow(hero,Priority.ALWAYS); GridPane.setVgrow(cardWrap,Priority.ALWAYS);
        root.setCenter(layout);
        stage.setScene(new Scene(root,1080,690));
    }

    private void showMain(JsonNode root) {
        JsonNode account = root.path("user");
        String username = account.path("username").asText("user");
        String name = account.path("displayName").asText(username);
        String email = account.path("email").asText("");
        String tier = account.path("subscriptionTier").asText("TIER_1").replace('_',' ');
        String avatarData = account.path("avatarData").isNull()?null:account.path("avatarData").asText(null);

        BorderPane page = new BorderPane();
        page.setStyle("-fx-background-color:#f3f6fa;");
        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(13,22,13,22));
        top.setStyle(TOPBAR);
        Label mark = new Label("⚡"); mark.setStyle("-fx-text-fill:#67e8f9;-fx-font-size:20px;");
        VBox brandBox=new VBox(0);
        Label brand = new Label("Electrical Workbench"); brand.setStyle("-fx-text-fill:white;-fx-font-size:17px;-fx-font-weight:bold;");
        Label brandSub = new Label("CEC 2024 Professional Calculator Suite"); brandSub.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:9px;");
        brandBox.getChildren().addAll(brand,brandSub);
        Label version = new Label("v" + updater.currentVersion()); version.setStyle("-fx-text-fill:#67e8f9;-fx-font-size:10px;-fx-font-weight:bold;");
        Region push = new Region(); HBox.setHgrow(push,Priority.ALWAYS);
        Button update = new Button("Checking updates…");
        update.setVisible(false); update.setManaged(false);
        update.setStyle("-fx-background-color:#0f766e;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:10;-fx-padding:8 12;");
        Button logout = new Button("Sign out"); logout.setStyle("-fx-background-color:rgba(255,255,255,.08);-fx-text-fill:#e2e8f0;-fx-border-color:rgba(255,255,255,.20);-fx-border-radius:10;-fx-background-radius:10;-fx-padding:8 12;");
        logout.setOnAction(e->{currentUsername=null;currentPassword=null;currentBootstrap=null;showLogin();checkCloud();});
        HBox cloudBox = new HBox(cloudStatus); cloudBox.setStyle("-fx-background-color:rgba(255,255,255,.07);-fx-background-radius:10;-fx-padding:5 8;");
        top.getChildren().addAll(mark,brandBox,version,push,update,cloudBox,logout);
        page.setTop(top);
        checkForUpdates(update);

        VBox content = new VBox(18);
        content.setPadding(new Insets(26,30,30,30));

        HBox profile = new HBox(20);
        profile.setAlignment(Pos.CENTER_LEFT);
        profile.setPadding(new Insets(22));
        profile.setStyle("-fx-background-color:white;-fx-background-radius:18;-fx-border-color:#dce6ef;-fx-border-radius:18;-fx-effect:dropshadow(gaussian,rgba(15,23,42,.05),18,0,0,5);");
        VBox identity = new VBox(5);
        Label eyebrow=new Label("ACCOUNT WORKSPACE"); eyebrow.setStyle("-fx-text-fill:#0e7490;-fx-font-size:9px;-fx-font-weight:bold;");
        Label welcome=new Label("Welcome, "+name); welcome.setStyle("-fx-font-size:24px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label handle=new Label("@"+username+(email.isBlank()?"":"  ·  "+email)); handle.setStyle("-fx-text-fill:#64748b;");
        Label badge=new Label(tier); badge.setStyle("-fx-background-color:#e0f2fe;-fx-text-fill:#075985;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:6 10;");
        Button editProfile=new Button("Profile & settings"); editProfile.setStyle(GHOST); editProfile.setOnAction(e->showProfileDialog());
        identity.getChildren().addAll(eyebrow,welcome,handle,badge,spacer(5),editProfile);
        Region profilePush=new Region(); HBox.setHgrow(profilePush,Priority.ALWAYS);
        VBox quick=new VBox(5); quick.setAlignment(Pos.CENTER_RIGHT);
        Label q1=new Label("CEC 2024"); q1.setStyle("-fx-text-fill:#0f172a;-fx-font-weight:bold;");
        Label q2=new Label("Cloud-connected calculator workspace"); q2.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:11px;");
        quick.getChildren().addAll(q1,q2);
        profile.getChildren().addAll(avatarNode(name,avatarData,82),identity,profilePush,quick);

        Label featureTitle = new Label("Workspace"); featureTitle.setStyle("-fx-font-size:19px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label featureSub = new Label("Open a tool below. Your account only shows features that are currently enabled."); featureSub.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");
        VBox featureHeading=new VBox(2,featureTitle,featureSub);

        TilePane featuresPane = new TilePane();
        featuresPane.setHgap(13); featuresPane.setVgap(13); featuresPane.setPrefColumns(2);
        Iterator<Map.Entry<String, JsonNode>> it=root.path("features").fields();
        while(it.hasNext()){
            Map.Entry<String,JsonNode> f=it.next();
            if(f.getValue().asBoolean(false)){
                String key=f.getKey();
                String icon=key.contains("CALCULATOR")?"∑":key.contains("REPORT")?"▤":"⚙";
                Button b=new Button(icon+"   "+pretty(key));
                b.setPrefWidth(360); b.setPrefHeight(58); b.setAlignment(Pos.CENTER_LEFT);
                b.setStyle("-fx-background-color:white;-fx-text-fill:#0f172a;-fx-font-weight:bold;-fx-background-radius:14;-fx-border-color:#dce6ef;-fx-border-radius:14;-fx-padding:16 18;-fx-font-size:13px;-fx-cursor:hand;");
                b.setOnAction(e->showFeature(key));
                featuresPane.getChildren().add(b);
            }
        }
        if(featuresPane.getChildren().isEmpty()){Label none=new Label("No features are enabled for this account."); none.setStyle("-fx-text-fill:#64748b;"); featuresPane.getChildren().add(none);}

        content.getChildren().addAll(profile,featureHeading,featuresPane);
        ScrollPane scroll=new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:#f3f6fa;-fx-background-color:#f3f6fa;");
        page.setCenter(scroll);
        stage.setScene(new Scene(page,1120,720));
    }

    private void showFeature(String key) {
        BorderPane page = new BorderPane(); page.setStyle("-fx-background-color:#f3f6fa;");
        HBox top = new HBox(12); top.setAlignment(Pos.CENTER_LEFT); top.setPadding(new Insets(13,22,13,22)); top.setStyle(TOPBAR);
        Button back = new Button("←  Workspace"); back.setStyle("-fx-background-color:rgba(255,255,255,.08);-fx-text-fill:white;-fx-border-color:rgba(255,255,255,.20);-fx-border-radius:10;-fx-background-radius:10;-fx-padding:8 12;");
        back.setOnAction(e -> showMain(currentBootstrap));
        Label title = new Label(pretty(key)); title.setStyle("-fx-text-fill:white;-fx-font-size:18px;-fx-font-weight:bold;");
        Region push = new Region(); HBox.setHgrow(push, Priority.ALWAYS);
        Label edition=new Label("CEC 2024"); edition.setStyle("-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:16;-fx-padding:5 9;-fx-font-size:10px;");
        HBox cloudBox = new HBox(cloudStatus); cloudBox.setStyle("-fx-background-color:rgba(255,255,255,.07);-fx-background-radius:10;-fx-padding:5 8;");
        top.getChildren().addAll(back,title,push,edition,cloudBox); page.setTop(top);
        Node feature = FeatureViews.create(key,currentBootstrap,portal,stage,currentUsername,currentPassword);
        ScrollPane scroll = new ScrollPane(feature); scroll.setFitToWidth(true); scroll.setStyle("-fx-background:#f3f6fa;-fx-background-color:#f3f6fa;"); page.setCenter(scroll);
        stage.setScene(new Scene(page,1180,760));
    }

    private void checkForUpdates(Button button) {
        CompletableFuture.supplyAsync(() -> { try { return updater.check(); } catch (Exception e) { return null; } }).thenAccept(info -> Platform.runLater(() -> {
            if (info == null || !info.updateAvailable()) return;
            button.setText("Update available · v" + info.latestVersion() + " — Update app"); button.setVisible(true); button.setManaged(true); button.setOnAction(e -> installUpdate(info, button));
        }));
    }

    private void installUpdate(UpdateService.UpdateInfo info, Button button) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION); confirm.initOwner(stage); confirm.setTitle("Update Electrical Workbench"); confirm.setHeaderText("Update to version " + info.latestVersion() + "?"); confirm.setContentText("The installer will download, verify its SHA-256 checksum, close this app, install the update, and relaunch the client. Windows may ask for permission.");
        Optional<ButtonType> result = confirm.showAndWait(); if (result.isEmpty() || result.get() != ButtonType.OK) return;
        button.setDisable(true); button.setText("Downloading update…");
        CompletableFuture.supplyAsync(() -> { try { return updater.downloadAndVerify(info); } catch (Exception ex) { throw new RuntimeException(ex); } }).whenComplete((msi, err) -> Platform.runLater(() -> {
            if (err != null) { button.setDisable(false); button.setText("Update available · v" + info.latestVersion() + " — Update app"); Throwable c = err.getCause()==null?err:err.getCause(); new Alert(Alert.AlertType.ERROR, "Update failed: " + (c.getMessage()==null?c.toString():c.getMessage())).showAndWait(); return; }
            try { button.setText("Installing update…"); updater.launchInstallerAndRelaunch(msi); Platform.exit(); }
            catch (Exception ex) { button.setDisable(false); button.setText("Update available · v" + info.latestVersion() + " — Update app"); new Alert(Alert.AlertType.ERROR, "Could not launch updater: " + ex.getMessage()).showAndWait(); }
        }));
    }

    private void showProfileDialog() {
        JsonNode account=currentBootstrap.path("user"); Dialog<Void> d=new Dialog<>(); d.setTitle("Profile & settings"); d.initOwner(stage); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        VBox box=new VBox(12); box.setPadding(new Insets(14)); box.setPrefWidth(480); box.setStyle("-fx-background-color:#f8fafc;");
        TextField name=new TextField(account.path("displayName").asText("")); name.setStyle(INPUT); TextField email=new TextField(account.path("email").asText("")); email.setStyle(INPUT); Label photoName=new Label("Current profile picture");
        final String[] avatarData={account.path("avatarData").isNull()?null:account.path("avatarData").asText(null)}; StackPane avatarWrap=new StackPane(avatarNode(name.getText(),avatarData[0],96)); Button choose=new Button("Choose picture"); choose.setStyle(GHOST); Button remove=new Button("Remove picture"); remove.setStyle(GHOST);
        choose.setOnAction(e->{FileChooser fc=new FileChooser(); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images","*.png","*.jpg","*.jpeg","*.webp")); var f=fc.showOpenDialog(stage); if(f!=null){try{byte[] bytes=Files.readAllBytes(f.toPath()); if(bytes.length>1_500_000) throw new IllegalArgumentException("Image is too large. Choose an image under 1.5 MB."); String type=Files.probeContentType(f.toPath()); if(type==null) type="image/jpeg"; avatarData[0]="data:"+type+";base64,"+Base64.getEncoder().encodeToString(bytes); avatarWrap.getChildren().setAll(avatarNode(name.getText(),avatarData[0],96)); photoName.setText(f.getName());}catch(Exception ex){new Alert(Alert.AlertType.ERROR,ex.getMessage()).showAndWait();}}});
        remove.setOnAction(e->{avatarData[0]=null; avatarWrap.getChildren().setAll(avatarNode(name.getText(),null,96)); photoName.setText("No profile picture");}); HBox photoButtons=new HBox(8,choose,remove);
        Button save=new Button("Save profile"); save.setStyle(PRIMARY); Label status=new Label();
        save.setOnAction(e->{save.setDisable(true); Map<String,Object> payload=Map.of("displayName",name.getText(),"email",email.getText(),"avatarData",avatarData[0]==null?"":avatarData[0]); CompletableFuture.supplyAsync(()->{try{return portal.updateProfile(currentUsername,currentPassword,json.writeValueAsString(payload));}catch(Exception ex){throw new RuntimeException(ex);}}).whenComplete((body,err)->Platform.runLater(()->{save.setDisable(false); if(err!=null){Throwable c=err.getCause()==null?err:err.getCause();status.setText(c.getMessage());return;} try{JsonNode updated=json.readTree(body); ((com.fasterxml.jackson.databind.node.ObjectNode)currentBootstrap).set("user",updated); showMain(currentBootstrap); status.setText("Profile saved.");}catch(Exception ex){status.setText("Could not refresh profile.");}}));});
        PasswordField current=new PasswordField(); current.setPromptText("Current password"); current.setStyle(INPUT); PasswordField next=new PasswordField(); next.setPromptText("New password (8+ characters)"); next.setStyle(INPUT); Button change=new Button("Change password"); change.setStyle(GHOST); Label passStatus=new Label();
        change.setOnAction(e->{Map<String,String> payload=Map.of("currentPassword",current.getText(),"newPassword",next.getText()); change.setDisable(true); CompletableFuture.runAsync(()->{try{portal.changePassword(currentUsername,currentPassword,json.writeValueAsString(payload));}catch(Exception ex){throw new RuntimeException(ex);}}).whenComplete((v,err)->Platform.runLater(()->{change.setDisable(false); if(err!=null){Throwable c=err.getCause()==null?err:err.getCause();passStatus.setText(c.getMessage());return;} currentPassword=next.getText(); current.clear(); next.clear(); passStatus.setText("Password changed.");}));});
        Label profileHeading=new Label("Account profile"); profileHeading.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label securityHeading=new Label("Security"); securityHeading.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        box.getChildren().addAll(profileHeading,new Label("Profile picture"),avatarWrap,photoName,photoButtons,new Separator(),fieldLabel("Display name"),name,fieldLabel("Email"),email,save,status,new Separator(),securityHeading,current,next,change,passStatus); ScrollPane pane=new ScrollPane(box); pane.setFitToWidth(true); d.getDialogPane().setContent(pane); d.showAndWait();
    }

    private static Node chip(String text){Label l=new Label(text);l.setStyle("-fx-text-fill:#dbeafe;-fx-background-color:rgba(14,116,144,.24);-fx-border-color:rgba(103,232,249,.22);-fx-border-radius:18;-fx-background-radius:18;-fx-padding:6 10;-fx-font-size:10px;-fx-font-weight:bold;");return l;}
    private static Node avatarNode(String name,String dataUrl,double size){StackPane holder=new StackPane(); holder.setMinSize(size,size); holder.setMaxSize(size,size); Circle bg=new Circle(size/2,Color.web("#0e7490")); Label initials=new Label(initials(name)); initials.setStyle("-fx-text-fill:white;-fx-font-size:"+(size*.31)+"px;-fx-font-weight:bold;"); holder.getChildren().addAll(bg,initials); Image image=decodeImage(dataUrl); if(image!=null){ImageView iv=new ImageView(image); iv.setFitWidth(size); iv.setFitHeight(size); iv.setPreserveRatio(false); iv.setClip(new Circle(size/2,size/2,size/2)); holder.getChildren().add(iv);} return holder;}
    private static Image decodeImage(String dataUrl){try{if(dataUrl==null||dataUrl.isBlank())return null;int comma=dataUrl.indexOf(',');if(comma<0)return null;return new Image(new ByteArrayInputStream(Base64.getDecoder().decode(dataUrl.substring(comma+1).getBytes(StandardCharsets.US_ASCII))));}catch(Exception e){return null;}}
    private static String initials(String name){if(name==null||name.isBlank())return"?";String[] parts=name.trim().split("\\s+");StringBuilder s=new StringBuilder();for(int i=0;i<Math.min(2,parts.length);i++)if(!parts[i].isBlank())s.append(Character.toUpperCase(parts[i].charAt(0)));return s.isEmpty()?"?":s.toString();}
    private static Label fieldLabel(String text){Label l=new Label(text);l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;-fx-font-size:12px;");return l;}
    private static Region spacer(double h){Region r=new Region();r.setMinHeight(h);return r;}
    private static String pretty(String key){String[] parts=key.toLowerCase().split("_");StringBuilder out=new StringBuilder();for(String p:parts)if(!p.isBlank())out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');return out.toString().trim();}
    public static void main(String[] args){launch(args);}
}

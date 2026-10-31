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
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class RemoteDesktopApp extends Application {
    private final PortalClient portal = new PortalClient();
    private final ObjectMapper json = new ObjectMapper();
    private final CloudStatusControl cloudStatus = new CloudStatusControl();
    private Stage stage;

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
        BorderPane root = new BorderPane();
        root.setStyle(BG);

        VBox hero = new VBox(18);
        hero.setPadding(new Insets(58, 42, 58, 58));
        hero.setAlignment(Pos.CENTER_LEFT);
        Label pill = new Label("REMOTE JAVAFX PLATFORM");
        pill.setStyle("-fx-text-fill:#dbeafe;-fx-border-color:rgba(255,255,255,.25);-fx-border-radius:20;-fx-background-color:rgba(255,255,255,.08);-fx-background-radius:20;-fx-padding:7 12;-fx-font-weight:bold;-fx-font-size:11px;");
        Label heroTitle = new Label("Your desktop tools,\nconnected to the cloud.");
        heroTitle.setStyle("-fx-text-fill:white;-fx-font-size:34px;-fx-font-weight:bold;");
        heroTitle.setWrapText(true);
        Label heroText = new Label("Sign in to load your profile, subscription tier, enabled features and managed configuration.");
        heroText.setMaxWidth(390);
        heroText.setWrapText(true);
        heroText.setStyle("-fx-text-fill:#dbeafe;-fx-font-size:15px;-fx-line-spacing:4px;");
        HBox cloudBox = new HBox(cloudStatus);
        cloudBox.setAlignment(Pos.CENTER_LEFT);
        cloudBox.setStyle("-fx-background-color:rgba(255,255,255,.10);-fx-background-radius:12;-fx-padding:7 10;");
        hero.getChildren().addAll(pill, heroTitle, heroText, cloudBox);

        VBox card = new VBox(13);
        card.setPadding(new Insets(38));
        card.setMaxWidth(390);
        card.setStyle(CARD);
        Label title = new Label("Welcome back");
        title.setStyle("-fx-font-size:27px;-fx-font-weight:bold;-fx-text-fill:#14213d;");
        Label subtitle = new Label("Use the same account as your web portal.");
        subtitle.setStyle("-fx-text-fill:#64748b;-fx-font-size:13px;");

        Label userLabel = fieldLabel("Username");
        TextField username = new TextField();
        username.setPromptText("Enter your username"); username.setStyle(INPUT);
        Label passLabel = fieldLabel("Password");
        PasswordField password = new PasswordField();
        password.setPromptText("Enter your password"); password.setStyle(INPUT);
        Label message = new Label();
        message.setWrapText(true);
        message.setStyle("-fx-text-fill:#b91c1c;-fx-font-size:12px;");
        Button login = new Button("Sign in to desktop client");
        login.setMaxWidth(Double.MAX_VALUE); login.setDefaultButton(true); login.setStyle(PRIMARY);

        login.setOnAction(e -> {
            login.setDisable(true); login.setText("Connecting to cloud…"); message.setText("");
            CompletableFuture.supplyAsync(() -> {
                try { return portal.bootstrap(username.getText().trim(), password.getText()); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            }).whenComplete((body, err) -> Platform.runLater(() -> {
                login.setDisable(false); login.setText("Sign in to desktop client");
                if (err != null) {
                    cloudStatus.setState(CloudConnectionState.DEGRADED, "Sign-in request failed");
                    Throwable cause = err.getCause() == null ? err : err.getCause();
                    message.setText(cause.getMessage() == null ? "Could not sign in. The cloud service may still be waking up." : cause.getMessage());
                    return;
                }
                try {
                    JsonNode data = json.readTree(body);
                    cloudStatus.setState(CloudConnectionState.AUTHENTICATED, "Authenticated as " + username.getText().trim());
                    showMain(data);
                } catch (Exception parse) { message.setText("The cloud returned an invalid response."); }
            }));
        });

        card.getChildren().addAll(title, subtitle, spacer(4), userLabel, username, passLabel, password, spacer(4), login, message);
        StackPane cardWrap = new StackPane(card);
        cardWrap.setPadding(new Insets(42, 58, 42, 32));
        cardWrap.setAlignment(Pos.CENTER);

        GridPane layout = new GridPane();
        ColumnConstraints left = new ColumnConstraints(); left.setPercentWidth(53);
        ColumnConstraints right = new ColumnConstraints(); right.setPercentWidth(47);
        layout.getColumnConstraints().addAll(left,right);
        layout.add(hero,0,0); layout.add(cardWrap,1,0);
        GridPane.setHgrow(hero, Priority.ALWAYS); GridPane.setHgrow(cardWrap, Priority.ALWAYS);
        GridPane.setVgrow(hero, Priority.ALWAYS); GridPane.setVgrow(cardWrap, Priority.ALWAYS);
        root.setCenter(layout);
        stage.setScene(new Scene(root, 960, 620));
    }

    private void showMain(JsonNode root) {
        JsonNode account = root.path("user");
        String username = account.path("username").asText("user");
        String name = account.path("displayName").asText(username);
        String email = account.path("email").asText("");
        String tier = account.path("subscriptionTier").asText("TIER_1").replace('_',' ');
        String avatarData = account.path("avatarData").isNull() ? null : account.path("avatarData").asText(null);

        BorderPane page = new BorderPane();
        page.setStyle("-fx-background-color:#f4f7fb;");

        HBox top = new HBox(14);
        top.setAlignment(Pos.CENTER_LEFT); top.setPadding(new Insets(14,22,14,22));
        top.setStyle("-fx-background-color:linear-gradient(to right,#0f172a,#17365d);");
        Label brand = new Label("Remote JavaFX Client");
        brand.setStyle("-fx-text-fill:white;-fx-font-size:18px;-fx-font-weight:bold;");
        Region push = new Region(); HBox.setHgrow(push,Priority.ALWAYS);
        HBox topCloud = new HBox(cloudStatus); topCloud.setStyle("-fx-background-color:rgba(255,255,255,.10);-fx-background-radius:10;-fx-padding:5 8;");
        Button logout = new Button("Sign out");
        logout.setStyle("-fx-background-color:rgba(255,255,255,.12);-fx-text-fill:white;-fx-border-color:rgba(255,255,255,.25);-fx-border-radius:9;-fx-background-radius:9;-fx-padding:8 12;");
        logout.setOnAction(e -> { showLogin(); checkCloud(); });
        top.getChildren().addAll(brand,push,topCloud,logout);
        page.setTop(top);

        VBox content = new VBox(18); content.setPadding(new Insets(26));
        HBox profile = new HBox(18); profile.setAlignment(Pos.CENTER_LEFT); profile.setPadding(new Insets(22));
        profile.setStyle("-fx-background-color:white;-fx-background-radius:17;-fx-border-color:#e7edf5;-fx-border-radius:17;");
        Node avatar = avatarNode(name, avatarData, 86);
        VBox identity = new VBox(5);
        Label welcome = new Label("Welcome, " + name);
        welcome.setStyle("-fx-font-size:24px;-fx-font-weight:bold;-fx-text-fill:#14213d;");
        Label handle = new Label("@" + username + (email.isBlank()?"":"  ·  "+email));
        handle.setStyle("-fx-text-fill:#64748b;-fx-font-size:13px;");
        Label badge = new Label(tier);
        badge.setStyle("-fx-background-color:#e8f0ff;-fx-text-fill:#1d4ed8;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:6 10;-fx-font-size:11px;");
        identity.getChildren().addAll(welcome,handle,badge);
        profile.getChildren().addAll(avatar,identity);

        Label featureTitle = new Label("Your enabled features");
        featureTitle.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#14213d;");
        TilePane featuresPane = new TilePane();
        featuresPane.setHgap(12); featuresPane.setVgap(12); featuresPane.setPrefColumns(2);
        JsonNode features = root.path("features");
        Iterator<Map.Entry<String, JsonNode>> it = features.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> f = it.next();
            if (f.getValue().asBoolean(false)) {
                Button b = new Button("✓  " + pretty(f.getKey()));
                b.setPrefWidth(320); b.setAlignment(Pos.CENTER_LEFT);
                b.setStyle("-fx-background-color:white;-fx-text-fill:#14213d;-fx-font-weight:bold;-fx-background-radius:13;-fx-border-color:#e2e8f0;-fx-border-radius:13;-fx-padding:16 18;");
                b.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION, pretty(f.getKey()) + " is enabled for this account.").showAndWait());
                featuresPane.getChildren().add(b);
            }
        }
        if (featuresPane.getChildren().isEmpty()) {
            Label none = new Label("No features are enabled for this account."); none.setStyle("-fx-text-fill:#64748b;");
            featuresPane.getChildren().add(none);
        }
        content.getChildren().addAll(profile,featureTitle,featuresPane);
        ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true); scroll.setStyle("-fx-background:#f4f7fb;-fx-background-color:#f4f7fb;");
        page.setCenter(scroll);
        stage.setScene(new Scene(page, 960, 650));
    }

    private static Node avatarNode(String name, String dataUrl, double size) {
        StackPane holder = new StackPane(); holder.setMinSize(size,size); holder.setMaxSize(size,size);
        Circle bg = new Circle(size/2, Color.web("#2563eb"));
        Label initials = new Label(initials(name));
        initials.setStyle("-fx-text-fill:white;-fx-font-size:" + (size*.31) + "px;-fx-font-weight:bold;");
        holder.getChildren().addAll(bg,initials);
        Image image = decodeImage(dataUrl);
        if (image != null) {
            ImageView iv = new ImageView(image); iv.setFitWidth(size); iv.setFitHeight(size); iv.setPreserveRatio(false);
            Circle clip = new Circle(size/2,size/2,size/2); iv.setClip(clip);
            holder.getChildren().add(iv);
        }
        return holder;
    }

    private static Image decodeImage(String dataUrl) {
        try {
            if (dataUrl == null || dataUrl.isBlank()) return null;
            int comma = dataUrl.indexOf(',');
            if (comma < 0) return null;
            byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(comma+1).getBytes(StandardCharsets.US_ASCII));
            return new Image(new ByteArrayInputStream(bytes));
        } catch (Exception e) { return null; }
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        StringBuilder s = new StringBuilder();
        for (int i=0;i<Math.min(2,parts.length);i++) if (!parts[i].isBlank()) s.append(Character.toUpperCase(parts[i].charAt(0)));
        return s.isEmpty()?"?":s.toString();
    }

    private static Label fieldLabel(String text) {
        Label l = new Label(text); l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;-fx-font-size:12px;"); return l;
    }
    private static Region spacer(double h) { Region r = new Region(); r.setMinHeight(h); return r; }

    private static String pretty(String key) {
        String[] parts = key.toLowerCase().split("_");
        StringBuilder out = new StringBuilder();
        for (String p : parts) if (!p.isBlank()) out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        return out.toString().trim();
    }

    public static void main(String[] args) { launch(args); }
}

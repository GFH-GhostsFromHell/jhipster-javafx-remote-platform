package com.example.desktop;

import com.example.desktop.ui.CloudStatusControl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class ElectricalWorkbenchDesktopApp extends Application {
    private final PortalClient portal = new PortalClient();
    private final ObjectMapper json = new ObjectMapper();
    private final CloudStatusControl cloudStatus = new CloudStatusControl();
    private final UpdateService updater = new UpdateService();

    private Stage stage;
    private String currentUsername;
    private String currentPassword;
    private JsonNode currentBootstrap;
    private StackPane workspaceContent;

    private static final String BG = "-fx-background-color:linear-gradient(to bottom right,#07111f,#0b2743 60%,#0e7490);";
    private static final String INPUT = "-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-border-radius:10;-fx-background-radius:10;-fx-padding:11 12;-fx-font-size:14px;";
    private static final String PRIMARY = "-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:10;-fx-padding:11 16;";
    private static final String CARD = "-fx-background-color:white;-fx-background-radius:16;-fx-border-color:#dfe7ef;-fx-border-radius:16;";
    private static final String NAV = "-fx-background-color:transparent;-fx-text-fill:#dbeafe;-fx-font-weight:bold;-fx-font-size:13px;-fx-alignment:center-left;-fx-padding:11 13;-fx-background-radius:9;";

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Electrical Workbench Desktop");
        stage.setMinWidth(900);
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
            } catch (Exception ex) {
                cloudStatus.setState(CloudConnectionState.OFFLINE, ex.getMessage());
            }
        });
    }

    private void showLogin() {
        BorderPane root = new BorderPane();
        root.setStyle(BG);

        VBox hero = new VBox(18);
        hero.setPadding(new Insets(58));
        hero.setAlignment(Pos.CENTER_LEFT);
        Label brand = new Label("ELECTRICAL WORKBENCH");
        brand.setStyle("-fx-text-fill:#67e8f9;-fx-font-weight:bold;-fx-font-size:12px;");
        Label title = new Label("Native desktop tools\nfor electricians.");
        title.setStyle("-fx-text-fill:white;-fx-font-size:35px;-fx-font-weight:bold;");
        Label subtitle = new Label("Code news, account tools and electrical utilities stay inside the desktop app. Web-only tools open separately when you choose them.");
        subtitle.setMaxWidth(430);
        subtitle.setWrapText(true);
        subtitle.setStyle("-fx-text-fill:#cbd5e1;-fx-font-size:15px;-fx-line-spacing:4px;");
        HBox cloudWrap = new HBox(cloudStatus);
        cloudWrap.setStyle("-fx-background-color:rgba(255,255,255,.10);-fx-background-radius:10;-fx-padding:5 8;");
        hero.getChildren().addAll(brand, title, subtitle, cloudWrap);

        VBox loginCard = new VBox(12);
        loginCard.setMaxWidth(390);
        loginCard.setPadding(new Insets(34));
        loginCard.setStyle("-fx-background-color:white;-fx-background-radius:20;-fx-effect:dropshadow(gaussian,rgba(0,0,0,.24),28,0,0,10);");
        Label loginTitle = new Label("Sign in");
        loginTitle.setStyle("-fx-font-size:26px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        TextField username = new TextField();
        username.setPromptText("Username");
        username.setStyle(INPUT);
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        password.setStyle(INPUT);
        Label message = new Label();
        message.setWrapText(true);
        message.setStyle("-fx-text-fill:#b91c1c;-fx-font-size:12px;");
        Button login = new Button("Sign in to Electrical Workbench");
        login.setDefaultButton(true);
        login.setMaxWidth(Double.MAX_VALUE);
        login.setStyle(PRIMARY);

        login.setOnAction(e -> {
            login.setDisable(true);
            login.setText("Connecting…");
            message.setText("");
            String u = username.getText().trim();
            String p = password.getText();
            CompletableFuture.supplyAsync(() -> {
                try { return portal.bootstrap(u, p); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            }).whenComplete((body, err) -> Platform.runLater(() -> {
                login.setDisable(false);
                login.setText("Sign in to Electrical Workbench");
                if (err != null) {
                    Throwable c = err.getCause() == null ? err : err.getCause();
                    message.setText(c.getMessage() == null ? "Could not sign in." : c.getMessage());
                    cloudStatus.setState(CloudConnectionState.DEGRADED, "Sign-in request failed");
                    return;
                }
                try {
                    currentUsername = u;
                    currentPassword = p;
                    currentBootstrap = json.readTree(body);
                    cloudStatus.setState(CloudConnectionState.AUTHENTICATED, "Authenticated as " + u);
                    showWorkspace();
                } catch (Exception ex) {
                    message.setText("The cloud returned an invalid response.");
                }
            }));
        });

        loginCard.getChildren().addAll(loginTitle, new Label("Use your cloud account."), fieldLabel("Username"), username,
            fieldLabel("Password"), password, login, message);
        StackPane loginWrap = new StackPane(loginCard);
        loginWrap.setPadding(new Insets(40));

        GridPane grid = new GridPane();
        ColumnConstraints c1 = new ColumnConstraints(); c1.setPercentWidth(54);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setPercentWidth(46);
        grid.getColumnConstraints().addAll(c1, c2);
        grid.add(hero, 0, 0);
        grid.add(loginWrap, 1, 0);
        root.setCenter(grid);
        stage.setScene(new Scene(root, 1000, 650));
    }

    private void showWorkspace() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:#eef3f8;");

        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(12, 18, 12, 18));
        top.setStyle("-fx-background-color:linear-gradient(to right,#050b14,#0b2743,#0e7490);");
        Label brand = new Label("⚡ Electrical Workbench Desktop");
        brand.setStyle("-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:18px;");
        Label version = new Label("v" + updater.currentVersion());
        version.setStyle("-fx-text-fill:#a5f3fc;-fx-font-size:11px;");
        Region push = new Region();
        HBox.setHgrow(push, Priority.ALWAYS);
        Button update = new Button("Checking updates…");
        update.setVisible(false); update.setManaged(false);
        update.setStyle("-fx-background-color:#16a34a;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-padding:7 10;");
        HBox cloudWrap = new HBox(cloudStatus);
        cloudWrap.setStyle("-fx-background-color:rgba(255,255,255,.10);-fx-background-radius:9;-fx-padding:4 7;");
        Button logout = new Button("Sign out");
        logout.setStyle("-fx-background-color:rgba(255,255,255,.10);-fx-text-fill:white;-fx-border-color:rgba(255,255,255,.20);-fx-border-radius:8;-fx-background-radius:8;-fx-padding:7 10;");
        logout.setOnAction(e -> {
            currentUsername = null; currentPassword = null; currentBootstrap = null;
            showLogin(); checkCloud();
        });
        top.getChildren().addAll(brand, version, push, update, cloudWrap, logout);
        root.setTop(top);
        checkForUpdates(update);

        VBox sidebar = new VBox(7);
        sidebar.setPadding(new Insets(18, 12, 18, 12));
        sidebar.setPrefWidth(205);
        sidebar.setStyle("-fx-background-color:#07111f;");
        Label section = new Label("WORKSPACE");
        section.setStyle("-fx-text-fill:#67e8f9;-fx-font-size:10px;-fx-font-weight:bold;-fx-padding:0 8 7 8;");
        Button home = navButton("⌂  Home");
        Button news = navButton("⚡  Code News");
        Button tools = navButton("🧮  Tools");
        Button profile = navButton("👤  Profile");
        sidebar.getChildren().addAll(section, home, news, tools, profile, spacer(10), new Separator());
        root.setLeft(sidebar);

        workspaceContent = new StackPane();
        workspaceContent.setPadding(new Insets(22));
        root.setCenter(workspaceContent);

        home.setOnAction(e -> setWorkspace(buildHome()));
        news.setOnAction(e -> setWorkspace(buildNativeNews()));
        tools.setOnAction(e -> setWorkspace(buildTools()));
        profile.setOnAction(e -> showProfileDialog());
        setWorkspace(buildHome());

        stage.setScene(new Scene(root, 1160, 760));
    }

    private Node buildHome() {
        JsonNode account = currentBootstrap.path("user");
        String username = account.path("username").asText("user");
        String name = account.path("displayName").asText(username);
        String email = account.path("email").asText("");
        String tier = account.path("subscriptionTier").asText("TIER_1").replace('_', ' ');
        String avatarData = account.path("avatarData").isNull() ? null : account.path("avatarData").asText(null);

        VBox page = new VBox(18);
        HBox profile = new HBox(16);
        profile.setAlignment(Pos.CENTER_LEFT);
        profile.setPadding(new Insets(20));
        profile.setStyle(CARD);
        VBox who = new VBox(5);
        Label hello = new Label("Welcome, " + name);
        hello.setStyle("-fx-font-size:24px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label handle = new Label("@" + username + (email.isBlank() ? "" : "  ·  " + email));
        handle.setStyle("-fx-text-fill:#64748b;");
        Label badge = new Label(tier);
        badge.setStyle("-fx-background-color:#cffafe;-fx-text-fill:#155e75;-fx-background-radius:999;-fx-padding:5 9;-fx-font-weight:bold;");
        who.getChildren().addAll(hello, handle, badge);
        profile.getChildren().addAll(avatarNode(name, avatarData, 76), who);

        Label title = new Label("Desktop workspace");
        title.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label detail = new Label("News is now rendered natively in JavaFX. Web-only tools open in your normal browser without replacing the desktop app.");
        detail.setWrapText(true);
        detail.setStyle("-fx-text-fill:#64748b;");

        TilePane tiles = new TilePane();
        tiles.setHgap(12); tiles.setVgap(12); tiles.setPrefColumns(2);
        tiles.getChildren().addAll(
            homeTile("⚡ CEC 2024 & Ontario Code News", "Native live ESA/OESC feed", () -> setWorkspace(buildNativeNews())),
            homeTile("🧮 Electrical Calculators", "Open the cloud calculator workspace", () -> openCloudPath("/calculators.html")),
            homeTile("↪ Conduit Bending", "Open bend calculation tools", () -> openCloudPath("/bend-calcs.html")),
             homeTile("🎯 Bender Calibration", "Measure and save shoe deduct and bend gain", () -> openCloudPath("/bender-calibration.html")),
            homeTile("☁ Cloud Portal", "Account and subscription workspace", () -> openCloudPath("/portal.html"))
        );

        Label enabledTitle = new Label("Enabled account features");
        enabledTitle.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        FlowPane enabled = new FlowPane(8, 8);
        currentBootstrap.path("features").fields().forEachRemaining(entry -> {
            if (entry.getValue().asBoolean(false)) {
                Label chip = new Label("✓ " + pretty(entry.getKey()));
                chip.setStyle("-fx-background-color:white;-fx-border-color:#dbe5ef;-fx-border-radius:999;-fx-background-radius:999;-fx-padding:7 10;-fx-text-fill:#334155;-fx-font-weight:bold;");
                enabled.getChildren().add(chip);
            }
        });
        if (enabled.getChildren().isEmpty()) enabled.getChildren().add(new Label("No additional features enabled."));

        page.getChildren().addAll(profile, title, detail, tiles, enabledTitle, enabled);
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color:transparent;-fx-background:transparent;");
        return scroll;
    }

    private Node buildTools() {
        VBox page = new VBox(16);
        Label title = new Label("Electrical tools");
        title.setStyle("-fx-font-size:24px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label note = new Label("These existing tools are still hosted by the cloud backend. They open in your normal browser by design; the desktop app remains open and native.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill:#64748b;");
        TilePane tiles = new TilePane();
        tiles.setHgap(12); tiles.setVgap(12); tiles.setPrefColumns(2);
        tiles.getChildren().addAll(
            homeTile("🧮 Electrical Calculators", "Transformers, motors, voltage drop and field calculations", () -> openCloudPath("/calculators.html")),
            homeTile("↪ Conduit Bending", "Bend calculations and layout tools", () -> openCloudPath("/bend-calcs.html")),
             homeTile("🎯 Bender Calibration", "Measure and save tool-specific deduct and gain", () -> openCloudPath("/bender-calibration.html")),
            homeTile("⚡ Code News", "Native desktop ESA/OESC feed", () -> setWorkspace(buildNativeNews())),
            homeTile("☁ Cloud Portal", "Account, subscriptions and cloud workspace", () -> openCloudPath("/portal.html"))
        );
        page.getChildren().addAll(title, note, tiles);
        return page;
    }

    private Node buildNativeNews() {
        VBox page = new VBox(14);
        Label title = new Label("CEC 2024 & Ontario Code News");
        title.setStyle("-fx-font-size:24px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label description = new Label("Live ESA public OESC bulletins and code-related notices. This screen is native JavaFX; only the official source link opens in your browser.");
        description.setWrapText(true);
        description.setStyle("-fx-text-fill:#64748b;");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label checked = new Label("Loading feed…");
        checked.setStyle("-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-border-radius:999;-fx-background-radius:999;-fx-padding:6 10;-fx-font-size:11px;-fx-font-weight:bold;");
        Button refresh = new Button("Refresh");
        refresh.setStyle(PRIMARY);
        header.getChildren().addAll(checked, refresh);

        FlowPane filters = new FlowPane(7, 7);
        VBox feed = new VBox(10);
        feed.setFillWidth(true);
        ScrollPane feedScroll = new ScrollPane(feed);
        feedScroll.setFitToWidth(true);
        feedScroll.setStyle("-fx-background-color:transparent;-fx-background:transparent;");
        VBox.setVgrow(feedScroll, Priority.ALWAYS);

        String[] active = {"all"};
        JsonNode[] snapshot = {null};
        String[][] defs = {
            {"all", "All"}, {"urgent", "Urgent"}, {"important", "Important"}, {"technical", "Technical"},
            {"info", "Info"}, {"bulletin", "OESC Bulletins"}, {"code", "CEC/OESC Notices"}
        };
        ToggleGroup group = new ToggleGroup();
        for (String[] def : defs) {
            ToggleButton b = new ToggleButton(def[1]);
            b.setToggleGroup(group);
            if ("all".equals(def[0])) b.setSelected(true);
            b.setStyle("-fx-background-radius:999;-fx-border-radius:999;-fx-padding:6 10;-fx-font-size:11px;-fx-font-weight:bold;");
            b.setOnAction(e -> {
                if (!b.isSelected()) { b.setSelected(true); return; }
                active[0] = def[0];
                if (snapshot[0] != null) renderNews(snapshot[0], feed, active[0]);
            });
            filters.getChildren().add(b);
        }

        Runnable load = () -> {
            feed.getChildren().setAll(new Label("Loading electrical-code news…"));
            refresh.setDisable(true);
            CompletableFuture.supplyAsync(() -> {
                try { return portal.electricalNews(); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            }).whenComplete((body, err) -> Platform.runLater(() -> {
                refresh.setDisable(false);
                if (err != null) {
                    Throwable c = err.getCause() == null ? err : err.getCause();
                    Label failed = new Label("Unable to load the news feed: " + (c.getMessage() == null ? "request failed" : c.getMessage()));
                    failed.setWrapText(true);
                    failed.setStyle("-fx-text-fill:#b91c1c;-fx-padding:20;");
                    feed.getChildren().setAll(failed);
                    checked.setText("Feed unavailable");
                    return;
                }
                try {
                    snapshot[0] = json.readTree(body);
                    String polled = snapshot[0].path("polledAt").asText("recently");
                    checked.setText("Last polled: " + polled.replace('T', ' ').replace("Z", " UTC"));
                    renderNews(snapshot[0], feed, active[0]);
                } catch (Exception ex) {
                    feed.getChildren().setAll(new Label("The news service returned an invalid response."));
                }
            }));
        };
        refresh.setOnAction(e -> load.run());
        load.run();

        Label disclaimer = new Label("Severity labels are an Electrical Workbench aid and are not official ESA or CSA severity ratings.");
        disclaimer.setWrapText(true);
        disclaimer.setStyle("-fx-background-color:#eff6ff;-fx-border-color:#bfdbfe;-fx-border-radius:12;-fx-background-radius:12;-fx-padding:10 12;-fx-text-fill:#1e3a8a;-fx-font-size:11px;");

        page.getChildren().addAll(title, description, header, filters, feedScroll, disclaimer);
        return page;
    }

    private void renderNews(JsonNode snapshot, VBox feed, String filter) {
        feed.getChildren().clear();
        int count = 0;
        for (JsonNode item : snapshot.path("items")) {
            if (!matchesNewsFilter(item, filter)) continue;
            feed.getChildren().add(newsCard(item));
            count++;
        }
        if (count == 0) {
            Label empty = new Label("No items match this filter.");
            empty.setStyle("-fx-text-fill:#64748b;-fx-padding:22;");
            feed.getChildren().add(empty);
        }
    }

    private boolean matchesNewsFilter(JsonNode item, String filter) {
        if (filter == null || "all".equals(filter)) return true;
        String severity = item.path("severity").asText("").toLowerCase();
        String type = item.path("type").asText("");
        if ("bulletin".equals(filter)) return "OESC Bulletin".equals(type);
        if ("code".equals(filter)) return "CEC/OESC Code Notice".equals(type);
        return severity.equals(filter);
    }

    private Node newsCard(JsonNode item) {
        String severity = item.path("severity").asText("INFO");
        String severityColor = safeColor(item.path("severityColor").asText("#64748b"));
        String type = item.path("type").asText("Electrical Notice");
        String title = item.path("title").asText("Untitled notice");
        String date = item.path("dateLabel").asText("Date unavailable");
        String summary = item.path("summary").asText("");
        String source = item.path("source").asText("Electrical Safety Authority");
        String bulletin = item.path("bulletinNumber").asText("");
        String reason = item.path("severityReason").asText("");
        String url = item.path("url").asText("");

        Region stripe = new Region();
        stripe.setMinWidth(5); stripe.setPrefWidth(5); stripe.setMaxWidth(5);
        stripe.setStyle("-fx-background-color:" + severityColor + ";-fx-background-radius:16 0 0 16;");

        VBox body = new VBox(7);
        body.setPadding(new Insets(14, 16, 14, 16));
        HBox meta = new HBox(9);
        meta.setAlignment(Pos.CENTER_LEFT);
        Label typeLabel = new Label(type.toUpperCase());
        typeLabel.setStyle("-fx-text-fill:#475569;-fx-font-size:10px;-fx-font-weight:bold;");
        Label sev = new Label(severity);
        sev.setStyle("-fx-background-color:" + severityColor + ";-fx-text-fill:white;-fx-background-radius:999;-fx-padding:3 7;-fx-font-size:10px;-fx-font-weight:bold;");
        Region push = new Region(); HBox.setHgrow(push, Priority.ALWAYS);
        Label dateLabel = new Label(date);
        dateLabel.setStyle("-fx-text-fill:#64748b;-fx-font-size:11px;");
        meta.getChildren().addAll(typeLabel, sev, push, dateLabel);

        Label headline = new Label(title);
        headline.setWrapText(true);
        headline.setStyle("-fx-text-fill:#0f172a;-fx-font-size:16px;-fx-font-weight:bold;");
        Label summaryLabel = new Label(summary);
        summaryLabel.setWrapText(true);
        summaryLabel.setStyle("-fx-text-fill:#475569;-fx-font-size:12px;");
        Label sourceLabel = new Label((bulletin.isBlank() ? "" : "Bulletin " + bulletin + "  ·  ") + source);
        sourceLabel.setStyle("-fx-text-fill:#64748b;-fx-font-size:10px;");
        Label reasonLabel = new Label(reason.isBlank() ? "" : "Severity reason: " + reason);
        reasonLabel.setWrapText(true);
        reasonLabel.setStyle("-fx-text-fill:#64748b;-fx-font-size:10px;");
        Button sourceButton = new Button("Open official source");
        sourceButton.setDisable(url.isBlank());
        sourceButton.setOnAction(e -> { if (!url.isBlank()) getHostServices().showDocument(url); });
        sourceButton.setStyle("-fx-background-color:#0e7490;-fx-text-fill:white;-fx-background-radius:8;-fx-font-weight:bold;-fx-padding:6 9;");

        body.getChildren().addAll(meta, headline, summaryLabel, sourceLabel, reasonLabel, sourceButton);
        HBox card = new HBox(stripe, body);
        HBox.setHgrow(body, Priority.ALWAYS);
        card.setStyle(CARD);
        return card;
    }

    private void openCloudPath(String path) {
        String target = portal.baseUrl() + (path.startsWith("/") ? path : "/" + path);
        getHostServices().showDocument(target);
    }

    private void showProfileDialog() {
        JsonNode account = currentBootstrap.path("user");
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Profile");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox box = new VBox(11);
        box.setPadding(new Insets(12));
        box.setPrefWidth(470);
        TextField displayName = new TextField(account.path("displayName").asText(""));
        displayName.setStyle(INPUT);
        TextField email = new TextField(account.path("email").asText(""));
        email.setStyle(INPUT);
        Label photoName = new Label("Current profile picture");
        final String[] avatarData = {account.path("avatarData").isNull() ? null : account.path("avatarData").asText(null)};
        StackPane avatar = new StackPane(avatarNode(displayName.getText(), avatarData[0], 90));
        Button choose = new Button("Choose picture");
        choose.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.webp"));
            var file = fc.showOpenDialog(stage);
            if (file == null) return;
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                if (bytes.length > 1_500_000) throw new IllegalArgumentException("Choose an image under 1.5 MB.");
                String type = Files.probeContentType(file.toPath());
                if (type == null) type = "image/jpeg";
                avatarData[0] = "data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes);
                avatar.getChildren().setAll(avatarNode(displayName.getText(), avatarData[0], 90));
                photoName.setText(file.getName());
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
            }
        });
        Button remove = new Button("Remove picture");
        remove.setOnAction(e -> {
            avatarData[0] = null;
            avatar.getChildren().setAll(avatarNode(displayName.getText(), null, 90));
            photoName.setText("No profile picture");
        });
        HBox photoButtons = new HBox(8, choose, remove);
        Button save = new Button("Save profile"); save.setStyle(PRIMARY);
        Label saveStatus = new Label();
        save.setOnAction(e -> {
            save.setDisable(true);
            Map<String,Object> payload = Map.of("displayName", displayName.getText(), "email", email.getText(), "avatarData", avatarData[0] == null ? "" : avatarData[0]);
            CompletableFuture.supplyAsync(() -> {
                try { return portal.updateProfile(currentUsername, currentPassword, json.writeValueAsString(payload)); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            }).whenComplete((body, err) -> Platform.runLater(() -> {
                save.setDisable(false);
                if (err != null) {
                    Throwable c = err.getCause() == null ? err : err.getCause();
                    saveStatus.setText(c.getMessage());
                    return;
                }
                try {
                    ((ObjectNode) currentBootstrap).set("user", json.readTree(body));
                    saveStatus.setText("Profile saved.");
                    setWorkspace(buildHome());
                } catch (Exception ex) { saveStatus.setText("Profile saved, but refresh failed."); }
            }));
        });

        box.getChildren().addAll(new Label("Profile picture"), avatar, photoName, photoButtons,
            new Separator(), fieldLabel("Display name"), displayName, fieldLabel("Email"), email, save, saveStatus);
        dialog.getDialogPane().setContent(box);
        dialog.showAndWait();
    }

    private void checkForUpdates(Button button) {
        CompletableFuture.supplyAsync(() -> {
            try { return updater.check(); }
            catch (Exception ex) { return null; }
        }).thenAccept(info -> Platform.runLater(() -> {
            if (info == null || !info.updateAvailable()) return;
            button.setText("Update v" + info.latestVersion());
            button.setVisible(true); button.setManaged(true);
            button.setOnAction(e -> installUpdate(info, button));
        }));
    }

    private void installUpdate(UpdateService.UpdateInfo info, Button button) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(stage);
        confirm.setTitle("Update Electrical Workbench");
        confirm.setHeaderText("Update to version " + info.latestVersion() + "?");
        confirm.setContentText("The MSI will download, verify, install, and relaunch the desktop app.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;
        button.setDisable(true); button.setText("Downloading…");
        CompletableFuture.supplyAsync(() -> {
            try { return updater.downloadAndVerify(info); }
            catch (Exception ex) { throw new RuntimeException(ex); }
        }).whenComplete((msi, err) -> Platform.runLater(() -> {
            if (err != null) {
                button.setDisable(false); button.setText("Update v" + info.latestVersion());
                Throwable c = err.getCause() == null ? err : err.getCause();
                new Alert(Alert.AlertType.ERROR, "Update failed: " + c.getMessage()).showAndWait();
                return;
            }
            try {
                updater.launchInstallerAndRelaunch(msi);
                Platform.exit();
            } catch (Exception ex) {
                button.setDisable(false);
                new Alert(Alert.AlertType.ERROR, "Could not launch updater: " + ex.getMessage()).showAndWait();
            }
        }));
    }

    private Button navButton(String text) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setStyle(NAV);
        return b;
    }

    private Button homeTile(String title, String detail, Runnable action) {
        Button b = new Button(title + "\n" + detail);
        b.setWrapText(true);
        b.setPrefWidth(390);
        b.setPrefHeight(82);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setStyle("-fx-background-color:white;-fx-text-fill:#0f172a;-fx-font-weight:bold;-fx-background-radius:14;-fx-border-color:#dbe5ef;-fx-border-radius:14;-fx-padding:16 18;");
        b.setOnAction(e -> action.run());
        return b;
    }

    private void setWorkspace(Node node) {
        if (workspaceContent != null) workspaceContent.getChildren().setAll(node);
    }

    private static String safeColor(String color) {
        return color != null && color.matches("#[0-9A-Fa-f]{6}") ? color : "#64748b";
    }

    private static Node avatarNode(String name, String dataUrl, double size) {
        StackPane holder = new StackPane();
        holder.setMinSize(size, size); holder.setMaxSize(size, size);
        Circle bg = new Circle(size / 2, Color.web("#0e7490"));
        Label initials = new Label(initials(name));
        initials.setStyle("-fx-text-fill:white;-fx-font-size:" + (size * .30) + "px;-fx-font-weight:bold;");
        holder.getChildren().addAll(bg, initials);
        Image image = decodeImage(dataUrl);
        if (image != null) {
            ImageView iv = new ImageView(image);
            iv.setFitWidth(size); iv.setFitHeight(size); iv.setPreserveRatio(false);
            iv.setClip(new Circle(size / 2, size / 2, size / 2));
            holder.getChildren().add(iv);
        }
        return holder;
    }

    private static Image decodeImage(String dataUrl) {
        try {
            if (dataUrl == null || dataUrl.isBlank()) return null;
            int comma = dataUrl.indexOf(',');
            if (comma < 0) return null;
            return new Image(new ByteArrayInputStream(Base64.getDecoder().decode(dataUrl.substring(comma + 1).getBytes(StandardCharsets.US_ASCII))));
        } catch (Exception ex) { return null; }
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++) if (!parts[i].isBlank()) out.append(Character.toUpperCase(parts[i].charAt(0)));
        return out.isEmpty() ? "?" : out.toString();
    }

    private static Label fieldLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;-fx-font-size:12px;");
        return l;
    }

    private static Region spacer(double h) {
        Region r = new Region(); r.setMinHeight(h); return r;
    }

    private static String pretty(String key) {
        if (key == null) return "";
        String[] parts = key.toLowerCase().split("_");
        StringBuilder out = new StringBuilder();
        for (String p : parts) if (!p.isBlank()) out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        return out.toString().trim();
    }

    public static void main(String[] args) { launch(args); }
}

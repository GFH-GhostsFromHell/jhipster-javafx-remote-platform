package com.example.desktop;

import com.example.desktop.ui.CloudStatusControl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class RemoteDesktopApp extends Application {
    private final PortalClient portal = new PortalClient();
    private final ObjectMapper json = new ObjectMapper();
    private final CloudStatusControl cloudStatus = new CloudStatusControl();
    private Stage stage;

    @Override public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Remote JavaFX Client");
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
                    ok ? "Connected to " + portal.baseUrl() : "Cloud health check failed");
            } catch (Exception e) {
                cloudStatus.setState(CloudConnectionState.OFFLINE, e.getMessage());
            }
        });
    }

    private void showLogin() {
        Label title = new Label("Remote Desktop Client");
        title.setStyle("-fx-font-size:24px;-fx-font-weight:bold;");
        TextField username = new TextField(); username.setPromptText("Username");
        PasswordField password = new PasswordField(); password.setPromptText("Password");
        Label message = new Label();
        Button login = new Button("Sign in"); login.setDefaultButton(true);

        login.setOnAction(e -> {
            login.setDisable(true); message.setText("Signing in…");
            CompletableFuture.supplyAsync(() -> {
                try { return portal.bootstrap(username.getText().trim(), password.getText()); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            }).whenComplete((body, err) -> Platform.runLater(() -> {
                login.setDisable(false);
                if (err != null) {
                    cloudStatus.setState(CloudConnectionState.DEGRADED, "Authentication failed");
                    Throwable cause = err.getCause() == null ? err : err.getCause();
                    message.setText(cause.getMessage() == null ? "Sign in failed" : cause.getMessage());
                    return;
                }
                try {
                    JsonNode root = json.readTree(body);
                    cloudStatus.setState(CloudConnectionState.AUTHENTICATED, "Authenticated as " + username.getText().trim());
                    showMain(root);
                } catch (Exception parse) { message.setText("Invalid response from cloud"); }
            }));
        });

        VBox form = new VBox(12, title, new Label("Cloud: " + portal.baseUrl()), username, password, login, message);
        form.setMaxWidth(380); form.setAlignment(Pos.CENTER_LEFT);
        BorderPane root = new BorderPane(); root.setPadding(new Insets(24));
        root.setTop(cloudStatus); BorderPane.setAlignment(cloudStatus, Pos.CENTER_RIGHT);
        root.setCenter(form);
        stage.setScene(new Scene(root, 720, 480));
    }

    private void showMain(JsonNode root) {
        String user = root.path("user").path("displayName").asText(root.path("user").path("username").asText("User"));
        Label title = new Label("Welcome, " + user);
        title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;");
        VBox featuresBox = new VBox(8);
        JsonNode features = root.path("features");
        Iterator<Map.Entry<String, JsonNode>> it = features.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> f = it.next();
            if (f.getValue().asBoolean(false)) {
                Button b = new Button(pretty(f.getKey()));
                b.setMaxWidth(Double.MAX_VALUE);
                b.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION, pretty(f.getKey()) + " is enabled for this account.").showAndWait());
                featuresBox.getChildren().add(b);
            }
        }
        if (featuresBox.getChildren().isEmpty()) featuresBox.getChildren().add(new Label("No features are enabled for this account."));
        Button logout = new Button("Sign out"); logout.setOnAction(e -> { showLogin(); checkCloud(); });
        VBox content = new VBox(16, title, new Label("Enabled features"), featuresBox, logout);
        content.setPadding(new Insets(24));
        BorderPane rootPane = new BorderPane(); rootPane.setTop(cloudStatus); rootPane.setCenter(content);
        BorderPane.setMargin(cloudStatus, new Insets(10));
        stage.setScene(new Scene(rootPane, 760, 520));
    }

    private static String pretty(String key) {
        String[] parts = key.toLowerCase().split("_");
        StringBuilder out = new StringBuilder();
        for (String p : parts) if (!p.isBlank()) out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        return out.toString().trim();
    }

    public static void main(String[] args) { launch(args); }
}

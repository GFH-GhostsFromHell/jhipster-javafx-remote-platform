package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Desktop;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Native JavaFX account access for the packaged Windows client.
 * On the login screen it replaces the login card in-place so registration,
 * verification and recovery feel like one part of Electrical Workbench.
 * Elsewhere (for example Profile) it falls back to a themed JavaFX window.
 */
public final class DesktopAccountAccess {
    private static final String BASE = "https://remote-javafx-portal.onrender.com";
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CARD = "-fx-background-color:white;-fx-background-radius:22;-fx-border-color:#d7e2ec;-fx-border-radius:22;-fx-effect:dropshadow(gaussian,rgba(0,0,0,.24),30,0,0,10);";
    private static final String INPUT = "-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-border-radius:9;-fx-background-radius:9;-fx-padding:10 11;";
    private static final String PRIMARY = "-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;-fx-padding:10 14;";
    private static final String SECONDARY = "-fx-background-color:#f8fafc;-fx-text-fill:#0f4c81;-fx-border-color:#c7d9e8;-fx-border-radius:9;-fx-background-radius:9;-fx-padding:9 12;-fx-font-weight:bold;";
    private static final String TAB = "-fx-background-color:#eef5f8;-fx-text-fill:#365268;-fx-background-radius:999;-fx-padding:7 11;-fx-font-size:11px;-fx-font-weight:bold;";
    private static final String TAB_ACTIVE = "-fx-background-color:#0e7490;-fx-text-fill:white;-fx-background-radius:999;-fx-padding:7 11;-fx-font-size:11px;-fx-font-weight:bold;";

    private DesktopAccountAccess() {}

    /**
     * Keeps the historical signature used by the shell. On the login screen this
     * swaps the existing login card for the account-access card instead of opening
     * a separate popup.
     */
    public static boolean showBeforeSignIn() {
        if (Platform.isFxApplicationThread()) {
            showInlineOrDialog();
        } else {
            Platform.runLater(DesktopAccountAccess::showInlineOrDialog);
        }
        return true;
    }

    private static void showInlineOrDialog() {
        Window owner = Window.getWindows().stream()
            .filter(Window::isShowing)
            .filter(Window::isFocused)
            .findFirst()
            .orElseGet(() -> Window.getWindows().stream().filter(Window::isShowing).findFirst().orElse(null));

        if (owner instanceof Stage stage && stage.getScene() != null && stage.getScene().getRoot() instanceof BorderPane root) {
            Node center = root.getCenter();
            if (center instanceof GridPane grid) {
                for (Node child : grid.getChildren()) {
                    Integer col = GridPane.getColumnIndex(child);
                    if (child instanceof StackPane holder && (col == null ? 0 : col) == 1 && !holder.getChildren().isEmpty()) {
                        Node original = holder.getChildren().get(0);
                        holder.getChildren().setAll(buildAccountCard(() -> holder.getChildren().setAll(original)));
                        return;
                    }
                }
            }
        }
        showThemedWindow(owner);
    }

    private static void showThemedWindow(Window owner) {
        Stage dialog = new Stage();
        if (owner != null) dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Electrical Workbench · Account Access");
        StackPane root = new StackPane(buildAccountCard(dialog::close));
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color:linear-gradient(to bottom right,#07111f,#0b2743 58%,#0e7490);");
        dialog.setScene(new Scene(root, 610, 720));
        dialog.setMinWidth(560);
        dialog.setMinHeight(620);
        dialog.show();
    }

    private static VBox buildAccountCard(Runnable backAction) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(28));
        card.setMaxWidth(500);
        card.setStyle(CARD);

        Label brand = new Label("⚡ ELECTRICAL WORKBENCH");
        brand.setStyle("-fx-text-fill:#0e7490;-fx-font-size:11px;-fx-font-weight:bold;");
        Label title = new Label("Account access");
        title.setStyle("-fx-text-fill:#0f172a;-fx-font-size:27px;-fx-font-weight:bold;");
        Label detail = new Label("Create an account, verify your email, or recover access without leaving the sign-in screen.");
        detail.setWrapText(true);
        detail.setStyle("-fx-text-fill:#64748b;-fx-font-size:13px;");

        ToggleGroup modes = new ToggleGroup();
        ToggleButton create = modeButton("Create", modes);
        ToggleButton verify = modeButton("Verify", modes);
        ToggleButton password = modeButton("Password", modes);
        ToggleButton username = modeButton("Username", modes);
        HBox tabs = new HBox(7, create, verify, password, username);
        tabs.setAlignment(Pos.CENTER_LEFT);

        StackPane content = new StackPane();
        content.setMinHeight(350);
        VBox.setVgrow(content, Priority.ALWAYS);

        Runnable refreshTabs = () -> {
            for (Toggle t : modes.getToggles()) {
                ((ToggleButton)t).setStyle(t.isSelected() ? TAB_ACTIVE : TAB);
            }
        };
        create.setOnAction(e -> { content.getChildren().setAll(createPane()); refreshTabs.run(); });
        verify.setOnAction(e -> { content.getChildren().setAll(verifyPane()); refreshTabs.run(); });
        password.setOnAction(e -> { content.getChildren().setAll(passwordPane()); refreshTabs.run(); });
        username.setOnAction(e -> { content.getChildren().setAll(usernamePane()); refreshTabs.run(); });

        create.setSelected(true);
        content.getChildren().setAll(createPane());
        refreshTabs.run();

        Separator separator = new Separator();
        Button back = new Button("← Back to sign in");
        back.setMaxWidth(Double.MAX_VALUE);
        back.setStyle(SECONDARY);
        back.setOnAction(e -> backAction.run());

        card.getChildren().addAll(brand, title, detail, tabs, separator, content, back);
        return card;
    }

    private static ToggleButton modeButton(String text, ToggleGroup group) {
        ToggleButton button = new ToggleButton(text);
        button.setToggleGroup(group);
        button.setStyle(TAB);
        button.setFocusTraversable(false);
        return button;
    }

    private static VBox createPane() {
        VBox box = formBox("Create your account", "Choose a username and subscription tier. Email verification is required before first sign-in.");
        TextField user = text("Username");
        TextField display = text("Display name");
        TextField email = text("Email address");
        PasswordField password = password("Password");
        PasswordField confirm = password("Confirm password");
        ComboBox<String> tier = new ComboBox<>();
        tier.getItems().addAll("Tier 1 · Free", "Tier 2", "Tier 3");
        tier.setValue("Tier 1 · Free");
        tier.setMaxWidth(Double.MAX_VALUE);
        Label status = statusLabel();
        Button submit = primary("Create account");
        submit.setOnAction(e -> {
            if (!password.getText().equals(confirm.getText())) {
                setStatus(status, "Passwords do not match.", false);
                return;
            }
            String tierValue = tier.getSelectionModel().getSelectedIndex() == 1 ? "TIER_2" : tier.getSelectionModel().getSelectedIndex() == 2 ? "TIER_3" : "TIER_1";
            run(submit, status, "Creating account…", () -> post("/api/public/register/start", Map.of(
                "username", user.getText().trim(),
                "displayName", display.getText().trim(),
                "email", email.getText().trim(),
                "password", password.getText(),
                "requestedTier", tierValue
            )), result -> setStatus(status, "Verification code sent to " + result.path("email").asText("your email") + ". Select Verify above to activate the account.", true));
        });
        box.getChildren().addAll(user, display, email, password, confirm, tier, submit, status);
        return box;
    }

    private static VBox verifyPane() {
        VBox box = formBox("Verify email", "Enter the six-digit code sent when the account was created.");
        TextField user = text("Username");
        TextField code = text("6-digit verification code");
        Label status = statusLabel();
        Button submit = primary("Verify account");
        submit.setOnAction(e -> run(submit, status, "Verifying…", () -> post("/api/public/register/verify", Map.of(
            "username", user.getText().trim(),
            "code", code.getText().trim()
        )), result -> {
            String checkout = result.path("checkoutUrl").asText("");
            setStatus(status, checkout.isBlank() ? "Account verified. You can return to Sign in." : "Account verified. Opening selected-tier checkout in your browser…", true);
            if (!checkout.isBlank()) openBrowser(checkout);
        }));
        box.getChildren().addAll(user, code, submit, status);
        return box;
    }

    private static VBox passwordPane() {
        VBox box = formBox("Reset password", "Request a reset code first, then enter the code with your username and new password.");
        TextField account = text("Username or email");
        Label requestStatus = statusLabel();
        Button request = secondary("Send reset code");
        request.setOnAction(e -> run(request, requestStatus, "Sending reset code…", () -> post("/api/public/recovery/password/start", Map.of(
            "account", account.getText().trim()
        )), result -> setStatus(requestStatus, result.path("message").asText("If the account exists, a reset code has been sent."), true)));

        Separator line = new Separator();
        TextField user = text("Username");
        TextField code = text("6-digit reset code");
        PasswordField next = password("New password");
        Label resetStatus = statusLabel();
        Button reset = primary("Set new password");
        reset.setOnAction(e -> run(reset, resetStatus, "Resetting password…", () -> post("/api/public/recovery/password/complete", Map.of(
            "username", user.getText().trim(),
            "code", code.getText().trim(),
            "newPassword", next.getText()
        )), result -> setStatus(resetStatus, result.path("message").asText("Password reset. You can sign in now."), true)));
        box.getChildren().addAll(account, request, requestStatus, line, user, code, next, reset, resetStatus);
        return box;
    }

    private static VBox usernamePane() {
        VBox box = formBox("Forgot username", "Enter the email address associated with your Electrical Workbench account.");
        TextField email = text("Account email");
        Label status = statusLabel();
        Button submit = primary("Send username reminder");
        submit.setOnAction(e -> run(submit, status, "Sending reminder…", () -> post("/api/public/recovery/username", Map.of(
            "email", email.getText().trim()
        )), result -> setStatus(status, result.path("message").asText("If the email matches an account, a username reminder has been sent."), true)));
        box.getChildren().addAll(email, submit, status);
        return box;
    }

    private static VBox formBox(String heading, String help) {
        VBox box = new VBox(9);
        box.setPadding(new Insets(5, 0, 0, 0));
        Label title = new Label(heading);
        title.setStyle("-fx-text-fill:#0f172a;-fx-font-size:18px;-fx-font-weight:bold;");
        Label detail = new Label(help);
        detail.setWrapText(true);
        detail.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");
        box.getChildren().addAll(title, detail);
        return box;
    }

    private static TextField text(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setStyle(INPUT);
        field.setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    private static PasswordField password(String prompt) {
        PasswordField field = new PasswordField();
        field.setPromptText(prompt);
        field.setStyle(INPUT);
        field.setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    private static Button primary(String text) {
        Button button = new Button(text);
        button.setStyle(PRIMARY);
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private static Button secondary(String text) {
        Button button = new Button(text);
        button.setStyle(SECONDARY);
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private static Label statusLabel() {
        Label label = new Label();
        label.setWrapText(true);
        label.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");
        return label;
    }

    private static void setStatus(Label label, String text, boolean success) {
        label.setText(text == null ? "" : text);
        label.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:" + (success ? "#047857" : "#b91c1c") + ";");
    }

    private static void run(Button button, Label status, String busyText, ThrowingSupplier action, ResultHandler success) {
        button.setDisable(true);
        String original = button.getText();
        button.setText(busyText);
        setStatus(status, "", true);
        Thread worker = new Thread(() -> {
            try {
                JsonNode result = action.get();
                Platform.runLater(() -> success.handle(result));
            } catch (Exception ex) {
                Platform.runLater(() -> setStatus(status, ex.getMessage() == null ? "Request failed." : ex.getMessage(), false));
            } finally {
                Platform.runLater(() -> { button.setDisable(false); button.setText(original); });
            }
        }, "account-access-request");
        worker.setDaemon(true);
        worker.start();
    }

    private static JsonNode post(String path, Map<String,?> body) throws Exception {
        String payload = JSON.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + path))
            .timeout(Duration.ofSeconds(90))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = response.body() == null || response.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = json.path("detail").asText(json.path("message").asText("Request returned HTTP " + response.statusCode()));
            throw new IllegalStateException(message);
        }
        return json;
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignored) { }
    }

    @FunctionalInterface
    private interface ThrowingSupplier { JsonNode get() throws Exception; }

    @FunctionalInterface
    private interface ResultHandler { void handle(JsonNode result); }
}

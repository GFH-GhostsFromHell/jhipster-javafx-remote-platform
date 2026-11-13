package com.gfh.devicesecurity;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

/**
 * Third Electrical Workbench app: voluntary single-file AES-256-GCM copies,
 * offline recovery, device status reporting and encrypted owner-operator chat.
 * Never encrypts operating-system drives, removes originals or issues remote lockouts.
 */
public final class DeviceSecurityDesktop extends Application {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_URL = "https://remote-javafx-portal.onrender.com";
    private final Preferences localPrefs = Preferences.userNodeForPackage(DeviceSecurityDesktop.class);
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(12)).build();

    private String deviceId;
    private DiskEncryptionStatus.Result latest;
    private final Label stateLabel = new Label("Not checked");
    private final Label details = new Label("Select Check Windows status to inspect the system drive.");
    private final Label cloudMessage = new Label("");
    private final TextField aliasField = new TextField("My Windows PC");
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final CheckBox consent = new CheckBox("I own or administer this device and agree to send its status.");
    private final Button reportButton = new Button("Report status to cloud");
    private final Button checkButton = new Button("Check Windows status");

    @Override
    public void start(Stage stage) {
        deviceId = localPrefs.get("device-id-v1", "");
        if (deviceId.isBlank()) {
            deviceId = UUID.randomUUID().toString();
            localPrefs.put("device-id-v1", deviceId);
        }
        stage.setTitle("Electrical Workbench · Device Security Center");
        BorderPane shell = new BorderPane();
        shell.setStyle("-fx-background-color:#F3F6FB;");

        VBox heading = new VBox(8);
        heading.setPadding(new Insets(24));
        heading.setStyle("-fx-background-color:#FFFFFF;-fx-border-color:#D3DEEA;-fx-border-width:0 0 1 0;");
        Label title = new Label("Device Security Center");
        title.setStyle("-fx-text-fill:#14263D;-fx-font-size:27px;-fx-font-weight:bold;");
        Label description = new Label("Check drive status, protect a selected data file, and contact cloud support.");
        description.setWrapText(true);
        description.setStyle("-fx-text-fill:#465870;-fx-font-size:14px;");
        heading.getChildren().addAll(title, description);
        shell.setTop(heading);

        VBox content = new VBox(16);
        content.setPadding(new Insets(20));
        VBox statusCard = card();
        Label statusTitle = sectionTitle("1 · Read-only Windows check");
        details.setWrapText(true);
        stateLabel.setStyle("-fx-font-weight:bold;-fx-text-fill:#124A7A;-fx-font-size:18px;");
        statusCard.getChildren().addAll(statusTitle, stateLabel, details, checkButton);

        VBox cloudCard = card();
        Label cloudTitle = sectionTitle("2 · Voluntary cloud status report");
        Label privacy = new Label("Only device alias, operating system, volume, status and report time are shared. No recovery keys, decryption tokens, passwords or files are uploaded.");
        privacy.setWrapText(true);
        privacy.setStyle("-fx-text-fill:#465870;");
        aliasField.setPromptText("Device alias (max 64 characters)");
        usernameField.setPromptText("Cloud portal username");
        passwordField.setPromptText("Cloud portal password");
        consent.setWrapText(true);
        reportButton.setDisable(true);
        cloudMessage.setWrapText(true);
        cloudMessage.setStyle("-fx-text-fill:#465870;");
        cloudCard.getChildren().addAll(cloudTitle, privacy,
            new Label("Device alias"), aliasField,
            new Label("Cloud username"), usernameField,
            new Label("Cloud password (not saved)"), passwordField,
            consent, reportButton, cloudMessage);

        VBox recoveryCard = card();
        Label recoveryTitle = sectionTitle("Recovery stays under your control");
        Label recovery = new Label("Only user-selected data-file copies are encrypted; Windows, boot files and your original data remain untouched. Keep your offline recovery key separately. No operator can revoke your independently retained recovery key.");
        recovery.setWrapText(true);
        recovery.setStyle("-fx-text-fill:#465870;");
        Hyperlink portalLink = new Hyperlink("Open Device Security Center in web portal");
        portalLink.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(URI.create(cloudUrl() + "/device-security.html"));
            } catch (Exception ex) {
                cloudMessage.setText("Could not open your browser: " + ex.getMessage());
            }
        });
        recoveryCard.getChildren().addAll(recoveryTitle, recovery, portalLink);
        content.getChildren().addAll(statusCard, new SelectedFilePanel().view(), cloudCard,
            new SupportChatPanel(usernameField, passwordField, cloudUrl()).view(), recoveryCard);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background:#F3F6FB;-fx-background-color:transparent;");
        shell.setCenter(scroll);

        checkButton.setOnAction(e -> checkStatus());
        consent.selectedProperty().addListener((o, prev, selected) -> updateReportButton());
        aliasField.textProperty().addListener((o, prev, next) -> updateReportButton());
        usernameField.textProperty().addListener((o, prev, next) -> updateReportButton());
        passwordField.textProperty().addListener((o, prev, next) -> updateReportButton());
        reportButton.setOnAction(e -> reportStatus());

        stage.setScene(new Scene(shell, 660, 820));
        stage.setMinWidth(480);
        stage.setMinHeight(620);
        stage.show();
    }

    private void checkStatus() {
        checkButton.setDisable(true);
        stateLabel.setText("Checking…");
        details.setText("Reading Windows BitLocker status. This does not change encryption.");
        CompletableFuture.supplyAsync(DiskEncryptionStatus::check).whenComplete((result, failure) ->
            Platform.runLater(() -> {
                checkButton.setDisable(false);
                latest = failure == null ? result : new DiskEncryptionStatus.Result(
                    "UNKNOWN", "Could not check the drive.", "N/A");
                stateLabel.setText(latest.state());
                details.setText(latest.detail() + " (" + latest.volume() + ")");
                updateReportButton();
            }));
    }

    private void updateReportButton() {
        reportButton.setDisable(latest == null || !consent.isSelected()
            || aliasField.getText().isBlank() || aliasField.getText().length() > 64
            || usernameField.getText().isBlank() || passwordField.getText().isBlank());
    }

    private void reportStatus() {
        if (latest == null || !consent.isSelected()) return;
        final DiskEncryptionStatus.Result snapshot = latest;
        final String alias = aliasField.getText().trim();
        final String username = usernameField.getText().trim();
        final String password = passwordField.getText();
        if (alias.length() > 64 || alias.isEmpty()) {
            cloudMessage.setText("Please enter a device alias of up to 64 characters.");
            return;
        }
        reportButton.setDisable(true);
        cloudMessage.setText("Submitting status report…");
        CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> payload = new LinkedHashMap<>();
                payload.put("deviceId", deviceId);
                payload.put("alias", alias);
                String os = System.getProperty("os.name", "Unknown");
                payload.put("operatingSystem", os.length() > 64 ? os.substring(0, 64) : os);
                payload.put("volume", snapshot.volume());
                payload.put("encryptionStatus", snapshot.state());
                String basic = Base64.getEncoder().encodeToString(
                    (username + ":" + password).getBytes(StandardCharsets.UTF_8));
                HttpRequest request = HttpRequest.newBuilder(
                    URI.create(cloudUrl() + "/api/device-security/devices"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)))
                    .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) return "Status report saved for your account.";
                if (response.statusCode() == 401 || response.statusCode() == 403)
                    return "Cloud sign-in failed or access was denied.";
                return "Cloud portal returned HTTP " + response.statusCode() + ".";
            } catch (Exception ex) {
                return "Could not submit report: " + ex.getClass().getSimpleName();
            }
        }).thenAccept(result -> Platform.runLater(() -> {
            cloudMessage.setText(result);
            updateReportButton();
        }));
    }

    private static String cloudUrl() {
        String raw = System.getenv().getOrDefault("PORTAL_URL", DEFAULT_URL)
            .replaceAll("/+$", "");
        URI uri = URI.create(raw);
        if (!"https".equalsIgnoreCase(uri.getScheme())
            && !(("http".equalsIgnoreCase(uri.getScheme()))
                && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost())))) {
            throw new IllegalArgumentException("Cloud portal must use HTTPS.");
        }
        return raw;
    }

    private static VBox card() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(18));
        card.setStyle("-fx-background-color:white;-fx-border-color:#B4C5D7;-fx-border-radius:12;-fx-background-radius:12;");
        return card;
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill:#14263D;-fx-font-weight:bold;-fx-font-size:18px;");
        return label;
    }

    public static void main(String[] args) { launch(args); }
}

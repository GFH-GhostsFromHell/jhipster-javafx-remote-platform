package com.gfh.devicesecurity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Optional, owner-initiated recovery-code escrow.
 * The cloud receives ONLY AES-GCM ciphertext; the offline key and escrow
 * passphrase never leave this JavaFX client in plaintext.
 */
public final class CloudRecoveryPanel {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final String baseUrl;
    private final TextField username;
    private final PasswordField accountPassword;
    private final VBox root = new VBox(10);
    private final TextField label = new TextField();
    private final PasswordField offlineRecovery = new PasswordField();
    private final PasswordField escrowPassword = new PasswordField();
    private final CheckBox offlineVerified = new CheckBox(
        "I have independently saved this recovery code outside this computer.");
    private final Button deposit = new Button("Back up encrypted recovery code");
    private final ComboBox<EntryInfo> records = new ComboBox<>();
    private final Button refresh = new Button("Load my encrypted backups");
    private final Button request = new Button("Request operator approval");
    private final Button release = new Button("Retrieve approved backup");
    private final TextField recoveredKey = new TextField();
    private final Label message = new Label("");

    private record EntryInfo(String id,String fileLabel) {
        @Override public String toString() { return fileLabel + " · " + id; }
    }

    public CloudRecoveryPanel(TextField username, PasswordField accountPassword, String baseUrl) {
        this.username = username;
        this.accountPassword = accountPassword;
        this.baseUrl = baseUrl;
        root.setPadding(new Insets(18));
        root.setStyle("-fx-background-color:white;-fx-border-color:#B4C5D7;-fx-border-radius:12;-fx-background-radius:12;");
        Label title = new Label("4 · Optional encrypted cloud backup");
        title.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#14263D;");
        Label description = wrap("After you have saved your offline recovery code, you may also upload a second "
            + "copy protected with an independent escrow passphrase. The cloud stores ciphertext only. "
            + "An operator must approve each one-time retrieval within 15 minutes. "
            + "You can always use your offline recovery code without the cloud.");
        label.setPromptText("File label — identify the matching .gfhenc copy");
        offlineRecovery.setPromptText("Paste the saved offline recovery code");
        escrowPassword.setPromptText("Separate strong escrow passphrase (12+ characters)");
        offlineVerified.setWrapText(true);
        records.setMaxWidth(Double.MAX_VALUE);
        recoveredKey.setEditable(false);
        recoveredKey.setPromptText("Approved recovery code is displayed here temporarily");
        message.setWrapText(true);
        root.getChildren().addAll(title, description,
            new Label("File label"), label,
            new Label("Offline recovery code to protect"), offlineRecovery,
            new Label("Additional cloud-backup passphrase"), escrowPassword,
            offlineVerified, deposit, new Separator(), refresh,
            new Label("My cloud backups"), records, request, release,
            new Label("Recovered key (copy to your offline recovery workflow)"), recoveredKey, message);
        deposit.setOnAction(e -> deposit());
        refresh.setOnAction(e -> refresh());
        request.setOnAction(e -> request());
        release.setOnAction(e -> release());
    }

    public Node view() { return root; }

    private static Label wrap(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill:#465870;");
        return label;
    }

    private void deposit() {
        if (!offlineVerified.isSelected()) {
            message.setText("First verify you have separately saved the recovery key outside this computer.");
            return;
        }
        final String fileLabel = label.getText().strip();
        final String code = offlineRecovery.getText().strip();
        final char[] passphrase = escrowPassword.getText().toCharArray();
        final String authorization = basicAuth();
        if (fileLabel.isBlank() || fileLabel.length() > 120 || passphrase.length < 12) {
            message.setText("Enter a file label (max 120 characters) and separate strong escrow passphrase.");
            Arrays.fill(passphrase, '\0');
            return;
        }
        deposit.setDisable(true);
        message.setText("Wrapping key locally and uploading encrypted backup…");
        CompletableFuture.supplyAsync(() -> {
            try {
                byte[] key = SelectedFileCrypto.readRecoveryText(code);
                Arrays.fill(key, (byte) 0);
                String wrapped = SupportMessageCrypto.seal(code, passphrase);
                return post("/api/device-security/escrow/entries", Map.of(
                    "fileLabel", fileLabel, "wrappedRecoveryKey", wrapped, "offlineBackupVerified", true), authorization);
            } catch (Exception e) { return "Backup failed: " + e.getMessage(); }
            finally { Arrays.fill(passphrase, '\0'); }
        }).thenAccept(result -> Platform.runLater(() -> {
            deposit.setDisable(false);
            offlineRecovery.clear();
            escrowPassword.clear();
            offlineVerified.setSelected(false);
            message.setText(result);
            if (result.startsWith("Submitted")) refresh();
        }));
    }

    private void refresh() {
        final String authorization = basicAuth();
        refresh.setDisable(true);
        CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<String> response = get("/api/device-security/escrow/entries", authorization);
                requireSuccess(response);
                return JSON.readTree(response.body());
            } catch (Exception e) { throw new RuntimeException(e); }
        }).whenComplete((data, failure) -> Platform.runLater(() -> {
            refresh.setDisable(false);
            if (failure != null) {
                message.setText("Could not load encrypted backups: " + failure.getMessage());
                return;
            }
            records.getItems().clear();
            for (JsonNode record : data) {
                records.getItems().add(new EntryInfo(value(record,"id"), value(record,"file_label")));
            }
            if (!records.getItems().isEmpty()) records.getSelectionModel().selectFirst();
            message.setText(records.getItems().size() + " encrypted cloud backup(s) found.");
        }));
    }

    private void request() {
        EntryInfo selected = records.getValue();
        if (selected == null) { message.setText("Select an encrypted cloud backup."); return; }
        final String authorization = basicAuth();
        request.setDisable(true);
        CompletableFuture.supplyAsync(() -> {
            try { return post("/api/device-security/escrow/entries/" + selected.id() + "/request", Map.of(), authorization); }
            catch (Exception e) { return "Could not request approval: " + e.getMessage(); }
        }).thenAccept(msg -> Platform.runLater(() -> {
            request.setDisable(false);
            message.setText(msg + " The operator must approve before cloud retrieval.");
        }));
    }

    private void release() {
        EntryInfo selected = records.getValue();
        if (selected == null) { message.setText("Select an encrypted cloud backup."); return; }
        final char[] passphrase = escrowPassword.getText().toCharArray();
        if (passphrase.length < 12) {
            Arrays.fill(passphrase, '\0');
            message.setText("Enter the separate escrow passphrase before retrieving an approved backup.");
            return;
        }
        final String authorization = basicAuth();
        release.setDisable(true);
        recoveredKey.clear();
        CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<String> result = postRaw(
                    "/api/device-security/escrow/entries/" + selected.id() + "/release",
                    "{}", authorization);
                requireSuccess(result);
                String wrapped = JSON.readTree(result.body()).path("wrappedRecoveryKey").asText();
                String code = SupportMessageCrypto.open(wrapped, passphrase);
                byte[] key = SelectedFileCrypto.readRecoveryText(code);
                Arrays.fill(key, (byte) 0);
                return new String[]{code, "Recovery key unwrapped locally. Save it securely and use Restore decrypted copy."};
            } catch (Exception e) {
                return new String[]{null, "Could not retrieve or decrypt cloud backup: " + e.getMessage()
                    + ". Wrong escrow passphrase requires a fresh operator approval."};
            } finally { Arrays.fill(passphrase, '\0'); }
        }).thenAccept(result -> Platform.runLater(() -> {
            release.setDisable(false);
            escrowPassword.clear();
            if (result[0] != null) recoveredKey.setText(result[0]);
            message.setText(result[1]);
        }));
    }

    private static String value(JsonNode object, String key) {
        if (object.has(key)) return object.get(key).asText();
        return object.path(key.toUpperCase()).asText("");
    }

    private String basicAuth() {
        String raw = username.getText().strip() + ":" + accountPassword.getText();
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String post(String path, Object body, String authorization) throws Exception {
        HttpResponse<String> result = postRaw(path, JSON.writeValueAsString(body), authorization);
        requireSuccess(result);
        return "Submitted encrypted backup or request successfully.";
    }

    private HttpResponse<String> postRaw(String path,String body,String authorization) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(30)).header("Authorization", authorization)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String authorization) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(30)).header("Authorization", authorization).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void requireSuccess(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException("Cloud returned HTTP " + response.statusCode());
    }
}

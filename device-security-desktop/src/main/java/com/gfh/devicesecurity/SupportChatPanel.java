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

/** Optional owner-initiated, passphrase-encrypted support chat and recovery approvals. */
public final class SupportChatPanel {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final VBox root = new VBox(10);
    private final TextField username;
    private final PasswordField accountPassword;
    private final String baseUrl;
    private final PasswordField chatPassphrase = new PasswordField();
    private final TextArea newMessage = new TextArea();
    private final TextArea messageHistory = new TextArea();
    private final Label status = new Label("");
    private final Button send = new Button("Send encrypted message");
    private final Button refresh = new Button("Refresh conversation");
    private final TextField recoveryLabel = new TextField();
    private final TextArea recoveryReason = new TextArea();
    private final Button request = new Button("Request operator review");
    private final Button checkRequests = new Button("Refresh request decisions");
    private final Label requestStatus = new Label("");

    public SupportChatPanel(TextField username, PasswordField accountPassword, String baseUrl) {
        this.username = username;
        this.accountPassword = accountPassword;
        this.baseUrl = baseUrl;
        root.setPadding(new Insets(18));
        root.setStyle("-fx-background-color:white;-fx-border-color:#B4C5D7;-fx-border-radius:12;-fx-background-radius:12;");
        Label title = new Label("4 · Encrypted support chat & operator review");
        title.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#14263D;");
        Label hint = new Label("Log in using the cloud credentials above. Set a strong chat passphrase and share it with your operator through a separate trusted channel. The portal stores encrypted messages only; it never receives this passphrase.");
        hint.setWrapText(true);
        chatPassphrase.setPromptText("Shared support passphrase (at least 12 characters)");
        newMessage.setPromptText("Write a support message to the operator");
        newMessage.setPrefRowCount(3);
        newMessage.setWrapText(true);
        messageHistory.setEditable(false);
        messageHistory.setPrefRowCount(8);
        messageHistory.setWrapText(true);
        status.setWrapText(true);
        recoveryLabel.setPromptText("File label (no path necessary)");
        recoveryReason.setPromptText("Reason for requesting recovery approval");
        recoveryReason.setPrefRowCount(2);
        recoveryReason.setWrapText(true);
        requestStatus.setWrapText(true);
        root.getChildren().addAll(title, hint, new Label("Chat passphrase"), chatPassphrase,
            new Label("Conversation"), messageHistory,
            new Label("New message"), newMessage, send, refresh, status,
            new Separator(), new Label("Recovery support request"),
            recoveryLabel, recoveryReason, request, checkRequests, requestStatus);
        send.setOnAction(e -> send());
        refresh.setOnAction(e -> refresh());
        request.setOnAction(e -> requestRecovery());
        checkRequests.setOnAction(e -> checkRequests());
    }

    public Node view() { return root; }

    private void send() {
        String plain = newMessage.getText();
        char[] password = chatPassphrase.getText().toCharArray();
        if (username.getText().isBlank() || accountPassword.getText().isBlank()) {
            status.setText("Enter cloud account credentials in the section above.");
            Arrays.fill(password, '\0');
            return;
        }
        final String authorization = basicAuth();
        send.setDisable(true);
        status.setText("Encrypting and sending…");
        CompletableFuture.supplyAsync(() -> {
            try {
                String sealed = SupportMessageCrypto.seal(plain, password);
                return submit("/api/device-security/support/messages", Map.of("ciphertext", sealed), authorization);
            } catch (Exception e) {
                return "Message not sent: " + e.getMessage();
            } finally {
                Arrays.fill(password, '\0');
            }
        }).thenAccept(result -> Platform.runLater(() -> {
            send.setDisable(false);
            status.setText(result);
            if (result.startsWith("Submitted")) { newMessage.clear(); refresh(); }
        }));
    }

    private void refresh() {
        char[] password = chatPassphrase.getText().toCharArray();
        final String authorization = basicAuth();
        refresh.setDisable(true);
        status.setText("Loading conversation…");
        CompletableFuture.supplyAsync(() -> {
            try {
                if (password.length < 12) throw new IllegalArgumentException("Enter the shared chat passphrase");
                HttpResponse<String> response = get("/api/device-security/support/messages", authorization);
                if (response.statusCode() != 200) throw new IllegalArgumentException("Cloud returned HTTP " + response.statusCode());
                StringBuilder out = new StringBuilder();
                JsonNode list = JSON.readTree(response.body());
                for (JsonNode item : list) {
                    String sender = field(item, "sender_role");
                    String sealed = field(item, "ciphertext");
                    String date = field(item, "sent_at");
                    try {
                        out.append(sender).append(" · ").append(date).append("\n")
                            .append(SupportMessageCrypto.open(sealed, password)).append("\n\n");
                    } catch (Exception ignored) {
                        out.append("[Unable to decrypt — check shared passphrase]").append("\n\n");
                    }
                }
                return new String[] {out.length() == 0 ? "No messages yet." : out.toString(), "Conversation refreshed."};
            } catch (Exception e) { return new String[] {null, "Could not read chat: " + e.getMessage()}; }
            finally { Arrays.fill(password, '\0'); }
        }).thenAccept(result -> Platform.runLater(() -> {
            refresh.setDisable(false);
            if (result[0] != null) messageHistory.setText(result[0]);
            status.setText(result[1]);
        }));
    }

    private void requestRecovery() {
        String label = recoveryLabel.getText().strip();
        String reason = recoveryReason.getText().strip();
        final String authorization = basicAuth();
        request.setDisable(true);
        requestStatus.setText("Submitting review request…");
        CompletableFuture.supplyAsync(() -> {
            try {
                return submit("/api/device-security/support/recovery",
                    Map.of("fileLabel", label, "reason", reason), authorization);
            } catch (Exception e) { return "Could not submit: " + e.getMessage(); }
        }).thenAccept(result -> Platform.runLater(() -> {
            request.setDisable(false);
            requestStatus.setText(result + " This approval does not replace your independent offline recovery key.");
        }));
    }

    private void checkRequests() {
        final String authorization = basicAuth();
        checkRequests.setDisable(true);
        CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<String> response = get("/api/device-security/support/recovery", authorization);
                if (response.statusCode() != 200) return "Cloud returned HTTP " + response.statusCode();
                JsonNode results = JSON.readTree(response.body());
                StringBuilder out = new StringBuilder();
                for (JsonNode item : results) {
                    out.append(field(item, "file_label")).append(": ")
                       .append(field(item, "state")).append("\n");
                }
                return out.length() == 0 ? "No requests submitted." : out.toString();
            } catch (Exception e) { return "Could not load requests: " + e.getMessage(); }
        }).thenAccept(result -> Platform.runLater(() -> {
            checkRequests.setDisable(false);
            requestStatus.setText(result);
        }));
    }

    private static String field(JsonNode json, String name) {
        if (json.has(name)) return json.path(name).asText();
        if (json.has(name.toUpperCase())) return json.path(name.toUpperCase()).asText();
        return "";
    }

    private String submit(String path, Object body, String authorization) throws Exception {
        HttpRequest req = request(path, authorization).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300)
            return "Cloud returned HTTP " + res.statusCode() + ".";
        return "Submitted successfully.";
    }

    private HttpResponse<String> get(String path, String authorization) throws Exception {
        return client.send(request(path, authorization).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Capture JavaFX input values on the UI thread, not from an I/O worker. */
    private String basicAuth() {
        String raw = username.getText().strip() + ":" + accountPassword.getText();
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder request(String path, String authorization) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(25))
            .header("Authorization", authorization);
    }
}

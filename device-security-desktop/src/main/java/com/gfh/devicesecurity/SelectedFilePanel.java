package com.gfh.devicesecurity;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Explicit single-file opt-in with full offline key confirmation.
 * The original file is never changed or deleted.
 */
public final class SelectedFilePanel {
    private final VBox root = new VBox(12);
    private final Label sourceLabel = new Label("No file selected");
    private final Label encryptedLabel = new Label("No encrypted file selected");
    private final Label operation = new Label("Ready");
    private final Label timer = new Label("Elapsed: 00:00 · Remaining: —");
    private final ProgressBar progress = new ProgressBar(0);
    private final TextField offlineKey = new TextField();
    private final PasswordField backupConfirmation = new PasswordField();
    private final PasswordField keyToDecrypt = new PasswordField();
    private final Button chooseSource = new Button("Choose one data file");
    private final Button prepare = new Button("Generate recovery key");
    private final Button encrypt = new Button("Encrypt a separate copy");
    private final Button chooseEncrypted = new Button("Choose .gfhenc file");
    private final Button decrypt = new Button("Restore decrypted copy");
    private byte[] pendingKey;
    private Path selectedSource;
    private Path selectedEncrypted;
    private boolean busy;

    public SelectedFilePanel() {
        root.setPadding(new Insets(18));
        root.setStyle("-fx-background-color:white;-fx-border-color:#B4C5D7;-fx-border-radius:12;-fx-background-radius:12;");
        Label title = new Label("2 · AES-256 protected data files");
        title.setStyle("-fx-text-fill:#14263D;-fx-font-weight:bold;-fx-font-size:18px;");
        Label note = wrap("Encrypt one explicitly selected file at a time (up to 64 MiB in this first version). "
            + "Your original file is retained. No folders, system files, startup actions or remote encryption.");
        Label offline = wrap("The recovery key is NOT uploaded or stored by this desktop app. "
            + "Save it in a secure offline location and re-enter the entire code to verify your backup. "
            + "If you lose it, the encrypted copy may become unrecoverable.");
        offline.setStyle("-fx-text-fill:#6B3C0A;-fx-font-weight:bold;");
        offlineKey.setEditable(false);
        offlineKey.setPromptText("Generate a recovery key");
        backupConfirmation.setPromptText("Re-enter the entire offline recovery code");
        keyToDecrypt.setPromptText("Paste your offline recovery code");
        sourceLabel.setWrapText(true);
        encryptedLabel.setWrapText(true);
        operation.setWrapText(true);
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setPrefWidth(500);
        root.getChildren().addAll(title, note, chooseSource, sourceLabel, prepare, offline,
            new Label("Offline recovery code"), offlineKey,
            new Label("Verify you saved your recovery code"), backupConfirmation,
            encrypt, new Separator(), chooseEncrypted, encryptedLabel,
            new Label("Recovery code for decryption"), keyToDecrypt, decrypt,
            new Separator(), operation, progress, timer);
        chooseSource.setOnAction(e -> select(false));
        chooseEncrypted.setOnAction(e -> select(true));
        prepare.setOnAction(e -> prepareKey());
        encrypt.setOnAction(e -> run(true));
        decrypt.setOnAction(e -> run(false));
        backupConfirmation.textProperty().addListener((obs, oldValue, newValue) -> updateButtons());
        keyToDecrypt.textProperty().addListener((obs, oldValue, newValue) -> updateButtons());
        updateButtons();
    }

    public Node view() { return root; }

    private static Label wrap(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill:#465870;");
        return label;
    }

    private void select(boolean encrypted) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(encrypted ? "Choose an encrypted .gfhenc file" : "Choose one data file");
        if (encrypted) chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Encrypted files", "*.gfhenc"));
        java.io.File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) return;
        if (encrypted) {
            selectedEncrypted = file.toPath();
            encryptedLabel.setText(file.getAbsolutePath());
        } else {
            selectedSource = file.toPath();
            sourceLabel.setText(file.getAbsolutePath());
            clearPendingKey();
        }
        updateButtons();
    }

    private void prepareKey() {
        clearPendingKey();
        try {
            pendingKey = SelectedFileCrypto.createKey();
            offlineKey.setText(SelectedFileCrypto.recoveryText(pendingKey));
            operation.setText("Write this code to an independent, secure location, then verify it below.");
        } catch (Exception ex) {
            operation.setText("Could not generate an encryption key.");
        }
        updateButtons();
    }

    private void run(boolean isEncrypt) {
        byte[] key;
        Path selected;
        try {
            if (isEncrypt) {
                if (pendingKey == null || selectedSource == null ||
                    !offlineKey.getText().equals(backupConfirmation.getText())) {
                    throw new IllegalArgumentException("Save your offline recovery key and re-enter it exactly before encryption.");
                }
                key = pendingKey.clone();
                selected = selectedSource;
            } else {
                if (selectedEncrypted == null) throw new IllegalArgumentException("Choose a .gfhenc file.");
                key = SelectedFileCrypto.readRecoveryText(keyToDecrypt.getText());
                selected = selectedEncrypted;
            }
        } catch (Exception ex) {
            operation.setText(ex.getMessage());
            return;
        }
        busy = true;
        updateButtons();
        progress.setProgress(0);
        long start = System.nanoTime();
        operation.setText(isEncrypt ? "Encrypting a new copy…" : "Authenticating and restoring a copy…");
        timer.setText("Elapsed: 00:00 · Remaining: calculating");
        long[] lastUI = {0};
        CompletableFuture.supplyAsync(() -> {
            try {
                SelectedFileCrypto.Progress callback = (done, total) -> {
                    long now = System.nanoTime();
                    if (now - lastUI[0] < 200_000_000L && done != total) return;
                    lastUI[0] = now;
                    double ratio = total <= 0 ? 1.0 : Math.min(1, (double)done / total);
                    long seconds = Math.max(0, (now - start) / 1_000_000_000L);
                    String estimate = done <= 0 || total <= 0 ? "calculating"
                        : formatTime((long)Math.ceil(seconds * (double)(total - done) / done));
                    Platform.runLater(() -> {
                        progress.setProgress(ratio);
                        timer.setText("Elapsed: " + formatTime(seconds) + " · Remaining: " + estimate);
                    });
                };
                Path output = isEncrypt
                    ? SelectedFileCrypto.encrypt(selected, key, callback)
                    : SelectedFileCrypto.decrypt(selected, key, callback);
                return "Completed. Created " + output + ". Original file retained.";
            } catch (Exception e) {
                return "Operation failed safely: " + e.getMessage()
                    + ". Original file unchanged; no partial output retained.";
            } finally {
                Arrays.fill(key, (byte)0);
            }
        }).thenAccept(msg -> Platform.runLater(() -> {
            busy = false;
            operation.setText(msg);
            timer.setText("Elapsed: " + formatTime((System.nanoTime() - start) / 1_000_000_000L)
                + " · Remaining: —");
            if (isEncrypt) clearPendingKey();
            else keyToDecrypt.clear();
            updateButtons();
        }));
    }

    private void clearPendingKey() {
        if (pendingKey != null) Arrays.fill(pendingKey, (byte)0);
        pendingKey = null;
        offlineKey.clear();
        backupConfirmation.clear();
    }

    private void updateButtons() {
        chooseSource.setDisable(busy);
        chooseEncrypted.setDisable(busy);
        prepare.setDisable(busy || selectedSource == null);
        encrypt.setDisable(busy || selectedSource == null || pendingKey == null
            || backupConfirmation.getText().isEmpty());
        decrypt.setDisable(busy || selectedEncrypted == null || keyToDecrypt.getText().isBlank());
    }

    private static String formatTime(long seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}

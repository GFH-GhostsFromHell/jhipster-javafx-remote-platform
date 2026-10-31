package com.example.desktop.ui;

import com.example.desktop.CloudConnectionState;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

public final class CloudStatusControl extends HBox {
    private final Label symbol = new Label("☁");
    private final Label text = new Label("Connecting...");
    private final Tooltip details = new Tooltip();

    public CloudStatusControl() {
        setSpacing(6);
        setPadding(new Insets(4, 8, 4, 8));
        getChildren().addAll(symbol, text);
        Tooltip.install(this, details);
        setState(CloudConnectionState.CONNECTING, "Checking the cloud API");
    }

    public void setState(CloudConnectionState state, String detail) {
        Runnable update = () -> {
            switch (state) {
                case CONNECTING -> { symbol.setText("☁"); text.setText("Connecting..."); }
                case ONLINE -> { symbol.setText("☁"); text.setText("Cloud Connected"); }
                case AUTHENTICATED -> { symbol.setText("🔒"); text.setText("Cloud Connected"); }
                case DEGRADED -> { symbol.setText("⚠"); text.setText("Cloud Limited"); }
                case OFFLINE -> { symbol.setText("☁"); text.setText("Cloud Offline"); }
            }
            details.setText(detail == null ? state.name() : detail);
        };
        if (Platform.isFxApplicationThread()) update.run(); else Platform.runLater(update);
    }
}

package com.example.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.net.URI;

/**
 * Compatibility wrapper retained so the existing desktop shell can keep using the
 * same calculator entry point while the full CEC-section workspace is restored.
 */
public final class NativeCalculatorPane extends BorderPane {
    public NativeCalculatorPane(PortalClient portal, ObjectMapper json, String username, String password) {
        Label engineeringTitle = new Label("Engineering Power Systems");
        engineeringTitle.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label engineeringDetail = new Label("Per-unit, base values, transformer impedance/reflection, transmission lines, symmetrical components, sequence faults and short-circuit studies.");
        engineeringDetail.setWrapText(true);
        engineeringDetail.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");
        Button engineering = new Button("⚡ Open Engineering Power");
        engineering.setStyle("-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;-fx-padding:9 13;");
        engineering.setOnAction(e -> {
            try {
                Desktop.getDesktop().browse(URI.create(portal.baseUrl() + "/engineering-power.html"));
            } catch (Exception ex) {
                engineering.setText("Could not open browser");
            }
        });
        VBox engineeringBar = new VBox(5, engineeringTitle, engineeringDetail, engineering);
        engineeringBar.setPadding(new Insets(0, 0, 14, 0));
        setTop(engineeringBar);
        setCenter(new SectionedCalculatorPane(portal, json, username, password));
    }
}

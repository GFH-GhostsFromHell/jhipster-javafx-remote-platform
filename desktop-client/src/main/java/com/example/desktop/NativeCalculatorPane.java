package com.example.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.net.URI;

/** Main native calculator workspace. Guided Worksheets are the default surface;
 * the detailed section-by-section calculator remains available beside it. */
public final class NativeCalculatorPane extends BorderPane {
    public NativeCalculatorPane(PortalClient portal, ObjectMapper json, String username, String password) {
        Label title = new Label("Electrical Workbench Calculators");
        title.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label detail = new Label("Guided Worksheets are optimized for C of Q-style questions. The full CEC section workspace remains available for direct section-based calculation.");
        detail.setWrapText(true);
        detail.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");
        Button engineering = new Button("⚡ Engineering Power Systems");
        engineering.setStyle("-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;-fx-padding:9 13;");
        engineering.setOnAction(e -> {
            try { Desktop.getDesktop().browse(URI.create(portal.baseUrl() + "/engineering-power.html")); }
            catch (Exception ex) { engineering.setText("Could not open browser"); }
        });
        Label update = new Label("✓ Guided mode includes conductor + VD, D3/K, grounding/bonding, transformer, motor taps, refrigeration, fire pump, neutral, fire alarm and service-demand worksheets. Conduit & Box Fill now has a dedicated advanced workspace with mixed conductor rows, dropdowns, automatic CEC fill limits, standard box/raceway presets and detailed pass/fail results.");
        update.setWrapText(true);
        update.setStyle("-fx-background-color:#ecfeff;-fx-border-color:#a5f3fc;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 10;-fx-text-fill:#155e75;-fx-font-weight:bold;-fx-font-size:12px;");
        VBox bar = new VBox(5, title, detail, engineering, update);
        bar.setPadding(new Insets(0,0,12,0));
        setTop(bar);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab guided = new Tab("Guided Worksheets", new GuidedWorksheetPane(json, username));
        Tab fill = new Tab("Conduit & Box Fill", new FillCalculatorPane());
        Tab sections = new Tab("CEC Sections / Advanced", new SectionedCalculatorPane(portal, json, username, password));
        tabs.getTabs().addAll(guided, fill, sections);
        tabs.getSelectionModel().select(guided);
        setCenter(tabs);
    }
}

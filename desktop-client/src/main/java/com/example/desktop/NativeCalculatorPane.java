package com.example.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.net.URI;

/** Main native calculator workspace. Guided Worksheets are the default surface;
 * the detailed section-by-section calculator remains available beside it. */
public final class NativeCalculatorPane extends BorderPane {
    public NativeCalculatorPane(PortalClient portal, ObjectMapper json, String username, String password) {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Label title = new Label("Electrical Workbench Calculators");
        title.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label detail = new Label("Use Integrated Design for coordinated field design, Transformer Sizer to select a standard transformer directly from connected loads, the Exam Simulator for blueprint-balanced 309A practice, or Tools & Presets to search the Workbench.");
        detail.setWrapText(true);
        detail.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");
        Button engineering = new Button("⚡ Engineering Power Systems");
        engineering.setStyle("-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;-fx-padding:9 13;");
        engineering.setOnAction(e -> {
            try { Desktop.getDesktop().browse(URI.create(portal.baseUrl() + "/engineering-power.html")); }
            catch (Exception ex) { engineering.setText("Could not open browser"); }
        });
        CheckBox darkMode = new CheckBox("Dark Workbench");
        darkMode.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;");
        darkMode.setOnAction(e -> {
            boolean dark = darkMode.isSelected();
            tabs.setStyle(dark ? "-fx-base:#111827;-fx-background:#0b1220;-fx-control-inner-background:#111827;-fx-text-background-color:#e5e7eb;" : "");
            setStyle(dark ? "-fx-background-color:#0b1220;" : "-fx-background-color:transparent;");
            title.setStyle(dark ? "-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#f8fafc;" : "-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
            detail.setStyle(dark ? "-fx-text-fill:#cbd5e1;-fx-font-size:12px;" : "-fx-text-fill:#64748b;-fx-font-size:12px;");
            darkMode.setStyle(dark ? "-fx-font-weight:bold;-fx-text-fill:#e2e8f0;" : "-fx-font-weight:bold;-fx-text-fill:#334155;");
        });
        HBox controls = new HBox(12, engineering, darkMode); controls.setAlignment(Pos.CENTER_LEFT);
        Label update = new Label("✓ Transformer Sizer now builds required kVA from mixed loads entered as A, VA, kVA, W or kW, applies quantity/PF/demand/continuous-load factors and spare capacity, then selects the next common 1φ or 3φ transformer size and shows primary/secondary current and headroom. Integrated Design, raceway/derating, exam simulation, reports and presets remain available beside it.");
        update.setWrapText(true);
        update.setStyle("-fx-background-color:#ecfeff;-fx-border-color:#a5f3fc;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 10;-fx-text-fill:#155e75;-fx-font-weight:bold;-fx-font-size:12px;");
        VBox bar = new VBox(5, title, detail, controls, update);
        bar.setPadding(new Insets(0,0,12,0));
        setTop(bar);

        Tab guided = new Tab("Guided Worksheets", new GuidedWorksheetPane(json, username));
        Tab design = new Tab("Integrated Design", new IntegratedDesignPane(json, username));
        Tab transformerSizer = new Tab("Transformer Sizer", new TransformerLoadSizingPane(json, username));
        Tab raceway = new Tab("Raceway + Derating", new RacewayAmpacityPane());
        Tab fill = new Tab("Conduit & Box Fill", new FillCalculatorPane());
        Tab derating = new Tab("Ampacity / Derating", new AmpacityDeratingPane());
        Tab reports = new Tab("Report Center", new PdfReportCenterPane(json, username));
        Tab sections = new Tab("CEC Sections / Advanced", new SectionedCalculatorPane(portal, json, username, password));
        Tab exam = new Tab("Exam Simulator", new ExamSimulatorPane(json, username, name -> {
            for (Tab t : tabs.getTabs()) if (t.getText().equals(name)) { tabs.getSelectionModel().select(t); break; }
        }));
        Tab tools = new Tab("Tools & Presets", new WorkbenchToolsPane(json, username, name -> {
            for (Tab t : tabs.getTabs()) if (t.getText().equals(name)) { tabs.getSelectionModel().select(t); break; }
        }));
        Tab modulesDraft = new Tab("Business Modules (Draft)", new EnterpriseModulesDraftPane(username));

        tabs.getTabs().addAll(tools, exam, guided, design, transformerSizer, raceway, fill, derating, reports, sections, modulesDraft);
        tabs.getSelectionModel().select(transformerSizer);
        setCenter(tabs);
    }
}

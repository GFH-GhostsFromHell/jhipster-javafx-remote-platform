package com.example.desktop;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Reversible UI-only concept for optional Business / Enterprise modules.
 *
 * This draft deliberately does not change billing, entitlements, existing calculator
 * behavior, or the current Business Team backend. It is intended only to validate the
 * desktop experience before any server-side module licensing is implemented.
 */
public final class EnterpriseModulesDraftPane extends BorderPane {
    private static final String CARD = "-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#dbe5ef;-fx-border-radius:14;";
    private static final String PRIMARY = "-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;-fx-padding:9 13;";
    private static final String GHOST = "-fx-background-color:#eef6fa;-fx-text-fill:#0f4c81;-fx-border-color:#c7d9e8;-fx-border-radius:9;-fx-background-radius:9;-fx-padding:9 12;-fx-font-weight:bold;";

    private final VBox detail = new VBox(14);

    public EnterpriseModulesDraftPane() {
        setStyle("-fx-background-color:#eef3f8;");

        VBox page = new VBox(18);
        page.setPadding(new Insets(4, 4, 24, 4));

        Label draft = new Label("DRAFT PREVIEW · NO BILLING CHANGES");
        draft.setStyle("-fx-background-color:#fef3c7;-fx-text-fill:#92400e;-fx-background-radius:999;-fx-padding:5 9;-fx-font-size:10px;-fx-font-weight:bold;");
        Label title = new Label("Business / Enterprise Modules");
        title.setStyle("-fx-font-size:25px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label subtitle = new Label("Keep the Electrical Workbench you already use, then add optional professional workspaces only when a business purchases them.");
        subtitle.setWrapText(true);
        subtitle.setStyle("-fx-text-fill:#64748b;-fx-font-size:13px;");
        VBox heading = new VBox(6, draft, title, subtitle);

        HBox architecture = new HBox(12,
            stat("CORE WORKBENCH", "Unchanged", "Existing calculators, design tools, study tools and reports stay where they are."),
            stat("BUSINESS BASE", "Team + Cloud", "Company account, users, roles, projects and shared administration."),
            stat("MODULES", "Optional", "Purchase only the advanced workspaces the organization needs."));
        for (Node node : architecture.getChildren()) HBox.setHgrow(node, Priority.ALWAYS);

        Label catalogTitle = new Label("Module catalog concept");
        catalogTitle.setStyle("-fx-font-size:19px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label catalogNote = new Label("These are concept modules only. The draft does not grant access, create subscriptions or change any existing account tier.");
        catalogNote.setWrapText(true);
        catalogNote.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");

        FlowPane catalog = new FlowPane(12, 12);
        catalog.setPrefWrapLength(940);
        catalog.getChildren().addAll(
            module("📐", "Advanced Electrical Estimating", "Gold Seal-aligned competency approach for tendering, takeoff, labour/material pricing, bid closing and cost history.", true,
                () -> showEstimating()),
            module("📊", "Project Controls", "Budgets, schedules, change orders, procurement, cost-to-complete and actual-vs-estimate tracking.", false,
                () -> showFuture("Project Controls")),
            module("🦺", "Safety & Compliance", "Company safety records, inspections, training expiry, corrective actions and safety dashboards.", false,
                () -> showFuture("Safety & Compliance")),
            module("👥", "HR, Training & Credentials", "Employees, licences, apprenticeship records, competencies, onboarding and role-based access.", false,
                () -> showFuture("HR, Training & Credentials")),
            module("⚙", "Engineering Studies", "Expanded studies, project calculations, technical reports and engineering collaboration.", false,
                () -> showFuture("Engineering Studies")),
            module("🧰", "Maintenance & Commissioning", "Assets, PM records, commissioning sheets, test history and equipment lifecycle records.", false,
                () -> showFuture("Maintenance & Commissioning"))
        );

        detail.setPadding(new Insets(18));
        detail.setStyle(CARD);
        showEstimating();

        page.getChildren().addAll(heading, architecture, catalogTitle, catalogNote, catalog, detail);
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        setCenter(scroll);
    }

    private Node module(String icon, String title, String text, boolean featured, Runnable open) {
        VBox card = new VBox(9);
        card.setPadding(new Insets(15));
        card.setPrefWidth(300);
        card.setMinHeight(190);
        card.setStyle(CARD + (featured ? "-fx-border-color:#67e8f9;-fx-border-width:2;" : ""));

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size:23px;");
        Label name = new Label(title);
        name.setWrapText(true);
        name.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label description = new Label(text);
        description.setWrapText(true);
        description.setStyle("-fx-text-fill:#64748b;-fx-font-size:11px;");
        Region push = new Region();
        VBox.setVgrow(push, Priority.ALWAYS);
        Button button = new Button(featured ? "Preview estimating" : "View concept");
        button.setStyle(featured ? PRIMARY : GHOST);
        button.setOnAction(e -> open.run());
        card.getChildren().addAll(iconLabel, name, description, push, button);
        return card;
    }

    private void showEstimating() {
        detail.getChildren().clear();
        Label badge = new Label("FIRST MODULE CONCEPT");
        badge.setStyle("-fx-background-color:#cffafe;-fx-text-fill:#155e75;-fx-background-radius:999;-fx-padding:4 8;-fx-font-size:10px;-fx-font-weight:bold;");
        Label title = new Label("Advanced Electrical Estimating");
        title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label note = new Label("A contractor-grade estimating workspace aligned to the kinds of competencies expected of professional construction estimators. This wording intentionally does not claim CCA Gold Seal accreditation or certification.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill:#475569;-fx-font-size:12px;-fx-line-spacing:2px;");

        VBox workflow = section("Bid workflow",
            "Opportunity / bid-no-bid",
            "Tender documents, drawings and specifications",
            "Scope review and exclusions",
            "Quantity takeoff and assemblies",
            "Material, labour, equipment and subcontract pricing",
            "Indirect costs, general conditions, bonding / insurance",
            "Overhead, profit, taxes and bid closing",
            "Proposal, alternates and submission",
            "Change orders, actual-vs-estimate and historical costs");

        VBox electrical = section("Electrical estimating structure",
            "Service and distribution",
            "Transformers, switchgear and panels",
            "Feeders, branch circuits, raceways and conductors",
            "Boxes, devices, lighting and controls",
            "Motors, VFDs and HVAC power",
            "Fire alarm, communications and specialty systems",
            "Grounding / bonding, supports and miscellaneous material");

        VBox access = section("Business licensing concept",
            "Enterprise Base stays separate from optional modules",
            "Company purchases MODULE_ESTIMATING",
            "Administrator assigns estimating seats to selected employees",
            "Estimator / PM / Owner permissions can differ",
            "Desktop and browser can share the same cloud project and estimate data",
            "Mobile remains a field companion rather than a full estimating workstation");

        HBox columns = new HBox(14, workflow, electrical, access);
        for (Node node : columns.getChildren()) HBox.setHgrow(node, Priority.ALWAYS);
        detail.getChildren().addAll(badge, title, note, new Separator(), columns);
    }

    private void showFuture(String moduleName) {
        detail.getChildren().clear();
        Label title = new Label(moduleName);
        title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label note = new Label("Concept placeholder only. This draft is intentionally focused on validating the modular layout before any additional module is designed or connected to billing.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill:#64748b;");
        Button estimating = new Button("Back to estimating preview");
        estimating.setStyle(GHOST);
        estimating.setOnAction(e -> showEstimating());
        detail.getChildren().addAll(title, note, estimating);
    }

    private static VBox section(String heading, String... items) {
        VBox box = new VBox(7);
        box.setPadding(new Insets(14));
        box.setMinWidth(0);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setStyle("-fx-background-color:#f8fafc;-fx-background-radius:12;-fx-border-color:#e2e8f0;-fx-border-radius:12;");
        Label title = new Label(heading);
        title.setWrapText(true);
        title.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        box.getChildren().add(title);
        for (String item : items) {
            Label label = new Label("• " + item);
            label.setWrapText(true);
            label.setStyle("-fx-text-fill:#475569;-fx-font-size:11px;");
            box.getChildren().add(label);
        }
        return box;
    }

    private static VBox stat(String heading, String value, String note) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(14));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle(CARD);
        Label h = new Label(heading);
        h.setStyle("-fx-text-fill:#64748b;-fx-font-size:10px;-fx-font-weight:bold;");
        Label v = new Label(value);
        v.setStyle("-fx-text-fill:#0e7490;-fx-font-size:20px;-fx-font-weight:bold;");
        Label n = new Label(note);
        n.setWrapText(true);
        n.setStyle("-fx-text-fill:#64748b;-fx-font-size:10px;");
        card.getChildren().addAll(h, v, n);
        return card;
    }
}

package com.example.desktop;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Reversible preview of optional Business / Enterprise modules. */
public final class EnterpriseModulesDraftPane extends BorderPane {
    private static final String CARD = "-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#dbe5ef;-fx-border-radius:14;";
    private static final String PRIMARY = "-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;-fx-padding:9 13;";
    private static final String GHOST = "-fx-background-color:#eef6fa;-fx-text-fill:#0f4c81;-fx-border-color:#c7d9e8;-fx-border-radius:9;-fx-background-radius:9;-fx-padding:9 12;-fx-font-weight:bold;";

    private final VBox page = new VBox(18);
    private final VBox detail = new VBox(14);

    private final String username;
    public EnterpriseModulesDraftPane(String username) {
        this.username=username==null?"default":username;
        setStyle("-fx-background-color:#eef3f8;");
        page.setPadding(new Insets(4,4,24,4));

        Label draft = new Label("DRAFT PREVIEW · NO BILLING CHANGES");
        draft.setStyle("-fx-background-color:#fef3c7;-fx-text-fill:#92400e;-fx-background-radius:999;-fx-padding:5 9;-fx-font-size:10px;-fx-font-weight:bold;");
        Label title = new Label("Business / Enterprise Modules");
        title.setStyle("-fx-font-size:25px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label subtitle = new Label("Keep the Electrical Workbench you already use, then add optional professional workspaces only when a business purchases them.");
        subtitle.setWrapText(true); subtitle.setStyle("-fx-text-fill:#64748b;-fx-font-size:13px;");
        VBox heading = new VBox(6,draft,title,subtitle);

        HBox architecture = new HBox(12,
            stat("CORE WORKBENCH","Unchanged","Existing calculators, design tools, study tools and reports stay where they are."),
            stat("BUSINESS BASE","Team + Cloud","Company account, users, roles, projects and shared administration."),
            stat("MODULES","Optional","Purchase only the advanced workspaces the organization needs."));
        for(Node n:architecture.getChildren()) HBox.setHgrow(n,Priority.ALWAYS);

        Label catalogTitle = new Label("Module catalog concept");
        catalogTitle.setStyle("-fx-font-size:19px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label catalogNote = new Label("The estimating module below is now a functional local preview. Other modules remain concept placeholders. No subscriptions or production entitlements are changed.");
        catalogNote.setWrapText(true); catalogNote.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");

        FlowPane catalog = new FlowPane(12,12); catalog.setPrefWrapLength(940);
        catalog.getChildren().addAll(
            module("📐","Advanced Electrical Estimating","Working tender scope, takeoff, labour/productivity, quote leveling, indirects, markups and bid review.",true,this::showEstimating),
            module("📊","Project Controls","Budgets, schedules, change orders, procurement, cost-to-complete and actual-vs-estimate tracking.",false,()->showFuture("Project Controls")),
            module("🦺","Safety & Compliance","Company safety records, inspections, training expiry, corrective actions and safety dashboards.",false,()->showFuture("Safety & Compliance")),
            module("👥","HR, Training & Credentials","Employees, licences, apprenticeship records, competencies, onboarding and role-based access.",false,()->showFuture("HR, Training & Credentials")),
            module("⚙","Engineering Studies","Expanded studies, project calculations, technical reports and engineering collaboration.",false,()->showFuture("Engineering Studies")),
            module("🧰","Maintenance & Commissioning","Assets, PM records, commissioning sheets, test history and equipment lifecycle records.",false,()->showFuture("Maintenance & Commissioning"))
        );

        detail.setPadding(new Insets(2));
        showEstimating();
        page.getChildren().addAll(heading,architecture,catalogTitle,catalogNote,catalog,detail);
        ScrollPane scroll = new ScrollPane(page); scroll.setFitToWidth(true); scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        setCenter(scroll);
    }

    private Node module(String icon,String title,String text,boolean featured,Runnable open){
        VBox card=new VBox(9); card.setPadding(new Insets(15)); card.setPrefWidth(300); card.setMinHeight(185);
        card.setStyle(CARD+(featured?"-fx-border-color:#67e8f9;-fx-border-width:2;":""));
        Label i=new Label(icon); i.setStyle("-fx-font-size:23px;");
        Label name=new Label(title); name.setWrapText(true); name.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label desc=new Label(text); desc.setWrapText(true); desc.setStyle("-fx-text-fill:#64748b;-fx-font-size:11px;");
        Region push=new Region(); VBox.setVgrow(push,Priority.ALWAYS);
        Button button=new Button(featured?"Open estimating workspace":"View concept"); button.setStyle(featured?PRIMARY:GHOST); button.setOnAction(e->open.run());
        card.getChildren().addAll(i,name,desc,push,button); return card;
    }

    private void showEstimating(){
        detail.getChildren().clear();
        EstimatingWorkspaceDraftPane estimator = new EstimatingWorkspaceDraftPane(username);
        estimator.setPrefHeight(980);
        detail.getChildren().add(estimator);
    }

    private void showFuture(String moduleName){
        detail.getChildren().clear();
        VBox card=new VBox(10); card.setPadding(new Insets(18)); card.setStyle(CARD);
        Label title=new Label(moduleName); title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label note=new Label("Concept placeholder only. The current preview is intentionally focused on making the estimating workflow useful before additional modules are implemented.");
        note.setWrapText(true); note.setStyle("-fx-text-fill:#64748b;");
        Button back=new Button("Back to estimating workspace"); back.setStyle(GHOST); back.setOnAction(e->showEstimating());
        card.getChildren().addAll(title,note,back); detail.getChildren().add(card);
    }

    private static VBox stat(String heading,String value,String note){
        VBox card=new VBox(5); card.setPadding(new Insets(14)); card.setMaxWidth(Double.MAX_VALUE); card.setStyle(CARD);
        Label h=new Label(heading); h.setStyle("-fx-text-fill:#64748b;-fx-font-size:10px;-fx-font-weight:bold;");
        Label v=new Label(value); v.setStyle("-fx-text-fill:#0e7490;-fx-font-size:20px;-fx-font-weight:bold;");
        Label n=new Label(note); n.setWrapText(true); n.setStyle("-fx-text-fill:#64748b;-fx-font-size:10px;");
        card.getChildren().addAll(h,v,n); return card;
    }
}

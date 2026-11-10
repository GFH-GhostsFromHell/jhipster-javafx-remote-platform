package com.example.desktop;

import com.example.desktop.ui.CloudStatusControl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Native JavaFX Electrical Workbench shell. Cloud services provide data/APIs; core UI stays native. */
public final class ElectricalWorkbenchNativeApp extends Application {
    private final PortalClient portal = new PortalClient();
    private final ObjectMapper json = new ObjectMapper();
    private final CloudStatusControl cloudStatus = new CloudStatusControl();
    private final UpdateService updater = new UpdateService();

    private Stage stage;
    private String currentUsername;
    private String currentPassword;
    private JsonNode currentBootstrap;
    private StackPane workspace;

    private static final String BG = "-fx-background-color:linear-gradient(to bottom right,#07111f,#0b2743 58%,#0e7490);";
    private static final String CARD = "-fx-background-color:white;-fx-background-radius:16;-fx-border-color:#dfe7ef;-fx-border-radius:16;";
    private static final String INPUT = "-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-border-radius:9;-fx-background-radius:9;-fx-padding:10 11;";
    private static final String PRIMARY = "-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;-fx-padding:10 14;";
    private static final String NAV = "-fx-background-color:transparent;-fx-text-fill:#dbeafe;-fx-font-weight:bold;-fx-font-size:13px;-fx-alignment:center-left;-fx-padding:11 13;-fx-background-radius:9;";

    @Override public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Electrical Workbench Desktop");
        stage.setMinWidth(980); stage.setMinHeight(680);
        showLogin(); stage.show(); checkCloud();
    }

    private void checkCloud() {
        cloudStatus.setState(CloudConnectionState.CONNECTING, "Checking " + portal.baseUrl());
        CompletableFuture.runAsync(() -> {
            try {
                boolean ok = portal.health();
                cloudStatus.setState(ok ? CloudConnectionState.ONLINE : CloudConnectionState.OFFLINE,
                    ok ? "Connected to " + portal.baseUrl() : "Cloud service is waking or unavailable");
            } catch (Exception ex) { cloudStatus.setState(CloudConnectionState.OFFLINE, ex.getMessage()); }
        });
    }

    private void showLogin() {
        BorderPane root = new BorderPane(); root.setStyle(BG);
        VBox hero = new VBox(17); hero.setPadding(new Insets(58)); hero.setAlignment(Pos.CENTER_LEFT);
        Label brand = new Label("ELECTRICAL WORKBENCH"); brand.setStyle("-fx-text-fill:#67e8f9;-fx-font-weight:bold;-fx-font-size:12px;");
        Label title = new Label("A Canadian electrician workspace\nthat stays native JavaFX.");
        title.setStyle("-fx-text-fill:white;-fx-font-size:35px;-fx-font-weight:bold;");
        Label detail = new Label("CEC calculators, Red Seal study, engineering power systems, field troubleshooting, project records, apprenticeship tracking and code news in one desktop application.");
        detail.setWrapText(true); detail.setMaxWidth(455); detail.setStyle("-fx-text-fill:#cbd5e1;-fx-font-size:15px;-fx-line-spacing:4px;");
        HBox cloud = new HBox(cloudStatus); cloud.setStyle("-fx-background-color:rgba(255,255,255,.10);-fx-background-radius:10;-fx-padding:5 8;");
        hero.getChildren().addAll(brand,title,detail,cloud);

        VBox loginCard = new VBox(12); loginCard.setMaxWidth(390); loginCard.setPadding(new Insets(34));
        loginCard.setStyle("-fx-background-color:white;-fx-background-radius:20;-fx-effect:dropshadow(gaussian,rgba(0,0,0,.24),28,0,0,10);");
        Label loginTitle = new Label("Sign in"); loginTitle.setStyle("-fx-font-size:26px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        TextField username = new TextField(); username.setPromptText("Username"); username.setStyle(INPUT);
        PasswordField password = new PasswordField(); password.setPromptText("Password"); password.setStyle(INPUT);
        Label message = new Label(); message.setWrapText(true); message.setStyle("-fx-text-fill:#b91c1c;-fx-font-size:12px;");
        Button login = new Button("Sign in to Electrical Workbench"); login.setMaxWidth(Double.MAX_VALUE); login.setDefaultButton(true); login.setStyle(PRIMARY);
        login.setOnAction(e -> {
            login.setDisable(true); login.setText("Connecting…"); message.setText("");
            String u=username.getText().trim(), p=password.getText();
            CompletableFuture.supplyAsync(() -> { try { return portal.bootstrap(u,p); } catch(Exception ex){ throw new RuntimeException(ex); } })
                .whenComplete((body,err)->Platform.runLater(()->{
                    login.setDisable(false); login.setText("Sign in to Electrical Workbench");
                    if(err!=null){Throwable c=unwrap(err);message.setText(message(c));cloudStatus.setState(CloudConnectionState.DEGRADED,"Sign-in failed");return;}
                    try{currentUsername=u;currentPassword=p;currentBootstrap=json.readTree(body);DesktopAccountSync.configure(u,p);cloudStatus.setState(CloudConnectionState.AUTHENTICATED,"Authenticated as "+u);showWorkspace();}
                    catch(Exception ex){message.setText("The cloud returned an invalid response.");}
                }));
        });
        Button accountAccess = new Button("Create account / verify / recover login");
        accountAccess.setMaxWidth(Double.MAX_VALUE);
        accountAccess.setStyle("-fx-background-color:#f8fafc;-fx-text-fill:#0f4c81;-fx-border-color:#c7d9e8;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:9 10;-fx-font-weight:bold;");
        accountAccess.setOnAction(e -> DesktopAccountAccess.showBeforeSignIn());
        loginCard.getChildren().addAll(loginTitle,new Label("Use your Electrical Workbench account."),fieldLabel("Username"),username,fieldLabel("Password"),password,login,accountAccess,message);

        GridPane layout=new GridPane();ColumnConstraints left=new ColumnConstraints();left.setPercentWidth(55);ColumnConstraints right=new ColumnConstraints();right.setPercentWidth(45);layout.getColumnConstraints().addAll(left,right);layout.add(hero,0,0);StackPane wrap=new StackPane(loginCard);wrap.setPadding(new Insets(40));layout.add(wrap,1,0);root.setCenter(layout);
        stage.setScene(new Scene(root,1040,680));
    }

    private void showWorkspace() {
        BorderPane root=new BorderPane();root.setStyle("-fx-background-color:#eef3f8;");
        HBox top=new HBox(12);top.setAlignment(Pos.CENTER_LEFT);top.setPadding(new Insets(12,18,12,18));top.setStyle("-fx-background-color:linear-gradient(to right,#050b14,#0b2743,#0e7490);");
        Label brand=new Label("⚡ Electrical Workbench Canada");brand.setStyle("-fx-text-fill:white;-fx-font-size:18px;-fx-font-weight:bold;");
        Label version=new Label("Installed v"+updater.currentVersion());version.setStyle("-fx-text-fill:#a5f3fc;-fx-font-size:12px;-fx-font-weight:bold;");Region push=new Region();HBox.setHgrow(push,Priority.ALWAYS);
        Button update=new Button("Check for Updates");update.setVisible(true);update.setManaged(true);update.setStyle("-fx-background-color:#16a34a;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-padding:7 10;");
        update.setOnAction(e->checkForUpdates(update,true));
        HBox cloud=new HBox(cloudStatus);cloud.setStyle("-fx-background-color:rgba(255,255,255,.10);-fx-background-radius:9;-fx-padding:4 7;");
        Button logout=new Button("Sign out");logout.setStyle("-fx-background-color:rgba(255,255,255,.10);-fx-text-fill:white;-fx-border-color:rgba(255,255,255,.20);-fx-border-radius:8;-fx-background-radius:8;-fx-padding:7 10;");logout.setOnAction(e->{DesktopAccountSync.disconnect();currentUsername=null;currentPassword=null;currentBootstrap=null;showLogin();checkCloud();});
        top.getChildren().addAll(brand,version,push,update,cloud,logout);root.setTop(top);checkForUpdates(update,false);

        VBox nav=new VBox(6);nav.setPadding(new Insets(18,12,18,12));nav.setPrefWidth(225);nav.setStyle("-fx-background-color:#07111f;");
        Label navTitle=new Label("WORKSPACE");navTitle.setStyle("-fx-text-fill:#67e8f9;-fx-font-size:10px;-fx-font-weight:bold;-fx-padding:0 8 7 8;");
        Button home=navButton("⌂  Home"), calculators=navButton("🧮  Electrical Calculators"), engineering=navButton("⚙  Power Systems"), learn=navButton("🎓  Learn"), field=navButton("🛠  Jobsite Tools"), safety=navButton("🦺  Safety"), projects=navButton("📁  Saved Projects"), career=navButton("📈  Trade Hours & Career"), business=navButton("🏢  Business Team"), news=navButton("⚡  Code News"), chatgpt=navButton("🤖  Ask ChatGPT"), profile=navButton("👤  Profile");
        nav.getChildren().addAll(navTitle,home,calculators,engineering,learn,field,safety,projects,career);
        if(isEnterpriseOwner()) nav.getChildren().add(business);
        nav.getChildren().addAll(news,chatgpt,profile);root.setLeft(nav);

        workspace=new StackPane();workspace.setPadding(new Insets(22));root.setCenter(workspace);
        home.setOnAction(e->setWorkspace(buildHome()));calculators.setOnAction(e->setWorkspace(buildCalculators()));engineering.setOnAction(e->setWorkspace(buildEngineering()));learn.setOnAction(e->setWorkspace(buildPlatform("Learn")));field.setOnAction(e->setWorkspace(buildPlatform("Field")));safety.setOnAction(e->setWorkspace(buildSafety()));projects.setOnAction(e->setWorkspace(buildPlatform("Projects")));career.setOnAction(e->setWorkspace(buildPlatform("Career")));business.setOnAction(e->setWorkspace(new EnterpriseTeamPane(portal,currentUsername,currentPassword)));news.setOnAction(e->setWorkspace(buildNews()));chatgpt.setOnAction(e->setWorkspace(AskChatGptView.create(currentUsername)));profile.setOnAction(e->setWorkspace(buildProfile()));setWorkspace(buildHome());
        stage.setScene(new Scene(root,1320,830));
    }

    private Node buildHome() {
        JsonNode account=currentBootstrap.path("user");String name=account.path("displayName").asText(account.path("username").asText("user"));String rawTier=account.path("subscriptionTier").asText("TIER_1");String tier="TIER_4".equals(rawTier)?"BUSINESS / ENTERPRISE":rawTier.replace('_',' ');
        VBox page=new VBox(18);Label hello=new Label("Welcome, "+name);hello.setStyle("-fx-font-size:26px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");Label tierLabel=new Label(tier);tierLabel.setStyle("-fx-background-color:#cffafe;-fx-text-fill:#155e75;-fx-background-radius:999;-fx-padding:5 9;-fx-font-weight:bold;");HBox head=new HBox(12,hello,tierLabel);head.setAlignment(Pos.CENTER_LEFT);
        Label note=new Label("Choose a task: Electrical Calculators for code and sizing, Engineering for power studies, Jobsite Tools for installation work, or Saved Projects for records. Native tools are available offline where indicated.");note.setWrapText(true);note.setStyle("-fx-text-fill:#64748b;-fx-font-size:14px;");
        Label managedNotice=new Label();managedNotice.setWrapText(true);managedNotice.setVisible(false);managedNotice.setManaged(false);managedNotice.setStyle("-fx-background-color:#ecfeff;-fx-border-color:#a5f3fc;-fx-border-radius:10;-fx-background-radius:10;-fx-padding:10 12;-fx-text-fill:#155e75;-fx-font-weight:bold;");
        CompletableFuture.supplyAsync(()->{try{return portal.enterpriseMembership(currentUsername,currentPassword);}catch(Exception ex){return null;}}).thenAccept(body->Platform.runLater(()->{
            if(body==null)return;
            try{JsonNode membership=json.readTree(body);if(!membership.path("managed").asBoolean(false))return;String businessName=membership.path("businessName").asText(membership.path("ownerUsername").asText("Business account"));String assigned=membership.path("assignedTier").asText("TIER_1").replace('_',' ');String role=membership.path("roleLabel").asText("");managedNotice.setText("🏢 Business-managed account · "+businessName+" · "+assigned+(role.isBlank()?"":" · "+role));managedNotice.setVisible(true);managedNotice.setManaged(true);}catch(Exception ignored){}
        }));
        TilePane tiles=new TilePane();tiles.setHgap(12);tiles.setVgap(12);tiles.setPrefColumns(2);
        tiles.getChildren().addAll(
            tile("🧮 Electrical Calculators","Full section-based calculators: services, busbars, motors, transformers, wiring, fire alarm, welders and more",()->setWorkspace(buildCalculators())),
            tile("⚙ Power System Analysis","Per-unit, base values, transformer impedance, transmission lines, symmetrical components, sequence faults and short-circuit tools",()->setWorkspace(buildEngineering())),
            tile("🎓 Red Seal Study & Daily Quiz","Red Seal daily quiz, CEC navigator, formulas, control trainer and province profile",()->setWorkspace(buildPlatform("Learn"))),
            tile("🛠 Jobsite Tools","Troubleshooting, nameplates, commissioning, inspection, blueprint scaling, fault current and grounding wizard",()->setWorkspace(buildPlatform("Field"))),
             tile("↪ Conduit Bend Marks & Calibration","Bend layouts, field instructions and saved bender measurements",()->setWorkspace(new ConduitBendingFieldPane())),
             tile("↪ Conduit Bends + 3D Preview","Experimental rotatable multi-bend preview and large marking/flip cues; current native bender remains unchanged",()->openFieldPage("/bend-instruction-lab.html")),
             tile("🛠 Equipment Troubleshooting · Offline","Offline motor, VFD, PLC, protection and Epiroc-style mining diagnostic guides",()->setWorkspace(new TroubleshootingKnowledgePane())),
             tile("📐 Jobsite Tools · Web","Blueprint takeoff, 3D conduit previews, quotes, controls, one-line diagrams, commissioning and project records",()->openFieldPage("/field-suite.html")),
             tile("📘 Equipment Troubleshooting · Online Guides","Open 23 detailed guided diagnostic procedures and saved troubleshooting cases",()->openFieldPage("/troubleshooting-kb.html")),
            tile("🦺 Electrical Safety","PPE rating checks, approach-distance planning, unsafe-condition photos, glove testing and safety-equipment maintenance",()->setWorkspace(buildSafety())),
            tile("📁 Saved Projects","Projects, equipment records, material takeoffs, saved field records and cable planning",()->setWorkspace(buildPlatform("Projects"))),
            tile("📈 Trade Hours & Career","Hours/skills tracking, credential wallet, wage calculator and official job resources",()->setWorkspace(buildPlatform("Career"))),
            tile("🤖 Ask ChatGPT","Connect your ChatGPT account and use an eligible ChatGPT plan in a native JavaFX chat workspace",()->setWorkspace(AskChatGptView.create(currentUsername))),
            tile("⚡ CEC / OESC Code News","Live ESA/OESC bulletins and code notices in native JavaFX",()->setWorkspace(buildNews()))
        );
        if(isEnterpriseOwner()) tiles.getChildren().add(tile("🏢 Business / Enterprise","Manage electricians, apprentices, field roles and Tier 1–3 business seats",()->setWorkspace(new EnterpriseTeamPane(portal,currentUsername,currentPassword))));
        VBox info=new VBox(8);info.setPadding(new Insets(18));info.setStyle(CARD);Label infoTitle=new Label("Native desktop architecture");infoTitle.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");Label infoText=new Label("The UI remains JavaFX. Calculations can use cloud APIs, while quiz history, projects, field sheets, apprenticeship hours and credentials are stored locally for offline use. Engineering Power is embedded inside the desktop app and uses the same account tier permissions as the cloud portal.");infoText.setWrapText(true);infoText.setStyle("-fx-text-fill:#475569;-fx-line-spacing:3px;");info.getChildren().addAll(infoTitle,infoText);
        page.getChildren().addAll(head,note,managedNotice,tiles,info);ScrollPane scroll=new ScrollPane(page);scroll.setFitToWidth(true);scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");return scroll;
    }

    private void openFieldPage(String path) {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(portal.baseUrl()+path));
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Unable to open Field Suite page: "+e.getMessage()).showAndWait();
        }
    }

    private Node buildCalculators(){return new NativeCalculatorPane(portal,json,currentUsername,currentPassword);}

    private Node buildEngineering() {
        VBox root=new VBox(10);
        Label title=new Label("Engineering Power Systems");title.setStyle("-fx-font-size:25px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label status=new Label("Loading authenticated Engineering Power workspace…");status.setStyle("-fx-text-fill:#64748b;");
        WebView web=new WebView();web.setContextMenuEnabled(true);VBox.setVgrow(web,Priority.ALWAYS);
        root.getChildren().addAll(title,status,web);
        CompletableFuture.supplyAsync(()->{try{return portal.authorizedPage("/engineering-power.html",currentUsername,currentPassword);}catch(Exception ex){throw new RuntimeException(ex);}})
            .whenComplete((html,err)->Platform.runLater(()->{
                if(err!=null){status.setText("Engineering Power unavailable: "+message(unwrap(err)));web.getEngine().loadContent("<html><body style='font-family:sans-serif;padding:24px'><h2>Engineering Power unavailable</h2><p>"+escapeHtml(message(unwrap(err)))+"</p></body></html>");return;}
                status.setText("Engineering Power · authenticated as "+currentUsername);
                web.getEngine().loadContent(html, "text/html");
            }));
        return root;
    }

    private Node buildSafety(){return new SafetyPane(json,currentUsername);}
    private Node buildPlatform(String area){return new ElectricianPlatformPane(json,currentUsername,area);}

    private Node buildNews() {
        VBox root=new VBox(13);Label title=new Label("CEC 2024 & Ontario Code News");title.setStyle("-fx-font-size:25px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");Label sub=new Label("Native JavaFX feed using the ESA/OESC backend data. Official source pages open only when you choose Open official source.");sub.setWrapText(true);sub.setStyle("-fx-text-fill:#64748b;");
        HBox controls=new HBox(9);controls.setAlignment(Pos.CENTER_LEFT);ComboBox<String> filter=new ComboBox<>();filter.getItems().addAll("All","Urgent","Important","Technical","Info","OESC Bulletin","CEC/OESC Code Notice");filter.setValue("All");Button refresh=new Button("Refresh");refresh.setStyle(PRIMARY);Label checked=new Label("Loading…");checked.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");controls.getChildren().addAll(filter,refresh,checked);
        VBox feed=new VBox(10);ScrollPane scroll=new ScrollPane(feed);scroll.setFitToWidth(true);scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");VBox.setVgrow(scroll,Priority.ALWAYS);JsonNode[] snapshot={null};
        Runnable render=()->{feed.getChildren().clear();if(snapshot[0]==null)return;for(JsonNode item:snapshot[0].path("items")){String selected=filter.getValue(),severity=item.path("severity").asText(""),type=item.path("type").asText("");boolean show="All".equals(selected)||selected.equalsIgnoreCase(severity)||selected.equals(type);if(show)feed.getChildren().add(newsCard(item));}if(feed.getChildren().isEmpty())feed.getChildren().add(new Label("No news items match this filter."));};
        Runnable load=()->{refresh.setDisable(true);checked.setText("Loading…");feed.getChildren().setAll(new Label("Loading code news…"));CompletableFuture.supplyAsync(()->{try{return portal.electricalNews();}catch(Exception ex){throw new RuntimeException(ex);}}).whenComplete((body,err)->Platform.runLater(()->{refresh.setDisable(false);if(err!=null){checked.setText("Feed unavailable");feed.getChildren().setAll(new Label(message(unwrap(err))));return;}try{snapshot[0]=json.readTree(body);checked.setText("Last backend poll: "+snapshot[0].path("polledAt").asText("unknown"));render.run();}catch(Exception ex){checked.setText("Invalid feed response");}}));};
        refresh.setOnAction(e->load.run());filter.setOnAction(e->render.run());root.getChildren().addAll(title,sub,controls,scroll);Platform.runLater(load);return root;
    }

    private Node newsCard(JsonNode item) {
        VBox card=new VBox(7);card.setPadding(new Insets(15));card.setStyle(CARD);String severity=item.path("severity").asText("INFO");Label meta=new Label(severity+"  •  "+item.path("type").asText("")+"  •  "+item.path("dateLabel").asText(""));meta.setStyle("-fx-text-fill:#0e7490;-fx-font-size:11px;-fx-font-weight:bold;");Label title=new Label(item.path("title").asText("Untitled"));title.setWrapText(true);title.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");Label summary=new Label(item.path("summary").asText(""));summary.setWrapText(true);summary.setStyle("-fx-text-fill:#475569;");HBox bottom=new HBox(10);bottom.setAlignment(Pos.CENTER_LEFT);Label source=new Label(item.path("source").asText(""));source.setStyle("-fx-text-fill:#64748b;-fx-font-size:11px;");Button open=new Button("Open official source");open.setOnAction(e->{String url=item.path("url").asText("");if(!url.isBlank())getHostServices().showDocument(url);});bottom.getChildren().addAll(open,source);card.getChildren().addAll(meta,title,summary,bottom);return card;
    }

    private Node buildProfile() {
        JsonNode account=currentBootstrap.path("user");VBox root=new VBox(14);Label title=new Label("Profile & Settings");title.setStyle("-fx-font-size:25px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        VBox profile=new VBox(10);profile.setPadding(new Insets(18));profile.setStyle(CARD);profile.setMaxWidth(650);TextField name=new TextField(account.path("displayName").asText(""));name.setStyle(INPUT);TextField email=new TextField(account.path("email").asText(""));email.setStyle(INPUT);Label saveStatus=new Label();saveStatus.setWrapText(true);Button save=new Button("Save profile");save.setStyle(PRIMARY);
        save.setOnAction(e->{save.setDisable(true);saveStatus.setText("Saving…");try{String payload=json.writeValueAsString(Map.of("displayName",name.getText(),"email",email.getText(),"avatarData",account.path("avatarData").asText("")));CompletableFuture.supplyAsync(()->{try{return portal.updateProfile(currentUsername,currentPassword,payload);}catch(Exception ex){throw new RuntimeException(ex);}}).whenComplete((body,err)->Platform.runLater(()->{save.setDisable(false);if(err!=null){saveStatus.setText(message(unwrap(err)));return;}try{((ObjectNode)currentBootstrap).set("user",json.readTree(body));saveStatus.setText("Profile saved.");}catch(Exception ex){saveStatus.setText("Saved, but could not refresh the local profile.");}}));}catch(Exception ex){save.setDisable(false);saveStatus.setText(ex.getMessage());}});
        profile.getChildren().addAll(fieldLabel("Display name"),name,fieldLabel("Email"),email,save,saveStatus);
        VBox password=new VBox(10);password.setPadding(new Insets(18));password.setStyle(CARD);password.setMaxWidth(650);Label pwTitle=new Label("Change password");pwTitle.setStyle("-fx-font-size:18px;-fx-font-weight:bold;");PasswordField current=new PasswordField();current.setPromptText("Current password");current.setStyle(INPUT);PasswordField next=new PasswordField();next.setPromptText("New password");next.setStyle(INPUT);Label pwStatus=new Label();pwStatus.setWrapText(true);Button change=new Button("Change password");change.setStyle(PRIMARY);
        change.setOnAction(e->{change.setDisable(true);pwStatus.setText("Changing…");try{String payload=json.writeValueAsString(Map.of("currentPassword",current.getText(),"newPassword",next.getText()));CompletableFuture.runAsync(()->{try{portal.changePassword(currentUsername,currentPassword,payload);}catch(Exception ex){throw new RuntimeException(ex);}}).whenComplete((v,err)->Platform.runLater(()->{change.setDisable(false);if(err!=null){pwStatus.setText(message(unwrap(err)));return;}currentPassword=next.getText();current.clear();next.clear();pwStatus.setText("Password changed.");}));}catch(Exception ex){change.setDisable(false);pwStatus.setText(ex.getMessage());}});
        password.getChildren().addAll(pwTitle,current,next,change,pwStatus);
        VBox accountTools=new VBox(10);accountTools.setPadding(new Insets(18));accountTools.setStyle(CARD);accountTools.setMaxWidth(650);Label accountTitle=new Label("Account, subscription & recovery");accountTitle.setStyle("-fx-font-size:18px;-fx-font-weight:bold;");String rawTier=account.path("subscriptionTier").asText("TIER_1");Label tier=new Label("Current subscription: "+("TIER_4".equals(rawTier)?"Business / Enterprise":rawTier.replace('_',' ')));tier.setStyle("-fx-text-fill:#475569;");Button billing=new Button("Open subscription / billing portal");billing.setStyle(PRIMARY);billing.setOnAction(e->getHostServices().showDocument(portal.baseUrl()+"/portal.html"));Button recovery=new Button("Create account / verify / recover login");recovery.setMaxWidth(Double.MAX_VALUE);recovery.setStyle("-fx-background-color:#f8fafc;-fx-text-fill:#0f4c81;-fx-border-color:#c7d9e8;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:9 10;-fx-font-weight:bold;");recovery.setOnAction(e->DesktopAccountAccess.showBeforeSignIn());accountTools.getChildren().addAll(accountTitle,tier,billing,recovery);
        if(isEnterpriseOwner()){Button business=new Button("Manage Business Team");business.setStyle(PRIMARY);business.setOnAction(e->setWorkspace(new EnterpriseTeamPane(portal,currentUsername,currentPassword)));accountTools.getChildren().add(business);}
        root.getChildren().addAll(title,profile,password,accountTools);ScrollPane scroll=new ScrollPane(root);scroll.setFitToWidth(true);scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");return scroll;
    }

    private boolean isEnterpriseOwner(){return currentBootstrap!=null&&"TIER_4".equals(currentBootstrap.path("user").path("subscriptionTier").asText(""));}
    private Button navButton(String text){Button b=new Button(text);b.setMaxWidth(Double.MAX_VALUE);b.setStyle(NAV);return b;}
    private Button tile(String title,String detail,Runnable action){Button b=new Button(title+"\n"+detail);b.setWrapText(true);b.setPrefWidth(460);b.setPrefHeight(95);b.setMaxWidth(Double.MAX_VALUE);b.setAlignment(Pos.CENTER_LEFT);b.setStyle("-fx-background-color:white;-fx-text-fill:#0f172a;-fx-font-weight:bold;-fx-background-radius:14;-fx-border-color:#dbe5ef;-fx-border-radius:14;-fx-padding:17 19;-fx-font-size:14px;");b.setOnAction(e->action.run());return b;}
    private void setWorkspace(Node node){workspace.getChildren().setAll(node);}

    private void checkForUpdates(Button button){checkForUpdates(button,false);}
    private void checkForUpdates(Button button,boolean manual){
        button.setDisable(true);button.setText("Checking updates…");
        CompletableFuture.supplyAsync(()->{try{return updater.check();}catch(Exception ex){throw new RuntimeException(ex);}})
            .whenComplete((info,err)->Platform.runLater(()->{
                button.setDisable(false);
                if(err!=null){
                    button.setText("Update check failed — retry");
                    button.setOnAction(e->checkForUpdates(button,true));
                    if(manual)new Alert(Alert.AlertType.ERROR,"Could not check for updates: "+message(unwrap(err))).showAndWait();
                    return;
                }
                if(info.updateAvailable()){
                    button.setText("Update v"+info.latestVersion());
                    button.setOnAction(e->installUpdate(info,button));
                }else{
                    button.setText("Up to date");
                    button.setOnAction(e->checkForUpdates(button,true));
                }
            }));
    }
    private void installUpdate(UpdateService.UpdateInfo info,Button button){Alert confirm=new Alert(Alert.AlertType.CONFIRMATION);confirm.initOwner(stage);confirm.setTitle("Update Electrical Workbench");confirm.setHeaderText("Install version "+info.latestVersion()+"?");confirm.setContentText("The MSI will download, verify, install, and relaunch the desktop app.");Optional<ButtonType> answer=confirm.showAndWait();if(answer.isEmpty()||answer.get()!=ButtonType.OK)return;button.setDisable(true);button.setText("Downloading…");CompletableFuture.supplyAsync(()->{try{return updater.downloadAndVerify(info);}catch(Exception ex){throw new RuntimeException(ex);}}).whenComplete((msi,err)->Platform.runLater(()->{if(err!=null){button.setDisable(false);button.setText("Update v"+info.latestVersion());new Alert(Alert.AlertType.ERROR,message(unwrap(err))).showAndWait();return;}try{updater.launchInstallerAndRelaunch(msi);Platform.exit();}catch(Exception ex){button.setDisable(false);new Alert(Alert.AlertType.ERROR,ex.getMessage()).showAndWait();}}));}

    private static String escapeHtml(String text){if(text==null)return "";return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static Label fieldLabel(String text){Label l=new Label(text);l.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;-fx-font-size:12px;");return l;}
    private static Throwable unwrap(Throwable t){Throwable c=t;while(c.getCause()!=null&&c!=c.getCause())c=c.getCause();return c;}
    private static String message(Throwable t){return t.getMessage()==null?t.getClass().getSimpleName():t.getMessage();}
    public static void main(String[] args){launch(args);}
}

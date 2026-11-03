package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Native JavaFX management surface for Tier 4 Business / Enterprise accounts. */
public final class EnterpriseTeamPane extends BorderPane {
    private final PortalClient portal;
    private final ObjectMapper json = new ObjectMapper();
    private final String username;
    private final String password;

    private final Label businessTitle = new Label("Business / Enterprise");
    private final TextField businessName = new TextField();
    private final Label seatCount = statValue();
    private final Label tier1Count = statValue();
    private final Label tier2Count = statValue();
    private final Label tier3Count = statValue();
    private final VBox roster = new VBox(10);
    private final Label pageStatus = statusLabel();
    private final TextField addUsername = new TextField();
    private final TextField addRole = new TextField();
    private final ComboBox<String> addTier = tierBox("TIER_1");

    private static final String CARD = "-fx-background-color:white;-fx-background-radius:16;-fx-border-color:#dbe5ef;-fx-border-radius:16;";
    private static final String INPUT = "-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-border-radius:9;-fx-background-radius:9;-fx-padding:9 11;";
    private static final String PRIMARY = "-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;-fx-padding:9 13;";
    private static final String GHOST = "-fx-background-color:#eef6fa;-fx-text-fill:#0f4c81;-fx-border-color:#c7d9e8;-fx-border-radius:9;-fx-background-radius:9;-fx-padding:9 12;-fx-font-weight:bold;";

    public EnterpriseTeamPane(PortalClient portal, String username, String password) {
        this.portal = portal;
        this.username = username;
        this.password = password;
        setStyle("-fx-background-color:#eef3f8;");

        VBox page = new VBox(18);
        page.setPadding(new Insets(4, 4, 24, 4));

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox heading = new VBox(3);
        businessTitle.setStyle("-fx-font-size:25px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label subtitle = new Label("Manage electricians, apprentices and other users assigned to this Tier 4 business account.");
        subtitle.setWrapText(true);
        subtitle.setStyle("-fx-text-fill:#64748b;");
        heading.getChildren().addAll(businessTitle, subtitle);
        Region push = new Region();
        HBox.setHgrow(push, Priority.ALWAYS);
        Button refresh = new Button("Refresh team");
        refresh.setStyle(GHOST);
        refresh.setOnAction(e -> load());
        header.getChildren().addAll(heading, push, refresh);

        HBox stats = new HBox(12,
            statCard("Managed seats", seatCount, "Users attached to this business"),
            statCard("Tier 1", tier1Count, "Core field tools"),
            statCard("Tier 2", tier2Count, "Expanded tools"),
            statCard("Tier 3", tier3Count, "Advanced / engineering tools")
        );
        for (Node n : stats.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        HBox management = new HBox(14, businessCard(), addMemberCard());
        HBox.setHgrow(management.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(management.getChildren().get(1), Priority.ALWAYS);

        VBox rosterCard = new VBox(12);
        rosterCard.setPadding(new Insets(18));
        rosterCard.setStyle(CARD);
        Label rosterTitle = new Label("Team roster");
        rosterTitle.setStyle("-fx-font-size:19px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label rosterNote = new Label("Each business-managed user keeps an individual login and can be assigned Tier 1, Tier 2 or Tier 3 access.");
        rosterNote.setWrapText(true);
        rosterNote.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");
        roster.getChildren().add(emptyRoster());
        rosterCard.getChildren().addAll(rosterTitle, rosterNote, roster);

        page.getChildren().addAll(header, pageStatus, stats, management, rosterCard);
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        setCenter(scroll);
        load();
    }

    private Node businessCard() {
        VBox card = new VBox(10);
        card.setPadding(new Insets(18));
        card.setStyle(CARD);
        Label title = new Label("Business profile");
        title.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label note = new Label("This organization name is shown to users managed by the business account.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");
        businessName.setPromptText("Business / organization name");
        businessName.setStyle(INPUT);
        Button save = new Button("Save business name");
        save.setStyle(PRIMARY);
        Label status = statusLabel();
        save.setOnAction(e -> {
            ObjectNode body = json.createObjectNode().put("businessName", businessName.getText());
            save.setDisable(true);
            request(() -> portal.renameEnterprise(username, password, body.toString()), ignored -> {
                status.setText("Business name saved.");
                load();
            }, status, save);
        });
        card.getChildren().addAll(title, note, fieldLabel("Business / organization name"), businessName, save, status);
        return card;
    }

    private Node addMemberCard() {
        VBox card = new VBox(10);
        card.setPadding(new Insets(18));
        card.setStyle(CARD);
        Label title = new Label("Add an existing user");
        title.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label note = new Label("The user must already have an Electrical Workbench account. Active personal paid subscriptions must be cancelled or transferred first.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");
        addUsername.setPromptText("employee username");
        addUsername.setStyle(INPUT);
        addRole.setPromptText("Apprentice, Journeyperson, Foreperson...");
        addRole.setStyle(INPUT);
        addTier.setMaxWidth(Double.MAX_VALUE);
        Button add = new Button("Add to business");
        add.setStyle(PRIMARY);
        Label status = statusLabel();
        add.setOnAction(e -> {
            String member = addUsername.getText().trim();
            if (member.isBlank()) { status.setText("Username is required."); return; }
            ObjectNode body = json.createObjectNode()
                .put("username", member)
                .put("subscriptionTier", addTier.getValue())
                .put("roleLabel", addRole.getText());
            add.setDisable(true);
            request(() -> portal.addEnterpriseMember(username, password, body.toString()), ignored -> {
                addUsername.clear();
                addRole.clear();
                addTier.setValue("TIER_1");
                status.setText("User added to the business account.");
                load();
            }, status, add);
        });
        card.getChildren().addAll(title, note, fieldLabel("Username"), addUsername, fieldLabel("Role / trade label"), addRole,
            fieldLabel("Assigned subscription tier"), addTier, add, status);
        return card;
    }

    private void load() {
        pageStatus.setText("Loading business account…");
        request(() -> portal.enterpriseDashboard(username, password), body -> {
            try {
                JsonNode data = json.readTree(body);
                businessTitle.setText(data.path("businessName").asText("Business / Enterprise"));
                businessName.setText(data.path("businessName").asText(""));
                seatCount.setText(String.valueOf(data.path("seatCount").asInt(0)));
                tier1Count.setText(String.valueOf(data.path("tier1Count").asInt(0)));
                tier2Count.setText(String.valueOf(data.path("tier2Count").asInt(0)));
                tier3Count.setText(String.valueOf(data.path("tier3Count").asInt(0)));
                renderRoster(data.path("members"));
                pageStatus.setText("Tier 4 Business / Enterprise · signed in as " + username);
            } catch (Exception ex) {
                pageStatus.setText("The business account returned an invalid response.");
            }
        }, pageStatus, null);
    }

    private void renderRoster(JsonNode members) {
        roster.getChildren().clear();
        if (!members.isArray() || members.isEmpty()) {
            roster.getChildren().add(emptyRoster());
            return;
        }
        for (JsonNode member : members) roster.getChildren().add(memberCard(member));
    }

    private Node memberCard(JsonNode member) {
        String memberUsername = member.path("username").asText("");
        VBox card = new VBox(9);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color:#f8fafc;-fx-background-radius:12;-fx-border-color:#dbe5ef;-fx-border-radius:12;");

        Label name = new Label(member.path("displayName").asText(memberUsername) + "  ·  " + memberUsername);
        name.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        String email = member.path("email").asText("");
        Label meta = new Label((email.isBlank() ? "No email" : email) + "  ·  " + (member.path("enabled").asBoolean(true) ? "Enabled" : "Disabled"));
        meta.setStyle("-fx-text-fill:#64748b;-fx-font-size:11px;");

        ComboBox<String> tier = tierBox(member.path("subscriptionTier").asText("TIER_1"));
        tier.setPrefWidth(130);
        TextField role = new TextField(member.path("roleLabel").asText(""));
        role.setPromptText("Apprentice / Journeyperson");
        role.setStyle(INPUT);
        HBox.setHgrow(role, Priority.ALWAYS);

        Button saveTier = new Button("Save tier");
        saveTier.setStyle(GHOST);
        Button saveRole = new Button("Save role");
        saveRole.setStyle(GHOST);
        Button remove = new Button("Remove");
        remove.setStyle("-fx-background-color:#334155;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;-fx-padding:9 12;");
        Label status = statusLabel();

        saveTier.setOnAction(e -> {
            ObjectNode body = json.createObjectNode().put("subscriptionTier", tier.getValue());
            saveTier.setDisable(true);
            request(() -> portal.updateEnterpriseMemberTier(username, password, memberUsername, body.toString()), ignored -> {
                status.setText("Tier updated.");
                load();
            }, status, saveTier);
        });
        saveRole.setOnAction(e -> {
            ObjectNode body = json.createObjectNode().put("roleLabel", role.getText());
            saveRole.setDisable(true);
            request(() -> portal.updateEnterpriseMemberRole(username, password, memberUsername, body.toString()), ignored -> {
                status.setText("Role updated.");
                load();
            }, status, saveRole);
        });
        remove.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Remove business user");
            confirm.setHeaderText("Remove " + memberUsername + " from this business account?");
            confirm.setContentText("Their prior personal subscription tier will be restored.");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            remove.setDisable(true);
            request(() -> portal.removeEnterpriseMember(username, password, memberUsername), ignored -> {
                status.setText("User removed.");
                load();
            }, status, remove);
        });

        HBox controls = new HBox(8, tier, role, saveTier, saveRole, remove);
        controls.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(name, meta, controls, status);
        return card;
    }

    private void request(ThrowingSupplier supplier, Consumer<String> success, Label status, Button button) {
        if (status != null) status.setText("Working…");
        CompletableFuture.supplyAsync(() -> {
            try { return supplier.get(); }
            catch (Exception ex) { throw new RuntimeException(ex); }
        }).whenComplete((body, error) -> Platform.runLater(() -> {
            if (button != null) button.setDisable(false);
            if (error != null) {
                Throwable cause = error;
                while (cause.getCause() != null && cause != cause.getCause()) cause = cause.getCause();
                if (status != null) status.setText(cause.getMessage() == null ? "Request failed." : cause.getMessage());
                return;
            }
            success.accept(body);
        }));
    }

    private static Node statCard(String title, Label value, String subtitle) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(14));
        card.setMinWidth(150);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle(CARD);
        Label heading = new Label(title.toUpperCase());
        heading.setStyle("-fx-text-fill:#64748b;-fx-font-size:10px;-fx-font-weight:bold;");
        Label sub = new Label(subtitle);
        sub.setWrapText(true);
        sub.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:10px;");
        card.getChildren().addAll(heading, value, sub);
        return card;
    }

    private static Label statValue() {
        Label value = new Label("—");
        value.setStyle("-fx-text-fill:#0e7490;-fx-font-size:24px;-fx-font-weight:bold;");
        return value;
    }

    private static ComboBox<String> tierBox(String value) {
        ComboBox<String> box = new ComboBox<>();
        box.getItems().addAll("TIER_1", "TIER_2", "TIER_3");
        box.setValue(box.getItems().contains(value) ? value : "TIER_1");
        box.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(String tier) { return tier == null ? "" : tier.replace('_', ' '); }
            @Override public String fromString(String text) { return text; }
        });
        return box;
    }

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;-fx-font-size:12px;");
        return label;
    }

    private static Label statusLabel() {
        Label label = new Label();
        label.setWrapText(true);
        label.setStyle("-fx-text-fill:#64748b;-fx-font-size:11px;");
        return label;
    }

    private static Node emptyRoster() {
        VBox empty = new VBox(4);
        empty.setPadding(new Insets(16));
        empty.setStyle("-fx-background-color:#f8fafc;-fx-background-radius:12;-fx-border-color:#dbe5ef;-fx-border-radius:12;");
        Label title = new Label("No managed users yet");
        title.setStyle("-fx-font-weight:bold;-fx-text-fill:#334155;");
        Label note = new Label("Add an existing user above to create the first business-managed seat.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill:#64748b;");
        empty.getChildren().addAll(title, note);
        return empty;
    }

    @FunctionalInterface
    private interface ThrowingSupplier { String get() throws Exception; }
}

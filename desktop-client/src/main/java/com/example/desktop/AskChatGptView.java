package com.example.desktop;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class AskChatGptView {
    private AskChatGptView() {}

    private static final String CARD = "-fx-background-color:white;-fx-background-radius:16;-fx-border-color:#dce6ef;-fx-border-radius:16;-fx-effect:dropshadow(gaussian,rgba(15,23,42,.05),15,0,0,4);";
    private static final String PRIMARY = "-fx-background-color:linear-gradient(to right,#1d4ed8,#0e7490);-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:10;-fx-padding:10 14;-fx-cursor:hand;";
    private static final String GHOST = "-fx-background-color:#edf6fb;-fx-text-fill:#0f4c81;-fx-border-color:#c7d9e8;-fx-border-radius:10;-fx-background-radius:10;-fx-padding:9 13;-fx-font-weight:bold;-fx-cursor:hand;";

    static Node create(String username) {
        ChatGptDesktopClient client = new ChatGptDesktopClient(username);
        VBox root = new VBox(14);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color:#f3f6fa;");

        Label title = new Label("Ask ChatGPT");
        title.setStyle("-fx-font-size:24px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label subtitle = new Label("Connect a ChatGPT account and use an eligible ChatGPT plan for AI requests directly inside the desktop Electrical Workbench.");
        subtitle.setWrapText(true);
        subtitle.setStyle("-fx-text-fill:#64748b;-fx-font-size:13px;");

        VBox warning = card("Important");
        Label warningText = new Label("ChatGPT can make mistakes. For electrical work, verify CEC/OESC requirements, manufacturer instructions, engineering documents and site safety procedures before acting on an answer.");
        warningText.setWrapText(true); warningText.setStyle("-fx-text-fill:#92400e;");
        Label privacy = new Label("Connecting does not give Electrical Workbench access to your existing ChatGPT conversations or ChatGPT memory.");
        privacy.setWrapText(true); privacy.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");
        warning.getChildren().addAll(warningText, privacy);

        VBox accountCard = card("ChatGPT connection");
        Label accountName = new Label(); accountName.setStyle("-fx-font-weight:bold;-fx-text-fill:#0f172a;");
        Label accountEmail = new Label(); accountEmail.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");
        Label connectionState = new Label(); connectionState.setStyle("-fx-font-weight:bold;");
        HBox accountButtons = new HBox(8);
        Button connect = new Button("Continue with ChatGPT"); connect.setStyle(PRIMARY);
        Button reconnect = new Button("Reconnect"); reconnect.setStyle(GHOST);
        Button different = new Button("Different account"); different.setStyle(GHOST);
        Button manageUsage = new Button("Manage usage"); manageUsage.setStyle(GHOST);
        Button disconnect = new Button("Disconnect"); disconnect.setStyle(GHOST);
        accountButtons.setAlignment(Pos.CENTER_LEFT);
        accountButtons.getChildren().addAll(connect,reconnect,different,manageUsage,disconnect);
        accountCard.getChildren().addAll(accountName,accountEmail,connectionState,accountButtons);

        VBox modelCard = card("Model");
        ComboBox<ChatGptDesktopClient.Model> models = new ComboBox<>(); models.setMaxWidth(Double.MAX_VALUE);
        Button refreshModels = new Button("Refresh models"); refreshModels.setStyle(GHOST);
        HBox modelRow = new HBox(8,models,refreshModels); HBox.setHgrow(models,Priority.ALWAYS);
        modelCard.getChildren().add(modelRow);

        VBox chatMessages = new VBox(10); chatMessages.setPadding(new Insets(4));
        ScrollPane chatScroll = new ScrollPane(chatMessages); chatScroll.setFitToWidth(true); chatScroll.setPrefViewportHeight(330); chatScroll.setStyle("-fx-background:#eef3f8;-fx-background-color:#eef3f8;");
        VBox.setVgrow(chatScroll, Priority.ALWAYS);

        TextArea input = new TextArea(); input.setPromptText("Ask ChatGPT about electrical theory, troubleshooting, calculations, study topics, projects, or anything else…"); input.setPrefRowCount(3); input.setWrapText(true);
        Button clear = new Button("Clear chat"); clear.setStyle(GHOST);
        Button send = new Button("Send"); send.setStyle(PRIMARY); send.setDefaultButton(true);
        HBox composerButtons = new HBox(8,clear,send); composerButtons.setAlignment(Pos.CENTER_RIGHT);
        VBox composer = new VBox(8,input,composerButtons);

        Label status = new Label(); status.setWrapText(true); status.setStyle("-fx-text-fill:#0e7490;-fx-font-size:12px;");
        Label error = new Label(); error.setWrapText(true); error.setStyle("-fx-text-fill:#b91c1c;-fx-font-size:12px;");
        List<ChatGptDesktopClient.ChatLine> history = new ArrayList<>();

        Runnable renderConnection = () -> {
            ChatGptDesktopClient.Profile p = client.profile();
            boolean connected = p != null && p.accessToken() != null && !p.accessToken().isBlank();
            accountName.setText(p == null ? "No ChatGPT account connected" : (p.name()==null||p.name().isBlank() ? (p.email()==null||p.email().isBlank()?"Connected account":p.email()) : p.name()));
            accountEmail.setText(p == null || p.email()==null ? "" : p.email());
            connectionState.setText(p == null ? "Connect an account to enable AI" : p.sharingEnabled() ? "ChatGPT plan usage enabled" : connected ? "Signed in · plan usage not enabled" : "Connection saved · reconnect required");
            connectionState.setStyle("-fx-font-weight:bold;-fx-text-fill:" + (p != null && p.sharingEnabled() ? "#15803d" : "#b45309") + ";");
            connect.setVisible(p == null); connect.setManaged(p == null);
            reconnect.setVisible(p != null); reconnect.setManaged(p != null);
            different.setVisible(p != null); different.setManaged(p != null);
            manageUsage.setVisible(p != null); manageUsage.setManaged(p != null);
            disconnect.setVisible(p != null); disconnect.setManaged(p != null);
            boolean ready = p != null && p.sharingEnabled();
            modelCard.setVisible(ready); modelCard.setManaged(ready);
            chatScroll.setVisible(ready); chatScroll.setManaged(ready);
            composer.setVisible(ready); composer.setManaged(ready);
            send.setDisable(!ready || models.getValue()==null || input.getText().isBlank());
        };

        Runnable refreshModelList = () -> {
            status.setText("Loading ChatGPT models…"); error.setText(""); refreshModels.setDisable(true);
            CompletableFuture.supplyAsync(() -> {
                try { return client.listModels(); } catch (Exception ex) { throw new RuntimeException(ex); }
            }).whenComplete((list,err) -> Platform.runLater(() -> {
                refreshModels.setDisable(false);
                if (err != null) { error.setText(message(err)); status.setText(""); return; }
                ChatGptDesktopClient.Model prior=models.getValue(); models.getItems().setAll(list);
                if (prior != null) for (ChatGptDesktopClient.Model m:list) if (m.slug().equals(prior.slug())) {models.setValue(m);break;}
                if (models.getValue()==null && !list.isEmpty()) models.setValue(list.get(0));
                status.setText(list.isEmpty()?"No models were returned for this ChatGPT account.":"Connected · "+list.size()+" model(s) available");
                renderConnection.run();
            }));
        };

        java.util.function.Consumer<Boolean> startConnect = forceNew -> {
            connect.setDisable(true); reconnect.setDisable(true); different.setDisable(true); error.setText(""); status.setText("Opening ChatGPT sign-in…");
            CompletableFuture.supplyAsync(() -> {
                try { return client.connect(forceNew, s -> Platform.runLater(() -> status.setText(s))); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            }).whenComplete((profile,err) -> Platform.runLater(() -> {
                connect.setDisable(false); reconnect.setDisable(false); different.setDisable(false);
                if (err != null) { error.setText(message(err)); status.setText(""); renderConnection.run(); return; }
                renderConnection.run(); status.setText(profile.sharingEnabled()?"ChatGPT account connected. Loading models…":"Signed in, but ChatGPT plan usage was not enabled.");
                if (profile.sharingEnabled()) refreshModelList.run();
            }));
        };

        connect.setOnAction(e -> startConnect.accept(false));
        reconnect.setOnAction(e -> startConnect.accept(false));
        different.setOnAction(e -> startConnect.accept(true));
        refreshModels.setOnAction(e -> refreshModelList.run());
        manageUsage.setOnAction(e -> open(ChatGptDesktopClient.MANAGE_USAGE));
        disconnect.setOnAction(e -> {
            status.setText("Disconnecting ChatGPT…"); error.setText("");
            CompletableFuture.runAsync(client::disconnect).whenComplete((v,err) -> Platform.runLater(() -> {
                history.clear(); chatMessages.getChildren().clear(); models.getItems().clear(); status.setText("ChatGPT disconnected from this desktop app."); renderConnection.run();
            }));
        });
        clear.setOnAction(e -> {history.clear();chatMessages.getChildren().clear();});
        input.textProperty().addListener((o,a,b) -> renderConnection.run());
        models.valueProperty().addListener((o,a,b) -> renderConnection.run());

        send.setOnAction(e -> {
            String prompt=input.getText().trim(); ChatGptDesktopClient.Model model=models.getValue();
            if(prompt.isBlank()||model==null)return;
            input.clear(); error.setText(""); status.setText("ChatGPT is responding…"); send.setDisable(true);
            ChatGptDesktopClient.ChatLine user=new ChatGptDesktopClient.ChatLine("user",prompt); history.add(user); chatMessages.getChildren().add(messageCard("You",prompt,false));
            Label streamText=new Label(); streamText.setWrapText(true); streamText.setStyle("-fx-text-fill:#0f172a;"); VBox streamCard=card("ChatGPT"); streamCard.getChildren().add(streamText); chatMessages.getChildren().add(streamCard); scrollBottom(chatScroll);
            List<ChatGptDesktopClient.ChatLine> requestHistory=new ArrayList<>(history.subList(Math.max(0,history.size()-20),history.size()));
            CompletableFuture.supplyAsync(() -> {
                try { return client.streamResponse(model.slug(),requestHistory,delta -> Platform.runLater(() -> {streamText.setText(streamText.getText()+delta);scrollBottom(chatScroll);})); }
                catch(Exception ex){throw new RuntimeException(ex);}
            }).whenComplete((answer,err) -> Platform.runLater(() -> {
                if(err!=null){chatMessages.getChildren().remove(streamCard);error.setText(message(err));status.setText("");renderConnection.run();return;}
                String finalText=(answer==null||answer.isBlank()?streamText.getText():answer).trim(); streamText.setText(finalText); if(!finalText.isBlank())history.add(new ChatGptDesktopClient.ChatLine("assistant",finalText)); status.setText("Using ChatGPT plan"); renderConnection.run(); scrollBottom(chatScroll);
            }));
        });

        root.getChildren().addAll(title,subtitle,warning,accountCard,modelCard,chatScroll,composer,status,error);
        renderConnection.run();
        if (client.profile()!=null && client.profile().sharingEnabled()) refreshModelList.run();
        return root;
    }

    private static VBox card(String title){VBox box=new VBox(9);box.setPadding(new Insets(15));box.setStyle(CARD);Label h=new Label(title);h.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#0e7490;");box.getChildren().add(h);return box;}
    private static Node messageCard(String title,String text,boolean assistant){VBox box=card(title);Label l=new Label(text);l.setWrapText(true);l.setStyle("-fx-text-fill:#0f172a;");box.getChildren().add(l);return box;}
    private static void scrollBottom(ScrollPane pane){Platform.runLater(() -> pane.setVvalue(1.0));}
    private static String message(Throwable err){Throwable c=err;while(c.getCause()!=null)c=c.getCause();return c.getMessage()==null?c.toString():c.getMessage();}
    private static void open(URI uri){try{if(Desktop.isDesktopSupported())Desktop.getDesktop().browse(uri);}catch(Exception ignored){}}
}
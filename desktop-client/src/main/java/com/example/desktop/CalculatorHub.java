package com.example.desktop;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;

public final class CalculatorHub {
  private CalculatorHub(){}

  public static Node create(PortalClient portal,String username,String password){
    Node tradeContent=CalculatorViews.create(portal,username,password);
    Map<String,TitledPane> calculators=extractCalculators(tradeContent);

    TabPane sections=new TabPane();
    sections.getTabs().addAll(
      sectionTab("Section 2 — General Rules", Section2Views.create()),
      sectionTab("Section 8 — Circuit Loading & Demand", ServiceCalculatorViews.create(portal,username,password)),
      sectionTab("Section 12 — Wiring Methods", Section12Views.create(calculators.get("conduit"),calculators.get("box"))),
      sectionTab("Section 14 — Protection & Control", sectionPage("Section 14 — Protection & Control","Tap and splitter worksheets with source protection, conductor ampacity and downstream protection checks.",calculators.get("tap"))),
      sectionTab("Section 16 — Class 1 & Class 2", Section16Views.create()),
      sectionTab("Section 24 — Patient Care Areas", Section24Views.create()),
      sectionTab("Section 26 — Electrical Equipment", Section26Views.create(portal,username,password)),
      sectionTab("Section 28 — Motors & Generators", Section28Views.create(portal,username,password)),
      sectionTab("Section 32 — Fire Alarm / Fire Pumps", Section32Views.create()),
      sectionTab("Section 42 — Electric Welders", Section42Views.create(portal,username,password))
    );
    sections.getSelectionModel().selectFirst();

    VBox root=new VBox();
    Label title=new Label("CEC 2024 Calculator Sections");title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#14213d;");
    Label subtitle=new Label("Choose the CEC section first, then use the calculator, worksheet or quick-reference tool inside that section.");subtitle.setWrapText(true);subtitle.setStyle("-fx-text-fill:#64748b;");
    VBox header=new VBox(5,title,subtitle);header.setPadding(new Insets(18,22,10,22));VBox.setVgrow(sections,Priority.ALWAYS);root.getChildren().addAll(header,sections);return root;
  }

  private static Tab sectionTab(String title,Node content){Tab t=new Tab(title,content);t.setClosable(false);return t;}
  private static Node sectionPage(String title,String description,TitledPane... panes){VBox body=new VBox(12);body.setPadding(new Insets(18));Label h=new Label(title);h.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#14213d;");Label d=new Label(description);d.setWrapText(true);d.setStyle("-fx-text-fill:#64748b;");Accordion accordion=new Accordion();for(TitledPane p:panes)if(p!=null)accordion.getPanes().add(p);if(!accordion.getPanes().isEmpty())accordion.setExpandedPane(accordion.getPanes().get(0));body.getChildren().addAll(h,d,new Separator(),accordion);ScrollPane scroll=new ScrollPane(body);scroll.setFitToWidth(true);scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);return scroll;}
  private static Map<String,TitledPane> extractCalculators(Node root){Map<String,TitledPane> found=new LinkedHashMap<>();findAccordionPanes(root,found);return found;}
  private static void findAccordionPanes(Node node,Map<String,TitledPane> found){if(node instanceof Accordion a){List<TitledPane> copy=new ArrayList<>(a.getPanes());a.getPanes().clear();for(TitledPane p:copy){String title=p.getText()==null?"":p.getText().toLowerCase(Locale.ROOT);if(title.startsWith("service demand"))continue;if(title.startsWith("transformer"))found.put("transformer",p);else if(title.startsWith("motor —"))found.put("motor",p);else if(title.startsWith("refrigeration"))found.put("refrigeration",p);else if(title.startsWith("multiple motors"))found.put("multi-motor",p);else if(title.startsWith("capacitor"))found.put("capacitor",p);else if(title.startsWith("welder"))found.put("welder",p);else if(title.startsWith("taps"))found.put("tap",p);else if(title.startsWith("conduit"))found.put("conduit",p);else if(title.startsWith("box fill"))found.put("box",p);}}if(node instanceof Parent p){for(Node child:new ArrayList<>(p.getChildrenUnmodifiable()))findAccordionPanes(child,found);}}
}

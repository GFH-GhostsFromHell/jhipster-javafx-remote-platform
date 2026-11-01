package com.example.desktop;

import javafx.geometry.Insets;
import javafx.geometry.Side;
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
    sections.setSide(Side.LEFT);
    sections.setTabMinWidth(205);
    sections.setTabMaxWidth(205);
    sections.setTabMinHeight(48);
    sections.setStyle("-fx-background-color:#edf3f8;-fx-open-tab-animation:none;-fx-close-tab-animation:none;");
    sections.getTabs().addAll(
      sectionTab("02  General Rules", Section2Views.create()),
      sectionTab("08  Service & Demand", ServiceCalculatorViews.create(portal,username,password)),
      sectionTab("12  Wiring Methods", Section12Views.create(calculators.get("conduit"),calculators.get("box"))),
      sectionTab("14  Protection & Control", sectionPage("Section 14 — Protection & Control","Tap and splitter worksheets with source protection, conductor ampacity and downstream protection checks.",calculators.get("tap"))),
      sectionTab("16  Class 1 & Class 2", Section16Views.create()),
      sectionTab("24  Patient Care", Section24Views.create()),
      sectionTab("26  Transformers & Capacitors", Section26Views.create(portal,username,password)),
      sectionTab("28  Motors & Generators", Section28Views.create(portal,username,password)),
      sectionTab("32  Fire Alarm / Fire Pumps", Section32Views.create()),
      sectionTab("42  Electric Welders", Section42Views.create(portal,username,password))
    );
    sections.getSelectionModel().selectFirst();

    VBox root=new VBox();
    root.setStyle("-fx-background-color:#f4f7fb;");
    HBox header=new HBox(16);
    header.setPadding(new Insets(18,24,16,24));
    header.setStyle("-fx-background-color:linear-gradient(to right,#07111f,#0b2743);-fx-border-color:transparent transparent #164e63 transparent;-fx-border-width:0 0 1 0;");
    VBox copy=new VBox(3);
    Label eyebrow=new Label("ELECTRICAL WORKBENCH  •  CEC 2024");
    eyebrow.setStyle("-fx-text-fill:#67e8f9;-fx-font-size:10px;-fx-font-weight:bold;");
    Label title=new Label("Calculator & Reference Workspace");
    title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:white;");
    Label subtitle=new Label("Choose a CEC section on the left, then work through the calculator or worksheet in the same order you would solve it on paper.");
    subtitle.setWrapText(true);subtitle.setStyle("-fx-text-fill:#cbd5e1;-fx-font-size:12px;");
    copy.getChildren().addAll(eyebrow,title,subtitle);
    Region push=new Region();HBox.setHgrow(push,Priority.ALWAYS);
    Label edition=new Label("CEC 2024");
    edition.setStyle("-fx-background-color:#0e7490;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:18;-fx-padding:7 12;");
    header.getChildren().addAll(copy,push,edition);

    VBox.setVgrow(sections,Priority.ALWAYS);
    root.getChildren().addAll(header,sections);
    return root;
  }

  private static Tab sectionTab(String title,Node content){Tab t=new Tab(title,content);t.setClosable(false);return t;}
  private static Node sectionPage(String title,String description,TitledPane... panes){VBox body=new VBox(12);body.setPadding(new Insets(22));body.setStyle("-fx-background-color:#f8fafc;");Label h=new Label(title);h.setStyle("-fx-font-size:21px;-fx-font-weight:bold;-fx-text-fill:#0f172a;");Label d=new Label(description);d.setWrapText(true);d.setStyle("-fx-text-fill:#64748b;");Accordion accordion=new Accordion();for(TitledPane p:panes)if(p!=null)accordion.getPanes().add(p);if(!accordion.getPanes().isEmpty())accordion.setExpandedPane(accordion.getPanes().get(0));body.getChildren().addAll(h,d,new Separator(),accordion);ScrollPane scroll=new ScrollPane(body);scroll.setFitToWidth(true);scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);scroll.setStyle("-fx-background:#f8fafc;-fx-background-color:#f8fafc;");return scroll;}
  private static Map<String,TitledPane> extractCalculators(Node root){Map<String,TitledPane> found=new LinkedHashMap<>();findAccordionPanes(root,found);return found;}
  private static void findAccordionPanes(Node node,Map<String,TitledPane> found){if(node instanceof Accordion a){List<TitledPane> copy=new ArrayList<>(a.getPanes());a.getPanes().clear();for(TitledPane p:copy){String title=p.getText()==null?"":p.getText().toLowerCase(Locale.ROOT);if(title.startsWith("service demand"))continue;if(title.startsWith("transformer"))found.put("transformer",p);else if(title.startsWith("motor —"))found.put("motor",p);else if(title.startsWith("refrigeration"))found.put("refrigeration",p);else if(title.startsWith("multiple motors"))found.put("multi-motor",p);else if(title.startsWith("capacitor"))found.put("capacitor",p);else if(title.startsWith("welder"))found.put("welder",p);else if(title.startsWith("taps"))found.put("tap",p);else if(title.startsWith("conduit"))found.put("conduit",p);else if(title.startsWith("box fill"))found.put("box",p);}}if(node instanceof Parent p){for(Node child:new ArrayList<>(p.getChildrenUnmodifiable()))findAccordionPanes(child,found);}}
}

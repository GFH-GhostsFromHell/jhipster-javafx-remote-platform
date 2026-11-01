package com.example.desktop;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class Section2Views {
  private Section2Views(){}
  public static Node create(){
    BorderPane root=new BorderPane();VBox head=new VBox(5);head.setPadding(new Insets(18,22,10,22));
    Label h=new Label("Section 2 — General Rules");h.setStyle("-fx-font-size:21px;-fx-font-weight:bold;-fx-text-fill:#14213d;");
    Label s=new Label("Fast-reference reminders for the general installation rules that show up throughout the CEC and on C of Q questions.");s.setWrapText(true);s.setStyle("-fx-text-fill:#64748b;");head.getChildren().addAll(h,s);
    TabPane tabs=new TabPane();tabs.getTabs().addAll(tab("Working Space",working()),tab("Equipment & Environment",equipment()),tab("Exam Reminders",exam()));root.setTop(head);root.setCenter(tabs);return root;
  }
  private static Node working(){return page(
    card("Rule 2-308 — Working space","Provide and maintain working space so electrical equipment can be operated and maintained safely. For common panelboard/MCC questions, think unobstructed working space in front with secure footing; where voltage, exposed live parts, or special equipment change the requirement, use the applicable table/subrule rather than a memorized one-size-fits-all value."),
    card("Door / access check","On exam questions, separate working-space depth from whether doors/covers can open enough to permit service. Do not treat a door-opening angle as a substitute for required working space."),
    card("Higher-risk equipment","Equipment above normal low-voltage conditions, large equipment, or locations with constrained egress can trigger additional working-space/egress requirements. Check Rules 2-308, 2-310 and the applicable table."));}
  private static Node equipment(){return page(
    card("Approval and suitability","Start with whether equipment is approved/acceptable for the installation and suitable for the environment. Then check the Section that governs that equipment."),
    card("Mechanical damage / environment","Ask: dry, damp, wet, corrosive, hazardous, outdoors, subject to physical damage, high temperature, or combustible construction? Those conditions often change the permitted wiring method or enclosure."),
    card("Manufacturer markings","CEC calculations do not override equipment markings. Terminal temperature ratings, MCA/MOCP, transformer data, motor nameplates, enclosure ratings and installation instructions can control the final selection."),
    card("Accessibility vs readily accessible","Treat these as Code terms, not casual wording. A device can be physically reachable yet still fail a requirement for ready access if ladders, removal of obstacles, or special access is needed."));}
  private static Node exam(){return page(
    card("General-rule question strategy","1) Identify the equipment/location. 2) Check Section 2 for general installation conditions. 3) Go to the specific equipment Section. 4) If a specific Section conflicts with a general rule, the specific requirement normally controls for that application."),
    card("Common traps","Do not mix conductor insulation temperature with equipment termination temperature. Do not assume a raceway is a bonding path where the Code requires a separate bonding conductor. Do not apply a continuous-load factor twice."),
    card("Use the Code edition shown","This app is organized around CEC 2024. Provincial amendments, ESA bulletins and equipment-specific requirements still need to be checked for field work."));}
  private static VBox page(Node... n){VBox v=new VBox(12,n);v.setPadding(new Insets(18));ScrollPane s=new ScrollPane(v);s.setFitToWidth(true);VBox wrap=new VBox(s);VBox.setVgrow(s,Priority.ALWAYS);return wrap;}
  private static VBox card(String title,String text){Label h=new Label(title);h.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#14213d;");Label b=new Label(text);b.setWrapText(true);b.setStyle("-fx-text-fill:#475569;");VBox v=new VBox(7,h,b);v.setPadding(new Insets(14));v.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-background-radius:10;-fx-border-radius:10;");return v;}
  private static Tab tab(String t,Node n){Tab x=new Tab(t,n);x.setClosable(false);return x;}
}

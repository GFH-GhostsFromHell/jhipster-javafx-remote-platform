package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.concurrent.CompletableFuture;

public final class ServiceCalculatorViews {
  private static final ObjectMapper JSON=new ObjectMapper();
  private ServiceCalculatorViews(){}

  public static Node create(PortalClient p,String u,String pw){
    VBox root=new VBox(14);root.setPadding(new Insets(22));
    Label h=new Label("CEC 2024 Service Calculations");h.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#14213d;");
    Label s=new Label("Occupancy-specific service worksheets based on the CEC 2024 Section 8 methods used in your practice questions.");s.setWrapText(true);s.setStyle("-fx-text-fill:#64748b;");
    Accordion a=new Accordion(residential(p,u,pw),apartment(p,u,pw),school(p,u,pw),hospital(p,u,pw),other(p,u,pw));a.setExpandedPane(a.getPanes().get(0));
    root.getChildren().addAll(h,s,new Separator(),a);return root;
  }

  private static TitledPane residential(PortalClient p,String u,String pw){
    GridPane f=form();TextField ground=tf("150"),upper=tf("100"),basement=tf("100"),range=tf("12"),roomHeat=tf("25"),central=tf("0"),ac=tf("7"),other=tf("8.5"),full=tf("0"),ev=tf("0"),v=tf("240");CheckBox interlock=new CheckBox("Heating and A/C interlocked");interlock.setSelected(true);
    row(f,0,"Ground-floor living area (m²)",ground);row(f,1,"Upper-floor living area (m²)",upper);row(f,2,"Qualifying basement area (m²; 75% counted)",basement);row(f,3,"Single range rating (kW)",range);row(f,4,"Room/baseboard heat connected (kW)",roomHeat);row(f,5,"Central electric heat (kW, 100%)",central);row(f,6,"Air conditioning / heat pump (kW)",ac);row(f,7,"Other loads >1500 W total (kW)",other);row(f,8,"Tankless/pool/spa 100% loads (kW)",full);row(f,9,"EVSE load (kW)",ev);row(f,10,"Service voltage",v);f.add(interlock,1,11);
    return pane("Residential dwelling — Rule 8-200",f,()->p.residentialServiceCalculator(u,pw,"{\"groundArea\":"+n(ground)+",\"upperArea\":"+n(upper)+",\"basementArea\":"+n(basement)+",\"rangeKw\":"+n(range)+",\"roomHeatKw\":"+n(roomHeat)+",\"centralHeatKw\":"+n(central)+",\"acKw\":"+n(ac)+",\"interlocked\":"+interlock.isSelected()+",\"otherOver1500Kw\":"+n(other)+",\"dedicated100Kw\":"+n(full)+",\"evseKw\":"+n(ev)+",\"voltage\":"+n(v)+"}"),r->
      "CEC living area: "+d(r,"livingAreaM2")+" m²\nBasic load: "+d(r,"basicLoadW")+" W\nRange demand: "+d(r,"rangeDemandW")+" W\nHeating demand: "+d(r,"heatingDemandW")+" W\nA/C load: "+d(r,"airConditioningW")+" W\nHVAC used: "+d(r,"hvacDemandW")+" W\nOther-load demand: "+d(r,"otherLoadsDemandW")+" W\nBefore minimum: "+d(r,"calculatedBeforeMinimumW")+" W\nRule minimum: "+d(r,"minimumRuleLoadW")+" W\n\nFINAL DEMAND: "+d(r,"calculatedDemandW")+" W\nSERVICE CURRENT: "+d(r,"serviceCurrentA")+" A\nSuggested standard service: "+i(r,"suggestedStandardServiceA")+" A\n\n"+refs(r));
  }

  private static TitledPane apartment(PortalClient p,String u,String pw){
    GridPane f=form();TextField suites=tf("12"),area=tf("90"),range=tf("12"),other=tf("0"),heat=tf("0"),ac=tf("0"),house=tf("18"),houseDf=tf("0.75"),houseCont=tf("1.25"),v=tf("240");ComboBox<Integer> ph=phases(1);CheckBox interlock=new CheckBox("Suite heating and A/C interlocked");interlock.setSelected(true);
    row(f,0,"Number of suites",suites);row(f,1,"Suite area each (m²)",area);row(f,2,"Range rating per suite (kW)",range);row(f,3,"Other suite load each (kW)",other);row(f,4,"Electric heat per suite (kW)",heat);row(f,5,"A/C per suite (kW)",ac);row(f,6,"House/common connected load (kW)",house);row(f,7,"House demand factor",houseDf);row(f,8,"House continuous multiplier",houseCont);row(f,9,"Service voltage",v);row(f,10,"Phases",ph);f.add(interlock,1,11);
    return pane("Apartment / multi-suite — Rule 8-202",f,()->p.apartmentServiceCalculator(u,pw,"{\"suites\":"+(int)n(suites)+",\"suiteArea\":"+n(area)+",\"rangeKw\":"+n(range)+",\"suiteOtherKw\":"+n(other)+",\"suiteHeatKw\":"+n(heat)+",\"suiteAcKw\":"+n(ac)+",\"suiteInterlocked\":"+interlock.isSelected()+",\"houseLoadKw\":"+n(house)+",\"houseDemandFactor\":"+n(houseDf)+",\"houseContinuousFactor\":"+n(houseCont)+",\"voltage\":"+n(v)+",\"phases\":"+ph.getValue()+"}"),r->
      "Suite count: "+i(r,"suiteCount")+"\nCalculated load per suite: "+d(r,"calculatedLoadPerSuiteW")+" W\nBasic load per suite: "+d(r,"basicLoadPerSuiteW")+" W\nRange demand per suite: "+d(r,"rangeDemandPerSuiteW")+" W\nSuite HVAC demand: "+d(r,"suiteHvacDemandW")+" W\n\nDemand groups:\n  1 suite @ 100%: "+i(r,"firstSuite100Percent")+"\n  next @ 65%: "+i(r,"nextSuites65Percent")+"\n  next @ 40%: "+i(r,"nextSuites40Percent")+"\n  remaining @ 25%: "+i(r,"remainingSuites25Percent")+"\n\nSuite demand after diversity: "+d(r,"suiteDemandAfterDiversityW")+" W\nHouse connected load: "+d(r,"houseConnectedLoadW")+" W\nHouse demand used: "+d(r,"houseDemandW")+" W\n\nFINAL DEMAND: "+d(r,"calculatedDemandW")+" W\nSERVICE CURRENT: "+d(r,"serviceCurrentA")+" A\nSuggested standard service: "+i(r,"suggestedStandardServiceA")+" A\n\n"+refs(r));
  }

  private static TitledPane school(PortalClient p,String u,String pw){
    GridPane f=form();TextField area=tf("900"),classroom=tf("550"),other=tf("40"),heat=tf("0"),ac=tf("0"),ev=tf("0"),cord=tf("0"),v=tf("240");ComboBox<Integer> ph=phases(1);CheckBox interlock=new CheckBox("Heating and A/C interlocked");interlock.setSelected(true);
    row(f,0,"Total building area (m²)",area);row(f,1,"Classroom area (m²)",classroom);row(f,2,"Other permanently connected load (kW)",other);row(f,3,"Electric space heat connected (kW)",heat);row(f,4,"Air conditioning (kW)",ac);row(f,5,"EVSE (kW)",ev);row(f,6,"High-power cord/receptacle load (kW)",cord);row(f,7,"Voltage",v);row(f,8,"Phases",ph);f.add(interlock,1,9);
    return pane("School — Rule 8-204",f,()->p.schoolServiceCalculator(u,pw,"{\"totalArea\":"+n(area)+",\"classroomArea\":"+n(classroom)+",\"otherKw\":"+n(other)+",\"heatKw\":"+n(heat)+",\"acKw\":"+n(ac)+",\"interlocked\":"+interlock.isSelected()+",\"evseKw\":"+n(ev)+",\"highPowerCordKw\":"+n(cord)+",\"voltage\":"+n(v)+",\"phases\":"+ph.getValue()+"}"),r->
      "Classroom portion (50 W/m²): "+d(r,"classroomLoadW")+" W\nRemaining-area portion (10 W/m²): "+d(r,"remainingAreaLoadW")+" W\nBasic connected load: "+d(r,"basicLoadW")+" W\nHeating demand: "+d(r,"heatingDemandW")+" W\nA/C: "+d(r,"airConditioningW")+" W\n\nFINAL DEMAND: "+d(r,"calculatedDemandW")+" W\nSERVICE CURRENT: "+d(r,"serviceCurrentA")+" A\nSuggested standard service: "+i(r,"suggestedStandardServiceA")+" A\n\n"+refs(r));
  }

  private static TitledPane hospital(PortalClient p,String u,String pw){
    GridPane f=form();TextField area=tf("1300"),hi=tf("275"),other=tf("85"),heat=tf("110"),ac=tf("0"),ev=tf("0"),cord=tf("0"),v=tf("600");ComboBox<Integer> ph=phases(3);CheckBox interlock=new CheckBox("Heating and A/C interlocked");interlock.setSelected(true);
    row(f,0,"Total hospital area (m²)",area);row(f,1,"High-intensity area (m²)",hi);row(f,2,"Other permanently connected loads (kW)",other);row(f,3,"Electric space heat connected (kW)",heat);row(f,4,"Air conditioning (kW)",ac);row(f,5,"EVSE (kW)",ev);row(f,6,"High-power cord/receptacle load (kW)",cord);row(f,7,"Voltage",v);row(f,8,"Phases",ph);f.add(interlock,1,9);
    return pane("Hospital — Rule 8-206",f,()->p.hospitalServiceCalculator(u,pw,"{\"totalArea\":"+n(area)+",\"highIntensityArea\":"+n(hi)+",\"otherKw\":"+n(other)+",\"heatKw\":"+n(heat)+",\"acKw\":"+n(ac)+",\"interlocked\":"+interlock.isSelected()+",\"evseKw\":"+n(ev)+",\"highPowerCordKw\":"+n(cord)+",\"voltage\":"+n(v)+",\"phases\":"+ph.getValue()+"}"),r->
      "Base area load (20 W/m²): "+d(r,"basicAreaLoadW")+" W\nHigh-intensity addition (100 W/m²): "+d(r,"highIntensityAdditionalW")+" W\nHeating demand: "+d(r,"heatingDemandW")+" W\nA/C: "+d(r,"airConditioningW")+" W\n\nFINAL DEMAND: "+d(r,"calculatedDemandW")+" W\nSERVICE CURRENT: "+d(r,"serviceCurrentA")+" A\nSuggested standard service: "+i(r,"suggestedStandardServiceA")+" A\n\n"+refs(r));
  }

  private static TitledPane other(PortalClient p,String u,String pw){
    GridPane f=form();TextField name=tf("Hotel / commercial / other"),area=tf("775"),wpm=tf("20"),df=tf("0.80"),special=tf("40"),heat=tf("75"),ac=tf("50"),motor=tf("0"),ev=tf("0"),v=tf("208");ComboBox<Integer> ph=phases(3);CheckBox interlock=new CheckBox("Heating and A/C interlocked");interlock.setSelected(true);
    row(f,0,"Occupancy label",name);row(f,1,"Area (m²)",area);row(f,2,"CEC Table 14 basic load (W/m²)",wpm);row(f,3,"Table 14 demand factor (decimal)",df);row(f,4,"Special / permanently connected load (kW)",special);row(f,5,"Electric heat connected (kW)",heat);row(f,6,"Air conditioning (kW)",ac);row(f,7,"Motor / other special load (kW)",motor);row(f,8,"EVSE (kW)",ev);row(f,9,"Voltage",v);row(f,10,"Phases",ph);f.add(interlock,1,11);
    return pane("Other occupancy — Rule 8-210 / Table 14",f,()->p.otherServiceCalculator(u,pw,"{\"occupancy\":"+JSON.writeValueAsString(name.getText())+",\"area\":"+n(area)+",\"wattsPerM2\":"+n(wpm)+",\"tableDemand\":"+n(df)+",\"specialKw\":"+n(special)+",\"heatKw\":"+n(heat)+",\"acKw\":"+n(ac)+",\"interlocked\":"+interlock.isSelected()+",\"motorKw\":"+n(motor)+",\"evseKw\":"+n(ev)+",\"voltage\":"+n(v)+",\"phases\":"+ph.getValue()+"}"),r->
      "Occupancy: "+t(r,"occupancy")+"\nDemanded area load: "+d(r,"basicTable14LoadW")+" W\nHeating demand: "+d(r,"heatingDemandW")+" W\nA/C: "+d(r,"airConditioningW")+" W\nSpecial/motor/EV loads: "+d(r,"specialAndMotorLoadsW")+" W\n\nFINAL DEMAND: "+d(r,"calculatedDemandW")+" W\nSERVICE CURRENT: "+d(r,"serviceCurrentA")+" A\nSuggested standard service: "+i(r,"suggestedStandardServiceA")+" A\n\n"+refs(r));
  }

  private interface Call{String get() throws Exception;} private interface Format{String apply(JsonNode r) throws Exception;}
  private static TitledPane pane(String title,Node form,Call call,Format fmt){Button go=new Button("Calculate service");go.setStyle("-fx-background-color:#1d4ed8;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;-fx-padding:10 16;");Label status=new Label();status.setWrapText(true);TextArea out=new TextArea();out.setEditable(false);out.setWrapText(true);out.setPrefRowCount(15);go.setOnAction(e->{go.setDisable(true);status.setText("Calculating…");CompletableFuture.supplyAsync(()->{try{return call.get();}catch(Exception ex){throw new RuntimeException(ex);}}).whenComplete((body,err)->Platform.runLater(()->{go.setDisable(false);if(err!=null){Throwable c=err.getCause()==null?err:err.getCause();status.setText("Calculation failed: "+c.getMessage());return;}try{out.setText(fmt.apply(JSON.readTree(body)));status.setText("Calculated using CEC_2024 occupancy-specific backend logic.");}catch(Exception ex){status.setText("Could not read result: "+ex.getMessage());}}));});VBox v=new VBox(10,form,go,status,out);v.setPadding(new Insets(8));return new TitledPane(title,v);}
  private static GridPane form(){GridPane g=new GridPane();g.setHgap(10);g.setVgap(8);g.setPadding(new Insets(10));ColumnConstraints a=new ColumnConstraints();a.setMinWidth(270);ColumnConstraints b=new ColumnConstraints();b.setHgrow(Priority.ALWAYS);g.getColumnConstraints().addAll(a,b);return g;}
  private static void row(GridPane g,int r,String l,Node n){g.add(new Label(l),0,r);g.add(n,1,r);} private static TextField tf(String s){TextField t=new TextField(s);t.setMaxWidth(300);return t;} private static ComboBox<Integer> phases(int d){ComboBox<Integer> c=new ComboBox<>();c.getItems().addAll(1,3);c.setValue(d);return c;} private static double n(TextField f){return Double.parseDouble(f.getText().trim());} private static double d(JsonNode r,String k){return r.path(k).asDouble();} private static int i(JsonNode r,String k){return r.path(k).asInt();} private static String t(JsonNode r,String k){return r.path(k).asText();} private static String refs(JsonNode r){return "Reference: "+t(r,"reference")+"\n\n"+t(r,"note");}
}

package com.example.desktop;

import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

public final class CalculatorHub {
  private CalculatorHub(){}
  public static Node create(PortalClient portal,String username,String password){
    TabPane tabs=new TabPane();
    Tab trade=new Tab("Trade Calculators",CalculatorViews.create(portal,username,password));trade.setClosable(false);
    Tab services=new Tab("Service Calculations",ServiceCalculatorViews.create(portal,username,password));services.setClosable(false);
    tabs.getTabs().addAll(trade,services);
    return tabs;
  }
}

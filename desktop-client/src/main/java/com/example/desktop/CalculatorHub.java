package com.example.desktop;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;

public final class CalculatorHub {
  private CalculatorHub(){}
  public static Node create(PortalClient portal,String username,String password){
    Node tradeContent=CalculatorViews.create(portal,username,password);
    removeOldGenericService(tradeContent);

    TabPane tabs=new TabPane();
    Tab trade=new Tab("Trade Calculators",tradeContent);trade.setClosable(false);
    Tab services=new Tab("Service Calculations",ServiceCalculatorViews.create(portal,username,password));services.setClosable(false);
    tabs.getTabs().addAll(trade,services);
    return tabs;
  }

  private static void removeOldGenericService(Node node){
    if(node instanceof Accordion a){
      a.getPanes().removeIf(p->p.getText()!=null&&p.getText().toLowerCase().startsWith("service demand"));
    }
    if(node instanceof Parent p){
      for(Node child:p.getChildrenUnmodifiable())removeOldGenericService(child);
    }
  }
}

package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CalculatorViews {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String EDITION = "CEC_2024";
    private CalculatorViews() {}

    public static Node create(PortalClient portal,String username,String password) {
        VBox root=new VBox(14); root.setPadding(new Insets(22));
        Label h=new Label("Electrical Calculators"); h.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#14213d;");
        Label s=new Label("CEC 2024 calculation tools. Lookup data and calculation rules are served from the backend so code editions can be updated centrally."); s.setWrapText(true); s.setStyle("-fx-text-fill:#64748b;");
        Label edition=new Label("Code edition: CEC 2024 · backend dataset starter-2"); edition.setStyle("-fx-font-weight:bold;-fx-text-fill:#1d4ed8;");
        Accordion a=new Accordion(transformer(portal,username,password),motor(portal,username,password),multiMotor(portal,username,password),taps(portal,username,password),conduitFill(portal,username,password),boxFill(portal,username,password),service(portal,username,password));
        a.setExpandedPane(a.getPanes().get(0));
        root.getChildren().addAll(h,s,new Separator(),edition,a,catalog(portal,username,password));
        return root;
    }

    private static TitledPane transformer(PortalClient p,String u,String pw){
        GridPane f=form(); TextField kva=tf("75"),pv=tf("600"),sv=tf("208"),design=tf("1.25"),pO=tf("1.25"),sO=tf("1.25"); ComboBox<Integer> ph=phases();
        row(f,0,"Transformer kVA",kva); row(f,1,"Primary voltage",pv); row(f,2,"Secondary voltage",sv); row(f,3,"Phases",ph); row(f,4,"Conductor/design multiplier",design); row(f,5,"Primary OCPD multiplier",pO); row(f,6,"Secondary OCPD multiplier",sO);
        return pane("Transformer — current, conductors & OCPD",f,()->p.transformerCalculator(u,pw,"{\"kva\":"+num(kva)+",\"primaryVoltage\":"+num(pv)+",\"secondaryVoltage\":"+num(sv)+",\"phases\":"+ph.getValue()+",\"designFactor\":"+num(design)+",\"primaryOcpdFactor\":"+num(pO)+",\"secondaryOcpdFactor\":"+num(sO)+",\"edition\":\""+EDITION+"\"}"),r->
            "Formula: "+t(r,"formula")+"\nPrimary current: "+d(r,"primaryCurrentA")+" A\nSecondary current: "+d(r,"secondaryCurrentA")+" A\n\nPrimary conductor ampacity: "+d(r,"requiredPrimaryAmpacityA")+" A → "+t(r.path("primaryConductorStarter"),"size")+"\nSecondary conductor ampacity: "+d(r,"requiredSecondaryAmpacityA")+" A → "+t(r.path("secondaryConductorStarter"),"size")+"\n\nPrimary OCPD: "+d(r,"primaryOcpdCalculatedA")+" A calculated → "+i(r,"primaryOcpdStandardA")+" A standard\nSecondary OCPD: "+d(r,"secondaryOcpdCalculatedA")+" A calculated → "+i(r,"secondaryOcpdStandardA")+" A standard\n\nReference: "+t(r,"reference")+"\n\n"+t(r,"note"));
    }

    private static TitledPane motor(PortalClient p,String u,String pw){
        GridPane f=form(); TextField flc=tf("28"),cf=tf("1.25"),of=tf("1.15"),oc=tf("1.75"); row(f,0,"Motor FLC (A)",flc); row(f,1,"Conductor factor",cf); row(f,2,"Overload factor",of); row(f,3,"Branch OCPD factor",oc);
        return pane("Motor — conductor, overload & branch OCPD",f,()->p.motorCalculator(u,pw,"{\"flc\":"+num(flc)+",\"conductorFactor\":"+num(cf)+",\"overloadFactor\":"+num(of)+",\"ocpdFactor\":"+num(oc)+",\"edition\":\""+EDITION+"\"}"),r->
            "Motor FLC: "+d(r,"motorFLC_A")+" A\nRequired conductor ampacity: "+d(r,"requiredConductorAmpacityA")+" A → "+t(r.path("starterConductor"),"size")+"\nOverload setting: "+d(r,"overloadSettingA")+" A\nBranch OCPD: "+d(r,"ocpdCalculatedA")+" A calculated → "+i(r,"ocpdStandardA")+" A standard\n\nReference: "+t(r,"reference")+"\n\n"+t(r,"note"));
    }

    private static TitledPane multiMotor(PortalClient p,String u,String pw){
        GridPane f=form(); TextField flcs=tf("28, 14, 9.6"),cf=tf("1.25"),oc=tf("1.75"); row(f,0,"Motor FLCs, comma-separated",flcs); row(f,1,"Largest-motor conductor factor",cf); row(f,2,"Largest branch OCPD factor",oc);
        return pane("Multiple motors — feeder conductor & OCPD",f,()->{List<Double> vals=parseList(flcs.getText());return p.multiMotorCalculator(u,pw,"{\"flcs\":"+JSON.writeValueAsString(vals)+",\"largestConductorFactor\":"+num(cf)+",\"largestBranchOcpdFactor\":"+num(oc)+",\"edition\":\""+EDITION+"\"}");},r->
            "Motors: "+i(r,"motorCount")+"\nLargest FLC: "+d(r,"largestMotorFLC_A")+" A\nOther motors total: "+d(r,"otherMotorsFLC_A")+" A\nFeeder ampacity: "+d(r,"feederAmpacityA")+" A → "+t(r.path("starterFeederConductor"),"size")+"\nFeeder OCPD: "+d(r,"feederOcpdCalculatedA")+" A calculated → "+i(r,"feederOcpdStandardA")+" A standard\n\nReference: "+t(r,"reference")+"\n\n"+t(r,"note"));
    }

    private static TitledPane taps(PortalClient p,String u,String pw){
        GridPane f=form(); TextField source=tf("225"),tap=tf("75"),ratio=tf("0.333333"),len=tf("3"); row(f,0,"Source OCPD (A)",source); row(f,1,"Tap conductor ampacity (A)",tap); row(f,2,"Minimum ratio (1/3 = 0.333333)",ratio); row(f,3,"Tap length (m)",len);
        return pane("Taps & splitters — ratio check",f,()->p.tapCalculator(u,pw,"{\"sourceOcpd\":"+num(source)+",\"tapAmpacity\":"+num(tap)+",\"minimumRatio\":"+num(ratio)+",\"lengthM\":"+num(len)+",\"edition\":\""+EDITION+"\"}"),r->
            "Minimum required tap ampacity: "+d(r,"minimumTapAmpacityA")+" A\nEntered tap ampacity: "+d(r,"tapAmpacityA")+" A\nRatio check: "+(r.path("passesRatio").asBoolean()?"PASS":"FAIL")+"\nLength: "+d(r,"lengthM")+" m\n\nReference: "+t(r,"reference")+"\n\n"+t(r,"note"));
    }

    private static TitledPane conduitFill(PortalClient p,String u,String pw){
        GridPane f=form(); TextField ra=tf("1000"),ca=tf("35"),count=tf("10"),pct=tf("40"); row(f,0,"Raceway internal area",ra); row(f,1,"Area per conductor",ca); row(f,2,"Number of conductors",count); row(f,3,"Maximum fill (%)",pct);
        return pane("Conduit fill",f,()->p.conduitFillCalculator(u,pw,"{\"racewayArea\":"+num(ra)+",\"conductorArea\":"+num(ca)+",\"count\":"+(int)num(count)+",\"maxFillPercent\":"+num(pct)+",\"edition\":\""+EDITION+"\"}"),r->
            "Used area: "+d(r,"usedArea")+"\nAllowed area: "+d(r,"allowedArea")+"\nActual fill: "+d(r,"actualFillPercent")+"%\nMaximum conductors at entered fill: "+i(r,"maximumCount")+"\nResult: "+(r.path("passes").asBoolean()?"PASS":"FAIL")+"\n\nReference: "+t(r,"reference"));
    }

    private static TitledPane boxFill(PortalClient p,String u,String pw){
        GridPane f=form(); TextField bv=tf("245"),allow=tf("24.6"),eq=tf("8"); row(f,0,"Box usable volume",bv); row(f,1,"Allowance per conductor-equivalent",allow); row(f,2,"Conductor equivalents",eq);
        return pane("Box fill",f,()->p.boxFillCalculator(u,pw,"{\"boxVolume\":"+num(bv)+",\"conductorAllowance\":"+num(allow)+",\"conductorEquivalents\":"+(int)num(eq)+",\"edition\":\""+EDITION+"\"}"),r->
            "Required volume: "+d(r,"requiredVolume")+"\nRemaining volume: "+d(r,"remainingVolume")+"\nResult: "+(r.path("passes").asBoolean()?"PASS":"FAIL")+"\n\nReference: "+t(r,"reference"));
    }

    private static TitledPane service(PortalClient p,String u,String pw){
        GridPane f=form(); TextField base=tf("12000"),bd=tf("1.0"),add=tf("18000"),ad=tf("0.75"),v=tf("240"); ComboBox<Integer> ph=phases(); ph.setValue(1); row(f,0,"Base load (VA)",base); row(f,1,"Base demand factor",bd); row(f,2,"Additional load (VA)",add); row(f,3,"Additional demand factor",ad); row(f,4,"Service voltage",v); row(f,5,"Phases",ph);
        return pane("Service demand — general engine",f,()->p.serviceCalculator(u,pw,"{\"baseLoadVA\":"+num(base)+",\"baseDemand\":"+num(bd)+",\"additionalLoadVA\":"+num(add)+",\"additionalDemand\":"+num(ad)+",\"voltage\":"+num(v)+",\"phases\":"+ph.getValue()+",\"edition\":\""+EDITION+"\"}"),r->
            "Calculated demand: "+d(r,"calculatedDemandVA")+" VA\nService current: "+d(r,"serviceCurrentA")+" A\nStarter conductor: "+t(r.path("starterConductor"),"size")+"\n\nReference: "+t(r,"reference")+"\n\n"+t(r,"note"));
    }

    private static Node catalog(PortalClient p,String u,String pw){VBox b=new VBox(6);Label h=new Label("Subscription calculator catalog");h.setStyle("-fx-font-size:16px;-fx-font-weight:bold;");b.getChildren().add(h);CompletableFuture.supplyAsync(()->{try{return p.calculatorCatalog(u,pw);}catch(Exception e){return null;}}).thenAccept(body->Platform.runLater(()->{if(body==null){b.getChildren().add(new Label("Could not load catalog."));return;}try{for(JsonNode c:JSON.readTree(body).path("calculators")){boolean ok=c.path("unlocked").asBoolean();Label l=new Label((ok?"✓  ":"🔒  ")+t(c,"name")+" — "+t(c,"description")+" ["+t(c,"minimumTier")+"]");l.setWrapText(true);l.setStyle(ok?"-fx-text-fill:#166534;":"-fx-text-fill:#64748b;");b.getChildren().add(l);}}catch(Exception e){b.getChildren().add(new Label("Could not parse catalog."));}}));return b;}

    private interface Formatter{String format(JsonNode r) throws Exception;}
    private interface ThrowingSupplier{String get() throws Exception;}
    private static TitledPane pane(String title,Node form,ThrowingSupplier call,Formatter fmt){Button go=new Button("Calculate");go.setStyle("-fx-background-color:#1d4ed8;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;-fx-padding:10 16;");Label status=new Label();status.setWrapText(true);TextArea out=new TextArea();out.setEditable(false);out.setWrapText(true);out.setPrefRowCount(12);go.setOnAction(e->{go.setDisable(true);status.setText("Calculating…");CompletableFuture.supplyAsync(()->{try{return call.get();}catch(Exception ex){throw new RuntimeException(ex);}}).whenComplete((body,err)->Platform.runLater(()->{go.setDisable(false);if(err!=null){Throwable c=err.getCause()==null?err:err.getCause();status.setText("Calculation failed: "+c.getMessage());return;}try{JsonNode r=JSON.readTree(body);out.setText(fmt.format(r));status.setText("Calculated using backend "+EDITION+" dataset.");}catch(Exception ex){status.setText("Could not read result: "+ex.getMessage());}}));});VBox content=new VBox(10,form,go,status,out);content.setPadding(new Insets(8));TitledPane tp=new TitledPane(title,content);tp.setExpanded(false);return tp;}
    private static GridPane form(){GridPane g=new GridPane();g.setHgap(10);g.setVgap(9);g.setPadding(new Insets(10));ColumnConstraints a=new ColumnConstraints();a.setMinWidth(220);ColumnConstraints b=new ColumnConstraints();b.setHgrow(Priority.ALWAYS);g.getColumnConstraints().addAll(a,b);return g;}
    private static void row(GridPane g,int r,String label,Node input){g.add(new Label(label),0,r);g.add(input,1,r);}
    private static TextField tf(String v){TextField t=new TextField(v);t.setMaxWidth(260);return t;}
    private static ComboBox<Integer> phases(){ComboBox<Integer> c=new ComboBox<>();c.getItems().addAll(1,3);c.setValue(3);return c;}
    private static double num(TextField f){return Double.parseDouble(f.getText().trim());}
    private static List<Double> parseList(String s){List<Double> a=new ArrayList<>();for(String p:s.split(","))a.add(Double.parseDouble(p.trim()));return a;}
    private static String t(JsonNode n,String k){return n.path(k).asText();}
    private static double d(JsonNode n,String k){return n.path(k).asDouble();}
    private static int i(JsonNode n,String k){return n.path(k).asInt();}
}

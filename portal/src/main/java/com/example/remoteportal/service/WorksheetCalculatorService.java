package com.example.remoteportal.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class WorksheetCalculatorService {
  private static final int[] STD={15,20,25,30,35,40,45,50,60,70,80,90,100,110,125,150,175,200,225,250,300,350,400,450,500,600,700,800,1000,1200,1600,2000,2500,3000,4000,5000,6000};

  public Map<String,Object> dryTransformer(double kva,double pv,double sv,int phases){
    positive(kva,"kVA");positive(pv,"Primary voltage");positive(sv,"Secondary voltage");phase(phases);
    double ip=current(kva*1000,pv,phases), is=current(kva*1000,sv,phases);
    double p125=ip*1.25;int pMax=nextUp(p125),pAlt=nextDown(p125);
    double pCondMax=pMax,pCondAlt=p125;
    double sCondMax=pMax*(pv/sv),sCondAlt=is*1.25;
    double s125=is*1.25;int sOcpd=nextDown(s125);
    LinkedHashMap<String,Object> r=new LinkedHashMap<>();
    r.put("edition","CEC_2024");r.put("calculator","DRY_TYPE_TRANSFORMER_WORKSHEET");
    r.put("primaryCurrentA",round(ip));r.put("primary125A",round(p125));r.put("primaryMaximumCommonOcpdA",pMax);r.put("primaryAlternateCommonOcpdA",pAlt);
    r.put("primaryConductorAmpacityMaxMethodA",round(pCondMax));r.put("primaryConductorAmpacityAlternateMethodA",round(pCondAlt));
    r.put("secondaryCurrentA",round(is));r.put("secondaryConductorAmpacityMaxMethodA",round(sCondMax));r.put("secondaryConductorAmpacityAlternateMethodA",round(sCondAlt));r.put("secondary125A",round(s125));r.put("secondaryCommonOcpdA",sOcpd);
    r.put("ratio",round(pv/sv));
    r.put("reference","Worksheet flow based on CEC 2024 Rules 26-254 and 26-256, with Table 13 common sizes; conductor/raceway/bond table selection still requires the applicable material, temperature and wiring method data.");
    r.put("note","This reproduces the calculation sequence in the uploaded dry-type transformer answer sheets. It reports required ampacities rather than inventing conductor/raceway sizes where the full validated CEC table dataset is not yet loaded.");
    return r;
  }

  public Map<String,Object> hvTransformer(double mva,double pvKv,double svKv,String protection,double protectionFactor,String material,String wiringMethod,String voltageClass){
    positive(mva,"MVA");positive(pvKv,"Primary kV");positive(svKv,"Secondary kV");factor(protectionFactor,"Protection factor");
    double ip=mva*1_000_000/(pvKv*1000*Math.sqrt(3));double is=mva*1_000_000/(svKv*1000*Math.sqrt(3));
    double pProtCalc=ip*protectionFactor;int pProt="FUSE".equalsIgnoreCase(protection)?nextUp(pProtCalc):nextDown(pProtCalc);
    double p125=ip*1.25,pRequired=Math.max(p125,pProt);
    double s125=is*1.25,sRatio=pProt*(pvKv/svKv),sRequired=Math.max(s125,sRatio);
    String table=tableHint(voltageClass,material,wiringMethod);
    LinkedHashMap<String,Object> r=new LinkedHashMap<>();r.put("edition","CEC_2024");r.put("calculator","HV_TRANSFORMER_WORKSHEET");
    r.put("primaryCurrentA",round(ip));r.put("primaryProtectionCalculatedA",round(pProtCalc));r.put("primaryProtectionCommonA",pProt);r.put("primary125AmpacityA",round(p125));r.put("primaryRequiredAmpacityAfterOcpdCheckA",round(pRequired));
    r.put("secondaryCurrentA",round(is));r.put("secondary125AmpacityA",round(s125));r.put("secondaryOcpdRatioMinimumA",round(sRatio));r.put("secondaryRequiredAmpacityA",round(sRequired));
    r.put("voltageRatio",round(pvKv/svKv));r.put("tableFamilyHint",table);r.put("material",material);r.put("wiringMethod",wiringMethod);
    r.put("reference","Worksheet flow based on CEC 2024 Rules 26-250 and 26-256, including the 26-256(4) OCPD-derived conductor minimum, plus the applicable D17 cable ampacity table.");
    r.put("note","The uploaded HV worksheet uses 300% circuit-breaker protection for the 44/27.6 kV example and 150% fuse protection for the 13.8/7.2 kV example. The protection factor remains visible because the applicable rule/protection arrangement must be confirmed. D17 table sizing is not automatically selected beyond the worksheet-derived table-family hint until that dataset is validated.");return r;
  }

  public Map<String,Object> capacitorKvar(double kvar,double voltage,int phases){
    positive(kvar,"kVAR");positive(voltage,"Voltage");phase(phases);
    double fla=phases==3?kvar*1000/(voltage*Math.sqrt(3)):kvar*1000/voltage;
    double conductor=fla*1.35,ocpdCalc=fla*2.50,disc=fla*1.35;int ocpd=nextDown(ocpdCalc),disconnect=nextUp(disc);
    LinkedHashMap<String,Object> r=new LinkedHashMap<>();r.put("edition","CEC_2024");r.put("calculator","CAPACITOR_WORKSHEET");r.put("flaA",round(fla));r.put("conductorAmpacityA",round(conductor));r.put("ocpdCalculatedA",round(ocpdCalc));r.put("maximumCommonOcpdA",ocpd);r.put("disconnectCalculatedA",round(disc));r.put("minimumCommonNonFusedDisconnectA",disconnect);
    r.put("reference","Uploaded worksheet flow: CEC 2024 Rules 26-208(1), 26-210 and 26-214; common OCPD sizes use Table 13.");r.put("note","If the disconnect is fusible, its physical rating must accommodate the selected fuses. The uploaded 115 kVAR example notes that 300 A fuses would require a larger fusible disconnect body.");return r;
  }

  private static String tableHint(String voltageClass,String material,String wiring){String v=voltageClass==null?"":voltageClass.toUpperCase();String m=material==null?"":material.toUpperCase();String w=wiring==null?"":wiring.toUpperCase();if(v.contains("25-46")&&m.contains("COPPER")&&w.contains("BURIED"))return "Table D17G (worksheet example: 25–46 kV copper in buried conduit)";if(v.contains("5-15")&&m.contains("ALUMINUM")&&w.contains("OUTDOOR"))return "Table D17N (worksheet example: 5–15 kV aluminum outdoors on ventilated tray)";return "Select the applicable CEC D17 table for voltage class, conductor material and installation method";}
  private static double current(double va,double v,int phases){return phases==3?va/(v*Math.sqrt(3)):va/v;}
  private static int nextUp(double a){for(int x:STD)if(x>=a)return x;return(int)Math.ceil(a);}
  private static int nextDown(double a){int last=STD[0];for(int x:STD){if(x>a)break;last=x;}return last;}
  private static void positive(double v,String n){if(v<=0)throw new IllegalArgumentException(n+" must be greater than zero");}
  private static void phase(int p){if(p!=1&&p!=3)throw new IllegalArgumentException("Phases must be 1 or 3");}
  private static void factor(double v,String n){if(v<=0||v>5)throw new IllegalArgumentException(n+" must be greater than zero and no more than 5.0");}
  private static double round(double v){return Math.round(v*100.0)/100.0;}
}

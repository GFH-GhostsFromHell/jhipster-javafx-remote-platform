package com.example.desktop;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Guarded offline industrial arithmetic. Never a protection or Code approval. */
final class IndustrialFieldMath {
    private IndustrialFieldMath() {}
    record Field(String id,String label,String example) {}
    record Model(String id,String title,List<Field> fields) {
        @Override public String toString(){return title;}
    }
    record Result(Map<String,Double> values,String caution,boolean codeComplianceVerified) {}

    static final List<Model> MODELS=List.of(
        new Model("ct","CT current transformation",List.of(
            new Field("primaryRatedA","CT rated primary A","400"),
            new Field("secondaryRatedA","CT rated secondary A","5"),
            new Field("actualPrimaryA","Actual primary current A","200"))),
        new Model("vt","VT / PT voltage transformation",List.of(
            new Field("primaryRatedV","VT rated primary V","4160"),
            new Field("secondaryRatedV","VT rated secondary V","120"),
            new Field("actualPrimaryV","Measured primary voltage V","4000"))),
        new Model("power","Balanced three-phase power",List.of(
            new Field("lineVoltageV","Line-to-line voltage V","600"),
            new Field("lineCurrentA","Line current A","100"),
            new Field("powerFactor","Power factor 0–1","0.85"))),
        new Model("pickup","CT relay pickup conversion",List.of(
            new Field("primaryRatedA","CT rated primary A","400"),
            new Field("secondaryRatedA","CT rated secondary A","5"),
            new Field("primaryPickupA","Previously specified primary pickup A","240"))),
        new Model("fault","Ideal transformer short-circuit estimate",List.of(
            new Field("transformerKva","Transformer kVA","75"),
            new Field("secondaryVoltageV","Secondary line voltage V","600"),
            new Field("impedancePercent","Nameplate impedance percent","5"))),
        new Model("analog","4–20 mA analog scaling",List.of(
            new Field("engineeringMin","4mA engineering endpoint","0"),
            new Field("engineeringMax","20mA engineering endpoint","1800"),
            new Field("engineeringValue","Requested engineering value","1200")))
    );

    static Result calculate(String mode,Map<String,Double> entries) {
        if(entries==null)throw new IllegalArgumentException("Enter measurements.");
        switch(mode){
            case "ct" -> {
                double p=pos(entries,"primaryRatedA"),s=pos(entries,"secondaryRatedA");
                double actual=pos(entries,"actualPrimaryA");
                return result("Ideal CT ratio. Verify secondary wiring, CT polarity, saturation and burden.",
                    "CT ratio",p/s,"Secondary current A",actual*s/p,
                    "Reconstructed primary A",(actual*s/p)*p/s);
            }
            case "vt" -> {
                double p=pos(entries,"primaryRatedV"),s=pos(entries,"secondaryRatedV");
                double actual=pos(entries,"actualPrimaryV");
                return result("Ideal VT ratio only. Verify grounding, fuses, burden and nameplates.",
                    "VT ratio",p/s,"Secondary voltage V",actual*s/p);
            }
            case "power" -> {
                double v=pos(entries,"lineVoltageV"),i=pos(entries,"lineCurrentA");
                double pf=pos(entries,"powerFactor");
                if(pf>1)throw new IllegalArgumentException("Power factor cannot exceed one.");
                double kva=Math.sqrt(3.0)*v*i/1000.0;
                return result("Balanced sinusoidal three-phase estimate. Harmonics and unbalance are not included.",
                    "Apparent power kVA",kva,"Real power kW",kva*pf);
            }
            case "pickup" -> {
                double p=pos(entries,"primaryRatedA"),s=pos(entries,"secondaryRatedA");
                double pickup=pos(entries,"primaryPickupA");
                return result("Already specified pickup conversion ONLY. NOT an approved relay setting. Verify a complete protection coordination study.",
                    "CT ratio",p/s,"Secondary pickup A",pickup*s/p);
            }
            case "fault" -> {
                double kva=pos(entries,"transformerKva"),v=pos(entries,"secondaryVoltageV");
                double z=pos(entries,"impedancePercent");
                if(z>=100)throw new IllegalArgumentException("Impedance percent must be below 100.");
                double fla=kva*1000.0/(Math.sqrt(3.0)*v);
                return result("IDEAL infinite source and transformer Z only. NOT actual available fault current, coordination or arc-flash result.",
                    "Transformer FLA A",fla,"IDEAL secondary fault A",fla*100.0/z);
            }
            case "analog" -> {
                double min=finite(entries,"engineeringMin"),max=finite(entries,"engineeringMax");
                double set=finite(entries,"engineeringValue");
                if(max<=min||set<min||set>max)
                    throw new IllegalArgumentException("Engineering span must increase and setpoint must be within it.");
                double frac=(set-min)/(max-min);
                return result("Ideal linear 4–20mA translation; verify device range, calibration and fail-safe.",
                    "Analog current mA",4+16*frac,"Span percent",100*frac);
            }
            default -> throw new IllegalArgumentException("Unsupported industrial calculation.");
        }
    }
    private static double finite(Map<String,Double> entries,String key){
        Double v=entries.get(key);
        if(v==null||!Double.isFinite(v))throw new IllegalArgumentException(key+" must be finite.");
        return v;
    }
    private static double pos(Map<String,Double> entries,String key){
        double v=finite(entries,key);
        if(v<=0)throw new IllegalArgumentException(key+" must be positive.");
        return v;
    }
    private static Result result(String caution,Object... vals){
        Map<String,Double> result=new LinkedHashMap<>();
        for(int k=0;k<vals.length;k+=2){
            double v=(double)vals[k+1];
            if(!Double.isFinite(v))throw new IllegalArgumentException("Calculated quantity is outside numeric range.");
            result.put((String)vals[k],v);
        }
        return new Result(Map.copyOf(result),caution,false);
    }
}

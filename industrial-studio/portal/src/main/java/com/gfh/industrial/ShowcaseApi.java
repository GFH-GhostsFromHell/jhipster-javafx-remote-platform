package com.gfh.industrial;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Public, read-only, deterministic DEMO data for the product showroom.
 * This endpoint is deliberately disconnected from database configurations,
 * authentication, OPC UA, Kepware, actual devices, and site data.
 */
@RestController
@RequestMapping("/api/studio")
public final class ShowcaseApi {
    public record Gauge(String id, String label, String unit, double value,
                        double min, double max, String quality, String state) {}
    public record Panel(String id, String name, String description, String icon,
                        List<Gauge> points) {}
    public record Showroom(String mode, String message, boolean readOnly,
                           boolean realPlcConnected, String timestamp,
                           List<Panel> panels) {}

    private static Gauge gauge(String id,String label,String unit,double value,
                               double low,double high,double warnLow,double warnHigh){
        double rounded=Math.round(value*10d)/10d;
        String state=rounded<warnLow || rounded>warnHigh?"SIMULATED_LIMIT":"NORMAL";
        return new Gauge(id,label,unit,rounded,low,high,"SIMULATED",state);
    }

    public static Showroom sampleAt(long millis){
        double t=millis/1000d;
        double wetWell=57+15*Math.sin(t/17)+3*Math.sin(t/7);
        double pumpSpeed=1280+240*Math.sin(t/22+0.6);
        double pumpAmps=32+pumpSpeed/95+4*Math.sin(t/9);
        double discharge=240+pumpSpeed/15+11*Math.sin(t/12);
        double flow=135+pumpSpeed/9+18*Math.sin(t/17+0.8);
        double belt=2.7+0.16*Math.sin(t/16);
        double throughput=480+110*Math.sin(t/21);
        double conveyorAmps=72+throughput/24+6*Math.sin(t/13);
        double bearing=53+3.4*Math.sin(t/41)+1.1*Math.sin(t/8);
        double vibration=1.4+0.6*Math.sin(t/14);
        double voltage=600+2.9*Math.sin(t/39);
        double current=310+40*Math.sin(t/29);
        double power=voltage*current*1.732*0.91/1000;
        double pf=0.92+0.02*Math.sin(t/27);
        double oilTemp=63+3.5*Math.sin(t/46);
        return new Showroom("SIMULATION",
            "Cloud showroom only. Synthetic values, not Kepware or PLC data.",
            true,false,Instant.ofEpochMilli(millis).toString(),List.of(
            new Panel("dewatering","Underground dewatering",
                "Sump pump and wet-well monitoring","droplets",List.of(
                gauge("wetWell","Wet well level","%",wetWell,0,100,25,80),
                gauge("pumpSpeed","Pump speed","RPM",pumpSpeed,0,1800,500,1700),
                gauge("pumpAmps","Motor current","A",pumpAmps,0,100,0,85),
                gauge("discharge","Discharge pressure","kPa",discharge,0,500,50,450),
                gauge("flow","Flow rate","m³/h",flow,0,400,50,375)
            )),
            new Panel("conveyor","Ore conveyor",
                "Material handling and condition monitoring","conveyor",List.of(
                gauge("belt","Belt speed","m/s",belt,0,4,1.5,3.5),
                gauge("throughput","Throughput","t/h",throughput,0,800,100,700),
                gauge("conveyorAmps","Drive current","A",conveyorAmps,0,140,0,130),
                gauge("bearing","Bearing temperature","°C",bearing,0,120,0,85),
                gauge("vibration","Vibration","mm/s",vibration,0,8,0,5)
            )),
            new Panel("substation","600 V substation",
                "Feeder and transformer monitoring","zap",List.of(
                gauge("voltage","Line voltage","V",voltage,0,700,570,630),
                gauge("current","Feeder current","A",current,0,600,0,525),
                gauge("power","Active power","kW",power,0,650,0,600),
                gauge("powerFactor","Power factor","",pf,0,1,0.85,1.01),
                gauge("oilTemp","Transformer oil","°C",oilTemp,0,120,0,95)
            ))
        ));
    }

    @GetMapping("/showcase")
    public Showroom showroom(){
        return sampleAt(System.currentTimeMillis());
    }
}

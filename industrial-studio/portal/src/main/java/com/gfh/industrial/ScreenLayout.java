package com.gfh.industrial;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Declarative, inert screen model. Never allows FXML, HTML, Java, scripts or raw styles. */
public record ScreenLayout(String name, int columns, List<Widget> widgets) {
    public record Widget(String id, String type, String title, String tagId, Double minimum, Double maximum) {}

    public static ScreenLayout example() {
        return new ScreenLayout("Pump station overview",3,List.of(
            new Widget("heading","TEXT","Pump station 1 • read-only simulator",null,null,null),
            new Widget("speed","VALUE","Pump speed","pumpSpeed",null,null),
            new Widget("level","GAUGE","Tank level","tankLevel",0.0,100.0),
            new Widget("current","VALUE","Motor current","motorCurrent",null,null),
            new Widget("status","STATUS","Tank limit status","tankLevel",null,null)
        ));
    }

    public void validate(Set<String> knownTags) {
        if (name == null || name.isBlank() || name.length() > 100) throw new IllegalArgumentException("Screen name must be 1-100 characters");
        if (columns < 1 || columns > 4) throw new IllegalArgumentException("Choose between 1 and 4 screen columns");
        if (widgets == null || widgets.isEmpty() || widgets.size() > 40) throw new IllegalArgumentException("Screen must have 1-40 widgets");
        var ids=new HashSet<String>();
        for (Widget w:widgets) {
            if (w == null || w.id()==null || !w.id().matches("[A-Za-z][A-Za-z0-9_-]{0,47}") || !ids.add(w.id())) {
                throw new IllegalArgumentException("Each widget needs a unique ID");
            }
            if (w.title()==null || w.title().isBlank() || w.title().length()>100) throw new IllegalArgumentException("Widget title must be 1-100 characters");
            if (!Set.of("TEXT","VALUE","GAUGE","STATUS").contains(w.type())) throw new IllegalArgumentException("Unsupported widget type");
            if ("TEXT".equals(w.type())) {
                if (w.tagId()!=null && !w.tagId().isBlank()) throw new IllegalArgumentException("Text widgets cannot bind PLC tags");
            } else if (w.tagId()==null || !knownTags.contains(w.tagId())) {
                throw new IllegalArgumentException("Widget tag is not defined in the OPC UA mapping");
            }
            if ("GAUGE".equals(w.type())) {
                if(w.minimum()==null || w.maximum()==null || !Double.isFinite(w.minimum()) ||
                   !Double.isFinite(w.maximum()) || w.minimum()>=w.maximum()) {
                   throw new IllegalArgumentException("Gauge requires finite min/max with min less than max");
                }
            }
        }
    }
}

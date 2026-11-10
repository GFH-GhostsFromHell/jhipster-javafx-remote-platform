package com.gfh.industrial.hmi;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.Map;

/** Only a fixed, read-only widget vocabulary can be rendered. No executable markup or code. */
public final class HmiScreenRenderer {
    private HmiScreenRenderer() {}

    public static void render(TilePane tiles, JsonNode layout, JsonNode samples) {
        tiles.getChildren().clear();
        int columns = layout.path("columns").asInt(2);
        tiles.setPrefColumns(Math.max(1, Math.min(4, columns)));
        tiles.setPrefTileWidth(240);
        Map<String,JsonNode> readings = new HashMap<>();
        for (JsonNode sample : samples) {
            if (sample.path("id").isTextual() && ("SIMULATED".equals(sample.path("quality").asText()) || "GOOD".equals(sample.path("quality").asText()))) {
                readings.put(sample.path("id").asText(),sample);
            }
        }
        int count=0;
        for (JsonNode widget : layout.path("widgets")) {
            if (++count > 40) break;
            String type=widget.path("type").asText();
            if (!type.equals("TEXT") && !type.equals("VALUE") &&
                !type.equals("GAUGE") && !type.equals("STATUS")) continue;
            VBox card=new VBox(10);
            card.setPrefSize(225,165);
            card.setMinWidth(220);
            card.setPadding(new Insets(14));
            card.setStyle("-fx-background-color:#1e4161; -fx-background-radius:9;");
            Label heading=new Label(widget.path("title").asText("Unnamed"));
            heading.setWrapText(true);
            heading.setStyle("-fx-font-weight:bold");
            card.getChildren().add(heading);
            if(type.equals("TEXT")){
                Label staticText=new Label("Read-only screen label");
                staticText.setWrapText(true);
                card.getChildren().add(staticText);
                tiles.getChildren().add(card);
                continue;
            }

            JsonNode value = readings.get(widget.path("tagId").asText());
            if (value == null) {
                Label missing=new Label("DATA UNAVAILABLE");
                missing.setStyle("-fx-text-fill:#ffadad; -fx-font-weight:bold");
                card.getChildren().add(missing);
                tiles.getChildren().add(card);
                continue;
            }
            String unit=value.path("unit").asText("");
            double number=value.path("value").asDouble(Double.NaN);
            if(!Double.isFinite(number)){
                card.getChildren().add(new Label("INVALID DATA"));
                tiles.getChildren().add(card);
                continue;
            }
            if(type.equals("VALUE")){
                Label reading=new Label(String.format(java.util.Locale.ROOT,"%.1f %s",number,unit));
                reading.setStyle("-fx-font-size:26px; -fx-text-fill:#8edbff; -fx-font-weight:bold");
                card.getChildren().add(reading);
            }else if(type.equals("STATUS")){
                Label state=new Label(value.path("alarm").asBoolean()?
                    "Configured threshold exceeded":"Within configured thresholds");
                state.setWrapText(true);
                state.setStyle(value.path("alarm").asBoolean()?
                    "-fx-text-fill:#ffbe8d; -fx-font-weight:bold":
                    "-fx-text-fill:#b1e6c8; -fx-font-weight:bold");
                card.getChildren().add(state);
            }else{
                double min=widget.path("minimum").asDouble(Double.NaN);
                double max=widget.path("maximum").asDouble(Double.NaN);
                if(!Double.isFinite(min)||!Double.isFinite(max)||max<=min){
                    card.getChildren().add(new Label("INVALID GAUGE RANGE"));
                }else{
                    double progress=Math.max(0,Math.min(1,(number-min)/(max-min)));
                    ProgressBar gauge=new ProgressBar(progress);
                    gauge.setPrefWidth(185);
                    card.getChildren().add(gauge);
                    Label reading=new Label(String.format(java.util.Locale.ROOT,"%.1f %s",number,unit));
                    reading.setStyle("-fx-text-fill:#8edbff");
                    card.getChildren().add(reading);
                }
            }
            Label meta=new Label("Data quality: "+value.path("quality").asText()+
                ("SIMULATED".equals(value.path("quality").asText())?" · DEMO":" · OPC UA"));
            meta.setStyle("-fx-text-fill:#ffd49b");
            card.getChildren().add(meta);
            tiles.getChildren().add(card);
        }
    }
}

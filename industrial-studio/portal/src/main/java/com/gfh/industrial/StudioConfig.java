package com.gfh.industrial;

import java.util.*;

public record StudioConfig(String endpoint, List<Tag> tags, String javaDraft) {
    public record Tag(String id, String label, String nodeId, String unit, Double lowAlarm, Double highAlarm) {}

    public static StudioConfig example() {
        return new StudioConfig("opc.tcp://127.0.0.1:49320",
            List.of(
                new Tag("pumpSpeed", "Pump Speed", "ns=2;s=Channel1.Device1.PumpSpeed", "RPM", 100.0, 1800.0),
                new Tag("tankLevel", "Tank Level", "ns=2;s=Channel1.Device1.TankLevel", "%", 15.0, 90.0),
                new Tag("motorCurrent", "Motor Current", "ns=2;s=Channel1.Device1.MotorCurrent", "A", 0.0, 80.0)
            ),
            "// JAVA DRAFT ONLY — this source is stored, not compiled or executed.\n"
            + "public final class MyTransform {\n"
            + "  public static double scale(double input) { return input; }\n"
            + "}\n");
    }

    public void validate() {
        if (endpoint == null || endpoint.length() > 255 || !endpoint.matches("opc\\.tcp://[A-Za-z0-9_.:-]+(:[0-9]{1,5})(/[^\\s]*)?")) {
            throw new IllegalArgumentException("Enter an opc.tcp://hostname:port endpoint");
        }
        if (tags == null || tags.isEmpty() || tags.size() > 200) {
            throw new IllegalArgumentException("Configure between 1 and 200 tags");
        }
        if (javaDraft == null || javaDraft.length() > 32000) {
            throw new IllegalArgumentException("Java draft must be <= 32,000 characters");
        }
        Set<String> ids = new HashSet<>();
        for (Tag t : tags) {
            if (t == null || t.id() == null || !t.id().matches("[a-zA-Z][a-zA-Z0-9_]{0,49}") || !ids.add(t.id())) {
                throw new IllegalArgumentException("Tag IDs must be unique alphanumeric identifiers");
            }
            if (t.nodeId() == null || t.nodeId().isBlank() || t.nodeId().length() > 300 ||
                    !t.nodeId().matches("ns=[0-9]+;[isgb]=.+")) {
                throw new IllegalArgumentException("Each tag needs an OPC UA NodeId (example: ns=2;s=TagName)");
            }
            if (t.label() == null || t.label().isBlank() || t.label().length() > 100 ||
                    t.unit() == null || t.unit().length() > 30) {
                throw new IllegalArgumentException("Tag labels and units must be short");
            }
            if (t.lowAlarm() != null && !Double.isFinite(t.lowAlarm()) ||
                t.highAlarm() != null && !Double.isFinite(t.highAlarm()) ||
                t.lowAlarm() != null && t.highAlarm() != null && t.lowAlarm() >= t.highAlarm()) {
                throw new IllegalArgumentException("Alarm thresholds must be finite and low < high");
            }
        }
    }
}

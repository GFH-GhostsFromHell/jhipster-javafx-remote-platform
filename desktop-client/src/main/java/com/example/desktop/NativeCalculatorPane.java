package com.example.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.scene.layout.BorderPane;

/**
 * Compatibility wrapper retained so the existing desktop shell can keep using the
 * same calculator entry point while the full CEC-section workspace is restored.
 */
public final class NativeCalculatorPane extends BorderPane {
    public NativeCalculatorPane(PortalClient portal, ObjectMapper json, String username, String password) {
        setCenter(new SectionedCalculatorPane(portal, json, username, password));
    }
}

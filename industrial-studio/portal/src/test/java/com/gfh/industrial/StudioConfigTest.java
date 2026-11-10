package com.gfh.industrial;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StudioConfigTest {
    @Test void demoConfigurationValid() {
        assertDoesNotThrow(() -> StudioConfig.example().validate());
    }
    @Test void rejectsDuplicateIds() {
        StudioConfig example=StudioConfig.example();
        var first=example.tags().get(0);
        var duplicate=new StudioConfig.Tag(first.id(),"Other", "ns=2;s=Duplicate","A",null,null);
        var config=new StudioConfig(example.endpoint(),List.of(first,duplicate),example.javaDraft());
        assertThrows(IllegalArgumentException.class,config::validate);
    }
    @Test void rejectsInvalidThresholdOrder() {
        var t=new StudioConfig.Tag("pressure","Pressure","ns=2;s=Pressure","kPa",150.0,75.0);
        var config=new StudioConfig("opc.tcp://localhost:49320",List.of(t),"// draft");
        assertThrows(IllegalArgumentException.class,config::validate);
    }
    @Test void rejectsMalformedEndpoint() {
        var example=StudioConfig.example();
        assertThrows(IllegalArgumentException.class,
            ()->new StudioConfig("https://public.example:49320",example.tags(),example.javaDraft()).validate());
    }
}
package com.gfh.industrial;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ScreenLayoutTest {
    @Test void exampleIsValid() {
        assertDoesNotThrow(() -> ScreenLayout.example().validate(Set.of("pumpSpeed","tankLevel","motorCurrent")));
    }
    @Test void unknownTagIsRejected() {
        var example=ScreenLayout.example();
        assertThrows(IllegalArgumentException.class,()->example.validate(Set.of("pumpSpeed")));
    }
    @Test void codeInjectionIsNotAWidgetType() {
        var malicious=new ScreenLayout("Test",1,List.of(new ScreenLayout.Widget(
            "x1","JAVA","Runtime.getRuntime()",null,null,null)));
        assertThrows(IllegalArgumentException.class,()->malicious.validate(Set.of("pumpSpeed")));
    }
    @Test void nullTypeRejected() {
        var malformed=new ScreenLayout("Test",1,List.of(new ScreenLayout.Widget(
            "x1",null,"Broken",null,null,null)));
        assertThrows(IllegalArgumentException.class,()->malformed.validate(Set.of()));
    }
    @Test void invalidGaugeRangeRejected() {
        var malformed=new ScreenLayout("Test",1,List.of(new ScreenLayout.Widget(
            "x1","GAUGE","Tank","tankLevel",100.0,0.0)));
        assertThrows(IllegalArgumentException.class,()->malformed.validate(Set.of("tankLevel")));
    }
    @Test void excessiveLayoutSizeRejected() {
        var widgets=java.util.stream.IntStream.range(0,41)
            .mapToObj(i->new ScreenLayout.Widget("wid"+i,"TEXT","Text",null,null,null)).toList();
        assertThrows(IllegalArgumentException.class,
            ()->new ScreenLayout("Test",2,widgets).validate(Set.of()));
    }
}

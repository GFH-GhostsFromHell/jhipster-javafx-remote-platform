package com.example.desktop;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class IndustrialFieldMathTest {
    @Test void ctVtAndThreePhaseWorkFromMeasuredRatings() {
        var ct=IndustrialFieldMath.calculate("ct",Map.of(
            "primaryRatedA",400.0,"secondaryRatedA",5.0,"actualPrimaryA",200.0));
        assertEquals(80,ct.values().get("CT ratio"),1e-9);
        assertEquals(2.5,ct.values().get("Secondary current A"),1e-9);
        assertFalse(ct.codeComplianceVerified());
        var vt=IndustrialFieldMath.calculate("vt",Map.of(
            "primaryRatedV",4160.0,"secondaryRatedV",120.0,"actualPrimaryV",4000.0));
        assertEquals(115.384615,vt.values().get("Secondary voltage V"),0.0001);
        var power=IndustrialFieldMath.calculate("power",Map.of(
            "lineVoltageV",600.0,"lineCurrentA",100.0,"powerFactor",.85));
        assertEquals(103.923048,power.values().get("Apparent power kVA"),0.0001);
        assertEquals(88.33459,power.values().get("Real power kW"),0.0001);
    }
    @Test void relayPickupIsConversionNotRecommendation() {
        var r=IndustrialFieldMath.calculate("pickup",Map.of(
            "primaryRatedA",400.0,"secondaryRatedA",5.0,"primaryPickupA",240.0));
        assertEquals(3,r.values().get("Secondary pickup A"),1e-9);
        assertTrue(r.caution().contains("NOT an approved relay setting"));
        assertFalse(r.codeComplianceVerified());
    }
    @Test void transformerFaultClearlyAssumesInfiniteSource() {
        var r=IndustrialFieldMath.calculate("fault",Map.of(
            "transformerKva",75.0,"secondaryVoltageV",600.0,"impedancePercent",5.0));
        assertEquals(72.16878365,r.values().get("Transformer FLA A"),0.0001);
        assertEquals(1443.37567,r.values().get("IDEAL secondary fault A"),0.0001);
        assertTrue(r.caution().contains("NOT actual available fault current"));
    }
    @Test void instrumentFourToTwentyUsesLiveZero() {
        var r=IndustrialFieldMath.calculate("analog",Map.of(
            "engineeringMin",0.0,"engineeringMax",1800.0,"engineeringValue",1200.0));
        assertEquals(14.6666667,r.values().get("Analog current mA"),0.0001);
    }
    @Test void invalidInputsFailRatherThanInventASetting() {
        assertThrows(IllegalArgumentException.class,()->IndustrialFieldMath.calculate("ct",Map.of(
            "primaryRatedA",400.0,"secondaryRatedA",0.0,"actualPrimaryA",200.0)));
        assertThrows(IllegalArgumentException.class,()->IndustrialFieldMath.calculate("power",Map.of(
            "lineVoltageV",600.0,"lineCurrentA",100.0,"powerFactor",1.1)));
        assertThrows(IllegalArgumentException.class,()->IndustrialFieldMath.calculate("fault",Map.of(
            "transformerKva",75.0,"secondaryVoltageV",600.0,"impedancePercent",100.0)));
        assertThrows(IllegalArgumentException.class,()->IndustrialFieldMath.calculate("analog",Map.of(
            "engineeringMin",0.0,"engineeringMax",1800.0,"engineeringValue",1900.0)));
        assertThrows(IllegalArgumentException.class,()->IndustrialFieldMath.calculate("analog",Map.of(
            "engineeringMin",0.0,"engineeringMax",Double.NaN,"engineeringValue",1.0)));
        assertThrows(IllegalArgumentException.class,()->IndustrialFieldMath.calculate("unknown",Map.of()));
        assertEquals(6,IndustrialFieldMath.MODELS.size());
        assertTrue(IndustrialFieldMath.MODELS.stream().allMatch(m->m.fields().size()==3));
    }
}

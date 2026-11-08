package com.example.desktop;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Offline calculator regression examples. Protects previously shipped numbers
 * from silently changing. It is not a certificate that the licensed CEC 2024
 * Table 5A / 5C data have been validated or that installation complies.
 */
class ConductorCorrectionFactorsTest {
    @Test void atThirtyCelsiusAllSupportedTerminationsHaveOneFactor() {
        for (int t : new int[]{60,75,90})
            assertEquals(1.0,ConductorCorrectionFactors.ambientFactor(30,t),1e-10);
    }

    @Test void exposedFiftyDegreeWorkedRowsStayConsistent() {
        assertEquals(.58,ConductorCorrectionFactors.ambientFactor(50,60),1e-10);
        assertEquals(.75,ConductorCorrectionFactors.ambientFactor(50,75),1e-10);
        assertEquals(.82,ConductorCorrectionFactors.ambientFactor(50,90),1e-10);
    }

    @Test void groupsHaveCorrectBoundariesIncludingZeroLoads() {
        assertEquals(1.0,ConductorCorrectionFactors.groupingFactor(0),1e-10);
        for (int c : new int[]{1,3})assertEquals(1.0,ConductorCorrectionFactors.groupingFactor(c),1e-10);
        for (int c : new int[]{4,6})assertEquals(.8,ConductorCorrectionFactors.groupingFactor(c),1e-10);
        for (int c : new int[]{7,24})assertEquals(.7,ConductorCorrectionFactors.groupingFactor(c),1e-10);
        for (int c : new int[]{25,42})assertEquals(.6,ConductorCorrectionFactors.groupingFactor(c),1e-10);
        for (int c : new int[]{43,100})assertEquals(.5,ConductorCorrectionFactors.groupingFactor(c),1e-10);
    }

    @Test void relevantTerminationTemperatureIsUsedForCorrection() {
        // Same 90°C insulation, different terminal-temperature columns:
        // 55A x .58 x .80 = 25.52 A on 60°C terminals at 50°C ambient.
        assertEquals(25.52,
            ConductorCorrectionFactors.correctedAmpacity(55,60,90,50,6),1e-9);
        // Only change verified termination rating; result must change.
        assertEquals(33.0,
            ConductorCorrectionFactors.correctedAmpacity(55,75,90,50,6),1e-9);
    }

    @Test void rejectsIllogicalTemperaturesOrNoApplicableFactor() {
        assertThrows(IllegalArgumentException.class,
            ()->ConductorCorrectionFactors.correctedAmpacity(55,75,60,50,6));
        assertThrows(IllegalArgumentException.class,
            ()->ConductorCorrectionFactors.correctedAmpacity(55,60,90,60,6));
        assertThrows(IllegalArgumentException.class,
            ()->ConductorCorrectionFactors.ambientFactor(40,100));
        assertThrows(IllegalArgumentException.class,
            ()->ConductorCorrectionFactors.ambientFactor(42,90));
        assertThrows(IllegalArgumentException.class,
            ()->ConductorCorrectionFactors.ambientFactor(75,75));
    }

    @Test void rejectsNonFiniteBaseAndInvalidCarryingCount() {
        for(double x:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,
                ()->ConductorCorrectionFactors.correctedAmpacity(x,75,90,40,6));
        assertThrows(IllegalArgumentException.class,
            ()->ConductorCorrectionFactors.groupingFactor(-1));
        assertEquals(Double.MAX_VALUE,
            ConductorCorrectionFactors.correctedAmpacity(Double.MAX_VALUE,90,90,30,1),1e295);
    }
}

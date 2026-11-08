package com.example.remoteportal.service;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Independently worked arithmetic / conservative-selection regression cases.
 * These are NOT a substitute for verifying CEC 2024 equipment-specific
 * protection options and Ontario amendments before field installation.
 */
class ElectricalCalculatorServiceTest {
    private final ElectricalCalculatorService sut = new ElectricalCalculatorService();
    private static final String EDITION = ElectricalCalculatorService.DEFAULT_EDITION;

    private static double number(Map<String,Object> result, String key) {
        return ((Number)result.get(key)).doubleValue();
    }

    @Test void threePhaseTransformerUsesLineVoltageAndRootThree() {
        // 75,000 VA, 600 -> 208 V, 3 phase:
        // Ip=75,000/(sqrt(3)*600)=72.17 A; Is=208.18 A.
        Map<String,Object> r = sut.transformer(75,600,208,3,1.25,1.25,1.25,EDITION);
        assertEquals(72.17, number(r,"primaryCurrentA"),.01);
        assertEquals(208.18,number(r,"secondaryCurrentA"),.01);
        assertEquals(90.21,number(r,"requiredPrimaryAmpacityA"),.02);
        assertEquals(260.23,number(r,"requiredSecondaryAmpacityA"),.03);
        // Do not assume a round-up exception for BOTH sides.
        assertEquals(90,r.get("primaryOcpdStandardA"));
        assertEquals(250,r.get("secondaryOcpdStandardA"));
        assertEquals(100,r.get("primaryNextStandardCandidateA"));
        assertEquals(false,r.get("nextStandardExceptionVerified"));
        assertEquals(true,r.get("codeReviewRequired"));
    }

    @Test void singlePhaseTransformerUsesOneTimesLineVoltage() {
        // 10 kVA, 240/120 single phase -> 41.67 A / 83.33 A.
        Map<String,Object> r = sut.transformer(10,240,120,1,1.25,1.25,1.25,EDITION);
        assertEquals(41.67,number(r,"primaryCurrentA"),.01);
        assertEquals(83.33,number(r,"secondaryCurrentA"),.01);
        assertEquals(50,r.get("primaryOcpdStandardA"));
        assertEquals(100,r.get("secondaryOcpdStandardA"));
        assertEquals(60,r.get("primaryNextStandardCandidateA"));
    }

    @Test void transformerDoesNotInventFifteenAmpProtectionWhenComputedLimitIsSmaller() {
        Map<String,Object> r = sut.transformer(1,600,120,1,1.25,1.25,1.25,EDITION);
        assertNull(r.get("primaryOcpdStandardA"));
        assertNull(r.get("secondaryOcpdStandardA"));
        assertEquals(true,r.get("primaryNoListedStandardWithinLimit"));
        assertEquals(true,r.get("secondaryNoListedStandardWithinLimit"));
        assertEquals(15,r.get("primaryNextStandardCandidateA"));
    }

    @Test void exactStandardTransformerLimitDoesNotPretendToNeedNextSizeException() {
        // Primary I = 80 A; with protection factor 1.25, limit = 100 A exactly.
        Map<String,Object> r = sut.transformer(80,1000,100,1,1.25,1.25,1.25,EDITION);
        assertEquals(100,r.get("primaryOcpdStandardA"));
        assertNull(r.get("primaryNextStandardCandidateA"));
    }

    @Test void motorNormalBranchSelectionDoesNotRoundAboveCalculatedLimit() {
        // 34A × 175% = 59.5A => 50A maximum listed standard <= limit.
        Map<String,Object> r=sut.motor(34,1.25,1.25,1.75,1.15,"TIME_DELAY_FUSE",false,EDITION);
        assertEquals(42.5,number(r,"requiredConductorAmpacityA"),.001);
        assertEquals(42.5,number(r,"overloadSettingA"),.001);
        assertEquals(39.1,number(r,"disconnectMinimumA"),.001);
        assertEquals(59.5,number(r,"branchOcpdCalculatedA"),.001);
        assertEquals(50,r.get("branchOcpdNormalStandardA"));
        assertEquals(50,r.get("branchOcpdStandardA"));
        assertEquals(false,r.get("startingReliefApplied"));
        assertEquals(false,r.get("minimum15AExceptionApplied"));
    }

    @Test void conditionalStartingReliefIsOnlyOneNextSizeAndWithinItsCeiling() {
        Map<String,Object> r=sut.motor(34,1.25,1.25,1.75,1.15,"TIME_DELAY_FUSE",true,EDITION);
        assertEquals(50,r.get("branchOcpdNormalStandardA"));
        assertEquals(60,r.get("branchOcpdNextStandardCandidateA"));
        assertEquals(76.5,number(r,"startingReliefCeilingA"),.001);
        assertEquals(true,r.get("startingReliefApplied"));
        assertEquals(60,r.get("branchOcpdStandardA"));
    }

    @Test void motorFifteenAmpMinimumIsExplicitException() {
        // Rule 28-200(3)(a) explicitly allows a 15 A minimum even when the
        // entered Table 29 percentage gives a limit below 15 A.
        Map<String,Object> r=sut.motor(5,1.25,1.15,1.75,1.15,"TIME_DELAY_FUSE",false,EDITION);
        assertEquals(8.75,number(r,"branchOcpdCalculatedA"),.001);
        assertEquals(15,r.get("branchOcpdStandardA"));
        assertEquals(true,r.get("minimum15AExceptionApplied"));
    }

    @Test void instantaneousTripDoesNotUseNormalStartingRelief() {
        Map<String,Object> r=sut.motor(34,1.25,1.15,1.75,1.15,"INSTANTANEOUS_TRIP",true,EDITION);
        assertEquals(false,r.get("startingReliefAllowed"));
        assertEquals(false,r.get("startingReliefApplied"));
        assertTrue(String.valueOf(r.get("startingReliefNote")).contains("28-210"));
    }

    @Test void unknownProtectionDeviceCannotQuietlyAuthorizeStartingRelief() {
        Map<String,Object> r=sut.motor(34,1.25,1.15,1.75,1.15,"UNRECOGNIZED",true,EDITION);
        assertEquals(false,r.get("startingReliefAllowed"));
        assertEquals(false,r.get("startingReliefApplied"));
        assertTrue(String.valueOf(r.get("startingReliefNote")).contains("recognized"));
    }

    @Test void rejectNonFiniteAndInvalidTransformerInputs() {
        assertThrows(IllegalArgumentException.class,
            () -> sut.transformer(Double.NaN,600,208,3,1.25,1.25,1.25,EDITION));
        assertThrows(IllegalArgumentException.class,
            () -> sut.transformer(75,Double.POSITIVE_INFINITY,208,3,1.25,1.25,1.25,EDITION));
        assertThrows(IllegalArgumentException.class,
            () -> sut.transformer(75,600,208,2,1.25,1.25,1.25,EDITION));
        assertThrows(IllegalArgumentException.class,
            () -> sut.transformer(75,600,208,3,Double.NaN,1.25,1.25,EDITION));
        assertThrows(IllegalArgumentException.class,
            () -> sut.transformer(75,600,208,3,1.25,Double.NEGATIVE_INFINITY,1.25,EDITION));
        assertThrows(IllegalArgumentException.class,
            () -> sut.transformer(75,600,208,3,1.25,1.25,0,EDITION));
        assertThrows(IllegalArgumentException.class,
            () -> sut.transformer(1e308,1e-100,208,3,1.25,1.25,1.25,EDITION));
    }

    @Test void rejectNonFiniteAndInvalidMotorInputs() {
        assertThrows(IllegalArgumentException.class,
            () -> sut.motor(Double.NaN,1.25,1.15,1.75,1.15,"TIME_DELAY_FUSE",false,EDITION));
        assertThrows(IllegalArgumentException.class,
            () -> sut.motor(Double.POSITIVE_INFINITY,1.25,1.15,1.75,1.15,"TIME_DELAY_FUSE",false,EDITION));
        assertThrows(IllegalArgumentException.class,
            () -> sut.motor(34,0,1.15,1.75,1.15,"TIME_DELAY_FUSE",false,EDITION));
        assertThrows(IllegalArgumentException.class,
            () -> sut.motor(34,1.25,Double.NaN,1.75,1.15,"TIME_DELAY_FUSE",false,EDITION));
        assertThrows(IllegalArgumentException.class,
            () -> sut.motor(34,1.25,1.15,Double.NEGATIVE_INFINITY,1.15,"TIME_DELAY_FUSE",false,EDITION));
        assertThrows(IllegalArgumentException.class,
            () -> sut.motor(1e308,1e308,1.15,1.75,1.15,"TIME_DELAY_FUSE",false,EDITION));
    }

    @Test void unsupportedEditionIsNotSilentlySubstituted() {
        assertThrows(IllegalArgumentException.class,
            () -> sut.motor(34,1.25,1.15,1.75,1.15,"TIME_DELAY_FUSE",false,"CEC_2021"));
        assertThrows(IllegalArgumentException.class,
            () -> sut.transformer(75,600,208,3,1.25,1.25,1.25,"CEC_2021"));
    }
}

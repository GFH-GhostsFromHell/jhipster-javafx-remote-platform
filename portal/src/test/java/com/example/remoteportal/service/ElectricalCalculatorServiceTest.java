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
    @Test void conductorReviewUsesSelectedSixtyDegreeTerminationBasis() {
        // 90°C conductor insulation does NOT authorize the 90°C column
        // at a 60°C rated termination; 55 × 0.82 × 0.80 = 36.08 A.
        Map<String,Object> r=sut.conductorReview(30,55,60,90,0.82,0.80,"TABLE_2",EDITION);
        assertEquals(60,r.get("selectedTerminationTemperatureC"));
        assertEquals(90,r.get("conductorInsulationTemperatureC"));
        assertEquals("TABLE 2",r.get("tableReference"));
        assertEquals(36.08,number(r,"adjustedAmpacityA"),0.001);
        assertEquals(6.08,number(r,"marginA"),0.001);
        assertEquals(true,r.get("meetsEnteredLoadArithmetically"));
        assertEquals(false,r.get("codeComplianceVerified"));
        assertEquals(false,r.get("terminationMarkingVerified"));
    }

    @Test void conductorReviewFailsLoadUsingTerminationColumnAtHotAmbient() {
        // 60°C column at 50°C ambient, 6 load conductors:
        // 55 × 0.58 × 0.80 = 25.52 A, not adequate for 30 A load.
        Map<String,Object> r=sut.conductorReview(30,55,60,90,0.58,0.80,"TABLE_2",EDITION);
        assertEquals(25.52,number(r,"adjustedAmpacityA"),0.001);
        assertEquals(-4.48,number(r,"marginA"),0.001);
        assertEquals(false,r.get("meetsEnteredLoadArithmetically"));
        assertTrue(String.valueOf(r.get("status")).contains("FAIL"));
    }

    @Test void conductorReviewUsesSeventyFiveDegreeBasisWhenSelected() {
        Map<String,Object> r=sut.conductorReview(50,85,75,90,0.88,0.80,"TABLE_2",EDITION);
        assertEquals(59.84,number(r,"adjustedAmpacityA"),0.001);
        assertEquals(true,r.get("meetsEnteredLoadArithmetically"));
        assertEquals(false,r.get("codeComplianceVerified"));
    }

    @Test void conductorReviewRejectsRatingsAndUnverifiedFactors() {
        assertThrows(IllegalArgumentException.class,
            ()->sut.conductorReview(30,55,75,60,0.82,0.80,"TABLE_2",EDITION));
        assertThrows(IllegalArgumentException.class,
            ()->sut.conductorReview(30,55,50,90,0.82,0.80,"TABLE_2",EDITION));
        assertThrows(IllegalArgumentException.class,
            ()->sut.conductorReview(30,55,60,90,Double.NaN,0.80,"TABLE_2",EDITION));
        assertThrows(IllegalArgumentException.class,
            ()->sut.conductorReview(30,55,60,90,0.82,1.01,"TABLE_2",EDITION));
        assertThrows(IllegalArgumentException.class,
            ()->sut.conductorReview(30,55,60,90,0.82,0.80,"UNKNOWN_TABLE",EDITION));
        assertThrows(IllegalArgumentException.class,
            ()->sut.conductorReview(30,55,60,90,0.82,0.80,null,EDITION));
        assertThrows(IllegalArgumentException.class,
            ()->sut.conductorReview(0,55,60,90,0.82,0.80,"TABLE_2",EDITION));
    }

    @Test void legacyMotorAndTransformerStarterSizesMustNeverClaimApproval() {
        Map<String,Object> motor=sut.motor(34,1.25,1.15,1.75,1.15,"TIME_DELAY_FUSE",false,EDITION);
        Map<String,Object> transformer=sut.transformer(75,600,208,3,1.25,1.25,1.25,EDITION);
        @SuppressWarnings("unchecked")
        Map<String,Object> starter=(Map<String,Object>)motor.get("starterConductor");
        @SuppressWarnings("unchecked")
        Map<String,Object> primary=(Map<String,Object>)transformer.get("primaryConductorStarter");
        assertEquals(true,starter.get("candidateOnly"));
        assertEquals(false,starter.get("codeComplianceVerified"));
        assertEquals(true,primary.get("candidateOnly"));
        assertEquals(false,motor.get("conductorSelectionVerified"));
        assertEquals(false,transformer.get("terminationTemperatureChecked"));
        assertTrue(String.valueOf(starter.get("requiredReview")).contains("60°C"));
    }

    @Test void conductorReviewRejectsNonFiniteInputAndUnsupportedEdition() {
        assertThrows(IllegalArgumentException.class,
            ()->sut.conductorReview(30,Double.POSITIVE_INFINITY,75,90,1,1,"TABLE_2",EDITION));
        assertThrows(IllegalArgumentException.class,
            ()->sut.conductorReview(30,55,60,90,0.82,0.8,"TABLE_2","CEC_2021"));
    }

}

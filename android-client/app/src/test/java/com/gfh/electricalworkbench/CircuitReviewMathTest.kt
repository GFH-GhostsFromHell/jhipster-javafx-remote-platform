package com.gfh.electricalworkbench

import org.junit.Assert.*
import org.junit.Test

/** Worked arithmetic only. Does not claim verified CEC/OESC table ampacities. */
class CircuitReviewMathTest {
    private fun baseline() = CircuitReviewMath.Input(
        loadA=40.0,designMultiplier=1.25,voltageV=600.0,phases=3,
        oneWayLengthM=40.0,circularMils=66360.0,kOhmCircularMilPerFoot=12.9,
        terminalColumnAmpacityA=85.0,terminalC=75,insulationC=90,
        ambientFactor=1.0,groupingFactor=1.0,selectedOcpdA=50.0,
        maxVoltageDropPercent=3.0
    )
    @Test fun generalThreePhaseCircuitMatchesIndependentWorkedExample() {
        val r=CircuitReviewMath.calculate(baseline())
        assertEquals(50.0,r.designCurrentA,0.001)
        assertEquals(85.0,r.adjustedAmpacityA,0.001)
        assertEquals(1.76746,r.voltageDropV,0.0001)
        assertEquals(.294576,r.voltageDropPercent,0.0001)
        assertEquals(6516.03,r.minimumCircularMilsForDrop,0.02)
        assertEquals(407.365,r.maximumOneWayLengthM,0.01)
        assertTrue(r.ampacityArithmeticFits)
        assertTrue(r.voltageDropArithmeticFits)
        assertTrue(r.protectionScreenFits)
    }
    @Test fun singlePhaseUsesTwoConductorsNotThreePhaseRootThree() {
        val r=CircuitReviewMath.calculate(baseline().copy(phases=1))
        assertEquals(2.04096,r.voltageDropV,0.02)
        assertTrue(r.voltageDropV>1.76746)
    }
    @Test fun hotSixtyDegreeTerminationsCanFailEvenWithNinetyDegreeWire() {
        val r=CircuitReviewMath.calculate(baseline().copy(
            terminalC=60,terminalColumnAmpacityA=55.0,ambientFactor=.58,
            groupingFactor=.8
        ))
        assertEquals(25.52,r.adjustedAmpacityA,0.001)
        assertFalse(r.ampacityArithmeticFits)
        assertFalse(r.protectionScreenFits)
    }
    @Test fun smallConductorFailsVoltageDropWithoutInflatingAmpacity() {
        val r=CircuitReviewMath.calculate(baseline().copy(circularMils=4000.0))
        assertFalse(r.voltageDropArithmeticFits)
        assertTrue(r.ampacityArithmeticFits)
    }
    @Test fun highOcpdDoesNotPassConservativeGeneralCircuitScreen() {
        val r=CircuitReviewMath.calculate(baseline().copy(selectedOcpdA=100.0))
        assertFalse(r.protectionScreenFits)
    }
    @Test fun zeroOrInvalidInputsAlwaysReject() {
        val cases=listOf(
            baseline().copy(loadA=0.0),
            baseline().copy(voltageV=Double.NaN),
            baseline().copy(oneWayLengthM=-1.0),
            baseline().copy(phases=2),
            baseline().copy(terminalC=90,insulationC=60),
            baseline().copy(groupingFactor=1.2),
            baseline().copy(designMultiplier=1.10),
            baseline().copy(maxVoltageDropPercent=100.0),
            baseline().copy(circularMils=Double.POSITIVE_INFINITY)
        )
        cases.forEach { v->assertThrows(IllegalArgumentException::class.java) {
            CircuitReviewMath.calculate(v)
        } }
    }
    @Test fun inferredLengthAndAreaScaleLinearly() {
        val a=CircuitReviewMath.calculate(baseline())
        val b=CircuitReviewMath.calculate(baseline().copy(oneWayLengthM=80.0))
        assertEquals(a.voltageDropV*2.0,b.voltageDropV,1e-9)
        assertEquals(a.minimumCircularMilsForDrop*2.0,b.minimumCircularMilsForDrop,1e-9)
        assertEquals(a.maximumOneWayLengthM,b.maximumOneWayLengthM,1e-9)
    }
}

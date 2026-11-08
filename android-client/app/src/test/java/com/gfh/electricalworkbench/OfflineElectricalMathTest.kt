package com.gfh.electricalworkbench

import org.junit.Assert.*
import org.junit.Test

/**
 * Native Android arithmetic regression checks. These are NOT code-table
 * verification or approvals of conductor/transformer installations.
 */
class OfflineElectricalMathTest {
    @Test fun independentThreePhaseTransformerExamples() {
        assertEquals(72.1688, OfflineElectricalMath.transformerCurrent(75.0, 600.0, 3), 0.015)
        assertEquals(208.179, OfflineElectricalMath.transformerCurrent(75.0, 208.0, 3), 0.015)
    }
    @Test fun independentSinglePhaseTransformerExample() {
        assertEquals(41.6667, OfflineElectricalMath.transformerCurrent(10.0, 240.0, 1), 0.015)
    }
    @Test fun transformerRejectsBadData() {
        val cases = listOf(
            Triple(Double.NaN,600.0,3),
            Triple(75.0,0.0,3),
            Triple(75.0,600.0,2),
            Triple(Double.POSITIVE_INFINITY,600.0,3)
        )
        cases.forEach { (kva, voltage, phases) ->
            assertThrows(IllegalArgumentException::class.java) {
                OfflineElectricalMath.transformerCurrent(kva,voltage,phases)
            }
        }
    }
    @Test fun correctionUsesEnteredTerminationColumnNotInsulationRating() {
        assertEquals(25.52,OfflineElectricalMath.correctedAmpacity(55.0,60,90,0.58,0.8),0.001)
        assertEquals(33.0,OfflineElectricalMath.correctedAmpacity(55.0,75,90,0.75,0.8),0.001)
    }
    @Test fun correctionRejectsInvalidTerminalsAndFactors() {
        val cases = listOf(
            { OfflineElectricalMath.correctedAmpacity(55.0,75,60,.75,.8) },
            { OfflineElectricalMath.correctedAmpacity(55.0,65,90,.75,.8) },
            { OfflineElectricalMath.correctedAmpacity(55.0,60,90,Double.NaN,.8) },
            { OfflineElectricalMath.correctedAmpacity(55.0,60,90,1.25,.8) },
            { OfflineElectricalMath.correctedAmpacity(0.0,60,90,.58,.8) },
            { OfflineElectricalMath.correctedAmpacity(Double.POSITIVE_INFINITY,60,90,.58,.8) }
        )
        cases.forEach { fn -> assertThrows(IllegalArgumentException::class.java) { fn() } }
    }
}

package com.gfh.electricalworkbench

import org.junit.Assert.*
import org.junit.Test

class IndustrialFieldMathTest {
    private fun map(vararg pairs: Pair<String,Double>):Map<String,Double> = mapOf(*pairs)
    @Test fun currentAndVoltageRatiosMatchIndependentWorkedCases(){
        val ct=IndustrialFieldMath.calculate("ct",map(
            "primaryRatedA" to 400.0,"secondaryRatedA" to 5.0,"actualPrimaryA" to 200.0))
        assertEquals(80.0,ct.values["CT ratio"]!!,1e-9)
        assertEquals(2.5,ct.values["Secondary current A"]!!,1e-9)
        assertFalse(ct.codeComplianceVerified)
        val vt=IndustrialFieldMath.calculate("vt",map(
            "primaryRatedV" to 4160.0,"secondaryRatedV" to 120.0,"actualPrimaryV" to 4000.0))
        assertEquals(115.384615,vt.values["Secondary voltage V"]!!,0.0001)
    }
    @Test fun balancedPowerAndPickupAreArithmeticOnly() {
        val power=IndustrialFieldMath.calculate("power",map(
            "lineVoltageV" to 600.0,"lineCurrentA" to 100.0,"powerFactor" to .85))
        assertEquals(103.923048,power.values["Apparent power kVA"]!!,0.0001)
        assertEquals(88.33459,power.values["Real power kW"]!!,0.0001)
        val relay=IndustrialFieldMath.calculate("pickup",map(
            "primaryRatedA" to 400.0,"secondaryRatedA" to 5.0,"primaryPickupA" to 240.0))
        assertEquals(3.0,relay.values["Secondary pickup A"]!!,1e-9)
        assertTrue(relay.caution.contains("NOT a recommended relay setting"))
    }
    @Test fun transformerFaultIsExplicitlyIdealAndAnalogUsesLiveZero() {
        val fault=IndustrialFieldMath.calculate("fault",map(
            "transformerKva" to 75.0,"secondaryVoltageV" to 600.0,"impedancePercent" to 5.0))
        assertEquals(72.16878365,fault.values["Transformer FLA A"]!!,0.0001)
        assertEquals(1443.37567,fault.values["IDEAL infinite-source secondary fault A"]!!,0.0001)
        assertTrue(fault.caution.contains("NOT actual available fault current"))
        val analog=IndustrialFieldMath.calculate("analog",map(
            "engineeringMin" to 0.0,"engineeringMax" to 1800.0,"engineeringValue" to 1200.0))
        assertEquals(14.66666667,analog.values["Analog current mA"]!!,0.0001)
    }
    @Test fun invalidAndNonfiniteValuesAreRejected(){
        val values=listOf(
            "ct" to map("primaryRatedA" to 0.0,"secondaryRatedA" to 5.0,"actualPrimaryA" to 200.0),
            "ct" to map("primaryRatedA" to Double.NaN,"secondaryRatedA" to 5.0,"actualPrimaryA" to 200.0),
            "power" to map("lineVoltageV" to 600.0,"lineCurrentA" to 100.0,"powerFactor" to 1.2),
            "pickup" to map("primaryRatedA" to 400.0,"secondaryRatedA" to 5.0,"primaryPickupA" to Double.POSITIVE_INFINITY),
            "fault" to map("transformerKva" to 75.0,"secondaryVoltageV" to 600.0,"impedancePercent" to 100.0),
            "analog" to map("engineeringMin" to 0.0,"engineeringMax" to 1800.0,"engineeringValue" to 2000.0),
            "unknown" to emptyMap()
        )
        values.forEach { (mode,input)->
            assertThrows(IllegalArgumentException::class.java) {
                IndustrialFieldMath.calculate(mode,input)
            }
        }
    }
    @Test fun exactlySixModesHaveOnlyThreeInputsAndNoCertifiedPreset() {
        assertEquals(6,IndustrialFieldMath.modes.size)
        assertTrue(IndustrialFieldMath.modes.all { it.fields.size==3 })
    }
}

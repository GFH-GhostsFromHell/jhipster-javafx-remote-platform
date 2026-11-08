package com.gfh.electricalworkbench

import kotlin.math.sqrt

/**
 * Pure offline electrical arithmetic shared with transformer and conductor review.
 * Does not contain licensed CEC table data or approve installation conditions.
 */
internal object OfflineElectricalMath {
    fun transformerCurrent(kva: Double, voltage: Double, phases: Int): Double {
        require(kva.isFinite() && kva > 0 && voltage.isFinite() && voltage > 0) {
            "Enter finite positive transformer kVA and voltage."
        }
        require(phases == 1 || phases == 3) { "Transformer phase count must be 1 or 3." }
        val result = kva * 1000.0 / (if (phases == 3) sqrt(3.0) * voltage else voltage)
        require(result.isFinite() && result > 0) { "Transformer current is outside the supported range." }
        return result
    }

    fun correctedAmpacity(
        terminationColumnAmpacityA: Double,
        terminationC: Int,
        insulationC: Int,
        ambientFactor: Double,
        groupingFactor: Double
    ): Double {
        require(terminationColumnAmpacityA.isFinite() && terminationColumnAmpacityA > 0) {
            "Enter a finite positive ampacity from the selected equipment termination column."
        }
        require(terminationC in listOf(60, 75, 90) && insulationC in listOf(60, 75, 90)
            && insulationC >= terminationC) {
            "Select compatible termination and insulation ratings (60/75/90 °C)."
        }
        require(ambientFactor.isFinite() && ambientFactor > 0 && ambientFactor <= 1
            && groupingFactor.isFinite() && groupingFactor > 0 && groupingFactor <= 1) {
            "Verify the ambient and grouping factors against the applicable CEC 2024 tables."
        }
        val corrected = terminationColumnAmpacityA * ambientFactor * groupingFactor
        require(corrected.isFinite() && corrected > 0) { "Ampacity calculation exceeded numeric limits." }
        return corrected
    }
}

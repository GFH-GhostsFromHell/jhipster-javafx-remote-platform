package com.gfh.electricalworkbench

import kotlin.math.sqrt

/** Pure, offline calculation. No embedded or asserted verified CEC ampacity dataset. */
internal object CircuitReviewMath {
    data class Input(
        val loadA: Double,
        val designMultiplier: Double,
        val voltageV: Double,
        val phases: Int,
        val oneWayLengthM: Double,
        val circularMils: Double,
        val kOhmCircularMilPerFoot: Double,
        val terminalColumnAmpacityA: Double,
        val terminalC: Int,
        val insulationC: Int,
        val ambientFactor: Double,
        val groupingFactor: Double,
        val selectedOcpdA: Double,
        val maxVoltageDropPercent: Double
    )
    data class Result(
        val designCurrentA: Double,
        val adjustedAmpacityA: Double,
        val conductorMarginA: Double,
        val voltageDropV: Double,
        val voltageDropPercent: Double,
        val allowableDropV: Double,
        val minimumCircularMilsForDrop: Double,
        val maximumOneWayLengthM: Double,
        val ampacityArithmeticFits: Boolean,
        val voltageDropArithmeticFits: Boolean,
        val protectionScreenFits: Boolean
    )

    fun calculate(v: Input): Result {
        val positive = listOf(v.loadA, v.designMultiplier, v.voltageV, v.oneWayLengthM,
            v.circularMils, v.kOhmCircularMilPerFoot, v.terminalColumnAmpacityA,
            v.selectedOcpdA, v.maxVoltageDropPercent)
        require(positive.all { it.isFinite() && it > 0 }) {
            "Enter positive finite load, voltage, length, wire area, K, ampacity and breaker values."
        }
        require(v.designMultiplier == 1.0 || v.designMultiplier == 1.25) {
            "Select 100% or 125% load-sizing treatment; special loads need their own rule."
        }
        require(v.phases == 1 || v.phases == 3) {
            "Select one-phase or three-phase circuit."
        }
        require(v.maxVoltageDropPercent < 100) {
            "Voltage-drop limit must be below 100%."
        }
        val ampacity=OfflineElectricalMath.correctedAmpacity(
            v.terminalColumnAmpacityA,v.terminalC,v.insulationC,v.ambientFactor,v.groupingFactor
        )
        val design=v.loadA*v.designMultiplier
        val feet=v.oneWayLengthM*3.280839895013123
        val phaseFactor=if (v.phases == 3) sqrt(3.0) else 2.0
        val drop=phaseFactor*v.kOhmCircularMilPerFoot*v.loadA*feet/v.circularMils
        val maxDrop=v.voltageV*v.maxVoltageDropPercent/100.0
        val minimumArea=phaseFactor*v.kOhmCircularMilPerFoot*v.loadA*feet/maxDrop
        val maximumLengthFt=maxDrop*v.circularMils/(phaseFactor*v.kOhmCircularMilPerFoot*v.loadA)
        val maxLengthM=maximumLengthFt/3.280839895013123
        require(listOf(design,ampacity,drop,maxDrop,minimumArea,maxLengthM).all {
            it.isFinite() && it > 0
        }) { "Calculation is outside the supported range; check input magnitudes." }
        return Result(
            designCurrentA=design,
            adjustedAmpacityA=ampacity,
            conductorMarginA=ampacity-design,
            voltageDropV=drop,
            voltageDropPercent=drop/v.voltageV*100.0,
            allowableDropV=maxDrop,
            minimumCircularMilsForDrop=minimumArea,
            maximumOneWayLengthM=maxLengthM,
            ampacityArithmeticFits=design<=ampacity+1e-9,
            voltageDropArithmeticFits=drop<=maxDrop+1e-9,
            // Conservative general-purpose screen, not an OCPD authorization.
            protectionScreenFits=v.selectedOcpdA+1e-9>=design &&
                v.selectedOcpdA<=ampacity+1e-9
        )
    }
}

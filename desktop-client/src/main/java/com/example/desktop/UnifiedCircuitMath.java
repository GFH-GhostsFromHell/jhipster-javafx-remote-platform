package com.example.desktop;

/**
 * Offline, intentionally non-authoritative circuit arithmetic.
 * All CEC/OESC ampacity, termination and correction-factor inputs must be
 * verified independently. The resistance K-factor VD model is approximate.
 */
final class UnifiedCircuitMath {
    private UnifiedCircuitMath() {}

    record Input(double loadA, double designMultiplier, double voltageV, int phases,
                 double oneWayLengthM, double circularMils, double kOhmCircularMilPerFoot,
                 double terminalColumnAmpacityA, int terminalC, int insulationC,
                 double ambientFactor, double groupingFactor, double selectedOcpdA,
                 double maxVoltageDropPercent) {}

    record Result(double designCurrentA, double adjustedAmpacityA, double conductorMarginA,
                  double voltageDropV, double voltageDropPercent, double allowableDropV,
                  double minimumCircularMilsForDrop, double maximumOneWayLengthM,
                  boolean ampacityArithmeticFits, boolean voltageDropArithmeticFits,
                  boolean protectionScreenFits, boolean codeComplianceVerified) {}

    static Result calculate(Input i) {
        for (double x : new double[]{
            i.loadA(), i.designMultiplier(), i.voltageV(), i.oneWayLengthM(),
            i.circularMils(), i.kOhmCircularMilPerFoot(), i.terminalColumnAmpacityA(),
            i.selectedOcpdA(), i.maxVoltageDropPercent()
        }) positive(x);
        if (i.designMultiplier() != 1 && i.designMultiplier() != 1.25)
            throw new IllegalArgumentException("General load basis must be 100% or 125%; use special Code rules for other loads.");
        if (i.phases() != 1 && i.phases() != 3)
            throw new IllegalArgumentException("Enter a 1-phase two-wire or balanced 3-phase circuit.");
        if (i.maxVoltageDropPercent() >= 100)
            throw new IllegalArgumentException("Selected voltage-drop target must be below 100%.");
        if ((i.terminalC() != 60 && i.terminalC() != 75 && i.terminalC() != 90)
                || (i.insulationC() != 60 && i.insulationC() != 75 && i.insulationC() != 90)
                || i.insulationC() < i.terminalC())
            throw new IllegalArgumentException("Verify compatible 60/75/90°C terminal and insulation ratings.");
        if (!Double.isFinite(i.ambientFactor()) || i.ambientFactor() <= 0 || i.ambientFactor() > 1
                || !Double.isFinite(i.groupingFactor()) || i.groupingFactor() <= 0 || i.groupingFactor() > 1)
            throw new IllegalArgumentException("Enter applicable ambient and grouping factors from 0 to 1.");

        final double design = i.loadA() * i.designMultiplier();
        final double adjusted = i.terminalColumnAmpacityA() * i.ambientFactor() * i.groupingFactor();
        final double ft = i.oneWayLengthM() * 3.280839895013123;
        final double phaseFactor = i.phases() == 3 ? Math.sqrt(3.0) : 2.0;
        final double drop = phaseFactor * i.kOhmCircularMilPerFoot() * i.loadA() * ft / i.circularMils();
        final double allowedDrop = i.voltageV() * i.maxVoltageDropPercent() / 100;
        final double minArea = phaseFactor * i.kOhmCircularMilPerFoot() * i.loadA() * ft / allowedDrop;
        final double maxLengthM = (allowedDrop * i.circularMils()
                / (phaseFactor * i.kOhmCircularMilPerFoot() * i.loadA())) / 3.280839895013123;
        final double dropPercent = drop / i.voltageV() * 100;
        final double margin = adjusted - design;

        for (double x : new double[]{design, adjusted, drop, allowedDrop, minArea, maxLengthM, dropPercent})
            positive(x);
        if (!Double.isFinite(margin))
            throw new IllegalArgumentException("Current/ampacity margin is outside supported range.");

        return new Result(design, adjusted, margin, drop, dropPercent,
                allowedDrop, minArea, maxLengthM,
                design <= adjusted + 1e-9,
                drop <= allowedDrop + 1e-9,
                i.selectedOcpdA() + 1e-9 >= design && i.selectedOcpdA() <= adjusted + 1e-9,
                false);
    }

    private static void positive(double x) {
        if (!Double.isFinite(x) || x <= 0)
            throw new IllegalArgumentException("Enter finite positive circuit, conductor, voltage-drop and device values.");
    }
}

package com.example.desktop;

import java.util.Map;

/**
 * Centralized CEC-2024-oriented correction FACTOR worksheet for the Windows
 * offline tools. Original preset values were already in AmpacityDeratingPane
 * and RacewayAmpacityPane; no licensed table text is reproduced here.
 *
 * IMPORTANT: CEC 2024 Table 5A / 5C row values are independent examples and
 * REQUIRE validation against an authorized current CSA/OESC copy before being
 * described as a certified/verified Code dataset. Equipment markings and the
 * selected Rule 4-006 termination-temperature column govern the application.
 */
final class ConductorCorrectionFactors {
    private ConductorCorrectionFactors() { }

    // Columns in order: 60°C / 75°C / 90°C termination temperature.
    private static final Map<Integer, double[]> AMBIENT = Map.ofEntries(
        Map.entry(30,new double[]{1.00,1.00,1.00}),
        Map.entry(35,new double[]{.91,.94,.96}),
        Map.entry(40,new double[]{.82,.88,.91}),
        Map.entry(45,new double[]{.71,.82,.87}),
        Map.entry(50,new double[]{.58,.75,.82}),
        Map.entry(55,new double[]{.41,.67,.76}),
        Map.entry(60,new double[]{Double.NaN,.58,.71}),
        Map.entry(65,new double[]{Double.NaN,.47,.65}),
        Map.entry(70,new double[]{Double.NaN,.33,.58}),
        Map.entry(75,new double[]{Double.NaN,Double.NaN,.50}),
        Map.entry(80,new double[]{Double.NaN,Double.NaN,.41})
    );

    static double ambientFactor(int ambientC, int terminationC) {
        final int column = switch(terminationC) {
            case 60 -> 0;
            case 75 -> 1;
            case 90 -> 2;
            default -> throw new IllegalArgumentException("Termination temperature must be 60, 75 or 90°C.");
        };
        double[] values = AMBIENT.get(ambientC);
        if (values == null) throw new IllegalArgumentException("Select a supported ambient temperature (30–80°C in 5°C increments).");
        double factor = values[column];
        if (!Double.isFinite(factor) || factor <= 0)
            throw new IllegalArgumentException("No usable correction factor for the selected temperature combination.");
        return factor;
    }

    static double groupingFactor(int currentCarryingCount) {
        if (currentCarryingCount < 0)
            throw new IllegalArgumentException("Current-carrying conductor count cannot be negative.");
        if (currentCarryingCount <= 3) return 1;
        if (currentCarryingCount <= 6) return .80;
        if (currentCarryingCount <= 24) return .70;
        if (currentCarryingCount <= 42) return .60;
        return .50;
    }

    static double correctedAmpacity(double terminationColumnAmpacityA,
                                    int terminationC, int insulationC,
                                    int ambientC, int currentCarryingCount) {
        if (!Double.isFinite(terminationColumnAmpacityA) || terminationColumnAmpacityA <= 0)
            throw new IllegalArgumentException("Enter a positive finite ampacity from the appropriate equipment termination column.");
        if (insulationC != 60 && insulationC != 75 && insulationC != 90)
            throw new IllegalArgumentException("Insulation must be 60, 75 or 90°C.");
        if (insulationC < terminationC)
            throw new IllegalArgumentException("Conductor insulation cannot be rated below the selected termination temperature.");
        double corrected=terminationColumnAmpacityA*
            ambientFactor(ambientC, terminationC)*groupingFactor(currentCarryingCount);
        if (!Double.isFinite(corrected) || corrected <= 0)
            throw new IllegalArgumentException("Conductor ampacity calculation exceeded numeric limits.");
        return corrected;
    }
}

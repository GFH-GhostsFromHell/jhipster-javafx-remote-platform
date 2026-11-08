package com.example.desktop;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-platform parity: same input scenario and manually calculated expected
 * values as Android, iOS and web. Code table data remain user supplied.
 */
class UnifiedCircuitMathTest {
    private static UnifiedCircuitMath.Input input() {
        return new UnifiedCircuitMath.Input(40,1.25,600,3,40,66360,12.9,
            85,75,90,1,1,50,3);
    }

    private static UnifiedCircuitMath.Input with(
        int phases,double length,double cm,double base,int terminal,
        double ambient,double grouping,double device
    ) {
        return new UnifiedCircuitMath.Input(40,1.25,600,phases,length,cm,12.9,
            base,terminal,90,ambient,grouping,device,3);
    }

    @Test void baselineMatchesAndroidWebAndIOSWorkedValues() {
        UnifiedCircuitMath.Result r = UnifiedCircuitMath.calculate(input());
        assertEquals(50,r.designCurrentA(),1e-9);
        assertEquals(85,r.adjustedAmpacityA(),1e-9);
        assertEquals(1.76746,r.voltageDropV(),0.0001);
        assertEquals(0.294576,r.voltageDropPercent(),0.0001);
        assertEquals(6516.03,r.minimumCircularMilsForDrop(),0.02);
        assertEquals(407.365,r.maximumOneWayLengthM(),0.01);
        assertTrue(r.ampacityArithmeticFits());
        assertTrue(r.voltageDropArithmeticFits());
        assertTrue(r.protectionScreenFits());
        assertFalse(r.codeComplianceVerified());
    }

    @Test void singlePhaseUsesTwoConductorFactor() {
        double v=UnifiedCircuitMath.calculate(with(1,40,66360,85,75,1,1,50)).voltageDropV();
        assertEquals(2.04096,v,0.02);
        assertTrue(v>UnifiedCircuitMath.calculate(input()).voltageDropV());
    }

    @Test void deratingFollowsTerminalRatingAndRejectsUnsafeDevice() {
        UnifiedCircuitMath.Result r=UnifiedCircuitMath.calculate(
            with(3,40,66360,55,60,.58,.8,50));
        assertEquals(25.52,r.adjustedAmpacityA(),1e-9);
        assertFalse(r.ampacityArithmeticFits());
        assertFalse(r.protectionScreenFits());
    }

    @Test void badVoltageDropAndLargeOcpdMustFailIndependently() {
        UnifiedCircuitMath.Result small=UnifiedCircuitMath.calculate(
            with(3,40,4000,85,75,1,1,50));
        assertFalse(small.voltageDropArithmeticFits());
        assertTrue(small.ampacityArithmeticFits());
        UnifiedCircuitMath.Result breaker=UnifiedCircuitMath.calculate(
            with(3,40,66360,85,75,1,1,100));
        assertFalse(breaker.protectionScreenFits());
    }

    @Test void distanceDoublesBothDropAndMinimumArea() {
        var a=UnifiedCircuitMath.calculate(input());
        var b=UnifiedCircuitMath.calculate(with(3,80,66360,85,75,1,1,50));
        assertEquals(2*a.voltageDropV(),b.voltageDropV(),1e-9);
        assertEquals(2*a.minimumCircularMilsForDrop(),b.minimumCircularMilsForDrop(),1e-9);
        assertEquals(a.maximumOneWayLengthM(),b.maximumOneWayLengthM(),1e-9);
    }

    @Test void malformedOrContradictoryInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
            ()->UnifiedCircuitMath.calculate(with(2,40,66360,85,75,1,1,50)));
        assertThrows(IllegalArgumentException.class,
            ()->UnifiedCircuitMath.calculate(with(3,-40,66360,85,75,1,1,50)));
        assertThrows(IllegalArgumentException.class,
            ()->UnifiedCircuitMath.calculate(with(3,40,66360,85,75,1.2,1,50)));
        assertThrows(IllegalArgumentException.class,
            ()->UnifiedCircuitMath.calculate(new UnifiedCircuitMath.Input(
                40,1.25,600,3,40,66360,12.9,85,90,60,1,1,50,3)));
        assertThrows(IllegalArgumentException.class,
            ()->UnifiedCircuitMath.calculate(new UnifiedCircuitMath.Input(
                Double.NaN,1.25,600,3,40,66360,12.9,85,75,90,1,1,50,3)));
        assertThrows(IllegalArgumentException.class,
            ()->UnifiedCircuitMath.calculate(new UnifiedCircuitMath.Input(
                40,1.1,600,3,40,66360,12.9,85,75,90,1,1,50,3)));
    }
}

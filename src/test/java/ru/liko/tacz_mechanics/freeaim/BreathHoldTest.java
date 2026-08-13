package ru.liko.tacz_mechanics.freeaim;

import org.junit.jupiter.api.Test;
import ru.liko.tacz_mechanics.client.freeaim.BreathHold;

import static org.junit.jupiter.api.Assertions.*;

class BreathHoldTest {

    /** Defaults: 100 ticks of air, 160 to refill, 0.15 steady, 1.8 exhausted, 8-tick settle. */
    private static BreathHold defaults() {
        BreathHold b = new BreathHold();
        b.setParams(100f, 160f, 0.15f, 1.8f, 8f);
        return b;
    }

    private static void tick(BreathHold b, boolean holding, int times) {
        for (int i = 0; i < times; i++) {
            b.tick(holding);
        }
    }

    @Test
    void startsSteadyAndFull() {
        BreathHold b = defaults();
        assertEquals(1f, b.getSteadiness(), 1e-6);
        assertEquals(1f, b.getBreathFraction(), 1e-6);
        assertFalse(b.isExhausted());
    }

    @Test
    void holdingSteadiesTheAimGradually() {
        BreathHold b = defaults();
        b.tick(true);
        assertTrue(b.getSteadiness() > 0.8f, "must not snap steady in one tick, was " + b.getSteadiness());
        tick(b, true, 39);
        assertTrue(b.getSteadiness() < 0.25f, "must settle near steadyFactor, was " + b.getSteadiness());
    }

    @Test
    void runningOutOfAirCostsMoreThanNormalShake() {
        BreathHold b = defaults();
        tick(b, true, 100);
        assertTrue(b.isExhausted());
        assertEquals(0f, b.getBreathFraction(), 1e-6);
        // Still holding the key: exhaustion must override, not keep the free steadiness.
        tick(b, true, 40);
        assertTrue(b.getSteadiness() > 1.4f, "exhausted shake must exceed normal, was " + b.getSteadiness());
    }

    @Test
    void exhaustionLiftsOnlyAfterHalfTheBarRefills() {
        BreathHold b = defaults();
        tick(b, true, 100);
        // 160 ticks refill the whole bar, so half of it takes 80.
        tick(b, false, 79);
        assertTrue(b.isExhausted(), "must still be exhausted just short of the halfway mark");
        b.tick(false);
        assertFalse(b.isExhausted());
    }

    @Test
    void breathRefillsButNeverOverflows() {
        BreathHold b = defaults();
        tick(b, true, 50);
        tick(b, false, 500);
        assertEquals(1f, b.getBreathFraction(), 1e-6);
        assertEquals(1f, b.getSteadiness(), 0.01f);
    }

    @Test
    void shrinkingCapacityDoesNotLeaveAStaleReserve() {
        BreathHold b = defaults();
        b.setParams(20f, 160f, 0.15f, 1.8f, 8f);
        assertEquals(1f, b.getBreathFraction(), 1e-6);
        tick(b, true, 20);
        assertTrue(b.isExhausted(), "20 ticks of air must be gone after 20 ticks of holding");
    }
}

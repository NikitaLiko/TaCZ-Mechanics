package ru.liko.tacz_mechanics.freeaim;

import org.junit.jupiter.api.Test;
import ru.liko.tacz_mechanics.client.freeaim.SwaySpring;
import static org.junit.jupiter.api.Assertions.*;

class SwaySpringTest {

    @Test
    void startsAtZero() {
        SwaySpring s = new SwaySpring();
        s.setParams(0.2f, 0.5f, 5f);
        assertEquals(0f, s.getValue(), 1e-6);
    }

    @Test
    void impulseDecaysBackToZero() {
        SwaySpring s = new SwaySpring();
        s.setParams(0.2f, 0.5f, 10f);
        s.addImpulse(3f);
        for (int i = 0; i < 500; i++) {
            s.update(1f);
        }
        assertEquals(0f, s.getValue(), 0.01f);
    }

    @Test
    void doesNotDivergeWithSaneParams() {
        SwaySpring s = new SwaySpring();
        s.setParams(0.3f, 0.6f, 10f);
        s.addImpulse(5f);
        for (int i = 0; i < 1000; i++) {
            s.update(1f);
            assertTrue(Math.abs(s.getValue()) <= 10f + 1e-3,
                    "position must stay within clamp, was " + s.getValue());
        }
    }

    @Test
    void clampsToMaxAngle() {
        SwaySpring s = new SwaySpring();
        s.setParams(0.0f, 0.0f, 2f); // нет возврата, нет демпфирования
        s.addImpulse(100f);
        s.update(1f);
        assertEquals(2f, s.getValue(), 1e-6);
        s.addImpulse(-100f);
        s.update(1f);
        assertEquals(-2f, s.getValue(), 1e-6);
    }

    @Test
    void interpolationBlendsPrevAndCurrent() {
        SwaySpring s = new SwaySpring();
        s.setParams(0.0f, 0.0f, 100f);
        s.addImpulse(10f);
        s.update(1f); // prev=0, current=10
        assertEquals(0f, s.getInterpolated(0f), 1e-6);
        assertEquals(10f, s.getInterpolated(1f), 1e-6);
        assertEquals(5f, s.getInterpolated(0.5f), 1e-6);
    }

    @Test
    void clampGuaranteesStabilityAtConfigMaxParams() {
        SwaySpring s = new SwaySpring();
        s.setParams(1.0f, 2.0f, 4f); // config maximums
        s.addImpulse(50f);
        for (int i = 0; i < 500; i++) {
            s.update(1f);
            assertTrue(Math.abs(s.getValue()) <= 4f + 1e-3f,
                    "value must stay within clamp bounds at config extremes, was " + s.getValue());
        }
    }

    @Test
    void substepsKeepStiffRecoilPresetStable() {
        // Recoil defaults: damping 1.5 > 1 flutters at the Nyquist limit with a single dt=1 step,
        // which is exactly what substepping exists to fix.
        SwaySpring s = new SwaySpring();
        s.setParams(0.9f, 1.5f, 10f);
        s.addImpulse(2f);
        float peak = 0f;
        float worstUndershoot = 0f;
        for (int i = 0; i < 60; i++) {
            s.update(1f, 4);
            peak = Math.max(peak, s.getValue());
            worstUndershoot = Math.min(worstUndershoot, s.getValue());
        }
        assertTrue(peak > 0.5f, "kick must actually be visible, peak was " + peak);
        assertTrue(worstUndershoot > -0.25f * peak,
                "must not bounce hard past center, undershoot was " + worstUndershoot);
        assertEquals(0f, s.getValue(), 0.01f, "kick must settle back to center");
    }

    @Test
    void interpolationAnchorsToTickStartWhenSubstepping() {
        SwaySpring s = new SwaySpring();
        s.setParams(0.4f, 0.9f, 10f);
        s.addImpulse(3f);
        s.update(1f, 4);
        float afterFirstTick = s.getValue();
        s.update(1f, 4);
        assertEquals(afterFirstTick, s.getInterpolated(0f), 1e-6,
                "pt=0 must be the position at the start of the tick, not the last substep");
    }

    @Test
    void softLimitIsExactBelowTheKneeAndCompressesAbove() {
        SwaySpring s = new SwaySpring();
        s.setParams(0f, 0f, 10f); // no spring, no damping: position == the raw impulse
        s.addImpulse(5f);
        s.update(1f);
        assertEquals(5f, s.getValue(), 1e-5, "below the knee (8 of 10) the sway must be untouched");

        s.reset();
        s.addImpulse(9f);
        s.update(1f);
        // knee 8, range 2, so 9 maps to 8 + 2*tanh(1/2)
        assertEquals(8f + 2f * (float) Math.tanh(0.5), s.getValue(), 1e-5);
        assertTrue(s.getValue() < 9f, "past the knee the travel must compress, not pass through");
    }

    @Test
    void slammingTheLimitDoesNotStopTheGunDead() {
        SwaySpring s = new SwaySpring();
        s.setParams(0.4f, 0.9f, 4f);
        s.addImpulse(12f); // far past maxAngle: the old hard wall zeroed velocity here
        float previous = 0f;
        float biggestStep = 0f;
        for (int i = 0; i < 5; i++) {
            s.update(1f, 4);
            biggestStep = Math.max(biggestStep, Math.abs(s.getValue() - previous));
            previous = s.getValue();
        }
        assertTrue(s.getValue() <= 4f + 1e-3f, "must still respect maxAngle, was " + s.getValue());
        assertTrue(biggestStep < 4f, "must ease into the limit, biggest single-tick jump was " + biggestStep);
    }

    @Test
    void resetZeroesEverything() {
        SwaySpring s = new SwaySpring();
        s.setParams(0.2f, 0.5f, 10f);
        s.addImpulse(5f);
        s.update(1f);
        s.reset();
        assertEquals(0f, s.getValue(), 1e-6);
        assertEquals(0f, s.getInterpolated(1f), 1e-6);
    }
}

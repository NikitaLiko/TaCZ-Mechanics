package ru.liko.tacz_mechanics.freeaim;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.client.freeaim.TremorSource;

import static org.junit.jupiter.api.Assertions.*;

class TremorSourceTest {

    @BeforeEach
    void configure() {
        Config.FreeAim.tremorEnabled = true;
        Config.FreeAim.tremorScale = 1.0;
        Config.FreeAim.tremorZoomScale = 0.0;
        Config.FreeAim.tremorBreathTicks = 80;
        Config.FreeAim.tremorBuildupTicks = 1; // straight to full strength: amplitude stays constant
    }

    /**
     * The oscillator phases wrap every ~50-140 ticks. A wrap must be invisible - an earlier version
     * scaled an already-wrapped phase, which tore a step into the sway.
     */
    @Test
    void swayStaysContinuousAcrossPhaseWraps() {
        TremorSource tremor = new TremorSource();
        float previousPitch = 0f;
        float previousYaw = 0f;
        float biggestStep = 0f;
        for (int i = 0; i < 4000; i++) {
            tremor.update(1f, 1f, 1f);
            if (i > 0) {
                biggestStep = Math.max(biggestStep, Math.abs(tremor.getPitch(1f) - previousPitch));
                biggestStep = Math.max(biggestStep, Math.abs(tremor.getYaw(1f) - previousYaw));
            }
            previousPitch = tremor.getPitch(1f);
            previousYaw = tremor.getYaw(1f);
        }
        // Fastest term is breathing at 2*PI/80 = 0.0785 rad/tick weighted 0.7, so a smooth signal
        // steps by under 0.1 per tick. A phase tear steps by a large share of the full amplitude.
        assertTrue(biggestStep < 0.1f, "sway must not jump between ticks, biggest step was " + biggestStep);
    }

    /** Breathing, not aimless wander, is what the vertical motion should read as. */
    @Test
    void breathingDominatesTheVerticalAndOutpacesTheHorizontal() {
        TremorSource tremor = new TremorSource();
        float pitchPeak = 0f;
        float yawPeak = 0f;
        int pitchZeroCrossings = 0;
        float previousPitch = 0f;
        for (int i = 0; i < 800; i++) { // 10 breath cycles
            tremor.update(1f, 1f, 1f);
            float pitch = tremor.getPitch(1f);
            if (i > 0 && Math.signum(pitch) != Math.signum(previousPitch)) {
                pitchZeroCrossings++;
            }
            previousPitch = pitch;
            pitchPeak = Math.max(pitchPeak, Math.abs(pitch));
            yawPeak = Math.max(yawPeak, Math.abs(tremor.getYaw(1f)));
        }
        assertTrue(pitchPeak > yawPeak, "lungs move the barrel up and down, not sideways");
        // A breath-led signal crosses zero about twice per cycle; formless drift would not.
        assertTrue(pitchZeroCrossings >= 15 && pitchZeroCrossings <= 25,
                "vertical motion must follow the breath cycle, crossings: " + pitchZeroCrossings);
    }

    @Test
    void breathHoldSuppressesTheSway() {
        TremorSource steady = new TremorSource();
        TremorSource held = new TremorSource();
        float steadyPeak = 0f;
        float heldPeak = 0f;
        for (int i = 0; i < 400; i++) {
            steady.update(1f, 1f, 1f);
            held.update(1f, 1f, 0.15f);
            steadyPeak = Math.max(steadyPeak, Math.abs(steady.getPitch(1f)));
            heldPeak = Math.max(heldPeak, Math.abs(held.getPitch(1f)));
        }
        assertTrue(steadyPeak > 0.1f, "unsteadied sway must be visible, peak was " + steadyPeak);
        assertEquals(steadyPeak * 0.15f, heldPeak, 1e-4, "holding breath must scale the sway, not reshape it");
    }

    @Test
    void noSwayWhenNotAiming() {
        TremorSource tremor = new TremorSource();
        for (int i = 0; i < 50; i++) {
            tremor.update(0f, 1f, 1f);
        }
        assertEquals(0f, tremor.getPitch(1f), 1e-6);
        assertEquals(0f, tremor.getYaw(1f), 1e-6);
    }
}

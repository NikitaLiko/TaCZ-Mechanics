package ru.liko.tacz_mechanics.client.freeaim;

import net.minecraft.util.Mth;
import ru.liko.tacz_mechanics.Config;

/**
 * Weapon sway while aiming down sights. The barrel rides a slow breathing cycle - a steady rise and
 * fall the shooter cannot switch off - with an aimless wander drifting around it. Amplitude ramps up
 * the longer ADS is held (arm fatigue), grows with scope magnification, and is scaled by
 * {@link BreathHold}.
 *
 * <p>Produces a direct angular offset in degrees rather than spring impulses: the amplitude has to
 * be stated in degrees so a scope's magnification can be reasoned about, and a spring would only
 * smear an already-smooth signal. Stateful: tracks hold time and independent oscillator phases.
 */
public final class TremorSource {

    private static final float TWO_PI = (float) (2 * Math.PI);

    /**
     * Slow wander speeds, non-harmonic so the path never repeats. Every oscillator gets its own
     * accumulator on purpose: scaling an already-wrapped phase (sin(phase * 1.7f)) jumped the sine's
     * argument by a non-multiple of 2*PI on every wrap, tearing a step into the sway every few
     * seconds - which is exactly the twitch a scope magnifies.
     */
    private static final float[] DRIFT_SPEEDS = {0.045f, 0.11f, 0.065f, 0.136f};
    /** Fixed offsets so the four oscillators do not all start at the same point. */
    private static final float[] DRIFT_OFFSETS = {0f, 1.3f, 0f, 2.1f};

    /** Share of the vertical motion that comes from breathing rather than aimless wander. */
    private static final float BREATH_PITCH_WEIGHT = 0.7f;
    /** Lungs drive the barrel up and down, so the horizontal wander is the smaller of the two. */
    private static final float YAW_WEIGHT = 0.6f;

    private float holdTicks = 0f;
    private float breathPhase = 0f;
    private final float[] drift = DRIFT_OFFSETS.clone();

    private float pitch = 0f;
    private float yaw = 0f;
    private float prevPitch = 0f;
    private float prevYaw = 0f;

    /**
     * @param aimingProgress 0..1 ADS blend
     * @param zoom           the gun's aiming magnification (1 = none)
     * @param steadiness     sway multiplier from {@link BreathHold}
     */
    public void update(float aimingProgress, float zoom, float steadiness) {
        prevPitch = pitch;
        prevYaw = yaw;

        if (!Config.FreeAim.tremorEnabled || aimingProgress <= 0.01f) {
            // Fatigue fades faster than it builds once the player stops aiming.
            holdTicks = Math.max(0f, holdTicks - 2f);
            pitch = 0f;
            yaw = 0f;
            return;
        }

        float buildupTicks = (float) Config.FreeAim.tremorBuildupTicks;
        holdTicks = Math.min(holdTicks + 1f, buildupTicks);
        float fatigue = buildupTicks > 0f ? holdTicks / buildupTicks : 1f;

        // A scope does not shake the hand more, but players expect a high-power optic to be harder
        // to hold steady than iron sights, on top of the magnification the optic already applies.
        float zoomFactor = 1f + Math.max(0f, zoom - 1f) * (float) Config.FreeAim.tremorZoomScale;

        // Sway is present as soon as ADS starts, then grows toward full strength.
        float amp = (float) Config.FreeAim.tremorScale
                * aimingProgress
                * (0.3f + 0.7f * fatigue)
                * zoomFactor
                * Math.max(0f, steadiness);

        float breathTicks = Math.max(1f, Config.FreeAim.tremorBreathTicks);
        breathPhase = wrap(breathPhase + TWO_PI / breathTicks);
        float breathing = Mth.sin(breathPhase);

        for (int i = 0; i < drift.length; i++) {
            drift[i] = wrap(drift[i] + DRIFT_SPEEDS[i]);
        }
        // Sum of two non-harmonic sines per axis: cheap organic wander, no external noise lib needed.
        float driftPitch = (Mth.sin(drift[0]) + 0.5f * Mth.sin(drift[1])) / 1.5f;
        float driftYaw = (Mth.cos(drift[2]) + 0.5f * Mth.sin(drift[3])) / 1.5f;

        pitch = (breathing * BREATH_PITCH_WEIGHT + driftPitch * (1f - BREATH_PITCH_WEIGHT)) * amp;
        yaw = driftYaw * amp * YAW_WEIGHT;
    }

    /** Sway offset in degrees, interpolated for smooth rendering between ticks. */
    public float getPitch(float pt) {
        return prevPitch + (pitch - prevPitch) * pt;
    }

    public float getYaw(float pt) {
        return prevYaw + (yaw - prevYaw) * pt;
    }

    private static float wrap(float phase) {
        return phase > TWO_PI ? phase - TWO_PI : phase;
    }

    public void reset() {
        holdTicks = 0f;
        breathPhase = 0f;
        System.arraycopy(DRIFT_OFFSETS, 0, drift, 0, drift.length);
        pitch = 0f;
        yaw = 0f;
        prevPitch = 0f;
        prevYaw = 0f;
    }
}

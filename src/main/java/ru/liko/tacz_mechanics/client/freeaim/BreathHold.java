package ru.liko.tacz_mechanics.client.freeaim;

/**
 * Hold-your-breath stamina for aimed shots: while the player holds the key the tremor is
 * suppressed, but the air runs out, and running it dry costs a spell of worse-than-normal shake.
 * No Minecraft dependencies (unit-testable).
 */
public final class BreathHold {

    /** Fraction of capacity that must refill before the exhaustion penalty lifts. */
    private static final float RECOVERY_THRESHOLD = 0.5f;

    private float capacityTicks = 100f;
    private float recoverTicks = 160f;
    private float steadyFactor = 0.15f;
    private float exhaustedFactor = 1.8f;
    private float settleTicks = 8f;

    private float breath = capacityTicks;
    private float steadiness = 1f;
    private boolean exhausted;

    public void setParams(float capacityTicks, float recoverTicks, float steadyFactor,
                          float exhaustedFactor, float settleTicks) {
        // A shrinking capacity must not leave a stale larger reserve behind.
        if (capacityTicks < this.capacityTicks) {
            breath = Math.min(breath, capacityTicks);
        }
        this.capacityTicks = Math.max(1f, capacityTicks);
        this.recoverTicks = Math.max(1f, recoverTicks);
        this.steadyFactor = steadyFactor;
        this.exhaustedFactor = exhaustedFactor;
        this.settleTicks = Math.max(1f, settleTicks);
    }

    /** @param holdRequested the player is aiming and holding the breath-hold key */
    public void tick(boolean holdRequested) {
        float target;
        if (holdRequested && !exhausted && breath > 0f) {
            breath -= 1f;
            if (breath <= 0f) {
                breath = 0f;
                exhausted = true;
            }
            target = steadyFactor;
        } else {
            breath = Math.min(capacityTicks, breath + capacityTicks / recoverTicks);
            if (exhausted && breath >= capacityTicks * RECOVERY_THRESHOLD) {
                exhausted = false;
            }
            target = exhausted ? exhaustedFactor : 1f;
        }
        // Settling into (and out of) a held breath is gradual: an instant snap reads as a glitch.
        steadiness += (target - steadiness) / settleTicks;
    }

    /** Multiplier applied to the tremor amplitude. */
    public float getSteadiness() {
        return steadiness;
    }

    /** 0..1 air remaining, for HUD feedback. */
    public float getBreathFraction() {
        return breath / capacityTicks;
    }

    public boolean isExhausted() {
        return exhausted;
    }

    public void reset() {
        breath = capacityTicks;
        steadiness = 1f;
        exhausted = false;
    }
}

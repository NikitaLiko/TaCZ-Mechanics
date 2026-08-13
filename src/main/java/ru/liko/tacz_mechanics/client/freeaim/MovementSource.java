package ru.liko.tacz_mechanics.client.freeaim;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import ru.liko.tacz_mechanics.Config;

/**
 * Produces walk/sprint bob and jump/land kicks as spring impulses.
 * Stateful: tracks bob phase and previous ground state.
 */
public final class MovementSource {

    // Converts dimensionless bob amplitude into spring velocity units. Scaled down together with
    // BOB_PHASE_RATE so the slower bob keeps roughly the same visible amplitude.
    private static final float BOB_TICK_SCALE = 0.02f;
    private static final float TWO_PI = (float) (2 * Math.PI);
    /**
     * Phase advance per block moved. At 20 ticks/s the pitch term runs at twice this, so the old
     * rate of 8 put a walk at ~3.5 ticks per cycle and a sprint past the Nyquist limit outright:
     * the impulse flipped sign almost every tick and aliased into twitching instead of bobbing.
     */
    private static final float BOB_PHASE_RATE = 3f;
    /** Speed above this is a teleport or knockback, not a stride. */
    private static final float MAX_STRIDE_SPEED = 0.5f;

    private float bobPhase = 0f;
    private float smoothedSpeed = 0f;
    private boolean wasOnGround = true;

    public void apply(LocalPlayer player, SwaySpring pitchSpring, SwaySpring yawSpring) {
        if (!Config.FreeAim.movementEnabled) {
            return;
        }

        // Observe this tick's ground state once (shared by bob and jump/land logic)
        boolean onGround = player.onGround();

        // Horizontal speed (blocks/tick) from this tick's movement
        double dx = player.getX() - player.xo;
        double dz = player.getZ() - player.zo;
        float speed = Math.min((float) Math.sqrt(dx * dx + dz * dz), MAX_STRIDE_SPEED);
        // Per-tick speed is noisy (steps, collisions, slabs); the stride behind it is not.
        smoothedSpeed += (speed - smoothedSpeed) * 0.3f;
        speed = smoothedSpeed;

        // Walk/sprint bob: advance phase by speed, emit a gentle figure-eight sway
        if (speed > 0.005f && onGround) {
            float amp = player.isSprinting()
                    ? (float) Config.FreeAim.movementSprintScale
                    : (float) Config.FreeAim.movementWalkScale;
            bobPhase += speed * BOB_PHASE_RATE;
            // Keep phase bounded to avoid float precision loss over long sessions
            if (bobPhase > TWO_PI) {
                bobPhase %= TWO_PI;
            }
            // Vertical bob twice per horizontal cycle, horizontal once
            pitchSpring.addImpulse(Mth.sin(bobPhase * 2f) * amp * BOB_TICK_SCALE);
            yawSpring.addImpulse(Mth.cos(bobPhase) * amp * BOB_TICK_SCALE);
        }

        // Jump / land kick
        if (wasOnGround && !onGround) {
            // Just left ground (jumped): barrel dips
            pitchSpring.addImpulse(-(float) Config.FreeAim.movementJumpScale);
        } else if (!wasOnGround && onGround) {
            // Just landed: barrel kicks up
            pitchSpring.addImpulse((float) Config.FreeAim.movementJumpScale);
        }
        wasOnGround = onGround;
    }

    public void reset() {
        bobPhase = 0f;
        smoothedSpeed = 0f;
        wasOnGround = true;
    }
}

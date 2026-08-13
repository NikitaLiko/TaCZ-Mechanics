package ru.liko.tacz_mechanics.movement;

import ru.liko.tacz_mechanics.Config;

import java.util.concurrent.TimeUnit;

/**
 * Represents the movement state of a player.
 * Tracks the posture (sit / prone) and the leaning (probe) state.
 *
 * <p>Encoded to a single int for the network exactly like ModularMovements:
 * {@code sit*100 + prone*10 + (probe+1)}. Posture and lean are mutually exclusive — entering a
 * posture zeroes the probe — so the render/hitbox chain never has to compose a prone roll with a
 * lean roll.
 */
public class PlayerState {
    private byte probe = 0;
    private float probeOffset = 0;
    private float probeOffsetOld = 0;
    private long lastSyncTime = System.currentTimeMillis();

    private boolean sitting = false;
    private boolean prone = false;
    // Single timestamp for ANY posture transition (enter sit, enter prone, stand up).
    // Per-posture timers let players alternate лег/сел faster than either cooldown.
    private long lastPostureChange;

    public byte getProbe() { return probe; }
    public float getProbeOffset() { return probeOffset; }
    public float getProbeOffsetOld() { return probeOffsetOld; }

    public boolean isSitting() { return sitting; }
    public boolean isProne() { return prone; }
    public boolean isStanding() { return !sitting && !prone; }

    private long lastProbe;

    public void updateOffset() {
        probeOffsetOld = probeOffset;
        double amplifier = (System.currentTimeMillis() - lastSyncTime) * (60 / 1000d);
        lastSyncTime = System.currentTimeMillis();

        // Slower speed for more realistic leaning (0.05 instead of 0.1)
        float speed = 0.05f;

        if (probe == -1) {
            if (probeOffset > -1) {
                probeOffset -= speed * amplifier;
            }
            if (probeOffset < -1) {
                probeOffset = -1;
            }
        }
        if (probe == 1) {
            if (probeOffset < 1) {
                probeOffset += speed * amplifier;
            }
            if (probeOffset > 1) {
                probeOffset = 1;
            }
        }

        if (probe == 0) {
            if (Math.abs(probeOffset) <= speed * amplifier) {
                probeOffset = 0;
            }
            if (probeOffset < 0) {
                probeOffset += speed * amplifier;
            }
            if (probeOffset > 0) {
                probeOffset -= speed * amplifier;
            }
        }
    }

    /**
     * Ограничивает анимированный наклон по максимальной величине в каждую сторону (из коллизии).
     */
    public void clampProbeOffset(float maxLeftMag, float maxRightMag) {
        if (probeOffset < 0) {
            if (probeOffset < -maxLeftMag) {
                probeOffset = -maxLeftMag;
            }
        } else if (probeOffset > 0) {
            if (probeOffset > maxRightMag) {
                probeOffset = maxRightMag;
            }
        }
    }

    public boolean canProbe() {
        return (System.currentTimeMillis() - this.lastProbe > TimeUnit.MILLISECONDS.toMillis((long)(Config.Movement.leanCooldown * 1000)));
    }

    public void resetProbe() {
        probe = 0;
    }

    public void leftProbe() {
        probe = -1;
        this.lastProbe = System.currentTimeMillis();
    }

    public void rightProbe() {
        probe = 1;
        this.lastProbe = System.currentTimeMillis();
    }

    // --- posture -----------------------------------------------------------

    public boolean canSit() {
        return sincePostureChange() > (long) (Config.Movement.sitCooldown * 1000);
    }

    public boolean canProne() {
        return sincePostureChange() > (long) (Config.Movement.proneCooldown * 1000);
    }

    /** Millis since the last posture transition of any kind. */
    public long sincePostureChange() {
        return System.currentTimeMillis() - lastPostureChange;
    }

    /** Entering a posture cancels the other posture and any lean. */
    public void enableSit() {
        sitting = true;
        prone = false;
        probe = 0;
        lastPostureChange = System.currentTimeMillis();
    }

    public void enableProne() {
        prone = true;
        sitting = false;
        probe = 0;
        lastPostureChange = System.currentTimeMillis();
    }

    public void standUp() {
        sitting = false;
        prone = false;
        lastPostureChange = System.currentTimeMillis();
    }

    public void readCode(int code) {
        probe = (byte) (code % 10 - 1);
        code /= 10;
        boolean newProne = code % 10 != 0;
        code /= 10;
        boolean newSitting = code % 10 != 0;
        if (newProne != prone || newSitting != sitting) {
            lastPostureChange = System.currentTimeMillis();
        }
        prone = newProne;
        sitting = newSitting;
    }

    public void reset() {
        readCode(1);
    }

    public int writeCode() {
        return (sitting ? 1 : 0) * 100 + (prone ? 1 : 0) * 10 + (probe + 1);
    }
}

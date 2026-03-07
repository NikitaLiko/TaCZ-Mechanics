package ru.liko.tacz_mechanics.client.suppression;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import ru.liko.tacz_mechanics.Config;

/**
 * Client-side suppression state manager.
 * Tracks the current suppression level and applies camera kick (aim punch)
 * when bullets fly near or impact nearby - like Squad's suppression effect.
 */
public class SuppressionHandler {
    private static float currentLevel = 0.0f;
    private static float prevLevel = 0.0f;

    // Camera kick offsets (aim punch)
    private static float kickYaw = 0.0f;
    private static float kickPitch = 0.0f;
    private static float prevKickYaw = 0.0f;
    private static float prevKickPitch = 0.0f;

    private static final RandomSource RANDOM = RandomSource.create();

    public static void addSuppression(float intensity) {
        float max = (float) Config.Suppression.maxIntensity;
        currentLevel = Math.min(max, currentLevel + intensity);

        // Apply camera kick (aim punch) in random direction
        if (intensity > 0.01f) {
            applyCameraKick(intensity);
        }
    }

    private static void applyCameraKick(float intensity) {
        float shakeBase = (float) Config.Suppression.shakeIntensity;

        // Random direction for kick
        float angle = RANDOM.nextFloat() * Mth.TWO_PI;

        // Kick magnitude based on intensity - more suppression = bigger kick
        float kickMagnitude = intensity * shakeBase * 3.0f; // Max ~3 degrees per hit

        // Add to existing kick (cumulative for rapid fire)
        kickYaw += Mth.cos(angle) * kickMagnitude;
        kickPitch += Mth.sin(angle) * kickMagnitude * 0.6f; // Less vertical kick

        // Clamp to prevent excessive rotation
        float maxKick = 8.0f * shakeBase; // Max kick angle
        kickYaw = Mth.clamp(kickYaw, -maxKick, maxKick);
        kickPitch = Mth.clamp(kickPitch, -maxKick * 0.7f, maxKick * 0.7f);
    }

    public static void tick() {
        prevLevel = currentLevel;
        prevKickYaw = kickYaw;
        prevKickPitch = kickPitch;

        if (currentLevel > 0.0f) {
            // Exponential decay for lingering suppression effect
            float decayFactor = (float) Config.Suppression.decayRate;
            float adjustedDecay = decayFactor * (0.5f + 0.5f * (1.0f - currentLevel));
            currentLevel = Math.max(0.0f, currentLevel * (1.0f - adjustedDecay));
        }

        // When suppression ends, reset kick immediately
        if (currentLevel <= 0.001f && (kickYaw != 0.0f || kickPitch != 0.0f)) {
            kickYaw = 0.0f;
            kickPitch = 0.0f;
        }
    }

    /**
     * Returns interpolated suppression level for shader effects.
     */
    public static float getLevel(float partialTick) {
        return Mth.lerp(partialTick, prevLevel, currentLevel);
    }

    public static float getRawLevel() {
        return currentLevel;
    }

    /**
     * Returns camera kick Yaw offset (aim punch horizontal).
     */
    public static float getKickYaw(float partialTick) {
        return Mth.lerp(partialTick, prevKickYaw, kickYaw);
    }

    /**
     * Returns camera kick Pitch offset (aim punch vertical).
     */
    public static float getKickPitch(float partialTick) {
        return Mth.lerp(partialTick, prevKickPitch, kickPitch);
    }

    public static boolean isActive() {
        return currentLevel > 0.001f || prevLevel > 0.001f;
    }

    public static void reset() {
        currentLevel = 0.0f;
        prevLevel = 0.0f;
        kickYaw = 0.0f;
        kickPitch = 0.0f;
        prevKickYaw = 0.0f;
        prevKickPitch = 0.0f;
    }
}

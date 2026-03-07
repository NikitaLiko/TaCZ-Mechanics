package ru.liko.tacz_mechanics.client.sound;

import net.minecraft.client.resources.sounds.SoundInstance;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;

/**
 * Utility class for applying OpenAL low-pass filters to sounds.
 * Creates a muffled/distant effect by reducing high frequencies.
 */
public class SoundFilterUtil {
    
    /**
     * Apply a low-pass filter to a sound instance.
     * 
     * @param instance The sound instance to filter
     * @param muffleAmount How much to muffle (0.0 = none, 1.0 = maximum)
     * @return true if filter was applied successfully
     */
    public static boolean applyLowPassFilter(SoundInstance instance, float muffleAmount) {
        int source = SoundSourceTracker.get(instance);
        if (source == -1) {
            return false;
        }

        // Check if source is valid and playing
        int sourceState = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
        if (sourceState != AL10.AL_PLAYING && sourceState != AL10.AL_PAUSED) {
            return false;
        }

        try {
            // Create low-pass filter
            int filter = EXTEfx.alGenFilters();
            if (filter == 0) {
                return false;
            }

            EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);

            // Calculate filter parameters based on muffle amount
            // Higher muffle = lower gain and gainHF
            float gain = Math.max(0.1f, 1.0f - muffleAmount * 0.7f);
            float gainHF = Math.max(0.05f, 1.0f - muffleAmount * 0.95f);

            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, gain);
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, gainHF);

            // Apply the filter to the source
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);

            // Check for OpenAL errors
            int error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                EXTEfx.alDeleteFilters(filter);
                return false;
            }

            return true;

        } catch (Exception e) {
            System.err.println("[SoundFilter] Exception while applying filter: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Calculate muffle amount based on distance.
     * 
     * @param distance Distance in blocks
     * @param nearDistance Distance where muffle starts (full clarity below)
     * @param farDistance Distance where muffle is maximum
     * @return Muffle amount (0.0 - 1.0)
     */
    public static float calculateMuffleFromDistance(double distance, double nearDistance, double farDistance) {
        if (distance <= nearDistance) {
            return 0.0f;
        }
        if (distance >= farDistance) {
            return 1.0f;
        }
        return (float) ((distance - nearDistance) / (farDistance - nearDistance));
    }
}

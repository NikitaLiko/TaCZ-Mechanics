package ru.liko.tacz_mechanics.data.distant_fire;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Configuration for distant fire sounds for a specific caliber/ammo type.
 * 
 * Distance levels:
 * - TACZ default: 0 to taczRange (handled by TACZ itself)
 * - Close: taczRange to closeMaxDistance
 * - Mid: closeMaxDistance to midMaxDistance  
 * - Far: midMaxDistance to farMaxDistance
 * - VeryFar: farMaxDistance+ (optional, can merge with Far)
 */
public record DistantFireSound(
    String caliberId,
    ResourceLocation closeSound,
    ResourceLocation midSound,
    ResourceLocation farSound,
    Optional<ResourceLocation> veryFarSound,
    int closeMaxDistance,
    int midMaxDistance,
    int farMaxDistance,
    float closeVolume,
    float midVolume,
    float farVolume,
    float veryFarVolume,
    int transitionBlocks
) {
    public static final Codec<DistantFireSound> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("caliber_id").forGetter(DistantFireSound::caliberId),
        ResourceLocation.CODEC.fieldOf("close_sound").forGetter(DistantFireSound::closeSound),
        ResourceLocation.CODEC.fieldOf("mid_sound").forGetter(DistantFireSound::midSound),
        ResourceLocation.CODEC.fieldOf("far_sound").forGetter(DistantFireSound::farSound),
        ResourceLocation.CODEC.optionalFieldOf("very_far_sound").forGetter(DistantFireSound::veryFarSound),
        Codec.INT.optionalFieldOf("close_max_distance", 100).forGetter(DistantFireSound::closeMaxDistance),
        Codec.INT.optionalFieldOf("mid_max_distance", 200).forGetter(DistantFireSound::midMaxDistance),
        Codec.INT.optionalFieldOf("far_max_distance", 400).forGetter(DistantFireSound::farMaxDistance),
        Codec.FLOAT.optionalFieldOf("close_volume", 0.9f).forGetter(DistantFireSound::closeVolume),
        Codec.FLOAT.optionalFieldOf("mid_volume", 0.7f).forGetter(DistantFireSound::midVolume),
        Codec.FLOAT.optionalFieldOf("far_volume", 0.5f).forGetter(DistantFireSound::farVolume),
        Codec.FLOAT.optionalFieldOf("very_far_volume", 0.3f).forGetter(DistantFireSound::veryFarVolume),
        Codec.INT.optionalFieldOf("transition_blocks", 20).forGetter(DistantFireSound::transitionBlocks)
    ).apply(instance, DistantFireSound::new));
    
    /**
     * Get the sound level for a given distance.
     */
    public SoundLevel getSoundLevel(double distance, int taczRange) {
        if (distance <= taczRange) {
            return SoundLevel.TACZ_DEFAULT;
        } else if (distance <= closeMaxDistance) {
            return SoundLevel.CLOSE;
        } else if (distance <= midMaxDistance) {
            return SoundLevel.MID;
        } else if (distance <= farMaxDistance) {
            return SoundLevel.FAR;
        } else if (veryFarSound.isPresent()) {
            return SoundLevel.VERY_FAR;
        } else {
            return SoundLevel.FAR;
        }
    }
    
    /**
     * Get the sound to play for a given distance.
     */
    public ResourceLocation getSoundForDistance(double distance, int taczRange) {
        SoundLevel level = getSoundLevel(distance, taczRange);
        return switch (level) {
            case TACZ_DEFAULT -> null; // Let TACZ handle it
            case CLOSE -> closeSound;
            case MID -> midSound;
            case FAR -> farSound;
            case VERY_FAR -> veryFarSound.orElse(farSound);
        };
    }
    
    /**
     * Get the base volume for a given distance.
     */
    public float getBaseVolumeForDistance(double distance, int taczRange) {
        SoundLevel level = getSoundLevel(distance, taczRange);
        return switch (level) {
            case TACZ_DEFAULT -> 1.0f;
            case CLOSE -> closeVolume;
            case MID -> midVolume;
            case FAR -> farVolume;
            case VERY_FAR -> veryFarVolume;
        };
    }
    
    /**
     * Calculate crossfade volumes for smooth transitions.
     * Returns [currentVolume, nextVolume] where nextVolume > 0 during transition.
     */
    public float[] getCrossfadeVolumes(double distance, int taczRange) {
        // Check transitions between levels
        float currentVol = 1.0f;
        float nextVol = 0.0f;
        
        // Transition: TACZ -> Close
        if (distance > taczRange - transitionBlocks && distance <= taczRange + transitionBlocks) {
            float t = (float)(distance - (taczRange - transitionBlocks)) / (transitionBlocks * 2);
            currentVol = 1.0f - t;
            nextVol = t * closeVolume;
        }
        // Transition: Close -> Mid
        else if (distance > closeMaxDistance - transitionBlocks && distance <= closeMaxDistance + transitionBlocks) {
            float t = (float)(distance - (closeMaxDistance - transitionBlocks)) / (transitionBlocks * 2);
            currentVol = closeVolume * (1.0f - t);
            nextVol = midVolume * t;
        }
        // Transition: Mid -> Far
        else if (distance > midMaxDistance - transitionBlocks && distance <= midMaxDistance + transitionBlocks) {
            float t = (float)(distance - (midMaxDistance - transitionBlocks)) / (transitionBlocks * 2);
            currentVol = midVolume * (1.0f - t);
            nextVol = farVolume * t;
        }
        // Transition: Far -> VeryFar (if exists)
        else if (veryFarSound.isPresent() && distance > farMaxDistance - transitionBlocks && distance <= farMaxDistance + transitionBlocks) {
            float t = (float)(distance - (farMaxDistance - transitionBlocks)) / (transitionBlocks * 2);
            currentVol = farVolume * (1.0f - t);
            nextVol = veryFarVolume * t;
        }
        // No transition, just the level volume
        else {
            currentVol = getBaseVolumeForDistance(distance, taczRange);
            nextVol = 0.0f;
        }
        
        return new float[]{Math.max(0, currentVol), Math.max(0, nextVol)};
    }
    
    /**
     * Get next sound for crossfade (during transition).
     */
    public ResourceLocation getNextSoundForCrossfade(double distance, int taczRange) {
        // Transition: TACZ -> Close
        if (distance > taczRange - transitionBlocks && distance <= taczRange + transitionBlocks) {
            return closeSound;
        }
        // Transition: Close -> Mid
        else if (distance > closeMaxDistance - transitionBlocks && distance <= closeMaxDistance + transitionBlocks) {
            return midSound;
        }
        // Transition: Mid -> Far
        else if (distance > midMaxDistance - transitionBlocks && distance <= midMaxDistance + transitionBlocks) {
            return farSound;
        }
        // Transition: Far -> VeryFar
        else if (veryFarSound.isPresent() && distance > farMaxDistance - transitionBlocks && distance <= farMaxDistance + transitionBlocks) {
            return veryFarSound.get();
        }
        return null;
    }
    
    public enum SoundLevel {
        TACZ_DEFAULT,
        CLOSE,
        MID,
        FAR,
        VERY_FAR
    }
}

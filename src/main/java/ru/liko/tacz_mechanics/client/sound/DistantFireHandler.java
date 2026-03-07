package ru.liko.tacz_mechanics.client.sound;

import com.tacz.guns.client.sound.GunSoundInstance;
import com.tacz.guns.init.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Handles distant fire sound effects.
 * Applies low-pass filtering based on distance from the player.
 */
public class DistantFireHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // Distance thresholds (in blocks)
    private static final double NEAR_DISTANCE = 30.0;   // No muffle below this
    private static final double FAR_DISTANCE = 300.0;   // Maximum muffle above this
    
    /**
     * Handle a gun sound and optionally apply distance filtering.
     * 
     * @return A filtered GunSoundInstance, or null to let TACZ handle it normally
     */
    @Nullable
    public static GunSoundInstance handleGunSound(
            Entity entity, 
            ResourceLocation soundName, 
            float volume, 
            float pitch, 
            int attenuationDistance, 
            boolean mono,
            double distanceToPlayer) {
        
        LOGGER.debug("[DistantFire] handleGunSound called: sound={}, distance={}", soundName, distanceToPlayer);
        
        // Calculate muffle amount based on distance
        float muffleAmount = SoundFilterUtil.calculateMuffleFromDistance(
            distanceToPlayer, NEAR_DISTANCE, FAR_DISTANCE
        );
        
        LOGGER.debug("[DistantFire] Calculated muffleAmount: {}", muffleAmount);
        
        // If no muffle needed, let TACZ handle it normally
        if (muffleAmount <= 0.01f) {
            LOGGER.debug("[DistantFire] No muffle needed, returning null");
            return null;
        }
        
        // Create the gun sound instance normally
        GunSoundInstance soundInstance = new GunSoundInstance(
            ModSounds.GUN.get(),
            SoundSource.PLAYERS,
            volume,
            pitch,
            entity,
            attenuationDistance,
            soundName,
            mono
        );
        
        LOGGER.debug("[DistantFire] Created GunSoundInstance with muffle={}", muffleAmount);
        
        // Register for filter application
        SoundFilterRegistry.register(soundInstance, muffleAmount);
        
        // Play the sound
        Minecraft.getInstance().getSoundManager().play(soundInstance);
        
        LOGGER.debug("[DistantFire] Sound played and registered for filtering");
        
        return soundInstance;
    }
}

package ru.liko.tacz_mechanics.client.sound;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.data.distant_fire.DistantFireSound;
import ru.liko.tacz_mechanics.data.distant_fire.DistantFireSoundManager;
import ru.liko.tacz_mechanics.network.DistantFireSoundPacket;

/**
 * Client-side handler for distant fire sounds.
 * Receives packets from server and plays sounds based on distance with smooth transitions.
 * 
 * Sound levels:
 * - TACZ default (0 - taczRange): Handled by TACZ
 * - Close (taczRange - closeMax): Close distant sound
 * - Mid (closeMax - midMax): Medium distant sound
 * - Far (midMax - farMax): Far distant sound
 * - VeryFar (farMax+): Very far sound (optional)
 */
@OnlyIn(Dist.CLIENT)
public class DistantFireClientHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // Default TACZ sound range (TACZ default is 32 blocks in config)
    private static final int TACZ_RANGE = 32;
    
    public static void handleDistantFireSound(DistantFireSoundPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        
        if (!Config.DistantFire.enabled) return;
        
        Vec3 playerPos = mc.player.position();
        Vec3 soundPos = new Vec3(packet.x(), packet.y(), packet.z());
        double distance = playerPos.distanceTo(soundPos);
        
        LOGGER.debug("[DistantFire] Received packet: caliber={}, distance={}", packet.caliberId(), distance);
        
        // Skip if within TACZ's normal range (TACZ handles it)
        if (distance < TACZ_RANGE) {
            LOGGER.debug("[DistantFire] Within TACZ range, skipping");
            return;
        }
        
        // Get config for this caliber
        DistantFireSound config = DistantFireSoundManager.INSTANCE.getConfigForCaliber(packet.caliberId());
        
        // Get sound level and calculate volumes for crossfade
        DistantFireSound.SoundLevel level = config.getSoundLevel(distance, TACZ_RANGE);
        float[] crossfadeVolumes = config.getCrossfadeVolumes(distance, TACZ_RANGE);
        
        LOGGER.debug("[DistantFire] Level: {}, Crossfade volumes: [{}, {}]", level, crossfadeVolumes[0], crossfadeVolumes[1]);
        
        // Play main sound for current level
        ResourceLocation mainSoundLoc = config.getSoundForDistance(distance, TACZ_RANGE);
        if (mainSoundLoc != null && crossfadeVolumes[0] > 0.01f) {
            float mainVolume = crossfadeVolumes[0] * packet.volume() * (float) Config.DistantFire.volumeMultiplier;
            playSound(mc, mainSoundLoc, packet.x(), packet.y(), packet.z(), mainVolume, packet.pitch());
        }
        
        // Play transition sound if in crossfade zone
        if (crossfadeVolumes[1] > 0.01f) {
            ResourceLocation nextSoundLoc = config.getNextSoundForCrossfade(distance, TACZ_RANGE);
            if (nextSoundLoc != null) {
                float nextVolume = crossfadeVolumes[1] * packet.volume() * (float) Config.DistantFire.volumeMultiplier;
                playSound(mc, nextSoundLoc, packet.x(), packet.y(), packet.z(), nextVolume, packet.pitch());
                LOGGER.debug("[DistantFire] Playing crossfade sound: {} at volume {}", nextSoundLoc, nextVolume);
            }
        }
    }
    
    private static void playSound(Minecraft mc, ResourceLocation soundLoc, double x, double y, double z, 
                                   float volume, float pitch) {
        SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.get(soundLoc);
        
        if (soundEvent == null) {
            // Create sound event dynamically
            soundEvent = SoundEvent.createVariableRangeEvent(soundLoc);
        }
        
        // Use FilteredSoundInstance for positional sound
        FilteredSoundInstance soundInstance = new FilteredSoundInstance(
            soundEvent,
            SoundSource.PLAYERS,
            volume,
            pitch,
            x, y, z,
            0.0f // No muffling - just positional distant sound
        );
        
        mc.getSoundManager().play(soundInstance);
        LOGGER.debug("[DistantFire] Played sound: {} at volume {}", soundLoc, volume);
    }
}

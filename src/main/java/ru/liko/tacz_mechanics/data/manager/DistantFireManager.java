package ru.liko.tacz_mechanics.data.manager;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import ru.liko.tacz_mechanics.data.DistantFire;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manager for distant fire sounds.
 * Handles delayed sound playback based on distance.
 */
public class DistantFireManager extends SimpleJsonResourceReloadListener {
    
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();
    
    private static final Comparator<DistantFire> COMPARATOR = Comparator
        .comparingInt(DistantFire::priority).reversed()
        .thenComparing(df -> df.ammoTypes().isEmpty() ? 1 : 0);
    
    public static final DistantFireManager INSTANCE = new DistantFireManager();
    
    private final Logger logger = LogUtils.getLogger();
    private Map<ResourceLocation, DistantFire> dataMap = Map.of();
    private List<DistantFire> sortedData = List.of();
    
    private final Queue<DelayedSound> delayedSounds = new ConcurrentLinkedQueue<>();
    
    private DistantFireManager() {
        super(GSON, "distant_fire");
    }
    
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> elements, ResourceManager resourceManager, ProfilerFiller profiler) {
        ImmutableMap.Builder<ResourceLocation, DistantFire> builder = ImmutableMap.builder();
        
        for (var entry : elements.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement json = entry.getValue();
            
            try {
                DistantFire distantFire = DistantFire.CODEC.parse(JsonOps.INSTANCE, json)
                    .getOrThrow(error -> new RuntimeException("Failed to parse " + id + ": " + error));
                
                builder.put(id, distantFire);
                logger.debug("Loaded distant fire config: {}", id);
            } catch (RuntimeException e) {
                logger.error("Parsing error loading {}", id, e);
            }
        }
        
        dataMap = builder.build();
        
        // Sort by priority for matching
        sortedData = dataMap.values().stream()
            .sorted(COMPARATOR)
            .toList();
        
        logger.info("Loaded {} distant fire configs", dataMap.size());
    }
    
    /**
     * Handle a gun fire event - schedule distant sounds for all players in range.
     */
    public void handleGunFire(ServerLevel level, Vec3 shooterPos, ResourceLocation ammoId) {
        // Find matching config
        DistantFire config = findConfig(ammoId);
        if (config == null) {
            return;
        }
        
        // For each player, calculate distance and schedule appropriate sound
        for (ServerPlayer player : level.players()) {
            double distance = player.position().distanceTo(shooterPos);
            
            config.getLayerForDistance(distance).ifPresent(layer -> {
                int delayTicks = layer.calculateDelayTicks(distance);
                
                if (delayTicks <= 0) {
                    // Play immediately
                    playSound(level, player, shooterPos, layer);
                } else {
                    // Schedule delayed playback
                    delayedSounds.add(new DelayedSound(
                        player.getUUID(),
                        shooterPos,
                        layer.sound(),
                        layer.volume(),
                        layer.pitch(),
                        delayTicks
                    ));
                }
            });
        }
    }
    
    /**
     * Tick delayed sounds - call this every server tick.
     */
    public void onServerTick(ServerLevel level) {
        Iterator<DelayedSound> iterator = delayedSounds.iterator();
        while (iterator.hasNext()) {
            DelayedSound sound = iterator.next();
            sound.remainingTicks--;
            
            if (sound.remainingTicks <= 0) {
                iterator.remove();
                
                // Find player and play sound
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(sound.playerUuid);
                if (player != null && player.level() == level) {
                    playSoundToPlayer(level, player, sound);
                }
            }
        }
    }
    
    private DistantFire findConfig(ResourceLocation ammoId) {
        for (DistantFire config : sortedData) {
            if (config.matchesAmmo(ammoId)) {
                return config;
            }
        }
        return null;
    }
    
    private void playSound(ServerLevel level, ServerPlayer player, Vec3 pos, DistantFire.SoundLayer layer) {
        playSoundAtPosition(level, player, pos, layer.sound(), layer.volume(), layer.pitch());
    }

    private void playSoundToPlayer(ServerLevel level, ServerPlayer player, DelayedSound sound) {
        playSoundAtPosition(level, player, sound.position, sound.soundId, sound.volume, sound.pitch);
    }

    private void playSoundAtPosition(ServerLevel level, ServerPlayer player, Vec3 pos,
                                     ResourceLocation soundId, float volume, float pitch) {
        SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(soundId);
        level.playSound(player, pos.x, pos.y, pos.z, soundEvent, SoundSource.PLAYERS, volume, pitch);
    }
    
    /**
     * Clear all pending delayed sounds (e.g., on world unload).
     */
    public void clearDelayedSounds() {
        delayedSounds.clear();
    }
    
    private static class DelayedSound {
        final UUID playerUuid;
        final Vec3 position;
        final ResourceLocation soundId;
        final float volume;
        final float pitch;
        int remainingTicks;
        
        DelayedSound(UUID playerUuid, Vec3 position, 
                     ResourceLocation soundId, float volume, float pitch, int remainingTicks) {
            this.playerUuid = playerUuid;
            this.position = position;
            this.soundId = soundId;
            this.volume = volume;
            this.pitch = pitch;
            this.remainingTicks = remainingTicks;
        }
    }
}

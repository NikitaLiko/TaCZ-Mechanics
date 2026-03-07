package ru.liko.tacz_mechanics.data.distant_fire;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import ru.liko.tacz_mechanics.Config;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Manages distant fire sound configurations loaded from JSON files.
 * Files are loaded from: data/<namespace>/tacz_mechanics/distant_fire/<caliber_id>.json
 */
public class DistantFireSoundManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    public static final DistantFireSoundManager INSTANCE = new DistantFireSoundManager();
    
    private final Map<String, DistantFireSound> calibers = new HashMap<>();
    private DistantFireSound defaultConfig;
    
    public DistantFireSoundManager() {
        super(GSON, "tacz_mechanics/distant_fire");
        createDefaultConfig();
    }
    
    private void createDefaultConfig() {
        // Default config using tacztweaks sounds (from user's resource pack)
        defaultConfig = new DistantFireSound(
            "default",
            ResourceLocation.parse("tacztweaks:distant/close_distance"),
            ResourceLocation.parse("tacztweaks:distant/medium_distance"),
            ResourceLocation.parse("tacztweaks:distant/far_distance"),
            Optional.of(ResourceLocation.parse("tacztweaks:distant/very_far_distance")),
            120,  // closeMaxDistance
            250,  // midMaxDistance
            450,  // farMaxDistance
            0.9f, // closeVolume
            0.7f, // midVolume
            0.5f, // farVolume
            0.3f, // veryFarVolume
            20    // transitionBlocks
        );
    }
    
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        calibers.clear();
        
        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement json = entry.getValue();
            
            try {
                DistantFireSound config = DistantFireSound.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(error -> LOGGER.error("Failed to parse distant fire config {}: {}", id, error))
                    .orElse(null);
                
                if (config != null) {
                    calibers.put(config.caliberId(), config);
                    if (Config.debug) {
                        LOGGER.debug("[DistantFire] Loaded config for caliber: {}", config.caliberId());
                    }
                    // If this is the "default" config, use it as fallback
                    if ("default".equals(config.caliberId())) {
                        defaultConfig = config;
                        if (Config.debug) {
                            LOGGER.debug("[DistantFire] Set default config from JSON");
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[DistantFire] Error loading distant fire config {}: {}", id, e.getMessage());
            }
        }
        if (Config.debug) {
            LOGGER.debug("[DistantFire] Loaded {} caliber configurations", calibers.size());
        }
    }
    
    /**
     * Get distant fire config for a caliber ID.
     * Falls back to default if not found.
     */
    public DistantFireSound getConfigForCaliber(String caliberId) {
        return calibers.getOrDefault(caliberId, defaultConfig);
    }
    
    /**
     * Get distant fire config for an ammo ResourceLocation.
     * Extracts caliber from path (e.g., "tacz:9mm" -> "9mm")
     */
    public DistantFireSound getConfigForAmmo(ResourceLocation ammoId) {
        if (ammoId == null) return defaultConfig;
        
        // Try full ID first
        String fullId = ammoId.toString();
        if (calibers.containsKey(fullId)) {
            return calibers.get(fullId);
        }
        
        // Try just the path
        String path = ammoId.getPath();
        if (calibers.containsKey(path)) {
            return calibers.get(path);
        }
        
        return defaultConfig;
    }
    
    /**
     * Check if we have a specific config for this caliber.
     */
    public boolean hasConfigForCaliber(String caliberId) {
        return calibers.containsKey(caliberId);
    }
    
    public DistantFireSound getDefaultConfig() {
        return defaultConfig;
    }
    
    public Map<String, DistantFireSound> getAllConfigs() {
        return Map.copyOf(calibers);
    }
}

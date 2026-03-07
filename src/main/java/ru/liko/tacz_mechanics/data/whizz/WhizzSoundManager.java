package ru.liko.tacz_mechanics.data.whizz;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import ru.liko.tacz_mechanics.TaczMechanics;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages whizz sound configurations loaded from JSON data packs.
 * Path: data/<namespace>/tacz_mechanics/whizz/*.json
 */
public class WhizzSoundManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    
    public static final WhizzSoundManager INSTANCE = new WhizzSoundManager();
    
    private final Map<ResourceLocation, WhizzSound> configs = new HashMap<>();
    private WhizzSound defaultConfig = WhizzSound.DEFAULT;
    
    private WhizzSoundManager() {
        super(GSON, TaczMechanics.MODID + "/whizz");
    }
    
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        configs.clear();
        
        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                JsonObject json = entry.getValue().getAsJsonObject();
                WhizzSound config = WhizzSound.fromJson(json);
                
                String configId = id.getPath();
                if (configId.contains("/")) {
                    configId = configId.substring(configId.lastIndexOf('/') + 1);
                }
                
                ResourceLocation configKey = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), configId);
                configs.put(configKey, config);
                
                if (configId.equals("default")) {
                    defaultConfig = config;
                }
                
                LOGGER.debug("[WhizzSound] Loaded config: {} with {} sounds", configKey, config.sounds().size());
            } catch (Exception e) {
                LOGGER.error("[WhizzSound] Failed to load config: {}", id, e);
            }
        }
        
        LOGGER.debug("[WhizzSound] Loaded {} whizz sound configs", configs.size());
    }
    
    public WhizzSound getConfig(ResourceLocation id) {
        return configs.getOrDefault(id, defaultConfig);
    }
    
    public WhizzSound getDefaultConfig() {
        return defaultConfig;
    }
}

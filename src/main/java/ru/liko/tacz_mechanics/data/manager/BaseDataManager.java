package ru.liko.tacz_mechanics.data.manager;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import ru.liko.tacz_mechanics.data.core.Target;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseDataManager<E> extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();
    
    protected final Logger logger = LogUtils.getLogger();
    protected Map<Class<?>, Map<ResourceLocation, E>> dataMap = Map.of();
    private boolean hasError = false;
    private final Comparator<E> comparator;
    private final Codec<E> codec;
    
    protected BaseDataManager(String directory, Comparator<E> comparator, Codec<E> codec) {
        super(GSON, directory);
        this.comparator = comparator;
        this.codec = codec;
    }
    
    public boolean hasError() {
        return hasError;
    }
    
    @SuppressWarnings("unchecked")
    protected <T extends E> Map<ResourceLocation, T> byType(Class<T> type) {
        return (Map<ResourceLocation, T>) dataMap.getOrDefault(type, Map.of());
    }
    
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> elements, ResourceManager resourceManager, ProfilerFiller profiler) {
        hasError = false;
        Map<Class<?>, ImmutableMap.Builder<ResourceLocation, E>> data = new HashMap<>();
        
        for (var entry : elements.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement json = entry.getValue();
            
            try {
                E element = codec.parse(JsonOps.INSTANCE, json)
                    .getOrThrow(error -> new RuntimeException("Failed to parse " + id + ": " + error));
                
                data.computeIfAbsent(element.getClass(), k -> ImmutableMap.builder())
                    .put(id, element);
                    
                logger.debug("Loaded {} from {}", element.getClass().getSimpleName(), id);
            } catch (RuntimeException e) {
                logger.error("Parsing error loading {}", id, e);
                hasError = true;
            }
        }
        
        Map<Class<?>, Map<ResourceLocation, E>> result = new HashMap<>();
        for (var entry : data.entrySet()) {
            result.put(entry.getKey(), entry.getValue()
                .orderEntriesByValue(comparator)
                .build());
        }
        dataMap = Map.copyOf(result);
        
        logger.info("Loaded {} entries", elements.size());
    }

    protected static boolean matchesTarget(List<Target> targets, ResourceLocation weaponId,
                                           ResourceLocation ammoId, float damage) {
        if (targets.isEmpty()) return true;
        return targets.stream().anyMatch(t -> t.test(weaponId, ammoId, damage));
    }
}

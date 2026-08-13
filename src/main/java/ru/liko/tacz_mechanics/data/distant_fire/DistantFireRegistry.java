package ru.liko.tacz_mechanics.data.distant_fire;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.data.manager.JsonResourceLoader;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory registry of distant-fire sound configurations.
 *
 * <p>Configs live in {@code data/<modid>/distant_fire/<id>.json} and are loaded
 * once at mod startup directly from the jar. There is no datapack override path.
 * If {@code tacz_mechanics:default} is bundled, it acts as the fallback.
 */
public final class DistantFireRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEFAULT_KEY = "default";
    private static final String DIRECTORY = "distant_fire";

    public static final DistantFireRegistry INSTANCE = new DistantFireRegistry();

    private volatile Map<String, DistantFireSound> byKey = Map.of();
    private boolean loaded = false;

    private DistantFireRegistry() {
    }

    public synchronized void bootstrap() {
        if (loaded) {
            return;
        }
        loaded = true;

        Map<ResourceLocation, JsonElement> resources = JsonResourceLoader.loadAll(
            TaczMechanics.MODID, DIRECTORY,
            "default",
            "127x99mm", "12gauge", "30-06", "338lapua", "45acp",
            "545x39mm", "556x45mm", "65creedmoor", "762x39",
            "762x51mm", "762x54mmr", "9x19mm", "lmg", "revolver",
            // suffuse pack calibers, reusing existing JSRS sound groups
            "12.7x108mm", "12.7x55", "14.5x114mm", "boomstickshot", "338nm",
            "6.8tvcm", "4.73x33mm", "6x35mm", "7.65x20mm");
        Map<String, DistantFireSound> next = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation id = entry.getKey();
            DistantFireSound sound = DistantFireSound.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                .resultOrPartial(error -> LOGGER.error("[DistantFire] Failed to parse {}: {}", id, error))
                .orElse(null);
            if (sound == null) {
                continue;
            }
            registerWithAliases(next, id, sound);
        }

        byKey = Map.copyOf(next);
        if (Config.debug) {
            LOGGER.debug("[DistantFire] Loaded {} caliber configurations", byKey.size());
        }
    }

    private static void registerWithAliases(Map<String, DistantFireSound> sink, ResourceLocation fileId, DistantFireSound sound) {
        for (String key : CaliberKey.candidates(sound.caliberId())) {
            sink.putIfAbsent(key, sound);
        }
        if (TaczMechanics.MODID.equals(fileId.getNamespace())) {
            sink.putIfAbsent(fileId.getPath(), sound);
        }
    }

    /** Lookup by raw caliber identifier. Tries normalised aliases via {@link CaliberKey}. */
    public Optional<DistantFireSound> getForCaliber(String caliberId) {
        Map<String, DistantFireSound> snapshot = byKey;
        for (String key : CaliberKey.candidates(caliberId)) {
            DistantFireSound s = snapshot.get(key);
            if (s != null) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    /** Bundled fallback ({@code tacz_mechanics:default}); empty if not provided. */
    public Optional<DistantFireSound> getDefault() {
        return Optional.ofNullable(byKey.get(DEFAULT_KEY));
    }

    /** Lookup with default fallback baked in, mirroring the most common caller pattern. */
    public Optional<DistantFireSound> resolve(String caliberId) {
        Optional<DistantFireSound> direct = getForCaliber(caliberId);
        return direct.isPresent() ? direct : getDefault();
    }
}

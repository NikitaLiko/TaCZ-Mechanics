package ru.liko.tacz_mechanics.client.sound;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.resources.sounds.SoundInstance;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks OpenAL source IDs for sound instances.
 * Used to apply filters to playing sounds.
 */
public class SoundSourceTracker {
    private static final Map<SoundInstance, Integer> sourceMap = new ConcurrentHashMap<>();
    private static Field sourceField;
    private static boolean fieldSearched = false;

    private static Field findSourceField() {
        if (fieldSearched) return sourceField;
        fieldSearched = true;

        try {
            // Try common field names
            String[] possibleNames = {"source", "f_83679_"};
            
            for (String name : possibleNames) {
                try {
                    Field field = Channel.class.getDeclaredField(name);
                    field.setAccessible(true);
                    sourceField = field;
                    return field;
                } catch (NoSuchFieldException ignored) {}
            }

            // Search by type
            for (Field field : Channel.class.getDeclaredFields()) {
                if (field.getType() == int.class) {
                    field.setAccessible(true);
                    sourceField = field;
                    return field;
                }
            }
        } catch (Exception e) {
            System.err.println("[SoundSourceTracker] Failed to find source field: " + e.getMessage());
        }
        
        return null;
    }

    public static void put(SoundInstance instance, Channel channel) {
        Field field = findSourceField();
        if (field == null) return;

        try {
            int sourceId = (int) field.get(channel);
            sourceMap.put(instance, sourceId);
        } catch (Exception e) {
            System.err.println("[SoundSourceTracker] Failed to get source ID: " + e.getMessage());
        }
    }

    public static int get(SoundInstance instance) {
        Integer sourceId = sourceMap.get(instance);
        return sourceId != null ? sourceId : -1;
    }

    public static void remove(SoundInstance instance) {
        sourceMap.remove(instance);
    }

    public static void cleanup() {
        sourceMap.clear();
    }
}

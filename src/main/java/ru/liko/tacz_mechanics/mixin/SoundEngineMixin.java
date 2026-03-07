package ru.liko.tacz_mechanics.mixin;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.client.sound.SoundSourceTracker;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Mixin to track OpenAL source IDs for sound instances.
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {
    private static Field channelsField;
    private static boolean fieldSearched = false;

    private static Field findChannelsField() {
        if (fieldSearched) return channelsField;
        fieldSearched = true;

        try {
            String[] possibleNames = {"instanceToChannel", "f_120232_", "channelAccess"};
            
            for (String name : possibleNames) {
                try {
                    Field field = SoundEngine.class.getDeclaredField(name);
                    field.setAccessible(true);
                    if (Map.class.isAssignableFrom(field.getType())) {
                        channelsField = field;
                        return field;
                    }
                } catch (NoSuchFieldException ignored) {}
            }

            // Search by type
            for (Field field : SoundEngine.class.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    channelsField = field;
                    return field;
                }
            }
        } catch (Exception e) {
            System.err.println("[SoundEngineMixin] Error finding channels field: " + e.getMessage());
        }

        return null;
    }

    @Inject(method = "play", at = @At("TAIL"))
    private void tacz_mechanics$trackSourceId(SoundInstance instance, CallbackInfo ci) {
        try {
            Field field = findChannelsField();
            if (field == null) return;

            @SuppressWarnings("unchecked")
            Map<SoundInstance, ?> map = (Map<SoundInstance, ?>) field.get(this);
            if (map == null) return;

            Object handle = map.get(instance);
            if (handle == null) return;

            // Find the channel in the handle
            for (Field f : handle.getClass().getDeclaredFields()) {
                if (Channel.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Channel channel = (Channel) f.get(handle);
                    if (channel != null) {
                        SoundSourceTracker.put(instance, channel);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            // Silent fail - don't spam logs
        }
    }

    @Inject(method = "stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"))
    private void tacz_mechanics$cleanupTracking(SoundInstance instance, CallbackInfo ci) {
        SoundSourceTracker.remove(instance);
    }
}

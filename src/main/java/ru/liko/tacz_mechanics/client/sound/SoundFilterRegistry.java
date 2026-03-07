package ru.liko.tacz_mechanics.client.sound;

import net.minecraft.client.resources.sounds.SoundInstance;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for tracking sounds that need filters applied.
 */
public class SoundFilterRegistry {
    
    private static final List<MuffledSound> trackedSounds = new CopyOnWriteArrayList<>();

    public static void register(SoundInstance sound, float muffleAmount) {
        trackedSounds.add(new MuffledSound(sound, muffleAmount));
    }

    public static List<MuffledSound> getAll() {
        return Collections.unmodifiableList(trackedSounds);
    }

    public static void tick() {
        // Apply filters to sounds that haven't had them applied yet
        for (MuffledSound muffled : trackedSounds) {
            if (!muffled.filterApplied) {
                if (SoundFilterUtil.applyLowPassFilter(muffled.sound, muffled.muffleAmount)) {
                    muffled.filterApplied = true;
                }
            }
        }
        
        // Remove sounds that are done
        trackedSounds.removeIf(m -> m.filterApplied);
    }

    public static void cleanup() {
        trackedSounds.clear();
    }
    
    public static class MuffledSound {
        public final SoundInstance sound;
        public final float muffleAmount;
        public boolean filterApplied = false;
        
        public MuffledSound(SoundInstance sound, float muffleAmount) {
            this.sound = sound;
            this.muffleAmount = muffleAmount;
        }
    }
}

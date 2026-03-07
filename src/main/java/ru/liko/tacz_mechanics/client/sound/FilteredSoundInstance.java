package ru.liko.tacz_mechanics.client.sound;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * A sound instance that should have a low-pass filter applied.
 */
public class FilteredSoundInstance extends AbstractSoundInstance {
    private final float muffleAmount;
    private boolean filterApplied = false;

    public FilteredSoundInstance(SoundEvent sound, SoundSource source, 
                                  float volume, float pitch, 
                                  double x, double y, double z, 
                                  float muffleAmount) {
        super(sound, source, RandomSource.create());
        this.volume = volume;
        this.pitch = pitch;
        this.x = x;
        this.y = y;
        this.z = z;
        this.muffleAmount = muffleAmount;
        this.looping = false;
        this.delay = 0;
        this.relative = false;
    }
    
    public FilteredSoundInstance(SoundEvent sound, SoundSource source, 
                                  float volume, float pitch, 
                                  RandomSource random,
                                  double x, double y, double z, 
                                  float muffleAmount) {
        super(sound, source, random);
        this.volume = volume;
        this.pitch = pitch;
        this.x = x;
        this.y = y;
        this.z = z;
        this.muffleAmount = muffleAmount;
        this.looping = false;
        this.delay = 0;
        this.relative = false;
    }

    public float getMuffleAmount() {
        return muffleAmount;
    }

    public boolean isFilterApplied() {
        return filterApplied;
    }

    public void setFilterApplied(boolean applied) {
        this.filterApplied = applied;
    }
}

package ru.liko.tacz_mechanics.client.deafen;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import ru.liko.tacz_mechanics.TaczMechanics;

/**
 * The whine itself: a looping, non-positional sound whose volume tracks the ringing level and which
 * stops itself once the ears recover.
 */
public class TinnitusSoundInstance extends AbstractTickableSoundInstance {

    private static final SoundEvent RINGING = SoundEvent.createVariableRangeEvent(
        ResourceLocation.fromNamespaceAndPath(TaczMechanics.MODID, "generic.tinnitus"));

    public TinnitusSoundInstance() {
        super(RINGING, SoundSource.PLAYERS, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        // Inside the listener's head: no position, no distance attenuation.
        this.relative = true;
        this.attenuation = Attenuation.NONE;
        this.volume = Math.max(0.05f, TinnitusHandler.getRawLevel());
        this.pitch = 1.0f;
    }

    @Override
    public void tick() {
        float level = TinnitusHandler.getRawLevel();
        if (level <= 0.01f) {
            stop();
            return;
        }
        this.volume = level;
    }
}

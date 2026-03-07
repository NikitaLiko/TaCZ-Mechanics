package ru.liko.tacz_mechanics.data.manager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ru.liko.tacz_mechanics.data.BulletSounds;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class BulletSoundsManager extends BaseDataManager<BulletSounds> {

    private static final Comparator<BulletSounds> COMPARATOR = Comparator
            .comparingInt(BulletSounds::getPriority)
            .thenComparing(s -> !s.getTarget().isEmpty() ? 0 : 1);

    public static final BulletSoundsManager INSTANCE = new BulletSoundsManager();

    private BulletSoundsManager() {
        super("bullet_sounds", COMPARATOR, BulletSounds.CODEC);
    }

    public void handleBlockSound(BlockSoundType type, ServerLevel level, ResourceLocation weaponId,
            ResourceLocation ammoId, float damage, BlockHitResult result, BlockState state) {
        var sounds = byType(BulletSounds.BlockSound.class);

        for (var entry : sounds.entrySet()) {
            ResourceLocation id = entry.getKey();
            BulletSounds.BlockSound blockSound = entry.getValue();

            // Check target conditions
            if (!matchesTarget(blockSound.target(), weaponId, ammoId, damage))
                continue;

            // Check block conditions
            if (!blockSound.blocks().isEmpty()) {
                boolean matches = blockSound.blocks().stream()
                        .anyMatch(b -> b.test(level, result.getBlockPos(), state));
                if (!matches)
                    continue;
            }

            logger.debug("Using block bullet sounds: {}", id);

            // Get sounds for this type
            List<BulletSounds.BlockSoundEntry> soundEntries = type.getSounds(blockSound);

            for (var soundEntry : soundEntries) {
                if (!matchesTarget(soundEntry.target(), weaponId, ammoId, damage))
                    continue;

                if (!soundEntry.blocks().isEmpty()) {
                    boolean matches = soundEntry.blocks().stream()
                            .anyMatch(b -> b.test(level, result.getBlockPos(), state));
                    if (!matches)
                        continue;
                }

                playSound(level, result.getLocation(), soundEntry.sound(), soundEntry.volume(), soundEntry.pitch(),
                        soundEntry.range());
            }

            return; // Use first matching sound configuration
        }
    }

    public void handleEntitySound(EntitySoundType type, ServerLevel level, ResourceLocation weaponId,
            ResourceLocation ammoId, float damage, Vec3 location, Entity target) {
        var sounds = byType(BulletSounds.EntitySound.class);

        for (var entry : sounds.entrySet()) {
            ResourceLocation id = entry.getKey();
            BulletSounds.EntitySound entitySound = entry.getValue();

            // Check target conditions
            if (!matchesTarget(entitySound.target(), weaponId, ammoId, damage))
                continue;

            // Check entity conditions
            if (!entitySound.entities().isEmpty()) {
                boolean matches = entitySound.entities().stream()
                        .anyMatch(e -> e.test(target));
                if (!matches)
                    continue;
            }

            logger.debug("Using entity bullet sounds: {}", id);

            // Get sounds for this type
            List<BulletSounds.EntitySoundEntry> soundEntries = type.getSounds(entitySound);

            for (var soundEntry : soundEntries) {
                if (!matchesTarget(soundEntry.target(), weaponId, ammoId, damage))
                    continue;

                if (!soundEntry.entities().isEmpty()) {
                    boolean matches = soundEntry.entities().stream()
                            .anyMatch(e -> e.test(target));
                    if (!matches)
                        continue;
                }

                playSound(level, location, soundEntry.sound(), soundEntry.volume(), soundEntry.pitch(),
                        soundEntry.range());
            }

            return; // Use first matching sound configuration
        }
    }

    private void playSound(ServerLevel level, Vec3 position, ResourceLocation soundId, float volume, float pitch,
            java.util.Optional<Float> range) {
        SoundEvent soundEvent = range.map(r -> SoundEvent.createFixedRangeEvent(soundId, r))
                .orElseGet(() -> SoundEvent.createVariableRangeEvent(soundId));
        level.playSound(null, position.x, position.y, position.z, soundEvent, SoundSource.BLOCKS, volume, pitch);
    }

    public enum BlockSoundType {
        HIT(BulletSounds.BlockSound::hit),
        RICOCHET(BulletSounds.BlockSound::ricochet),
        PIERCE(BulletSounds.BlockSound::pierce),
        BREAK(BulletSounds.BlockSound::breakSound);

        private final Function<BulletSounds.BlockSound, List<BulletSounds.BlockSoundEntry>> getter;

        BlockSoundType(Function<BulletSounds.BlockSound, List<BulletSounds.BlockSoundEntry>> getter) {
            this.getter = getter;
        }

        public List<BulletSounds.BlockSoundEntry> getSounds(BulletSounds.BlockSound sound) {
            return getter.apply(sound);
        }
    }

    public enum EntitySoundType {
        HIT(BulletSounds.EntitySound::hit),
        PIERCE(BulletSounds.EntitySound::pierce),
        KILL(BulletSounds.EntitySound::kill);

        private final Function<BulletSounds.EntitySound, List<BulletSounds.EntitySoundEntry>> getter;

        EntitySoundType(Function<BulletSounds.EntitySound, List<BulletSounds.EntitySoundEntry>> getter) {
            this.getter = getter;
        }

        public List<BulletSounds.EntitySoundEntry> getSounds(BulletSounds.EntitySound sound) {
            return getter.apply(sound);
        }
    }
}

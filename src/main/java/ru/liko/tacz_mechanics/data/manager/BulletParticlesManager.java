package ru.liko.tacz_mechanics.data.manager;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ru.liko.tacz_mechanics.data.BulletParticles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

public class BulletParticlesManager extends BaseDataManager<BulletParticles> {
    
    private static final Comparator<BulletParticles> COMPARATOR = Comparator
        .comparingInt(BulletParticles::getPriority)
        .thenComparing(p -> !p.getTarget().isEmpty() ? 0 : 1);
    
    public static final BulletParticlesManager INSTANCE = new BulletParticlesManager();
    
    private final List<ParticleEmitter> emitters = new ArrayList<>();
    
    private BulletParticlesManager() {
        super("bullet_particles", COMPARATOR, BulletParticles.CODEC);
    }
    
    public void onLevelTick(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        Iterator<ParticleEmitter> iterator = emitters.iterator();
        while (iterator.hasNext()) {
            ParticleEmitter emitter = iterator.next();
            if (!emitter.dimension.equals(dimension)) {
                continue;
            }
            emitter.remainingDuration--;
            try {
                level.sendParticles(
                    emitter.options,
                    emitter.x,
                    emitter.y,
                    emitter.z,
                    emitter.count,
                    emitter.deltaX,
                    emitter.deltaY,
                    emitter.deltaZ,
                    emitter.speed
                );
            } catch (Exception e) {
                logger.warn("Failed to send particles", e);
            }
            if (emitter.remainingDuration <= 0) {
                iterator.remove();
            }
        }
    }
    
    public void handleBlockParticle(BlockParticleType type, ServerLevel level, ResourceLocation weaponId, ResourceLocation ammoId, float damage, BlockHitResult result, BlockState state) {
        var particles = byType(BulletParticles.BlockParticles.class);
        
        for (var entry : particles.entrySet()) {
            ResourceLocation id = entry.getKey();
            BulletParticles.BlockParticles blockParticles = entry.getValue();
            
            // Check target conditions
            if (!matchesTarget(blockParticles.target(), weaponId, ammoId, damage)) continue;
            
            // Check block conditions
            if (!blockParticles.blocks().isEmpty()) {
                boolean matches = blockParticles.blocks().stream()
                    .anyMatch(b -> b.test(level, result.getBlockPos(), state));
                if (!matches) continue;
            }
            
            logger.debug("Using block bullet particles: {}", id);
            
            // Get particles for this type
            List<BulletParticles.BlockParticleEntry> particleEntries = type.getParticles(blockParticles);
            
            for (var particleEntry : particleEntries) {
                if (!matchesTarget(particleEntry.target(), weaponId, ammoId, damage)) continue;
                
                if (!particleEntry.blocks().isEmpty()) {
                    boolean matches = particleEntry.blocks().stream()
                        .anyMatch(b -> b.test(level, result.getBlockPos(), state));
                    if (!matches) continue;
                }
                
                String particleString = particleEntry.particle();
                // Replace {block} placeholder with actual block ID
                ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                particleString = particleString.replace("{block}", blockId.toString());
                particleString = particleString.replace("%s", blockId.toString());
                if (particleString.equals("minecraft:block")) {
                    particleString = "minecraft:block " + blockId;
                }
                
                summonParticle(level, result.getLocation(), particleEntry.position(),
                    particleEntry.delta(), particleEntry.speed(), particleEntry.count(),
                    particleEntry.duration(), particleString);
            }
            
            return; // Use first matching particle configuration
        }
    }
    
    public void handleEntityParticle(EntityParticleType type, ServerLevel level, ResourceLocation weaponId, ResourceLocation ammoId, float damage, Vec3 location, Entity target) {
        var particles = byType(BulletParticles.EntityParticles.class);
        
        for (var entry : particles.entrySet()) {
            ResourceLocation id = entry.getKey();
            BulletParticles.EntityParticles entityParticles = entry.getValue();
            
            // Check target conditions
            if (!matchesTarget(entityParticles.target(), weaponId, ammoId, damage)) continue;
            
            // Check entity conditions
            if (!entityParticles.entities().isEmpty()) {
                boolean matches = entityParticles.entities().stream()
                    .anyMatch(e -> e.test(target));
                if (!matches) continue;
            }
            
            logger.debug("Using entity bullet particles: {}", id);
            
            // Get particles for this type
            List<BulletParticles.EntityParticleEntry> particleEntries = type.getParticles(entityParticles);
            
            for (var particleEntry : particleEntries) {
                if (!matchesTarget(particleEntry.target(), weaponId, ammoId, damage)) continue;
                
                if (!particleEntry.entities().isEmpty()) {
                    boolean matches = particleEntry.entities().stream()
                        .anyMatch(e -> e.test(target));
                    if (!matches) continue;
                }
                
                summonParticle(level, location, particleEntry.position(),
                    particleEntry.delta(), particleEntry.speed(), particleEntry.count(),
                    particleEntry.duration(), particleEntry.particle());
            }
            
            return; // Use first matching particle configuration
        }
    }
    
    private void summonParticle(ServerLevel level, Vec3 position, BulletParticles.Coordinates posCoords,
                                BulletParticles.Coordinates deltaCoords, double speed, int count,
                                int duration, String particleString) {
        try {
            ParticleOptions options = parseParticle(particleString, level);
            if (options == null) return;

            Vec3 coords = calculateCoordinates(position, posCoords);
            Vec3 delta = calculateDelta(deltaCoords);

            emitters.add(new ParticleEmitter(
                level.dimension(),
                options,
                coords.x, coords.y, coords.z,
                delta.x, delta.y, delta.z,
                speed, count, duration
            ));
        } catch (Exception e) {
            logger.warn("Failed to parse particle: {}", particleString, e);
        }
    }
    
    private ParticleOptions parseParticle(String particleString, ServerLevel level) {
        // 1.21+ block particle: ParticleArgument expects block_state in NBT; "minecraft:block <id>" is legacy.
        String blockPrefix = "minecraft:block ";
        if (particleString.startsWith(blockPrefix)) {
            String blockIdStr = particleString.substring(blockPrefix.length()).trim();
            if (!blockIdStr.isEmpty()) {
                ResourceLocation blockId = ResourceLocation.tryParse(blockIdStr);
                if (blockId != null) {
                    Block block = BuiltInRegistries.BLOCK.get(blockId);
                    if (block != null && block != Blocks.AIR) {
                        BlockState state = block.defaultBlockState();
                        return new BlockParticleOption(ParticleTypes.BLOCK, state);
                    }
                }
            }
        }
        try {
            return ParticleArgument.readParticle(new StringReader(particleString), level.registryAccess());
        } catch (CommandSyntaxException e) {
            logger.warn("Failed to parse particle string: {}", particleString, e);
            return null;
        }
    }
    
    private Vec3 calculateCoordinates(Vec3 base, BulletParticles.Coordinates coords) {
        return switch (coords.type()) {
            case ABSOLUTE -> new Vec3(coords.x(), coords.y(), coords.z());
            case RELATIVE -> new Vec3(base.x + coords.x(), base.y + coords.y(), base.z + coords.z());
            case LOCAL -> new Vec3(base.x + coords.x(), base.y + coords.y(), base.z + coords.z());
        };
    }
    
    private Vec3 calculateDelta(BulletParticles.Coordinates coords) {
        return new Vec3(coords.x(), coords.y(), coords.z());
    }
    
    public enum BlockParticleType {
        HIT(BulletParticles.BlockParticles::hit),
        PIERCE(BulletParticles.BlockParticles::pierce),
        BREAK(BulletParticles.BlockParticles::breakParticle),
        RICOCHET(BulletParticles.BlockParticles::ricochet);
        
        private final Function<BulletParticles.BlockParticles, List<BulletParticles.BlockParticleEntry>> getter;
        
        BlockParticleType(Function<BulletParticles.BlockParticles, List<BulletParticles.BlockParticleEntry>> getter) {
            this.getter = getter;
        }
        
        public List<BulletParticles.BlockParticleEntry> getParticles(BulletParticles.BlockParticles particles) {
            return getter.apply(particles);
        }
    }
    
    public enum EntityParticleType {
        HIT(BulletParticles.EntityParticles::hit),
        PIERCE(BulletParticles.EntityParticles::pierce),
        KILL(BulletParticles.EntityParticles::kill);
        
        private final Function<BulletParticles.EntityParticles, List<BulletParticles.EntityParticleEntry>> getter;
        
        EntityParticleType(Function<BulletParticles.EntityParticles, List<BulletParticles.EntityParticleEntry>> getter) {
            this.getter = getter;
        }
        
        public List<BulletParticles.EntityParticleEntry> getParticles(BulletParticles.EntityParticles particles) {
            return getter.apply(particles);
        }
    }
    
    private static class ParticleEmitter {
        final ResourceKey<Level> dimension;
        final ParticleOptions options;
        final double x, y, z;
        final double deltaX, deltaY, deltaZ;
        final double speed;
        final int count;
        int remainingDuration;

        ParticleEmitter(ResourceKey<Level> dimension, ParticleOptions options, double x, double y, double z,
                        double deltaX, double deltaY, double deltaZ,
                        double speed, int count, int duration) {
            this.dimension = dimension;
            this.options = options;
            this.x = x;
            this.y = y;
            this.z = z;
            this.deltaX = deltaX;
            this.deltaY = deltaY;
            this.deltaZ = deltaZ;
            this.speed = speed;
            this.count = count;
            this.remainingDuration = duration;
        }
    }
}

package ru.liko.tacz_mechanics.data.manager;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.data.BulletInteractions;

import java.util.Comparator;

public class BulletInteractionsManager extends BaseDataManager<BulletInteractions> {
    
    // Higher priority wins, then more-specific (has target/has blocks) wins.
    private static final Comparator<BulletInteractions> COMPARATOR = Comparator
        .comparingInt(BulletInteractions::getPriority).reversed()
        .thenComparing(i -> !i.getTarget().isEmpty() ? 0 : 1)
        .thenComparing(i -> {
            if (i instanceof BulletInteractions.BlockInteraction bi) {
                return !bi.blocks().isEmpty() ? 0 : 1;
            }
            return 1;
        });
    
    public static final BulletInteractionsManager INSTANCE = new BulletInteractionsManager();
    
    private BulletInteractionsManager() {
        super("bullet_interactions", COMPARATOR, BulletInteractions.CODEC,
            "pierce_metal", "pierce_thin_barriers", "pointed_drip_stone");
    }
    
    public BulletInteractions.PierceSettings findBlockPierce(Level level,
                                                             ResourceLocation gunId,
                                                             ResourceLocation ammoId,
                                                             float damage,
                                                             BlockHitResult result,
                                                             BlockState state,
                                                             double distance,
                                                             RandomSource random) {
        var interactions = byType(BulletInteractions.BlockInteraction.class);
        
        for (var entry : interactions.entrySet()) {
            BulletInteractions.BlockInteraction interaction = entry.getValue();
            
            if (!matches(interaction, level, gunId, ammoId, damage, result, state)) {
                debugTrace("'{}' rejected: target/block mismatch (gun={}, ammo={}, damage={}, block={})",
                    entry.getKey(), gunId, ammoId, damage, state.getBlock());
                continue;
            }

            BulletInteractions.PierceSettings pierce = interaction.pierce();
            String reason = pierceRejectionReason(level, result.getBlockPos(), state, damage, distance, pierce, random);
            if (reason != null) {
                debugTrace("'{}' rejected: {}", entry.getKey(), reason);
                continue;
            }
            
            debugTrace("Using block pierce interaction: {}", entry.getKey());
            return pierce;
        }
        
        return null;
    }

    /**
     * True when some interaction is written for this gun/ammo/block combination, no matter
     * whether its own gates (chance, hardness, distance) let the shot through. Callers use
     * this to keep generic fallbacks away from blocks that are explicitly configured.
     */
    public boolean hasBlockRuleFor(Level level,
                                  ResourceLocation gunId,
                                  ResourceLocation ammoId,
                                  float damage,
                                  BlockHitResult result,
                                  BlockState state) {
        return byType(BulletInteractions.BlockInteraction.class).values().stream()
            .anyMatch(i -> matches(i, level, gunId, ammoId, damage, result, state));
    }

    private boolean matches(BulletInteractions.BlockInteraction interaction, Level level,
                            ResourceLocation gunId, ResourceLocation ammoId, float damage,
                            BlockHitResult result, BlockState state) {
        if (!matchesTarget(interaction.target(), gunId, ammoId, damage)) return false;
        if (interaction.blocks().isEmpty()) return true;
        return interaction.blocks().stream().anyMatch(b -> b.test(level, result.getBlockPos(), state));
    }

    private void debugTrace(String fmt, Object... args) {
        if (Config.debug || Config.Pierce.debug) {
            logger.info("[BulletInteractions] " + fmt, args);
        } else {
            logger.debug(fmt, args);
        }
    }

    private String pierceRejectionReason(Level level, BlockPos pos, BlockState state, float damage, double distance,
                                         BulletInteractions.PierceSettings pierce, RandomSource random) {
        if (damage < pierce.minDamage()) {
            return String.format("damage=%.2f below min_damage=%.2f", damage, pierce.minDamage());
        }
        if (pierce.maxDistance() >= 0.0f && distance > pierce.maxDistance()) {
            return String.format("distance=%.2f above max_distance=%.2f", distance, pierce.maxDistance());
        }
        if (pierce.maxHardness() >= 0.0f) {
            float hardness = state.getDestroySpeed(level, pos);
            if (hardness < 0) {
                return String.format("block %s is unbreakable (hardness<0)", state.getBlock());
            }
            if (hardness > pierce.maxHardness()) {
                return String.format("hardness=%.2f above max_hardness=%.2f", hardness, pierce.maxHardness());
            }
        }
        if (pierce.chance() < 1.0f) {
            float roll = random.nextFloat();
            if (roll > pierce.chance()) {
                return String.format("chanceRoll=%.3f above chance=%.3f", roll, pierce.chance());
            }
        }
        return null;
    }
    
}

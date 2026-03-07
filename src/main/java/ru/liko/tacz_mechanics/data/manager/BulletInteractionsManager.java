package ru.liko.tacz_mechanics.data.manager;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import ru.liko.tacz_mechanics.data.BulletInteractions;

import java.util.Comparator;

public class BulletInteractionsManager extends BaseDataManager<BulletInteractions> {
    
    private static final Comparator<BulletInteractions> COMPARATOR = Comparator
        .comparingInt(BulletInteractions::getPriority)
        .thenComparing(i -> !i.getTarget().isEmpty() ? 0 : 1);
    
    public static final BulletInteractionsManager INSTANCE = new BulletInteractionsManager();
    
    private BulletInteractionsManager() {
        super("bullet_interactions", COMPARATOR, BulletInteractions.CODEC);
    }
    
    public BulletInteractions.PierceSettings findBlockPierce(ServerLevel level,
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
            
            if (!matchesTarget(interaction.target(), gunId, ammoId, damage)) continue;
            
            if (!interaction.blocks().isEmpty()) {
                boolean matches = interaction.blocks().stream()
                    .anyMatch(b -> b.test(level, result.getBlockPos(), state));
                if (!matches) continue;
            }
            
            BulletInteractions.PierceSettings pierce = interaction.pierce();
            if (!canPierce(level, result.getBlockPos(), state, damage, distance, pierce, random)) {
                continue;
            }
            
            logger.debug("Using block pierce interaction: {}", entry.getKey());
            return pierce;
        }
        
        return null;
    }
    
    private boolean canPierce(ServerLevel level, BlockPos pos, BlockState state, float damage, double distance,
                              BulletInteractions.PierceSettings pierce, RandomSource random) {
        if (damage < pierce.minDamage()) {
            return false;
        }
        if (pierce.maxDistance() >= 0.0f && distance > pierce.maxDistance()) {
            return false;
        }
        if (pierce.maxHardness() >= 0.0f) {
            float hardness = state.getDestroySpeed(level, pos);
            if (hardness < 0 || hardness > pierce.maxHardness()) {
                return false;
            }
        }
        if (pierce.chance() < 1.0f && random.nextFloat() > pierce.chance()) {
            return false;
        }
        return true;
    }
}

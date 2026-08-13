package ru.liko.tacz_mechanics.compat;

import com.tacz.guns.api.event.common.GunShootEvent;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.TaczMechanics;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Muzzle blast dust: firing with the barrel close to a surface blows debris off it, so a prone or
 * crouched shooter marks their own position with every shot. A standing shooter's muzzle is too far
 * from the floor for the blast to reach it.
 *
 * <p>Server-side on purpose — the puff is a tell for everyone else, not just for the shooter.
 * Hooked on {@code GunShootEvent}, which the server posts for every gun from its own authoritative
 * shoot path; a server-side {@code GunFireEvent} only reaches guns driven through the script API.
 * One puff per shot, not per shotgun pellet.
 */
@EventBusSubscriber(modid = TaczMechanics.MODID)
public final class MuzzleDustHandler {

    /** How far ahead of the eyes the muzzle sits, roughly the length of a rifle. */
    private static final double MUZZLE_REACH = 0.8;

    /** shooter -> game time of its last puff. Weak keys so leaving players drop out on their own. */
    private static final Map<LivingEntity, Long> LAST_PUFF = new WeakHashMap<>();

    private MuzzleDustHandler() {
    }

    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (!Config.MuzzleDust.enabled) {
            return;
        }
        if (event.getLogicalSide() != LogicalSide.SERVER) {
            return;
        }
        LivingEntity shooter = event.getShooter();
        if (!(shooter.level() instanceof ServerLevel level)) {
            return;
        }

        long now = level.getGameTime();
        Long last = LAST_PUFF.get(shooter);
        if (last != null && now - last < Config.MuzzleDust.cooldownTicks) {
            return;
        }

        Vec3 muzzle = shooter.getEyePosition().add(shooter.getLookAngle().scale(MUZZLE_REACH));
        Vec3 below = muzzle.subtract(0, Config.MuzzleDust.maxSurfaceDistance, 0);
        BlockHitResult hit = level.clip(new ClipContext(muzzle, below,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        if (hit.getType() == HitResult.Type.MISS) {
            return;
        }

        BlockState state = level.getBlockState(hit.getBlockPos());
        // Nothing to raise off air or off a liquid surface.
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return;
        }

        LAST_PUFF.put(shooter, now);

        int count = Config.MuzzleDust.particleCount;
        if (GunAttachments.isSilenced(event.getGunItemStack())) {
            count = Math.max(1, count / 2);
        }
        Vec3 at = hit.getLocation().add(0, 0.02, 0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
            at.x, at.y, at.z, count, 0.22, 0.02, 0.22, 0.03);
    }
}

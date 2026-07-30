package ru.liko.tacz_mechanics.mixin;

import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.particle.ImpactFxSender;

/**
 * Splash effects for bullets entering water or lava.
 *
 * <p>TaCZ traces bullets with {@link ClipContext.Fluid#NONE}, so a fluid never produces a block hit
 * and there is no {@code onHitBlock} to hang this on. Testing "is the bullet inside a fluid now"
 * does not work either: a rifle round covers tens of blocks per tick, so on any shot into water
 * shallower than one tick of travel the trace reaches the bottom in the same tick and the bullet is
 * discarded before it ever ticks while submerged.
 *
 * <p>So the test is on the segment, not on the entity: clip this tick's flight path with fluids
 * enabled and see whether the nearest surface it meets is a fluid one. That fires whether or not
 * the bullet survives, and gives the exact entry point and face for free.
 */
@Mixin(value = EntityKineticBullet.class, remap = false)
public abstract class BulletFluidEntryMixin {

    @Inject(method = "onBulletTick", at = @At("HEAD"))
    private void taczMechanics$onBulletTick(CallbackInfo ci) {
        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        Level level = bullet.level();
        // Effects are broadcast from the server; the client bullet ticks too and would double them.
        if (!(level instanceof ServerLevel serverLevel)) return;

        Vec3 start = bullet.position();
        // Already submerged — this is a bullet travelling through the fluid, not entering it.
        // The guard also keeps the extra clip below off the underwater path entirely.
        if (!level.getFluidState(BlockPos.containing(start)).isEmpty()) return;

        Vec3 end = start.add(bullet.getDeltaMovement());
        if (start.distanceToSqr(end) < 1.0e-6) return;

        // ponytail: a second clip per bullet per tick, on top of the one TaCZ already runs. Sampling
        // fluid states along the segment instead would be cheaper but misses thin water crossed
        // mid-step, which is exactly the shot this feature exists for.
        BlockHitResult hit = level.clip(new ClipContext(
            start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, bullet));
        if (hit.getType() == HitResult.Type.MISS) return;

        // The clip returns the nearest of the block and fluid surfaces. A fluid at the hit block
        // means the fluid is what the bullet reached first.
        FluidState fluid = level.getFluidState(hit.getBlockPos());
        if (fluid.isEmpty()) return;

        ImpactFxSender.send(serverLevel, hit.getLocation(), hit.getDirection(),
            // Waterlogged blocks would otherwise resolve as their host block (fence, stairs, ...).
            fluid.createLegacyBlock(), ImpactFxSender.SPLASH);
    }
}

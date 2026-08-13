package ru.liko.tacz_mechanics.mixin;

import com.mojang.logging.LogUtils;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.particles.BulletHoleOption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.data.BulletInteractions;
import ru.liko.tacz_mechanics.data.manager.BulletInteractionsManager;
import ru.liko.tacz_mechanics.data.manager.BulletParticlesManager;
import ru.liko.tacz_mechanics.data.manager.BulletSoundsManager;
import ru.liko.tacz_mechanics.mixininterface.EntityKineticBulletImpactState;
import ru.liko.tacz_mechanics.particle.ImpactFxSender;
import ru.liko.tacz_mechanics.particle.ModParticles;
import ru.liko.tacz_mechanics.mixininterface.EntityKineticBulletStartPosAccessor;
import ru.liko.tacz_mechanics.util.PierceGeometry;

/**
 * Unified handler for {@code EntityKineticBullet#onHitBlock}. It performs the
 * pierce (block penetration) check first and falls back to ricochet if pierce
 * is not applicable. This replaces the older split between
 * {@code PierceEntityKineticBulletMixin} and {@code RicochetEntityKineticBulletMixin}
 * which both injected at HEAD with {@code cancellable = true} and therefore
 * could short-circuit each other (subsequent HEAD injectors are skipped after
 * {@code ci.cancel()}, leaving stale skip-flags).
 */
@Mixin(value = EntityKineticBullet.class, remap = false)
public abstract class BulletBlockImpactMixin implements EntityKineticBulletImpactState {

    @Unique
    private static final Logger TACZ_MECHANICS$LOGGER = LogUtils.getLogger();

    @Unique
    private static final double TACZ_MECHANICS$EXIT_EPSILON = 0.005;

    /**
     * Части регдоллов Sable Player Ragdoll — и игроков, и мобов. Сверяем пространство имён,
     * а не конкретный блок: тело есть тело, какой бы части оно ни принадлежало.
     */
    @Unique
    private static final String TACZ_MECHANICS$RAGDOLL_NAMESPACE = "sable_player_ragdoll";

    @Unique
    private int taczMechanics$ricochetCount = 0;

    @Unique
    private int taczMechanics$pierceCount = 0;

    @Shadow
    private boolean explosion;

    @Shadow
    private ResourceLocation ammoId;

    @Shadow
    private ResourceLocation gunId;

    @Shadow
    private ResourceLocation gunDisplayId;

    @Shadow
    private float damageModifier;

    @Override
    public int taczMechanics$getRicochetCount() {
        return taczMechanics$ricochetCount;
    }

    @Override
    public void taczMechanics$incrementRicochetCount() {
        taczMechanics$ricochetCount++;
    }

    @Override
    public int taczMechanics$getPierceCount() {
        return taczMechanics$pierceCount;
    }

    @Override
    public void taczMechanics$incrementPierceCount() {
        taczMechanics$pierceCount++;
    }

    @Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true)
    private void taczMechanics$onHitBlock(BlockHitResult result, Vec3 startVec, Vec3 endVec, CallbackInfo ci) {
        if (result.getType() == HitResult.Type.MISS) return;

        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        if (this.explosion) return;

        // NOTE: onHitBlock runs on BOTH sides (called from onBulletTick before the
        // isClientSide gate in tick()). The geometric redirect (velocity/position/rotation)
        // and the cancel MUST happen on both sides — otherwise the client falls through to
        // vanilla onHitBlock, which discards the bullet, and the ricocheted/pierced bullet
        // vanishes visually. World side effects (particles, sounds, holes) stay server-only.
        // RNG rolls are seeded deterministically so client and server reach the same verdict.
        Level level = bullet.level();

        BlockState state = level.getBlockState(result.getBlockPos());

        // Тело — не архитектура. Пробитие и рикошет здесь считать нельзя: коробка конечности
        // тоньше thinBlockMaxThickness, поэтому fallback принимает её за стекло, пробивает и
        // делает ci.cancel(). Отмена гасит весь onHitBlock вместе с AmmoHitBlockEvent, а по
        // этому событию моды ведут урон и импульс по регдоллу (Trauma) — попадание для них
        // просто исчезает. Пропускаем такие блоки к штатному обработчику TACZ.
        if (TACZ_MECHANICS$RAGDOLL_NAMESPACE.equals(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) {
            return;
        }

        Vec3 hitVec = result.getLocation();
        float damage = bullet.getDamage(hitVec);

        // PIERCE — try first (priority over ricochet for the same hit).
        if (Config.Pierce.enabled
                && (Config.Pierce.maxPierces <= 0 || taczMechanics$pierceCount < Config.Pierce.maxPierces)) {
            if (taczMechanics$attemptPierce(level, bullet, result, state, hitVec, damage)) {
                ci.cancel();
                return;
            }
        }

        // RICOCHET — fall back if no pierce
        if (Config.Ricochet.enabled) {
            int maxBounces = Config.Ricochet.demoPreset ? 999 : Config.Ricochet.maxBounces;
            if (maxBounces > 0 && taczMechanics$ricochetCount < maxBounces
                    && BulletSoundsManager.INSTANCE.hasRicochetForBlock(level, this.gunId, this.ammoId, damage, result, state)) {
                if (taczMechanics$attemptRicochet(level, bullet, result, state, hitVec, damage)) {
                    ci.cancel();
                    return;
                }
            } else {
                taczMechanics$debugRicochet("skip: maxBounces=%s count=%s ricochetForBlock=%s",
                    maxBounces, taczMechanics$ricochetCount,
                    BulletSoundsManager.INSTANCE.hasRicochetForBlock(level, this.gunId, this.ammoId, damage, result, state));
            }
        }
    }

    // ============================================================
    // PIERCE
    // ============================================================

    @Unique
    private boolean taczMechanics$attemptPierce(Level level, EntityKineticBullet bullet, BlockHitResult result,
                                                BlockState state, Vec3 hitVec, float damage) {
        Vec3 velocity = bullet.getDeltaMovement();
        double speed = velocity.length();
        if (speed < Config.Pierce.minSpeed) {
            taczMechanics$debugPierce("skip: speed=%.3f minSpeed=%.3f", speed, Config.Pierce.minSpeed);
            return false;
        }

        BlockPos blockPos = result.getBlockPos();
        Vec3 dirNorm = velocity.normalize();
        java.util.List<AABB> boxes = taczMechanics$collisionBoxes(level, blockPos, state);

        double distanceFromStart = taczMechanics$distanceFromStart(bullet, hitVec);
        // Deterministic roll so client and server agree on whether the bullet pierces.
        RandomSource random = taczMechanics$deterministicRandom(bullet, blockPos, taczMechanics$pierceCount);
        BulletInteractions.PierceSettings pierce = BulletInteractionsManager.INSTANCE.findBlockPierce(
            level, this.gunId, this.ammoId, damage, result, state, distanceFromStart, random);
        // Nothing configured for this block: fall back to "thin enough to shoot through",
        // measured on the collision shape the bullet actually crosses. A block someone did
        // configure keeps its own verdict (including a failed chance roll).
        if (pierce == null
                && !BulletInteractionsManager.INSTANCE.hasBlockRuleFor(level, this.gunId, this.ammoId, damage, result, state)) {
            pierce = taczMechanics$thinBlockPierce(boxes, state, hitVec, dirNorm);
        }
        if (pierce == null) {
            taczMechanics$debugPierce("skip: no pierce config matched block=%s", state.getBlock());
            return false;
        }

        Vec3 newVelocity = velocity.scale(pierce.speedMultiplier());
        if (newVelocity.lengthSqr() < 1.0e-6) {
            taczMechanics$debugPierce("skip: post-pierce velocity too low (mul=%.3f)", pierce.speedMultiplier());
            return false;
        }

        Vec3 exitPoint = taczMechanics$computeExitPoint(boxes, blockPos, hitVec, dirNorm);
        Vec3 finalPos = exitPoint.add(dirNorm.scale(TACZ_MECHANICS$EXIT_EPSILON));

        // Server-only world effects (holes/particles/sounds).
        if (level instanceof ServerLevel serverLevel) {
            if (pierce.spawnHole()) {
                taczMechanics$spawnBulletHole(serverLevel, blockPos, hitVec, result.getDirection());
            }
            if (pierce.spawnParticles()) {
                BulletParticlesManager.INSTANCE.handleBlockParticle(
                    BulletParticlesManager.BlockParticleType.PIERCE,
                    serverLevel, this.gunId, this.ammoId, damage, result, state);
                ImpactFxSender.send(serverLevel, hitVec, result.getDirection(), state,
                    ImpactFxSender.PIERCE_ENTRY * ImpactFxSender.scaleForDamage(damage));
            }
            if (pierce.spawnSounds()) {
                BulletSoundsManager.INSTANCE.handleBlockSound(
                    BulletSoundsManager.BlockSoundType.PIERCE,
                    serverLevel, this.gunId, this.ammoId, damage, result, state);
            }
        }

        // Apply damage modifier multiplicatively, no artificial floor:
        // a per-pierce floor (the old Math.max(0.25f, ...) logic) silently
        // boosted shotgun pellets (whose damageModifier starts at 1/N) and
        // also nullified speed/damage decay across multiple pierces.
        this.damageModifier *= pierce.damageMultiplier();
        bullet.setDeltaMovement(newVelocity);
        bullet.setPos(finalPos.x, finalPos.y, finalPos.z);

        // IMPORTANT: do NOT touch startPos. EntityKineticBullet#getDamage uses
        // hitVec.distanceTo(startPos) for distance falloff; resetting startPos
        // would make a sniper that pierces glass at 100m deal point-blank
        // damage to whatever it hits next.

        if (pierce.spawnExitHole() && level instanceof ServerLevel serverExitLevel) {
            Direction exitFace = taczMechanics$pickExitFace(blockPos, exitPoint);
            taczMechanics$spawnBulletHole(serverExitLevel, blockPos, exitPoint, exitFace);
            // Spall on the far side: weaker than the entry, and thrown out of the exit face.
            if (pierce.spawnParticles()) {
                ImpactFxSender.send(serverExitLevel, exitPoint, exitFace, state,
                    ImpactFxSender.PIERCE_EXIT * ImpactFxSender.scaleForDamage(damage));
            }
        }

        taczMechanics$pierceCount++;
        taczMechanics$debugPierce("pierce#%d block=%s entry=%s exit=%s damage*=%.3f speed=%.3f->%.3f distFromStart=%.2f",
            taczMechanics$pierceCount, state.getBlock(), hitVec, exitPoint,
            pierce.damageMultiplier(), speed, newVelocity.length(), distanceFromStart);
        return true;
    }

    // ============================================================
    // RICOCHET
    // ============================================================

    @Unique
    private boolean taczMechanics$attemptRicochet(Level level, EntityKineticBullet bullet, BlockHitResult result,
                                                  BlockState state, Vec3 hitVec, float damage) {
        Direction face = result.getDirection();
        Vec3 velocity = bullet.getDeltaMovement();
        double speed = velocity.length();
        double minSpeed = Config.Ricochet.demoPreset ? 0.0 : Config.Ricochet.minSpeed;
        if (speed < minSpeed) {
            taczMechanics$debugRicochet("skip: speed=%.3f min=%.3f", speed, minSpeed);
            return false;
        }

        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal()).normalize();
        Vec3 incoming = velocity.normalize().scale(-1.0);
        double dot = incoming.dot(normal);
        if (dot <= 0.0) {
            taczMechanics$debugRicochet("skip: dot=%.3f (back-face hit)", dot);
            return false;
        }

        double incidenceDeg = Math.toDegrees(Math.acos(Mth.clamp(dot, -1.0, 1.0)));
        double minAngle = Config.Ricochet.demoPreset ? 0.0 : Config.Ricochet.minAngle;
        if (incidenceDeg < minAngle) {
            taczMechanics$debugRicochet("skip: angle=%.2f min=%.2f", incidenceDeg, minAngle);
            return false;
        }

        double chance = Config.Ricochet.demoPreset ? 1.0 : Config.Ricochet.chance;
        // Deterministic roll so client and server agree on whether the bullet ricochets.
        RandomSource random = taczMechanics$deterministicRandom(bullet, result.getBlockPos(), taczMechanics$ricochetCount);
        double chanceRoll = random.nextDouble();
        if (chanceRoll > chance) {
            taczMechanics$debugRicochet("skip: chanceRoll=%.3f chance=%.3f", chanceRoll, chance);
            return false;
        }

        // Mirror reflection: v' = v - 2(v·n)n
        Vec3 reflected = velocity.subtract(normal.scale(2.0 * velocity.dot(normal)));
        // Realistic ricochets flatten along the surface plane
        double flatten = Config.Ricochet.demoPreset ? 0.0 : Config.Ricochet.flattenReflection;
        if (flatten > 0) {
            double normalComp = reflected.dot(normal);
            reflected = reflected.subtract(normal.scale(normalComp * flatten));
        }
        double speedMultiplier = Config.Ricochet.demoPreset ? 0.9 : Config.Ricochet.speedMultiplier;
        double newSpeed = speed * speedMultiplier;
        if (reflected.lengthSqr() < 1.0e-6) {
            taczMechanics$debugRicochet("skip: reflection vector too small");
            return false;
        }
        Vec3 newVelocity = reflected.normalize().scale(newSpeed);
        if (newVelocity.lengthSqr() < 1.0e-4) {
            taczMechanics$debugRicochet("skip: post-ricochet velocity too low");
            return false;
        }

        Vec3 offset = normal.scale(0.05);
        Vec3 newPos = hitVec.add(offset);
        if (newPos.distanceToSqr(Vec3.atCenterOf(result.getBlockPos())) > 4.0) {
            taczMechanics$debugRicochet("skip: new position too far from block");
            return false;
        }

        taczMechanics$ricochetCount++;
        bullet.setDeltaMovement(newVelocity);

        // Update rotation so tracer/model follow the new direction within this tick.
        // Note: tick() reads getDeltaMovement() but xRot/yRot are read directly in render.
        double dx = newVelocity.x;
        double dz = newVelocity.z;
        double horiz = newVelocity.horizontalDistance();
        bullet.setYRot((float) Math.toDegrees(Mth.atan2(dx, dz)));
        bullet.setXRot((float) Math.toDegrees(Mth.atan2(newVelocity.y, horiz)));
        bullet.yRotO = bullet.getYRot();
        bullet.xRotO = bullet.getXRot();

        bullet.setPos(newPos.x, newPos.y, newPos.z);
        // IMPORTANT: do NOT reset startPos for the same reason as in pierce —
        // damage falloff must continue from the original shot origin.

        // Server-only world effects (holes/sounds/particles). The redirect above already
        // ran on both sides so the client bullet bounces in sync and stays visible.
        if (level instanceof ServerLevel serverLevel) {
            BulletHoleOption bulletHole = new BulletHoleOption(
                face,
                result.getBlockPos(),
                this.ammoId.toString(),
                this.gunId.toString(),
                this.gunDisplayId.toString());
            for (var player : serverLevel.players()) {
                serverLevel.sendParticles(player, bulletHole, true, hitVec.x, hitVec.y, hitVec.z, 1, 0, 0, 0, 0);
            }
            BulletSoundsManager.INSTANCE.handleBlockSound(BulletSoundsManager.BlockSoundType.RICOCHET_IMPACT,
                serverLevel, this.gunId, this.ammoId, damage, result, state);
            BulletSoundsManager.INSTANCE.handleBlockSound(BulletSoundsManager.BlockSoundType.RICOCHET,
                serverLevel, this.gunId, this.ammoId, damage, result, state);
            BulletParticlesManager.INSTANCE.handleBlockParticle(BulletParticlesManager.BlockParticleType.RICOCHET,
                serverLevel, this.gunId, this.ammoId, damage, result, state);
            float ricochetStrength = ImpactFxSender.RICOCHET * ImpactFxSender.scaleForDamage(damage);
            ImpactFxSender.send(serverLevel, hitVec, face, state, ricochetStrength);
            // Sparks trailing away along the bounce, which the surface-normal burst cannot show.
            ImpactFxSender.sendDirected(serverLevel, ModParticles.RICOCHET_STREAK.get(),
                hitVec, newVelocity, ricochetStrength);
        }

        taczMechanics$debugRicochet("ricochet#%d speed=%.3f->%.3f angle=%.2f chanceRoll=%.3f",
            taczMechanics$ricochetCount, speed, newVelocity.length(), incidenceDeg, chanceRoll);
        return true;
    }

    // ============================================================
    // GEOMETRY HELPERS
    // ============================================================

    /**
     * Builds a {@link RandomSource} from data identical on both sides — the entity network
     * id (same id client/server), the hit block position (derived from the shared trajectory),
     * and the per-bullet bounce/pierce counter. This makes the ricochet/pierce chance roll
     * deterministic so the client and server reach the same verdict and stay in sync; without
     * it the unsynced {@code bullet.getRandom()} would let one side bounce while the other
     * passes straight through.
     */
    @Unique
    private RandomSource taczMechanics$deterministicRandom(EntityKineticBullet bullet, BlockPos pos, int salt) {
        long seed = bullet.getId();
        seed = seed * 31L + pos.asLong();
        seed = seed * 31L + salt;
        return RandomSource.create(seed);
    }

    /** True distance the bullet has travelled from its original spawn point. */
    @Unique
    private double taczMechanics$distanceFromStart(EntityKineticBullet bullet, Vec3 hitVec) {
        if (bullet instanceof EntityKineticBulletStartPosAccessor accessor) {
            Vec3 start = accessor.taczMechanics$getStartPos();
            if (start != null) {
                return start.distanceTo(hitVec);
            }
        }
        return 0.0;
    }

    /**
     * Collision boxes of the block in world space. An empty collision shape is treated
     * as a full cube, matching how the bullet trace itself resolved the hit.
     */
    @Unique
    private static java.util.List<AABB> taczMechanics$collisionBoxes(Level level, BlockPos blockPos, BlockState state) {
        double ox = blockPos.getX();
        double oy = blockPos.getY();
        double oz = blockPos.getZ();
        VoxelShape shape;
        try {
            shape = state.getCollisionShape(level, blockPos);
        } catch (Exception e) {
            shape = null;
        }
        if (shape == null || shape.isEmpty()) {
            return java.util.List.of(new AABB(ox, oy, oz, ox + 1, oy + 1, oz + 1));
        }
        return shape.toAabbs().stream().map(part -> part.move(ox, oy, oz)).toList();
    }

    /**
     * Default pierce for blocks nobody configured, gated purely on how much material the
     * bullet has to cross: fences, gates, bars, panes, doors, trapdoors, plates and
     * anything thinner pass; slabs, stairs and full blocks do not.
     */
    @Unique
    private BulletInteractions.PierceSettings taczMechanics$thinBlockPierce(java.util.List<AABB> boxes, BlockState state,
                                                                           Vec3 entry, Vec3 dirNorm) {
        double max = Config.Pierce.thinBlockMaxThickness;
        if (max <= 0.0) return null;
        double thickness = PierceGeometry.materialThickness(boxes, entry, dirNorm);
        if (thickness > max) {
            taczMechanics$debugPierce("skip: thin fallback thickness=%.3f above max=%.3f block=%s",
                thickness, max, state.getBlock());
            return null;
        }
        taczMechanics$debugPierce("thin fallback: block=%s thickness=%.3f <= %.3f", state.getBlock(), thickness, max);
        return new BulletInteractions.PierceSettings(1.0f, 0.0f, -1.0f, -1.0f,
            (float) Config.Pierce.thinBlockDamageMultiplier, (float) Config.Pierce.thinBlockSpeedMultiplier,
            true, true, true, true);
    }

    /**
     * Finds the point at which the bullet's trajectory exits the given block.
     * Falls back to the block centre if the trajectory does not intersect
     * (e.g. tangential hit on a partial shape).
     */
    @Unique
    private static Vec3 taczMechanics$computeExitPoint(java.util.List<AABB> boxes, BlockPos blockPos, Vec3 entry, Vec3 dir) {
        if (dir.lengthSqr() < 1.0e-9) return entry;
        double bestT = PierceGeometry.exitT(boxes, entry, dir);
        if (!Double.isFinite(bestT) || bestT <= 0.0) {
            return Vec3.atCenterOf(blockPos);
        }
        return entry.add(dir.scale(bestT));
    }

    /** Selects the block face whose plane is closest to the given exit point. */
    @Unique
    private static Direction taczMechanics$pickExitFace(BlockPos blockPos, Vec3 exit) {
        double cx = exit.x - blockPos.getX();
        double cy = exit.y - blockPos.getY();
        double cz = exit.z - blockPos.getZ();
        double dxMin = Math.abs(cx);
        double dxMax = Math.abs(1.0 - cx);
        double dyMin = Math.abs(cy);
        double dyMax = Math.abs(1.0 - cy);
        double dzMin = Math.abs(cz);
        double dzMax = Math.abs(1.0 - cz);
        double best = dxMin;
        Direction face = Direction.WEST;
        if (dxMax < best) { best = dxMax; face = Direction.EAST; }
        if (dyMin < best) { best = dyMin; face = Direction.DOWN; }
        if (dyMax < best) { best = dyMax; face = Direction.UP; }
        if (dzMin < best) { best = dzMin; face = Direction.NORTH; }
        if (dzMax < best) { face = Direction.SOUTH; }
        return face;
    }

    @Unique
    private void taczMechanics$spawnBulletHole(ServerLevel level, BlockPos blockPos, Vec3 pos, Direction direction) {
        BulletHoleOption option = new BulletHoleOption(
            direction,
            blockPos,
            this.ammoId.toString(),
            this.gunId.toString(),
            this.gunDisplayId.toString());
        for (var player : level.players()) {
            level.sendParticles(player, option, true, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }
    }

    // ============================================================
    // DEBUG LOGGING
    // ============================================================

    @Unique
    private void taczMechanics$debugPierce(String fmt, Object... args) {
        if (!Config.debug && !Config.Pierce.debug) return;
        TACZ_MECHANICS$LOGGER.info("[Pierce] " + fmt, args);
    }

    @Unique
    private void taczMechanics$debugRicochet(String fmt, Object... args) {
        if (!Config.debug && !Config.Ricochet.debug) return;
        TACZ_MECHANICS$LOGGER.info("[Ricochet] " + fmt, args);
    }
}

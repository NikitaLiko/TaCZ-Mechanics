package ru.liko.tacz_mechanics.particle;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Sends one bullet impact to the clients in range. Every impact path — plain hit, pierce entry,
 * pierce exit, ricochet — goes through here so they cannot drift apart.
 */
public final class ImpactFxSender {

    /** Firefights routinely run past vanilla's 32-block particle radius. */
    private static final double RANGE_SQR = 64.0 * 64.0;

    /** Relative size of the burst; scales every layer's particle count on the client. */
    public static final float HIT = 1.0f;
    public static final float RICOCHET = 0.75f;
    public static final float PIERCE_ENTRY = 0.7f;
    public static final float PIERCE_EXIT = 0.5f;
    public static final float SPLASH = 1.0f;

    /**
     * One particle carries the struck block state, the struck face normal, and the burst intensity.
     *
     * <p>{@code count = 0} is load-bearing: on that path {@code ClientPacketListener} forwards
     * {@code (xDist, yDist, zDist) * maxSpeed} to the provider verbatim instead of randomising it.
     * So the unit normal goes in the delta and the intensity in the speed, and the client reads
     * them back as direction and length of one vector — no custom packet needed.
     */
    public static void send(ServerLevel level, Vec3 at, Direction face, BlockState state, float intensity) {
        if (intensity <= 0.0f) return;
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        BlockParticleOption option = new BlockParticleOption(ModParticles.BULLET_IMPACT.get(), state);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at.x, at.y, at.z) > RANGE_SQR) continue;
            level.sendParticles(player, option, true, at.x, at.y, at.z, 0,
                normal.x, normal.y, normal.z, intensity);
        }
    }

    private ImpactFxSender() {
    }
}

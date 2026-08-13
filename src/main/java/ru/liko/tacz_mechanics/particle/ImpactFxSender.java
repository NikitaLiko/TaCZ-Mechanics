package ru.liko.tacz_mechanics.particle;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
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
        broadcast(level, option, at, normal, intensity);
    }

    /**
     * Sends an effect that is not tied to the struck surface — a ricochet streak leaving along the
     * bounce direction. {@code direction} need not be axis-aligned; it rides in the same three
     * floats a face normal would.
     */
    public static void sendDirected(ServerLevel level, SimpleParticleType type, Vec3 at,
                                    Vec3 direction, float intensity) {
        if (intensity <= 0.0f || direction.lengthSqr() < 1.0e-8) return;
        broadcast(level, type, at, direction.normalize(), intensity);
    }

    private static void broadcast(ServerLevel level, ParticleOptions option, Vec3 at,
                                  Vec3 unitDirection, float intensity) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at.x, at.y, at.z) > RANGE_SQR) continue;
            level.sendParticles(player, option, true, at.x, at.y, at.z, 0,
                unitDirection.x, unitDirection.y, unitDirection.z, intensity);
        }
    }

    /**
     * How much bullet is arriving, as a multiplier on the burst. A 9mm should puff; a .50 should
     * throw a crater's worth. Square-rooted so heavy calibres stay ahead without running away, and
     * clamped at both ends so no configured round can produce nothing or a particle storm.
     *
     * <p>Shotgun pellets each carry a fraction of the shot's damage, so each pellet lands a small
     * burst and the pattern as a whole still reads as heavy — which is the behaviour we want.
     */
    public static float scaleForDamage(float damage) {
        return Mth.clamp(0.35f + 0.22f * (float) Math.sqrt(Math.max(0.0f, damage)), 0.5f, 1.8f);
    }

    private ImpactFxSender() {
    }
}

package ru.liko.tacz_mechanics.movement;

import com.mojang.logging.LogUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import ru.liko.tacz_mechanics.Config;

/**
 * Helpers shared by the sit / prone postures and the leaning (probe) movement.
 *
 * <p>The numeric constants live here so the two sides of every posture — the rendered model
 * (client mixins) and the hitbox skeleton ({@code PlayerSkeleton}) — read the exact same numbers
 * and cannot drift apart.
 */
public final class MovementPosture {

    private static final Logger LOGGER = LogUtils.getLogger();

    // --- posture dimensions (blocks), matching ModularMovements ------------
    public static final float SIT_WIDTH = 0.6f;
    public static final float SIT_HEIGHT = 1.2f;
    public static final float SIT_EYE = 1.1f;
    // Narrow enough to crawl through a 1-block gap. The flat body extends ~1.3 blocks past this box;
    // TaCZ's broad-phase (widened in EntityUtilHitboxMixin) covers that, not the box width.
    public static final float PRONE_WIDTH = 0.6f;
    public static final float PRONE_HEIGHT = 0.5f;
    public static final float PRONE_EYE = 0.4f;

    // --- sit bone pose (radians): the vanilla "riding" leg bend -----------
    public static final float SIT_LEG_X = -1.4137167f;
    public static final float SIT_LEG_Y = Mth.PI / 10f;
    public static final float SIT_LEG_Z = 0.07853982f;

    // --- prone bone pose (radians), matching ModularMovements crawl --------
    public static final float PRONE_HEAD_X = (float) Math.toRadians(-70);
    public static final float PRONE_ARM_X = Mth.PI;
    public static final float PRONE_ARM_FACTOR = 0.2f;
    public static final float PRONE_LEG_FACTOR = 0.2f;

    // --- whole-body transform, applied at the same chain point as the lean roll
    public static final float SIT_DROP = -0.5f;
    public static final float PRONE_PITCH = (float) Math.toRadians(-90);
    public static final float PRONE_TX = 0f;
    public static final float PRONE_TY = -1.3f;
    public static final float PRONE_TZ = 0.1f;

    private MovementPosture() {
    }

    /** Sit / prone drop the eye; leaning does not. Zero while standing. */
    public static Vec3 cameraEyeOffset(Player player, PlayerState state, float partialTick) {
        return Vec3.ZERO;
    }

    /**
     * Whether the player has room to return to the full standing box. Built from the player type's
     * own dimensions (× scale) rather than {@code getDimensions}, which our own mixin shrinks while
     * the player is still posed.
     */
    public static boolean canStand(Player player) {
        EntityDimensions standing = EntityType.PLAYER.getDimensions().scale(player.getScale());
        AABB box = standing.makeBoundingBox(player.position());
        return player.level().noCollision(player, box);
    }

    /**
     * Keeps the vanilla {@link Pose} forced to {@code STANDING} while sitting or prone. Vanilla's
     * {@code updatePlayerPose} measures fit with the un-shrunk {@code getDimensions(pose)} (our size
     * event only fires from {@code refreshDimensions}), so under a low ceiling it would otherwise
     * flip the player into CROUCHING — the "squished into sneak" bug. The real collision box stays
     * our posture box; only the pose label is pinned. Cleared when standing again.
     */
    public static void applyForcedPose(Player player, PlayerState state) {
        boolean posture = state != null && !state.isStanding();
        if (posture) {
            if (player.getForcedPose() != Pose.STANDING) {
                player.setForcedPose(Pose.STANDING);
            }
        } else if (player.getForcedPose() == Pose.STANDING) {
            player.setForcedPose(null);
        }
    }

    /**
     * Дебаг-лог фактического хитбокса игрока и точки обзора. Включается флагом {@code movement.debug}.
     * {@code side} — "CLIENT" или "SERVER".
     */
    public static void logHitbox(String side, Player player, PlayerState state) {
        if (!Config.Movement.debug) {
            return;
        }
        AABB box = player.getBoundingBox();
        Vec3 eye = player.getEyePosition();
        LOGGER.info(String.format(
            "[MovementDebug/%s] probe=%d pos=(%.3f, %.3f, %.3f) bbW=%.3f bbH=%.3f eyeH=%.3f "
                + "box=[x %.3f..%.3f | y %.3f..%.3f | z %.3f..%.3f] eyePos=(%.3f, %.3f, %.3f) yBodyRot=%.1f",
            side, state == null ? 0 : state.getProbe(),
            player.getX(), player.getY(), player.getZ(),
            player.getBbWidth(), player.getBbHeight(), player.getEyeHeight(),
            box.minX, box.maxX, box.minY, box.maxY, box.minZ, box.maxZ,
            eye.x, eye.y, eye.z, player.yBodyRot));
    }

    /**
     * Whether the player is in a state where sitting / lying down makes sense at all: on their own
     * feet, alive, and not doing something the posture would conflict with. Checked on both sides —
     * the client for responsiveness, the server as the authority (a modified client can send any
     * code it likes).
     */
    public static boolean canHoldPosture(Player player) {
        return player.isAlive()
            && !player.isSpectator()
            && !player.isInWater()
            && !player.isInLava()
            && !player.isSwimming()
            && !player.isPassenger()
            && !player.isSleeping()
            && !player.isFallFlying()
            && !player.onClimbable();
    }

    /**
     * Server-side validation of a state transition. Leaning is always allowed; entering sit/prone is
     * rejected while {@link #canHoldPosture} is false (water, vehicle, elytra…); standing back up is
     * rejected (and rolled back) if a block is in the way.
     */
    public static boolean canApplyMovementCode(Player player, int oldCode, int newCode) {
        boolean wasPosture = oldCode / 100 % 10 != 0 || oldCode / 10 % 10 != 0;
        boolean nowStanding = newCode / 100 % 10 == 0 && newCode / 10 % 10 == 0;
        if (wasPosture && nowStanding) {
            return canStand(player);
        }
        if (!nowStanding) {
            return canHoldPosture(player);
        }
        return true;
    }
}

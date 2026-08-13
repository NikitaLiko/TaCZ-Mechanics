package ru.liko.tacz_mechanics.movement;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ограничение наклона по коллизии: смещение глаз/камеры не должно уходить внутрь блоков.
 * Множитель совпадает с {@link ru.liko.tacz_mechanics.mixin.movement.PlayerEyePositionMixin}.
 */
public final class LeanCollision {

    /**
     * Sideways reach of a full lean, in blocks. The single number the whole lean derives from —
     * eye offset, body tilt and hitbox all follow it, so they cannot be tuned apart.
     */
    public static double offsetScale() {
        return ru.liko.tacz_mechanics.Config.Movement.leanDistance;
    }

    private LeanCollision() {
    }

    /**
     * @param directionSign -1 — наклон влево (отрицательный probeOffset), +1 — вправо
     * @return максимальная величина |probeOffset| в этом направлении в [0, 1]
     */
    public static float maxLeanMagnitude(Player player, float directionSign) {
        if (directionSign == 0) {
            return 0f;
        }
        float lo = 0f;
        float hi = 1f;
        for (int i = 0; i < 12; i++) {
            float mid = (lo + hi) * 0.5f;
            float probe = directionSign * mid;
            if (eyeLeanClear(player, probe)) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /**
     * Возвращает безопасное значение probeOffset для текущей позиции игрока.
     * Если запрошенное смещение уводит «глаза» в блок — бинарно сужает величину до безопасной.
     */
    public static float safeProbeOffset(Player player, float probeOffset) {
        if (probeOffset == 0) return 0f;
        if (eyeLeanClear(player, probeOffset)) return probeOffset;
        float sign = probeOffset < 0 ? -1f : 1f;
        float lo = 0f;
        float hi = Math.abs(probeOffset);
        for (int i = 0; i < 8; i++) {
            float mid = (lo + hi) * 0.5f;
            if (eyeLeanClear(player, sign * mid)) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return sign * lo;
    }

    public static boolean eyeLeanClear(Player player, float probeOffset) {
        if (probeOffset == 0) {
            return true;
        }
        double eyeY = player.getY() + player.getEyeHeight();
        Vec3 baseEye = new Vec3(player.getX(), eyeY, player.getZ());
        double yawRad = Math.toRadians(player.getYRot());
        double ox = -probeOffset * offsetScale() * Math.cos(yawRad);
        double oz = -probeOffset * offsetScale() * Math.sin(yawRad);
        Vec3 leaned = baseEye.add(ox, 0, oz);
        // Must be at least as large as vanilla Entity#isInWall suffocation box
        // (width * 0.8 around eye), plus a small safety margin to avoid damage.
        double width = player.getDimensions(player.getPose()).width();
        double halfHoriz = Math.max(0.24, width * 0.4) + 0.02;
        AABB box = new AABB(leaned, leaned).inflate(halfHoriz, 0.18, halfHoriz);
        return player.level().noCollision(player, box);
    }
}

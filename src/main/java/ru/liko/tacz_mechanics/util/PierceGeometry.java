package ru.liko.tacz_mechanics.util;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Pure ray/box math shared by the bullet pierce logic. */
public final class PierceGeometry {

    private PierceGeometry() {
    }

    /**
     * Slab-based ray/AABB intersection. Returns {@code {tEnter, tExit}} as parametric
     * distances along {@code dir} (with {@code tEnter} clamped to 0 so a ray starting
     * inside the box counts from its origin), or {@code null} when the ray misses the
     * box or exits behind the origin. {@code dir} need not be normalised.
     */
    public static double[] raySpan(Vec3 origin, Vec3 dir, AABB box) {
        double tMin = Double.NEGATIVE_INFINITY;
        double tMax = Double.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            double o, d, mn, mx;
            switch (axis) {
                case 0 -> { o = origin.x; d = dir.x; mn = box.minX; mx = box.maxX; }
                case 1 -> { o = origin.y; d = dir.y; mn = box.minY; mx = box.maxY; }
                default -> { o = origin.z; d = dir.z; mn = box.minZ; mx = box.maxZ; }
            }
            if (Math.abs(d) < 1.0e-9) {
                if (o < mn - 1.0e-6 || o > mx + 1.0e-6) return null;
                continue;
            }
            double t1 = (mn - o) / d;
            double t2 = (mx - o) / d;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tMin) tMin = t1;
            if (t2 < tMax) tMax = t2;
            if (tMax < tMin) return null;
        }
        if (tMax <= 0.0) return null;
        return new double[]{Math.max(tMin, 0.0), tMax};
    }

    /**
     * Farthest exit distance along {@code dir} over all boxes, or {@code -1} when the
     * ray misses every one of them.
     */
    public static double exitT(List<AABB> boxes, Vec3 origin, Vec3 dir) {
        double best = -1.0;
        for (AABB box : boxes) {
            double[] span = raySpan(origin, dir, box);
            if (span != null && span[1] > best) best = span[1];
        }
        return best;
    }

    /**
     * Total length of solid material the ray crosses, i.e. the sum of its overlaps with
     * the boxes — air gaps between them (fence post vs. arms, a pane's slot) are not
     * counted. {@code dir} must be normalised for the result to be in blocks.
     */
    public static double materialThickness(List<AABB> boxes, Vec3 origin, Vec3 dir) {
        double total = 0.0;
        for (AABB box : boxes) {
            double[] span = raySpan(origin, dir, box);
            if (span != null) total += span[1] - span[0];
        }
        return total;
    }
}

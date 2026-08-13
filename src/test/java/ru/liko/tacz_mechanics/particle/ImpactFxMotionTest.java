package ru.liko.tacz_mechanics.particle;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import ru.liko.tacz_mechanics.client.particle.ImpactFx;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The launch frame is the one piece of impact-FX math that can be silently wrong: debris that
 * leaves along the wrong axis is exactly what made the earlier attempt look cheap.
 */
class ImpactFxMotionTest {

    private static final double EPS = 1.0e-9;

    @Test
    void radialSkirtRunsAlongTheSurfaceAndCoversEveryDirection() {
        ImpactFx.Motion motion = new ImpactFx.Motion(0.0, 0.0, 0.0, 0.24);
        RandomSource random = RandomSource.create(4242L);

        for (Direction face : Direction.values()) {
            Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
            Vec3 u = n.cross(Math.abs(n.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
            Vec3 v = n.cross(u).normalize();
            boolean[] quadrant = new boolean[4];

            for (int i = 0; i < 2000; i++) {
                Vec3 launched = motion.sample(n, random);
                assertEquals(0.0, launched.dot(n), EPS, "must stay in the surface plane on " + face);

                double speed = launched.length();
                assertTrue(speed >= 0.24 * 0.55 - EPS && speed <= 0.24 + EPS,
                    "speed " + speed + " out of range on " + face);

                quadrant[(launched.dot(u) >= 0 ? 0 : 2) + (launched.dot(v) >= 0 ? 0 : 1)] = true;
            }
            for (int q = 0; q < 4; q++) {
                assertTrue(quadrant[q], "no dust thrown into quadrant " + q + " on " + face);
            }
        }
    }

    @Test
    void noSpreadLaunchesStraightOutAlongTheNormal() {
        ImpactFx.Motion motion = new ImpactFx.Motion(0.3, 0.0, 0.0, 0.0);
        RandomSource random = RandomSource.create(1234L);

        for (Direction face : Direction.values()) {
            Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
            Vec3 velocity = motion.sample(n, random);
            assertEquals(0.3, velocity.length(), EPS, "speed on " + face);
            assertEquals(0.3, velocity.dot(n), EPS, "all of it along the normal on " + face);
        }
    }

    @Test
    void spreadStaysInsideItsBoundsOnEveryFace() {
        ImpactFx.Motion motion = new ImpactFx.Motion(0.30, 0.16, 0.04, 0.0);
        RandomSource random = RandomSource.create(9001L);

        for (Direction face : Direction.values()) {
            Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
            for (int i = 0; i < 2000; i++) {
                Vec3 velocity = motion.sample(n, random);
                double along = velocity.dot(n);
                double across = velocity.subtract(n.scale(along)).length();

                assertTrue(along >= 0.30 - 0.16 - EPS && along <= 0.30 + 0.16 + EPS,
                    "normal component " + along + " out of range on " + face);
                // Two independent tangents, each within ±spreadTangent.
                assertTrue(across <= 0.04 * Math.sqrt(2.0) + EPS,
                    "tangential component " + across + " out of range on " + face);
            }
        }
    }

    @Test
    void debrisNeverFlowsBackIntoTheStruckSurface() {
        // Every shipped layer keeps spreadNormal below velocity, so nothing is launched inwards.
        ImpactFx.Motion motion = new ImpactFx.Motion(0.25, 0.22, 0.10, 0.0);
        RandomSource random = RandomSource.create(7L);
        Vec3 n = Vec3.atLowerCornerOf(Direction.UP.getNormal());

        for (int i = 0; i < 5000; i++) {
            assertTrue(motion.sample(n, random).dot(n) > 0.0, "launched into the block");
        }
    }
}

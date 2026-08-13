package ru.liko.tacz_mechanics.hitbox;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The skeleton is only worth anything if the boxes sit where the model is drawn, so these check
 * against the model's real dimensions: 0.9375 blocks wide across the arms, head from y=1.407 to
 * y=1.876 — both notably different from the 0.6 x 1.8 vanilla bounding box.
 */
class PlayerSkeletonTest {

    private static final Vec3 FEET = Vec3.ZERO;

    /** Shoots horizontally along -Z, from 5 blocks in front of a player facing +Z. */
    private static PlayerSkeleton.Hit shoot(SkeletonPose pose, double x, double y) {
        return PlayerSkeleton.raycast(pose, FEET, new Vec3(x, y, 5), new Vec3(x, y, -5));
    }

    @Test
    void centreShotsResolveToTheRightBone() {
        assertEquals(PlayerSkeleton.Part.HEAD, shoot(SkeletonPose.standing(), 0, 1.65).part());
        assertEquals(PlayerSkeleton.Part.TORSO, shoot(SkeletonPose.standing(), 0, 1.0).part());
        assertEquals(PlayerSkeleton.Part.LEG, shoot(SkeletonPose.standing(), 0, 0.4).part());
    }

    @Test
    void headIsNarrowerThanTheVanillaBox() {
        // x=0.28 is inside the 0.6-wide bounding box but outside the 0.469-wide head
        assertNull(shoot(SkeletonPose.standing(), 0.28, 1.65));
        assertEquals(PlayerSkeleton.Part.HEAD, shoot(SkeletonPose.standing(), 0.2, 1.65).part());
    }

    @Test
    void armsStickOutBeyondTheVanillaBox() {
        PlayerSkeleton.Hit hit = shoot(SkeletonPose.standing(), 0.4, 1.2);
        assertNotNull(hit, "the model is 0.9375 wide, so x=0.4 should be solid arm");
        assertEquals(PlayerSkeleton.Part.ARM, hit.part());
    }

    @Test
    void headRotationMovesTheBox() {
        // a 45-degree turn swings the head cube's corner out to 0.33, past x=0.3
        assertNull(shoot(SkeletonPose.standing(), 0.3, 1.65));
        SkeletonPose turned = SkeletonPose.standing();
        turned.headYaw = 45f;
        assertEquals(PlayerSkeleton.Part.HEAD, shoot(turned, 0.3, 1.65).part());
    }

    @Test
    void crouchingDropsTheHeadOutOfTheWay() {
        assertEquals(PlayerSkeleton.Part.HEAD, shoot(SkeletonPose.standing(), 0, 1.8).part());
        assertNull(shoot(crouched(), 0, 1.8));
    }

    /**
     * A crouching player drops twice: the model's own part offsets, plus the whole-model shift from
     * {@code PlayerRenderer.getRenderOffset}. Miss the second and the skeleton floats 0.125 above the
     * model. Standing, the head tops out just over the 1.8 bounding box; crouched, just over 1.5.
     */
    @Test
    void crouchingAppliesTheRenderOffsetToo() {
        assertEquals(1.875, headTop(SkeletonPose.standing()), 1e-3);
        assertEquals(1.505, headTop(crouched()), 1e-3);
    }

    /** Prone lays the whole body flat on the ground: every bone centre sits near y=0, not stacked up. */
    @Test
    void proneLiesFlat() {
        SkeletonPose pose = SkeletonPose.standing();
        pose.posture = 2;
        double maxY = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        for (PlayerSkeleton.OrientedBox box : PlayerSkeleton.boxes(pose)) {
            org.joml.Vector3f c = box.transform().transformPosition(new org.joml.Vector3f(
                    (float) ((box.local().minX + box.local().maxX) / 2),
                    (float) ((box.local().minY + box.local().maxY) / 2),
                    (float) ((box.local().minZ + box.local().maxZ) / 2)));
            maxY = Math.max(maxY, c.y);
            minY = Math.min(minY, c.y);
        }
        assertTrue(maxY - minY < 0.4, "prone body should be flat, y spread " + (maxY - minY));
        assertTrue(maxY < 0.6, "prone body should hug the ground, highest bone at " + maxY);
    }

    @Test
    void proneBodyIsHittable() {
        SkeletonPose pose = SkeletonPose.standing();
        pose.posture = 2;
        // Prone body lies flat ~0.1-0.3 above the feet, extending along Z. A horizontal shot along
        // -Z at that low height must strike a bone, or a prone player is invulnerable.
        StringBuilder tried = new StringBuilder();
        boolean anyHit = false;
        for (double y = 0.05; y <= 0.45; y += 0.05) {
            for (double x = -0.3; x <= 0.3; x += 0.15) {
                PlayerSkeleton.Hit hit = shoot(pose, x, y);
                if (hit != null) {
                    anyHit = true;
                    tried.append(String.format("  HIT %-5s at x=%+.2f y=%.2f%n", hit.part(), x, y));
                }
            }
        }
        assertTrue(anyHit, "prone body should be hittable by a horizontal shot\n" + tried);
    }

    private static SkeletonPose crouched() {
        SkeletonPose pose = SkeletonPose.standing();
        pose.crouching = true;
        pose.renderOffsetY = -2.0f / 16.0f;
        return pose;
    }

    private static double headTop(SkeletonPose pose) {
        PlayerSkeleton.OrientedBox head = PlayerSkeleton.boxes(pose).stream()
                .filter(b -> b.part() == PlayerSkeleton.Part.HEAD)
                .findFirst()
                .orElseThrow();
        double top = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            var corner = head.transform().transformPosition(new org.joml.Vector3f(
                    (float) ((i & 4) != 0 ? head.local().maxX : head.local().minX),
                    (float) ((i & 2) != 0 ? head.local().maxY : head.local().minY),
                    (float) ((i & 1) != 0 ? head.local().maxZ : head.local().minZ)));
            top = Math.max(top, corner.y);
        }
        return top;
    }

    @Test
    void bodyRotationTurnsTheWholeSkeleton() {
        // head on, the bullet meets the chest 2px out
        PlayerSkeleton.Hit front = shoot(SkeletonPose.standing(), 0, 1.0);
        assertEquals(PlayerSkeleton.Part.TORSO, front.part());
        assertEquals(2 * 0.9375 / 16, front.pos().z, 1e-4);

        // turned sideways it meets the outer arm instead, and passes on into the chest
        SkeletonPose sideways = SkeletonPose.standing();
        sideways.bodyYRot = 90f;
        PlayerSkeleton.Hit side = shoot(sideways, 0, 1.0);
        assertEquals(PlayerSkeleton.Part.TORSO, side.part());
        assertTrue(side.pos().z > front.pos().z + 0.25,
                "sideways the arm should be met far sooner, z=" + side.pos().z);
    }

    @Test
    void walkCycleSwingsTheLegsOutOfStep() {
        // standing, both legs are level
        assertEquals(shoot(SkeletonPose.standing(), -0.1, 0.35).pos().z,
                shoot(SkeletonPose.standing(), 0.1, 0.35).pos().z, 1e-4);

        // mid-stride one leg leads and the other trails
        SkeletonPose walking = SkeletonPose.standing();
        walking.limbSwingAmount = 0.6f;
        double rightZ = shoot(walking, -0.1, 0.35).pos().z;
        double leftZ = shoot(walking, 0.1, 0.35).pos().z;
        assertTrue(leftZ - rightZ > 0.5, "legs should be out of step, got " + rightZ + " and " + leftZ);
    }

    @Test
    void aimingWithAGunThrowsTheArmsForward() {
        SkeletonPose aiming = SkeletonPose.standing();
        aiming.holdingGun = true;
        aiming.aimProgress = 1f;

        double resting = shoot(SkeletonPose.standing(), 0.31, 1.35).pos().z;
        PlayerSkeleton.Hit aimed = shoot(aiming, 0.31, 1.35);
        assertEquals(PlayerSkeleton.Part.ARM, aimed.part());
        assertTrue(aimed.pos().z > resting * 1.5,
                "the aimed arm should reach well past the resting one: " + aimed.pos().z + " vs " + resting);
    }

    @Test
    void leaningRollsTheWholeBody() {
        SkeletonPose leaning = SkeletonPose.standing();
        leaning.leanRoll = SkeletonPose.leanRollFor(1f, 1.62f, 0.6); // full lean, ~22 degrees

        // the head has rolled sideways, so the straight-on head shot misses centre
        assertNull(shoot(leaning, 0, 1.8));
        assertNotNull(shoot(leaning, -0.6, 1.6), "the head should have moved sideways, not vanished");
    }

    /**
     * The lean angle is derived from the eye offset, so the skeleton must end up leaning exactly as
     * far, and the same way, as the camera does — at any facing. A sign error here would let players
     * peek round a corner while their hitbox leaned into cover.
     */
    @Test
    void leanMovesTheSkeletonWhereTheEyeGoes() {
        for (float yaw : new float[]{0f, 90f, 180f, -45f, 137f}) {
            for (float probe : new float[]{1f, -1f, 0.5f}) {
                float eyeHeight = 1.62f;
                SkeletonPose leaning = SkeletonPose.standing();
                leaning.bodyYRot = yaw;
                leaning.leanRoll = SkeletonPose.leanRollFor(probe, eyeHeight, 0.6);
                SkeletonPose upright = SkeletonPose.standing();
                upright.bodyYRot = yaw;

                // PlayerEyePositionMixin's offset, which is what the camera actually does
                double radians = Math.toRadians(yaw);
                double expectedX = -probe * 0.6 * Math.cos(radians);
                double expectedZ = -probe * 0.6 * Math.sin(radians);

                Vec3 uprightHead = headCentre(upright);
                Vec3 leaningHead = headCentre(leaning);
                Vec3 moved = leaningHead.subtract(uprightHead);
                String where = "yaw=" + yaw + " probe=" + probe;
                assertEquals(expectedX, moved.x, 0.06, where);
                assertEquals(expectedZ, moved.z, 0.06, where);

                // Leaning is one rigid rotation about the feet, which is what keeps the eye, the
                // model and the hitbox from drifting apart. A rotation preserves distance from the
                // pivot, and it must lower the head rather than slide it sideways at full height.
                assertEquals(uprightHead.length(), leaningHead.length(), 1e-3, where);
                assertTrue(moved.y < -0.01, where + " should drop the head, got " + moved.y);
            }
        }
    }

    private static Vec3 headCentre(SkeletonPose pose) {
        PlayerSkeleton.OrientedBox head = PlayerSkeleton.boxes(pose).stream()
                .filter(b -> b.part() == PlayerSkeleton.Part.HEAD)
                .findFirst()
                .orElseThrow();
        var centre = head.local().getCenter();
        var world = head.transform().transformPosition(
                new org.joml.Vector3f((float) centre.x, (float) centre.y, (float) centre.z));
        return new Vec3(world.x, world.y, world.z);
    }

    @Test
    void sleepingLaysTheSkeletonFlat() {
        SkeletonPose asleep = SkeletonPose.standing();
        asleep.sleeping = true;

        // the body is down at ground level and nothing is left standing
        assertTrue(anythingAt(asleep, 0.15), "a sleeping player should still be hittable near the ground");
        assertFalse(anythingAt(asleep, 0.6), "nothing should reach knee height any more");
        assertFalse(anythingAt(asleep, 1.65));
    }

    /** Sweeps sideways, so it does not care which way the sleeping body ended up laid out. */
    private static boolean anythingAt(SkeletonPose pose, double y) {
        for (double x = -1.5; x <= 1.5; x += 0.02) {
            if (shoot(pose, x, y) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Leaning moves bone pivots, so posing the same player twice must not compound. The renderer hit
     * exactly this: {@code setupAnim} never resets the head, body or leg x, so leaning them walked the
     * parts off the player a little further every frame.
     */
    @Test
    void posingIsRepeatable() {
        SkeletonPose leaning = SkeletonPose.standing();
        leaning.leanRoll = SkeletonPose.leanRollFor(1f, 1.62f, 0.6);

        Vec3 first = headCentre(leaning);
        for (int i = 0; i < 20; i++) {
            PlayerSkeleton.boxes(leaning);
        }
        Vec3 after = headCentre(leaning);
        assertEquals(first.x, after.x, 1e-6);
        assertEquals(first.y, after.y, 1e-6);
    }

    /**
     * A SuperbWarfare seat parents the whole model to the vehicle, so a rolled vehicle has to carry
     * the skeleton with it — this is the one place the vanilla rotation chain is replaced rather
     * than extended, and getting the quaternion order wrong leaves the boxes upright inside a
     * banking aircraft.
     */
    @Test
    void seatRotationRollsTheWholeSkeleton() {
        SkeletonPose seated = SkeletonPose.standing();
        seated.riding = true;
        seated.seatRotation = new org.joml.Quaternionf().rotateZ((float) Math.toRadians(90));

        // rolled 90 degrees, the head is no longer above the seat but out to one side of it
        Vec3 head = headCentre(seated);
        assertTrue(Math.abs(head.y) < 0.35, "head should have rolled off the vertical, y=" + head.y);
        assertTrue(Math.abs(head.x) > 1.2, "head should be out sideways, x=" + head.x);

        // and the boxes go with it: a shot through where a standing head would be now misses
        assertNull(shoot(seated, 0, 1.65));
        assertEquals(PlayerSkeleton.Part.HEAD, shoot(seated, head.x, head.y).part());
    }

    /**
     * Point-blank: the muzzle (segment start) can end up inside a bone — two players pressed
     * together, or a target lag-compensated onto the shooter. {@code AABB.clip} returns empty when
     * the ray starts inside the box, so without an explicit containment check the shot passes
     * clean through the body it is touching.
     */
    @Test
    void segmentStartingInsideABoneStillHits() {
        // z=0.10 is inside the torso (2px deep = +-0.117 around the spine)
        PlayerSkeleton.Hit hit = PlayerSkeleton.raycast(SkeletonPose.standing(), FEET,
                new Vec3(0, 1.0, 0.10), new Vec3(0, 1.0, -5));
        assertNotNull(hit, "a shot fired from inside the torso must register");
        assertEquals(PlayerSkeleton.Part.TORSO, hit.part());
    }

    @Test
    void muzzleTouchingTheChestHits() {
        // just outside the torso surface — the shortest real-world gap two colliding players allow
        PlayerSkeleton.Hit hit = PlayerSkeleton.raycast(SkeletonPose.standing(), FEET,
                new Vec3(0, 1.0, 0.15), new Vec3(0, 1.0, -5));
        assertNotNull(hit);
        assertEquals(PlayerSkeleton.Part.TORSO, hit.part());
    }

    /**
     * A moving target's visible model sits a few ticks of movement away from where lag compensation
     * anchors the skeleton, and the error's sign differs between the TaCZ and SuperbWarfare
     * pipelines — so the bones stretch symmetrically along the motion, and only along it.
     */
    @Test
    void velocitySlopStretchesBonesAlongMovement() {
        double saved = ru.liko.tacz_mechanics.Config.Hitbox.velocitySlop;
        try {
            SkeletonPose running = SkeletonPose.standing();
            running.velocity = new Vec3(0.25, 0, 0); // sprinting along +X

            // x=0.75 clears the 0.469-wide silhouette on both sides while it stands still
            ru.liko.tacz_mechanics.Config.Hitbox.velocitySlop = 0;
            assertNull(shoot(running, 0.75, 1.0));

            ru.liko.tacz_mechanics.Config.Hitbox.velocitySlop = 3.0;
            assertNotNull(shoot(running, 0.75, 1.0), "bones should stretch towards the movement");
            assertNotNull(shoot(running, -0.75, 1.0), "and just as far the other way");
            assertNull(shoot(running, 0, 2.5), "no stretch off the movement axis");
            assertNull(shoot(SkeletonPose.standing(), 0.75, 1.0),
                    "a standing target keeps its exact silhouette");
        } finally {
            ru.liko.tacz_mechanics.Config.Hitbox.velocitySlop = saved;
        }
    }

    @Test
    void missesReturnNull() {
        assertNull(shoot(SkeletonPose.standing(), 0, 2.5));
        assertNull(shoot(SkeletonPose.standing(), 2.0, 1.0));
    }
}

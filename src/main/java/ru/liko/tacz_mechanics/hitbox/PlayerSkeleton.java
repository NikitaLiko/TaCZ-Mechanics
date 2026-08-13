package ru.liko.tacz_mechanics.hitbox;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.movement.MovementPosture;

import java.util.ArrayList;
import java.util.List;

/**
 * Oriented bounding boxes on the humanoid skeleton, posed by a server-side port of the client
 * animation code.
 *
 * <p>{@link #animate} is a near line-for-line transcription of {@code HumanoidModel.setupAnim} and
 * {@link #baseTransform} of {@code LivingEntityRenderer.setupRotations} +
 * {@code PlayerRenderer.setupRotations}, deliberately kept in the same order and with the same magic
 * numbers so it can be diffed against vanilla when Mojang changes something. Box sizes and pivots
 * come from {@code HumanoidModel.createMesh}.
 *
 * <p>The server has no model, so the pose is recomputed rather than read — which is also what makes
 * it cheat-proof: every input in {@link SkeletonPose} is state the server owns.
 */
public final class PlayerSkeleton {

    public enum Part { HEAD, TORSO, ARM, LEG }

    public record Hit(Vec3 pos, Part part) {}

    /** A posed bone: {@code transform} maps {@code local} (model pixels) into anchor-relative space. */
    public record OrientedBox(Part part, Matrix4f transform, AABB local) {}

    /** A bone, mirroring the {@code ModelPart} fields the animation writes to. */
    private static final class Joint {
        final Part part;
        final AABB cube;
        float x;
        float y;
        float z;
        float xRot;
        float yRot;
        float zRot;

        Joint(Part part, float x, float y, float z, AABB cube) {
            this.part = part;
            this.x = x;
            this.y = y;
            this.z = z;
            this.cube = cube;
        }
    }

    /** The six bones a bullet can hit. Sleeves, pants, hat and cloak are cosmetic copies. */
    private static final class Rig {
        final Joint head = new Joint(Part.HEAD, 0f, 0f, 0f, new AABB(-4, -8, -4, 4, 0, 4));
        final Joint body = new Joint(Part.TORSO, 0f, 0f, 0f, new AABB(-4, 0, -2, 4, 12, 2));
        final Joint rightArm = new Joint(Part.ARM, -5f, 2f, 0f, new AABB(-3, -2, -2, 1, 10, 2));
        final Joint leftArm = new Joint(Part.ARM, 5f, 2f, 0f, new AABB(-1, -2, -2, 3, 10, 2));
        final Joint rightLeg = new Joint(Part.LEG, -1.9f, 12f, 0f, new AABB(-2, 0, -2, 2, 12, 2));
        final Joint leftLeg = new Joint(Part.LEG, 1.9f, 12f, 0f, new AABB(-2, 0, -2, 2, 12, 2));

        Joint[] all() {
            return new Joint[]{head, body, rightArm, leftArm, rightLeg, leftLeg};
        }

        Joint arm(HumanoidArm arm) {
            return arm == HumanoidArm.LEFT ? leftArm : rightArm;
        }
    }

    private PlayerSkeleton() {
    }

    public static float damageMultiplier(Part part) {
        return switch (part) {
            // the head keeps running through TaCZ's own headshot multiplier
            case HEAD -> 1f;
            case TORSO -> (float) Config.Hitbox.torsoMultiplier;
            case ARM -> (float) Config.Hitbox.armMultiplier;
            case LEG -> (float) Config.Hitbox.legMultiplier;
        };
    }

    /**
     * Clips the segment against every bone.
     *
     * @param anchor world position of the model's feet
     * @return the nearest bone hit, or null if the segment slips past the whole skeleton
     */
    public static Hit raycast(SkeletonPose pose, Vec3 anchor, Vec3 from, Vec3 to) {
        Vector3f fromM = relative(from, anchor);
        Vector3f toM = relative(to, anchor);
        Vector3f slop = velocitySlop(pose);

        Hit best = null;
        double bestDistSqr = Double.MAX_VALUE;
        boolean torsoHit = false;

        for (OrientedBox box : boxes(pose)) {
            Matrix4f inverse = new Matrix4f(box.transform()).invert();
            Vector3f a = inverse.transformPosition(new Vector3f(fromM));
            Vector3f b = inverse.transformPosition(new Vector3f(toM));

            AABB cube = box.local();
            if (slop != null) {
                Vector3f d = inverse.transformDirection(new Vector3f(slop));
                cube = cube.inflate(Math.abs(d.x), Math.abs(d.y), Math.abs(d.z));
            }
            Vec3 hit = cube
                    .clip(new Vec3(a.x, a.y, a.z), new Vec3(b.x, b.y, b.z))
                    .orElse(null);
            if (hit == null) {
                // AABB.clip is empty when the ray starts inside the box, which is exactly a
                // point-blank shot — muzzle pressed into the body. Register it at the start point.
                if (!cube.contains(a.x, a.y, a.z)) {
                    continue;
                }
                hit = new Vec3(a.x, a.y, a.z);
            }

            if (box.part() == Part.TORSO) {
                torsoHit = true;
            }
            Vector3f world = box.transform().transformPosition(new Vector3f((float) hit.x, (float) hit.y, (float) hit.z));
            Vec3 worldHit = new Vec3(anchor.x + world.x, anchor.y + world.y, anchor.z + world.z);
            double distSqr = from.distanceToSqr(worldHit);
            if (distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                best = new Hit(worldHit, box.part());
            }
        }

        // An arm held in front of the chest shouldn't shield it — the bullet carries on into the torso.
        if (best != null && best.part() == Part.ARM && torsoHit) {
            return new Hit(best.pos(), Part.TORSO);
        }
        return best;
    }

    /**
     * The shooter aims at the model they see, which sits a few ticks of the target's movement away
     * from where the ping rewind places the hitbox (TaCZ shifts its box 5 ticks back, the client
     * renders ~2 ticks back, SuperbWarfare rewinds by ping only). The old fat AABB soaked that error
     * up; the exact skeleton does not, so stretch every bone along the target's motion instead. The
     * error's sign differs between the two pipelines, hence symmetric. Null when the target stands
     * still — a stationary silhouette stays exact.
     */
    private static Vector3f velocitySlop(SkeletonPose pose) {
        double ticks = Config.Hitbox.velocitySlop;
        Vec3 slop = pose.velocity.scale(ticks);
        double lenSqr = slop.lengthSqr();
        if (ticks <= 0 || lenSqr == 0) {
            return null;
        }
        // ponytail: hard cap so an elytra dive doesn't stretch bones across half the map
        double max = 1.0;
        if (lenSqr > max * max) {
            slop = slop.scale(max / Math.sqrt(lenSqr));
        }
        return new Vector3f((float) slop.x, (float) slop.y, (float) slop.z);
    }

    /** The posed skeleton. Shared by the raycast and the debug render so they cannot disagree. */
    public static List<OrientedBox> boxes(SkeletonPose pose) {
        Rig rig = new Rig();
        animate(rig, pose);

        Matrix4f base = baseTransform(pose);
        List<OrientedBox> result = new ArrayList<>(6);
        for (Joint joint : rig.all()) {
            Matrix4f transform = new Matrix4f(base).translate(joint.x, joint.y, joint.z);
            // ModelPart.translateAndRotate order: Z, then Y, then X
            if (joint.zRot != 0f) {
                transform.rotateZ(joint.zRot);
            }
            if (joint.yRot != 0f) {
                transform.rotateY(joint.yRot);
            }
            if (joint.xRot != 0f) {
                transform.rotateX(joint.xRot);
            }
            result.add(new OrientedBox(joint.part, transform, joint.cube));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // LivingEntityRenderer.setupRotations + PlayerRenderer.setupRotations
    // ------------------------------------------------------------------

    /** Model space (pixels, Y down) -> world space, relative to the feet. */
    private static Matrix4f baseTransform(SkeletonPose pose) {
        // Both offsets are applied by the render dispatcher, before the entity scale.
        Matrix4f m = new Matrix4f()
                .translate((float) pose.bedOffset.x, (float) pose.bedOffset.y + pose.renderOffsetY,
                        (float) pose.bedOffset.z)
                .scale(pose.scale);

        // SuperbWarfare's LivingEntityRendererMixin cancels vanilla setupRotations for a seated player
        // and parents the model to the seat instead, so this replaces the whole block below rather
        // than adding to it — including the lean, which a seated player cannot do anyway.
        if (pose.seatRotation != null) {
            return m.rotate(pose.seatRotation)
                    .rotateY((180f - pose.bodyYRot) * Mth.DEG_TO_RAD)
                    .scale(pose.seatScale)
                    .scale(-1f, -1f, 1f)
                    .scale(0.9375f)
                    .translate(0f, -1.501f, 0f)
                    .scale(1f / 16f);
        }

        if (pose.sleeping) {
            float bedRot = pose.bedDirection != null ? sleepDirectionToRotation(pose.bedDirection) : pose.bodyYRot;
            m.rotateY(bedRot * Mth.DEG_TO_RAD)
                    .rotateZ(FLIP_DEGREES * Mth.DEG_TO_RAD)
                    .rotateY(270f * Mth.DEG_TO_RAD);
        } else {
            m.rotateY((180f - pose.bodyYRot) * Mth.DEG_TO_RAD);

            if (pose.deathTime > 0) {
                float f = Math.min(Mth.sqrt((pose.deathTime - 1.0f) / 20.0f * 1.6f), 1.0f);
                m.rotateZ(f * FLIP_DEGREES * Mth.DEG_TO_RAD);
            } else if (pose.autoSpinAttack) {
                m.rotateX((-90f - pose.headPitch) * Mth.DEG_TO_RAD)
                        .rotateY(pose.ageInTicks * -75f * Mth.DEG_TO_RAD);
            } else if (pose.upsideDown) {
                m.rotateZ(180f * Mth.DEG_TO_RAD);
            }

            // PlayerRenderer adds the elytra and swimming tilt on top
            if (pose.fallFlying) {
                float t = Mth.clamp(pose.fallFlyingTicks * pose.fallFlyingTicks / 100.0f, 0.0f, 1.0f);
                if (!pose.autoSpinAttack) {
                    m.rotateX(t * (-90f - pose.viewXRot) * Mth.DEG_TO_RAD);
                }
                double moveSqr = pose.velocity.horizontalDistanceSqr();
                double viewSqr = pose.viewVector.horizontalDistanceSqr();
                if (moveSqr > 0.0 && viewSqr > 0.0) {
                    double dot = (pose.velocity.x * pose.viewVector.x + pose.velocity.z * pose.viewVector.z)
                            / Math.sqrt(moveSqr * viewSqr);
                    double cross = pose.velocity.x * pose.viewVector.z - pose.velocity.z * pose.viewVector.x;
                    m.rotateY((float) (Math.signum(cross) * Math.acos(Mth.clamp(dot, -1.0, 1.0))));
                }
            } else if (pose.swimAmount > 0.0f) {
                float target = pose.swimmingInFluid ? -90f - pose.headPitch : -90f;
                m.rotateX(Mth.lerp(pose.swimAmount, 0.0f, target) * Mth.DEG_TO_RAD);
                if (pose.visuallySwimming) {
                    m.translate(0.0f, -1.0f, 0.3f);
                }
            }
        }

        // This mod's posture / lean, last in the rotation block and about the origin (the feet). The
        // renderer applies the identical transform at the return of PlayerRenderer.setupRotations —
        // the same point in the same chain (PostureBodyMixin / LeanBodyRollMixin) — so the model and
        // these boxes cannot drift apart, and neither side touches state that outlives the frame.
        if (pose.posture == 2) {
            m.rotateX(MovementPosture.PRONE_PITCH);
            m.translate(MovementPosture.PRONE_TX, MovementPosture.PRONE_TY, MovementPosture.PRONE_TZ);
        } else if (pose.posture == 1) {
            m.translate(0f, MovementPosture.SIT_DROP, 0f);
        } else if (pose.leanRoll != 0f) {
            m.rotateZ(pose.leanRoll);
        }

        return m.scale(-1f, -1f, 1f)
                .scale(0.9375f)
                .translate(0f, -1.501f, 0f)
                .scale(1f / 16f);
    }

    private static final float FLIP_DEGREES = 90f;

    private static float sleepDirectionToRotation(Direction direction) {
        return switch (direction) {
            case SOUTH -> 90f;
            case WEST -> 0f;
            case NORTH -> 270f;
            case EAST -> 180f;
            default -> 0f;
        };
    }

    // ------------------------------------------------------------------
    // HumanoidModel.setupAnim
    // ------------------------------------------------------------------

    private static void animate(Rig rig, SkeletonPose pose) {
        boolean flying = pose.fallFlyingTicks > 4;
        boolean swimming = pose.visuallySwimming;

        rig.head.yRot = pose.headYaw * Mth.DEG_TO_RAD;
        if (flying) {
            rig.head.xRot = -Mth.HALF_PI / 2f;
        } else if (pose.swimAmount > 0.0f) {
            rig.head.xRot = swimming
                    ? rotlerpRad(pose.swimAmount, rig.head.xRot, -Mth.HALF_PI / 2f)
                    : rotlerpRad(pose.swimAmount, rig.head.xRot, pose.headPitch * Mth.DEG_TO_RAD);
        } else {
            rig.head.xRot = pose.headPitch * Mth.DEG_TO_RAD;
        }

        rig.body.yRot = 0.0f;
        rig.rightArm.z = 0.0f;
        rig.rightArm.x = -5.0f;
        rig.leftArm.z = 0.0f;
        rig.leftArm.x = 5.0f;

        float speedScale = 1.0f;
        if (flying) {
            speedScale = (float) pose.velocity.lengthSqr() / 0.2f;
            speedScale *= speedScale * speedScale;
        }
        if (speedScale < 1.0f) {
            speedScale = 1.0f;
        }

        float swing = pose.limbSwing;
        float amount = pose.limbSwingAmount;
        rig.rightArm.xRot = Mth.cos(swing * 0.6662f + Mth.PI) * 2.0f * amount * 0.5f / speedScale;
        rig.leftArm.xRot = Mth.cos(swing * 0.6662f) * 2.0f * amount * 0.5f / speedScale;
        rig.rightArm.zRot = 0.0f;
        rig.leftArm.zRot = 0.0f;
        rig.rightLeg.xRot = Mth.cos(swing * 0.6662f) * 1.4f * amount / speedScale;
        rig.leftLeg.xRot = Mth.cos(swing * 0.6662f + Mth.PI) * 1.4f * amount / speedScale;
        rig.rightLeg.yRot = 0.005f;
        rig.leftLeg.yRot = -0.005f;
        rig.rightLeg.zRot = 0.005f;
        rig.leftLeg.zRot = -0.005f;

        if (pose.riding) {
            rig.rightArm.xRot += -Mth.PI / 5f;
            rig.leftArm.xRot += -Mth.PI / 5f;
            rig.rightLeg.xRot = -1.4137167f;
            rig.rightLeg.yRot = Mth.PI / 10f;
            rig.rightLeg.zRot = 0.07853982f;
            rig.leftLeg.xRot = -1.4137167f;
            rig.leftLeg.yRot = -Mth.PI / 10f;
            rig.leftLeg.zRot = -0.07853982f;
        }

        rig.rightArm.yRot = 0.0f;
        rig.leftArm.yRot = 0.0f;

        boolean mainIsRight = pose.mainArm == HumanoidArm.RIGHT;
        if (pose.usingItem) {
            boolean usingMainHand = pose.usedItemHand == net.minecraft.world.InteractionHand.MAIN_HAND;
            if (usingMainHand == mainIsRight) {
                poseRightArm(rig, pose);
            } else {
                poseLeftArm(rig, pose);
            }
        } else {
            boolean twoHanded = mainIsRight ? pose.leftArmPose.isTwoHanded() : pose.rightArmPose.isTwoHanded();
            if (mainIsRight != twoHanded) {
                poseLeftArm(rig, pose);
                poseRightArm(rig, pose);
            } else {
                poseRightArm(rig, pose);
                poseLeftArm(rig, pose);
            }
        }

        setupAttackAnimation(rig, pose);

        if (pose.crouching) {
            rig.body.xRot = 0.5f;
            rig.rightArm.xRot += 0.4f;
            rig.leftArm.xRot += 0.4f;
            rig.rightLeg.z = 4.0f;
            rig.leftLeg.z = 4.0f;
            rig.rightLeg.y = 12.2f;
            rig.leftLeg.y = 12.2f;
            rig.head.y = 4.2f;
            rig.body.y = 3.2f;
            rig.leftArm.y = 5.2f;
            rig.rightArm.y = 5.2f;
        } else {
            rig.body.xRot = 0.0f;
            rig.rightLeg.z = 0.0f;
            rig.leftLeg.z = 0.0f;
            rig.rightLeg.y = 12.0f;
            rig.leftLeg.y = 12.0f;
            rig.head.y = 0.0f;
            rig.body.y = 0.0f;
            rig.leftArm.y = 2.0f;
            rig.rightArm.y = 2.0f;
        }

        if (pose.rightArmPose != SkeletonPose.ArmPose.SPYGLASS) {
            bobJoint(rig.rightArm, pose.ageInTicks, 1.0f);
        }
        if (pose.leftArmPose != SkeletonPose.ArmPose.SPYGLASS) {
            bobJoint(rig.leftArm, pose.ageInTicks, -1.0f);
        }

        if (pose.swimAmount > 0.0f) {
            animateSwimming(rig, pose);
        }

        // TaCZ mixes into setupAnim's TAIL, so its gun pose overrides everything above.
        if (pose.holdingGun) {
            animateGunPose(rig, pose);
        }

        // Leaning is not a bone pose — it tilts the whole body, and is applied in baseTransform.
        if (pose.leanLegTilt != 0f) {
            float tilt = pose.leanLegTilt * 20f * Mth.DEG_TO_RAD;
            if (pose.leanLegTilt >= 0) {
                rig.rightLeg.zRot += tilt;
            } else {
                rig.leftLeg.zRot += tilt;
            }
        }

        // Sit / prone bone bends, mirrored by PostureAnimMixin on the rendered model. Skipped in a
        // vehicle or bed, which own the pose. The whole-body drop/flat is in baseTransform.
        if (pose.posture != 0 && !pose.riding && !pose.sleeping) {
            applyPosture(rig, pose);
        }

        animateSuperbWarfare(rig, pose);
    }

    private static void applyPosture(Rig rig, SkeletonPose pose) {
        if (pose.posture == 1) {
            rig.rightLeg.xRot = MovementPosture.SIT_LEG_X;
            rig.rightLeg.yRot = MovementPosture.SIT_LEG_Y;
            rig.rightLeg.zRot = MovementPosture.SIT_LEG_Z;
            rig.leftLeg.xRot = MovementPosture.SIT_LEG_X;
            rig.leftLeg.yRot = -MovementPosture.SIT_LEG_Y;
            rig.leftLeg.zRot = -MovementPosture.SIT_LEG_Z;
        } else if (pose.posture == 2) {
            rig.head.xRot += MovementPosture.PRONE_HEAD_X;
            rig.rightArm.xRot = rig.rightArm.xRot * MovementPosture.PRONE_ARM_FACTOR + MovementPosture.PRONE_ARM_X;
            rig.leftArm.xRot = rig.leftArm.xRot * MovementPosture.PRONE_ARM_FACTOR + MovementPosture.PRONE_ARM_X;
            rig.rightArm.yRot = 0f;
            rig.leftArm.yRot = 0f;
            rig.rightLeg.xRot *= MovementPosture.PRONE_LEG_FACTOR;
            rig.leftLeg.xRot *= MovementPosture.PRONE_LEG_FACTOR;
        }
    }

    /**
     * Port of SuperbWarfare's {@code HumanoidModelMixin}, which injects into the same tail of
     * {@code setupAnim} — so it lands here, last, and overrides the gun pose above just as it does
     * on the client. Seats with no {@code "Pose"} keep the vanilla riding pose {@link #animate}
     * already applied through {@code pose.riding}.
     *
     * <p>ponytail: their parachute pose is not reproduced — cosmetic, and nobody is shooting a
     * parachutist's arm off. Add the same way if that turns out to be wrong.
     */
    private static void animateSuperbWarfare(Rig rig, SkeletonPose pose) {
        switch (pose.seatPose) {
            case "Pilot" -> {
                rig.head.xRot = 0f;
                rig.head.yRot = 0f;
                rig.head.zRot = 0f;
                rig.rightArm.xRot = -55f * Mth.DEG_TO_RAD;
                rig.rightArm.yRot = -15f * Mth.DEG_TO_RAD;
                rig.rightArm.zRot = -30f * Mth.DEG_TO_RAD;
            }
            case "Tow" -> {
                rig.head.xRot = 0f;
                rig.leftArm.yRot = 45f * Mth.DEG_TO_RAD;
                rig.leftArm.xRot = -115f * Mth.DEG_TO_RAD;
                rig.rightArm.yRot = 25f * Mth.DEG_TO_RAD;
                rig.rightArm.xRot = -115f * Mth.DEG_TO_RAD;
            }
            case "Climb" -> {
                rig.leftArm.xRot = -112.5f * Mth.DEG_TO_RAD;
                rig.rightArm.xRot = -112.5f * Mth.DEG_TO_RAD;
            }
            case "Stand" -> {
                zero(rig.leftLeg);
                zero(rig.rightLeg);
            }
            case "MachineGunStand" -> {
                rig.leftArm.xRot = -Mth.HALF_PI;
                rig.leftArm.yRot = 0f;
                rig.leftArm.zRot = 0f;
                rig.rightArm.xRot = -Mth.HALF_PI;
                rig.rightArm.yRot = 0f;
                rig.rightArm.zRot = 0f;
                zero(rig.leftLeg);
                zero(rig.rightLeg);
            }
            default -> {
            }
        }

        if (pose.sbwProne) {
            rig.head.xRot = (pose.viewXRot - 90f) * Mth.DEG_TO_RAD;
            rig.head.yRot = 0f;
            rig.leftArm.xRot = (-180f + pose.viewXRot) * Mth.DEG_TO_RAD;
            rig.rightArm.xRot = (-180f + pose.viewXRot) * Mth.DEG_TO_RAD;
            rig.leftArm.yRot = 0f;
            rig.rightArm.yRot = 0f;
            rig.leftArm.zRot = -30f * Mth.DEG_TO_RAD;
            rig.rightArm.zRot = 0f;
            rig.rightArm.x = -3f;
            rig.leftArm.x = 3f;
        }
    }

    private static void zero(Joint joint) {
        joint.xRot = 0f;
        joint.yRot = 0f;
        joint.zRot = 0f;
    }

    /**
     * Port of TaCZ's {@code ThirdPersonManager} "default" animation, blending hold into aim by the
     * server-computed ADS progress.
     *
     * <p>ponytail: the "minigun" variant and PlayerAnimator keyframes are not reproduced — the
     * animation id lives in a client-side display JSON. Every stock gun but the minigun uses
     * "default", so this is exact for all of them.
     */
    private static void animateGunPose(Rig rig, SkeletonPose pose) {
        if (pose.aimProgress <= 0f) {
            rig.rightArm.yRot = -0.3f + rig.head.yRot;
            rig.leftArm.yRot = 0.8f + rig.head.yRot;
            rig.rightArm.xRot = -1.4f + rig.head.xRot;
            rig.leftArm.xRot = -1.4f + rig.head.xRot;
        } else {
            float yaw = Mth.lerp(pose.aimProgress, 0.3f, 0.35f);
            float pitch = Mth.lerp(pose.aimProgress, 1.4f, 1.6f);
            rig.rightArm.yRot = -yaw + rig.head.yRot;
            rig.leftArm.yRot = 0.8f + rig.head.yRot;
            rig.rightArm.xRot = -pitch + rig.head.xRot;
            rig.leftArm.xRot = -pitch + rig.head.xRot;
        }
    }

    private static void animateSwimming(Rig rig, SkeletonPose pose) {
        float cycle = pose.limbSwing % 26.0f;
        float right = pose.attackArm == HumanoidArm.RIGHT && pose.attackTime > 0.0f ? 0.0f : pose.swimAmount;
        float left = pose.attackArm == HumanoidArm.LEFT && pose.attackTime > 0.0f ? 0.0f : pose.swimAmount;

        if (!pose.usingItem) {
            if (cycle < 14.0f) {
                rig.leftArm.xRot = rotlerpRad(left, rig.leftArm.xRot, 0.0f);
                rig.rightArm.xRot = Mth.lerp(right, rig.rightArm.xRot, 0.0f);
                rig.leftArm.yRot = rotlerpRad(left, rig.leftArm.yRot, Mth.PI);
                rig.rightArm.yRot = Mth.lerp(right, rig.rightArm.yRot, Mth.PI);
                rig.leftArm.zRot = rotlerpRad(left, rig.leftArm.zRot,
                        Mth.PI + 1.8707964f * quadraticArmUpdate(cycle) / quadraticArmUpdate(14.0f));
                rig.rightArm.zRot = Mth.lerp(right, rig.rightArm.zRot,
                        Mth.PI - 1.8707964f * quadraticArmUpdate(cycle) / quadraticArmUpdate(14.0f));
            } else if (cycle < 22.0f) {
                float t = (cycle - 14.0f) / 8.0f;
                rig.leftArm.xRot = rotlerpRad(left, rig.leftArm.xRot, Mth.HALF_PI * t);
                rig.rightArm.xRot = Mth.lerp(right, rig.rightArm.xRot, Mth.HALF_PI * t);
                rig.leftArm.yRot = rotlerpRad(left, rig.leftArm.yRot, Mth.PI);
                rig.rightArm.yRot = Mth.lerp(right, rig.rightArm.yRot, Mth.PI);
                rig.leftArm.zRot = rotlerpRad(left, rig.leftArm.zRot, 5.012389f - 1.8707964f * t);
                rig.rightArm.zRot = Mth.lerp(right, rig.rightArm.zRot, 1.2707963f + 1.8707964f * t);
            } else if (cycle < 26.0f) {
                float t = (cycle - 22.0f) / 4.0f;
                rig.leftArm.xRot = rotlerpRad(left, rig.leftArm.xRot, Mth.HALF_PI - Mth.HALF_PI * t);
                rig.rightArm.xRot = Mth.lerp(right, rig.rightArm.xRot, Mth.HALF_PI - Mth.HALF_PI * t);
                rig.leftArm.yRot = rotlerpRad(left, rig.leftArm.yRot, Mth.PI);
                rig.rightArm.yRot = Mth.lerp(right, rig.rightArm.yRot, Mth.PI);
                rig.leftArm.zRot = rotlerpRad(left, rig.leftArm.zRot, Mth.PI);
                rig.rightArm.zRot = Mth.lerp(right, rig.rightArm.zRot, Mth.PI);
            }
        }

        rig.leftLeg.xRot = Mth.lerp(pose.swimAmount, rig.leftLeg.xRot,
                0.3f * Mth.cos(pose.limbSwing * 0.33333334f + Mth.PI));
        rig.rightLeg.xRot = Mth.lerp(pose.swimAmount, rig.rightLeg.xRot,
                0.3f * Mth.cos(pose.limbSwing * 0.33333334f));
    }

    private static void setupAttackAnimation(Rig rig, SkeletonPose pose) {
        if (pose.attackTime <= 0.0f) {
            return;
        }
        Joint attacking = rig.arm(pose.attackArm);
        float t = pose.attackTime;
        rig.body.yRot = Mth.sin(Mth.sqrt(t) * Mth.TWO_PI) * 0.2f;
        if (pose.attackArm == HumanoidArm.LEFT) {
            rig.body.yRot *= -1.0f;
        }

        rig.rightArm.z = Mth.sin(rig.body.yRot) * 5.0f;
        rig.rightArm.x = -Mth.cos(rig.body.yRot) * 5.0f;
        rig.leftArm.z = -Mth.sin(rig.body.yRot) * 5.0f;
        rig.leftArm.x = Mth.cos(rig.body.yRot) * 5.0f;
        rig.rightArm.yRot += rig.body.yRot;
        rig.leftArm.yRot += rig.body.yRot;
        rig.leftArm.xRot += rig.body.yRot;

        t = 1.0f - pose.attackTime;
        t *= t;
        t *= t;
        t = 1.0f - t;
        float f1 = Mth.sin(t * Mth.PI);
        float f2 = Mth.sin(pose.attackTime * Mth.PI) * -(rig.head.xRot - 0.7f) * 0.75f;
        attacking.xRot -= f1 * 1.2f + f2;
        attacking.yRot += rig.body.yRot * 2.0f;
        attacking.zRot += Mth.sin(pose.attackTime * Mth.PI) * -0.4f;
    }

    private static void poseRightArm(Rig rig, SkeletonPose pose) {
        switch (pose.rightArmPose) {
            case EMPTY -> rig.rightArm.yRot = 0.0f;
            case ITEM -> {
                rig.rightArm.xRot = rig.rightArm.xRot * 0.5f - Mth.PI / 10f;
                rig.rightArm.yRot = 0.0f;
            }
            case BLOCK -> poseBlockingArm(rig, rig.rightArm, true);
            case BOW_AND_ARROW -> {
                rig.rightArm.yRot = -0.1f + rig.head.yRot;
                rig.leftArm.yRot = 0.1f + rig.head.yRot + 0.4f;
                rig.rightArm.xRot = -Mth.HALF_PI + rig.head.xRot;
                rig.leftArm.xRot = -Mth.HALF_PI + rig.head.xRot;
            }
            case THROW_SPEAR -> {
                rig.rightArm.xRot = rig.rightArm.xRot * 0.5f - Mth.PI;
                rig.rightArm.yRot = 0.0f;
            }
            case CROSSBOW_CHARGE -> animateCrossbowCharge(rig, pose, true);
            case CROSSBOW_HOLD -> animateCrossbowHold(rig, true);
            case SPYGLASS -> {
                rig.rightArm.xRot = Mth.clamp(rig.head.xRot - 1.9198622f
                        - (pose.crouching ? Mth.PI / 12f : 0.0f), -2.4f, 3.3f);
                rig.rightArm.yRot = rig.head.yRot - Mth.PI / 12f;
            }
            case TOOT_HORN -> {
                rig.rightArm.xRot = Mth.clamp(rig.head.xRot, -1.2f, 1.2f) - 1.4835298f;
                rig.rightArm.yRot = rig.head.yRot - Mth.PI / 6f;
            }
            case BRUSH -> {
                rig.rightArm.xRot = rig.rightArm.xRot * 0.5f - Mth.PI / 5f;
                rig.rightArm.yRot = 0.0f;
            }
        }
    }

    private static void poseLeftArm(Rig rig, SkeletonPose pose) {
        switch (pose.leftArmPose) {
            case EMPTY -> rig.leftArm.yRot = 0.0f;
            case ITEM -> {
                rig.leftArm.xRot = rig.leftArm.xRot * 0.5f - Mth.PI / 10f;
                rig.leftArm.yRot = 0.0f;
            }
            case BLOCK -> poseBlockingArm(rig, rig.leftArm, false);
            case BOW_AND_ARROW -> {
                rig.rightArm.yRot = -0.1f + rig.head.yRot - 0.4f;
                rig.leftArm.yRot = 0.1f + rig.head.yRot;
                rig.rightArm.xRot = -Mth.HALF_PI + rig.head.xRot;
                rig.leftArm.xRot = -Mth.HALF_PI + rig.head.xRot;
            }
            case THROW_SPEAR -> {
                rig.leftArm.xRot = rig.leftArm.xRot * 0.5f - Mth.PI;
                rig.leftArm.yRot = 0.0f;
            }
            case CROSSBOW_CHARGE -> animateCrossbowCharge(rig, pose, false);
            case CROSSBOW_HOLD -> animateCrossbowHold(rig, false);
            case SPYGLASS -> {
                rig.leftArm.xRot = Mth.clamp(rig.head.xRot - 1.9198622f
                        - (pose.crouching ? Mth.PI / 12f : 0.0f), -2.4f, 3.3f);
                rig.leftArm.yRot = rig.head.yRot + Mth.PI / 12f;
            }
            case TOOT_HORN -> {
                rig.leftArm.xRot = Mth.clamp(rig.head.xRot, -1.2f, 1.2f) - 1.4835298f;
                rig.leftArm.yRot = rig.head.yRot + Mth.PI / 6f;
            }
            case BRUSH -> {
                rig.leftArm.xRot = rig.leftArm.xRot * 0.5f - Mth.PI / 5f;
                rig.leftArm.yRot = 0.0f;
            }
        }
    }

    private static void poseBlockingArm(Rig rig, Joint arm, boolean right) {
        arm.xRot = arm.xRot * 0.5f - 0.9424779f
                + Mth.clamp(rig.head.xRot, -Mth.PI * 4.0f / 9.0f, 0.43633232f);
        arm.yRot = (right ? -30.0f : 30.0f) * Mth.DEG_TO_RAD
                + Mth.clamp(rig.head.yRot, -Mth.PI / 6f, Mth.PI / 6f);
    }

    private static void animateCrossbowHold(Rig rig, boolean right) {
        Joint holding = right ? rig.rightArm : rig.leftArm;
        Joint other = right ? rig.leftArm : rig.rightArm;
        holding.yRot = (right ? -0.3f : 0.3f) + rig.head.yRot;
        other.yRot = (right ? 0.6f : -0.6f) + rig.head.yRot;
        holding.xRot = -Mth.HALF_PI + rig.head.xRot + 0.1f;
        other.xRot = -1.5f + rig.head.xRot;
    }

    private static void animateCrossbowCharge(Rig rig, SkeletonPose pose, boolean right) {
        Joint charging = right ? rig.rightArm : rig.leftArm;
        Joint other = right ? rig.leftArm : rig.rightArm;
        charging.yRot = right ? -0.8f : 0.8f;
        charging.xRot = -0.97079635f;
        other.xRot = charging.xRot;
        other.yRot = Mth.lerp(pose.crossbowCharge, 0.4f, 0.85f) * (right ? 1 : -1);
        other.xRot = Mth.lerp(pose.crossbowCharge, other.xRot, -Mth.HALF_PI);
    }

    private static void bobJoint(Joint joint, float ageInTicks, float direction) {
        joint.zRot += direction * (Mth.cos(ageInTicks * 0.09f) * 0.05f + 0.05f);
        joint.xRot += direction * Mth.sin(ageInTicks * 0.067f) * 0.05f;
    }

    private static float rotlerpRad(float delta, float from, float to) {
        float diff = (to - from) % Mth.TWO_PI;
        if (diff < -Mth.PI) {
            diff += Mth.TWO_PI;
        }
        if (diff >= Mth.PI) {
            diff -= Mth.TWO_PI;
        }
        return from + delta * diff;
    }

    private static float quadraticArmUpdate(float value) {
        return -65.0f * value + value * value;
    }

    private static Vector3f relative(Vec3 point, Vec3 anchor) {
        return new Vector3f((float) (point.x - anchor.x), (float) (point.y - anchor.y), (float) (point.z - anchor.z));
    }
}

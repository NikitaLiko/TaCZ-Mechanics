package ru.liko.tacz_mechanics.hitbox;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.joml.Quaternionf;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.movement.LeanCollision;
import ru.liko.tacz_mechanics.movement.MovementStateManager;

/**
 * Every input {@code HumanoidModel.setupAnim} and {@code LivingEntityRenderer.setupRotations} read,
 * pulled off state the server already has.
 *
 * <p>Mutable fields rather than a record on purpose: this mirrors how the vanilla model carries its
 * own animation inputs, keeps {@link #of} a flat transcription, and lets tests tweak one thing off
 * {@link #standing()} instead of threading two dozen constructor arguments.
 */
public final class SkeletonPose {

    /** Server-side stand-in for the client-only {@code HumanoidModel.ArmPose}. */
    public enum ArmPose {
        EMPTY, ITEM, BLOCK, BOW_AND_ARROW, THROW_SPEAR,
        CROSSBOW_CHARGE, CROSSBOW_HOLD, SPYGLASS, TOOT_HORN, BRUSH;

        public boolean isTwoHanded() {
            return this == BOW_AND_ARROW || this == CROSSBOW_HOLD || this == CROSSBOW_CHARGE || this == THROW_SPEAR;
        }
    }

    // --- body orientation -------------------------------------------------
    public float bodyYRot;
    public float headYaw;
    public float headPitch;
    public float scale = 1f;

    // --- walk cycle -------------------------------------------------------
    public float limbSwing;
    public float limbSwingAmount;
    public float ageInTicks;

    // --- whole-body poses -------------------------------------------------
    public boolean crouching;
    public boolean riding;
    public boolean sleeping;
    public Direction bedDirection;
    public int deathTime;
    public boolean autoSpinAttack;
    public boolean fallFlying;
    public float fallFlyingTicks;
    public float swimAmount;
    public boolean visuallySwimming;
    public boolean swimmingInFluid;
    public float viewXRot;
    public boolean upsideDown;
    public Vec3 viewVector = new Vec3(0, 0, 1);
    public Vec3 velocity = Vec3.ZERO;
    /** Sleeping shifts the model off its feet position towards the head of the bed. */
    public Vec3 bedOffset = Vec3.ZERO;
    /**
     * {@code PlayerRenderer.getRenderOffset} drops a crouching player's whole model. It is applied by
     * the render dispatcher, outside setupRotations, so it is easy to miss — and missing it leaves
     * the skeleton floating above a crouching player.
     */
    public float renderOffsetY;

    // --- arms -------------------------------------------------------------
    public float attackTime;
    public HumanoidArm attackArm = HumanoidArm.RIGHT;
    public HumanoidArm mainArm = HumanoidArm.RIGHT;
    public boolean usingItem;
    public InteractionHand usedItemHand = InteractionHand.MAIN_HAND;
    public ArmPose rightArmPose = ArmPose.EMPTY;
    public ArmPose leftArmPose = ArmPose.EMPTY;
    /** 0..1 crossbow pull progress, the one arm pose that needs more than an enum. */
    public float crossbowCharge;

    // --- TaCZ -------------------------------------------------------------
    /** True when TaCZ's third-person gun pose overrides the arms. */
    public boolean holdingGun;
    /** 0..1 ADS blend. The server computes this itself and broadcasts it, so it is authoritative. */
    public float aimProgress;

    // --- this mod ---------------------------------------------------------
    /**
     * Lean as a whole-body roll in radians, applied to the model as a whole rather than to any bone.
     * Used when {@code movement.leanBodyRoll} is on, and read verbatim by both the renderer and this
     * skeleton so the two cannot disagree.
     */
    public float leanRoll;
    /** Lean as the original leg bend, -1..1, used when {@code movement.leanBodyRoll} is off. */
    public float leanLegTilt;
    /** 0 = none, 1 = sit, 2 = prone. Mutually exclusive with the lean above. */
    public int posture;

    // --- SuperbWarfare ----------------------------------------------------
    /**
     * Root rotation of a player sitting in a SuperbWarfare vehicle. Non-null replaces the whole
     * vanilla rotation block, because their renderer cancels {@code setupRotations} rather than
     * adding to it.
     */
    public Quaternionf seatRotation;
    /** Their {@code PassengerRenderScale}; 1 for every vehicle but five. */
    public float seatScale = 1f;
    /** Their per-seat {@code "Pose"} bone override, "Default" or empty when there is none. */
    public String seatPose = "";
    /** Their prone-with-gun pose, which applies outside vehicles too. */
    public boolean sbwProne;

    /** Holder so the lookup stays off the class initializer, which unit tests reach with no FML. */
    private static final class Sbw {
        static final boolean LOADED = ModList.get().isLoaded("superbwarfare");
    }

    /** A player standing still, facing +Z, holding nothing. */
    public static SkeletonPose standing() {
        return new SkeletonPose();
    }

    public static SkeletonPose of(Player player) {
        SkeletonPose pose = new SkeletonPose();

        // LivingEntityRenderer.render: riders take their body yaw from the vehicle, head clamped to +-85
        boolean shouldSit = player.isPassenger()
                && player.getVehicle() != null
                && player.getVehicle().shouldRiderSit();
        float bodyRot = player.yBodyRot;
        float headRot = player.getYHeadRot();
        if (shouldSit && player.getVehicle() instanceof LivingEntity vehicle) {
            bodyRot = vehicle.yBodyRot;
            float delta = Mth.clamp(Mth.wrapDegrees(headRot - bodyRot), -85f, 85f);
            bodyRot = headRot - delta;
            if (delta * delta > 2500f) {
                bodyRot += delta * 0.2f;
            }
        }

        pose.upsideDown = isUpsideDown(player);
        pose.bodyYRot = bodyRot;
        pose.headYaw = Mth.wrapDegrees(headRot - bodyRot) * (pose.upsideDown ? -1f : 1f);
        pose.headPitch = player.getXRot() * (pose.upsideDown ? -1f : 1f);
        pose.scale = player.getScale();

        pose.limbSwing = player.walkAnimation.position();
        pose.limbSwingAmount = Math.min(player.walkAnimation.speed(), 1.0f);
        pose.ageInTicks = player.tickCount;

        pose.crouching = player.isCrouching();
        pose.renderOffsetY = pose.crouching ? player.getScale() * -2.0f / 16.0f : 0f;
        pose.riding = shouldSit;
        pose.sleeping = player.isSleeping();
        pose.bedDirection = player.getBedOrientation();
        if (pose.sleeping && pose.bedDirection != null) {
            float reach = player.getEyeHeight(net.minecraft.world.entity.Pose.STANDING) - 0.1f;
            pose.bedOffset = new Vec3(-pose.bedDirection.getStepX() * reach, 0,
                    -pose.bedDirection.getStepZ() * reach);
        }
        pose.deathTime = player.deathTime;
        pose.autoSpinAttack = player.isAutoSpinAttack();
        pose.fallFlying = player.isFallFlying();
        pose.fallFlyingTicks = player.getFallFlyingTicks();
        pose.swimAmount = player.getSwimAmount(1.0f);
        pose.visuallySwimming = player.isVisuallySwimming();
        pose.swimmingInFluid = player.isInWater();
        pose.viewXRot = player.getViewXRot(1.0f);
        pose.viewVector = player.getViewVector(1.0f);
        // Not getDeltaMovement: player movement is client-authoritative, so on the server that field
        // is not the real velocity. Position delta is, and is what TaCZ's HitboxHelper uses too.
        pose.velocity = new Vec3(player.getX() - player.xOld, player.getY() - player.yOld,
                player.getZ() - player.zOld);

        pose.attackTime = player.getAttackAnim(1.0f);
        pose.mainArm = player.getMainArm();
        pose.attackArm = player.swingingArm == InteractionHand.MAIN_HAND
                ? pose.mainArm
                : pose.mainArm.getOpposite();
        pose.usingItem = player.isUsingItem();
        pose.usedItemHand = player.getUsedItemHand();
        // PlayerRenderer.setModelProperties: a two-handed main pose overrides whatever the off hand
        // would otherwise have done.
        ArmPose mainPose = armPose(player, InteractionHand.MAIN_HAND);
        ArmPose offPose = armPose(player, InteractionHand.OFF_HAND);
        if (mainPose.isTwoHanded()) {
            offPose = player.getOffhandItem().isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
        }
        if (pose.mainArm == HumanoidArm.RIGHT) {
            pose.rightArmPose = mainPose;
            pose.leftArmPose = offPose;
        } else {
            pose.rightArmPose = offPose;
            pose.leftArmPose = mainPose;
        }
        pose.crossbowCharge = crossbowCharge(player);

        // TaCZ's InnerThirdPersonManager gate, verbatim
        pose.holdingGun = IGun.getIGunOrNull(player.getMainHandItem()) != null
                && !pose.sleeping
                && !player.onClimbable()
                && !player.isSwimming()
                && player.getPose() != net.minecraft.world.entity.Pose.FALL_FLYING;
        pose.aimProgress = pose.holdingGun ? IGunOperator.fromLivingEntity(player).getSynAimingProgress() : 0f;

        var moveState = MovementStateManager.get(player.getUUID());
        if (Config.Movement.enabled && moveState != null) {
            pose.posture = moveState.isSitting() ? 1 : moveState.isProne() ? 2 : 0;
        }
        // Posture and lean are mutually exclusive; only read the lean while standing.
        if (pose.posture == 0) {
            if (Config.Movement.leanBodyRoll) {
                pose.leanRoll = leanRoll(player);
            } else {
                pose.leanLegTilt = Config.Movement.enabled && moveState != null ? moveState.getProbeOffset() : 0f;
            }
        }

        if (Sbw.LOADED) {
            SbwPose.apply(player, pose);
        }
        return pose;
    }

    /**
     * Leaning shifts the eye sideways by {@code offsetScale()} blocks without lowering it, which is
     * geometrically a roll about the feet. Deriving the angle from that same offset is what keeps
     * the hitbox, the camera and the rendered model on the same lean.
     */
    public static float leanRoll(Player player) {
        if (!Config.Movement.enabled) {
            return 0f;
        }
        var state = MovementStateManager.get(player.getUUID());
        return state == null ? 0f : leanRoll(player, state.getProbeOffset());
    }

    /** Overload for the renderer, which interpolates the probe offset across the tick. */
    public static float leanRoll(Player player, float probeOffset) {
        if (!Config.Movement.enabled) {
            return 0f;
        }
        float offset = LeanCollision.safeProbeOffset(player, probeOffset);
        if (offset == 0f) {
            return 0f;
        }
        return leanRollFor(offset, Math.max(player.getEyeHeight(), 0.1f), LeanCollision.offsetScale());
    }

    /**
     * The lean angle on its own, so the sign is under test without needing a live player.
     *
     * <p>Negative because the eye offset is applied opposite to the facing direction, so the body has
     * to roll the other way round the Z axis to follow it. The direction is pinned by test rather
     * than by argument — it has been wrong once already.
     *
     * @param safeOffset   collision-clamped lean, -1..1
     * @param leanDistance sideways reach of a full lean, in blocks
     */
    public static float leanRollFor(float safeOffset, float eyeHeight, double leanDistance) {
        double sin = Mth.clamp(safeOffset * leanDistance / eyeHeight, -1.0, 1.0);
        return (float) -Math.asin(sin);
    }

    /** Mirrors {@code PlayerRenderer.getArmPose}, minus the client-only item extension hook. */
    private static ArmPose armPose(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return ArmPose.EMPTY;
        }
        if (player.getUsedItemHand() == hand && player.getUseItemRemainingTicks() > 0) {
            UseAnim anim = stack.getUseAnimation();
            switch (anim) {
                case BLOCK:
                    return ArmPose.BLOCK;
                case BOW:
                    return ArmPose.BOW_AND_ARROW;
                case SPEAR:
                    return ArmPose.THROW_SPEAR;
                case CROSSBOW:
                    return ArmPose.CROSSBOW_CHARGE;
                case SPYGLASS:
                    return ArmPose.SPYGLASS;
                case TOOT_HORN:
                    return ArmPose.TOOT_HORN;
                case BRUSH:
                    return ArmPose.BRUSH;
                default:
                    break;
            }
        } else if (!player.swinging && stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            return ArmPose.CROSSBOW_HOLD;
        }
        return ArmPose.ITEM;
    }

    private static float crossbowCharge(Player player) {
        ItemStack using = player.getUseItem();
        if (!(using.getItem() instanceof CrossbowItem)) {
            return 0f;
        }
        float duration = CrossbowItem.getChargeDuration(using, player);
        if (duration <= 0f) {
            return 0f;
        }
        return Mth.clamp(player.getTicksUsingItem(), 0f, duration) / duration;
    }

    /** Mirrors {@code LivingEntityRenderer.isEntityUpsideDown}; the cape flag is synced entity data. */
    private static boolean isUpsideDown(Player player) {
        String name = ChatFormatting.stripFormatting(player.getName().getString());
        return ("Dinnerbone".equals(name) || "Grumm".equals(name))
                && player.isModelPartShown(PlayerModelPart.CAPE);
    }
}

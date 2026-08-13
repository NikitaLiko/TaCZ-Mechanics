package ru.liko.tacz_mechanics.client.recoil;

import com.tacz.guns.api.event.common.GunFireEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.client.freeaim.FreeAimHandler;
import ru.liko.tacz_mechanics.client.freeaim.RecoilSource;

/**
 * Residual recoil — the part TaCZ does not have.
 *
 * <p>TaCZ's camera recoil ({@code CameraSetupEvent#applyCameraRecoil}) drives the real view angles
 * along a spline whose last keyframe returns to the starting point, so a burst ends aimed exactly
 * where it began: spray is free. This adds a kick that is never taken back — the shooter has to
 * pull the aim down themselves — and randomises the horizontal direction across a burst so the
 * pattern cannot be memorised and counter-dragged.
 *
 * <p>Runs purely client-side: the client owns its own look direction anyway, and the movement
 * packet it sends carries the rotation we leave behind.
 */
@EventBusSubscriber(modid = TaczMechanics.MODID, value = Dist.CLIENT)
public final class RecoilDrift {

    /** Ticks without a shot after which the next burst picks a fresh horizontal direction. */
    private static final int BURST_RESET_TICKS = 20;
    /** How far the horizontal direction may wander from shot to shot inside one burst. */
    private static final float WANDER_STEP = 0.6f;
    /** Below this many degrees the leftover kick is dropped instead of trickling forever. */
    private static final float RESIDUAL_EPSILON = 0.002f;

    private static float pendingPitch;
    private static float pendingYaw;
    private static float wander;
    private static int idleTicks = BURST_RESET_TICKS;

    private RecoilDrift() {
    }

    @SubscribeEvent
    public static void onGunFire(GunFireEvent event) {
        if (!Config.Recoil.residualEnabled) {
            return;
        }
        if (event.getLogicalSide() != LogicalSide.CLIENT) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player != event.getShooter()) {
            return;
        }

        RandomSource random = player.getRandom();
        wander = nextWander(wander, idleTicks >= BURST_RESET_TICKS, random.nextFloat() * 2f - 1f);
        idleTicks = 0;

        // Per-gun strength comes from the gun's own TaCZ recoil curve, same source the free-aim kick uses.
        float[] factors = RecoilSource.factors(player.getMainHandItem());
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float aiming = FreeAimHandler.getInstance().aimingProgress(partialTick);
        float braced = Mth.lerp(aiming, 1f, (float) Config.Recoil.adsMultiplier);
        float variance = (float) Config.Recoil.variance;
        float jitter = 1f + (random.nextFloat() * 2f - 1f) * variance;

        pendingPitch += (float) Config.Recoil.pitchDegrees * factors[0] * jitter * braced;
        pendingYaw += (float) Config.Recoil.yawDegrees * factors[1] * wander * braced;
    }

    /**
     * Horizontal direction of the next shot, in [-1, 1]. A fresh burst starts from a random
     * direction; inside a burst the direction random-walks, which is what makes the climb
     * unlearnable — a fixed left-then-right pattern would just be counter-dragged.
     */
    public static float nextWander(float current, boolean freshBurst, float roll) {
        if (freshBurst) {
            return Mth.clamp(roll, -1f, 1f);
        }
        return Mth.clamp(current + roll * WANDER_STEP, -1f, 1f);
    }

    /** Feeds the outstanding kick into the real view angles a fraction at a time. */
    public static void tick() {
        if (idleTicks < BURST_RESET_TICKS) {
            idleTicks++;
        }
        if (Math.abs(pendingPitch) < RESIDUAL_EPSILON && Math.abs(pendingYaw) < RESIDUAL_EPSILON) {
            pendingPitch = 0f;
            pendingYaw = 0f;
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            reset();
            return;
        }

        float follow = (float) Config.Recoil.followSpeed;
        float stepPitch = pendingPitch * follow;
        float stepYaw = pendingYaw * follow;
        // Deliberately not touching xRotO/yRotO: leaving them behind lets the renderer interpolate
        // the kick over the frame instead of teleporting the view, the same way TaCZ applies its own.
        player.setXRot(Mth.clamp(player.getXRot() - stepPitch, -90f, 90f));
        player.setYRot(player.getYRot() + stepYaw);
        pendingPitch -= stepPitch;
        pendingYaw -= stepYaw;
    }

    public static void reset() {
        pendingPitch = 0f;
        pendingYaw = 0f;
        wander = 0f;
        idleTicks = BURST_RESET_TICKS;
    }
}

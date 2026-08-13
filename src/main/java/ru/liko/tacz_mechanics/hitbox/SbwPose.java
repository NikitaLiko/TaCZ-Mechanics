package ru.liko.tacz_mechanics.hitbox;

import com.atsuishio.superbwarfare.data.vehicle.subdata.SeatInfo;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.entity.vehicle.utils.VehicleVecUtils;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.mojang.math.Axis;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaterniond;
import org.joml.Quaternionf;

import java.util.List;

/**
 * SuperbWarfare's overrides of the player pose, read off state the server already owns.
 *
 * <p>Every SuperbWarfare type in this mod lives in this one class, so the rest of the hitbox code
 * loads fine without the mod — {@link SkeletonPose} only reaches in here behind
 * {@link SkeletonPose#SBW_LOADED}.
 */
final class SbwPose {

    private SbwPose() {
    }

    static void apply(Player player, SkeletonPose pose) {
        // SuperbWarfare's prone: HumanoidModelMixin, tail of setupAnim, gated on the SWIMMING pose it
        // forces without the player actually swimming.
        pose.sbwProne = player.getMainHandItem().getItem() instanceof GunItem
                && player.getPose() == Pose.SWIMMING
                && !player.isSwimming();

        if (!(player.getVehicle() instanceof VehicleEntity vehicle)) {
            return;
        }
        int index = vehicle.getSeatIndex(player);
        List<SeatInfo> seats = vehicle.computed().seats();
        if (index < 0 || index >= seats.size()) {
            return;
        }
        pose.seatPose = seats.get(index).pose;
        pose.seatScale = vehicle.getPassengerRenderScale();

        // LivingEntityRendererMixin cancels vanilla setupRotations outright and parents the whole model
        // to the seat's transform, so this is the entire root rotation, not an addition to it.
        float transformYaw = (float) VehicleVecUtils
                .getYRotFromVector(vehicle.getTransformDirectionNoOrientation(1f, player));
        Quaterniond seat = vehicle.getRotationFromString(seats.get(index).transform, 1f)
                .mul(new Quaterniond(Axis.YP.rotationDegrees(-transformYaw)));
        pose.seatRotation = new Quaternionf((float) seat.x, (float) seat.y, (float) seat.z, (float) seat.w);
    }
}

package ru.liko.tacz_mechanics.client.hitbox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.hitbox.PlayerSkeleton;
import ru.liko.tacz_mechanics.hitbox.SkeletonPose;
import ru.liko.tacz_mechanics.movement.PlayerState;
import ru.liko.tacz_mechanics.movement.client.MovementClientHandler;

/**
 * Draws the skeleton the server shoots at, so you can check it lines up with the rendered model.
 *
 * <p>Uses the same un-interpolated pose the server does, so the boxes lag the model by up to a tick
 * while moving — that gap is real, not a rendering artefact.
 */
@EventBusSubscriber(modid = TaczMechanics.MODID, value = Dist.CLIENT)
public final class HitboxDebugRenderer {

    private static final int[][] EDGES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7},
            {0, 2}, {1, 3}, {4, 6}, {5, 7},
            {0, 4}, {1, 5}, {2, 6}, {3, 7},
    };

    private static boolean toggledOn;

    private HitboxDebugRenderer() {
    }

    /** @return the new state, so the command can report it */
    public static boolean toggle() {
        toggledOn = !toggledOn;
        return toggledOn;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (!toggledOn && !Config.Hitbox.debugRender) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        for (Player player : minecraft.level.players()) {
            // your own skeleton would be drawn around the camera in first person
            if (player == minecraft.player && !event.getCamera().isDetached()) {
                continue;
            }
            Vec3 anchor = new Vec3(
                    Mth.lerp(partial, player.xo, player.getX()),
                    Mth.lerp(partial, player.yo, player.getY()),
                    Mth.lerp(partial, player.zo, player.getZ()));

            poseStack.pushPose();
            poseStack.translate(anchor.x - camera.x, anchor.y - camera.y, anchor.z - camera.z);
            for (PlayerSkeleton.OrientedBox box : PlayerSkeleton.boxes(poseFor(player, partial))) {
                drawBox(poseStack.last(), lines, box);
            }
            poseStack.popPose();
        }
        buffers.endBatch(RenderType.lines());
    }

    /**
     * The server-side lean map is empty on the client, where other players' lean arrives through
     * {@link MovementClientHandler}. Patching it in keeps the overlay honest about leaning.
     */
    private static SkeletonPose poseFor(Player player, float partial) {
        SkeletonPose pose = SkeletonPose.of(player);
        PlayerState state = MovementClientHandler.getStateForPlayer(player);
        if (state == null) {
            return pose;
        }
        float probeOffset = Mth.lerp(partial, state.getProbeOffsetOld(), state.getProbeOffset());
        if (Config.Movement.leanBodyRoll) {
            pose.leanRoll = SkeletonPose.leanRoll(player, probeOffset);
        } else {
            pose.leanLegTilt = Config.Movement.enabled ? probeOffset : 0f;
        }
        return pose;
    }

    private static void drawBox(PoseStack.Pose pose, VertexConsumer lines, PlayerSkeleton.OrientedBox box) {
        Vector3f[] corners = corners(box.transform(), box.local());
        float red = box.part() == PlayerSkeleton.Part.HEAD ? 1f : box.part() == PlayerSkeleton.Part.LEG ? 1f : 0f;
        float green = box.part() == PlayerSkeleton.Part.TORSO ? 1f : box.part() == PlayerSkeleton.Part.LEG ? 1f : 0f;
        float blue = box.part() == PlayerSkeleton.Part.ARM ? 1f : 0f;
        for (int[] edge : EDGES) {
            line(pose, lines, corners[edge[0]], corners[edge[1]], red, green, blue);
        }
    }

    /** Corner index is a bit field: x -> 4, y -> 2, z -> 1, which is what {@link #EDGES} assumes. */
    private static Vector3f[] corners(Matrix4f transform, AABB box) {
        Vector3f[] corners = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = transform.transformPosition(new Vector3f(
                    (float) ((i & 4) != 0 ? box.maxX : box.minX),
                    (float) ((i & 2) != 0 ? box.maxY : box.minY),
                    (float) ((i & 1) != 0 ? box.maxZ : box.minZ)));
        }
        return corners;
    }

    private static void line(PoseStack.Pose pose, VertexConsumer lines, Vector3f from, Vector3f to,
                             float red, float green, float blue) {
        Vector3f normal = new Vector3f(to).sub(from).normalize();
        lines.addVertex(pose, from.x, from.y, from.z).setColor(red, green, blue, 1f)
                .setNormal(pose, normal.x, normal.y, normal.z);
        lines.addVertex(pose, to.x, to.y, to.z).setColor(red, green, blue, 1f)
                .setNormal(pose, normal.x, normal.y, normal.z);
    }
}

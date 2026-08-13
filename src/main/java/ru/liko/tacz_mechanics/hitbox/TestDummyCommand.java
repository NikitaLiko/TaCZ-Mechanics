package ru.liko.tacz_mechanics.hitbox;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.Connection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.movement.MovementPosture;
import ru.liko.tacz_mechanics.movement.MovementStateManager;
import ru.liko.tacz_mechanics.movement.PlayerState;
import ru.liko.tacz_mechanics.movement.network.MovementStateBroadcastPayload;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code /taczmech dummy [sit|prone] | clear} — spawns a stationary {@link FakePlayer} to shoot at
 * while testing the OBB skeleton hitbox.
 *
 * <p>The target has to be a {@code ServerPlayer} (that is what {@code EntityUtilHitboxMixin} keys
 * on) and has to live in the level's entity storage so TaCZ's {@code getEntities} candidate search
 * finds it — {@link ServerLevel#addNewPlayer} gives both, plus client-side tracking so it renders.
 * Invulnerable so it survives repeated fire; a hit still registers because the OBB test runs before
 * damage. Enable {@code hitbox.debugRender} to log each shot's HIT/MISS.
 */
@EventBusSubscriber(modid = TaczMechanics.MODID)
public final class TestDummyCommand {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<FakePlayer> DUMMIES = new ArrayList<>();
    private static int counter = 0;

    /**
     * Gives the fake player's connection a real (but network-less) {@link EmbeddedChannel}. Its channel
     * is null by default, which makes mods that push packets to a joining player — JourneyMap and the
     * common-networking layer — NPE on {@code connection.channel().attr(...)}. A live embedded channel
     * satisfies those lookups (packets are just buffered and dropped), letting the normal
     * {@code addNewPlayer} path run — which is the one Moonrise's rewritten entity storage hooks, unlike
     * the raw {@code entityManager} field (Moonrise leaves that null).
     */
    private static void giveFakeChannel(FakePlayer fake) {
        try {
            Field connField = fieldOfType(fake.connection.getClass(), Connection.class);
            if (connField == null) {
                return;
            }
            Connection conn = (Connection) connField.get(fake.connection);
            Field channelField = conn == null ? null : fieldOfType(conn.getClass(), Channel.class);
            if (channelField != null && channelField.get(conn) == null) {
                channelField.set(conn, new EmbeddedChannel());
            }
        } catch (Throwable t) {
            LOGGER.warn("[TaczMechanics] could not give dummy a channel", t);
        }
    }

    private static Field fieldOfType(Class<?> owner, Class<?> type) {
        for (Class<?> c = owner; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (type.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        return null;
    }

    private TestDummyCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("taczmech")
                .then(Commands.literal("dummy")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> spawn(ctx.getSource(), 0))
                        .then(Commands.literal("sit").executes(ctx -> spawn(ctx.getSource(), 1)))
                        .then(Commands.literal("prone").executes(ctx -> spawn(ctx.getSource(), 2)))
                        .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource())))));
    }

    private static int spawn(CommandSourceStack source, int posture) throws CommandSyntaxException {
        ServerPlayer executor = source.getPlayerOrException();
        try {
            return doSpawn(source, executor, posture);
        } catch (Exception e) {
            LOGGER.error("[TaczMechanics] dummy spawn failed", e);
            source.sendFailure(Component.literal("Dummy spawn failed: " + e));
            return 0;
        }
    }

    private static int doSpawn(CommandSourceStack source, ServerPlayer executor, int posture) {
        ServerLevel level = source.getLevel();

        float yaw = executor.getYRot();
        Vec3 forward = Vec3.directionFromRotation(0f, yaw);
        Vec3 pos = executor.position().add(forward.scale(3.0));

        GameProfile profile = new GameProfile(UUID.randomUUID(), "TaczDummy" + (++counter));
        FakePlayer fake = new FakePlayer(level, profile);
        giveFakeChannel(fake);
        fake.setInvulnerable(true);
        fake.setNoGravity(true);
        // Face the shooter, so stepping back gives a front-on target.
        fake.moveTo(pos.x, executor.getY(), pos.z, yaw + 180f, 0f);
        fake.setYHeadRot(yaw + 180f);
        fake.yBodyRot = yaw + 180f;

        // Send the profile first so clients can build the RemotePlayer when the spawn packet arrives.
        level.getServer().getPlayerList()
                .broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.<ServerPlayer>of(fake)));
        // Normal player-add path (Moonrise hooks this; the raw entityManager field it leaves null). The
        // EmbeddedChannel above keeps the join event's packet-pushing mods from NPEing on the fake.
        level.addNewPlayer(fake);
        DUMMIES.add(fake);

        if (posture != 0) {
            PlayerState state = MovementStateManager.getOrCreate(fake.getUUID());
            if (posture == 1) {
                state.enableSit();
            } else {
                state.enableProne();
            }
            MovementPosture.applyForcedPose(fake, state);
            fake.refreshDimensions();
            PacketDistributor.sendToAllPlayers(new MovementStateBroadcastPayload(fake.getUUID(), state.writeCode()));
        }

        String label = posture == 1 ? "sitting" : posture == 2 ? "prone" : "standing";
        source.sendSuccess(() -> Component.literal("Spawned " + label + " dummy '" + profile.getName() + "'")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        int removed = DUMMIES.size();
        List<UUID> ids = new ArrayList<>(removed);
        for (FakePlayer dummy : DUMMIES) {
            ids.add(dummy.getUUID());
            MovementStateManager.remove(dummy.getUUID());
            dummy.discard();
        }
        if (!ids.isEmpty()) {
            source.getServer().getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(ids));
        }
        DUMMIES.clear();
        source.sendSuccess(() -> Component.literal("Removed " + removed + " dummies").withStyle(ChatFormatting.GRAY), false);
        return removed;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (FakePlayer dummy : DUMMIES) {
            MovementStateManager.remove(dummy.getUUID());
            dummy.discard();
        }
        DUMMIES.clear();
        counter = 0;
    }
}

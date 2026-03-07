package ru.liko.tacz_mechanics.mixin;

import com.mojang.logging.LogUtils;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.data.whizz.WhizzSound;
import ru.liko.tacz_mechanics.data.whizz.WhizzSoundManager;
import ru.liko.tacz_mechanics.mixininterface.EntityKineticBulletExtension;
import ru.liko.tacz_mechanics.network.SuppressionPacket;

import java.util.HashSet;
import java.util.Set;

@Mixin(value = EntityKineticBullet.class, remap = false)
public abstract class WhizzEntityKineticBulletMixin implements EntityKineticBulletExtension {
    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();
    
    @Unique
    private Vec3 taczMechanics$destination = Vec3.ZERO;
    
    @Unique
    private final Set<Integer> taczMechanics$playedWhizzFor = new HashSet<>();

    @Unique
    private final Set<Integer> taczMechanics$suppressedFor = new HashSet<>();
    
    @Unique
    private boolean taczMechanics$playedWhizz = false;

    @Override
    public Vec3 taczMechanics$getDestination() {
        return taczMechanics$destination;
    }

    @Override
    public void taczMechanics$setDestination(Vec3 position) {
        taczMechanics$destination = position;
    }

    @Override
    public boolean taczMechanics$hasPlayedWhizz() {
        return taczMechanics$playedWhizz;
    }

    @Override
    public void taczMechanics$setPlayedWhizz(boolean played) {
        taczMechanics$playedWhizz = played;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void taczMechanics$tick$handleWhizzAndSuppression(CallbackInfo ci) {
        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        Level level = bullet.level();
        if (!(level instanceof ServerLevel serverLevel)) return;

        boolean whizzEnabled = Config.Whizz.enabled;
        boolean suppressionEnabled = Config.Suppression.enabled;
        if (!whizzEnabled && !suppressionEnabled) return;

        Vec3 currentPos = bullet.position();
        Vec3 velocity = bullet.getDeltaMovement();
        taczMechanics$destination = currentPos.add(velocity);

        double speed = velocity.length();
        WhizzSound whizzConfig = WhizzSoundManager.INSTANCE.getDefaultConfig();
        boolean speedOkForWhizz = speed >= whizzConfig.minSpeed();

        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (bullet.getOwner() == player) continue;
            if (player.level().dimension() != level.dimension()) continue;

            boolean alreadyWhizzed = taczMechanics$playedWhizzFor.contains(player.getId());
            boolean alreadySuppressed = taczMechanics$suppressedFor.contains(player.getId());
            if (alreadyWhizzed && alreadySuppressed) continue;

            taczMechanics$handleProximityForPlayer(
                player, bullet, currentPos, speed,
                whizzEnabled && speedOkForWhizz && !alreadyWhizzed,
                suppressionEnabled && !alreadySuppressed,
                whizzConfig
            );
        }
    }
    
    @Unique
    private void taczMechanics$handleProximityForPlayer(
        ServerPlayer player, EntityKineticBullet bullet, Vec3 currentPos, double speed,
        boolean doWhizz, boolean doSuppression, WhizzSound whizzConfig
    ) {
        Vec3 playerPos = player.getEyePosition();
        Vec3 trajectory = taczMechanics$destination.subtract(currentPos);

        double trajectoryLengthSq = trajectory.lengthSqr();
        if (trajectoryLengthSq < 0.001) return;

        Vec3 toPlayer = playerPos.subtract(currentPos);
        double t = toPlayer.dot(trajectory) / trajectoryLengthSq;
        if (t < 0.0 || t > 1.0) return;

        Vec3 closestPoint = currentPos.add(trajectory.scale(t));
        double distance = playerPos.distanceTo(closestPoint);

        if (doWhizz) {
            WhizzSound.DistanceSound sound = whizzConfig.getSoundForDistance(distance);
            if (sound != null) {
                taczMechanics$playedWhizzFor.add(player.getId());
                SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(sound.sound());
                player.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(soundEvent),
                    SoundSource.PLAYERS,
                    playerPos.x, playerPos.y, playerPos.z,
                    sound.volume(), sound.pitch(),
                    player.getRandom().nextLong()
                ));
            }
        }

        if (doSuppression) {
            double maxRadius = Config.Suppression.detectionRadius;
            if (distance <= maxRadius) {
                taczMechanics$suppressedFor.add(player.getId());
                double normalizedDist = 1.0 - (distance / maxRadius);
                double speedFactor = Math.min(speed / 3.0, 1.5);
                float intensity = (float) (normalizedDist * Config.Suppression.flybyIntensity * speedFactor);
                if (intensity > 0.001f) {
                    PacketDistributor.sendToPlayer(player, new SuppressionPacket(intensity));
                }
            }
        }
    }
}

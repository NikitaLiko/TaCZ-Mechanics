package ru.liko.tacz_mechanics.mixin.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.movement.MovementStateManager;
import ru.liko.tacz_mechanics.movement.PlayerState;

/** Silences footsteps while a player is sitting or prone (crawling stays quiet). */
@Mixin(Entity.class)
public abstract class StepSoundMuffleMixin {

    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void taczMechanics$muffle(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!Config.Movement.enabled || !Config.Movement.muffleSteps) return;
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Player player)) return;
        PlayerState move = MovementStateManager.get(player.getUUID());
        if (move != null && !move.isStanding()) {
            ci.cancel();
        }
    }
}

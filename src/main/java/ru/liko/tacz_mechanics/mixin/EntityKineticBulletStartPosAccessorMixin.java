package ru.liko.tacz_mechanics.mixin;

import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import ru.liko.tacz_mechanics.mixininterface.EntityKineticBulletStartPosAccessor;

@Mixin(value = EntityKineticBullet.class, remap = false)
public abstract class EntityKineticBulletStartPosAccessorMixin implements EntityKineticBulletStartPosAccessor {
    @Shadow(remap = false)
    @Mutable
    private Vec3 startPos;

    @Override
    public Vec3 taczMechanics$getStartPos() {
        return startPos;
    }

    @Override
    public void taczMechanics$setStartPos(Vec3 pos) {
        this.startPos = pos;
    }
}

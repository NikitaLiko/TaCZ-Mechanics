package ru.liko.tacz_mechanics.mixininterface;

import net.minecraft.world.phys.Vec3;

public interface EntityKineticBulletStartPosAccessor {
    Vec3 taczMechanics$getStartPos();
    void taczMechanics$setStartPos(Vec3 pos);
}

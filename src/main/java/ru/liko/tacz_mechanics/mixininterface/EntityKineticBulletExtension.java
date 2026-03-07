package ru.liko.tacz_mechanics.mixininterface;

import net.minecraft.world.phys.Vec3;

public interface EntityKineticBulletExtension {
    Vec3 taczMechanics$getDestination();
    void taczMechanics$setDestination(Vec3 position);
    boolean taczMechanics$hasPlayedWhizz();
    void taczMechanics$setPlayedWhizz(boolean played);
}

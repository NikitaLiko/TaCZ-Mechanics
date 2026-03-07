package ru.liko.tacz_mechanics.mixininterface;

public interface EntityKineticBulletImpactState {
    void taczMechanics$setSkipRicochet(boolean skip);
    boolean taczMechanics$consumeSkipRicochet();
}

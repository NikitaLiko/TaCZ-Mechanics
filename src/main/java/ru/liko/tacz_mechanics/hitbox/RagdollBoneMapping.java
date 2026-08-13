package ru.liko.tacz_mechanics.hitbox;

import ru.liko.pjmragdoll.api.BonePart;

/**
 * Maps PJM-Ragdooll's six physics bones onto the four parts this mod's hit
 * pipeline speaks in.
 *
 * <p>Left and right collapse together on purpose: TaCZ and SuperbWarfare both
 * only distinguish head / torso / arm / leg for damage and flags, so carrying
 * the side through would add a dimension neither consumes.
 */
public final class RagdollBoneMapping {

    private RagdollBoneMapping() {}

    public static PlayerSkeleton.Part toPart(BonePart bone) {
        return switch (bone) {
            case HEAD -> PlayerSkeleton.Part.HEAD;
            case TORSO -> PlayerSkeleton.Part.TORSO;
            case ARM_LEFT, ARM_RIGHT -> PlayerSkeleton.Part.ARM;
            case LEG_LEFT, LEG_RIGHT -> PlayerSkeleton.Part.LEG;
        };
    }
}

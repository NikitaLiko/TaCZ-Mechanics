package ru.liko.tacz_mechanics.hitbox;

import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** TaCZ's hit result, plus which bone the bullet actually landed on. */
public class BoneEntityResult extends EntityKineticBullet.EntityResult {
    private final PlayerSkeleton.Part part;

    public BoneEntityResult(Entity entity, Vec3 hitVec, PlayerSkeleton.Part part) {
        super(entity, hitVec, part == PlayerSkeleton.Part.HEAD);
        this.part = part;
    }

    public PlayerSkeleton.Part getPart() {
        return part;
    }
}

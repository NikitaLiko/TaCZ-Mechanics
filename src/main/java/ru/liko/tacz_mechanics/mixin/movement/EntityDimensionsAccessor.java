package ru.liko.tacz_mechanics.mixin.movement;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityDimensionsAccessor {
    
    @Accessor("dimensions")
    EntityDimensions tacz$getDimensions();
    
    @Accessor("dimensions")
    void tacz$setDimensions(EntityDimensions dimensions);
    
    @Accessor("eyeHeight")
    float tacz$getEyeHeight();
    
    @Accessor("eyeHeight")
    void tacz$setEyeHeight(float eyeHeight);
}

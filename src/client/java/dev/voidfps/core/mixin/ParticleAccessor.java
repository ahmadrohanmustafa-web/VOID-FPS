package dev.voidfps.core.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("x") double voidfps$getX();
    @Accessor("y") double voidfps$getY();
    @Accessor("z") double voidfps$getZ();
    @Accessor("xo") double voidfps$getOldX();
    @Accessor("yo") double voidfps$getOldY();
    @Accessor("zo") double voidfps$getOldZ();
}

package com.akashic.kunftig.mixin.mixins.fishing;

import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Write access to {@code Entity#isImmuneToFire}, for items caught in lava. */
@Mixin(Entity.class)
public interface EntityFireAccessor {

    @Accessor("isImmuneToFire")
    void kunftig$setImmuneToFire(boolean immune);
}

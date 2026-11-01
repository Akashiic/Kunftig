package com.akashic.kunftig.mixin.crucible;

import org.bukkit.World;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Empty on purpose: it only makes Mixin hand {@code org.bukkit.World} to {@link CrucibleMixinPlugin#postApply}, which
 * applies {@link com.akashic.kunftig.crucible.WorldDescriptorFix} (a mixin cannot change an existing method's
 * descriptor). Never listed statically: {@link CrucibleMixinPlugin#getMixins} offers it only when it can apply.
 * Mixin reads this annotation with ASM, so the class literal loads nothing.
 */
@Mixin(value = World.class, remap = false)
public interface BukkitWorldDescriptorMixin {
}

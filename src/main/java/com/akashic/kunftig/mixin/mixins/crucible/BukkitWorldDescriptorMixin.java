package com.akashic.kunftig.mixin.mixins.crucible;

import org.bukkit.World;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Empty on purpose: it only makes Mixin hand {@code org.bukkit.World} to {@code KunftigMixinPlugin#postApply}, which
 * applies {@link com.akashic.kunftig.crucible.WorldDescriptorFix} (a mixin cannot change an existing method's
 * descriptor). Mixin reads this annotation with ASM, so the class literal loads nothing; the plugin skips the mixin
 * where Bukkit does not exist.
 */
@Mixin(value = World.class, remap = false)
public interface BukkitWorldDescriptorMixin {
}

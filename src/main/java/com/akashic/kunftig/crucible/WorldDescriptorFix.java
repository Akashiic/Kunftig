package com.akashic.kunftig.crucible;

import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodNode;

/**
 * Provisional fix for production Crucible ({@code 1.7.10-staging-67b53034}), until CrucibleMC fixes its jar (Kunftig
 * issue #3). The released {@code org/bukkit/World.class} declares {@code getWorldServer()} as returning {@code mt},
 * the obfuscated name of {@code net.minecraft.world.WorldServer}. Crucible ships Minecraft with deobfuscated names, so
 * FML skips runtime deobfuscation and nothing ever renames {@code mt}: direct calls fail with
 * {@code NoSuchMethodError} (the plugin remapper rewrites the caller, not the interface) and reflection on
 * {@code World} with {@code NoClassDefFoundError}. {@code CraftWorld} already returns {@code WorldServer}.
 * <p>
 * This rewrites exactly that one descriptor while the class loads, and only when it is the broken one; anything else
 * (a fixed Crucible, Thermos, Paper) is left untouched. Applied from {@code KunftigMixinPlugin} through an empty
 * mixin, on dedicated servers only. Disable with {@code -Dkunftig.crucible.worldFix.disable=true}.
 */
public final class WorldDescriptorFix {

    public static final String TARGET = "org/bukkit/World";
    public static final String METHOD = "getWorldServer";
    public static final String BROKEN = "()Lmt;";
    public static final String FIXED = "()Lnet/minecraft/world/WorldServer;";

    /** Resources that must exist ({@code WorldServer}) and must not ({@code mt}) for the fix to make sense. */
    public static final String RUNTIME_TYPE = "net/minecraft/world/WorldServer.class";
    public static final String OBFUSCATED_TYPE = "mt.class";

    private WorldDescriptorFix() {}

    /**
     * Whether this environment has the defect's preconditions: Bukkit's {@code World} present, {@code WorldServer}
     * under its runtime name, and no class called {@code mt}. Looks up resources only; loads nothing.
     */
    public static boolean applies(ClassLoader loader) {
        return loader.getResource(TARGET + ".class") != null && loader.getResource(RUNTIME_TYPE) != null
            && loader.getResource(OBFUSCATED_TYPE) == null;
    }

    /** @return true if the broken descriptor was found and rewritten */
    public static boolean apply(ClassNode node) {
        if (!TARGET.equals(node.name)) {
            return false;
        }
        MethodNode broken = null;
        for (MethodNode method : node.methods) {
            if (METHOD.equals(method.name)) {
                if (FIXED.equals(method.desc)) {
                    return false; // already correct: a fixed Crucible
                }
                if (BROKEN.equals(method.desc)) {
                    broken = method;
                }
            }
        }
        if (broken == null) {
            return false;
        }
        broken.desc = FIXED;
        broken.signature = null;
        return true;
    }
}

package com.akashic.kunftig.mixin.crucible;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import com.akashic.kunftig.crucible.WorldDescriptorFix;

/**
 * Plugin of {@code mixins.kunftig.crucible.json}, the provisional fix of {@code org.bukkit.World#getWorldServer} on
 * Crucible (issue #3). It must never stop a server:
 * <ul>
 * <li>the config is not {@code required}: Mixin only logs a target that was loaded too early;</li>
 * <li>the mixin is not listed in the JSON but offered by {@link #getMixins} only when the kill switch is off, the jar
 * has the defect's preconditions, and {@code org.bukkit.World} has not been loaded yet; otherwise the fix is skipped
 * with a log line and the server runs as without it.</li>
 * </ul>
 * The config targets Mixin's PREINIT phase because coremods may load {@code World} while they are injected:
 * PlayerContainerFix 2.0.0 probes for Bukkit with {@code Class.forName("org.bukkit.World")} there, which crashed
 * 8a18b84 (mixin in the required DEFAULT config). If this config is selected before that load, the fix applies to it;
 * if not, the already-loaded check above skips it.
 */
public final class CrucibleMixinPlugin implements IMixinConfigPlugin {

    static final String MIXIN = "BukkitWorldDescriptorMixin";
    static final String WORLD = "org.bukkit.World";
    private static final Logger LOG = LogManager.getLogger("Kunftig");

    /** What the plugin decides, separated from Mixin's services so it can be tested. */
    interface Environment {

        boolean disabled();

        boolean hasDefect();

        boolean worldAlreadyLoaded();
    }

    static List<String> mixins(Environment environment, Logger log) {
        if (environment.disabled()) {
            log.warn("Crucible World#getWorldServer fix disabled by -Dkunftig.crucible.worldFix.disable=true");
            return Collections.emptyList();
        }
        if (!environment.hasDefect()) {
            return Collections.emptyList(); // not Bukkit, or a jar without the defect: nothing to do
        }
        if (environment.worldAlreadyLoaded()) {
            log.warn(
                "Kunftig: org.bukkit.World was loaded before mixins could apply (a coremod probing for Bukkit with "
                    + "Class.forName, e.g. PlayerContainerFix), so World#getWorldServer() keeps Crucible's broken "
                    + "descriptor. Nothing else changes; see Kunftig docs/API.md, section 6.");
            return Collections.emptyList();
        }
        return Collections.singletonList(MIXIN);
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        try {
            final ClassLoader loader = CrucibleMixinPlugin.class.getClassLoader();
            return mixins(new Environment() {

                @Override
                public boolean disabled() {
                    return Boolean.getBoolean("kunftig.crucible.worldFix.disable");
                }

                @Override
                public boolean hasDefect() {
                    return WorldDescriptorFix.applies(loader);
                }

                @Override
                public boolean worldAlreadyLoaded() {
                    return MixinService.getService()
                        .getClassTracker()
                        .isClassLoaded(WORLD);
                }
            }, LOG);
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("Kunftig: Crucible World#getWorldServer fix skipped", e);
            return Collections.emptyList();
        }
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        try {
            if (WorldDescriptorFix.apply(targetClass)) {
                LOG.info(
                    "Kunftig: fixed org.bukkit.World#getWorldServer() in this Crucible jar (returned the obfuscated "
                        + "class mt; see Kunftig issue #3). Plugins can call it and reflect on World again.");
            }
        } catch (RuntimeException e) {
            LOG.error("Kunftig: could not fix org.bukkit.World#getWorldServer(); the class is left as it was", e);
        }
    }
}

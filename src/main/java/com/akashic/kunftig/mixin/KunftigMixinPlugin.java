package com.akashic.kunftig.mixin;

import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin: kill switches that work before any config file can be read.
 * <p>
 * {@code -Dkunftig.nametag.disable=true} disables every nametag mixin,
 * {@code -Dkunftig.nametag.visibility.disable=true}
 * only the nametag visibility mixin, {@code -Dkunftig.fishing.disable=true} the fishing mixin and
 * {@code -Dkunftig.particles.disable=true} the particle bridge (the client then never registers the particle channel,
 * so servers send it vanilla particles), {@code -Dkunftig.itemModels.disable=true} the item model icons. Nametag and
 * particle mixins are listed as {@code client} in
 * {@code mixins.kunftig.json}, so a dedicated server never applies them. No mixin targets a Bukkit class: coremods may
 * load those before Mixin can transform them.
 */
public final class KunftigMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOG = LogManager.getLogger("Kunftig");
    private static final String NAMETAG_PACKAGE = "com.akashic.kunftig.mixin.mixins.nametag.";
    private static final String FISHING_PACKAGE = "com.akashic.kunftig.mixin.mixins.fishing.";
    private static final String PARTICLE_PACKAGE = "com.akashic.kunftig.mixin.mixins.particle.";
    private static final String ITEM_MODEL_PACKAGE = "com.akashic.kunftig.mixin.mixins.itemmodel.";

    @Override
    public void onLoad(String mixinPackage) {
        if (Boolean.getBoolean("kunftig.nametag.disable")) {
            LOG.warn("Nametag mixins disabled by -Dkunftig.nametag.disable=true");
        }
        if (Boolean.getBoolean("kunftig.fishing.disable")) {
            LOG.warn("Fishing mixins disabled by -Dkunftig.fishing.disable=true");
        }
        if (Boolean.getBoolean("kunftig.particles.disable")) {
            LOG.warn("Particle mixins disabled by -Dkunftig.particles.disable=true");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith(FISHING_PACKAGE) && Boolean.getBoolean("kunftig.fishing.disable")) {
            return false;
        }
        if (mixinClassName.startsWith(PARTICLE_PACKAGE) && Boolean.getBoolean("kunftig.particles.disable")) {
            return false;
        }
        if (mixinClassName.startsWith(ITEM_MODEL_PACKAGE) && Boolean.getBoolean("kunftig.itemModels.disable")) {
            return false;
        }
        if (mixinClassName.startsWith(NAMETAG_PACKAGE)) {
            if (Boolean.getBoolean("kunftig.nametag.disable")) {
                return false;
            }
            if (mixinClassName.endsWith("VisibilityMixin")
                && Boolean.getBoolean("kunftig.nametag.visibility.disable")) {
                LOG.warn("Nametag visibility mixin disabled by -Dkunftig.nametag.visibility.disable=true");
                return false;
            }
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}

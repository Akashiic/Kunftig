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
 * so servers send it vanilla particles), {@code -Dkunftig.itemModels.disable=true} the item model icons,
 * {@code -Dkunftig.holograms.disable=true} the
 * holograms (client mixin and server bridge), {@code -Dkunftig.rodModels.disable=true} the 3D fishing rods (client
 * mixins; the server side of {@code KunftigRods} still runs), {@code -Dkunftig.input.disable=true} the input keys
 * (client: no keys, the channel is never registered), {@code -Dkunftig.screen.disable=true} the screen HUD and the
 * lock-on (client). Nametag and
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
    private static final String HOLOGRAM_PACKAGE = "com.akashic.kunftig.mixin.mixins.hologram.";
    private static final String DISPLAY_PACKAGE = "com.akashic.kunftig.mixin.mixins.display.";
    private static final String ROD_MODEL_PACKAGE = "com.akashic.kunftig.mixin.mixins.rodmodel.";
    private static final String INPUT_PACKAGE = "com.akashic.kunftig.mixin.mixins.input.";
    private static final String SCREEN_PACKAGE = "com.akashic.kunftig.mixin.mixins.screen.";
    private static final String BLADE_PACKAGE = "com.akashic.kunftig.mixin.mixins.blade.";

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
        if (mixinClassName.startsWith(HOLOGRAM_PACKAGE) && Boolean.getBoolean("kunftig.holograms.disable")) {
            return false;
        }
        if (mixinClassName.startsWith(DISPLAY_PACKAGE) && Boolean.getBoolean("kunftig.displays.disable")) {
            return false;
        }
        if (mixinClassName.startsWith(ITEM_MODEL_PACKAGE) && Boolean.getBoolean("kunftig.itemModels.disable")) {
            return false;
        }
        if (mixinClassName.endsWith("SlotCellMixin") && Boolean.getBoolean("kunftig.slotCells.disable")) {
            LOG.warn("Item model slot cells disabled by -Dkunftig.slotCells.disable=true");
            return false;
        }
        if (mixinClassName.startsWith(ROD_MODEL_PACKAGE) && Boolean.getBoolean("kunftig.rodModels.disable")) {
            return false;
        }
        if (mixinClassName.startsWith(INPUT_PACKAGE) && Boolean.getBoolean("kunftig.input.disable")) {
            return false;
        }
        if (mixinClassName.startsWith(SCREEN_PACKAGE) && Boolean.getBoolean("kunftig.screen.disable")) {
            return false;
        }
        if (mixinClassName.startsWith(BLADE_PACKAGE) && Boolean.getBoolean("kunftig.blades.disable")) {
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

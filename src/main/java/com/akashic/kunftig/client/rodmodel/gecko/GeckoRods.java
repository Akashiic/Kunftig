package com.akashic.kunftig.client.rodmodel.gecko;

import java.lang.reflect.Field;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.client.rodmodel.RodModels;

import software.bernie.geckolib3.GeckoLib;
import software.bernie.geckolib3.core.IAnimatableModel;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.molang.MolangRegistrar;

/**
 * Installs the GeckoLib side of the 3D rods. The only entry point of this package, called reflectively by the client
 * proxy when the mod {@code geckolib3} is loaded: no other Kunftig class names a class of this package, so nothing
 * here (nor GeckoLib) is ever loaded without GeckoLib.
 */
public final class GeckoRods {

    private GeckoRods() {}

    /** Called reflectively (post-initialization, after the other mods registered their item renderers). */
    public static void install() {
        GeckoLib.initialize(); // idempotent; GeckoLib also does it in its own init
        // Created before any animation is parsed again, so the rods' Molang reads this variable (unknown ones are 0).
        MolangRegistrar.getParser()
            .setValue(RodGeoModel.TENSION, 0);
        // Without a model fetcher, GeckoLib's controllers cannot find the animations of an animatable.
        AnimationController.addModelFetcher(
            (AnimationController.ModelFetcher<RodAnimatable>) object -> object instanceof RodAnimatable
                ? (IAnimatableModel<RodAnimatable>) ((RodAnimatable) object).model
                : null);
        IItemRenderer previous = registered(Items.fishing_rod);
        if (previous != null) {
            Kunftig.LOG.info(
                "Kunftig 3D rods: fishing rods already have an item renderer ({}); it keeps every rod without a 3D model",
                previous.getClass()
                    .getName());
        }
        MinecraftForgeClient.registerItemRenderer(Items.fishing_rod, new RodItemRenderer(previous));
        RodModels.INSTANCE.markInstalled();
        Kunftig.LOG.info("Kunftig 3D rods: GeckoLib renderer installed on the fishing rod");
    }

    /** The renderer Forge has for an item, whatever render type it handles. */
    @SuppressWarnings("unchecked")
    private static IItemRenderer registered(Item item) {
        try {
            Field f = MinecraftForgeClient.class.getDeclaredField("customItemRenderers");
            f.setAccessible(true);
            return ((Map<Item, IItemRenderer>) f.get(null)).get(item);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Kunftig.LOG.warn("Kunftig 3D rods: cannot see other fishing rod renderers: {}", e.toString());
            return null;
        }
    }
}

package com.akashic.kunftig.client.display.gecko;

import com.akashic.kunftig.Kunftig;
import com.akashic.kunftig.client.display.DisplayClient;

import software.bernie.geckolib3.GeckoLib;
import software.bernie.geckolib3.core.IAnimatableModel;
import software.bernie.geckolib3.core.controller.AnimationController;

/**
 * Installs the GeckoLib side of the displays. The only entry point of this package, called reflectively by the client
 * proxy when the mod {@code geckolib3} is loaded: no other Kunftig class names a class of this package.
 */
public final class GeckoDisplays {

    private GeckoDisplays() {}

    /** Called reflectively (post-initialization). */
    @SuppressWarnings("unchecked")
    public static void install() {
        GeckoLib.initialize(); // idempotent
        AnimationController.addModelFetcher(
            (AnimationController.ModelFetcher<DisplayAnimatable>) object -> object instanceof DisplayAnimatable
                ? (IAnimatableModel<DisplayAnimatable>) ((DisplayAnimatable) object).model
                : null);
        DisplayClient.INSTANCE.install(new DisplayRenderer());
        Kunftig.LOG.info("Kunftig displays: GeckoLib renderer installed");
    }
}
